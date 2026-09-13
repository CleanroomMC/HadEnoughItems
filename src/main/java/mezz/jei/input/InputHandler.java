package mezz.jei.input;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.config.Config;
import mezz.jei.config.IngredientBlacklistType;
import mezz.jei.config.KeyBindings;
import mezz.jei.gui.Focus;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.ghost.GhostIngredientDragManager;
import mezz.jei.gui.ghost.IGhostIngredientDragSource;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.IngredientListOverlay;
import mezz.jei.gui.overlay.bookmarks.LeftAreaDispatcher;
import mezz.jei.gui.recipes.RecipeClickableArea;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.recipes.RecipeRegistry;
import mezz.jei.runtime.JeiRuntime;
import mezz.jei.util.ReflectionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class InputHandler {

	/**
	 * Ordered most specific first.
	 */
	private enum KeyBind {
		SHOW_RECIPE(KeyBindings.showRecipe),
		SHOW_USES(KeyBindings.showUses),
		BOOKMARK_NEW_GROUP_TO_TOP(KeyBindings.bookmarkNewGroupToTop),
		BOOKMARK_NEW_GROUP(KeyBindings.bookmarkNewGroup),
		BOOKMARK_TO_TOP(KeyBindings.bookmarkToTop),
		BOOKMARK(KeyBindings.bookmark);

		private final KeyBinding keyBind;

		KeyBind(KeyBinding keyBind) {
			this.keyBind = keyBind;
		}

		public boolean tryMatch(int keyCode) {
			return keyBind.isActiveAndMatches(keyCode);
		}
	}

	private final RecipeRegistry recipeRegistry;
	private final IIngredientRegistry ingredientRegistry;
	private final IngredientFilter ingredientFilter;
	private final RecipesGui recipesGui;
	private final IngredientListOverlay ingredientListOverlay;
	private final LeftAreaDispatcher leftAreaDispatcher;
	private final BookmarkList bookmarkList;
	private final IAutocraftingHandler autocraftingHandler;
	private final List<IShowsRecipeFocuses> showsRecipeFocuses = new ArrayList<>();
	private final IntSet clickHandled = new IntArraySet();
	private final GhostIngredientDragManager ghostIngredientDragManager;

	private boolean deferMouseEventCancellation;

	public InputHandler(JeiRuntime runtime, IngredientRegistry ingredientRegistry, IngredientListOverlay ingredientListOverlay, GuiScreenHelper guiScreenHelper, LeftAreaDispatcher leftAreaDispatcher, BookmarkList bookmarkList, GhostIngredientDragManager ghostIngredientDragManager) {
		this.recipeRegistry = runtime.getRecipeRegistry();
		this.ingredientRegistry = ingredientRegistry;
		this.ingredientFilter = runtime.getIngredientFilter();
		this.recipesGui = runtime.getRecipesGui();
		this.ingredientListOverlay = ingredientListOverlay;
		this.leftAreaDispatcher = leftAreaDispatcher;
		this.bookmarkList = bookmarkList;
		this.autocraftingHandler = runtime.getAutocraftingHandler();
		this.ghostIngredientDragManager = ghostIngredientDragManager;

		this.showsRecipeFocuses.add(recipesGui);
		this.showsRecipeFocuses.add(ingredientListOverlay);
		this.showsRecipeFocuses.add(leftAreaDispatcher);
		this.showsRecipeFocuses.add(new GuiContainerWrapper(guiScreenHelper));
	}

	/**
	 * When we have keyboard focus, use Pre
	 */
	@SubscribeEvent
	public void onGuiKeyboardEvent(GuiScreenEvent.KeyboardInputEvent.Pre event) {
		if (hasKeyboardFocus() && handleKeyEvent()) {
			event.setCanceled(true);
		}
	}

	/**
	 * Without focus, use Post
	 */
	@SubscribeEvent
	public void onGuiKeyboardEvent(GuiScreenEvent.KeyboardInputEvent.Post event) {
		if (hasKeyboardFocus()) return;
		else if (handleKeyEvent())
			event.setCanceled(true);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public void onGuiMouseEvent(GuiScreenEvent.MouseInputEvent.Pre event) {
		this.deferMouseEventCancellation = false;
		GuiScreen guiScreen = event.getGui();
		Minecraft minecraft = guiScreen.mc;
		if (minecraft != null) {
			int x = Mouse.getEventX() * guiScreen.width / minecraft.displayWidth;
			int y = guiScreen.height - Mouse.getEventY() * guiScreen.height / minecraft.displayHeight - 1;
			if (handleMouseEvent(guiScreen, x, y)) {
				if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState() && ingredientListOverlay.isMouseOverSearchField(x, y)) {
					// More Overlays observes uncanceled search-field clicks at the default event priority
					this.deferMouseEventCancellation = true;
				} else {
					event.setCanceled(true);
				}
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public void cancelDeferredGuiMouseEvent(GuiScreenEvent.MouseInputEvent.Pre event) {
		if (this.deferMouseEventCancellation) {
			event.setCanceled(true);
			this.deferMouseEventCancellation = false;
		}
	}

	public boolean handleMouseEvent(GuiScreen guiScreen, int mouseX, int mouseY) {
		boolean cancelEvent = false;
		final int eventButton = Mouse.getEventButton();
		if (eventButton > -1) {
			if (Mouse.getEventButtonState()) {
				clickHandled.remove(eventButton);
				cancelEvent = handleMouseClick(guiScreen, eventButton, mouseX, mouseY, null);
				if (cancelEvent) {
					clickHandled.add(eventButton);
				}
			} else {
				boolean wasClickHandled = clickHandled.remove(eventButton);
				cancelEvent = handleMouseRelease(guiScreen, mouseX, mouseY) || wasClickHandled;
			}
		} else if (Mouse.getEventDWheel() != 0) {
			cancelEvent = handleMouseScroll(Mouse.getEventDWheel(), mouseX, mouseY);
		} else {
			cancelEvent = ghostIngredientDragManager.handleMouseMoved(guiScreen, getFocusUnderMouseForClick(mouseX, mouseY));
		}
		return cancelEvent;
	}

	private boolean handleMouseRelease(GuiScreen guiScreen, int mouseX, int mouseY) {
		final int eventButton = Mouse.getEventButton();
		ghostIngredientDragManager.handleMouseMoved(guiScreen, getFocusUnderMouseForClick(mouseX, mouseY));
		IClickedIngredient<?> pendingClick = ghostIngredientDragManager.takePendingClick(eventButton);
		if (pendingClick != null) {
			handleMouseClick(guiScreen, eventButton, mouseX, mouseY, pendingClick);
			return true;
		}
		if (ghostIngredientDragManager.handleMouseReleased(eventButton, mouseX, mouseY)) {
			return true;
		}
		if (leftAreaDispatcher.handleMouseReleased(mouseX, mouseY, eventButton)) {
			return true;
		}

		return false;
	}

	private boolean handleMouseScroll(int dWheel, int mouseX, int mouseY) {
        if (ingredientListOverlay.isMouseOver(mouseX, mouseY) && ingredientListOverlay.handleMouseScrolled(mouseX, mouseY, dWheel)) {
            return true;
        }

        if (leftAreaDispatcher.isMouseOver(mouseX, mouseY) && leftAreaDispatcher.handleMouseScrolled(mouseX, mouseY, dWheel)) {
            return true;
        }

        return false;
	}

	private boolean handleMouseClick(GuiScreen guiScreen, int mouseButton, int mouseX, int mouseY, @Nullable IClickedIngredient<?> pendingClick) {
		IClickedIngredient<?> clicked = pendingClick == null ? getFocusUnderMouseForClick(mouseX, mouseY) : pendingClick;
		if (Config.isEditModeEnabled() && clicked != null && handleClickEdit(clicked)) {
			return true;
		}

		IIngredientListElement<?> listElement = getElementUnderMouse();
		if (pendingClick == null && this.ghostIngredientDragManager.handleMouseClicked(guiScreen.mc, guiScreen, clicked, listElement, mouseButton, mouseX, mouseY)) {
			return true;
		}

		if (ingredientListOverlay.handleMouseClicked(mouseX, mouseY, mouseButton)) {
			return true;
		}

		if (leftAreaDispatcher.handleMouseClicked(mouseX, mouseY, mouseButton)) {
			return true;
		}

		if (clicked != null && (Config.mouseClickToSeeRecipe() && handleMouseClickedFocus(mouseButton, clicked))) {
			return true;
		}
		if (handleFocusKeybinds(mouseButton - 100)) {
			return true;
		}

		if (guiScreen instanceof GuiContainer) {
			GuiContainer guiContainer = (GuiContainer) guiScreen;
			RecipeClickableArea clickableArea = recipeRegistry.getRecipeClickableArea(guiContainer, mouseX - guiContainer.getGuiLeft(), mouseY - guiContainer.getGuiTop());
			if (clickableArea != null) {
				List<String> recipeCategoryUids = clickableArea.getRecipeCategoryUids();
				recipesGui.showCategories(recipeCategoryUids);
				return true;
			}
		}

		return handleGlobalKeybinds(mouseButton - 100);
	}

	@Nullable
	private IClickedIngredient<?> getFocusUnderMouseForClick(int mouseX, int mouseY) {
		for (IShowsRecipeFocuses gui : showsRecipeFocuses) {
			if (gui.canSetFocusWithMouse()) {
				IClickedIngredient<?> clicked = gui.getIngredientUnderMouse(mouseX, mouseY);
				if (clicked != null) {
					return clicked;
				}
			}
		}
		return null;
	}

	@Nullable
	public IIngredientListElement<?> getElementUnderMouse() {
		for (IShowsRecipeFocuses gui : showsRecipeFocuses) {
			if (!(gui instanceof IGhostIngredientDragSource)) {
				continue;
			}
			IIngredientListElement<?> element = ((IGhostIngredientDragSource) gui).getElementUnderMouse();
			if (element != null) {
				if (element.getIngredient() instanceof BookmarkItem) {
					return ((BookmarkItem<?>) element.getIngredient()).getSavedElement();
				}
				return element;
			}
		}
		return null;
	}

	@Nullable
	private IClickedIngredient<?> getIngredientUnderMouseForKey(int mouseX, int mouseY) {
		for (IShowsRecipeFocuses gui : showsRecipeFocuses) {
			IClickedIngredient<?> clicked = gui.getIngredientUnderMouse(mouseX, mouseY);
			if (clicked != null) {
				return clicked;
			}
		}
		return null;
	}

	private <V> boolean handleMouseClickedFocus(int mouseButton, IClickedIngredient<V> clicked) {
		Object value = clicked.getValue();
		if (value instanceof BookmarkItem) {
			value = ((BookmarkItem<?>) value).getIngredient();
		}
		if (mouseButton == 0) {
			IFocus<?> focus = new Focus<>(IFocus.Mode.OUTPUT, value);
			recipesGui.show(focus);
			clicked.onClickHandled();
			return true;
		} else if (mouseButton == 1) {
			IFocus<?> focus = new Focus<>(IFocus.Mode.INPUT, value);
			recipesGui.show(focus);
			clicked.onClickHandled();
			return true;
		}

		return false;
	}

	private <V> boolean handleClickEdit(IClickedIngredient<V> clicked) {
		V ingredient = clicked.getValue();
		IngredientBlacklistType blacklistType = GuiScreen.isCtrlKeyDown() ? IngredientBlacklistType.WILDCARD : IngredientBlacklistType.ITEM;

		IIngredientHelper<V> ingredientHelper = ingredientRegistry.getIngredientHelper(ingredient);

		if (Config.isIngredientOnConfigBlacklist(ingredient, ingredientHelper)) {
			Config.removeIngredientFromConfigBlacklist(ingredientFilter, ingredientRegistry, ingredient, blacklistType, ingredientHelper);
		} else {
			Config.addIngredientToConfigBlacklist(ingredientFilter, ingredientRegistry, ingredient, blacklistType, ingredientHelper);
		}
		clicked.onClickHandled();
		return true;
	}

	private boolean hasKeyboardFocus() {
		return ingredientListOverlay.hasKeyboardFocus() || autocraftingHandler.isActive();
	}

	private boolean handleKeyEvent() {
		char typedChar = Keyboard.getEventCharacter();
		int eventKey = Keyboard.getEventKey();

		return ((eventKey == 0 && typedChar >= 32) || Keyboard.getEventKeyState()) &&
				handleKeyDown(typedChar, eventKey);
	}

	private boolean handleKeyDown(char typedChar, int eventKey) {
		if (autocraftingHandler.isActive()) {
			if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
				autocraftingHandler.stop();
				return true;
			}
		}

		if (ghostIngredientDragManager.handleKeyDown(eventKey)) {
			return true;
		}

		if (ingredientListOverlay.hasKeyboardFocus()) {
			if (KeyBindings.isInventoryCloseKey(eventKey) || KeyBindings.isEnterKey(eventKey)) {
				ingredientListOverlay.setKeyboardFocus(false);
				return true;
			} else if (ingredientListOverlay.onKeyPressed(typedChar, eventKey)) {
				return true;
			}
		}

		if (leftAreaDispatcher.onKeyPressed(typedChar, eventKey)) {
			return true;
		}

		if (!isContainerTextFieldFocused()) {
			if (handleGlobalKeybinds(eventKey)) {
				return true;
			}
			if (KeyBindings.toggleOverlay.isActiveAndMatches(eventKey)) {
				Config.toggleOverlayEnabled();
				return true;
			}
			if (KeyBindings.toggleBookmarkOverlay.isActiveAndMatches(eventKey)) {
				Config.toggleBookmarkEnabled();
				return true;
			}
			if (handleFocusKeybinds(eventKey)) {
				return true;
			}
			return ingredientListOverlay.onKeyPressed(typedChar, eventKey);
		}

		return false;
	}

	private boolean handleGlobalKeybinds(int eventKey) {
		return ingredientListOverlay.onGlobalKeyPressed(eventKey);
	}

	private boolean handleFocusKeybinds(int eventKey) {
		KeyBind pressedKey = null;
		for (KeyBind keyBind : KeyBind.values()) {
			if (keyBind.tryMatch(eventKey)) {
				pressedKey = keyBind;
				break;
			}
		}

		if (pressedKey == null) {
			return false;
		}

		switch (pressedKey) {
			case BOOKMARK:
				return addBookmark(false, false);
			case BOOKMARK_TO_TOP:
				return addBookmark(false, true);
			case BOOKMARK_NEW_GROUP:
				return addBookmark(true, isPrepend());
			case BOOKMARK_NEW_GROUP_TO_TOP:
				return addBookmark(true, true);
			case SHOW_RECIPE:
				return showRecipeOrUses(IFocus.Mode.OUTPUT);
			case SHOW_USES:
				return showRecipeOrUses(IFocus.Mode.INPUT);
		}

		return false;
	}

	private static boolean isPrepend() {
		return GuiScreen.isShiftKeyDown();
	}

	private boolean addBookmark(boolean newGroup, boolean addToFront) {
		int mouseX = MouseHelper.getX();
		int mouseY = MouseHelper.getY();
		IClickedIngredient<?> clicked = getIngredientUnderMouseForKey(mouseX, mouseY);
		if (clicked == null) {
			return false;
		}

		Object value = clicked.getValue();

		// Pointing at something already bookmarked means the player wants it gone
		if (value instanceof BookmarkItem) {
			boolean removed = bookmarkList.remove(value);
			if (removed && bookmarkList.isEmpty() && Config.isBookmarkOverlayEnabled()) {
				Config.toggleBookmarkEnabled();
			}
			return removed;
		}

		final boolean added;
		RecipeLayout layout = recipesGui.getRecipeLayout(mouseX, mouseY);
		if (layout != null) {
			added = layout.addToBookmarks(addToFront);
		} else if (value instanceof CollapsedGroupIngredient) {
			added = false;
		} else if (newGroup) {
			added = bookmarkList.addToNewGroup(new BookmarkItem<>(value), addToFront);
		} else {
			added = bookmarkList.add(new BookmarkItem<>(value), addToFront);
		}
		if (added && !Config.isBookmarkOverlayEnabled()) {
			Config.toggleBookmarkEnabled();
		}
		return added;
	}

	private boolean showRecipeOrUses(IFocus.Mode mode) {
		IClickedIngredient<?> clicked = getIngredientUnderMouseForKey(MouseHelper.getX(), MouseHelper.getY());
		if (clicked == null) {
			return false;
		}

		Object value = clicked.getValue();
		recipesGui.show(new Focus<>(mode, value instanceof BookmarkItem ? ((BookmarkItem<?>) value).getIngredient() : value));
		clicked.onClickHandled();
		return true;
	}

	private boolean isContainerTextFieldFocused() {
		GuiScreen gui = Minecraft.getMinecraft().currentScreen;
		if (gui == null) {
			return false;
		}
		GuiTextField textField = ReflectionUtil.getFieldWithClass(gui, GuiTextField.class);
		return textField != null && textField.getVisible() && textField.isFocused();
	}
}

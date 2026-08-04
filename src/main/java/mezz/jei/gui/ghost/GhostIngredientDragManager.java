package mezz.jei.gui.ghost;

import mezz.jei.Internal;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.DefaultGhostIngredientHandler;
import mezz.jei.config.Config;
import mezz.jei.config.KeyBindings;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.input.IClickedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GhostIngredientDragManager {
	private final GuiScreenHelper guiScreenHelper;
	private final IngredientRegistry ingredientRegistry;
	private final List<GhostIngredientReturning> ghostIngredientsReturning = new ArrayList<>();
	private final DefaultGhostIngredientHandler defaultHandler = new DefaultGhostIngredientHandler();

	@Nullable
	private GhostIngredientDrag<?> ghostIngredientDrag;
	@Nullable
	private Object hoveredIngredient;
	@Nullable
	private List<IGhostIngredientHandler.Target<Object>> hoveredIngredientTargets;
	@Nullable
	private IGhostIngredientHandler<?> hoverHandler;
	private int dragMouseButton = -1;
	private boolean dropOnMouseRelease;

	public GhostIngredientDragManager(GuiScreenHelper guiScreenHelper, IngredientRegistry ingredientRegistry) {
		this.guiScreenHelper = guiScreenHelper;
		this.ingredientRegistry = ingredientRegistry;
	}

	public void updateScreen(GuiScreen gui, boolean forceUpdate) {
		if (gui == null) {
			this.stopDrag();
		}
	}

	public void drawTooltips(Minecraft minecraft, int mouseX, int mouseY) {
		if (!(minecraft.currentScreen instanceof GuiContainer)) { // guiContainer uses drawOnForeground
			drawGhostIngredientHighlights(minecraft, mouseX, mouseY);
		}
		if (ghostIngredientDrag != null) {
			ghostIngredientDrag.drawItem(minecraft, mouseX, mouseY);
		}
		ghostIngredientsReturning.forEach(returning -> returning.drawItem(minecraft));
		ghostIngredientsReturning.removeIf(GhostIngredientReturning::isComplete);
	}

	public void drawOnForeground(Minecraft minecraft, GuiContainer gui, int mouseX, int mouseY) {
		GlStateManager.pushMatrix();
		GlStateManager.translate(-gui.getGuiLeft(), -gui.getGuiTop(), 0);
		drawGhostIngredientHighlights(minecraft, mouseX, mouseY);
		GlStateManager.popMatrix();
	}

	private void drawGhostIngredientHighlights(Minecraft minecraft, int mouseX, int mouseY) {
		if (this.ghostIngredientDrag != null) {
			this.ghostIngredientDrag.drawTargets(mouseX, mouseY);
		} else {
			IIngredientListElement elementUnderMouse = Internal.getInputHandler().getElementUnderMouse();
			Object hovered = elementUnderMouse == null ? null : elementUnderMouse.getIngredient();
			boolean showHighlight = true;
			if (!Objects.equals(hovered, this.hoveredIngredient)) {
				this.hoveredIngredient = hovered;
				this.hoveredIngredientTargets = null;
				GuiScreen currentScreen = minecraft.currentScreen;
				if (currentScreen != null && hovered != null) {
					IGhostIngredientHandler<GuiScreen> handler = guiScreenHelper.getGhostIngredientHandler(currentScreen);
					if (handler != null && handler.shouldHighlightTargets()) {
						Object targetIngredient = getIngredientForHandler(handler, hovered);
						this.hoveredIngredientTargets = handler.getTargets(currentScreen, targetIngredient, false);
						hoverHandler = handler;
					} else if (handler == null) {
						this.hoveredIngredientTargets = defaultHandler.getTargets(currentScreen, hovered, false);
						hoverHandler = defaultHandler;
					}
				}
			}
			if (hoverHandler == defaultHandler) {
				showHighlight = false;
			}
			if (this.hoveredIngredientTargets != null && !Config.isCheatItemsEnabled() && showHighlight) {
				GhostIngredientDrag.drawTargets(mouseX, mouseY, this.hoveredIngredientTargets);
			}
		}
	}

	public boolean handleMouseClicked(Minecraft minecraft, GuiScreen currentScreen, IClickedIngredient<?> clicked,
			IIngredientListElement<?> listElement, int mouseButton, int mouseX, int mouseY) {
		if (this.ghostIngredientDrag != null) {
			if (dropOnMouseRelease) {
				return true;
			}
			return completeDrag(mouseX, mouseY);
		}
		EntityPlayerSP player = minecraft.player;
		if (player != null && clicked != null) {
			ItemStack mouseItem = player.inventory.getItemStack();
			if (mouseItem.isEmpty() && this.handleClickGhostIngredient(currentScreen, clicked, listElement, mouseButton)) {
				return true;
			}
		}
		return false;
	}

	public boolean handleMouseReleased(int mouseButton, int mouseX, int mouseY) {
		if (ghostIngredientDrag == null || !dropOnMouseRelease || mouseButton != dragMouseButton) {
			return false;
		}
		completeDrag(mouseX, mouseY);
		return true;
	}

	private boolean completeDrag(int mouseX, int mouseY) {
		boolean success = this.ghostIngredientDrag.onClick(mouseX, mouseY);
		if (!success) {
			GhostIngredientReturning<?> returning = GhostIngredientReturning.create(this.ghostIngredientDrag, mouseX, mouseY);
			this.ghostIngredientsReturning.add(returning);
		}
		this.ghostIngredientDrag = null;
		this.dragMouseButton = -1;
		this.dropOnMouseRelease = false;
		return success;
	}

	public void stopDrag() {
		if (this.ghostIngredientDrag != null) {
			this.ghostIngredientDrag.stop();
			this.ghostIngredientDrag = null;
		}
		this.dragMouseButton = -1;
		this.dropOnMouseRelease = false;
	}

	public <T extends GuiScreen, V> boolean handleClickGhostIngredient(T currentScreen, IClickedIngredient<V> clicked) {
		return handleClickGhostIngredient(currentScreen, clicked, null, -1);
	}

	private <T extends GuiScreen> boolean handleClickGhostIngredient(T currentScreen, IClickedIngredient<?> clicked,
			@Nullable IIngredientListElement<?> listElement, int mouseButton) {
		if (clicked == null) {
			return false;
		}
		IGhostIngredientHandler<T> handler = guiScreenHelper.getGhostIngredientHandler(currentScreen);
		if (handler == null) {
			if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
				return handleClickGhostIngredient(defaultHandler, currentScreen, clicked, mouseButton);
			}
			return false;
		}
		Object ingredient = listElement == null ? getIngredientForHandler(handler, clicked.getValue()) : listElement.getIngredient();
		return startGhostIngredientDrag(handler, currentScreen, clicked, ingredient, clicked.getValue(), true, mouseButton);
	}

	public <T extends GuiScreen> boolean handleClickGhostIngredient(IGhostIngredientHandler<T> handler, T currentScreen,
			IClickedIngredient<?> clicked) {
		return handleClickGhostIngredient(handler, currentScreen, clicked, -1);
	}

	public boolean handleKeyDown(int eventKey) {
		if (KeyBindings.isInventoryCloseKey(eventKey) || KeyBindings.isEnterKey(eventKey)) {
			// Only cancel other handling of inputs if we are currently dragging
			if (this.ghostIngredientDrag != null) {
				stopDrag();
				return true;
			}
		}
		return false;
	}

	private <T extends GuiScreen> boolean handleClickGhostIngredient(IGhostIngredientHandler<T> handler, T currentScreen,
	                                                                 IClickedIngredient<?> clicked, int mouseButton) {
		Object ingredient = getIngredientForHandler(handler, clicked.getValue());
		return startGhostIngredientDrag(handler, currentScreen, clicked, ingredient, clicked.getValue(), handler != defaultHandler, mouseButton);
	}

	private Object getIngredientForHandler(IGhostIngredientHandler<?> handler, Object ingredient) {
		if (handler != defaultHandler && ingredient instanceof BookmarkItem) {
			return ((BookmarkItem<?>) ingredient).getIngredient();
		}
		return ingredient;
	}

	private <T extends GuiScreen, V> boolean startGhostIngredientDrag(IGhostIngredientHandler<T> handler,
	                                                                  T currentScreen, IClickedIngredient<?> clicked, V ingredient, Object bookmarkIngredient,
	                                                                  boolean includeBookmarkTargets, int mouseButton) {
		if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
			if (handler.quickMove(currentScreen, ingredient)) {
				clicked.onClickHandled();
				return true;
			}
		}
		List<IGhostIngredientHandler.Target<V>> targets = new ArrayList<>(handler.getTargets(currentScreen, ingredient, true));
		if (includeBookmarkTargets) {
			addBookmarkTargets(currentScreen, bookmarkIngredient, targets);
		}
		if (!targets.isEmpty()) {
			IIngredientRenderer<V> ingredientRenderer = ingredientRegistry.getIngredientRenderer(ingredient);
			Rectangle clickedArea = clicked.getArea();
			this.ghostIngredientDrag = new GhostIngredientDrag<>(handler, targets, ingredientRenderer, ingredient, clickedArea);
			this.dropOnMouseRelease = mouseButton >= 0 && Config.holdToDragGhostIngredients();
			this.dragMouseButton = dropOnMouseRelease ? mouseButton : -1;
			clicked.onClickHandled();
			return true;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private <V> void addBookmarkTargets(GuiScreen currentScreen, Object bookmarkIngredient, List<IGhostIngredientHandler.Target<V>> targets) {
		List<IGhostIngredientHandler.Target<Object>> bookmarkTargets = defaultHandler.getTargets(currentScreen, bookmarkIngredient, true);
		for (IGhostIngredientHandler.Target<Object> bookmarkTarget : bookmarkTargets) {
			if (bookmarkTarget instanceof IGhostIngredientHandler.AwareTarget) {
				targets.add(new CarrierAwareTarget((IGhostIngredientHandler.AwareTarget<Object>) bookmarkTarget, bookmarkIngredient));
			} else {
				targets.add(new CarrierTarget(bookmarkTarget, bookmarkIngredient));
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private static class CarrierAwareTarget implements IGhostIngredientHandler.AwareTarget {

		private final IGhostIngredientHandler.AwareTarget<Object> target;
		private final Object carrier;

		private CarrierAwareTarget(IGhostIngredientHandler.AwareTarget<Object> target, Object carrier) {
			this.target = target;
			this.carrier = carrier;
		}

		@Override
		public void onDrag(Object ingredient, int mouseX, int mouseY) {
			target.onDrag(carrier, mouseX, mouseY);
		}

		@Override
		public void onDragComplete() {
			target.onDragComplete();
		}

		@Override
		public void accept(Object ingredient, int mouseX, int mouseY) {
			target.accept(carrier, mouseX, mouseY);
		}

		@Override
		public Rectangle getArea() {
			return target.getArea();
		}

	}

	@SuppressWarnings("rawtypes")
	private static class CarrierTarget implements IGhostIngredientHandler.Target {

		private final IGhostIngredientHandler.Target<Object> target;
		private final Object carrier;

		private CarrierTarget(IGhostIngredientHandler.Target<Object> target, Object carrier) {
			this.target = target;
			this.carrier = carrier;
		}

		@Override
		public Rectangle getArea() {
			return target.getArea();
		}

		@Override
		public void accept(Object ingredient) {
			target.accept(carrier);
		}

	}

}

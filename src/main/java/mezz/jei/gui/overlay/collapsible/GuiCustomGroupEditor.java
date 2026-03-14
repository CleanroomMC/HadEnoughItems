package mezz.jei.gui.overlay.collapsible;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.config.Config;
import mezz.jei.config.CustomGroupsConfig;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.startup.StackHelper;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * Editor screen for a custom collapsible group.
 * Left panel: searchable item grid for selection.
 * Right panel: preview of selected items.
 */
public class GuiCustomGroupEditor extends GuiScreen {

	private static final int ITEM_SIZE = 18;
	private static final int GRID_PADDING = 2;

	private static final int BTN_SAVE = 0;
	private static final int BTN_CANCEL = 1;
	private static final int BTN_PREV_PAGE = 2;
	private static final int BTN_NEXT_PAGE = 3;
	private static final int BTN_PREV_SEL_PAGE = 4;
	private static final int BTN_NEXT_SEL_PAGE = 5;

	private final GuiCollapsibleGroups parentScreen;
	private final CustomGroupsConfig.CustomGroup group;
	private final Set<String> selectedUids = new LinkedHashSet<>();

	@Nullable
	private GuiTextField nameField;
	@Nullable
	private GuiTextField searchField;

	// Left grid (all items)
	private List<IIngredientListElement> filteredItems = Collections.emptyList();
	private int leftCols;
	private int leftRows;
	private int leftGridX;
	private int leftGridY;
	private int leftPage = 0;
	private int leftTotalPages = 1;
	private int leftItemsPerPage;

	// Right grid (selected items)
	private int rightCols;
	private int rightRows;
	private int rightGridX;
	private int rightGridY;
	private int rightPage = 0;
	private int rightTotalPages = 1;
	private int rightItemsPerPage;

	// Cached selected elements for the right panel (any ingredient type)
	private List<IIngredientListElement<?>> selectedStacks = new ArrayList<>();

	// Drag-select state
	private boolean isDragging = false;
	private boolean dragAdding = false;
	@Nullable private String lastDraggedUid = null;

	public GuiCustomGroupEditor(GuiCollapsibleGroups parentScreen, CustomGroupsConfig.CustomGroup group) {
		this.parentScreen = parentScreen;
		this.group = group;
		this.selectedUids.addAll(group.itemUids);
	}

	@Override
	public void initGui() {
		super.initGui();
		Keyboard.enableRepeatEvents(true);
		this.buttonList.clear();

		int topBarHeight = 48;
		int panelDivider = (int) (this.width * 0.65);

		// Name field
		nameField = new GuiTextField(10, this.fontRenderer, 60, 6, panelDivider - 70, 16);
		nameField.setMaxStringLength(40);
		nameField.setText(group.displayName != null ? group.displayName : "");

		// Search field
		searchField = new GuiTextField(11, this.fontRenderer, 4, topBarHeight - 18, panelDivider - 12, 14);
		searchField.setMaxStringLength(128);
		searchField.setText("");

		// Save & Cancel buttons
		this.buttonList.add(new GuiButton(BTN_SAVE, panelDivider + 4, 4, 50, 18,
			Translator.translateToLocal("jei.gui.collapsible.editor.save")));
		this.buttonList.add(new GuiButton(BTN_CANCEL, panelDivider + 58, 4, 50, 18,
			Translator.translateToLocal("jei.gui.collapsible.back")));

		// Calculate left grid layout
		int leftWidth = panelDivider - 8;
		int leftHeight = this.height - topBarHeight - 26; // room for page nav
		leftCols = Math.max(1, leftWidth / ITEM_SIZE);
		leftRows = Math.max(1, leftHeight / ITEM_SIZE);
		leftGridX = 4;
		leftGridY = topBarHeight;
		leftItemsPerPage = leftCols * leftRows;

		// Calculate right grid layout
		int rightWidth = this.width - panelDivider - 8;
		int rightHeight = this.height - topBarHeight - 26;
		rightCols = Math.max(1, rightWidth / ITEM_SIZE);
		rightRows = Math.max(1, rightHeight / ITEM_SIZE);
		rightGridX = panelDivider + 4;
		rightGridY = topBarHeight;
		rightItemsPerPage = rightCols * rightRows;

		// Page nav buttons for left grid
		int leftNavY = this.height - 22;
		this.buttonList.add(new GuiButton(BTN_PREV_PAGE, 4, leftNavY, 30, 18, "<"));
		this.buttonList.add(new GuiButton(BTN_NEXT_PAGE, panelDivider - 34, leftNavY, 30, 18, ">"));

		// Page nav buttons for right grid
		this.buttonList.add(new GuiButton(BTN_PREV_SEL_PAGE, panelDivider + 4, leftNavY, 30, 18, "<"));
		this.buttonList.add(new GuiButton(BTN_NEXT_SEL_PAGE, this.width - 34, leftNavY, 30, 18, ">"));

		updateFilteredItems();
		updateSelectedStacks();
	}

	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		Keyboard.enableRepeatEvents(false);
	}

	/**
	 * Returns a unique string identifier for any ingredient type, or null if unavailable.
	 * Uses StackHelper for ItemStacks, and the generic IngredientRegistry helper for everything else
	 * (e.g. FluidStack via FluidStackHelper.getUniqueId).
	 */
	@Nullable
	private static String getIngredientUid(Object ingredient) {
		if (ingredient instanceof ItemStack) {
			ItemStack stack = (ItemStack) ingredient;
			if (stack.isEmpty()) return null;
			try {
				return Internal.getStackHelper().getUniqueIdentifierForStack(stack);
			} catch (Exception e) {
				return null;
			}
		}
		try {
			@SuppressWarnings("unchecked")
			mezz.jei.api.ingredients.IIngredientHelper<Object> helper =
					(mezz.jei.api.ingredients.IIngredientHelper<Object>)
					Internal.getIngredientRegistry().getIngredientHelper(ingredient);
			return helper.getUniqueId(ingredient);
		} catch (Exception e) {
			return null;
		}
	}

	/** Renders a tooltip for any ingredient type, falling back to ItemStack rendering for items. */
	@SuppressWarnings("unchecked")
	private <T> void renderIngredientTooltip(IIngredientListElement<T> element, int mouseX, int mouseY) {
		try {
			T ingredient = element.getIngredient();
			if (ingredient instanceof ItemStack) {
				renderToolTip((ItemStack) ingredient, mouseX, mouseY);
				return;
			}
			IIngredientRenderer<T> renderer = element.getIngredientRenderer();
			List<String> tooltip = renderer.getTooltip(this.mc, ingredient,
					this.mc.gameSettings.advancedItemTooltips
							? ITooltipFlag.TooltipFlags.ADVANCED
							: ITooltipFlag.TooltipFlags.NORMAL);
			if (!tooltip.isEmpty()) {
				drawHoveringText(tooltip, mouseX, mouseY, renderer.getFontRenderer(this.mc, ingredient));
			}
		} catch (Exception ignored) {
		}
	}

	private void updateFilteredItems() {		if (!Internal.hasIngredientFilter()) {
			filteredItems = Collections.emptyList();
			return;
		}
		IngredientFilter filter = Internal.getIngredientFilter();
		String search = (searchField != null) ? searchField.getText() : "";
		filteredItems = filter.getIngredientList(search);
		leftTotalPages = Math.max(1, (filteredItems.size() + leftItemsPerPage - 1) / leftItemsPerPage);
		if (leftPage >= leftTotalPages) {
			leftPage = leftTotalPages - 1;
		}
	}

	private void updateSelectedStacks() {
		selectedStacks.clear();
		if (!Internal.hasIngredientFilter()) {
			return;
		}
		IngredientFilter filter = Internal.getIngredientFilter();
		// Get the full ingredient list and find elements matching selectedUids (any ingredient type)
		List<IIngredientListElement> all = filter.getIngredientList("");
		for (IIngredientListElement<?> element : all) {
			String uid = getIngredientUid(element.getIngredient());
			if (uid != null && selectedUids.contains(uid)) {
				selectedStacks.add(element);
			}
		}
		rightTotalPages = Math.max(1, (selectedStacks.size() + rightItemsPerPage - 1) / rightItemsPerPage);
		if (rightPage >= rightTotalPages) {
			rightPage = rightTotalPages - 1;
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		switch (button.id) {
			case BTN_SAVE:
				saveAndClose();
				break;
			case BTN_CANCEL:
				this.mc.displayGuiScreen(parentScreen);
				break;
			case BTN_PREV_PAGE:
				leftPage = Math.max(0, leftPage - 1);
				break;
			case BTN_NEXT_PAGE:
				leftPage = Math.min(leftTotalPages - 1, leftPage + 1);
				break;
			case BTN_PREV_SEL_PAGE:
				rightPage = Math.max(0, rightPage - 1);
				break;
			case BTN_NEXT_SEL_PAGE:
				rightPage = Math.min(rightTotalPages - 1, rightPage + 1);
				break;
		}
	}

	private void saveAndClose() {
		if (nameField != null) {
			group.displayName = nameField.getText();
		}
		group.itemUids = new ArrayList<>(selectedUids);

		CustomGroupsConfig customGroupsConfig = Config.getCustomGroupsConfig();
		if (customGroupsConfig != null) {
			customGroupsConfig.updateGroup(group);
			Internal.getCollapsibleEntryRegistry().recollectCustomEntries();
			if (Internal.hasIngredientFilter()) {
				IngredientFilter filter = Internal.getIngredientFilter();
				// Invalidate the filter cache so the next call to getIngredientList
				// rebuilds collapsedListCached with the new custom entries.
				filter.invalidateCache();
				filter.getIngredientList(Config.getFilterText());
				filter.notifyListenersOfChange();
			}
		}
		parentScreen.onEditorClosed();
		this.mc.displayGuiScreen(parentScreen);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();

		int panelDivider = (int) (this.width * 0.65);

		// Name label
		this.fontRenderer.drawStringWithShadow(
			Translator.translateToLocal("jei.gui.collapsible.editor.name") + ":",
			4, 10, 0xFFFFFF);
		if (nameField != null) {
			nameField.drawTextBox();
		}

		// Search field
		if (searchField != null) {
			searchField.drawTextBox();
		}

		// Divider line
		drawVerticalLine(panelDivider, 0, this.height, 0xFF555555);

		// "Selected" header on right panel
		String selHeader = Translator.translateToLocal("jei.gui.collapsible.editor.title");
		this.fontRenderer.drawStringWithShadow(selHeader, panelDivider + 4, 26, 0xCCCCCC);
		String selCount = String.format(Translator.translateToLocal("jei.gui.collapsible.editor.selected"), selectedUids.size());
		this.fontRenderer.drawStringWithShadow(selCount, panelDivider + 4, 36, 0x888888);

		// Draw left grid (all items)
		drawLeftGrid(mouseX, mouseY);

		// Draw right grid (selected items)
		drawRightGrid(mouseX, mouseY);

		// Left page counter
		if (leftTotalPages > 1) {
			int navY = this.height - 18;
			String pageText = (leftPage + 1) + "/" + leftTotalPages;
			int centerX = (4 + panelDivider) / 2;
			this.drawCenteredString(this.fontRenderer, pageText, centerX, navY, 0xAAAAAA);
		}

		// Right page counter
		if (rightTotalPages > 1) {
			int navY = this.height - 18;
			String pageText = (rightPage + 1) + "/" + rightTotalPages;
			int centerX = (panelDivider + 4 + this.width) / 2;
			this.drawCenteredString(this.fontRenderer, pageText, centerX, navY, 0xAAAAAA);
		}

		super.drawScreen(mouseX, mouseY, partialTicks);

		// Draw tooltips last (after buttons)
		drawLeftGridTooltips(mouseX, mouseY);
		drawRightGridTooltips(mouseX, mouseY);
	}

	private void drawLeftGrid(int mouseX, int mouseY) {
		if (filteredItems.isEmpty()) {
			return;
		}
		int startIdx = leftPage * leftItemsPerPage;

		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.enableDepth();

		for (int i = 0; i < leftItemsPerPage && (startIdx + i) < filteredItems.size(); i++) {
			IIngredientListElement<?> element = filteredItems.get(startIdx + i);
			int col = i % leftCols;
			int row = i / leftCols;
			int x = leftGridX + col * ITEM_SIZE;
			int y = leftGridY + row * ITEM_SIZE;

			Object ingredient = element.getIngredient();
			renderIngredient(element, x + 1, y + 1);

			// Green overlay if selected
			String uid = getIngredientUid(ingredient);
			if (uid != null && selectedUids.contains(uid)) {
				RenderHelper.disableStandardItemLighting();
				GlStateManager.disableDepth();
				GlStateManager.colorMask(true, true, true, false);
				GuiUtils.drawGradientRect(0, x, y, x + ITEM_SIZE, y + ITEM_SIZE, 0x4000FF00, 0x4000FF00);
				GlStateManager.colorMask(true, true, true, true);
				GlStateManager.enableDepth();
				RenderHelper.enableGUIStandardItemLighting();
			}

			// Highlight on hover
			if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
				RenderHelper.disableStandardItemLighting();
				GlStateManager.disableDepth();
				GlStateManager.colorMask(true, true, true, false);
				GuiUtils.drawGradientRect(0, x, y, x + ITEM_SIZE, y + ITEM_SIZE, 0x80FFFFFF, 0x80FFFFFF);
				GlStateManager.colorMask(true, true, true, true);
				GlStateManager.enableDepth();
				RenderHelper.enableGUIStandardItemLighting();
			}
		}

		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableDepth();
	}

	private void drawRightGrid(int mouseX, int mouseY) {
		if (selectedStacks.isEmpty()) {
			return;
		}
		int startIdx = rightPage * rightItemsPerPage;

		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.enableDepth();

		for (int i = 0; i < rightItemsPerPage && (startIdx + i) < selectedStacks.size(); i++) {
			IIngredientListElement<?> element = selectedStacks.get(startIdx + i);
			int col = i % rightCols;
			int row = i / rightCols;
			int x = rightGridX + col * ITEM_SIZE;
			int y = rightGridY + row * ITEM_SIZE;

			renderIngredient(element, x + 1, y + 1);

			// Highlight on hover
			if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
				RenderHelper.disableStandardItemLighting();
				GlStateManager.disableDepth();
				GlStateManager.colorMask(true, true, true, false);
				GuiUtils.drawGradientRect(0, x, y, x + ITEM_SIZE, y + ITEM_SIZE, 0x80FF8888, 0x80FF8888);
				GlStateManager.colorMask(true, true, true, true);
				GlStateManager.enableDepth();
				RenderHelper.enableGUIStandardItemLighting();
			}
		}

		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableDepth();
	}

	@SuppressWarnings("unchecked")
	private <T> void renderIngredient(IIngredientListElement<T> element, int x, int y) {
		try {
			IIngredientRenderer<T> renderer = element.getIngredientRenderer();
			T ingredient = element.getIngredient();
			renderer.render(this.mc, x, y, ingredient);
		} catch (Exception ignored) {
		}
	}

	private void drawLeftGridTooltips(int mouseX, int mouseY) {
		if (filteredItems.isEmpty()) {
			return;
		}
		int startIdx = leftPage * leftItemsPerPage;
		for (int i = 0; i < leftItemsPerPage && (startIdx + i) < filteredItems.size(); i++) {
			int col = i % leftCols;
			int row = i / leftCols;
			int x = leftGridX + col * ITEM_SIZE;
			int y = leftGridY + row * ITEM_SIZE;
			if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
				renderIngredientTooltip(filteredItems.get(startIdx + i), mouseX, mouseY);
				return;
			}
		}
	}

	private void drawRightGridTooltips(int mouseX, int mouseY) {
		if (selectedStacks.isEmpty()) {
			return;
		}
		int startIdx = rightPage * rightItemsPerPage;
		for (int i = 0; i < rightItemsPerPage && (startIdx + i) < selectedStacks.size(); i++) {
			int col = i % rightCols;
			int row = i / rightCols;
			int x = rightGridX + col * ITEM_SIZE;
			int y = rightGridY + row * ITEM_SIZE;
			if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
				renderIngredientTooltip(selectedStacks.get(startIdx + i), mouseX, mouseY);
				return;
			}
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		if (nameField != null) {
			nameField.mouseClicked(mouseX, mouseY, mouseButton);
		}
		if (searchField != null) {
			boolean wasSearchFocused = searchField.isFocused();
			searchField.mouseClicked(mouseX, mouseY, mouseButton);
			// Right-click clears search
			if (searchField.isFocused() && mouseButton == 1) {
				searchField.setText("");
				updateFilteredItems();
			}
		}

		// Left grid click: toggle item selection and start drag
		if (mouseButton == 0 && !filteredItems.isEmpty()) {
			int startIdx = leftPage * leftItemsPerPage;
			for (int i = 0; i < leftItemsPerPage && (startIdx + i) < filteredItems.size(); i++) {
				int col = i % leftCols;
				int row = i / leftCols;
				int x = leftGridX + col * ITEM_SIZE;
				int y = leftGridY + row * ITEM_SIZE;
				if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
					IIngredientListElement<?> element = filteredItems.get(startIdx + i);
					String uid = getIngredientUid(element.getIngredient());
					if (uid != null) {
						dragAdding = !selectedUids.contains(uid);
						isDragging = true;
						lastDraggedUid = uid;
						toggleSelectionByUid(uid);
					}
					return;
				}
			}
		}

		// Right grid click: remove from selection
		if (mouseButton == 0 && !selectedStacks.isEmpty()) {
			int startIdx = rightPage * rightItemsPerPage;
			for (int i = 0; i < rightItemsPerPage && (startIdx + i) < selectedStacks.size(); i++) {
				int col = i % rightCols;
				int row = i / rightCols;
				int x = rightGridX + col * ITEM_SIZE;
				int y = rightGridY + row * ITEM_SIZE;
				if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
					IIngredientListElement<?> element = selectedStacks.get(startIdx + i);
					String uid = getIngredientUid(element.getIngredient());
					if (uid != null) removeSelectionByUid(uid);
					return;
				}
			}
		}

		super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state) {
		super.mouseReleased(mouseX, mouseY, state);
		isDragging = false;
		lastDraggedUid = null;
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
		if (!isDragging || clickedMouseButton != 0 || filteredItems.isEmpty()) return;
		int startIdx = leftPage * leftItemsPerPage;
		for (int i = 0; i < leftItemsPerPage && (startIdx + i) < filteredItems.size(); i++) {
			int col = i % leftCols;
			int row = i / leftCols;
			int x = leftGridX + col * ITEM_SIZE;
			int y = leftGridY + row * ITEM_SIZE;
			if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
				String uid = getIngredientUid(filteredItems.get(startIdx + i).getIngredient());
				if (uid != null && !uid.equals(lastDraggedUid)) {
					lastDraggedUid = uid;
					if (dragAdding) {
						if (selectedUids.add(uid)) updateSelectedStacks();
					} else {
						if (selectedUids.remove(uid)) updateSelectedStacks();
					}
				}
				return;
			}
		}
	}

	private void toggleSelectionByUid(String uid) {
		if (selectedUids.contains(uid)) {
			selectedUids.remove(uid);
		} else {
			selectedUids.add(uid);
		}
		updateSelectedStacks();
	}

	private void removeSelectionByUid(String uid) {
		selectedUids.remove(uid);
		updateSelectedStacks();
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (nameField != null && nameField.isFocused()) {
			nameField.textboxKeyTyped(typedChar, keyCode);
			return;
		}
		if (searchField != null && searchField.isFocused()) {
			searchField.textboxKeyTyped(typedChar, keyCode);
			updateFilteredItems();
			return;
		}
		if (keyCode == Keyboard.KEY_ESCAPE) {
			this.mc.displayGuiScreen(parentScreen);
			return;
		}
		super.keyTyped(typedChar, keyCode);
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int scrollDelta = Mouse.getEventDWheel();
		if (scrollDelta != 0) {
			int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
			int panelDivider = (int) (this.width * 0.65);

			if (mouseX < panelDivider) {
				// Scroll left grid
				if (scrollDelta < 0 && leftPage < leftTotalPages - 1) {
					leftPage++;
				} else if (scrollDelta > 0 && leftPage > 0) {
					leftPage--;
				}
			} else {
				// Scroll right grid
				if (scrollDelta < 0 && rightPage < rightTotalPages - 1) {
					rightPage++;
				} else if (scrollDelta > 0 && rightPage > 0) {
					rightPage--;
				}
			}
		}
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		if (nameField != null) {
			nameField.updateCursorCounter();
		}
		if (searchField != null) {
			searchField.updateCursorCounter();
		}
	}
}

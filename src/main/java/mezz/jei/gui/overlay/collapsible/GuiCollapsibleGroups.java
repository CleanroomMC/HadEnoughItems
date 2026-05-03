package mezz.jei.gui.overlay.collapsible;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.config.Config;
import mezz.jei.config.CustomGroupsConfig;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.group.CollapsibleGroup;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.ingredients.group.CollapsedGroupIngredient.GroupSource;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.ingredients.group.CollapsibleGroupRegistry;
import mezz.jei.util.Translator;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * Management screen for collapsible groups.
 * Shows all default and custom groups with toggle/configure/delete controls.
 */
public class GuiCollapsibleGroups extends GuiScreen {

	private static final int CARD_HEIGHT = 84;
	private static final int CARD_PADDING = 4;
	private static final int PREVIEW_SIZE = 16;
	private static final int PREVIEW_COLS = 8;
	private static final int PREVIEW_ROWS = 3;
	// Maximum items fetched per card for the scrollable preview (20 scrollable rows)
	private static final int PREVIEW_FETCH_MAX = PREVIEW_COLS * 20;

	// Dynamic layout — recomputed on each initGui() call so the screen adapts to GUI scale
	private int cardsPerCol;
	private int cardsPerPage;
	private int layoutContentTop;
	private int layoutContentWidth;
	private int layoutContentLeft;
	private int layoutColGap;
	private int layoutColWidth;

	private static final int BTN_BACK = 0;
	private static final int BTN_NEW = 1;
	private static final int BTN_PREV_PAGE = 2;
	private static final int BTN_NEXT_PAGE = 3;
	private static final int BTN_TOGGLE_BASE = 100;
	private static final int BTN_CONFIGURE_BASE = 200;
	private static final int BTN_DELETE_BASE = 300;
	private static final int BTN_DELETE_CONFIRM_BASE = 400;
	private static final int BTN_DELETE_CANCEL_BASE = 500;

	private final GuiScreen parentScreen;
	private final List<GroupCardEntry> cardEntries = new ArrayList<>();
	private int currentPage = 0;
	private int totalPages = 1;
	/** Index into {@code cardEntries} of the custom group awaiting delete confirmation, or -1. */
	private int pendingDeleteIdx = -1;
	@Nullable private IIngredientListElement<?> tooltipElement = null;

	// Drag-to-scroll state for card preview boxes
	private int dragCardAbsIdx = -1;
	private int dragStartMouseY;
	private int dragStartRow;

	public GuiCollapsibleGroups(GuiScreen parentScreen) {
		this.parentScreen = parentScreen;
	}

	private void computeLayout() {
		layoutContentTop = 30;
		layoutColGap = 4;
		// Use up to 90% of screen width, capped at 700 so wide monitors still look reasonable
		layoutContentWidth = Math.min(this.width - 20, Math.max(300, (int) (this.width * 0.9)));
		layoutContentLeft = (this.width - layoutContentWidth) / 2;
		layoutColWidth = (layoutContentWidth - layoutColGap) / 2;
		// Reserve: header (30) + header buttons (24) + nav row (28) + bottom margin (6)
		int availableForCards = this.height - layoutContentTop - 28 - 6;
		cardsPerCol = Math.max(1, availableForCards / (CARD_HEIGHT + CARD_PADDING));
		cardsPerPage = 2 * cardsPerCol;
	}

	@Override
	public void initGui() {
		super.initGui();
		this.buttonList.clear();
		computeLayout();

		// Back button
		this.buttonList.add(new GuiButton(BTN_BACK, layoutContentLeft, 4, 60, 20,
			Translator.translateToLocal("hei.gui.collapsible.back")));

		// New Group button
		this.buttonList.add(new GuiButton(BTN_NEW, layoutContentLeft + layoutContentWidth - 62, 4, 60, 20,
			Translator.translateToLocal("hei.gui.collapsible.newGroup")));

		rebuildCards();
		rebuildPageButtons();
	}

	private void rebuildCards() {
		cardEntries.clear();

		CollapsibleGroupRegistry registry = Internal.getCollapsedGroupRegistry();

		List<CollapsibleGroup> allGroups = new ArrayList<>(registry.getAllGroups().values());
		allGroups.sort(Comparator.comparingInt(g -> sourceOrder(g.getIngredient().getSource())));

		// Fetch the ingredient list once and reuse it across all cards
		@SuppressWarnings({"unchecked", "rawtypes"})
		List<IIngredientListElement<?>> ingredientList = Internal.hasIngredientFilter()
			? (List<IIngredientListElement<?>>) (List) Internal.getIngredientFilter().getIngredientList("")
			: Collections.emptyList();

		// Precompute UIDs once for all ingredients.
		String[] elementUids = new String[ingredientList.size()];
		for (int i = 0; i < ingredientList.size(); i++) {
			IIngredientListElement<?> e = ingredientList.get(i);
			@SuppressWarnings({"unchecked", "rawtypes"})
			String uid = ((mezz.jei.api.ingredients.IIngredientHelper) e.getIngredientHelper()).getUniqueId(e.getIngredient());
			elementUids[i] = uid;
		}

		for (CollapsibleGroup group : allGroups) {
			CollapsedGroupIngredient ingredient = group.getIngredient();
			List<IIngredientListElement<?>> previewItems = new ArrayList<>();
			int itemCount = 0;
			for (int i = 0; i < ingredientList.size(); i++) {
				if (ingredient.matchesUid(elementUids[i])) {
					itemCount++;
					if (previewItems.size() < PREVIEW_FETCH_MAX) {
						previewItems.add(ingredientList.get(i));
					}
				}
			}
			cardEntries.add(new GroupCardEntry(ingredient.getId(), ingredient.getDisplayName(),
				ingredient.getSource(), group.isEnabled(), previewItems, itemCount));
		}

		totalPages = Math.max(1, (cardEntries.size() + cardsPerPage - 1) / cardsPerPage);
		if (currentPage >= totalPages) {
			currentPage = totalPages - 1;
		}
	}

	private static int sourceOrder(GroupSource source) {
		switch (source) {
			case CUSTOM: return 0;
			case MOD: return 1;
			default: return 2;
		}
	}

	private void rebuildPageButtons() {
		// Remove old card-specific and page buttons
		buttonList.removeIf(b -> b.id >= BTN_PREV_PAGE);

		int startIdx = currentPage * cardsPerPage;
		int endIdx = Math.min(startIdx + cardsPerPage, cardEntries.size());

		for (int i = startIdx; i < endIdx; i++) {
			int localIdx = i - startIdx;
			int col = localIdx / cardsPerCol; // 0 = left, 1 = right
			int row = localIdx % cardsPerCol;
			int cardX = layoutContentLeft + col * (layoutColWidth + layoutColGap);
			int cardY = layoutContentTop + row * (CARD_HEIGHT + CARD_PADDING);
			GroupCardEntry card = cardEntries.get(i);

			int btnX = cardX + layoutColWidth - 56;
			int btnY = cardY + 4;

			// Toggle button
			String toggleLabel = card.enabled
				? Translator.translateToLocal("hei.gui.collapsible.enabled")
				: Translator.translateToLocal("hei.gui.collapsible.disabled");
			this.buttonList.add(new GuiButton(BTN_TOGGLE_BASE + i, btnX, btnY, 52, 20, toggleLabel));

			if (card.source == GroupSource.CUSTOM) {
				if (i == pendingDeleteIdx) {
					// Confirm row: ✔ Yes / ✗ No
					this.buttonList.add(new GuiButton(BTN_DELETE_CONFIRM_BASE + i, btnX, btnY + 22, 24, 20,
						"\u2714")); // ✔ checkmark
					this.buttonList.add(new GuiButton(BTN_DELETE_CANCEL_BASE + i, btnX + 26, btnY + 22, 26, 20,
						"\u2716")); // ✗ cancel
				} else {
					// Configure button
					this.buttonList.add(new GuiButton(BTN_CONFIGURE_BASE + i, btnX, btnY + 22, 24, 20,
						"\u270E")); // pencil unicode
					// Delete button
					this.buttonList.add(new GuiButton(BTN_DELETE_BASE + i, btnX + 26, btnY + 22, 26, 20,
						"\u2716")); // cross unicode
				}
			}
		}

		// Page navigation
		if (totalPages > 1) {
			int navY = layoutContentTop + cardsPerCol * (CARD_HEIGHT + CARD_PADDING) + 4;
			this.buttonList.add(new GuiButton(BTN_PREV_PAGE, layoutContentLeft, navY, 20, 20, "<"));
			this.buttonList.add(new GuiButton(BTN_NEXT_PAGE, layoutContentLeft + layoutContentWidth - 20, navY, 20, 20, ">"));
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == BTN_BACK) {
			this.mc.displayGuiScreen(parentScreen);
			return;
		}
		if (button.id == BTN_NEW) {
			String newId = "custom:" + UUID.randomUUID().toString().substring(0, 8);
			CustomGroupsConfig customGroupsConfig = Config.getCustomGroupsConfig();
			if (customGroupsConfig != null) {
				CustomGroupsConfig.CustomGroup newGroup = new CustomGroupsConfig.CustomGroup(newId, "New Group", CollapsedGroupIngredient.BACKGROUND_COLOR_SMOKE, CollapsedGroupIngredient.BORDER_COLOR_SMOKE, new ArrayList<>());
				this.mc.displayGuiScreen(new GuiCustomGroupEditor(this, newGroup));
			}
			return;
		}
		if (button.id == BTN_PREV_PAGE) {
			currentPage = Math.max(0, currentPage - 1);
			pendingDeleteIdx = -1;
			rebuildPageButtons();
			return;
		}
		if (button.id == BTN_NEXT_PAGE) {
			currentPage = Math.min(totalPages - 1, currentPage + 1);
			pendingDeleteIdx = -1;
			rebuildPageButtons();
			return;
		}

		// Toggle
		if (button.id >= BTN_TOGGLE_BASE && button.id < BTN_CONFIGURE_BASE) {
			int idx = button.id - BTN_TOGGLE_BASE;
			if (idx >= 0 && idx < cardEntries.size()) {
				GroupCardEntry card = cardEntries.get(idx);
				card.enabled = !card.enabled;

				Internal.getCollapsedGroupRegistry().setEnabled(card.enabled, card.id);
				Config.saveDisabledGroups(Internal.getCollapsedGroupRegistry().getDisabledGroups());

				if (Internal.hasIngredientFilter()) {
					IngredientFilter filter = Internal.getIngredientFilter();
					filter.invalidateCache();
					filter.getIngredientList(Config.getFilterText());
					filter.notifyListenersOfChange();
				}

				rebuildPageButtons();
			}
			return;
		}

		// Configure
		if (button.id >= BTN_CONFIGURE_BASE && button.id < BTN_DELETE_BASE) {
			int idx = button.id - BTN_CONFIGURE_BASE;
			if (idx >= 0 && idx < cardEntries.size()) {
				GroupCardEntry card = cardEntries.get(idx);
				if (card.source == GroupSource.CUSTOM) {
					CustomGroupsConfig customGroupsConfig = Config.getCustomGroupsConfig();
					if (customGroupsConfig != null) {
						CustomGroupsConfig.CustomGroup group = customGroupsConfig.getGroup(card.id);
						if (group != null) {
							this.mc.displayGuiScreen(new GuiCustomGroupEditor(this, group));
							return;
						}
					}
				}
			}
			return;
		}

		// Delete — first click: arm confirmation
		if (button.id >= BTN_DELETE_BASE && button.id < BTN_DELETE_CONFIRM_BASE) {
			int idx = button.id - BTN_DELETE_BASE;
			if (idx >= 0 && idx < cardEntries.size() && cardEntries.get(idx).source == GroupSource.CUSTOM) {
				pendingDeleteIdx = idx;
				rebuildPageButtons();
			}
			return;
		}

		// Delete confirmed — execute the actual removal
		if (button.id >= BTN_DELETE_CONFIRM_BASE && button.id < BTN_DELETE_CANCEL_BASE) {
			int idx = button.id - BTN_DELETE_CONFIRM_BASE;
			pendingDeleteIdx = -1;
			if (idx >= 0 && idx < cardEntries.size()) {
				GroupCardEntry card = cardEntries.get(idx);
				if (card.source == GroupSource.CUSTOM) {
					CustomGroupsConfig customGroupsConfig = Config.getCustomGroupsConfig();
					if (customGroupsConfig != null) {
						customGroupsConfig.removeGroup(card.id);
						Internal.getCollapsedGroupRegistry().loadCustomGroups();

						if (Internal.hasIngredientFilter()) {
							IngredientFilter filter = Internal.getIngredientFilter();
							filter.invalidateCache();
							filter.getIngredientList(Config.getFilterText());
							filter.notifyListenersOfChange();
						}

						rebuildCards();
						rebuildPageButtons();
					}
				}
			}
			return;
		}

		// Delete cancelled
		if (button.id >= BTN_DELETE_CANCEL_BASE) {
			pendingDeleteIdx = -1;
			rebuildPageButtons();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();

		// Title
		String title = Translator.translateToLocal("hei.gui.collapsible.title");
		this.drawCenteredString(this.fontRenderer, title, this.width / 2, 10, 0xFFFFFF);

		int startIdx = currentPage * cardsPerPage;
		int endIdx = Math.min(startIdx + cardsPerPage, cardEntries.size());

		tooltipElement = null;
		for (int i = startIdx; i < endIdx; i++) {
			int localIdx = i - startIdx;
			int col = localIdx / cardsPerCol;
			int row = localIdx % cardsPerCol;
			int cardX = layoutContentLeft + col * (layoutColWidth + layoutColGap);
			int cardY = layoutContentTop + row * (CARD_HEIGHT + CARD_PADDING);
			GroupCardEntry card = cardEntries.get(i);
			drawCard(card, cardX, cardY, layoutColWidth, mouseX, mouseY);

			// Draw "Delete?" label to the left of the ✔/✗ confirm buttons
			if (i == pendingDeleteIdx && card.source == GroupSource.CUSTOM) {
				int btnX = cardX + layoutColWidth - 56;
				int btnY = cardY + 4;
				String confirmLabel = Translator.translateToLocal("hei.gui.collapsible.confirmDelete");
				int labelX = btnX - this.fontRenderer.getStringWidth(confirmLabel) - 3;
				this.fontRenderer.drawStringWithShadow(confirmLabel, labelX, btnY + 27, 0xFFFF4444);
			}
		}

		// Page counter
		if (totalPages > 1) {
			int navY = layoutContentTop + cardsPerCol * (CARD_HEIGHT + CARD_PADDING) + 4;
			String pageText = (currentPage + 1) + "/" + totalPages;
			this.drawCenteredString(this.fontRenderer, pageText, this.width / 2, navY + 6, 0xAAAAAA);
		}

		// Empty state
		if (cardEntries.isEmpty()) {
			this.drawCenteredString(this.fontRenderer, "No collapsible groups defined", this.width / 2, layoutContentTop + 20, 0x888888);
		}

		super.drawScreen(mouseX, mouseY, partialTicks);

		if (tooltipElement != null) {
			renderIngredientTooltip(tooltipElement, mouseX, mouseY);
		}
	}

	private void drawCard(GroupCardEntry card, int x, int y, int width, int mouseX, int mouseY) {
		// Card background
		int bgColor;
		switch (card.source) {
			case CUSTOM:  bgColor = 0x40336699; break;
			case MOD:     bgColor = 0x40553366; break;
			default:      bgColor = 0x40444444; break;
		}
		drawRect(x, y, x + width, y + CARD_HEIGHT, bgColor);

		// Border
		int borderColor = card.enabled ? 0xFF558855 : 0xFF885555;
		drawHorizontalLine(x, x + width - 1, y, borderColor);
		drawHorizontalLine(x, x + width - 1, y + CARD_HEIGHT - 1, borderColor);
		drawVerticalLine(x, y, y + CARD_HEIGHT - 1, borderColor);
		drawVerticalLine(x + width - 1, y, y + CARD_HEIGHT - 1, borderColor);

		// Group name
		String sourceLabel;
		String sourceColor;
		switch (card.source) {
			case CUSTOM:
				sourceColor = "\u00A7e";
				sourceLabel = Translator.translateToLocal("hei.gui.collapsible.customGroup");
				break;
			case MOD:
				sourceColor = "\u00A7d";
				sourceLabel = Translator.translateToLocal("hei.gui.collapsible.modGroup");
				break;
			default:
				sourceColor = "\u00A77";
				sourceLabel = Translator.translateToLocal("hei.gui.collapsible.defaultGroup");
				break;
		}
		String namePrefix = sourceColor + "[" + sourceLabel + "] \u00A7r";
		this.fontRenderer.drawStringWithShadow(namePrefix + card.displayName, x + 4, y + 4, 0xFFFFFF);

		// Item count
		String countText = String.format(Translator.translateToLocal("hei.gui.collapsible.itemCount"), card.itemCount);
		this.fontRenderer.drawStringWithShadow(countText, x + 4, y + 16, 0xAAAAAA);

		// Scrollable preview grid
		int slotSize = PREVIEW_SIZE + 2;
		int previewX = x + 4;
		int previewY = y + 28;
		int totalRows = (card.previewItems.size() + PREVIEW_COLS - 1) / PREVIEW_COLS;
		int maxScrollRow = Math.max(0, totalRows - PREVIEW_ROWS);
		card.previewScrollRow = Math.max(0, Math.min(card.previewScrollRow, maxScrollRow));
		int scrollRow = card.previewScrollRow;

		int firstItem = scrollRow * PREVIEW_COLS;
		int lastItem = Math.min(card.previewItems.size(), (scrollRow + PREVIEW_ROWS) * PREVIEW_COLS);

		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.enableDepth();
		for (int i = firstItem; i < lastItem; i++) {
			IIngredientListElement<?> element = card.previewItems.get(i);
			int visibleRow = (i / PREVIEW_COLS) - scrollRow;
			int col = i % PREVIEW_COLS;
			int itemX = previewX + col * slotSize;
			int itemY = previewY + visibleRow * slotSize;
			renderIngredient(element, itemX + 1, itemY + 1);
			if (mouseX >= itemX && mouseX < itemX + PREVIEW_SIZE
					&& mouseY >= itemY && mouseY < itemY + PREVIEW_SIZE) {
				tooltipElement = element;
			}
		}
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableDepth();

		// Scrollbar indicator
		if (maxScrollRow > 0) {
			int sbX = previewX + PREVIEW_COLS * slotSize + 2;
			int sbY = previewY;
			int sbH = PREVIEW_ROWS * slotSize;
			drawRect(sbX, sbY, sbX + 3, sbY + sbH, 0x55FFFFFF);
			int thumbH = Math.max(4, sbH * PREVIEW_ROWS / totalRows);
			int thumbY = sbY + (sbH - thumbH) * scrollRow / maxScrollRow;
			drawRect(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xCCFFFFFF);
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int scrollDelta = Mouse.getEventDWheel();
		if (scrollDelta != 0) {
			int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
			int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

			// Check if the cursor is inside any visible card's preview box
			boolean handledByCard = false;
			int slotSize = PREVIEW_SIZE + 2;
			int startIdx = currentPage * cardsPerPage;
			int endIdx = Math.min(startIdx + cardsPerPage, cardEntries.size());
			for (int i = startIdx; i < endIdx; i++) {
				int localIdx = i - startIdx;
				int col = localIdx / cardsPerCol;
				int row = localIdx % cardsPerCol;
				int cardX = layoutContentLeft + col * (layoutColWidth + layoutColGap);
				int cardY = layoutContentTop + row * (CARD_HEIGHT + CARD_PADDING);
				int previewX = cardX + 4;
				int previewY = cardY + 28;
				int previewW = PREVIEW_COLS * slotSize;
				int previewH = PREVIEW_ROWS * slotSize;
				if (mouseX >= previewX && mouseX < previewX + previewW
						&& mouseY >= previewY && mouseY < previewY + previewH) {
					GroupCardEntry card = cardEntries.get(i);
					int totalRows = (card.previewItems.size() + PREVIEW_COLS - 1) / PREVIEW_COLS;
					int maxScrollRow = Math.max(0, totalRows - PREVIEW_ROWS);
					if (maxScrollRow > 0) {
						if (scrollDelta < 0) {
							card.previewScrollRow = Math.min(card.previewScrollRow + 1, maxScrollRow);
						} else {
							card.previewScrollRow = Math.max(card.previewScrollRow - 1, 0);
						}
						handledByCard = true;
					}
					break;
				}
			}

			if (!handledByCard) {
				if (scrollDelta < 0 && currentPage < totalPages - 1) {
					currentPage++;
					rebuildPageButtons();
				} else if (scrollDelta > 0 && currentPage > 0) {
					currentPage--;
					rebuildPageButtons();
				}
			}
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		if (mouseButton == 0) {
			dragCardAbsIdx = -1;
			int slotSize = PREVIEW_SIZE + 2;
			int startIdx = currentPage * cardsPerPage;
			int endIdx = Math.min(startIdx + cardsPerPage, cardEntries.size());
			for (int i = startIdx; i < endIdx; i++) {
				int localIdx = i - startIdx;
				int col = localIdx / cardsPerCol;
				int row = localIdx % cardsPerCol;
				int cardX = layoutContentLeft + col * (layoutColWidth + layoutColGap);
				int cardY = layoutContentTop + row * (CARD_HEIGHT + CARD_PADDING);
				int previewX = cardX + 4;
				int previewY = cardY + 28;
				int previewW = PREVIEW_COLS * slotSize;
				int previewH = PREVIEW_ROWS * slotSize;
				if (mouseX >= previewX && mouseX < previewX + previewW
						&& mouseY >= previewY && mouseY < previewY + previewH) {
					dragCardAbsIdx = i;
					dragStartMouseY = mouseY;
					dragStartRow = cardEntries.get(i).previewScrollRow;
					break;
				}
			}
		}
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
		if (clickedMouseButton == 0 && dragCardAbsIdx >= 0 && dragCardAbsIdx < cardEntries.size()) {
			int slotSize = PREVIEW_SIZE + 2;
			GroupCardEntry card = cardEntries.get(dragCardAbsIdx);
			int totalRows = (card.previewItems.size() + PREVIEW_COLS - 1) / PREVIEW_COLS;
			int maxScrollRow = Math.max(0, totalRows - PREVIEW_ROWS);
			// Dragging up (negative deltaY) scrolls down through items
			int rowDelta = (dragStartMouseY - mouseY) / slotSize;
			card.previewScrollRow = Math.max(0, Math.min(dragStartRow + rowDelta, maxScrollRow));
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state) {
		super.mouseReleased(mouseX, mouseY, state);
		if (state == 0) {
			dragCardAbsIdx = -1;
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == 1) { // ESC
			this.mc.displayGuiScreen(parentScreen);
			return;
		}
		super.keyTyped(typedChar, keyCode);
	}

	@SuppressWarnings("unchecked")
	private <T> void renderIngredient(IIngredientListElement<T> element, int x, int y) {
		try {
			IIngredientRenderer<T> renderer = element.getIngredientRenderer();
			renderer.render(this.mc, x, y, element.getIngredient());
		} catch (Exception ignored) {
		}
	}

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

	private static class GroupCardEntry {
		final String id;
		final String displayName;
		final GroupSource source;
		boolean enabled;
		final List<IIngredientListElement<?>> previewItems;
		final int itemCount;
		int previewScrollRow = 0;

		GroupCardEntry(String id, String displayName, GroupSource source, boolean enabled, List<IIngredientListElement<?>> previewItems, int itemCount) {
			this.id = id;
			this.displayName = displayName;
			this.source = source;
			this.enabled = enabled;
			this.previewItems = previewItems;
			this.itemCount = itemCount;
		}
	}
}

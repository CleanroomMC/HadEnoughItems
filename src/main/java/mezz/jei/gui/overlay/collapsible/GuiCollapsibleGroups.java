package mezz.jei.gui.overlay.collapsible;

import mezz.jei.Internal;
import mezz.jei.config.Config;
import mezz.jei.config.CustomGroupsConfig;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.CollapsedStack;
import mezz.jei.ingredients.CollapsibleEntry;
import mezz.jei.ingredients.CollapsibleEntryRegistry;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * Management screen for collapsible groups.
 * Shows all default and custom groups with toggle/configure/delete controls.
 */
public class GuiCollapsibleGroups extends GuiScreen {

	private static final int CARD_HEIGHT = 60;
	private static final int CARD_PADDING = 4;
	private static final int PREVIEW_SIZE = 16;
	private static final int PREVIEW_COLS = 8;
	private static final int CARDS_PER_COL = 3;
	private static final int CARDS_PER_PAGE = 6; // 2 columns × 3 rows

	private static final int BTN_BACK = 0;
	private static final int BTN_NEW = 1;
	private static final int BTN_PREV_PAGE = 2;
	private static final int BTN_NEXT_PAGE = 3;
	private static final int BTN_TOGGLE_BASE = 100;
	private static final int BTN_CONFIGURE_BASE = 200;
	private static final int BTN_DELETE_BASE = 300;

	private final GuiScreen parentScreen;
	private final List<GroupCardEntry> cardEntries = new ArrayList<>();
	private int currentPage = 0;
	private int totalPages = 1;

	public GuiCollapsibleGroups(GuiScreen parentScreen) {
		this.parentScreen = parentScreen;
	}

	@Override
	public void initGui() {
		super.initGui();
		this.buttonList.clear();

		// Back button
		this.buttonList.add(new GuiButton(BTN_BACK, 4, 4, 60, 20,
			Translator.translateToLocal("jei.gui.collapsible.back")));

		// New Group button
		this.buttonList.add(new GuiButton(BTN_NEW, this.width - 64, 4, 60, 20,
			"+ " + Translator.translateToLocal("jei.gui.collapsible.newGroup")));

		rebuildCards();
		rebuildPageButtons();
	}

	private void rebuildCards() {
		cardEntries.clear();

		CollapsibleEntryRegistry registry = Internal.getCollapsibleEntryRegistry();

		// Custom groups come first (like REI)
		for (CollapsibleEntry entry : registry.getCustomEntries()) {
			List<ItemStack> previewItems = getPreviewItems(entry);
			int itemCount = getMatchedItemCount(entry);
			cardEntries.add(new GroupCardEntry(entry.getId(), entry.getDisplayName(), true,
				!registry.getDisabledGroups().contains(entry.getId()), previewItems, itemCount));
		}

		// Default groups
		for (CollapsibleEntry entry : registry.getEntries()) {
			List<ItemStack> previewItems = getPreviewItems(entry);
			int itemCount = getMatchedItemCount(entry);
			cardEntries.add(new GroupCardEntry(entry.getId(), entry.getDisplayName(), false,
				!registry.getDisabledGroups().contains(entry.getId()), previewItems, itemCount));
		}

		totalPages = Math.max(1, (cardEntries.size() + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE);
		if (currentPage >= totalPages) {
			currentPage = totalPages - 1;
		}
	}

	private void rebuildPageButtons() {
		// Remove old card-specific and page buttons
		buttonList.removeIf(b -> b.id >= BTN_PREV_PAGE);

		int contentTop = 30;
		int contentWidth = Math.min(this.width - 10, 480);
		int contentLeft = (this.width - contentWidth) / 2;
		int colGap = 4;
		int colWidth = (contentWidth - colGap) / 2;

		int startIdx = currentPage * CARDS_PER_PAGE;
		int endIdx = Math.min(startIdx + CARDS_PER_PAGE, cardEntries.size());

		for (int i = startIdx; i < endIdx; i++) {
			int localIdx = i - startIdx;
			int col = localIdx / CARDS_PER_COL; // 0 = left, 1 = right
			int row = localIdx % CARDS_PER_COL;
			int cardX = contentLeft + col * (colWidth + colGap);
			int cardY = contentTop + row * (CARD_HEIGHT + CARD_PADDING);
			GroupCardEntry card = cardEntries.get(i);

			int btnX = cardX + colWidth - 56;
			int btnY = cardY + 4;

			// Toggle button
			String toggleLabel = card.enabled
				? Translator.translateToLocal("jei.gui.collapsible.enabled")
				: Translator.translateToLocal("jei.gui.collapsible.disabled");
			this.buttonList.add(new GuiButton(BTN_TOGGLE_BASE + i, btnX, btnY, 52, 14, toggleLabel));

			if (card.isCustom) {
				// Configure button
				this.buttonList.add(new GuiButton(BTN_CONFIGURE_BASE + i, btnX, btnY + 16, 24, 14,
					"\u270E")); // pencil unicode
				// Delete button
				this.buttonList.add(new GuiButton(BTN_DELETE_BASE + i, btnX + 26, btnY + 16, 26, 14,
					"\u2716")); // cross unicode
			}
		}

		// Page navigation
		if (totalPages > 1) {
			int navY = contentTop + CARDS_PER_COL * (CARD_HEIGHT + CARD_PADDING) + 4;
			this.buttonList.add(new GuiButton(BTN_PREV_PAGE, contentLeft, navY, 40, 20, "<"));
			this.buttonList.add(new GuiButton(BTN_NEXT_PAGE, contentLeft + contentWidth - 40, navY, 40, 20, ">"));
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
				CustomGroupsConfig.CustomGroup newGroup = new CustomGroupsConfig.CustomGroup(newId, "New Group", new ArrayList<>());
				customGroupsConfig.addGroup(newGroup);
				Internal.getCollapsibleEntryRegistry().recollectCustomEntries();
				this.mc.displayGuiScreen(new GuiCustomGroupEditor(this, newGroup));
			}
			return;
		}
		if (button.id == BTN_PREV_PAGE) {
			currentPage = Math.max(0, currentPage - 1);
			rebuildPageButtons();
			return;
		}
		if (button.id == BTN_NEXT_PAGE) {
			currentPage = Math.min(totalPages - 1, currentPage + 1);
			rebuildPageButtons();
			return;
		}

		// Toggle
		if (button.id >= BTN_TOGGLE_BASE && button.id < BTN_CONFIGURE_BASE) {
			int idx = button.id - BTN_TOGGLE_BASE;
			if (idx >= 0 && idx < cardEntries.size()) {
				GroupCardEntry card = cardEntries.get(idx);
				card.enabled = !card.enabled;

				CollapsibleEntryRegistry registry = Internal.getCollapsibleEntryRegistry();
				Set<String> disabled = new HashSet<>(registry.getDisabledGroups());
				if (card.enabled) {
					disabled.remove(card.id);
				} else {
					disabled.add(card.id);
				}
				registry.setDisabledGroups(disabled);
				Config.saveDisabledGroups(disabled);

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
				if (card.isCustom) {
					CustomGroupsConfig customGroupsConfig = Config.getCustomGroupsConfig();
					if (customGroupsConfig != null) {
						for (CustomGroupsConfig.CustomGroup group : customGroupsConfig.getCustomGroups()) {
							if (group.id.equals(card.id)) {
								this.mc.displayGuiScreen(new GuiCustomGroupEditor(this, group));
								return;
							}
						}
					}
				}
			}
			return;
		}

		// Delete
		if (button.id >= BTN_DELETE_BASE) {
			int idx = button.id - BTN_DELETE_BASE;
			if (idx >= 0 && idx < cardEntries.size()) {
				GroupCardEntry card = cardEntries.get(idx);
				if (card.isCustom) {
					CustomGroupsConfig customGroupsConfig = Config.getCustomGroupsConfig();
					if (customGroupsConfig != null) {
						customGroupsConfig.removeGroup(card.id);
						Internal.getCollapsibleEntryRegistry().recollectCustomEntries();

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
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();

		// Title
		String title = Translator.translateToLocal("jei.gui.collapsible.title");
		this.drawCenteredString(this.fontRenderer, title, this.width / 2, 10, 0xFFFFFF);

		int contentWidth = Math.min(this.width - 10, 480);
		int contentLeft = (this.width - contentWidth) / 2;
		int contentTop = 30;
		int colGap = 4;
		int colWidth = (contentWidth - colGap) / 2;

		int startIdx = currentPage * CARDS_PER_PAGE;
		int endIdx = Math.min(startIdx + CARDS_PER_PAGE, cardEntries.size());

		for (int i = startIdx; i < endIdx; i++) {
			int localIdx = i - startIdx;
			int col = localIdx / CARDS_PER_COL;
			int row = localIdx % CARDS_PER_COL;
			int cardX = contentLeft + col * (colWidth + colGap);
			int cardY = contentTop + row * (CARD_HEIGHT + CARD_PADDING);
			GroupCardEntry card = cardEntries.get(i);
			drawCard(card, cardX, cardY, colWidth);
		}

		// Page counter
		if (totalPages > 1) {
			int navY = contentTop + CARDS_PER_COL * (CARD_HEIGHT + CARD_PADDING) + 4;
			String pageText = (currentPage + 1) + "/" + totalPages;
			this.drawCenteredString(this.fontRenderer, pageText, this.width / 2, navY + 6, 0xAAAAAA);
		}

		// Empty state
		if (cardEntries.isEmpty()) {
			this.drawCenteredString(this.fontRenderer, "No collapsible groups defined", this.width / 2, contentTop + 20, 0x888888);
		}

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private void drawCard(GroupCardEntry card, int x, int y, int width) {
		// Card background
		int bgColor = card.isCustom ? 0x40336699 : 0x40444444;
		drawRect(x, y, x + width, y + CARD_HEIGHT, bgColor);

		// Border
		int borderColor = card.enabled ? 0xFF558855 : 0xFF885555;
		drawHorizontalLine(x, x + width - 1, y, borderColor);
		drawHorizontalLine(x, x + width - 1, y + CARD_HEIGHT - 1, borderColor);
		drawVerticalLine(x, y, y + CARD_HEIGHT - 1, borderColor);
		drawVerticalLine(x + width - 1, y, y + CARD_HEIGHT - 1, borderColor);

		// Group name
		String namePrefix = card.isCustom
			? "\u00A7e[" + Translator.translateToLocal("jei.gui.collapsible.customGroup") + "] \u00A7r"
			: "\u00A77[" + Translator.translateToLocal("jei.gui.collapsible.defaultGroup") + "] \u00A7r";
		this.fontRenderer.drawStringWithShadow(namePrefix + card.displayName, x + 4, y + 4, 0xFFFFFF);

		// Item count
		String countText = String.format(Translator.translateToLocal("jei.gui.collapsible.itemCount"), card.itemCount);
		this.fontRenderer.drawStringWithShadow(countText, x + 4, y + 16, 0xAAAAAA);

		// Preview items
		int previewY = y + 28;
		int previewX = x + 4;

		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.enableDepth();
		int count = Math.min(card.previewItems.size(), PREVIEW_COLS);
		for (int i = 0; i < count; i++) {
			ItemStack stack = card.previewItems.get(i);
			int itemX = previewX + i * (PREVIEW_SIZE + 2);
			this.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, itemX, previewY);
		}
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableDepth();

		if (card.itemCount > PREVIEW_COLS) {
			String moreText = "+" + (card.itemCount - PREVIEW_COLS);
			int moreX = previewX + PREVIEW_COLS * (PREVIEW_SIZE + 2) + 2;
			this.fontRenderer.drawStringWithShadow(moreText, moreX, previewY + 4, 0x888888);
		}

		// ID text (small, dimmed)
		String idText = "\u00A78" + card.id;
		this.fontRenderer.drawStringWithShadow(idText, x + 4, y + CARD_HEIGHT - 12, 0x666666);
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int scrollDelta = Mouse.getEventDWheel();
		if (scrollDelta != 0) {
			if (scrollDelta < 0 && currentPage < totalPages - 1) {
				currentPage++;
				rebuildPageButtons();
			} else if (scrollDelta > 0 && currentPage > 0) {
				currentPage--;
				rebuildPageButtons();
			}
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

	/**
	 * Get up to 16 preview ItemStacks for a collapsible entry by scanning the ingredient list.
	 */
	private List<ItemStack> getPreviewItems(CollapsibleEntry entry) {
		List<ItemStack> items = new ArrayList<>();
		if (!Internal.hasIngredientFilter()) {
			return items;
		}
		IngredientFilter filter = Internal.getIngredientFilter();
		List<IIngredientListElement> ingredientList = filter.getIngredientList();
		for (IIngredientListElement<?> element : ingredientList) {
			if (entry.matches(element)) {
				Object ingredient = element.getIngredient();
				if (ingredient instanceof ItemStack) {
					items.add((ItemStack) ingredient);
					if (items.size() >= 16) {
						break;
					}
				}
			}
		}
		return items;
	}

	/**
	 * Count matched items for display.
	 */
	private int getMatchedItemCount(CollapsibleEntry entry) {
		if (!Internal.hasIngredientFilter()) {
			return 0;
		}
		IngredientFilter filter = Internal.getIngredientFilter();
		List<IIngredientListElement> ingredientList = filter.getIngredientList();
		int count = 0;
		for (IIngredientListElement<?> element : ingredientList) {
			if (entry.matches(element)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Called when returning from the editor screen to refresh the card list.
	 */
	public void onEditorClosed() {
		rebuildCards();
		rebuildPageButtons();
	}

	private static class GroupCardEntry {
		final String id;
		final String displayName;
		final boolean isCustom;
		boolean enabled;
		final List<ItemStack> previewItems;
		final int itemCount;

		GroupCardEntry(String id, String displayName, boolean isCustom, boolean enabled, List<ItemStack> previewItems, int itemCount) {
			this.id = id;
			this.displayName = displayName;
			this.isCustom = isCustom;
			this.enabled = enabled;
			this.previewItems = previewItems;
			this.itemCount = itemCount;
		}
	}
}

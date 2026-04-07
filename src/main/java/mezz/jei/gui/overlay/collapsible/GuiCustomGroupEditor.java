package mezz.jei.gui.overlay.collapsible;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.config.Config;
import mezz.jei.config.CustomGroupsConfig;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.util.Translator;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
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

	// Persisted across editor instances, reopening restores last search and page.
	private static String savedSearchText = "";
	private static int savedFirstItemIndex = 0;

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
	// Maps each right-panel element to its stored UID (exact or wildcard ending in ":*")
	private final Map<IIngredientListElement<?>, String> selectedStackToStoredUid = new HashMap<>();

	// Index of OTHER custom groups — used to tint items that already belong to a different group.
	// Built once in initGui(); key = exact UID, value = list of display names.
	private Map<String, List<String>> otherGroupExactUids = new HashMap<>();
	// [prefix, displayName] pairs for wildcard entries in other groups
	private List<String[]> otherGroupWildcardPrefixes = new ArrayList<>();

	// Drag-select state
	private boolean isDragging = false;
	private boolean dragAdding = false;
	/** True when the current drag was started with Ctrl held — uses wildcard UIDs. */
	private boolean dragWildcard = false;
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
		nameField = new GuiTextField(10, this.fontRenderer, 62, 6, panelDivider - 70, 16);
		nameField.setMaxStringLength(40);
		nameField.setText(group.displayName != null ? group.displayName : "");

		// Search field — restore saved text so the user's last search carries over
		searchField = new GuiTextField(11, this.fontRenderer, 4, topBarHeight - 18, panelDivider - 12, 14);
		searchField.setMaxStringLength(128);
		searchField.setText(savedSearchText);

		// Save & Cancel buttons
		this.buttonList.add(new GuiButton(BTN_SAVE, panelDivider + 4, 4, 50, 20,
			Translator.translateToLocal("hei.gui.collapsible.editor.save")));
		this.buttonList.add(new GuiButton(BTN_CANCEL, panelDivider + 58, 4, 50, 20,
			Translator.translateToLocal("hei.gui.collapsible.back")));

		// Calculate left grid layout
		int leftWidth = panelDivider - 8;
		int leftHeight = this.height - topBarHeight - 26; // room for page nav
		leftCols = Math.max(1, leftWidth / ITEM_SIZE);
		leftRows = Math.max(1, leftHeight / ITEM_SIZE);
		leftGridX = (panelDivider - 4 - leftCols * ITEM_SIZE) / 2;
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
		this.buttonList.add(new GuiButton(BTN_PREV_PAGE, 4, leftNavY, 30, 20, "<"));
		this.buttonList.add(new GuiButton(BTN_NEXT_PAGE, panelDivider - 34, leftNavY, 30, 20, ">"));

		// Page nav buttons for right grid
		this.buttonList.add(new GuiButton(BTN_PREV_SEL_PAGE, panelDivider + 4, leftNavY, 30, 20, "<"));
		this.buttonList.add(new GuiButton(BTN_NEXT_SEL_PAGE, this.width - 34, leftNavY, 30, 20, ">"));

		updateFilteredItems();
		leftPage = Math.max(0, Math.min(savedFirstItemIndex / leftItemsPerPage, leftTotalPages - 1));
		updateSelectedStacks();
		buildOtherGroupIndex();
	}

	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		Keyboard.enableRepeatEvents(false);
		// Persist the current search text and the absolute index of the first visible item.
		savedSearchText = (searchField != null) ? searchField.getText() : "";
		savedFirstItemIndex = leftPage * leftItemsPerPage;
	}

	private static String getIngredientUid(Object ingredient) {
		return Internal.getIngredientRegistry().getIngredientHelper(ingredient).getUniqueId(ingredient);
	}

	private static String getIngredientWildcardUid(Object ingredient) {
		IIngredientHelper helper = Internal.getIngredientRegistry().getIngredientHelper(ingredient);
		String wildcardId = helper.getWildcardId(ingredient);
		return wildcardId.equals(getIngredientUid(ingredient)) ? null : wildcardId + ":*";
	}

	/**
	 * Returns true if the given normal UID is covered by any selected entry -
	 * either an exact match, or a wildcard prefix match (stored as "prefix:*").
	 */
	private boolean isUidSelected(String normalUid) {
		return selectedUids.contains(normalUid) || findCoveringWildcard(normalUid) != null;
	}

	/**
	 * Builds a reverse index of all other custom groups for fast membership lookup
	 */
	private void buildOtherGroupIndex() {
		otherGroupExactUids.clear();
		otherGroupWildcardPrefixes.clear();
		CustomGroupsConfig cfg = Config.getCustomGroupsConfig();
		if (cfg == null) return;
		for (CustomGroupsConfig.CustomGroup g : cfg.getCustomGroups()) {
			if (g.id.equals(group.id) || g.itemUids == null) continue;
			String name = (g.displayName != null && !g.displayName.isEmpty()) ? g.displayName : g.id;
			for (String uid : g.itemUids) {
				if (uid.endsWith(":*")) {
					otherGroupWildcardPrefixes.add(new String[]{uid.substring(0, uid.length() - 2), name});
				} else {
					otherGroupExactUids.computeIfAbsent(uid, k -> new ArrayList<>()).add(name);
				}
			}
		}
	}

	/** Returns the names of other custom groups that contain the given normal UID, or an empty list. */
	private List<String> getOtherGroupNames(String normalUid) {
		List<String> names = new ArrayList<>(otherGroupExactUids.getOrDefault(normalUid, Collections.emptyList()));
		for (String[] entry : otherGroupWildcardPrefixes) {
			if (normalUid.equals(entry[0]) || normalUid.startsWith(entry[0] + ":")) {
				names.add(entry[1]);
			}
		}
		return names;
	}

	private String findCoveringWildcard(String normalUid) {
		for (String stored : selectedUids) {
			if (stored.endsWith(":*")) {
				String prefix = stored.substring(0, stored.length() - 2);
				if (normalUid.equals(prefix) || normalUid.startsWith(prefix + ":")) {
					return prefix + ":*";
				}
			}
		}
		return null;
	}

	/**
	 * Collects all exact UIDs from the full ingredient list that share the given wildcard prefix.
	 * Used for auto-promote checks and wildcard decomposition.
	 */
	private List<String> getSiblingUids(String wildcardUid) {
		if (!wildcardUid.endsWith(":*") || !Internal.hasIngredientFilter()) {
			return Collections.emptyList();
		}
		String prefix = wildcardUid.substring(0, wildcardUid.length() - 2);
		List<IIngredientListElement> all = Internal.getIngredientFilter().getIngredientList("");
		List<String> result = new ArrayList<>();
		for (IIngredientListElement<?> elem : all) {
			String uid = getIngredientUid(elem.getIngredient());
			if (uid != null && (uid.equals(prefix) || uid.startsWith(prefix + ":"))) {
				result.add(uid);
			}
		}
		return result;
	}

	/**
	 * Decomposes a wildcard entry into individual exact entries, excluding one item
	 * (the one the user just clicked to remove).
	 */
	private void decomposeWildcard(String wildcardUid, @Nullable String excludeUid) {
		selectedUids.remove(wildcardUid);
		for (String sibling : getSiblingUids(wildcardUid)) {
			if (!sibling.equals(excludeUid)) {
				selectedUids.add(sibling);
			}
		}
		updateSelectedStacks();
	}

	/**
	 * After adding an exact UID, checks if every meta variant of that item is now individually
	 * selected. If so, replaces them all with a single wildcard entry.
	 */
	private void maybePromoteToWildcard(Object ingredient, String addedUid) {
		String wildcardUid = getIngredientWildcardUid(ingredient);
		if (wildcardUid == null || !wildcardUid.endsWith(":*")) {
			return;
		}
		List<String> siblings = getSiblingUids(wildcardUid);
		if (siblings.isEmpty()) {
			return;
		}
		for (String sibling : siblings) {
			if (!selectedUids.contains(sibling)) {
				return;
			}
		}
		// All variants are individually selected — promote to a single wildcard entry
		siblings.forEach(selectedUids::remove);
		selectedUids.add(wildcardUid);
		updateSelectedStacks();
	}

	/** Returns a mutable list of tooltip lines for any ingredient element. */
	private <T> List<String> getIngredientTooltipLines(IIngredientListElement<T> element) {
		try {
			T ingredient = element.getIngredient();
			ITooltipFlag flag = mc.gameSettings.advancedItemTooltips
					? ITooltipFlag.TooltipFlags.ADVANCED
					: ITooltipFlag.TooltipFlags.NORMAL;
			return new ArrayList<>(element.getIngredientRenderer().getTooltip(mc, ingredient, flag));
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	private void updateFilteredItems() {
		if (!Internal.hasIngredientFilter()) {
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
		selectedStackToStoredUid.clear();
		if (!Internal.hasIngredientFilter()) {
			return;
		}
		IngredientFilter filter = Internal.getIngredientFilter();
		List<IIngredientListElement> all = filter.getIngredientList("");

		// Precompute wildcard prefix → stored UID for O(n) matching
		Map<String, String> wildcardPrefixToStored = new LinkedHashMap<>();
		for (String uid : selectedUids) {
			if (uid.endsWith(":*")) {
				wildcardPrefixToStored.put(uid.substring(0, uid.length() - 2), uid);
			}
		}

		for (IIngredientListElement<?> element : all) {
			String uid = getIngredientUid(element.getIngredient());
			if (selectedUids.contains(uid)) {
				// Exact match
				selectedStacks.add(element);
				selectedStackToStoredUid.put(element, uid);
			} else if (!wildcardPrefixToStored.isEmpty()) {
				// Wildcard prefix match — show ALL matching elements
				for (Map.Entry<String, String> entry : wildcardPrefixToStored.entrySet()) {
					String prefix = entry.getKey();
					String storedUid = entry.getValue();
					if (uid.equals(prefix) || uid.startsWith(prefix + ":")) {
						selectedStacks.add(element);
						selectedStackToStoredUid.put(element, storedUid);
						break;
					}
				}
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
			Internal.getCollapsedGroupRegistry().loadCustomGroups();
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
			Translator.translateToLocal("hei.gui.collapsible.editor.name") + ":",
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
		String selHeader = Translator.translateToLocal("hei.gui.collapsible.editor.title");
		this.fontRenderer.drawStringWithShadow(selHeader, panelDivider + 4, 26, 0xCCCCCC);
		String selCount = String.format(Translator.translateToLocal("hei.gui.collapsible.editor.selected"), selectedUids.size());
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

			// Orange tint if this item belongs to another custom group
			String uid = getIngredientUid(ingredient);
			if (!getOtherGroupNames(uid).isEmpty()) {
				RenderHelper.disableStandardItemLighting();
				GlStateManager.disableDepth();
				GlStateManager.colorMask(true, true, true, false);
				GuiUtils.drawGradientRect(0, x, y, x + ITEM_SIZE, y + ITEM_SIZE, 0x40FF8800, 0x40FF8800);
				GlStateManager.colorMask(true, true, true, true);
				GlStateManager.enableDepth();
				RenderHelper.enableGUIStandardItemLighting();
			}

			// Green overlay if selected (exact or wildcard)
			boolean selected = isUidSelected(uid);
			if (selected) {
				RenderHelper.disableStandardItemLighting();
				GlStateManager.disableDepth();
				GlStateManager.colorMask(true, true, true, false);
				GuiUtils.drawGradientRect(0, x, y, x + ITEM_SIZE, y + ITEM_SIZE, 0x4000FF00, 0x4000FF00);
				GlStateManager.colorMask(true, true, true, true);
				// "*" badge for wildcard-matched items (covered by a stored ":*" uid, not exact)
				if (!selectedUids.contains(uid)) {
					fontRenderer.drawStringWithShadow("*", x + 1, y + 1, 0xFFAA00);
				}
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

			// "*" badge if this is a wildcard representative entry
			String storedUid = selectedStackToStoredUid.get(element);
			if (storedUid != null && storedUid.endsWith(":*")) {
				RenderHelper.disableStandardItemLighting();
				GlStateManager.disableDepth();
				fontRenderer.drawStringWithShadow("*", x + 1, y + 1, 0xFFAA00);
				GlStateManager.enableDepth();
				RenderHelper.enableGUIStandardItemLighting();
			}

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
				IIngredientListElement<?> element = filteredItems.get(startIdx + i);
				List<String> lines = getIngredientTooltipLines(element);
				if (element.getIngredient() instanceof ItemStack) {
					boolean alreadySelected = isUidSelected(getIngredientUid(element.getIngredient()));
					if (alreadySelected) {
						lines.add(TextFormatting.GOLD + "Ctrl+Click: Remove all variants");
					} else {
						lines.add(TextFormatting.GOLD + "Ctrl+Click: Select all variants (Wildcard)");
					}
				}
				List<String> otherGroups = getOtherGroupNames(getIngredientUid(element.getIngredient()));
				if (!otherGroups.isEmpty()) {
					lines.add(TextFormatting.GOLD + "In group" + (otherGroups.size() > 1 ? "s" : "") + ": "
							+ String.join(", ", otherGroups));
				}
				if (!lines.isEmpty()) {
					drawHoveringText(lines, mouseX, mouseY, mc.fontRenderer);
				}
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
				IIngredientListElement<?> element = selectedStacks.get(startIdx + i);
				String storedUid = selectedStackToStoredUid.get(element);
				List<String> lines = getIngredientTooltipLines(element);
				if (storedUid != null && storedUid.endsWith(":*")) {
					lines.add(TextFormatting.GOLD + "Wildcard (*)");
				}
				if (!lines.isEmpty()) {
					drawHoveringText(lines, mouseX, mouseY, mc.fontRenderer);
				}
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
			searchField.mouseClicked(mouseX, mouseY, mouseButton);
			// Right-click clears search
			if (searchField.isFocused() && mouseButton == 1) {
				searchField.setText("");
				leftPage = 0;
				updateFilteredItems();
			}
		}

		// Left grid click: toggle item selection and start drag
		// Ctrl+Click: adds a wildcard UID, or removes entire family if item is already selected
		// Normal click: adds/removes exact UID, with auto-promote and wildcard-decompose support
		if (mouseButton == 0 && !filteredItems.isEmpty()) {
			int startIdx = leftPage * leftItemsPerPage;
			for (int i = 0; i < leftItemsPerPage && (startIdx + i) < filteredItems.size(); i++) {
				int col = i % leftCols;
				int row = i / leftCols;
				int x = leftGridX + col * ITEM_SIZE;
				int y = leftGridY + row * ITEM_SIZE;
				if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
					IIngredientListElement<?> element = filteredItems.get(startIdx + i);
					boolean ctrl = isCtrlKeyDown();
					String exactUid = getIngredientUid(element.getIngredient());
					if (ctrl) {
						if (isUidSelected(exactUid)) {
							// Ctrl+Click on a selected item → remove the entire family (all meta variants)
							String familyWildcard = getIngredientWildcardUid(element.getIngredient());
							if (familyWildcard != null && familyWildcard.endsWith(":*")) {
								String prefix = familyWildcard.substring(0, familyWildcard.length() - 2);
								selectedUids.remove(familyWildcard);
								selectedUids.removeIf(existing ->
										existing.equals(prefix) || existing.startsWith(prefix + ":"));
							} else {
								selectedUids.remove(exactUid);
							}
							dragAdding = false;
							dragWildcard = true;
							isDragging = true;
							lastDraggedUid = exactUid;
							updateSelectedStacks();
						} else {
							// Ctrl+Click on unselected item → add wildcard, remove any exact UIDs it already covers
							String wildcardUid = getIngredientWildcardUid(element.getIngredient());
							if (wildcardUid == null) wildcardUid = exactUid;
							if (wildcardUid.endsWith(":*")) {
								String prefix = wildcardUid.substring(0, wildcardUid.length() - 2);
								selectedUids.removeIf(existing ->
										existing.equals(prefix) || existing.startsWith(prefix + ":"));
							}
							dragAdding = true;
							dragWildcard = true;
							isDragging = true;
							lastDraggedUid = wildcardUid;
							selectedUids.add(wildcardUid);
							updateSelectedStacks();
						}
					} else {
						// Normal click
						if (!selectedUids.contains(exactUid)) {
							// Item is covered by a wildcard → decompose to exact entries minus the clicked one
							String coveringWildcard = findCoveringWildcard(exactUid);
							if (coveringWildcard != null) {
								decomposeWildcard(coveringWildcard, exactUid);
								dragAdding = false;
								dragWildcard = false;
								isDragging = true;
								lastDraggedUid = exactUid;
								return;
							}
						}
						boolean adding = !selectedUids.contains(exactUid);
						dragAdding = adding;
						dragWildcard = false;
						isDragging = true;
						lastDraggedUid = exactUid;
						toggleSelectionByUid(exactUid);
						// Auto-promote: if all meta variants are now individually selected, consolidate to a wildcard
						if (adding) {
							maybePromoteToWildcard(element.getIngredient(), exactUid);
						}
					}
					return;
				}
			}
		}

		// Right grid click: remove from selection.
		// Ctrl+Click: wildcard removal — removes the entire family (wildcard entry + all exact siblings).
		// Normal click on a wildcard entry: decomposes it, removing only the clicked item.
		// Normal click on an exact entry: removes just that item.
		if (mouseButton == 0 && !selectedStacks.isEmpty()) {
			int startIdx = rightPage * rightItemsPerPage;
			for (int i = 0; i < rightItemsPerPage && (startIdx + i) < selectedStacks.size(); i++) {
				int col = i % rightCols;
				int row = i / rightCols;
				int x = rightGridX + col * ITEM_SIZE;
				int y = rightGridY + row * ITEM_SIZE;
				if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
					IIngredientListElement<?> element = selectedStacks.get(startIdx + i);
					String storedUid = selectedStackToStoredUid.get(element);
					if (storedUid != null) {
						boolean ctrl = isCtrlKeyDown();
						if (ctrl) {
							// Ctrl+Click — remove all variants regardless of exact/wildcard storage
							String familyWildcard = getIngredientWildcardUid(element.getIngredient());
							if (familyWildcard != null && familyWildcard.endsWith(":*")) {
								String prefix = familyWildcard.substring(0, familyWildcard.length() - 2);
								selectedUids.remove(familyWildcard);
								selectedUids.removeIf(existing ->
										existing.equals(prefix) || existing.startsWith(prefix + ":"));
								updateSelectedStacks();
							} else {
								removeSelectionByUid(storedUid);
							}
						} else if (storedUid.endsWith(":*")) {
							// Normal click on a wildcard entry — decompose, removing only this item
							String exactUid = getIngredientUid(element.getIngredient());
							decomposeWildcard(storedUid, exactUid);
						} else {
							removeSelectionByUid(storedUid);
						}
					}
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
		dragWildcard = false;
		lastDraggedUid = null;
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
		// Wildcard selections are single-click only — no drag expansion
		if (!isDragging || clickedMouseButton != 0 || filteredItems.isEmpty() || dragWildcard) return;
		int startIdx = leftPage * leftItemsPerPage;
		for (int i = 0; i < leftItemsPerPage && (startIdx + i) < filteredItems.size(); i++) {
			int col = i % leftCols;
			int row = i / leftCols;
			int x = leftGridX + col * ITEM_SIZE;
			int y = leftGridY + row * ITEM_SIZE;
			if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
				IIngredientListElement<?> elem = filteredItems.get(startIdx + i);
				String uid = getIngredientUid(elem.getIngredient());
				if (uid != null && !uid.equals(lastDraggedUid)) {
					lastDraggedUid = uid;
					if (dragAdding) {
						// Skip items already covered by an exact or wildcard selection
						if (!isUidSelected(uid) && selectedUids.add(uid)) {
							updateSelectedStacks();
							// Auto-promote: if all variants of this item are selected, consolidate to a wildcard
							maybePromoteToWildcard(elem.getIngredient(), uid);
						}
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
			String before = searchField.getText();
			searchField.textboxKeyTyped(typedChar, keyCode);
			if (!searchField.getText().equals(before)) {
				leftPage = 0;
			}
			updateFilteredItems();
			return;
		}
		// Ctrl+F focuses the search field
		if (keyCode == Keyboard.KEY_F && isCtrlKeyDown()) {
			if (searchField != null) {
				searchField.setFocused(true);
				if (nameField != null) {
					nameField.setFocused(false);
				}
			}
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

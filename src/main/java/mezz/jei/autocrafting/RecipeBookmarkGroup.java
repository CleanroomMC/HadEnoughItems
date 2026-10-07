package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.DummyBookmarkItem;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.transfer.RecipeTransferErrorSlots;
import mezz.jei.transfer.RecipeTransferErrorTooltip;
import mezz.jei.util.Translator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A bookmark group whose contents are a crafting chain rather than a flat list.
 * <p>
 * Two different lists matter here and are deliberately kept apart. {@link #getItems()} is the real
 * bookmarks — what gets searched, removed and written to disk. {@link #getIngredientListElements()} is
 * the derived view the overlay draws: one row per recipe, made of the output, anything else that recipe
 * yields, and a slot per ingredient. Conflating the two is why removing a row used to silently do
 * nothing, and why ingredient slots were being written to the bookmark file.
 */
public class RecipeBookmarkGroup extends BookmarkGroup {
	private final RecipeChain chain = new RecipeChain(this);

	/**
	 * The derived rows, rebuilt only when the chain changes.
	 * <p>
	 * Held rather than recomputed because the overlay asks for this several times a frame, and because
	 * the renderer maps a drawn element back to its bookmark by object identity — handing out fresh
	 * elements each frame would break hovering, clicking and expanding.
	 */
	@Nullable
	private List<BookmarkItem<?>> displayRows;
	@Nullable
	private List<IIngredientListElement<?>> displayElements;
	private boolean builtWithRecipeBookmarksEnabled;

	public RecipeBookmarkGroup(int id) {
		super(id);
	}

	public ChainSolution solution() {
		return chain.solution();
	}

	/** What is left to craft and what is missing, given what the player is carrying right now. */
	public CraftingPlan plan() {
		return chain.plan();
	}

	/** Called by the chain when its shape changes, so the drawn rows are built again. */
	void onChainChanged() {
		this.displayRows = null;
		this.displayElements = null;
	}

	/**
	 * Called when the player scrolls a requested amount. The shape of the chain is unchanged, but every
	 * amount in it followed from that request, so the solution has to be worked out again — not just the
	 * rows, which is why this goes through the chain rather than straight to {@link #onChainChanged()}.
	 */
	void onRequestedAmountChanged() {
		chain.invalidate();
	}

	/** True when nothing else in the chain needs this step; see {@link RecipeChain#isRoot}. */
	boolean isChainRoot(RecipeBookmarkItem<?> node) {
		return chain.isRoot(node);
	}

	@Override
	public boolean addItem(BookmarkItem<?> item) {
		if (!canAddItem(item)) {
			return false;
		}
		addItemInternal(item);
		foldIfAbsorbed(item);
		return true;
	}

	@Override
	public boolean addItem(BookmarkItem<?> item, boolean toFront) {
		if (!canAddItem(item) || !super.addItem(item, toFront)) {
			return false;
		}
		foldIfAbsorbed(item);
		return true;
	}

	/**
	 * Hands a newly added bookmark to the chain, and takes it back out of the group if the chain absorbed
	 * it into a step it already had.
	 * <p>
	 * Bookmarking something the chain is already producing teaches the existing node its recipe rather
	 * than adding a second one. The incoming object is then a duplicate of a step that is already here,
	 * and leaving it in the group would write it to disk and load it back as a rival node.
	 */
	private void foldIfAbsorbed(BookmarkItem<?> item) {
		if (chain.addOutput((RecipeBookmarkItem<?>) item)) {
			super.removeItem(item);
		}
	}

	@Override
	public void addItemInternal(BookmarkItem<?> item) {
		super.addItemInternal(item);
		onChainChanged();
	}

	@Override
	public boolean canAddItem(BookmarkItem<?> item) {
		return item instanceof RecipeBookmarkItem;
	}

	@Override
	public void removeItem(BookmarkItem<?> item) {
		super.removeItem(item);
		if (item instanceof RecipeBookmarkItem) {
			chain.removeNode((RecipeBookmarkItem<?>) item);
		}
		onChainChanged();
	}

	@Override
	public void finishLoading() {
		chain.rebuildGraph();
	}

	@Override
	public boolean acceptsChanges() {
		return false;
	}

	@Override
	public int getColor() {
		return Config.getRecipeBookmarkGroupColor();
	}

	/**
	 * The rows the overlay draws: for each recipe, its output, then any by-products, then a slot per
	 * ingredient carrying the amount the chain worked out.
	 */
	private List<BookmarkItem<?>> displayRows() {
		boolean enabled = Config.areRecipeBookmarksEnabled();
		if (displayRows != null && builtWithRecipeBookmarksEnabled == enabled) {
			return displayRows;
		}
		builtWithRecipeBookmarksEnabled = enabled;
		displayElements = null;

		if (!enabled) {
			displayRows = Collections.emptyList();
			return displayRows;
		}

		ChainSolution solution = chain.solution();
		List<BookmarkItem<?>> rows = new ArrayList<>();
		for (RecipeBookmarkItem<?> output : solution.order()) {
			if (output.secondaryTo != null || output.inputs().isEmpty()) {
				continue; // By-products are listed under their primary; ingredients have no row of their own.
			}
			rows.add(output);
			rows.addAll(chain.getSecondaryOutputs(output));

			for (RecipeBookmarkItem<?> input : output.inputs()) {
				// Show whatever the chain settled on for this ingredient, so the slot here and the row that
				// produces it below are visibly the same item.
				RecipeBookmarkItem<?> resolved = chain.resolvedNodeFor(input);
				Object shown = resolved != null ? resolved.getIngredient() : input.aliases().get(0);
				// Read through solution() on every draw, not off the one in hand: scrolling a requested
				// amount re-solves the chain but leaves these rows in place in the grid.
				rows.add(new DummyBookmarkItem<>(shown, this,
					() -> RecipeBookmarkItem.totalConsumed(input, solution().craftsOf(output))));
			}
		}
		displayRows = rows;
		return rows;
	}

	@Override
	public List<IIngredientListElement<?>> getIngredientListElements() {
		List<BookmarkItem<?>> rows = displayRows(); // May clear displayElements.
		if (displayElements == null) {
			List<IIngredientListElement<?>> elements = new ObjectArrayList<>(rows.size());
			for (BookmarkItem<?> row : rows) {
				IIngredientListElement<?> element = getIngredientListElement(row);
				if (element != null) {
					elements.add(element);
				}
			}
			displayElements = elements;
		}
		return displayElements;
	}

	/**
	 * Why this chain could not be crafted in the context of the current player.
	 * <p>
	 * Only worth asking once nothing is missing.
	 * In a chain of any depth, the later steps are fed by the earlier ones.
	 * Until those have run, the ingredients are not there yet.
	 * What is left is the container's own objections:
	 * A recipe too large for the 2x2 inventory grid, a full inventory, a category this block cannot craft.
	 */
	@Nullable
	public String getCraftingBlocker(CraftingPlan plan) {
		for (CraftingPlan.Step step : plan.steps()) {
			int crafts = (int) Math.min(Integer.MAX_VALUE, step.crafts());
			IRecipeTransferError error = AutocraftingHandler.isRecipeAutoCraftable(step.recipe(), crafts);
			if (error == null || error instanceof RecipeTransferErrorSlots) {
				continue;
			}
			if (error.getSimpleReason() != null) {
				return error.getSimpleReason();
			}
			return Translator.translateToLocal("jei.tooltip.error.recipe.transfer.unknown");
		}
		return null;
	}

	/** The ingredients the chain needs that the player has neither got nor any way to craft. */
	public List<IIngredientListElement> getMissingIngredients() {
		return getMissingIngredients(plan());
	}

	public List<IIngredientListElement> getMissingIngredients(CraftingPlan plan) {
		List<CraftingPlan.Need> needs = plan.need();
		List<IIngredientListElement> elements = new ObjectArrayList<>(needs.size());
		for (CraftingPlan.Need shortfall : needs) {
			IIngredientListElement<?> element = getIngredientListElement(
				new DummyBookmarkItem<>(shortfall.ingredient().getIngredient(), null, shortfall.amount())
			);
			if (element != null) {
				elements.add(element);
			}
		}
		return elements;
	}

	public void autocraft() {
		IAutocraftingHandler handler = Internal.getRuntime().getAutocraftingHandler();
		if (!handler.isActive() && handler instanceof AutocraftingHandler) {
			((AutocraftingHandler) handler).start(this);
		}
	}
}

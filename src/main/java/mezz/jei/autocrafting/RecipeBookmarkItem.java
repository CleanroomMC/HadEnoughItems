package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.wrapper.IShapedCraftingRecipeWrapper;
import mezz.jei.autocrafting.favorites.FavoriteRecipes;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.ingredients.Ingredients;
import mezz.jei.plugins.vanilla.crafting.ShapelessRecipeWrapper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * One step of a crafting chain: an ingredient, and the recipe that makes it once one is known.
 * <p>
 * A node carries no running total of its own. What the chain needs of it is worked out by
 * {@link ChainSolution} and read back through {@link #getDisplayAmount()}, so that what the overlay
 * draws is always the figure the chain planned around rather than whatever a traversal last left behind.
 */
public class RecipeBookmarkItem<I> extends BookmarkItem<I> {

	long outputAmount = 0L;
	long selfOutputAmount = 0L;
	IRecipeWrapper recipe;
	IRecipeCategory<?> category;
	List<RecipeBookmarkItem<?>> inputs;
	BookmarkItem<?> secondaryTo;

	private List<I> aliases;
	boolean foundAliases = false;
	boolean reusableInCrafting = false;

	public RecipeBookmarkItem(I ingredient) {
		super(ingredient);
		this.aliases = new ObjectArrayList<>();
		this.aliases.add(getIngredient());
	}

	public RecipeBookmarkItem(List<I> aliases) {
		this(aliases, 0L, false);
	}

	public RecipeBookmarkItem(List<I> aliases, long amount, boolean reusableInCrafting) {
		super(aliases.get(0));
		this.aliases = new ObjectArrayList<>(aliases);
		this.aliases.set(0, getIngredient()); // In case it needed to be normalized
		setAmount(amount);
		this.reusableInCrafting = reusableInCrafting;
	}

	public RecipeBookmarkItem(RecipeBookmarkItem<I> other) {
		super(other.getIngredient());
		this.aliases = new ObjectArrayList<>(other.aliases);
		this.foundAliases = other.foundAliases;
		this.reusableInCrafting = other.reusableInCrafting;
		setAmount(other.getAmount());
		this.outputAmount = other.outputAmount;
		this.selfOutputAmount = other.selfOutputAmount;
		this.recipe = other.recipe;
		this.category = other.category;
		this.inputs = other.inputs;
		this.secondaryTo = other.secondaryTo;
	}

	/**
	 * Output lists contain alternatives for a single slot. Keep the selected variant for its slot
	 * and the first valid variant for other slots, with the selected output first in the bookmark row.
	 */
	public static List<RecipeBookmarkItem<?>> createRecipeOutputs(Object selectedOutput, IRecipeWrapper recipe, IRecipeCategory<?> category) {
		Ingredients ingredients = new Ingredients();
		recipe.getIngredients(ingredients);
		List<RecipeBookmarkItem<?>> outputs = new ObjectArrayList<>();
		addRecipeOutput(outputs, selectedOutput, recipe, category, ingredients);
		for (IIngredientType<?> type : ingredients.getOutputIngredients().keySet()) {
			for (List<?> outputSlot : ingredients.getOutputs(type)) {
				if (IngredientUtil.aliasesContains(outputSlot, selectedOutput)) {
					continue;
				}
				for (Object output : outputSlot) {
					if (addRecipeOutput(outputs, output, recipe, category, ingredients)) {
						break;
					}
				}
			}
		}
		return outputs;
	}

	private static boolean addRecipeOutput(List<RecipeBookmarkItem<?>> outputs, Object ingredient, IRecipeWrapper recipe,
										IRecipeCategory<?> category, Ingredients ingredients) {
		IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
		if (ingredient == null || !ingredientRegistry.isValidIngredient(ingredient) || !ingredientRegistry.isIngredientCraftable(ingredient)) {
			return false;
		}
		for (RecipeBookmarkItem<?> output : outputs) {
			if (IngredientUtil.equals(output.getIngredient(), ingredient)) {
				return true;
			}
		}
		RecipeBookmarkItem<Object> output = new RecipeBookmarkItem<>(ingredient);
		output.populateWith(recipe, category, ingredients);
		if (output.isPopulated()) {
			outputs.add(output);
			return true;
		}
		return false;
	}

	public void populateWithFavorite() {
		if (recipe != null) {
			return;
		}
		for (I alias : aliases) {
			IRecipeWrapper favorite = FavoriteRecipes.getFavorite(alias);
			IRecipeCategory<?> favoriteCategory = null;
			if (favorite == null) {
				// Find recipe from other bookmarked recipes
				RecipeBookmarkItem<?> found = (RecipeBookmarkItem<?>) Internal.getBookmarkList()
						.getBookmarkGroupsInternal()
						.stream()
						.map(BookmarkGroup::getItemsInternal)
						.flatMap(Collection::stream)
						.filter(item -> item != this)
						.filter(RecipeBookmarkItem.class::isInstance)
						.map(RecipeBookmarkItem.class::cast)
						.filter(item -> item.recipe != null)
						.filter(item -> IngredientUtil.aliasesContains(item.aliases, alias))
						.findFirst()
						.orElse(null);
				if (found != null) {
					favorite = found.recipe;
					favoriteCategory = found.category;
				}
			} else {
				favoriteCategory = FavoriteRecipes.getFavoriteCategory(alias);
			}
			if (favorite != null) {
				setIngredientUnchecked(alias);
				populateWith(favorite, favoriteCategory);
				return;
			}
		}
	}

	public void populateWith(IRecipeWrapper recipe, IRecipeCategory<?> category) {
		Ingredients ingredients = new Ingredients();
		recipe.getIngredients(ingredients);
		populateWith(recipe, category, ingredients);
	}

	private void populateWith(IRecipeWrapper recipe, IRecipeCategory<?> category, Ingredients ingredients) {
		if (!Internal.getIngredientRegistry().isIngredientCraftable(getIngredient())) {
			return;
		}
		this.recipe = recipe;
		this.category = category;
		this.inputs = new ObjectArrayList<>();
		for (IIngredientType<?> type : ingredients.getInputIngredients().keySet()) {
			List<List> typeInputs = (List) ingredients.getInputs(type);
			List<Boolean> reusableInputs = type == VanillaTypes.ITEM ? getReusableInputs((List) typeInputs) : null;
			populateInputType(typeInputs, reusableInputs);
		}
		this.outputAmount = getOutputAmount(ingredients);
	}

	/**
	 * Returns the amount produced in one recipe without adding together rotating variants.
	 * <p>
	 * When a mod represents a chance output as {@code [1x dust, 2x dust]}, the safest useful value is the
	 * smallest positive amount in that slot which would be 1x, and not 1x + 2x = 3x.
	 * Deterministic outputs retain their real stack size
	 * and two separate slots containing the same ingredient still contribute independently.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private long getOutputAmount(Ingredients ingredients) {
		I ingredient = getIngredient();
		IIngredientType<I> ingredientType = Internal.getIngredientRegistry().getIngredientType(ingredient);
		if (ingredientType == null) {
			return 1L;
		}
		long total = 0L;
		for (List outputSlot : ingredients.getOutputs(ingredientType)) {
			long slotAmount = Long.MAX_VALUE;
			for (Object candidate : outputSlot) {
				if (IngredientUtil.equals(ingredient, candidate)) {
					long candidateAmount = IngredientUtil.getCount(candidate);
					slotAmount = Math.min(slotAmount, Math.max(1L, candidateAmount));
				}
			}
			if (slotAmount != Long.MAX_VALUE) {
				total += slotAmount;
			}
		}
		return Math.max(1L, total);
	}

	/**
	 * How much of {@code input} this recipe consumes in total, given how many times it has to run.
	 * <p>
	 * A reusable ingredient such as a bucket or a crafting tool is handed back after each craft, so one
	 * set covers every run. This matches how {@link ChainSolution} weighs a reusable edge, so the number
	 * shown on an ingredient slot is the same one the chain planned around.
	 */
	static long totalConsumed(RecipeBookmarkItem<?> input, long crafts) {
		if (input.reusableInCrafting) {
			return input.inputAmount();
		}
		return input.inputAmount() * crafts;
	}

	/**
	 * Reads this node's inputs back off its recipe after a load.
	 */
	public void populateSelf() {
		populateWith(recipe, category);
	}

	private void populateInputType(List<List> typeInputs, List<Boolean> reusableInputs) {
		int typeSize = typeInputs.size();
		boolean[] seen = new boolean[typeSize];
		for (int i = 0; i < typeSize; i++) {
			if (seen[i]) {
				continue;
			}
			List inputAliases = removeNulls(typeInputs.get(i));
			if (inputAliases.isEmpty()) { // Yet another edge case! A recipe input is completely null.
				continue;
			}
			boolean reusable = isReusableInput(reusableInputs, i);
			int count = IngredientUtil.getCount(inputAliases.get(0));
			for (int j = i + 1; j < typeSize; j++) {
				List other = typeInputs.get(j);
				if (reusable == isReusableInput(reusableInputs, j) && IngredientUtil.aliasesEquals(inputAliases, other)) {
					count += IngredientUtil.getCount(other.get(0));
					seen[j] = true;
				}
			}
			inputs.add(new RecipeBookmarkItem<>(inputAliases, count, reusable));
		}
		inputs.forEach(input -> input.foundAliases = true);
	}

	private boolean isReusableInput(List<Boolean> reusableInputs, int index) {
		return reusableInputs != null && index < reusableInputs.size() && reusableInputs.get(index);
	}

	private List<Boolean> getReusableInputs(List<List<ItemStack>> typeInputs) {
		List<Boolean> reusableInputs = new ObjectArrayList<>(typeInputs.size());
		for (int i = 0; i < typeInputs.size(); i++) {
			reusableInputs.add(false);
		}
		if (!(recipe instanceof ShapelessRecipeWrapper)) {
			return reusableInputs;
		}
		IRecipe rawRecipe = ((ShapelessRecipeWrapper<?>) recipe).getRawRecipe();
		for (int i = 0; i < typeInputs.size(); i++) {
			if (isReusableInput(typeInputs, i, rawRecipe)) {
				reusableInputs.set(i, true);
			}
		}
		return reusableInputs;
	}

	private boolean isReusableInput(List<List<ItemStack>> typeInputs, int inputIndex, IRecipe rawRecipe) {
		List<ItemStack> aliases = removeNulls(typeInputs.get(inputIndex));
		if (aliases.isEmpty()) {
			return false;
		}
		for (ItemStack alias : aliases) {
			try {
				InventoryCrafting crafting = createCraftingInventory(typeInputs, inputIndex, alias);
				NonNullList<ItemStack> remainingItems = rawRecipe.getRemainingItems(crafting);
				if (inputIndex >= remainingItems.size() || !hasMatchingRemainder(Collections.singletonList(alias), remainingItems.get(inputIndex))) {
					return false;
				}
			} catch (RuntimeException ignored) {
				return false;
			}
		}
		return true;
	}

	private InventoryCrafting createCraftingInventory(List<List<ItemStack>> typeInputs, int selectedInputIndex, ItemStack selectedStack) {
		InventoryCrafting crafting = this.getInventory();
		int size = Math.min(typeInputs.size(), crafting.getSizeInventory());
		for (int i = 0; i < size; i++) {
			List<ItemStack> aliases = removeNulls(typeInputs.get(i));
			if (!aliases.isEmpty()) {
				ItemStack stack = i == selectedInputIndex ? selectedStack.copy() : aliases.get(0).copy();
				stack.setCount(1);
				crafting.setInventorySlotContents(i, stack);
			}
		}
		return crafting;
	}

	@Nonnull
	private InventoryCrafting getInventory() {
		int width = 3;
		int height = 3;
		if (recipe instanceof IShapedCraftingRecipeWrapper) {
			IShapedCraftingRecipeWrapper shapedRecipe = (IShapedCraftingRecipeWrapper) recipe;
			width = shapedRecipe.getWidth();
			height = shapedRecipe.getHeight();
		}
		return new InventoryCrafting(new Container() {
			@Override
			public boolean canInteractWith(EntityPlayer playerIn) {
				return false;
			}
		}, width, height);
	}

	private boolean hasMatchingRemainder(List<ItemStack> aliases, ItemStack remainder) {
		if (remainder.isEmpty()) {
			return false;
		}
		for (ItemStack alias : removeNulls(aliases)) {
			if (!alias.isEmpty() && Internal.getStackHelper().isEquivalent(alias, remainder)) {
				return true;
			}
		}
		return false;
	}

	private <T> List<T> removeNulls(List<T> original) {
		List<T> list = new ObjectArrayList<>(original.size());
		for (T item : original) {
			if (item != null) {
				list.add(item);
			}
		}
		return list;
	}

	/** The ingredients that can satisfy this node, never null. */
	public List<I> aliases() {
		return aliases;
	}

	/** The ingredients this node's recipe consumes. Empty until the node knows its recipe. */
	public List<RecipeBookmarkItem<?>> inputs() {
		return inputs == null ? Collections.emptyList() : inputs;
	}

	/**
	 * How much of this ingredient one application of the consuming recipe takes.
	 * <p>
	 * Only meaningful on the input templates hanging off {@link #inputs()}; a node in the graph carries no
	 * amount of its own, since what the chain needs of it is worked out by {@link ChainSolution}.
	 */
	public long inputAmount() {
		return getAmount();
	}

	/**
	 * Builds a graph node standing for this input.
	 * <p>
	 * The node takes a copy of the alias list rather than sharing this template's: a node's aliases get
	 * narrowed as the chain works out what every recipe involved will accept, and that must not reach back
	 * into the recipe this input was read from.
	 */
	RecipeBookmarkItem<I> asChainNode() {
		return new RecipeBookmarkItem<>(new ObjectArrayList<>(this.aliases), 0, this.reusableInCrafting);
	}

	/**
	 * Adopts another node's aliases and ingredient, used when this node turns out to stand for the same
	 * ingredient as one reached from a different recipe.
	 */
	@SuppressWarnings("unchecked")
	void adoptAliasesOf(RecipeBookmarkItem<?> other) {
		this.aliases = new ObjectArrayList<>((List<I>) other.aliases);
		setIngredient(other.getIngredient());
	}

	/**
	 * Narrows this node's aliases to those in {@code keptUniqueIds}.
	 * <p>
	 * A node fed by several recipes can only be satisfied by ingredients every one of them accepts, so
	 * each time it is matched its aliases are cut down to the intersection. The last alias is never
	 * dropped, so a node always has something to stand for.
	 */
	void retainAliases(Collection<String> keptUniqueIds) {
		IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
		List<I> kept = new ObjectArrayList<>(this.aliases.size());
		for (I alias : this.aliases) {
			if (keptUniqueIds.contains(ingredientRegistry.getUniqueId(alias))) {
				kept.add(alias);
			}
		}
		if (!kept.isEmpty()) {
			this.aliases = kept;
		}
	}

	public boolean isPopulated() {
		return recipe != null && category != null;
	}

	public boolean isExplicitlyRequested() {
		return requestOwner().selfOutputAmount > 0L;
	}

	private RecipeBookmarkItem<?> requestOwner() {
		RecipeBookmarkItem<?> owner = this;
		while (owner.secondaryTo instanceof RecipeBookmarkItem) {
			owner = (RecipeBookmarkItem<?>) owner.secondaryTo;
		}
		return owner;
	}

	@Override
	public boolean startsNewRow() {
		return secondaryTo == null && !inputs().isEmpty();
	}

	@Override
	public void changeAmount(long delta) {
		if (delta == 0L) {
			return;
		}
		BookmarkGroup owner = getGroup();
		RecipeBookmarkItem<?> requestOwner = requestOwner();
		long ownerOutputAmount = Math.max(1L, requestOwner.outputAmount);
		long selectedOutputAmount = Math.max(1L, this.outputAmount);
		long requestedCrafts = ChainSolution.craftsFor(requestOwner.selfOutputAmount, ownerOutputAmount);
		// A step something else in the chain needs may be taken down to zero — that just means no extra
		// beyond what the chain works out. A step nothing needs may not: it is a row the player put there,
		// and zero of it is not an amount, it is a removal.
		long floorCrafts = owner instanceof RecipeBookmarkGroup && ((RecipeBookmarkGroup) owner).isChainRoot(requestOwner) ? 1L : 0L;
		long craftStep = Math.max(1L, (Math.abs(delta) + selectedOutputAmount - 1L) / selectedOutputAmount);
		if (delta < 0L) {
			craftStep = -craftStep;
		}
		long snappedCrafts = (requestedCrafts / craftStep) * craftStep;
		long newCrafts = Math.max(floorCrafts, snappedCrafts + craftStep);
		requestOwner.selfOutputAmount = newCrafts * ownerOutputAmount;

		if (owner instanceof RecipeBookmarkGroup) {
			((RecipeBookmarkGroup) owner).onRequestedAmountChanged();
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Nullable
	public IRecipeLayout createLayout() {
		return RecipeLayout.create(-1, (IRecipeCategory) category, recipe, null, 0, 0);
	}

	@Override
	public long getDisplayAmount() {
		// The chain-wide total, so a step needed only by a later recipe still shows how many will be made.
		BookmarkGroup owner = getGroup();
		if (owner instanceof RecipeBookmarkGroup) {
			return ((RecipeBookmarkGroup) owner).solution().producedOf(this);
		}
		return getAmount();
	}

	@Override
	public boolean deserialize(NBTTagCompound serialized) {
		super.deserialize(serialized);
		this.selfOutputAmount = serialized.getLong("selfOutputAmount");
		this.category = Internal.getRuntime().getRecipeRegistry().getRecipeCategory(serialized.getString("category"));
		this.recipe = Internal.getRuntime().getRecipeRegistry().getRecipeById(serialized.getLong("recipe"), category);
		return category != null && recipe != null;
	}

	@Override
	@Nullable
	public String serialize() {
		// Nodes the chain created for itself carry no recipe and are rebuilt on load, so they are not saved.
		if (!isPopulated()) {
			return null;
		}
		I ingredient = getIngredient();
		NBTTagCompound tag = getNBTOfIngredient(ingredient);
		if (tag == null) {
			return null;
		}
		tag.setLong("amount", getAmount());
		tag.setLong("selfOutputAmount", selfOutputAmount);
		tag.setString("category", category.getUid());
		tag.setLong("recipe", Internal.getRuntime().getRecipeRegistry().getRecipeId(recipe));

		if (ingredient instanceof ItemStack) {
			return MARKER_RECIPE + MARKER_STACK + tag;
		} else {
			return MARKER_RECIPE + MARKER_OTHER + tag;
		}
	}

	@Override
	public RecipeBookmarkItem<I> copy() {
		return new RecipeBookmarkItem<>(this);
	}
}

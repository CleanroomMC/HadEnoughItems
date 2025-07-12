package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.autocrafting.favorites.FavoriteRecipes;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.DummyBookmarkItem;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.ingredients.Ingredients;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.stream.Collectors;

public class RecipeBookmarkItem<I> extends BookmarkItem<I> {
    // How much of this item is produced by its recipe.
    public long outputAmount = 0L;
    // How much of this item the player requested apart from the recipe chain.
    public long selfOutputAmount = 0L;
    public IRecipeWrapper recipe;
    public IRecipeCategory<?> category;
    public List<RecipeBookmarkItem<?>> inputs;
    public BookmarkItem<?> secondaryTo;
    // These are possible ingredients, which are helpful for OreDictionary.
    public List<I> aliases;
    public boolean foundAliases = false;
    private List<DummyBookmarkItem<?>> inputDummyItems; // Cached for performance

    public RecipeBookmarkItem(I ingredient) {
        super(ingredient);
        this.ingredient = IngredientUtil.normalizeCopy(ingredient);
        this.aliases = new ObjectArrayList<>();
        this.aliases.add(ingredient);
    }

    public RecipeBookmarkItem(List<I> aliases) {
        super(aliases.get(0));
        this.aliases = aliases;
    }

    public RecipeBookmarkItem(List<I> aliases, int amount) {
        super(aliases.get(0));
        this.aliases = aliases;
        this.amount = amount;
    }

    public void populateWithFavorite() {
        if (recipe != null) {
            return;
        }
        for (I alias : aliases) {
            IRecipeWrapper favorite = FavoriteRecipes.getFavorite(alias);
            if (favorite != null) {
                IRecipeCategory<?> favoriteCategory = FavoriteRecipes.getFavoriteCategory(alias);
                this.ingredient = alias;
                populateWith(favorite, favoriteCategory);
                return;
            }
        }
    }

    public void populateWith(IRecipeWrapper recipe, IRecipeCategory<?> category) {
        this.recipe = recipe;
        this.category = category;
        Ingredients ingredients = new Ingredients();
        recipe.getIngredients(ingredients);
        inputs = new ObjectArrayList<>();
        for (IIngredientType<?> type : ingredients.getInputIngredients().keySet()) {
            populateInputType(ingredients.getInputs(type));
        }
        this.outputAmount = 0L;
        for (Object other :
                ingredients.getOutputIngredients().get(Internal.getIngredientRegistry().getIngredientType(ingredient))) {
            if (IngredientUtil.equals(ingredient, other)) {
                this.outputAmount += IngredientUtil.getCount(other);
            }
        }

        inputDummyItems = inputs.stream().map((input) -> {
            final long initialSize = input.amount;
            return new DummyBookmarkItem<>(input.aliases.get(0), getGroup(), () -> initialSize * getMultiplier());
        }).collect(Collectors.toList());
    }

    public void populateSelf(RecipeChain chain) {
        populateWith(recipe, category);
        RecipeBookmarkItem<?> possibleSecondary = chain.findOutputWithSameRecipe(this);
        if (possibleSecondary != null) {
            secondaryTo = possibleSecondary;
        }
    }

    private <T> void populateInputType(List<List<T>> typeInputs) {
        int typeSize = typeInputs.size();
        boolean[] seen = new boolean[typeSize];
        for (int i = 0; i < typeSize; i++) {
            if (seen[i]) {
                continue;
            }
            List<T> inputAliases = removeNulls(typeInputs.get(i));
            if (inputAliases.isEmpty()) { // Yet another edge case! A recipe input is completely null.
                continue;
            }
            int count = IngredientUtil.getCount(inputAliases.get(0));
            for (int j = i + 1; j < typeSize; j++) {
                List<T> other = typeInputs.get(j);
                if (IngredientUtil.aliasesEquals(inputAliases, other)) {
                    count += IngredientUtil.getCount(other.get(0));
                    seen[j] = true;
                }
            }
            inputs.add(new RecipeBookmarkItem<>(inputAliases, count));
        }
        inputs.forEach(input -> input.foundAliases = true);
    }

    private <T> List<T> removeNulls(List<T> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i) == null) {
                list.remove(i);
            }
        }
        return list;
    }

    public boolean isPopulated() {
        return recipe != null;
    }

    @Override
    public boolean startsNewRow() {
        return secondaryTo == null && this.inputs != null;
    }

    public List<DummyBookmarkItem<?>> getInputs() {
        return inputDummyItems;
    }

    public long getMultiplier() {
        return (amount + outputAmount - 1) / outputAmount;
    }

    @Override
    public void changeAmount(long delta) {
        this.selfOutputAmount = Math.max(0L, this.selfOutputAmount + delta);
        if (this.getGroup() instanceof RecipeBookmarkGroup) {
            ((RecipeBookmarkGroup) this.getGroup()).update();
        }
    }

    public IRecipeLayout createLayout() {
        return RecipeLayout.create(-1, (IRecipeCategory) category, recipe, null, 0, 0);
    }

    @Override
    public long getDisplayAmount() {
        if (outputAmount == 0) {
            return amount;
        }
        return outputAmount * getMultiplier();
    }

    @Override
    public boolean deserialize(NBTTagCompound serialized) {
        super.deserialize(serialized);
        this.selfOutputAmount = serialized.getLong("selfOutputAmount");
        this.category = Internal.getRuntime().getRecipeRegistry().getRecipeCategory(serialized.getString("category"));
        this.recipe = Internal.getRuntime().getRecipeRegistry().getRecipeById(serialized.getLong("recipe"), category);
        return true;
    }

    public String serialize() {
        NBTTagCompound tag = getNBTOfIngredient(ingredient);
        tag.setLong("amount", amount);
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
    public void setGroup(BookmarkGroup group) {
        super.setGroup(group);
        if (this.inputDummyItems != null) {
            this.inputDummyItems.forEach(item -> item.setGroup(group));
        }
    }
}

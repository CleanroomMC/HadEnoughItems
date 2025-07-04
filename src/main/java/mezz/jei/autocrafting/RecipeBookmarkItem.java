package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.autocrafting.favorites.FavoriteRecipes;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.DummyBookmarkItem;
import mezz.jei.ingredients.Ingredients;

import java.util.List;
import java.util.stream.Collectors;

public class RecipeBookmarkItem<I> extends BookmarkItem<I> {
    public long outputAmount = 0L;
    public long selfOutputAmount = 0L;
    public IRecipeWrapper recipe;
    public List<RecipeBookmarkItem<?>> inputs;
    public BookmarkItem<?> secondaryTo;
    public RecipeChain chain;
    // These are possible ingredients, which are helpful for OreDictionary.
    public List<I> aliases;

    public RecipeBookmarkItem(I ingredient) {
        super(ingredient);
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
                this.ingredient = alias;
                populateWith(favorite);
                return;
            }
        }
    }

    public void populateWith(IRecipeWrapper recipe) {
        this.recipe = recipe;
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
        long multiplier = (amount + outputAmount - 1) / outputAmount;
        return inputs.stream().map((input) -> new DummyBookmarkItem<>(input.aliases.get(0), group, input.amount * multiplier)).collect(Collectors.toList());
    }
}

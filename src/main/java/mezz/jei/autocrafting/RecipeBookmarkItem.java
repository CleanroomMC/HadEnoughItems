package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import mezz.jei.Internal;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.autocrafting.favorites.FavoriteRecipes;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.ingredients.Ingredients;

import java.util.Map;

public class RecipeBookmarkItem<I> extends BookmarkItem<I> {
    public long outputAmount = 0L;
    public IRecipeWrapper recipe;
    public Map<Object, Integer> inputs;
    public BookmarkItem<?> secondaryTo;

    public RecipeBookmarkItem(I ingredient) {
        super(ingredient);
    }

    public void populateWithFavorite() {
        if (recipe != null) {
            return;
        }
        IRecipeWrapper favorite = FavoriteRecipes.getFavorite(ingredient);
        if (favorite == null) {
            return;
        }
        populateWith(favorite);
    }

    public void populateWith(IRecipeWrapper recipe) {
        this.recipe = recipe;
        Ingredients ingredients = new Ingredients();
        recipe.getIngredients(ingredients);
        inputs = new Object2IntOpenHashMap<>();
        for (IIngredientType<?> type : ingredients.getInputIngredients().keySet()) {
            for (Object input : ingredients.getInputIngredients().get(type)) {
                inputs.put(input, 0);
            }
        }
        this.outputAmount = 0L;
        for (Object other :
                ingredients.getOutputIngredients().get(Internal.getIngredientRegistry().getIngredientType(ingredient))) {
            if (other == ingredient) {
                this.outputAmount += IngredientUtil.getCount(other);
            }
        }
    }

    public boolean isPopulated() {
        return recipe != null;
    }

}

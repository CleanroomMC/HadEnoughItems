package mezz.jei.util;

import mezz.jei.Internal;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;
import mezz.jei.gui.Focus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class RecipeUtil {
    private RecipeUtil() {

    }

    /**
     * Gets all crafting recipes with HEI's internal recipes cache, with the matching output
     *
     * @param output                        output ingredient of the crafting recipes
     * @return                              crafting recipes that matches and has the output ingredient
     * @throws IllegalArgumentException     when the ingredient is not supported by HEI
     */
    public static <V> List<IRecipeWrapper> getCraftingRecipesWithOutput(V output) throws IllegalArgumentException {
        return getRecipesWithOutput(output, ICraftingRecipeWrapper.class::isInstance);
    }

    /**
     * Gets all  recipes with HEI's internal recipes cache, with the matching output
     *
     * @param output                        output ingredient of the crafting recipes
     * @param predicate                     filter for what type of recipe wrapper to match
     * @return                              crafting recipes that matches and has the output ingredient
     * @throws IllegalArgumentException     when the ingredient is not supported by HEI
     */
    public static <V> List<IRecipeWrapper> getRecipesWithOutput(V output, Predicate<IRecipeWrapper> predicate) throws IllegalArgumentException {
        return getRecipesWithOutput(output).stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * Gets all recipes with HEI's internal recipes cache, with the matching output
     *
     * @param output                        output ingredient of the recipes
     * @return                              recipes that matches and has the output ingredient
     * @throws IllegalArgumentException     when the ingredient is not supported by HEI
     */
    public static <V> List<IRecipeWrapper> getRecipesWithOutput(V output) throws IllegalArgumentException {
        IIngredientHelper<V> helper = Internal.getIngredientRegistry().getIngredientHelper(output);
        IFocus<?> focus = helper.translateFocus(new Focus<>(IFocus.Mode.OUTPUT, output), Focus::new);

        IRecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
        List<IRecipeCategory> recipeCategories = recipeRegistry.getRecipeCategories(focus);
        if (recipeCategories.isEmpty()) {
            return Collections.emptyList();
        }

        List<IRecipeWrapper> recipes = new ArrayList<>();
        for (IRecipeCategory<?> category : recipeCategories) {
            recipes.addAll(recipeRegistry.getRecipeWrappers(category, focus));
        }

        return recipes;
    }

}

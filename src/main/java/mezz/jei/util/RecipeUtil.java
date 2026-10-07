package mezz.jei.util;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.Internal;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;
import mezz.jei.gui.Focus;
import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Utilities to query recipes in vanilla or any mod that supports HEI's recipe framework.
 * This way is consistently faster than querying via {@link ForgeRegistries#RECIPES}.
 *
 * @since 4.30.0
 */
public final class RecipeUtil {

    private RecipeUtil() {
    }

    public static Query query() {
        return new Query();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Set<IRecipeWrapper> search(IRecipeCategory category,
                                             Collection<IIngredientListElement<?>> ingredients,
                                             boolean searchInputs, boolean searchOutputs) {
        IRecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
        Set<IRecipeWrapper> matched = new ReferenceOpenHashSet<>();
        MutableFocus focus = new MutableFocus();
        if (searchInputs) {
            focus.setMode(IFocus.Mode.INPUT);
            for (IIngredientListElement<?> element : ingredients) {
                focus.setValue(element.getIngredient());
                matched.addAll(recipeRegistry.getRecipeWrappers(category, translateFocus(element, focus)));
            }
        }
        if (searchOutputs) {
            focus.setMode(IFocus.Mode.OUTPUT);
            for (IIngredientListElement<?> element : ingredients) {
                focus.setValue(element.getIngredient());
                matched.addAll(recipeRegistry.getRecipeWrappers(category, translateFocus(element, focus)));
            }
        }
        return matched;
    }

    public static List<IRecipeWrapper> query(Consumer<Query> consumer) throws IllegalArgumentException {
        Query query = new Query();
        consumer.accept(query);
        return query.result();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static class Query {

        private final List inputs = new ArrayList<>();
        private final List outputs = new ArrayList<>();

        @Nullable
        private Predicate<IRecipeWrapper> recipeConditions;

        public Query input(Object input) {
            this.inputs.add(input);
            return this;
        }

        public Query inputs(Object... inputs) {
            Collections.addAll(this.inputs, inputs);
            return this;
        }

        public Query inputs(Iterable inputs) {
            this.inputs.addAll((Collection) inputs);
            return this;
        }

        public Query output(Object output) {
            this.outputs.add(output);
            return this;
        }

        public Query outputs(Object... outputs) {
            Collections.addAll(this.outputs, outputs);
            return this;
        }

        public Query outputs(Iterable outputs) {
            this.outputs.addAll((Collection) outputs);
            return this;
        }

        public Query vanillaCraftingOnly() {
            return condition(recipe -> recipe instanceof ICraftingRecipeWrapper);
        }

        public Query condition(Predicate<IRecipeWrapper> condition) {
            if (this.recipeConditions == null) {
                this.recipeConditions = condition;
            } else {
                this.recipeConditions = this.recipeConditions.and(condition);
            }
            return this;
        }

        public List<IRecipeWrapper> result() throws IllegalArgumentException {
            Preconditions.checkArgument(!this.inputs.isEmpty() || !this.outputs.isEmpty(),
                    "Both inputs and outputs were empty when querying for recipes, that is not allowed");

            IRecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
            Set<IRecipeWrapper> recipes = new ReferenceOpenHashSet<>();
            MutableFocus focus = new MutableFocus();

            focus.setMode(IFocus.Mode.INPUT);
            for (Object input : this.inputs) {
                focus.setValue(input);
                for (IRecipeCategory category : recipeRegistry.getRecipeCategories(focus)) {
                    recipes.addAll(recipeRegistry.getRecipeWrappers(category, focus));
                }
            }

            focus.setMode(IFocus.Mode.OUTPUT);
            for (Object output : this.outputs) {
                focus.setValue(output);
                for (IRecipeCategory category : recipeRegistry.getRecipeCategories(focus)) {
                    recipes.addAll(recipeRegistry.getRecipeWrappers(category, focus));
                }
            }

            if (this.recipeConditions != null) {
                recipes.removeIf(wrapper -> !this.recipeConditions.test(wrapper));
            }
            return new ArrayList<>(recipes);
        }

    }

    @SuppressWarnings("unchecked")
    private static <V> IFocus<?> translateFocus(IIngredientListElement<V> element, MutableFocus focus) {
        return element.getIngredientHelper().translateFocus((Focus<V>) focus, Focus::new);
    }

    private static class MutableFocus extends Focus<Object> {

        private Mode mode;
        private Object value;

        private MutableFocus() {
            super();
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public void setValue(Object value) {
            this.value = Internal.getIngredientRegistry().getIngredientHelper(value).copyIngredient(value);
        }

        @Override
        public Mode getMode() {
            return mode;
        }

        @Override
        public Object getValue() {
            return value;
        }

    }

}

package mezz.jei.api.ingredients;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * A subtype interpreter tells HEI how to create unique ids for ingredients.
 *
 * For example, an ItemStack may have some NBT that is used to create many subtypes,
 * and other NBT that is used for electric charge that can be ignored.
 * You can tell JEI how to interpret these differences by implementing an
 * {@link IIngredientSubtypeInterpreter} and registering it with JEI in
 * {@link mezz.jei.api.ISubtypeRegistry}
 *
 * @since HEI 4.18.1
 */
@FunctionalInterface
public interface IIngredientSubtypeInterpreter<T> extends Function<T, String> {
    String NONE = "";

    /**
     * Get the data from an ingredient that is relevant to telling subtypes apart in the given context.
     * This should account for nbt, and anything else that's relevant.
     *
     * Return {@link #NONE} if there is no data used for subtypes.
     */
    String apply(T ingredient);

    /**
     * Get the data from an ingredient that is relevant to telling subtypes apart.
     * This should account for meta, nbt and anything else that's relevant.
     * Returns null if there is no data used for subtypes.
     *
     * @deprecated since JEI 4.7.8, extended life in HEI 4.18.1 for backwards compatibility
     */
    @Deprecated
    @Nullable
    default String getSubtypeInfo(T ingredient) {
        return apply(ingredient);
    }
}

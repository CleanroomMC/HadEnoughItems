package mezz.jei.ingredients;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;

/**
 * IIngredientHelper for the CollapsedStack ingredient type.
 * Follows the BookmarkIngredientHelper delegation pattern — most methods
 * delegate to the first ingredient's helper for addon compatibility.
 */
public class CollapsedStackIngredientHelper implements IIngredientHelper<CollapsedStack> {
	public static final CollapsedStackIngredientHelper INSTANCE = new CollapsedStackIngredientHelper();

	@Nullable
	@Override
	public CollapsedStack getMatch(Iterable<CollapsedStack> ingredients, CollapsedStack ingredientToMatch) {
		for (CollapsedStack cs : ingredients) {
			if (cs.getId().equals(ingredientToMatch.getId())) {
				return cs;
			}
		}
		return null;
	}

	@Override
	public String getDisplayName(CollapsedStack ingredient) {
		return ingredient.getDisplayName();
	}

	@Override
	public String getUniqueId(CollapsedStack ingredient) {
		// Prefixed to avoid collisions with other ingredient type UIDs
		return "collapsedstack:" + ingredient.getId();
	}

	@Override
	public String getWildcardId(CollapsedStack ingredient) {
		return getUniqueId(ingredient);
	}

	@Override
	public String getModId(CollapsedStack ingredient) {
		return getFirstIngredientHelper(ingredient).getModId(getFirstIngredient(ingredient));
	}

	@Override
	public String getDisplayModId(CollapsedStack ingredient) {
		return getFirstIngredientHelper(ingredient).getDisplayModId(getFirstIngredient(ingredient));
	}

	@Override
	public Iterable<Color> getColors(CollapsedStack ingredient) {
		return getFirstIngredientHelper(ingredient).getColors(getFirstIngredient(ingredient));
	}

	@Override
	public String getResourceId(CollapsedStack ingredient) {
		return "collapsedstack:" + ingredient.getId();
	}

	@Override
	public ItemStack getCheatItemStack(CollapsedStack ingredient) {
		// Delegate cheat-give to the first ingredient in the group
		return getFirstIngredientHelper(ingredient).getCheatItemStack(getFirstIngredient(ingredient));
	}

	@Override
	public CollapsedStack copyIngredient(CollapsedStack ingredient) {
		// CollapsedStack instances are transient and shared via the registry per filter cycle;
		// returning the same instance is safe for current usage patterns.
		return ingredient;
	}

	@Override
	public boolean isValidIngredient(CollapsedStack ingredient) {
		return !ingredient.isEmpty();
	}

	@Override
	public boolean isIngredientOnServer(CollapsedStack ingredient) {
		return true;
	}

	@Override
	public Collection<String> getOreDictNames(CollapsedStack ingredient) {
		return Collections.emptyList();
	}

	@Override
	public Collection<String> getCreativeTabNames(CollapsedStack ingredient) {
		return Collections.emptyList();
	}

	@Override
	public String getErrorInfo(@Nullable CollapsedStack ingredient) {
		if (ingredient == null) {
			return "CollapsedStack is null";
		}
		return "CollapsedStack[" + ingredient.getId() + ", " + ingredient.size() + " items]";
	}

	// Delegate recipe lookups to the representative item for addon compatibility
	@Override
	public IFocus<?> translateFocus(IFocus<CollapsedStack> focus, IFocusFactory focusFactory) {
		CollapsedStack cs = focus.getValue();
		if (cs != null && !cs.isEmpty()) {
			Object firstIngredient = cs.getIngredients().get(0).getIngredient();
			return focusFactory.createFocus(focus.getMode(), firstIngredient);
		}
		return focus;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static IIngredientHelper getFirstIngredientHelper(CollapsedStack ingredient) {
		if (ingredient.isEmpty()) {
			// Fallback: return ItemStack helper as a safe default
			return Internal.getIngredientRegistry().getIngredientHelper(ItemStack.class);
		}
		Object first = ingredient.getIngredients().get(0).getIngredient();
		return Internal.getIngredientRegistry().getIngredientHelper(first);
	}

	@SuppressWarnings("rawtypes")
	private static Object getFirstIngredient(CollapsedStack ingredient) {
		if (ingredient.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return ingredient.getIngredients().get(0).getIngredient();
	}
}

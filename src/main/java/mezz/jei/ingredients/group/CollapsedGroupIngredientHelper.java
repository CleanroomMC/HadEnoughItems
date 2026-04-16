package mezz.jei.ingredients.group;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IFocus;
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
public class CollapsedGroupIngredientHelper implements IIngredientHelper<CollapsedGroupIngredient> {
	public static final CollapsedGroupIngredientHelper INSTANCE = new CollapsedGroupIngredientHelper();

	@Nullable
	@Override
	public CollapsedGroupIngredient getMatch(Iterable<CollapsedGroupIngredient> ingredients, CollapsedGroupIngredient ingredientToMatch) {
		for (CollapsedGroupIngredient cs : ingredients) {
			if (cs.getId().equals(ingredientToMatch.getId())) {
				return cs;
			}
		}
		return null;
	}

	@Override
	public String getDisplayName(CollapsedGroupIngredient ingredient) {
		return ingredient.getDisplayName();
	}

	@Override
	public String getUniqueId(CollapsedGroupIngredient ingredient) {
		// Prefixed to avoid collisions with other ingredient type UIDs
		return "collapsedstack:" + ingredient.getId();
	}

	@Override
	public String getWildcardId(CollapsedGroupIngredient ingredient) {
		return getUniqueId(ingredient);
	}

	@Override
	public String getModId(CollapsedGroupIngredient ingredient) {
		if (ingredient.isEmpty()) {
			return "jei";
		}
		return getFirstIngredientHelper(ingredient).getModId(getFirstIngredient(ingredient));
	}

	@Override
	public String getDisplayModId(CollapsedGroupIngredient ingredient) {
		if (ingredient.isEmpty()) {
			return "jei";
		}
		return getFirstIngredientHelper(ingredient).getDisplayModId(getFirstIngredient(ingredient));
	}

	@Override
	public Iterable<Color> getColors(CollapsedGroupIngredient ingredient) {
		return getFirstIngredientHelper(ingredient).getColors(getFirstIngredient(ingredient));
	}

	@Override
	public String getResourceId(CollapsedGroupIngredient ingredient) {
		return "collapsedstack:" + ingredient.getId();
	}

	@Override
	public ItemStack getCheatItemStack(CollapsedGroupIngredient ingredient) {
		// Delegate cheat-give to the first ingredient in the group
		return getFirstIngredientHelper(ingredient).getCheatItemStack(getFirstIngredient(ingredient));
	}

	@Override
	public CollapsedGroupIngredient copyIngredient(CollapsedGroupIngredient ingredient) {
		// CollapsedStack instances are transient and shared via the registry per filter cycle;
		// returning the same instance is safe for current usage patterns.
		return ingredient;
	}

	@Override
	public boolean isValidIngredient(CollapsedGroupIngredient ingredient) {
		return !ingredient.isEmpty();
	}

	@Override
	public boolean isIngredientOnServer(CollapsedGroupIngredient ingredient) {
		return true;
	}

	@Override
	public Collection<String> getOreDictNames(CollapsedGroupIngredient ingredient) {
		return Collections.emptyList();
	}

	@Override
	public Collection<String> getCreativeTabNames(CollapsedGroupIngredient ingredient) {
		return Collections.emptyList();
	}

	@Override
	public String getErrorInfo(@Nullable CollapsedGroupIngredient ingredient) {
		if (ingredient == null) {
			return "CollapsibleGroup is null";
		}
		return "CollapsibleGroup[" + ingredient.getId() + ", " + ingredient.size() + " items]";
	}

	// Delegate recipe lookups to the representative item for addon compatibility
	@Override
	public IFocus<?> translateFocus(IFocus<CollapsedGroupIngredient> focus, IFocusFactory focusFactory) {
		CollapsedGroupIngredient cs = focus.getValue();
		if (cs != null && !cs.isEmpty()) {
			Object firstIngredient = cs.getIngredients().get(0).getIngredient();
			return focusFactory.createFocus(focus.getMode(), firstIngredient);
		}
		return focus;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static IIngredientHelper getFirstIngredientHelper(CollapsedGroupIngredient ingredient) {
		if (ingredient.isEmpty()) {
			// Fallback: return ItemStack helper as a safe default
			return Internal.getIngredientRegistry().getIngredientHelper(ItemStack.class);
		}
		Object first = ingredient.getIngredients().get(0).getIngredient();
		return Internal.getIngredientRegistry().getIngredientHelper(first);
	}

	@SuppressWarnings("rawtypes")
	private static Object getFirstIngredient(CollapsedGroupIngredient ingredient) {
		if (ingredient.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return ingredient.getIngredients().get(0).getIngredient();
	}
}

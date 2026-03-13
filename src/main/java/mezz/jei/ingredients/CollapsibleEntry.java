package mezz.jei.ingredients;

import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

/**
 * Defines a collapsible group of ingredients in the ingredient list.
 * When collapsed, all matching ingredients are shown as a single entry.
 * When expanded, all matching ingredients appear individually.
 *
 * The matcher operates on the raw ingredient object so that non-ItemStack
 * ingredient types (e.g. EnchantmentData for enchanted books) can be matched.
 */
public class CollapsibleEntry {
	private final String id;
	private final String displayName;
	/** Matches against the raw ingredient object (any type). */
	private final Predicate<Object> matcher;
	private boolean expanded;

	/**
	 * Primary constructor — matcher receives the raw ingredient object.
	 */
	public CollapsibleEntry(String id, String displayName, Predicate<Object> matcher) {
		this.id = id;
		this.displayName = displayName;
		this.matcher = matcher;
		this.expanded = false;
	}

	/**
	 * Convenience constructor for groups that only care about ItemStack ingredients.
	 * Non-ItemStack ingredients automatically return false.
	 */
	public static CollapsibleEntry ofItemStack(String id, String displayName, Predicate<ItemStack> stackMatcher) {
		return new CollapsibleEntry(id, displayName,
				ingredient -> ingredient instanceof ItemStack && stackMatcher.test((ItemStack) ingredient));
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public void toggleExpanded() {
		this.expanded = !this.expanded;
	}

	/**
	 * Tests whether the given element matches this collapsible group.
	 * The raw ingredient (any type) is passed to the matcher.
	 * Empty ItemStacks are always rejected.
	 */
	public boolean matches(IIngredientListElement<?> element) {
		Object ingredient = element.getIngredient();
		if (ingredient instanceof ItemStack && ((ItemStack) ingredient).isEmpty()) {
			return false;
		}
		return matcher.test(ingredient);
	}
}

package mezz.jei.ingredients;

import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

/**
 * Defines a collapsible group of ingredients in the ingredient list.
 * When collapsed, all matching ingredients are shown as a single entry.
 * When expanded, all matching ingredients appear individually.
 */
public class CollapsibleEntry {
	private final String id;
	private final String displayName;
	private final Predicate<ItemStack> matcher;
	private boolean expanded;

	public CollapsibleEntry(String id, String displayName, Predicate<ItemStack> matcher) {
		this.id = id;
		this.displayName = displayName;
		this.matcher = matcher;
		this.expanded = false;
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
	 * Only ItemStack ingredients are tested; non-ItemStack ingredients never match.
	 */
	public boolean matches(IIngredientListElement<?> element) {
		Object ingredient = element.getIngredient();
		if (ingredient instanceof ItemStack) {
			ItemStack itemStack = (ItemStack) ingredient;
			if (!itemStack.isEmpty()) {
				return matcher.test(itemStack);
			}
		}
		return false;
	}
}

package mezz.jei.ingredients;

import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Represents a collapsible ingredient group — both the group definition (id, display name,
 * matcher, expanded state) and the runtime list of matched ingredients from the current filter.
 * <p>
 * Registered as an {@link IIngredientType} so that addons which introspect grid items
 * always find a valid type. Recipe lookups are delegated to the first ingredient via
 * {@link mezz.jei.api.ingredients.IIngredientHelper#translateFocus}.
 * <p>
 */
public class CollapsedStack {
	// Registered as IIngredientType for addon compatibility — addons expect every grid item to have a type
	public static final IIngredientType<CollapsedStack> TYPE = () -> CollapsedStack.class;

	private final String id;
	private final String displayName;
	/** Matches against the raw ingredient object (any type). */
	private final Predicate<Object> matcher;
	private boolean expanded;
	private final List<IIngredientListElement<?>> ingredients;

	/**
	 * Primary constructor — matcher receives the raw ingredient object.
	 */
	public CollapsedStack(String id, String displayName, Predicate<Object> matcher) {
		this.id = id;
		this.displayName = displayName;
		this.matcher = matcher;
		this.expanded = false;
		this.ingredients = new ArrayList<>();
	}

	/**
	 * Convenience factory for groups that only care about ItemStack ingredients.
	 * Non-ItemStack ingredients automatically return false.
	 */
	public static CollapsedStack ofItemStack(String id, String displayName, Predicate<ItemStack> stackMatcher) {
		return new CollapsedStack(id, displayName,
				ingredient -> ingredient instanceof ItemStack && stackMatcher.test((ItemStack) ingredient));
	}

	// --- Group definition ---

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

	// --- Runtime ingredient list (transient per filter cycle) ---

	public List<IIngredientListElement<?>> getIngredients() {
		return ingredients;
	}

	public void addIngredient(IIngredientListElement<?> element) {
		ingredients.add(element);
	}

	/** Clears the transient ingredient list for reuse across filter recalculations. */
	public void clearIngredients() {
		ingredients.clear();
	}

	public int size() {
		return ingredients.size();
	}

	public boolean isEmpty() {
		return ingredients.isEmpty();
	}
}

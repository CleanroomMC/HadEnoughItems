package mezz.jei.ingredients;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
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

	/** Identifies who registered this group. */
	public enum GroupSource {
		DEFAULT,
		MOD,
		CUSTOM
	}

	private final String id;
	private final String displayName;
	private final GroupSource source;
	/** Matches against the raw ingredient object (any type). */
	private final Predicate<Object> matcher;
	/**
	 * Optional fast-path matcher that receives a pre-computed UID string instead of the raw
	 * ingredient.  Set on custom groups by {@link CollapsedStackRegistry} so that
	 * {@link mezz.jei.ingredients.IngredientFilter#collapse} can skip calling
	 * {@code getUniqueIdentifierForStack()} N×M times per filter cycle.
	 */
	@Nullable private Predicate<String> uidMatcher;
	private boolean expanded;
	private final List<IIngredientListElement<?>> ingredients;

	/**
	 * Primary constructor — matcher receives the raw ingredient object.
	 */
	public CollapsedStack(String id, String displayName, Predicate<Object> matcher) {
		this(id, displayName, matcher, GroupSource.DEFAULT);
	}

	/**
	 * Constructor with explicit group source.
	 */
	public CollapsedStack(String id, String displayName, Predicate<Object> matcher, GroupSource source) {
		this.id = id;
		this.displayName = displayName;
		this.matcher = matcher;
		this.source = source;
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

	/**
	 * Convenience factory for ItemStack groups with an explicit source.
	 */
	public static CollapsedStack ofItemStack(String id, String displayName, Predicate<ItemStack> stackMatcher, GroupSource source) {
		return new CollapsedStack(id, displayName,
				ingredient -> ingredient instanceof ItemStack && stackMatcher.test((ItemStack) ingredient), source);
	}

	// --- Group definition ---

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public GroupSource getSource() {
		return source;
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

	/** Returns the UID-based matcher, or {@code null} if this group uses a raw-ingredient predicate. */
	@Nullable
	public Predicate<String> getUidMatcher() {
		return uidMatcher;
	}

	public void setUidMatcher(Predicate<String> uidMatcher) {
		this.uidMatcher = uidMatcher;
	}

	/**
	 * Computes a unique identifier string for any ingredient type.
	 * Returns {@code null} if the ingredient is empty or an error occurs.
	 * Used by {@code collapse()} to precompute UIDs once per element.
	 */
	@Nullable
	public static String computeIngredientUid(Object ingredient) {
		if (ingredient instanceof ItemStack) {
			ItemStack stack = (ItemStack) ingredient;
			if (stack.isEmpty()) return null;
			try {
				return Internal.getStackHelper().getUniqueIdentifierForStack(stack);
			} catch (Exception e) {
				return null;
			}
		}
		try {
			@SuppressWarnings("unchecked")
			IIngredientHelper<Object> helper = (IIngredientHelper<Object>)
					Internal.getIngredientRegistry().getIngredientHelper(ingredient);
			return helper.getUniqueId(ingredient);
		} catch (Exception e) {
			return null;
		}
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

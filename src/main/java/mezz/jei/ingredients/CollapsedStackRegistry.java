package mezz.jei.ingredients;

import mezz.jei.Internal;
import mezz.jei.config.Config;
import mezz.jei.config.CustomGroupsConfig;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.startup.StackHelper;
import mezz.jei.util.Log;
import net.minecraft.item.ItemStack;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * Registry for CollapsedStack group definitions.
 * Groups are checked in registration order; first match wins.
 */
public class CollapsedStackRegistry {
	@Nullable
	private static CollapsedStackRegistry instance;

	private final LinkedHashMap<String, CollapsedStack> entries = new LinkedHashMap<>();
	private final List<CollapsedStack> customEntries = new ArrayList<>();
	private final Set<String> disabledGroups = new HashSet<>();

	public static CollapsedStackRegistry getInstance() {
		if (instance == null) {
			instance = new CollapsedStackRegistry();
		}
		return instance;
	}

	public static void setInstance(@Nullable CollapsedStackRegistry registry) {
		instance = registry;
	}

	/**
	 * Register a collapsible group whose membership is determined by an ItemStack predicate.
	 * Non-ItemStack ingredients are automatically excluded.
	 *
	 * @param id          unique identifier for the group
	 * @param displayName localized display name
	 * @param matcher     predicate that returns true for ItemStacks belonging to this group
	 */
	public void group(String id, String displayName, Predicate<ItemStack> matcher) {
		entries.put(id, CollapsedStack.ofItemStack(id, displayName, matcher));
	}

	/**
	 * Register a collapsible group whose membership is determined by a predicate on the
	 * raw ingredient object. Use this when the ingredients are not ItemStacks
	 * (e.g. EnchantmentData for enchanted books).
	 *
	 * @param id          unique identifier for the group
	 * @param displayName localized display name
	 * @param matcher     predicate on the raw ingredient object
	 */
	public void groupForType(String id, String displayName, Predicate<Object> matcher) {
		entries.put(id, new CollapsedStack(id, displayName, matcher));
	}

	public Collection<CollapsedStack> getEntries() {
		return entries.values();
	}

	@Nullable
	public CollapsedStack getEntry(String id) {
		return entries.get(id);
	}

	public void clear() {
		entries.clear();
	}

	public Set<String> getDisabledGroups() {
		return disabledGroups;
	}

	public void setDisabledGroups(Collection<String> disabled) {
		this.disabledGroups.clear();
		this.disabledGroups.addAll(disabled);
	}

	public boolean isGroupEnabled(String id) {
		return !disabledGroups.contains(id);
	}

	public List<CollapsedStack> getCustomEntries() {
		return customEntries;
	}

	/**
	 * Load custom collapsible groups from the JSON config.
	 * Creates CollapsedStack objects that match items by their unique identifier.
	 */
	public void loadCustomGroups() {
		customEntries.clear();
		CustomGroupsConfig customGroupsConfig = Config.getCustomGroupsConfig();
		if (customGroupsConfig == null) {
			return;
		}
		for (CustomGroupsConfig.CustomGroup group : customGroupsConfig.getCustomGroups()) {
			if (group.id == null || group.id.isEmpty() || group.itemUids == null) {
				continue;
			}
			// Split stored UIDs into exact matches and wildcard prefixes (stored as "prefix:*")
			Set<String> exactUids = new HashSet<>();
			Set<String> wildcardPrefixes = new HashSet<>();
			for (String uid : group.itemUids) {
				if (uid.endsWith(":*")) {
					wildcardPrefixes.add(uid.substring(0, uid.length() - 2));
				} else {
					exactUids.add(uid);
				}
			}
			String displayName = group.displayName != null ? group.displayName : group.id;
			// Matcher works for both ItemStack and non-ItemStack ingredients (e.g. FluidStack):
			// for ItemStacks use StackHelper, for everything else use the generic IngredientRegistry helper.
			customEntries.add(new CollapsedStack(group.id, displayName, ingredient -> {
				try {
					String uid;
					if (ingredient instanceof ItemStack) {
						ItemStack stack = (ItemStack) ingredient;
						if (stack.isEmpty()) return false;
						uid = Internal.getStackHelper().getUniqueIdentifierForStack(stack);
					} else {
						@SuppressWarnings("unchecked")
						mezz.jei.api.ingredients.IIngredientHelper<Object> helper =
								(mezz.jei.api.ingredients.IIngredientHelper<Object>)
								Internal.getIngredientRegistry().getIngredientHelper(ingredient);
						uid = helper.getUniqueId(ingredient);
					}
					if (exactUids.contains(uid)) return true;
					// Check wildcard prefix: "minecraft:iron_pickaxe" matches "minecraft:iron_pickaxe:5" etc.
					for (String prefix : wildcardPrefixes) {
						if (uid.equals(prefix) || uid.startsWith(prefix + ":")) return true;
					}
					return false;
				} catch (Exception e) {
					return false;
				}
			}));
		}
		Log.get().debug("Loaded {} custom collapsible groups", customEntries.size());
	}

	/**
	 * Reload custom entries from config. Called after saving changes.
	 */
	public void recollectCustomEntries() {
		loadCustomGroups();
	}

	/**
	 * Sync disabled group state from Config values.
	 */
	public void syncDisabledGroups() {
		this.disabledGroups.clear();
		this.disabledGroups.addAll(Config.getDisabledGroups());
	}
}

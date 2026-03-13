package mezz.jei.ingredients;

import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * Registry holding all collapsible entry group definitions.
 * Groups are checked in registration order; first match wins.
 */
public class CollapsibleEntryRegistry {
	@Nullable
	private static CollapsibleEntryRegistry instance;

	private final LinkedHashMap<String, CollapsibleEntry> entries = new LinkedHashMap<>();
	private final Set<String> disabledGroups = new HashSet<>();

	public static CollapsibleEntryRegistry getInstance() {
		if (instance == null) {
			instance = new CollapsibleEntryRegistry();
		}
		return instance;
	}

	public static void setInstance(@Nullable CollapsibleEntryRegistry registry) {
		instance = registry;
	}

	/**
	 * Register a collapsible group with a predicate matcher.
	 * @param id unique identifier for the group
	 * @param displayName localized display name
	 * @param matcher predicate that returns true for ItemStacks belonging to this group
	 */
	public void group(String id, String displayName, Predicate<ItemStack> matcher) {
		entries.put(id, new CollapsibleEntry(id, displayName, matcher));
	}

	public Collection<CollapsibleEntry> getEntries() {
		return entries.values();
	}

	@Nullable
	public CollapsibleEntry getEntry(String id) {
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
}

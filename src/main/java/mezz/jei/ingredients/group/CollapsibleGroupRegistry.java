package mezz.jei.ingredients.group;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mezz.jei.Internal;
import mezz.jei.api.ICollapsibleGroupRegistry;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.config.Config;
import mezz.jei.config.CustomGroupsConfig;
import mezz.jei.util.Log;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CollapsibleGroupRegistry implements ICollapsibleGroupRegistry {

    private final Map<String, CollapsibleGroup> groups = new Object2ObjectOpenHashMap<>();

    @Override
    public Builder newGroup(String id, String langKey) {
        return new Builder(this, id, langKey);
    }

    public void setEnabled(boolean enabled, String group) {
        CollapsibleGroup collapsibleGroup = this.groups.get(group);
        if (collapsibleGroup != null) {
            collapsibleGroup.setEnabled(enabled);
        }
    }

    public void setEnabled(boolean enabled, List<String> groups) {
        for (String group : groups) {
            this.setEnabled(enabled, group);
        }
    }

    public void setExpandedOnAllGroups(boolean expanded) {
        this.groups.forEach((id, group) -> group.getIngredient().setExpanded(expanded));
    }

    public void closeAllGroups() {
        this.setExpandedOnAllGroups(false);
    }

    public void expandOrCloseAll() {
        this.setExpandedOnAllGroups(this.groups.values().stream().map(CollapsibleGroup::getIngredient).allMatch(CollapsedGroupIngredient::isExpanded));
    }

    public boolean isGroupDisabled(String group) {
        CollapsibleGroup collapsibleGroup = this.groups.get(group);
        return collapsibleGroup == null || !collapsibleGroup.isEnabled();
    }

    public Map<String, CollapsibleGroup> getAllGroups() {
        return Collections.unmodifiableMap(this.groups);
    }

    public Collection<String> getDisabledGroups() {
        return groups.entrySet().stream()
                .filter(entry -> !entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public void loadCustomGroups() {
        this.groups.entrySet().removeIf(entry -> entry.getValue().getIngredient().getSource() == CollapsedGroupIngredient.GroupSource.CUSTOM);

        CustomGroupsConfig customGroupsConfig = Config.getCustomGroupsConfig();
        if (customGroupsConfig == null) {
            return;
        }
        int amount = 0;
        for (CustomGroupsConfig.CustomGroup group : customGroupsConfig.getCustomGroups()) {
            if (group.id == null || group.id.isEmpty() || group.itemUids == null) {
                continue;
            }
            List<Object> ingredients = new ArrayList<>();
            Set<String> ingredientUids = new HashSet<>(group.itemUids);
            CollapsedGroupIngredient ingredient = new CollapsedGroupIngredient(group.id, group.displayName, ingredients, ingredientUids, CollapsedGroupIngredient.GroupSource.CUSTOM);
            this.groups.put(group.id, new CollapsibleGroup(ingredient));
            amount++;
        }
        Log.get().info("Loaded {} custom collapsible groups", amount);
    }

    public static class Builder implements ICollapsibleGroupRegistry.Builder {

        private final CollapsibleGroupRegistry registry;
        private final String id;
        private final String langKey;
        private final List<Object> ingredients = new ArrayList<>();
        private final Set<String> ingredientUids = new ObjectOpenHashSet<>();

        public Builder(CollapsibleGroupRegistry registry, String id, String langKey) {
            this.registry = registry;
            this.id = id;
            this.langKey = langKey;
        }

        @Override
        public ICollapsibleGroupRegistry.Builder add(Object... ingredients) {
            IIngredientRegistry registry = Internal.getIngredientRegistry();
            for (Object ingredient : ingredients) {
                this.ingredients.add(ingredient);
                this.ingredientUids.add(registry.getIngredientHelper(ingredient).getUniqueId(ingredient));
            }
            return this;
        }

        @Override
        public ICollapsibleGroupRegistry.Builder addAllOf(IIngredientType<?>... types) {
            IIngredientRegistry registry = Internal.getIngredientRegistry();
            for (IIngredientType type : types) {
                for (Object ingredient : registry.getAllIngredients(type)) {
                    this.ingredients.add(ingredient);
                    this.ingredientUids.add(registry.getIngredientHelper(ingredient).getUniqueId(ingredient));
                }
            }
            return this;
        }

        @Override
        public <V> ICollapsibleGroupRegistry.Builder addAny(IIngredientType<V> type, Predicate<V> filter) {
            IIngredientRegistry registry = Internal.getIngredientRegistry();
            for (V ingredient : registry.getAllIngredients(type)) {
                if (filter.test(ingredient)) {
                    this.ingredients.add(ingredient);
                    this.ingredientUids.add(registry.getIngredientHelper(ingredient).getUniqueId(ingredient));
                }
            }
            return this;
        }

        @Override
        public void build() {
            this.registry.groups.put(this.id,
                    new CollapsibleGroup(
                            new CollapsedGroupIngredient(this.id, this.langKey, this.ingredients, this.ingredientUids)));
        }

    }

}

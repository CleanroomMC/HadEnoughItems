package mezz.jei.startup;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IModIngredientRegistration;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import mezz.jei.ingredients.IngredientBlacklistInternal;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.IngredientSet;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ModIngredientRegistration implements IModIngredientRegistration, IIngredientAliasRegistration {
	private final Map<IIngredientType, Collection> allIngredientsMap = new Reference2ObjectLinkedOpenHashMap<>();
	private final Map<IIngredientType, IIngredientHelper> ingredientHelperMap = new Reference2ObjectOpenHashMap<>();
	private final Map<IIngredientType, IIngredientRenderer> ingredientRendererMap = new Reference2ObjectOpenHashMap<>();
	private final Set<IIngredientType<?>> craftableIngredientTypes = new ObjectLinkedOpenHashSet<>();
	private final Map<IIngredientType, ListMultimap<String, String>> ingredientAliasesMap = new Reference2ObjectOpenHashMap<>();
	private final Map<IIngredientType, ListMultimap<String, String>> ingredientSubtypeAliasesMap = new Reference2ObjectOpenHashMap<>();

	@Override
	public <V> void register(IIngredientType<V> ingredientType, Collection<V> allIngredients, IIngredientHelper<V> ingredientHelper, IIngredientRenderer<V> ingredientRenderer) {
		ErrorUtil.checkNotNull(ingredientType, "ingredientType");
		ErrorUtil.checkNotNull(allIngredients, "allIngredients");
		ErrorUtil.checkNotNull(ingredientHelper, "ingredientHelper");
		ErrorUtil.checkNotNull(ingredientRenderer, "ingredientRenderer");

		allIngredientsMap.put(ingredientType, allIngredients);
		ingredientHelperMap.put(ingredientType, ingredientHelper);
		ingredientRendererMap.put(ingredientType, ingredientRenderer);
	}

	@Override
	public <V> void markAsCraftable(IIngredientType<V> ingredientType) {
		ErrorUtil.checkNotNull(ingredientType, "ingredientType");
		craftableIngredientTypes.add(ingredientType);
	}

	@Override
	@Deprecated
	public <V> void register(Class<V> ingredientClass, Collection<V> allIngredients, IIngredientHelper<V> ingredientHelper, IIngredientRenderer<V> ingredientRenderer) {
		ErrorUtil.checkNotNull(ingredientClass, "ingredientClass");
		register(() -> ingredientClass, allIngredients, ingredientHelper, ingredientRenderer);
	}

	@Override
	public <I> void addAliasToAllSubtypes(IIngredientType<I> type, I ingredient, String alias) {
		addAliasesToAllSubtypes(type, ingredient, Collections.singleton(alias));
	}

	@Override
	public <I> void addAliasesToAllSubtypes(IIngredientType<I> type, I ingredient, Collection<String> aliases) {
		ErrorUtil.checkNotNull(type, "type");
		ErrorUtil.checkNotNull(ingredient, "ingredient");
		ErrorUtil.checkNotNull(aliases, "aliases");

		String wildcardId = getIngredientHelper(type).getWildcardId(ingredient);
		ingredientSubtypeAliasesMap.computeIfAbsent(type, k -> ArrayListMultimap.create()).putAll(wildcardId, aliases);
	}

	@Override
	public <I> void addAlias(IIngredientType<I> type, I ingredient, String alias) {
		addAliases(type, ingredient, Collections.singleton(alias));
	}

	@Override
	public <I> void addAliases(IIngredientType<I> type, I ingredient, Collection<String> aliases) {
		ErrorUtil.checkNotNull(type, "type");
		ErrorUtil.checkNotNull(ingredient, "ingredient");
		ErrorUtil.checkNotNull(aliases, "aliases");

		String uid = getIngredientHelper(type).getUniqueId(ingredient);
		ingredientAliasesMap.computeIfAbsent(type, k -> ArrayListMultimap.create()).putAll(uid, aliases);
	}

	@Override
	public <I> void addAliases(IIngredientType<I> type, Collection<I> ingredients, String alias) {
		addAliases(type, ingredients, Collections.singleton(alias));
	}

	@Override
	public <I> void addAliases(IIngredientType<I> type, Collection<I> ingredients, Collection<String> aliases) {
		ErrorUtil.checkNotNull(ingredients, "ingredients");
		for (I ingredient : ingredients) {
			addAliases(type, ingredient, aliases);
		}
	}

	private <T> IIngredientHelper<T> getIngredientHelper(IIngredientType<T> ingredientType) {
		@SuppressWarnings("unchecked")
		IIngredientHelper<T> ingredientHelper = ingredientHelperMap.get(ingredientType);
		if (ingredientHelper == null) {
			throw new IllegalArgumentException("Unknown ingredient type: " + ingredientType.getIngredientClass());
		}
		return ingredientHelper;
	}

	public IngredientRegistry createIngredientRegistry(IModIdHelper modIdHelper, IngredientBlacklistInternal blacklist) {
		Map<IIngredientType, IngredientSet> ingredientsMap = new Reference2ObjectLinkedOpenHashMap<>();
		for (Map.Entry<IIngredientType, Collection> entry : allIngredientsMap.entrySet()) {
			IIngredientType ingredientType = entry.getKey();
			@SuppressWarnings("unchecked")
			IngredientSet ingredientSet = createIngredientSet(ingredientType, entry.getValue());
			ingredientsMap.put(ingredientType, ingredientSet);
		}

		return new IngredientRegistry(
			modIdHelper,
			blacklist,
			ingredientsMap,
			ImmutableMap.copyOf(ingredientHelperMap),
			ImmutableMap.copyOf(ingredientRendererMap),
			new ObjectLinkedOpenHashSet<>(craftableIngredientTypes),
			ImmutableMap.copyOf(ingredientAliasesMap),
			ImmutableMap.copyOf(ingredientSubtypeAliasesMap)
		);
	}

	private <T> IngredientSet<T> createIngredientSet(IIngredientType<T> ingredientType, Collection<T> ingredients) {
		@SuppressWarnings("unchecked")
		IIngredientHelper<T> ingredientHelper = ingredientHelperMap.get(ingredientType);
		IngredientSet<T> ingredientSet = IngredientSet.create(ingredientType, ingredientHelper);
		ingredientSet.addAll(ingredients);
		return ingredientSet;
	}
}

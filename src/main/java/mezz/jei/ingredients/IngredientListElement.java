package mezz.jei.ingredients;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.startup.IModIdHelper;
import mezz.jei.startup.ProxyCommonClient;
import mezz.jei.util.LegacyUtil;
import mezz.jei.util.Log;
import mezz.jei.util.StringUtil;
import mezz.jei.util.Translator;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IngredientListElement<V> implements IIngredientListElement<V> {
	private static final Pattern SPACE_PATTERN = Pattern.compile("\\s");

	private final V ingredient;
	private final int orderIndex;
	private final IIngredientHelper<V> ingredientHelper;
	private final IIngredientRenderer<V> ingredientRenderer;
	private final Object modIds; // Can be String or String[]
	private final Object modNames; // Can be String or String[]

	private boolean visible = true;

	@Nullable
	public static <V> IngredientListElement<V> create(V ingredient, IIngredientHelper<V> ingredientHelper, IIngredientRenderer<V> ingredientRenderer, IModIdHelper modIdHelper, int orderIndex) {
		try {
			return new IngredientListElement<>(ingredient, orderIndex, ingredientHelper, ingredientRenderer, modIdHelper);
		} catch (RuntimeException e) {
			try {
				String ingredientInfo = ingredientHelper.getErrorInfo(ingredient);
				Log.get().warn("Found a broken ingredient {}", ingredientInfo, e);
			} catch (RuntimeException e2) {
				Log.get().warn("Found a broken ingredient.", e2);
			}
			return null;
		}
	}

	protected IngredientListElement(V ingredient, int orderIndex, IIngredientHelper<V> ingredientHelper, IIngredientRenderer<V> ingredientRenderer, IModIdHelper modIdHelper) {
		this.ingredient = ingredient;
		this.orderIndex = orderIndex;
		this.ingredientHelper = ingredientHelper;
		this.ingredientRenderer = ingredientRenderer;
		String displayModId = ingredientHelper.getDisplayModId(ingredient);
		String modId = ingredientHelper.getModId(ingredient);
		if (modId.equals(displayModId)) {
			this.modIds = StringUtil.intern(modId);
			this.modNames = StringUtil.intern(modIdHelper.getModNameForModId(modId));
		} else {
			this.modIds = new String[] { StringUtil.intern(modId), StringUtil.intern(displayModId) };
			String modIdName = modIdHelper.getModNameForModId(modId);
			String displayModIdName = modIdHelper.getModNameForModId(displayModId);
			if (modIdName.equals(displayModIdName)) {
				this.modNames = StringUtil.intern(modIdName);
			} else {
				this.modNames = new String[] { StringUtil.intern(modIdName), StringUtil.intern(displayModIdName) };
			}
		}
	}

	@Override
	public final V getIngredient() {
		return ingredient;
	}

	@Override
	public int getOrderIndex() {
		return orderIndex;
	}

	@Override
	public IIngredientHelper<V> getIngredientHelper() {
		return ingredientHelper;
	}

	@Override
	public IIngredientRenderer<V> getIngredientRenderer() {
		return ingredientRenderer;
	}

	@Override
	public final String getDisplayName() {
		return IngredientInformation.getDisplayName(ingredient, ingredientHelper);
	}

	@Override
	public String getModNameForSorting() {
		return this.modNames instanceof String ? (String) this.modNames : ((String[]) this.modNames)[0];
	}

	@Override
	public Set<String> getModNameStrings() {
		Set<String> modNameStrings = new ObjectArraySet<>();
		if (this.modIds instanceof String) {
			addModIdStrings(modNameStrings, (String) this.modIds);
		} else {
			String[] modIdsCasted = (String[]) this.modIds;
            for (String modId : modIdsCasted) {
                addModIdStrings(modNameStrings, modId);
            }
		}
		if (this.modNames instanceof String) {
			addModNameStrings(modNameStrings, (String) this.modNames);
		} else {
			String[] modNamesCasted = (String[]) this.modNames;
			for (String modName : modNamesCasted) {
				addModNameStrings(modNameStrings, modName);
			}
		}
		return modNameStrings;
	}

	private static void addModIdStrings(Set<String> modNames, String modId) {
		String modIdNoSpaces = SPACE_PATTERN.matcher(modId).replaceAll("");
		modNames.add(modId);
		modNames.add(modIdNoSpaces);
	}

	private static void addModNameStrings(Set<String> modNames, String modName) {
		String modNameLowercase = modName.toLowerCase(Locale.ENGLISH);
		String modNameNoSpaces = SPACE_PATTERN.matcher(modNameLowercase).replaceAll("");
		modNames.add(modNameNoSpaces);
	}

	@Override
	public final List<String> getTooltipStrings() {
		String modId = this.modIds instanceof String ? (String) this.modIds : ((String[]) this.modIds)[0];
		String modName = this.modNames instanceof String ? (String) this.modNames : ((String[]) this.modNames)[0];
		String modNameLowercase = modName.toLowerCase(Locale.ENGLISH);
		String displayNameLowercase = Translator.toLowercaseWithLocale(this.getDisplayName());
		return IngredientInformation.getTooltipStrings(ingredient, ingredientRenderer, ImmutableSet.of(modId, modNameLowercase, displayNameLowercase, this.getResourceId()));
	}

	@Override
	public Collection<String> getOreDictStrings() {
		Collection<String> oreDictNames = ingredientHelper.getOreDictNames(ingredient);
		return oreDictNames.stream()
			.map(s -> s.toLowerCase(Locale.ENGLISH))
			.collect(Collectors.toList());
	}

	@Override
	public Collection<String> getCreativeTabsStrings() {
		Collection<String> creativeTabsStrings = ingredientHelper.getCreativeTabNames(ingredient);
		return creativeTabsStrings.stream()
			.map(Translator::toLowercaseWithLocale)
			.collect(Collectors.toList());
	}

	@Override
	public Collection<String> getColorStrings() {
		return IngredientInformation.getColorStrings(ingredient, ingredientHelper);
	}

	@Override
	public String getResourceId() {
		return LegacyUtil.getResourceId(ingredient, ingredientHelper);
	}

	@Override
	public boolean isVisible() {
		if (visible) {
			return true;
		}
		if (FMLLaunchHandler.side().isClient()) {
			return Config.getShowHiddenIngredientsInCreative() && ProxyCommonClient.isCreative() &&
					!Internal.getHelpers().getIngredientBlacklist().isIngredientBlacklistedByApi(ingredient);
		}
		return false;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public int getGroupIndex() {
		if (ingredient instanceof BookmarkItem<?> && ((BookmarkItem<?>) ingredient).getGroup() != null) {
			return ((BookmarkItem<?>) ingredient).getGroup().id;
		}
		return 0;
	}

	@Override
	public boolean startsNewRow() {
		if (ingredient instanceof BookmarkItem) {
			return ((BookmarkItem<?>) ingredient).startsNewRow();
		}
		return false;
	}

	@Override
	public int getOrdinal() {
		return ingredientHelper.getOrdinal(ingredient);
	}
}

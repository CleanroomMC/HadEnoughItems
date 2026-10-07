package mezz.jei.plugins.vanilla.ingredients.item;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.color.ColorGetter;
import mezz.jei.startup.StackHelper;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.Log;
import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ItemStackHelper implements IIngredientHelper<ItemStack> {
	private final StackHelper stackHelper;
	private final Object2IntMap<ItemStack> hashCache = new Object2IntOpenHashMap<>();

	public ItemStackHelper(StackHelper stackHelper) {
		this.stackHelper = stackHelper;
	}

	@Override
	public List<ItemStack> expandSubtypes(List<ItemStack> contained) {
		return stackHelper.getAllSubtypes(contained);
	}

	@Override
	public IFocus<?> translateFocus(IFocus<ItemStack> focus, IFocusFactory focusFactory) {
		ItemStack itemStack = focus.getValue();
		Item item = itemStack.getItem();
		// Special case for ItemBlocks containing fluid blocks.
		// Nothing crafts those, the player probably wants to look up fluids.
		if (item instanceof ItemBlock) {
			Block block = ((ItemBlock) item).getBlock();
			Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
			if (fluid != null) {
				FluidStack fluidStack = new FluidStack(fluid, Fluid.BUCKET_VOLUME);
				return focusFactory.createFocus(focus.getMode(), fluidStack);
			}
		}
		return focus;
	}

	@Override
	@Nullable
	public ItemStack getMatch(Iterable<ItemStack> ingredients, ItemStack toMatch) {
		return stackHelper.containsStack(ingredients, toMatch);
	}

	@Override
	public String getDisplayName(ItemStack ingredient) {
		return ErrorUtil.checkNotNull(ingredient.getDisplayName(), "itemStack.getDisplayName()");
	}

	@Override
	public String getUniqueId(ItemStack ingredient) {
		ErrorUtil.checkNotEmpty(ingredient);
		return stackHelper.getUniqueIdentifierForStack(ingredient);
	}

	@Override
	public String getFullUniqueId(ItemStack ingredient) {
		ErrorUtil.checkNotEmpty(ingredient);
		return stackHelper.getUniqueIdentifierForStack(ingredient, StackHelper.UidMode.FULL);
	}

	@Override
	public int getHash(ItemStack ingredient) {
		Integer cachedHash = hashCache.get(ingredient);
		if (cachedHash != null) {
			return cachedHash;
		}
		int hash = ingredient.getCount() * 31 + ingredient.getItemDamage();
		hash = hash * 31 + ingredient.getItem().getRegistryName().hashCode();
		NBTTagCompound tag = ingredient.getTagCompound();
		if (tag != null) {
			try {
				hash = hash * 31 + tag.hashCode();
			} catch (StackOverflowError e) {
				Log.get().error("Stack overflow while hashing ItemStack ingredient: {}", ingredient.getItem().getRegistryName(), e);
			}
		}
		hashCache.put(ingredient, hash);
		return hash;
	}

	@Override
	public String getWildcardId(ItemStack ingredient) {
		ErrorUtil.checkNotEmpty(ingredient);
		return stackHelper.getUniqueIdentifierForStack(ingredient, StackHelper.UidMode.WILDCARD);
	}

	@Override
	public String getModId(ItemStack ingredient) {
		ErrorUtil.checkNotEmpty(ingredient);

		Item item = ingredient.getItem();
		ResourceLocation itemName = item.getRegistryName();
		if (itemName == null) {
			String stackInfo = getErrorInfo(ingredient);
			throw new IllegalStateException("item.getRegistryName() returned null for: " + stackInfo);
		}

		return itemName.getNamespace();
	}

	@Override
	public String getDisplayModId(ItemStack ingredient) {
		ErrorUtil.checkNotEmpty(ingredient);

		Item item = ingredient.getItem();
		String modId = item.getCreatorModId(ingredient);
		if (modId == null) {
			String stackInfo = getErrorInfo(ingredient);
			throw new IllegalStateException("item.getCreatorModId() returned null for: " + stackInfo);
		}
		return modId;
	}

	@Override
	public Iterable<Color> getColors(ItemStack ingredient) {
		return ColorGetter.getColors(ingredient, 2);
	}

	@Override
	public String getResourceId(ItemStack ingredient) {
		ErrorUtil.checkNotEmpty(ingredient);

		Item item = ingredient.getItem();
		ResourceLocation itemName = item.getRegistryName();
		if (itemName == null) {
			String stackInfo = getErrorInfo(ingredient);
			throw new IllegalStateException("item.getRegistryName() returned null for: " + stackInfo);
		}

		return itemName.getPath();
	}

	@Override
	public int getOrdinal(ItemStack ingredient) {
		return ingredient.getMetadata();
	}

	@Override
	public ItemStack getCheatItemStack(ItemStack ingredient) {
		return ingredient;
	}

	@Override
	public ItemStack copyIngredient(ItemStack ingredient) {
		return ingredient.copy();
	}

	@Override
	public boolean isValidIngredient(ItemStack ingredient) {
		return !ingredient.isEmpty();
	}

	@Override
	public boolean isIngredientOnServer(ItemStack ingredient) {
		Item item = ingredient.getItem();
		return ForgeRegistries.ITEMS.containsValue(item);
	}

	@Override
	public Collection<String> getOreDictNames(ItemStack ingredient) {
		int[] oreIds = OreDictionary.getOreIDs(ingredient);
		Collection<String> names = new ArrayList<>(oreIds.length);
		for (int oreId : oreIds) {
			names.add(OreDictionary.getOreName(oreId));
		}
		return names;
	}

	@Override
	public Collection<String> getCreativeTabNames(ItemStack ingredient) {
		Collection<String> creativeTabsStrings = new ArrayList<>();
		Item item = ingredient.getItem();
		for (CreativeTabs creativeTab : item.getCreativeTabs()) {
			if (creativeTab != null) {
				String creativeTabName = I18n.format(creativeTab.getTranslationKey());
				creativeTabsStrings.add(creativeTabName);
			}
		}
		return creativeTabsStrings;
	}

	@Override
	public String getErrorInfo(@Nullable ItemStack ingredient) {
		return ErrorUtil.getItemStackInfo(ingredient);
	}
}

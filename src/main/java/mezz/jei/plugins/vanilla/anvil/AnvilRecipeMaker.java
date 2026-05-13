package mezz.jei.plugins.vanilla.anvil;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.IVanillaRecipeFactory;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.Log;
import net.minecraftforge.oredict.OreDictionary;

public final class AnvilRecipeMaker {
	private AnvilRecipeMaker() {
	}

	public static List<IRecipeWrapper> getAnvilRecipes(IVanillaRecipeFactory vanillaRecipeFactory, IIngredientRegistry ingredientRegistry) {
		Stopwatch sw = Stopwatch.createStarted();
		return Stream.concat(
						wrapStream("vanilla repair", () -> getRepairRecipes(vanillaRecipeFactory), sw),
						wrapStream("enchantment", () -> getBookEnchantmentRecipes(ingredientRegistry), sw))
				.collect(Collectors.toList());
	}

	private static <T> Stream<T> wrapStream(String name, Supplier<Stream<T>> supplier, Stopwatch stopwatch) {
		stopwatch.reset();
		stopwatch.start();
		try {
			List<T> data = supplier.get().collect(Collectors.toList());
			Log.get().debug("Registered {} recipes in {}", name, stopwatch);
			return data.stream();
		} catch (RuntimeException e) {
			Log.get().error("Failed to create {} recipes.", name, e);
			return Stream.empty();
		} finally {
			stopwatch.stop();
		}
	}

	/* Enchantment recipes */

	private static final class EnchantmentData {
		private final Enchantment enchantment;
		// Books per enchantment level
		private final List<ItemStack> enchantedBooks;

		EnchantmentData(Enchantment enchantment) {
			this.enchantment = enchantment;
			this.enchantedBooks = enumerateBooks(enchantment);
		}

		@SuppressWarnings("UnstableApiUsage")
		public List<ItemStack> getEnchantedBooks(ItemStack ingredient) {
			Item item = ingredient.getItem();
			List<ItemStack> list = enchantedBooks.stream()
					.filter(enchantedBook -> item.isBookEnchantable(ingredient, enchantedBook))
					.collect(ImmutableList.toImmutableList());
			// avoid using copy of list if it contains the exact same items
			return list.size() == enchantedBooks.size() ? enchantedBooks : list;
		}

		private boolean canEnchant(ItemStack ingredient) {
			try {
				return enchantment.canApply(ingredient);
			} catch (RuntimeException e) {
				String stackInfo = ErrorUtil.getItemStackInfo(ingredient);
				Log.get().error("Failed to check if ingredient can be enchanted: {}", stackInfo, e);
				return false;
			}
		}

		@SuppressWarnings("UnstableApiUsage")
		private static List<ItemStack> enumerateBooks(Enchantment enchantment) {
			return IntStream.rangeClosed(enchantment.getMinLevel(), enchantment.getMaxLevel())
					.mapToObj(level -> ItemEnchantedBook.getEnchantedItemStack(new net.minecraft.enchantment.EnchantmentData(enchantment, level)))
					.collect(ImmutableList.toImmutableList());
		}
	}

	private static Stream<IRecipeWrapper> getBookEnchantmentRecipes(IIngredientRegistry ingredientRegistry) {
		List<EnchantmentData> enchantmentDatas = ForgeRegistries.ENCHANTMENTS.getValuesCollection().stream()
				.map(EnchantmentData::new)
				.collect(Collectors.toList());
		Collection<ItemStack> ingredients = ingredientRegistry.getAllIngredients(VanillaTypes.ITEM);
		return ingredients.stream()
				.filter(ItemStack::isItemEnchantable)
				.flatMap(ingredient -> getBookEnchantmentRecipes(enchantmentDatas, ingredient));
	}

	private static Stream<IRecipeWrapper> getBookEnchantmentRecipes(List<EnchantmentData> enchantmentDatas, ItemStack ingredient) {
		List<ItemStack> ingredientSingletonList = ImmutableList.of(ingredient);
		return enchantmentDatas.stream()
				.filter(data -> data.canEnchant(ingredient))
				.map(data -> data.getEnchantedBooks(ingredient))
				.filter(enchantedBooks -> !enchantedBooks.isEmpty())
				.map(enchantedBooks -> {
					List<ItemStack> outputs = getEnchantedIngredients(ingredient, enchantedBooks);
					// All lists here are immutable, except outputs which is a transforming list,
					// so we call the constructor directly
					return new AnvilRecipeWrapper(ingredientSingletonList, enchantedBooks, outputs);
				});
	}

	private static List<ItemStack> getEnchantedIngredients(ItemStack ingredient, List<ItemStack> enchantedBooks) {
		return Lists.transform(enchantedBooks, enchantedBook -> getEnchantedIngredient(ingredient, enchantedBook));
	}

	private static ItemStack getEnchantedIngredient(ItemStack ingredient, ItemStack enchantedBook) {
		ItemStack enchantedIngredient = ingredient.copy();
		Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(enchantedBook);
		EnchantmentHelper.setEnchantments(enchantments, enchantedIngredient);
		return enchantedIngredient;
	}

	/* Repair recipes */

	private static class RepairData {
		private final ItemStack repairIngredient;
		private final List<ItemStack> repairables;

		public RepairData(ItemStack repairIngredient, ItemStack... repairables) {
			this.repairIngredient = repairIngredient;
			this.repairables = Collections.unmodifiableList(Arrays.asList(repairables));
		}

		public ItemStack getRepairStack() {
			return repairIngredient;
		}

		public List<ItemStack> getRepairables() {
			return repairables;
		}
	}

	private static Stream<RepairData> getRepairData() {
		return Stream.of(
				new RepairData(Item.ToolMaterial.WOOD.getRepairItemStack(),
						new ItemStack(Items.WOODEN_SWORD),
						new ItemStack(Items.WOODEN_PICKAXE),
						new ItemStack(Items.WOODEN_AXE),
						new ItemStack(Items.WOODEN_SHOVEL),
						new ItemStack(Items.WOODEN_HOE)
				),
				new RepairData(new ItemStack(Blocks.PLANKS, 1, OreDictionary.WILDCARD_VALUE),
						new ItemStack(Items.SHIELD)
				),
				new RepairData(Item.ToolMaterial.STONE.getRepairItemStack(),
						new ItemStack(Items.STONE_SWORD),
						new ItemStack(Items.STONE_PICKAXE),
						new ItemStack(Items.STONE_AXE),
						new ItemStack(Items.STONE_SHOVEL),
						new ItemStack(Items.STONE_HOE)
				),
				new RepairData(ItemArmor.ArmorMaterial.LEATHER.getRepairItemStack(),
						new ItemStack(Items.LEATHER_HELMET),
						new ItemStack(Items.LEATHER_CHESTPLATE),
						new ItemStack(Items.LEATHER_LEGGINGS),
						new ItemStack(Items.LEATHER_BOOTS)
				),
				new RepairData(new ItemStack(Items.LEATHER),
						new ItemStack(Items.ELYTRA)
				),
				new RepairData(Item.ToolMaterial.IRON.getRepairItemStack(),
						new ItemStack(Items.IRON_SWORD),
						new ItemStack(Items.IRON_PICKAXE),
						new ItemStack(Items.IRON_AXE),
						new ItemStack(Items.IRON_SHOVEL),
						new ItemStack(Items.IRON_HOE)
				),
				new RepairData(ItemArmor.ArmorMaterial.IRON.getRepairItemStack(),
						new ItemStack(Items.IRON_HELMET),
						new ItemStack(Items.IRON_CHESTPLATE),
						new ItemStack(Items.IRON_LEGGINGS),
						new ItemStack(Items.IRON_BOOTS)
				),
				new RepairData(ItemArmor.ArmorMaterial.CHAIN.getRepairItemStack(),
						new ItemStack(Items.CHAINMAIL_HELMET),
						new ItemStack(Items.CHAINMAIL_CHESTPLATE),
						new ItemStack(Items.CHAINMAIL_LEGGINGS),
						new ItemStack(Items.CHAINMAIL_BOOTS)
				),
				new RepairData(Item.ToolMaterial.GOLD.getRepairItemStack(),
						new ItemStack(Items.GOLDEN_SWORD),
						new ItemStack(Items.GOLDEN_PICKAXE),
						new ItemStack(Items.GOLDEN_AXE),
						new ItemStack(Items.GOLDEN_SHOVEL),
						new ItemStack(Items.GOLDEN_HOE)
				),
				new RepairData(ItemArmor.ArmorMaterial.GOLD.getRepairItemStack(),
						new ItemStack(Items.GOLDEN_HELMET),
						new ItemStack(Items.GOLDEN_CHESTPLATE),
						new ItemStack(Items.GOLDEN_LEGGINGS),
						new ItemStack(Items.GOLDEN_BOOTS)
				),
				new RepairData(Item.ToolMaterial.DIAMOND.getRepairItemStack(),
						new ItemStack(Items.DIAMOND_SWORD),
						new ItemStack(Items.DIAMOND_PICKAXE),
						new ItemStack(Items.DIAMOND_AXE),
						new ItemStack(Items.DIAMOND_SHOVEL),
						new ItemStack(Items.DIAMOND_HOE)
				),
				new RepairData(ItemArmor.ArmorMaterial.DIAMOND.getRepairItemStack(),
						new ItemStack(Items.DIAMOND_HELMET),
						new ItemStack(Items.DIAMOND_CHESTPLATE),
						new ItemStack(Items.DIAMOND_LEGGINGS),
						new ItemStack(Items.DIAMOND_BOOTS)
				)
		);
	}

	private static Stream<IRecipeWrapper> getRepairRecipes(IVanillaRecipeFactory vanillaRecipeFactory) {
		return getRepairData().flatMap(repairData -> getRepairRecipes(repairData, vanillaRecipeFactory));
	}

	private static Stream<IRecipeWrapper> getRepairRecipes(RepairData repairData, IVanillaRecipeFactory vanillaRecipeFactory) {
		List<ItemStack> repairStackInput = ImmutableList.of(repairData.getRepairStack());
		List<ItemStack> repairables = repairData.getRepairables();
		Stream.Builder<IRecipeWrapper> recipes = Stream.builder();

		for (ItemStack repairable : repairables) {
			ItemStack damagedThreeQuarters = repairable.copy();
			damagedThreeQuarters.setItemDamage(damagedThreeQuarters.getMaxDamage() * 3 / 4);
			ItemStack damagedHalf = repairable.copy();
			damagedHalf.setItemDamage(damagedHalf.getMaxDamage() / 2);

			List<ItemStack> damagedThreeQuartersSingletonList = ImmutableList.of(damagedThreeQuarters);
			IRecipeWrapper repairWithSame = vanillaRecipeFactory.createAnvilRecipe(
					damagedThreeQuartersSingletonList,
					damagedThreeQuartersSingletonList,
					ImmutableList.of(damagedHalf)
			);
			recipes.add(repairWithSame);

			ItemStack damagedFully = repairable.copy();
			damagedFully.setItemDamage(damagedFully.getMaxDamage());
			IRecipeWrapper repairWithMaterial = vanillaRecipeFactory.createAnvilRecipe(
					ImmutableList.of(damagedFully),
					repairStackInput,
					damagedThreeQuartersSingletonList
			);
			recipes.add(repairWithMaterial);
		}

		return recipes.build();
	}

	public static int findLevelsCost(ItemStack leftStack, ItemStack rightStack) {
		EntityPlayer player = Minecraft.getMinecraft().player;
		if (player == null) {
			return -1;
		}
		InventoryPlayer fakeInventory = new InventoryPlayer(player);
		try {
			ContainerRepair repair = new ContainerRepair(fakeInventory, player.world, player);
			repair.inventorySlots.get(0).putStack(leftStack);
			repair.inventorySlots.get(1).putStack(rightStack);
			return repair.maximumCost;
		} catch (RuntimeException e) {
			String left = ErrorUtil.getItemStackInfo(leftStack);
			String right = ErrorUtil.getItemStackInfo(rightStack);
			Log.get().error("Could not get anvil level cost for: ({} and {}).", left, right, e);
			return -1;
		}
	}
}

package mezz.jei.plugins.jei;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import mezz.jei.api.*;
import mezz.jei.bookmarks.BookmarkIngredientHelper;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.BookmarkItemRender;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.ingredients.group.CollapsedGroupIngredientHelper;
import mezz.jei.render.CollapsedGroupRenderer;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import mezz.jei.Internal;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.IModIngredientRegistration;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.config.Config;
import mezz.jei.gui.GuiHelper;
import mezz.jei.gui.overlay.GuiProperties;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.plugins.jei.debug.DebugGhostIngredientHandler;
import mezz.jei.plugins.jei.debug.DebugRecipe;
import mezz.jei.plugins.jei.debug.DebugRecipeCategory;
import mezz.jei.plugins.jei.info.IngredientInfoRecipeCategory;
import mezz.jei.plugins.jei.ingredients.DebugIngredient;
import mezz.jei.plugins.jei.ingredients.DebugIngredientHelper;
import mezz.jei.plugins.jei.ingredients.DebugIngredientListFactory;
import mezz.jei.plugins.jei.ingredients.DebugIngredientRenderer;
import mezz.jei.runtime.JeiHelpers;

@JEIPlugin
public class JEIInternalPlugin implements IModPlugin {
	@Nullable
	public static IIngredientRegistry ingredientRegistry;
	@Nullable
	public static IJeiRuntime jeiRuntime;

	@Override
	public void registerIngredients(IModIngredientRegistration ingredientRegistration) {
		if (Config.isDebugModeEnabled()) {
			DebugIngredientHelper ingredientHelper = new DebugIngredientHelper();
			DebugIngredientRenderer ingredientRenderer = new DebugIngredientRenderer(ingredientHelper);
			ingredientRegistration.register(DebugIngredient.TYPE, Collections.emptyList(), ingredientHelper, ingredientRenderer);
		}
		BookmarkIngredientHelper bookmarkIngredientHelper = new BookmarkIngredientHelper();
		BookmarkItemRender bookmarkItemRender = new BookmarkItemRender();
		ingredientRegistration.register(BookmarkItem.TYPE, Collections.emptyList(), bookmarkIngredientHelper, bookmarkItemRender);

		// Register CollapsedStack as ingredient type — addons that introspect grid items require a registered type
		CollapsedGroupIngredientHelper csHelper = new CollapsedGroupIngredientHelper();
		ingredientRegistration.register(CollapsedGroupIngredient.TYPE, Collections.emptyList(), csHelper, CollapsedGroupRenderer.INSTANCE);
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registry) {
		JeiHelpers jeiHelpers = Internal.getHelpers();
		GuiHelper guiHelper = jeiHelpers.getGuiHelper();

		registry.addRecipeCategories(
			new IngredientInfoRecipeCategory(guiHelper)
		);

		if (Config.isDebugModeEnabled()) {
			registry.addRecipeCategories(
				new DebugRecipeCategory(guiHelper)
			);
		}
	}

	@Override
	public void register(IModRegistry registry) {
		ingredientRegistry = registry.getIngredientRegistry();
		registry.addGuiScreenHandler(GuiContainer.class, GuiProperties::create);
		registry.addGuiScreenHandler(RecipesGui.class, GuiProperties::create);

		if (Config.isDebugModeEnabled()) {
			registry.addIngredientInfo(Arrays.asList(
				new ItemStack(Items.OAK_DOOR),
				new ItemStack(Items.SPRUCE_DOOR),
				new ItemStack(Items.BIRCH_DOOR),
				new ItemStack(Items.JUNGLE_DOOR),
				new ItemStack(Items.ACACIA_DOOR),
				new ItemStack(Items.DARK_OAK_DOOR)
				),
				VanillaTypes.ITEM,
				"description.jei.wooden.door.1", // actually 2 lines
				"description.jei.wooden.door.2",
				"description.jei.wooden.door.3"
			);

			registry.addIngredientInfo(new FluidStack(FluidRegistry.WATER, Fluid.BUCKET_VOLUME), VanillaTypes.FLUID, "water");

			registry.addRecipes(Arrays.asList(
				new DebugRecipe(),
				new DebugRecipe()
			), "debug");

			registry.addRecipeCatalyst(new DebugIngredient(7), "debug");
			registry.addRecipeCatalyst(new FluidStack(FluidRegistry.WATER, Fluid.BUCKET_VOLUME), "debug");
			registry.addRecipeCatalyst(new ItemStack(Items.STICK), "debug");
			int i = 0;
			for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
				ItemStack catalystIngredient = new ItemStack(item);
				if (!catalystIngredient.isEmpty()) {
					registry.addRecipeCatalyst(catalystIngredient, "debug");
				}
				i++;
				if (i > 30) {
					break;
				}
			}

			registry.addAdvancedGuiHandlers(new IAdvancedGuiHandler<GuiBrewingStand>() {
				@Override
				public Class<GuiBrewingStand> getGuiContainerClass() {
					return GuiBrewingStand.class;
				}

				@Override
				public List<Rectangle> getGuiExtraAreas(GuiBrewingStand guiContainer) {
					int widthMovement = (int) ((System.currentTimeMillis() / 100) % 100);
					int size = 25 + widthMovement;
					return Collections.singletonList(
						new Rectangle(guiContainer.getGuiLeft() + guiContainer.getXSize(), guiContainer.getGuiTop() + 40, size, size)
					);
				}

				@Nullable
				@Override
				public Object getIngredientUnderMouse(GuiBrewingStand guiContainer, int mouseX, int mouseY) {
					if (mouseX < 10 && mouseY < 10) {
						return new FluidStack(FluidRegistry.WATER, Fluid.BUCKET_VOLUME);
					}
					return null;
				}
			});

			registry.addGhostIngredientHandler(GuiBrewingStand.class, new DebugGhostIngredientHandler<>());
		}
	}

	@Override
	public void registerCollapsibleGroups(ICollapsibleGroupRegistry registry) {
		registry.newGroup("enchanted_books", "Enchanted Books").addAllOf(VanillaTypes.ENCHANT).build();
		registry.newGroup("potions", "Potions")
				.addAny(VanillaTypes.ITEM, stack -> stack.getItem() == Items.POTIONITEM)
				.build();
		registry.newGroup("splash_potions", "Splash Potions")
				.addAny(VanillaTypes.ITEM, stack -> stack.getItem() == Items.SPLASH_POTION)
				.build();
		registry.newGroup("lingering_potions", "Lingering Potions")
				.addAny(VanillaTypes.ITEM, stack -> stack.getItem() == Items.LINGERING_POTION)
				.build();
		registry.newGroup("tipped_arrows", "Tipped Arrows")
				.addAny(VanillaTypes.ITEM, stack -> stack.getItem() == Items.TIPPED_ARROW)
				.build();
		registry.newGroup("spawn_eggs", "Spawn Eggs")
				.addAny(VanillaTypes.ITEM, stack -> stack.getItem() == Items.SPAWN_EGG)
				.build();
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		JEIInternalPlugin.jeiRuntime = jeiRuntime;

		if (Config.isDebugModeEnabled()) {
			if (ingredientRegistry != null) {
				ingredientRegistry.addIngredientsAtRuntime(DebugIngredient.TYPE, DebugIngredientListFactory.create());
			}
		}
	}
}

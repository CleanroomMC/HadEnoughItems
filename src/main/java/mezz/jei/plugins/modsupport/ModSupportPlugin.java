package mezz.jei.plugins.modsupport;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.ISubtypeRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import mezz.jei.util.Log;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

@JEIPlugin
public class ModSupportPlugin implements IModPlugin {

    @Override
    public void registerSubtypes(ISubtypeRegistry subtypeRegistry) {
        Item patchouliBook = getModdedItem("patchouli", "guide_book");
        if (patchouliBook != null) {
            subtypeRegistry.registerSubtypeInterpreter(patchouliBook, PatchouliBooksSubtypeInterpreter.INSTANCE);
        }
    }

    private Item getModdedItem(String namespace, String path) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, path));
    }

    @Override
    public void register(IModRegistry registry) {
        IRecipeTransferRegistry recipeTransferRegistry = registry.getRecipeTransferRegistry();

        Class<? extends Container> toAttachOutput;

        toAttachOutput = findContainerClass("fastbench", "shadows.fastbench.gui.ContainerFastBench");
        if (toAttachOutput != null) {
            recipeTransferRegistry.overrideOutputSlot(toAttachOutput, VanillaRecipeCategoryUid.CRAFTING, 0);
        }

        toAttachOutput = findContainerClass("fastbench", "shadows.fastbench.gui.ClientContainerFastBench");
        if (toAttachOutput != null) {
            recipeTransferRegistry.overrideOutputSlot(toAttachOutput, VanillaRecipeCategoryUid.CRAFTING, 0);
        }

        toAttachOutput = findContainerClass("tconstruct", "slimeknights.tconstruct.tools.common.inventory.ContainerCraftingStation");
        if (toAttachOutput != null) {
            recipeTransferRegistry.overrideOutputSlot(toAttachOutput, VanillaRecipeCategoryUid.CRAFTING, 0);
        }

        toAttachOutput = findContainerClass("projecte", "moze_intel.projecte.gameObjs.container.PhilosStoneContainer");
        if (toAttachOutput != null) {
            recipeTransferRegistry.overrideOutputSlot(toAttachOutput, VanillaRecipeCategoryUid.CRAFTING, 0);
        }
    }

    private Class<? extends Container> findContainerClass(String modId, String className) {
        if (!Loader.isModLoaded(modId)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Container> clazz = (Class<? extends Container>) Class.forName(className);
            return clazz;
        } catch (Exception e) {
            Log.get().error("Found '{}' mod but unable to find class '{}'", modId, className, e);
            return null;
        }
    }
}

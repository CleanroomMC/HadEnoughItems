package mezz.jei.plugins.modsupport;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.ISubtypeRegistry;
import mezz.jei.api.JEIPlugin;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
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

}

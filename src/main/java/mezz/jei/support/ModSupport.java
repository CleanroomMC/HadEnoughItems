package mezz.jei.support;

import mezz.jei.api.ISubtypeRegistry;
import mezz.jei.api.ISubtypeRegistry.ISubtypeInterpreter;
import mezz.jei.util.Log;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import vazkii.patchouli.common.item.PatchouliItems;

/**
 * A static class containing mod support injections and more
 */
public class ModSupport {

    public static void registerSubtypeInterpreters(ISubtypeRegistry registry) {
        if (Loader.isModLoaded("patchouli")) {
            Log.get().info("Registering SubtypeInterpreter for Patchouli's Books.");
            registry.registerSubtypeInterpreter(PatchouliItems.book, stack -> {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag == null) {
                    return ISubtypeInterpreter.NONE;
                }
                NBTTagCompound bookTag = tag.getCompoundTag("patchouli:book");
                return bookTag.isEmpty() ? ISubtypeInterpreter.NONE : bookTag.toString();
            });
        }
    }

}

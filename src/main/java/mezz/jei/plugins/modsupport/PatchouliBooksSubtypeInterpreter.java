package mezz.jei.plugins.modsupport;

import mezz.jei.api.ISubtypeRegistry.ISubtypeInterpreter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

public class PatchouliBooksSubtypeInterpreter implements ISubtypeInterpreter {

    public static final PatchouliBooksSubtypeInterpreter INSTANCE = new PatchouliBooksSubtypeInterpreter();

    private PatchouliBooksSubtypeInterpreter() { }

    @Override
    public String apply(ItemStack itemStack) {
        NBTTagCompound tag = itemStack.getTagCompound();
        if (tag == null) {
            return NONE;
        }
        if (tag.hasKey("patchouli:book", NBT.TAG_STRING)) {
            return itemStack.getTagCompound().getString("patchouli:book");
        }
        return NONE;
    }

}

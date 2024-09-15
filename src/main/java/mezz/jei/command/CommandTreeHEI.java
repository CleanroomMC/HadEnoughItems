package mezz.jei.command;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

public class CommandTreeHEI extends CommandTreeBase {

    public CommandTreeHEI() {
        this.addSubcommand(new CommandLoadBookmarks());
    }

    @Override
    public String getName() {
        return "hei";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/hei [command]";
    }

}

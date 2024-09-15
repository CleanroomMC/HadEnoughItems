package mezz.jei.command;

import mezz.jei.Internal;
import mezz.jei.bookmarks.BookmarkList;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandLoadBookmarks extends CommandBase {

    @Override
    public String getName() {
        return "loadBookmarks";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/hei loadBookmarks";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        BookmarkList bookmarkList = Internal.getBookmarkList();
        if (bookmarkList == null) {
            throw new CommandException("hei.command.load_bookmarks.failure");
        }
        bookmarkList.loadBookmarks();
        int amount = bookmarkList.size();
        sender.sendMessage(new TextComponentTranslation("hei.command.load_bookmarks.success", amount));
    }

}

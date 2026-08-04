package mezz.jei.bookmarks;

import mezz.jei.Internal;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiScreen;

import java.util.Collections;
import java.util.List;

public class DefaultGhostIngredientHandler implements IGhostIngredientHandler<GuiScreen> {
	@Override
	public <I> List<Target<I>> getTargets(GuiScreen gui, I ingredient, boolean doStart) {
		if (Internal.getBookmarkList().getGroupOrganizer() == null) {
			return Collections.emptyList();
		}
		return Internal.getBookmarkList().getGroupOrganizer().getTargets(ingredient);
	}

	@Override
	public void onComplete() {

	}
}

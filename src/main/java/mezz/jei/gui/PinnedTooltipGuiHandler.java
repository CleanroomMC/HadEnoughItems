package mezz.jei.gui;

import mezz.jei.Internal;
import mezz.jei.api.gui.IGlobalGuiHandler;
import mezz.jei.runtime.JeiRuntime;

import java.awt.Rectangle;
import java.util.Collection;
import java.util.Collections;

/**
 * Reports the pinned recipe tooltip as an area the ingredient and bookmark overlays must keep out of.
 * <p>
 * Those overlays already avoid {@link GuiScreenHelper#isInGuiExclusionArea} both when drawing their
 * tooltips and when hit-testing, so making them step aside is enough to stop them drawing underneath
 * the pinned tooltip and answering to the same pointer. Neither side needs to know about the other.
 */
public class PinnedTooltipGuiHandler implements IGlobalGuiHandler {
	public static final PinnedTooltipGuiHandler INSTANCE = new PinnedTooltipGuiHandler();

	@Override
	public Collection<Rectangle> getGuiExtraAreas() {
		JeiRuntime runtime = Internal.getRuntime();
		if (runtime == null) {
			return Collections.emptyList();
		}
		Rectangle bounds = runtime.getRecipesGui().getPinnedTooltipBounds();
        if (bounds == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(bounds);
    }
}

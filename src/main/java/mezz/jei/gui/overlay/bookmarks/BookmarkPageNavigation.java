package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.Internal;
import mezz.jei.gui.GuiHelper;
import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.gui.elements.GuiLabelButton;
import mezz.jei.input.IPaged;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import java.awt.*;

public class BookmarkPageNavigation {
    private final IPaged paged;
    private final Runnable pageCallback;
    private final GuiButton nextButton;
    private final GuiButton backButton;
    private final GuiLabelButton pageLabel;
    private final boolean hideOnSinglePage;

    public BookmarkPageNavigation(IPaged paged, Runnable pageCallback, boolean hideOnSinglePage) {
        this.paged = paged;
        this.pageCallback = pageCallback;
        GuiHelper guiHelper = Internal.getHelpers().getGuiHelper();
        this.nextButton = new GuiIconButton(0, guiHelper.getArrowNext(), (mc, mouseX, mouseY) -> paged.nextPage());
        this.backButton = new GuiIconButton(1, guiHelper.getArrowPrevious(), (mc, mouseX, mouseY) -> paged.previousPage());
        this.pageLabel = new GuiLabelButton(2, "", (mc, mouseX, mouseY) -> { this.pageCallback.run(); return true; });
        this.hideOnSinglePage = hideOnSinglePage;
    }

    public void updateBounds(Rectangle area) {
        int buttonSize = area.height;
        this.nextButton.x = area.x + area.width - buttonSize;
        this.nextButton.y = area.y;
        this.nextButton.width = this.nextButton.height = buttonSize;
        this.backButton.x = area.x;
        this.backButton.y = area.y;
        this.backButton.width = this.backButton.height = buttonSize;
        int pagePadding = 8;
        this.pageLabel.x = area.x + buttonSize + pagePadding;
        this.pageLabel.y = area.y;
        this.pageLabel.width = area.width - buttonSize*2 - pagePadding*2;
        this.pageLabel.height = buttonSize;
    }

    public void updatePageState() {
        int pageNum = this.paged.getPageNumber();
        int pageCount = this.paged.getPageCount();
        this.pageLabel.displayString = (pageNum + 1) + "/" + pageCount;
    }

    public void draw(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!hideOnSinglePage || this.paged.hasNext() || this.paged.hasPrevious()) {
            nextButton.drawButton(minecraft, mouseX, mouseY, partialTicks);
            backButton.drawButton(minecraft, mouseX, mouseY, partialTicks);
            pageLabel.drawButton(minecraft, mouseX, mouseY, partialTicks);
        }
    }

    public boolean isMouseOver() {
        return nextButton.isMouseOver() ||
            backButton.isMouseOver() ||
            pageLabel.isMouseOver();
    }

    public boolean handleMouseClickedButtons(int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return nextButton.mousePressed(minecraft, mouseX, mouseY) ||
            backButton.mousePressed(minecraft, mouseX, mouseY) ||
            pageLabel.mousePressed(minecraft, mouseX, mouseY);
    }
}

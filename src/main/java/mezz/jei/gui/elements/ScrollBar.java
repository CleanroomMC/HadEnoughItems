package mezz.jei.gui.elements;

import java.awt.*;

import net.minecraft.client.Minecraft;

/**
 * A scrollbar widget for scrolling through content.
 * Ported and adapted from JEI for use in HEI.
 *
 * @since HEI ?
 */
public class ScrollBar {

    public static final int WIDTH = 14;
    private static final int MIN_MARKER_HEIGHT = 14;
    private static final int BORDER_SIZE = 1;

    private final DrawableNineSliceTexture background;
    private final DrawableNineSliceTexture marker;
    private Rectangle area;
    private double dragOriginY = -1;

    public ScrollBar(Rectangle area, DrawableNineSliceTexture background, DrawableNineSliceTexture marker) {
        this.area = area;
        this.background = background;
        this.marker = marker;
    }

    public ScrollBar(int x, int y, int height, DrawableNineSliceTexture background, DrawableNineSliceTexture marker) {
        this(new Rectangle(x, y, WIDTH, height), background, marker);
    }

    public void updateBounds(Rectangle area) {
        this.area = area;
    }

    public Rectangle getArea() {
        return area;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return area.contains(mouseX, mouseY);
    }

    public boolean isDragging() {
        return dragOriginY >= 0;
    }

    public void stopDrag() {
        this.dragOriginY = -1;
    }

    /**
     * Draw the scrollbar.
     *
     * @param minecraft     the Minecraft instance
     * @param visibleAmount number of visible items
     * @param hiddenAmount  number of hidden items
     * @param scrollOffsetY scroll offset (0 = top, 1 = bottom)
     */
    public void draw(Minecraft minecraft, int visibleAmount, int hiddenAmount, float scrollOffsetY) {
        if (area.width == 0 || area.height == 0) {
            return;
        }

        background.draw(minecraft, area.x, area.y, area.width, area.height);
        Rectangle markerArea = calculateMarkerArea(area, visibleAmount, hiddenAmount, scrollOffsetY);
        marker.draw(minecraft, markerArea.x, markerArea.y, markerArea.width, markerArea.height);
    }

    /**
     * Start dragging the scrollbar.
     *
     * @param mouseX        mouse X position
     * @param mouseY        mouse Y position
     * @param visibleAmount number of visible items
     * @param hiddenAmount  number of hidden items
     * @param scrollOffsetY current scroll offset
     * @return the updated scroll offset
     */
    public ScrollResult startDrag(
        double mouseX,
        double mouseY,
        int visibleAmount,
        int hiddenAmount,
        float scrollOffsetY
    ) {
        if (hiddenAmount <= 0 || !isMouseOver(mouseX, mouseY)) {
            return ScrollResult.notHandled(scrollOffsetY);
        }

        float updatedScrollOffsetY = scrollOffsetY;
        Rectangle markerArea = calculateMarkerArea(area, visibleAmount, hiddenAmount, updatedScrollOffsetY);
        if (!markerArea.contains(mouseX, mouseY)) {
            double markerTopY = mouseY - (markerArea.height / 2.0);
            updatedScrollOffsetY = calculateScrollOffsetY(area, markerArea, markerTopY, updatedScrollOffsetY);
            markerArea = calculateMarkerArea(area, visibleAmount, hiddenAmount, updatedScrollOffsetY);
        }
        this.dragOriginY = mouseY - markerArea.y;
        return ScrollResult.handled(updatedScrollOffsetY);
    }

    /**
     * Drag to a new position.
     *
     * @param mouseY        mouse Y position
     * @param visibleAmount number of visible items
     * @param hiddenAmount  number of hidden items
     * @param scrollOffsetY current scroll offset
     * @return the updated scroll offset
     */
    public ScrollResult dragTo(double mouseY, int visibleAmount, int hiddenAmount, float scrollOffsetY) {
        if (!isDragging()) {
            return ScrollResult.notHandled(scrollOffsetY);
        }

        Rectangle markerArea = calculateMarkerArea(area, visibleAmount, hiddenAmount, scrollOffsetY);
        double markerTopY = mouseY - this.dragOriginY;
        float updatedScrollOffsetY = calculateScrollOffsetY(area, markerArea, markerTopY, scrollOffsetY);
        return ScrollResult.handled(updatedScrollOffsetY);
    }

    /**
     * Handle mouse wheel scrolling.
     *
     * @param mouseX        mouse X position
     * @param mouseY        mouse Y position
     * @param scrollDelta   scroll delta (positive = scroll down, negative = scroll up)
     * @param visibleAmount number of visible items
     * @param hiddenAmount  number of hidden items
     * @param scrollOffsetY current scroll offset
     * @return the updated scroll offset
     */
    public ScrollResult scroll(double mouseX, double mouseY, double scrollDelta, int visibleAmount, int hiddenAmount, float scrollOffsetY) {
        if (hiddenAmount <= 0 || !isMouseOver(mouseX, mouseY)) {
            return ScrollResult.notHandled(scrollOffsetY);
        }

        float step = 3.0F / area.height;
        float updatedScrollOffsetY = scrollOffsetY + (scrollDelta > 0 ? -1 : 1) * step;
        updatedScrollOffsetY = clamp(updatedScrollOffsetY, 0, 1);
        return ScrollResult.handled(updatedScrollOffsetY);
    }

    /**
     * Calculate the marker area based on current state.
     */
    static Rectangle calculateMarkerArea(Rectangle area, int visibleAmount, int hiddenAmount, float scrollOffsetY) {
        int trackWidth = Math.max(0, area.width - (2 * BORDER_SIZE));
        int trackHeight = Math.max(0, area.height - (2 * BORDER_SIZE));
        int markerHeight = trackHeight;
        int totalAmount = Math.max(0, visibleAmount) + Math.max(0, hiddenAmount);
        if (hiddenAmount > 0 && totalAmount > 0) {
            markerHeight = Math.round(trackHeight * (visibleAmount / (float) totalAmount));
            int minMarkerHeight = Math.min(MIN_MARKER_HEIGHT, trackHeight);
            markerHeight = clamp(markerHeight, minMarkerHeight, trackHeight);
        }
        float validScrollOffsetY = clamp(scrollOffsetY, 0, 1);
        int markerY = Math.round((trackHeight - markerHeight) * validScrollOffsetY);
        return new Rectangle(
            area.x + BORDER_SIZE,
            area.y + BORDER_SIZE + markerY,
            trackWidth,
            markerHeight
        );
    }

    /**
     * Calculate scroll offset from marker position.
     */
    private static float calculateScrollOffsetY(
        Rectangle area,
        Rectangle markerArea,
        double markerTopY,
        float fallbackScrollOffsetY
    ) {
        int minY = area.y + BORDER_SIZE;
        int trackHeight = Math.max(0, area.height - (2 * BORDER_SIZE));
        int maxY = minY + trackHeight - markerArea.height;
        int travel = maxY - minY;
        if (travel <= 0) {
            return clamp(fallbackScrollOffsetY, 0, 1);
        }
        float scrollOffsetY = (float) ((markerTopY - minY) / travel);
        return clamp(scrollOffsetY, 0, 1);
    }

    /**
     * Clamp a value between min and max.
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamp a value between min and max.
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Result of a scroll operation.
     */
    public static class ScrollResult {
        private final boolean handled;
        private final float scrollOffsetY;

        private ScrollResult(boolean handled, float scrollOffsetY) {
            this.handled = handled;
            this.scrollOffsetY = scrollOffsetY;
        }

        public static ScrollResult handled(float scrollOffsetY) {
            return new ScrollResult(true, scrollOffsetY);
        }

        public static ScrollResult notHandled(float scrollOffsetY) {
            return new ScrollResult(false, scrollOffsetY);
        }

        public boolean isHandled() {
            return handled;
        }

        public float getScrollOffsetY() {
            return scrollOffsetY;
        }
    }
}

package mezz.jei.util;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

public final class CountUtil {
    private CountUtil() {

    }

    /**
     * Renders count onto screen
     * @param font        The font renderer
     * @param count       The count
     * @param xPosition   X coordinate
     * @param yPosition   Y coordinate
     * @param relative    Whether or not to render relative to the xPosition and yPosition
     *                    (mimics the way item stack counts are rendered),
     *                    or to use xPosition and yPosition as absolute screen coordinates
     */
    public static void renderCountString(FontRenderer font, long count, int xPosition, int yPosition, boolean relative) {
        renderCountString(font, count, xPosition, yPosition, relative, false);
    }

    /**
     * Renders count onto screen
     * @param font        The font renderer
     * @param count       The count
     * @param xPosition   X coordinate
     * @param yPosition   Y coordinate
     * @param relative    Whether or not to render relative to the xPosition and yPosition
     *                    (mimics the way item stack counts are rendered),
     *                    or to use xPosition and yPosition as absolute screen coordinates
     * @param alwaysScale To scale no matter what the count is, for uniformity
     */
    public static void renderCountString(FontRenderer font, long count, int xPosition, int yPosition, boolean relative, boolean alwaysScale) {
        String countText = CountUtil.minifyCountString(count);

        renderStringAsCount(font, countText, xPosition, yPosition, 0xFFFFFFFF, relative, alwaysScale || count > 99);
    }

    /**
     * Renders string as it would be if it was a count on an itemstack.
     * 
     * @param font        The font renderer
     * @param count       The count
     * @param xPosition   X coordinate
     * @param yPosition   Y coordinate
     * @param color       Color of rendered string
     * @param relative    Whether or not to render relative to the xPosition and yPosition
     *                    (mimics the way item stack counts are rendered),
     *                    or to use xPosition and yPosition as absolute screen coordinates
     * @param scale       True to scale down by 1/2 in the screen-space
     * @see #renderStringAsCount(FontRenderer, String, int, int, int, boolean, float)
     */
    public static void renderStringAsCount(FontRenderer font, String count, int xPosition, int yPosition, int color, boolean relative, boolean scale) {
        renderStringAsCount(font, count, xPosition, yPosition, color, relative, scale ? 0.5f : 1.0f);
    }

    /**
     * Renders string as it would be if it was a count on an itemstack
     * @param font        The font renderer
     * @param count       The count
     * @param xPosition   X coordinate
     * @param yPosition   Y coordinate
     * @param color       Color of rendered string
     * @param relative    Whether or not to render relative to the xPosition and yPosition
     *                    (mimics the way item stack counts are rendered),
     *                    or to use xPosition and yPosition as absolute screen coordinates
     * @param scale       Value to scale the count by
     */
    public static void renderStringAsCount(FontRenderer font, String count, int xPosition, int yPosition, int color, boolean relative, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();

        if (scale != 1.0f) {
            GlStateManager.scale(scale, scale, 1.0F);
        }

        int x = scale != 1.0f
                ? (int)((relative ? xPosition + 16 : xPosition) / scale) - font.getStringWidth(count)
                : (relative ? xPosition + 17 : xPosition) - font.getStringWidth(count);
        int y = scale != 1.0f
                ? (int)((relative ? yPosition + 16 : yPosition) / scale) - 8
                : (relative ? yPosition + 9 : yPosition);

        font.drawStringWithShadow(count, x, y, color);

        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }

    /**
     * Formats the stack count for display
     */
    public static String minifyCountString(long count) {
        if (count <= 9999) {
            return String.valueOf(count);
        }

        if (count <= 999999) {
            float k = count / 1000f;
            return String.format(k % 1 == 0 ? "%.0fk" : "%.1fk", k);
        }

        if (count <= 999999999) {
            float m = count / 1000000f;
            return String.format(m % 1 == 0 ? "%.0fm" : "%.1fm", m);
        }

        float g = count / 1000000000f;
        return String.format(g % 1 == 0 ? "%.0fg" : "%.1fg", g);
    }

}

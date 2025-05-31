package mezz.jei.bookmarks;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.ingredients.IngredientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.util.ITooltipFlag;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.Rectangle;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("rawtypes")
public class BookmarkItemRender implements IIngredientRenderer<BookmarkItem> {
    @Override
    public void render(Minecraft minecraft, int xPosition, int yPosition, @Nullable BookmarkItem ingredient) {
        if (ingredient != null) {
            IngredientRegistry registry = Internal.getIngredientRegistry();
            IIngredientType<Object> ingredientType = registry.getIngredientType(ingredient.ingredient);
            registry.getIngredientRenderer(ingredientType).render(minecraft, xPosition, yPosition, ingredient.ingredient);

            if (ingredient.amount != 0L) {
                String text = String.valueOf(ingredient.amount);
                drawScaledText(
                    text,
                    new Rectangle(xPosition + 1, yPosition + 1, 12, 14),
                    1f,
                    0xFFFFFFFF,
                    true);
            }
        }
    }

    private static void drawScaledText(String text, Rectangle rect, float scale, int color, boolean shadow) {
        Minecraft mc = Minecraft.getMinecraft();
        @SuppressWarnings("ConstantConditions")
        float screenScale = mc.currentScreen.width * 1f / mc.displayWidth;
        float textScale = Math.max(screenScale, Math.max(scale, 1f) * (mc.fontRenderer.getUnicodeFlag() ? 0.75f : 0.5f));

        GlStateManager.disableDepth();
        {
            final int width = mc.fontRenderer.getStringWidth(text);
            final float partW = rect.getWidth() / 2f;
            final float partH = rect.getHeight() / 2f;
            final double offsetX = Math
                .ceil(rect.getX() + partW + partW - (width / 2f) * textScale);
            final double offsetY = Math.ceil(
                rect.getY() + partH
                    + partH
                    - (mc.fontRenderer.FONT_HEIGHT / 2f) * textScale);

            GL11.glTranslated(offsetX, offsetY, 0);
            GL11.glScaled(textScale, textScale, 1);
            mc.fontRenderer.drawString(text, 0, 0, color, shadow);
            GL11.glScaled(1 / textScale, 1 / textScale, 1);
            GL11.glTranslated(-1 * offsetX, -1 * offsetY, 0);
        }
        GlStateManager.enableDepth();
    }

    @Override
    public List<String> getTooltip(Minecraft minecraft, BookmarkItem ingredient, ITooltipFlag tooltipFlag) {
        return getIngredientRenderer(ingredient.ingredient).getTooltip(minecraft, ingredient.ingredient, tooltipFlag);
    }

    @Override
    public FontRenderer getFontRenderer(Minecraft minecraft, BookmarkItem ingredient) {
        return getIngredientRenderer(ingredient.ingredient).getFontRenderer(minecraft, ingredient.ingredient);
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<String> getTooltip(Minecraft minecraft, BookmarkItem ingredient, boolean advanced) {
        return getIngredientRenderer(ingredient.ingredient).getTooltip(minecraft, ingredient.ingredient, advanced);
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<String> getTooltip(Minecraft minecraft, BookmarkItem ingredient) {
        return getIngredientRenderer(ingredient.ingredient).getTooltip(minecraft, ingredient.ingredient);
    }

    private static <E> IIngredientRenderer<E> getIngredientRenderer(E ingredient) {
        return Internal.getIngredientRegistry().getIngredientRenderer(ingredient);
    }
}

package mezz.jei.gui;

import mezz.jei.render.IngredientListBatchRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.config.GuiUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class TooltipRenderer {
    private TooltipRenderer() {
    }

    public static void drawHoveringText(Minecraft minecraft, String textLine, int x, int y) {
        drawHoveringText(ItemStack.EMPTY, minecraft, Collections.singletonList(textLine), x, y, -1, minecraft.fontRenderer);
    }

    public static void drawHoveringText(Minecraft minecraft, List<String> textLines, int x, int y) {
        drawHoveringText(ItemStack.EMPTY, minecraft, textLines, x, y, -1, minecraft.fontRenderer);
    }

    public static void drawHoveringText(Minecraft minecraft, List<String> textLines, int x, int y, int maxWidth) {
        drawHoveringText(ItemStack.EMPTY, minecraft, textLines, x, y, maxWidth, minecraft.fontRenderer);
    }

    public static void drawHoveringText(Minecraft minecraft, List<String> textLines, int x, int y, FontRenderer font) {
        drawHoveringText(ItemStack.EMPTY, minecraft, textLines, x, y, -1, font);
    }

    public static void drawHoveringText(Minecraft minecraft, List<String> textLines, int x, int y, int maxWidth, FontRenderer font) {
        drawHoveringText(ItemStack.EMPTY, minecraft, textLines, x, y, maxWidth, font);
    }

    public static void drawHoveringText(ItemStack itemStack, Minecraft minecraft, List<String> textLines, int x, int y, FontRenderer font) {
        drawHoveringText(itemStack, minecraft, textLines, x, y, -1, font);
    }

    public static void drawHoveringText(ItemStack itemStack, Minecraft minecraft, List<String> textLines, int x, int y, int maxWidth, FontRenderer font) {
        ScaledResolution scaledresolution = new ScaledResolution(minecraft);
        GuiUtils.drawHoveringText(itemStack, textLines, x, y, scaledresolution.getScaledWidth(), scaledresolution.getScaledHeight(), maxWidth, font);
    }

    public static void drawHoveringText(ItemStack itemStack, Minecraft minecraft, List<String> textLines, int x, int y, int maxWidth) {
        drawHoveringText(itemStack, minecraft, textLines, x, y, maxWidth, minecraft.fontRenderer);
    }

    public static void drawHoveringTextAndItems(Minecraft minecraft, List<Object> textLines, int x, int y) {
        drawHoveringTextAndItems(ItemStack.EMPTY, minecraft, textLines, x, y, -1, minecraft.fontRenderer);
    }

    public static void drawHoveringTextAndItems(ItemStack stack, Minecraft minecraft, List<Object> lines, int mouseX, int mouseY, int maxTextWidth, FontRenderer font) {
        // Almost a copy from GuiUtils.drawHoveringText, but also allowing IngredientListBatchRenderer lines.

        ScaledResolution scaledresolution = new ScaledResolution(minecraft);
        int screenWidth = scaledresolution.getScaledWidth();
        int screenHeight = scaledresolution.getScaledHeight();
        List<String> textLinesOnly = lines.stream().filter(String.class::isInstance).map(String.class::cast).collect(Collectors.toList());
        RenderTooltipEvent.Pre event = new RenderTooltipEvent.Pre(stack, textLinesOnly, mouseX, mouseY, screenWidth, screenHeight, maxTextWidth, font);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return;
        }
        mouseX = event.getX();
        mouseY = event.getY();
        screenWidth = event.getScreenWidth();
        screenHeight = event.getScreenHeight();
        maxTextWidth = event.getMaxWidth();
        font = event.getFontRenderer();

        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        int tooltipTextWidth = 0;

        for (Object line : lines) {
            if (line instanceof String) {
                String textLine = (String) line;
                int textLineWidth = font.getStringWidth(textLine);

                if (textLineWidth > tooltipTextWidth) {
                    tooltipTextWidth = textLineWidth;
                }
            }
        }

        boolean needsWrap = false;

        int titleLinesCount = 1;
        int tooltipX = mouseX + 12;
        if (tooltipX + tooltipTextWidth + 4 > screenWidth) {
            tooltipX = mouseX - 16 - tooltipTextWidth;
            if (tooltipX < 4) // if the tooltip doesn't fit on the screen
            {
                if (mouseX > screenWidth / 2) {
                    tooltipTextWidth = mouseX - 12 - 8;
                } else {
                    tooltipTextWidth = screenWidth - 16 - mouseX;
                }
                needsWrap = true;
            }
        }

        if (maxTextWidth > 0 && tooltipTextWidth > maxTextWidth) {
            tooltipTextWidth = maxTextWidth;
            needsWrap = true;
        }

        if (needsWrap) {
            int wrappedTooltipWidth = 0;
            List<Object> wrappedTextLines = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i) instanceof String) {
                    String textLine = (String) lines.get(i);
                    List<String> wrappedLine = font.listFormattedStringToWidth(textLine, tooltipTextWidth);
                    if (i == 0) {
                        titleLinesCount = wrappedLine.size();
                    }
                    for (String line : wrappedLine) {
                        int lineWidth = font.getStringWidth(line);
                        if (lineWidth > wrappedTooltipWidth) {
                            wrappedTooltipWidth = lineWidth;
                        }
                        wrappedTextLines.add(line);
                    }
                } else {
                    wrappedTextLines.add(lines.get(i));
                }
            }
            tooltipTextWidth = wrappedTooltipWidth;
            lines = wrappedTextLines;

            if (mouseX > screenWidth / 2) {
                tooltipX = mouseX - 16 - tooltipTextWidth;
            } else {
                tooltipX = mouseX + 12;
            }

            // This part is particularly different. We try to wrap any IngredientListBatchRenderer lines based on the wrappedTooltipWidth.
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i) instanceof IngredientListBatchRenderer) {
                    IngredientListBatchRenderer renderer = (IngredientListBatchRenderer) lines.get(i);
                    renderer.moveSlotsToFit(wrappedTooltipWidth); // This is cached, fortunately.
                }
            }
        } else {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i) instanceof IngredientListBatchRenderer) {
                    IngredientListBatchRenderer renderer = (IngredientListBatchRenderer) lines.get(i);
                    renderer.moveSlotsToFit(screenWidth / 2); // This is cached, fortunately.
                    tooltipTextWidth = Math.max(tooltipTextWidth, renderer.getWidth());
                }
            }
        }

        int tooltipY = mouseY - 12;
        int tooltipHeight = 8;

        if (lines.size() > 1) {
            for (int i = 1; i < lines.size(); i++) { // Skip the first line
                Object line = lines.get(i);
                if (line instanceof String) {
                    tooltipHeight += 10;
                } else if (line instanceof IngredientListBatchRenderer) {
                    tooltipHeight += ((IngredientListBatchRenderer) line).getHeight();
                }
            }
            if (lines.size() > titleLinesCount) {
                tooltipHeight += 2; // gap between title lines and next lines
            }
        }

        if (tooltipY < 4) {
            tooltipY = 4;
        } else if (tooltipY + tooltipHeight + 4 > screenHeight) {
            tooltipY = screenHeight - tooltipHeight - 4;
        }

        final int zLevel = 300;
        int backgroundColor = 0xF0100010;
        int borderColorStart = 0x505000FF;
        int borderColorEnd = (borderColorStart & 0xFEFEFE) >> 1 | borderColorStart & 0xFF000000;
        RenderTooltipEvent.Color colorEvent = new RenderTooltipEvent.Color(stack, textLinesOnly, tooltipX, tooltipY, font, backgroundColor, borderColorStart, borderColorEnd);
        MinecraftForge.EVENT_BUS.post(colorEvent);
        backgroundColor = colorEvent.getBackground();
        borderColorStart = colorEvent.getBorderStart();
        borderColorEnd = colorEvent.getBorderEnd();
        GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY - 4, tooltipX + tooltipTextWidth + 3, tooltipY - 3, backgroundColor, backgroundColor);
        GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY + tooltipHeight + 3, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 4, backgroundColor, backgroundColor);
        GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3, backgroundColor, backgroundColor);
        GuiUtils.drawGradientRect(zLevel, tooltipX - 4, tooltipY - 3, tooltipX - 3, tooltipY + tooltipHeight + 3, backgroundColor, backgroundColor);
        GuiUtils.drawGradientRect(zLevel, tooltipX + tooltipTextWidth + 3, tooltipY - 3, tooltipX + tooltipTextWidth + 4, tooltipY + tooltipHeight + 3, backgroundColor, backgroundColor);
        GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY - 3 + 1, tooltipX - 3 + 1, tooltipY + tooltipHeight + 3 - 1, borderColorStart, borderColorEnd);
        GuiUtils.drawGradientRect(zLevel, tooltipX + tooltipTextWidth + 2, tooltipY - 3 + 1, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3 - 1, borderColorStart, borderColorEnd);
        GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3, tooltipY - 3 + 1, borderColorStart, borderColorStart);
        GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY + tooltipHeight + 2, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3, borderColorEnd, borderColorEnd);

        MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostBackground(stack, textLinesOnly, tooltipX, tooltipY, font, tooltipTextWidth, tooltipHeight));
        int tooltipTop = tooltipY;

        for (int lineNumber = 0; lineNumber < lines.size(); ++lineNumber) {
            Object lineObject = lines.get(lineNumber);
            if (lineObject instanceof String) {
                font.drawStringWithShadow((String) lineObject, (float) tooltipX, (float) tooltipY, -1);
                tooltipY += 10;
            } else if (lineObject instanceof IngredientListBatchRenderer) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(tooltipX, tooltipY, 100.0F);
                ((IngredientListBatchRenderer) lineObject).render(minecraft);
                GlStateManager.popMatrix();
                tooltipY += ((IngredientListBatchRenderer) lineObject).getHeight();
            }

            if (lineNumber + 1 == titleLinesCount) {
                tooltipY += 2;
            }
        }

        MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostText(stack, textLinesOnly, tooltipX, tooltipTop, font, tooltipTextWidth, tooltipHeight));

        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableRescaleNormal();
    }
}

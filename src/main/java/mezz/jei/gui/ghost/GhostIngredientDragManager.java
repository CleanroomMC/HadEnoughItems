package mezz.jei.gui.ghost;

import mezz.jei.Internal;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.bookmarks.DefaultGhostIngredientHandler;
import mezz.jei.config.Config;
import mezz.jei.config.KeyBindings;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.input.IClickedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GhostIngredientDragManager {
    private final GuiScreenHelper guiScreenHelper;
    private final IngredientRegistry ingredientRegistry;
    private final List<GhostIngredientReturning> ghostIngredientsReturning = new ArrayList<>();
    @Nullable
    private GhostIngredientDrag<?> ghostIngredientDrag;
    @Nullable
    private Object hoveredIngredient;
    @Nullable
    private List<IGhostIngredientHandler.Target<Object>> hoveredIngredientTargets;
    private final DefaultGhostIngredientHandler defaultHandler = new DefaultGhostIngredientHandler();
    @Nullable
    private IGhostIngredientHandler<?> hoverHandler;

    public GhostIngredientDragManager(GuiScreenHelper guiScreenHelper, IngredientRegistry ingredientRegistry) {
        this.guiScreenHelper = guiScreenHelper;
        this.ingredientRegistry = ingredientRegistry;
    }

    public void updateScreen(GuiScreen gui, boolean forceUpdate) {
        if (gui == null) {
            this.stopDrag();
        }
    }

    public void drawTooltips(Minecraft minecraft, int mouseX, int mouseY) {
        if (!(minecraft.currentScreen instanceof GuiContainer)) { // guiContainer uses drawOnForeground
            drawGhostIngredientHighlights(minecraft, mouseX, mouseY);
        }
        if (ghostIngredientDrag != null) {
            ghostIngredientDrag.drawItem(minecraft, mouseX, mouseY);
        }
        ghostIngredientsReturning.forEach(returning -> returning.drawItem(minecraft));
        ghostIngredientsReturning.removeIf(GhostIngredientReturning::isComplete);
    }

    public void drawOnForeground(Minecraft minecraft, GuiContainer gui, int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(-gui.getGuiLeft(), -gui.getGuiTop(), 0);
        drawGhostIngredientHighlights(minecraft, mouseX, mouseY);
        GlStateManager.popMatrix();
    }

    private void drawGhostIngredientHighlights(Minecraft minecraft, int mouseX, int mouseY) {
        if (this.ghostIngredientDrag != null) {
            this.ghostIngredientDrag.drawTargets(mouseX, mouseY);
        } else {
            IIngredientListElement elementUnderMouse = Internal.getInputHandler().getElementUnderMouse();
            Object hovered = elementUnderMouse == null ? null : elementUnderMouse.getIngredient();
            boolean showHighlight = true;
            if (!Objects.equals(hovered, this.hoveredIngredient)) {
                this.hoveredIngredient = hovered;
                this.hoveredIngredientTargets = null;
                GuiScreen currentScreen = minecraft.currentScreen;
                if (currentScreen != null && hovered != null) {
                    IGhostIngredientHandler<GuiScreen> handler = guiScreenHelper.getGhostIngredientHandler(currentScreen);
                    if (handler != null && handler.shouldHighlightTargets()) {
                        this.hoveredIngredientTargets = handler.getTargets(currentScreen, hovered, false);
                        hoverHandler = handler;
                    } else if (handler == null) {
                        this.hoveredIngredientTargets = defaultHandler.getTargets(currentScreen, hovered, false);
                        hoverHandler = defaultHandler;
                    }
                }
            }
            if (hoverHandler == defaultHandler) {
                showHighlight = false;
            }
            if (this.hoveredIngredientTargets != null && !Config.isCheatItemsEnabled() && showHighlight) {
                GhostIngredientDrag.drawTargets(mouseX, mouseY, this.hoveredIngredientTargets);
            }
        }
    }

    public boolean handleMouseClicked(Minecraft minecraft, GuiScreen currentScreen, IClickedIngredient<?> clicked, IIngredientListElement<?> listElement, int mouseX, int mouseY) {
        if (this.ghostIngredientDrag != null) {
            boolean success = this.ghostIngredientDrag.onClick(mouseX, mouseY);
            if (!success) {
                GhostIngredientReturning<?> returning = GhostIngredientReturning.create(this.ghostIngredientDrag, mouseX, mouseY);
                this.ghostIngredientsReturning.add(returning);
            }
            this.ghostIngredientDrag = null;
            return success;
        }
        EntityPlayerSP player = minecraft.player;
        if (player != null && listElement != null) {
            ItemStack mouseItem = player.inventory.getItemStack();
            if (mouseItem.isEmpty() && this.handleClickGhostIngredient(currentScreen, clicked)) {
                return true;
            }
        }
        return false;
    }

    public void stopDrag() {
        if (this.ghostIngredientDrag != null) {
            this.ghostIngredientDrag.stop();
            this.ghostIngredientDrag = null;
        }
    }

    public <T extends GuiScreen, V> boolean handleClickGhostIngredient(T currentScreen, IClickedIngredient<V> clicked) {
        if (clicked == null) {
            return false;
        }
        IGhostIngredientHandler<T> handler = guiScreenHelper.getGhostIngredientHandler(currentScreen);
        if (handler == null) {
            if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                return handleClickGhostIngredient(defaultHandler, currentScreen, clicked);
            }
            return false;
        }
        return handleClickGhostIngredient(handler, currentScreen, clicked);
    }

    public <T extends GuiScreen, V> boolean handleClickGhostIngredient(IGhostIngredientHandler<T> handler, T currentScreen, IClickedIngredient<V> clicked) {
        V ingredient = clicked.getValue();
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            if (handler.quickMove(currentScreen, ingredient)) {
                clicked.onClickHandled();
                return true;
            }
        }
        List<IGhostIngredientHandler.Target<V>> targets = handler.getTargets(currentScreen, ingredient, true);
        if (!targets.isEmpty()) {
            IIngredientRenderer<V> ingredientRenderer = ingredientRegistry.getIngredientRenderer(ingredient);
            Rectangle clickedArea = clicked.getArea();
            this.ghostIngredientDrag = new GhostIngredientDrag<>(handler, targets, ingredientRenderer, ingredient, clickedArea);
            clicked.onClickHandled();
            return true;
        }
        return false;
    }

    public boolean handleKeyDown(int eventKey) {
        if (KeyBindings.isInventoryCloseKey(eventKey) || KeyBindings.isEnterKey(eventKey)) {
            // Only cancel other handling of inputs if we are currently dragging
            if (this.ghostIngredientDrag != null) {
                stopDrag();
                return true;
            }
        }
        return false;
    }
}

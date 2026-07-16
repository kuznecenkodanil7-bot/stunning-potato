package dev.raidmine.stafftool.ui;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.util.AuthManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class HudEditorScreen extends Screen {
    private HudOverlay.Edge dragEdge;
    private double startMouseX;
    private double startMouseY;
    private int startX;
    private int startY;
    private int startWidth;
    private int startHeight;
    private float startWidthScale;
    private float startHeightScale;
    private double dragOffsetX;
    private double dragOffsetY;
    private long centeredAt;
    private long openedAt;

    public HudEditorScreen() {
        super(Text.literal("RM Tools HUD editor"));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        HudOverlay.setEditingSelected(false);
        HudOverlay.setEditingInteraction(false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiTheme.surface(UiTheme.argb(126, 3, 4, 7)));
        drawGuides(context);

        float progress = UiTheme.easeOutCubic(Math.min(1F, (System.currentTimeMillis() - openedAt) / 420F));
        int hudOffsetY = Math.round((1F - progress) * -90F);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0, hudOffsetY);
        HudOverlay.renderEditable(context);
        context.getMatrices().popMatrix();

        renderCenterLogo(context, mouseX, mouseY, progress);

        if (System.currentTimeMillis() - centeredAt < 1200L) {
            String centered = "Панель отцентрирована";
            int w = UiTheme.textWidth(centered, 10F, true) + 34;
            int tx = (width - w) / 2;
            UiTheme.roundedBorder(context, tx, 40, w, 30, 10, 1,
                    UiTheme.withAlpha(UiTheme.accent(), 220), UiTheme.PANEL_2);
            UiTheme.icon(context, UiIcon.CENTER, tx + 9, 49, 14, UiTheme.accent());
            UiTheme.text(context, textRenderer, centered, tx + 29, 50, 10F, UiTheme.TEXT, true);
        }
    }

    private void renderCenterLogo(DrawContext context, int mouseX, int mouseY, float progress) {
        Rect target = centerLogoRect();
        int animatedY = Math.round(height + 40 + (target.y() - height - 40) * progress);
        Rect logo = new Rect(target.x(), animatedY, target.w(), target.h());
        boolean hovered = progress > 0.95F && logo.contains(mouseX, mouseY);

        if (hovered) UiTheme.logoHoverGlow(context, logo.x(), logo.y(), logo.w(), logo.h());
        else UiTheme.logo(context, logo.x(), logo.y(), logo.w(), logo.h(), 255);

        String hint = "Нажмите на меню, чтобы перейти в меню настроек";
        int hintW = UiTheme.textWidth(hint, 8.8F, false);
        int alpha = hovered ? 150 : 55;
        UiTheme.text(context, textRenderer, hint, (width - hintW) / 2, logo.y() + logo.h() + 13,
                8.8F, UiTheme.withAlpha(UiTheme.TEXT, alpha), false);
    }

    private void drawGuides(DrawContext context) {
        int centerX = width / 2;
        int centerY = height / 2;
        context.fill(centerX, 0, centerX + 1, height, UiTheme.argb(34, 255, 138, 0));
        context.fill(0, centerY, width, centerY + 1, UiTheme.argb(20, 255, 138, 0));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!AuthManager.canUseMod() || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (System.currentTimeMillis() - openedAt < 380L) return true;
        if (centerLogoRect().contains(click.x(), click.y())) {
            MinecraftClient.getInstance().setScreen(new SettingsScreen(this));
            return true;
        }

        HudOverlay.Layout layout = HudOverlay.layout(width, height);
        if (!layout.contains(click.x(), click.y()) && !isOnHandle(layout, click.x(), click.y())) {
            HudOverlay.setEditingSelected(false);
            return true;
        }

        boolean wasSelected = HudOverlay.isEditingSelected();
        HudOverlay.setEditingSelected(true);
        HudOverlay.Edge edge = wasSelected ? layout.edgeAt(click.x(), click.y()) : HudOverlay.Edge.MOVE;
        if (edge == null) edge = HudOverlay.Edge.MOVE;
        dragEdge = edge;
        startMouseX = click.x();
        startMouseY = click.y();
        startX = layout.x();
        startY = layout.y();
        startWidth = layout.width();
        startHeight = layout.height();
        startWidthScale = RaidMineStaffMod.config().hudWidthScale;
        startHeightScale = RaidMineStaffMod.config().hudHeightScale;
        dragOffsetX = click.x() - layout.x();
        dragOffsetY = click.y() - layout.y();
        HudOverlay.setEditingInteraction(true);
        return true;
    }

    private boolean isOnHandle(HudOverlay.Layout layout, double mouseX, double mouseY) {
        if (!HudOverlay.isEditingSelected()) return false;
        for (HudOverlay.Handle handle : layout.handles()) {
            if (handle.rect().contains(mouseX, mouseY)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragEdge == null) return false;
        if (dragEdge == HudOverlay.Edge.MOVE) {
            HudOverlay.setPosition(width, height,
                    (int) Math.round(click.x() - dragOffsetX),
                    (int) Math.round(click.y() - dragOffsetY));
            return true;
        }

        double dx = click.x() - startMouseX;
        double dy = click.y() - startMouseY;
        boolean ctrl = isControlDown();

        float widthDelta = switch (dragEdge) {
            case E, NE, SE -> (float) (dx / HudOverlay.BASE_WIDTH);
            case W, NW, SW -> (float) (-dx / HudOverlay.BASE_WIDTH);
            default -> 0F;
        };
        float heightDelta = switch (dragEdge) {
            case S, SE, SW -> (float) (dy / HudOverlay.BASE_HEIGHT);
            case N, NE, NW -> (float) (-dy / HudOverlay.BASE_HEIGHT);
            default -> 0F;
        };

        if (ctrl) {
            float uniform = Math.abs(widthDelta) >= Math.abs(heightDelta) ? widthDelta : heightDelta;
            if (uniform == 0F) uniform = widthDelta != 0F ? widthDelta : heightDelta;
            HudOverlay.setWidthScale(startWidthScale + uniform);
            HudOverlay.setHeightScale(startHeightScale + uniform);
        } else {
            if (widthDelta != 0F) HudOverlay.setWidthScale(startWidthScale + widthDelta);
            if (heightDelta != 0F) HudOverlay.setHeightScale(startHeightScale + heightDelta);
        }

        HudOverlay.Layout resized = HudOverlay.layout(width, height);
        int x = startX;
        int y = startY;
        if (ctrl) {
            x = startX + (startWidth - resized.width()) / 2;
            y = startY + (startHeight - resized.height()) / 2;
        } else {
            if (dragEdge == HudOverlay.Edge.W || dragEdge == HudOverlay.Edge.NW || dragEdge == HudOverlay.Edge.SW) {
                x = startX + startWidth - resized.width();
            }
            if (dragEdge == HudOverlay.Edge.N || dragEdge == HudOverlay.Edge.NE || dragEdge == HudOverlay.Edge.NW) {
                y = startY + startHeight - resized.height();
            }
        }
        HudOverlay.setPosition(width, height, x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragEdge == null) return false;
        dragEdge = null;
        HudOverlay.setEditingInteraction(false);
        RaidMineStaffMod.config().save();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        HudOverlay.Layout layout = HudOverlay.layout(width, height);
        if (!layout.contains(mouseX, mouseY)) return false;
        float delta = verticalAmount > 0 ? 0.04F : -0.04F;
        HudOverlay.setWidthScale(RaidMineStaffMod.config().hudWidthScale + delta);
        HudOverlay.setHeightScale(RaidMineStaffMod.config().hudHeightScale + delta);
        RaidMineStaffMod.config().save();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int step = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 8 : 2;
        switch (input.key()) {
            case GLFW.GLFW_KEY_R -> { HudOverlay.reset(); centeredAt = System.currentTimeMillis(); return true; }
            case GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_C -> { HudOverlay.centerTop(); centeredAt = System.currentTimeMillis(); return true; }
            case GLFW.GLFW_KEY_UP -> { HudOverlay.nudge(width, height, 0, -step); return true; }
            case GLFW.GLFW_KEY_DOWN -> { HudOverlay.nudge(width, height, 0, step); return true; }
            case GLFW.GLFW_KEY_LEFT -> { HudOverlay.nudge(width, height, -step, 0); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { HudOverlay.nudge(width, height, step, 0); return true; }
            case GLFW.GLFW_KEY_ESCAPE -> { close(); return true; }
        }
        return super.keyPressed(input);
    }

    private boolean isControlDown() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public void close() {
        HudOverlay.setEditingInteraction(false);
        HudOverlay.setEditingSelected(false);
        RaidMineStaffMod.config().save();
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) { }

    @Override
    public boolean shouldPause() { return false; }

    private Rect centerLogoRect() {
        int w = Math.min(190, Math.max(132, width / 6));
        int h = w;
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}

package dev.raidmine.stafftool.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class WelcomeScreen extends Screen {
    private final Screen parent;
    private final String username;
    private long openedAt;

    public WelcomeScreen(Screen parent, String username) {
        super(Text.literal("RM Tools — с возвращением"));
        this.parent = parent;
        this.username = username == null || username.isBlank() ? "модератор" : username;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        ClickableWidget.playClickSound(MinecraftClient.getInstance().getSoundManager());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long age = System.currentTimeMillis() - openedAt;
        float appear = UiTheme.easeOutBack(Math.min(1F, age / 380F));
        float fade = age < 3300L ? 1F : Math.max(0F, 1F - (age - 3300L) / 650F);
        if (age > 3950L) {
            MinecraftClient.getInstance().setScreen(parent);
            return;
        }

        context.fillGradient(0, 0, width, height,
                UiTheme.surface(UiTheme.withAlpha(UiTheme.argb(255, 2, 3, 5), Math.round(245F * fade))),
                UiTheme.surface(UiTheme.withAlpha(UiTheme.argb(255, 10, 9, 8), Math.round(245F * fade))));
        UiTheme.softGlow(context, width / 5, height / 4, Math.max(120, width / 3),
                Math.max(80, height / 3), 0xFF2A2A2A, Math.round(20F * fade));
        UiTheme.softGlow(context, width - width / 6, height / 3, Math.max(120, width / 3),
                Math.max(80, height / 3), UiTheme.accent(), Math.round(10F * fade));

        Rect logo = logoRect();
        int animatedY = Math.round(height + 40 + (logo.y() - height - 40) * appear);
        Rect shown = new Rect(logo.x(), animatedY, logo.w(), logo.h());
        boolean hovered = shown.contains(mouseX, mouseY);
        if (hovered && fade > 0.85F) UiTheme.logoHoverGlow(context, shown.x(), shown.y(), shown.w(), shown.h());
        else UiTheme.logo(context, shown.x(), shown.y(), shown.w(), shown.h(), Math.round(255F * fade));

        String message = "С возвращением, " + username + "!";
        int messageW = UiTheme.textWidth(message, 18F, true);
        UiTheme.text(context, textRenderer, message, (width - messageW) / 2, shown.y() + shown.h() + 22,
                18F, UiTheme.withAlpha(UiTheme.TEXT, Math.round(255F * fade)), true);
        String hint = "Нажмите на логотип, чтобы открыть настройки";
        int hintW = UiTheme.textWidth(hint, 9F, false);
        UiTheme.text(context, textRenderer, hint, (width - hintW) / 2, shown.y() + shown.h() + 50,
                9F, UiTheme.withAlpha(UiTheme.MUTED, Math.round(150F * fade)), false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (logoRect().contains(click.x(), click.y())) {
            ClickableWidget.playClickSound(MinecraftClient.getInstance().getSoundManager());
            MinecraftClient.getInstance().setScreen(new SettingsScreen(parent));
        } else {
            MinecraftClient.getInstance().setScreen(parent);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        MinecraftClient.getInstance().setScreen(parent);
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }

    private Rect logoRect() {
        int size = Math.min(220, Math.max(150, Math.min(width, height) / 4));
        return new Rect((width - size) / 2, (height - size) / 2 - 42, size, size);
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
}

package dev.raidmine.stafftool.ui;

import dev.raidmine.stafftool.util.AuthManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Authorization screen styled after the supplied RaidMine SVG layout. */
public final class LoginScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget passwordField;
    private String status = "";
    private boolean error;
    private long openedAt;
    private float focusAnim;
    private float loginHoverAnim;
    private float exitHoverAnim;

    public LoginScreen(Screen parent) {
        super(Text.literal("RM Tools — авторизация"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        passwordField = new TextFieldWidget(textRenderer, 0, 0, 10, 38, Text.literal("Пароль"));
        passwordField.setMaxLength(32);
        passwordField.setDrawsBackground(false);
        passwordField.setEditableColor(UiTheme.TEXT);
        passwordField.setUneditableColor(UiTheme.FAINT);
        passwordField.setFocused(true);
        openedAt = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long age = System.currentTimeMillis() - openedAt;
        float appear = UiTheme.easeOutCubic(Math.min(1F, age / 420F));
        Layout l = layout();
        int cardY = l.cardY() + Math.round((1F - appear) * 22F);
        updatePasswordBounds(l, cardY);

        renderSvgBackground(context, age);

        int logoW = 118;
        int logoH = 98;
        int logoX = (width - logoW) / 2;
        int logoY = Math.max(28, cardY - 150);
        float pulse = 0.5F + 0.5F * (float) Math.sin(age / 640.0);
        UiTheme.softGlow(context, logoX + logoW / 2, logoY + logoH / 2,
                86, 54, 0xFFFF8A00, 24 + Math.round(24F * pulse));
        UiTheme.logo(context, logoX, logoY, logoW, logoH, Math.round(255F * appear));

        String head = "ПАНЕЛЬ УПРАВЛЕНИЯ";
        int headW = UiTheme.textWidth(head, 13.5F, true);
        UiTheme.text(context, textRenderer, head, (width - headW) / 2, logoY + logoH + 13,
                13.5F, UiTheme.TEXT, true);
        int lineW = 104;
        context.fillGradient((width - lineW) / 2, logoY + logoH + 36,
                (width + lineW) / 2, logoY + logoH + 38,
                UiTheme.withAlpha(UiTheme.accent(), 0), UiTheme.accent());
        context.fillGradient(width / 2, logoY + logoH + 36,
                (width + lineW) / 2, logoY + logoH + 38,
                UiTheme.accent(), UiTheme.withAlpha(UiTheme.accent(), 0));
        String sub = "Авторизуйтесь для доступа к инструментам";
        int subW = UiTheme.textWidth(sub, 8.6F, false);
        UiTheme.text(context, textRenderer, sub, (width - subW) / 2, logoY + logoH + 48,
                8.6F, UiTheme.MUTED, false);

        UiTheme.shadow(context, l.cardX(), cardY, l.cardW(), l.cardH(), 18);
        UiTheme.roundedBorder(context, l.cardX(), cardY, l.cardW(), l.cardH(), 18, 1,
                UiTheme.withAlpha(UiTheme.accent(), 170), UiTheme.argb(248, 10, 11, 14));
        UiTheme.panelTexture(context, l.cardX() + 2, cardY + 2, l.cardW() - 4, l.cardH() - 4, UiTheme.accent());

        UiTheme.roundedRect(context, l.cardX() + 22, cardY + 22, 34, 34, 10,
                UiTheme.argb(210, 38, 25, 8));
        UiTheme.icon(context, UiIcon.SHIELD, l.cardX() + 31, cardY + 31, 16, UiTheme.accent());
        UiTheme.text(context, textRenderer, "Авторизация", l.cardX() + 68, cardY + 25,
                12F, UiTheme.TEXT, true);
        UiTheme.text(context, textRenderer, "Войдите в панель управления RaidMine",
                l.cardX() + 68, cardY + 45, 8.2F, UiTheme.MUTED, false);

        String username = AuthManager.currentSessionName(MinecraftClient.getInstance());
        boolean allowed = AuthManager.isAllowedUsername(username);
        Rect loginField = l.loginField(cardY);
        drawInput(context, loginField, UiIcon.USER, username.isBlank() ? "Ник не найден" : username,
                false, allowed ? UiTheme.TEXT : UiTheme.DANGER, 0F);

        Rect passRect = l.passwordField(cardY);
        focusAnim += ((passwordField.isFocused() ? 1F : 0F) - focusAnim) * 0.18F;
        drawInput(context, passRect, UiIcon.SHIELD,
                passwordField.getText().isBlank() ? "Пароль" : "*".repeat(passwordField.getText().length()),
                passwordField.getText().isBlank(), UiTheme.TEXT, focusAnim);
        if (passwordField.isFocused() && (System.currentTimeMillis() / 420L) % 2L == 0L) {
            String mask = "*".repeat(passwordField.getText().length());
            int caretX = passRect.x() + 39 + UiTheme.textWidth(mask, 10.2F, true) + 1;
            context.fill(caretX, passRect.y() + 10, caretX + 1, passRect.y() + 28, UiTheme.accent());
        }

        Rect loginButton = l.loginButton(cardY);
        Rect exitButton = l.exitButton(cardY);
        loginHoverAnim += (((allowed && loginButton.contains(mouseX, mouseY)) ? 1F : 0F) - loginHoverAnim) * 0.20F;
        exitHoverAnim += ((exitButton.contains(mouseX, mouseY) ? 1F : 0F) - exitHoverAnim) * 0.20F;
        drawButton(context, loginButton, "ВОЙТИ", UiIcon.CHECK, true, allowed, loginHoverAnim);
        drawButton(context, exitButton, "ВЫЙТИ ИЗ ИГРЫ", UiIcon.CLOSE, false, true, exitHoverAnim);

        if (!allowed) {
            status = "Этот аккаунт не добавлен в список персонала";
            error = true;
        }
        if (status != null && !status.isBlank()) {
            int statusColor = error ? UiTheme.DANGER : UiTheme.SUCCESS;
            int statusW = UiTheme.textWidth(status, 8.3F, false);
            UiTheme.text(context, textRenderer, status,
                    l.cardX() + (l.cardW() - statusW) / 2, cardY + l.cardH() - 38,
                    8.3F, statusColor, false);
        }

        String secure = "Вход защищён локальной авторизацией RM Tools";
        int secureW = UiTheme.textWidth(secure, 7.4F, false);
        UiTheme.text(context, textRenderer, secure, (width - secureW) / 2,
                cardY + l.cardH() + 18, 7.4F, UiTheme.withAlpha(UiTheme.MUTED, 145), false);
    }

    private void renderSvgBackground(DrawContext context, long age) {
        context.fillGradient(0, 0, width, height, 0xFF030304, 0xFF070707);
        // Large, smooth circles from the reference SVG.
        UiTheme.softGlow(context, width / 5, height / 4, Math.max(120, width / 3), Math.max(90, height / 3),
                0xFF242424, 25);
        UiTheme.softGlow(context, width - width / 7, height / 5, Math.max(130, width / 3), Math.max(95, height / 3),
                0xFFFF8A00, 12);
        UiTheme.softGlow(context, width / 2, height + height / 8, Math.max(180, width / 2), Math.max(100, height / 3),
                0xFFFF6B00, 10);
        int specks = 6;
        for (int i = 0; i < specks; i++) {
            float phase = (age / 1600F + i * 0.19F) % 1F;
            int sx = Math.round(width * (0.08F + i * 0.17F));
            int sy = Math.round(height * (0.18F + (i % 3) * 0.24F));
            int a = 5 + Math.round(8F * (1F - Math.abs(phase * 2F - 1F)));
            UiTheme.roundedRectExact(context, sx, sy, 2, 2, 1, UiTheme.withAlpha(0xFFFFA31A, a));
        }
    }

    private void drawInput(DrawContext context, Rect rect, UiIcon icon, String value,
                           boolean placeholder, int valueColor, float focus) {
        int border = UiTheme.blend(UiTheme.argb(160, 52, 54, 60), UiTheme.accent(), focus);
        UiTheme.roundedBorder(context, rect.x(), rect.y(), rect.w(), rect.h(), 10, 1,
                border, UiTheme.argb(245, 17, 18, 22));
        UiTheme.icon(context, icon, rect.x() + 12, rect.y() + 11, 14,
                UiTheme.blend(UiTheme.FAINT, UiTheme.accent(), focus));
        UiTheme.text(context, textRenderer, value, rect.x() + 39, rect.y() + 12,
                9.2F, placeholder ? UiTheme.FAINT : valueColor, !placeholder);
    }

    private void drawButton(DrawContext context, Rect rect, String label, UiIcon icon,
                            boolean accent, boolean enabled, float hover) {
        int base = accent ? UiTheme.accent() : UiTheme.argb(255, 27, 29, 34);
        int hoverColor = accent ? UiTheme.blend(UiTheme.accent(), UiTheme.accent2(), 0.55F)
                : UiTheme.argb(255, 42, 44, 52);
        int color = enabled ? UiTheme.blend(base, hoverColor, hover) : UiTheme.argb(170, 45, 47, 54);
        if (accent && enabled) {
            UiTheme.softGlow(context, rect.x() + rect.w() / 2, rect.y() + rect.h() / 2,
                    rect.w() / 2, rect.h() / 2, UiTheme.accent(), Math.round(16F + 18F * hover));
        }
        UiTheme.roundedRect(context, rect.x(), rect.y(), rect.w(), rect.h(), 10, color);
        int labelW = UiTheme.textWidth(label, 8.4F, true);
        int contentW = labelW + 18;
        int contentX = rect.x() + (rect.w() - contentW) / 2;
        UiTheme.icon(context, icon, contentX, rect.y() + 10, 12, enabled ? UiTheme.TEXT : UiTheme.FAINT);
        UiTheme.text(context, textRenderer, label, contentX + 18, rect.y() + 11,
                8.4F, enabled ? UiTheme.TEXT : UiTheme.FAINT, true);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        Layout l = layout();
        updatePasswordBounds(l, l.cardY());
        if (passwordField.mouseClicked(click, doubled)) return true;
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (l.loginButton(l.cardY()).contains(click.x(), click.y())) {
                attemptLogin();
                return true;
            }
            if (l.exitButton(l.cardY()).contains(click.x(), click.y())) {
                MinecraftClient.getInstance().scheduleStop();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEnter()) {
            attemptLogin();
            return true;
        }
        return passwordField != null && passwordField.keyPressed(input) || super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return passwordField != null && passwordField.charTyped(input) || super.charTyped(input);
    }

    private void attemptLogin() {
        String username = AuthManager.currentSessionName(MinecraftClient.getInstance());
        if (!AuthManager.isAllowedUsername(username)) {
            status = "Доступ запрещён для аккаунта " + (username.isBlank() ? "—" : username);
            error = true;
            return;
        }
        if (passwordField.getText().isBlank()) {
            status = "Введите пароль";
            error = true;
            passwordField.setFocused(true);
            return;
        }
        if (AuthManager.login(passwordField.getText())) {
            error = false;
            status = "Вход выполнен";
            MinecraftClient.getInstance().setScreen(new WelcomeScreen(parent, username));
        } else {
            error = true;
            status = "Неверный пароль";
            passwordField.setText("");
            passwordField.setFocused(true);
        }
    }

    private void updatePasswordBounds(Layout l, int cardY) {
        Rect field = l.passwordField(cardY);
        passwordField.setDimensionsAndPosition(field.w(), field.h(), field.x(), field.y());
    }

    private Layout layout() {
        int cardW = Math.min(470, width - 34);
        int cardH = Math.min(330, height - 80);
        int cardX = (width - cardW) / 2;
        int cardY = Math.max(180, (height - cardH) / 2 + 48);
        if (cardY + cardH + 42 > height) cardY = Math.max(18, height - cardH - 42);
        return new Layout(cardX, cardY, cardW, cardH);
    }

    @Override
    public void close() {
        if (AuthManager.canUseMod()) MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) { }

    @Override
    public boolean shouldPause() { return false; }

    private record Layout(int cardX, int cardY, int cardW, int cardH) {
        Rect loginField(int y) { return new Rect(cardX + 34, y + 86, cardW - 68, 38); }
        Rect passwordField(int y) { return new Rect(cardX + 34, y + 136, cardW - 68, 38); }
        Rect loginButton(int y) { return new Rect(cardX + 34, y + 192, (cardW - 78) * 3 / 5, 36); }
        Rect exitButton(int y) {
            Rect login = loginButton(y);
            return new Rect(login.x() + login.w() + 10, y + 192,
                    cardX + cardW - 34 - (login.x() + login.w() + 10), 36);
        }
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}

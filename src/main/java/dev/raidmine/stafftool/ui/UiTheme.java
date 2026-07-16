package dev.raidmine.stafftool.ui;

import dev.raidmine.stafftool.RaidMineStaffMod;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class UiTheme {
    public static final int BG = argb(248, 6, 7, 10);
    public static final int PANEL = argb(247, 12, 13, 17);
    public static final int PANEL_2 = argb(252, 18, 20, 26);
    public static final int CARD = argb(243, 24, 26, 34);
    public static final int CARD_HOVER = argb(255, 31, 34, 44);
    public static final int SUCCESS = argb(255, 69, 218, 155);
    public static final int DANGER = argb(255, 255, 91, 104);
    public static final int WARNING = argb(255, 255, 185, 66);
    public static final int TEXT = argb(255, 247, 249, 252);
    public static final int MUTED = argb(255, 166, 175, 191);
    public static final int FAINT = argb(255, 102, 113, 132);
    public static final int BORDER = argb(110, 91, 103, 124);

    public static final float FONT_SIZE = 11.0F;
    public static final float FONT_SMALL = 9.0F;
    public static final float FONT_MEDIUM = 12.0F;
    public static final float FONT_TITLE = 16.0F;

    private static final Identifier LOGO = Identifier.of(RaidMineStaffMod.MOD_ID, "textures/gui/rm_logo.png");
    private static final int LOGO_WIDTH = 4096;
    private static final int LOGO_HEIGHT = 3378;

    private UiTheme() {
    }

    public static int accent() {
        return RaidMineStaffMod.config() == null ? 0xFFFFA31A : RaidMineStaffMod.config().accentColor;
    }

    public static int accent2() {
        return RaidMineStaffMod.config() == null ? 0xFFFF5A00 : RaidMineStaffMod.config().accentColorSecondary;
    }

    public static float backgroundOpacity() {
        return RaidMineStaffMod.config() == null ? 0.92F : RaidMineStaffMod.config().uiBackgroundOpacity;
    }

    public static int surface(int color) {
        int alpha = channel(color, 24);
        int red = channel(color, 16);
        int green = channel(color, 8);
        int blue = channel(color, 0);
        boolean darkSurface = red < 105 && green < 110 && blue < 130;
        if (!darkSurface) return color;
        return withAlpha(color, Math.max(0, Math.round(alpha * backgroundOpacity())));
    }

    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int blend(int from, int to, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int a = Math.round(channel(from, 24) + (channel(to, 24) - channel(from, 24)) * t);
        int r = Math.round(channel(from, 16) + (channel(to, 16) - channel(from, 16)) * t);
        int g = Math.round(channel(from, 8) + (channel(to, 8) - channel(from, 8)) * t);
        int b = Math.round(channel(from, 0) + (channel(to, 0) - channel(from, 0)) * t);
        return argb(a, r, g, b);
    }

    private static int channel(int color, int shift) {
        return (color >> shift) & 0xFF;
    }

    /** Subtle procedural texture made from smooth translucent shapes. */
    public static void panelTexture(DrawContext context, int x, int y, int width, int height, int accentColor) {
        if (width <= 8 || height <= 8) return;
        float opacity = backgroundOpacity();
        if (opacity <= 0.001F) return;
        int orb = Math.max(18, Math.min(width, height) / 2);
        softGlow(context, x + width - orb / 2, y + orb / 3,
                orb, Math.max(12, orb / 2), accentColor, Math.round(18F * opacity));
        softGlow(context, x + orb / 3, y + height - orb / 4,
                Math.max(20, orb * 3 / 4), Math.max(10, orb / 3), accent2(), Math.round(11F * opacity));
        int lineY = y + Math.max(3, height / 5);
        int lineW = Math.max(20, width / 3);
        roundedRectExact(context, x + width - lineW - 10, lineY,
                lineW, 1, 1, withAlpha(0xFFFFFFFF, Math.round(9F * opacity)));
    }

    /** Smooth, layered glow that never changes the color of the underlying surface. */
    public static void softGlow(DrawContext context, int centerX, int centerY,
                                int radiusX, int radiusY, int color, int maxAlpha) {
        int layers = 12;
        for (int i = layers; i >= 1; i--) {
            float t = i / (float) layers;
            int rx = Math.max(1, Math.round(radiusX * t));
            int ry = Math.max(1, Math.round(radiusY * t));
            float falloff = 1F - t;
            int alpha = Math.max(1, Math.round(maxAlpha * falloff * falloff));
            roundedRectExact(context, centerX - rx, centerY - ry,
                    rx * 2, ry * 2, Math.min(rx, ry), withAlpha(color, alpha));
        }
    }

    public static void shadow(DrawContext context, int x, int y, int width, int height, int radius) {
        for (int i = 10; i >= 1; i--) {
            int alpha = Math.max(2, 20 - i * 2);
            roundedRect(context, x - i, y - i, width + i * 2, height + i * 2, radius + i,
                    argb(alpha, 0, 0, 0));
        }
    }

    public static void glow(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        for (int i = 8; i >= 2; i--) {
            roundedRect(context, x - i, y - i / 2, width + i * 2, height + i,
                    radius + i / 2, withAlpha(color, Math.max(2, 24 - i * 2)));
        }
    }

    public static void roundedRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        roundedRectInternal(context, x, y, width, height, radius, surface(color));
    }

    /** Draws a color with its exact alpha, bypassing the global background opacity. */
    public static void roundedRectExact(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        roundedRectInternal(context, x, y, width, height, radius, color);
    }

    private static void roundedRectInternal(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || ((color >>> 24) & 0xFF) == 0) return;
        if (SmoothAssets.ensureInitialized()) {
            SmoothAssets.roundedRect(context, x, y, width, height, radius, color);
            return;
        }
        radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (radius == 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        context.fill(x + radius, y, x + width - radius, y + height, color);
        context.fill(x, y + radius, x + width, y + height - radius, color);
    }

    public static void outline(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        roundedBorder(context, x, y, width, height, radius, 1, color, PANEL_2);
    }

    public static void roundedBorder(DrawContext context, int x, int y, int width, int height,
                                     int radius, int thickness, int borderColor, int innerColor) {
        thickness = Math.max(1, Math.min(thickness, Math.min(width, height) / 3));
        roundedRectExact(context, x, y, width, height, radius, borderColor);
        roundedRect(context, x + thickness, y + thickness,
                width - thickness * 2, height - thickness * 2,
                Math.max(0, radius - thickness), innerColor);
    }

    public static void logo(DrawContext context, int x, int y, int width, int height, int alpha) {
        if (SmoothAssets.drawLogo(context, x, y, width, height, alpha)) {
            return;
        }
        float scale = Math.min(width / (float) LOGO_WIDTH, height / (float) LOGO_HEIGHT);
        int drawWidth = Math.max(1, Math.round(LOGO_WIDTH * scale));
        int drawHeight = Math.max(1, Math.round(LOGO_HEIGHT * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        int tint = withAlpha(0xFFFFFFFF, alpha);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO,
                drawX, drawY, 0, 0, drawWidth, drawHeight,
                LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT, tint);
    }

    public static void logoTint(DrawContext context, int x, int y, int width, int height, int tint) {
        if (SmoothAssets.drawLogoTint(context, x, y, width, height, tint)) return;
        float scale = Math.min(width / (float) LOGO_WIDTH, height / (float) LOGO_HEIGHT);
        int drawWidth = Math.max(1, Math.round(LOGO_WIDTH * scale));
        int drawHeight = Math.max(1, Math.round(LOGO_HEIGHT * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO,
                drawX, drawY, 0, 0, drawWidth, drawHeight,
                LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT, tint);
    }

    public static void logoGlow(DrawContext context, int x, int y, int width, int height) {
        int glow = withAlpha(accent(), 44);
        logoTint(context, x - 2, y, width, height, glow);
        logoTint(context, x + 2, y, width, height, glow);
        logoTint(context, x, y - 2, width, height, glow);
        logoTint(context, x, y + 2, width, height, glow);
        logo(context, x, y, width, height, 255);
    }

    public static void logoHoverGlow(DrawContext context, int x, int y, int width, int height) {
        int outer = withAlpha(accent(), 92);
        int inner = withAlpha(0xFFFFD24A, 115);
        for (int[] offset : new int[][]{{-3,0},{3,0},{0,-3},{0,3},{-2,-2},{2,-2},{-2,2},{2,2}}) {
            logoTint(context, x + offset[0], y + offset[1], width, height, outer);
        }
        for (int[] offset : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
            logoTint(context, x + offset[0], y + offset[1], width, height, inner);
        }
        logo(context, x, y, width, height, 255);
    }

    public static void brandText(DrawContext context, String value, int x, int y, float size, int color) {
        SmoothAssets.drawBrandText(context, value, x, y, size, color);
    }

    public static int brandTextWidth(String value, float size) {
        return SmoothAssets.brandTextWidth(value, size);
    }

    public static void text(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        text(context, renderer, value, x, y, FONT_SIZE, color, false);
    }

    public static void textSmall(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        text(context, renderer, value, x, y, FONT_SMALL, color, false);
    }

    public static void textBold(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        text(context, renderer, value, x, y, FONT_SIZE, color, true);
    }

    public static void textMedium(DrawContext context, TextRenderer renderer, String value, int x, int y, int color, boolean bold) {
        text(context, renderer, value, x, y, FONT_MEDIUM, color, bold);
    }

    public static void textTitle(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        text(context, renderer, value, x, y, FONT_TITLE, color, true);
    }

    public static void text(DrawContext context, TextRenderer renderer, String value, int x, int y,
                            float size, int color, boolean bold) {
        if (usesMinecraftFont() && renderer != null) {
            float scale = Math.max(0.45F, size / 9.0F);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            Text text = Text.literal(value).styled(style -> style.withBold(bold));
            context.drawText(renderer, text, 0, 0, color, false);
            context.getMatrices().popMatrix();
            return;
        }
        if (SmoothAssets.ensureInitialized()) {
            SmoothAssets.drawText(context, value, x, y, size, color, bold);
        } else {
            context.drawText(renderer, Text.literal(value), x, y, color, false);
        }
    }

    public static int textWidth(String value) {
        return textWidth(value, FONT_SIZE, false);
    }

    public static int textWidth(String value, float size, boolean bold) {
        TextRenderer renderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        if (usesMinecraftFont() && renderer != null) {
            return Math.max(0, Math.round(renderer.getWidth(value) * Math.max(0.45F, size / 9.0F)));
        }
        if (SmoothAssets.ensureInitialized()) return SmoothAssets.textWidth(value, size, bold);
        return renderer == null ? 0 : renderer.getWidth(value);
    }

    private static boolean usesMinecraftFont() {
        return RaidMineStaffMod.config() != null
                && "MINECRAFT".equalsIgnoreCase(RaidMineStaffMod.config().fontFamily);
    }

    public static String ellipsize(TextRenderer renderer, String value, int maxWidth) {
        if (value == null) return "";
        if (textWidth(value) <= maxWidth) return value;
        String suffix = "…";
        int allowed = Math.max(0, maxWidth - textWidth(suffix));
        StringBuilder result = new StringBuilder();
        for (int codePoint : value.codePoints().toArray()) {
            String next = result + new String(Character.toChars(codePoint));
            if (textWidth(next) > allowed) break;
            result.appendCodePoint(codePoint);
        }
        return result + suffix;
    }

    public static void icon(DrawContext context, UiIcon icon, int x, int y, int size, int color) {
        if (SmoothAssets.ensureInitialized()) SmoothAssets.drawIcon(context, icon, x, y, size, color);
    }

    public static float easeOutCubic(float t) {
        t = Math.max(0F, Math.min(1F, t));
        float p = 1F - t;
        return 1F - p * p * p;
    }

    public static float easeOutBack(float t) {
        t = Math.max(0F, Math.min(1F, t));
        float c1 = 1.70158F;
        float c3 = c1 + 1F;
        float p = t - 1F;
        return 1F + c3 * p * p * p + c1 * p * p;
    }
}

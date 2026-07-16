package dev.raidmine.stafftool.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.raidmine.stafftool.RaidMineStaffMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * High-resolution UI assets. Icons and fonts are loaded as separate linear-
 * filtered textures so shrinking the HUD never falls back to a tiny shared
 * sprite atlas. The bundled font resources are rasterized glyph atlases, not
 * distributable font files.
 */
public final class SmoothAssets {
    private static final int SHAPE_SIZE = 1024;
    private static final Identifier SHAPE_ID = Identifier.of(RaidMineStaffMod.MOD_ID, "runtime/shape_circle_hq");
    private static final int[] TEXTURE_LEVELS = {64, 128, 256, 512, 1024, 2048};
    private static final Map<Integer, TextureAsset> LOGOS = new HashMap<>();

    private static final EnumMap<UiIcon, Map<Integer, TextureAsset>> ICONS = new EnumMap<>(UiIcon.class);
    private static final Map<String, BakedFont> FONTS = new HashMap<>();
    private static final Map<String, Boolean> FONT_FAILURES = new HashMap<>();

    private static volatile boolean shapeInitialized;
    private static volatile boolean shapeFailed;

    private SmoothAssets() {
    }

    public static boolean ensureInitialized() {
        return ensureShapeInitialized();
    }

    private static boolean ensureShapeInitialized() {
        if (shapeInitialized) return true;
        if (shapeFailed) return false;
        synchronized (SmoothAssets.class) {
            if (shapeInitialized) return true;
            try {
                BufferedImage image = new BufferedImage(SHAPE_SIZE, SHAPE_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setColor(Color.WHITE);
                graphics.fill(new Ellipse2D.Float(0, 0, SHAPE_SIZE, SHAPE_SIZE));
                graphics.dispose();
                NativeImage nativeImage = toNativeImage(image);
                MinecraftClient.getInstance().getTextureManager().registerTexture(
                        SHAPE_ID, new LinearTexture(() -> "RM Tools HQ round shape", nativeImage));
                shapeInitialized = true;
                return true;
            } catch (Throwable throwable) {
                shapeFailed = true;
                RaidMineStaffMod.LOGGER.error("Could not create RM Tools shape texture", throwable);
                return false;
            }
        }
    }

    private static int textureLevelFor(int drawSize) {
        int requested = Math.max(1, drawSize);
        for (int level : TEXTURE_LEVELS) {
            if (level >= requested * 8) return level;
        }
        return TEXTURE_LEVELS[TEXTURE_LEVELS.length - 1];
    }

    private static TextureAsset ensureLogo(int drawSize) {
        int level = textureLevelFor(drawSize);
        TextureAsset existing = LOGOS.get(level);
        if (existing != null) return existing;
        synchronized (LOGOS) {
            existing = LOGOS.get(level);
            if (existing != null) return existing;
            String resource = "/assets/raidmine_staff/textures/gui/logo_hq/logo_" + level + ".png";
            try (InputStream stream = SmoothAssets.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IllegalStateException("Missing logo resource " + resource);
                NativeImage image = NativeImage.read(stream);
                Identifier id = Identifier.of(RaidMineStaffMod.MOD_ID, "runtime/logo_hq_" + level);
                int width = image.getWidth();
                int height = image.getHeight();
                MinecraftClient.getInstance().getTextureManager().registerTexture(
                        id, new LinearTexture(() -> "RM Tools HQ logo " + level, image));
                TextureAsset created = new TextureAsset(id, width, height);
                LOGOS.put(level, created);
                return created;
            } catch (Throwable throwable) {
                RaidMineStaffMod.LOGGER.error("Could not load RM Tools logo level {}", level, throwable);
                return null;
            }
        }
    }

    public static boolean drawLogo(DrawContext context, int x, int y, int boxWidth, int boxHeight, int alpha) {
        int tint = ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24) | 0x00FFFFFF;
        return drawLogoTint(context, x, y, boxWidth, boxHeight, tint);
    }

    public static boolean drawLogoTint(DrawContext context, int x, int y, int boxWidth, int boxHeight, int tint) {
        if (boxWidth <= 0 || boxHeight <= 0) return false;
        TextureAsset logo = ensureLogo(Math.max(boxWidth, boxHeight));
        if (logo == null) return false;
        float scale = Math.min(boxWidth / (float) logo.width(), boxHeight / (float) logo.height());
        int width = Math.max(1, Math.round(logo.width() * scale));
        int height = Math.max(1, Math.round(logo.height() * scale));
        int drawX = x + (boxWidth - width) / 2;
        int drawY = y + (boxHeight - height) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, logo.id(),
                drawX, drawY, 0, 0, width, height,
                logo.width(), logo.height(), logo.width(), logo.height(), tint);
        return true;
    }

    public static void drawIcon(DrawContext context, UiIcon icon, int x, int y, int size, int color) {
        if (icon == null || size <= 0) return;
        TextureAsset asset = ensureIcon(icon, size);
        if (asset == null) return;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, asset.id(),
                x, y, 0, 0, size, size,
                asset.width(), asset.height(), asset.width(), asset.height(), color);
    }

    private static TextureAsset ensureIcon(UiIcon icon, int drawSize) {
        int level = textureLevelFor(drawSize);
        Map<Integer, TextureAsset> levels = ICONS.computeIfAbsent(icon, ignored -> new HashMap<>());
        TextureAsset existing = levels.get(level);
        if (existing != null) return existing;
        synchronized (levels) {
            existing = levels.get(level);
            if (existing != null) return existing;
            String file = icon.name().toLowerCase(Locale.ROOT);
            String resource = "/assets/raidmine_staff/textures/icons/ui_hq/" + file + "_" + level + ".png";
            try (InputStream stream = SmoothAssets.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IllegalStateException("Missing icon resource " + resource);
                NativeImage image = NativeImage.read(stream);
                Identifier id = Identifier.of(RaidMineStaffMod.MOD_ID,
                        "runtime/icon_hq_" + file + "_" + level);
                int width = image.getWidth();
                int height = image.getHeight();
                MinecraftClient.getInstance().getTextureManager().registerTexture(
                        id, new LinearTexture(() -> "RM Tools HQ icon " + file + " " + level, image));
                TextureAsset created = new TextureAsset(id, width, height);
                levels.put(level, created);
                return created;
            } catch (Throwable throwable) {
                RaidMineStaffMod.LOGGER.error("Could not load HQ icon {} level {}", icon, level, throwable);
                return null;
            }
        }
    }

    public static int drawText(DrawContext context, String text, float x, float y,
                               float size, int color, boolean bold) {
        return selectedFont().draw(context, text, x, y, size, color, bold);
    }

    public static int textWidth(String text, float size, boolean bold) {
        return selectedFont().width(text, size, bold);
    }

    public static int drawBrandText(DrawContext context, String text, float x, float y,
                                    float size, int color) {
        return selectedFont().draw(context, text, x, y, size, color, false);
    }

    public static int brandTextWidth(String text, float size) {
        return selectedFont().width(text, size, false);
    }

    public static String fontName() {
        return switch (selectedFontKey()) {
            case "nunito" -> "Nunito";
            case "gerhaus" -> "Gerhaus";
            case "montserrat_alternates" -> "Montserrat Alternates";
            case "mighty_zeo" -> "Mighty Zeo";
            case "xanmono" -> "Xanmono";
            case "shoptronic_sp" -> "Shoptronic SP";
            case "gnf" -> "GNF";
            case "etude_noire_new" -> "Etude Noire New";
            case "nuqun" -> "Nuqun";
            case "pixy" -> "Pixy";
            case "etude_noire" -> "Etude Noire";
            case "seenonim" -> "Seenonim";
            case "hemico" -> "Hemico";
            default -> "Nunito";
        };
    }

    public static void reloadFontAtlas() {
        // Baked atlases do not need to be regenerated. Selection is resolved
        // on every draw, so changing the setting takes effect immediately.
    }

    private static BakedFont selectedFont() {
        return font(selectedFontKey());
    }

    private static String selectedFontKey() {
        String configured = RaidMineStaffMod.config() == null ? "AUTO" : RaidMineStaffMod.config().fontFamily;
        if (configured == null) return "hemico";
        return switch (configured.trim().toUpperCase(Locale.ROOT)) {
            case "GERHAUS" -> "gerhaus";
            case "MONTSERRAT_ALTERNATES", "MONTSERRAT ALTERNATES" -> "montserrat_alternates";
            case "MIGHTY_ZEO", "MIGHTY ZEO" -> "mighty_zeo";
            case "XANMONO" -> "xanmono";
            case "SHOPTRONIC_SP", "SHOPTRONIC SP" -> "shoptronic_sp";
            case "GNF" -> "gnf";
            case "ETUDE_NOIRE_NEW", "ETUDE NOIRE NEW" -> "etude_noire_new";
            case "NUQUN" -> "nuqun";
            case "PIXY" -> "pixy";
            case "ETUDE_NOIRE" -> "etude_noire";
            case "SEENONIM" -> "seenonim";
            case "AUTO", "HEMICO" -> "hemico";
            case "NUNITO" -> "nunito";
            default -> "hemico";
        };
    }

    private static BakedFont font(String key) {
        BakedFont existing = FONTS.get(key);
        if (existing != null) return existing;
        synchronized (FONTS) {
            existing = FONTS.get(key);
            if (existing != null) return existing;
            if (Boolean.TRUE.equals(FONT_FAILURES.get(key))) return BakedFont.EMPTY;
            try {
                BakedFont loaded = loadFont(key);
                FONTS.put(key, loaded);
                return loaded;
            } catch (Throwable throwable) {
                FONT_FAILURES.put(key, true);
                RaidMineStaffMod.LOGGER.error("Could not load baked RM Tools font {}", key, throwable);
                return BakedFont.EMPTY;
            }
        }
    }

    private static BakedFont loadFont(String key) throws Exception {
        String base = "/assets/raidmine_staff/fonts/" + key;
        try (InputStream imageStream = SmoothAssets.class.getResourceAsStream(base + ".png");
             InputStream jsonStream = SmoothAssets.class.getResourceAsStream(base + ".json")) {
            if (imageStream == null || jsonStream == null) throw new IllegalStateException("Missing baked font " + key);
            NativeImage image = NativeImage.read(imageStream);
            Identifier id = Identifier.of(RaidMineStaffMod.MOD_ID, "runtime/font_" + key);
            MinecraftClient.getInstance().getTextureManager().registerTexture(
                    id, new LinearTexture(() -> "RM Tools baked font " + key, image));

            JsonObject root = JsonParser.parseReader(new InputStreamReader(jsonStream, StandardCharsets.UTF_8)).getAsJsonObject();
            int sourceSize = root.get("sourceSize").getAsInt();
            int atlasWidth = root.get("atlasWidth").getAsInt();
            int atlasHeight = root.get("atlasHeight").getAsInt();
            Map<Long, Glyph> glyphs = new HashMap<>();
            for (JsonElement element : root.getAsJsonArray("glyphs")) {
                JsonObject glyph = element.getAsJsonObject();
                int point = glyph.get("codePoint").getAsInt();
                boolean bold = glyph.get("bold").getAsBoolean();
                glyphs.put(glyphKey(point, bold), new Glyph(
                        glyph.get("x").getAsInt(), glyph.get("y").getAsInt(),
                        glyph.get("w").getAsInt(), glyph.get("h").getAsInt(),
                        glyph.get("advance").getAsInt(), glyph.get("padding").getAsInt()));
            }
            return new BakedFont(id, atlasWidth, atlasHeight, sourceSize, glyphs);
        }
    }

    public static void roundedRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        if (!ensureShapeInitialized()) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (radius == 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        context.fill(x + radius, y, x + width - radius, y + height, color);
        context.fill(x, y + radius, x + radius, y + height - radius, color);
        context.fill(x + width - radius, y + radius, x + width, y + height - radius, color);
        int half = SHAPE_SIZE / 2;
        drawShapeRegion(context, 0, 0, half, half, x, y, radius, radius, color);
        drawShapeRegion(context, half, 0, half, half, x + width - radius, y, radius, radius, color);
        drawShapeRegion(context, 0, half, half, half, x, y + height - radius, radius, radius, color);
        drawShapeRegion(context, half, half, half, half, x + width - radius, y + height - radius, radius, radius, color);
    }

    private static void drawShapeRegion(DrawContext context, int sx, int sy, int sw, int sh,
                                        int x, int y, int width, int height, int color) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, SHAPE_ID,
                x, y, sx, sy, width, height, sw, sh, SHAPE_SIZE, SHAPE_SIZE, color);
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        for (int y = 0; y < image.getHeight(); y++) {
            int row = y * image.getWidth();
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setColorArgb(x, y, pixels[row + x]);
            }
        }
        return nativeImage;
    }

    private static long glyphKey(int codePoint, boolean bold) {
        return ((long) codePoint << 1) | (bold ? 1L : 0L);
    }

    private record TextureAsset(Identifier id, int width, int height) { }
    private record Glyph(int x, int y, int w, int h, int advance, int padding) { }

    private record BakedFont(Identifier id, int atlasWidth, int atlasHeight,
                             int sourceSize, Map<Long, Glyph> glyphs) {
        private static final BakedFont EMPTY = new BakedFont(null, 1, 1, 96, Map.of());

        int draw(DrawContext context, String text, float x, float y, float size, int color, boolean bold) {
            if (id == null || text == null || text.isEmpty()) return 0;
            float scale = Math.max(0.01F, size / sourceSize);
            float cursor = x;
            for (int point : text.codePoints().toArray()) {
                Glyph glyph = glyphs.get(glyphKey(point, bold));
                if (glyph == null) glyph = glyphs.get(glyphKey(point, false));
                if (glyph == null) glyph = glyphs.get(glyphKey('?', bold));
                if (glyph == null) continue;
                int pad = Math.max(0, Math.round(glyph.padding() * scale));
                int destWidth = Math.max(1, Math.round(glyph.w() * scale));
                int destHeight = Math.max(1, Math.round(glyph.h() * scale));
                context.drawTexture(RenderPipelines.GUI_TEXTURED, id,
                        Math.round(cursor) - pad, Math.round(y) - pad,
                        glyph.x(), glyph.y(), destWidth, destHeight,
                        glyph.w(), glyph.h(), atlasWidth, atlasHeight, color);
                cursor += glyph.advance() * scale;
            }
            return Math.round(cursor - x);
        }

        int width(String text, float size, boolean bold) {
            if (text == null || text.isEmpty()) return 0;
            float scale = Math.max(0.01F, size / sourceSize);
            float width = 0F;
            for (int point : text.codePoints().toArray()) {
                Glyph glyph = glyphs.get(glyphKey(point, bold));
                if (glyph == null) glyph = glyphs.get(glyphKey(point, false));
                if (glyph == null) glyph = glyphs.get(glyphKey('?', false));
                if (glyph != null) width += glyph.advance() * scale;
            }
            return Math.round(width);
        }
    }

    private static final class LinearTexture extends NativeImageBackedTexture {
        private LinearTexture(java.util.function.Supplier<String> nameSupplier, NativeImage image) {
            super(nameSupplier, image);
            this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        }
    }
}

package dev.raidmine.stafftool.ui;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.chat.UiNotificationCenter;
import dev.raidmine.stafftool.rules.PunishmentType;
import dev.raidmine.stafftool.stats.SessionStats;
import dev.raidmine.stafftool.util.AuthManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Adaptive, high-resolution top HUD used by RM Tools. */
public final class HudOverlay {
    public static final int BASE_WIDTH = 268;
    public static final int BASE_HEIGHT = 42;
    private static final int WARN_BLUE = 0xFF4A9DFF;

    private static final CounterAnimator BAN_COUNTER = new CounterAnimator();
    private static final CounterAnimator MUTE_COUNTER = new CounterAnimator();
    private static final CounterAnimator WARN_COUNTER = new CounterAnimator();

    private static volatile boolean editingInteraction;
    private static volatile boolean editingSelected;

    private HudOverlay() { }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof HudEditorScreen) return;
        SessionStats stats = renderInternal(context, false);
        if (stats != null) HintSidebarOverlay.render(context, stats);
    }

    public static void renderEditable(DrawContext context) {
        renderInternal(context, true);
    }

    public static void setEditingInteraction(boolean interacting) { editingInteraction = interacting; }
    public static void setEditingSelected(boolean selected) {
        editingSelected = selected;
        if (!selected) editingInteraction = false;
    }
    public static boolean isEditingSelected() { return editingSelected; }

    private static SessionStats renderInternal(DrawContext context, boolean editing) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.player == null
                || !RaidMineStaffMod.config().hudEnabled || !AuthManager.canUseMod()) return null;

        SessionStats stats = RaidMineStaffMod.stats();
        Layout layout = layout(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        UiNotificationCenter.Notice notice = editing ? null : UiNotificationCenter.top();
        renderBar(context, layout, stats, notice, editing);
        return stats;
    }

    private static void renderBar(DrawContext context, Layout l, SessionStats stats,
                                  UiNotificationCenter.Notice notice, boolean editing) {
        float transition = notice == null ? 0F : UiNotificationCenter.progress(notice);
        int neutral = UiTheme.argb(247, 12, 14, 18);
        int noticeAccent = notice == null ? UiTheme.accent() : switch (notice.kind()) {
            case VIOLATION -> 0xFFFF486D;
            case MENTION -> 0xFFFFA52B;
            case INFO -> UiTheme.SUCCESS;
        };
        int background = UiTheme.blend(neutral,
                UiTheme.withAlpha(UiTheme.blend(neutral, noticeAccent, 0.13F), 247), transition);
        int border = stats.goalReached() && notice == null ? UiTheme.SUCCESS
                : UiTheme.blend(UiTheme.accent(), noticeAccent, transition);
        int radius = Math.max(14, l.height() / 2);

        if (RaidMineStaffMod.config().hudOutlineEnabled) {
            int outlineAlpha = Math.round(255F * RaidMineStaffMod.config().hudOutlineOpacity);
            int thickness = Math.max(1, Math.round(1.55F * l.contentScale()));
            UiTheme.roundedBorder(context, l.x(), l.y(), l.width(), l.height(), radius,
                    thickness, UiTheme.withAlpha(border, outlineAlpha), background);
            if (RaidMineStaffMod.config().hudBorderRunnersEnabled) {
                drawOrbitingBorder(context, l, border, outlineAlpha, thickness);
            }
        } else {
            UiTheme.roundedRect(context, l.x(), l.y(), l.width(), l.height(), radius, background);
        }
        UiTheme.panelTexture(context, l.x() + 2, l.y() + 2, l.width() - 4, l.height() - 4,
                notice == null ? UiTheme.accent() : noticeAccent);

        if (editing && editingSelected) {
            int alpha = editingInteraction ? 245 : 160;
            UiTheme.roundedBorder(context, l.x() - 1, l.y() - 1, l.width() + 2, l.height() + 2,
                    radius + 1, 1, UiTheme.withAlpha(UiTheme.accent(), alpha), UiTheme.withAlpha(background, 0));
        }

        if (notice == null) {
            renderStatsContent(context, l, stats, 255, 0);
        } else {
            int oldAlpha = Math.round(255F * (1F - transition));
            int newAlpha = Math.round(255F * transition);
            renderStatsContent(context, l, stats, oldAlpha, -Math.round(9F * transition));
            renderNoticeContent(context, l, notice, newAlpha, Math.round(9F * (1F - transition)), noticeAccent);
        }

        if (editing && editingSelected) renderHandles(context, l, UiTheme.accent());
    }

    private static void drawOrbitingBorder(DrawContext context, Layout l, int color, int alpha, int thickness) {
        if (alpha <= 0 || l.width() < 70 || l.height() < 24) return;
        int highlight = UiTheme.blend(color, 0xFFFFFFFF, 0.30F);
        float phase = (System.currentTimeMillis() % 12000L) / 12000F;
        drawRoundedTrail(context, l, phase, 0.34F, thickness, highlight, alpha);
        drawRoundedTrail(context, l, (phase + 0.50F) % 1F, 0.34F, thickness, highlight, alpha);
    }

    /** Draws a long anti-aliased trail along the actual rounded perimeter. */
    private static void drawRoundedTrail(DrawContext context, Layout l, float head,
                                         float trailLength, int thickness, int color, int alpha) {
        int radius = Math.max(10, l.height() / 2);
        int samples = Math.max(36, Math.round((l.width() + l.height()) * trailLength / 2.2F));
        for (int i = 0; i < samples; i++) {
            float local = i / (float) Math.max(1, samples - 1);
            float position = head - local * trailLength;
            while (position < 0F) position += 1F;
            RoundedPoint point = pointOnRoundedRect(l, radius, position);
            float fade = 1F - local;
            int dotAlpha = Math.round(alpha * fade * fade * 0.92F);
            int size = Math.max(2, thickness + Math.round(1.5F * fade));
            UiTheme.roundedRectExact(context, Math.round(point.x()) - size / 2,
                    Math.round(point.y()) - size / 2, size, size, size / 2,
                    UiTheme.withAlpha(color, dotAlpha));
        }
    }

    private static RoundedPoint pointOnRoundedRect(Layout l, int radius, float t) {
        float r = Math.min(radius, Math.min(l.width(), l.height()) / 2F);
        float straightW = Math.max(0F, l.width() - 2F * r);
        float straightH = Math.max(0F, l.height() - 2F * r);
        float arc = (float) (Math.PI * r / 2F);
        float perimeter = 2F * straightW + 2F * straightH + 4F * arc;
        float d = (t - (float) Math.floor(t)) * perimeter;
        float x = l.x() + r;
        float y = l.y();

        if (d <= straightW) return new RoundedPoint(x + d, y);
        d -= straightW;
        if (d <= arc) {
            float a = -1.5707964F + (d / arc) * 1.5707964F;
            return new RoundedPoint(l.x() + l.width() - r + (float) Math.cos(a) * r,
                    l.y() + r + (float) Math.sin(a) * r);
        }
        d -= arc;
        if (d <= straightH) return new RoundedPoint(l.x() + l.width(), l.y() + r + d);
        d -= straightH;
        if (d <= arc) {
            float a = (d / arc) * 1.5707964F;
            return new RoundedPoint(l.x() + l.width() - r + (float) Math.cos(a) * r,
                    l.y() + l.height() - r + (float) Math.sin(a) * r);
        }
        d -= arc;
        if (d <= straightW) return new RoundedPoint(l.x() + l.width() - r - d, l.y() + l.height());
        d -= straightW;
        if (d <= arc) {
            float a = 1.5707964F + (d / arc) * 1.5707964F;
            return new RoundedPoint(l.x() + r + (float) Math.cos(a) * r,
                    l.y() + l.height() - r + (float) Math.sin(a) * r);
        }
        d -= arc;
        if (d <= straightH) return new RoundedPoint(l.x(), l.y() + l.height() - r - d);
        d -= straightH;
        float a = 3.1415927F + (d / arc) * 1.5707964F;
        return new RoundedPoint(l.x() + r + (float) Math.cos(a) * r,
                l.y() + r + (float) Math.sin(a) * r);
    }

    private static void renderStatsContent(DrawContext context, Layout l, SessionStats stats,
                                           int alpha, int yOffset) {
        if (alpha <= 2) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Metrics m = metrics(l, stats);
        int yBase = l.y() + yOffset;
        int cursor = l.x() + m.margin();

        // The glow is rendered first, clipped to the panel, then the logo is placed above it.
        int logoY = yBase + (l.height() - m.logoSize()) / 2;
        context.enableScissor(l.x() + 1, l.y() + 1, l.x() + l.width() - 1, l.y() + l.height() - 1);
        UiTheme.softGlow(context, cursor + m.logoSize() / 2, logoY + m.logoSize() / 2,
                Math.max(16, Math.round(m.logoSize() * 0.72F)),
                Math.max(10, Math.round(m.logoSize() * 0.48F)),
                0xFFFF8A00, Math.round(76F * alpha / 255F));
        context.disableScissor();
        UiTheme.logo(context, cursor, logoY, m.logoSize(), m.logoSize(), alpha);
        cursor += m.logoBlockWidth() + m.gap();

        int brandHeight = Math.round(18F * m.scale());
        int brandY = yBase + (l.height() - brandHeight) / 2;
        UiTheme.brandText(context, "RaidMine", cursor, brandY, Math.max(7.2F, 9.2F * m.scale()),
                UiTheme.withAlpha(UiTheme.TEXT, alpha));
        UiTheme.brandText(context, "Tools", cursor, brandY + Math.round(10F * m.scale()),
                Math.max(6.8F, 8.4F * m.scale()), UiTheme.withAlpha(UiTheme.MUTED, alpha));
        cursor += m.brandWidth() + m.gap();

        cursor = counterPanel(context, tr, cursor, yBase, l, m, UiIcon.BAN, stats.bans(),
                UiTheme.DANGER, m.banWidth(), BAN_COUNTER, alpha);
        cursor += m.gap();
        cursor = counterPanel(context, tr, cursor, yBase, l, m, UiIcon.MUTE, stats.mutes(),
                UiTheme.WARNING, m.muteWidth(), MUTE_COUNTER, alpha);
        cursor += m.gap();
        cursor = counterPanel(context, tr, cursor, yBase, l, m, UiIcon.WARN, stats.warns(),
                WARN_BLUE, m.warnWidth(), WARN_COUNTER, alpha);
        cursor += m.gap();

        int blockY = yBase + (l.height() - m.blockHeight()) / 2;
        int timeColor = stats.goalReached() ? UiTheme.SUCCESS : UiTheme.TEXT;
        UiTheme.roundedRect(context, cursor, blockY, m.timeWidth(), m.blockHeight(),
                Math.max(8, m.blockHeight() / 3), UiTheme.argb(Math.round(112F * alpha / 255F), 31, 34, 42));
        int clockX = cursor + m.innerPad();
        int clockY = blockY + (m.blockHeight() - m.iconSize()) / 2;
        UiTheme.icon(context, UiIcon.CLOCK, clockX, clockY, m.iconSize(), UiTheme.withAlpha(timeColor, alpha));
        String time = formatTime(stats.elapsedSeconds());
        float timeSize = Math.max(7.2F, 8.9F * m.scale());
        int timeTextY = blockY + (m.blockHeight() - Math.round(timeSize)) / 2;
        UiTheme.text(context, tr, time, clockX + m.iconSize() + m.innerPad(), timeTextY,
                timeSize, UiTheme.withAlpha(timeColor, alpha), true);
        cursor += m.timeWidth() + m.gap();

        UiTheme.roundedRect(context, cursor, blockY, m.eyeWidth(), m.blockHeight(),
                Math.max(8, m.blockHeight() / 3), UiTheme.argb(Math.round(92F * alpha / 255F), 32, 35, 43));
        int eyeSize = Math.max(12, Math.round(15F * m.scale()));
        int eyeX = cursor + (m.eyeWidth() - eyeSize) / 2;
        int eyeY = blockY + (m.blockHeight() - eyeSize) / 2;
        if (stats.isVanished()) {
            // Only the icon receives a soft purple glow. The neutral tile never changes color.
            int purple = 0xFFC06CFF;
            UiTheme.softGlow(context, eyeX + eyeSize / 2, eyeY + eyeSize / 2,
                    Math.max(9, eyeSize), Math.max(7, eyeSize * 3 / 4),
                    purple, Math.round(72F * alpha / 255F));
            UiTheme.icon(context, UiIcon.EYE, eyeX, eyeY, eyeSize, UiTheme.withAlpha(purple, alpha));
        } else {
            UiTheme.icon(context, UiIcon.EYE_OFF, eyeX, eyeY, eyeSize,
                    UiTheme.withAlpha(UiTheme.argb(255, 73, 77, 88), alpha));
        }
    }

    private static int counterPanel(DrawContext context, TextRenderer tr, int x, int yBase, Layout l, Metrics m,
                                    UiIcon icon, int value, int accent, int baseWidth, CounterAnimator animator, int alpha) {
        animator.update(value);
        float progress = animator.transitionProgress();
        float effect = animator.effectIntensity();
        boolean active = effect > 0.001F;
        int y = yBase + (l.height() - m.blockHeight()) / 2;
        int width = Math.max(baseWidth, Math.max(UiTheme.textWidth(Integer.toString(value), Math.max(7.1F, 8.4F * m.scale()), true),
                UiTheme.textWidth(Integer.toString(animator.previous()), Math.max(7.1F, 8.4F * m.scale()), true)) + m.innerPad() * 2);
        int radius = Math.max(8, m.blockHeight() / 3);
        int neutral = UiTheme.argb(Math.round(96F * alpha / 255F), 31, 34, 42);

        if (active) {
            int pulseAlpha = Math.round(alpha * (0.20F * effect));
            int borderAlpha = Math.round(alpha * (0.30F + 0.62F * effect));
            UiTheme.softGlow(context, x + width / 2, y + m.blockHeight() / 2,
                    Math.max(12, width / 2), Math.max(7, m.blockHeight() / 2),
                    accent, Math.round(34F * effect));
            UiTheme.roundedBorder(context, x, y, width, m.blockHeight(), radius, 1,
                    UiTheme.withAlpha(accent, borderAlpha),
                    UiTheme.withAlpha(UiTheme.blend(UiTheme.argb(255, 31, 34, 42), accent, 0.10F * effect), alpha));
            UiTheme.roundedRectExact(context, x + 2, y + 2, width - 4, m.blockHeight() - 4,
                    Math.max(6, radius - 2), UiTheme.withAlpha(accent, pulseAlpha));
        } else {
            UiTheme.roundedRect(context, x, y, width, m.blockHeight(), radius, neutral);
        }

        int iconX = x + (width - m.iconSize()) / 2;
        int iconY = y + Math.max(2, Math.round(3F * m.scale()));
        UiTheme.icon(context, icon, iconX, iconY, m.iconSize(), UiTheme.withAlpha(accent, alpha));

        float numberSize = Math.max(7.1F, 8.4F * m.scale());
        int baseline = y + m.blockHeight() - Math.max(10, Math.round(11F * m.scale()));
        if (progress < 1F) {
            float eased = UiTheme.easeOutCubic(progress);
            drawAnimatedNumber(context, tr, Integer.toString(animator.previous()), x, width,
                    baseline + Math.round(8F * eased), numberSize,
                    UiTheme.withAlpha(UiTheme.TEXT, Math.round(alpha * (1F - eased))));
            drawAnimatedNumber(context, tr, Integer.toString(animator.current()), x, width,
                    baseline + Math.round(8F * (1F - eased)), numberSize,
                    UiTheme.withAlpha(UiTheme.TEXT, Math.round(alpha * eased)));
        } else {
            drawAnimatedNumber(context, tr, Integer.toString(value), x, width, baseline,
                    numberSize, UiTheme.withAlpha(UiTheme.TEXT, alpha));
        }
        return x + width;
    }

    private static void drawAnimatedNumber(DrawContext context, TextRenderer tr, String value,
                                           int x, int width, int y, float size, int color) {
        int textW = UiTheme.textWidth(value, size, true);
        UiTheme.text(context, tr, value, x + (width - textW) / 2, y, size, color, true);
    }

    private static Metrics metrics(Layout l, SessionStats stats) {
        float scale = Math.max(0.70F, Math.min(1.55F, l.height() / (float) BASE_HEIGHT));
        int margin = Math.max(5, Math.round(7F * scale));
        int gap = Math.max(2, Math.round(3F * scale));
        int blockH = Math.max(27, Math.min(l.height() - margin, Math.round(33F * scale)));
        int icon = Math.max(11, Math.round(13F * scale));
        int inner = Math.max(4, Math.round(5F * scale));
        int logo = Math.max(34, Math.min(l.height() - 2, Math.round(40F * scale)));
        int logoBlock = logo + Math.max(1, Math.round(2F * scale));
        int brand = Math.max(Math.round(49F * scale),
                Math.max(UiTheme.brandTextWidth("RaidMine", Math.max(7.2F, 9.2F * scale)),
                        UiTheme.brandTextWidth("Tools", Math.max(6.8F, 8.4F * scale))) + 2);
        float numSize = Math.max(7.1F, 8.4F * scale);
        int banW = Math.max(Math.round(27F * scale), UiTheme.textWidth(Integer.toString(stats.bans()), numSize, true) + inner * 2);
        int muteW = Math.max(Math.round(27F * scale), UiTheme.textWidth(Integer.toString(stats.mutes()), numSize, true) + inner * 2);
        int warnW = Math.max(Math.round(27F * scale), UiTheme.textWidth(Integer.toString(stats.warns()), numSize, true) + inner * 2);
        String time = formatTime(stats.elapsedSeconds());
        float timeSize = Math.max(7.2F, 8.9F * scale);
        int timeW = icon + inner * 3 + UiTheme.textWidth(time, timeSize, true);
        int eyeW = Math.max(Math.round(24F * scale), icon + inner * 2);

        int fixed = margin * 2 + logoBlock + brand + banW + muteW + warnW + timeW + eyeW + gap * 6;
        int extra = Math.max(0, l.width() - fixed);
        if (extra > 0) {
            brand += extra * 20 / 100;
            banW += extra * 12 / 100;
            muteW += extra * 12 / 100;
            warnW += extra * 12 / 100;
            timeW += extra * 26 / 100;
            eyeW += extra * 8 / 100;
            int used = extra * 90 / 100;
            gap += Math.max(0, (extra - used) / 6);
        }
        return new Metrics(scale, margin, gap, blockH, icon, inner, logo, logoBlock,
                brand, banW, muteW, warnW, timeW, eyeW);
    }

    private static void renderNoticeContent(DrawContext context, Layout l, UiNotificationCenter.Notice notice,
                                            int alpha, int yOffset, int accent) {
        if (alpha <= 2) return;
        float s = Math.max(0.72F, Math.min(1.55F, l.height() / (float) BASE_HEIGHT));
        int y = l.y() + yOffset;
        int iconSize = Math.max(17, Math.round(20F * s));
        int iconX = l.x() + Math.max(9, Math.round(11F * s));
        int iconY = y + (l.height() - iconSize) / 2;
        UiIcon icon = notice.kind() == UiNotificationCenter.Kind.MENTION ? UiIcon.BELL
                : notice.kind() == UiNotificationCenter.Kind.VIOLATION ? UiIcon.WARN : UiIcon.CHECK;
        UiTheme.icon(context, icon, iconX, iconY, iconSize, UiTheme.withAlpha(accent, alpha));
        int textX = iconX + iconSize + Math.max(6, Math.round(8F * s));
        int maxWidth = l.x() + l.width() - textX - Math.max(8, Math.round(10F * s));
        float titleSize = Math.max(7.8F, 9.2F * s);
        float messageSize = Math.max(6.8F, 7.9F * s);
        String title = UiTheme.ellipsize(MinecraftClient.getInstance().textRenderer, notice.title(), maxWidth);
        String message = UiTheme.ellipsize(MinecraftClient.getInstance().textRenderer, notice.message(), maxWidth);
        int totalTextH = Math.round(titleSize + messageSize + 4F * s);
        int top = y + (l.height() - totalTextH) / 2;
        UiTheme.text(context, MinecraftClient.getInstance().textRenderer, title,
                textX, top, titleSize, UiTheme.withAlpha(UiTheme.TEXT, alpha), true);
        UiTheme.text(context, MinecraftClient.getInstance().textRenderer, message,
                textX, top + Math.round(titleSize + 3F * s), messageSize,
                UiTheme.withAlpha(UiTheme.MUTED, alpha), false);
    }

    private static void renderHandles(DrawContext context, Layout l, int color) {
        for (Handle handle : l.handles()) {
            Rect r = handle.rect();
            UiTheme.roundedRectExact(context, r.x(), r.y(), r.w(), r.h(),
                    Math.max(3, r.w() / 2), UiTheme.withAlpha(color, 245));
        }
    }

    public static Layout layout(int screenWidth, int screenHeight) {
        float widthScale = RaidMineStaffMod.config().hudWidthScale;
        float heightScale = RaidMineStaffMod.config().hudHeightScale;
        int configuredWidth = Math.max(238, Math.round(BASE_WIDTH * widthScale));
        int height = Math.max(34, Math.round(BASE_HEIGHT * heightScale));
        int minimumWidth = minimumContentWidth(height, RaidMineStaffMod.stats());
        int width = Math.min(Math.max(1, screenWidth), Math.max(configuredWidth, minimumWidth));
        int availableX = Math.max(0, screenWidth - width);
        int availableY = Math.max(0, screenHeight - height);
        int x = Math.round(availableX * RaidMineStaffMod.config().hudX);
        int y = Math.round(availableY * RaidMineStaffMod.config().hudY);
        x = Math.max(0, Math.min(availableX, x));
        y = Math.max(0, Math.min(availableY, y));
        float contentScale = Math.max(0.70F, Math.min(1.55F, height / (float) BASE_HEIGHT));
        return new Layout(x, y, width, height, widthScale, heightScale, contentScale);
    }


    private static int minimumContentWidth(int height, SessionStats stats) {
        float scale = Math.max(0.70F, Math.min(1.55F, height / (float) BASE_HEIGHT));
        int margin = Math.max(5, Math.round(7F * scale));
        int gap = Math.max(2, Math.round(3F * scale));
        int icon = Math.max(11, Math.round(13F * scale));
        int inner = Math.max(4, Math.round(5F * scale));
        int logo = Math.max(34, Math.min(height - 2, Math.round(40F * scale)));
        int brand = Math.max(Math.round(49F * scale),
                Math.max(UiTheme.brandTextWidth("RaidMine", Math.max(7.2F, 9.2F * scale)),
                        UiTheme.brandTextWidth("Tools", Math.max(6.8F, 8.4F * scale))) + 2);
        float numberSize = Math.max(7.1F, 8.4F * scale);
        int ban = Math.max(Math.round(27F * scale), UiTheme.textWidth(Integer.toString(stats.bans()), numberSize, true) + inner * 2);
        int mute = Math.max(Math.round(27F * scale), UiTheme.textWidth(Integer.toString(stats.mutes()), numberSize, true) + inner * 2);
        int warn = Math.max(Math.round(27F * scale), UiTheme.textWidth(Integer.toString(stats.warns()), numberSize, true) + inner * 2);
        int time = icon + inner * 3 + UiTheme.textWidth(formatTime(stats.elapsedSeconds()), Math.max(7.2F, 8.9F * scale), true);
        int eye = Math.max(Math.round(24F * scale), icon + inner * 2);
        return margin * 2 + logo + Math.max(1, Math.round(2F * scale)) + brand
                + ban + mute + warn + time + eye + gap * 6;
    }

    public static void setPosition(int screenWidth, int screenHeight, int x, int y) {
        Layout current = layout(screenWidth, screenHeight);
        int maxX = Math.max(1, screenWidth - current.width());
        int maxY = Math.max(1, screenHeight - current.height());
        RaidMineStaffMod.config().hudX = Math.max(0, Math.min(maxX, x)) / (float) maxX;
        RaidMineStaffMod.config().hudY = Math.max(0, Math.min(maxY, y)) / (float) maxY;
    }

    public static void nudge(int screenWidth, int screenHeight, int dx, int dy) {
        Layout current = layout(screenWidth, screenHeight);
        setPosition(screenWidth, screenHeight, current.x() + dx, current.y() + dy);
        RaidMineStaffMod.config().save();
    }

    public static void setWidthScale(float scale) {
        RaidMineStaffMod.config().hudWidthScale = Math.max(0.76F, Math.min(1.80F, scale));
    }
    public static void setHeightScale(float scale) {
        RaidMineStaffMod.config().hudHeightScale = Math.max(0.72F, Math.min(1.80F, scale));
    }
    public static void setScale(float scale) { setWidthScale(scale); setHeightScale(scale); }
    public static void centerTop() {
        RaidMineStaffMod.config().hudX = 0.5F;
        RaidMineStaffMod.config().hudY = 0.015F;
        RaidMineStaffMod.config().save();
    }
    public static void reset() {
        RaidMineStaffMod.config().hudX = 0.5F;
        RaidMineStaffMod.config().hudY = 0.015F;
        RaidMineStaffMod.config().hudWidthScale = 0.88F;
        RaidMineStaffMod.config().hudHeightScale = 1.00F;
        RaidMineStaffMod.config().save();
    }

    private static String formatTime(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, secs)
                : String.format("%02d:%02d", minutes, secs);
    }

    public enum Edge { MOVE, N, S, E, W, NE, NW, SE, SW }
    public record Handle(Edge edge, Rect rect) { }
    public record Layout(int x, int y, int width, int height,
                         float widthScale, float heightScale, float contentScale) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
        public Handle[] handles() {
            int size = Math.max(7, Math.round(8 * contentScale));
            int half = size / 2;
            int cx = x + width / 2;
            int cy = y + height / 2;
            return new Handle[]{
                    new Handle(Edge.NW, new Rect(x - half, y - half, size, size)),
                    new Handle(Edge.N, new Rect(cx - half, y - half, size, size)),
                    new Handle(Edge.NE, new Rect(x + width - half, y - half, size, size)),
                    new Handle(Edge.W, new Rect(x - half, cy - half, size, size)),
                    new Handle(Edge.E, new Rect(x + width - half, cy - half, size, size)),
                    new Handle(Edge.SW, new Rect(x - half, y + height - half, size, size)),
                    new Handle(Edge.S, new Rect(cx - half, y + height - half, size, size)),
                    new Handle(Edge.SE, new Rect(x + width - half, y + height - half, size, size))
            };
        }
        public Edge edgeAt(double mouseX, double mouseY) {
            if (editingSelected) {
                for (Handle handle : handles()) if (handle.rect().contains(mouseX, mouseY)) return handle.edge();
            }
            return contains(mouseX, mouseY) ? Edge.MOVE : null;
        }
    }
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    private record RoundedPoint(float x, float y) { }

    private record Metrics(float scale, int margin, int gap, int blockHeight, int iconSize, int innerPad,
                           int logoSize, int logoBlockWidth, int brandWidth,
                           int banWidth, int muteWidth, int warnWidth, int timeWidth, int eyeWidth) {
    }

    private static final class CounterAnimator {
        private static final long NUMBER_TRANSITION_MILLIS = 920L;
        private static final long EFFECT_MILLIS = 5000L;
        private static final long EFFECT_FADE_IN = 520L;
        private static final long EFFECT_FADE_OUT = 1300L;

        private int previous;
        private int current;
        private boolean initialized;
        private long changedAt;

        void update(int value) {
            if (!initialized) {
                previous = value;
                current = value;
                initialized = true;
                changedAt = 0L;
                return;
            }
            if (value != current) {
                previous = current;
                current = value;
                changedAt = System.currentTimeMillis();
            }
        }

        float transitionProgress() {
            if (changedAt == 0L) return 1F;
            return Math.max(0F, Math.min(1F,
                    (System.currentTimeMillis() - changedAt) / (float) NUMBER_TRANSITION_MILLIS));
        }

        float effectIntensity() {
            if (changedAt == 0L) return 0F;
            long age = System.currentTimeMillis() - changedAt;
            if (age < 0L || age >= EFFECT_MILLIS) return 0F;
            if (age < EFFECT_FADE_IN) {
                return UiTheme.easeOutCubic(age / (float) EFFECT_FADE_IN);
            }
            long remaining = EFFECT_MILLIS - age;
            if (remaining < EFFECT_FADE_OUT) {
                float t = remaining / (float) EFFECT_FADE_OUT;
                return t * t * (3F - 2F * t);
            }
            return 1F;
        }

        int previous() { return previous; }
        int current() { return current; }
    }

}

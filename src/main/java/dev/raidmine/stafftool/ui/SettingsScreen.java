package dev.raidmine.stafftool.ui;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.config.ForbiddenWordsStore;
import dev.raidmine.stafftool.config.ModConfig;
import dev.raidmine.stafftool.util.FolderOpener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SettingsScreen extends Screen {
    private static final int[] PRESETS = {
            0xFFFF8A00, 0xFFFF4D3A, 0xFFFFB020, 0xFFFF6B00,
            0xFFFF5F7A, 0xFFFF2E63, 0xFFB36BFF, 0xFF6D5DFF,
            0xFF45B7FF, 0xFF3978FF, 0xFF36D1A5, 0xFF0EA87A,
            0xFFFFD24A, 0xFFFF8A00, 0xFFF5F7FA, 0xFF9AA4B2,
            0xFF59E1FF, 0xFF4A7BFF, 0xFFFF77C8, 0xFFB541FF
    };
    private static final String[] FONT_VALUES = {
            "AUTO", "NUNITO", "GERHAUS", "MONTSERRAT_ALTERNATES",
            "MIGHTY_ZEO", "XANMONO", "SHOPTRONIC_SP", "GNF",
            "ETUDE_NOIRE_NEW", "NUQUN", "PIXY", "HEMICO",
            "ETUDE_NOIRE", "SEENONIM", "MINECRAFT"
    };
    private static final String[] FONT_LABELS = {
            "Авто — Hemico", "Nunito", "Gerhaus", "Montserrat Alternates",
            "Mighty Zeo", "Xanmono", "Shoptronic SP", "GNF",
            "Etude Noire New", "Nuqun", "Pixy", "Hemico",
            "Etude Noire", "Seenonim", "Minecraft"
    };

    private final Screen parent;
    private final int[] scroll = new int[Tab.values().length];
    private final Map<String, Float> toggleAnimation = new HashMap<>();

    private Tab tab = Tab.APPEARANCE;
    private Tab previousTab = Tab.APPEARANCE;
    private long tabChangedAt;
    private TextFieldWidget wordField;
    private TextFieldWidget accentField;
    private TextFieldWidget reasonField;
    private TextFieldWidget backgroundPercentField;
    private TextFieldWidget uiOutlinePercentField;
    private TextFieldWidget hudOutlinePercentField;
    private String status;
    private long statusAt;
    private DragTarget dragTarget = DragTarget.NONE;

    public SettingsScreen(Screen parent) {
        super(Text.literal("RM Tools — настройки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = RaidMineStaffMod.config();
        wordField = field("Добавить слово", 96, "");
        accentField = field("Цвет интерфейса", 7, String.format(Locale.ROOT, "#%06X", config.accentColor & 0xFFFFFF));
        reasonField = field("Формат причины", 64, config.punishmentReasonTemplate);
        backgroundPercentField = percentField(config.uiBackgroundOpacity);
        uiOutlinePercentField = percentField(config.uiOutlineOpacity);
        hudOutlinePercentField = percentField(config.hudOutlineOpacity);
    }

    private TextFieldWidget percentField(float value) {
        return field("Проценты", 3, Integer.toString(Math.round(value * 100F)));
    }

    private TextFieldWidget field(String label, int maxLength, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, 0, 0, 10, 28, Text.literal(label));
        field.setMaxLength(maxLength);
        field.setText(value);
        field.setDrawsBackground(false);
        field.setEditableColor(UiTheme.TEXT);
        field.setUneditableColor(UiTheme.FAINT);
        return field;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height,
                UiTheme.surface(UiTheme.argb(238, 3, 4, 7)), UiTheme.surface(UiTheme.argb(248, 11, 12, 16)));
        Layout l = layout();
        UiTheme.shadow(context, l.x(), l.y(), l.w(), l.h(), 20);
        int outlineAlpha = Math.round(255F * RaidMineStaffMod.config().uiOutlineOpacity);
        if (outlineAlpha > 0) {
            UiTheme.roundedBorder(context, l.x(), l.y(), l.w(), l.h(), 20, 2,
                    UiTheme.withAlpha(UiTheme.accent(), outlineAlpha), UiTheme.BG);
        } else {
            UiTheme.roundedRect(context, l.x(), l.y(), l.w(), l.h(), 20, UiTheme.BG);
        }
        UiTheme.panelTexture(context, l.x() + 2, l.y() + 2, l.w() - 4, l.h() - 4, UiTheme.accent());

        renderHeader(context, l, mouseX, mouseY);
        renderTabs(context, l, mouseX, mouseY);
        Rect viewport = viewport(l);
        context.enableScissor(viewport.x(), viewport.y(), viewport.x() + viewport.w(), viewport.y() + viewport.h());
        float progress = Math.min(1F, (System.currentTimeMillis() - tabChangedAt) / 330F);
        // The old tab is not rendered behind the new one: this removes the ghost panel.
        UiTheme.roundedRectExact(context, viewport.x(), viewport.y(), viewport.w(), viewport.h(),
                0, UiTheme.surface(UiTheme.argb(248, 8, 9, 12)));
        if (tabChangedAt > 0L && progress < 1F && previousTab != tab) {
            float eased = UiTheme.easeOutCubic(progress);
            int direction = tab.ordinal() >= previousTab.ordinal() ? 1 : -1;
            int newOffset = Math.round(viewport.w() * 0.24F * (1F - eased)) * direction;
            renderTab(context, tab, l, mouseX - newOffset, mouseY, newOffset,
                    0.955F + 0.045F * eased, true);
        } else {
            renderTab(context, tab, l, mouseX, mouseY, 0, 1F, true);
        }
        context.disableScissor();
        renderScrollbar(context, l);
        renderStatus(context, l);
    }

    private void renderTab(DrawContext context, Tab candidate, Layout l, int mouseX, int mouseY,
                           int xOffset, float scale, boolean interactive) {
        context.getMatrices().pushMatrix();
        float cx = l.x() + l.w() / 2F;
        float cy = l.y() + l.h() / 2F;
        context.getMatrices().translate(cx + xOffset, cy);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-cx, -cy);
        switch (candidate) {
            case APPEARANCE -> renderAppearance(context, l, mouseX, mouseY, scroll[candidate.ordinal()], interactive);
            case MODERATION -> renderModeration(context, l, mouseX, mouseY, scroll[candidate.ordinal()], interactive);
            case WORDS -> renderWords(context, l, mouseX, mouseY, scroll[candidate.ordinal()], interactive);
        }
        context.getMatrices().popMatrix();
    }

    private void renderHeader(DrawContext context, Layout l, int mouseX, int mouseY) {
        UiTheme.logo(context, l.x() + 18, l.y() + 9, 50, 50, 255);
        UiTheme.text(context, textRenderer, "RM Tools", l.x() + 80, l.y() + 16, 16F, UiTheme.TEXT, true);
        UiTheme.text(context, textRenderer, "Персонализация, модерация и контроль чата",
                l.x() + 80, l.y() + 39, 9.4F, UiTheme.MUTED, false);
        Rect close = closeRect(l);
        UiTheme.roundedRect(context, close.x(), close.y(), close.w(), close.h(), 10,
                close.contains(mouseX, mouseY) ? UiTheme.CARD_HOVER : UiTheme.CARD);
        UiTheme.icon(context, UiIcon.CLOSE, close.x() + 8, close.y() + 8, 14, UiTheme.MUTED);
    }

    private void renderTabs(DrawContext context, Layout l, int mouseX, int mouseY) {
        int x = l.x() + 18;
        int y = l.y() + 70;
        int w = (l.w() - 44) / 3;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab candidate = Tab.values()[i];
            Rect rect = new Rect(x + i * (w + 4), y, w, 34);
            boolean selected = tab == candidate;
            UiTheme.roundedRect(context, rect.x(), rect.y(), rect.w(), rect.h(), 10,
                    selected ? UiTheme.withAlpha(UiTheme.accent(), 78)
                            : rect.contains(mouseX, mouseY) ? UiTheme.CARD_HOVER : UiTheme.CARD);
            if (selected) UiTheme.roundedRectExact(context, rect.x() + 12, rect.y() + rect.h() - 2,
                    rect.w() - 24, 2, 1, UiTheme.accent());
            UiTheme.icon(context, candidate.icon, rect.x() + 12, rect.y() + 10, 14,
                    selected ? UiTheme.accent() : UiTheme.FAINT);
            UiTheme.text(context, textRenderer,
                    UiTheme.ellipsize(textRenderer, candidate.label, rect.w() - 46),
                    rect.x() + 34, rect.y() + 11, 9.2F,
                    selected ? UiTheme.TEXT : UiTheme.MUTED, true);
        }
    }

    private void renderAppearance(DrawContext context, Layout l, int mouseX, int mouseY, int offset, boolean interactive) {
        Rect viewport = viewport(l);
        int x = l.x() + 20;
        int y = viewport.y() - offset;
        int w = l.w() - 40;
        int half = (w - 14) / 2;
        int rightX = x + half + 14;

        card(context, x, y, half, 610, "Цвет и прозрачность", UiIcon.PALETTE);
        card(context, rightX, y, half, 610, "Шрифт интерфейса", UiIcon.FONT);

        UiTheme.text(context, textRenderer, "Готовые темы", x + 18, y + 46, 9.3F, UiTheme.MUTED, false);
        for (int i = 0; i < PRESETS.length / 2; i++) {
            Rect r = presetRect(x, y, i);
            int primary = PRESETS[i * 2];
            int secondary = PRESETS[i * 2 + 1];
            UiTheme.roundedRectExact(context, r.x(), r.y(), r.w(), r.h(), 10, primary);
            context.fillGradient(r.x() + r.w() / 2, r.y(), r.x() + r.w(), r.y() + r.h(), primary, secondary);
            if ((RaidMineStaffMod.config().accentColor & 0xFFFFFF) == (primary & 0xFFFFFF)) {
                UiTheme.icon(context, UiIcon.CHECK, r.x() + (r.w() - 14) / 2, r.y() + (r.h() - 14) / 2, 14, UiTheme.TEXT);
            }
        }

        int fieldY = y + 176;
        accentField.setX(x + 18); accentField.setY(fieldY); accentField.setWidth(half - 86);
        input(context, accentField, "HEX-цвет");
        Rect apply = new Rect(x + half - 58, fieldY, 40, 28);
        button(context, apply, "OK", apply.contains(mouseX, mouseY), true, UiIcon.CHECK);

        renderSliderWithInput(context, "Прозрачность фона", backgroundTrack(x, y, half),
                RaidMineStaffMod.config().uiBackgroundOpacity, backgroundPercentField);
        renderSliderWithInput(context, "Прозрачность обводки меню", uiOutlineTrack(x, y, half),
                RaidMineStaffMod.config().uiOutlineOpacity, uiOutlinePercentField);

        Rect hudOutline = hudOutlineToggle(x, y, half);
        toggle(context, hudOutline, "Обводка верхней панели", RaidMineStaffMod.config().hudOutlineEnabled,
                mouseX, mouseY, UiIcon.RESIZE, "hud_outline");
        if (RaidMineStaffMod.config().hudOutlineEnabled) {
            renderSliderWithInput(context, "Прозрачность обводки панели", hudOutlineTrack(x, y, half),
                    RaidMineStaffMod.config().hudOutlineOpacity, hudOutlinePercentField);
        } else {
            drawWrapped(context, "После включения появится отдельная настройка прозрачности обводки панели.",
                    x + 18, hudOutline.y() + 43, half - 36, 8.2F, UiTheme.FAINT, false);
        }
        Rect runners = hudRunnersToggle(x, y, half);
        toggle(context, runners, "Две бегущие линии по краю",
                RaidMineStaffMod.config().hudBorderRunnersEnabled,
                mouseX, mouseY, UiIcon.MOVE, "hud_runners");
        drawWrapped(context, "Линии движутся медленно по сглаженному контуру панели и могут быть выключены отдельно.",
                x + 18, runners.y() + 43, half - 36, 8.2F, UiTheme.FAINT, false);

        UiTheme.text(context, textRenderer, "Текущий шрифт: " + currentFontLabel(),
                rightX + 18, y + 46, 9.2F, UiTheme.MUTED, false);
        for (int i = 0; i < FONT_VALUES.length; i++) {
            Rect r = fontRect(rightX, y, half, i);
            boolean selected = RaidMineStaffMod.config().fontFamily.equalsIgnoreCase(FONT_VALUES[i]);
            UiTheme.roundedRect(context, r.x(), r.y(), r.w(), r.h(), 10,
                    selected ? UiTheme.withAlpha(UiTheme.accent(), 74)
                            : r.contains(mouseX, mouseY) ? UiTheme.CARD_HOVER : UiTheme.CARD);
            UiTheme.icon(context, UiIcon.FONT, r.x() + 9, r.y() + 9, 14,
                    selected ? UiTheme.accent() : UiTheme.FAINT);
            UiTheme.text(context, textRenderer,
                    UiTheme.ellipsize(textRenderer, FONT_LABELS[i], r.w() - 42),
                    r.x() + 31, r.y() + 10, 8.5F,
                    selected ? UiTheme.TEXT : UiTheme.MUTED, true);
        }
        int infoY = y + 66 + ((FONT_VALUES.length + 1) / 2) * 40 + 12;
        drawWrapped(context,
                "Все загруженные шрифты запечены в крупные сглаженные атласы. Исходные TTF не включены в мод.",
                rightX + 18, infoY, half - 36, 8.2F, UiTheme.FAINT, false);
    }

    private void renderModeration(DrawContext context, Layout l, int mouseX, int mouseY, int offset, boolean interactive) {
        Rect viewport = viewport(l);
        int x = l.x() + 20;
        int y = viewport.y() - offset;
        int w = l.w() - 40;
        int half = (w - 14) / 2;
        int rightX = x + half + 14;
        card(context, x, y, half, 450, "Автоматизация", UiIcon.TIMER);
        card(context, rightX, y, half, 450, "Правила и уведомления", UiIcon.SHIELD);

        int rowY = y + 46;
        toggle(context, new Rect(x + 18, rowY, half - 36, 38), "AFK Kick → /hub",
                RaidMineStaffMod.config().afkKickEnabled, mouseX, mouseY, UiIcon.TIMER, "afk");
        rowY += 48;
        toggle(context, new Rect(x + 18, rowY, half - 36, 38), "Автоматический скриншот",
                RaidMineStaffMod.config().autoScreenshot, mouseX, mouseY, UiIcon.SCREENSHOT, "shot");
        rowY += 48;
        toggle(context, new Rect(x + 18, rowY, half - 36, 38), "Панель подсказок",
                RaidMineStaffMod.config().showHintsPanel, mouseX, mouseY, UiIcon.BELL, "hints");

        int goalY = y + 210;
        UiTheme.text(context, textRenderer, "Дневная норма онлайна", x + 18, goalY, 9.2F, UiTheme.MUTED, false);
        Rect minus = new Rect(x + 18, goalY + 21, 34, 30);
        Rect plus = new Rect(x + half - 52, goalY + 21, 34, 30);
        button(context, minus, "−", minus.contains(mouseX, mouseY), false, UiIcon.CHEVRON_LEFT);
        button(context, plus, "+", plus.contains(mouseX, mouseY), false, UiIcon.PLUS);
        String goal = RaidMineStaffMod.config().dailyOnlineGoalMinutes + " мин";
        UiTheme.text(context, textRenderer, goal, x + (half - UiTheme.textWidth(goal, 12F, true)) / 2,
                goalY + 29, 12F, UiTheme.TEXT, true);
        drawWrapped(context, "Сброс происходит ежедневно в 00:00 по Москве. В HUB и после минуты AFK время не идёт.",
                x + 18, goalY + 64, half - 36, 8.3F, UiTheme.FAINT, false);

        rowY = y + 46;
        toggle(context, new Rect(rightX + 18, rowY, half - 36, 38), "Уведомления об упоминании",
                RaidMineStaffMod.config().mentionNotifications, mouseX, mouseY, UiIcon.BELL, "mentions");
        rowY += 48;
        toggle(context, new Rect(rightX + 18, rowY, half - 36, 38), "Контроль запрещённых слов",
                RaidMineStaffMod.config().forbiddenWordAlerts, mouseX, mouseY, UiIcon.WARN, "words");
        UiTheme.text(context, textRenderer, "Формат причины", rightX + 18, y + 158, 9.2F, UiTheme.MUTED, false);
        reasonField.setX(rightX + 18); reasonField.setY(y + 178); reasonField.setWidth(half - 36);
        input(context, reasonField, "{rule} — только пункт правил");
        drawWrapped(context,
                "AFK Kick работает только в мультиплеере. Через 4 минуты без движения мод отправляет /hub раньше серверного кика.",
                rightX + 18, y + 224, half - 36, 8.3F, UiTheme.FAINT, false);
    }

    private void renderWords(DrawContext context, Layout l, int mouseX, int mouseY, int offset, boolean interactive) {
        Rect viewport = viewport(l);
        int x = l.x() + 20;
        int y = viewport.y() - offset;
        int w = l.w() - 40;
        int contentH = Math.max(430, 142 + RaidMineStaffMod.config().forbiddenWords.size() * 34);
        card(context, x, y, w, contentH, "Запрещённые слова", UiIcon.WARN);
        drawWrapped(context,
                "Список хранится в config/rm_tools/forbidden_words.json. Файл можно копировать другим модераторам.",
                x + 18, y + 43, w - 36, 8.8F, UiTheme.MUTED, false);

        wordField.setX(x + 18); wordField.setY(y + 78); wordField.setWidth(Math.min(370, w - 300));
        input(context, wordField, "Введите слово или фразу");
        Rect add = new Rect(wordField.getX() + wordField.getWidth() + 10, wordField.getY(), 84, 28);
        button(context, add, "ДОБАВИТЬ", add.contains(mouseX, mouseY), true, UiIcon.PLUS);
        Rect open = new Rect(x + w - 188, wordField.getY(), 82, 28);
        button(context, open, "ПАПКА", open.contains(mouseX, mouseY), false, UiIcon.FOLDER);
        Rect reload = new Rect(x + w - 96, wordField.getY(), 78, 28);
        button(context, reload, "ОБНОВИТЬ", reload.contains(mouseX, mouseY), false, UiIcon.RELOAD);

        List<String> words = RaidMineStaffMod.config().forbiddenWords;
        int listY = y + 122;
        if (words.isEmpty()) {
            UiTheme.text(context, textRenderer, "Список пуст. Добавьте первое слово.", x + 18, listY + 8, 9F, UiTheme.FAINT, false);
            return;
        }
        for (int i = 0; i < words.size(); i++) {
            Rect row = new Rect(x + 18, listY + i * 34, w - 36, 28);
            UiTheme.roundedRect(context, row.x(), row.y(), row.w(), row.h(), 9,
                    row.contains(mouseX, mouseY) ? UiTheme.CARD_HOVER : UiTheme.CARD);
            UiTheme.roundedRectExact(context, row.x() + 9, row.y() + 10, 8, 8, 4, UiTheme.accent());
            UiTheme.text(context, textRenderer, words.get(i), row.x() + 26, row.y() + 9, 9.2F, UiTheme.TEXT, true);
            Rect remove = removeRect(row);
            UiTheme.icon(context, UiIcon.TRASH, remove.x() + 5, remove.y() + 6, 13,
                    remove.contains(mouseX, mouseY) ? UiTheme.DANGER : UiTheme.FAINT);
        }
    }

    private void renderSliderWithInput(DrawContext context, String label, Rect track, float value, TextFieldWidget field) {
        UiTheme.text(context, textRenderer, label, track.x(), track.y() - 23, 9.1F, UiTheme.MUTED, false);
        UiTheme.roundedRect(context, track.x(), track.y(), track.w(), track.h(), 4, UiTheme.argb(190, 48, 52, 62));
        int fillW = Math.round(track.w() * Math.max(0F, Math.min(1F, value)));
        if (fillW > 0) UiTheme.roundedRectExact(context, track.x(), track.y(), fillW, track.h(), 4, UiTheme.accent());
        int knobX = Math.max(track.x() - 1, Math.min(track.x() + track.w() - 9, track.x() + fillW - 5));
        UiTheme.roundedRectExact(context, knobX, track.y() - 4, 10, 16, 5, UiTheme.TEXT);
        field.setX(track.x() + track.w() + 10); field.setY(track.y() - 11); field.setWidth(52);
        input(context, field, "0-100");
    }

    private void renderScrollbar(DrawContext context, Layout l) {
        Rect viewport = viewport(l);
        int max = maxScroll(tab, viewport.h());
        if (max <= 0) return;
        int x = viewport.x() + viewport.w() - 5;
        UiTheme.roundedRect(context, x, viewport.y() + 4, 3, viewport.h() - 8, 2, UiTheme.argb(100, 52, 56, 65));
        int thumbH = Math.max(28, Math.round(viewport.h() * (viewport.h() / (float) (viewport.h() + max))));
        int travel = viewport.h() - 8 - thumbH;
        int thumbY = viewport.y() + 4 + Math.round(travel * (scroll[tab.ordinal()] / (float) max));
        UiTheme.roundedRectExact(context, x - 1, thumbY, 5, thumbH, 3, UiTheme.withAlpha(UiTheme.accent(), 210));
    }

    private void renderStatus(DrawContext context, Layout l) {
        if (status == null) return;
        long age = System.currentTimeMillis() - statusAt;
        long duration = 5000L;
        if (age >= duration) {
            status = null;
            return;
        }
        float in = UiTheme.easeOutCubic(Math.min(1F, age / 260F));
        float out = age < duration - 520L ? 1F
                : Math.max(0F, (duration - age) / 520F);
        float visibility = in * out;
        int w = Math.min(l.w() - 50, UiTheme.textWidth(status, 9.4F, true) + 30);
        int x = l.x() + (l.w() - w) / 2;
        int baseY = l.y() + l.h() - 38;
        int y = baseY + Math.round((1F - visibility) * 34F);
        UiTheme.roundedRect(context, x, y, w, 26, 9,
                UiTheme.withAlpha(UiTheme.PANEL_2, Math.round(245F * visibility)));
        UiTheme.text(context, textRenderer, status, x + 10, y + 9, 9.4F,
                UiTheme.withAlpha(UiTheme.TEXT, Math.round(255F * visibility)), true);
    }

    private void card(DrawContext context, int x, int y, int w, int h, String title, UiIcon icon) {
        UiTheme.roundedRect(context, x, y, w, h, 15, UiTheme.PANEL);
        UiTheme.roundedRect(context, x + 1, y + 1, w - 2, h - 2, 14, UiTheme.argb(245, 18, 20, 26));
        UiTheme.panelTexture(context, x + 2, y + 2, w - 4, h - 4, UiTheme.accent());
        UiTheme.icon(context, icon, x + 16, y + 15, 16, UiTheme.accent());
        UiTheme.text(context, textRenderer, title, x + 42, y + 16, 10.4F, UiTheme.TEXT, true);
    }

    private void input(DrawContext context, TextFieldWidget field, String placeholder) {
        UiTheme.roundedRect(context, field.getX(), field.getY(), field.getWidth(), 28, 9,
                field.isFocused() ? UiTheme.withAlpha(UiTheme.accent(), 105) : UiTheme.BORDER);
        UiTheme.roundedRect(context, field.getX() + 1, field.getY() + 1, field.getWidth() - 2, 26, 8, UiTheme.CARD);
        String value = field.getText();
        if (field.isFocused() && !value.isBlank() && value.equals(field.getSelectedText())) {
            int selectionW = UiTheme.textWidth(value, 9F, false) + 4;
            UiTheme.roundedRectExact(context, field.getX() + 7, field.getY() + 6,
                    selectionW, 16, 4, UiTheme.withAlpha(UiTheme.accent(), 105));
        }
        UiTheme.text(context, textRenderer, value.isBlank() ? placeholder : value,
                field.getX() + 9, field.getY() + 9, 9F, value.isBlank() ? UiTheme.FAINT : UiTheme.TEXT, false);
        if (field.isFocused() && field.getSelectedText().isEmpty()
                && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caret = field.getX() + 9 + UiTheme.textWidth(value, 9F, false);
            context.fill(caret, field.getY() + 6, caret + 1, field.getY() + 21, UiTheme.accent());
        }
    }

    private void toggle(DrawContext context, Rect rect, String label, boolean value,
                        int mouseX, int mouseY, UiIcon icon, String key) {
        UiTheme.roundedRect(context, rect.x(), rect.y(), rect.w(), rect.h(), 10,
                rect.contains(mouseX, mouseY) ? UiTheme.CARD_HOVER : UiTheme.CARD);
        float target = value ? 1F : 0F;
        float current = toggleAnimation.getOrDefault(key, target);
        current += (target - current) * 0.18F;
        if (Math.abs(target - current) < 0.002F) current = target;
        toggleAnimation.put(key, current);
        int iconColor = UiTheme.blend(UiTheme.FAINT, UiTheme.accent(), current);
        UiTheme.icon(context, icon, rect.x() + 10, rect.y() + (rect.h() - 14) / 2, 14, iconColor);
        UiTheme.text(context, textRenderer, label, rect.x() + 34, rect.y() + (rect.h() - 10) / 2, 9F, UiTheme.TEXT, true);
        Rect sw = new Rect(rect.x() + rect.w() - 46, rect.y() + (rect.h() - 18) / 2, 36, 18);
        UiTheme.roundedRect(context, sw.x(), sw.y(), sw.w(), sw.h(), 9,
                UiTheme.blend(UiTheme.argb(255, 55, 60, 70), UiTheme.accent(), current));
        int knobX = Math.round(sw.x() + 3 + 17F * current);
        UiTheme.roundedRectExact(context, knobX, sw.y() + 3, 12, 12, 6, UiTheme.TEXT);
    }

    private void button(DrawContext context, Rect rect, String label, boolean hovered, boolean accent, UiIcon icon) {
        int bg = accent ? (hovered ? UiTheme.blend(UiTheme.accent(), UiTheme.accent2(), 0.45F) : UiTheme.accent())
                : hovered ? UiTheme.CARD_HOVER : UiTheme.CARD;
        UiTheme.roundedRect(context, rect.x(), rect.y(), rect.w(), rect.h(), 9, bg);
        if (icon != null && rect.w() >= 56) UiTheme.icon(context, icon, rect.x() + 8, rect.y() + 8, 12,
                accent ? UiTheme.TEXT : UiTheme.MUTED);
        int textX = icon != null && rect.w() >= 56 ? rect.x() + 25
                : rect.x() + (rect.w() - UiTheme.textWidth(label, 8F, true)) / 2;
        UiTheme.text(context, textRenderer, label, textX, rect.y() + 10, 8F, UiTheme.TEXT, true);
    }

    private void drawWrapped(DrawContext context, String value, int x, int y, int maxWidth,
                             float size, int color, boolean bold) {
        if (value == null || value.isBlank()) return;
        StringBuilder line = new StringBuilder();
        int lineY = y;
        for (String word : value.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && UiTheme.textWidth(candidate, size, bold) > maxWidth) {
                UiTheme.text(context, textRenderer, line.toString(), x, lineY, size, color, bold);
                line.setLength(0); line.append(word); lineY += Math.round(size + 5F);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) UiTheme.text(context, textRenderer, line.toString(), x, lineY, size, color, bold);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        Layout l = layout();
        double mx = click.x(); double my = click.y();
        if (closeRect(l).contains(mx, my)) { close(); return true; }
        for (int i = 0; i < Tab.values().length; i++) {
            if (tabRect(l, i).contains(mx, my)) {
                if (tab != Tab.values()[i]) {
                    previousTab = tab; tab = Tab.values()[i]; tabChangedAt = System.currentTimeMillis();
                }
                focusOnly(null); return true;
            }
        }
        if (System.currentTimeMillis() - tabChangedAt < 330L) return true;

        if (tab == Tab.APPEARANCE) return clickAppearance(click, l, mx, my, doubled);
        if (tab == Tab.MODERATION) return clickModeration(click, l, mx, my, doubled);
        return clickWords(click, l, mx, my, doubled);
    }

    private boolean clickAppearance(Click click, Layout l, double mx, double my, boolean doubled) {
        Rect viewport = viewport(l);
        int x = l.x() + 20; int y = viewport.y() - scroll[Tab.APPEARANCE.ordinal()];
        int w = l.w() - 40; int half = (w - 14) / 2;
        for (int i = 0; i < PRESETS.length / 2; i++) {
            if (presetRect(x, y, i).contains(mx, my)) {
                RaidMineStaffMod.config().setAccent(PRESETS[i * 2], PRESETS[i * 2 + 1]);
                accentField.setText(String.format(Locale.ROOT, "#%06X", PRESETS[i * 2] & 0xFFFFFF));
                showStatus("Цвет интерфейса изменён"); return true;
            }
        }
        Rect apply = new Rect(x + half - 58, y + 176, 40, 28);
        if (apply.contains(mx, my)) { applyHex(); return true; }
        if (backgroundTrack(x, y, half).contains(mx, my)) { dragTarget = DragTarget.BACKGROUND; setSlider(mx, backgroundTrack(x, y, half), dragTarget); return true; }
        if (uiOutlineTrack(x, y, half).contains(mx, my)) { dragTarget = DragTarget.UI_OUTLINE; setSlider(mx, uiOutlineTrack(x, y, half), dragTarget); return true; }
        if (hudOutlineToggle(x, y, half).contains(mx, my)) {
            RaidMineStaffMod.config().hudOutlineEnabled ^= true; save("Обводка панели обновлена"); return true;
        }
        if (RaidMineStaffMod.config().hudOutlineEnabled && hudOutlineTrack(x, y, half).contains(mx, my)) {
            dragTarget = DragTarget.HUD_OUTLINE; setSlider(mx, hudOutlineTrack(x, y, half), dragTarget); return true;
        }
        if (hudRunnersToggle(x, y, half).contains(mx, my)) {
            RaidMineStaffMod.config().hudBorderRunnersEnabled ^= true;
            save("Бегущие линии обновлены");
            return true;
        }
        int rightX = x + half + 14;
        for (int i = 0; i < FONT_VALUES.length; i++) {
            if (fontRect(rightX, y, half, i).contains(mx, my)) {
                RaidMineStaffMod.config().fontFamily = FONT_VALUES[i]; RaidMineStaffMod.config().save();
                SmoothAssets.reloadFontAtlas(); showStatus("Шрифт изменён: " + FONT_LABELS[i]); return true;
            }
        }
        for (TextFieldWidget field : appearanceFields()) {
            if (field.mouseClicked(click, doubled)) { focusOnly(field); return true; }
        }
        focusOnly(null); return true;
    }

    private boolean clickModeration(Click click, Layout l, double mx, double my, boolean doubled) {
        Rect viewport = viewport(l);
        int x = l.x() + 20; int y = viewport.y() - scroll[Tab.MODERATION.ordinal()];
        int w = l.w() - 40; int half = (w - 14) / 2; int rightX = x + half + 14;
        Rect afk = new Rect(x + 18, y + 46, half - 36, 38);
        Rect shot = new Rect(x + 18, y + 94, half - 36, 38);
        Rect hints = new Rect(x + 18, y + 142, half - 36, 38);
        Rect mentions = new Rect(rightX + 18, y + 46, half - 36, 38);
        Rect words = new Rect(rightX + 18, y + 94, half - 36, 38);
        if (afk.contains(mx, my)) { RaidMineStaffMod.config().afkKickEnabled ^= true; save("AFK Kick обновлён"); return true; }
        if (shot.contains(mx, my)) { RaidMineStaffMod.config().autoScreenshot ^= true; save("Автоскриншот обновлён"); return true; }
        if (hints.contains(mx, my)) { RaidMineStaffMod.config().showHintsPanel ^= true; save("Панель подсказок обновлена"); return true; }
        if (mentions.contains(mx, my)) { RaidMineStaffMod.config().mentionNotifications ^= true; save("Упоминания обновлены"); return true; }
        if (words.contains(mx, my)) { RaidMineStaffMod.config().forbiddenWordAlerts ^= true; save("Контроль слов обновлён"); return true; }
        int goalY = y + 210;
        if (new Rect(x + 18, goalY + 21, 34, 30).contains(mx, my)) {
            RaidMineStaffMod.config().dailyOnlineGoalMinutes = Math.max(15, RaidMineStaffMod.config().dailyOnlineGoalMinutes - 15); save("Норма изменена"); return true;
        }
        if (new Rect(x + half - 52, goalY + 21, 34, 30).contains(mx, my)) {
            RaidMineStaffMod.config().dailyOnlineGoalMinutes = Math.min(720, RaidMineStaffMod.config().dailyOnlineGoalMinutes + 15); save("Норма изменена"); return true;
        }
        if (reasonField.mouseClicked(click, doubled)) { focusOnly(reasonField); return true; }
        focusOnly(null); return true;
    }

    private boolean clickWords(Click click, Layout l, double mx, double my, boolean doubled) {
        Rect viewport = viewport(l);
        int x = l.x() + 20; int y = viewport.y() - scroll[Tab.WORDS.ordinal()]; int w = l.w() - 40;
        if (wordField.mouseClicked(click, doubled)) { focusOnly(wordField); return true; }
        Rect add = new Rect(wordField.getX() + wordField.getWidth() + 10, wordField.getY(), 84, 28);
        Rect open = new Rect(x + w - 188, wordField.getY(), 82, 28);
        Rect reload = new Rect(x + w - 96, wordField.getY(), 78, 28);
        if (add.contains(mx, my)) { addWord(); return true; }
        if (open.contains(mx, my)) { openWordsFolder(); return true; }
        if (reload.contains(mx, my)) { reloadWords(); return true; }
        int listY = y + 122;
        List<String> words = RaidMineStaffMod.config().forbiddenWords;
        for (int i = 0; i < words.size(); i++) {
            Rect row = new Rect(x + 18, listY + i * 34, w - 36, 28);
            if (removeRect(row).contains(mx, my)) {
                words.remove(i); RaidMineStaffMod.config().save(); showStatus("Слово удалено"); return true;
            }
        }
        focusOnly(null); return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragTarget == DragTarget.NONE || tab != Tab.APPEARANCE) return super.mouseDragged(click, offsetX, offsetY);
        Layout l = layout(); Rect viewport = viewport(l);
        int x = l.x() + 20; int y = viewport.y() - scroll[Tab.APPEARANCE.ordinal()]; int half = ((l.w() - 40) - 14) / 2;
        Rect track = switch (dragTarget) {
            case BACKGROUND -> backgroundTrack(x, y, half);
            case UI_OUTLINE -> uiOutlineTrack(x, y, half);
            case HUD_OUTLINE -> hudOutlineTrack(x, y, half);
            case NONE -> backgroundTrack(x, y, half);
        };
        setSlider(click.x(), track, dragTarget); return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragTarget == DragTarget.NONE) return super.mouseReleased(click);
        dragTarget = DragTarget.NONE; syncPercentFields(); RaidMineStaffMod.config().save(); showStatus("Прозрачность сохранена"); return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Rect viewport = viewport(layout());
        if (!viewport.contains(mouseX, mouseY)) return false;
        int max = maxScroll(tab, viewport.h());
        int next = scroll[tab.ordinal()] + (verticalAmount < 0 ? 38 : -38);
        scroll[tab.ordinal()] = Math.max(0, Math.min(max, next));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (input.isSelectAll()) {
            for (TextFieldWidget field : allFields()) {
                if (field.isFocused()) {
                    field.setCursorToEnd(false);
                    field.setSelectionEnd(0);
                    return true;
                }
            }
        }
        if (input.isEnter()) {
            if (wordField.isFocused()) { addWord(); return true; }
            if (accentField.isFocused()) { applyHex(); return true; }
            if (reasonField.isFocused()) { saveReason(); return true; }
            if (backgroundPercentField.isFocused()) { applyPercent(backgroundPercentField, DragTarget.BACKGROUND); return true; }
            if (uiOutlinePercentField.isFocused()) { applyPercent(uiOutlinePercentField, DragTarget.UI_OUTLINE); return true; }
            if (hudOutlinePercentField.isFocused()) { applyPercent(hudOutlinePercentField, DragTarget.HUD_OUTLINE); return true; }
        }
        for (TextFieldWidget field : allFields()) if (field.isFocused() && field.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        for (TextFieldWidget field : allFields()) if (field.isFocused() && field.charTyped(input)) return true;
        return super.charTyped(input);
    }

    private void setSlider(double mouseX, Rect track, DragTarget target) {
        float value = Math.max(0F, Math.min(1F, (float) ((mouseX - track.x()) / Math.max(1.0, track.w()))));
        switch (target) {
            case BACKGROUND -> {
                RaidMineStaffMod.config().uiBackgroundOpacity = value;
                backgroundPercentField.setText(Integer.toString(Math.round(value * 100F)));
            }
            case UI_OUTLINE -> {
                RaidMineStaffMod.config().uiOutlineOpacity = value;
                uiOutlinePercentField.setText(Integer.toString(Math.round(value * 100F)));
            }
            case HUD_OUTLINE -> {
                RaidMineStaffMod.config().hudOutlineOpacity = value;
                hudOutlinePercentField.setText(Integer.toString(Math.round(value * 100F)));
            }
            case NONE -> { }
        }
    }

    private void applyPercent(TextFieldWidget field, DragTarget target) {
        try {
            int percent = Math.max(0, Math.min(100, Integer.parseInt(field.getText().trim())));
            field.setText(Integer.toString(percent));
            float value = percent / 100F;
            switch (target) {
                case BACKGROUND -> RaidMineStaffMod.config().uiBackgroundOpacity = value;
                case UI_OUTLINE -> RaidMineStaffMod.config().uiOutlineOpacity = value;
                case HUD_OUTLINE -> RaidMineStaffMod.config().hudOutlineOpacity = value;
                case NONE -> { }
            }
            RaidMineStaffMod.config().save(); showStatus("Значение применено");
        } catch (NumberFormatException exception) {
            showStatus("Введите число от 0 до 100");
        }
    }

    private void syncPercentFields() {
        backgroundPercentField.setText(Integer.toString(Math.round(RaidMineStaffMod.config().uiBackgroundOpacity * 100F)));
        uiOutlinePercentField.setText(Integer.toString(Math.round(RaidMineStaffMod.config().uiOutlineOpacity * 100F)));
        hudOutlinePercentField.setText(Integer.toString(Math.round(RaidMineStaffMod.config().hudOutlineOpacity * 100F)));
    }

    private void applyHex() {
        String raw = accentField.getText().trim().replace("#", "");
        try {
            int primary = Integer.parseInt(raw, 16) & 0xFFFFFF;
            int secondary = darken(primary, 0.72F);
            RaidMineStaffMod.config().setAccent(0xFF000000 | primary, 0xFF000000 | secondary);
            accentField.setText(String.format(Locale.ROOT, "#%06X", primary)); showStatus("Цвет интерфейса изменён");
        } catch (NumberFormatException exception) { showStatus("HEX должен быть вида #FF8A00"); }
    }

    private void addWord() {
        String value = wordField.getText().trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) { showStatus("Введите слово"); return; }
        if (!RaidMineStaffMod.config().forbiddenWords.contains(value)) RaidMineStaffMod.config().forbiddenWords.add(value);
        RaidMineStaffMod.config().save(); wordField.setText(""); showStatus("Слово добавлено");
    }
    private void reloadWords() {
        RaidMineStaffMod.config().forbiddenWords = ForbiddenWordsStore.loadOrCreate(RaidMineStaffMod.config().forbiddenWords);
        RaidMineStaffMod.config().save(); scroll[Tab.WORDS.ordinal()] = 0; showStatus("Список перечитан из файла");
    }
    private void openWordsFolder() { FolderOpener.Result result = FolderOpener.open(ForbiddenWordsStore.directory()); showStatus(result.message()); }
    private void saveReason() {
        String template = reasonField.getText().trim();
        RaidMineStaffMod.config().punishmentReasonTemplate = template.isEmpty() ? "{rule}" : template;
        RaidMineStaffMod.config().save(); showStatus("Формат причины сохранён");
    }
    private void save(String message) { RaidMineStaffMod.config().save(); showStatus(message); }
    private void showStatus(String message) { status = message; statusAt = System.currentTimeMillis(); }

    private void focusOnly(TextFieldWidget focused) {
        for (TextFieldWidget field : allFields()) field.setFocused(field == focused);
    }
    private TextFieldWidget[] appearanceFields() {
        return new TextFieldWidget[]{accentField, backgroundPercentField, uiOutlinePercentField, hudOutlinePercentField};
    }
    private TextFieldWidget[] allFields() {
        return new TextFieldWidget[]{wordField, accentField, reasonField, backgroundPercentField, uiOutlinePercentField, hudOutlinePercentField};
    }

    private String currentFontLabel() {
        if ("MINECRAFT".equalsIgnoreCase(RaidMineStaffMod.config().fontFamily)) return "Minecraft";
        return SmoothAssets.fontName();
    }
    private int maxScroll(Tab candidate, int viewportHeight) {
        int content = switch (candidate) {
            case APPEARANCE -> 610;
            case MODERATION -> 450;
            case WORDS -> Math.max(430, 142 + RaidMineStaffMod.config().forbiddenWords.size() * 34);
        };
        return Math.max(0, content - viewportHeight);
    }
    private static int darken(int rgb, float factor) {
        int r = Math.round(((rgb >> 16) & 255) * factor);
        int g = Math.round(((rgb >> 8) & 255) * factor);
        int b = Math.round((rgb & 255) * factor);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void close() {
        saveReason(); syncPercentFields(); RaidMineStaffMod.config().save();
        MinecraftClient.getInstance().setScreen(parent);
    }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) { }
    @Override public boolean shouldPause() { return false; }

    private Layout layout() {
        int w = Math.min(940, width - 24); int h = Math.min(640, height - 20);
        return new Layout((width - w) / 2, (height - h) / 2, w, h);
    }
    private Rect viewport(Layout l) { return new Rect(l.x() + 2, l.y() + 112, l.w() - 4, l.h() - 130); }
    private Rect closeRect(Layout l) { return new Rect(l.x() + l.w() - 48, l.y() + 16, 30, 30); }
    private Rect tabRect(Layout l, int index) {
        int x = l.x() + 18; int y = l.y() + 70; int w = (l.w() - 44) / 3;
        return new Rect(x + index * (w + 4), y, w, 34);
    }
    private Rect presetRect(int x, int y, int index) {
        int column = index % 5; int row = index / 5;
        return new Rect(x + 18 + column * 43, y + 66 + row * 40, 35, 30);
    }
    private Rect fontRect(int x, int y, int width, int index) {
        int column = index % 2; int row = index / 2; int w = (width - 46) / 2;
        return new Rect(x + 18 + column * (w + 10), y + 66 + row * 40, w, 32);
    }
    private Rect backgroundTrack(int x, int y, int width) { return new Rect(x + 18, y + 248, Math.max(95, width - 100), 8); }
    private Rect uiOutlineTrack(int x, int y, int width) { return new Rect(x + 18, y + 306, Math.max(95, width - 100), 8); }
    private Rect hudOutlineToggle(int x, int y, int width) { return new Rect(x + 18, y + 338, width - 36, 38); }
    private Rect hudOutlineTrack(int x, int y, int width) { return new Rect(x + 18, y + 414, Math.max(95, width - 100), 8); }
    private Rect hudRunnersToggle(int x, int y, int width) { return new Rect(x + 18, y + 456, width - 36, 38); }
    private Rect removeRect(Rect row) { return new Rect(row.x() + row.w() - 30, row.y() + 1, 26, 26); }

    private enum DragTarget { NONE, BACKGROUND, UI_OUTLINE, HUD_OUTLINE }
    private enum Tab {
        APPEARANCE("Персонализация", UiIcon.PALETTE),
        MODERATION("Модерация", UiIcon.SHIELD),
        WORDS("Запрещённые слова", UiIcon.WARN);
        private final String label; private final UiIcon icon;
        Tab(String label, UiIcon icon) { this.label = label; this.icon = icon; }
    }
    private record Layout(int x, int y, int w, int h) { }
    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
}

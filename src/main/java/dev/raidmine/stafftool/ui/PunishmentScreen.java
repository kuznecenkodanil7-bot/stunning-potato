package dev.raidmine.stafftool.ui;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.rules.PunishmentOption;
import dev.raidmine.stafftool.rules.RuleCatalog;
import dev.raidmine.stafftool.rules.RuleCategory;
import dev.raidmine.stafftool.rules.RuleEntry;
import dev.raidmine.stafftool.util.CommandExecutor;
import dev.raidmine.stafftool.util.NicknameResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class PunishmentScreen extends Screen {
    private static final int PAGE_SIZE = 8;

    private final Screen parent;
    private final String initialTarget;
    private final long openedAt = System.currentTimeMillis();

    private TextFieldWidget targetField;
    private RuleCategory category = RuleCategory.CHAT;
    private int page;
    private RuleEntry selectedRule;
    private int selectedOptionIndex;
    private boolean confirmationOpen;
    private long confirmationOpenedAt;
    private String toastMessage;
    private boolean toastSuccess;
    private long toastAt;

    public PunishmentScreen(Screen parent, String initialTarget) {
        super(Text.literal("RM Tools"));
        this.parent = parent;
        this.initialTarget = initialTarget == null ? "" : initialTarget;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        targetField = new TextFieldWidget(textRenderer,
                layout.contentX(), layout.panelY() + 54,
                Math.min(250, layout.contentWidth()), 22,
                Text.literal("Ник игрока"));
        targetField.setMaxLength(16);
        targetField.setText(initialTarget);
        targetField.setDrawsBackground(false);
        targetField.setEditableColor(UiTheme.TEXT);
        targetField.setUneditableColor(UiTheme.FAINT);
        targetField.setFocused(initialTarget.isBlank());

        List<RuleEntry> visible = RuleCatalog.byCategory(category);
        if (!visible.isEmpty()) {
            selectRule(visible.getFirst());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height,
                UiTheme.surface(UiTheme.argb(220, 7, 10, 19)),
                UiTheme.surface(UiTheme.argb(236, 12, 15, 28)));

        float openProgress = UiTheme.easeOutCubic((System.currentTimeMillis() - openedAt) / 280F);
        Layout base = layout();
        int animatedY = base.panelY() + Math.round((1F - openProgress) * 18F);
        Layout layout = base.withPanelY(animatedY);

        UiTheme.shadow(context, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), 18);
        int outlineAlpha = Math.round(255F * RaidMineStaffMod.config().uiOutlineOpacity);
        if (outlineAlpha > 0) {
            UiTheme.roundedBorder(context, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(),
                    18, 2, UiTheme.withAlpha(UiTheme.accent(), outlineAlpha), UiTheme.BG);
        } else {
            UiTheme.roundedRect(context, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), 18, UiTheme.BG);
        }
        UiTheme.panelTexture(context, layout.panelX() + 2, layout.panelY() + 2,
                layout.panelWidth() - 4, layout.panelHeight() - 4, UiTheme.accent());

        renderSidebar(context, layout, mouseX, mouseY);
        renderHeader(context, layout);
        renderRuleGrid(context, layout, mouseX, mouseY);
        renderDetails(context, layout, mouseX, mouseY);
        renderSettingsButton(context, layout, mouseX, mouseY);

        targetField.setX(layout.contentX());
        targetField.setY(layout.panelY() + 54);
        targetField.setWidth(Math.min(250, layout.contentWidth()));
        renderTargetField(context);

        if (confirmationOpen) {
            renderConfirmation(context, layout, mouseX, mouseY);
        }
        renderToast(context, layout);
    }

    private void renderSidebar(DrawContext context, Layout l, int mouseX, int mouseY) {
        int x = l.panelX();
        int y = l.panelY();
        UiTheme.roundedRect(context, x, y, l.sidebarWidth(), l.panelHeight(), 18, UiTheme.PANEL);
        UiTheme.panelTexture(context, x + 2, y + 2, l.sidebarWidth() - 4, l.panelHeight() - 4, UiTheme.accent());
        context.fill(x + l.sidebarWidth() - 1, y + 18, x + l.sidebarWidth(), y + l.panelHeight() - 18, UiTheme.BORDER);

        UiTheme.logo(context, x + 17, y + 18, 40, 34, 255);
        UiTheme.brandText(context, "RaidMine", x + 64, y + 22, 10.4F, UiTheme.TEXT);
        UiTheme.brandText(context, "Tools", x + 64, y + 38, 9.2F, UiTheme.MUTED);

        int itemY = y + 92;
        for (RuleCategory candidate : RuleCategory.values()) {
            Rect rect = new Rect(x + 13, itemY, l.sidebarWidth() - 26, 44);
            boolean active = candidate == category;
            boolean hovered = rect.contains(mouseX, mouseY);
            if (active || hovered) {
                UiTheme.roundedRect(context, rect.x(), rect.y(), rect.w(), rect.h(), 10,
                        active ? UiTheme.argb(62, 97, 111, 255) : UiTheme.argb(70, 45, 53, 74));
            }
            if (active) {
                UiTheme.roundedRect(context, rect.x(), rect.y() + 10, 3, 24, 2, UiTheme.accent());
            }
            UiTheme.roundedRect(context, rect.x() + 12, rect.y() + 10, 26, 24, 7,
                    active ? UiTheme.withAlpha(UiTheme.accent(), 90) : UiTheme.argb(110, 49, 58, 79));
            UiTheme.icon(context, categoryIcon(candidate), rect.x() + 18, rect.y() + 15, 14,
                    active ? UiTheme.TEXT : UiTheme.MUTED);
            UiTheme.textMedium(context, textRenderer,
                    UiTheme.ellipsize(textRenderer, candidate.title(), rect.w() - 57),
                    rect.x() + 47, rect.y() + 16, active ? UiTheme.TEXT : UiTheme.MUTED, active);
            itemY += 51;
        }

        int tipY = y + l.panelHeight() - 82;
        UiTheme.roundedRect(context, x + 14, tipY, l.sidebarWidth() - 28, 56, 10, UiTheme.argb(120, 31, 38, 56));
        UiTheme.text(context, textRenderer, "Подсказка", x + 26, tipY + 12, UiTheme.MUTED);
        UiTheme.text(context, textRenderer, "ЛКМ по нику в чате", x + 26, tipY + 27, UiTheme.TEXT);
        UiTheme.text(context, textRenderer, "или /rmp <ник>", x + 26, tipY + 40, UiTheme.FAINT);
    }

    private void renderHeader(DrawContext context, Layout l) {
        int y = l.panelY();
        UiTheme.textTitle(context, textRenderer, "Выдача наказания", l.contentX(), y + 18, UiTheme.TEXT);
        UiTheme.text(context, textRenderer, "Выберите пункт правил и проверьте срок перед отправкой команды",
                l.contentX(), y + 37, UiTheme.MUTED);

        int fieldW = Math.min(250, l.contentWidth());
        UiTheme.roundedRect(context, l.contentX(), y + 49, fieldW, 31, 9, UiTheme.CARD);
        UiTheme.roundedRect(context, l.contentX() + 10, y + 60, 4, 9, 2, UiTheme.accent());
        UiTheme.text(context, textRenderer, "Игрок", l.contentX() + 19, y + 41, UiTheme.FAINT);
    }

    private void renderRuleGrid(DrawContext context, Layout l, int mouseX, int mouseY) {
        List<RuleEntry> rules = RuleCatalog.byCategory(category);
        int maxPage = Math.max(0, (rules.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage));
        int from = page * PAGE_SIZE;
        int to = Math.min(rules.size(), from + PAGE_SIZE);

        int gridY = l.panelY() + 103;
        int gap = 10;
        int cardW = Math.max(170, (l.contentWidth() - gap) / 2);
        int cardH = Math.max(70, (l.panelHeight() - 174 - gap * 3) / 4);

        for (int i = from; i < to; i++) {
            int local = i - from;
            int col = local % 2;
            int row = local / 2;
            int x = l.contentX() + col * (cardW + gap);
            int y = gridY + row * (cardH + gap);
            Rect rect = new Rect(x, y, cardW, cardH);
            RuleEntry rule = rules.get(i);
            boolean selected = rule.equals(selectedRule);
            boolean hovered = rect.contains(mouseX, mouseY) && !confirmationOpen;

            UiTheme.roundedRect(context, x, y, cardW, cardH, 11,
                    selected ? UiTheme.argb(72, 97, 111, 255) : hovered ? UiTheme.CARD_HOVER : UiTheme.CARD);
            if (selected) {
                UiTheme.roundedRect(context, x, y, 4, cardH, 3, UiTheme.accent());
            }
            UiTheme.roundedRect(context, x + 12, y + 12, 36, 20, 7,
                    selected ? UiTheme.withAlpha(UiTheme.accent(), 120) : UiTheme.argb(140, 55, 64, 86));
            UiTheme.text(context, textRenderer, rule.code(), x + 20, y + 18, 9.2F,
                    selected ? UiTheme.TEXT : UiTheme.MUTED, true);
            UiTheme.text(context, textRenderer,
                    UiTheme.ellipsize(textRenderer, rule.title(), cardW - 66),
                    x + 57, y + 17, 9.4F, UiTheme.TEXT, true);
            UiTheme.text(context, textRenderer,
                    UiTheme.ellipsize(textRenderer, rule.description(), cardW - 24),
                    x + 12, y + 42, 8.5F, UiTheme.MUTED, false);
            UiTheme.text(context, textRenderer,
                    rule.options().size() + " вариант(а)", x + 12, y + cardH - 16, 8.2F, UiTheme.FAINT, false);
        }

        int pagerY = l.panelY() + l.panelHeight() - 42;
        if (maxPage > 0) {
            Rect prev = prevPageRect(l);
            Rect next = nextPageRect(l);
            iconButton(context, prev, UiIcon.CHEVRON_LEFT, prev.contains(mouseX, mouseY), page > 0);
            iconButton(context, next, UiIcon.CHEVRON_RIGHT, next.contains(mouseX, mouseY), page < maxPage);
            String info = (page + 1) + " / " + (maxPage + 1);
            int infoX = l.contentX() + (l.contentWidth() - UiTheme.textWidth(info)) / 2;
            UiTheme.text(context, textRenderer, info, infoX, pagerY + 10, UiTheme.MUTED);
        }
    }

    private void renderDetails(DrawContext context, Layout l, int mouseX, int mouseY) {
        int x = l.detailX();
        int y = l.panelY() + 16;
        int w = l.detailWidth();
        int h = l.panelHeight() - 32;
        UiTheme.roundedRect(context, x, y, w, h, 14, UiTheme.PANEL_2);
        UiTheme.panelTexture(context, x + 2, y + 2, w - 4, h - 4, UiTheme.accent());

        UiTheme.text(context, textRenderer, "ДЕТАЛИ", x + 18, y + 18, UiTheme.FAINT);
        if (selectedRule == null) {
            UiTheme.text(context, textRenderer, "Выберите правило", x + 18, y + 45, UiTheme.MUTED);
            return;
        }

        UiTheme.roundedRect(context, x + 18, y + 41, 46, 24, 8, UiTheme.withAlpha(UiTheme.accent(), 110));
        UiTheme.text(context, textRenderer, selectedRule.code(), x + 29, y + 49, UiTheme.TEXT);
        UiTheme.text(context, textRenderer,
                UiTheme.ellipsize(textRenderer, selectedRule.title(), w - 84),
                x + 74, y + 49, UiTheme.TEXT);

        int descriptionY = y + 82;
        drawWrapped(context, selectedRule.description(), x + 18, descriptionY, w - 36, UiTheme.MUTED, 4);

        int optionY = y + 158;
        UiTheme.text(context, textRenderer, "Вариант наказания", x + 18, optionY, UiTheme.FAINT);
        Rect optionBox = optionBoxRect(l);
        UiTheme.roundedRect(context, optionBox.x(), optionBox.y(), optionBox.w(), optionBox.h(), 11, UiTheme.CARD);

        PunishmentOption option = selectedOption();
        if (option != null) {
            int accent = colorFor(option);
            UiTheme.roundedRect(context, optionBox.x() + 12, optionBox.y() + 12, 34, 34, 10, UiTheme.withAlpha(accent, 38));
            UiTheme.icon(context, optionIcon(option), optionBox.x() + 20, optionBox.y() + 20, 18, accent);
            UiTheme.textMedium(context, textRenderer, option.type().displayName(), optionBox.x() + 56, optionBox.y() + 11, accent, true);
            UiTheme.text(context, textRenderer,
                    UiTheme.ellipsize(textRenderer, option.label(), optionBox.w() - 118),
                    optionBox.x() + 56, optionBox.y() + 30, UiTheme.TEXT);

            Rect left = previousOptionRect(l);
            Rect right = nextOptionRect(l);
            iconButton(context, left, UiIcon.CHEVRON_LEFT, left.contains(mouseX, mouseY), selectedRule.options().size() > 1);
            iconButton(context, right, UiIcon.CHEVRON_RIGHT, right.contains(mouseX, mouseY), selectedRule.options().size() > 1);
        }

        int evidenceY = optionBox.y() + optionBox.h() + 22;
        UiTheme.roundedRect(context, x + 18, evidenceY, w - 36, 62, 10, UiTheme.argb(110, 66, 52, 28));
        UiTheme.roundedRect(context, x + 27, evidenceY + 13, 28, 28, 9, UiTheme.withAlpha(UiTheme.WARNING, 38));
        UiTheme.icon(context, UiIcon.WARN, x + 34, evidenceY + 20, 14, UiTheme.WARNING);
        UiTheme.textBold(context, textRenderer, "Перед выдачей", x + 65, evidenceY + 12, UiTheme.WARNING);
        UiTheme.text(context, textRenderer, "Проверьте доказательства и точный", x + 65, evidenceY + 29, UiTheme.MUTED);
        UiTheme.text(context, textRenderer, "срок согласно ситуации.", x + 65, evidenceY + 42, UiTheme.MUTED);

        Rect issue = issueButtonRect(l);
        boolean validTarget = NicknameResolver.isValid(targetField == null ? "" : targetField.getText());
        button(context, issue, validTarget ? "ВЫДАТЬ НАКАЗАНИЕ" : "УКАЖИТЕ НИК",
                issue.contains(mouseX, mouseY), validTarget && option != null, true);

        UiTheme.text(context, textRenderer,
                "Вариант " + (selectedOptionIndex + 1) + " из " + selectedRule.options().size(),
                x + 18, issue.y() - 17, UiTheme.FAINT);
    }

    private void renderConfirmation(DrawContext context, Layout l, int mouseX, int mouseY) {
        context.fill(0, 0, width, height, UiTheme.argb(145, 0, 0, 0));
        float t = UiTheme.easeOutBack((System.currentTimeMillis() - confirmationOpenedAt) / 230F);
        int modalW = Math.min(430, width - 40);
        int modalH = 224;
        int x = (width - modalW) / 2;
        int y = (height - modalH) / 2 + Math.round((1F - t) * 16F);

        UiTheme.shadow(context, x, y, modalW, modalH, 16);
        UiTheme.roundedRect(context, x, y, modalW, modalH, 16, UiTheme.PANEL_2);
        UiTheme.roundedRect(context, x + 20, y + 19, 36, 36, 12, UiTheme.withAlpha(UiTheme.WARNING, 85));
        UiTheme.icon(context, UiIcon.WARN, x + 29, y + 27, 18, UiTheme.WARNING);
        UiTheme.text(context, textRenderer, "Подтвердите наказание", x + 69, y + 23, UiTheme.TEXT);
        UiTheme.text(context, textRenderer, "Команда будет немедленно отправлена на сервер", x + 69, y + 40, UiTheme.MUTED);

        PunishmentOption option = selectedOption();
        String player = targetField == null ? "" : targetField.getText();
        UiTheme.roundedRect(context, x + 20, y + 75, modalW - 40, 80, 11, UiTheme.CARD);
        UiTheme.text(context, textRenderer, "Игрок", x + 34, y + 89, UiTheme.FAINT);
        UiTheme.text(context, textRenderer, player, x + 100, y + 89, UiTheme.TEXT);
        UiTheme.text(context, textRenderer, "Правило", x + 34, y + 109, UiTheme.FAINT);
        String ruleSummary = selectedRule == null ? "—" : selectedRule.code() + " — " + selectedRule.title();
        UiTheme.text(context, textRenderer,
                UiTheme.ellipsize(textRenderer, ruleSummary, modalW - 134),
                x + 100, y + 109, UiTheme.TEXT);
        UiTheme.text(context, textRenderer, "Санкция", x + 34, y + 129, UiTheme.FAINT);
        UiTheme.text(context, textRenderer,
                UiTheme.ellipsize(textRenderer, option == null ? "—" : option.label(), modalW - 134),
                x + 100, y + 129, UiTheme.TEXT);

        Rect cancel = cancelRect(x, y, modalW, modalH);
        Rect confirm = confirmRect(x, y, modalW, modalH);
        button(context, cancel, "ОТМЕНА", cancel.contains(mouseX, mouseY), true, false);
        button(context, confirm, "ПОДТВЕРДИТЬ", confirm.contains(mouseX, mouseY), true, true);
    }

    private void renderToast(DrawContext context, Layout l) {
        if (toastMessage == null) {
            return;
        }
        long age = System.currentTimeMillis() - toastAt;
        if (age > 3200L) {
            toastMessage = null;
            return;
        }
        float in = UiTheme.easeOutCubic(Math.min(1F, age / 220F));
        float out = age < 2700L ? 1F : 1F - (age - 2700L) / 500F;
        int alpha = Math.round(235F * Math.max(0F, out));
        int w = Math.min(440, l.panelWidth() - 40);
        int x = l.panelX() + (l.panelWidth() - w) / 2;
        int y = l.panelY() + 16 + Math.round((1F - in) * -24F);
        int accent = toastSuccess ? UiTheme.SUCCESS : UiTheme.DANGER;
        UiTheme.roundedRect(context, x, y, w, 38, 11, UiTheme.withAlpha(UiTheme.PANEL_2, alpha));
        UiTheme.roundedRect(context, x + 11, y + 10, 4, 18, 2, UiTheme.withAlpha(accent, alpha));
        UiTheme.text(context, textRenderer,
                UiTheme.ellipsize(textRenderer, toastMessage, w - 38),
                x + 25, y + 15, UiTheme.withAlpha(UiTheme.TEXT, alpha));
    }


    private void renderSettingsButton(DrawContext context, Layout l, int mouseX, int mouseY) {
        Rect rect = settingsRect(l);
        boolean hovered = rect.contains(mouseX, mouseY) && !confirmationOpen;
        if (hovered) {
            UiTheme.glow(context, rect.x(), rect.y(), rect.w(), rect.h(), 10, UiTheme.accent());
        }
        UiTheme.roundedRect(context, rect.x(), rect.y(), rect.w(), rect.h(), 10,
                hovered ? UiTheme.CARD_HOVER : UiTheme.CARD);
        UiTheme.icon(context, UiIcon.SETTINGS, rect.x() + 8, rect.y() + 8, 16,
                hovered ? UiTheme.accent() : UiTheme.MUTED);
    }

    private void renderTargetField(DrawContext context) {
        if (targetField == null) {
            return;
        }
        String value = targetField.getText();
        int x = targetField.getX() + 17;
        int y = targetField.getY() + 6;
        if (value.isBlank()) {
            UiTheme.text(context, textRenderer, "Введите ник игрока", x, y, UiTheme.FAINT);
        } else {
            UiTheme.textMedium(context, textRenderer, value, x, y - 1, UiTheme.TEXT, true);
        }
        if (targetField.isFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretX = x + UiTheme.textWidth(value, UiTheme.FONT_MEDIUM, true) + 1;
            context.fill(caretX, y - 1, caretX + 1, y + 12, UiTheme.accent());
        }
        UiTheme.icon(context, UiIcon.USER, targetField.getX() + 4, targetField.getY() + 6, 12, UiTheme.MUTED);
    }

    private UiIcon categoryIcon(RuleCategory candidate) {
        return switch (candidate) {
            case CHAT -> UiIcon.CHAT;
            case GAMEPLAY -> UiIcon.GAMEPLAY;
            case DONATER -> UiIcon.DONATER;
            case QUICK -> UiIcon.QUICK;
        };
    }

    private UiIcon optionIcon(PunishmentOption option) {
        return switch (option.type()) {
            case WARN -> UiIcon.WARN;
            case MUTE -> UiIcon.MUTE;
            case BAN, PERMANENT_BAN -> UiIcon.BAN;
            case KICK -> UiIcon.KICK;
        };
    }

    private void iconButton(DrawContext context, Rect rect, UiIcon icon, boolean hovered, boolean enabled) {
        int bg = !enabled ? UiTheme.argb(80, 45, 52, 69) : hovered ? UiTheme.CARD_HOVER : UiTheme.CARD;
        int color = enabled ? UiTheme.TEXT : UiTheme.FAINT;
        UiTheme.roundedRect(context, rect.x(), rect.y(), rect.w(), rect.h(), 9, bg);
        int size = Math.min(16, Math.min(rect.w() - 8, rect.h() - 8));
        UiTheme.icon(context, icon, rect.x() + (rect.w() - size) / 2, rect.y() + (rect.h() - size) / 2, size, color);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(click, doubled);
        }

        if (confirmationOpen) {
            Layout l = layout();
            int modalW = Math.min(430, width - 40);
            int modalH = 224;
            int x = (width - modalW) / 2;
            int y = (height - modalH) / 2;
            if (cancelRect(x, y, modalW, modalH).contains(mouseX, mouseY)) {
                confirmationOpen = false;
                return true;
            }
            if (confirmRect(x, y, modalW, modalH).contains(mouseX, mouseY)) {
                executeSelected();
                confirmationOpen = false;
                return true;
            }
            return true;
        }

        if (targetField != null && targetField.mouseClicked(click, doubled)) {
            return true;
        }

        Layout l = layout();
        if (settingsRect(l).contains(mouseX, mouseY)) {
            MinecraftClient.getInstance().setScreen(new SettingsScreen(this));
            return true;
        }
        int itemY = l.panelY() + 92;
        for (RuleCategory candidate : RuleCategory.values()) {
            Rect rect = new Rect(l.panelX() + 13, itemY, l.sidebarWidth() - 26, 44);
            if (rect.contains(mouseX, mouseY)) {
                category = candidate;
                page = 0;
                List<RuleEntry> visible = RuleCatalog.byCategory(candidate);
                selectRule(visible.isEmpty() ? null : visible.getFirst());
                return true;
            }
            itemY += 51;
        }

        List<RuleEntry> rules = RuleCatalog.byCategory(category);
        int from = page * PAGE_SIZE;
        int to = Math.min(rules.size(), from + PAGE_SIZE);
        int gap = 10;
        int cardW = Math.max(170, (l.contentWidth() - gap) / 2);
        int cardH = Math.max(70, (l.panelHeight() - 174 - gap * 3) / 4);
        int gridY = l.panelY() + 103;
        for (int i = from; i < to; i++) {
            int local = i - from;
            Rect rect = new Rect(l.contentX() + (local % 2) * (cardW + gap),
                    gridY + (local / 2) * (cardH + gap), cardW, cardH);
            if (rect.contains(mouseX, mouseY)) {
                selectRule(rules.get(i));
                return true;
            }
        }

        int maxPage = Math.max(0, (rules.size() - 1) / PAGE_SIZE);
        if (prevPageRect(l).contains(mouseX, mouseY) && page > 0) {
            page--;
            selectFirstOnPage(rules);
            return true;
        }
        if (nextPageRect(l).contains(mouseX, mouseY) && page < maxPage) {
            page++;
            selectFirstOnPage(rules);
            return true;
        }

        if (selectedRule != null && selectedRule.options().size() > 1) {
            if (previousOptionRect(l).contains(mouseX, mouseY)) {
                selectedOptionIndex = (selectedOptionIndex - 1 + selectedRule.options().size()) % selectedRule.options().size();
                return true;
            }
            if (nextOptionRect(l).contains(mouseX, mouseY)) {
                selectedOptionIndex = (selectedOptionIndex + 1) % selectedRule.options().size();
                return true;
            }
        }

        if (issueButtonRect(l).contains(mouseX, mouseY)) {
            if (!NicknameResolver.isValid(targetField.getText())) {
                showToast(false, "Укажите корректный ник от 2 до 16 символов");
                return true;
            }
            if (selectedOption() == null) {
                showToast(false, "Сначала выберите вариант наказания");
                return true;
            }
            if (RaidMineStaffMod.config().requireConfirmation) {
                confirmationOpen = true;
                confirmationOpenedAt = System.currentTimeMillis();
            } else {
                executeSelected();
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (confirmationOpen) {
                confirmationOpen = false;
            } else {
                close();
            }
            return true;
        }
        return (targetField != null && targetField.keyPressed(input))
                || super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return (targetField != null && targetField.charTyped(input))
                || super.charTyped(input);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void executeSelected() {
        PunishmentOption option = selectedOption();
        if (option == null || targetField == null) {
            showToast(false, "Не выбрано наказание");
            return;
        }
        String player = targetField.getText();
        String ruleCode = selectedRule == null ? "RULE" : selectedRule.code();
        CommandExecutor.Result result = CommandExecutor.execute(player, ruleCode, option);
        showToast(result.success(), result.message());
        if (result.success()) {
            dev.raidmine.stafftool.chat.UiNotificationCenter.info("Наказание выдано", player + " • " + ruleCode);
            MinecraftClient client = MinecraftClient.getInstance();
            Screen evidenceScreen = dev.raidmine.stafftool.util.ScreenshotService.evidenceScreen(parent);
            dev.raidmine.stafftool.util.ScreenshotService.requestAfterChatRender(player, ruleCode, evidenceScreen);
            client.setScreen(evidenceScreen);
        }
    }

    private void showToast(boolean success, String message) {
        toastSuccess = success;
        toastMessage = message;
        toastAt = System.currentTimeMillis();
    }

    private void selectRule(RuleEntry rule) {
        selectedRule = rule;
        selectedOptionIndex = 0;
    }

    private void selectFirstOnPage(List<RuleEntry> rules) {
        int index = page * PAGE_SIZE;
        selectRule(index < rules.size() ? rules.get(index) : null);
    }

    private PunishmentOption selectedOption() {
        if (selectedRule == null || selectedRule.options().isEmpty()) {
            return null;
        }
        selectedOptionIndex = Math.max(0, Math.min(selectedOptionIndex, selectedRule.options().size() - 1));
        return selectedRule.options().get(selectedOptionIndex);
    }

    private int colorFor(PunishmentOption option) {
        return switch (option.type()) {
            case WARN -> UiTheme.WARNING;
            case MUTE -> UiTheme.accent();
            case BAN, PERMANENT_BAN -> UiTheme.DANGER;
            case KICK -> UiTheme.SUCCESS;
        };
    }

    private void button(DrawContext context, Rect rect, String label, boolean hovered, boolean enabled, boolean accent) {
        int bg;
        int text;
        if (!enabled) {
            bg = UiTheme.argb(80, 45, 52, 69);
            text = UiTheme.FAINT;
        } else if (accent) {
            bg = hovered ? UiTheme.blend(UiTheme.accent(), UiTheme.accent2(), 0.45F) : UiTheme.accent();
            text = UiTheme.TEXT;
        } else {
            bg = hovered ? UiTheme.CARD_HOVER : UiTheme.CARD;
            text = UiTheme.TEXT;
        }
        UiTheme.roundedRect(context, rect.x(), rect.y(), rect.w(), rect.h(), 9, bg);
        int tx = rect.x() + (rect.w() - UiTheme.textWidth(label, UiTheme.FONT_SIZE, true)) / 2;
        int ty = rect.y() + (rect.h() - 8) / 2;
        UiTheme.textBold(context, textRenderer, label, tx, ty, text);
    }

    private void drawWrapped(DrawContext context, String text, int x, int y, int maxWidth, int color, int maxLines) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (UiTheme.textWidth(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        int count = Math.min(lines.size(), maxLines);
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) {
                line = UiTheme.ellipsize(textRenderer, line + "…", maxWidth);
            }
            UiTheme.text(context, textRenderer, line, x, y + i * 12, 9.0F, color, false);
        }
    }

    private Layout layout() {
        int panelW = Math.min(1040, Math.max(760, width - 34));
        int panelH = Math.min(650, Math.max(500, height - 34));
        panelW = Math.min(panelW, width - 12);
        panelH = Math.min(panelH, height - 12);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int sidebarW = Math.min(190, Math.max(154, panelW / 6));
        int detailW = Math.min(320, Math.max(270, panelW / 3));
        int contentX = panelX + sidebarW + 18;
        int detailX = panelX + panelW - detailW - 16;
        int contentW = Math.max(350, detailX - contentX - 16);
        return new Layout(panelX, panelY, panelW, panelH, sidebarW, contentX, contentW, detailX, detailW);
    }

    private Rect optionBoxRect(Layout l) {
        return new Rect(l.detailX() + 18, l.panelY() + 190, l.detailWidth() - 36, 56);
    }

    private Rect previousOptionRect(Layout l) {
        Rect box = optionBoxRect(l);
        return new Rect(box.x() + box.w() - 58, box.y() + 14, 22, 28);
    }

    private Rect nextOptionRect(Layout l) {
        Rect box = optionBoxRect(l);
        return new Rect(box.x() + box.w() - 31, box.y() + 14, 22, 28);
    }

    private Rect issueButtonRect(Layout l) {
        return new Rect(l.detailX() + 18, l.panelY() + l.panelHeight() - 67, l.detailWidth() - 82, 43);
    }

    private Rect settingsRect(Layout l) {
        return new Rect(l.detailX() + l.detailWidth() - 52, l.panelY() + l.panelHeight() - 64, 38, 38);
    }

    private Rect prevPageRect(Layout l) {
        return new Rect(l.contentX(), l.panelY() + l.panelHeight() - 42, 32, 27);
    }

    private Rect nextPageRect(Layout l) {
        return new Rect(l.contentX() + l.contentWidth() - 32, l.panelY() + l.panelHeight() - 42, 32, 27);
    }

    private static Rect cancelRect(int x, int y, int modalW, int modalH) {
        return new Rect(x + 20, y + modalH - 51, (modalW - 50) / 2, 33);
    }

    private static Rect confirmRect(int x, int y, int modalW, int modalH) {
        int w = (modalW - 50) / 2;
        return new Rect(x + 30 + w, y + modalH - 51, w, 33);
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int sidebarWidth, int contentX, int contentWidth,
                          int detailX, int detailWidth) {
        Layout withPanelY(int newY) {
            int delta = newY - panelY;
            return new Layout(panelX, newY, panelWidth, panelHeight, sidebarWidth,
                    contentX, contentWidth, detailX, detailWidth);
        }
    }
}

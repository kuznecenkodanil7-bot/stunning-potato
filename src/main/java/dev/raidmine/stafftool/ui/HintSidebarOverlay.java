package dev.raidmine.stafftool.ui;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.stats.SessionStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class HintSidebarOverlay {
    private HintSidebarOverlay() {
    }

    public static void render(DrawContext context, SessionStats stats) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!RaidMineStaffMod.config().showHintsPanel || client.player == null || !stats.goalReached()) return;
        int width = Math.min(226, client.getWindow().getScaledWidth() / 3);
        int height = 84;
        int x = client.getWindow().getScaledWidth() - width - 10;
        int y = Math.max(48, client.getWindow().getScaledHeight() / 2 - height / 2);

        UiTheme.shadow(context, x, y, width, height, 14);
        UiTheme.roundedRect(context, x, y, width, height, 14, UiTheme.SUCCESS);
        UiTheme.roundedRect(context, x + 2, y + 2, width - 4, height - 4, 12, UiTheme.argb(246, 13, 18, 20));
        UiTheme.panelTexture(context, x + 3, y + 3, width - 6, height - 6, UiTheme.SUCCESS);
        UiTheme.icon(context, UiIcon.BELL, x + 13, y + 13, 16, UiTheme.SUCCESS);
        UiTheme.text(context, client.textRenderer, "Уведомления и подсказки", x + 38, y + 14,
                10F, UiTheme.TEXT, true);
        context.fill(x + 13, y + 36, x + width - 13, y + 37, UiTheme.argb(80, 98, 114, 120));
        UiTheme.icon(context, UiIcon.CHECK, x + 14, y + 49, 14, UiTheme.SUCCESS);
        UiTheme.text(context, client.textRenderer, "Молодцы! Вы отыграли", x + 38, y + 47,
                9.2F, UiTheme.TEXT, true);
        UiTheme.text(context, client.textRenderer, "сегодняшнюю норму онлайна!", x + 38, y + 62,
                8.8F, UiTheme.MUTED, false);
    }
}

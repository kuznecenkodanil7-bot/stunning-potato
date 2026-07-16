package dev.raidmine.stafftool.ui;

import dev.raidmine.stafftool.util.ChatSelectionHelper;
import net.minecraft.client.gui.DrawContext;

public final class ChatHoverRenderer {
    private ChatHoverRenderer() {
    }

    public static void render(DrawContext context, ChatSelectionHelper.Hit hit) {
        if (hit == null || hit.visibleToken().isEmpty()) return;

        int x = hit.left() - 1;
        int y = hit.top();
        int w = hit.width() + 2;
        int h = hit.height() + 1;
        int radius = Math.max(2, Math.min(4, h / 3));
        int fill = UiTheme.withAlpha(UiTheme.accent(), 44);
        int outline = UiTheme.withAlpha(UiTheme.accent(), 218);

        // No glow and no second text rendering: only a clean accent fill and
        // one-pixel outline over the author token already drawn by the chat.
        UiTheme.roundedBorder(context, x, y, w, h, radius, 1, outline, fill);
    }
}

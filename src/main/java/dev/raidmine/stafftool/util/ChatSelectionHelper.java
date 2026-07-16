package dev.raidmine.stafftool.util;

import dev.raidmine.stafftool.mixin.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;

import java.util.List;
import java.util.Optional;

/**
 * Finds only the message author. The primary path asks ChatHud to emit its
 * real transformed glyph coordinates; the visible-line calculation is kept
 * only as a SmartChat compatibility fallback.
 */
public final class ChatSelectionHelper {
    private static final int CHAT_BOTTOM_OFFSET = 40;

    private ChatSelectionHelper() {
    }

    public static Optional<Hit> find(MinecraftClient client, double mouseX, double mouseY) {
        if (client == null || client.inGameHud == null || client.textRenderer == null) return Optional.empty();
        ChatHud chatHud = client.inGameHud.getChatHud();

        // Exact path: this uses the same transformation stack as vanilla ChatHud,
        // including Minecraft chat scale and line spacing.
        NicknameHitConsumer consumer = new NicknameHitConsumer(
                client.textRenderer,
                (int) Math.round(mouseX),
                (int) Math.round(mouseY)
        );
        chatHud.render(consumer, client.getWindow().getScaledHeight(), client.inGameHud.getTicks(), true);
        Optional<NicknameHitConsumer.Hit> exact = consumer.hit();
        if (exact.isPresent()) {
            NicknameHitConsumer.Hit hit = exact.get();
            return Optional.of(new Hit(hit.nickname(), hit.visibleToken(),
                    hit.left(), hit.top(), hit.right(), hit.bottom()));
        }

        // SmartChat fallback: some versions render the visible lines themselves.
        if (!(chatHud instanceof ChatHudAccessor accessor)) return Optional.empty();
        List<ChatHudLine.Visible> visible = accessor.rmtools$getVisibleMessages();
        if (visible == null || visible.isEmpty()) return Optional.empty();

        double scale = Math.max(0.25D, Math.min(2.0D, client.options.getChatScale().getValue()));
        double spacing = Math.max(0.0D, Math.min(1.0D, client.options.getChatLineSpacing().getValue()));
        int lineStep = Math.max(9, (int) (9.0D * (spacing + 1.0D)));
        int textOffset = (int) Math.round(8.0D * (spacing + 1.0D) - 4.0D * spacing);
        int base = (int) Math.floor((client.getWindow().getScaledHeight() - CHAT_BOTTOM_OFFSET) / scale);
        int scrolled = Math.max(0, accessor.rmtools$getScrolledLines());
        int maxRows = Math.min(Math.max(0, visible.size() - scrolled), 100);
        TextRenderer renderer = client.textRenderer;

        Hit nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int row = 0; row < maxRows; row++) {
            int visibleIndex = row + scrolled;
            if (visibleIndex < 0 || visibleIndex >= visible.size()) break;
            String raw = orderedTextToString(visible.get(visibleIndex).content());
            Optional<NicknameResolver.AuthorMatch> match = NicknameResolver.resolveAuthorMatch(raw);
            if (match.isEmpty()) continue;

            NicknameResolver.AuthorMatch author = match.get();
            String prefix = raw.substring(0, author.start());
            String token = raw.substring(author.start(), author.end());
            int unscaledTop = base - row * lineStep - textOffset;
            int top = (int) Math.round(unscaledTop * scale);
            int bottom = top + Math.max(8, (int) Math.ceil(9.0D * scale));
            int left = (int) Math.round(4.0D * scale + renderer.getWidth(prefix) * scale);
            int right = left + Math.max(1, (int) Math.ceil(renderer.getWidth(token) * scale));

            if (mouseX < left - 3 || mouseX > right + 3) continue;
            double centerY = (top + bottom) * 0.5D;
            double distance = Math.abs(mouseY - centerY);
            if (distance < nearestDistance && distance <= Math.max(12D, lineStep * scale * 1.35D)) {
                nearestDistance = distance;
                nearest = new Hit(author.name(), token, left, top, right, bottom);
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static String orderedTextToString(OrderedText text) {
        StringBuilder builder = new StringBuilder();
        if (text != null) {
            text.accept((index, style, codePoint) -> {
                builder.appendCodePoint(codePoint);
                return true;
            });
        }
        return builder.toString();
    }

    public record Hit(String nickname, String visibleToken, int left, int top, int right, int bottom) {
        public int width() { return Math.max(1, right - left); }
        public int height() { return Math.max(1, bottom - top); }
    }
}

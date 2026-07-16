package dev.raidmine.stafftool.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

/** Tracks real glyph coordinates produced by vanilla chat and SmartChat. */
public final class ChatRenderTracker {
    private static final int TEXT_HEIGHT = 9;
    private static final List<ChatSelectionHelper.Hit> LAST_HITS = new ArrayList<>();
    private static final Map<Integer, ChatSelectionHelper.Hit> LINE_AUTHORS = new HashMap<>();
    private static boolean collecting;
    private static Screen screen;
    private static DrawContext frameContext;
    private static long completedAt;
    private static long frameStartedAt;

    private ChatRenderTracker() { }

    public static void begin(Screen currentScreen) {
        collecting = true;
        screen = currentScreen;
        frameContext = null;
        frameStartedAt = System.currentTimeMillis();
        LAST_HITS.clear();
        LINE_AUTHORS.clear();
    }

    public static void invalidate() {
        collecting = false;
        frameContext = null;
        screen = null;
        completedAt = 0L;
        frameStartedAt = 0L;
        LAST_HITS.clear();
        LINE_AUTHORS.clear();
    }

    public static void finish(Screen currentScreen) {
        if (screen == currentScreen) completedAt = System.currentTimeMillis();
        collecting = false;
    }

    public static Optional<LocalHover> observeAndFindHover(DrawContext context, TextRenderer renderer,
                                                           OrderedText text, int x, int y,
                                                           int mouseX, int mouseY) {
        if (context == null || renderer == null || text == null) return Optional.empty();
        Screen current = MinecraftClient.getInstance().currentScreen;
        if (!isChatLikeScreen(current)) return Optional.empty();

        if (!collecting) {
            if (frameContext != context || screen != current) {
                LAST_HITS.clear();
                LINE_AUTHORS.clear();
                frameContext = context;
                screen = current;
                frameStartedAt = System.currentTimeMillis();
            }
        } else if (frameContext != context) {
            frameContext = context;
            frameStartedAt = System.currentTimeMillis();
        }

        StringBuilder raw = new StringBuilder();
        List<Piece> pieces = new ArrayList<>();
        final float[] cursor = {x};
        text.accept((index, style, codePoint) -> {
            String character = new String(Character.toChars(codePoint));
            int start = raw.length();
            raw.append(character);
            float width = renderer.getTextHandler().getWidth(OrderedText.styled(codePoint, style));
            pieces.add(new Piece(start, raw.length(), cursor[0], cursor[0] + width));
            cursor[0] += width;
            return true;
        });

        Optional<NicknameResolver.AuthorMatch> fullLine = NicknameResolver.resolveAuthorMatch(raw.toString());
        Optional<NicknameResolver.AuthorMatch> resolved = fullLine.isPresent()
                ? fullLine : NicknameResolver.resolveStandaloneAuthorMatch(raw.toString());
        if (resolved.isEmpty()) return Optional.empty();
        NicknameResolver.AuthorMatch author = resolved.get();

        float left = Float.MAX_VALUE;
        float right = Float.MIN_VALUE;
        for (Piece piece : pieces) {
            if (piece.end() > author.start() && piece.start() < author.end()) {
                left = Math.min(left, piece.left());
                right = Math.max(right, piece.right());
            }
        }
        if (left == Float.MAX_VALUE || right <= left) return Optional.empty();

        Matrix3x2f matrix = new Matrix3x2f(context.getMatrices());
        Vector2f a = matrix.transformPosition(new Vector2f(left, y));
        Vector2f b = matrix.transformPosition(new Vector2f(right, y));
        Vector2f c = matrix.transformPosition(new Vector2f(left, y + TEXT_HEIGHT));
        Vector2f d = matrix.transformPosition(new Vector2f(right, y + TEXT_HEIGHT));
        int screenLeft = Math.round(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)));
        int screenRight = Math.round(Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)));
        int screenTop = Math.round(Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)));
        int screenBottom = Math.round(Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)));
        if (screenRight <= screenLeft || screenBottom <= screenTop) return Optional.empty();

        ChatSelectionHelper.Hit hit = new ChatSelectionHelper.Hit(author.name(),
                raw.substring(author.start(), author.end()), screenLeft, screenTop, screenRight, screenBottom);
        int lineKey = Math.round(screenTop / 3.0F) * 3;
        ChatSelectionHelper.Hit existing = LINE_AUTHORS.get(lineKey);
        boolean authoritative = fullLine.isPresent();
        if (existing != null && !authoritative && screenLeft >= existing.left()) {
            return Optional.empty();
        }
        if (existing != null && !existing.equals(hit)) LAST_HITS.remove(existing);
        LINE_AUTHORS.put(lineKey, hit);
        if (!LAST_HITS.contains(hit)) LAST_HITS.add(hit);
        completedAt = System.currentTimeMillis();

        if (mouseX >= screenLeft - 1 && mouseX <= screenRight + 1
                && mouseY >= screenTop - 1 && mouseY <= screenBottom + 1) {
            return Optional.of(new LocalHover(Math.round(left) - 1, y - 1,
                    Math.max(2, Math.round(right - left) + 2), TEXT_HEIGHT + 2, hit));
        }
        return Optional.empty();
    }

    public static boolean hasFrameAfter(Screen currentScreen, long timestamp) {
        return currentScreen != null && currentScreen == screen
                && completedAt >= timestamp && frameStartedAt >= timestamp;
    }

    public static boolean hasFreshFrame(Screen currentScreen) {
        return currentScreen != null && currentScreen == screen
                && System.currentTimeMillis() - completedAt <= 1200L;
    }

    public static boolean hasAuthor(Screen currentScreen, String nickname) {
        if (!hasFreshFrame(currentScreen) || nickname == null || nickname.isBlank()) return false;
        for (ChatSelectionHelper.Hit hit : LAST_HITS) {
            if (hit.nickname().equalsIgnoreCase(nickname)) return true;
        }
        return false;
    }

    public static Optional<ChatSelectionHelper.Hit> find(Screen currentScreen, double mouseX, double mouseY) {
        if (currentScreen == null || currentScreen != screen) return Optional.empty();
        if (System.currentTimeMillis() - completedAt > 2500L) return Optional.empty();
        ChatSelectionHelper.Hit best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ChatSelectionHelper.Hit hit : LAST_HITS) {
            if (mouseX < hit.left() - 2 || mouseX > hit.right() + 2
                    || mouseY < hit.top() - 2 || mouseY > hit.bottom() + 2) continue;
            double centerX = (hit.left() + hit.right()) * 0.5D;
            double centerY = (hit.top() + hit.bottom()) * 0.5D;
            double distance = Math.abs(mouseX - centerX) + Math.abs(mouseY - centerY) * 1.5D;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = hit;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isChatLikeScreen(Screen current) {
        if (current == null) return false;
        if (current instanceof ChatScreen) return true;
        String simple = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String full = current.getClass().getName().toLowerCase(Locale.ROOT);
        if (simple.contains("chat") || full.contains("smartchat")) return true;
        if (full.contains("labymod")) {
            return simple.contains("message") || simple.contains("chatoverlay")
                    || full.contains("smart.chat") || full.contains("smart_chat");
        }
        return false;
    }

    private record Piece(int start, int end, float left, float right) { }
    public record LocalHover(int x, int y, int width, int height, ChatSelectionHelper.Hit screenHit) { }
}

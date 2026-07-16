package dev.raidmine.stafftool.chat;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.ui.UiTheme;
import dev.raidmine.stafftool.util.NicknameResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Applies forbidden-word styling and dispatches chat notifications. */
public final class ChatTextProcessor {
    public static final int FORBIDDEN_START = 0xFFFF2F55;
    public static final int FORBIDDEN_END = 0xFFFF77C8;
    private static String lastNoticeKey = "";
    private static long lastNoticeAt;

    private ChatTextProcessor() { }

    public static Text process(Text original) {
        if (original == null) return Text.empty();
        String plain = original.getString();
        if (plain.isEmpty()) return original;
        MessageInfo info = inspectRenderedLine(plain);
        if (info.forbiddenRanges().isEmpty()) return original;

        List<StyledPoint> points = new ArrayList<>();
        StringBuilder rebuiltPlain = new StringBuilder();
        original.asOrderedText().accept((index, style, codePoint) -> {
            int start = rebuiltPlain.length();
            rebuiltPlain.appendCodePoint(codePoint);
            points.add(new StyledPoint(start, rebuiltPlain.length(), codePoint, style));
            return true;
        });

        MutableText result = Text.empty();
        for (StyledPoint point : points) {
            ForbiddenRange forbidden = containing(info.forbiddenRanges(), point.start());
            Style style = point.style();
            if (forbidden != null) {
                float t = ratio(point.start(), forbidden.start(), forbidden.end());
                style = style.withColor(UiTheme.blend(FORBIDDEN_START, FORBIDDEN_END, t)).withBold(true);
            }
            result.append(Text.literal(new String(Character.toChars(point.codePoint()))).setStyle(style));
        }
        return result;
    }

    /** Works for SmartChat because it can be called from live text rendering. */
    public static MessageInfo inspectRenderedLine(String plain) {
        if (plain == null || plain.isBlank()) return MessageInfo.EMPTY;
        MinecraftClient client = MinecraftClient.getInstance();
        String author = NicknameResolver.resolveAuthor(plain).orElse("");
        int separator = author.isBlank() ? -1 : NicknameResolver.messageSeparator(plain, author);
        int bodyStart = separator >= 0 ? separator + 1 : 0;
        String body = bodyStart < plain.length() ? plain.substring(bodyStart) : "";
        boolean playerMessage = !author.isBlank() && separator >= 0;
        // SmartChat may render the message body in a separate draw call. Search
        // that component too so forbidden words still receive the gradient.
        List<ForbiddenRange> ranges = findForbiddenRanges(plain, bodyStart);

        if (playerMessage && RaidMineStaffMod.config().mentionNotifications
                && client.player != null && !ChatEventGate.mentionsSuppressed()) {
            String ownName = client.player.getGameProfile().name();
            if (!author.equalsIgnoreCase(ownName) && containsExactToken(body, ownName)) {
                notifyOnce("mention|" + author + "|" + plain, () -> UiNotificationCenter.mention(author));
            }
        }
        if (RaidMineStaffMod.config().forbiddenWordAlerts && !ranges.isEmpty()) {
            ForbiddenRange first = ranges.getFirst();
            String source = author.isBlank() ? "Игрок" : author;
            notifyOnce("violation|" + source + "|" + first.value() + "|" + plain,
                    () -> UiNotificationCenter.violation(source, first.value()));
        }
        return new MessageInfo(author, separator, ranges);
    }

    public static List<ForbiddenRange> forbiddenRanges(String plain) {
        return inspectRenderedLine(plain).forbiddenRanges();
    }

    private static void notifyOnce(String key, Runnable action) {
        long now = System.currentTimeMillis();
        if (key.equals(lastNoticeKey) && now - lastNoticeAt < 3500L) return;
        lastNoticeKey = key;
        lastNoticeAt = now;
        action.run();
    }

    private static List<ForbiddenRange> findForbiddenRanges(String text, int searchFrom) {
        List<ForbiddenRange> ranges = new ArrayList<>();
        if (!RaidMineStaffMod.config().forbiddenWordAlerts) return ranges;
        for (String configured : RaidMineStaffMod.config().forbiddenWords) {
            String word = configured == null ? "" : configured.trim();
            if (word.isEmpty()) continue;
            int from = Math.max(0, searchFrom);
            while (from < text.length()) {
                int index = indexOfIgnoreCase(text, word, from);
                if (index < 0) break;
                int end = index + word.length();
                ranges.add(new ForbiddenRange(index, end, text.substring(index, end)));
                from = Math.max(end, index + 1);
            }
        }
        ranges.sort(Comparator.comparingInt(ForbiddenRange::start));
        return ranges;
    }

    private static boolean containsExactToken(String text, String token) {
        if (token == null || token.isBlank()) return false;
        int index = indexOfIgnoreCase(text, token, 0);
        while (index >= 0) {
            int end = index + token.length();
            if ((index == 0 || !nicknameChar(text.charAt(index - 1)))
                    && (end >= text.length() || !nicknameChar(text.charAt(end)))) return true;
            index = indexOfIgnoreCase(text, token, index + 1);
        }
        return false;
    }

    private static boolean nicknameChar(char c) {
        return c == '_' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
    }

    private static int indexOfIgnoreCase(String text, String search, int from) {
        return text.toLowerCase(Locale.ROOT).indexOf(search.toLowerCase(Locale.ROOT), from);
    }

    private static ForbiddenRange containing(List<ForbiddenRange> ranges, int position) {
        for (ForbiddenRange range : ranges) if (position >= range.start() && position < range.end()) return range;
        return null;
    }

    private static float ratio(int position, int start, int end) {
        return end <= start + 1 ? 0F : Math.max(0F, Math.min(1F,
                (position - start) / (float) (end - start - 1)));
    }

    private record StyledPoint(int start, int end, int codePoint, Style style) { }
    public record ForbiddenRange(int start, int end, String value) { }
    public record MessageInfo(String author, int separator, List<ForbiddenRange> forbiddenRanges) {
        private static final MessageInfo EMPTY = new MessageInfo("", -1, List.of());
    }
}

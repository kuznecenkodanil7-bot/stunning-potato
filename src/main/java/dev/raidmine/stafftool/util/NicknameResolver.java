package dev.raidmine.stafftool.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Style;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NicknameResolver {
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_]{2,16}$");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_]{2,16}");
    private static final Pattern COMMAND_NAME = Pattern.compile(
            "(?:^|\\s)/(?:msg|tell|w|whisper|m|reply|r|party\\s+invite|friend\\s+add)\\s+([A-Za-z0-9_]{2,16})(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RECORD_VALUE = Pattern.compile("(?:value|command|suggestion)=([^,)}]+)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> UI_WORDS = Set.of(
            "raidmine", "global", "local", "server", "chattools", "cmi", "minecraft",
            "hover", "click", "message", "command", "prefix", "staff", "moder", "admin",
            "staffchat", "system", "notification", "chat", "clan", "party", "guild"
    );

    private NicknameResolver() {
    }

    public static Optional<String> onlinePlayerExact(String candidate) {
        String cleaned = clean(candidate);
        if (!isValid(cleaned) || isUiWord(cleaned)) return Optional.empty();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return Optional.empty();
        for (PlayerListEntry entry : client.getNetworkHandler().getListedPlayerListEntries()) {
            String name = entry.getProfile().name();
            if (name.equalsIgnoreCase(cleaned)) return Optional.of(name);
        }
        return Optional.empty();
    }

    public static List<String> onlineNames() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return List.of();
        List<String> names = new ArrayList<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getListedPlayerListEntries()) {
            String name = entry.getProfile().name();
            if (isValid(name) && !isUiWord(name)) names.add(name);
        }
        names.sort(Comparator.comparingInt(String::length).reversed());
        return names;
    }

    /**
     * Finds the sender immediately before the real message separator. Colons
     * inside timestamps such as [12:34] are ignored. This prevents pinged
     * players in the message body from becoming clickable.
     */
    public static Optional<AuthorMatch> resolveAuthorMatch(String line) {
        if (line == null || line.isBlank()) return Optional.empty();
        List<TokenMatch> tokens = tokens(line);
        if (tokens.isEmpty()) return Optional.empty();

        for (int separator : separators(line)) {
            TokenMatch online = lastTokenBefore(tokens, separator, true);
            if (online != null && separator - online.end() <= 72) {
                return Optional.of(new AuthorMatch(online.resolvedName(), online.start(), online.end(), separator));
            }
        }

        // Vanished/off-tab fallback: use the last valid non-service token
        // before a plausible message separator.
        for (int separator : separators(line)) {
            TokenMatch fallback = lastTokenBefore(tokens, separator, false);
            if (fallback != null && separator - fallback.end() <= 48) {
                return Optional.of(new AuthorMatch(fallback.raw(), fallback.start(), fallback.end(), separator));
            }
        }
        return Optional.empty();
    }

    public static Optional<String> resolveAuthor(String line) {
        return resolveAuthorMatch(line).map(AuthorMatch::name);
    }

    /**
     * Compatibility path for SmartChat versions that draw the author as an
     * independent text component. It accepts exactly one online nickname and
     * rejects components containing a second ordinary token, so message-body
     * mentions cannot replace an already tracked sender on the same line.
     */
    public static Optional<AuthorMatch> resolveStandaloneAuthorMatch(String line) {
        if (line == null || line.isBlank()) return Optional.empty();
        Matcher matcher = TOKEN.matcher(line);
        AuthorMatch selected = null;
        while (matcher.find()) {
            String raw = matcher.group();
            Optional<String> online = onlinePlayerExact(raw);
            if (online.isPresent()) {
                if (selected != null) return Optional.empty();
                selected = new AuthorMatch(online.get(), matcher.start(), matcher.end(), -1);
            } else if (!isUiWord(raw)) {
                // A standalone author component may include service labels, but
                // not regular words from the message body.
                return Optional.empty();
            }
        }
        return Optional.ofNullable(selected);
    }

    public static int[] locateAuthorToken(String line, String author) {
        Optional<AuthorMatch> match = resolveAuthorMatch(line);
        if (match.isPresent() && match.get().name().equalsIgnoreCase(author)) {
            return new int[]{match.get().start(), match.get().end()};
        }
        return new int[]{-1, -1};
    }

    public static int messageSeparator(String line, String author) {
        Optional<AuthorMatch> match = resolveAuthorMatch(line);
        if (match.isPresent() && match.get().name().equalsIgnoreCase(author)) return match.get().separator();
        return -1;
    }

    public static Optional<String> fromStyle(Style style) {
        if (style == null) return Optional.empty();
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, style.getInsertion());
        inspectObject(style.getClickEvent(), candidates);
        inspectObject(style.getHoverEvent(), candidates);
        for (String candidate : candidates) {
            Optional<String> online = onlinePlayerExact(candidate);
            if (online.isPresent()) return online;
        }
        return Optional.empty();
    }

    public static Optional<String> fromClickedLine(String line, int charStart, int charEnd) {
        Optional<AuthorMatch> match = resolveAuthorMatch(line);
        if (match.isEmpty()) return Optional.empty();
        int start = Math.max(0, charStart);
        int end = Math.max(start + 1, charEnd);
        AuthorMatch author = match.get();
        return end >= author.start() && start <= author.end() ? Optional.of(author.name()) : Optional.empty();
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    public static boolean isNicknameChar(char character) {
        return character == '_'
                || character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9';
    }

    private static List<TokenMatch> tokens(String line) {
        List<TokenMatch> result = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(line);
        while (matcher.find()) {
            String raw = matcher.group();
            if (isUiWord(raw)) continue;
            Optional<String> online = onlinePlayerExact(raw);
            result.add(new TokenMatch(raw, online.orElse(null), matcher.start(), matcher.end()));
        }
        return result;
    }

    private static List<Integer> separators(String line) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < line.length(); index++) {
            char c = line.charAt(index);
            if (c != ':' && c != '»' && c != '›') continue;
            if (c == ':' && index > 0 && index + 1 < line.length()
                    && Character.isDigit(line.charAt(index - 1))
                    && Character.isDigit(line.charAt(index + 1))) {
                continue;
            }
            result.add(index);
        }
        return result;
    }

    private static TokenMatch lastTokenBefore(List<TokenMatch> tokens, int separator, boolean onlineOnly) {
        TokenMatch selected = null;
        for (TokenMatch token : tokens) {
            if (token.end() > separator) break;
            if (onlineOnly && token.resolvedName() == null) continue;
            if (!onlineOnly && !isValid(token.raw())) continue;
            selected = token;
        }
        return selected;
    }

    private static void inspectObject(Object object, Set<String> candidates) {
        if (object == null) return;
        for (String methodName : new String[]{"value", "getValue", "command", "suggestion"}) {
            try {
                Method method = object.getClass().getMethod(methodName);
                Object value = method.invoke(object);
                if (value != null) addFromString(candidates, value.toString());
            } catch (ReflectiveOperationException ignored) { }
        }
        addFromString(candidates, object.toString());
    }

    private static void addFromString(Set<String> candidates, String value) {
        if (value == null || value.isBlank()) return;
        addCandidate(candidates, value);
        Matcher commandMatcher = COMMAND_NAME.matcher(value);
        while (commandMatcher.find()) addCandidate(candidates, commandMatcher.group(1));
        Matcher recordMatcher = RECORD_VALUE.matcher(value);
        while (recordMatcher.find()) addCandidate(candidates, recordMatcher.group(1));
    }

    private static void addCandidate(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) candidates.add(value);
    }

    private static String clean(String value) {
        String result = value == null ? "" : value.trim();
        result = result.replace("\\\"", "").replace("\"", "");
        if (result.startsWith("/")) {
            Matcher matcher = COMMAND_NAME.matcher(result);
            if (matcher.find()) return matcher.group(1);
        }
        String[] tokens = result.split("[^A-Za-z0-9_]+");
        for (String token : tokens) {
            if (isValid(token) && !isUiWord(token)) return token;
        }
        return result.replaceAll("[^A-Za-z0-9_]", "");
    }

    private static boolean isUiWord(String value) {
        return UI_WORDS.contains(value.toLowerCase(Locale.ROOT));
    }

    public record AuthorMatch(String name, int start, int end, int separator) { }
    private record TokenMatch(String raw, String resolvedName, int start, int end) { }
}

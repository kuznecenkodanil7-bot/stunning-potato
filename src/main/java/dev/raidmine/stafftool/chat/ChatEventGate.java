package dev.raidmine.stafftool.chat;

public final class ChatEventGate {
    private static volatile long suppressMentionsUntil;

    private ChatEventGate() {
    }

    public static void suppressMentions(long milliseconds) {
        suppressMentionsUntil = Math.max(suppressMentionsUntil, System.currentTimeMillis() + Math.max(0L, milliseconds));
    }

    public static boolean mentionsSuppressed() {
        return System.currentTimeMillis() < suppressMentionsUntil;
    }
}

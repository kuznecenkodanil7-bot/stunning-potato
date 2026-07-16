package dev.raidmine.stafftool.stats;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.chat.ChatEventGate;
import dev.raidmine.stafftool.chat.UiNotificationCenter;
import dev.raidmine.stafftool.rules.PunishmentType;
import dev.raidmine.stafftool.util.ServerGuard;
import dev.raidmine.stafftool.util.AfkKickManager;
import net.minecraft.client.MinecraftClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

public final class SessionStats {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final long SAVE_INTERVAL_MILLIS = 30_000L;

    private final DailyStateStore.State state;
    private long lastTickMillis;
    private long lastSaveMillis;
    private boolean active;
    private boolean forcedHubPause;
    private long forcedHubPauseAt;
    private boolean vanished;
    private String currentMode = "—";
    private long lastActionMillis;
    private PunishmentType lastType;
    private String lastRecordedCommand = "";
    private long lastRecordedCommandAt;

    public SessionStats() {
        state = DailyStateStore.load();
        ensureMoscowDay();
    }

    public void tick(MinecraftClient client) {
        ensureMoscowDay();
        long now = System.currentTimeMillis();
        if (lastTickMillis == 0L) lastTickMillis = now;

        long delta = Math.max(0L, Math.min(5_000L, now - lastTickMillis));
        if (active) state.activeMillis += delta;

        currentMode = ServerGuard.detectMode(client);
        if (forcedHubPause
                && now - forcedHubPauseAt >= 5_000L
                && ServerGuard.isKnownActiveMode(client)) {
            forcedHubPause = false;
        }
        active = !forcedHubPause
                && ServerGuard.isActivityCounted(client)
                && !AfkKickManager.isOnlineTimerPaused();
        lastTickMillis = now;

        if (goalReached() && !state.goalNoticeShown) {
            state.goalNoticeShown = true;
            UiNotificationCenter.info("Норма онлайна выполнена", "Молодцы! Вы отыграли сегодняшнюю норму онлайна!");
            saveNow();
        } else if (now - lastSaveMillis >= SAVE_INTERVAL_MILLIS) {
            saveNow();
        }
    }

    public void record(PunishmentType type) {
        ensureMoscowDay();
        switch (type) {
            case BAN, PERMANENT_BAN -> state.bans++;
            case MUTE -> state.mutes++;
            case WARN -> state.warns++;
            case KICK -> { }
        }
        lastType = type;
        lastActionMillis = System.currentTimeMillis();
        saveNow();
    }

    public void observeManualCommand(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return;
        String normalized = rawMessage.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("/")) return;
        long now = System.currentTimeMillis();
        if (normalized.equals(lastRecordedCommand) && now - lastRecordedCommandAt < 1500L) return;
        lastRecordedCommand = normalized;
        lastRecordedCommandAt = now;

        String hub = normalizeCommand(RaidMineStaffMod.config().hubCommand);
        if (startsWithCommand(normalized, hub)) {
            forcedHubPause = true;
            forcedHubPauseAt = now;
            active = false;
            currentMode = "HUB";
        } else if (normalized.startsWith("/server ") || normalized.startsWith("/play ") || normalized.startsWith("/join ")) {
            forcedHubPause = false;
            forcedHubPauseAt = 0L;
        }
        if (isVanishCommand(normalized)) {
            vanished = !vanished;
            UiNotificationCenter.info("Ваниш", vanished ? "Режим невидимости включён" : "Режим невидимости выключен");
        }

        PunishmentType type = detectPunishment(normalized);
        if (type != null) {
            ChatEventGate.suppressMentions(3500L);
            record(type);
        }
    }


    private boolean isVanishCommand(String normalized) {
        if (startsWithCommand(normalized, normalizeCommand(RaidMineStaffMod.config().vanishCommand))) return true;
        for (String alias : RaidMineStaffMod.config().vanishCommandAliases) {
            if (startsWithCommand(normalized, normalizeCommand(alias))) return true;
        }
        return false;
    }

    private PunishmentType detectPunishment(String normalized) {
        String warnToken = firstToken(RaidMineStaffMod.config().warnCommand);
        String muteToken = firstToken(RaidMineStaffMod.config().muteCommand);
        String banToken = firstToken(RaidMineStaffMod.config().banCommand);
        String permanentBanToken = firstToken(RaidMineStaffMod.config().permanentBanCommand);
        if (startsWithCommand(normalized, warnToken)) return PunishmentType.WARN;
        if (startsWithCommand(normalized, muteToken)) return PunishmentType.MUTE;
        if (startsWithCommand(normalized, permanentBanToken)) return PunishmentType.PERMANENT_BAN;
        if (startsWithCommand(normalized, banToken)) return PunishmentType.BAN;
        return null;
    }

    private boolean startsWithCommand(String raw, String token) {
        if (token.isBlank()) return false;
        String normalizedToken = token.startsWith("/") ? token : "/" + token;
        return raw.equals(normalizedToken) || raw.startsWith(normalizedToken + " ");
    }

    private String firstToken(String template) {
        String cleaned = normalizeCommand(template);
        int space = cleaned.indexOf(' ');
        return space < 0 ? cleaned : cleaned.substring(0, space);
    }

    private String normalizeCommand(String command) {
        String cleaned = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        return cleaned;
    }

    private void ensureMoscowDay() {
        String today = LocalDate.now(MOSCOW).toString();
        if (today.equals(state.moscowDate)) return;
        state.moscowDate = today;
        state.activeMillis = 0L;
        state.bans = 0;
        state.mutes = 0;
        state.warns = 0;
        state.goalNoticeShown = false;
        lastTickMillis = System.currentTimeMillis();
        saveNow();
    }

    private void saveNow() {
        DailyStateStore.save(state);
        lastSaveMillis = System.currentTimeMillis();
    }

    public int bans() { return state.bans; }
    public int mutes() { return state.mutes; }
    public int warns() { return state.warns; }
    public String currentMode() { return currentMode; }
    public boolean isActive() { return active; }
    public boolean isVanished() { return vanished; }
    public boolean isHubPaused() { return forcedHubPause || ServerGuard.isPausedModeName(currentMode); }

    public long elapsedSeconds() {
        long live = state.activeMillis;
        if (active && lastTickMillis > 0L) {
            live += Math.max(0L, Math.min(5_000L, System.currentTimeMillis() - lastTickMillis));
        }
        return Math.max(0L, live / 1000L);
    }

    public long goalSeconds() {
        return Math.max(60L, RaidMineStaffMod.config().dailyOnlineGoalMinutes * 60L);
    }

    public boolean goalReached() {
        return elapsedSeconds() >= goalSeconds();
    }

    public float goalProgress() {
        return Math.max(0F, Math.min(1F, elapsedSeconds() / (float) goalSeconds()));
    }

    public float pulse(PunishmentType type) {
        if (type != lastType || lastActionMillis == 0L) return 0F;
        long age = System.currentTimeMillis() - lastActionMillis;
        if (age >= 700L) return 0F;
        float t = age / 700F;
        return (1F - t) * (1F - t);
    }
}

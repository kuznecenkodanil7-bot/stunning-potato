package dev.raidmine.stafftool.util;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.chat.UiNotificationCenter;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ThreadLocalRandom;

public final class AfkKickManager {
    private static long lastActivityAt = System.currentTimeMillis();
    private static long thresholdMillis;
    private static double lastMouseX = Double.NaN;
    private static double lastMouseY = Double.NaN;
    private static double lastPlayerX = Double.NaN;
    private static double lastPlayerY = Double.NaN;
    private static double lastPlayerZ = Double.NaN;
    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;
    private static boolean sentToHub;

    private AfkKickManager() {
    }

    public static void markActivity() {
        lastActivityAt = System.currentTimeMillis();
        sentToHub = false;
        chooseThreshold();
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            resetSnapshot();
            return;
        }
        if (client.getCurrentServerEntry() == null || !RaidMineStaffMod.config().afkKickEnabled) {
            markActivity();
            capture(client);
            return;
        }
        if (!ServerGuard.isActivityCounted(client) || RaidMineStaffMod.stats().isHubPaused()) {
            markActivity();
            capture(client);
            return;
        }

        if (hasMoved(client)) markActivity();
        capture(client);
        if (thresholdMillis == 0L) chooseThreshold();

        long idleMillis = System.currentTimeMillis() - lastActivityAt;
        if (!sentToHub && idleMillis >= thresholdMillis) {
            String command = RaidMineStaffMod.config().hubCommand == null ? "hub" : RaidMineStaffMod.config().hubCommand.trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (!command.isBlank()) {
                client.player.networkHandler.sendChatCommand(command);
                UiNotificationCenter.info("AFK Kick", "Вы отправлены в /hub до серверного AFK-кика");
                sentToHub = true;
            }
        }
    }

    public static long idleMillis() {
        return Math.max(0L, System.currentTimeMillis() - lastActivityAt);
    }

    public static boolean isOnlineTimerPaused() {
        int seconds = RaidMineStaffMod.config() == null ? 60 : RaidMineStaffMod.config().afkOnlinePauseSeconds;
        return idleMillis() >= seconds * 1000L;
    }

    public static long secondsUntilHub() {
        if (thresholdMillis <= 0L) return 0L;
        return Math.max(0L, (thresholdMillis - (System.currentTimeMillis() - lastActivityAt)) / 1000L);
    }

    private static boolean hasMoved(MinecraftClient client) {
        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();
        if (!Double.isNaN(lastMouseX) && (Math.abs(mouseX - lastMouseX) > 0.25 || Math.abs(mouseY - lastMouseY) > 0.25)) {
            return true;
        }
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        if (!Double.isNaN(lastPlayerX)) {
            double distanceSq = square(x - lastPlayerX) + square(y - lastPlayerY) + square(z - lastPlayerZ);
            if (distanceSq > 0.0004D || Math.abs(yaw - lastYaw) > 0.25F || Math.abs(pitch - lastPitch) > 0.25F) {
                return true;
            }
        }
        return false;
    }

    private static void capture(MinecraftClient client) {
        lastMouseX = client.mouse.getX();
        lastMouseY = client.mouse.getY();
        lastPlayerX = client.player.getX();
        lastPlayerY = client.player.getY();
        lastPlayerZ = client.player.getZ();
        lastYaw = client.player.getYaw();
        lastPitch = client.player.getPitch();
    }

    private static void resetSnapshot() {
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
        lastPlayerX = Double.NaN;
        lastPlayerY = Double.NaN;
        lastPlayerZ = Double.NaN;
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
        sentToHub = false;
    }

    private static void chooseThreshold() {
        int min = RaidMineStaffMod.config() == null ? 240 : RaidMineStaffMod.config().afkKickMinSeconds;
        int max = RaidMineStaffMod.config() == null ? 240 : RaidMineStaffMod.config().afkKickMaxSeconds;
        int selected = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        thresholdMillis = selected * 1000L;
    }

    private static double square(double value) {
        return value * value;
    }
}

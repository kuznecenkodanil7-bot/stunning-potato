package dev.raidmine.stafftool.util;

import dev.raidmine.stafftool.RaidMineStaffMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerGuard {
    private ServerGuard() {
    }

    public static boolean isAllowed(MinecraftClient client) {
        if (!RaidMineStaffMod.config().restrictToRaidMine) {
            return true;
        }
        String context = collectContext(client).toLowerCase(Locale.ROOT);
        return RaidMineStaffMod.config().allowedAddressFragments.stream()
                .filter(fragment -> fragment != null && !fragment.isBlank())
                .map(fragment -> fragment.toLowerCase(Locale.ROOT))
                .anyMatch(context::contains);
    }

    public static String currentAddress(MinecraftClient client) {
        ServerInfo server = client.getCurrentServerEntry();
        return server == null || server.address == null ? "одиночная игра" : server.address;
    }

    public static boolean isActivityCounted(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) return false;
        if (!isAllowed(client)) return false;
        String context = collectContext(client).toLowerCase(Locale.ROOT);
        for (String keyword : RaidMineStaffMod.config().pausedServerKeywords) {
            if (!keyword.isBlank() && context.contains(keyword)) return false;
        }
        for (String keyword : RaidMineStaffMod.config().activeServerKeywords) {
            if (!keyword.isBlank() && context.contains(keyword)) return true;
        }
        return !context.contains("hub") && !context.contains("lobby") && !context.contains("limbo");
    }


    public static boolean isKnownActiveMode(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) return false;
        String context = collectContext(client).toLowerCase(Locale.ROOT);
        for (String keyword : RaidMineStaffMod.config().activeServerKeywords) {
            if (!keyword.isBlank() && context.contains(keyword)) return true;
        }
        return false;
    }

    public static boolean isPausedModeName(String mode) {
        if (mode == null || mode.isBlank()) return false;
        String normalized = mode.toLowerCase(Locale.ROOT);
        for (String keyword : RaidMineStaffMod.config().pausedServerKeywords) {
            if (!keyword.isBlank() && normalized.contains(keyword)) return true;
        }
        return false;
    }

    public static String detectMode(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) return "—";
        String context = collectContext(client).toLowerCase(Locale.ROOT);
        for (String keyword : RaidMineStaffMod.config().pausedServerKeywords) {
            if (!keyword.isBlank() && context.contains(keyword)) return keyword.toUpperCase(Locale.ROOT);
        }
        for (String keyword : RaidMineStaffMod.config().activeServerKeywords) {
            if (!keyword.isBlank() && context.contains(keyword)) return keyword.toUpperCase(Locale.ROOT);
        }
        return "RAIDMINE";
    }

    private static String collectContext(MinecraftClient client) {
        List<String> parts = new ArrayList<>();
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null) {
            add(parts, server.name);
            add(parts, server.address);
        }
        add(parts, invokeToString(client.getNetworkHandler(), "getBrand"));
        add(parts, invokeToString(client.getNetworkHandler(), "getServerBrand"));
        add(parts, invokeToString(client.getNetworkHandler(), "getPlayerListHeader"));
        add(parts, invokeToString(client.getNetworkHandler(), "getPlayerListFooter"));
        add(parts, invokeToString(client.player, "getDisplayName"));
        add(parts, invokeToString(client.player, "getName"));
        add(parts, invokeScoreboardSidebar(client));
        return String.join(" | ", parts);
    }

    private static String invokeScoreboardSidebar(MinecraftClient client) {
        try {
            Scoreboard scoreboard = client.getNetworkHandler().getScoreboard();
            if (scoreboard == null) return "";
            StringBuilder context = new StringBuilder();
            ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (sidebar != null) {
                context.append(sidebar.getName())
                        .append(" | ")
                        .append(sidebar.getDisplayName().getString());
            }
            for (ScoreboardObjective objective : scoreboard.getObjectives()) {
                if (context.length() > 0) context.append(" | ");
                context.append(objective.getName())
                        .append(" | ")
                        .append(objective.getDisplayName().getString());
            }
            return context.toString();
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.debug("Could not inspect the current scoreboard", exception);
            return "";
        }
    }

    private static String invokeToString(Object target, String methodName) {
        if (target == null) return "";
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static void add(List<String> parts, String value) {
        if (value != null && !value.isBlank()) parts.add(value);
    }
}

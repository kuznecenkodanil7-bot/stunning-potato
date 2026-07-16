package dev.raidmine.stafftool.util;

import dev.raidmine.stafftool.RaidMineStaffMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.util.Map;

public final class AuthManager {
    private static volatile boolean authenticated;
    private static volatile String authenticatedUser = "";

    private AuthManager() {
    }

    public static boolean canUseMod() {
        MinecraftClient client = MinecraftClient.getInstance();
        String username = currentSessionName(client);
        return isAllowedUsername(username)
                && authenticated
                && username.equalsIgnoreCase(authenticatedUser);
    }

    public static boolean needsLogin(MinecraftClient client) {
        String username = currentSessionName(client);
        if (username.isBlank()) {
            return false;
        }
        if (!isAllowedUsername(username)) {
            return true;
        }
        return !(authenticated && username.equalsIgnoreCase(authenticatedUser));
    }

    public static boolean isAllowedUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        for (String allowed : RaidMineStaffMod.config().staffCredentials.keySet()) {
            if (allowed.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Uses the current Minecraft nickname automatically. Only the password is
     * entered by the staff member.
     */
    public static boolean login(String password) {
        MinecraftClient client = MinecraftClient.getInstance();
        String username = currentSessionName(client);
        if (!isAllowedUsername(username)) {
            authenticated = false;
            authenticatedUser = "";
            return false;
        }

        for (Map.Entry<String, String> entry : RaidMineStaffMod.config().staffCredentials.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(username)
                    && entry.getValue().equals(password)) {
                authenticated = true;
                authenticatedUser = entry.getKey();
                return true;
            }
        }
        return false;
    }

    public static void logout() {
        authenticated = false;
        authenticatedUser = "";
    }

    /**
     * Returns the real current nickname. The online player profile has
     * priority because launchers/LabyMod may replace the session nickname
     * after Minecraft has already started.
     */
    public static String currentSessionName(MinecraftClient client) {
        if (client == null) {
            return "";
        }

        try {
            if (client.player != null && client.player.getGameProfile() != null) {
                String profileName = client.player.getGameProfile().name();
                if (profileName != null && !profileName.isBlank()) {
                    return profileName.trim();
                }
            }
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.debug("Could not read the current player profile name", exception);
        }

        try {
            Session session = client.getSession();
            if (session != null) {
                String sessionName = session.getUsername();
                if (sessionName != null && !sessionName.isBlank()) {
                    return sessionName.trim();
                }
            }
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.debug("Could not read the Minecraft session username", exception);
        }

        return "";
    }
}

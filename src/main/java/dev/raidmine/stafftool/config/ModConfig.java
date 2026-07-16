package dev.raidmine.stafftool.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.raidmine.stafftool.RaidMineStaffMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("rm_tools");
    private static final Path PATH = DIRECTORY.resolve("settings.json");
    private static final List<Path> LEGACY_PATHS = List.of(
            FabricLoader.getInstance().getConfigDir().resolve("rm-tools.json"),
            FabricLoader.getInstance().getConfigDir().resolve("raidmine-staff.json")
    );

    public boolean hudEnabled = true;
    public float hudX = 0.5F;
    public float hudY = 0.015F;
    /** Legacy uniform scale retained for migration only. */
    public float hudScale = 0.92F;
    public float hudWidthScale = 0.88F;
    public float hudHeightScale = 1.00F;
    public boolean hudOutlineEnabled = false;
    public boolean hudBorderRunnersEnabled = true;
    public float hudOutlineOpacity = 1.00F;
    public float uiOutlineOpacity = 1.00F;
    public boolean requireConfirmation = true;
    public boolean restrictToRaidMine = true;
    public List<String> allowedAddressFragments = new ArrayList<>(List.of("raidmine"));

    public int accentColor = 0xFFFF8A00;
    public int accentColorSecondary = 0xFFFF4D3A;
    public String fontFamily = "AUTO";
    public float uiBackgroundOpacity = 0.92F;
    public boolean autoScreenshot = true;
    public boolean mentionNotifications = true;
    public boolean forbiddenWordAlerts = true;
    public boolean showHintsPanel = true;
    public String punishmentReasonTemplate = "{rule}";
    public List<String> forbiddenWords = new ArrayList<>();

    public int dailyOnlineGoalMinutes = 120;
    public boolean afkKickEnabled = false;
    public int afkKickMinSeconds = 240;
    public int afkKickMaxSeconds = 240;
    public int afkOnlinePauseSeconds = 60;

    public String warnCommand = "warn {player} {reason}";
    public String muteCommand = "mute {player} {duration} {reason}";
    public String banCommand = "ban {player} {duration} {reason}";
    public String permanentBanCommand = "ban {player} permanent {reason}";
    public String kickCommand = "kick {player} {reason}";
    public String staffChatCommand = "/sc {message}";
    public String vanishCommand = "/v";
    public List<String> vanishCommandAliases = new ArrayList<>(List.of("v", "vanish", "cmi:v"));
    public String hubCommand = "/hub";

    public List<String> activeServerKeywords = new ArrayList<>(List.of(
            "duels", "anarchy", "grief", "survival", "skyblock", "pvp", "mines", "event"
    ));
    public List<String> pausedServerKeywords = new ArrayList<>(List.of(
            "hub", "lobby", "limbo", "auth"
    ));

    public Map<String, String> staffCredentials = new LinkedHashMap<>(Map.of(
            "NAU1ZER", "111",
            "xzktoV2_1", "111"
    ));

    public static Path directory() {
        return DIRECTORY;
    }

    public static ModConfig load() {
        Path source = Files.exists(PATH) ? PATH : firstExistingLegacy();
        ModConfig loaded;
        if (source == null) {
            loaded = new ModConfig();
        } else {
            try (Reader reader = Files.newBufferedReader(source)) {
                loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded == null) loaded = new ModConfig();
            } catch (Exception exception) {
                RaidMineStaffMod.LOGGER.error("Could not read {}. Defaults will be used.", source, exception);
                loaded = new ModConfig();
            }
        }
        loaded.normalize();
        loaded.forbiddenWords = ForbiddenWordsStore.loadOrCreate(loaded.forbiddenWords);
        loaded.save();
        return loaded;
    }

    private static Path firstExistingLegacy() {
        for (Path legacy : LEGACY_PATHS) {
            if (Files.exists(legacy)) return legacy;
        }
        return null;
    }

    public void save() {
        normalize();
        try {
            Files.createDirectories(DIRECTORY);
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
            ForbiddenWordsStore.save(forbiddenWords);
        } catch (IOException exception) {
            RaidMineStaffMod.LOGGER.error("Could not save {}", PATH, exception);
        }
    }

    public String formatReason(String ruleCode) {
        String rule = ruleCode == null || ruleCode.isBlank() ? "RULE" : ruleCode.trim();
        String template = punishmentReasonTemplate == null || punishmentReasonTemplate.isBlank()
                ? "{rule}" : punishmentReasonTemplate.trim();
        String formatted = template.replace("{rule}", rule).trim();
        return formatted.isBlank() ? rule : formatted;
    }

    public void setAccent(int primary, int secondary) {
        accentColor = 0xFF000000 | (primary & 0x00FFFFFF);
        accentColorSecondary = 0xFF000000 | (secondary & 0x00FFFFFF);
        save();
    }

    private void normalize() {
        hudX = clamp(hudX, 0F, 1F, 0.5F);
        hudY = clamp(hudY, 0F, 1F, 0.015F);
        hudScale = clamp(hudScale, 0.70F, 1.80F, 0.92F);
        if (hudWidthScale <= 0F) hudWidthScale = hudScale;
        if (hudHeightScale <= 0F) hudHeightScale = hudScale;
        hudWidthScale = clamp(hudWidthScale, 0.76F, 1.80F, 0.88F);
        hudHeightScale = clamp(hudHeightScale, 0.72F, 1.80F, 1.00F);
        uiBackgroundOpacity = clamp(uiBackgroundOpacity, 0F, 1F, 0.92F);
        uiOutlineOpacity = clamp(uiOutlineOpacity, 0F, 1F, 1F);
        hudOutlineOpacity = clamp(hudOutlineOpacity, 0F, 1F, 1F);
        accentColor = 0xFF000000 | (accentColor & 0x00FFFFFF);
        accentColorSecondary = 0xFF000000 | (accentColorSecondary & 0x00FFFFFF);
        punishmentReasonTemplate = fallback(punishmentReasonTemplate, "{rule}");
        fontFamily = fallback(fontFamily, "AUTO");
        dailyOnlineGoalMinutes = Math.max(15, Math.min(720, dailyOnlineGoalMinutes));
        // RaidMine removes 15 minutes at the server-side 5 minute AFK kick.
        // RM Tools always transfers to hub one minute earlier.
        afkKickMinSeconds = 240;
        afkKickMaxSeconds = 240;
        afkOnlinePauseSeconds = Math.max(30, Math.min(180, afkOnlinePauseSeconds));

        if (allowedAddressFragments == null || allowedAddressFragments.isEmpty()) {
            allowedAddressFragments = new ArrayList<>(List.of("raidmine"));
        }
        if (forbiddenWords == null) forbiddenWords = new ArrayList<>();
        if (vanishCommandAliases == null || vanishCommandAliases.isEmpty()) {
            vanishCommandAliases = new ArrayList<>(List.of("v", "vanish", "cmi:v"));
        }
        if (activeServerKeywords == null || activeServerKeywords.isEmpty()) {
            activeServerKeywords = new ArrayList<>(List.of("duels", "anarchy", "grief"));
        }
        if (pausedServerKeywords == null || pausedServerKeywords.isEmpty()) {
            pausedServerKeywords = new ArrayList<>(List.of("hub", "lobby", "limbo"));
        }
        if (staffCredentials == null || staffCredentials.isEmpty()) {
            staffCredentials = new LinkedHashMap<>();
            staffCredentials.put("NAU1ZER", "111");
            staffCredentials.put("xzktoV2_1", "111");
        }

        forbiddenWords = normalizeList(forbiddenWords);
        activeServerKeywords = normalizeList(activeServerKeywords);
        pausedServerKeywords = normalizeList(pausedServerKeywords);
        vanishCommandAliases = normalizeList(vanishCommandAliases);
        staffCredentials = normalizeMap(staffCredentials);

        warnCommand = fallback(warnCommand, "warn {player} {reason}");
        muteCommand = fallback(muteCommand, "mute {player} {duration} {reason}");
        banCommand = fallback(banCommand, "ban {player} {duration} {reason}");
        permanentBanCommand = fallback(permanentBanCommand, "ban {player} permanent {reason}");
        kickCommand = fallback(kickCommand, "kick {player} {reason}");
        staffChatCommand = fallback(staffChatCommand, "/sc {message}");
        vanishCommand = fallback(vanishCommand, "/v");
        hubCommand = fallback(hubCommand, "/hub");
    }

    private static Map<String, String> normalizeMap(Map<String, String> raw) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!key.isBlank() && !value.isBlank()) normalized.put(key, value);
        }
        return normalized;
    }

    private static ArrayList<String> normalizeList(List<String> source) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String word : source) {
            if (word != null && !word.isBlank()) unique.add(word.trim().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(unique);
    }

    private static float clamp(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

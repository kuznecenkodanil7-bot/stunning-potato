package dev.raidmine.stafftool.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.raidmine.stafftool.RaidMineStaffMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class DailyStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("rm_tools")
            .resolve("daily_state.json");
    private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("rm-tools")
            .resolve("daily-state.json");

    private DailyStateStore() {
    }

    static State load() {
        migrateLegacy();
        if (!Files.exists(FILE)) return new State();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            State state = GSON.fromJson(reader, State.class);
            return state == null ? new State() : state;
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.error("Could not read daily RM Tools state", exception);
            return new State();
        }
    }

    static void save(State state) {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(state, writer);
            }
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.error("Could not save daily RM Tools state", exception);
        }
    }

    private static void migrateLegacy() {
        try {
            if (!Files.exists(FILE) && Files.exists(LEGACY_FILE)) {
                Files.createDirectories(FILE.getParent());
                Files.copy(LEGACY_FILE, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.warn("Could not migrate legacy daily state", exception);
        }
    }

    static final class State {
        String moscowDate = "";
        long activeMillis;
        int bans;
        int mutes;
        int warns;
        boolean goalNoticeShown;
    }
}

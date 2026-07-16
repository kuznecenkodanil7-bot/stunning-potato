package dev.raidmine.stafftool.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.raidmine.stafftool.RaidMineStaffMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ForbiddenWordsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<String>>() {}.getType();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("rm_tools");
    private static final Path FILE = DIRECTORY.resolve("forbidden_words.json");
    private static final Path LEGACY_DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("rm-tools");
    private static final Path LEGACY_FILE = LEGACY_DIRECTORY.resolve("forbidden-words.json");

    private ForbiddenWordsStore() {
    }

    public static Path directory() {
        return DIRECTORY;
    }

    public static Path file() {
        return FILE;
    }

    public static List<String> loadOrCreate(List<String> fallback) {
        try {
            Files.createDirectories(DIRECTORY);
            migrateLegacyFile();
            if (!Files.exists(FILE)) {
                List<String> normalized = normalize(fallback);
                save(normalized);
                return normalized;
            }
            try (Reader reader = Files.newBufferedReader(FILE)) {
                List<String> loaded = GSON.fromJson(reader, LIST_TYPE);
                return normalize(loaded);
            }
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.error("Could not read forbidden words from {}", FILE, exception);
            return normalize(fallback);
        }
    }

    public static void save(List<String> words) {
        try {
            Files.createDirectories(DIRECTORY);
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(normalize(words), LIST_TYPE, writer);
            }
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.error("Could not save forbidden words to {}", FILE, exception);
        }
    }

    private static void migrateLegacyFile() {
        try {
            if (!Files.exists(FILE) && Files.exists(LEGACY_FILE)) {
                Files.copy(LEGACY_FILE, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.warn("Could not migrate legacy forbidden words file", exception);
        }
    }

    private static List<String> normalize(List<String> source) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (source != null) {
            for (String word : source) {
                if (word != null && !word.isBlank()) {
                    unique.add(word.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(unique);
    }
}

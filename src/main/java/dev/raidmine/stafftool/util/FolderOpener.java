package dev.raidmine.stafftool.util;

import dev.raidmine.stafftool.RaidMineStaffMod;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class FolderOpener {
    private FolderOpener() {
    }

    public static Result open(Path directory) {
        if (directory == null) return new Result(false, "Папка не указана");
        try {
            Files.createDirectories(directory);
            Path absolute = directory.toAbsolutePath().normalize();
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder builder;
            if (os.contains("win")) {
                builder = new ProcessBuilder("explorer.exe", absolute.toString());
            } else if (os.contains("mac")) {
                builder = new ProcessBuilder("open", absolute.toString());
            } else {
                builder = new ProcessBuilder("xdg-open", absolute.toString());
            }
            builder.redirectErrorStream(true);
            builder.start();
            return new Result(true, "Папка открыта: " + absolute.getFileName());
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.warn("Could not open folder through OS command", exception);
            try {
                String path = directory.toAbsolutePath().normalize().toString();
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.keyboard != null) {
                    client.keyboard.setClipboard(path);
                    return new Result(false, "Путь скопирован: " + path);
                }
            } catch (Exception ignored) {
            }
            return new Result(false, "Папка: " + directory.toAbsolutePath().normalize());
        }
    }

    public record Result(boolean opened, String message) {
    }
}

package dev.raidmine.stafftool;

import dev.raidmine.stafftool.config.ModConfig;
import dev.raidmine.stafftool.stats.SessionStats;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RaidMineStaffMod implements ClientModInitializer {
    public static final String MOD_ID = "raidmine_staff";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModConfig config;
    private static SessionStats stats;

    @Override
    public void onInitializeClient() {
        config = ModConfig.load();
        stats = new SessionStats();
        LOGGER.info("RM Tools initialized");
    }

    public static ModConfig config() {
        return config;
    }

    public static SessionStats stats() {
        return stats;
    }
}

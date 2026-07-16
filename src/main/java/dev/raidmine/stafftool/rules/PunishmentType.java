package dev.raidmine.stafftool.rules;

public enum PunishmentType {
    WARN("Варн"),
    MUTE("Мут"),
    BAN("Бан"),
    PERMANENT_BAN("Бан навсегда"),
    KICK("Кик");

    private final String displayName;

    PunishmentType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

package dev.raidmine.stafftool.rules;

public record PunishmentOption(
        PunishmentType type,
        String duration,
        String label,
        String reason
) {
    public PunishmentOption {
        duration = duration == null ? "" : duration;
        label = label == null ? type.displayName() : label;
        reason = reason == null ? "" : reason;
    }
}

package dev.raidmine.stafftool.rules;

import java.util.List;

public record RuleEntry(
        String code,
        String title,
        String description,
        RuleCategory category,
        List<PunishmentOption> options
) {
    public RuleEntry {
        options = List.copyOf(options);
    }
}

package dev.raidmine.stafftool.rules;

public enum RuleCategory {
    CHAT("Общение", "2.x"),
    GAMEPLAY("Игровой процесс", "3.x"),
    DONATER("Донатеры", "4.x"),
    QUICK("Быстрые действия", "⚡");

    private final String title;
    private final String badge;

    RuleCategory(String title, String badge) {
        this.title = title;
        this.badge = badge;
    }

    public String title() {
        return title;
    }

    public String badge() {
        return badge;
    }
}

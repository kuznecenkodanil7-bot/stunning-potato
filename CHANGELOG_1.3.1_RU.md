# RM Tools 1.3.1 — исправление сборки

Исправлена ошибка Minecraft 1.21.11 Yarn:

- удалён прямой вызов `ClientPlayerEntity#getScoreboard()`;
- scoreboard теперь безопасно определяется через reflection, сначала из `ClientWorld`;
- добавлен fallback для совместимости с другими mappings;
- версия проекта повышена до 1.3.1.

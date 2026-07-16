# Сборка версии 1.1.0 для Minecraft 1.21.11

Проект использует актуальные сигнатуры Yarn 1.21.11:

- `Screen.mouseClicked(Click, boolean)`;
- `Screen.keyPressed(KeyInput)`;
- `Screen.charTyped(CharInput)`;
- `Screen.mouseDragged(Click, double, double)`;
- `InGameHud.render(DrawContext, RenderTickCounter)`;
- `Keyboard.onKey(long, int, KeyInput)`.

Сборка в GitHub Actions:

```text
gradle --no-daemon clean build
```

Готовый JAR находится в артефакте `raidmine-staff-tool`.

В текущей рабочей среде Gradle CLI отсутствует, поэтому окончательную компиляцию выполняет включённый workflow GitHub Actions. Исходники проверены на синтаксис Java 21, JSON-файлы валидируются перед упаковкой.

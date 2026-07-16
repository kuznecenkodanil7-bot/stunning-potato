package dev.raidmine.stafftool.util;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.chat.UiNotificationCenter;
import dev.raidmine.stafftool.ui.PunishmentScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Captures evidence only after an expanded chat frame is actually visible. */
public final class ScreenshotService {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final long OPEN_DELAY_MILLIS = 220L;
    private static final long AUTHOR_WAIT_MILLIS = 2600L;
    private static final long CAPTURE_AFTER_READY_MILLIS = 120L;
    private static Pending pending;

    private ScreenshotService() { }

    public static void requestAfterChatRender(String player, String reason, Screen chatScreen) {
        if (!RaidMineStaffMod.config().autoScreenshot) return;
        ChatRenderTracker.invalidate();
        pending = new Pending(player, reason, evidenceScreen(chatScreen),
                System.currentTimeMillis(), false, 0, 0L);
    }

    /** Keeps SmartChat and its selected tab when punishment was opened from it. */
    public static Screen evidenceScreen(Screen preferred) {
        if (ChatRenderTracker.isChatLikeScreen(preferred)) return preferred;
        return new ChatScreen("", false);
    }

    /** Called after the complete ChatScreen render. It only arms the capture. */
    public static void afterChatRendered() {
        Pending request = pending;
        if (request == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Screen current = client.currentScreen;
        if (!ChatRenderTracker.isChatLikeScreen(current) || current instanceof PunishmentScreen) return;

        int frames = request.renderedFrames() + 1;
        long age = System.currentTimeMillis() - request.requestedAt();
        boolean fresh = ChatRenderTracker.hasFreshFrame(current)
                && ChatRenderTracker.hasFrameAfter(current, request.requestedAt());
        boolean authorVisible = fresh && ChatRenderTracker.hasAuthor(current, request.player());
        long readyAt = request.readyAt();
        if (frames >= 3 && fresh && (authorVisible || age >= AUTHOR_WAIT_MILLIS)) {
            readyAt = readyAt == 0L ? System.currentTimeMillis() : readyAt;
        }
        pending = request.withState(true, frames, readyAt);
    }

    /** Runs on the client tick after the rendered chat frame has reached the framebuffer. */
    public static void tick(MinecraftClient client) {
        Pending request = pending;
        if (request == null || client == null || client.currentScreen instanceof PunishmentScreen) return;
        long now = System.currentTimeMillis();
        Screen current = client.currentScreen;

        if (!ChatRenderTracker.isChatLikeScreen(current)) {
            if (!request.chatOpened() && now - request.requestedAt() >= OPEN_DELAY_MILLIS) {
                Screen target = request.chatScreen() == null ? new ChatScreen("", false) : request.chatScreen();
                ChatRenderTracker.invalidate();
                pending = request.withState(true, 0, 0L);
                client.setScreen(target);
            }
            return;
        }

        boolean fresh = ChatRenderTracker.hasFreshFrame(current)
                && ChatRenderTracker.hasFrameAfter(current, request.requestedAt());
        boolean authorVisible = fresh && ChatRenderTracker.hasAuthor(current, request.player());
        long age = now - request.requestedAt();
        int frames = request.renderedFrames();
        if (fresh) {
            frames++;
            request = request.withState(true, frames, request.readyAt());
            pending = request;
        }
        long readyAt = request.readyAt();
        if (readyAt == 0L && fresh && frames >= 3
                && (authorVisible || age >= AUTHOR_WAIT_MILLIS)) {
            readyAt = now;
            request = request.withState(true, frames, readyAt);
            pending = request;
            return;
        }
        if (readyAt == 0L || now - readyAt < CAPTURE_AFTER_READY_MILLIS || !fresh) return;

        pending = null;
        captureNow(request.player(), request.reason());
    }

    public static void cancel() { pending = null; }

    private static void captureNow(String player, String reason) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ChatRenderTracker.isChatLikeScreen(client.currentScreen)) return;
        String safePlayer = sanitize(player, "player");
        String safeReason = sanitize(reason, "rule");
        String fileName = safePlayer + "_" + safeReason + "_" + FORMAT.format(LocalDateTime.now()) + ".png";
        try {
            ScreenshotRecorder.saveScreenshot(
                    client.runDirectory, fileName, client.getFramebuffer(), 1,
                    text -> client.execute(() -> UiNotificationCenter.info("Скриншот сохранён", fileName))
            );
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.error("Could not capture moderation screenshot", exception);
            UiNotificationCenter.info("Ошибка скриншота",
                    exception.getMessage() == null ? "Не удалось сохранить" : exception.getMessage());
        }
    }

    private static String sanitize(String value, String fallback) {
        String safe = value == null ? "" : value.trim().replaceAll("[^A-Za-zА-Яа-я0-9_.-]+", "_");
        safe = safe.replaceAll("_+", "_");
        if (safe.length() > 48) safe = safe.substring(0, 48);
        return safe.isBlank() ? fallback : safe;
    }

    private record Pending(String player, String reason, Screen chatScreen,
                           long requestedAt, boolean chatOpened,
                           int renderedFrames, long readyAt) {
        Pending withState(boolean opened, int frames, long ready) {
            return new Pending(player, reason, chatScreen, requestedAt, opened, frames, ready);
        }
    }
}

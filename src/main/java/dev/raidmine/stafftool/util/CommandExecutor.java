package dev.raidmine.stafftool.util;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.config.ModConfig;
import dev.raidmine.stafftool.chat.ChatEventGate;
import dev.raidmine.stafftool.rules.PunishmentOption;
import net.minecraft.client.MinecraftClient;

public final class CommandExecutor {
    private CommandExecutor() {
    }

    public static Result execute(String player, String ruleCode, PunishmentOption option) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!AuthManager.canUseMod()) {
            return Result.error("Требуется авторизация RM Tools");
        }
        if (client.player == null || client.getNetworkHandler() == null) {
            return Result.error("Нет подключения к серверу");
        }
        if (!NicknameResolver.isValid(player)) {
            return Result.error("Некорректный ник: " + player);
        }
        if (!ServerGuard.isAllowed(client)) {
            return Result.error("Команды заблокированы вне RaidMine: " + ServerGuard.currentAddress(client));
        }

        ModConfig config = RaidMineStaffMod.config();
        String template = switch (option.type()) {
            case WARN -> config.warnCommand;
            case MUTE -> config.muteCommand;
            case BAN -> config.banCommand;
            case PERMANENT_BAN -> config.permanentBanCommand;
            case KICK -> config.kickCommand;
        };

        String reason = config.formatReason(ruleCode);
        String command = template
                .replace("{player}", player)
                .replace("{duration}", option.duration())
                .replace("{reason}", reason)
                .replaceAll("\\s+", " ")
                .trim();
        if (command.startsWith("/")) command = command.substring(1);

        try {
            ChatEventGate.suppressMentions(3500L);
            client.player.networkHandler.sendChatCommand(command);
            return Result.success("Наказание отправлено • " + player + " • " + reason);
        } catch (Exception exception) {
            RaidMineStaffMod.LOGGER.error("Could not send punishment command", exception);
            return Result.error("Не удалось отправить команду: " + exception.getMessage());
        }
    }

    public record Result(boolean success, String message) {
        public static Result success(String message) { return new Result(true, message); }
        public static Result error(String message) { return new Result(false, message); }
    }
}

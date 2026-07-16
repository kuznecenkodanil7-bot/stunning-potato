package dev.raidmine.stafftool.mixin;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.chat.ChatTextProcessor;
import dev.raidmine.stafftool.util.AuthManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tracks commands sent by SmartChat and inspects non-player-formatted chat packets. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "sendChatCommand(Ljava/lang/String;)V", at = @At("HEAD"))
    private void rmtools$observeOutgoingCommand(String command, CallbackInfo ci) {
        if (!AuthManager.canUseMod() || command == null || command.isBlank()) return;
        RaidMineStaffMod.stats().observeManualCommand(command.startsWith("/") ? command : "/" + command);
    }

    @Inject(method = "onProfilelessChatMessage(Lnet/minecraft/network/packet/s2c/play/ProfilelessChatMessageS2CPacket;)V",
            at = @At("HEAD"), require = 0)
    private void rmtools$inspectProfilelessMessage(ProfilelessChatMessageS2CPacket packet, CallbackInfo ci) {
        if (!AuthManager.canUseMod() || packet == null || packet.message() == null) return;
        ChatTextProcessor.inspectRenderedLine(packet.message().getString());
    }

    @Inject(method = "onGameMessage(Lnet/minecraft/network/packet/s2c/play/GameMessageS2CPacket;)V",
            at = @At("HEAD"), require = 0)
    private void rmtools$inspectGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (!AuthManager.canUseMod() || packet == null || packet.content() == null) return;
        ChatTextProcessor.inspectRenderedLine(packet.content().getString());
    }
}

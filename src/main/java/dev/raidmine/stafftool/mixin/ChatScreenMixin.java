package dev.raidmine.stafftool.mixin;

import dev.raidmine.stafftool.ui.PunishmentScreen;
import dev.raidmine.stafftool.util.AuthManager;
import dev.raidmine.stafftool.util.ChatSelectionHelper;
import dev.raidmine.stafftool.util.ChatRenderTracker;
import dev.raidmine.stafftool.util.NicknameResolver;
import dev.raidmine.stafftool.util.ScreenshotService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void rmtools$openFromNickname(Click click, boolean doubled,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!AuthManager.canUseMod() || click.button() != 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Screen current = (Screen) (Object) this;
        Optional<ChatSelectionHelper.Hit> hit = ChatRenderTracker.hasFreshFrame(current)
                ? ChatRenderTracker.find(current, click.x(), click.y())
                : Optional.empty();
        if (hit.isEmpty()) hit = ChatSelectionHelper.find(client, click.x(), click.y());
        if (hit.isEmpty()) return;

        client.setScreen(new PunishmentScreen(current, hit.get().nickname()));
        cir.setReturnValue(true);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD")
    )
    private void rmtools$beforeChatRendered(DrawContext context, int mouseX, int mouseY,
                                             float delta, CallbackInfo ci) {
        ChatRenderTracker.begin((Screen) (Object) this);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("TAIL")
    )
    private void rmtools$afterChatRendered(DrawContext context, int mouseX, int mouseY,
                                            float delta, CallbackInfo ci) {
        Screen current = (Screen) (Object) this;
        ChatRenderTracker.finish(current);
        ScreenshotService.afterChatRendered();
    }

    @Inject(
            method = "sendMessage(Ljava/lang/String;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void rmtools$manualOpen(String message, boolean addToHistory, CallbackInfo ci) {
        if (!AuthManager.canUseMod()) return;
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.toLowerCase().startsWith("/rmp ")) {
            String nickname = trimmed.substring(5).trim();
            if (!NicknameResolver.isValid(nickname)) return;

            MinecraftClient client = MinecraftClient.getInstance();
            Screen parent = (Screen) (Object) this;
            client.setScreen(new PunishmentScreen(parent, nickname));
            ci.cancel();
            return;
        }
    }
}

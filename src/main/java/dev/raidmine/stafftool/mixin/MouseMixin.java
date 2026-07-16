package dev.raidmine.stafftool.mixin;

import dev.raidmine.stafftool.ui.PunishmentScreen;
import dev.raidmine.stafftool.util.AuthManager;
import dev.raidmine.stafftool.util.ChatRenderTracker;
import dev.raidmine.stafftool.util.ChatSelectionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private double x;
    @Shadow private double y;

    @Inject(
            method = "onMouseButton(JLnet/minecraft/client/input/MouseInput;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void rmtools$smartChatNicknameClick(long windowHandle, MouseInput input, int action, CallbackInfo ci) {
        if (!AuthManager.canUseMod() || action != InputUtil.GLFW_PRESS || input.button() != 0) return;
        Window window = client.getWindow();
        if (windowHandle != window.getHandle()) return;
        Screen current = client.currentScreen;
        if (current == null) return;
        if (!ChatRenderTracker.isChatLikeScreen(current)) return;

        double mouseX = Mouse.scaleX(window, x);
        double mouseY = Mouse.scaleY(window, y);
        Optional<ChatSelectionHelper.Hit> hit = ChatRenderTracker.hasFreshFrame(current)
                ? ChatRenderTracker.find(current, mouseX, mouseY)
                : Optional.empty();
        if (hit.isEmpty()) hit = ChatSelectionHelper.find(client, mouseX, mouseY);
        if (hit.isEmpty()) return;
        client.setScreen(new PunishmentScreen(current, hit.get().nickname()));
        ci.cancel();
    }
}

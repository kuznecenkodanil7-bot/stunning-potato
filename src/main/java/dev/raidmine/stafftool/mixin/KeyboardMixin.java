package dev.raidmine.stafftool.mixin;

import dev.raidmine.stafftool.ui.HudEditorScreen;
import dev.raidmine.stafftool.util.AfkKickManager;
import dev.raidmine.stafftool.util.AuthManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(
            method = "onKey(JILnet/minecraft/client/input/KeyInput;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void rmtools$onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle()) return;
        if (action == GLFW.GLFW_PRESS) AfkKickManager.markActivity();
        if (action == GLFW.GLFW_PRESS
                && input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT
                && client.currentScreen == null
                && client.player != null
                && AuthManager.canUseMod()) {
            client.setScreen(new HudEditorScreen());
            ci.cancel();
        }
    }
}

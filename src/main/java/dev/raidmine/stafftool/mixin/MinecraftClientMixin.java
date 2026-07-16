package dev.raidmine.stafftool.mixin;

import dev.raidmine.stafftool.RaidMineStaffMod;
import dev.raidmine.stafftool.ui.LoginScreen;
import dev.raidmine.stafftool.util.AfkKickManager;
import dev.raidmine.stafftool.util.AuthManager;
import dev.raidmine.stafftool.util.ScreenshotService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow public Screen currentScreen;

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void rmtools$tick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (AuthManager.needsLogin(client)) {
            if (!(currentScreen instanceof LoginScreen)) {
                client.setScreen(new LoginScreen(currentScreen));
            }
            return;
        }
        if (AuthManager.canUseMod()) {
            AfkKickManager.tick(client);
            RaidMineStaffMod.stats().tick(client);
            ScreenshotService.tick(client);
        }
    }
}

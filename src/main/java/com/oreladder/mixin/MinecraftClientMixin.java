package com.oreladder.mixin;

import com.oreladder.ClientState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    // Ограничиваем частоту кадров значением, которое прислал сервер.
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void oreladder$limitFps(CallbackInfoReturnable<Integer> cir) {
        int cap = ClientState.fpsCap;
        if (cap > 0) {
            cir.setReturnValue(Math.min(cir.getReturnValue(), cap));
        }
    }
}

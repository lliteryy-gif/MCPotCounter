package ru.litery.potioncounter.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.litery.potioncounter.PotionCounter;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class NetworkHandlerMixin {
    // TAIL executes after vanilla's main-thread check and metadata application.
    // This catches even a projectile spawned and destroyed between client ticks.
    @Inject(method = "onEntityTrackerUpdate", at = @At("TAIL"))
    private void potioncounter$metadata(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        PotionCounter.observeId(packet.id());
    }
}

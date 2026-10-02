package ru.litery.potioncounter.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.litery.potioncounter.PotionCounter;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "getAndUpdateRenderState", at = @At("RETURN"))
    private void potioncounter$name(
            Entity entity,
            float tickProgress,
            CallbackInfoReturnable<EntityRenderState> cir
    ) {
        EntityRenderState state = cir.getReturnValue();

        if (entity instanceof PlayerEntity player
                && state != null
                && state.displayName != null) {
            state.displayName =
                    PotionCounter.decorate(player, state.displayName);
        }
    }
}

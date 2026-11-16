package xixi.interaction.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xixi.interaction.InteractionMod;

@Mixin(value=LivingEntityRenderer.class,remap=false)
abstract class LapHeadRenderMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
    private void xixi$resetLapHead(LivingEntity entity,LivingEntityRenderState state,float partialTick,CallbackInfo callback){
        var minecraft=Minecraft.getInstance();
        var server=minecraft.getSingleplayerServer();
        if(server==null||entity.level()!=minecraft.level)return;
        Float lapYaw=InteractionMod.lapBodyYaw(server,entity.getUUID());
        if(lapYaw!=null){
            state.bodyRot=lapYaw;
            state.xRot=0;
            state.yRot=0;
        }else if(InteractionMod.resettingHead(server,entity.getUUID())){
            state.xRot=0;
            state.yRot=0;
        }
    }
}

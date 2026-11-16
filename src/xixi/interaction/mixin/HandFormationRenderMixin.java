package xixi.interaction.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xixi.interaction.InteractionMod;

@Mixin(value=EntityRenderer.class,remap=false)
abstract class HandFormationRenderMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",at=@At("TAIL"))
    private void xixi$applyFormation(Entity entity,EntityRenderState state,float partialTick,CallbackInfo callback){
        var minecraft=Minecraft.getInstance();
        var server=minecraft.getSingleplayerServer();
        if(minecraft.level==null||server==null||entity.level()!=minecraft.level)return;
        var formation=InteractionMod.handRender(server,entity.getUUID());
        if(formation==null)return;
        var owner=minecraft.level.getPlayerByUUID(formation.owner());
        if(owner==null||entity.distanceToSqr(owner)>16)return;
        var position=owner.getPosition(partialTick);
        float yaw=owner.getViewYRot(partialTick);
        double[] target=formation.layout().at(position.x,position.z,yaw);
        state.x=target[0];
        state.y=position.y;
        state.z=target[1];
    }
}

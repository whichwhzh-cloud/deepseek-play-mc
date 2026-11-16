package xixi.interaction.mixin;

import com.elfmcys.yesstevemodel.OoOo0OOOoo0o0oOOoooooo0O;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xixi.interaction.InteractionMod;
import xixi.interaction.YsmExpressions;

@Mixin(targets="com.elfmcys.yesstevemodel.OoO0oo0o00o0oO000000o0OO",remap=false)
abstract class YsmFaceRenderMixin {
    // The body submit already applied the real YSM rotations and scale to this stack.
    @Inject(method="OO0OoO00ooOOo0o00O000OoO(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
        +"Lcom/elfmcys/yesstevemodel/OoOo0OOOoo0o0oOOoooooo0O;Lcom/mojang/blaze3d/vertex/PoseStack;"
        +"Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at=@At(value="INVOKE",shift=At.Shift.AFTER,
            target="Lcom/elfmcys/yesstevemodel/OoO0oo0o00o0oO000000o0OO;OO0OoO00ooOOo0o00O000OoO("
                +"Lcom/elfmcys/yesstevemodel/oo0oo0O0O0oo00OO00000Oo0;Lcom/elfmcys/yesstevemodel/O0o0O0O00ooo0ooo000o0ooo;"
                +"Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                +"Lnet/minecraft/client/renderer/rendertype/RenderType;)V"))
    private void xixi$submitBlush(LivingEntityRenderState state,OoOo0OOOoo0o0oOOoooooo0O<?> evaluated,
                                   PoseStack stack,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo ci){
        if(!(state instanceof AvatarRenderState avatar)||state.isInvisible)return;
        var mc=Minecraft.getInstance();
        var server=mc.getSingleplayerServer();
        if(server==null||mc.level==null)return;
        var entity=mc.level.getEntity(avatar.id);
        if(entity!=null&&InteractionMod.hasDsAppearance(server,entity.getUUID()))
            YsmExpressions.applyFace(server,entity.getUUID(),evaluated.oOo0O0Oooo0o0Oo0O0O00Ooo(),stack,collector);
    }
}

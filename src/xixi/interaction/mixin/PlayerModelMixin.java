package xixi.interaction.mixin;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xixi.interaction.*;
@Mixin(value=HumanoidModel.class,remap=false)
public abstract class PlayerModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",at=@At("TAIL"),require=1)
    private void xixi$pose(HumanoidRenderState raw,CallbackInfo ci){
        if(!(raw instanceof AvatarRenderState state))return;
        var model=(HumanoidModel<?>)(Object)this;
        var visual=InteractionClient.visual(state.id);
        if(visual!=null){
            VanillaPoses.apply(model,state,visual);
            return;
        }
        var minecraft=Minecraft.getInstance();
        var server=minecraft.getSingleplayerServer();
        if(minecraft.level==null||server==null)return;
        var entity=minecraft.level.getEntity(state.id);
        if(entity==null||InteractionMod.hasDsAppearance(server,entity.getUUID()))return;
        var rain=CompanionAmbient.rain(server,entity.getUUID());
        if(rain==null)return;
        boolean left=rain.arm()==HumanoidArm.LEFT;
        var arm=left?model.leftArm:model.rightArm;
        arm.xRot=-2.1f;
        arm.zRot=left?-.1f:.1f;
    }
}

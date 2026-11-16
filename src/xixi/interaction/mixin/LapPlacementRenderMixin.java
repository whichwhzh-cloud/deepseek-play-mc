package xixi.interaction.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xixi.interaction.InteractionMod;
import xixi.interaction.InteractionItems;
import xixi.interaction.InteractionRules;
import xixi.interaction.CompanionAmbient;
import xixi.interaction.NativeActions;
import xixi.interaction.YsmDsArmPose;
import xixi.interaction.YsmExpressions;
import net.minecraft.world.phys.Vec3;

@Mixin(value=EntityRenderDispatcher.class,remap=false)
abstract class LapPlacementRenderMixin {
    // After the dispatcher has pushed and positioned this entity's pose stack,
    // before either the vanilla or YSM renderer submits its model.
    @Inject(method="submit",at=@At(value="INVOKE",
        target="Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",ordinal=0,shift=At.Shift.AFTER))
    private void xixi$placeLap(EntityRenderState raw,CameraRenderState camera,double x,double y,double z,
                               PoseStack stack,SubmitNodeCollector collector,CallbackInfo callback){
        if(!(raw instanceof AvatarRenderState state))return;
        var minecraft=Minecraft.getInstance();
        if(minecraft.level==null||minecraft.getSingleplayerServer()==null)return;
        var lying=minecraft.level.getEntity(state.id);
        if(lying==null)return;
        var server=minecraft.getSingleplayerServer();
        var ds=InteractionMod.dsRender(server,lying.getUUID());
        var rain=ds==null?CompanionAmbient.rain(server,lying.getUUID()):null;
        if(ds==null)ds=CompanionAmbient.sleep(server,lying.getUUID());
        if(ds==null&&rain!=null)ds=rain.animation();
        if(ds==null)ds=NativeActions.angerRender(server,lying.getUUID());
        if(ds!=null&&ds.pose().equals("carry")){
            var carrier=minecraft.level.getPlayerByUUID(ds.owner());
            if(carrier!=null){
                Vec3 base=carrier.getPosition(state.partialTick);
                float yaw=Mth.rotLerp(state.partialTick,carrier.yBodyRotO,carrier.yBodyRot);
                var seat=InteractionRules.BACK_SEAT.at(base.x,base.z,yaw);
                stack.translate(seat[0]-state.x,base.y+InteractionRules.BACK_SEAT_HEIGHT-state.y,seat[1]-state.z);
            }
        }
        if(ds!=null&&YsmDsArmPose.apply(server,lying,state,ds))
            stack.translate(0,-InteractionMod.DS_SEAT_DROP,0);
        var lap=InteractionMod.lapRender(server,lying.getUUID());
        var seated=lap==null?null:minecraft.level.getPlayerByUUID(lap.seated());
        Vec3 lapHead=seated==null||seated.distanceToSqr(lying)>9?null:
            InteractionMod.lapHead(lap,seated.getPosition(state.partialTick));
        boolean hasExpressions=InteractionMod.hasDsAppearance(server,lying.getUUID());
        if(hasExpressions){
            Vec3 anchor=lapHead==null?new Vec3(0,YsmExpressions.headHeight(),0):lapHead.subtract(state.x,state.y,state.z);
            YsmExpressions.submitSticker(server,lying.getUUID(),anchor,state.bodyRot,camera,stack,collector);
        }
        if(rain!=null&&rain.drawProp()&&lying instanceof LivingEntity actor)
            xixi$umbrella(minecraft,state,actor,rain.arm(),stack,collector);
        if(ds!=null&&(ds.pose().equals("sleep")||ds.pose().equals("sleep_idle"))){
            double yaw=Math.toRadians(ds.yaw());
            double breath=ds.pose().equals("sleep_idle")?.012*Math.sin(state.ageInTicks*.14):0;
            stack.translate(0,.25+breath,0);
            stack.mulPose(new Quaternionf().rotationAxis((float)(Math.PI/2),
                -(float)Math.cos(yaw),0,-(float)Math.sin(yaw)));
        }
        if(lapHead!=null){
            // Tilt the entire render around the lying actor's head pivot.
            double yaw=Math.toRadians(lap.formationYaw());
            float sign=lap.ownerLying()?1:-1;
            float dx=sign*(float)Math.cos(yaw), dz=sign*(float)Math.sin(yaw);
            var tilt=new Quaternionf().rotationAxis((float)Math.toRadians(82),dz,0,-dx);
            var rotatedHead=tilt.transform(new Vector3f(0,lap.headHeight(),0));
            stack.translate(lapHead.x-state.x-rotatedHead.x,lapHead.y-state.y-rotatedHead.y,lapHead.z-state.z-rotatedHead.z);
            stack.mulPose(tilt);
        }
    }

    private static void xixi$umbrella(Minecraft mc,AvatarRenderState state,LivingEntity actor,HumanoidArm arm,
                                      PoseStack stack,SubmitNodeCollector collector){
        boolean left=arm==HumanoidArm.LEFT;
        var item=new ItemStackRenderState();
        mc.getItemModelResolver().updateForLiving(item,new ItemStack(InteractionItems.UMBRELLA.get()),
            left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,actor);
        var holding=new InteractionRules.Layout(left?.31:-.31,.24,0).at(0,0,state.bodyRot);
        stack.pushPose();
        try{
            stack.translate(holding[0],1.28,holding[1]);
            stack.mulPose(new Quaternionf().rotationY(-state.bodyRot*Mth.DEG_TO_RAD));
            item.submit(stack,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        }finally{stack.popPose();}
    }
}

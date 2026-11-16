package xixi.interaction.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xixi.interaction.InteractionMod;

@Mixin(value=Camera.class,remap=false)
abstract class InteractionCameraMixin {
    @Shadow public abstract Vec3 position();
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw,float pitch,float roll);
    @Shadow public abstract float xRot();
    @Shadow public abstract float yRot();
    @Shadow public abstract float getRoll();
    private double xixi$eyeOffset;
    private float xixi$pitchOffset;
    private float xixi$rollOffset;
    private float xixi$yawOffset;
    private boolean xixi$wasLap;

    @Inject(method="alignWithEntity(F)V",at=@At("TAIL"))
    private void xixi$adjustEye(float partialTick,CallbackInfo callback){
        Minecraft minecraft=Minecraft.getInstance();
        var player=minecraft.player;
        var server=minecraft.getSingleplayerServer();
        if(player==null||server==null||minecraft.getCameraEntity()!=player||!minecraft.options.getCameraType().isFirstPerson()){
            xixi$eyeOffset=0;
            xixi$pitchOffset=0;
            xixi$rollOffset=0;
            xixi$yawOffset=0;
            xixi$wasLap=false;
            return;
        }
        String action=InteractionMod.cameraAction(server,player.getUUID());
        boolean lap=action.equals("player_lap");
        if(xixi$wasLap&&!lap){
            xixi$pitchOffset=0;
            xixi$rollOffset=0;
            xixi$yawOffset=0;
        }
        xixi$wasLap=lap;
        double target=switch(action){
            case "sit","lean","maid_lap","brush" -> -.63;
            case "player_lap" -> -.94;
            case "hug","back_hug" -> -.14;
            default -> 0;
        };
        xixi$eyeOffset+=(target-xixi$eyeOffset)*.25;
        if(Math.abs(target-xixi$eyeOffset)<.002)xixi$eyeOffset=target;
        setPosition(position().add(0,xixi$eyeOffset,0));
        float pitch=switch(action){
            case "player_lap" -> -12;
            case "brush" -> 10;
            default -> 0;
        };
        float roll=action.equals("player_lap")?-12:0;
        float yaw=0;
        if(lap){
            var partnerId=InteractionMod.cameraPartner(server,player.getUUID());
            var partner=partnerId==null||minecraft.level==null?null:minecraft.level.getPlayerByUUID(partnerId);
            if(partner!=null){
                var face=partner.position().add(0,1.0,0);
                var delta=face.subtract(position());
                if(delta.horizontalDistanceSqr()>.0025){
                    float facing=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
                    yaw=net.minecraft.util.Mth.wrapDegrees(facing-yRot());
                    pitch=(float)Math.toDegrees(-Math.atan2(delta.y,delta.horizontalDistance()))-xRot();
                }
            }
        }
        xixi$yawOffset+=net.minecraft.util.Mth.wrapDegrees(yaw-xixi$yawOffset)*.15f;
        xixi$pitchOffset+=(pitch-xixi$pitchOffset)*.19f;
        xixi$rollOffset+=(roll-xixi$rollOffset)*.19f;
        if(Math.abs(xixi$yawOffset)>.01f||Math.abs(xixi$pitchOffset)>.01f||Math.abs(xixi$rollOffset)>.01f)
            setRotation(yRot()+xixi$yawOffset,Math.clamp(xRot()+xixi$pitchOffset,-89f,89f),getRoll()+xixi$rollOffset);
    }
}

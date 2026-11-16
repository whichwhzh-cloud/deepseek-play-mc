package xixi.interaction.mixin;

import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.pathing.body.Aim;
import com.dwinovo.numen.pathing.body.Controls;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="com.dwinovo.numen.pathing.drive.Steering",remap=false)
abstract class NumenSteeringMixin {
    @Inject(method="pass",at=@At("HEAD"))
    private static void xixi$faceSwimWaypoint(ServerPlayer player,Controls controls,double x,double z,CallbackInfo callback){
        xixi$swimAim(player,x,z);
    }

    @Inject(method="stop",at=@At("HEAD"))
    private static void xixi$faceSwimStop(ServerPlayer player,Controls controls,double x,double z,double feetY,CallbackInfoReturnable<Boolean> callback){
        xixi$swimAim(player,x,z);
    }

    @Unique
    private static void xixi$swimAim(ServerPlayer player,double x,double z){
        if(!(player instanceof NumenPlayer)||!player.isInWater()||player.onGround())return;
        double dx=x-player.getX();
        double dz=z-player.getZ();
        // The stock steering only turns on the ground. Keep swim pitch and vertical controls intact.
        if(dx*dx+dz*dz>=.05*.05){
            float pitch=player.getXRot();
            Aim.faceToward(player,x,z);
            player.setXRot(pitch);
        }
    }
}

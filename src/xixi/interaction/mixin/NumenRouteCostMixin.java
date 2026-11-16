package xixi.interaction.mixin;

import com.dwinovo.numen.pathing.plan.CostModel;
import com.dwinovo.numen.pathing.plan.Maneuver;
import com.dwinovo.numen.pathing.plan.MoveKind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=CostModel.class,remap=false)
abstract class NumenRouteCostMixin {
    @Inject(method="overhead",at=@At("RETURN"),cancellable=true)
    private void xixi$preferStableFooting(Maneuver step,CallbackInfoReturnable<Double> callback){
        double extra=0;
        if(step.kind()==MoveKind.DIAGONAL){
            double heightChange=Math.abs(step.start().feetY()-step.landing().feetY());
            extra=heightChange>.125||step.jump()?14:2;
        }else if(step.kind()==MoveKind.SWIM&&step.submerged()){
            extra=4;
        }
        // A penalty leaves corner steps and dives available when they are the only route.
        if(extra>0)callback.setReturnValue(callback.getReturnValue()+extra);
    }
}

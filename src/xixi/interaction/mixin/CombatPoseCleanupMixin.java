package xixi.interaction.mixin;

import com.dwinovo.numen.core.task.base.AbstractCompanionTask;
import com.dwinovo.numen.core.task.combat.AttackCompanionTask;
import com.dwinovo.numen.core.task.combat.AttackTaskRecord;
import com.dwinovo.numen.entity.NumenPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the last facing direction, but release the vertical aim and align head and torso. */
@Mixin(value=AttackCompanionTask.class,remap=false)
abstract class CombatPoseCleanupMixin extends AbstractCompanionTask<AttackTaskRecord> {
    protected CombatPoseCleanupMixin(NumenPlayer player,AttackTaskRecord record){
        super(player,record);
    }

    @Inject(method="cleanup()V",at=@At("TAIL"))
    private void xixi$releaseCombatAim(CallbackInfo callback){
        float facing=player.getYRot();
        player.setXRot(0);
        player.xRotO=0;
        player.setYBodyRot(facing);
        player.yBodyRotO=facing;
        player.setYHeadRot(facing);
        player.yHeadRotO=facing;
    }
}

package xixi.interaction.mixin;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.Task;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xixi.interaction.CreativeFlight;
import xixi.interaction.InteractionMod;

/** Hold physical tasks and let Numen finish processing and shipping results. */
@Mixin(targets="com.dwinovo.numen.task.CompanionBrain",remap=false)
public abstract class CompanionBrainMixin {
    @Shadow @Final private List<Task> idlePoses;

    // Local 2 is the result of TaskSelector.select in Numen 0.1.4.1.
    @ModifyVariable(method="tick(Lcom/dwinovo/numen/entity/NumenPlayer;)V",at=@At(value="STORE",ordinal=0),index=2)
    private Task xixi$holdPhysicalTask(Task selected,NumenPlayer body){
        boolean working=selected!=null&&!idlePoses.contains(selected);
        InteractionMod.observeBodyTask(body,working);
        if(working)CreativeFlight.beforeGroundTask(body,selected);
        if(!InteractionMod.holds(body))return selected;
        body.controls().stop();
        return null;
    }
}

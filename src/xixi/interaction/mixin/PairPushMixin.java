package xixi.interaction.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xixi.interaction.InteractionMod;

@Mixin(value=Entity.class,remap=false)
abstract class PairPushMixin {
    @Inject(method="push(Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true)
    private void xixi$preventPairPush(Entity other,CallbackInfo callback){
        if(InteractionMod.suppressPairPush((Entity)(Object)this,other))callback.cancel();
    }
}

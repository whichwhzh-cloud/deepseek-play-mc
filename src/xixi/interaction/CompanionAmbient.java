package xixi.interaction;

import com.dwinovo.numen.entity.NumenPlayer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;

public final class CompanionAmbient {
    public record Umbrella(InteractionMod.DsRender animation,HumanoidArm arm,boolean drawProp) {}
    private record Rest(InteractionMod.DsRender animation,Vec3 origin,BlockPos bed) {}
    private static final Map<UUID,Rest> RESTS=new HashMap<>();
    private static volatile Map<UUID,Rest> restViews=Map.of();
    private static volatile Map<UUID,Umbrella> umbrellas=Map.of();
    private static volatile MinecraftServer activeServer;

    private CompanionAmbient(){}

    static void tick(MinecraftServer server,InteractionConfig config){
        if(config==null)return;
        int tick=server.getTickCount();
        if(tick%5!=0&&RESTS.isEmpty())return;
        var away=new HashSet<>(RESTS.keySet());
        Map<UUID,Umbrella> rain=tick%5==0?new HashMap<>():null;
        for(var player:server.getPlayerList().getPlayers()){
            if(!(player instanceof NumenPlayer companion))continue;
            var id=companion.getUUID();
            var owner=companion.resolveOwnerPlayer();
            away.remove(id);
            if(rain==null&&!RESTS.containsKey(id))continue;
            boolean allowed=!InteractionMod.occupied(companion)&&InteractionMod.ambientSafe(companion)
                &&InteractionMod.hasAmbientAppearance(companion);
            var old=RESTS.get(id);
            var bed=owner!=null&&owner.isSleeping()?owner.getSleepingPos().orElse(null):null;
            boolean rest=allowed&&config.enableSleepIdle&&bed!=null&&!owner.hasDisconnected()
                &&owner.level()==companion.level()&&owner.hurtTime==0&&companion.onGround()
                &&!companion.isUsingItem()&&!InteractionMod.ambientBusy(companion);
            if(old!=null&&(!rest||!old.bed().equals(bed)||old.origin().distanceToSqr(companion.position())>.12)){
                RESTS.remove(id);
            }else if(rest&&old==null){
                var placed=prepareRest(server,owner,companion,bed);
                if(placed!=null)RESTS.put(id,placed);
            }
            if(rain!=null&&allowed&&config.enableRainUmbrella&&!RESTS.containsKey(id)&&!NativeActions.playing(id)
                &&companion.level().isRainingAt(BlockPos.containing(companion.getX(),companion.getEyeY(),companion.getZ()))){
                var open=umbrella(companion,tick);
                if(open!=null)rain.put(id,open);
            }
        }
        away.forEach(RESTS::remove);
        restViews=Map.copyOf(RESTS);
        if(rain!=null)umbrellas=Map.copyOf(rain);
        activeServer=server;
    }

    private static Rest prepareRest(MinecraftServer server,ServerPlayer owner,NumenPlayer companion,BlockPos bed){
        var facing=owner.level().getBlockState(bed).getOptionalValue(BedBlock.FACING).orElse(null);
        if(facing==null||owner.distanceToSqr(companion)>9)return null;
        var foot=Vec3.atBottomCenterOf(bed.relative(facing.getOpposite()));
        float yaw=facing.getOpposite().toYRot();
        for(double side:new double[]{1.18,-1.18}){
            double[] xy=new InteractionRules.Layout(side,0,0).at(foot.x,foot.z,yaw);
            var origin=new Vec3(xy[0],foot.y,xy[1]);
            var towardHead=new Vec3(facing.getStepX(),0,facing.getStepZ());
            if(companion.position().distanceToSqr(origin)>7.56||!InteractionMod.pathClear(companion,origin)
                ||!InteractionMod.clear(companion,origin.add(towardHead.scale(.7)))
                ||!InteractionMod.clear(companion,origin.add(towardHead.scale(1.4))))continue;
            NativeActions.stopPlayback(companion);
            InteractionMod.move(companion,origin,yaw);
            return new Rest(new InteractionMod.DsRender("sleep_idle",server.getTickCount(),yaw,owner.getUUID()),origin,bed);
        }
        return null;
    }

    private static Umbrella umbrella(NumenPlayer companion,int tick){
        if(companion.isUsingItem()||companion.swinging)return null;
        var main=companion.getMainHandItem();
        var off=companion.getOffhandItem();
        var item=InteractionItems.UMBRELLA.get();
        boolean heldMain=main.is(item),heldOff=off.is(item);
        boolean drawProp=!heldMain&&!heldOff;
        if(drawProp&&!main.isEmpty()&&!off.isEmpty())return null;
        boolean offhand=heldOff||(!heldMain&&off.isEmpty());
        HumanoidArm arm=offhand?companion.getMainArm().getOpposite():companion.getMainArm();
        String pose=arm==HumanoidArm.LEFT?"rain_umbrella_left":"rain_umbrella_right";
        return new Umbrella(new InteractionMod.DsRender(pose,tick,companion.getYRot(),companion.getOwnerUuid()),arm,drawProp);
    }

    static boolean resting(NumenPlayer companion){return RESTS.containsKey(companion.getUUID());}

    public static InteractionMod.DsRender sleep(MinecraftServer server,UUID companion){
        var rest=server==activeServer?restViews.get(companion):null;
        return rest==null?null:rest.animation();
    }

    public static Umbrella rain(MinecraftServer server,UUID companion){
        return server==activeServer?umbrellas.get(companion):null;
    }

    static void suppress(UUID id){
        if(RESTS.remove(id)!=null)restViews=Map.copyOf(RESTS);
        if(umbrellas.containsKey(id)){
            var rain=new HashMap<>(umbrellas);
            rain.remove(id);
            umbrellas=Map.copyOf(rain);
        }
    }

    static void clear(){
        RESTS.clear();
        restViews=Map.of();
        umbrellas=Map.of();
        activeServer=null;
    }
}

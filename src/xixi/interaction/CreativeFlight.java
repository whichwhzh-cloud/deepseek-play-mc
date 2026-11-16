package xixi.interaction;

import com.dwinovo.numen.api.NumenApi;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.pathing.body.Aim;
import com.dwinovo.numen.sdk.Fn;
import com.dwinovo.numen.sdk.Job;
import com.dwinovo.numen.sdk.ServerCall;
import com.dwinovo.numen.task.CompanionTickDispatcher;
import com.dwinovo.numen.task.Task;
import com.dwinovo.numen.task.TaskFactory;
import com.dwinovo.numen.task.TaskRecord;
import com.dwinovo.numen.task.TaskResult;
import com.dwinovo.numen.task.TaskState;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CreativeFlight {
    private static final double REACH=.07;
    private static final double SPEED=.30;
    private static final double RANGE=48;
    private static final int TIMEOUT=250;

    public record Destination(double x,double y,double z) {}
    public record Outcome(boolean reached,String message) {}

    private static final class FlightRecord extends TaskRecord {
        final Vec3 target;
        final boolean landing;

        FlightRecord(ServerCall call,Vec3 target,boolean landing){
            super(call,call.her().level().getGameTime()+TIMEOUT);
            this.target=target;
            this.landing=landing;
        }
    }

    private static final class State {
        boolean managing;
    }

    public static void register(NumenApi api){
        TaskFactory.register(FlightRecord.class,Pilot::new);
        api.api("flight","Creative only. Use takeoff(), to(x,y,z), land(), stop(), or status(). Ordinary numen.move.to walks on terrain.",CreativeFlight.class);
    }

    @Fn("Rise three blocks in creative mode, then hover. Requires clear air above and the companion's own Creative flight permission.")
    public static Job<Outcome> takeoff(ServerCall call){
        return schedule(call,call.her().position().add(0,3,0),false);
    }

    @Fn("Fly straight to exact x, y, z feet coordinates in creative mode, up to 48 blocks in loaded chunks. Waits for arrival. Use separate waypoints to go around obstacles.")
    public static Job<Outcome> to(ServerCall call,Destination point){
        return schedule(call,new Vec3(point.x(),point.y(),point.z()),false);
    }

    @Fn("Land vertically on the nearest clear surface below, within 48 blocks. Then resume ordinary ground movement.")
    public static Job<Outcome> land(ServerCall call){
        var body=call.her();
        String reason=unavailable(body);
        if(reason!=null)return Job.done(new Outcome(false,reason));
        if(body.onGround()&&!body.getAbilities().flying)
            return Job.done(new Outcome(true,"已经在地面上"));
        var column=body.getBoundingBox().expandTowards(0,-RANGE,0);
        double surface=Double.NEGATIVE_INFINITY;
        for(var shape:body.level().getBlockCollisions(body,column)){
            double y=shape.max(Direction.Axis.Y);
            if(y<=body.getY()+.015)surface=Math.max(surface,y);
        }
        if(!Double.isFinite(surface))return Job.done(new Outcome(false,"正下方 48 格内没有可降落的地面"));
        return schedule(call,new Vec3(body.getX(),surface,body.getZ()),true);
    }

    @Fn("Cancel the current creative flight route. If airborne, hover in place. Use land() to descend.")
    public static boolean stop(ServerCall call){
        var body=call.her();
        if(!(CompanionTickDispatcher.currentTaskFor(body.getUUID()) instanceof FlightRecord))return false;
        return CompanionTickDispatcher.stopActive(body,TaskRecord.StopCause.OWNER)!=null;
    }

    @Fn("Read the companion's creative flight permission, flying state, and current feet coordinates.")
    public static String status(ServerCall call){
        var body=call.her();
        String mode=body.getAbilities().flying?"飞行中":permitted(body)?"可以起飞":"未开启创造飞行权限";
        return String.format(Locale.ROOT,"%s，脚下坐标 %.2f, %.2f, %.2f",mode,body.getX(),body.getY(),body.getZ());
    }

    static boolean permitted(NumenPlayer body){
        return body.isCreative()&&body.getAbilities().mayfly;
    }

    private static Job<Outcome> schedule(ServerCall call,Vec3 target,boolean landing){
        var body=call.her();
        String reason=unavailable(body);
        if(reason!=null)return Job.done(new Outcome(false,reason));
        if(!validDestination(body.position(),target))
            return Job.done(new Outcome(false,"请使用 48 格内有效的飞行坐标"));
        if(!clear(body,body.getBoundingBox().move(target.subtract(body.position()))))
            return Job.done(new Outcome(false,"飞行目标有障碍或不在已加载区域"));
        return Job.of(new FlightRecord(call,target,landing));
    }

    private static String unavailable(NumenPlayer body){
        if(!permitted(body))return "需要先把伙伴本人切换到创造模式";
        var owner=body.resolveOwnerPlayer();
        if(!body.isAlive()||body.hasDisconnected()||owner==null||owner.hasDisconnected()||owner.level()!=body.level())
            return "伙伴与玩家须在同一个维度且保持在线";
        if(body.isPassenger()||body.isVehicle()||body.isSleeping()||InteractionMod.occupied(body)||CompanionAmbient.resting(body))
            return "请先结束骑乘、休息或双人互动";
        return null;
    }

    static boolean validDestination(Vec3 from,Vec3 target){
        return Double.isFinite(target.x)&&Double.isFinite(target.y)&&Double.isFinite(target.z)
            &&from.distanceToSqr(target)<=RANGE*RANGE;
    }

    static Vec3 velocity(Vec3 delta){
        double distance=delta.length();
        // Reduce speed near the destination without ever passing through it.
        return distance<=REACH?Vec3.ZERO:delta.scale(Math.min(SPEED/distance,.55));
    }

    private static boolean clear(NumenPlayer body,AABB box){
        var level=body.level();
        if(box.minY<level.getMinY()||box.maxY>level.getMaxY()+1||!level.getWorldBorder().isWithinBounds(box))return false;
        for(double x:new double[]{box.minX,box.maxX})
            for(double z:new double[]{box.minZ,box.maxZ})
                if(!level.hasChunkAt(BlockPos.containing(x,box.minY,z)))return false;
        return level.noBlockCollision(body,box);
    }

    private static void setFlight(NumenPlayer body,boolean flying){
        if(body.getAbilities().flying==flying)return;
        body.getAbilities().flying=flying;
        body.onUpdateAbilities();
    }

    public static void beforeGroundTask(NumenPlayer body,Task selected){
        if(selected instanceof Pilot||!permitted(body)||!body.getAbilities().flying)return;
        var state=body.state(State.class,State::new);
        if(!state.managing)return;
        state.managing=false;
        setFlight(body,false);
        body.setDeltaMovement(Vec3.ZERO);
    }

    private static final class Pilot implements Task {
        private final FlightRecord record;
        private Vec3 lastPosition;
        private int stalled;
        private boolean started;
        private TaskResult result;

        Pilot(NumenPlayer body,FlightRecord record){
            this.record=record;
        }

        @Override public String name(){return "creative flight";}

        @Override public TaskState tick(NumenPlayer body){
            String reason=unavailable(body);
            if(reason!=null)return finish(body,false,reason);
            Vec3 position=body.position();
            Vec3 delta=record.target.subtract(position);
            if(delta.lengthSqr()<=REACH*REACH)
                return finish(body,true,record.landing?"已降落":"已飞到目标");
            Vec3 step=velocity(delta);
            if(!clear(body,body.getBoundingBox().expandTowards(step)))
                return finish(body,false,"飞行路线被挡住，请换一个中途点");
            stalled=lastPosition!=null&&lastPosition.distanceToSqr(position)<.0004?stalled+1:0;
            if(stalled>=12)return finish(body,false,"飞行没有进展，请换一个中途点");
            lastPosition=position;
            started=true;
            body.state(State.class,State::new).managing=true;
            body.controls().releaseAll();
            body.setSprinting(false);
            if(delta.horizontalDistanceSqr()>.01)Aim.faceToward(body,record.target.x,record.target.z);
            setFlight(body,true);
            body.setDeltaMovement(step);
            return TaskState.RUNNING;
        }

        private TaskState finish(NumenPlayer body,boolean arrived,String message){
            result=TaskResult.ok(message,new Outcome(arrived,message));
            if(started||arrived&&record.landing)settle(body,arrived&&record.landing);
            return TaskState.SUCCESS;
        }

        @Override public void stop(NumenPlayer body,StopReason reason){
            if(started)settle(body,reason==StopReason.BODY_GONE);
        }

        private void settle(NumenPlayer body,boolean disable){
            body.controls().releaseAll();
            body.setDeltaMovement(Vec3.ZERO);
            if(disable||!permitted(body)||body.onGround()){
                body.state(State.class,State::new).managing=false;
                if(permitted(body))setFlight(body,false);
            }
        }

        @Override public TaskResult result(TaskState state){
            return result==null?Task.super.result(state):result;
        }
    }
}

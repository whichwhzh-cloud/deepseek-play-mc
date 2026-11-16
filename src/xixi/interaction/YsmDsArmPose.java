package xixi.interaction;

import com.elfmcys.yesstevemodel.OOOoooO00oo000OOOOoO0OO0;
import com.elfmcys.yesstevemodel.oooOoOoo000OoOo0OOoO0oO0;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YsmDsArmPose {
    private static final Logger LOG=LoggerFactory.getLogger("xixi-interaction");
    // YSM 2.6.5's render buffer stores 12 native-order floats per bone. The first three are Euler angles.
    private static final int BONE_BYTES=12*Float.BYTES;
    private static final Map<oooOoOoo000OoOo0OOoO0oO0,Rig> RIGS=new WeakHashMap<>();
    private static final Set<String> UNAVAILABLE=new HashSet<>();
    private static final Map<String,Clip> CLIPS=loadClips();

    private record Clip(int frames,Map<String,float[][]> tracks){}
    private record Track(int offset,float[][] frames,boolean head){}
    private record BoundPose(List<Track> tracks,boolean seated){}
    private record Rig(Map<String,Integer> bones,Map<String,BoundPose> poses){}
    private record Look(float pitch,float yaw){}

    private YsmDsArmPose(){}

    private static Map<String,Clip> loadClips(){
        var stream=YsmDsArmPose.class.getResourceAsStream("/assets/xixi_interaction/ds_poses.json");
        if(stream==null)return Map.of();
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            return Map.copyOf(new Gson().fromJson(reader,new TypeToken<Map<String,Clip>>(){}));
        }catch(Exception ex){
            LOG.error("Could not load DS pose data",ex);
            return Map.of();
        }
    }

    private static Look seatedLook(MinecraftServer server,Entity entity,InteractionMod.DsRender pose,float partialTick){
        if(!pose.pose().equals("player_lap"))return null;
        var lap=InteractionMod.lapRender(server,pose.owner());
        if(lap==null||!lap.seated().equals(entity.getUUID()))return null;
        var position=entity.getPosition(partialTick);
        var eyes=InteractionMod.lapHead(lap,position).add(0,.12,0);
        var minecraft=Minecraft.getInstance();
        if(minecraft.player!=null&&pose.owner().equals(minecraft.player.getUUID())
            &&minecraft.getCameraEntity()==minecraft.player&&minecraft.options.getCameraType().isFirstPerson()){
            eyes=minecraft.gameRenderer.getMainCamera().position();
        }
        var face=position.add(0,YsmInteractionModel.DS_PREVIEW.headHeight()-InteractionMod.DS_SEAT_DROP+.10,0);
        var delta=eyes.subtract(face);
        float yaw=Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-delta.x,delta.z))-lap.seatedYaw());
        float pitch=(float)Math.toDegrees(Math.atan2(-delta.y,delta.horizontalDistance()));
        return new Look(Mth.clamp(pitch,-80,80),Mth.clamp(yaw,-105,105));
    }

    public static boolean apply(MinecraftServer server,Entity entity,EntityRenderState state,InteractionMod.DsRender pose){
        var clip=CLIPS.get(pose.pose());
        if(clip==null)return false;
        var evaluated=OOOoooO00oo000OOOOoO0OO0.OO0OoO00ooOOo0o00O000OoO(state);
        var submission=evaluated==null?null:evaluated.oOo0O0Oooo0o0Oo0O0O00Ooo();
        if(submission==null||submission.O000O0000O00000oo0o0OooO==null){
            if(UNAVAILABLE.add(pose.pose()))LOG.warn("DS pose {}: no YSM frame data for entity {}",pose.pose(),entity.getId());
            return false;
        }
        var geometry=submission.o0Oo00O0Ooo00oOo0O000Ooo;
        var source=submission.O000O0000O00000oo0o0OooO;
        if(geometry==null||source.isReadOnly()||source.limit()!=geometry.OO0OoO00ooOOo0o00O000OoO().size()*BONE_BYTES){
            if(UNAVAILABLE.add(pose.pose()))LOG.warn("DS pose {}: unexpected YSM bone buffer length {}",pose.pose(),source.limit());
            return false;
        }
        var rig=RIGS.computeIfAbsent(geometry,YsmDsArmPose::mapRig);
        var bound=rig.poses.computeIfAbsent(pose.pose(),action->bind(rig,action,clip));
        var output=source.duplicate().order(ByteOrder.nativeOrder());
        int frame=Math.floorMod(server.getTickCount()-pose.started(),clip.frames-1);
        float partial=state.partialTick;
        var look=seatedLook(server,entity,pose,partial);
        for(var track:bound.tracks){
            var a=track.frames[frame];
            var b=track.frames[frame+1];
            int offset=track.offset();
            output.putFloat(offset,track.head&&look!=null?-look.pitch*Mth.DEG_TO_RAD:lerp(a[0],b[0],partial));
            output.putFloat(offset+Float.BYTES,track.head&&look!=null?-look.yaw*Mth.DEG_TO_RAD:lerp(a[1],b[1],partial));
            output.putFloat(offset+2*Float.BYTES,track.head&&look!=null?0:lerp(a[2],b[2],partial));
        }
        return bound.seated();
    }

    private static float lerp(float a,float b,float partial){return a+(b-a)*partial;}

    private static Rig mapRig(oooOoOoo000OoOo0OOoO0oO0 geometry){
        var map=new HashMap<String,Integer>();
        var bones=geometry.OO0OoO00ooOOo0o00O000OoO();
        for(int i=0;i<bones.size();i++){
            String name=normalized(bones.get(i).OO0OoO00ooOOo0o00O000OoO());
            if(!name.isEmpty())map.putIfAbsent(name,i*BONE_BYTES);
        }
        return new Rig(map,new HashMap<>());
    }

    private static String normalized(String name){return name==null?"":name.toLowerCase(Locale.ROOT).replaceAll("[ _.-]","");}

    private static BoundPose bind(Rig rig,String action,Clip clip){
        var tracks=new ArrayList<Track>();
        var missing=new ArrayList<String>();
        for(var entry:clip.tracks.entrySet()){
            String name=normalized(entry.getKey());
            var bone=rig.bones.get(name);
            if(bone==null){
                for(String alias:aliases(name)){
                    bone=rig.bones.get(alias);
                    if(bone!=null)break;
                }
            }
            if(bone==null)missing.add(entry.getKey());
            else tracks.add(new Track(bone,entry.getValue(),name.equals("head")));
        }
        LOG.info("DS pose {}: {}/{} bones mapped; missing {}",action,tracks.size(),clip.tracks.size(),missing);
        boolean seated=InteractionRules.SEATED.contains(action)&&!action.equals("maid_lap")
            &&!missing.contains("LeftLeg")&&!missing.contains("RightLeg");
        if((seated&&missing.contains("Head"))||(!seated&&InteractionRules.SEATED.contains(action)&&!action.equals("maid_lap"))
            ||((action.equals("hug")||action.equals("back_hug"))&&missing.stream().anyMatch(name->name.endsWith("Arm")))){
            LOG.info("DS rig names available for {}: {}",action,rig.bones.keySet());
        }
        return new BoundPose(List.copyOf(tracks),seated);
    }

    private static List<String> aliases(String bone){
        return switch(bone){
            case "leftarm" -> List.of("armleft","arml","larm","leftupperarm","upperarml","lupperarm");
            case "rightarm" -> List.of("armright","armr","rarm","rightupperarm","upperarmr","rupperarm");
            case "leftforearm" -> List.of("forearmleft","forearml","lforearm","leftlowerarm","lowerarml","llowerarm");
            case "rightforearm" -> List.of("forearmright","forearmr","rforearm","rightlowerarm","lowerarmr","rlowerarm");
            case "leftleg" -> List.of("legleft","legl","lleg","leftupperleg","upperlegl","leftthigh","thighl");
            case "rightleg" -> List.of("legright","legr","rleg","rightupperleg","upperlegr","rightthigh","thighr");
            case "leftlowerleg" -> List.of("lowerlegl","llowerleg","leftshin","shinl","leftcalf","calfl");
            case "rightlowerleg" -> List.of("lowerlegr","rlowerleg","rightshin","shinr","rightcalf","calfr");
            case "leftfoot" -> List.of("footleft","footl");
            case "rightfoot" -> List.of("footright","footr");
            case "lefthand" -> List.of("handleft","handl");
            case "righthand" -> List.of("handright","handr");
            default -> List.of();
        };
    }
}

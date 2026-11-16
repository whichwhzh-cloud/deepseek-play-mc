package xixi.interaction;

import com.dwinovo.numen.sdk.Fn;
import com.dwinovo.numen.sdk.Example;
import com.dwinovo.numen.sdk.ServerCall;
import com.dwinovo.numen.entity.NumenPlayer;
import com.elfmcys.yesstevemodel.o0oo0000oooOo0ooOOOo0OOo;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Lets Numen use the equipped model's published extra animations. */
public final class NativeActions {
    public record Play(String action,int seconds) {}
    record Action(String id,String label,String animation,int ticks) {}
    private record Catalog(MinecraftServer server,Map<String,Action> actions) {}
    private static volatile Catalog catalog=new Catalog(null,Map.of());
    private static final Map<UUID,Integer> PLAYING=new HashMap<>();
    private static final Map<UUID,InteractionMod.DsRender> ANGER_POSES=new ConcurrentHashMap<>();
    static final String ANGER_POSE="native_angry";

    private NativeActions(){}

    static void updateCatalog(MinecraftServer server,Map<String,Action> actions){
        if(catalog.server()!=server||!catalog.actions().equals(actions)){
            catalog=new Catalog(server,actions);
            if(!actions.isEmpty())org.slf4j.LoggerFactory.getLogger("xixi-interaction")
                .info("DS native actions available: {}",actions.values().stream().map(a->a.id()+" = "+a.label()).toList());
            var angry=actions.get("怒了");
            if(angry!=null)org.slf4j.LoggerFactory.getLogger("xixi-interaction")
                .info("DS anger action playback value: {}",angry.animation());
        }
    }

    static boolean isDance(String value){
        String text=value.toLowerCase(Locale.ROOT);
        return text.contains("跳舞")||text.contains("舞蹈")||text.contains("之舞")||text.endsWith("舞")
            ||text.contains("dance")||text.contains("dancing")||text.contains("ダンス")||text.contains("踊");
    }

    @Fn("List native DS whale model actions, with IDs and menu labels. Use a listed action by ID with actions.play when it fits the current situation. Player interactions use interaction.request.")
    @Example("return xixi_interaction.actions.list()")
    public static List<String> list(ServerCall call){
        return menu(call.her()).values().stream()
            .map(action->action.id()+" · "+action.label()).toList();
    }

    static Map<String,Action> menu(NumenPlayer companion){
        return InteractionMod.hasNativeModel(companion)?available(companion.level().getServer()):Map.of();
    }

    @Fn("Play a native DS action on yourself. Use the exact ID from actions.list. Leave seconds at 0 to use the action's duration, or choose 1 to 30 seconds for a held pose. Call only when it fits, avoid repeating it. This never starts a paired interaction.")
    @Example("xixi_interaction.actions.play(\"点头\", 0)")
    public static String play(ServerCall call,Play request){
        return play(call.her(),request.action(),request.seconds());
    }

    static String play(NumenPlayer companion,String requested,int seconds){
        var server=companion.level().getServer();
        String id=requested==null?"":requested.strip();
        var action=available(server).get(id);
        if(action==null)return "请从 actions.list 选择可用动作。";
        String blocked=InteractionMod.nativeActionBlockReason(companion);
        if(blocked!=null)return blocked;
        var ysm=o0oo0000oooOo0ooOOOo0OOo.O0oo00O0OoooOOOO00ooO000(companion).orElse(null);
        if(ysm==null)return "YSM 外观尚未加载。";
        int ticks=seconds<=0?action.ticks():Math.clamp(seconds,1,30)*20;
        ysm.OO0OoO00ooOOo0o00O000OoO(companion,action.animation());
        UUID idOfCompanion=companion.getUUID();
        int now=server.getTickCount();
        PLAYING.put(idOfCompanion,now+ticks);
        CompanionAmbient.suppress(idOfCompanion);
        if(id.equals("怒了")){
            ANGER_POSES.put(idOfCompanion,new InteractionMod.DsRender(ANGER_POSE,now,companion.getYRot(),companion.getOwnerUuid()));
            return "已触发「怒了」及 DS 生气动作。";
        }
        ANGER_POSES.remove(idOfCompanion);
        return "已提交原生动作："+action.label()+"。";
    }

    @Fn("Stop the native model action you started with actions.play.")
    public static boolean stop(ServerCall call){return stopPlayback(call.her());}

    private static Map<String,Action> available(MinecraftServer server){
        var current=catalog;
        return current.server()==server?current.actions():Map.of();
    }

    public static InteractionMod.DsRender angerRender(MinecraftServer server,UUID companion){
        return catalog.server()==server?ANGER_POSES.get(companion):null;
    }

    static boolean playing(UUID companion){return PLAYING.containsKey(companion);}

    static boolean stopPlayback(ServerPlayer companion){
        ANGER_POSES.remove(companion.getUUID());
        if(PLAYING.remove(companion.getUUID())==null)return false;
        o0oo0000oooOo0ooOOOo0OOo.O0oo00O0OoooOOOO00ooO000(companion)
            .ifPresent(ysm->ysm.OO0OoO00ooOOo0o00O000OoO(companion));
        return true;
    }

    static void tick(MinecraftServer server){
        var active=PLAYING.entrySet().iterator();
        while(active.hasNext()){
            var entry=active.next();
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if(player instanceof com.dwinovo.numen.entity.NumenPlayer companion){
                if(server.getTickCount()<entry.getValue()&&InteractionMod.nativeActionBlockReason(companion)==null)continue;
                o0oo0000oooOo0ooOOOo0OOo.O0oo00O0OoooOOOO00ooO000(companion)
                    .ifPresent(ysm->ysm.OO0OoO00ooOOo0o00O000OoO(companion));
            }
            ANGER_POSES.remove(entry.getKey());
            active.remove();
        }
    }

    static void clear(MinecraftServer server){
        for(UUID id:List.copyOf(PLAYING.keySet())){
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null)stopPlayback(player);
        }
        PLAYING.clear();
        ANGER_POSES.clear();
    }
}

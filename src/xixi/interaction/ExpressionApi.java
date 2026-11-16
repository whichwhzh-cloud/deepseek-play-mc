package xixi.interaction;

import com.dwinovo.numen.sdk.Fn;
import com.dwinovo.numen.sdk.Example;
import com.dwinovo.numen.sdk.ServerCall;
import com.dwinovo.numen.entity.NumenPlayer;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

public final class ExpressionApi {
    public record Reaction(String mood,int seconds) {}
    public record Sticker(int id,int seconds) {}
    public record Blush(int seconds) {}
    record Display(int sticker,boolean blush) {}
    private record Cue(MinecraftServer server,int sticker,int stickerUntil,int blushUntil) {}
    private static final Map<UUID,Cue> CUES=new ConcurrentHashMap<>();
    private static final Map<String,Display> REACTIONS=Map.of(
        "hungry",new Display(1,false),
        "sad",new Display(2,false),
        "affectionate",new Display(3,true),
        "happy",new Display(4,false),
        "surprised",new Display(5,false),
        "cool",new Display(6,false),
        "awkward",new Display(7,true),
        "stunned",new Display(8,false),
        "playful",new Display(9,false));

    private ExpressionApi(){}

    @Fn("Show a brief reaction when it fits your situation. Moods: hungry, sad, affectionate, happy, surprised, cool, awkward, stunned, playful. Affectionate and awkward include a blush when available. Use occasionally. Pass seconds from 1 to 12, default 4.")
    public static String react(ServerCall call,Reaction request){
        var display=request.mood()==null?null:REACTIONS.get(request.mood().strip().toLowerCase(Locale.ROOT));
        if(display==null)return "请选择支持的 mood。";
        return show(call.her(),display.sticker(),display.blush(),request.seconds());
    }

    @Fn("Choose your own sticker any time it fits, including during an interaction. IDs: 1=rice, 2=pleading, 3=heart, 4=dinner, 5=wide eyes, 6=talisman, 7=sweat, 8=stunned, 9=clown. 0 hides your sticker. Seconds: 1 to 12, default 4. You may replace or hide it early.")
    @Example("xixi_interaction.expression.sticker(3, 4)")
    public static String sticker(ServerCall call,Sticker request){
        return show(call.her(),request.id(),false,request.seconds());
    }

    @Fn("Blush briefly without changing your sticker. Use 1 to 12 seconds, default 4.")
    @Example("xixi_interaction.expression.blush(4)")
    public static String blush(ServerCall call,Blush request){return show(call.her(),null,true,request.seconds());}

    @Fn("Remove your own sticker and blush immediately.")
    public static boolean dismiss(ServerCall call){return CUES.remove(call.her().getUUID())!=null;}

    @Fn("Read your current automatic sticker ID and blush status.")
    public static String status(ServerCall call){
        var companion=call.her();
        var server=companion.level().getServer();
        var display=InteractionMod.allowsAutoExpression(server,companion.getUUID())?current(server,companion.getUUID()):null;
        return display==null?"当前没有自主表情。":"贴纸 "+display.sticker()+"，脸红 "+(display.blush()?"开启":"关闭")+"。";
    }

    static String show(NumenPlayer companion,Integer sticker,boolean blush,int seconds){
        if(sticker!=null&&(sticker<0||sticker>9))return "表情包编号必须是 0 到 9。";
        var server=companion.level().getServer();
        UUID id=companion.getUUID();
        int now=server.getTickCount();
        int duration=Math.clamp(seconds==0?4:seconds,1,12);
        int until=now+duration*20;
        if(sticker!=null&&sticker==0){
            clearSticker(id);
            return "已收起自主贴纸。";
        }
        if(!InteractionMod.hasDsAppearance(server,id))return "迁移的表情需要 DS 鲸鱼娘外观。";
        if(!InteractionMod.allowsAutoExpression(server,id))return "玩家已关闭自主表情。";
        var config=FMLPaths.CONFIGDIR.get();
        if(!Files.isRegularFile(config.resolve("xixi-sticker-atlas.png")))return "缺少本机表情贴图。";
        boolean hasBlush=blush&&Files.isRegularFile(config.resolve("xixi-face-accessories.json"));
        if(sticker==null&&!hasBlush)return "缺少本机脸红资源。";
        CUES.compute(id,(key,before)->{
            var active=before!=null&&before.server()==server?before:new Cue(server,0,now,now);
            return new Cue(server,sticker==null?active.sticker():sticker,sticker==null?active.stickerUntil():until,
                hasBlush?until:active.blushUntil());
        });
        return "已提交"+(sticker==null?"脸红":hasBlush?"贴纸和脸红":"贴纸")+"，持续 "+duration+" 秒。";
    }

    static void clearSticker(UUID companion){
        CUES.computeIfPresent(companion,(id,before)->new Cue(before.server(),0,0,before.blushUntil()));
    }

    static Display current(MinecraftServer server,UUID companion){
        Cue cue=CUES.get(companion);
        if(cue==null||cue.server()!=server)return null;
        int now=server.getTickCount();
        if(now>=cue.stickerUntil()&&now>=cue.blushUntil()){
            CUES.remove(companion,cue);
            return null;
        }
        return new Display(now<cue.stickerUntil()?cue.sticker():0,now<cue.blushUntil());
    }

    static void clear(){CUES.clear();}
}

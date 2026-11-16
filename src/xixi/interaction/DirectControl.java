package xixi.interaction;

import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.sdk.Fn;
import com.dwinovo.numen.sdk.ServerCall;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

/** Explicit owner requests share the same playback and expression checks as Numen. */
public final class DirectControl {
    private static final String HELP="汐的功能：/xixi emote 列出动作，/xixi emote 鸭子坐 播放，/xixi sticker 3 显示爱心，/xixi sticker 0 收起，/xixi blush 脸红。";
    private static final String SKIP="XIXI_SKIP";
    private static final Map<String,Integer> STICKERS=Map.ofEntries(
        Map.entry("吃白饭",1),Map.entry("拜托",2),Map.entry("爱心",3),Map.entry("晚饭",4),Map.entry("睁大眼",5),
        Map.entry("符纸",6),Map.entry("流汗",7),Map.entry("发呆",8),Map.entry("小丑",9));
    private static final Pattern STICKER=Pattern.compile("(?:(?:给我)?(?:显示|发(?:个|一张)?|用(?:个|一张)?|放(?:个|一张)?|来(?:个|一张)?))\\s*(吃白饭|拜托|爱心|晚饭|睁大眼|符纸|流汗|发呆|小丑)(?:贴纸|表情包)?(?:\\s*([0-9]{1,2})\\s*秒)?");
    private static final Pattern ACTION=Pattern.compile("(?:做(?:个|一下|一遍)?|播放|表演|来(?:个|一下|一遍)?|试(?:试|一下)?|给我(?:做个)?)?\\s*(.+?)(?:\\s*(?:一下|一遍|看看))?");

    private DirectControl(){}

    public record Request(String text) {}

    @Fn("Execute a short explicit owner request for an xixi sticker or named DS model animation. Returns XIXI_SKIP for ordinary conversation. Examples: 显示爱心 8 秒, 收起贴纸, 做个鸭子坐, 列出原生动作.")
    public static String chat(ServerCall call,Request request){
        var reply=natural(call.her(),request.text(),true);
        return reply==null?SKIP:reply;
    }

    static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("xixi")
            .requires(source->source.getEntity() instanceof ServerPlayer)
            .executes(context->reply(HELP,context.getSource()))
            .then(Commands.argument("options",StringArgumentType.greedyString()).executes(context->{
                var source=context.getSource();
                var player=source.getPlayerOrException();
                String options=StringArgumentType.getString(context,"options");
                var companion=companion(player);
                String result=companion==null?"当前没有在线、归你所有的汐。":command(companion,options);
                return reply(result,source);
            })));
    }

    private static int reply(String result,net.minecraft.commands.CommandSourceStack source){
        source.sendSuccess(()->Component.literal(result),false);
        return 1;
    }

    static void onChat(ServerChatEvent event){
        if(event.getPlayer() instanceof NumenPlayer)return;
        var companion=companion(event.getPlayer());
        if(companion==null)return;
        String result=natural(companion,event.getRawText(),false);
        if(result!=null){
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(Component.literal(result));
        }
    }

    private static NumenPlayer companion(ServerPlayer sender){
        if(sender instanceof NumenPlayer n)return n;
        for(var player:sender.level().getServer().getPlayerList().getPlayers()){
            if(player instanceof NumenPlayer n&&n.isOwnedByPlayer(sender.getUUID())
                &&n.getScoreboardName().equalsIgnoreCase("xixi")&&!n.isRemoved())return n;
        }
        return null;
    }

    private static String command(NumenPlayer companion,String options){
        var words=options.strip().split("\\s+",2);
        String value=words.length==2?words[1].strip():"";
        return switch(words[0]){
            case "help","" -> HELP;
            case "list","actions" -> list(companion);
            case "emote","action" -> value.isEmpty()?list(companion):NativeActions.play(companion,value,0);
            case "sticker","贴纸","表情" -> {
                if(value.isEmpty())yield "贴纸：1 吃白饭、2 拜托、3 爱心、4 晚饭、5 睁大眼、6 符纸、7 流汗、8 发呆、9 小丑、0 收起。";
                var parts=value.split("\\s+",2);
                Integer id=parts[0].matches("[0-9]")?Integer.valueOf(parts[0]):STICKERS.get(parts[0]);
                int seconds=parts.length==2&&parts[1].matches("[0-9]{1,2}")?Integer.parseInt(parts[1]):8;
                yield id==null?"请输入贴纸编号或名称。":ExpressionApi.show(companion,id,false,seconds);
            }
            case "blush" -> ExpressionApi.show(companion,null,true,5);
            case "stop" -> NativeActions.stopPlayback(companion)?"已结束原生动作。":"当前没有通过汐的接口播放的原生动作。";
            default -> {
                var result=natural(companion,options,true);
                yield result==null?HELP:result;
            }
        };
    }

    private static String natural(NumenPlayer companion,String raw,boolean direct){
        if(raw==null||raw.isBlank()||raw.length()>96)return null;
        String text=raw.strip().replaceAll("[。！!?？]+$","").strip();
        boolean addressed=text.matches("^(?:汐|小汐|xixi)(?:[ ，,、：:]|$).*");
        if(addressed)text=text.replaceFirst("^(?:汐|小汐|xixi)[ ，,、：:]*","");
        if(text.startsWith("/xixi "))return command(companion,text.substring(6));
        text=text.replaceFirst("^(?:请|麻烦你)\\s*","").replaceFirst("(?:吧|好吗)$","").strip();
        if(text.matches("(?:查(?:一下)?|列出|查看|说说|看看)(?:(?:你的?|全部|自带的?|模型|原生|额外|可用|单人|有什么|能做的)\\s*){0,6}动作"))return list(companion);
        if(text.matches("(?:关闭|收起|取消)(?:一下)?(?:贴纸|表情包|爱心)"))return ExpressionApi.show(companion,0,false,4);
        if(text.matches("(?:(?:单独|显示|来个|做个))?脸红(?:一下)?"))return ExpressionApi.show(companion,null,true,5);
        var sticker=STICKER.matcher(text);
        if(sticker.matches())return ExpressionApi.show(companion,STICKERS.get(sticker.group(1)),false,
            sticker.group(2)==null?8:Integer.parseInt(sticker.group(2)));
        var action=ACTION.matcher(text);
        if(!action.matches())return null;
        var menu=NativeActions.menu(companion);
        String id=action.group(1).strip();
        if(!addressed&&!direct&&text.equals(id))return null;
        if(menu.containsKey(id))return NativeActions.play(companion,id,0);
        if(id.equals("招手")&&menu.containsKey("招手1"))return NativeActions.play(companion,"招手1",0);
        return null;
    }

    private static String list(NumenPlayer companion){
        var menu=NativeActions.menu(companion);
        return menu.isEmpty()?"DS 原生动作列表尚未就绪，请确认汐正在用 DS 鲸鱼娘。":
            "汐的 "+menu.size()+" 个原生动作："+String.join("、",menu.keySet())+"。";
    }
}

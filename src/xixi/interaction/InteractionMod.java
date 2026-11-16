package xixi.interaction;

import java.util.*;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.plugins.ysm.Ysm;
import com.dwinovo.numen.task.CompanionTickDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Local integrated-server prototype. All mutations and validation run on its server thread. */
@Mod("xixi_interaction")
public final class InteractionMod {
    public record Action(String id, String label, String emote, boolean seated, String playerEmote) {}
    public static final List<Action> ACTIONS = List.of(
        new Action("sit", "一起坐下", "xixi_test_rest_low1", true, "xixi_player_sit"),
        new Action("lean", "坐着依靠", "xixi_test_sit_lean", true, "xixi_player_sit"),
        new Action("maid_lap", "汐枕在膝上", "xixi_test_maid_lap", true, "xixi_player_maid_lap"),
        new Action("player_lap", "枕在汐膝上", "xixi_test_player_lap", true, "xixi_player_lap"),
        new Action("brush", "梳头（手持刷子）", "xixi_test_brush", true, "xixi_player_brush"),
        new Action("pat", "摸头", "xixi_test_pat", false, "xixi_player_pat"),
        new Action("hug", "拥抱", "xixi_test_hug", false, "xixi_player_hug_test"),
        new Action("hand", "牵手", "xixi_test_hand", false, "xixi_player_hand"),
        new Action("back_hug", "从背后抱住我", "xixi_test_back_hug", false, "stop"),
        new Action("ask_pat", "伸头请求摸摸", "xixi_test_ask_pat", false, "stop"),
        new Action("umbrella", "一起撑伞", "xixi_test_umbrella", false, "xixi_player_umbrella"),
        new Action("carry", "背起汐", "xixi_test_carry", false, "xixi_player_carry"),
        new Action("lift", "抱起（实验）", "xixi_test_lift", false, "xixi_player_lift"),
        new Action("sleep", "陪睡姿态预览", "xixi_test_sleep", false, "stop")
    );
    public record View(String action, boolean seated, InteractionRules.Mode mode, String reason, long revision, UUID invitation, String invitationLabel) {}
    public record Visual(String action, boolean companion, boolean umbrellaHolder, boolean brushLeft, boolean brushing) {}
    private enum StartCause { PLAYER, PLAYER_REQUEST, PREPARATION, AUTOMATIC }
    private static final class Session {
        final UUID owner, companion; final Action action; final InteractionRules.Mode mode;
        final Vec3 origin; final float yaw; final int deadline; final boolean mounted;
        Vec3 companionAnchor, lastOwnerPos; boolean walking; int lastEmote;
        boolean umbrellaOwner;
        boolean contactPaused;
        boolean automatic;
        int lastPatTick=-20,patUntil,poseStarted,brushUntil;
        long lastBrushEvent;
        Session(UUID o,UUID n,Action a,InteractionRules.Mode m,Vec3 p,Vec3 np,float y,int d,boolean ride){owner=o;companion=n;action=a;mode=m;origin=p;lastOwnerPos=p;companionAnchor=np;yaw=y;deadline=d;mounted=ride;}
    }
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("xixi-interaction");
    static final boolean HAS_PAIR_MODEL_ASSETS=InteractionMod.class.getResource("/assets/xixi_interaction/ds_poses.json")!=null;
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Integer> PENDING_STOPS=new HashMap<>();
    private static final Map<UUID,String> REASONS=new HashMap<>();
    private static final Map<UUID,Long> REVISIONS=new HashMap<>();
    private static final Ysm YSM=new Ysm(Ysm.Storage.NEOFORGE);
    private static volatile Map<UUID,Visual> visuals=Map.of();
    private static volatile MinecraftServer visualServer;
    private static volatile Map<UUID,UUID> owners=Map.of();
    private static volatile Set<UUID> dsAppearances=Set.of();
    private static final Set<UUID> autoExpressionsDisabled=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile Map<UUID,String> bodyStates=Map.of();
    private static volatile Map<UUID,String> cameraActions=Map.of();
    private static volatile Map<UUID,UUID> boundPairs=Map.of();
    public record HandRender(UUID owner,InteractionRules.Layout layout) {}
    private static volatile Map<UUID,HandRender> handRenders=Map.of();
    public static final double DS_SEAT_DROP=.44;
    public record DsRender(String pose,int started,float yaw,UUID owner) {}
    private static volatile Map<UUID,DsRender> dsRenders=Map.of();
    private static volatile Map<UUID,Float> lapBodyYaw=Map.of();
    public record LapRender(UUID seated,float seatedYaw,float formationYaw,boolean ownerLying,float headHeight,
                            InteractionRules.LapOffset offset){}
    private static volatile Map<UUID,LapRender> lapRenders=Map.of();
    private static final String PAT_EVENT="xixi_head_pat";
    private static final String ACTION_EVENT="xixi_player_interaction";
    private static final int BACK_HUG_DURATION=80;
    private static final int BACK_HUG_IDLE=240;
    private static final int BACK_HUG_COOLDOWN=6000;
    private record RecentAction(long expires,String description) {}
    private static final Map<UUID,RecentAction> RECENT_ACTIONS=new java.util.concurrent.ConcurrentHashMap<>();
    private record ClientActivity(MinecraftServer server,boolean playing,Set<UUID> goals) {}
    private static volatile ClientActivity clientActivity=new ClientActivity(null,false,Set.of());
    private record IdleBackHug(Vec3 owner,Vec3 companion,int since) {}
    private static final Map<UUID,IdleBackHug> BACK_HUG_IDLE_STATE=new HashMap<>();
    private static final Map<UUID,Integer> BACK_HUG_NEXT=new HashMap<>();
    private static final Set<UUID> BUSY_BODIES=new HashSet<>();
    private static volatile com.dwinovo.numen.api.NumenApi numenApi;
    private static final Map<UUID,Long> recentPats=new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Map<UUID,Long> headResetUntil=Map.of();
    private static final Map<UUID,Integer> LAST_REQUEST=new HashMap<>();
    private record Invitation(UUID token,UUID companion,String action,int expires) {}
    private static final Map<UUID,Invitation> INVITATIONS=new HashMap<>();
    private static final String MARKER="xixi_interaction_ysm_active";
    private static InteractionConfig config;
    public InteractionMod(net.neoforged.bus.api.IEventBus modBus){
        InteractionItems.ITEMS.register(modBus);
        com.dwinovo.numen.api.NumenPlugins.register("xixi_interaction",api->{
            api.api("interaction","Carry out explicit owner requests and invite the owner to companion interactions.",InteractionApi.class);
            api.api("expression","Choose, replace, or clear your own stickers and blush.",ExpressionApi.class);
            api.api("actions","List and play your equipped DS whale model's native actions.",NativeActions.class);
            api.api("control","Dispatch explicit owner requests for stickers or native animations. The /xixi command is also available.",DirectControl.class);
            api.contributeBodyState(n->bodyState(n.getUUID()));
            api.registerEventType(PAT_EVENT,false);
            api.registerEventType(ACTION_EVENT,false);
            numenApi=api;
        });
        // Numen's built-in YSM help is where existing agents look for playback.
        com.dwinovo.numen.api.NumenPlugins.register("ysm",api->{
            api.api("xixi_actions","List and play named native DS whale actions, including nested YSM menus. Call list() before play(action, 0).",NativeActions.class);
            api.api("xixi_expression","Show or hide DS whale stickers and blush. Call sticker(3, 8) for a heart, sticker(0, 4) to hide it, or blush(4).",ExpressionApi.class);
        });
        NeoForge.EVENT_BUS.addListener(InteractionMod::tick);
        NeoForge.EVENT_BUS.addListener(InteractionMod::shutdown);
        NeoForge.EVENT_BUS.addListener(InteractionMod::started);
        NeoForge.EVENT_BUS.addListener(InteractionMod::registerCommands);
        NeoForge.EVENT_BUS.addListener(DirectControl::onChat);
        if(FMLEnvironment.getDist()==Dist.CLIENT)InteractionClient.register();
    }
    private static void started(net.neoforged.neoforge.event.server.ServerStartedEvent e){
        SESSIONS.clear();PENDING_STOPS.clear();REASONS.clear();REVISIONS.clear();visuals=Map.of();owners=Map.of();bodyStates=Map.of();visualServer=e.getServer();
        LAST_REQUEST.clear();INVITATIONS.clear();cameraActions=Map.of();boundPairs=Map.of();handRenders=Map.of();dsRenders=Map.of();lapBodyYaw=Map.of();lapRenders=Map.of();headResetUntil=Map.of();
        try{config=InteractionConfig.load();}catch(Exception ex){config=null;LOG.error("Invalid interaction configuration; interactions disabled",ex);}
        recentPats.clear();
        ExpressionApi.clear();
        NativeActions.clear(e.getServer());
        CompanionAmbient.clear();
        RECENT_ACTIONS.clear();BACK_HUG_IDLE_STATE.clear();BACK_HUG_NEXT.clear();BUSY_BODIES.clear();
    }
    public static Map<UUID,Visual> visuals(MinecraftServer s){return s==visualServer?visuals:Map.of();}
    public static String cameraAction(MinecraftServer server,UUID owner){return server==visualServer?cameraActions.getOrDefault(owner,""):"";}
    public static boolean clientOwns(MinecraftServer s,UUID owner,UUID target){return s==visualServer&&owner.equals(owners.get(target));}
    public static boolean hasDsAppearance(MinecraftServer server,UUID target){
        return server==visualServer&&dsAppearances.contains(target);
    }
    static boolean hasNativeModel(NumenPlayer companion){
        return YsmInteractionModel.DS_PREVIEW.matches(YSM.readLook(companion));
    }
    static boolean hasAmbientAppearance(NumenPlayer companion){
        var look=YSM.readLook(companion);
        return look==null||HAS_PAIR_MODEL_ASSETS&&YsmInteractionModel.DS_PREVIEW.matches(look);
    }
    static String nativeActionBlockReason(NumenPlayer companion){
        var id=companion.getUUID();
        var current=SESSIONS.get(companion.getOwnerUuid());
        if(current!=null&&current.companion.equals(id))return "正在与玩家进行「"+current.action.label+"」，结束后可以播放原生单人动作。";
        if(CompanionAmbient.resting(companion))return "正在床边休息，起身后可以播放单人动作。";
        if(companion.isRemoved()||companion.hasDisconnected()||!companion.isAlive())return "汐当前不在线。";
        if(companion.hurtTime!=0||companion.isPassenger()||companion.isSleeping()||companion.isInLava()||companion.isOnFire())
            return "当前身体状态不适合播放单人动作。";
        if(PENDING_STOPS.containsKey(id))return "上一个动作正在停止，稍后可以再播。";
        if(BUSY_BODIES.contains(id)||CompanionTickDispatcher.currentTaskFor(id)!=null)return "正在执行身体任务，完成后可以播放原生动作。";
        return hasNativeModel(companion)?null:"当前外观未使用 DS 鲸鱼娘。";
    }
    static boolean allowsAutoExpression(MinecraftServer server,UUID target){
        return server==visualServer&&!autoExpressionsDisabled.contains(target);
    }
    static void setAutoExpression(UUID target,boolean enabled){
        if(enabled)autoExpressionsDisabled.remove(target);
        else autoExpressionsDisabled.add(target);
    }
    public static boolean suppressPairPush(net.minecraft.world.entity.Entity first,net.minecraft.world.entity.Entity second){
        if(first.level().isClientSide()){
            Map<UUID,UUID> pairs=boundPairs;
            return second.getUUID().equals(pairs.get(first.getUUID()))||first.getUUID().equals(pairs.get(second.getUUID()));
        }
        if(!(first.level() instanceof net.minecraft.server.level.ServerLevel level)||level.getServer()!=visualServer)return false;
        Session session=SESSIONS.get(first.getUUID());
        if(session!=null&&session.companion.equals(second.getUUID()))return InteractionRules.bound(session.action.id);
        session=SESSIONS.get(second.getUUID());
        return session!=null&&session.companion.equals(first.getUUID())&&InteractionRules.bound(session.action.id);
    }
    private static void registerCommands(RegisterCommandsEvent event){
        DirectControl.register(event);
        event.getDispatcher().register(Commands.literal("xixi_accept")
            .then(Commands.argument("invitation",StringArgumentType.word()).executes(context->{
                var source=context.getSource();
                var owner=source.getPlayerOrException();
                try {
                    acceptInvitation(source.getServer(),owner.getUUID(),StringArgumentType.getString(context,"invitation"),null);
                    return 1;
                } catch(Exception ex) {
                    source.sendFailure(Component.literal(ex.getMessage()));
                    return 0;
                }
            })));
    }
    public static HandRender handRender(MinecraftServer server,UUID companion){
        return server==visualServer?handRenders.get(companion):null;
    }
    public static DsRender dsRender(MinecraftServer server,UUID companion){
        return server==visualServer?dsRenders.get(companion):null;
    }
    public static Float lapBodyYaw(MinecraftServer server,UUID entity){
        return server==visualServer?lapBodyYaw.get(entity):null;
    }
    public static LapRender lapRender(MinecraftServer server,UUID entity){
        return server==visualServer?lapRenders.get(entity):null;
    }
    public static Vec3 lapHead(LapRender lap,Vec3 seated){
        // In the companion's tilted pose, the 7-pixel head cube extends inward
        // from the neck pivot. Place its center over the knees, not the neck.
        var kneeLayout=lap.ownerLying()?new InteractionRules.Layout(-.06,.29,0)
            :new InteractionRules.Layout(.19,.23,0);
        double[] knee=kneeLayout.at(seated.x,seated.z,lap.seatedYaw());
        var offset=lap.offset();
        double[] head=new InteractionRules.Layout(offset.side(),offset.forward(),0)
            .at(knee[0],knee[1],lap.formationYaw());
        return new Vec3(head[0],seated.y+(lap.ownerLying()?.48:.46)+offset.height(),head[1]);
    }
    public static Vec3 seatedPatHead(MinecraftServer server,UUID companion,Vec3 seated,Vec3 companionPosition){
        LapRender lap=lapRender(server,companion);
        if(lap!=null)return lapHead(lap,seated);
        return companionPosition.add(0,dsRender(server,companion)==null?1.12:YsmInteractionModel.DS_PREVIEW.headHeight()-DS_SEAT_DROP,0);
    }
    public static boolean resettingHead(MinecraftServer server,UUID companion){
        return server==visualServer&&System.nanoTime()<headResetUntil.getOrDefault(companion,Long.MIN_VALUE);
    }
    public static UUID cameraPartner(MinecraftServer server,UUID owner){
        return server==visualServer?boundPairs.get(owner):null;
    }
    private static boolean canOffer(Session current,UUID companion,String action){
        return current==null||(current.companion.equals(companion)&&current.action.seated
            &&InteractionRules.SEATED.contains(action)&&!current.action.id.equals(action));
    }
    public static void acceptInvitation(MinecraftServer server,UUID ownerId,String token,InteractionRules.Mode preferred)throws Exception{
        Invitation invitation=INVITATIONS.get(ownerId);
        var current=SESSIONS.get(ownerId);
        if(invitation==null||!invitation.token.toString().equals(token)||server.getTickCount()>=invitation.expires
            ||!canOffer(current,invitation.companion,invitation.action))
            throw new IllegalStateException("邀请已过期，或当前互动无法切换到这个动作。");
        var companion=owned(server,ownerId,invitation.companion);
        var owner=server.getPlayerList().getPlayer(ownerId);
        if(owner==null)throw new IllegalStateException("玩家当前离线");
        var mode=current!=null?current.mode:preferred!=null?preferred:
            defaultMode(owner,companion);
        performRequested(server,owner,companion,invitation.action,mode,current);
        INVITATIONS.remove(ownerId,invitation);
    }

    private static String performRequested(MinecraftServer server,ServerPlayer owner,NumenPlayer companion,String action,
                                           InteractionRules.Mode mode,Session current)throws Exception{
        UUID ownerId=owner.getUUID(),target=companion.getUUID();
        boolean startSeated=InteractionRules.SEATED.contains(action)&&!action.equals("sit")
            &&(current==null||!current.action.seated);
        if(startSeated){
            if(action.equals("brush")&&!hasBrush(owner))throw new IllegalStateException("梳理需要玩家手持刷子");
            select(server,ownerId,target,"sit",mode,REVISIONS.getOrDefault(ownerId,0L),StartCause.PREPARATION);
        }
        try{
            return select(server,ownerId,target,action,mode,REVISIONS.getOrDefault(ownerId,0L),StartCause.PLAYER_REQUEST);
        }catch(Exception ex){
            if(startSeated)stop(server,ownerId,"未能完成请求的动作");
            throw ex;
        }
    }

    private static String bodyState(UUID companion){
        String state=bodyStates.getOrDefault(companion,"当前没有进行玩家互动。");
        if(CompanionAmbient.sleep(visualServer,companion)!=null)state+=" 你正在玩家床边休息，等玩家起床后恢复。";
        if(CompanionAmbient.rain(visualServer,companion)!=null)state+=" 你正在露天打伞。";
        Long pat=recentPats.get(companion);
        if(pat!=null&&System.nanoTime()-pat<12_000_000_000L)state+=" 玩家刚摸了你的头。";
        var recent=RECENT_ACTIONS.get(companion);
        if(recent!=null&&System.nanoTime()<recent.expires())state+=" "+recent.description();
        return state+" 可以自然回应已经发生的互动，无需再次调用同一动作。"
            +" 合适时可调用 xixi_interaction.expression.sticker(3, 4) 显示爱心 4 秒，sticker(0, 4) 收起，blush(4) 单独脸红。"
            +" 空闲时可用 xixi_interaction.actions.list() 查 DS 原生动作，用 xixi_interaction.actions.play(\"点头\", 0) 播放；第一个参数为列表里的动作 ID。"
            +" 玩家明确要求互动时用 xixi_interaction.interaction.perform；你主动发起双人动作时用 interaction.request 并等玩家接受。"
            +" 你可以自己决定用表情或原生动作回应对话和场景，无需玩家点名。"
            +" 例如被摸头、一起休息或开心时可以用爱心，认同时可以点头，疑惑时可以思考。"
            +" 根据实际情境选一种就够，也可以自然地不做动作。战斗和身体任务优先，双人互动中可以用贴纸。";
    }
    public static final class InteractionApi {
        public record Request(String action) {}
        @com.dwinovo.numen.sdk.Fn("Carry out the owner's explicit request for an interaction. Use action sit, lean, maid_lap (rest on their lap), player_lap (they rest on your lap), brush, pat, hug, hand, back_hug, ask_pat, umbrella, carry, lift, sleep, or stop. Call this when the owner asks in plain language. For your own initiative, use interaction.request. Return the real execution result.")
        public static String perform(com.dwinovo.numen.sdk.ServerCall call,Request request){
            var companion=call.her();
            var server=companion.level().getServer();
            var owner=companion.resolveOwnerPlayer();
            if(owner==null)return "玩家当前离线";
            UUID ownerId=owner.getUUID(),target=companion.getUUID();
            var current=SESSIONS.get(ownerId);
            if(current!=null&&!current.companion.equals(target))return "玩家正在与另一位伙伴互动";
            String action=request.action()==null?"":request.action();
            if(!action.equals("stop")&&!InteractionRules.ALL.contains(action))return "没有这个互动动作";
            var mode=current!=null?current.mode:
                defaultMode(owner,companion);
            try{
                return performRequested(server,owner,companion,action,mode,current);
            }catch(Exception ex){
                return "未执行："+ex.getMessage();
            }
        }

        @com.dwinovo.numen.sdk.Fn("Invite your owner to any supported paired activity: sit, lean, maid_lap (your head on their lap), player_lap (their head on your lap), brush, pat, hug, hand, back_hug, ask_pat, umbrella, carry, lift, or sleep. The owner accepts in chat or on right click. A seated branch can be invited while seated as well. Carry/lift and actions needing gear depend on settings and equipment.")
        public static boolean request(com.dwinovo.numen.sdk.ServerCall call,Request request){
            var companion=call.her();
            var owner=companion.resolveOwnerPlayer();
            String id=request.action()==null?"":request.action().strip();
            if(config==null||!InteractionRules.ALL.contains(id)||unsafe(owner,companion,false,id.equals("hand"))!=null)
                return false;
            if(!canOffer(SESSIONS.get(owner.getUUID()),companion.getUUID(),id)
                ||id.equals("brush")&&!hasBrush(owner)
                ||id.equals("umbrella")&&!hasUmbrella(owner)&&!hasUmbrella(companion)
                ||!carryEnabled(id))return false;
            String words=switch(id){
                case "sit" -> "可以陪我坐一会儿吗？";
                case "lean" -> "我可以靠你一会儿吗？";
                case "maid_lap" -> "我可以枕在你膝盖上吗？";
                case "player_lap" -> "要枕在我膝盖上休息一下吗？";
                case "brush" -> "可以帮我梳一下吗？";
                case "pat","ask_pat" -> "可以摸摸我的头吗？";
                case "hand" -> "我们牵手走一会儿，好吗？";
                case "hug" -> "可以抱一下吗？";
                case "back_hug" -> "我可以从背后抱抱你吗？";
                case "umbrella" -> "要一起撑伞吗？";
                case "carry" -> "你可以背我一会儿吗？";
                case "lift" -> "你可以抱起我吗？";
                case "sleep" -> "要一起休息一下吗？";
                default -> throw new IllegalStateException("Missing invitation wording: "+id);
            };
            var server=companion.level().getServer();
            int now=server.getTickCount();
            var pending=INVITATIONS.get(owner.getUUID());
            if(pending!=null&&now<pending.expires)return false;
            Integer last=LAST_REQUEST.get(companion.getUUID());
            if(last!=null&&now-last<2400)return false;
            LAST_REQUEST.put(companion.getUUID(),now);
            Invitation invitation=new Invitation(UUID.randomUUID(),companion.getUUID(),id,now+3600);
            INVITATIONS.put(owner.getUUID(),invitation);
            owner.sendSystemMessage(Component.literal("<"+companion.getScoreboardName()+"> "+words+" （也可右键我，接受邀请） ")
                .append(Component.literal("[接受]").withStyle(style->style
                    .withColor(net.minecraft.ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.RunCommand("/xixi_accept "+invitation.token)))));
            return true;
        }
        @com.dwinovo.numen.sdk.Fn("Read current owner interaction; no mutation, safe during sensing.")
        public static String status(com.dwinovo.numen.sdk.ServerCall call){return bodyState(call.her().getUUID());}
        @com.dwinovo.numen.sdk.Fn("Cancel this companion's owner interaction and release held body tasks.")
        public static boolean stop(com.dwinovo.numen.sdk.ServerCall call){
            var n=call.her();var s=SESSIONS.get(n.getOwnerUuid());if(s==null||!s.companion.equals(n.getUUID()))return false;
            InteractionMod.stop(n.level().getServer(),s.owner,"互动已取消");return true;
        }
    }
    public static boolean seated(UUID owner){var s=SESSIONS.get(owner);return s!=null&&s.action.seated;}
    public static String current(UUID owner){var s=SESSIONS.get(owner);return s==null?REASONS.getOrDefault(owner,"未进行互动"):s.action.label;}
    public static View view(UUID owner,UUID target){
        var s=SESSIONS.get(owner);
        var invitation=INVITATIONS.get(owner);
        if(invitation!=null&&(!invitation.companion.equals(target)||!canOffer(s,target,invitation.action)))invitation=null;
        String requested=invitation==null?"":invitation.action;
        String label=ACTIONS.stream().filter(action->action.id.equals(requested)).map(Action::label).findFirst().orElse("");
        return new View(s==null?"":s.action.id,s!=null&&s.companion.equals(target)&&s.action.seated,
            s==null?InteractionRules.Mode.DS_PREVIEW:s.mode,current(owner),REVISIONS.getOrDefault(owner,0L),
            invitation==null?null:invitation.token,label);
    }
    public static NumenPlayer owned(MinecraftServer server,UUID owner,UUID target){
        var n=NumenPlayer.findByUuid(server,target);
        if(n==null||!n.isOwnedByPlayer(owner))throw new IllegalStateException("只能与自己的 Numen 伙伴互动");return n;
    }
    private static String unsafe(ServerPlayer o,ServerPlayer n,boolean mounted){return unsafe(o,n,mounted,false);}
    private static String unsafe(ServerPlayer o,ServerPlayer n,boolean mounted,boolean hand){
        if(o==null||n==null||o.hasDisconnected()||n.isRemoved()||!o.isAlive()||!n.isAlive())return "角色离线或死亡";
        if(o.level()!=n.level()||o.distanceToSqr(n)>16)return "距离过远或已切换维度";
        if(o.isSpectator()||n.isSpectator()||o.isSleeping()||n.isSleeping())return "当前状态不能互动";
        if((!hand&&(o.isInWater()||n.isInWater()))||o.isInLava()||n.isInLava()||o.isOnFire()||n.isOnFire())return "进入水中或火焰";
        if(o.getHealth()<=5||n.getHealth()<=5||o.hurtTime>0||n.hurtTime>0)return "受伤或生命值过低";
        if(o.isPassenger()||(!mounted&&n.isPassenger())||(mounted&&n.getVehicle()!=o))return "乘坐状态发生变化";
        if(!hand&&!mounted&&(!o.onGround()||!n.onGround()))return "离开地面";
        if(!hand&&(!o.level().getEntities(o,o.getBoundingBox().inflate(8),e->e instanceof Enemy&&e.isAlive()).isEmpty()
            ||!n.level().getEntities(n,n.getBoundingBox().inflate(8),e->e instanceof Enemy&&e.isAlive()).isEmpty()))return "附近出现敌怪";
        return null;
    }
    public static boolean holds(NumenPlayer n){
        for(var s:SESSIONS.values())if(s.companion.equals(n.getUUID())){
            var o=n.level().getServer().getPlayerList().getPlayer(s.owner);
            return !s.contactPaused&&unsafe(o,n,s.mounted,s.action.id.equals("hand"))==null&&!o.isShiftKeyDown()&&(s.action.id.equals("hand")||n.level().getServer().getTickCount()<s.deadline);
        }return CompanionAmbient.resting(n)&&!ambientBusy(n);
    }
    public static void observeBodyTask(NumenPlayer companion,boolean busy){
        if(busy)BUSY_BODIES.add(companion.getUUID());
        else BUSY_BODIES.remove(companion.getUUID());
    }
    public static void observeClientActivity(MinecraftServer server,boolean playing,Set<UUID> goals){
        clientActivity=new ClientActivity(server,playing,Set.copyOf(goals));
    }
    private static boolean hasWork(NumenPlayer companion,ClientActivity activity){
        UUID id=companion.getUUID();
        return BUSY_BODIES.contains(id)||CompanionTickDispatcher.currentTaskFor(id)!=null||activity.goals().contains(id);
    }
    static boolean ambientBusy(NumenPlayer companion){return hasWork(companion,clientActivity);}
    static boolean occupied(NumenPlayer companion){return SESSIONS.containsKey(companion.getOwnerUuid());}
    static boolean ambientSafe(NumenPlayer companion){
        if(!companion.isAlive()||companion.isRemoved()||companion.isPassenger()
            ||companion.isSleeping()||companion.isInWater()||companion.isInLava()||companion.isOnFire()
            ||companion.hurtTime>0||companion.getHealth()<=5)return false;
        return companion.level().getEntities(companion,companion.getBoundingBox().inflate(8),e->e instanceof Enemy&&e.isAlive()).isEmpty();
    }
    private static boolean carryEnabled(String id){
        return switch(id){case "carry"->config.enableBackCarry;case "lift"->config.enableCarry;default->true;};
    }
    private static void command(MinecraftServer server,ServerPlayer p,String suffix)throws Exception{
        String name=p.getScoreboardName();if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Unsupported player name");
        int result=server.getCommands().getDispatcher().execute("ysm "+suffix.replace("{player}",name),server.createCommandSourceStack().withSuppressedOutput());
        if(result<=0)throw new IllegalStateException("YSM 拒绝动作指令");
    }
    private static void play(MinecraftServer server,ServerPlayer p,String emote)throws Exception{command(server,p,"play {player} "+emote);PENDING_STOPS.remove(p.getUUID());}
    private static boolean stopOne(MinecraftServer server,UUID id){
        var p=server.getPlayerList().getPlayer(id);
        if(p!=null)try{play(server,p,"stop");p.getPersistentData().remove(MARKER);return true;}catch(Exception ex){LOG.warn("Stop pending for {}: {}",id,ex.getMessage());}
        PENDING_STOPS.putIfAbsent(id,server.getTickCount()+12000);return false;
    }
    public static void stop(MinecraftServer server,UUID owner){stop(server,owner,"已结束互动");}
    private static void stop(MinecraftServer server,UUID owner,String reason){
        var s=SESSIONS.remove(owner);if(s==null)return;
        var n=server.getPlayerList().getPlayer(s.companion);
        if(n instanceof NumenPlayer np){
            np.controls().stop();
            if(s.mounted&&np.getVehicle()!=null&&np.getVehicle().getUUID().equals(owner))np.stopRiding();
            if(InteractionRules.lap(s.action.id)){
                resetLapLook(np);
                if(s.mode!=InteractionRules.Mode.DS_PREVIEW){
                    var resets=new HashMap<>(headResetUntil);
                    resets.put(np.getUUID(),System.nanoTime()+500_000_000L);
                    headResetUntil=Map.copyOf(resets);
                }
            }
        }
        if(s.mode!=InteractionRules.Mode.VANILLA&&!s.automatic)stopOne(server,s.owner);
        if(s.mode==InteractionRules.Mode.TEST_YSM)stopOne(server,s.companion);
        REASONS.put(owner,reason);REVISIONS.merge(owner,1L,Long::sum);publish(server);
        LOG.info("Stopped owner={} action={} reason={}",owner,s.action.id,reason);
    }
    private static Vec3 target(ServerPlayer o,String action,float yaw){
        double[] xy=config.layouts.get(action).at(o.getX(),o.getZ(),yaw);return new Vec3(xy[0],o.getY(),xy[1]);
    }
    static boolean clear(NumenPlayer n,Vec3 p){
        return clear(n,p,false);
    }
    private static boolean clear(NumenPlayer n,Vec3 p,boolean hand){
        var level=n.level();var box=n.getBoundingBox().move(p.subtract(n.position())).deflate(.02);
        if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(p))||!level.getWorldBorder().isWithinBounds(box)||!level.noBlockCollision(n,box))return false;
        if(!level.getEntities(n,box,e->e instanceof net.minecraft.world.entity.LivingEntity&&e.isAlive()&&!e.getUUID().equals(n.getOwnerUuid())).isEmpty())return false;
        var under=net.minecraft.core.BlockPos.containing(p.x,p.y-.08,p.z);
        if(!hand&&level.getBlockState(under).getCollisionShape(level,under).isEmpty())return false;
        for(var b:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(box.minX,box.minY-.1,box.minZ),net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
            var state=level.getBlockState(b);
            var fluid=state.getFluidState();
            if((!fluid.isEmpty()&&(!hand||!fluid.is(net.minecraft.tags.FluidTags.WATER)))||state.is(net.minecraft.world.level.block.Blocks.FIRE)||state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)||state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)||state.is(net.minecraft.world.level.block.Blocks.CACTUS)||state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)||state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)||state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW))return false;
        }return true;
    }
    static void move(NumenPlayer n,Vec3 p,float yaw){
        n.teleportTo(p.x,p.y,p.z);n.setYRot(yaw);n.setYHeadRot(yaw);n.setYBodyRot(yaw);n.controls().stop();n.setDeltaMovement(Vec3.ZERO);
    }
    static boolean pathClear(NumenPlayer n,Vec3 goal){
        int steps=Math.max(1,(int)Math.ceil(n.position().distanceTo(goal)/.15));if(steps>32)return false;
        for(int i=1;i<=steps;i++)if(!clear(n,n.position().lerp(goal,i/(double)steps)))return false;return true;
    }
    private static boolean handSegment(NumenPlayer companion,Vec3 from,Vec3 to){
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.12));
        if(steps>40)return false;
        for(int i=1;i<=steps;i++)if(!clear(companion,from.lerp(to,i/(double)steps),true))return false;
        return true;
    }
    private static void resetLapLook(NumenPlayer companion){
        companion.setXRot(0);
        companion.xRotO=0;
        companion.setYHeadRot(companion.yBodyRot);
        companion.yHeadRotO=companion.yBodyRot;
    }
    private static Vec3 handPosition(Vec3 ownerPos,float yaw){
        var layout=config.layouts.get("hand");
        double[] offset=layout.at(ownerPos.x,ownerPos.z,yaw);
        return new Vec3(offset[0],ownerPos.y,offset[1]);
    }
    private static Vec3 findHandBodyPosition(NumenPlayer companion,Vec3 desired,Vec3 ownerPosition){
        Vec3 start=companion.position();
        if(handSegment(companion,start,desired))return desired;
        // A stair can intersect the diagonal. Give the companion the same up-across-down route.
        double high=Math.max(start.y,desired.y);
        for(double extra:new double[]{0,.5,1}){
            Vec3 up=new Vec3(start.x,high+extra,start.z);
            Vec3 across=new Vec3(desired.x,high+extra,desired.z);
            if(handSegment(companion,start,up)&&handSegment(companion,up,across)
                &&handSegment(companion,across,desired))return desired;
        }
        // In a tight passage the physical entity can follow the owner's route.
        if(handSegment(companion,start,ownerPosition))return ownerPosition;
        return start;
    }
    public static String select(MinecraftServer server,UUID ownerId,UUID target,String id,InteractionRules.Mode mode,long revision)throws Exception{
        return select(server,ownerId,target,id,mode,revision,StartCause.PLAYER);
    }

    private static InteractionRules.Mode defaultMode(ServerPlayer owner,NumenPlayer companion){
        if(!HAS_PAIR_MODEL_ASSETS||YSM.readLook(owner)==null&&YSM.readLook(companion)==null)
            return InteractionRules.Mode.VANILLA;
        return InteractionRules.Mode.DS_PREVIEW;
    }

    private static String select(MinecraftServer server,UUID ownerId,UUID target,String id,InteractionRules.Mode mode,long revision,StartCause cause)throws Exception{
        if(id.equals("stop")){stop(server,ownerId);return "已结束互动";}
        if(config==null)throw new IllegalStateException("互动配置无效，请检查日志");
        if(REVISIONS.getOrDefault(ownerId,0L)!=revision)throw new IllegalStateException("互动已变化，请重新右键打开菜单");
        var owner=server.getPlayerList().getPlayer(ownerId);var n=owned(server,ownerId,target);var old=SESSIONS.get(ownerId);
        Action a=ACTIONS.stream().filter(x->x.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未知动作"));
        if(mode!=InteractionRules.Mode.VANILLA&&!HAS_PAIR_MODEL_ASSETS)
            throw new IllegalStateException("当前安装包支持普通皮肤双人动作");
        boolean dsPreview=mode==InteractionRules.Mode.DS_PREVIEW;
        boolean automatic=cause==StartCause.AUTOMATIC;
        String error=InteractionRules.transition(old==null?"":old.action.id,id,old!=null&&old.companion.equals(target),hasBrush(owner));
        if(error!=null)throw new IllegalStateException(error);
        error=unsafe(owner,n,old!=null&&old.mounted,id.equals("hand"));if(error!=null)throw new IllegalStateException(error);
        if(owner.distanceToSqr(n)>9||owner.isShiftKeyDown())throw new IllegalStateException("请在 3 格内松开 Shift 后选择动作");
        if(id.equals("umbrella")&&!hasUmbrella(owner)&&!hasUmbrella(n))throw new IllegalStateException("玩家或伙伴需副手持有汐的伞");
        boolean carry=InteractionRules.CARRY.contains(id);
        if(!carryEnabled(id))throw new IllegalStateException("此动作已在互动配置中关闭");
        if(carry&&owner.getPassengers().stream().anyMatch(passenger->passenger!=n))
            throw new IllegalStateException("玩家已有乘客");
        if(mode==InteractionRules.Mode.VANILLA&&(YSM.readLook(n)!=null||YSM.readLook(owner)!=null))throw new IllegalStateException("普通皮肤模式需先在 YSM 中关闭双方模型");
        Vec3 pos=config.alignPositions||automatic||id.equals("hand")?target(owner,id,owner.getYRot()):n.position();
        if(!(id.equals("hand")?handSegment(n,n.position(),pos):pathClear(n,pos)))throw new IllegalStateException("目标站位或途中被遮挡、悬空，请换空旷平地");
        NativeActions.stopPlayback(n);
        CompanionAmbient.suppress(n.getUUID());
        // Failed validation leaves the existing session untouched. From here failures stop both sides.
        stop(server,ownerId,"切换动作");
        if(PENDING_STOPS.containsKey(ownerId)&&!stopOne(server,ownerId)||PENDING_STOPS.containsKey(target)&&!stopOne(server,target))throw new IllegalStateException("上一动作尚未停止，请稍后重试");
        if(mode!=InteractionRules.Mode.VANILLA){
            var companionModel=dsPreview?YsmInteractionModel.DS_PREVIEW:YsmInteractionModel.COMPANION;
            if(!dsPreview||!companionModel.matches(YSM.readLook(n)))command(server,n,companionModel.selectCommand());
            if(!automatic)command(server,owner,YsmInteractionModel.OWNER.selectCommand());
            var nl=YSM.readLook(n);var ol=YSM.readLook(owner);
            if(!companionModel.matches(nl)||!automatic&&!YsmInteractionModel.OWNER.matches(ol))
                throw new IllegalStateException("互动测试模型尚未加载");
        }
        int duration=automatic?BACK_HUG_DURATION:config.maxSeconds*20;
        var session=new Session(ownerId,target,a,mode,owner.position(),pos,owner.getYRot(),server.getTickCount()+duration,carry);
        session.automatic=automatic;
        session.poseStarted=server.getTickCount();
        session.umbrellaOwner=hasUmbrella(owner);
        SESSIONS.put(ownerId,session);
        try{
            move(n,pos,owner.getYRot()+(float)config.layouts.get(id).yaw());
            if(InteractionRules.lap(id))resetLapLook(n);
            if(carry&&!n.startRiding(owner,true,true))throw new IllegalStateException("当前角色不允许背负");
            if(mode!=InteractionRules.Mode.VANILLA){
                if(!automatic)owner.getPersistentData().putBoolean(MARKER,true);
                if(!dsPreview)n.getPersistentData().putBoolean(MARKER,true);
                if(!session.contactPaused){
                    if(!automatic)play(server,owner,id.equals("brush")?brushOwnerAnimation(owner,false)
                        :id.equals("umbrella")&&!session.umbrellaOwner?"xixi_player_hand":a.playerEmote);
                    if(!dsPreview)play(server,n,id.equals("umbrella")&&session.umbrellaOwner?"xixi_test_hand":a.emote);
                }
            }
            REVISIONS.merge(ownerId,1L,Long::sum);REASONS.remove(ownerId);publish(server);
            if(id.equals("pat"))patAgain(server,ownerId,target);
            else if(cause!=StartCause.PREPARATION)notifyInteraction(n,a,cause);
            if(id.equals("brush")){
                session.lastBrushEvent=System.nanoTime();
                brushAgain(server,ownerId,target);
            }
            if(id.equals("back_hug"))BACK_HUG_NEXT.put(target,server.getTickCount()+BACK_HUG_COOLDOWN);
            LOG.info("Started owner={} action={} mode={}",ownerId,id,mode);
            return a.label+" · Shift 取消";
        }catch(Exception ex){stop(server,ownerId,"动作启动失败");throw ex;}
    }
    private static void notifyInteraction(NumenPlayer companion,Action action,StartCause cause){
        String description=switch(action.id){
            case "sit" -> "玩家和你一起坐下了。";
            case "lean" -> "你正坐在玩家身边，靠着玩家。";
            case "maid_lap" -> "你把头枕在玩家的膝上。";
            case "player_lap" -> "玩家把头枕在你的膝上。";
            case "brush" -> "玩家拿刷子给你梳理。";
            case "hug" -> "玩家抱住了你。";
            case "hand" -> "玩家牵住了你的手。";
            case "back_hug" -> cause==StartCause.AUTOMATIC?"你主动从背后抱住了玩家。":"玩家选择了让你从背后抱住他们。";
            case "ask_pat" -> "玩家让你伸头请求摸摸。";
            case "umbrella" -> "你和玩家一起撑起了伞。";
            case "carry" -> "玩家把你背起来了。";
            case "lift" -> "玩家把你抱起来了。";
            case "sleep" -> "玩家开启了陪睡姿态预览。";
            default -> "玩家开启了"+action.label+"互动。";
        };
        RECENT_ACTIONS.put(companion.getUUID(),new RecentAction(System.nanoTime()+25_000_000_000L,description));
        var api=numenApi;
        if(api!=null)try{
            api.emit(companion,ACTION_EVENT,Map.of("action",action.id,"phase","started",
                "initiator",cause==StartCause.AUTOMATIC?"companion":"player"),description,false);
        }catch(RuntimeException ex){
            LOG.warn("Could not deliver {} interaction to Numen",action.id,ex);
        }
    }
    private static boolean hasBrush(ServerPlayer o){return o!=null&&(o.getMainHandItem().is(Items.BRUSH)||o.getOffhandItem().is(Items.BRUSH));}
    private static net.minecraft.world.InteractionHand brushHand(ServerPlayer player){
        return player.getMainHandItem().is(Items.BRUSH)?net.minecraft.world.InteractionHand.MAIN_HAND:net.minecraft.world.InteractionHand.OFF_HAND;
    }
    private static boolean brushLeft(ServerPlayer player){
        var arm=brushHand(player)==net.minecraft.world.InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
        return arm==net.minecraft.world.entity.HumanoidArm.LEFT;
    }
    private static String brushOwnerAnimation(ServerPlayer player,boolean stroking){
        return "xixi_player_brush"+(brushLeft(player)?"_left":"")+(stroking?"":"_ready");
    }
    public static void brushAgain(MinecraftServer server,UUID ownerId,UUID target){
        var session=SESSIONS.get(ownerId);
        if(session==null||!session.companion.equals(target)||!session.action.id.equals("brush"))return;
        var owner=server.getPlayerList().getPlayer(ownerId);
        var companion=NumenPlayer.findByUuid(server,target);
        if(!hasBrush(owner)||companion==null||!companion.isOwnedByPlayer(ownerId)||unsafe(owner,companion,false)!=null
            ||owner.distanceToSqr(companion)>4)return;
        int now=server.getTickCount();
        if(now-session.lastPatTick<10)return;
        session.lastPatTick=now;
        owner.swing(brushHand(owner));
        session.brushUntil=now+22;
        if(session.mode!=InteractionRules.Mode.VANILLA)try{
            play(server,owner,brushOwnerAnimation(owner,true));
        }catch(Exception e){LOG.warn("Could not replay brushing",e);}
        if(session.mode==InteractionRules.Mode.VANILLA)publish(server);
        long time=System.nanoTime();
        if(session.lastBrushEvent==0||time-session.lastBrushEvent>=8_000_000_000L){
            session.lastBrushEvent=time;
            String description="玩家正在用刷子给你梳头。";
            RECENT_ACTIONS.put(target,new RecentAction(time+12_000_000_000L,description));
            var api=numenApi;
            if(api!=null)try{api.emit(companion,ACTION_EVENT,Map.of("action","brush","phase","stroke"),description,false);}
            catch(RuntimeException e){LOG.warn("Could not deliver brush stroke to Numen",e);}
        }
    }
    public static void patAgain(MinecraftServer server,UUID ownerId,UUID target){
        var s=SESSIONS.get(ownerId);
        if(s==null||!s.companion.equals(target))return;
        String action=s.action.id;
        boolean seated=InteractionRules.seatedPat(action);
        if(!seated&&!action.equals("pat"))return;
        var owner=server.getPlayerList().getPlayer(ownerId);
        var companion=owned(server,ownerId,target);
        if(unsafe(owner,companion,false)!=null||owner.distanceToSqr(companion)>4||!owner.getMainHandItem().isEmpty())return;
        int now=server.getTickCount();
        if(now-s.lastPatTick<8)return;
        s.lastPatTick=now;
        owner.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Vec3 head=seated?seatedPatHead(server,target,owner.position(),companion.position()):
            new Vec3(companion.getX(),companion.getEyeY(),companion.getZ());
        companion.level().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
            head.x,head.y+.25,head.z,3,.15,.08,.15,.02);
        if(s.mode!=InteractionRules.Mode.VANILLA)try{
            play(server,owner,seated?"xixi_player_"+action+"_pat":s.action.playerEmote);
            if(seated)s.patUntil=now+12;
        }
        catch(Exception ex){LOG.warn("Could not replay pat animation",ex);}
        long time=System.nanoTime();
        Long previous=recentPats.put(target,time);
        var api=numenApi;
        if(api!=null&&(previous==null||time-previous>=8_000_000_000L))try{
            api.emit(companion,PAT_EVENT,Map.of("action",action),"玩家摸了你的头。保持当前动作即可。",false);
        }catch(RuntimeException ex){LOG.warn("Could not deliver head pat event to Numen",ex);}
    }
    private static boolean hasUmbrella(ServerPlayer p){return p!=null&&p.getOffhandItem().is(InteractionItems.UMBRELLA.get());}
    public static String adjust(MinecraftServer server,UUID ownerId,UUID target,long revision,double side,double forward,double yaw,boolean reset)throws Exception{
        var s=SESSIONS.get(ownerId);
        if(REVISIONS.getOrDefault(ownerId,0L)!=revision)throw new IllegalStateException("动作已变化，请重新打开校准菜单");
        if(s==null||!s.companion.equals(target)||!InteractionRules.adjustable(s.action.id))throw new IllegalStateException("请先进入可调整站位的动作");
        var owner=server.getPlayerList().getPlayer(ownerId);var n=owned(server,ownerId,target);
        if(unsafe(owner,n,false)!=null)throw new IllegalStateException("当前不能调整站位");
        if(!Double.isFinite(side)||!Double.isFinite(forward)||!Double.isFinite(yaw)||Math.abs(side)>.1||Math.abs(forward)>.1||Math.abs(yaw)>10)throw new IllegalArgumentException("调整步长超限");
        var old=config.layouts.get(s.action.id);
        var next=reset?InteractionRules.layout(s.action.id):new InteractionRules.Layout(old.side()+side,old.forward()+forward,old.yaw()+yaw);
        if(!next.valid())throw new IllegalStateException("已达到站位调整范围");
        double[] xz=next.at(owner.getX(),owner.getZ(),s.yaw);var pos=new Vec3(xz[0],owner.getY(),xz[1]);
        if(!pathClear(n,pos))throw new IllegalStateException("调整方向有障碍或地面不安全");
        config.layouts.put(s.action.id,next);
        try{config.save();}catch(Exception e){config.layouts.put(s.action.id,old);throw e;}
        move(n,pos,s.yaw+(float)next.yaw());s.companionAnchor=pos;
        if(InteractionRules.lap(s.action.id))publish(server);
        return String.format(java.util.Locale.ROOT,"已保存：左右 %.2f / 前后 %.2f / 朝向 %.0f°",next.side(),next.forward(),next.yaw());
    }
    public static String adjustLap(MinecraftServer server,UUID owner,UUID target,long revision,
                                   double side,double forward,double height,boolean reset)throws Exception{
        var session=SESSIONS.get(owner);
        if(session==null||!session.companion.equals(target)||!InteractionRules.lap(session.action.id)
            ||session.mode==InteractionRules.Mode.VANILLA||REVISIONS.getOrDefault(owner,0L)!=revision)
            throw new IllegalStateException("请重新打开膝枕调整菜单");
        String action=lapOffsetKey(session);
        var old=config.lapOffsets.getOrDefault(action,InteractionRules.LapOffset.ZERO);
        var next=reset?InteractionRules.LapOffset.ZERO:
            new InteractionRules.LapOffset(old.side()+side,old.forward()+forward,old.height()+height);
        if(!next.valid())throw new IllegalArgumentException("已达到躺姿调整范围");
        config.lapOffsets.put(action,next);
        try{config.save();}catch(Exception ex){config.lapOffsets.put(action,old);throw ex;}
        publish(server);
        return String.format(java.util.Locale.ROOT,"躺姿偏移  左右 %.2f  前后 %.2f  高度 %.2f",
            next.side(),next.forward(),next.height());
    }
    private static String lapOffsetKey(Session session){
        return session.mode==InteractionRules.Mode.DS_PREVIEW?"ds_"+session.action.id:session.action.id;
    }
    private static void publish(MinecraftServer server){
        var cameras=new HashMap<UUID,String>();
        var pairs=new HashMap<UUID,UUID>();
        var hands=new HashMap<UUID,HandRender>();
        var ds=new HashMap<UUID,DsRender>();
        var lapYaw=new HashMap<UUID,Float>();
        var laps=new HashMap<UUID,LapRender>();
        for(var s:SESSIONS.values()){
            cameras.put(s.owner,s.action.id);
            if(InteractionRules.bound(s.action.id))pairs.put(s.owner,s.companion);
            if(s.action.id.equals("hand"))hands.put(s.companion,new HandRender(s.owner,config.layouts.get("hand")));
            if(s.mode==InteractionRules.Mode.DS_PREVIEW&&!s.contactPaused){
                String pose=s.action.id;
                if(pose.equals("umbrella"))pose=s.umbrellaOwner?"hand":s.walking?"umbrella_walk":"umbrella";
                ds.put(s.companion,new DsRender(pose,s.poseStarted,s.yaw,s.owner));
            }
            if(InteractionRules.lap(s.action.id)&&s.mode!=InteractionRules.Mode.VANILLA){
                boolean ownerLying=s.action.id.equals("player_lap");
                UUID seated=ownerLying?s.companion:s.owner;
                UUID lying=ownerLying?s.owner:s.companion;
                float seatedYaw=ownerLying?s.yaw+(float)config.layouts.get(s.action.id).yaw():s.yaw;
                lapYaw.put(seated,seatedYaw);
                // Once tilted, a model facing away from its head direction faces upwards.
                lapYaw.put(lying,s.yaw+(ownerLying?90f:-90f));
                var model=ownerLying?YsmInteractionModel.OWNER:
                    s.mode==InteractionRules.Mode.DS_PREVIEW?YsmInteractionModel.DS_PREVIEW:YsmInteractionModel.COMPANION;
                laps.put(lying,new LapRender(seated,seatedYaw,s.yaw,ownerLying,model.headHeight(),
                    config.lapOffsets.getOrDefault(lapOffsetKey(s),InteractionRules.LapOffset.ZERO)));
            }
        }
        cameraActions=Map.copyOf(cameras);
        boundPairs=Map.copyOf(pairs);
        handRenders=Map.copyOf(hands);
        dsRenders=Map.copyOf(ds);
        lapBodyYaw=Map.copyOf(lapYaw);
        lapRenders=Map.copyOf(laps);
        var m=new HashMap<UUID,Visual>();
        for(var s:SESSIONS.values())if(s.mode==InteractionRules.Mode.VANILLA&&!s.contactPaused){
            var player=s.action.id.equals("brush")?server.getPlayerList().getPlayer(s.owner):null;
            m.put(s.owner,new Visual(s.action.id,false,s.umbrellaOwner,player!=null&&brushLeft(player),s.brushUntil!=0));
            m.put(s.companion,new Visual(s.action.id,true,!s.umbrellaOwner,false,false));
        }
        var states=new HashMap<UUID,String>();
        for(var s:SESSIONS.values()){
            String activity="玩家正在进行互动："+s.action.label+"。继续正常讲话和回答玩家。"
                +" 行走和站位由互动接管，请勿自行启动寻路、攻击或切换模型。";
            states.put(s.companion,activity);
        }
        bodyStates=Map.copyOf(states);
        visualServer=server;visuals=Map.copyOf(m);
    }
    private static void tick(ServerTickEvent.Post event){
        var server=event.getServer();
        NativeActions.tick(server);
        if(server.getTickCount()%100==0)INVITATIONS.values().removeIf(invitation->server.getTickCount()>=invitation.expires);
        var ownership=new HashMap<UUID,UUID>();for(var p:server.getPlayerList().getPlayers())if(p instanceof NumenPlayer n&&n.getOwnerUuid()!=null)ownership.put(n.getUUID(),n.getOwnerUuid());owners=Map.copyOf(ownership);
        if(server.getTickCount()%20==0){
            var ds=new HashSet<UUID>();
            for(UUID id:ownership.keySet()){
                var companion=server.getPlayerList().getPlayer(id);
                if(companion!=null&&YsmInteractionModel.DS_PREVIEW.matches(YSM.readLook(companion)))ds.add(id);
            }
            dsAppearances=Set.copyOf(ds);
        }
        for(var s:List.copyOf(SESSIONS.values())){
            var o=server.getPlayerList().getPlayer(s.owner);var entity=server.getPlayerList().getPlayer(s.companion);
            String reason=unsafe(o,entity,s.mounted,s.action.id.equals("hand"));
            if(reason==null&&(!(entity instanceof NumenPlayer n)||!n.isOwnedByPlayer(s.owner)))reason="伙伴归属变化";
            if(reason==null&&s.automatic&&hasWork((NumenPlayer)entity,clientActivity))reason="汐有任务要处理";
            if(reason==null&&(o.isShiftKeyDown()||entity.isShiftKeyDown()))reason="Shift 取消";
            if(reason==null&&!s.action.id.equals("hand")&&server.getTickCount()>=s.deadline)reason="互动达到时间上限";
            if(reason==null&&s.action.id.equals("brush")&&!hasBrush(o))reason="已收起刷子";
            if(reason==null&&s.action.id.equals("umbrella")&&!hasUmbrella(s.umbrellaOwner?o:entity))reason="已收起伞";
            if(reason==null&&server.getTickCount()%10==0){
                var ol=YSM.readLook(o);var nl=YSM.readLook(entity);
                if(s.mode==InteractionRules.Mode.TEST_YSM&&(!YsmInteractionModel.OWNER.matches(ol)||!YsmInteractionModel.COMPANION.matches(nl)))reason="模型已切换，结束当前动作";
                if(s.mode==InteractionRules.Mode.DS_PREVIEW&&(!s.automatic&&!YsmInteractionModel.OWNER.matches(ol)||!YsmInteractionModel.DS_PREVIEW.matches(nl)))reason="模型已切换，结束互动";
                if(s.mode==InteractionRules.Mode.VANILLA&&(ol!=null||nl!=null))reason="已启用 YSM，结束普通皮肤动作";
            }
            boolean walking=InteractionRules.WALKING.contains(s.action.id);
            boolean bound=InteractionRules.bound(s.action.id);
            if(reason==null&&!walking&&!s.mounted&&(o.position().distanceToSqr(s.origin)>.25||!bound&&entity.position().distanceToSqr(s.companionAnchor)>.36))reason="移动结束互动";
            if(reason!=null){stop(server,s.owner,reason);continue;}
            var n=(NumenPlayer)entity;
            if(s.mounted){
                float yaw=o.getYRot();
                n.setYRot(yaw);
                n.setYBodyRot(yaw);
                n.setYHeadRot(yaw);
            }
            if(s.brushUntil!=0&&server.getTickCount()>=s.brushUntil){
                s.brushUntil=0;
                if(s.mode!=InteractionRules.Mode.VANILLA)try{play(server,o,brushOwnerAnimation(o,false));}
                catch(Exception e){LOG.warn("Could not restore brush ready pose",e);}
                if(s.mode==InteractionRules.Mode.VANILLA)publish(server);
            }
            if(s.patUntil!=0&&server.getTickCount()>=s.patUntil){
                s.patUntil=0;
                try{play(server,o,s.action.playerEmote);}
                catch(Exception ex){LOG.warn("Could not restore seated pose after head pat",ex);}
            }
            if(s.action.id.equals("hand")){
                try{tickHand(server,s,o,n);}
                catch(Exception ex){stop(server,s.owner,"牵手动作播放失败");}
            }else if(walking){
                Vec3 goal=target(o,s.action.id,o.getYRot());
                boolean sideBySide=pathClear(n,goal);
                boolean moving=o.position().subtract(s.lastOwnerPos).horizontalDistanceSqr()>.0004;
                s.lastOwnerPos=o.position();
                double distance=goal.distanceTo(n.position());
                Vec3 step=n.position().lerp(goal,distance<=.24?1:.24/distance);
                boolean contact=sideBySide&&Math.abs(goal.y-n.getY())<.15&&distance<.68;
                try{
                    if(!contact){
                        if(!s.contactPaused){
                            s.contactPaused=true;
                            if(s.mode!=InteractionRules.Mode.VANILLA)play(server,o,"stop");
                            if(s.mode==InteractionRules.Mode.TEST_YSM)play(server,n,"stop");
                            publish(server);
                        }
                        continue;
                    }
                    if(distance>.03)move(n,step,o.getYRot());
                    if(s.contactPaused){
                        if(distance>.30)continue;
                        if(s.mode!=InteractionRules.Mode.VANILLA)
                            play(server,o,s.action.id.equals("umbrella")&&!s.umbrellaOwner?"xixi_player_hand":s.action.playerEmote);
                    }
                    if(s.mode==InteractionRules.Mode.TEST_YSM&&(s.contactPaused||(moving!=s.walking&&server.getTickCount()-s.lastEmote>=8))){
                        String key=s.action.id.equals("umbrella")&&s.umbrellaOwner?"hand":s.action.id;
                        play(server,n,"xixi_test_"+key+(moving?"_walk":""));
                        s.walking=moving;s.lastEmote=server.getTickCount();
                    }
                    if(s.mode==InteractionRules.Mode.DS_PREVIEW&&moving!=s.walking&&server.getTickCount()-s.lastEmote>=8){
                        s.walking=moving;
                        s.lastEmote=server.getTickCount();
                        s.poseStarted=s.lastEmote;
                        publish(server);
                    }
                    if(s.contactPaused){s.contactPaused=false;publish(server);}
                }catch(Exception ex){stop(server,s.owner,"行走动作播放失败");}
            }else if(bound){
                Vec3 goal=s.companionAnchor;
                if(!pathClear(n,goal)){stop(server,s.owner,"站位被挡住");continue;}
                if(goal.distanceToSqr(n.position())>.0009)move(n,goal,s.yaw+(float)config.layouts.get(s.action.id).yaw());
                else n.controls().stop();
            }else{
                n.controls().stop();
            }
        }
        CompanionAmbient.tick(server,config);
        if(server.getTickCount()%20==0)tickIdleBackHug(server);
        if(server.getTickCount()%40==0)for(var e:Map.copyOf(PENDING_STOPS).entrySet()){
            if(server.getTickCount()>e.getValue()){PENDING_STOPS.remove(e.getKey());LOG.error("Stop retry expired for {}",e.getKey());}
            else if(server.getPlayerList().getPlayer(e.getKey())!=null)stopOne(server,e.getKey());
        }
        if(server.getTickCount()%100==0)for(var p:server.getPlayerList().getPlayers())if(p.getPersistentData().getBoolean(MARKER).orElse(false)&&SESSIONS.values().stream().noneMatch(s->s.owner.equals(p.getUUID())||s.companion.equals(p.getUUID())))stopOne(server,p.getUUID());
    }
    private static void tickIdleBackHug(MinecraftServer server){
        var activity=clientActivity;
        if(config==null||!config.enableIdleBackHug||activity.server()!=server||!activity.playing()){
            BACK_HUG_IDLE_STATE.clear();
            return;
        }
        int now=server.getTickCount();
        for(var player:server.getPlayerList().getPlayers()){
            if(!(player instanceof NumenPlayer companion)||!companion.getScoreboardName().equalsIgnoreCase("xixi"))continue;
            UUID target=companion.getUUID();
            var owner=companion.resolveOwnerPlayer();
            if(owner==null||SESSIONS.containsKey(owner.getUUID())||INVITATIONS.containsKey(owner.getUUID())
                ||BACK_HUG_NEXT.getOrDefault(target,0)>now||hasWork(companion,activity)
                ||owner.isShiftKeyDown()||owner.isUsingItem()||companion.isUsingItem()
                ||owner.distanceToSqr(companion)>6.25||unsafe(owner,companion,false)!=null){
                BACK_HUG_IDLE_STATE.remove(target);
                continue;
            }
            var stationary=BACK_HUG_IDLE_STATE.get(target);
            if(stationary==null||stationary.owner().distanceToSqr(owner.position())>.04
                ||stationary.companion().distanceToSqr(companion.position())>.16){
                BACK_HUG_IDLE_STATE.put(target,new IdleBackHug(owner.position(),companion.position(),now));
                continue;
            }
            if(now-stationary.since()<BACK_HUG_IDLE)continue;
            var companionLook=YSM.readLook(companion);
            InteractionRules.Mode mode;
            if(HAS_PAIR_MODEL_ASSETS&&YsmInteractionModel.DS_PREVIEW.matches(companionLook))mode=InteractionRules.Mode.DS_PREVIEW;
            else if(companionLook==null&&YSM.readLook(owner)==null)mode=InteractionRules.Mode.VANILLA;
            else continue;
            UUID ownerId=owner.getUUID();
            BACK_HUG_IDLE_STATE.remove(target);
            try{
                select(server,ownerId,target,"back_hug",mode,REVISIONS.getOrDefault(ownerId,0L),StartCause.AUTOMATIC);
            }catch(Exception ex){
                BACK_HUG_NEXT.put(target,now+400);
                LOG.debug("Idle back hug unavailable: {}",ex.getMessage());
            }
        }
    }
    private static void tickHand(MinecraftServer server,Session session,ServerPlayer owner,NumenPlayer companion)throws Exception{
        Vec3 ownerPosition=owner.position();
        float yaw=owner.getYRot();
        Vec3 fixed=handPosition(ownerPosition,yaw);
        Vec3 body=findHandBodyPosition(companion,fixed,ownerPosition);
        if(companion.position().distanceToSqr(body)>.000001){
            companion.setPos(body.x,body.y,body.z);
            companion.setDeltaMovement(Vec3.ZERO);
        }
        companion.setYRot(yaw);
        companion.setYBodyRot(yaw);
        companion.setYHeadRot(yaw);
        companion.controls().stop();
        boolean moving=ownerPosition.distanceToSqr(session.lastOwnerPos)>.0004;
        session.lastOwnerPos=ownerPosition;
        if(session.mode==InteractionRules.Mode.TEST_YSM&&moving!=session.walking&&server.getTickCount()-session.lastEmote>=6){
            play(server,companion,moving?"xixi_test_hand_walk":"xixi_test_hand");
            session.walking=moving;
            session.lastEmote=server.getTickCount();
        }
    }
    private static void shutdown(ServerStoppingEvent event){
        for(UUID owner:List.copyOf(SESSIONS.keySet()))stop(event.getServer(),owner,"世界已退出");
        PENDING_STOPS.clear();REASONS.clear();REVISIONS.clear();LAST_REQUEST.clear();INVITATIONS.clear();cameraActions=Map.of();boundPairs=Map.of();handRenders=Map.of();dsRenders=Map.of();lapBodyYaw=Map.of();lapRenders=Map.of();headResetUntil=Map.of();visuals=Map.of();owners=Map.of();bodyStates=Map.of();visualServer=null;
        recentPats.clear();
        RECENT_ACTIONS.clear();BACK_HUG_IDLE_STATE.clear();BACK_HUG_NEXT.clear();BUSY_BODIES.clear();
        dsAppearances=Set.of();
        ExpressionApi.clear();
        NativeActions.clear(event.getServer());
        CompanionAmbient.clear();
    }
}

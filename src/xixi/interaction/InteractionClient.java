package xixi.interaction;
import java.util.*;
import com.dwinovo.numen.client.agent.AgentLoopRegistry;
import com.dwinovo.numen.client.agent.Conversations;
import com.dwinovo.numen.client.chat.CompanionChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

public final class InteractionClient {
    private static boolean shiftWasDown,opening;
    private static Set<UUID> activeGoals=Set.of();
    private static InteractionRules.Mode preferred=InteractionMod.HAS_PAIR_MODEL_ASSETS?
        InteractionRules.Mode.DS_PREVIEW:InteractionRules.Mode.VANILLA;
    public static void register(){NeoForge.EVENT_BUS.addListener(InteractionClient::interact);NeoForge.EVENT_BUS.addListener(InteractionClient::specific);NeoForge.EVENT_BUS.addListener(InteractionClient::tick);NeoForge.EVENT_BUS.addListener(InteractionClient::rightClickHead);}
    private static void rightClickHead(InputEvent.InteractionKeyMappingTriggered event){
        if(!event.isUseItem()||event.getHand()!=InteractionHand.MAIN_HAND)return;
        var mc=Minecraft.getInstance();
        var player=mc.player;
        var server=mc.getSingleplayerServer();
        if(server==null||mc.level==null||player==null||mc.screen!=null||mc.getCameraEntity()!=player)return;
        UUID owner=player.getUUID();
        String action=InteractionMod.cameraAction(server,owner);
        boolean brushing=action.equals("brush");
        if(brushing){
            if(!player.getMainHandItem().is(Items.BRUSH)&&!player.getOffhandItem().is(Items.BRUSH))return;
        }else if(!InteractionRules.seatedPat(action)||!player.getMainHandItem().isEmpty())return;
        UUID target=InteractionMod.cameraPartner(server,owner);
        var companion=target==null?null:mc.level.getPlayerByUUID(target);
        if(companion==null||player.distanceToSqr(companion)>4)return;
        var camera=mc.gameRenderer.getMainCamera();
        Vec3 start=camera.position();
        Vec3 end=start.add(Vec3.directionFromRotation(camera.xRot(),camera.yRot()).scale(8));
        Vec3 head=InteractionMod.seatedPatHead(server,target,player.position(),companion.position()).add(0,.11,0);
        // During a lap pose the visible head can be outside the companion's entity hitbox.
        var hit=AABB.ofSize(head,.50,.48,.50).clip(start,end);
        if(hit.isEmpty())return;
        var block=mc.level.clip(new ClipContext(start,hit.get(),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(block.getType()!=HitResult.Type.MISS)return;
        event.setSwingHand(false);
        event.setCanceled(true);
        server.execute(()->{
            if(brushing)InteractionMod.brushAgain(server,owner,target);
            else InteractionMod.patAgain(server,owner,target);
        });
    }
    private static boolean eligible(PlayerInteractEvent e,net.minecraft.world.entity.Entity target){
        var mc=Minecraft.getInstance();if(mc.getSingleplayerServer()==null||e.getHand()!=InteractionHand.MAIN_HAND||!(target instanceof Player)||!(e.getItemStack().isEmpty()||e.getItemStack().is(Items.BRUSH)))return false;
        if(e.getLevel().isClientSide())return InteractionMod.clientOwns(mc.getSingleplayerServer(),e.getEntity().getUUID(),target.getUUID());
        return target instanceof com.dwinovo.numen.entity.NumenPlayer n&&n.isOwnedByPlayer(e.getEntity().getUUID());
    }
    private static void interact(PlayerInteractEvent.EntityInteract e){if(!eligible(e,e.getTarget()))return;e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);if(e.getLevel().isClientSide())open(e.getTarget().getUUID());}
    private static void specific(PlayerInteractEvent.EntityInteractSpecific e){if(!eligible(e,e.getTarget()))return;e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);if(e.getLevel().isClientSide())open(e.getTarget().getUUID());}
    private static void message(String text){var mc=Minecraft.getInstance();mc.execute(()->{if(mc.player!=null)mc.gui.setOverlayMessage(Component.literal(text==null?"操作失败，请查看日志":text),false);});}
    private static void open(UUID target){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server==null||opening||mc.player==null||mc.screen instanceof Menu)return;
        opening=true;UUID owner=mc.player.getUUID();
        server.execute(()->{try{
            String action=InteractionMod.cameraAction(server,owner);
            if(action.equals("pat")||action.equals("brush")){
                if(action.equals("brush"))InteractionMod.brushAgain(server,owner,target);
                else InteractionMod.patAgain(server,owner,target);
                mc.execute(()->opening=false);
                return;
            }
            var n=InteractionMod.owned(server,owner,target);var p=server.getPlayerList().getPlayer(owner);
            if(p==null||p.level()!=n.level()||p.distanceToSqr(n)>9)throw new IllegalStateException("请靠近伙伴");
            var view=InteractionMod.view(owner,target);
            mc.execute(()->{opening=false;if(mc.getSingleplayerServer()==server&&mc.player!=null&&mc.player.getUUID().equals(owner))mc.setScreen(new Menu(target,view));});
        }catch(Exception e){mc.execute(()->opening=false);message(e.getMessage());}});
    }
    private static void select(UUID target,String id,InteractionRules.Mode mode,long revision){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server==null||mc.player==null)return;UUID owner=mc.player.getUUID();
        server.execute(()->{try{InteractionMod.select(server,owner,target,id,mode,revision);}catch(Exception e){if(mc.getSingleplayerServer()==server)message("未执行："+e.getMessage());}});
    }
    private static void openTaskChat(UUID target){
        var mc=Minecraft.getInstance();
        var server=mc.getSingleplayerServer();
        if(server==null||mc.player==null||!InteractionMod.clientOwns(server,mc.player.getUUID(),target))return;
        mc.setScreen(new CompanionChatScreen(Conversations.instance().of(target)));
    }
    private static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();boolean down=mc.player!=null&&mc.options.keyShift.isDown();
        if(down&&!shiftWasDown&&mc.getSingleplayerServer()!=null){UUID owner=mc.player.getUUID();var server=mc.getSingleplayerServer();server.execute(()->InteractionMod.stop(server,owner));}
        shiftWasDown=down;if(mc.level==null)opening=false;
        var server=mc.getSingleplayerServer();
        if(server==null||mc.player==null)activeGoals=Set.of();
        else if(mc.player.tickCount%20==0){
            NativeActionCatalog.refresh(server);
            Set<UUID> goals=new HashSet<>();
            for(UUID id:AgentLoopRegistry.loadedEntityUuids()){
                var goal=AgentLoopRegistry.get(id).map(loop->loop.goal()).orElse(null);
                if(goal!=null&&goal.hasTurnsLeft())goals.add(id);
            }
            activeGoals=Set.copyOf(goals);
        }
        InteractionMod.observeClientActivity(server,mc.player!=null&&mc.screen==null,activeGoals);
    }
    public static InteractionMod.Visual visual(int id){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.getSingleplayerServer()==null)return null;
        var entity=mc.level.getEntity(id);return entity==null?null:InteractionMod.visuals(mc.getSingleplayerServer()).get(entity.getUUID());
    }
    private static void adjust(UUID target,long revision,double side,double forward,double yaw,boolean reset){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server==null||mc.player==null)return;UUID owner=mc.player.getUUID();
        server.execute(()->{try{message(InteractionMod.adjust(server,owner,target,revision,side,forward,yaw,reset));}catch(Exception e){message(e.getMessage());}});
    }
    private static void adjustLap(UUID target,long revision,double side,double forward,double height,boolean reset){
        var mc=Minecraft.getInstance();
        var server=mc.getSingleplayerServer();
        if(server==null||mc.player==null)return;
        UUID owner=mc.player.getUUID();
        server.execute(()->{
            try{message(InteractionMod.adjustLap(server,owner,target,revision,side,forward,height,reset));}
            catch(Exception e){message(e.getMessage());}
        });
    }
    private static final class Calibration extends Screen {
        final UUID target;final long revision;final boolean lap;
        Calibration(UUID target,long revision,boolean lap){
            super(Component.literal(lap?"膝枕接触位置":"互动站位校准"));
            this.target=target;this.revision=revision;this.lap=lap;
        }
        private void change(double side,double forward,double yawOrHeight,boolean reset){
            if(lap)adjustLap(target,revision,side,forward,yawOrHeight,reset);
            else adjust(target,revision,side,forward,yawOrHeight,reset);
        }
        private void b(String text,int x,int y,int w,Runnable r){addRenderableWidget(Button.builder(Component.literal(text),v->r.run()).bounds(x,y,w,20).build());}
        protected void init(){int left=width/2-132,top=Math.max(40,height/2-80);
            b("左移 0.05",left,top,128,()->change(-.05,0,0,false));b("右移 0.05",left+136,top,128,()->change(.05,0,0,false));
            b("向前 0.05",left,top+24,128,()->change(0,.05,0,false));b("向后 0.05",left+136,top+24,128,()->change(0,-.05,0,false));
            b(lap?"降低 0.05":"转向 -5°",left,top+48,128,()->change(0,0,lap?-.05:-5,false));
            b(lap?"抬高 0.05":"转向 +5°",left+136,top+48,128,()->change(0,0,lap?.05:5,false));
            b(lap?"恢复默认接触位置":"恢复此动作默认站位",left,top+76,264,()->change(0,0,0,true));
            b("返回动作菜单",left,top+104,128,()->{onClose();open(target);});b("结束互动",left+136,top+104,128,()->{select(target,"stop",preferred,0);onClose();});
        }
        public boolean isPauseScreen(){return false;}
        public boolean keyPressed(KeyEvent e){if(e.key()==GLFW.GLFW_KEY_LEFT_SHIFT||e.key()==GLFW.GLFW_KEY_RIGHT_SHIFT){select(target,"stop",preferred,0);onClose();return true;}return super.keyPressed(e);}
        public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){extractTransparentBackground(g);super.extractRenderState(g,x,y,dt);g.centeredText(font,title,width/2,12,0xFFFFFFFF);g.centeredText(font,Component.literal("每次调整自动保存 · 障碍方向拒绝移动 · Shift 取消"),width/2,height-16,0xFFCCCCCC);}
    }
    private static final class Menu extends Screen {
        private enum Tab {
            AFFECTION("亲近", "pat", "hug", "back_hug", "ask_pat"),
            REST("休息", "sit", "lean", "maid_lap", "player_lap", "brush", "sleep"),
            TRAVEL("同行", "hand", "umbrella", "carry", "lift");

            final String label;
            final List<String> actions;

            Tab(String label,String... actions){
                this.label=label;
                this.actions=List.of(actions);
            }
        }

        final UUID target;
        final InteractionMod.View view;
        private int page;
        private Tab tab;
        private InteractionRules.Mode mode;

        Menu(UUID target,InteractionMod.View view){
            super(Component.literal("与汐互动"));
            this.target=target;
            this.view=view;
            mode=view.action().isEmpty()?preferred:view.mode();
            tab=view.seated()?Tab.REST:Arrays.stream(Tab.values())
                .filter(group->group.actions.contains(view.action())).findFirst().orElse(Tab.AFFECTION);
        }

        private Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
        protected void init(){
            int w=Math.min(344,width-12),left=(width-w)/2,half=(w-6)/2;
            int tabWidth=(w-16)/5;
            for(Tab group:Tab.values()){
                var control=button(group.label,left+group.ordinal()*(tabWidth+4),32,tabWidth,()->{
                    tab=group;
                    page=0;
                    rebuildWidgets();
                });
                control.active=group!=tab;
            }
            button("表情",left+3*(tabWidth+4),32,tabWidth,()->Minecraft.getInstance().setScreen(new ExpressionScreen(target,this)));
            button("交代任务",left+4*(tabWidth+4),32,tabWidth,()->openTaskChat(target));
            var actions=tab.actions.stream().map(id->InteractionMod.ACTIONS.stream()
                .filter(action->action.id().equals(id)).findFirst().orElseThrow()).toList();
            boolean calibrate=InteractionRules.adjustable(view.action());
            boolean invited=view.invitation()!=null;
            int top=58,footer=height-40;
            int contextY=footer-24;
            int actionEnd=(calibrate||invited)?contextY:footer;
            if(((actions.size()+1)/2)*24-4>actionEnd-top)actionEnd-=24;
            int rows=Math.max(1,(actionEnd-top+4)/24);
            int per=rows*2,pages=(actions.size()+per-1)/per;
            page=Math.min(page,pages-1);
            for(int i=page*per;i<Math.min(actions.size(),(page+1)*per);i++){
                var action=actions.get(i);
                int slot=i-page*per;
                var control=button(action.label(),left+(slot%2)*(half+6),top+(slot/2)*24,half,()->{
                    select(target,action.id(),mode,view.revision());
                    onClose();
                });
                control.active=!action.seated()||action.id().equals("sit")||view.seated();
            }
            if(calibrate){
                boolean lap=InteractionRules.lap(view.action())&&view.mode()!=InteractionRules.Mode.VANILLA;
                button(lap?"调整膝枕接触位置":"调整当前动作站位",left,contextY,invited?half:w,
                    ()->Minecraft.getInstance().setScreen(new Calibration(target,view.revision(),lap)));
            }
            if(invited)button("接受邀请 · "+view.invitationLabel(),calibrate?left+half+6:left,
                contextY,calibrate?half:w,this::accept);
            if(pages>1){
                button("上一页",left,actionEnd,half,()->{page=(page+pages-1)%pages;rebuildWidgets();});
                button("下一页 "+(page+1)+"/"+pages,left+half+6,actionEnd,half,()->{page=(page+1)%pages;rebuildWidgets();});
            }
            if(InteractionMod.HAS_PAIR_MODEL_ASSETS)button(appearance(),left,footer,half,()->{
                mode=switch(mode){
                    case DS_PREVIEW -> InteractionRules.Mode.TEST_YSM;
                    case TEST_YSM -> InteractionRules.Mode.VANILLA;
                    case VANILLA -> InteractionRules.Mode.DS_PREVIEW;
                };
                preferred=mode;
                rebuildWidgets();
            });
            button(view.action().isEmpty()?"关闭":view.seated()?"起身":"结束互动",
                InteractionMod.HAS_PAIR_MODEL_ASSETS?left+half+6:left,footer,InteractionMod.HAS_PAIR_MODEL_ASSETS?half:w,()->{
                if(!view.action().isEmpty())select(target,"stop",mode,view.revision());
                onClose();
            });
        }
        private String appearance(){
            return switch(mode){
                case DS_PREVIEW -> "外观 · DS 鲸鱼娘";
                case TEST_YSM -> "外观 · 酒狐";
                case VANILLA -> "外观 · 普通皮肤";
            };
        }
        private void accept(){
            var mc=Minecraft.getInstance();
            var server=mc.getSingleplayerServer();
            if(server==null||mc.player==null)return;
            UUID owner=mc.player.getUUID();
            var selectedMode=mode;
            server.execute(()->{
                try{InteractionMod.acceptInvitation(server,owner,view.invitation().toString(),selectedMode);}
                catch(Exception ex){message(ex.getMessage());}
            });
            onClose();
        }
        public boolean isPauseScreen(){return false;}
        public boolean keyPressed(KeyEvent e){if(e.key()==GLFW.GLFW_KEY_LEFT_SHIFT||e.key()==GLFW.GLFW_KEY_RIGHT_SHIFT){select(target,"stop",mode,view.revision());onClose();return true;}return super.keyPressed(e);}
        public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
            extractTransparentBackground(g);
            super.extractRenderState(g,x,y,dt);
            g.centeredText(font,title,width/2,8,0xFFFFFFFF);
            if(!view.reason().isEmpty())g.centeredText(font,Component.literal(view.reason()),width/2,20,0xFFBCE8EF);
            String hint=tab==Tab.REST&&!view.seated()?"一起坐下后，再右键选择分支":
                view.action().isEmpty()?"Esc 关闭菜单":"Shift 结束互动 · Esc 关闭菜单";
            g.centeredText(font,Component.literal(hint),width/2,height-14,0xFFDADADA);
        }
    }
}

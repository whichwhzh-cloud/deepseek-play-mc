package xixi.interaction;

import java.util.Locale;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class ExpressionScreen extends Screen {
    private enum Page {
        BLUSH("脸红"), STICKERS("表情包"), PLACEMENT("贴纸位置");
        final String label;
        Page(String label){this.label=label;}
    }

    private final UUID target;
    private final Screen parent;
    private final YsmExpressions.Settings settings;
    private Page page=Page.STICKERS;
    ExpressionScreen(UUID target,Screen parent){
        super(Component.literal("汐的表情"));
        this.target=target;
        this.parent=parent;
        settings=YsmExpressions.settings(target);
    }

    @Override
    protected void init(){
        int panel=Math.min(width-12,308);
        int left=(width-panel)/2;
        int third=(panel-8)/3;
        int tabWidth=third;
        for(Page choice:Page.values()){
            var tab=button(choice.label,left+choice.ordinal()*(tabWidth+4),27,tabWidth,()->{
                page=choice;
                rebuildWidgets();
            });
            tab.active=choice!=page;
        }
        switch(page){
            case BLUSH -> blush(left,panel);
            case STICKERS -> stickers(left,third);
            case PLACEMENT -> placement(left,panel);
        }
        int half=(panel-4)/2;
        button("返回互动",left,height-26,half,this::onClose);
        var server=minecraft.getSingleplayerServer();
        boolean auto=InteractionMod.allowsAutoExpression(server,target);
        button("自主表情 "+(auto?"开":"关"),left+half+4,height-26,half,()->{
            InteractionMod.setAutoExpression(target,!auto);
            rebuildWidgets();
        });
    }

    private void blush(int left,int panel){
        boolean available=FaceAccessories.blushAvailable();
        button(settings.blush?"脸红预览 · 开":"脸红预览 · 关",left,54,panel,()->{
            settings.blush=!settings.blush;
            rebuildWidgets();
        }).active=available;
        int half=(panel-4)/2;
        slider("贴合深度",left,78,half,-.14,.075,settings.blushDepth," 格",available,v->settings.blushDepth=v);
        slider("脸红高度",left+half+4,78,half,-.07,.07,settings.blushHeight," 格",available,v->settings.blushHeight=v);
        button("恢复默认贴合",left,102,panel,()->{
            settings.blushDepth=0;
            settings.blushHeight=0;
            rebuildWidgets();
        }).active=available;
    }

    private void stickers(int left,int third){
        boolean available=YsmExpressions.atlasAvailable();
        for(int i=0;i<=YsmExpressions.stickerCount();i++){
            int id=i;
            String mark=settings.sticker==id?"✓ ":"";
            var control=button(mark+YsmExpressions.stickerName(id),left+i%3*(third+4),54+i/3*23,third,()->{
                ExpressionApi.clearSticker(target);
                settings.sticker=id;
                rebuildWidgets();
            });
            control.active=id!=settings.sticker&&(id==0||available);
        }
    }

    private void placement(int left,int panel){
        int half=(panel-4)/2,right=left+half+4;
        slider("左右",left,54,half,-1.5,1.5,settings.side," 格",true,v->settings.side=v);
        slider("上下",right,54,half,-.6,1.6,settings.height," 格",true,v->settings.height=v);
        slider("前后",left,78,half,-1.2,1.2,settings.depth," 格",true,v->settings.depth=v);
        slider("大小",right,78,half,.2,1.3,settings.size," 格",true,v->settings.size=v);
        button("恢复默认位置和大小",left,102,panel,()->{settings.resetSticker();rebuildWidgets();});
    }

    private Button button(String label,int x,int y,int width,Runnable handler){
        return addRenderableWidget(Button.builder(Component.literal(label),button->handler.run())
            .bounds(x,y,width,20).build());
    }

    private void slider(String label,int x,int y,int width,double min,double max,double initial,String unit,
                        boolean enabled,DoubleConsumer setter){
        var widget=new AbstractSliderButton(x,y,width,20,number(label,initial,unit),(initial-min)/(max-min)){
            @Override protected void updateMessage(){setMessage(number(label,min+(max-min)*value,unit));}
            @Override protected void applyValue(){setter.accept(min+(max-min)*value);}
        };
        widget.active=enabled;
        addRenderableWidget(widget);
    }

    private static Component number(String label,double value,String unit){
        String display=unit.equals("%")||unit.equals("°")
            ?Long.toString(Math.round(value)):String.format(Locale.ROOT,"%.2f",value);
        return Component.literal(label+" "+display+unit);
    }

    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean keyPressed(KeyEvent event){
        if(event.key()==GLFW.GLFW_KEY_LEFT_SHIFT||event.key()==GLFW.GLFW_KEY_RIGHT_SHIFT)
            return parent.keyPressed(event);
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float dt){
        extractTransparentBackground(graphics);
        super.extractRenderState(graphics,x,y,dt);
        graphics.centeredText(font,title,width/2,10,0xFFFFFFFF);
        String notice=switch(page){
            case BLUSH -> FaceAccessories.blushAvailable()?"贴合深度调为负值时会向脸内移":"缺少本机大肥鱼的脸红资源";
            case STICKERS -> YsmExpressions.atlasAvailable()?"":"请使用本机大肥鱼贴图安装表情包";
            case PLACEMENT -> "";
        };
        if(!notice.isEmpty())graphics.centeredText(font,Component.literal(notice),width/2,height-39,0xFFE4DDD8);
    }
}

package xixi.interaction;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
/** Original six-limb fallback poses; does not unpack or edit protected YSM assets. */
public final class VanillaPoses {
    public static void apply(HumanoidModel<?> m,AvatarRenderState state,InteractionMod.Visual visual){
        String a=visual.action();boolean n=visual.companion();
        boolean lap=(a.equals("maid_lap")&&n)||(a.equals("player_lap")&&!n);
        if(InteractionRules.SEATED.contains(a)){
            m.root().y+=9;m.leftLeg.xRot=m.rightLeg.xRot=-1.45f;m.leftLeg.yRot=-.12f;m.rightLeg.yRot=.12f;
            m.leftArm.xRot=m.rightArm.xRot=-.35f;m.leftArm.zRot=-.12f;m.rightArm.zRot=.12f;
            if(a.equals("lean")&&n){m.root().zRot=-.18f;m.head.zRot=.1f;}
            if(a.equals("brush")&&!n){
                var arm=visual.brushLeft()?m.leftArm:m.rightArm;
                arm.xRot=visual.brushing()?-1.05f+(float)Math.sin(state.ageInTicks*.22f)*.2f:-.73f;
            }
        }
        if(lap||a.equals("sleep")){m.root().zRot=1.5708f;m.root().y=lap?15:18;m.root().x+=lap?5:0;m.leftLeg.xRot=m.rightLeg.xRot=0;m.leftArm.xRot=m.rightArm.xRot=0;}
        if(a.equals("hug")||(a.equals("back_hug")&&n)){m.leftArm.xRot=m.rightArm.xRot=-1.2f;m.leftArm.yRot=-.35f;m.rightArm.yRot=.35f;}
        if(a.equals("pat")||a.equals("ask_pat")){if(n){m.head.xRot=.35f;m.body.xRot=.12f;}else{m.rightArm.xRot=-1.45f+(float)Math.sin(state.ageInTicks*.25f)*.08f;m.rightArm.zRot=.18f;}}
        if(a.equals("hand")){if(n){m.leftArm.xRot=-.2f;m.leftArm.zRot=-.45f;}else{m.rightArm.xRot=-.2f;m.rightArm.zRot=.45f;}}
        if(a.equals("umbrella")){if(visual.umbrellaHolder()){m.leftArm.xRot=-2.1f;m.leftArm.zRot=-.1f;}else{m.rightArm.xRot=-.3f;m.rightArm.zRot=.3f;}}
        if(InteractionRules.CARRY.contains(a)){if(n){m.leftLeg.xRot=m.rightLeg.xRot=-1.2f;m.leftArm.xRot=m.rightArm.xRot=-1.4f;}else m.leftArm.xRot=m.rightArm.xRot=-.9f;}
    }
}

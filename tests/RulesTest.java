import xixi.interaction.InteractionRules;
import java.util.*;
public final class RulesTest {
    static int count;
    static void check(boolean ok,String label){count++;if(!ok)throw new AssertionError(label);}
    static void near(double a,double b,String label){check(Math.abs(a-b)<1e-8,label);}
    public static void main(String[] args){
        check(InteractionRules.transition("","sit",false,false)==null,"enter sitting");
        for(String branch:List.of("lean","maid_lap","player_lap","brush")){
            check(InteractionRules.transition("",branch,false,true)!=null,"cannot skip seated entry "+branch);
            check(InteractionRules.transition("sit",branch,false,true)!=null,"other companion cannot inherit seated state");
            for(String prev:InteractionRules.SEATED)check(InteractionRules.transition(prev,branch,true,true)==null,"seated branches switch "+prev+" -> "+branch);
        }
        check(InteractionRules.transition("sit","brush",true,false)!=null,"brush required");
        check(InteractionRules.transition("brush","sit",true,false)==null,"can put brush away and sit");
        check(InteractionRules.transition("sit","invented",true,true)!=null,"unknown ID rejected");
        for(String a:InteractionRules.ALL){
            check(InteractionRules.layout(a).valid(),"default layout valid "+a);
            for(int yaw=0;yaw<360;yaw+=15){
                var l=InteractionRules.layout(a);var p=l.at(7,-3,yaw);
                near(Math.hypot(p[0]-7,p[1]+3),Math.hypot(l.side(),l.forward()),"rotation preserves offset length");
            }
        }
        var right=new InteractionRules.Layout(1,0,0);
        near(right.at(0,0,0)[0],1,"south facing right offset");near(right.at(0,0,90)[1],1,"west facing right offset");
        var back=InteractionRules.BACK_SEAT;
        near(back.at(2,5,0)[1],4.64,"carry stays behind carrier facing south");
        near(back.at(2,5,90)[0],2.36,"carry follows carrier yaw");
        check(!new InteractionRules.Layout(Double.NaN,0,0).valid(),"NaN rejected");
        check(!new InteractionRules.Layout(0,Double.POSITIVE_INFINITY,0).valid(),"infinity rejected");
        check(!new InteractionRules.Layout(5,0,0).valid(),"large offsets rejected");
        check(!new InteractionRules.Layout(0,0,361).valid(),"excessive yaw rejected");
        System.out.println("PASS: "+count+" transition and placement assertions");
    }
}

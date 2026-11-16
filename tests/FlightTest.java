package xixi.interaction;

import com.dwinovo.numen.sdk.ApiRegistry;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.world.phys.Vec3;

public final class FlightTest {
    public static void main(String[] args){
        Vec3 origin=new Vec3(-.5,80,10.5);
        check(CreativeFlight.validDestination(origin,origin.add(0,48,0)),"48-block boundary");
        check(!CreativeFlight.validDestination(origin,origin.add(0,48.01,0)),"out of range");
        check(!CreativeFlight.validDestination(origin,new Vec3(0,Double.NaN,0)),"non-finite coordinate");
        check(CreativeFlight.velocity(new Vec3(0,3,0)).y>.29,"vertical takeoff input");
        check(CreativeFlight.velocity(new Vec3(0,-3,0)).y<-.29,"descending input");
        check(CreativeFlight.velocity(new Vec3(4,4,4)).length()<=.3000001,"bounded diagonal speed");
        check(CreativeFlight.velocity(new Vec3(0,.10,0)).y<.10,"no near-target overshoot");
        check(CreativeFlight.velocity(new Vec3(0,-.06,0)).equals(Vec3.ZERO),"no movement inside arrival radius");
        ApiRegistry.register("xixi_interaction","flight","Creative flight",CreativeFlight.class);
        Map<String,Integer> arity=ApiRegistry.functions().stream()
            .filter(fn->fn.fullName().startsWith("xixi_interaction.flight."))
            .collect(Collectors.toMap(fn->fn.fullName().substring("xixi_interaction.flight.".length()),fn->fn.params().size()));
        check(arity.equals(Map.of("takeoff",0,"to",3,"land",0,"stop",0,"status",0)),"Numen Lua registration");
        System.out.println("PASS: creative flight coordinate and motion bounds");
    }

    private static void check(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }
}

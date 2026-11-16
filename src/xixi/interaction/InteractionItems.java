package xixi.interaction;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
public final class InteractionItems {
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("xixi_interaction");
    public static final DeferredItem<Item> UMBRELLA=ITEMS.registerSimpleItem("umbrella",p->p.stacksTo(1));
}

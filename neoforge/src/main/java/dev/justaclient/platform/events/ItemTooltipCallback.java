package dev.justaclient.platform.events;
import java.util.List;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
public interface ItemTooltipCallback {
    Event<ItemTooltipCallback> EVENT = new Event<>();
    void tooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines);
}

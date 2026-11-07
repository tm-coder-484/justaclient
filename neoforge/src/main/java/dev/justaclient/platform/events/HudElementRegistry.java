package dev.justaclient.platform.events;
import java.util.*;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
public final class HudElementRegistry {
    private static final Map<ResourceLocation,Function<HudElement,HudElement>> replacements=new HashMap<>();
    private static final Map<ResourceLocation,HudElement> attached=new LinkedHashMap<>();
    public static void attachElementBefore(ResourceLocation before,ResourceLocation id,HudElement element) { attached.put(id,element); }
    public static void attachElementAfter(ResourceLocation after,ResourceLocation id,HudElement element) { attached.put(id,element); }
    public static void replaceElement(ResourceLocation id,Function<HudElement,HudElement> replacement) { replacements.put(id,replacement); }
    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT,ResourceLocation.fromNamespaceAndPath("justaclient","hud"),
            (g,d)->attached.values().forEach(l->l.render(g,d)));
    }
    public static void before(RenderGuiLayerEvent.Pre event) {
        var replacement=replacements.get(event.getName());
        if(replacement!=null) {
            event.setCanceled(true);
            replacement.apply(event.getLayer()::render).render(event.getGuiGraphics(),event.getPartialTick());
        }
    }
}

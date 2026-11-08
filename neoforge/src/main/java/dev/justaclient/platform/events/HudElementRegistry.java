package dev.justaclient.platform.events;
import java.util.*;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
public final class HudElementRegistry {
    private static final Map<ResourceLocation,Function<HudElement,HudElement>> replacements=new HashMap<>();
    private record Attachment(ResourceLocation anchor, boolean after, HudElement element) {}
    private static final Map<ResourceLocation,Attachment> attached=new LinkedHashMap<>();
    public static void attachElementBefore(ResourceLocation before,ResourceLocation id,HudElement element) { attached.put(id,new Attachment(before,false,element)); }
    public static void attachElementAfter(ResourceLocation after,ResourceLocation id,HudElement element) { attached.put(id,new Attachment(after,true,element)); }
    public static void replaceElement(ResourceLocation id,Function<HudElement,HudElement> replacement) { replacements.put(id,replacement); }
    public static void register(RegisterGuiLayersEvent event) {
        // Features initialize after layer registration. Keep dispatchers at their native anchors,
        // then read the live attachments when rendering (waypoints/glow must precede the HUD).
        for (var anchor : List.of(VanillaGuiLayers.CAMERA_OVERLAYS,VanillaGuiLayers.CHAT)) {
            for (boolean after : new boolean[] {false,true}) {
                var id=ResourceLocation.fromNamespaceAndPath("justaclient",(after?"after_":"before_")+anchor.getPath());
                net.minecraft.client.gui.LayeredDraw.Layer layer=(g,d)-> {
                    if(net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
                    attached.values().stream().filter(a->a.anchor.equals(anchor)&&a.after==after).forEach(a->a.element.render(g,d));
                };
                if(after) event.registerAbove(anchor,id,layer); else event.registerBelow(anchor,id,layer);
            }
        }
    }
    public static void before(RenderGuiLayerEvent.Pre event) {
        var replacement=replacements.get(event.getName());
        if(replacement!=null) {
            event.setCanceled(true);
            replacement.apply(event.getLayer()::render).render(event.getGuiGraphics(),event.getPartialTick());
        }
    }
}

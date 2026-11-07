package dev.justaclient.platform.events;
import dev.justaclient.platform.input.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** Connects the client's callbacks to NeoForge events. All callbacks run on the client thread. */
public final class ClientEvents {
    private static boolean started;
    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(HudElementRegistry::before);
        bus.addListener((net.neoforged.neoforge.client.event.RenderNameTagEvent e) -> {
            if(e.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
                var tinted=dev.justaclient.feature.impl.FriendsFeature.get().tint(e.getContent(),player.getGameProfile().getName(),true);
                e.setContent(dev.justaclient.feature.impl.TiersFeature.get().decorate(tinted,player.getUUID(),true));
            }
        });
        bus.addListener((ViewportEvent.RenderFog e) -> dev.justaclient.feature.Features.get(dev.justaclient.feature.impl.FogFeature.class).adjust(e));
        bus.addListener((ViewportEvent.ComputeFogColor e) -> {
            var level=Minecraft.getInstance().level;if(level==null)return;
            var color=dev.justaclient.feature.Features.get(dev.justaclient.feature.impl.FogFeature.class).color(e.getCamera(),level,new org.joml.Vector4f(e.getRed(),e.getGreen(),e.getBlue(),1));
            e.setRed(color.x);e.setGreen(color.y);e.setBlue(color.z);
        });
        bus.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY)
                {
                    dev.justaclient.render.Projection.capture(e.getCamera().getPosition(),e.getModelViewMatrix(),e.getProjectionMatrix());
                    var mc=Minecraft.getInstance();
                    if(mc.level!=null && mc.level.effects().skyType()==net.minecraft.client.renderer.DimensionSpecialEffects.SkyType.NORMAL) {
                        var stack=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();stack.pushMatrix();stack.set(e.getModelViewMatrix());
                        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                        try {
                            float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
                            dev.justaclient.feature.Features.get(dev.justaclient.feature.impl.SkyFeature.class).renderNight(mc.level.getTimeOfDay(partial)*(float)(Math.PI*2),1-mc.level.getRainLevel(partial),mc.level.getStarBrightness(partial));
                        } finally { stack.popMatrix();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix(); }
                    }
                }
            if (e.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                var context=new WorldRenderContext(e);
                WorldRenderEvents.END_MAIN.listeners().forEach(l->l.accept(context));
                context.consumers().endBatch();
            }
        });
        bus.addListener((RenderHighlightEvent.Block e) -> {
            for(var listener:WorldRenderEvents.BEFORE_BLOCK_OUTLINE.listeners())
                if(!listener.allow(null,null)) { e.setCanceled(true);break; }
        });
        bus.addListener((ClientTickEvent.Post e) -> {
            var mc = Minecraft.getInstance();
            if (!started) { started = true; ClientLifecycleEvents.CLIENT_STARTED.listeners().forEach(l -> l.accept(mc)); }
            ClientTickEvents.END_CLIENT_TICK.listeners().forEach(l -> l.accept(mc));
            if (mc.screen != null) ScreenEvents.afterTick(mc.screen).listeners().forEach(l -> l.run(mc.screen));
        });
        bus.addListener((GameShuttingDownEvent e) -> ClientLifecycleEvents.CLIENT_STOPPING.listeners().forEach(l -> l.accept(Minecraft.getInstance())));
        bus.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> ClientPlayConnectionEvents.JOIN.listeners().forEach(l -> l.join(Minecraft.getInstance().getConnection(), null, Minecraft.getInstance())));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientPlayConnectionEvents.DISCONNECT.listeners().forEach(l -> l.leave(Minecraft.getInstance().getConnection(), Minecraft.getInstance())));
        bus.addListener((ScreenEvent.Init.Post e) -> {
            var s = e.getScreen(); ScreenEvents.init(s);
            ScreenEvents.AFTER_INIT.listeners().forEach(l -> l.init(Minecraft.getInstance(), s, s.width, s.height));
        });
        bus.addListener((ScreenEvent.Render.Post e) -> ScreenEvents.afterRender(e.getScreen()).listeners().forEach(l -> l.render(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick())));
        bus.addListener((ScreenEvent.Closing e) -> {
            ScreenEvents.remove(e.getScreen()).listeners().forEach(l -> l.run(e.getScreen()));
            ScreenEvents.init(e.getScreen());
        });
        bus.addListener((ScreenEvent.MouseButtonPressed.Pre e) -> {
            var input = new MouseButtonEvent(e.getMouseX(), e.getMouseY(), e.getButton());
            for (var l : ScreenMouseEvents.allowMouseClick(e.getScreen()).listeners()) if (!l.allow(e.getScreen(), input)) { e.setCanceled(true); break; }
        });
        bus.addListener((ScreenEvent.MouseButtonReleased.Pre e) -> {
            var input = new MouseButtonEvent(e.getMouseX(), e.getMouseY(), e.getButton());
            for (var l : ScreenMouseEvents.allowMouseRelease(e.getScreen()).listeners()) if (!l.allow(e.getScreen(), input)) { e.setCanceled(true); break; }
        });
        bus.addListener((ScreenEvent.MouseScrolled.Pre e) -> {
            for (var l : ScreenMouseEvents.allowMouseScroll(e.getScreen()).listeners()) if (!l.allow(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getScrollDeltaX(), e.getScrollDeltaY())) { e.setCanceled(true); break; }
        });
        bus.addListener((ScreenEvent.KeyPressed.Pre e) -> {
            var input = new KeyEvent(e.getKeyCode(), e.getScanCode(), e.getModifiers());
            for (var l : ScreenKeyboardEvents.allowKeyPress(e.getScreen()).listeners()) if (!l.allow(e.getScreen(), input)) { e.setCanceled(true); break; }
        });
        bus.addListener((ScreenEvent.KeyPressed.Post e) -> ScreenKeyboardEvents.afterKeyPress(e.getScreen()).listeners().forEach(l -> l.run(e.getScreen(), new KeyEvent(e.getKeyCode(), e.getScanCode(), e.getModifiers()))));
        bus.addListener((ChunkEvent.Load e) -> {
            if (e.getLevel() instanceof ClientLevel level && e.getChunk() instanceof LevelChunk chunk)
                ClientChunkEvents.CHUNK_LOAD.listeners().forEach(l -> l.load(level, chunk));
        });
        bus.addListener((ItemTooltipEvent e) -> ItemTooltipCallback.EVENT.listeners().forEach(l -> l.tooltip(e.getItemStack(), e.getContext(), e.getFlags(), e.getToolTip())));
    }
}

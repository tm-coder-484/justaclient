package dev.justaclient.debug;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.justaclient.Justaclient;
import dev.justaclient.render.FullScreen;
import dev.justaclient.render.JacPipelines;
import dev.justaclient.ui.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Opt-in live checks for stale frames, framebuffer clipping, and real screen close/return paths. */
public final class MenuRegression {
    private enum Phase { WAIT, MARK, CHECK, CLOSE, RETURN, NEXT, DONE }
    private record Visit(String name, Function<Screen, Screen> open) {}
    private static final Map<String, Boolean> results = new LinkedHashMap<>();
    private static final List<Visit> visits = new ArrayList<>();
    private static final int[] scales = {2, 4};
    private static Path out;
    private static Phase phase = Phase.WAIT;
    private static Screen root, active;
    private static String label;
    private static long due;
    private static int visit = -1, scale, originalScale;

    public static void init() {
        String path = System.getProperty("justaclient.menuRegression");
        if (path == null) return;
        out = Path.of(path).toAbsolutePath();
        visits.add(new Visit("missing-background-control", parent -> new Screen(net.minecraft.network.chat.Component.literal("Repaint negative control")) {
            @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float delta) {}
            @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
        }));
        visits.add(new Visit("settings", parent -> ClickGuiScreen.tourAt(parent, ClickGuiScreen.tourPage("theme"))));
        visits.add(new Visit("mods", ModsScreen::new));
        visits.add(new Visit("ring", RingMenuScreen::new));
        visits.add(new Visit("hud-editor", HudEditorScreen::new));
        visits.add(new Visit("neoforge-mods", net.neoforged.neoforge.client.gui.ModListScreen::new));
        visits.add(new Visit("neoforge-client-config", parent -> {
            var owner = net.neoforged.fml.ModList.get().getModContainerById("justaclient").orElseThrow();
            return owner.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class).orElseThrow().createScreen(owner, parent);
        }));
        if (net.neoforged.fml.ModList.get().isLoaded("create"))
            visits.add(new Visit("create-config", parent -> dev.justaclient.mods.ModConfigScreens.open("create", parent)));
        NeoForge.EVENT_BUS.addListener(MenuRegression::rendered);
    }

    public static void tick(Minecraft mc) {
        if (out == null || phase == Phase.DONE) return;
        if (phase == Phase.WAIT) {
            if (mc.screen instanceof CrashHelperScreen) { mc.screen.onClose(); return; }
            if (!mc.isGameLoadFinished() || mc.getOverlay() != null || !(mc.screen instanceof TitleScreen || mc.screen instanceof JacTitleScreen)) return;
            if (due == 0) { due = System.currentTimeMillis() + 2000; return; }
            if (System.currentTimeMillis() < due) return;
            originalScale = mc.options.guiScale().get();
            framebufferChecks(mc);
            startScale(mc);
        } else if (phase == Phase.CLOSE) {
            active.onClose();
            phase = Phase.RETURN;
            due = System.currentTimeMillis() + 3000;
        } else if (phase == Phase.RETURN) {
            if (mc.screen != root && System.currentTimeMillis() < due) return;
            check("scale" + scales[scale] + "-close-" + visits.get(visit).name, mc.screen == root);
            if (mc.screen != root) mc.setScreen(root);
            active = root;
            label = "return-from-" + visits.get(visit).name;
            markNext();
        } else if (phase == Phase.NEXT) {
            if (++visit == visits.size()) {
                if (++scale == scales.length) { finish(mc); return; }
                visit = -1;
                startScale(mc);
                return;
            }
            label = visits.get(visit).name;
            active = Objects.requireNonNull(visits.get(visit).open.apply(root), "No screen for " + label);
            mc.setScreen(active);
            markNext();
        }
    }

    private static void startScale(Minecraft mc) {
        mc.options.guiScale().set(scales[scale]);
        mc.resizeDisplay();
        root = active = new JacTitleScreen();
        mc.setScreen(root);
        label = "title";
        markNext();
    }

    private static void markNext() { phase = Phase.MARK; due = System.currentTimeMillis() + 700; }

    private static void rendered(ScreenEvent.Render.Post event) {
        if (out == null || event.getScreen() != active) return;
        if (phase == Phase.MARK && System.currentTimeMillis() >= due) {
            event.getGuiGraphics().fill(1, 1, 5, 5, 0xFFFF00FF);
            event.getGuiGraphics().flush();
            phase = Phase.CHECK;
        } else if (phase == Phase.CHECK) {
            var mc = Minecraft.getInstance();
            event.getGuiGraphics().flush();
            int p = (int) (3 * mc.getWindow().getGuiScale());
            boolean stale = pixel(mc.getMainRenderTarget(), p, mc.getMainRenderTarget().height - p - 1) == 0xFFFF00FF;
            check("scale" + scales[scale] + "-repaint-" + label, label.equals("missing-background-control") ? stale : !stale);
            try { Files.createDirectories(out); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
            Screenshot.grab(out.toFile(), "scale" + scales[scale] + "-" + label + ".png", mc.getMainRenderTarget(), message -> {});
            phase = active == root ? Phase.NEXT : Phase.CLOSE;
        }
    }

    private static void framebufferChecks(Minecraft mc) {
        var source = new TextureTarget(16, 16, true, Minecraft.ON_OSX);
        var target = new TextureTarget(16, 16, true, Minecraft.ON_OSX);
        try {
            source.setClearColor(1, 0, 0, 1); FullScreen.clear(source);
            target.setClearColor(0, 0, 1, 1); FullScreen.clear(target);
            RenderSystem.enableScissor(0, 0, 1, 1);
            FullScreen.copy(source, target);
            check("framebuffer-copy-outside-gui-clip", pixel(target, 8, 8) == 0xFFFF0000);
            check("framebuffer-copy-restores-gui-clip", GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
            FullScreen.clear(target);
            JacPipelines.BLUR_DOWN.setSampler("InSampler", source.getColorTextureId());
            RenderSystem.enableBlend(); RenderSystem.disableDepthTest(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
            FullScreen.draw(JacPipelines.BLUR_DOWN, target, false);
            check("blur-pass-outside-gui-clip", pixel(target, 8, 8) == 0xFFFF0000);
            check("blur-pass-restores-gui-clip", GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
            check("blur-pass-restores-render-state", GL11.glIsEnabled(GL11.GL_BLEND) && !GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                && !GL11.glIsEnabled(GL11.GL_CULL_FACE) && !GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
            target.setClearColor(0, 0, 0, 0);
            RenderSystem.colorMask(false, false, false, false); RenderSystem.depthMask(false);
            FullScreen.clear(target);
            check("clear-reused-target-outside-gui-clip", pixel(target, 8, 8) == 0);
            check("clear-restores-depth-write-mask", !GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        } finally {
            RenderSystem.disableScissor(); RenderSystem.colorMask(true, true, true, true); RenderSystem.depthMask(true);
            source.destroyBuffers(); target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static int pixel(RenderTarget target, int x, int y) {
        int previous = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try (var stack = MemoryStack.stackPush()) {
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            var rgba = stack.malloc(4);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
            return (rgba.get(3) & 255) << 24 | (rgba.get(0) & 255) << 16 | (rgba.get(1) & 255) << 8 | rgba.get(2) & 255;
        } finally { com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous); }
    }

    private static void check(String name, boolean pass) {
        results.put(name, pass);
        Justaclient.LOGGER.info("[menu-regression] {} {}", pass ? "PASS" : "FAIL", name);
    }

    private static void finish(Minecraft mc) {
        try { Files.writeString(out.resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(results)); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
        mc.options.guiScale().set(originalScale); mc.options.save();
        phase = Phase.DONE;
        if (results.containsValue(false)) throw new IllegalStateException("Menu/framebuffer regression failed: " + out);
        Justaclient.LOGGER.info("[menu-regression] ALL PASSED ({} checks)", results.size());
        mc.stop();
    }
}

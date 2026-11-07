package dev.justaclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.HandGlowFeature;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Draws the first-person hand into its own target instead of the main one, then lays it back over the frame through a
 * shader that adds the glow and colour style. Only the hand goes through here, so the world is never affected.
 */
public final class HandFx {
	private static @Nullable TextureTarget target;
	private static boolean capturing;
	private static final long START = System.nanoTime();

	private HandFx() {
	}

	/** Redirects the hand's draws. Called just before vanilla draws the hand. */
	public static void begin() {
		HandGlowFeature glow = Features.get(HandGlowFeature.class);
		if (!glow.isEnabled() || Shaders.packInUse()) {
			return;
		}
		glow.cost.begin();
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		if (target == null || target.width != main.width || target.height != main.height) {
			if (target != null) {
				target.destroyBuffers();
			}
			target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
		}
		target.setClearColor(0,0,0,0); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
		
		
		capturing = true;
		glow.cost.end();
	}

	/** Restores normal output and composites the captured hand. Called right after vanilla has drawn it. */
	public static void end() {
		if (!capturing) {
			return;
		}
		capturing = false;
		Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
		
		HandGlowFeature glow = Features.get(HandGlowFeature.class);
		glow.cost.begin();
        int c0=glow.colorStart(),c1=glow.colorEnd();
        float scale=(float)Math.max(1,Minecraft.getInstance().getWindow().getGuiScale());
        var shader=JacPipelines.HAND_COMPOSITE;
        shader.safeGetUniform("GlowColor").set(r(c0),g(c0),b(c0),glow.glowStrength());
        shader.safeGetUniform("Accent2").set(r(c1),g(c1),b(c1),glow.styleAmount());
        shader.safeGetUniform("Params").set(glow.styleIndex(),glow.glowRadius()*scale,(System.nanoTime()-START)/1e9f,Math.max(1f,scale*0.75f));
        shader.setSampler("HandSampler",target.getColorTextureId());
        FullScreen.draw(shader,Minecraft.getInstance().getMainRenderTarget(),true);
		glow.cost.end();
	}

	private static float r(int c) {
		return ((c >> 16) & 0xFF) / 255f;
	}

	private static float g(int c) {
		return ((c >> 8) & 0xFF) / 255f;
	}

	private static float b(int c) {
		return (c & 0xFF) / 255f;
	}
}

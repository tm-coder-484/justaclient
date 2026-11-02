package dev.justaclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** A dynamic texture sampled with linear filtering and clamped edges, for photos such as album art. */
public final class SmoothTexture extends DynamicTexture {
	public SmoothTexture(Supplier<String> label, int width, int height) {
		super(label, width, height, true);
		this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}
}

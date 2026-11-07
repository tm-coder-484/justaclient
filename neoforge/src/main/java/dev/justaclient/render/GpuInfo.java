package dev.justaclient.render;

import com.mojang.blaze3d.platform.GLX;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;

/** The graphics card and processor, for the Dev Console. Each Minecraft version reads these differently. */
public final class GpuInfo {
	private GpuInfo() {
	}

	/** Label and value pairs. */
	public static List<String[]> describe() {
		List<String[]> out = new ArrayList<>();
		try {
            out.add(new String[]{"GPU",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)});
            out.add(new String[]{"Vendor",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR)});
            out.add(new String[]{"Backend","OpenGL "+org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION)});
		} catch (RuntimeException e) {
			out.add(new String[] {"GPU", "unknown"});
		}
		String cpu;
		try {
			cpu = GLX._getCpuInfo();
		} catch (RuntimeException | LinkageError e) {
			cpu = "unknown";
		}
		out.add(new String[] {"CPU", cpu + " (" + Runtime.getRuntime().availableProcessors() + " threads)"});
		return out;
	}
}

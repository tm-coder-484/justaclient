package dev.justaclient.render;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.systems.GpuDevice;
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
			GpuDevice device = RenderSystem.getDevice();
			out.add(new String[] {"GPU", device.getRenderer()});
			out.add(new String[] {"Vendor", device.getVendor()});
			out.add(new String[] {"Backend", device.getBackendName() + " " + device.getVersion()});
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

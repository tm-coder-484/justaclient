package dev.justaclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
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
			DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
			out.add(new String[] {"GPU", info.name()});
			out.add(new String[] {"Vendor", info.vendorName()});
			out.add(new String[] {"Backend", info.backendName() + " " + info.driverInfo()});
			out.add(new String[] {"Device type", String.valueOf(info.type())});
		} catch (RuntimeException e) {
			out.add(new String[] {"GPU", "unknown"});
		}
		out.add(new String[] {"CPU", System.getProperty("os.arch") + " (" + Runtime.getRuntime().availableProcessors() + " threads)"});
		return out;
	}
}

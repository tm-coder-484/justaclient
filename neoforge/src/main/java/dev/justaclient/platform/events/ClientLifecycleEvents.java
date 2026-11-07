package dev.justaclient.platform.events;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
public final class ClientLifecycleEvents {
    public static final Event<Consumer<Minecraft>> CLIENT_STARTED = new Event<>(), CLIENT_STOPPING = new Event<>();
}

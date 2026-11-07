package dev.justaclient.platform.events;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
public final class ClientTickEvents { public static final Event<Consumer<Minecraft>> END_CLIENT_TICK = new Event<>(); }

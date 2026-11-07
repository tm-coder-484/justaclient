package dev.justaclient.platform.events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
public final class ClientPlayConnectionEvents {
    public interface Join { void join(ClientPacketListener handler, Object sender, Minecraft mc); }
    public interface Disconnect { void leave(ClientPacketListener handler, Minecraft mc); }
    public static final Event<Join> JOIN = new Event<>();
    public static final Event<Disconnect> DISCONNECT = new Event<>();
}

package dev.justaclient.platform.events;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
public final class ClientChunkEvents {
    public interface Load { void load(ClientLevel level, LevelChunk chunk); }
    public static final Event<Load> CHUNK_LOAD = new Event<>();
}

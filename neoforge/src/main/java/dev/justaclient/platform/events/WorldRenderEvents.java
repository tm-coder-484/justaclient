package dev.justaclient.platform.events;
import java.util.function.Consumer;
public final class WorldRenderEvents {
    public interface Outline { boolean allow(Object context,Object state); }
    public static final Event<Consumer<WorldRenderContext>> END_MAIN=new Event<>();
    public static final Event<Outline> BEFORE_BLOCK_OUTLINE=new Event<>();
}

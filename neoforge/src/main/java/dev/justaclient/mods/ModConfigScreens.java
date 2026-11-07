package dev.justaclient.mods;
import dev.justaclient.Justaclient;
import dev.justaclient.ui.ClickGuiScreen;
import dev.justaclient.ui.ModsScreen;
import java.lang.ref.WeakReference;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jspecify.annotations.Nullable;
/** Uses NeoForge config-screen extension points for installed mods. */
public final class ModConfigScreens {
    private static WeakReference<Screen> opened=new WeakReference<>(null),openedFrom=new WeakReference<>(null);
    public static boolean has(String id) {
        return id.equals(Justaclient.MOD_ID) || id.equals("minecraft") || ModList.get().getModContainerById(id).flatMap(m->m.getCustomExtension(IConfigScreenFactory.class)).isPresent();
    }
    public static @Nullable Screen open(String id,Screen parent) {
        if(id.equals(Justaclient.MOD_ID)) return ClickGuiScreen.open(parent);
        if(id.equals("minecraft")) return new net.minecraft.client.gui.screens.options.OptionsScreen(parent,net.minecraft.client.Minecraft.getInstance().options);
        try {
            var container=ModList.get().getModContainerById(id);
            var factory=container.flatMap(m->m.getCustomExtension(IConfigScreenFactory.class));
            if(factory.isEmpty()) return null;
            Screen screen=factory.get().createScreen(container.get(),parent);
            if(screen!=null) { opened=new WeakReference<>(screen);openedFrom=new WeakReference<>(parent); }
            return screen;
        } catch (RuntimeException | LinkageError e) { Justaclient.LOGGER.warn("Could not open settings for {}",id,e);return null; }
    }
    public static @Nullable Screen closedTo(@Nullable Screen closing) {
        if(closing==null || opened.get()!=closing) return null;
        Screen back=openedFrom.get();opened.clear();openedFrom.clear();return back;
    }
    public static Screen modsScreen(@Nullable Screen parent) { return new ModsScreen(parent); }
    public static Component modsButtonText() { return Component.literal("Mods"); }
}

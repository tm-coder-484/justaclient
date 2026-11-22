package dev.justaclient.mods;
import dev.justaclient.Justaclient;
import dev.justaclient.ui.ClickGuiScreen;
import dev.justaclient.ui.ModsScreen;
import dev.justaclient.platform.PlatformLoader;
import java.lang.ref.WeakReference;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jspecify.annotations.Nullable;
/** Uses NeoForge config-screen extension points for installed mods. */
public final class ModConfigScreens {
    private record ConfigOwner(net.neoforged.fml.ModContainer container,IConfigScreenFactory factory) {}
    private static WeakReference<Screen> opened=new WeakReference<>(null),openedFrom=new WeakReference<>(null);
    public static boolean has(String id) {
        if(id.equals(Justaclient.MOD_ID) || id.equals("minecraft")) return true;
        return configOwner(id).isPresent();
    }
    private static Optional<ConfigOwner> configOwner(String id) {
        var owner=ModList.get().getModContainerById(id);
        if(owner.isEmpty()) return Optional.empty();
        var direct=factory(owner.get());
        if(direct.isPresent()) return Optional.of(new ConfigOwner(owner.get(),direct.get()));
        try {
            var entry=PlatformLoader.getInstance().getModContainer(id);
            if(entry.isPresent()) for(var child:entry.get().getContainedMods()) {
                var nested=configOwner(child.getMetadata().getId());
                if(nested.isPresent()) return nested;
            }
        } catch(RuntimeException | LinkageError | java.util.ServiceConfigurationError e) { Justaclient.LOGGER.warn("Could not inspect bundled settings for {}",id,e); }
        return Optional.empty();
    }
    static Optional<IConfigScreenFactory> factory(net.neoforged.fml.ModContainer container) {
        try {
            return container.getCustomExtension(IConfigScreenFactory.class);
        } catch (RuntimeException | LinkageError | java.util.ServiceConfigurationError e) {
            Justaclient.LOGGER.warn("Could not inspect settings for {}",container.getModId(),e);
            return Optional.empty();
        }
    }
    public static @Nullable Screen open(String id,Screen parent) {
        if(id.equals(Justaclient.MOD_ID)) return ClickGuiScreen.open(parent);
        if(id.equals("minecraft")) return new net.minecraft.client.gui.screens.options.OptionsScreen(parent,net.minecraft.client.Minecraft.getInstance().options);
        try {
            var owner=configOwner(id);
            if(owner.isEmpty()) return null;
            Screen screen=owner.get().factory.createScreen(owner.get().container,parent);
            if(screen!=null) { opened=new WeakReference<>(screen);openedFrom=new WeakReference<>(parent); }
            return screen;
        } catch (RuntimeException | LinkageError | java.util.ServiceConfigurationError e) { Justaclient.LOGGER.warn("Could not open settings for {}",id,e);return null; }
    }
    public static @Nullable Screen closedTo(@Nullable Screen closing) {
        if(closing==null || opened.get()!=closing) return null;
        Screen back=openedFrom.get();opened.clear();openedFrom.clear();return back;
    }
    public static Screen modsScreen(@Nullable Screen parent) { return new ModsScreen(parent); }
    public static Component modsButtonText() { return Component.literal("Mods"); }
}

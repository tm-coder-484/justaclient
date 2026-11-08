package dev.justaclient.mods;

import java.lang.reflect.Proxy;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModConfigScreensTest {
    private static ModContainer container() {
        IModInfo info = (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(), new Class<?>[] {IModInfo.class},
            (proxy, method, args) -> "fixture");
        return new ModContainer(info) {
            @Override public net.neoforged.bus.api.IEventBus getEventBus() { return null; }
        };
    }
    @Test void workingConfigFactoriesRemainAvailable() {
        var mod = container();
        IConfigScreenFactory factory = (owner, parent) -> parent;
        mod.registerExtensionPoint(IConfigScreenFactory.class, factory);
        assertSame(factory, ModConfigScreens.factory(mod).orElseThrow());
    }
    @Test void noFactoryIsOptional() { assertTrue(ModConfigScreens.factory(container()).isEmpty()); }
    @Test void brokenLazyFactoryDoesNotCrashModsScreen() {
        var mod = container();
        mod.registerExtensionPoint(IConfigScreenFactory.class, (java.util.function.Supplier<IConfigScreenFactory>) () -> { throw new IllegalStateException("Uninitialized config"); });
        assertTrue(ModConfigScreens.factory(mod).isEmpty());
    }
    @Test void missingOptionalConfigDependencyDoesNotCrashModsScreen() {
        var mod = container();
        mod.registerExtensionPoint(IConfigScreenFactory.class, (java.util.function.Supplier<IConfigScreenFactory>) () -> { throw new NoClassDefFoundError("optional/config/Dependency"); });
        assertTrue(ModConfigScreens.factory(mod).isEmpty());
    }
    @Test void brokenConfigServiceProviderDoesNotCrashModsScreen() {
        var mod = container();
        mod.registerExtensionPoint(IConfigScreenFactory.class, (java.util.function.Supplier<IConfigScreenFactory>) () -> { throw new java.util.ServiceConfigurationError("Broken optional config provider"); });
        assertTrue(ModConfigScreens.factory(mod).isEmpty());
    }
}

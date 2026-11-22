package dev.justaclient.platform;

import java.lang.reflect.Proxy;
import java.util.*;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModInfo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformLoaderMetadataTest {
    private static PlatformLoader.ModMetadata metadata(Object authors) {
        IConfigurable config = (IConfigurable) Proxy.newProxyInstance(IConfigurable.class.getClassLoader(), new Class<?>[] {IConfigurable.class},
            (proxy, method, args) -> method.getName().equals("getConfigElement") ? Optional.ofNullable(authors) : List.of());
        IModInfo info = (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(), new Class<?>[] {IModInfo.class},
            (proxy, method, args) -> method.getName().equals("getConfig") ? config : "fixture");
        return new PlatformLoader.ModMetadata(info);
    }

    @Test void commaSeparatedAuthorsRemainReadable() {
        assertEquals(List.of("Alice", "Bob"), metadata(" Alice, , Bob ").getAuthors().stream().map(PlatformLoader.Person::getName).toList());
    }
    @Test void listAuthorsDoNotCrashModsScreen() {
        assertEquals(List.of("Alice", "Bob"), metadata(List.of(" Alice ", "Bob", 42)).getAuthors().stream().map(PlatformLoader.Person::getName).toList());
    }
    @Test void absentAuthorsAreOptional() { assertEquals(List.of(), metadata(null).getAuthors()); }
    @Test void unfamiliarAuthorsDoNotCrashModsScreen() { assertEquals(List.of(), metadata(Map.of("unexpected", "value")).getAuthors()); }
}

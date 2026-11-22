package dev.justaclient.platform;

import java.lang.reflect.Proxy;
import java.util.*;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformLoaderOwnershipTest {
    private static class File {
        final List<IModInfo> mods=new ArrayList<>();
        final IModFileInfo metadata;
        IModFile file;
        File(File parent) {
            metadata=(IModFileInfo)Proxy.newProxyInstance(IModFileInfo.class.getClassLoader(),new Class<?>[]{IModFileInfo.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getMods" -> mods;
                    case "getFile" -> file;
                    default -> throw new AssertionError("Unexpected metadata call: "+method.getName());
                });
            file=(IModFile)Proxy.newProxyInstance(IModFile.class.getClassLoader(),new Class<?>[]{IModFile.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getModInfos" -> mods;
                    case "getDiscoveryAttributes" -> ModFileDiscoveryAttributes.DEFAULT.withParent(parent==null?null:parent.file);
                    default -> throw new AssertionError("Archive I/O is unnecessary: "+method.getName());
                });
        }
        IModInfo mod(String id) {
            IModInfo mod=(IModInfo)Proxy.newProxyInstance(IModInfo.class.getClassLoader(),new Class<?>[]{IModInfo.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getModId" -> id;
                    case "getOwningFile" -> metadata;
                    default -> throw new AssertionError("Unexpected mod call: "+method.getName());
                });
            mods.add(mod);return mod;
        }
    }
    @Test void standaloneModHasNoParent() {
        var file=new File(null);var mod=file.mod("standalone");
        assertTrue(PlatformLoader.parentOf(mod).isEmpty());
    }
    @Test void multipleModIdsInOneJarShareTheFirstEntry() {
        var file=new File(null);var first=file.mod("first");var second=file.mod("second");
        assertSame(first,PlatformLoader.parentOf(second).orElseThrow());
        assertEquals(List.of("second"),new PlatformLoader.ModMetadata(first).getProvides());
    }
    @Test void bundledModsUseLoaderOwnershipWithoutOpeningArchives() {
        var outer=new File(null);var bundle=outer.mod("bundle");
        var inner=new File(outer);var child=inner.mod("child");var sibling=inner.mod("child_second");
        var nested=new File(inner);var grandchild=nested.mod("grandchild");
        var all=List.of(bundle,child,sibling,grandchild);
        assertSame(bundle,PlatformLoader.parentOf(child).orElseThrow());
        assertEquals(List.of(child),PlatformLoader.childrenOf(bundle,all));
        assertEquals(List.of(sibling,grandchild),PlatformLoader.childrenOf(child,all));
    }
    @Test void librariesWithoutModIdsDoNotBreakParentDiscovery() {
        var outer=new File(null);var bundle=outer.mod("bundle");
        var library=new File(outer);var inner=new File(library);var child=inner.mod("child");
        assertSame(bundle,PlatformLoader.parentOf(child).orElseThrow());
    }
    @Test void brokenMetadataDoesNotInvalidateOtherBundles() {
        var outer=new File(null);var bundle=outer.mod("bundle");
        var inner=new File(outer);var child=inner.mod("child");
        IModInfo broken=(IModInfo)Proxy.newProxyInstance(IModInfo.class.getClassLoader(),new Class<?>[]{IModInfo.class},
            (proxy,method,args)->{throw new IllegalStateException("Broken third-party metadata");});
        assertEquals(List.of(child),PlatformLoader.childrenOf(bundle,List.of(bundle,broken,child)));
    }
}

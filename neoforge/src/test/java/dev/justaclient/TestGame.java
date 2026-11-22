package dev.justaclient;
import java.nio.file.*;
public final class TestGame {
    private static Path dir;
    public static synchronized Path dir() {
        if(dir==null)try {
            dir=Files.createTempDirectory("jac-neoforge-test");
            net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(dir);
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
        return dir;
    }
    public static Path config() { dir();return dev.justaclient.platform.PlatformLoader.getInstance().getConfigDir().resolve("justaclient"); }
}

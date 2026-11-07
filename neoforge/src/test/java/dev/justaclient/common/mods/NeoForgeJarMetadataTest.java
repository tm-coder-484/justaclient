package dev.justaclient.common.mods;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class NeoForgeJarMetadataTest {
    @TempDir Path temp;
    @Test void readsMultipleModsAndRequiredDependencies() throws Exception {
        Path jar=temp.resolve("bundle.jar.disabled");
        try(var zip=new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            zip.write("""
                modLoader="javafml"
                loaderVersion="[4,)"
                [[mods]]
                modId="main_mod"
                displayName="Main mod"
                version="1.2.3"
                [[mods]]
                modId="bundled_api"
                version="1.0"
                [[dependencies.main_mod]]
                modId="neoforge"
                type="required"
                versionRange="[21.1,)"
                [[dependencies.main_mod]]
                modId="bundled_api"
                type="required"
                [[dependencies.main_mod]]
                modId="optional_mod"
                type="optional"
                """.getBytes(StandardCharsets.UTF_8));
        }
        var metadata=JarMetadata.read(jar);
        assertNotNull(metadata);
        assertEquals("Main mod",metadata.name());
        assertEquals(Set.of("main_mod","bundled_api"),metadata.providedIds());
        assertEquals(Set.of("neoforge"),metadata.requiredIds());
    }
    @Test void readsForgeMandatoryDependenciesAndManifestVersion() throws Exception {
        Path jar=temp.resolve("legacy.jar");
        try(var zip=new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            zip.write("""
                [[mods]]
                modId="legacy"
                version="${file.jarVersion}"
                [[dependencies.legacy]]
                modId="minecraft"
                mandatory=true
                """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\r\nImplementation-Version: 2.4.6\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        }
        var metadata=JarMetadata.read(jar);
        assertNotNull(metadata);
        assertEquals("2.4.6",metadata.version());
        assertEquals(Set.of("minecraft"),metadata.requiredIds());
    }
}

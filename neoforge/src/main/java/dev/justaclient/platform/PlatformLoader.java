package dev.justaclient.platform;

import java.nio.file.*;
import java.util.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

/** Loader metadata used by the client, backed by NeoForge's installed mod list. */
public final class PlatformLoader {
    private static final PlatformLoader INSTANCE = new PlatformLoader();
    public static PlatformLoader getInstance() { return INSTANCE; }
    public Path getGameDir() { return FMLPaths.GAMEDIR.get(); }
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    public boolean isDevelopmentEnvironment() { return !FMLLoader.isProduction(); }
    public boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
    public Collection<ModContainer> getAllMods() { return ModList.get().getMods().stream().map(ModContainer::new).toList(); }
    public Optional<ModContainer> getModContainer(String id) {
        return ModList.get().getMods().stream().filter(i -> i.getModId().equals(id)).findFirst().map(ModContainer::new);
    }
    public record ModContainer(IModInfo info) {
        public ModMetadata getMetadata() { return new ModMetadata(info); }
        public ModOrigin getOrigin() { return new ModOrigin(List.of(info.getOwningFile().getFile().getFilePath())); }
        public Optional<ModContainer> getContainingMod() { return Optional.empty(); }
        public Collection<ModContainer> getContainedMods() { return List.of(); }
        public Optional<Path> findPath(String path) {
            Path found = info.getOwningFile().getFile().findResource(path);
            return Files.exists(found) ? Optional.of(found) : Optional.empty();
        }
    }
    public record ModOrigin(List<Path> getPaths) {
        public enum Kind { PATH, NESTED }
        public Kind getKind() { return getPaths.getFirst().getFileSystem().equals(FileSystems.getDefault()) ? Kind.PATH : Kind.NESTED; }
    }
    public record Version(String getFriendlyString) { }
    public record Person(String getName) { }
    public record ContactInformation(Map<String, String> values) {
        public Optional<String> get(String key) { return Optional.ofNullable(values.get(key)); }
    }
    public record ModDependency(String getModId, Kind getKind) {
        public enum Kind { DEPENDS, OPTIONAL }
    }
    public record ModMetadata(IModInfo info) {
        public String getId() { return info.getModId(); }
        public String getName() { return info.getDisplayName(); }
        public Version getVersion() { return new Version(info.getVersion().toString()); }
        public String getDescription() { return info.getDescription(); }
        public String getType() { return Set.of("minecraft", "neoforge", "java", "fml").contains(getId()) ? "builtin" : "mod"; }
        public List<String> getProvides() { return List.of(); }
        public List<Person> getAuthors() {
            return info.getConfig().<String>getConfigElement("authors").map(s -> Arrays.stream(s.split(",")).map(String::trim).map(Person::new).toList()).orElse(List.of());
        }
        public ContactInformation getContact() {
            Map<String, String> map = new HashMap<>();
            info.getModURL().ifPresent(url -> map.put("homepage", url.toString()));
            info.getOwningFile().getConfig().<String>getConfigElement("issueTrackerURL").ifPresent(url -> map.put("issues", url));
            return new ContactInformation(map);
        }
        public Optional<String> getIconPath(int size) { return info.getLogoFile(); }
        public List<ModDependency> getDependencies() {
            return info.getDependencies().stream().map(d -> new ModDependency(d.getModId(), d.getType() == IModInfo.DependencyType.REQUIRED ? ModDependency.Kind.DEPENDS : ModDependency.Kind.OPTIONAL)).toList();
        }
        public boolean library() { return info.getOwningFile().getFile().getType() == net.neoforged.neoforgespi.locating.IModFile.Type.LIBRARY; }
    }
}

package dev.justaclient.mods;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Most mods hand their settings screen over through Mod Menu's API: a {@code modmenu} entry point class that implements
 * {@code com.terraformersmc.modmenu.api.ModMenuApi} and returns a {@code ConfigScreenFactory}. Without Mod Menu
 * installed those interfaces do not exist, so the entry point class cannot even load and the Mods panel would show
 * "No settings" for nearly every mod.
 *
 * <p>When Mod Menu is not installed this writes small stand-ins of the five API types at launch and puts them on the
 * game's class path, so those entry points load and their factories can be called. With Mod Menu installed nothing is
 * done and Mod Menu's own classes are used.
 */
public final class ModMenuStubs implements PreLaunchEntrypoint {
	private static final String PKG = "com/terraformersmc/modmenu/api/";
	private static final String API = PKG + "ModMenuApi";
	private static final String FACTORY = PKG + "ConfigScreenFactory";
	private static final String CHECKER = PKG + "UpdateChecker";
	private static final String INFO = PKG + "UpdateInfo";
	private static final String CHANNEL = PKG + "UpdateChannel";
	// Not Justaclient.LOGGER: touching that class this early would load game classes before the game starts.
	private static final Logger LOGGER = LoggerFactory.getLogger("justaclient");
	private static final String HOOKS = "dev/justaclient/mods/ModConfigScreens";

	/** True once the stand-ins are on the class path (false with Mod Menu installed). */
	private static boolean installed;

	@Override
	public void onPreLaunch() {
		install();
	}

	public static boolean installed() {
		return installed;
	}

	static synchronized void install() {
		if (installed || FabricLoader.getInstance().isModLoaded("modmenu")) {
			return;
		}
		ClassLoader loader = ModMenuStubs.class.getClassLoader();
		if (loader.getResource(API + ".class") != null) {
			// Some other jar already carries the API.
			return;
		}
		try {
			// 26.3 is not obfuscated, so these are the runtime names. Nothing is loaded here.
			String screen = "Lnet/minecraft/client/gui/screens/Screen;";
			String component = "Lnet/minecraft/network/chat/Component;";
			Path dir = FabricLoader.getInstance().getGameDir().resolve("config/justaclient/cache/modmenu-api");
			for (Map.Entry<String, byte[]> e : classes(screen, component).entrySet()) {
				Path file = dir.resolve(e.getKey() + ".class");
				Files.createDirectories(file.getParent());
				if (!Files.exists(file) || !Arrays.equals(Files.readAllBytes(file), e.getValue())) {
					Files.write(file, e.getValue());
				}
			}
			FabricLauncherBase.getLauncher().addToClassPath(dir);
			installed = true;
			LOGGER.info("Mod Menu is not installed; mod settings screens are reached through Justaclient's stand-in");
		} catch (Throwable t) {
			LOGGER.warn("Could not set up the Mod Menu stand-in; most mods will show no settings", t);
		}
	}

	/** The stand-in class files by internal name. */
	static Map<String, byte[]> classes(String screen, String component) {
		Map<String, byte[]> out = new LinkedHashMap<>();
		out.put(API, api(screen, component));
		out.put(FACTORY, iface(FACTORY, w -> abstractMethod(w, "create", "(" + screen + ")" + screen)));
		out.put(CHECKER, iface(CHECKER, w -> abstractMethod(w, "checkForUpdates", "()L" + INFO + ";")));
		out.put(INFO, iface(INFO, w -> {
			abstractMethod(w, "isUpdateAvailable", "()Z");
			abstractMethod(w, "getDownloadLink", "()Ljava/lang/String;");
			abstractMethod(w, "getUpdateChannel", "()L" + CHANNEL + ";");
			MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "getUpdateMessage", "()" + component, null, null);
			m.visitCode();
			m.visitInsn(Opcodes.ACONST_NULL);
			m.visitInsn(Opcodes.ARETURN);
			m.visitMaxs(0, 0);
			m.visitEnd();
		}));
		out.put(CHANNEL, channel());
		return out;
	}

	private static byte[] api(String screen, String component) {
		return iface(API, w -> {
			MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "createModsScreen", "(" + screen + ")" + screen, null, null);
			m.visitCode();
			m.visitVarInsn(Opcodes.ALOAD, 0);
			m.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "modsScreen", "(" + screen + ")" + screen, false);
			m.visitInsn(Opcodes.ARETURN);
			m.visitMaxs(0, 0);
			m.visitEnd();

			m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "createModsButtonText", "()" + component, null, null);
			m.visitCode();
			m.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "modsButtonText", "()" + component, false);
			m.visitInsn(Opcodes.ARETURN);
			m.visitMaxs(0, 0);
			m.visitEnd();

			returnsNull(w, "getModConfigScreenFactory", "()L" + FACTORY + ";");
			returnsNull(w, "getUpdateChecker", "()L" + CHECKER + ";");
			returnsEmptyMap(w, "getProvidedConfigScreenFactories");
			returnsEmptyMap(w, "getProvidedUpdateCheckers");

			m = w.visitMethod(Opcodes.ACC_PUBLIC, "attachModpackBadges", "(Ljava/util/function/Consumer;)V", null, null);
			m.visitCode();
			m.visitInsn(Opcodes.RETURN);
			m.visitMaxs(0, 0);
			m.visitEnd();
		});
	}

	private static byte[] iface(String name, java.util.function.Consumer<ClassWriter> body) {
		ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		w.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, name, null, "java/lang/Object", null);
		body.accept(w);
		w.visitEnd();
		return w.toByteArray();
	}

	private static void abstractMethod(ClassWriter w, String name, String desc) {
		w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, desc, null, null).visitEnd();
	}

	private static void returnsNull(ClassWriter w, String name, String desc) {
		MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
		m.visitCode();
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitInsn(Opcodes.ARETURN);
		m.visitMaxs(0, 0);
		m.visitEnd();
	}

	private static void returnsEmptyMap(ClassWriter w, String name) {
		MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, name, "()Ljava/util/Map;", null, null);
		m.visitCode();
		m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Map", "of", "()Ljava/util/Map;", true);
		m.visitInsn(Opcodes.ARETURN);
		m.visitMaxs(0, 0);
		m.visitEnd();
	}

	/** {@code enum UpdateChannel { ALPHA, BETA, RELEASE; static UpdateChannel getUserPreference() { return RELEASE; } }} */
	private static byte[] channel() {
		String self = "L" + CHANNEL + ";";
		String arr = "[" + self;
		String[] names = {"ALPHA", "BETA", "RELEASE"};
		ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		w.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, CHANNEL, "Ljava/lang/Enum<" + self + ">;",
			"java/lang/Enum", null);
		for (String n : names) {
			w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, n, self, null, null).visitEnd();
		}
		w.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", arr, null, null).visitEnd();

		MethodVisitor m = w.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
		m.visitCode();
		m.visitVarInsn(Opcodes.ALOAD, 0);
		m.visitVarInsn(Opcodes.ALOAD, 1);
		m.visitVarInsn(Opcodes.ILOAD, 2);
		m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
		m.visitInsn(Opcodes.RETURN);
		m.visitMaxs(0, 0);
		m.visitEnd();

		m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()" + arr, null, null);
		m.visitCode();
		m.visitFieldInsn(Opcodes.GETSTATIC, CHANNEL, "$VALUES", arr);
		m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, arr, "clone", "()Ljava/lang/Object;", false);
		m.visitTypeInsn(Opcodes.CHECKCAST, arr);
		m.visitInsn(Opcodes.ARETURN);
		m.visitMaxs(0, 0);
		m.visitEnd();

		m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "valueOf", "(Ljava/lang/String;)" + self, null, null);
		m.visitCode();
		m.visitLdcInsn(org.objectweb.asm.Type.getObjectType(CHANNEL));
		m.visitVarInsn(Opcodes.ALOAD, 0);
		m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
		m.visitTypeInsn(Opcodes.CHECKCAST, CHANNEL);
		m.visitInsn(Opcodes.ARETURN);
		m.visitMaxs(0, 0);
		m.visitEnd();

		m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getUserPreference", "()" + self, null, null);
		m.visitCode();
		m.visitFieldInsn(Opcodes.GETSTATIC, CHANNEL, "RELEASE", self);
		m.visitInsn(Opcodes.ARETURN);
		m.visitMaxs(0, 0);
		m.visitEnd();

		m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		m.visitCode();
		for (int i = 0; i < names.length; i++) {
			m.visitTypeInsn(Opcodes.NEW, CHANNEL);
			m.visitInsn(Opcodes.DUP);
			m.visitLdcInsn(names[i]);
			m.visitIntInsn(Opcodes.BIPUSH, i);
			m.visitMethodInsn(Opcodes.INVOKESPECIAL, CHANNEL, "<init>", "(Ljava/lang/String;I)V", false);
			m.visitFieldInsn(Opcodes.PUTSTATIC, CHANNEL, names[i], self);
		}
		m.visitIntInsn(Opcodes.BIPUSH, names.length);
		m.visitTypeInsn(Opcodes.ANEWARRAY, CHANNEL);
		for (int i = 0; i < names.length; i++) {
			m.visitInsn(Opcodes.DUP);
			m.visitIntInsn(Opcodes.BIPUSH, i);
			m.visitFieldInsn(Opcodes.GETSTATIC, CHANNEL, names[i], self);
			m.visitInsn(Opcodes.AASTORE);
		}
		m.visitFieldInsn(Opcodes.PUTSTATIC, CHANNEL, "$VALUES", arr);
		m.visitInsn(Opcodes.RETURN);
		m.visitMaxs(0, 0);
		m.visitEnd();
		w.visitEnd();
		return w.toByteArray();
	}

	/** Created by Fabric for the {@code preLaunch} entry point. */
	public ModMenuStubs() {
	}
}

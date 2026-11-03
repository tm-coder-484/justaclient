package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.ServerFolders;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.ServersFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.PopupMenu;
import dev.justaclient.ui.widget.Scroller;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The client's server list: the same servers as the vanilla list, with folders, favourites, recent servers, search and
 * live pings. Drag a server onto a folder to file it; right-click for more. Works from the pause menu too: joining
 * leaves the current world first.
 */
public final class ServersScreen extends JacScreen {
	private static final float ROW_H = 40f;
	private static final float PAD = 12f;
	private static final float SIDE_W = 118f;
	private static final float SIDE_ROW = 17f;
	private static final ThreadPoolExecutor PINGS = new ScheduledThreadPoolExecutor(5, r -> {
		Thread t = new Thread(r, "Justaclient server ping");
		t.setDaemon(true);
		return t;
	});

	/** A sidebar entry: one of the built-in views, or a folder. */
	private record View(String id, String label, Icons icon, @Nullable String folder) {
	}

	private static final View ALL = new View("all", "All servers", Icons.LIST, null);
	private static final View FAVOURITES = new View("fav", "Favourites", Icons.STAR, null);
	private static final View RECENT = new View("recent", "Recent", Icons.CLOCK, null);
	private static final View UNFILED = new View("unfiled", "Not in a folder", Icons.FOLDER_OPEN, null);

	private final ServerList servers;
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private final Map<String, FaviconTexture> icons = new HashMap<>();
	private final Map<String, byte[]> iconBytes = new HashMap<>();
	private List<ServerData> shown = List.of();
	private View view = ALL;
	private @Nullable ServerData selected;
	private final InputBox search = new InputBox("", 64, "Search servers", Icons.SEARCH);
	private final InputBox folderName = new InputBox("", 32, "Folder name", Icons.FOLDER);
	/** The folder being renamed, or "" when making a new one, or null when neither. */
	private @Nullable String naming;
	private final Scroller scroll = new Scroller();
	private final List<Button> buttons = new ArrayList<>();
	/** Refresh and the vanilla list, kept at the right of the footer; edit and delete live on the right-click menu. */
	private final List<Button> rightButtons = new ArrayList<>();
	private @Nullable Button joinButton;
	private @Nullable PopupMenu menu;
	private @Nullable ServerData dragging;
	private double dragStartX;
	private double dragStartY;
	private boolean dragMoved;
	private long lastClick;
	private @Nullable ServerData lastClicked;
	private @Nullable ServerData editing;
	private String status = "";

	private float px;
	private float py;
	private float pw;
	private float ph;
	private float listX;
	private float listY;
	private float listW;
	private float listH;
	private float sideX;
	private float sideY;

	public ServersScreen(@Nullable Screen parent) {
		super("Servers", parent);
		this.servers = new ServerList(net.minecraft.client.Minecraft.getInstance());
		this.servers.load();
	}

	private ServerFolders folders() {
		return ServersFeature.get().folders();
	}

	private void saveFolders() {
		ServersFeature.get().saveFolders();
	}

	@Override
	protected void setup() {
		buttons.clear();
		joinButton = new Button(0, 0, 70, 18, Icons.PLAY, "Join", true, this::joinSelected);
		buttons.add(joinButton);
		buttons.add(new Button(0, 0, 56, 18, Icons.PLUS, "Add", false, this::add));
		buttons.add(new Button(0, 0, 64, 18, Icons.LINK, "Direct", false, this::direct));
		rightButtons.clear();
		rightButtons.add(new Button(0, 0, 18, 18, Icons.REFRESH_CW, "", false, this::refresh));
		rightButtons.add(new Button(0, 0, 80, 18, Icons.LAYOUT_GRID, "Vanilla list", false, () -> open(new JoinMultiplayerScreen(this))));
		applyFilter();
	}

	// Data

	private List<ServerData> all() {
		List<ServerData> list = new ArrayList<>();
		for (int i = 0; i < servers.size(); i++) {
			list.add(servers.get(i));
		}
		return list;
	}

	private ServerFolders.@Nullable Meta meta(ServerData data) {
		return folders().peek(data.ip);
	}

	private boolean favourite(ServerData data) {
		ServerFolders.Meta m = meta(data);
		return m != null && m.favourite;
	}

	private @Nullable String folderOf(ServerData data) {
		ServerFolders.Meta m = meta(data);
		return m == null ? null : m.folder;
	}

	private long lastJoined(ServerData data) {
		ServerFolders.Meta m = meta(data);
		return m == null ? 0 : m.lastJoined;
	}

	private List<View> views() {
		List<View> list = new ArrayList<>(List.of(ALL, FAVOURITES, RECENT));
		if (!folders().folders.isEmpty()) {
			list.add(UNFILED);
		}
		for (String f : folders().folders) {
			list.add(new View("folder:" + f, f, Icons.FOLDER, f));
		}
		return list;
	}

	private int count(View v) {
		return (int) all().stream().filter(d -> in(v, d)).count();
	}

	private boolean in(View v, ServerData d) {
		if (v == ALL) {
			return true;
		}
		if (v == FAVOURITES) {
			return favourite(d);
		}
		if (v == RECENT) {
			return lastJoined(d) > 0;
		}
		if (v == UNFILED) {
			return folderOf(d) == null;
		}
		return v.folder != null && v.folder.equals(folderOf(d));
	}

	private void applyFilter() {
		String q = search.get().trim().toLowerCase(Locale.ROOT);
		List<ServerData> list = new ArrayList<>(all().stream().filter(d -> in(view, d))
			.filter(d -> q.isEmpty() || d.name.toLowerCase(Locale.ROOT).contains(q) || d.ip.toLowerCase(Locale.ROOT).contains(q)).toList());
		if (view == RECENT) {
			list.sort(Comparator.comparingLong(this::lastJoined).reversed());
		} else {
			// Favourites first, otherwise the list's own order.
			list.sort(Comparator.comparing(d -> !favourite(d)));
		}
		shown = list;
		if (selected != null && !shown.contains(selected)) {
			selected = null;
		}
	}

	// Actions

	private void joinSelected() {
		if (selected != null) {
			join(selected);
		}
	}

	private void join(ServerData data) {
		ServerFolders.Meta m = folders().meta(data.ip);
		m.lastJoined = System.currentTimeMillis();
		saveFolders();
		if (minecraft.level != null || minecraft.getConnection() != null) {
			open(new JacConfirmScreen(this, Icons.SERVER, "Switch server", "Leave this world and join " + data.name + "?", "Join", () -> {
				close();
				minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
				ServersScreen back = new ServersScreen(new TitleScreen());
				ConnectScreen.startConnecting(back, minecraft, ServerAddress.parseString(data.ip), data, false, null);
			}));
			return;
		}
		close();
		ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
	}

	private void add() {
		editing = new ServerData(net.minecraft.client.resources.language.I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
		ServerData data = editing;
		open(new ManageServerScreen(this, Component.translatable("manageServer.add.title"), ok -> {
			if (ok) {
				ServerData hidden = servers.unhide(data.ip);
				if (hidden != null) {
					hidden.copyNameIconFrom(data);
					selected = hidden;
				} else {
					servers.add(data, false);
					selected = data;
				}
				servers.save();
				if (view.folder != null) {
					folders().meta(data.ip).folder = view.folder;
					saveFolders();
				} else if (view == FAVOURITES) {
					folders().meta(data.ip).favourite = true;
					saveFolders();
				}
			}
			editing = null;
			open(this);
		}, data));
	}

	private void direct() {
		ServerData data = new ServerData(net.minecraft.client.resources.language.I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
		open(new DirectJoinServerScreen(this, ok -> {
			if (ok) {
				ServerData existing = servers.get(data.ip);
				if (existing != null) {
					data.copyNameIconFrom(existing);
				}
				close();
				ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
			} else {
				open(this);
			}
		}, data));
	}

	private void edit(ServerData data) {
		ServerData copy = new ServerData(data.name, data.ip, ServerData.Type.OTHER);
		copy.copyFrom(data);
		String oldIp = data.ip;
		open(new ManageServerScreen(this, Component.translatable("manageServer.edit.title"), ok -> {
			if (ok) {
				data.copyNameIconFrom(copy);
				data.ip = copy.ip;
				data.setResourcePackStatus(copy.getResourcePackStatus());
				servers.save();
				if (!oldIp.equalsIgnoreCase(data.ip)) {
					ServerFolders.Meta old = folders().peek(oldIp);
					if (old != null) {
						folders().servers.remove(oldIp.trim().toLowerCase(Locale.ROOT));
						ServerFolders.Meta now = folders().meta(data.ip);
						now.folder = old.folder;
						now.favourite = old.favourite;
						now.lastJoined = old.lastJoined;
						saveFolders();
					}
					data.setState(ServerData.State.INITIAL);
				}
			}
			open(this);
		}, copy));
	}

	private void delete(ServerData data) {
		open(new JacConfirmScreen(this, Icons.TRASH, "Delete server", "Remove " + data.name + " from your server list?", "Delete", () -> {
			servers.remove(data);
			servers.save();
			folders().servers.remove(data.ip.trim().toLowerCase(Locale.ROOT));
			saveFolders();
			if (selected == data) {
				selected = null;
			}
			open(this);
		}));
	}

	private void refresh() {
		for (ServerData d : all()) {
			d.setState(ServerData.State.INITIAL);
		}
		status = "Pinging " + servers.size() + " servers";
	}

	private void toggleFavourite(ServerData data) {
		ServerFolders.Meta m = folders().meta(data.ip);
		m.favourite = !m.favourite;
		saveFolders();
		applyFilter();
	}

	private void moveTo(ServerData data, @Nullable String folder) {
		folders().meta(data.ip).folder = folder;
		saveFolders();
		status = folder == null ? data.name + " is no longer in a folder" : data.name + " moved to " + folder;
		applyFilter();
	}

	private void startNaming(@Nullable String folder) {
		naming = folder == null ? "" : folder;
		folderName.set(folder == null ? "" : folder);
		folderName.focused = true;
	}

	private void finishNaming() {
		if (naming == null) {
			return;
		}
		String name = folderName.get().trim();
		if (!name.isEmpty()) {
			boolean ok = naming.isEmpty() ? folders().addFolder(name) : folders().renameFolder(naming, name);
			if (ok) {
				saveFolders();
				if (!naming.isEmpty() && view.folder != null && view.folder.equals(naming)) {
					view = new View("folder:" + name, name, Icons.FOLDER, name);
				}
			} else {
				status = "There is already a folder called " + name;
			}
		}
		naming = null;
		folderName.focused = false;
		applyFilter();
	}

	private void close() {
		pinger.removeAll();
		for (FaviconTexture t : icons.values()) {
			t.close();
		}
		icons.clear();
		iconBytes.clear();
	}

	@Override
	public void onClose() {
		close();
		super.onClose();
	}

	@Override
	public void tick() {
		super.tick();
		pinger.tick();
	}

	@Override
	public void removed() {
		pinger.removeAll();
		super.removed();
	}

	// Pinging

	private void ping(ServerData data) {
		if (data.state() != ServerData.State.INITIAL) {
			return;
		}
		data.setState(ServerData.State.PINGING);
		data.motd = Component.empty();
		data.status = Component.empty();
		PINGS.submit(() -> {
			try {
				pinger.pingServer(data, () -> minecraft.execute(servers::save), () -> {
					data.setState(data.protocol == SharedConstants.getCurrentVersion().protocolVersion() ? ServerData.State.SUCCESSFUL
						: ServerData.State.INCOMPATIBLE);
				}, EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()));
			} catch (java.net.UnknownHostException e) {
				data.setState(ServerData.State.UNREACHABLE);
				data.motd = Component.literal("Can't resolve the address");
			} catch (Exception e) {
				data.setState(ServerData.State.UNREACHABLE);
				data.motd = Component.literal("Can't connect to the server");
			}
		});
	}

	private @Nullable FaviconTexture icon(ServerData data) {
		byte[] bytes = data.getIconBytes();
		if (bytes == null) {
			return null;
		}
		FaviconTexture tex = icons.computeIfAbsent(data.ip, ip -> FaviconTexture.forServer(minecraft.getTextureManager(), ip));
		if (!Arrays.equals(bytes, iconBytes.get(data.ip))) {
			iconBytes.put(data.ip, bytes);
			try {
				tex.upload(NativeImage.read(bytes));
			} catch (Throwable e) {
				return null;
			}
		}
		return tex;
	}

	// Drawing

	private void layout() {
		pw = Math.min(600f, width - 24f);
		ph = Math.min(360f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		sideX = px + PAD;
		sideY = py + 40;
		listX = sideX + SIDE_W + PAD;
		listY = py + 40;
		listW = px + pw - PAD - listX;
		listH = py + ph - 34 - listY;
		search.place(px + pw - PAD - 160 - 20, py + 10, 160);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float t) {
		layout();
		float a = t;
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		int accent = JacConfig.accent();
		JacText.icon(g, Icons.SERVER, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Servers", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		long online = all().stream().filter(d -> d.state() == ServerData.State.SUCCESSFUL).count();
		String sub = servers.size() + (servers.size() == 1 ? " server" : " servers") + (online > 0 ? " · " + online + " online" : "")
			+ (minecraft.level != null ? " · joining leaves this world" : "");
		JacText.draw(g, sub, px + PAD + 15, py + PAD + 12, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		search.draw(g, mx, my, a);
		boolean closeHot = mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHot ? Theme.TEXT : Theme.TEXT_DIM, a));
		drawSidebar(g, mx, my, a);
		drawList(g, mx, my, a);
		drawFooter(g, mx, my, a);
		if (dragging != null && dragMoved) {
			g.nextStratum();
			float w = JacText.width(dragging.name, Theme.LABEL, JacText.Weight.SEMIBOLD) + 24;
			Draw.rect(g, mx + 6, my - 6, w, 14, 7f, Colors.fade(Colors.withAlpha(accent, 0xD0), a));
			JacText.icon(g, Icons.SERVER, mx + 11, my - 2.5f, 6.5f, 0xFFFFFFFF);
			JacText.draw(g, dragging.name, mx + 22, my - 3, 0xFFFFFFFF, Theme.LABEL, JacText.Weight.SEMIBOLD);
		}
		if (menu != null) {
			// Above the server list's game-font text.
			g.nextStratum();
			menu.draw(g, mx, my);
		}
	}

	private int sideIndexAt(double mx, double my) {
		if (mx < sideX || mx >= sideX + SIDE_W) {
			return -1;
		}
		List<View> vs = views();
		for (int i = 0; i < vs.size(); i++) {
			float y = sideRowY(i, vs);
			if (my >= y && my < y + SIDE_ROW) {
				return i;
			}
		}
		return -1;
	}

	private float sideRowY(int i, List<View> vs) {
		// A gap between the built-in views and the folders.
		int builtIn = folders().folders.isEmpty() ? 3 : 4;
		return sideY + i * SIDE_ROW + (i >= builtIn ? 10 : 0);
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mx, int my, float a) {
		List<View> vs = views();
		int accent = JacConfig.accent();
		int hotIndex = sideIndexAt(mx, my);
		for (int i = 0; i < vs.size(); i++) {
			View v = vs.get(i);
			float y = sideRowY(i, vs);
			boolean sel = v.id.equals(view.id);
			boolean dropTarget = dragging != null && dragMoved && i == hotIndex && v.folder != null;
			if (sel || i == hotIndex || dropTarget) {
				Draw.rect(g, sideX, y, SIDE_W, SIDE_ROW - 2, 5f,
					Colors.fade(dropTarget ? Colors.withAlpha(accent, 0x60) : sel ? Colors.withAlpha(accent, 0x30) : 0x10FFFFFF, a));
			}
			if (naming != null && v.folder != null && v.folder.equals(naming)) {
				folderName.place(sideX, y, SIDE_W);
				folderName.h = SIDE_ROW - 2;
				folderName.draw(g, mx, my, a);
				continue;
			}
			JacText.icon(g, v.icon, sideX + 6, y + 4, 7f, Colors.fade(sel ? accent : Theme.TEXT_DIM, a));
			int n = count(v);
			String c = String.valueOf(n);
			float cw = JacText.width(c, Theme.SMALL, JacText.Weight.REGULAR);
			JacText.draw(g, fit(v.label, Theme.LABEL, sel ? JacText.Weight.SEMIBOLD : JacText.Weight.REGULAR, SIDE_W - 30 - cw), sideX + 18, y + 3.5f,
				Colors.fade(sel ? Theme.TEXT : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, 0.5f), a), Theme.LABEL,
				sel ? JacText.Weight.SEMIBOLD : JacText.Weight.REGULAR);
			JacText.draw(g, c, sideX + SIDE_W - 6 - cw, y + 4, Colors.fade(Theme.TEXT_DIM, a * 0.8f), Theme.SMALL, JacText.Weight.REGULAR);
		}
		float y = sideRowY(vs.size(), vs) + 2;
		if (naming != null && naming.isEmpty()) {
			folderName.place(sideX, y, SIDE_W);
			folderName.h = SIDE_ROW - 2;
			folderName.draw(g, mx, my, a);
		} else {
			boolean hot = mx >= sideX && mx < sideX + SIDE_W && my >= y && my < y + SIDE_ROW;
			JacText.icon(g, Icons.PLUS, sideX + 6, y + 4, 7f, Colors.fade(hot ? accent : Theme.TEXT_DIM, a));
			JacText.draw(g, "New folder", sideX + 18, y + 3.5f, Colors.fade(hot ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
		if (folders().folders.isEmpty()) {
			for (String line : wrap("Make a folder, then drag servers onto it.", Theme.SMALL, JacText.Weight.REGULAR, SIDE_W - 8)) {
				y += 9;
				JacText.draw(g, line, sideX + 4, y + 12, Colors.fade(Theme.TEXT_DIM, a * 0.7f), Theme.SMALL, JacText.Weight.REGULAR);
			}
		}
	}

	private void drawList(GuiGraphicsExtractor g, int mx, int my, float a) {
		scroll.setMax(shown.size() * ROW_H - listH);
		float s = scroll.get();
		Draw.rect(g, listX, listY, listW, listH, 6f, Colors.fade(0x18000000, a));
		g.enableScissor((int) listX, (int) listY, (int) (listX + listW), (int) (listY + listH));
		int accent = JacConfig.accent();
		for (int i = Math.max(0, (int) (s / ROW_H)); i < shown.size(); i++) {
			float y = listY + i * ROW_H - s;
			if (y > listY + listH) {
				break;
			}
			ServerData d = shown.get(i);
			ping(d);
			boolean sel = d == selected;
			boolean hot = menu == null && mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H && my >= listY && my < listY + listH;
			if (sel || hot) {
				Draw.rect(g, listX + 2, y + 1, listW - 4, ROW_H - 2, 6f, Colors.fade(sel ? Colors.withAlpha(accent, 0x30) : 0x10FFFFFF, a));
			}
			FaviconTexture icon = icon(d);
			float ix = listX + 6;
			float iy = y + 4;
			if (icon != null) {
				int c = ((Math.round(a * 255) & 0xFF) << 24) | 0xFFFFFF;
				g.blit(RenderPipelines.GUI_TEXTURED, icon.textureLocation(), (int) ix, (int) iy, 0f, 0f, 32, 32, 32, 32, c);
			} else {
				Draw.rect(g, ix, iy, 32, 32, 5f, Colors.fade(0x1FFFFFFF, a));
				JacText.icon(g, Icons.SERVER, ix + 9, iy + 9, 14f, Colors.fade(Theme.TEXT_DIM, a));
			}
			float tx = ix + 40;
			boolean fav = favourite(d);
			float rightW = 74;
			float nameMax = listW - (tx - listX) - rightW;
			String name = fit(d.name, Theme.LABEL + 0.5f, JacText.Weight.SEMIBOLD, nameMax - (fav ? 12 : 0));
			JacText.draw(g, name, tx, y + 5, Colors.fade(Theme.TEXT, a), Theme.LABEL + 0.5f, JacText.Weight.SEMIBOLD);
			if (fav) {
				JacText.icon(g, Icons.STAR, tx + JacText.width(name, Theme.LABEL + 0.5f, JacText.Weight.SEMIBOLD) + 4, y + 5.5f, 6.5f,
					Colors.fade(0xFFFBBF24, a));
			}
			String folder = folderOf(d);
			if (folder != null && view.folder == null) {
				float fw = JacText.width(folder, Theme.SMALL, JacText.Weight.REGULAR) + 16;
				float fx = listX + listW - rightW - fw - 4;
				Draw.rect(g, fx, y + 4, fw, 10, 5f, Colors.fade(Theme.CONTROL, a));
				JacText.icon(g, Icons.FOLDER, fx + 4, y + 6, 5.5f, Colors.fade(Theme.TEXT_DIM, a));
				JacText.draw(g, folder, fx + 12, y + 5.5f, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			}
			// MOTD in the game's font, so its colours show.
			int motdW = (int) (listW - (tx - listX) - 10);
			List<FormattedCharSequence> lines = font.split(d.motd == null ? Component.empty() : d.motd, Math.max(20, motdW));
			int alpha = Math.round(a * 255) & 0xFF;
			for (int l = 0; l < Math.min(2, lines.size()); l++) {
				vanillaText(g, lines.get(l), (int) tx, (int) (y + 16 + l * 10), (alpha << 24) | 0x9AA0AE);
			}
			drawStatus(g, d, listX + listW - 8, y + 5, a);
		}
		g.disableScissor();
		if (shown.isEmpty()) {
			String msg = servers.size() == 0 ? "No servers yet. Add one below." : view.folder != null && search.get().isEmpty()
				? "Drag servers here to fill this folder" : view == FAVOURITES && search.get().isEmpty() ? "Right-click a server to make it a favourite"
				: "Nothing here";
			JacText.drawCentered(g, msg, listX + listW / 2f, listY + 24, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
		if (scroll.max() > 0) {
			float barH = Math.max(16f, listH * listH / (listH + scroll.max()));
			float barY = listY + (listH - barH) * (s / scroll.max());
			Draw.rect(g, listX + listW - 3, barY, 2, barH, 1f, Colors.fade(0x50FFFFFF, a));
		}
	}

	/** Ping bars and player count, right-aligned at {@code right}. */
	private void drawStatus(GuiGraphicsExtractor g, ServerData d, float right, float y, float a) {
		int bars;
		int color;
		String label;
		switch (d.state()) {
			case SUCCESSFUL -> {
				bars = d.ping < 150 ? 5 : d.ping < 300 ? 4 : d.ping < 600 ? 3 : d.ping < 1000 ? 2 : 1;
				color = d.ping < 300 ? 0xFF34D399 : d.ping < 600 ? 0xFFFBBF24 : 0xFFF87171;
				label = d.players != null ? d.players.online() + "/" + d.players.max() : "";
			}
			case INCOMPATIBLE -> {
				bars = 0;
				color = 0xFFF87171;
				label = d.version.getString();
			}
			case UNREACHABLE -> {
				bars = 0;
				color = 0xFFF87171;
				label = "Offline";
			}
			default -> {
				bars = (int) (System.currentTimeMillis() / 150 % 5) + 1;
				color = Theme.TEXT_DIM;
				label = "";
			}
		}
		float bx = right - 14;
		for (int i = 0; i < 5; i++) {
			float h = 2 + i * 1.5f;
			Draw.rect(g, bx + i * 3, y + 8 - h, 2, h, 0.5f, Colors.fade(i < bars ? color : 0x30FFFFFF, a));
		}
		if (d.state() == ServerData.State.SUCCESSFUL) {
			String ms = d.ping + " ms";
			JacText.draw(g, ms, right - JacText.width(ms, Theme.SMALL, JacText.Weight.REGULAR), y + 12, Colors.fade(Theme.TEXT_DIM, a * 0.8f),
				Theme.SMALL, JacText.Weight.REGULAR);
		}
		if (!label.isEmpty()) {
			label = fit(label, Theme.SMALL, JacText.Weight.REGULAR, 54);
			float w = JacText.width(label, Theme.SMALL, JacText.Weight.REGULAR);
			JacText.draw(g, label, bx - 5 - w, y + 1.5f, Colors.fade(d.state() == ServerData.State.SUCCESSFUL ? Theme.TEXT : color, a), Theme.SMALL,
				JacText.Weight.REGULAR);
		}
	}

	private void drawFooter(GuiGraphicsExtractor g, int mx, int my, float a) {
		float fy = py + ph - 26;
		Draw.rect(g, px + PAD, fy - 6, pw - PAD * 2, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
		if (joinButton != null) {
			joinButton.active(selected != null);
		}
		float rx = px + pw - PAD;
		for (int i = rightButtons.size() - 1; i >= 0; i--) {
			Button b = rightButtons.get(i);
			rx -= b.width();
			b.setPosition(rx, fy);
			b.render(g, mx, my, a);
			rx -= 5;
		}
		float bx = px + PAD;
		for (Button b : buttons) {
			b.setPosition(bx, fy);
			b.render(g, mx, my, a);
			bx += b.width() + 5;
		}
		if (!status.isEmpty() && bx < rx - 40) {
			JacText.draw(g, fit(status, Theme.SMALL, JacText.Weight.REGULAR, rx - bx - 4), bx + 4, fy + 5.5f, Colors.fade(Theme.TEXT_DIM, a),
				Theme.SMALL, JacText.Weight.REGULAR);
		}
	}

	// Input

	private @Nullable ServerData rowAt(double mx, double my) {
		if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) {
			return null;
		}
		int i = (int) ((my - listY + scroll.get()) / ROW_H);
		return i >= 0 && i < shown.size() ? shown.get(i) : null;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (menu != null) {
			PopupMenu m = menu;
			menu = null;
			m.click(mx, my);
			return true;
		}
		if (naming != null && !folderName.inside(mx, my)) {
			finishNaming();
		}
		if (button == 0 && search.click(mx, my)) {
			return true;
		}
		if (naming != null && folderName.click(mx, my)) {
			return true;
		}
		if (button == 0 && mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26) {
			onClose();
			return true;
		}
		if (button == 0) {
			for (Button b : buttons) {
				if (b.mouseClicked(mx, my, button)) {
					return true;
				}
			}
			for (Button b : rightButtons) {
				if (b.mouseClicked(mx, my, button)) {
					return true;
				}
			}
		}
		List<View> vs = views();
		int side = sideIndexAt(mx, my);
		if (side >= 0) {
			View v = vs.get(side);
			if (button == 1 && v.folder != null) {
				String f = v.folder;
				menu = new PopupMenu().header(f).add(Icons.PENCIL, "Rename", () -> startNaming(f))
					.add(Icons.ARROW_UP_DOWN, "Move up", folders().folders.indexOf(f) > 0, () -> {
						folders().moveFolder(f, -1);
						saveFolders();
					}).add(Icons.ARROW_UP_DOWN, "Move down", folders().folders.indexOf(f) < folders().folders.size() - 1, () -> {
						folders().moveFolder(f, 1);
						saveFolders();
					}).add(Icons.TRASH, "Delete folder", () -> {
						folders().removeFolder(f);
						saveFolders();
						if (view.folder != null && view.folder.equals(f)) {
							view = ALL;
						}
						applyFilter();
					}).open(mx, my, width, height);
			} else {
				view = v;
				applyFilter();
				scroll.scrollTo(0);
			}
			return true;
		}
		float newY = sideRowY(vs.size(), vs) + 2;
		if (button == 0 && mx >= sideX && mx < sideX + SIDE_W && my >= newY && my < newY + SIDE_ROW) {
			startNaming(null);
			return true;
		}
		ServerData d = rowAt(mx, my);
		if (d != null) {
			selected = d;
			if (button == 1) {
				menu = serverMenu(d).open(mx, my, width, height);
				return true;
			}
			long now = System.currentTimeMillis();
			if (lastClicked == d && now - lastClick < 300) {
				join(d);
				return true;
			}
			lastClicked = d;
			lastClick = now;
			dragging = d;
			dragStartX = mx;
			dragStartY = my;
			dragMoved = false;
			return true;
		}
		return false;
	}

	private PopupMenu serverMenu(ServerData d) {
		boolean fav = favourite(d);
		PopupMenu m = new PopupMenu().header(d.name).add(Icons.PLAY, "Join", () -> join(d))
			.add(Icons.STAR, fav ? "Remove from favourites" : "Add to favourites", () -> toggleFavourite(d)).add(Icons.PENCIL, "Edit", () -> edit(d))
			.add(Icons.COPY, "Copy address", () -> minecraft.keyboardHandler.setClipboard(d.ip));
		if (!folders().folders.isEmpty()) {
			m.header("Move to folder");
			String current = folderOf(d);
			for (String f : folders().folders) {
				m.add(Icons.FOLDER, f, !f.equals(current), () -> moveTo(d, f));
			}
			if (current != null) {
				m.add(Icons.X, "Out of " + current, () -> moveTo(d, null));
			}
		}
		m.add(Icons.TRASH, "Delete", () -> delete(d));
		return m;
	}

	@Override
	protected void drag(double mx, double my, int button) {
		if (dragging != null && !dragMoved && Math.hypot(mx - dragStartX, my - dragStartY) > 4) {
			dragMoved = true;
		}
	}

	@Override
	protected void release(double mx, double my, int button) {
		if (dragging != null && dragMoved) {
			int side = sideIndexAt(mx, my);
			if (side >= 0) {
				View v = views().get(side);
				if (v.folder != null) {
					moveTo(dragging, v.folder);
				} else if (v == UNFILED) {
					moveTo(dragging, null);
				} else if (v == FAVOURITES && !favourite(dragging)) {
					toggleFavourite(dragging);
				}
			}
		}
		dragging = null;
		dragMoved = false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		scroll.scroll(amount, ROW_H * 1.5f);
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (menu != null && key == InputConstants.KEY_ESCAPE) {
			menu = null;
			return true;
		}
		if (naming != null) {
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				finishNaming();
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				naming = null;
				folderName.focused = false;
				return true;
			}
			folderName.key(key, ctrl);
			return true;
		}
		if (search.focused) {
			if (key == InputConstants.KEY_ESCAPE) {
				search.focused = false;
				return true;
			}
			String before = search.get();
			boolean used = search.key(key, ctrl);
			if (!before.equals(search.get())) {
				applyFilter();
				scroll.scrollTo(0);
			}
			if (used) {
				return true;
			}
		}
		if (ctrl && key == InputConstants.KEY_F) {
			search.focused = true;
			return true;
		}
		if (key == InputConstants.KEY_F5) {
			refresh();
			return true;
		}
		if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && selected != null) {
			join(selected);
			return true;
		}
		if (key == InputConstants.KEY_DELETE && selected != null) {
			delete(selected);
			return true;
		}
		if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
			int i = selected == null ? -1 : shown.indexOf(selected);
			int next = Math.max(0, Math.min(shown.size() - 1, i + (key == InputConstants.KEY_DOWN ? 1 : -1)));
			if (next < shown.size()) {
				selected = shown.get(next);
				scroll.reveal(next * ROW_H, (next + 1) * ROW_H, listH);
			}
			return true;
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (naming != null) {
			folderName.typed(text);
			return true;
		}
		if (!search.focused) {
			if (text.isBlank()) {
				return false;
			}
			search.focused = true;
		}
		search.typed(text);
		applyFilter();
		scroll.scrollTo(0);
		return true;
	}
}

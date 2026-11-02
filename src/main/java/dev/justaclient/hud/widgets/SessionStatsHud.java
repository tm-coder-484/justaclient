package dev.justaclient.hud.widgets;

import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.RowsHud;
import dev.justaclient.hud.SessionStats;
import dev.justaclient.render.Icons;
import java.util.List;

public final class SessionStatsHud extends RowsHud {
	private final BoolSetting time = bool("time", "Play time", true);
	private final BoolSetting combat = bool("combat", "Kills, deaths and K/D", true);
	private final BoolSetting mined = bool("mined", "Blocks mined", true);
	private final BoolSetting walked = bool("walked", "Distance travelled", true);

	public SessionStatsHud() {
		super("session_stats", "Session stats", "Play time, kills, deaths, blocks and distance this session", Icons.ACTIVITY, false, 0f, 0.7f);
	}

	@Override
	protected void collect(List<Row> rows, boolean preview) {
		if (time.on()) {
			long seconds = preview ? 4380 : SessionStats.playMillis() / 1000;
			rows.add(new Row("Session", seconds >= 3600 ? String.format("%dh %02dm", seconds / 3600, seconds / 60 % 60)
				: String.format("%dm %02ds", seconds / 60, seconds % 60)));
		}
		if (combat.on()) {
			int kills = preview ? 12 : SessionStats.kills();
			int deaths = preview ? 3 : SessionStats.deaths();
			rows.add(new Row("Kills", String.valueOf(kills)));
			rows.add(new Row("Deaths", String.valueOf(deaths)));
			rows.add(new Row("K/D", String.format("%.2f", deaths == 0 ? kills : (float) kills / deaths)));
		}
		if (mined.on()) {
			rows.add(new Row("Mined", String.valueOf(preview ? 342 : SessionStats.blocks())));
		}
		if (walked.on()) {
			double d = preview ? 2400 : SessionStats.distance();
			rows.add(new Row("Travelled", d >= 1000 ? String.format("%.2f km", d / 1000) : String.format("%.0f m", d)));
		}
	}
}

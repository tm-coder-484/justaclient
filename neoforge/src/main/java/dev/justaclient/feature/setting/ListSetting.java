package dev.justaclient.feature.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * A list of short names, such as friends or screens to skip. Shown as removable chips with a box to add more. Older
 * profiles that kept the list as one comma separated string still load.
 */
public final class ListSetting extends Setting<List<String>> {
	public final int maxItems;
	public final int maxLength;
	public final String placeholder;

	public ListSetting(String id, String name, List<String> defaultValue, int maxItems, int maxLength, String placeholder) {
		super(id, name, List.copyOf(defaultValue));
		this.maxItems = maxItems;
		this.maxLength = maxLength;
		this.placeholder = placeholder;
	}

	/** Keeps the list clean: trimmed, no blanks, no duplicates (ignoring case), within the limits. */
	@Override
	public void set(List<String> list) {
		List<String> out = new ArrayList<>();
		if (list != null) {
			for (String s : list) {
				String v = s == null ? "" : s.trim();
				if (v.length() > maxLength) {
					v = v.substring(0, maxLength).trim();
				}
				if (!v.isEmpty() && !contains(out, v) && out.size() < maxItems) {
					out.add(v);
				}
			}
		}
		super.set(List.copyOf(out));
	}

	public boolean contains(String item) {
		return contains(value, item);
	}

	private static boolean contains(List<String> list, String item) {
		String key = item.trim().toLowerCase(Locale.ROOT);
		return list.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).equals(key));
	}

	/** Adds {@code item}; returns false if it was blank, already there or the list is full. */
	public boolean add(String item) {
		if (item == null || item.isBlank() || contains(item) || value.size() >= maxItems) {
			return false;
		}
		List<String> next = new ArrayList<>(value);
		next.add(item);
		set(next);
		return true;
	}

	/** Removes {@code item} (ignoring case); returns false if it was not there. */
	public boolean remove(String item) {
		if (item == null || !contains(item)) {
			return false;
		}
		String key = item.trim().toLowerCase(Locale.ROOT);
		set(value.stream().filter(s -> !s.toLowerCase(Locale.ROOT).equals(key)).toList());
		return true;
	}

	@Override
	public JsonElement save() {
		JsonArray array = new JsonArray();
		value.forEach(array::add);
		return array;
	}

	@Override
	public void load(@Nullable JsonElement json) {
		if (json == null) {
			return;
		}
		if (json.isJsonArray()) {
			List<String> out = new ArrayList<>();
			for (JsonElement e : json.getAsJsonArray()) {
				if (e.isJsonPrimitive()) {
					out.add(e.getAsString());
				}
			}
			set(out);
		} else if (json.isJsonPrimitive()) {
			// Before lists existed these were one string separated by commas.
			set(List.of(json.getAsString().split(",")));
		}
	}
}

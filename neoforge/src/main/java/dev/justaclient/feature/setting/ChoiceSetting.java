package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** One option out of an enum, shown as a segmented control. Each constant supplies its own label. */
public final class ChoiceSetting<E extends Enum<E> & ChoiceSetting.Named> extends Setting<E> {
	public interface Named {
		String label();
	}

	public final List<E> options;

	public ChoiceSetting(String id, String name, E defaultValue) {
		super(id, name, defaultValue);
		this.options = List.of(defaultValue.getDeclaringClass().getEnumConstants());
	}

	public List<String> labels() {
		return options.stream().map(Named::label).toList();
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value.name());
	}

	@Override
	public void load(@Nullable JsonElement json) {
		if (json == null || !json.isJsonPrimitive()) {
			return;
		}
		String name = json.getAsString();
		for (E option : options) {
			if (option.name().equals(name)) {
				value = option;
			}
		}
	}
}

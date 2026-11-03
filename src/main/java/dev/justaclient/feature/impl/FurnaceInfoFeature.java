package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.inventory.ContainerOverlays;
import dev.justaclient.inventory.InvCompat;
import dev.justaclient.mixin.AbstractFurnaceMenuAccessor;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * A panel beside furnaces, smokers and blast furnaces: time left for this item and the whole stack, fuel left, how many
 * items the fuel will smelt, and whether it is enough. Fuel items also say how many items they smelt. All of it comes
 * from the numbers the furnace already sends for its arrows.
 */
public final class FurnaceInfoFeature extends Feature {
	private static final int GREEN = 0xFF4ADE80;
	private static final int AMBER = 0xFFFBBF24;

	private final BoolSetting stackTime = bool("stack_time", "Time for the whole stack", true);
	private final BoolSetting fuelVerdict = bool("fuel_verdict", "Say if the fuel is enough", true);
	private final BoolSetting fuelTooltip = bool("fuel_tooltip", "Fuel tooltips (items smelted)", true);

	public FurnaceInfoFeature() {
		super("furnace_info", "Furnace info", "Time left, fuel left and how far the fuel goes, beside furnaces", Category.INVENTORY, Icons.FLAME,
			true);
	}

	public static void init() {
		FurnaceInfoFeature feature = Features.get(FurnaceInfoFeature.class);
		ContainerOverlays.register(feature.new Panel());
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!feature.isEnabled() || !feature.fuelTooltip.on() || lines.isEmpty()
				|| !(Minecraft.getInstance().screen instanceof AbstractFurnaceScreen<?> screen)) {
				return;
			}
			int burn = InvCompat.burnTicks(stack, fast(screen));
			if (burn <= 0) {
				return;
			}
			int cook = cookTime(screen);
			float items = burn / (float) cook;
			String each = items == Math.floor(items) ? String.valueOf((int) items) : String.format("%.1f", items);
			String all = stack.getCount() > 1 ? ", " + format(items * stack.getCount()) + " for the stack" : "";
			lines.add(Math.min(1, lines.size()), Component.literal("Smelts " + each + " item" + (items == 1f ? "" : "s") + all)
				.withStyle(ChatFormatting.GRAY));
		});
	}

	private static String format(float v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
	}

	/** Ticks one item takes here: the furnace's own number while it cooks, otherwise the usual time for this kind. */
	private static int cookTime(AbstractFurnaceScreen<?> screen) {
		int total = data(screen.getMenu()).get(3);
		if (total > 0) {
			return total;
		}
		return fast(screen) ? 100 : 200;
	}

	/** Blast furnaces and smokers cook twice as fast and burn fuel twice as fast. */
	private static boolean fast(AbstractFurnaceScreen<?> screen) {
		return screen instanceof BlastFurnaceScreen || screen instanceof SmokerScreen;
	}

	private static ContainerData data(AbstractFurnaceMenu menu) {
		return ((AbstractFurnaceMenuAccessor) menu).jac$data();
	}

	private final class Panel implements ContainerOverlays.Overlay {
		@Override
		public boolean applies(AbstractContainerScreen<?> screen) {
			return isEnabled() && screen instanceof AbstractFurnaceScreen<?>;
		}

		@Override
		public float width(ContainerOverlays.Box box) {
			return 118;
		}

		private int lines(AbstractFurnaceScreen<?> screen) {
			AbstractFurnaceMenu menu = screen.getMenu();
			ItemStack input = menu.slots.get(0).getItem();
			int n = 1;
			if (!input.isEmpty()) {
				n += stackTime.on() && input.getCount() > 1 ? 4 : 3;
			}
			n += menu.slots.get(1).getItem().isEmpty() ? 0 : 1;
			n += fuelVerdict.on() && !input.isEmpty() ? 1 : 0;
			return n;
		}

		@Override
		public float height(ContainerOverlays.Box box) {
			return 18 + lines((AbstractFurnaceScreen<?>) box.screen()) * 10 + 2;
		}

		@Override
		public void draw(ContainerOverlays.Box box, GuiGraphics g, float x, float y, float w, float h, double mx, double my) {
			AbstractFurnaceScreen<?> screen = (AbstractFurnaceScreen<?>) box.screen();
			AbstractFurnaceMenu menu = screen.getMenu();
			ContainerData data = data(menu);
			int litLeft = data.get(0);
			// A furnace placed with commands can report more progress than its total; never show a negative time.
			int progress = Math.min(data.get(2), cookTime(screen));
			int cook = cookTime(screen);
			ItemStack input = menu.slots.get(0).getItem();
			ItemStack fuel = menu.slots.get(1).getItem();
			ItemStack output = menu.slots.get(2).getItem();

			float ly = ContainerOverlays.header(g, Icons.FLAME, menu.isLit() ? "Smelting" : "Furnace", x, y, w);
			if (!input.isEmpty()) {
				ly = ContainerOverlays.bar(g, progress / (float) cook, dev.justaclient.config.JacConfig.accent(), x, ly, w);
				ly = ContainerOverlays.row(g, "This item", ContainerOverlays.time(cook - progress), Theme.TEXT, x, ly, w);
				if (stackTime.on() && input.getCount() > 1) {
					ly = ContainerOverlays.row(g, "All " + input.getCount(), ContainerOverlays.time(input.getCount() * cook - progress), Theme.TEXT, x, ly,
						w);
				}
				int room = output.isEmpty() ? 64 : output.getMaxStackSize() - output.getCount();
				ly = ContainerOverlays.row(g, "Output room", room + "", room < input.getCount() ? AMBER : Theme.TEXT, x, ly, w);
			}
			ly = ContainerOverlays.row(g, "Burning", litLeft > 0 ? ContainerOverlays.time(litLeft) + " (" + format(litLeft / (float) cook) + ")" : "no",
				litLeft > 0 ? Theme.TEXT : Theme.TEXT_DIM, x, ly, w);
			int burn = InvCompat.burnTicks(fuel, fast(screen));
			if (!fuel.isEmpty()) {
				String value = burn < 0 ? "?" : format(burn * fuel.getCount() / (float) cook) + " items";
				ly = ContainerOverlays.row(g, "Fuel slot", value, Theme.TEXT, x, ly, w);
			}
			float covered = (litLeft + Math.max(0, burn) * fuel.getCount()) / (float) cook;
			float needed = input.getCount() - progress / (float) cook;
			if (fuelVerdict.on() && !input.isEmpty()) {
				if (burn < 0) {
					ContainerOverlays.row(g, "Fuel", "can't tell", Theme.TEXT_DIM, x, ly, w);
				} else if (covered + 0.001f >= needed) {
					ContainerOverlays.row(g, "Fuel", "enough", GREEN, x, ly, w);
				} else {
					int per = burn > 0 ? burn : 1600;
					int more = (int) Math.ceil((needed - covered) * cook / per);
					String what = burn > 0 ? fuel.getHoverName().getString() : "Coal";
					ContainerOverlays.row(g, "Short by", fit(more + " " + what, w - 50), AMBER, x, ly, w);
				}
			}
		}

		private static String fit(String s, float max) {
			String out = s;
			while (out.length() > 3 && JacText.width(out, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) > max) {
				out = out.substring(0, out.length() - 1);
			}
			return out.length() < s.length() ? out + "." : out;
		}
	}
}

package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A little item that lives on your screen and obeys gravity: it falls, bounces off the edges, swings when you turn and
 * can be picked up and thrown with the mouse while the chat is open. Pure fun.
 */
public final class TrinketFeature extends Feature {
	public enum Item implements ChoiceSetting.Named {
		HELD("Held item"), TOTEM("Totem"), DIAMOND("Diamond"), APPLE("Golden apple"), PEARL("Ender pearl"), CAKE("Cake"), SLIME("Slime ball");

		private final String label;

		Item(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Item> item = choice("item", "Item", Item.TOTEM);
	private final NumberSetting size = number("size", "Size", 24, 12, 48, 1, v -> Math.round(v) + " px");
	private final NumberSetting bounce = number("bounce", "Bounciness", 0.7, 0.1, 0.95, 0.05, v -> Math.round(v * 100) + "%");
	private final NumberSetting gravity = number("gravity", "Gravity", 1.0, 0, 2, 0.1, v -> String.format("%.1fx", v));
	private final BoolSetting swing = bool("swing", "Swing when you turn", true);
	private final BoolSetting shadow = bool("shadow", "Soft shadow", true);

	private double x = -1;
	private double y;
	private double vx = 80;
	private double vy;
	private double angle;
	private double spin = 90;
	private long last = -1;
	private float lastYaw = Float.NaN;
	private float lastPitch;
	private boolean grabbed;
	private boolean releaseRequested;
	private double grabDx;
	private double grabDy;
	private double throwVx;
	private double throwVy;
	private double prevMx;
	private double prevMy;

	public TrinketFeature() {
		super("trinket", "Physics trinket", "An item on your screen that falls, bounces and can be thrown", Category.HUD, Icons.DICE_5, false);
	}

	private ItemStack stack() {
		Minecraft mc = Minecraft.getInstance();
		return switch (item.get()) {
			case HELD -> mc.player == null || mc.player.getMainHandItem().isEmpty() ? new ItemStack(Items.TOTEM_OF_UNDYING) : mc.player.getMainHandItem();
			case TOTEM -> new ItemStack(Items.TOTEM_OF_UNDYING);
			case DIAMOND -> new ItemStack(Items.DIAMOND);
			case APPLE -> new ItemStack(Items.GOLDEN_APPLE);
			case PEARL -> new ItemStack(Items.ENDER_PEARL);
			case CAKE -> new ItemStack(Items.CAKE);
			case SLIME -> new ItemStack(Items.SLIME_BALL);
		};
	}

	public void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		double s = size.get();
		double r = s / 2;
		long now = System.nanoTime();
		double dt = last < 0 ? 0 : Math.min(0.05, (now - last) / 1e9);
		last = now;
		if (x < 0) {
			x = sw * 0.7;
			y = sh * 0.2;
		}

		// Grab and throw while the chat frees the mouse.
		double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
		double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
		if (grabbed && (releaseRequested || !(mc.gui.screen() instanceof ChatScreen))) {
			grabbed = false;
			releaseRequested = false;
			// Throw with the speed the mouse was moving at over the last few frames.
			vx = Math.max(-2500, Math.min(2500, throwVx));
			vy = Math.max(-2500, Math.min(2500, throwVy));
			spin = vx * 0.8;
		}

		if (grabbed) {
			x = mx + grabDx;
			y = my + grabDy;
			vx = 0;
			vy = 0;
			if (dt > 0) {
				throwVx = throwVx * 0.5 + (mx - prevMx) / dt * 0.5;
				throwVy = throwVy * 0.5 + (my - prevMy) / dt * 0.5;
			}
		} else if (dt > 0) {
			LocalPlayer p = mc.player;
			if (swing.on() && p != null && mc.gui.screen() == null) {
				float yaw = p.getYRot();
				float pitch = p.getXRot();
				if (!Float.isNaN(lastYaw)) {
					// Turning right pushes the trinket left, as if it hung in the world behind the glass.
					vx -= (yaw - lastYaw) * 14;
					vy -= (pitch - lastPitch) * 14;
				}
				lastYaw = yaw;
				lastPitch = pitch;
			} else {
				lastYaw = Float.NaN;
			}
			vy += 1400 * gravity.get() * dt;
			vx *= Math.pow(0.6, dt);
			vy *= Math.pow(0.8, dt);
			x += vx * dt;
			y += vy * dt;
			double e = bounce.get();
			if (x < r) {
				x = r;
				vx = Math.abs(vx) * e;
				spin = -spin * e + vy * 0.2;
			} else if (x > sw - r) {
				x = sw - r;
				vx = -Math.abs(vx) * e;
				spin = -spin * e - vy * 0.2;
			}
			if (y < r) {
				y = r;
				vy = Math.abs(vy) * e;
			} else if (y > sh - r) {
				y = sh - r;
				if (Math.abs(vy) < 40 * gravity.get() + 5) {
					vy = 0;
				} else {
					vy = -Math.abs(vy) * e;
				}
				// Rolling on the floor: spin follows the ground speed, and friction slows both.
				spin += (vx / r * 57.3 - spin) * Math.min(1, dt * 8);
				vx *= Math.pow(0.25, dt);
			}
			spin *= Math.pow(0.7, dt);
			angle += spin * dt;
		}
		prevMx = mx;
		prevMy = my;

		if (shadow.on()) {
			float floor = (float) Math.max(0, 1 - (sh - y - r) / (sh * 0.5));
			Draw.shadow(g, (float) (x - r * 0.6), sh - 3f, (float) (r * 1.2), 2f, 2f, 6f, (int) (0x50 * floor) << 24);
		}
		g.pose().pushMatrix();
		g.pose().translate((float) x, (float) y);
		g.pose().rotate((float) Math.toRadians(angle));
		g.pose().scale((float) (s / 16), (float) (s / 16));
		g.fakeItem(stack(), -8, -8);
		g.pose().popMatrix();
	}

	/** A click with the chat open; returns true if it picked the trinket up. Positions are GUI coordinates. */
	public boolean grab(double mx, double my) {
		if (!isEnabled() || Math.hypot(mx - x, my - y) > size.get() / 2 + 4) {
			return false;
		}
		grabbed = true;
		releaseRequested = false;
		throwVx = 0;
		throwVy = 0;
		grabDx = x - mx;
		grabDy = y - my;
		return true;
	}

	/** The mouse button came up; the next frame throws the trinket with the mouse's speed. */
	public void release() {
		releaseRequested = grabbed;
	}

	@Override
	protected void onEnable() {
		x = -1;
		last = -1;
	}
}

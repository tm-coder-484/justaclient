package dev.justaclient.mixin;

import dev.justaclient.feature.impl.SmoothScrollFeature;
import dev.justaclient.ui.ScrollGlide;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth scrolling: the wheel moves a target, and each frame the list's scroll amount eases towards it through the normal
 * setter (so lists that place their entries on scroll keep them in step). Any other change to the scroll amount, such as
 * dragging the bar or jumping to an entry, stops the glide.
 */
@Mixin(AbstractSelectionList.class)
abstract class AbstractScrollAreaMixin implements ScrollGlide {
	@Shadow
	private double scrollAmount;

	@Shadow
	public abstract int getMaxScroll();

	@Shadow
	public abstract void setScrollAmount(double value);

	@Unique
	private double justaclient$target = Double.NaN;
	@Unique
	private long justaclient$last;
	@Unique
	private boolean justaclient$stepping;

	@Redirect(method = "mouseScrolled", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/AbstractSelectionList;setScrollAmount(D)V"))
	private void justaclient$glide(AbstractSelectionList self, double value) {
		SmoothScrollFeature smooth = SmoothScrollFeature.active();
		if (smooth == null) {
			self.setScrollAmount(value);
			return;
		}
		double delta = (value - scrollAmount) * smooth.distance();
		double from = Double.isNaN(justaclient$target) ? scrollAmount : justaclient$target;
		justaclient$target = Mth.clamp(from + delta, 0.0, getMaxScroll());
		justaclient$last = System.nanoTime();
	}

	@Override
	public void justaclient$step() {
		if (Double.isNaN(justaclient$target)) {
			return;
		}
		SmoothScrollFeature smooth = SmoothScrollFeature.active();
		double target = Mth.clamp(justaclient$target, 0.0, getMaxScroll());
		double next;
		if (smooth == null) {
			next = target;
		} else {
			long now = System.nanoTime();
			double dt = Math.min(0.1, (now - justaclient$last) / 1e9);
			justaclient$last = now;
			next = scrollAmount + (target - scrollAmount) * (1.0 - Math.exp(-smooth.rate() * dt));
			if (Math.abs(target - next) < 0.25) {
				next = target;
			}
		}
		justaclient$stepping = true;
		try {
			setScrollAmount(next);
		} finally {
			justaclient$stepping = false;
		}
		if (next == target) {
			justaclient$target = Double.NaN;
		}
	}

	@Inject(method = "setScrollAmount", at = @At("HEAD"))
	private void justaclient$stopGlide(double value, CallbackInfo ci) {
		if (!justaclient$stepping) {
			justaclient$target = Double.NaN;
		}
	}
}

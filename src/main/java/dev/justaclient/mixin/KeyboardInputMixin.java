package dev.justaclient.mixin;

import dev.justaclient.feature.impl.ToggleSprintFeature;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends ClientInput {
	/** Toggle sprint: report the sprint key as held, exactly like holding it down. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void justaclient$holdSprint(CallbackInfo ci) {
		if (ToggleSprintFeature.active()) {
			Input k = keyPresses;
			keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), k.shift(), true);
		}
	}
}

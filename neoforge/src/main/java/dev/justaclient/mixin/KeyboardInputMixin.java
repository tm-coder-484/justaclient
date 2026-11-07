package dev.justaclient.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.justaclient.feature.impl.ToggleSprintFeature;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LocalPlayer.class)
abstract class KeyboardInputMixin {
    @ModifyExpressionValue(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean justaclient$sprint(boolean original) { return original || ToggleSprintFeature.active(); }
}

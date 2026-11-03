package dev.justaclient.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The recipe book beside crafting tables, furnaces and the inventory, so panels can stay off it while it is open. */
@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccessor {
	@Accessor("recipeBookComponent")
	RecipeBookComponent<?> jac$recipeBook();
}

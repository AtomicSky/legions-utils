package com.legions.client.mixin;

import com.legions.client.LegionsPingController;
import com.legions.client.LegionsTeammateAttackWarning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void legions_client$rememberLastAttackedPlayer(Player player, Entity target, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        LegionsPingController.recordAttackedEntity(client, target);
        LegionsTeammateAttackWarning.warnIfTeammateAttack(client, player, target);
    }
}

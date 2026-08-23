package com.legions.client.mixin;

import com.legions.client.LegionsPingController;
import com.legions.client.LegionsTeammateAttackWarning;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void legions_client$rememberLastAttackedPlayer(PlayerEntity player, Entity target, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        LegionsPingController.recordAttackedEntity(client, target);
        LegionsTeammateAttackWarning.warnIfTeammateAttack(client, player, target);
    }
}

package com.legions.client.mixin;

import com.legions.client.LegionsPingController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onEntityDamage", at = @At("HEAD"))
    private void legions_client$recordLastAttacker(EntityDamageS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || packet.entityId() != client.player.getId()) {
            return;
        }

        Entity attacker = packet.createDamageSource(client.world).getAttacker();
        LegionsPingController.recordAttacker(client, attacker);
    }
}

package com.legions.client.mixin;

import com.legions.client.LegionsPingController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void legions_client$recordLastAttacker(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || packet.entityId() != client.player.getId()) {
            return;
        }

        Entity attacker = packet.getSource(client.level).getEntity();
        LegionsPingController.recordAttacker(client, attacker);
    }
}

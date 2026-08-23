package com.legions.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

public final class LegionsTeammateAttackWarning {
    private static final float WARNING_VOLUME = 0.8F;
    private static final float WARNING_PITCH = 1.1F;

    private LegionsTeammateAttackWarning() {
    }

    public static void warnIfTeammateAttack(Minecraft client, Player attacker, Entity target) {
        if (!LegionsClient.enabled(client)
                || client.player == null
                || attacker != client.player
                || !(target instanceof Player teammate)
                || teammate == attacker
                || LegionsFeatures.isSpectatorTeam(attacker)
                || LegionsFeatures.isSpectatorTeam(teammate)
                || isFreeForAllTeam(attacker)
                || isFreeForAllTeam(teammate)
                || !LegionsFeatures.isTeammate(attacker, teammate)) {
            return;
        }

        Component message = Component.empty()
                .append(Component.literal("Don't hit ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(teammate.getName().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" - they're your teammate!").withStyle(ChatFormatting.RED));
        client.player.sendOverlayMessage(message);
        client.player.playSound(SoundEvents.VILLAGER_NO, WARNING_VOLUME, WARNING_PITCH);
    }

    private static boolean isFreeForAllTeam(Player player) {
        PlayerTeam team = player.getTeam();
        return team != null
                && (isFreeForAllLabel(team.getName())
                || isFreeForAllLabel(team.getDisplayName().getString()));
    }

    private static boolean isFreeForAllLabel(String value) {
        if (value == null) {
            return false;
        }
        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = Character.toLowerCase(value.charAt(index));
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                normalized.append(character);
            }
        }
        String label = normalized.toString();
        return label.equals("ffa") || label.equals("freeforall");
    }
}

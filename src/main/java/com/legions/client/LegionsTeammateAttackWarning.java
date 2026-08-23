package com.legions.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class LegionsTeammateAttackWarning {
    private static final float WARNING_VOLUME = 0.8F;
    private static final float WARNING_PITCH = 1.1F;

    private LegionsTeammateAttackWarning() {
    }

    public static void warnIfTeammateAttack(MinecraftClient client, PlayerEntity attacker, Entity target) {
        if (!LegionsClient.enabled(client)
                || client.player == null
                || attacker != client.player
                || !(target instanceof PlayerEntity teammate)
                || teammate == attacker
                || LegionsFeatures.isSpectatorTeam(attacker)
                || LegionsFeatures.isSpectatorTeam(teammate)
                || isFreeForAllTeam(attacker)
                || isFreeForAllTeam(teammate)
                || !LegionsFeatures.isTeammate(attacker, teammate)) {
            return;
        }

        Text message = Text.empty()
                .append(Text.literal("Don't hit ").formatted(Formatting.RED, Formatting.BOLD))
                .append(teammate.getName().copy().formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" - they're your teammate!").formatted(Formatting.RED));
        client.player.sendMessage(message, true);
        client.player.playSound(SoundEvents.ENTITY_VILLAGER_NO, WARNING_VOLUME, WARNING_PITCH);
    }

    private static boolean isFreeForAllTeam(PlayerEntity player) {
        Team team = player.getScoreboardTeam();
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

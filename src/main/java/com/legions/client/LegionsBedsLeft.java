package com.legions.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Pattern;

/** Chat-driven round state; no block scans or entity movement tracking. */
final class LegionsBedsLeft {
    static final String[] TEAMS = {"green", "blue", "red", "purple"};
    static final int[] COLORS = {0xFF55FF55, 0xFF5555FF, 0xFFFF5555, 0xFFCC55FF};
    private static final Pattern FORMATTING = Pattern.compile("(?i)(?:Â)?§[0-9A-FK-ORX]");
    private static final Pattern SIGNAL = Pattern.compile("(?:^|.*[\\s>:])HU:Bed Wars$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESET = Pattern.compile(
            "^\\[Legions]\\s+(?:[A-Za-z0-9_]{1,16} started hosting an? .+ event on map .+!"
                    + "|The game is starting\\.*!"
                    + "|Unfortunately, the event you were participating in has been cancelled\\. Thanks for participating!"
                    + "|Team .+ has won the game! Thanks for participating in this event!)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BROKEN = Pattern.compile(
            "^(?:\\[[^]\\r\\n]+]\\s*)*(green|blue|red|purple)(?:2)? bed is broken[!.]?$",
            Pattern.CASE_INSENSITIVE);
    private static Object connection;
    private static boolean active;
    private static int broken;
    private static int present;
    private static boolean secondSession;
    private static int revision;

    static void tick(MinecraftClient client) {
        Object current = client == null ? null : client.getNetworkHandler();
        if (current != connection || (current == null && active)) {
            reset();
            connection = current;
        }
        if (active && client != null && client.player != null && client.player.getScoreboardTeam() != null) {
            String name = client.player.getScoreboardTeam().getName().toLowerCase(Locale.ROOT);
            if (teamIndex(name) >= 0 && secondSession != name.endsWith("2")) {
                secondSession = name.endsWith("2");
                revision++;
            }
        }
    }

    static void receive(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        tick(client);
        if (LegionsClient.enabled(client)) accept(text.getString());
    }

    static void accept(String raw) {
        String message = FORMATTING.matcher(raw).replaceAll("").replace("\u200B", "").strip();
        if (RESET.matcher(message).matches()) {
            reset();
        } else if (SIGNAL.matcher(message).matches()) {
            // Duplicate signals must not resurrect already broken beds.
            if (!active) {
                active = true;
                present = (1 << 1) | (1 << 2);
                revision++;
            }
        } else if (active) {
            var match = BROKEN.matcher(message);
            if (match.matches()) {
                int bit = 1 << teamIndex(match.group(1));
                if ((broken & bit) == 0) {
                    broken |= bit;
                    present |= bit;
                    revision++;
                }
            }
        }
    }

    static int teamIndex(String name) {
        if (name == null) return -1;
        for (int i = 0; i < TEAMS.length; i++) {
            if (TEAMS[i].equalsIgnoreCase(name) || (TEAMS[i] + "2").equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    static boolean inSession(String name) { return name.endsWith("2") == secondSession; }
    static boolean active() { return active; }
    static int revision() { return revision; }
    static boolean broken(int index) { return (broken & (1 << index)) != 0; }
    static boolean present(int index) { return (present & (1 << index)) != 0; }
    static void observe(int index) { present |= 1 << index; }

    static void reset() {
        active = false;
        broken = present = 0;
        secondSession = false;
        revision++;
    }
}

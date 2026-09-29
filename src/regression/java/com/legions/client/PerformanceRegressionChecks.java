package com.legions.client;

import com.legions.client.config.LegionsConfig;
import net.minecraft.text.Style;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** No live client, network requests, or changes to the user's config. */
public final class PerformanceRegressionChecks {
    public static void main(String[] args) throws Exception {
        checkBorderVertices();
        checkFightSelection();
        checkRatingStyles();
        checkTeamRows();
        checkConfigComparison();
        checkBedsLeft();
        System.out.println("PASS: border geometry/reuse, stable fight selection, rating styles, HUD row invalidation, config comparison");
    }

    private static void checkBedsLeft() {
        LegionsBedsLeft.reset();
        LegionsBedsLeft.accept("[Broadcast] blue bed is broken");
        require(!LegionsBedsLeft.active(), "Bed broadcast enabled an ordinary round");
        LegionsBedsLeft.accept("Someone: I mentioned HU:Bed Wars earlier");
        require(!LegionsBedsLeft.active(), "Quoted signal activated bed HUD");
        LegionsBedsLeft.accept("§a[Host] Steve: HU:Bed Wars");
        require(LegionsBedsLeft.active(), "Decorated host signal not detected");
        require(LegionsBedsLeft.present(1) && LegionsBedsLeft.present(2), "Default teams absent");
        LegionsBedsLeft.accept("[Broadcast] BLUE bed is broken!");
        require(LegionsBedsLeft.broken(1) && !LegionsBedsLeft.broken(2), "Wrong bed broken");
        LegionsBedsLeft.accept("HU:Bed Wars");
        require(LegionsBedsLeft.broken(1), "Duplicate signal restored a bed");
        LegionsBedsLeft.accept("[Legions] purple bed is broken");
        require(LegionsBedsLeft.present(3) && LegionsBedsLeft.broken(3), "Optional team not tracked");
        require(LegionsBedsLeft.teamIndex("Blue2") == 1 && !LegionsBedsLeft.inSession("blue2"), "Session isolation failed");
        String[] resets = {
                "[Legions] Team Blue has won the game! Thanks for participating in this event!",
                "[Legions] Unfortunately, the event you were participating in has been cancelled. Thanks for participating!",
                "[Legions] Steve started hosting a Squads event on map Entangle!",
                "[Legions] The game is starting...!"
        };
        for (String reset : resets) {
            LegionsBedsLeft.accept(reset);
            require(!LegionsBedsLeft.active() && !LegionsBedsLeft.broken(1), "Round state leaked: " + reset);
            LegionsBedsLeft.accept("HU:Bed Wars");
        }
        LegionsBedsLeft.tick(null);
        require(!LegionsBedsLeft.active(), "Disconnect retained Bed Wars state");
        LegionsBedsLeft.reset();
        System.out.println("PASS: Beds Left signal, broadcasts, duplicates, optional teams, session names, round resets");
    }

    private static void checkBorderVertices() throws Exception {
        Class<?> type = Class.forName("com.legions.client.LegionsWorldBorder$BorderSegment");
        Constructor<?> ctor = type.getDeclaredConstructor(double.class, double.class, double.class, double.class);
        ctor.setAccessible(true);
        Object segment = ctor.newInstance(-12.5, 9.25, 44.75, -8.5);
        Method update = method(type, "updateConnectorVertices", double.class, double.class);
        Field vertices = field(type, "connectorVertices");
        Random random = new Random(28471);
        for (int trial = 0; trial < 10000; trial++) {
            double cameraY = random.nextDouble(-128, 400);
            double range = random.nextBoolean() ? 16 : 32;
            double first = Math.max(-64, Math.ceil((cameraY - range) / 16) * 16);
            double last = Math.min(320, cameraY + range);
            List<Vec3d> expected = new ArrayList<>();
            for (double y = first; y <= last; y += 16) {
                expected.add(new Vec3d(-12.5, y, 9.25));
                expected.add(new Vec3d(44.75, y, -8.5));
            }
            update.invoke(segment, first, last);
            Vec3d[] actual = (Vec3d[]) vertices.get(segment);
            require(List.of(actual).equals(expected), "Connector geometry changed at " + first + ".." + last
                    + ": " + List.of(actual) + " != " + expected);
            Vec3d[] snapshot = actual.clone();
            update.invoke(segment, first, last);
            require(vertices.get(segment) == actual, "Unchanged connectors reallocated array");
            for (int i = 0; i < actual.length; i++) {
                require(actual[i] == snapshot[i], "Unchanged connectors reallocated positions");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkFightSelection() throws Exception {
        Class<?> type = Class.forName("com.legions.client.LegionsPingController$FightCandidate");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Method set = method(type, "set", Vec3d.class, double.class, int.class, int.class, double.class, double.class);
        List<Object> scratch = (List<Object>) field(LegionsPingController.class, "fightCandidateScratch").get(null);
        Method select = method(LegionsPingController.class, "selectBestFightCandidate");
        Comparator<Candidate> oldOrder = Comparator.comparingInt(Candidate::players).reversed()
                .thenComparingDouble(Candidate::spread)
                .thenComparing(Comparator.comparingInt(Candidate::teams).reversed())
                .thenComparingDouble(Candidate::distance);
        Random random = new Random(7392);
        for (int trial = 0; trial < 1500; trial++) {
            scratch.clear();
            List<Candidate> expected = new ArrayList<>();
            int count = trial == 0 ? 0 : random.nextInt(1, 201);
            for (int i = 0; i < count; i++) {
                // Small ranges deliberately produce exact ties; stable sort must
                // select the same object, not merely equivalent field values.
                int players = random.nextInt(3, 8), teams = random.nextInt(2, 5);
                double spread = random.nextInt(3), distance = random.nextInt(3);
                Object value = set.invoke(ctor.newInstance(), Vec3d.ZERO, 0.0, players, teams, spread, distance);
                scratch.add(value);
                expected.add(new Candidate(value, players, teams, spread, distance));
            }
            expected.sort(oldOrder);
            select.invoke(null);
            require(scratch.size() == expected.size(), "Candidate lost during selection");
            if (!expected.isEmpty()) require(scratch.getFirst() == expected.getFirst().value(), "Fight selection/tie order changed");
        }
        scratch.clear();
    }

    private static void checkRatingStyles() throws Exception {
        Class<?> type = Class.forName("com.legions.client.LegionsFeatures$TabListTag");
        Constructor<?> ctor = type.getDeclaredConstructor(String.class, int.class);
        ctor.setAccessible(true);
        int[] colors = {0xFCFCFC, 0xDAEBFC, 0xB9DAFC, 0x97CAFC, 0x76B9FC,
                0x54A8EB, 0x3286DA, 0x1165CA, 0x1143A8, 0x226576, 0x438643,
                0x65A811, 0x86CA00, 0xA8EB00, 0xCACA00, 0xEBA800, 0xFC8600,
                0xFC5400, 0xFC3200, 0xDA1100, 0xA80000};
        for (int rating = -1; rating <= 2100; rating++) {
            Object tag = ctor.newInstance("rating", rating);
            Style style = (Style) field(type, "style").get(tag);
            Style bracket = (Style) field(type, "bracketStyle").get(tag);
            int expected = rating < 0 ? 0xA0A0A0 : colors[Math.min(20, rating / 100)];
            require(style.getColor().getRgb() == expected, "Rating color changed at " + rating);
            require(bracket.getColor().getRgb() == (rating >= 2000 ? 0 : 0xFFFFFF), "Bracket color changed");
            require(style.isBold() == (rating >= 2000) && bracket.isBold() == (rating >= 2000), "Rating bold changed");
        }
    }

    private static void checkTeamRows() throws Exception {
        Class<?> mutable = Class.forName("com.legions.client.LegionsHud$MutableTeamCount");
        Class<?> rowType = Class.forName("com.legions.client.LegionsHud$TeamCount");
        Constructor<?> ctor = mutable.getDeclaredConstructor(String.class, int.class, int.class, int.class);
        ctor.setAccessible(true);
        Object count = ctor.newInstance("Blue", 0xFF5555FF, 1600, 1);
        Object row = method(mutable, "toTeamCount").invoke(count);
        Method matches = method(mutable, "matches", rowType);
        require((boolean) matches.invoke(count, row), "Identical HUD row not reusable");
        for (String name : List.of("name", "color", "count", "ratingTotal", "knownRatings")) {
            Field changed = field(rowType, name);
            Object original = changed.get(row);
            changed.set(row, original instanceof String ? "Red" : (Integer) original + 1);
            require(!(boolean) matches.invoke(count, row), "HUD row failed to invalidate " + name);
            changed.set(row, original);
        }
    }

    private static void checkConfigComparison() {
        LegionsConfig first = new LegionsConfig().normalize();
        LegionsConfig second = first.copy();
        require(first.sameSettings(second), "Copied config differs");
        second.pingRows.getFirst().message = "  " + first.pingRows.getFirst().message + "  ";
        require(first.sameSettings(second), "Normalized-equivalent message differs");
        second.pingRows.getFirst().message = "different message";
        require(!first.sameSettings(second), "Message edit not detected");
        second = first.copy();
        second.pingRows.getFirst().presses = 5;
        require(!first.sameSettings(second), "Press-count edit not detected");
        second = first.copy();
        second.pingRows.removeLast();
        require(!first.sameSettings(second), "Row removal not detected");
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Candidate(Object value, int players, int teams, double spread, double distance) {}
}

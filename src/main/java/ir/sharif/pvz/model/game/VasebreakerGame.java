package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Vasebreaker: break every vase without letting the zombies inside reach the
 * house. No sun falls and no plant is selected; one-use seed packets fall out
 * of vases and vanish if not picked up quickly.
 */
class VasebreakerGame implements MinigameLogic {

    private static final double PACKET_LIFETIME_SECONDS = 15;

    /** What a vase holds and whether its kind is visible (plant/ghoul vases). */
    private record Vase(String content, String kind) {
    }

    private record FallenPacket(String plant, double expiresAt) {
    }

    private final Map<Integer, Vase> vases = new LinkedHashMap<>();
    private final Map<Integer, FallenPacket> packets = new HashMap<>();
    private final List<String> hand = new ArrayList<>();
    private final int stage;
    private final Random random;
    private double now;

    VasebreakerGame(int stage, Random random) {
        this.stage = stage;
        this.random = random;
    }

    @Override
    public void init(GameSession session) {
        session.setWavesEnabled(false);
        session.disableMowers();
        layVases(session);
        session.eventLog().add("Break every vase (break vase -l (x, y)) and survive what crawls out!");
    }

    private void layVases(GameSession session) {
        List<String> zombieFills = stage >= 3
                ? List.of("zombie:normal", "zombie:conehead", "zombie:buckethead")
                : List.of("zombie:normal", "zombie:conehead");
        List<String> packetFills = List.of("packet:peashooter", "packet:snow-pea",
                "packet:wall-nut", "packet:repeater", "packet:cherry-bomb");
        int count = 4 + 4 * stage;
        List<Integer> tiles = new ArrayList<>();
        for (int row = 0; row < GameSession.ROWS; row++) {
            for (int col = 4; col < GameSession.COLS; col++) {
                tiles.add(LevelSpec.tileKey(row, col));
            }
        }
        java.util.Collections.shuffle(tiles, random);
        for (int i = 0; i < count && i < tiles.size(); i++) {
            String content = random.nextBoolean()
                    ? zombieFills.get(random.nextInt(zombieFills.size()))
                    : packetFills.get(random.nextInt(packetFills.size()));
            vases.put(tiles.get(i), new Vase(content, "normal"));
        }
        vases.put(tiles.get(count), new Vase("packet:repeater", "plant"));
        if (stage >= 3) {
            vases.put(tiles.get(count + 1), new Vase("zombie:gargantuar", "ghoul"));
        }
        session.eventLog().add(vases.size() + " vases are stacked on the yard; 'show vases' lists them.");
    }

    @Override
    public void tick(GameSession session, double seconds) {
        now = seconds;
        for (Map.Entry<Integer, FallenPacket> entry : new HashMap<>(packets).entrySet()) {
            if (seconds >= entry.getValue().expiresAt()) {
                packets.remove(entry.getKey());
                session.eventLog().add("The " + entry.getValue().plant() + " seed packet crumbled away.");
            }
        }
        if (vases.isEmpty() && session.getZombies().isEmpty() && !session.isOver()) {
            session.winNow("Every vase is broken; the yard is safe!");
        }
    }

    @Override
    public String plantingRejection(int x, int y) {
        return vases.containsKey(LevelSpec.tileKey(y - 1, x - 1))
                ? "Error: a vase occupies (" + x + ", " + y + ")." : null;
    }

    @Override
    public boolean freePlantMode() {
        return true;
    }

    @Override
    public String takeFromHand(String type) {
        if (!hand.remove(type)) {
            return "Error: you do not hold a " + type + " packet.";
        }
        return null;
    }

    @Override
    public List<String> handContents() {
        return new ArrayList<>(hand);
    }

    @Override
    public String breakVase(GameSession session, int x, int y) {
        Vase vase = vases.remove(LevelSpec.tileKey(y - 1, x - 1));
        if (vase == null) {
            return "Error: there is no vase at (" + x + ", " + y + ").";
        }
        String[] parts = vase.content().split(":");
        if (parts[0].equals("zombie")) {
            session.spawnZombie(GameCatalog.get().zombie(parts[1]), y - 1, x);
            return "The vase at (" + x + ", " + y + ") held a " + parts[1] + "!";
        }
        packets.put(LevelSpec.tileKey(y - 1, x - 1),
                new FallenPacket(parts[1], now + PACKET_LIFETIME_SECONDS));
        return "A " + parts[1] + " seed packet fell out of the vase at (" + x + ", " + y
                + "); take it with 'take packet -l (" + x + ", " + y + ")'.";
    }

    @Override
    public String takePacket(GameSession session, int x, int y) {
        FallenPacket packet = packets.remove(LevelSpec.tileKey(y - 1, x - 1));
        if (packet == null) {
            return "Error: there is no seed packet at (" + x + ", " + y + ").";
        }
        hand.add(packet.plant());
        return "You picked up a " + packet.plant() + " packet; plant it anywhere for free.";
    }

    @Override
    public List<String> vasesInfo() {
        if (vases.isEmpty()) {
            return List.of("No vase is left.");
        }
        List<String> lines = new ArrayList<>();
        vases.forEach((key, vase) -> lines.add("Vase at (" + (key % GameSession.COLS + 1) + ", "
                + (key / GameSession.COLS + 1) + ")"
                + (vase.kind().equals("normal") ? "" : " [" + vase.kind() + " vase]")));
        return lines;
    }
}

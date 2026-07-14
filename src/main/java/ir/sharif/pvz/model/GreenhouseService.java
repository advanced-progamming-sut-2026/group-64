package ir.sharif.pvz.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Greenhouse rules: planting a random flower, real-clock growth (marigold 2h,
 * any other plant 8h), diamond-paid speed-up and collecting the rewards.
 */
public class GreenhouseService {

    public static final int COLUMNS = 5;
    public static final int ROW_COUNT = 4;
    public static final int MARIGOLD_COINS = 500;
    public static final int MARIGOLD_GROW_HOURS = 2;
    public static final int PLANT_GROW_HOURS = 8;
    private static final long HOUR_MILLIS = 3_600_000L;

    private final UserRepository userRepository;
    private final LongSupplier clock;
    private final Random random;

    public GreenhouseService(UserRepository userRepository, LongSupplier clock, Random random) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.random = random;
    }

    /**
     * One line per pot: locked, empty, growing (with remaining time) or ready.
     */
    public List<String> describe(User user) {
        List<String> lines = new ArrayList<>();
        long now = clock.getAsLong();
        for (int y = 1; y <= ROW_COUNT; y++) {
            for (int x = 1; x <= COLUMNS; x++) {
                GreenhousePot pot = potAt(user, x, y);
                lines.add("(" + x + ", " + y + "): " + describePot(pot, now));
            }
        }
        return lines;
    }

    private String describePot(GreenhousePot pot, long now) {
        if (!pot.isUnlocked()) {
            return "locked";
        }
        if (pot.isEmpty()) {
            return "empty";
        }
        if (pot.isReady(now)) {
            return pot.getPlantType() + " [ready]";
        }
        long remainingMinutes = (pot.getReadyAtMillis() - now + 59_999) / 60_000;
        return pot.getPlantType() + " (ready in " + remainingMinutes + " min)";
    }

    /**
     * Plants a random flower: 50% marigold, 50% one of the user's unlocked plants.
     */
    public String plantPot(User user, int x, int y) {
        String invalid = invalidPot(user, x, y);
        if (invalid != null) {
            return invalid;
        }
        GreenhousePot pot = potAt(user, x, y);
        if (!pot.isEmpty()) {
            return "Error: the pot at (" + x + ", " + y + ") is already growing "
                    + pot.getPlantType() + ".";
        }
        String type = "marigold";
        int hours = MARIGOLD_GROW_HOURS;
        if (random.nextBoolean()) {
            List<String> unlocked = new ArrayList<>(user.getUnlockedPlants());
            type = unlocked.get(random.nextInt(unlocked.size()));
            hours = PLANT_GROW_HOURS;
        }
        pot.plant(type, clock.getAsLong() + hours * HOUR_MILLIS);
        userRepository.save();
        return "A " + type + " was planted at (" + x + ", " + y + "); it grows in " + hours + " hours.";
    }

    /**
     * Collects a fully grown flower: marigold pays coins, any other plant
     * stores a one-shot boost for its type (one stored boost per type).
     */
    public String collect(User user, int x, int y) {
        String invalid = invalidPot(user, x, y);
        if (invalid != null) {
            return invalid;
        }
        GreenhousePot pot = potAt(user, x, y);
        if (pot.isEmpty()) {
            return "Error: there is nothing growing at (" + x + ", " + y + ").";
        }
        if (!pot.isReady(clock.getAsLong())) {
            return "Error: the " + pot.getPlantType() + " at (" + x + ", " + y + ") is not fully grown yet.";
        }
        String type = pot.getPlantType();
        pot.clear();
        String result;
        if (type.equals("marigold")) {
            user.addCoins(MARIGOLD_COINS);
            result = "Collected a marigold: +" + MARIGOLD_COINS + " coins (total " + user.getCoins() + ").";
        } else if (user.getStoredBoosts().add(type)) {
            result = "Collected a " + type + ": its next use in a level starts boosted.";
        } else {
            result = "Collected a " + type + ", but a boost for it was already stored; the pot is free again.";
        }
        userRepository.save();
        return result;
    }

    /**
     * Finishes growth instantly for 1 diamond per remaining hour (rounded up).
     */
    public String grow(User user, int x, int y) {
        String invalid = invalidPot(user, x, y);
        if (invalid != null) {
            return invalid;
        }
        GreenhousePot pot = potAt(user, x, y);
        long now = clock.getAsLong();
        if (pot.isEmpty()) {
            return "Error: there is nothing growing at (" + x + ", " + y + ").";
        }
        if (pot.isReady(now)) {
            return "Error: the " + pot.getPlantType() + " at (" + x + ", " + y + ") is already fully grown.";
        }
        int cost = (int) ((pot.getReadyAtMillis() - now + HOUR_MILLIS - 1) / HOUR_MILLIS);
        if (user.getDiamonds() < cost) {
            return "Error: speeding this up costs " + cost + " diamonds; you have " + user.getDiamonds() + ".";
        }
        user.spendDiamonds(cost);
        pot.finishNow(now);
        userRepository.save();
        return "Spent " + cost + " diamonds; the " + pot.getPlantType() + " at ("
                + x + ", " + y + ") is fully grown.";
    }

    private String invalidPot(User user, int x, int y) {
        if (x < 1 || x > COLUMNS || y < 1 || y > ROW_COUNT) {
            return "Error: (" + x + ", " + y + ") is not a valid pot.";
        }
        if (!potAt(user, x, y).isUnlocked()) {
            return "Error: the pot at (" + x + ", " + y + ") is locked; buy it in the shop.";
        }
        return null;
    }

    private GreenhousePot potAt(User user, int x, int y) {
        return user.getGreenhousePots().get((y - 1) * COLUMNS + (x - 1));
    }
}

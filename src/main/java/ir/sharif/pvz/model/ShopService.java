package ir.sharif.pvz.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * The shop: permanent items plus a daily seed-packet offer that refreshes at
 * midnight (system clock) and can be bought once per day.
 */
public class ShopService {

    public static final int POT_COIN_PRICE = 2000;
    public static final int PLANT_FOOD_DIAMOND_PRICE = 3;
    public static final int MAX_PENDING_PLANT_FOOD = 3;
    public static final int RANDOM_BUNDLE_COIN_PRICE = 1000;
    public static final int RANDOM_BUNDLE_PACKETS = 5;
    public static final int CHOICE_BUNDLE_DIAMOND_PRICE = 5;
    public static final int CHOICE_BUNDLE_PACKETS = 10;
    public static final int EXCHANGE_DIAMOND_PRICE = 5;
    public static final int EXCHANGE_COINS = 500;
    public static final int DAILY_BUNDLE_PACKETS = 10;
    public static final int DAILY_BUNDLE_COIN_PRICE = 1600;

    private final UserRepository userRepository;
    private final LongSupplier clock;
    private final Random random;

    public ShopService(UserRepository userRepository, LongSupplier clock, Random random) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.random = random;
    }

    public List<String> listItems() {
        List<String> lines = new ArrayList<>();
        lines.add("pot: " + POT_COIN_PRICE + " coins - unlocks one greenhouse pot (max 20)");
        lines.add("plant-food: " + PLANT_FOOD_DIAMOND_PRICE
                + " diamonds - a plant food at the start of the next level (max 3 stored)");
        lines.add("random-packets: " + RANDOM_BUNDLE_COIN_PRICE + " coins - "
                + RANDOM_BUNDLE_PACKETS + " seed packets of a random unlocked plant");
        lines.add("choice-packets: " + CHOICE_BUNDLE_DIAMOND_PRICE + " diamonds - "
                + CHOICE_BUNDLE_PACKETS + " seed packets of an unlocked plant of your choice (-t)");
        lines.add("exchange: " + EXCHANGE_DIAMOND_PRICE + " diamonds - " + EXCHANGE_COINS + " coins");
        return lines;
    }

    public List<String> describeDaily(User user) {
        String today = today();
        List<String> lines = new ArrayList<>();
        lines.add("Daily offer (" + today + "): " + DAILY_BUNDLE_PACKETS + " seed packets of "
                + dailyPlant(user) + " for " + DAILY_BUNDLE_COIN_PRICE + " coins (20% off 2000)");
        lines.add("Buy it with: shop buy -i daily -n 1");
        if (today.equals(user.getLastDailyPurchaseDate())) {
            lines.add("You already bought today's offer.");
        }
        return lines;
    }

    public String buy(User user, String itemId, int count, String plantType) {
        if (count < 1) {
            return "Error: the count must be at least 1.";
        }
        return switch (itemId) {
            case "pot" -> buyPots(user, count);
            case "plant-food" -> buyPlantFood(user, count);
            case "random-packets" -> buyRandomPackets(user, count);
            case "choice-packets" -> buyChoicePackets(user, count, plantType);
            case "exchange" -> exchange(user, count);
            case "daily" -> buyDaily(user, count);
            default -> "Error: there is no shop item with id '" + itemId + "'.";
        };
    }

    private String buyPots(User user, int count) {
        int price = POT_COIN_PRICE * count;
        if (user.getCoins() < price) {
            return "Error: " + count + " pot(s) cost " + price + " coins; you have " + user.getCoins() + ".";
        }
        int unlockedNow = 0;
        for (int i = 0; i < count; i++) {
            if (!user.unlockNextPot()) {
                break;
            }
            unlockedNow++;
        }
        if (unlockedNow == 0) {
            return "Error: all 20 greenhouse pots are already unlocked.";
        }
        user.spendCoins(POT_COIN_PRICE * unlockedNow);
        userRepository.save();
        return "Unlocked " + unlockedNow + " greenhouse pot(s).";
    }

    private String buyPlantFood(User user, int count) {
        if (user.getPendingPlantFood() + count > MAX_PENDING_PLANT_FOOD) {
            return "Error: you cannot store more than " + MAX_PENDING_PLANT_FOOD + " plant foods.";
        }
        int price = PLANT_FOOD_DIAMOND_PRICE * count;
        if (user.getDiamonds() < price) {
            return "Error: " + count + " plant food(s) cost " + price + " diamonds; you have "
                    + user.getDiamonds() + ".";
        }
        user.spendDiamonds(price);
        user.setPendingPlantFood(user.getPendingPlantFood() + count);
        userRepository.save();
        return "You will start the next level with " + user.getPendingPlantFood() + " plant food(s).";
    }

    private String buyRandomPackets(User user, int count) {
        int price = RANDOM_BUNDLE_COIN_PRICE * count;
        if (user.getCoins() < price) {
            return "Error: " + count + " bundle(s) cost " + price + " coins; you have " + user.getCoins() + ".";
        }
        user.spendCoins(price);
        List<String> unlocked = new ArrayList<>(user.getUnlockedPlants());
        StringBuilder result = new StringBuilder("You received:");
        for (int i = 0; i < count; i++) {
            String plant = unlocked.get(random.nextInt(unlocked.size()));
            user.getSeedPackets().merge(plant, RANDOM_BUNDLE_PACKETS, Integer::sum);
            result.append(' ').append(RANDOM_BUNDLE_PACKETS).append("x ").append(plant)
                    .append(i == count - 1 ? "." : ",");
        }
        userRepository.save();
        return result.toString();
    }

    private String buyChoicePackets(User user, int count, String plantType) {
        if (plantType == null) {
            return "Error: choose the plant with -t <plant_type>.";
        }
        if (!user.getUnlockedPlants().contains(plantType)) {
            return "Error: you can only buy packets of plants you have unlocked.";
        }
        int price = CHOICE_BUNDLE_DIAMOND_PRICE * count;
        if (user.getDiamonds() < price) {
            return "Error: " + count + " bundle(s) cost " + price + " diamonds; you have "
                    + user.getDiamonds() + ".";
        }
        user.spendDiamonds(price);
        user.getSeedPackets().merge(plantType, CHOICE_BUNDLE_PACKETS * count, Integer::sum);
        userRepository.save();
        return "You received " + CHOICE_BUNDLE_PACKETS * count + " seed packets of " + plantType + ".";
    }

    private String exchange(User user, int count) {
        int price = EXCHANGE_DIAMOND_PRICE * count;
        if (user.getDiamonds() < price) {
            return "Error: exchanging costs " + price + " diamonds; you have " + user.getDiamonds() + ".";
        }
        user.spendDiamonds(price);
        user.addCoins(EXCHANGE_COINS * count);
        userRepository.save();
        return "Exchanged " + price + " diamonds for " + EXCHANGE_COINS * count + " coins.";
    }

    private String buyDaily(User user, int count) {
        if (count != 1) {
            return "Error: the daily offer can only be bought once.";
        }
        String today = today();
        if (today.equals(user.getLastDailyPurchaseDate())) {
            return "Error: you already bought today's offer; come back tomorrow.";
        }
        if (user.getCoins() < DAILY_BUNDLE_COIN_PRICE) {
            return "Error: the daily offer costs " + DAILY_BUNDLE_COIN_PRICE + " coins; you have "
                    + user.getCoins() + ".";
        }
        String plant = dailyPlant(user);
        user.spendCoins(DAILY_BUNDLE_COIN_PRICE);
        user.getSeedPackets().merge(plant, DAILY_BUNDLE_PACKETS, Integer::sum);
        user.setLastDailyPurchaseDate(today);
        userRepository.save();
        return "You received " + DAILY_BUNDLE_PACKETS + " seed packets of " + plant + ".";
    }

    /**
     * Today's offered plant: picked deterministically from the date so it
     * stays the same all day and changes at midnight.
     */
    private String dailyPlant(User user) {
        List<String> unlocked = new ArrayList<>(user.getUnlockedPlants());
        int index = Math.floorMod(today().hashCode(), unlocked.size());
        return unlocked.get(index);
    }

    private String today() {
        return LocalDate.ofInstant(Instant.ofEpochMilli(clock.getAsLong()), ZoneId.systemDefault()).toString();
    }
}

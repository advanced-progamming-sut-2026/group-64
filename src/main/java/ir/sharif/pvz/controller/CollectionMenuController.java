package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.model.game.PlantSpec;
import ir.sharif.pvz.model.game.ZombieSpec;
import ir.sharif.pvz.view.ConsoleView;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The collection menu: browse earned/all plants and zombies, upgrade and purchase plants.
 */
public class CollectionMenuController extends MenuController {

    private static final int PURCHASE_COIN_COST = 2000;
    private static final int UPGRADE_COINS_PER_LEVEL = 1000;
    private static final int UPGRADE_PACKETS_PER_LEVEL = 3;

    private static final Pattern SHOW_PLANT = Pattern.compile("^menu\\s+collection\\s+show-plant\\s+-p\\s+(\\S+)$");
    private static final Pattern SHOW_ZOMBIE = Pattern.compile("^menu\\s+collection\\s+show-zombie\\s+-z\\s+(\\S+)$");
    private static final Pattern UPGRADE = Pattern.compile("^menu\\s+collection\\s+upgrade-plant\\s+-p\\s+(\\S+)$");
    private static final Pattern PURCHASE = Pattern.compile("^menu\\s+collection\\s+purchase-plant\\s+-p\\s+(\\S+)$");

    public CollectionMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.COLLECTION;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of();
    }

    @Override
    protected void onExit() {
        context.setCurrentMenu(MenuType.GAME);
        view.info("You are back in the game menu.");
    }

    @Override
    protected void handleCommand(String input) {
        Matcher matcher;
        if (input.equals("menu collection show-plants")) {
            listNames(context.getCurrentUser().getUnlockedPlants(), "You have no plant yet.");
        } else if (input.equals("menu collection show-all-plants")) {
            listAllPlants();
        } else if (input.equals("menu collection show-zombies")) {
            listNames(context.getCurrentUser().getObservedZombies(), "You have not seen any zombie yet.");
        } else if (input.equals("menu collection show-all-zombies")) {
            for (ZombieSpec spec : GameCatalog.get().allZombies()) {
                view.info("- " + spec.getName());
            }
        } else if ((matcher = SHOW_PLANT.matcher(input)).matches()) {
            showPlant(matcher.group(1));
        } else if ((matcher = SHOW_ZOMBIE.matcher(input)).matches()) {
            showZombie(matcher.group(1));
        } else if ((matcher = UPGRADE.matcher(input)).matches()) {
            upgradePlant(matcher.group(1));
        } else if ((matcher = PURCHASE.matcher(input)).matches()) {
            purchasePlant(matcher.group(1));
        } else {
            view.unknownCommand();
        }
    }

    private void listNames(Set<String> names, String emptyMessage) {
        if (names.isEmpty()) {
            view.info(emptyMessage);
            return;
        }
        names.forEach(name -> view.info("- " + name));
    }

    private void listAllPlants() {
        Set<String> unlocked = context.getCurrentUser().getUnlockedPlants();
        for (PlantSpec spec : GameCatalog.get().allPlants()) {
            view.info("- " + spec.getName() + (unlocked.contains(spec.getName()) ? "" : " [locked]"));
        }
    }

    private void showPlant(String name) {
        PlantSpec spec = GameCatalog.get().plant(name);
        if (spec == null) {
            view.error("There is no plant named '" + name + "'.");
            return;
        }
        User user = context.getCurrentUser();
        view.info(spec.getName() + ":");
        view.info("    category: " + spec.getCategory().name().toLowerCase(Locale.ROOT));
        view.info("    sun cost: " + spec.getSunCost());
        view.info("    recharge: " + spec.getRechargeSeconds() + "s");
        view.info("    hp: " + spec.getHp());
        view.info("    damage: " + spec.getDamage());
        view.info("    tags: " + String.join(", ", spec.getTags()));
        view.info("    level: " + user.getPlantLevel(name));
        view.info("    unlocked: " + user.getUnlockedPlants().contains(name));
    }

    private void showZombie(String name) {
        ZombieSpec spec = GameCatalog.get().zombie(name);
        if (spec == null) {
            view.error("There is no zombie named '" + name + "'.");
            return;
        }
        view.info(spec.getName() + ":");
        view.info("    health: " + spec.getHp());
        view.info("    armor:");
        for (Map.Entry<String, Integer> piece : spec.getArmor().entrySet()) {
            view.info("        " + piece.getKey() + ": " + piece.getValue());
        }
        view.info("    speed: " + spec.getTilesPerSecond() + " tiles/s");
        view.info("    damage: " + spec.getDamagePerSecond() + "/s");
        view.info("    wave cost: " + spec.getWaveCost());
        view.info("    description: " + spec.getDescription());
    }

    private void upgradePlant(String name) {
        User user = context.getCurrentUser();
        if (GameCatalog.get().plant(name) == null) {
            view.error("There is no plant named '" + name + "'.");
            return;
        }
        if (!user.getUnlockedPlants().contains(name)) {
            view.error("Plant '" + name + "' is locked; purchase it first.");
            return;
        }
        int nextLevel = user.getPlantLevel(name) + 1;
        int coinsNeeded = UPGRADE_COINS_PER_LEVEL * nextLevel;
        int packetsNeeded = UPGRADE_PACKETS_PER_LEVEL * nextLevel;
        int packets = user.getSeedPackets().getOrDefault(name, 0);
        if (user.getCoins() < coinsNeeded) {
            view.error("Upgrading " + name + " to level " + nextLevel + " needs " + coinsNeeded + " coins.");
            return;
        }
        if (packets < packetsNeeded) {
            view.error("Upgrading " + name + " to level " + nextLevel + " needs "
                    + packetsNeeded + " seed packets of it; you have " + packets + ".");
            return;
        }
        user.spendCoins(coinsNeeded);
        user.getSeedPackets().put(name, packets - packetsNeeded);
        user.setPlantLevel(name, nextLevel);
        context.getUserRepository().save();
        view.info("Plant '" + name + "' upgraded to level " + nextLevel + ".");
    }

    private void purchasePlant(String name) {
        User user = context.getCurrentUser();
        if (GameCatalog.get().plant(name) == null) {
            view.error("There is no plant named '" + name + "'.");
            return;
        }
        if (user.getUnlockedPlants().contains(name)) {
            view.error("You already own '" + name + "'.");
            return;
        }
        if (user.getCoins() < PURCHASE_COIN_COST) {
            view.error("Buying a new plant costs " + PURCHASE_COIN_COST + " coins.");
            return;
        }
        user.spendCoins(PURCHASE_COIN_COST);
        user.getUnlockedPlants().add(name);
        user.addNews("New plant unlocked: " + name);
        context.getUserRepository().save();
        view.info("Plant '" + name + "' purchased and unlocked.");
    }
}

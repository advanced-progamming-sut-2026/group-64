package ir.sharif.pvz.view;

import ir.sharif.pvz.model.NewsItem;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.Plant;
import ir.sharif.pvz.model.game.Sun;
import ir.sharif.pvz.model.game.Zombie;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The single place that writes to the console; controllers never print directly.
 */
public class ConsoleView {

    private final PrintStream out;

    public ConsoleView() {
        this(System.out);
    }

    public ConsoleView(PrintStream out) {
        this.out = out;
    }

    public void info(String message) {
        out.println(message);
    }

    public void error(String message) {
        out.println("Error: " + message);
    }

    public void errors(List<String> messages) {
        for (String message : messages) {
            error(message);
        }
    }

    public void showCurrentMenu(String menuName) {
        out.println("You are in the " + menuName + " menu.");
    }

    public void showSecurityQuestions(List<String> questions) {
        out.println("Pick a security question with:");
        out.println("pick question -q <question_number> -a <answer> -c <answer_confirm>");
        for (int i = 0; i < questions.size(); i++) {
            out.println((i + 1) + ". " + questions.get(i));
        }
    }

    public void unknownCommand() {
        out.println("Error: invalid command.");
    }

    public void showUserInfo(User user) {
        out.println("Username: " + user.getUsername());
        out.println("Nickname: " + user.getNickname());
        out.println("Games played: " + user.getGamesPlayed());
        out.println("Coins: " + user.getCoins());
        out.println("Diamonds: " + user.getDiamonds());
        out.println("Levels passed: " + user.getLevelsPassed());
        out.println("Max mew points: " + user.getMaxMewPoints());
    }

    public void showNews(List<NewsItem> items, String emptyMessage) {
        if (items.isEmpty()) {
            out.println(emptyMessage);
            return;
        }
        for (NewsItem item : items) {
            out.println((item.isRead() ? "" : "[new] ") + item.getText());
        }
    }

    /**
     * Terminal rendering of the whole board: header stats then one line per row.
     */
    public void showMap(GameSession session) {
        out.println("Wave: " + session.getCurrentWave() + " | Sun: " + session.getSunAmount()
                + " | Plant food: " + session.getPlantFood());
        for (int y = 1; y <= GameSession.ROWS; y++) {
            StringBuilder line = new StringBuilder(session.isMowerAvailable(y - 1) ? "M |" : "  |");
            for (int x = 1; x <= GameSession.COLS; x++) {
                line.append(renderTile(session, x, y)).append('|');
            }
            out.println(line);
        }
    }

    private String renderTile(GameSession session, int x, int y) {
        Plant plant = session.plantAtTile(x, y);
        String name = plant == null ? terrainMark(session.terrainAt(x, y))
                : shortName(plant.getSpec().getName());
        long zombieCount = session.getZombies().stream()
                .filter(z -> z.getRow() == y - 1 && Math.round(z.getX()) == x).count();
        boolean sun = session.groundSuns().stream().anyMatch(s -> s.getRow() == y - 1 && s.getCol() == x - 1);
        return String.format("%s %s%s", name, zombieCount == 0 ? " " : "Z" + zombieCount, sun ? "*" : " ");
    }

    private String terrainMark(ir.sharif.pvz.model.game.TileTerrain terrain) {
        return switch (terrain) {
            case GRAVE -> "GRAV";
            case WATER -> "~~~~";
            case LILY -> "lily";
            case SLIPPERY_UP -> "/ice";
            case SLIPPERY_DOWN -> "\\ice";
            case SPAWNER -> "^^^^";
            default -> "....";
        };
    }

    private String shortName(String name) {
        return name.length() <= 4 ? String.format("%-4s", name) : name.substring(0, 4);
    }

    public void showPlantsStatus(GameSession session) {
        for (String type : session.getSelectedPlants()) {
            double remaining = session.cooldownRemaining(type);
            int cost = ir.sharif.pvz.model.game.GameCatalog.get().plant(type).getSunCost();
            String readiness = remaining <= 0 ? "ready"
                    : "ready in " + String.format(Locale.ROOT, "%.1f", remaining) + "s";
            boolean affordable = session.getSunAmount() >= cost;
            out.println(type + ": needs " + cost + " sun (" + (affordable ? "affordable" : "not enough sun")
                    + "), " + readiness);
        }
    }

    public void showTileStatus(GameSession session, int x, int y) {
        if (x < 1 || x > GameSession.COLS || y < 1 || y > GameSession.ROWS) {
            error("(" + x + ", " + y + ") is not a valid tile.");
            return;
        }
        if (session.terrainAt(x, y) == ir.sharif.pvz.model.game.TileTerrain.GRAVE) {
            out.println("A grave with " + session.graveHpAt(x, y) + " hp blocks this tile.");
        } else if (session.terrainAt(x, y) != ir.sharif.pvz.model.game.TileTerrain.NORMAL) {
            out.println("Terrain: " + session.terrainAt(x, y).name().toLowerCase(Locale.ROOT));
        }
        Plant plant = session.plantAtTile(x, y);
        if (plant != null) {
            out.println("Plant " + plant.getSpec().getName() + ": hp " + plant.getHp()
                    + "/" + plant.getSpec().getHp()
                    + (session.isPlantDisabled(x, y) ? " (disabled)" : ""));
        }
        List<Zombie> here = session.getZombies().stream()
                .filter(z -> z.getRow() == y - 1 && Math.round(z.getX()) == x).toList();
        for (Zombie zombie : here) {
            out.println("Zombie " + zombie.getSpec().getName() + ": health " + zombie.getHp());
        }
        for (Sun sun : session.groundSuns()) {
            if (sun.getRow() == y - 1 && sun.getCol() == x - 1) {
                out.println("A sun worth " + sun.value() + " is on this tile.");
            }
        }
        if (plant == null && here.isEmpty()) {
            out.println("Tile (" + x + ", " + y + ") is empty.");
        }
    }

    /**
     * The YAML-like per-zombie report shown by "zombies info".
     */
    public void showZombiesInfo(List<Zombie> zombies) {
        if (zombies.isEmpty()) {
            out.println("There is no zombie on the map.");
            return;
        }
        for (Zombie zombie : zombies) {
            out.println(capitalize(zombie.getSpec().getName()) + ":");
            out.println("    position: " + positionOf(zombie) + ", " + (zombie.getRow() + 1));
            out.println("    health: " + zombie.getHp());
            out.println("    armor:");
            for (Map.Entry<String, Integer> piece : zombie.getArmor().entrySet()) {
                out.println("        " + piece.getKey() + ": " + piece.getValue());
            }
            out.println("    effects:");
            for (Map.Entry<String, Double> effect : zombie.activeEffects().entrySet()) {
                out.println("        " + effect.getKey() + ": "
                        + String.format(Locale.ROOT, "%.1f", effect.getValue()) + "s");
            }
        }
    }

    private String positionOf(Zombie zombie) {
        double x = zombie.getX();
        if (x == Math.floor(x)) {
            return String.valueOf((int) x);
        }
        return String.format(Locale.ROOT, "%.1f", x);
    }

    private String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}

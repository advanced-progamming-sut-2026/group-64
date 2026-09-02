package ir.sharif.pvz.model.game;

import java.util.ArrayList;

/**
 * The debug commands from phase 1, lifted out of {@link GameSession} so the
 * engine class stays about actually playing a level. They work on the session's
 * package-private state directly, the same way the board and wave systems do.
 */
public final class Cheats {

    private final GameSession session;

    Cheats(GameSession session) {
        this.session = session;
    }

    public String addSuns(int count) {
        session.sunAmount += count;
        return "Added " + count + " suns; you now have " + session.sunAmount + " sun.";
    }

    public String removeCooldown() {
        session.cooldownsDisabled = true;
        session.plantCooldowns.clear();
        return "All cooldowns removed.";
    }

    public String addPlantFood() {
        session.grantPlantFood(1);
        return "You have " + session.getPlantFood() + " plant foods now.";
    }

    public String spawnZombie(String type, int x, int y) {
        ZombieSpec spec = GameCatalog.get().zombie(type);
        if (spec == null) {
            return "Error: there is no zombie type named '" + type + "'.";
        }
        if (!session.validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        session.spawnZombie(spec, y - 1, x);
        return "Zombie " + type + " spawned at (" + x + ", " + y + ").";
    }

    public String releaseTheNuke() {
        for (Zombie zombie : new ArrayList<>(session.zombies)) {
            session.killZombie(zombie);
        }
        return "The nuke wiped the whole map.";
    }
}

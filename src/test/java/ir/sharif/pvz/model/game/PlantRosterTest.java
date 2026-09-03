package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Covers the plants the project sheet asks for beyond the original handful:
 * the shooters that cover more than their own lane, the walls that do
 * something when bitten, the shrooms that charm or disarm, and the one-shot
 * explosives.
 */
class PlantRosterTest {

    private static GameSession quietSession(List<String> plants) {
        GameSession session = new GameSession(3, plants, new HashSet<>(), new Random(42));
        session.setWavesEnabled(false);
        session.cheats().addSuns(5000);
        session.cheats().removeCooldown();
        return session;
    }

    private static Zombie spawn(GameSession session, String type, int row, double x) {
        return session.spawnZombie(GameCatalog.get().zombie(type), row, x);
    }

    private static int healthOf(GameSession session, int row) {
        return session.getZombies().stream()
                .filter(zombie -> zombie.getRow() == row)
                .mapToInt(Zombie::totalRemainingHealth)
                .sum();
    }

    // ===== the roster itself =====

    @Test
    void everyPlantTheSheetListsIsInTheCatalog() {
        List<String> required = List.of("sunflower", "twin-sunflower", "sun-shroom",
                "primal-sunflower", "gold-bloom", "peashooter", "repeater", "threepeater",
                "snow-pea", "rotobaga", "pea-pod", "split-pea", "citron", "caulipower",
                "electric-blueberry", "bowling-bulb", "cactus", "fire-peashooter", "starfruit",
                "goo-peashooter", "mega-gatling-pea", "sea-shroom", "puff-shroom", "fume-shroom",
                "cabbage-pult", "kernel-pult", "melon-pult", "winter-melon", "pepper-pult",
                "potato-mine", "primal-potato-mine", "cherry-bomb", "squash", "grapeshot",
                "jalapeno", "doom-shroom", "tangle-kelp", "iceberg-lettuce", "bonk-choy",
                "phat-beet", "chomper", "wasabi-whip", "kiwibeast", "wall-nut", "tall-nut",
                "endurian", "garlic", "sweet-potato", "explode-o-nut", "pumpkin", "sun-bean",
                "torchwood", "magnet-shroom", "hypno-shroom", "cattail", "imitater",
                "ice-shroom", "lily-pad", "hot-potato", "grave-buster", "enlighten-mint",
                "appease-mint", "arma-mint", "bombard-mint", "enforce-mint", "reinforce-mint",
                "enchant-mint", "pierce-mint", "cattail-mint");
        assertEquals(69, required.size());
        for (String name : required) {
            assertNotNull(GameCatalog.get().plant(name), name + " is missing from the catalog");
        }
    }

    @Test
    void everyPlantCarriesAtLeastOneTag() {
        for (PlantSpec spec : GameCatalog.get().allPlants()) {
            assertFalse(spec.getTags().isEmpty(), spec.getName() + " has no tags");
        }
    }

    @Test
    void eachMintNamesAFamilyThatExists() {
        for (PlantSpec spec : GameCatalog.get().allPlants()) {
            if (spec.getCategory() != PlantCategory.MINT || spec.getName().equals("peppermint")) {
                continue;
            }
            String family = spec.getTags().stream()
                    .filter(tag -> tag.startsWith("family:"))
                    .findFirst().orElse(null);
            assertNotNull(family, spec.getName() + " names no family");
            PlantCategory.valueOf(family.substring(7).toUpperCase(java.util.Locale.ROOT)
                    .replace('-', '_'));
        }
    }

    @Test
    void theStarterLoadoutAndEveryQuestRewardNameARealPlant() {
        ir.sharif.pvz.model.User fresh = new ir.sharif.pvz.model.User(
                "grader", "hash", "Grader", "g@example.com", ir.sharif.pvz.model.Gender.FEMALE);
        assertFalse(fresh.getUnlockedPlants().isEmpty());
        for (String name : fresh.getUnlockedPlants()) {
            assertNotNull(GameCatalog.get().plant(name), name + " is not a plant");
        }
        for (ir.sharif.pvz.model.Quest quest : ir.sharif.pvz.model.QuestCatalog.all()) {
            quest.grant(fresh);
        }
        for (String name : fresh.getUnlockedPlants()) {
            assertNotNull(GameCatalog.get().plant(name),
                    name + " is handed out by a quest but is not a plant");
        }
    }

    // ===== sun producers =====

    @Test
    void eachSunProducerYieldsItsOwnAmount() {
        GameSession session = quietSession(List.of("twin-sunflower"));
        session.plant("twin-sunflower", 1, 1);
        session.advance(24 * GameSession.TICKS_PER_SECOND + 5);
        assertEquals("Collected 100 sun; you now have "
                + (session.getSunAmount() + 100) + " sun.", session.collectSun(1, 1));
    }

    @Test
    void goldBloomPaysOutAtOnceAndDisappears() {
        GameSession session = quietSession(List.of("gold-bloom"));
        int before = session.getSunAmount();
        session.plant("gold-bloom", 3, 3);
        session.advance(2);
        assertEquals(before + 375, session.getSunAmount());
        assertNull(session.plantAtTile(3, 3));
    }

    @Test
    void theSunShroomGrowsIntoABiggerYield() {
        GameSession session = quietSession(List.of("sun-shroom"));
        session.plant("sun-shroom", 1, 1);
        session.advance(2);
        assertEquals("Collected 25 sun; you now have "
                + (session.getSunAmount() + 25) + " sun.", session.collectSun(1, 1),
                "a fresh sun-shroom is still on its first stage");
        session.advance(73 * GameSession.TICKS_PER_SECOND);
        session.collectSun(1, 1);
        session.advance(25 * GameSession.TICKS_PER_SECOND);
        assertEquals("Collected 75 sun; you now have "
                + (session.getSunAmount() + 75) + " sun.", session.collectSun(1, 1),
                "a grown one yields three times as much");
    }

    // ===== shooters that cover more than their own lane =====

    @Test
    void threepeaterHitsTheLaneAboveAndBelow() {
        GameSession session = quietSession(List.of("threepeater"));
        session.plant("threepeater", 1, 3);
        for (int row = 0; row < 5; row++) {
            spawn(session, "normal", row, 6);
        }
        int full = GameCatalog.get().zombie("normal").getHp();
        session.advance(2 * GameSession.TICKS_PER_SECOND);
        assertTrue(healthOf(session, 1) < full, "the lane above is taking fire");
        assertTrue(healthOf(session, 2) < full, "so is the threepeater's own lane");
        assertTrue(healthOf(session, 3) < full, "and the lane below");
        assertEquals(full, healthOf(session, 0), "the far lanes are untouched");
        assertEquals(full, healthOf(session, 4), "the far lanes are untouched");
    }

    @Test
    void splitPeaShootsBackwardsAsWellAsForwards() {
        GameSession session = quietSession(List.of("split-pea"));
        session.plant("split-pea", 5, 3);
        Zombie behind = spawn(session, "normal", 2, 2);
        Zombie ahead = spawn(session, "normal", 2, 8);
        int startBehind = behind.totalRemainingHealth();
        int startAhead = ahead.totalRemainingHealth();
        session.advance(2 * GameSession.TICKS_PER_SECOND);
        assertTrue(behind.totalRemainingHealth() < startBehind, "the zombie behind was hit");
        assertTrue(ahead.totalRemainingHealth() < startAhead, "the zombie ahead was hit");
    }

    @Test
    void rotobagaShootsTheDiagonalsAndNotItsOwnLane() {
        GameSession session = quietSession(List.of("rotobaga"));
        session.plant("rotobaga", 3, 3);
        Zombie straightAhead = spawn(session, "normal", 2, 7);
        Zombie diagonal = spawn(session, "normal", 1, 7);
        int start = straightAhead.totalRemainingHealth();
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        assertEquals(start, straightAhead.totalRemainingHealth(),
                "rotobaga never fires straight ahead");
        assertTrue(diagonal.totalRemainingHealth() < start, "the diagonal lane is hit");
    }

    @Test
    void aPeaPodStacksItsHeadsAndHitsHarderForEachOne() {
        GameSession session = quietSession(List.of("pea-pod"));
        assertEquals("Planted pea-pod at (2, 3).", session.plant("pea-pod", 2, 3));
        assertTrue(session.plant("pea-pod", 2, 3).contains("grew a head; it now has 2"));
        assertEquals(2, session.plantAtTile(2, 3).getStack());
        Zombie target = spawn(session, "normal", 2, 6);
        int start = target.totalRemainingHealth();
        session.advance(2 * GameSession.TICKS_PER_SECOND);
        assertTrue(start - target.totalRemainingHealth() >= 40,
                "two heads shoot twice as hard as one");
    }

    @Test
    void gooPeashooterPoisonsThroughArmour() {
        GameSession session = quietSession(List.of("goo-peashooter"));
        session.plant("goo-peashooter", 1, 3);
        Zombie coneHead = spawn(session, "conehead", 2, 6);
        int startHp = coneHead.getHp();
        session.advance(4 * GameSession.TICKS_PER_SECOND);
        assertTrue(coneHead.isPoisoned(), "the goo pea leaves poison behind");
        assertTrue(coneHead.getHp() < startHp,
                "poison eats the zombie itself, not only its cone");
    }

    // ===== melee =====

    @Test
    void bonkChoyHitsTheTileBehindItToo() {
        GameSession session = quietSession(List.of("bonk-choy"));
        session.plant("bonk-choy", 4, 3);
        Zombie behind = spawn(session, "normal", 2, 3.5);
        int start = behind.totalRemainingHealth();
        session.advance(GameSession.TICKS_PER_SECOND);
        assertTrue(behind.totalRemainingHealth() < start);
    }

    @Test
    void phatBeetThumpsEverythingAroundIt() {
        GameSession session = quietSession(List.of("phat-beet"));
        session.plant("phat-beet", 4, 3);
        Zombie above = spawn(session, "normal", 1, 4.5);
        Zombie below = spawn(session, "normal", 3, 4.5);
        int start = above.totalRemainingHealth();
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        assertTrue(above.totalRemainingHealth() < start, "the lane above is in range");
        assertTrue(below.totalRemainingHealth() < start, "so is the lane below");
    }

    @Test
    void chomperSwallowsItsTargetWhole() {
        GameSession session = quietSession(List.of("chomper"));
        session.plant("chomper", 4, 3);
        spawn(session, "buckethead", 2, 4.5);
        session.advance(GameSession.TICKS_PER_SECOND);
        assertTrue(session.getZombies().isEmpty(), "a bucket head goes down in one bite");
    }

    // ===== the walls that do more than block =====

    @Test
    void endurianSpikesBackAtWhoeverBitesIt() {
        GameSession session = quietSession(List.of("endurian"));
        session.plant("endurian", 3, 3);
        Zombie biter = spawn(session, "normal", 2, 3.2);
        int start = biter.totalRemainingHealth();
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        assertTrue(biter.totalRemainingHealth() < start, "the biter takes the spikes back");
    }

    @Test
    void sunBeanPaysOutWhileItIsEaten() {
        GameSession session = quietSession(List.of("sun-bean"));
        session.plant("sun-bean", 3, 3);
        spawn(session, "normal", 2, 3.2);
        int before = session.getSunAmount();
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.getSunAmount() > before, "every bite drops sun");
    }

    @Test
    void garlicPushesItsBiterIntoAnotherLane() {
        GameSession session = quietSession(List.of("garlic"));
        session.plant("garlic", 3, 3);
        Zombie biter = spawn(session, "normal", 2, 3.2);
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        assertFalse(biter.getRow() == 2 && session.plantAtTile(3, 3) != null,
                "the garlic is spent and the zombie moved lane");
    }

    @Test
    void sweetPotatoDrawsTheNeighbouringLanesIn() {
        GameSession session = quietSession(List.of("sweet-potato"));
        Zombie above = spawn(session, "normal", 1, 6);
        session.plant("sweet-potato", 3, 3);
        assertEquals(2, above.getRow(), "the neighbour was pulled into the potato's lane");
    }

    @Test
    void aPumpkinTakesTheBitesForThePlantInside() {
        GameSession session = quietSession(List.of("sunflower", "pumpkin"));
        session.plant("sunflower", 3, 3);
        assertTrue(session.plant("pumpkin", 3, 3).contains("around sunflower"));
        Plant sunflower = session.plantAtTile(3, 3);
        assertEquals("sunflower", sunflower.getSpec().getName());
        int start = sunflower.getHp();
        spawn(session, "normal", 2, 3.2);
        session.advance(5 * GameSession.TICKS_PER_SECOND);
        assertEquals(start, session.plantAtTile(3, 3).getHp(),
                "the shell soaks the damage while it lasts");
    }

    // ===== the shrooms that charm and disarm =====

    @Test
    void magnetShroomRipsTheMetalOffANearbyZombie() {
        GameSession session = quietSession(List.of("magnet-shroom"));
        session.plant("magnet-shroom", 3, 3);
        Zombie bucket = spawn(session, "buckethead", 2, 5);
        assertFalse(bucket.getArmor().isEmpty());
        session.advance(11 * GameSession.TICKS_PER_SECOND);
        assertTrue(bucket.getArmor().isEmpty(), "the bucket was pulled off");
    }

    @Test
    void hypnoShroomTurnsItsEaterAround() {
        GameSession session = quietSession(List.of("hypno-shroom"));
        session.plant("hypno-shroom", 3, 3);
        Zombie biter = spawn(session, "normal", 2, 3.2);
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        assertTrue(biter.isHypnotized(), "the biter now fights for the player");
    }

    @Test
    void aCharmedZombieMaulsTheOnesBehindIt() {
        GameSession session = quietSession(List.of("hypno-shroom"));
        session.plant("hypno-shroom", 3, 3);
        spawn(session, "normal", 2, 3.2);
        Zombie victim = spawn(session, "normal", 2, 4.0);
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        int start = victim.totalRemainingHealth();
        session.advance(10 * GameSession.TICKS_PER_SECOND);
        assertTrue(victim.totalRemainingHealth() < start, "the charmed zombie bites its own kind");
    }

    // ===== one-shot plants =====

    @Test
    void jalapenoBurnsOutTheWholeLaneAndIsSpent() {
        GameSession session = quietSession(List.of("jalapeno"));
        for (double x = 3; x <= 8; x++) {
            spawn(session, "normal", 2, x);
        }
        spawn(session, "normal", 0, 5);
        session.plant("jalapeno", 1, 3);
        session.advance(2);
        assertEquals(1, session.getZombies().size(), "only the other lane survives");
        assertNull(session.plantAtTile(1, 3));
    }

    @Test
    void iceShroomFreezesEveryZombieOnTheBoard() {
        GameSession session = quietSession(List.of("ice-shroom"));
        Zombie far = spawn(session, "normal", 0, 8);
        Zombie near = spawn(session, "normal", 4, 4);
        session.plant("ice-shroom", 3, 3);
        session.advance(2);
        assertTrue(far.isFrozen());
        assertTrue(near.isFrozen());
    }

    @Test
    void doomShroomLeavesACraterNothingCanBePlantedIn() {
        GameSession session = quietSession(List.of("doom-shroom", "sunflower"));
        spawn(session, "normal", 0, 8);
        session.plant("doom-shroom", 3, 3);
        session.advance(2);
        assertTrue(session.getZombies().isEmpty(), "the blast covers the whole map");
        assertTrue(session.plant("sunflower", 3, 3).contains("crater"));
    }

    @Test
    void graveBusterClearsTheGraveItIsPlantedOn() {
        GameSession session = quietSession(List.of("grave-buster"));
        session.raiseGrave(2, 3, null);
        assertTrue(session.plant("grave-buster", 5, 5).contains("no grave to chew on"));
        assertTrue(session.plant("grave-buster", 4, 3).startsWith("Planted"));
        session.advance(2);
        assertTrue(session.drainEvents().stream().anyMatch(e -> e.contains("was busted")));
    }

    @Test
    void tangleKelpDragsTheZombieThatStepsOnItUnder() {
        GameSession session = quietSession(List.of("tangle-kelp"));
        session.plant("tangle-kelp", 3, 3);
        spawn(session, "gargantuar", 2, 3.0);
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.getZombies().isEmpty(), "even a gargantuar goes under");
    }

    @Test
    void theShortLivedShroomsWiltAfterTheirMinute() {
        GameSession session = quietSession(List.of("puff-shroom"));
        session.plant("puff-shroom", 3, 3);
        assertNotNull(session.plantAtTile(3, 3));
        session.advance(61 * GameSession.TICKS_PER_SECOND);
        assertNull(session.plantAtTile(3, 3), "a puff-shroom lasts a minute");
    }

    @Test
    void anImitaterCopiesTheLastPlantTheGardenerPutDown() {
        GameSession session = quietSession(List.of("peashooter", "imitater"));
        session.plant("peashooter", 1, 3);
        session.plant("imitater", 2, 3);
        assertEquals("peashooter", session.plantAtTile(2, 3).getSpec().getName());
    }

    // ===== the mints =====

    @Test
    void aMintFeedsItsWholeFamilyAndIsUsedUp() {
        GameSession session = quietSession(List.of("sunflower", "enlighten-mint"));
        session.plant("sunflower", 1, 1);
        session.plant("sunflower", 1, 2);
        int before = session.getSunAmount();
        session.plant("enlighten-mint", 5, 5);
        session.advance(2);
        assertNull(session.plantAtTile(5, 5), "the mint is spent");
        assertEquals(before + 300, session.getSunAmount(),
                "both sunflowers burst into a plant food's worth of sun");
    }

    @Test
    void aMintOnlyFeedsItsOwnFamily() {
        GameSession session = quietSession(List.of("sunflower", "arma-mint"));
        session.plant("sunflower", 1, 1);
        int before = session.getSunAmount();
        session.plant("arma-mint", 5, 5);
        session.advance(2);
        assertEquals(before, session.getSunAmount(), "the lobber mint leaves sunflowers alone");
    }
}

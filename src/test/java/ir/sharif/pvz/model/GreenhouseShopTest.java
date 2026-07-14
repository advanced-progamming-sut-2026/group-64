package ir.sharif.pvz.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GreenhouseShopTest {

    private static final long HOUR = 3_600_000L;

    private final AtomicLong now = new AtomicLong(1_000_000_000_000L);
    private UserRepository repository;
    private GreenhouseService greenhouse;
    private ShopService shop;
    private User user;

    @BeforeEach
    void setUp() {
        repository = new UserRepository() {
            @Override
            public void save() {
                // keep unit tests off the disk
            }
        };
        greenhouse = new GreenhouseService(repository, now::get, new Random(1));
        shop = new ShopService(repository, now::get, new Random(1));
        user = new User("tester", "hash", "Tess", "t@mail.com", Gender.FEMALE);
    }

    @Test
    void onlyFirstRowOfPotsStartsUnlocked() {
        assertTrue(greenhouse.plantPot(user, 1, 1).startsWith("A "));
        assertTrue(greenhouse.plantPot(user, 1, 2).contains("locked"));
        assertTrue(greenhouse.plantPot(user, 6, 1).contains("not a valid pot"));
    }

    @Test
    void marigoldGrowsInTwoHoursAndPaysCoins() {
        Random alwaysMarigold = new Random() {
            @Override
            public boolean nextBoolean() {
                return false;
            }
        };
        greenhouse = new GreenhouseService(repository, now::get, alwaysMarigold);
        assertTrue(greenhouse.plantPot(user, 1, 1).contains("marigold"));
        assertTrue(greenhouse.collect(user, 1, 1).startsWith("Error"));
        now.addAndGet(2 * HOUR);
        assertTrue(greenhouse.collect(user, 1, 1).contains("+500 coins"));
        assertEquals(500, user.getCoins());
        assertTrue(greenhouse.plantPot(user, 1, 1).startsWith("A "));
    }

    @Test
    void collectingAPlantStoresASingleBoost() {
        Random alwaysSamePlant = new Random() {
            @Override
            public boolean nextBoolean() {
                return true;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        greenhouse = new GreenhouseService(repository, now::get, alwaysSamePlant);
        greenhouse.plantPot(user, 1, 1);
        greenhouse.plantPot(user, 2, 1);
        now.addAndGet(8 * HOUR);
        assertTrue(greenhouse.collect(user, 1, 1).contains("boosted"));
        assertEquals(1, user.getStoredBoosts().size());
        assertTrue(greenhouse.collect(user, 2, 1).contains("already stored"));
        assertEquals(1, user.getStoredBoosts().size());
    }

    @Test
    void growCostsOneDiamondPerRemainingHourRoundedUp() {
        Random alwaysPlant = new Random() {
            @Override
            public boolean nextBoolean() {
                return true;
            }
        };
        greenhouse = new GreenhouseService(repository, now::get, alwaysPlant);
        greenhouse.plantPot(user, 1, 1);
        now.addAndGet(5 * HOUR + HOUR / 2);
        assertTrue(greenhouse.grow(user, 1, 1).startsWith("Error"));
        user.addDiamonds(3);
        assertTrue(greenhouse.grow(user, 1, 1).contains("Spent 3 diamonds"));
        assertEquals(0, user.getDiamonds());
        assertTrue(greenhouse.collect(user, 1, 1).startsWith("Collected"));
    }

    @Test
    void buyingPotsUnlocksSlotsUntilAllTwenty() {
        user.addCoins(2000 * 16);
        assertTrue(shop.buy(user, "pot", 15, null).contains("15"));
        assertTrue(shop.buy(user, "pot", 1, null).contains("already unlocked"));
        assertEquals(2000, user.getCoins());
    }

    @Test
    void plantFoodPurchaseIsCappedAtThree() {
        user.addDiamonds(20);
        assertTrue(shop.buy(user, "plant-food", 3, null).contains("3 plant food"));
        assertTrue(shop.buy(user, "plant-food", 1, null).startsWith("Error"));
        assertEquals(3, user.getPendingPlantFood());
    }

    @Test
    void choicePacketsNeedAnUnlockedPlant() {
        user.addDiamonds(10);
        assertTrue(shop.buy(user, "choice-packets", 1, null).startsWith("Error"));
        assertTrue(shop.buy(user, "choice-packets", 1, "laser-bean").startsWith("Error"));
        assertTrue(shop.buy(user, "choice-packets", 1, "peashooter").contains("10 seed packets"));
        assertEquals(10, user.getSeedPackets().get("peashooter"));
    }

    @Test
    void exchangeTurnsDiamondsIntoCoins() {
        user.addDiamonds(5);
        assertTrue(shop.buy(user, "exchange", 1, null).contains("500 coins"));
        assertEquals(0, user.getDiamonds());
        assertEquals(500, user.getCoins());
    }

    @Test
    void dailyOfferIsOncePerDayAndResetsAtMidnight() {
        user.addCoins(5000);
        assertTrue(shop.buy(user, "daily", 1, null).contains("10 seed packets"));
        assertTrue(shop.buy(user, "daily", 1, null).contains("already bought"));
        now.addAndGet(24 * HOUR);
        assertTrue(shop.buy(user, "daily", 1, null).contains("10 seed packets"));
        assertEquals(5000 - 2 * 1600, user.getCoins());
    }
}

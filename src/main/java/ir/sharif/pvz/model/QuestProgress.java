package ir.sharif.pvz.model;

import ir.sharif.pvz.model.game.LevelReport;
import ir.sharif.pvz.model.game.PlantCategory;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a player has done, in the terms the sheet's quests ask about.
 *
 * <p>It is two tallies rather than a field per quest: one that keeps counting
 * for good, and one that starts again each calendar day for the daily quests.
 * A quest asks for a key and a number, so adding one to the catalogue needs no
 * change here.
 */
public class QuestProgress {

    /** Keys the daily tally uses; the lifetime one uses the same shapes. */
    public static final String SUN = "sun";
    public static final String MOWER_KILLS = "mower.kills";
    public static final String FAST_TEN = "fast.kills";
    public static final String AT_THE_DOOR = "door.kills";
    public static final String ZERO_SUN_WIN = "win.zerosun";
    public static final String SYMMETRIC_WIN = "win.symmetric";
    public static final String ASYMMETRIC_WIN = "win.asymmetric";
    public static final String NIGHT_ON_A_DAY_WIN = "win.nightplants";
    public static final String SUN_PRODUCERS_WIN = "win.sunproducers";
    public static final String EXPLOSIVES_USED = "used.explosives";
    public static final String HARDEST_WIN_STREAK = "streak.hardest";
    public static final String FEWEST_PLANTS_LOST = "win.fewestlost";

    private Map<String, Integer> lifetime = new HashMap<>();
    private Map<String, Integer> today = new HashMap<>();
    private String todayDate = "";

    /** Kills per chapter, e.g. "kills.chapter.ANCIENT_EGYPT". */
    public static String chapterKills(String chapter) {
        return "kills.chapter." + chapter;
    }

    /** A level won killing with one plant only, e.g. "soleplant.cactus". */
    public static String soleKiller(String plant) {
        return "soleplant." + plant;
    }

    /** A level won killing only with one family, e.g. "onlyfamily.SHOOTER". */
    public static String onlyFamily(PlantCategory family) {
        return "onlyfamily." + family.name();
    }

    /** A level won without a family on the lawn, e.g. "nofamily.SHOOTER". */
    public static String withoutFamily(PlantCategory family) {
        return "nofamily." + family.name();
    }

    /** A level won with a column left bare, e.g. "emptycol.4". */
    public static String emptyColumn(int column) {
        return "emptycol." + column;
    }

    /** A level won with a row left bare, e.g. "emptyrow.3". */
    public static String emptyRow(int row) {
        return "emptyrow." + row;
    }

    /** A level won with a row and a column both bare, e.g. "cross.3". */
    public static String cross(int index) {
        return "cross." + index;
    }

    /**
     * A lifetime tally, which never resets.
     */
    public int total(String key) {
        return lifetime().getOrDefault(key, 0);
    }

    /**
     * Today's tally, which is zero again once the date rolls over.
     */
    public int todays(String key, String date) {
        return date.equals(todayDate) ? today().getOrDefault(key, 0) : 0;
    }

    private void add(String key, int amount, String date) {
        lifetime().merge(key, amount, Integer::sum);
        rollOver(date);
        today().merge(key, amount, Integer::sum);
    }

    /**
     * Records a tally's best rather than its sum, for the quests that ask for
     * the most or the fewest of something in a single level.
     */
    private void best(String key, int value, String date, boolean higherIsBetter) {
        rollOver(date);
        // a list, not a set: the two tallies can hold equal contents
        for (Map<String, Integer> tally : List.of(lifetime(), today())) {
            Integer had = tally.get(key);
            boolean better = had == null
                    || (higherIsBetter ? value > had : value < had);
            if (better) {
                tally.put(key, value);
            }
        }
    }

    private void rollOver(String date) {
        if (!date.equals(todayDate)) {
            todayDate = date;
            today = new HashMap<>();
        }
    }

    /**
     * Folds one finished level into the tallies.
     */
    public void record(LevelReport report, String date) {
        add(SUN, report.sunCollected(), date);
        add(chapterKills(report.chapter().name()), report.kills(), date);
        add(MOWER_KILLS, report.killsBy().getOrDefault(
                ir.sharif.pvz.model.game.LevelLog.MOWER, 0), date);
        best(FAST_TEN, report.killsInFastWindow(), date, true);
        best(AT_THE_DOOR, report.killsAtTheDoor(), date, true);
        recordSoleKiller(report, date);
        recordExplosives(report, date);
        if (report.won()) {
            recordWin(report, date);
        } else {
            lifetime().put(HARDEST_WIN_STREAK, 0);
        }
    }

    private void recordSoleKiller(LevelReport report, String date) {
        String only = report.soleKiller();
        if (only != null && !only.equals(ir.sharif.pvz.model.game.LevelLog.MOWER)) {
            best(soleKiller(only), report.kills(), date, true);
        }
    }

    private void recordExplosives(LevelReport report, String date) {
        long explosives = report.plantedOfFamily(PlantCategory.EXPLOSIVE)
                + report.plantedOfFamily(PlantCategory.TRAP);
        best(EXPLOSIVES_USED, (int) explosives, date, true);
    }

    private void recordWin(LevelReport report, String date) {
        best(FEWEST_PLANTS_LOST, report.plantsLost(), date, false);
        if (report.sunLeft() == 0) {
            add(ZERO_SUN_WIN, 1, date);
        }
        if (report.symmetric()) {
            add(SYMMETRIC_WIN, 1, date);
        }
        if (report.asymmetric()) {
            add(ASYMMETRIC_WIN, 1, date);
        }
        if (!report.nightLevel() && nightPlantsOnly(report)) {
            add(NIGHT_ON_A_DAY_WIN, 1, date);
        }
        best(SUN_PRODUCERS_WIN, (int) report.plantedOfFamily(PlantCategory.SUN_PRODUCER),
                date, false);
        recordFamilies(report, date);
        recordBareLines(report, date);
        if (report.difficulty() >= 5) {
            add(HARDEST_WIN_STREAK, 1, date);
        } else {
            lifetime().put(HARDEST_WIN_STREAK, 0);
        }
    }

    /**
     * True when everything the player planted was a mushroom, which the sheet
     * calls playing a day level with night plants.
     */
    private static boolean nightPlantsOnly(LevelReport report) {
        return !report.planted().isEmpty() && report.planted().stream()
                .map(name -> ir.sharif.pvz.model.game.GameCatalog.get().plant(name))
                .allMatch(spec -> spec != null && spec.hasTag("shroom"));
    }

    private void recordFamilies(LevelReport report, String date) {
        Set<PlantCategory> killers = report.familiesThatKilled();
        if (killers.size() == 1) {
            add(onlyFamily(killers.iterator().next()), 1, date);
        }
        Set<PlantCategory> used = report.familiesPlanted();
        for (PlantCategory family : PlantCategory.values()) {
            if (!used.contains(family)) {
                add(withoutFamily(family), 1, date);
            }
        }
    }

    private void recordBareLines(LevelReport report, String date) {
        report.emptyColumns().forEach(column -> add(emptyColumn(column), 1, date));
        report.emptyRows().forEach(row -> add(emptyRow(row), 1, date));
        report.emptyRows().stream()
                .filter(row -> report.emptyColumns().contains(row))
                .forEach(index -> add(cross(index), 1, date));
    }

    /**
     * Gson builds these without running the field initialisers, so an account
     * saved before this existed comes back with nulls.
     */
    private Map<String, Integer> lifetime() {
        if (lifetime == null) {
            lifetime = new HashMap<>();
        }
        return lifetime;
    }

    private Map<String, Integer> today() {
        if (today == null) {
            today = new HashMap<>();
        }
        return today;
    }

    /**
     * A family named the way the quest ids spell it, or null when there is no
     * such family.
     */
    public static PlantCategory familyNamed(String name) {
        try {
            return PlantCategory.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

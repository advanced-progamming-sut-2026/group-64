package ir.sharif.pvz.devtools;

import ir.sharif.pvz.net.Snapshot;
import java.util.List;

/**
 * A handmade versus board, so the two-player screen can be photographed
 * without standing up a server and a second player.
 */
public final class SampleBoard {

    private SampleBoard() {
    }

    /**
     * A round in progress: plants dug in, zombies walking, one brain gone.
     */
    public static Snapshot build() {
        return new Snapshot(
                42, 120, false, false, 275, 150,
                List.of(true, true, false, true, true),
                List.of(
                        new Snapshot.PlantView("sunflower", 2, 1, 300, 300, false),
                        new Snapshot.PlantView("peashooter", 3, 2, 220, 300, false),
                        new Snapshot.PlantView("wall-nut", 4, 3, 2600, 4000, false),
                        new Snapshot.PlantView("snow-pea", 3, 4, 300, 300, false),
                        new Snapshot.PlantView("repeater", 2, 5, 300, 300, false)),
                List.of(
                        new Snapshot.ZombieView("normal", 7.4, 0, 180, 0, false, false, false),
                        new Snapshot.ZombieView("conehead", 6.1, 1, 200, 240, false, false, true),
                        new Snapshot.ZombieView("buckethead", 8.2, 3, 200, 900, false, true, false),
                        new Snapshot.ZombieView("imp", 5.3, 4, 100, 0, true, false, false)),
                List.of(new Snapshot.SunView(4, 2, "NORMAL"),
                        new Snapshot.SunView(6, 5, "SPECIAL")),
                List.of(new Snapshot.ShotView("pea", 4.6, 1, 0.4, false),
                        new Snapshot.ShotView("ice", 4.2, 3, 0.3, false)));
    }
}

package ir.sharif.pvz.view;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ir.sharif.pvz.model.game.Chapter;
import ir.sharif.pvz.model.game.GameCatalog;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The sound and art layers look their files up by name at runtime, so a renamed
 * or missing file would only show up as silence or a blank tile. These checks
 * fail loudly instead.
 */
class AudioResourceTest {

    private static String slug(Chapter chapter) {
        return chapter.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private void assertPresent(String path) {
        assertNotNull(getClass().getResource(path), path + " should be packaged");
    }

    @Test
    void everyRecordedEffectIsPackaged() {
        for (String name : List.of("explosion", "zombie", "mower", "wave", "win", "lose")) {
            assertPresent("/audio/sfx/" + name + ".mp3");
        }
    }

    @Test
    void everyChapterHasItsOwnThemeAndBackground() {
        for (Chapter chapter : Chapter.values()) {
            assertPresent("/audio/music/" + slug(chapter) + ".mp3");
            assertPresent("/assets/backgrounds/" + slug(chapter) + ".png");
        }
    }

    @Test
    void theMenuThemeIsPackaged() {
        assertPresent("/audio/music/menu.mp3");
    }

    @Test
    void everyPlantAndZombieHasArtwork() {
        GameCatalog.get().allPlants()
                .forEach(plant -> assertPresent("/assets/plants/" + plant.getName() + ".png"));
        GameCatalog.get().allZombies()
                .forEach(zombie -> assertPresent("/assets/zombies/" + zombie.getName() + ".png"));
    }

    @Test
    void armouredZombiesHaveABareVariantToSwapTo() {
        for (var zombie : GameCatalog.get().allZombies()) {
            if (zombie.getArmor().isEmpty()) {
                continue;
            }
            assertPresent("/assets/zombies/" + zombie.getName() + "-bare.png");
        }
    }

    @Test
    void chapterIdsArePlainSlugs() {
        for (Chapter chapter : Chapter.values()) {
            assertTrue(slug(chapter).matches("[a-z-]+"), "bad chapter id: " + slug(chapter));
        }
    }
}

package ir.sharif.pvz.view.fx;

import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Loads and caches the sprites extracted from the Plants vs. Zombies 2 asset
 * dump (see tools/build_manifest.py). Every lookup is by the same id the model
 * already uses — "peashooter", "conehead" — so no caller needs to know that the
 * art originally lived inside a shared texture atlas.
 */
public final class Assets {

    private static final String ROOT = "/assets/";
    private static final Map<String, Image> CACHE = new HashMap<>();

    private Assets() {
    }

    /**
     * The sprite at the given path under resources/assets, or null when the
     * project has no art for it (a zombie we never extracted, say).
     */
    public static Image image(String path) {
        return CACHE.computeIfAbsent(path, key -> {
            var stream = Assets.class.getResourceAsStream(ROOT + key + ".png");
            return stream == null ? null : new Image(stream);
        });
    }

    /**
     * The external form of a sprite's resource URL, for the places where CSS
     * needs a path rather than a loaded image. Empty when the art is missing.
     */
    public static String url(String path) {
        var resource = Assets.class.getResource(ROOT + path + ".png");
        return resource == null ? "" : resource.toExternalForm();
    }

    public static Image plant(String type) {
        return image("plants/" + type);
    }

    public static Image zombie(String type) {
        return image("zombies/" + type);
    }

    public static Image background(String chapterId) {
        return image("backgrounds/" + chapterId);
    }

    public static Image ui(String name) {
        return image("ui/" + name);
    }

    /**
     * An ImageView scaled to fit a box of the given height, keeping the aspect
     * ratio. Returns an empty view when the sprite is missing so the layout
     * still holds its place.
     */
    public static ImageView view(Image image, double height) {
        ImageView node = new ImageView(image);
        node.setPreserveRatio(true);
        node.setSmooth(true);
        node.setFitHeight(height);
        return node;
    }

    public static ImageView plantView(String type, double height) {
        return view(plant(type), height);
    }

    public static ImageView zombieView(String type, double height) {
        return view(zombie(type), height);
    }
}

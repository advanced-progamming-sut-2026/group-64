import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Crops the sprites listed in a manifest out of the shared PvZ2 atlas PNGs.
 *
 * <p>Usage: java AtlasExtract.java &lt;manifest.tsv&gt; &lt;atlasDir&gt; &lt;outDir&gt;
 * Each manifest line is: name TAB atlas.png TAB x TAB y TAB w TAB h
 */
public final class AtlasExtract {

    private AtlasExtract() {
    }

    public static void main(String[] args) throws IOException {
        Path manifest = Path.of(args[0]);
        Path atlasDir = Path.of(args[1]);
        Path outDir = Path.of(args[2]);

        String lastAtlas = null;
        BufferedImage atlas = null;
        int written = 0;
        int skipped = 0;

        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t");
            String name = parts[0];
            String atlasName = parts[1];
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            int w = Integer.parseInt(parts[4]);
            int h = Integer.parseInt(parts[5]);

            if (!atlasName.equals(lastAtlas)) {
                Path atlasPath = atlasDir.resolve(atlasName);
                if (!Files.exists(atlasPath)) {
                    System.out.println("  missing atlas: " + atlasName);
                    skipped++;
                    continue;
                }
                atlas = ImageIO.read(atlasPath.toFile());
                lastAtlas = atlasName;
            }
            // clamp so a rectangle that runs past the atlas edge still yields art
            int cw = Math.min(w, atlas.getWidth() - x);
            int ch = Math.min(h, atlas.getHeight() - y);
            if (cw <= 0 || ch <= 0) {
                System.out.println("  out of bounds: " + name);
                skipped++;
                continue;
            }
            BufferedImage crop = atlas.getSubimage(x, y, cw, ch);
            Path out = outDir.resolve(name + ".png");
            Files.createDirectories(out.getParent());
            ImageIO.write(crop, "png", out.toFile());
            written++;
        }
        System.out.println("wrote " + written + " sprites, skipped " + skipped);
    }
}

package ir.sharif.pvz.view.fx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import javafx.scene.media.AudioClip;

/**
 * The game's sound effects.
 *
 * <p>Most effects are real recordings under resources/audio/sfx. Three of them
 * — planting, shooting and picking up sun — have no recording, so those are
 * synthesised instead: a short waveform is built sample by sample, wrapped in a
 * WAV header and handed to JavaFX once. Either way a caller just names a sound.
 */
public final class Sfx {

    /**
     * Every sound the game can make. A sound with a file name plays that
     * recording; the rest are synthesised.
     */
    public enum Sound {
        PLANT(null), SHOOT(null), SUN(null),
        EXPLODE("explosion"), ZOMBIE("zombie"), MOWER("mower"),
        WAVE("wave"), WIN("win"), LOSE("lose");

        private final String file;

        Sound(String file) {
            this.file = file;
        }
    }

    private static final int SAMPLE_RATE = 22050;
    private static final Map<Sound, AudioClip> CLIPS = new EnumMap<>(Sound.class);

    private static double volume = 0.6;
    private static boolean enabled = true;

    private Sfx() {
    }

    /**
     * Turns all sound on or off, for the pause menu's control.
     */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the master volume, 0 to 1.
     */
    public static void setVolume(double level) {
        volume = Math.max(0, Math.min(1, level));
    }

    public static double getVolume() {
        return volume;
    }

    /**
     * Plays a sound, building it the first time it is asked for. Any failure to
     * reach the audio device is ignored: sound is a nicety, never a blocker.
     */
    public static void play(Sound sound) {
        if (!enabled) {
            return;
        }
        try {
            CLIPS.computeIfAbsent(sound, Sfx::build).play(volume);
        } catch (RuntimeException e) {
            enabled = false;
        }
    }

    private static AudioClip build(Sound sound) {
        if (sound.file != null) {
            var url = Sfx.class.getResource("/audio/sfx/" + sound.file + ".mp3");
            if (url != null) {
                return new AudioClip(url.toExternalForm());
            }
        }
        byte[] wav = wav(render(sound));
        try {
            Path file = Files.createTempFile("pvz-" + sound.name().toLowerCase(java.util.Locale.ROOT), ".wav");
            file.toFile().deleteOnExit();
            Files.write(file, wav);
            return new AudioClip(file.toUri().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The waveform for one effect, as samples between -1 and 1.
     */
    private static double[] render(Sound sound) {
        return switch (sound) {
            case PLANT -> tone(0.16, 420, 260, 0.5);
            case SHOOT -> tone(0.09, 900, 620, 0.35);
            case SUN -> chime(0.34, 880, 1320);
            // reached only if a recording is ever missing from the jar
            case EXPLODE -> noise(0.45, 0.9, 6);
            case ZOMBIE -> growl(0.35);
            case MOWER -> noise(0.6, 0.5, 2);
            case WAVE -> growl(0.5);
            case WIN -> chime(0.7, 660, 990);
            case LOSE -> tone(0.7, 300, 90, 0.55);
        };
    }

    /**
     * A sine that slides from one pitch to another and fades out.
     */
    private static double[] tone(double seconds, double fromHz, double toHz, double gain) {
        double[] out = new double[(int) (SAMPLE_RATE * seconds)];
        double phase = 0;
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) out.length;
            phase += 2 * Math.PI * (fromHz + (toHz - fromHz) * t) / SAMPLE_RATE;
            out[i] = Math.sin(phase) * gain * (1 - t) * (1 - t);
        }
        return out;
    }

    /**
     * Two pitches together, for the bright pickup and victory sounds.
     */
    private static double[] chime(double seconds, double lowHz, double highHz) {
        double[] out = new double[(int) (SAMPLE_RATE * seconds)];
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) out.length;
            double envelope = Math.exp(-3.5 * t) * 0.42;
            out[i] = envelope * (Math.sin(2 * Math.PI * lowHz * i / SAMPLE_RATE)
                    + 0.6 * Math.sin(2 * Math.PI * highHz * i / SAMPLE_RATE));
        }
        return out;
    }

    /**
     * Filtered noise with a long tail, for blasts and the mower.
     */
    private static double[] noise(double seconds, double gain, double decay) {
        Random random = new Random(7);
        double[] out = new double[(int) (SAMPLE_RATE * seconds)];
        double smoothed = 0;
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) out.length;
            smoothed = smoothed * 0.72 + (random.nextDouble() * 2 - 1) * 0.28;
            out[i] = smoothed * gain * Math.exp(-decay * t);
        }
        return out;
    }

    /**
     * A wobbling low tone, which is as close to a groan as a sine gets.
     */
    private static double[] growl(double seconds) {
        double[] out = new double[(int) (SAMPLE_RATE * seconds)];
        double phase = 0;
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) out.length;
            double wobble = 90 + Math.sin(t * 26) * 22;
            phase += 2 * Math.PI * wobble / SAMPLE_RATE;
            out[i] = Math.sin(phase) * 0.45 * Math.exp(-2.2 * t);
        }
        return out;
    }

    /**
     * Wraps samples in a 16-bit mono WAV container.
     */
    private static byte[] wav(double[] samples) {
        ByteBuffer body = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (double sample : samples) {
            body.putShort((short) (Math.max(-1, Math.min(1, sample)) * Short.MAX_VALUE));
        }
        byte[] data = body.array();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(36 + data.length);
        header.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(data.length);
        out.writeBytes(header.array());
        out.writeBytes(data);
        return out.toByteArray();
    }
}

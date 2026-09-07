package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Play / stop registered sounds. Unreal mental model: {@code UAudioDevice}
 * + {@code UGameplayStatics::PlaySound2D}.
 *
 * <p>Looks up {@link AssetKind#AUDIO} rows on the world's
 * {@link AssetRegistry} ({@link AudioHandle} payloads). One voice per
 * asset id. Spatial / FMOD / attenuation are out of scope — {@link #play2D}
 * is the fire-and-forget 2D stub.
 *
 * <p>Device I/O is behind {@link AudioDevice}. The foss default is
 * {@link SilentAudioDevice} so tests need no hardware.
 */
public final class AudioManager {
    private static final class Voice {
        final String id;
        final boolean twoDimensional;
        final float volume;

        Voice(String id, boolean twoDimensional, float volume) {
            this.id = id;
            this.twoDimensional = twoDimensional;
            this.volume = volume;
        }
    }

    private final AssetRegistry assets;
    private AudioDevice device;
    private float masterVolume = 1.0f;
    private final Map<String, Voice> playing = new LinkedHashMap<>();

    public AudioManager(AssetRegistry assets) {
        this(assets, new SilentAudioDevice());
    }

    public AudioManager(AssetRegistry assets, AudioDevice device) {
        this.assets = assets == null ? new AssetRegistry() : assets;
        this.device = device == null ? new SilentAudioDevice() : device;
        this.device.setMasterVolume(masterVolume);
    }

    public AssetRegistry assets() {
        return assets;
    }

    public AudioDevice device() {
        return device;
    }

    /** Tests inject a recording device. Playing voices are stopped first. */
    public void setDevice(AudioDevice device) {
        stopAll();
        this.device = device == null ? new SilentAudioDevice() : device;
        this.device.setMasterVolume(masterVolume);
    }

    /** {@code PlaySound} — one voice per registered AUDIO id (restarts if live). */
    public boolean play(String idOrPath) {
        return playInternal(idOrPath, 1.0f, false);
    }

    /** {@code PlaySound2D} stub — same catalog, marked non-spatial. */
    public boolean play2D(String idOrPath) {
        return play2D(idOrPath, 1.0f);
    }

    public boolean play2D(String idOrPath, float volumeScale) {
        return playInternal(idOrPath, volumeScale, true);
    }

    public boolean stop(String idOrPath) {
        Asset asset = findAudio(idOrPath);
        if (asset == null) {
            return false;
        }
        Voice removed = playing.remove(asset.id());
        if (removed == null) {
            return false;
        }
        device.stop(asset.id());
        return true;
    }

    public void stopAll() {
        if (playing.isEmpty()) {
            device.stopAll();
            return;
        }
        List<String> ids = new ArrayList<>(playing.keySet());
        playing.clear();
        for (String id : ids) {
            device.stop(id);
        }
        device.stopAll();
    }

    public boolean isPlaying(String idOrPath) {
        Asset asset = findAudio(idOrPath);
        return asset != null && playing.containsKey(asset.id());
    }

    public int playingCount() {
        return playing.size();
    }

    public List<String> playingIds() {
        return new ArrayList<>(playing.keySet());
    }

    public void setMasterVolume(float volume) {
        if (Float.isNaN(volume) || Float.isInfinite(volume)) {
            throw new IllegalArgumentException("master volume");
        }
        masterVolume = clamp01(volume);
        device.setMasterVolume(masterVolume);
    }

    public float masterVolume() {
        return masterVolume;
    }

    void appendDump(StringBuilder out) {
        out.append("world.audio=").append(playing.size()).append('\n');
    }

    List<String> describe() {
        List<String> lines = new ArrayList<>(playing.size());
        for (Voice voice : playing.values()) {
            lines.add(String.format(
                    Locale.US,
                    "%s vol=%.2f 2d=%d",
                    voice.id,
                    voice.volume,
                    voice.twoDimensional ? 1 : 0));
        }
        return lines;
    }

    private boolean playInternal(String idOrPath, float volumeScale, boolean twoDimensional) {
        Asset asset = findAudio(idOrPath);
        if (asset == null) {
            return false;
        }
        if (Float.isNaN(volumeScale) || Float.isInfinite(volumeScale)) {
            throw new IllegalArgumentException("volume");
        }
        float volume = clamp01(volumeScale);
        if (playing.containsKey(asset.id())) {
            device.stop(asset.id());
        }
        playing.put(asset.id(), new Voice(asset.id(), twoDimensional, volume));
        device.play(asset.id(), volume, twoDimensional);
        return true;
    }

    private Asset findAudio(String idOrPath) {
        if (idOrPath == null) {
            return null;
        }
        Asset asset = assets.find(idOrPath);
        if (asset == null || asset.kind() != AssetKind.AUDIO) {
            return null;
        }
        return asset;
    }

    private static float clamp01(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }
}

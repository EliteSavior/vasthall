package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class AudioManagerTest {
    private GameInstance game;
    private AudioManager audio;
    private RecordingAudioDevice device;

    @Before
    public void setUp() {
        game = GameInstance.withDemoAssets();
        game.init();
        game.openLevel("Hall");
        audio = game.audio();
        device = new RecordingAudioDevice();
        audio.setDevice(device);
    }

    @Test
    public void playAndStopByRegisteredAudioId() {
        assertFalse(audio.isPlaying(AssetRegistry.HALL_AMBIENCE_ID));
        assertEquals(0, audio.playingCount());

        assertTrue(audio.play(AssetRegistry.HALL_AMBIENCE_ID));
        assertTrue(audio.isPlaying(AssetRegistry.HALL_AMBIENCE_ID));
        assertEquals(1, audio.playingCount());
        assertTrue(audio.playingIds().contains(AssetRegistry.HALL_AMBIENCE_ID));
        assertEquals(1, device.playCalls);
        assertEquals(AssetRegistry.HALL_AMBIENCE_ID, device.lastPlayId);

        assertTrue(audio.stop(AssetRegistry.HALL_AMBIENCE_ID));
        assertFalse(audio.isPlaying(AssetRegistry.HALL_AMBIENCE_ID));
        assertEquals(0, audio.playingCount());
        assertEquals(1, device.stopCalls);
        assertEquals(AssetRegistry.HALL_AMBIENCE_ID, device.lastStopId);
    }

    @Test
    public void playResolvesAssetPathAndRejectsNonAudio() {
        assertTrue(audio.play(AssetRegistry.HALL_AMBIENCE_PATH));
        assertTrue(audio.isPlaying(AssetRegistry.HALL_AMBIENCE_ID));
        assertTrue(audio.isPlaying(AssetRegistry.HALL_AMBIENCE_PATH));

        assertFalse(audio.play("MissingCue"));
        assertFalse(audio.play(AssetRegistry.HALL_MESH_ID));
        assertFalse(audio.play(null));
        assertFalse(audio.play("  "));
        assertEquals(1, audio.playingCount());
        assertEquals(1, device.playCalls);
    }

    @Test
    public void play2DIsANonSpatialStub() {
        assertTrue(audio.play2D(AssetRegistry.HALL_AMBIENCE_ID));
        assertTrue(audio.isPlaying(AssetRegistry.HALL_AMBIENCE_ID));
        assertTrue(device.lastPlayWas2D);
        assertEquals(1.0f, device.lastPlayVolume, 0.0001f);

        assertTrue(audio.play2D(AssetRegistry.HALL_AMBIENCE_ID, 0.5f));
        assertEquals(0.5f, device.lastPlayVolume, 0.0001f);
        assertTrue(device.lastPlayWas2D);
        assertEquals(1, audio.playingCount());
    }

    @Test
    public void masterVolumeClampsAndReachesTheDevice() {
        assertEquals(1.0f, audio.masterVolume(), 0.0001f);

        audio.setMasterVolume(0.25f);
        assertEquals(0.25f, audio.masterVolume(), 0.0001f);
        assertEquals(0.25f, device.lastMasterVolume, 0.0001f);

        audio.setMasterVolume(-1.0f);
        assertEquals(0.0f, audio.masterVolume(), 0.0001f);
        audio.setMasterVolume(2.0f);
        assertEquals(1.0f, audio.masterVolume(), 0.0001f);

        try {
            audio.setMasterVolume(Float.NaN);
            fail("expected invalid master volume");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("volume"));
        }
    }

    @Test
    public void stopMissingOrAlreadyStoppedIsSoft() {
        assertFalse(audio.stop("MissingCue"));
        assertFalse(audio.stop(AssetRegistry.HALL_AMBIENCE_ID));
        assertTrue(audio.play(AssetRegistry.HALL_AMBIENCE_ID));
        assertTrue(audio.stop(AssetRegistry.HALL_AMBIENCE_PATH));
        assertFalse(audio.stop(AssetRegistry.HALL_AMBIENCE_ID));
        audio.stopAll();
        assertEquals(0, audio.playingCount());
    }

    @Test
    public void gameplayStaticsReachAudioThroughWorld() {
        assertSame(audio, GameplayStatics.getAudioManager(game.world()));
        assertTrue(GameplayStatics.playSound2D(game.world(), AssetRegistry.HALL_AMBIENCE_ID));
        assertTrue(GameplayStatics.isSoundPlaying(game.world(), AssetRegistry.HALL_AMBIENCE_ID));
        GameplayStatics.setMasterVolume(game.world(), 0.4f);
        assertEquals(0.4f, GameplayStatics.getMasterVolume(game.world()), 0.0001f);
        assertTrue(GameplayStatics.stopSound(game.world(), AssetRegistry.HALL_AMBIENCE_ID));
        assertFalse(GameplayStatics.isSoundPlaying(game.world(), AssetRegistry.HALL_AMBIENCE_ID));
    }

    @Test
    public void consolePlayStopAndMasterVolume() {
        DeveloperConsole console = game.console();

        String played = console.exec("PlaySound HallAmbience");
        assertTrue(played.contains("playing HallAmbience"));
        assertTrue(audio.isPlaying(AssetRegistry.HALL_AMBIENCE_ID));

        String volume = console.exec("SetMasterVolume 0.5");
        assertTrue(volume.contains("0.50") || volume.contains("0.5"));
        assertEquals(0.5f, audio.masterVolume(), 0.0001f);

        String listed = console.exec("audio");
        assertTrue(listed.contains("audio=1"));
        assertTrue(listed.contains("HallAmbience"));

        String stopped = console.exec("StopSound /Game/Audio/HallAmbience");
        assertTrue(stopped.contains("stopped HallAmbience"));
        assertFalse(audio.isPlaying(AssetRegistry.HALL_AMBIENCE_ID));

        String missing = console.exec("playsound MissingCue");
        assertTrue(missing.startsWith("error:"));
        assertTrue(missing.toLowerCase().contains("sound"));
        assertTrue(console.exec("playsound").startsWith("error:"));
        assertTrue(console.exec("setmastervolume").startsWith("error:"));
        assertTrue(console.exec("setmastervolume nope").startsWith("error:"));

        String help = console.exec("help");
        assertTrue(help.contains("playsound"));
        assertTrue(help.contains("stopsound"));
        assertTrue(help.contains("setmastervolume"));
    }

    @Test
    public void dumpAndStatIncludePlayingCount() {
        StringBuilder out = new StringBuilder();
        game.world().appendDump(out);
        assertTrue(out.toString().contains("world.audio=0"));

        audio.play(AssetRegistry.HALL_AMBIENCE_ID);
        out.setLength(0);
        game.world().appendDump(out);
        assertTrue(out.toString().contains("world.audio=1"));

        String stat = game.console().exec("stat");
        assertTrue(stat.contains("audio=1"));
    }

    @Test
    public void worldAndGameInstanceShareTheSameManager() {
        assertSame(game.audio(), game.world().audio());
        assertSame(game.audio(), GameplayStatics.getAudioManager(game.world()));
        World standalone = new World(AssetRegistry.withDemoAssets());
        assertNotNull(standalone.audio());
        assertTrue(standalone.audio().play(AssetRegistry.HALL_AMBIENCE_ID));
        assertEquals(1, standalone.audio().playingCount());
    }

    @Test
    public void silentDeviceNeedsNoHardware() {
        AudioManager silent = new AudioManager(AssetRegistry.withDemoAssets());
        assertTrue(silent.play(AssetRegistry.HALL_AMBIENCE_ID));
        assertTrue(silent.isPlaying(AssetRegistry.HALL_AMBIENCE_ID));
        silent.setMasterVolume(0.1f);
        assertTrue(silent.stop(AssetRegistry.HALL_AMBIENCE_ID));
        assertEquals(0, silent.playingCount());
    }

    static final class RecordingAudioDevice implements AudioDevice {
        int playCalls;
        int stopCalls;
        String lastPlayId;
        String lastStopId;
        float lastPlayVolume = -1.0f;
        float lastMasterVolume = -1.0f;
        boolean lastPlayWas2D;
        final List<String> log = new ArrayList<>();

        @Override
        public void play(String soundId, float volume, boolean twoDimensional) {
            playCalls++;
            lastPlayId = soundId;
            lastPlayVolume = volume;
            lastPlayWas2D = twoDimensional;
            log.add("play " + soundId);
        }

        @Override
        public void stop(String soundId) {
            stopCalls++;
            lastStopId = soundId;
            log.add("stop " + soundId);
        }

        @Override
        public void setMasterVolume(float volume) {
            lastMasterVolume = volume;
            log.add("volume " + volume);
        }

        @Override
        public void stopAll() {
            log.add("stopAll");
        }
    }
}

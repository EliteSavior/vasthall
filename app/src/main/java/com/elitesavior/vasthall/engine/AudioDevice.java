package com.elitesavior.vasthall.engine;

/**
 * Backend that actually emits samples. Unreal analog: {@code FAudioDevice}
 * / platform audio mixer.
 *
 * <p>The default foss implementation is silent ({@link SilentAudioDevice})
 * so {@link AudioManager} logic can be unit-tested without MediaPlayer,
 * SoundPool, or hardware. A later Android device can implement this
 * without changing the gameplay API.
 */
public interface AudioDevice {
    void play(String soundId, float volume, boolean twoDimensional);

    void stop(String soundId);

    void setMasterVolume(float volume);

    void stopAll();
}

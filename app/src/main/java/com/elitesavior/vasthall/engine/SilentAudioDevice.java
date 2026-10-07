package com.elitesavior.vasthall.engine;

/**
 * Foss no-op mixer. Accepts play/stop/volume and does not touch
 * Android {@code MediaPlayer} / {@code SoundPool}.
 */
public final class SilentAudioDevice implements AudioDevice {
    @Override
    public void play(String soundId, float volume, boolean twoDimensional) {
    }

    @Override
    public void stop(String soundId) {
    }

    @Override
    public void setMasterVolume(float volume) {
    }

    @Override
    public void stopAll() {
    }
}

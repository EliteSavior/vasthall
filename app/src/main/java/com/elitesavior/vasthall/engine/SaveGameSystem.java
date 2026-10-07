package com.elitesavior.vasthall.engine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Slot files on disk for {@link SaveGame}. Unreal mental model:
 * {@code SaveGameToSlot} / {@code LoadGameFromSlot} / {@code DoesSaveGameExist}
 * / {@code DeleteGameInSlot} over a directory of {@code <slot>.sav} JSON files.
 *
 * <p>Tests inject a temp directory. The activity points this at
 * {@code filesDir/SaveGames}.
 */
public final class SaveGameSystem {
    private static final Pattern SLOT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final String EXTENSION = ".sav";

    private File directory;

    public void setDirectory(File directory) {
        if (directory == null) {
            throw new IllegalArgumentException("save directory");
        }
        this.directory = directory;
    }

    public File directory() {
        if (directory == null) {
            directory = new File(System.getProperty("java.io.tmpdir"), "vasthall-saves");
        }
        return directory;
    }

    public boolean saveToSlot(String slot, SaveGame save) {
        String name = requireSlotName(slot);
        if (save == null) {
            throw new IllegalArgumentException("save");
        }
        File dir = directory();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create save directory: " + dir);
        }
        File target = slotFile(name);
        File tmp = new File(dir, name + EXTENSION + ".tmp");
        try {
            Files.write(tmp.toPath(), save.toJson().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException failed) {
            throw new IllegalStateException("save failed: " + name, failed);
        } finally {
            if (tmp.exists() && !tmp.delete()) {
                tmp.deleteOnExit();
            }
        }
    }

    public SaveGame loadFromSlot(String slot) {
        if (directory == null || !isSlotName(slot)) {
            return null;
        }
        File file = new File(directory, slot + EXTENSION);
        if (!file.isFile()) {
            return null;
        }
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return SaveGame.parse(json);
        } catch (IOException failed) {
            throw new IllegalStateException("load failed: " + slot, failed);
        }
    }

    public boolean doesSlotExist(String slot) {
        if (directory == null || !isSlotName(slot)) {
            return false;
        }
        return new File(directory, slot + EXTENSION).isFile();
    }

    public boolean deleteSlot(String slot) {
        if (!doesSlotExist(slot)) {
            return false;
        }
        return slotFile(slot).delete();
    }

    public List<String> slots() {
        File dir = directory;
        if (dir == null) {
            return new ArrayList<>();
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return new ArrayList<>();
        }
        List<String> names = new ArrayList<>();
        for (File file : files) {
            String fileName = file.getName();
            if (!file.isFile() || !fileName.endsWith(EXTENSION)) {
                continue;
            }
            String slot = fileName.substring(0, fileName.length() - EXTENSION.length());
            if (isSlotName(slot)) {
                names.add(slot);
            }
        }
        Collections.sort(names);
        return names;
    }

    public File slotFile(String slot) {
        return new File(directory(), requireSlotName(slot) + EXTENSION);
    }

    static String requireSlotName(String slot) {
        if (!isSlotName(slot)) {
            throw new IllegalArgumentException("slot name");
        }
        return slot;
    }

    static boolean isSlotName(String slot) {
        return slot != null && SLOT.matcher(slot).matches();
    }
}

package com.oreladder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Всё хранится в <мир>/oreladder.json */
public class LadderData {
    public static class P {
        public String lang = "ru";
        public boolean langManual = false;

        public int stage = 0;             // индекс следующей руды (0..TOTAL)
        public int oresDone = 0;          // сколько руд реально добыто
        public String task = "";
        public boolean swapUsed = false;
        public double rewards = 0;
        public double penalty = 0;
        public List<String> restrictions = new ArrayList<>();

        public boolean skippedOptional = false; // золото Незера пропущено
        public boolean optionalDone = false;
        public long optionalDeadline = 0;       // 5 минут на золото Незера после незерита

        public int finalPhase = 0;        // 0 нет, 1 убить дракона, 2 сдача, 3 победа, 4 проклятие
        public long dragonDeadline = 0;   // 30 минут на дракона (с входа в Энд)
        public long deadline = 0;         // 30 минут на сдачу после смерти дракона

        // состояние активного задания
        public long age = 0;
        public int[] base = new int[6];
        public int[] bedBase = new int[16];
        public int count = 0;
        public int flags = 0;
        public int lastFood = -1;
        public String startBiome = "";
        public String lastHitSkeleton = "";

        public transient int lastPunish = -1000;
        public transient int jitterCap = 0;
        public transient long jitterAt = 0;
    }

    public static class Save {
        public int mode = 0; // 0 нормальный, 1 очень экстримальный
        public boolean chestSet = false;
        public int chestY = 64;
        public Map<String, P> players = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Save save = new Save();
    private static Path file;

    public static void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("oreladder.json");
        boolean loaded = false;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                Save s = GSON.fromJson(r, Save.class);
                if (s != null) {
                    save = s;
                    loaded = true;
                }
            } catch (Exception e) {
                System.err.println("[OreLadder] Не удалось прочитать данные: " + e);
            }
        }
        if (!loaded) {
            save = new Save();
            if (server.isSingleplayer()) save.mode = ModeState.pending;
            ModeState.pending = 0;
        }
        if (save.players == null) save.players = new HashMap<>();
        if (!loaded) save();
    }

    public static void save() {
        if (file == null) return;
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(save, w);
        } catch (Exception e) {
            System.err.println("[OreLadder] Не удалось сохранить данные: " + e);
        }
    }

    public static Save data() { return save; }
    public static int mode() { return save.mode; }
    public static void setMode(int m) { save.mode = m; save(); }

    public static boolean anyProgress() {
        for (P p : save.players.values()) if (p.oresDone > 0 || !p.task.isEmpty()) return true;
        return false;
    }

    public static P get(ServerPlayerEntity p) {
        P d = save.players.computeIfAbsent(p.getUuidAsString(), k -> new P());
        if (d.task == null) d.task = "";
        if (d.lang == null) d.lang = "ru";
        if (d.startBiome == null) d.startBiome = "";
        if (d.lastHitSkeleton == null) d.lastHitSkeleton = "";
        if (d.restrictions == null) d.restrictions = new ArrayList<>();
        if (d.base == null || d.base.length != 6) d.base = Arrays.copyOf(d.base == null ? new int[0] : d.base, 6);
        if (d.bedBase == null || d.bedBase.length != 16) d.bedBase = Arrays.copyOf(d.bedBase == null ? new int[0] : d.bedBase, 16);
        return d;
    }

    public static void reset(ServerPlayerEntity p) {
        P old = get(p);
        P n = new P();
        n.lang = old.lang;
        n.langManual = old.langManual;
        save.players.put(p.getUuidAsString(), n);
        save();
    }
}

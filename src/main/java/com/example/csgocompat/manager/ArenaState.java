package com.example.csgocompat.manager;

import com.example.csgocompat.config.CsgoConfig;
import net.minecraft.core.BlockPos;

/**
 * Impostazioni della mappa attualmente caricata: spawn delle due squadre e numero di bot.
 * Prima vivevano come campi statici dentro il comando e sparivano ad ogni riavvio.
 */
public final class ArenaState {

    private static BlockPos spawnT = null;
    private static BlockPos spawnCT = null;
    private static int botsT = -1;
    private static int botsCT = -1;
    private static String loadedMapName = null;

    private ArenaState() {
    }

    public static BlockPos getSpawnT() {
        return spawnT;
    }

    public static BlockPos getSpawnCT() {
        return spawnCT;
    }

    public static void setSpawnT(BlockPos pos) {
        spawnT = pos == null ? null : pos.immutable();
    }

    public static void setSpawnCT(BlockPos pos) {
        spawnCT = pos == null ? null : pos.immutable();
    }

    public static int getBotsT() {
        return botsT >= 0 ? botsT : CsgoConfig.get().botsT;
    }

    public static int getBotsCT() {
        return botsCT >= 0 ? botsCT : CsgoConfig.get().botsCT;
    }

    public static void setBotsT(int count) {
        botsT = Math.max(0, count);
    }

    public static void setBotsCT(int count) {
        botsCT = Math.max(0, count);
    }

    public static String getLoadedMapName() {
        return loadedMapName;
    }

    public static void setLoadedMapName(String name) {
        loadedMapName = name;
    }

    public static boolean isReady() {
        return spawnT != null && spawnCT != null;
    }

    public static void clear() {
        spawnT = null;
        spawnCT = null;
        botsT = -1;
        botsCT = -1;
        loadedMapName = null;
    }
}

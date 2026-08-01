package com.example.csgocompat.manager;

import com.example.csgocompat.CsGoCompatMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro delle C4 piazzate.
 *
 * <p>Il chunk della bomba viene forzato in memoria finché la bomba esiste: senza questo, se il
 * chunk si scaricava la C4 smetteva di ticchettare e usciva dal registro, facendo vincere il round
 * ai CT con la bomba ancora attiva.
 *
 * <p>Lista copy-on-write perché viene letta anche dal thread di rendering dell'HUD.
 */
public final class BombRegistry {

    private static final List<BlockPos> PLANTED = new CopyOnWriteArrayList<>();
    /** C4 disinnescate: non contano più come attive ma vanno rimosse alla pulizia del round. */
    private static final List<BlockPos> DEFUSED_PENDING_CLEANUP = new CopyOnWriteArrayList<>();

    private BombRegistry() {
    }

    public static List<BlockPos> all() {
        return Collections.unmodifiableList(PLANTED);
    }

    public static boolean isEmpty() {
        return PLANTED.isEmpty();
    }

    public static boolean contains(BlockPos pos) {
        return pos != null && PLANTED.contains(pos);
    }

    public static BlockPos first() {
        return PLANTED.isEmpty() ? null : PLANTED.get(0);
    }

    public static void add(ServerLevel level, BlockPos pos) {
        if (pos == null) return;
        BlockPos immutable = pos.immutable();
        if (PLANTED.contains(immutable)) return;
        PLANTED.add(immutable);
        setForced(level, immutable, true);
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        if (pos == null) return;
        if (PLANTED.remove(pos.immutable())) {
            setForced(level, pos, false);
        }
    }

    /**
     * Segna una C4 disinnescata: esce subito dalle bombe attive (così le condizioni di vittoria
     * tornano corrette) ma resta in coda di pulizia.
     *
     * <p>Senza questa coda il blocco disinnescato non veniva più rimosso da nessuno: {@code remove}
     * lo toglieva dal registro e {@code clearAll} a inizio round non sapeva più dove fosse, quindi
     * la bomba restava piantata nella mappa per tutti i round successivi.
     */
    public static void markDefused(ServerLevel level, BlockPos pos) {
        if (pos == null) return;
        BlockPos immutable = pos.immutable();
        remove(level, immutable);
        if (!DEFUSED_PENDING_CLEANUP.contains(immutable)) {
            DEFUSED_PENDING_CLEANUP.add(immutable);
        }
    }

    /** Rimuove ogni C4 registrata (attiva o disinnescata), sia dal mondo che dal registro. */
    public static void clearAll(ServerLevel level) {
        List<BlockPos> all = new ArrayList<>(PLANTED);
        all.addAll(DEFUSED_PENDING_CLEANUP);

        for (BlockPos pos : all) {
            if (level != null && level.getBlockState(pos).is(CsGoCompatMod.C4_BOMB)) {
                level.removeBlock(pos, false);
            }
            setForced(level, pos, false);
        }
        PLANTED.clear();
        DEFUSED_PENDING_CLEANUP.clear();
    }

    private static void setForced(ServerLevel level, BlockPos pos, boolean forced) {
        if (level == null) return;
        try {
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, forced);
        } catch (Throwable ignored) {
        }
    }
}

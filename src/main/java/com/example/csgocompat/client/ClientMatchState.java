package com.example.csgocompat.client;

import com.example.csgocompat.network.MatchStatePayload;

/**
 * Copia lato client dello stato del match, alimentata dai pacchetti del server.
 * L'HUD legge da qui invece che dai campi statici del server.
 */
public final class ClientMatchState {

    private static MatchStatePayload current = null;
    private static long lastUpdateMillis = 0;

    private ClientMatchState() {
    }

    public static void apply(MatchStatePayload payload) {
        current = payload;
        lastUpdateMillis = System.currentTimeMillis();
    }

    public static void clear() {
        current = null;
    }

    /** Lo stato scade se il server smette di aggiornarlo (disconnessione, mod assente lato server). */
    public static MatchStatePayload get() {
        if (current == null || System.currentTimeMillis() - lastUpdateMillis > 5000) {
            return null;
        }
        return current;
    }

    public static boolean shouldRenderHud() {
        MatchStatePayload state = get();
        if (state == null) return false;
        return state.phase() != MatchStatePayload.PHASE_IDLE || state.bombPlanted();
    }
}

package com.example.csgocompat.manager;

import com.example.csgocompat.config.CsgoConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Team Blackboard: memoria condivisa fra i membri di una stessa squadra (T e CT).
 */
public class MatchManager {

    private static final Map<String, Vec3> LAST_KNOWN_ENEMY_POSITIONS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_KNOWN_ENEMY_TIMES = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_RADIO_CALL_TIMES = new ConcurrentHashMap<>();

    public static void updateEnemyPosition(String team, Vec3 position, Level level) {
        if (team == null || team.equals("NEUTRAL") || position == null || level == null) {
            return;
        }
        LAST_KNOWN_ENEMY_POSITIONS.put(team, position);
        LAST_KNOWN_ENEMY_TIMES.put(team, level.getGameTime());
    }

    /**
     * Segnala un avvistamento: aggiorna la blackboard e manda la chiamata radio.
     *
     * <p>La radio ora è indirizzata solo alla propria squadra: prima finiva a tutti i giocatori,
     * rivelando la posizione dei T ai T stessi e ai CT contemporaneamente.
     */
    public static void reportEnemySpotted(String team, Vec3 position, Level level) {
        if (team == null || team.equals("NEUTRAL") || position == null || level == null) {
            return;
        }

        updateEnemyPosition(team, position, level);

        CsgoConfig cfg = CsgoConfig.get();
        long gameTime = level.getGameTime();
        Long lastCall = LAST_RADIO_CALL_TIMES.get(team);
        if (lastCall != null && (gameTime - lastCall) <= cfg.radioCooldownTicks) {
            return;
        }
        LAST_RADIO_CALL_TIMES.put(team, gameTime);

        String siteName = SiteRegistry.nearestName(position);
        String color = team.equalsIgnoreCase("CT") ? "§b" : "§c";
        String msg = color + "§l[Radio - " + team + "] §r" + color
                + "Enemy spotted near §f§l" + siteName + color + "! Rotate!";

        Component message = Component.literal(msg);
        for (Player player : level.players()) {
            if (cfg.radioToOwnTeamOnly && !team.equals(CsgoMatchState.resolveTeam(player))) {
                continue;
            }
            player.displayClientMessage(message, false);
        }

        level.playSound(null, BlockPos.containing(position), SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.HOSTILE, 1.0f, 1.5f);
    }

    public static String getNearestSiteName(Level level, Vec3 pos) {
        return SiteRegistry.nearestName(pos);
    }

    public static Vec3 getLastKnownEnemyPosition(String team) {
        if (team == null) return null;
        return LAST_KNOWN_ENEMY_POSITIONS.get(team);
    }

    public static boolean hasRecentEnemyPosition(String team, Level level, long maxAgeTicks) {
        if (team == null || level == null) return false;
        Vec3 pos = LAST_KNOWN_ENEMY_POSITIONS.get(team);
        Long time = LAST_KNOWN_ENEMY_TIMES.get(team);
        if (pos == null || time == null) return false;

        long elapsed = level.getGameTime() - time;
        return elapsed >= 0 && elapsed <= maxAgeTicks;
    }

    public static void clear() {
        LAST_KNOWN_ENEMY_POSITIONS.clear();
        LAST_KNOWN_ENEMY_TIMES.clear();
        LAST_RADIO_CALL_TIMES.clear();
    }
}

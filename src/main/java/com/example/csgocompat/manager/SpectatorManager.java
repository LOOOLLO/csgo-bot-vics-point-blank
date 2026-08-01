package com.example.csgocompat.manager;

import com.example.csgocompat.entity.CounterTerroristEntity;
import com.example.csgocompat.entity.TerroristEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spettatore in stile CS:GO: invece del teleport fisso sul punto di morte, il giocatore può
 * agganciarsi alla telecamera di un compagno (giocatore o bot) e ciclare fra loro.
 */
public final class SpectatorManager {

    private static final Map<UUID, Integer> CYCLE_INDEX = new HashMap<>();

    private SpectatorManager() {
    }

    public static void onPlayerDisconnect(UUID uuid) {
        CYCLE_INDEX.remove(uuid);
    }

    /** Bersagli osservabili: prima i compagni umani, poi i bot della stessa squadra. */
    public static List<LivingEntity> targets(ServerPlayer player) {
        List<LivingEntity> result = new ArrayList<>(CsgoMatchState.livingTeammates(player));

        String team = CsgoMatchState.resolveTeam(player);
        if (!team.equals("NEUTRAL") && player.level() instanceof ServerLevel level) {
            AABB area = player.getBoundingBox().inflate(512.0);
            if (team.equals("T")) {
                result.addAll(level.getEntitiesOfClass(TerroristEntity.class, area, LivingEntity::isAlive));
            } else {
                result.addAll(level.getEntitiesOfClass(CounterTerroristEntity.class, area, LivingEntity::isAlive));
            }
        }
        return result;
    }

    public static boolean attachToBestTeammate(ServerPlayer player) {
        List<LivingEntity> available = targets(player);
        if (available.isEmpty()) {
            player.displayClientMessage(Component.literal("§7[CS:GO] No teammate to follow — free camera."), true);
            return false;
        }
        CYCLE_INDEX.put(player.getUUID(), 0);
        return spectate(player, available.get(0));
    }

    /** Passa al bersaglio successivo; se non ce ne sono, torna alla telecamera libera. */
    public static boolean cycle(ServerPlayer player) {
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.displayClientMessage(Component.literal("§c[CS:GO] You are still alive!"), false);
            return false;
        }

        List<LivingEntity> available = targets(player);
        if (available.isEmpty()) {
            detach(player);
            player.displayClientMessage(Component.literal("§7[CS:GO] No teammate alive — free camera."), true);
            return false;
        }

        int index = (CYCLE_INDEX.getOrDefault(player.getUUID(), -1) + 1) % available.size();
        CYCLE_INDEX.put(player.getUUID(), index);
        return spectate(player, available.get(index));
    }

    private static boolean spectate(ServerPlayer player, LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        player.setCamera(target);
        player.displayClientMessage(Component.literal(
                "§7[Spec] §f" + target.getName().getString() + " §7— §f/csgo spec §7for the next one, §f/csgo spec free §7for the free camera"), true);
        return true;
    }

    /** Torna alla telecamera libera. */
    public static void detach(ServerPlayer player) {
        CYCLE_INDEX.remove(player.getUUID());
        try {
            player.setCamera(player);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sgancia automaticamente chi stava seguendo un bersaglio morto, altrimenti la telecamera resta
     * incollata a un cadavere.
     */
    public static void tick(ServerPlayer player) {
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            CYCLE_INDEX.remove(player.getUUID());
            return;
        }
        Entity camera = player.getCamera();
        if (camera != null && camera != player && !camera.isAlive()) {
            if (!cycle(player)) {
                detach(player);
            }
        }
    }
}

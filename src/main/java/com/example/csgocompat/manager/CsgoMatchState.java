package com.example.csgocompat.manager;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.config.CsgoConfig;
import com.example.csgocompat.entity.CounterTerroristEntity;
import com.example.csgocompat.entity.TerroristEntity;
import com.example.csgocompat.item.ModItems;
import com.example.csgocompat.network.CsgoNetworking;
import com.example.csgocompat.network.MatchStatePayload;
import com.example.csgocompat.util.GunUtil;
import com.example.csgocompat.util.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CsgoMatchState {

    public enum MatchDifficulty {
        EASY, NORMAL, HARD, UNFAIR
    }

    public enum MatchPhase {
        IDLE, WARMUP, FREEZE, LIVE, ROUND_END
    }

    public static MatchDifficulty currentDifficulty = MatchDifficulty.NORMAL;
    public static MatchPhase phase = MatchPhase.IDLE;

    public static int roundTimeTicks = 2300;
    public static int scoreT = 0;
    public static int scoreCT = 0;
    public static int aliveTCount = 0;
    public static int aliveCTCount = 0;

    private static int roundEndCooldownTicks = -1;
    private static int freezeTicksLeft = 0;
    private static int warmupTicksLeft = 0;
    private static int roundNumber = 0;
    private static int roundGraceTicks = 0;
    private static boolean sidesSwapped = false;

    private static final Map<UUID, String> PLAYER_TEAMS = new HashMap<>();
    private static final Map<UUID, Vec3> PLAYER_LAST_POSITIONS = new HashMap<>();
    private static final Map<UUID, Integer> RESPAWN_TIMERS = new HashMap<>();
    private static final Set<UUID> DEAD_THIS_ROUND = new HashSet<>();
    private static final Map<UUID, Vec3> FROZEN_POSITIONS = new HashMap<>();
    /** Vittime colpite alla testa di recente, per marcare l'headshot nel kill feed. */
    private static final Map<UUID, Long> RECENT_HEADSHOTS = new HashMap<>();

    /** Bot vivi tracciati per UUID: contarli così è O(numero di bot) invece di scansionare la mappa. */
    private static final Set<UUID> T_BOTS = new HashSet<>();
    private static final Set<UUID> CT_BOTS = new HashSet<>();

    // ------------------------------------------------------------------ stato

    public static boolean isMatchActive() {
        return phase == MatchPhase.LIVE;
    }

    /** Compatibilità con il codice che leggeva il vecchio flag. */
    public static boolean matchActive() {
        return isMatchActive();
    }

    public static boolean isWarmup() {
        return phase == MatchPhase.WARMUP;
    }

    public static int getRoundNumber() {
        return roundNumber;
    }

    public static void setPlayerTeam(UUID uuid, String team) {
        PLAYER_TEAMS.put(uuid, team);
    }

    public static String getPlayerTeam(UUID uuid) {
        return PLAYER_TEAMS.getOrDefault(uuid, "NEUTRAL");
    }

    public static void clearPlayerTeams() {
        PLAYER_TEAMS.clear();
        PLAYER_LAST_POSITIONS.clear();
        RESPAWN_TIMERS.clear();
        DEAD_THIS_ROUND.clear();
    }

    /** Pulizia alla disconnessione: senza questo le mappe crescevano all'infinito. */
    public static void onPlayerDisconnect(UUID uuid) {
        FROZEN_POSITIONS.remove(uuid);
        RECENT_HEADSHOTS.remove(uuid);
        PLAYER_LAST_POSITIONS.remove(uuid);
        RESPAWN_TIMERS.remove(uuid);
        DEAD_THIS_ROUND.remove(uuid);
    }

    public static CsgoConfig.DifficultySettings difficultySettings() {
        CsgoConfig cfg = CsgoConfig.get();
        return switch (currentDifficulty) {
            case EASY -> cfg.easy;
            case HARD -> cfg.hard;
            case UNFAIR -> cfg.unfair;
            default -> cfg.normal;
        };
    }

    public static double getHealthForDifficulty() {
        return difficultySettings().health;
    }

    public static double getSpeedForDifficulty() {
        return difficultySettings().speed;
    }

    // ------------------------------------------------------------------ respawn

    /**
     * Imposta il punto di respawn usando l'API pubblica di 1.21.11. La versione precedente ci
     * arrivava per reflection scandendo i costruttori ad ogni chiamata, fallendo in silenzio.
     */
    public static void setPlayerRespawnPos(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return;
        try {
            LevelData.RespawnData data =
                    LevelData.RespawnData.of(level.dimension(), pos, player.getYRot(), player.getXRot());
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(data, true), false);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ tick

    public static void tickServer(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;

        switch (phase) {
            case ROUND_END -> tickRoundEnd(server);
            case FREEZE -> tickFreeze(server, level);
            case WARMUP -> tickWarmup(server, level);
            case LIVE -> tickLive(server, level);
            case IDLE -> {
            }
        }

        if (server.getTickCount() % CsgoNetworking.SYNC_INTERVAL_TICKS == 0) {
            syncToClients(server);
        }
    }

    private static void tickRoundEnd(MinecraftServer server) {
        if (roundEndCooldownTicks > 0) {
            roundEndCooldownTicks--;
            if (roundEndCooldownTicks == 0) {
                roundEndCooldownTicks = -1;
                startNextRound(server, null);
            }
        }
    }

    private static void tickWarmup(MinecraftServer server, ServerLevel level) {
        if (warmupTicksLeft > 0) {
            warmupTicksLeft--;
            if (warmupTicksLeft % 200 == 0) {
                int secs = warmupTicksLeft / 20;
                broadcastActionBar(server, "§e§lWARMUP §7— §f" + secs + "s until the match");
            }
            if (warmupTicksLeft == 0) {
                broadcast(server, "§6§l[CS:GO] Warmup over — the match begins!");
                startMatchFromScratch(server);
                return;
            }
        }

        // Respawn continuo dei giocatori durante il warmup.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String team = resolveTeam(player);
            if (team.equals("NEUTRAL")) continue;

            if (!player.isAlive() || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                int timer = RESPAWN_TIMERS.getOrDefault(player.getUUID(), CsgoConfig.get().warmupRespawnDelayTicks);
                timer--;
                if (timer <= 0) {
                    RESPAWN_TIMERS.remove(player.getUUID());
                    respawnForWarmup(player, team);
                } else {
                    RESPAWN_TIMERS.put(player.getUUID(), timer);
                    player.displayClientMessage(
                            Component.literal("§7Respawning in §e" + Math.max(1, timer / 20) + "s"), true);
                }
            }
        }

        // Rimpiazza i bot morti per tenere vivo il deathmatch.
        refreshBotCounts(level);
        aliveTCount = T_BOTS.size();
        aliveCTCount = CT_BOTS.size();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isPlayerInPlay(player)) continue;
            String team = resolveTeam(player);
            if (team.equals("T")) aliveTCount++;
            else if (team.equals("CT")) aliveCTCount++;
        }

        if (server.getTickCount() % 40 == 0) {
            int wantT = ArenaState.getBotsT();
            int wantCT = ArenaState.getBotsCT();
            if (T_BOTS.size() < wantT && ArenaState.getSpawnT() != null) {
                spawnTerrorist(level, ArenaState.getSpawnT());
            }
            if (CT_BOTS.size() < wantCT && ArenaState.getSpawnCT() != null) {
                spawnCounterTerrorist(level, ArenaState.getSpawnCT(), CT_BOTS.size());
            }
        }
    }

    private static void tickLive(MinecraftServer server, ServerLevel level) {
        handlePlayerLifecycle(server, level);
        refreshBotCounts(level);

        aliveTCount = T_BOTS.size();
        aliveCTCount = CT_BOTS.size();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isPlayerInPlay(player)) continue;
            String team = resolveTeam(player);
            if (team.equals("T")) aliveTCount++;
            else if (team.equals("CT")) aliveCTCount++;
        }

        if (roundTimeTicks > 0) {
            roundTimeTicks--;
        }

        // Piccola grazia iniziale: senza, il round poteva chiudersi prima che i bot fossero contati.
        if (roundGraceTicks > 0) {
            roundGraceTicks--;
            return;
        }

        if (server.getTickCount() % 40 == 0) {
            ensureBombExists(server, level);
        }

        boolean bombPlanted = !BombRegistry.isEmpty();

        if (aliveCTCount == 0) {
            // Tutti i CT eliminati: i T vincono anche senza aspettare l'esplosione.
            winRound(server, "T", "§c§l[CS:GO] Counter-Terrorists eliminated!");
        } else if (aliveTCount == 0 && !bombPlanted) {
            winRound(server, "CT", "§b§l[CS:GO] Terrorists eliminated!");
        } else if (roundTimeTicks <= 0 && !bombPlanted) {
            winRound(server, "CT", "§b§l[CS:GO] Time has run out!");
        }
    }

    /** Morte, spettatore e aggiornamento dello spawn point. */
    private static void handlePlayerLifecycle(MinecraftServer server, ServerLevel level) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            String team = resolveTeam(player);
            if (team.equals("NEUTRAL")) continue;

            boolean dead = DEAD_THIS_ROUND.contains(uuid);

            if (!dead && player.isAlive() && player.getHealth() > 0.0f) {
                PLAYER_LAST_POSITIONS.put(uuid, player.position());
                // Solo per chi è davvero in gioco: prima lo spawn point seguiva anche la telecamera
                // libera degli spettatori.
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                        && server.getTickCount() % 40 == 0) {
                    setPlayerRespawnPos(player, level, player.blockPosition());
                }
            }
        }
    }

    /** Chiamato dall'evento di morte: registra il decesso una volta sola. */
    public static void onPlayerDeath(ServerPlayer player) {
        if (player == null) return;
        String team = resolveTeam(player);
        if (team.equals("NEUTRAL")) return;

        UUID uuid = player.getUUID();
        PLAYER_LAST_POSITIONS.put(uuid, player.position());
        dropCarriedBomb(player);

        if (phase == MatchPhase.LIVE) {
            DEAD_THIS_ROUND.add(uuid);
        } else if (phase == MatchPhase.WARMUP) {
            RESPAWN_TIMERS.put(uuid, CsgoConfig.get().warmupRespawnDelayTicks);
        }
    }

    /** Chiamato dopo il respawn vanilla: decide se il giocatore torna in gioco o va in spettatore. */
    public static void onPlayerRespawn(ServerPlayer player) {
        if (player == null) return;
        String team = resolveTeam(player);
        if (team.equals("NEUTRAL")) return;

        if (phase == MatchPhase.WARMUP) {
            respawnForWarmup(player, team);
            return;
        }

        if (DEAD_THIS_ROUND.contains(player.getUUID())) {
            enterSpectator(player);
        }
    }

    private static void enterSpectator(ServerPlayer player) {
        Vec3 deathPos = PLAYER_LAST_POSITIONS.getOrDefault(player.getUUID(), player.position());
        player.setGameMode(GameType.SPECTATOR);
        if (player.level() instanceof ServerLevel sLevel) {
            player.teleportTo(sLevel, deathPos.x, deathPos.y + 1.0, deathPos.z,
                    Collections.emptySet(), player.getYRot(), player.getXRot(), false);
        }
        player.displayClientMessage(
                Component.literal("§c§l[CS:GO] You died! §7Use /csgo spec §fto follow a teammate."), false);
        SpectatorManager.attachToBestTeammate(player);
    }

    private static void respawnForWarmup(ServerPlayer player, String team) {
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            SpectatorManager.detach(player);
            player.setGameMode(GameType.SURVIVAL);
        }
        equipTeam(player, team);
        teleportToSpawn(player, team);
        player.setHealth(player.getMaxHealth());
        player.displayClientMessage(Component.literal("§a[CS:GO] Respawned — warmup"), true);
    }

    // ------------------------------------------------------------------ bot

    private static void refreshBotCounts(ServerLevel level) {
        T_BOTS.removeIf(uuid -> !isAliveEntity(level, uuid));
        CT_BOTS.removeIf(uuid -> !isAliveEntity(level, uuid));
    }

    private static boolean isAliveEntity(ServerLevel level, UUID uuid) {
        Entity entity = level.getEntityInAnyDimension(uuid);
        return entity != null && entity.isAlive();
    }

    public static void registerBot(String team, UUID uuid) {
        if ("T".equals(team)) T_BOTS.add(uuid);
        else if ("CT".equals(team)) CT_BOTS.add(uuid);
    }

    public static void unregisterBot(UUID uuid) {
        T_BOTS.remove(uuid);
        CT_BOTS.remove(uuid);
    }

    // ------------------------------------------------------------------ round

    private static void winRound(MinecraftServer server, String winner, String reason) {
        if ("T".equals(winner)) scoreT++;
        else scoreCT++;

        broadcast(server, reason);
        broadcast(server, ("T".equals(winner) ? "§c§l[CS:GO] Round won by the TERRORISTS! " : "§b§l[CS:GO] Round won by the COUNTER-TERRORISTS! ")
                + "§f(T " + scoreT + " - " + scoreCT + " CT)");
        endRound(server);
    }

    public static void onBombDefused(MinecraftServer server) {
        if (phase != MatchPhase.LIVE) return;
        playGlobalSound(server, ModSounds.BOMB_DEFUSED, 1.0f);
        winRound(server, "CT", "§a§l[CS:GO] Bomb defused!");
    }

    public static void onBombExploded(MinecraftServer server) {
        if (phase != MatchPhase.LIVE) return;
        winRound(server, "T", "§c§l[CS:GO] Target destroyed!");
    }

    private static void endRound(MinecraftServer server) {
        phase = MatchPhase.ROUND_END;
        roundTimeTicks = 0;
        aliveTCount = 0;
        aliveCTCount = 0;
        MatchManager.clear();

        CsgoConfig cfg = CsgoConfig.get();
        if (scoreT >= cfg.roundsToWin || scoreCT >= cfg.roundsToWin) {
            String champion = scoreT > scoreCT ? "§c§lTERRORISTS" : "§b§lCOUNTER-TERRORISTS";
            broadcast(server, "§6§l[CS:GO] MATCH OVER! " + champion + " §6win §f" + scoreT + " - " + scoreCT);
            finishMatch(server);
            return;
        }

        roundEndCooldownTicks = cfg.roundEndDelayTicks();
    }

    private static void finishMatch(MinecraftServer server) {
        phase = MatchPhase.IDLE;
        roundEndCooldownTicks = -1;
        ServerLevel level = server.overworld();
        if (level != null) {
            cleanArena(level);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                SpectatorManager.detach(player);
                player.setGameMode(GameType.SURVIVAL);
            }
        }
        DEAD_THIS_ROUND.clear();
        syncToClients(server);
    }

    /** Avvia la sequenza completa: warmup (se abilitato) e poi il primo round. */
    public static boolean startMatch(MinecraftServer server, ServerPlayer initiator) {
        if (!ensureArenaReady(server, initiator)) return false;

        scoreT = 0;
        scoreCT = 0;
        roundNumber = 0;
        sidesSwapped = false;

        if (CsgoConfig.get().warmupEnabled) {
            startWarmup(server);
            return true;
        }
        return startMatchFromScratch(server);
    }

    private static void startWarmup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) return;

        cleanArena(level);
        phase = MatchPhase.WARMUP;
        warmupTicksLeft = CsgoConfig.get().warmupTicks();
        DEAD_THIS_ROUND.clear();
        RESPAWN_TIMERS.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String team = resolveTeam(player);
            if (!team.equals("NEUTRAL")) {
                respawnForWarmup(player, team);
            }
        }

        broadcast(server, "§e§l[CS:GO] WARMUP §7— infinite respawns for "
                + CsgoConfig.get().warmupSeconds + "s. §fUse /csgo skipwarmup §7to skip it.");
        syncToClients(server);
    }

    public static void skipWarmup(MinecraftServer server) {
        if (phase != MatchPhase.WARMUP) return;
        warmupTicksLeft = 0;
        broadcast(server, "§6§l[CS:GO] Warmup skipped!");
        startMatchFromScratch(server);
    }

    private static boolean startMatchFromScratch(MinecraftServer server) {
        roundNumber = 0;
        return startNextRound(server, null);
    }

    public static boolean startNextRound(MinecraftServer server, ServerPlayer initiator) {
        if (!ensureArenaReady(server, initiator)) {
            phase = MatchPhase.IDLE;
            return false;
        }

        ServerLevel level = server.overworld();
        BlockPos spawnT = ArenaState.getSpawnT();
        BlockPos spawnCT = ArenaState.getSpawnCT();

        cleanArena(level);

        roundNumber++;
        CsgoConfig cfg = CsgoConfig.get();

        // Cambio campo a metà partita.
        if (cfg.halfTimeRound > 0 && roundNumber == cfg.halfTimeRound + 1 && !sidesSwapped) {
            swapSides(server);
        }

        int tBotCount = ArenaState.getBotsT();
        int ctBotCount = ArenaState.getBotsCT();

        DEAD_THIS_ROUND.clear();
        RESPAWN_TIMERS.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String pTeam = getPlayerTeam(player.getUUID());
            if (pTeam.equals("T")) {
                tBotCount = Math.max(0, tBotCount - 1);
                prepareForRound(player, "T");
            } else if (pTeam.equals("CT")) {
                ctBotCount = Math.max(0, ctBotCount - 1);
                prepareForRound(player, "CT");
            }
        }

        for (int i = 0; i < tBotCount; i++) {
            spawnTerrorist(level, spawnT);
        }
        for (int i = 0; i < ctBotCount; i++) {
            spawnCounterTerrorist(level, spawnCT, i);
        }

        giveBombToTerroristPlayer(server);

        roundTimeTicks = cfg.roundTimeTicks();
        roundEndCooldownTicks = -1;
        MatchManager.clear();
        PlantingManager.clear();
        RECENT_HEADSHOTS.clear();

        broadcast(server, "§6§l[CS:GO] Round " + roundNumber + " §7— §eDifficulty: " + currentDifficulty.name()
                + " §7| §c" + ArenaState.getBotsT() + " T §7(" + tBotCount + " bots) §7vs §b"
                + ArenaState.getBotsCT() + " CT §7(" + ctBotCount + " bots)");

        if (cfg.freezeTimeSeconds > 0) {
            beginFreeze(server, level, cfg.freezeTimeSeconds * 20);
        } else {
            beginLiveRound(server, level);
        }
        syncToClients(server);
        return true;
    }

    // ------------------------------------------------------------------ freeze time

    /** Congela squadre e bot: nessuno si muove finché il round non parte davvero. */
    private static void beginFreeze(MinecraftServer server, ServerLevel level, int ticks) {
        phase = MatchPhase.FREEZE;
        freezeTicksLeft = ticks;
        FROZEN_POSITIONS.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (resolveTeam(player).equals("NEUTRAL")) continue;
            FROZEN_POSITIONS.put(player.getUUID(), player.position());
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks + 5, 255, false, false));
            // JUMP_BOOST con ampiezza altissima azzera il salto.
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, ticks + 5, 128, false, false));
        }

        setBotsFrozen(level, true);
    }

    private static void tickFreeze(MinecraftServer server, ServerLevel level) {
        freezeTicksLeft--;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Vec3 anchor = FROZEN_POSITIONS.get(player.getUUID());
            if (anchor == null) continue;

            player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
            if (player.position().distanceToSqr(anchor) > 0.75) {
                player.teleportTo(level, anchor.x, anchor.y, anchor.z,
                        Collections.emptySet(), player.getYRot(), player.getXRot(), false);
            }
            player.displayClientMessage(Component.literal(
                    "§e§lFREEZE §7— §f" + Math.max(1, (freezeTicksLeft / 20) + 1) + "s"), true);
        }

        if (freezeTicksLeft <= 0) {
            beginLiveRound(server, level);
        }
    }

    private static void beginLiveRound(MinecraftServer server, ServerLevel level) {
        phase = MatchPhase.LIVE;
        roundGraceTicks = 20;
        freezeTicksLeft = 0;
        FROZEN_POSITIONS.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.removeEffect(MobEffects.SLOWNESS);
            player.removeEffect(MobEffects.JUMP_BOOST);
        }
        setBotsFrozen(level, false);

        playGlobalSound(server, ModSounds.ROUND_START, 0.9f);
        broadcastActionBar(server, "§a§lGO GO GO!");
        syncToClients(server);
    }

    private static void setBotsFrozen(ServerLevel level, boolean frozen) {
        List<TerroristEntity> ts = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(TerroristEntity.class), e -> true, ts);
        for (TerroristEntity bot : ts) bot.setNoAi(frozen);

        List<CounterTerroristEntity> cts = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(CounterTerroristEntity.class), e -> true, cts);
        for (CounterTerroristEntity bot : cts) bot.setNoAi(frozen);
    }

    // ------------------------------------------------------------------ kill feed

    /** Registrato da {@code ShootEnemyGoal} quando il colpo è alla testa. */
    public static void markHeadshot(UUID victim, long gameTime) {
        RECENT_HEADSHOTS.put(victim, gameTime);
    }

    /** Costruisce e diffonde una riga di kill feed. */
    public static void reportKill(net.minecraft.world.entity.LivingEntity victim,
                                  net.minecraft.world.damagesource.DamageSource source) {
        if (!CsgoConfig.get().showKillFeed) return;
        if (victim == null) return;
        MinecraftServer victimServer = victim.level().getServer();
        if (victimServer == null) return;

        String victimTeam = CsGoCompatMod.getTeam(victim);
        Entity attacker = source == null ? null : source.getEntity();
        String attackerTeam = attacker == null ? "NEUTRAL" : CsGoCompatMod.getTeam(attacker);
        if (victimTeam.equals("NEUTRAL") && attackerTeam.equals("NEUTRAL")) return;

        String weapon = "";
        if (attacker instanceof net.minecraft.world.entity.LivingEntity livingAttacker) {
            ItemStack held = livingAttacker.getMainHandItem();
            if (!held.isEmpty()) weapon = held.getHoverName().getString();
        }
        if (weapon.isEmpty() && source != null) weapon = source.getMsgId();

        Long headshotAt = RECENT_HEADSHOTS.remove(victim.getUUID());
        boolean headshot = headshotAt != null && victim.level().getGameTime() - headshotAt <= 5;

        CsgoNetworking.broadcast(victimServer, new com.example.csgocompat.network.KillFeedPayload(
                attacker == null ? "" : attacker.getName().getString(),
                victim.getName().getString(),
                weapon,
                headshot,
                MatchStatePayload.teamId(attackerTeam),
                MatchStatePayload.teamId(victimTeam)
        ));
    }

    /**
     * Consegna la C4 a un giocatore T.
     *
     * <p>Prima la bomba veniva assegnata solo dall'elezione dei ruoli fra bot: con i bot T a 0 e un
     * solo giocatore nei T, nessuno riceveva mai la C4 e il round era impossibile da vincere.
     */
    private static boolean giveBombToTerroristPlayer(MinecraftServer server) {
        if (!CsgoConfig.get().giveBombToPlayer) return false;

        List<ServerPlayer> terrorists = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!"T".equals(getPlayerTeam(player.getUUID()))) continue;
            if (!isPlayerInPlay(player)) continue;
            terrorists.add(player);
        }
        if (terrorists.isEmpty()) return false;

        ServerPlayer carrier = terrorists.get(server.overworld().random.nextInt(terrorists.size()));
        if (!carrier.getInventory().add(new ItemStack(CsGoCompatMod.C4_BOMB.asItem()))) {
            return false;
        }
        carrier.displayClientMessage(Component.literal(
                "§c§l[CS:GO] You have the C4! §fPlant it on the ground inside a Bomb Site."), false);
        return true;
    }

    /**
     * Rete di sicurezza: se nessuno ha la C4 e non è piazzata, la riconsegna.
     *
     * <p>Copre tutti i casi in cui la bomba andava persa e il round diventava invincibile: giocatore
     * entrato a round iniziato, carrier despawnato, warmup appena finito, oppure zero bot T con un
     * solo giocatore nei T.
     */
    private static void ensureBombExists(MinecraftServer server, ServerLevel level) {
        if (!BombRegistry.isEmpty()) return;
        if (playerCarriesBomb(level)) return;

        List<TerroristEntity> bots = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(TerroristEntity.class),
                net.minecraft.world.entity.LivingEntity::isAlive, bots);
        for (TerroristEntity bot : bots) {
            if (bot.getMainHandItem().is(CsGoCompatMod.C4_BOMB.asItem())) return;
        }

        List<ItemEntity> dropped = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class),
                item -> item.getItem().is(CsGoCompatMod.C4_BOMB.asItem()), dropped);
        if (!dropped.isEmpty()) return;

        if (giveBombToTerroristPlayer(server)) return;

        if (!bots.isEmpty()) {
            TerroristEntity bot = bots.get(0);
            bot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(CsGoCompatMod.C4_BOMB.asItem()));
            bot.squadRole = TerroristEntity.SquadRole.CARRIER;
        }
    }

    /** True se un giocatore dei T sta portando la C4: evita che i bot ne generino una seconda. */
    public static boolean playerCarriesBomb(net.minecraft.world.level.Level level) {
        if (level == null) return false;
        for (net.minecraft.world.entity.player.Player player : level.players()) {
            if (!"T".equals(resolveTeam(player))) continue;
            if (player.getInventory().contains(stack -> stack.is(CsGoCompatMod.C4_BOMB.asItem()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Se il giocatore morto portava la C4, la fa cadere a terra esplicitamente: con
     * {@code keepInventory} attivo la bomba resterebbe nell'inventario di un cadavere e il round
     * non sarebbe più vincibile.
     */
    private static void dropCarriedBomb(ServerPlayer player) {
        ItemStack bomb = new ItemStack(CsGoCompatMod.C4_BOMB.asItem());
        boolean had = player.getInventory().contains(stack -> stack.is(CsGoCompatMod.C4_BOMB.asItem()));
        if (!had) return;

        player.getInventory().clearOrCountMatchingItems(
                stack -> stack.is(CsGoCompatMod.C4_BOMB.asItem()), -1, player.inventoryMenu.getCraftSlots());

        if (player.level() instanceof ServerLevel serverLevel) {
            ItemEntity entity = new ItemEntity(serverLevel, player.getX(), player.getY() + 0.5, player.getZ(), bomb);
            entity.setDefaultPickUpDelay();
            serverLevel.addFreshEntity(entity);
        }
    }

    private static void swapSides(MinecraftServer server) {
        sidesSwapped = true;
        int tmp = scoreT;
        scoreT = scoreCT;
        scoreCT = tmp;

        BlockPos spawnT = ArenaState.getSpawnT();
        ArenaState.setSpawnT(ArenaState.getSpawnCT());
        ArenaState.setSpawnCT(spawnT);

        // Anche il numero di bot segue lo scambio: altrimenti chi passava, ad esempio, da T (0 bot)
        // a CT si ritrovava 5 compagni bot e l'altra squadra vuota.
        int botsT = ArenaState.getBotsT();
        ArenaState.setBotsT(ArenaState.getBotsCT());
        ArenaState.setBotsCT(botsT);

        for (Map.Entry<UUID, String> entry : PLAYER_TEAMS.entrySet()) {
            if ("T".equals(entry.getValue())) entry.setValue("CT");
            else if ("CT".equals(entry.getValue())) entry.setValue("T");
        }

        broadcast(server, "§6§l[CS:GO] HALF-TIME! §fTeams switch sides.");
    }

    private static boolean ensureArenaReady(MinecraftServer server, ServerPlayer initiator) {
        if (ArenaState.getSpawnT() == null || ArenaState.getSpawnCT() == null) {
            String msg = "§c[CS:GO] Spawns are not set! Use §f/csgo match spawnT <x y z> §cand §f/csgo match spawnCT <x y z>";
            if (initiator != null) {
                initiator.displayClientMessage(Component.literal(msg), false);
            } else {
                broadcast(server, msg);
            }
            return false;
        }
        if (SiteRegistry.isEmpty()) {
            String msg = "§e[CS:GO] No Bomb Site registered — Terrorists will not be able to plant. Use the Bomb Site Wand.";
            if (initiator != null) {
                initiator.displayClientMessage(Component.literal(msg), false);
            } else {
                broadcast(server, msg);
            }
        }
        return true;
    }

    /**
     * Rimuove bot, C4 a terra e bombe di un round precedente.
     * Itera le entità caricate invece di interrogare una AABB grande quanto il mondo.
     */
    private static void cleanArena(ServerLevel level) {
        if (level == null) return;

        discardAll(level, EntityTypeTest.forClass(TerroristEntity.class), e -> true);
        discardAll(level, EntityTypeTest.forClass(CounterTerroristEntity.class), e -> true);
        discardAll(level, EntityTypeTest.forClass(ItemEntity.class),
                item -> item.getItem().is(CsGoCompatMod.C4_BOMB.asItem()));

        BombRegistry.clearAll(level);
        T_BOTS.clear();
        CT_BOTS.clear();
    }

    private static <T extends Entity> void discardAll(ServerLevel level, EntityTypeTest<Entity, T> test,
                                                      java.util.function.Predicate<? super T> filter) {
        List<T> found = new ArrayList<>();
        level.getEntities(test, filter, found);
        found.forEach(Entity::discard);
    }

    // ------------------------------------------------------------------ spawn dei bot

    private static void spawnTerrorist(ServerLevel level, BlockPos spawn) {
        if (spawn == null) return;
        TerroristEntity bot = CsGoCompatMod.TERRORIST.create(
                level, null, spawn, EntitySpawnReason.COMMAND, false, false);
        if (bot == null) return;
        applyBotAttributes(bot, level, spawn);
        level.addFreshEntity(bot);
        registerBot("T", bot.getUUID());
    }

    private static void spawnCounterTerrorist(ServerLevel level, BlockPos spawn, int index) {
        if (spawn == null) return;
        CounterTerroristEntity bot = CsGoCompatMod.COUNTER_TERRORIST.create(
                level, null, spawn, EntitySpawnReason.COMMAND, false, false);
        if (bot == null) return;
        bot.assignedSiteIndex = index;
        applyBotAttributes(bot, level, spawn);
        level.addFreshEntity(bot);
        registerBot("CT", bot.getUUID());
    }

    private static void applyBotAttributes(net.minecraft.world.entity.Mob bot, ServerLevel level, BlockPos spawn) {
        double health = getHealthForDifficulty();
        double speed = getSpeedForDifficulty();

        double offsetX = (level.random.nextDouble() - 0.5) * 3.0;
        double offsetZ = (level.random.nextDouble() - 0.5) * 3.0;
        bot.setPos(spawn.getX() + 0.5 + offsetX, spawn.getY() + 1.0, spawn.getZ() + 0.5 + offsetZ);

        AttributeInstance maxHealth = bot.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
            bot.setHealth((float) health);
        }
        AttributeInstance movement = bot.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.setBaseValue(speed);
        }
        AttributeInstance followRange = bot.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(CsgoConfig.get().botFollowRange);
        }

        bot.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4, false, false));
        bot.setPersistenceRequired();
    }

    // ------------------------------------------------------------------ reset

    public static void resetMatch(MinecraftServer server) {
        phase = MatchPhase.IDLE;
        roundTimeTicks = CsgoConfig.get().roundTimeTicks();
        scoreT = 0;
        scoreCT = 0;
        aliveTCount = 0;
        aliveCTCount = 0;
        roundEndCooldownTicks = -1;
        warmupTicksLeft = 0;
        roundNumber = 0;
        sidesSwapped = false;

        clearPlayerTeams();
        MatchManager.clear();

        if (server != null) {
            ServerLevel level = server.overworld();
            // Il vecchio reset lasciava bot e C4 attive nel mondo.
            cleanArena(level);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    SpectatorManager.detach(player);
                    player.setGameMode(GameType.SURVIVAL);
                }
            }
            syncToClients(server);
        }
    }

    // ------------------------------------------------------------------ squadre

    /** Risoluzione unica della squadra: mappa del match, poi armatura indossata. */
    public static String resolveTeam(net.minecraft.world.entity.player.Player player) {
        if (player == null) return "NEUTRAL";
        String stored = getPlayerTeam(player.getUUID());
        if (!stored.equals("NEUTRAL")) return stored;

        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof com.example.csgocompat.item.TeamArmorItem armor) {
                return armor.getTeam();
            }
        }
        return "NEUTRAL";
    }

    private static boolean isPlayerInPlay(ServerPlayer player) {
        return player.isAlive()
                && player.getHealth() > 0.0f
                && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                && !DEAD_THIS_ROUND.contains(player.getUUID());
    }

    public static boolean joinTeam(ServerPlayer player, String team) {
        if (player == null || team == null) return false;
        String normalized = team.equalsIgnoreCase("T") ? "T" : team.equalsIgnoreCase("CT") ? "CT" : null;
        if (normalized == null) {
            player.displayClientMessage(Component.literal("§c[CS:GO] Invalid team — use T or CT."), false);
            return false;
        }

        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            SpectatorManager.detach(player);
            player.setGameMode(GameType.SURVIVAL);
        }

        setPlayerTeam(player.getUUID(), normalized);
        DEAD_THIS_ROUND.remove(player.getUUID());
        equipTeam(player, normalized);
        teleportToSpawn(player, normalized);

        player.displayClientMessage(Component.literal(normalized.equals("T")
                ? "§c§l[CS:GO] You joined the TERRORISTS! §fAK-47, 128 rounds and T armour."
                : "§b§l[CS:GO] You joined the COUNTER-TERRORISTS! §fM4A1, 128 rounds, defusal kit and CT armour."), false);
        return true;
    }

    /** Reset di inizio round per un giocatore già assegnato ad una squadra. */
    private static void prepareForRound(ServerPlayer player, String team) {
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            SpectatorManager.detach(player);
            player.setGameMode(GameType.SURVIVAL);
        }
        equipTeam(player, team);
        teleportToSpawn(player, team);
    }

    private static void equipTeam(ServerPlayer player, String team) {
        if (CsgoConfig.get().clearInventoryOnJoin) {
            player.getInventory().clearContent();
        }

        player.setHealth(player.getMaxHealth());
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4, false, false));

        if (team.equals("T")) {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.T_HELMET));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.T_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.T_LEGGINGS));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.T_BOOTS));
            ItemStack ak = GunUtil.getGunItemStack("ak47");
            player.setItemSlot(EquipmentSlot.MAINHAND, ak);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            player.getInventory().add(GunUtil.getAmmoItemStack(ak, "ak47", 64));
            player.getInventory().add(GunUtil.getAmmoItemStack(ak, "ak47", 64));
        } else {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.CT_HELMET));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.CT_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.CT_LEGGINGS));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.CT_BOOTS));
            ItemStack m4 = GunUtil.getGunItemStack("m4a1");
            player.setItemSlot(EquipmentSlot.MAINHAND, m4);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.DEFUSAL_KIT));
            player.getInventory().add(GunUtil.getAmmoItemStack(m4, "m4a1", 64));
            player.getInventory().add(GunUtil.getAmmoItemStack(m4, "m4a1", 64));
        }
    }

    private static void teleportToSpawn(ServerPlayer player, String team) {
        BlockPos spawn = team.equals("T") ? ArenaState.getSpawnT() : ArenaState.getSpawnCT();
        if (spawn == null || !(player.level() instanceof ServerLevel level)) return;
        setPlayerRespawnPos(player, level, spawn);
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
                Collections.emptySet(), player.getYRot(), player.getXRot(), false);
    }

    // ------------------------------------------------------------------ output

    public static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().getPlayers().forEach(p -> p.displayClientMessage(Component.literal(message), false));
    }

    public static void broadcastActionBar(MinecraftServer server, String message) {
        server.getPlayerList().getPlayers().forEach(p -> p.displayClientMessage(Component.literal(message), true));
    }

    /**
     * Suono non posizionale inviato a ciascun giocatore: usare Level#playSound farebbe sentire
     * il suono due volte a chi sta vicino a un compagno.
     */
    private static void playGlobalSound(MinecraftServer server, net.minecraft.sounds.SoundEvent sound, float volume) {
        if (sound == null) return;
        Holder<net.minecraft.sounds.SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSoundPacket(
                    holder, SoundSource.VOICE,
                    player.getX(), player.getY(), player.getZ(),
                    volume, 1.0f, player.getRandom().nextLong()));
        }
    }

    public static void syncToClients(MinecraftServer server) {
        if (server == null) return;

        byte phaseId = switch (phase) {
            case WARMUP -> MatchStatePayload.PHASE_WARMUP;
            case FREEZE -> MatchStatePayload.PHASE_FREEZE;
            case LIVE -> MatchStatePayload.PHASE_LIVE;
            case ROUND_END -> MatchStatePayload.PHASE_ROUND_END;
            default -> MatchStatePayload.PHASE_IDLE;
        };

        BlockPos bomb = BombRegistry.first();
        int bombTicks = 0;
        if (bomb != null) {
            ServerLevel level = server.overworld();
            if (level != null && level.getBlockEntity(bomb)
                    instanceof com.example.csgocompat.block.entity.C4BombBlockEntity c4) {
                bombTicks = c4.getTicksRemaining();
            }
        }

        int displayedTime = switch (phase) {
            case WARMUP -> warmupTicksLeft;
            case FREEZE -> freezeTicksLeft;
            default -> roundTimeTicks;
        };

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MatchStatePayload payload = new MatchStatePayload(
                    phaseId,
                    Math.max(0, displayedTime),
                    scoreT,
                    scoreCT,
                    aliveTCount,
                    aliveCTCount,
                    bomb != null,
                    bombTicks,
                    MatchStatePayload.teamId(resolveTeam(player)),
                    CsgoConfig.get().roundsToWin,
                    bomb == null ? 0 : bomb.getX(),
                    bomb == null ? 0 : bomb.getY(),
                    bomb == null ? 0 : bomb.getZ()
            );
            CsgoNetworking.send(player, payload);
        }
    }

    /** Elenco dei compagni ancora vivi, usato dallo spettatore. */
    public static List<ServerPlayer> livingTeammates(ServerPlayer player) {
        List<ServerPlayer> result = new ArrayList<>();
        MinecraftServer server = player.level().getServer();
        if (server == null) return result;
        String team = resolveTeam(player);
        if (team.equals("NEUTRAL")) return result;

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player) continue;
            if (isPlayerInPlay(other) && resolveTeam(other).equals(team)) {
                result.add(other);
            }
        }
        return result;
    }
}

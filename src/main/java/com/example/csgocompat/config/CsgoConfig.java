package com.example.csgocompat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configurazione della mod, salvata come JSON leggibile in &lt;mondo&gt;/csgo_mc/config.json.
 * Tutti i valori di gameplay che prima erano hardcoded vivono qui.
 */
public class CsgoConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("csgo_mc/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static CsgoConfig instance = new CsgoConfig();

    // --- Round ---
    public int roundTimeSeconds = 115;
    public int roundEndDelaySeconds = 5;
    public int roundsToWin = 13;
    /** 0 = disabilitato. Round dopo il quale le squadre si scambiano di lato. */
    public int halfTimeRound = 12;

    /** Secondi di freeze time a inizio round (0 = disabilitato). */
    public int freezeTimeSeconds = 10;

    // --- Bomba ---
    public int bombTimerSeconds = 40;
    public int botPlantTicks = 64;
    public int botDefuseTicks = 100;
    /** Tempo di piazzamento per il giocatore: 64 tick = 3,2s, come in CS. */
    public int playerPlantTicks = 64;
    public int playerDefuseTicks = 200;
    public int playerDefuseTicksWithKit = 100;
    /** Plant e defuse si annullano se chi li esegue viene colpito. */
    public boolean plantCancelsOnDamage = true;
    /** Distanza massima (in blocchi) di cui ci si può spostare senza annullare plant/defuse. */
    public double plantCancelDistance = 1.0;
    public double bombDamage = 200.0;
    public double bombRadius = 20.0;
    public boolean bombDestroysBlocks = false;
    /** Raggio orizzontale attorno al blocco site, usato solo se il site non ha un'area definita. */
    public int plantRadiusFromSite = 10;
    /**
     * Il blocco Bomb Site sta a filo del pavimento, quindi la C4 va piazzata sopra di esso.
     * Numero di blocchi sopra la quota del site entro cui è consentito piazzare (1 = solo Y+1).
     */
    public int plantMaxYAboveSite = 1;
    /** Richiede un blocco solido sotto la C4: niente bombe a mezz'aria. */
    public boolean plantRequiresSolidGround = true;
    /** Se un giocatore è nei T, la C4 viene consegnata a lui invece che a un bot. */
    public boolean giveBombToPlayer = true;

    // --- Squadre ---
    public int botsT = 5;
    public int botsCT = 5;
    public boolean friendlyFire = false;
    public boolean clearInventoryOnJoin = true;
    /** Se true la radio è visibile solo ai membri della propria squadra. */
    public boolean radioToOwnTeamOnly = true;
    public int radioCooldownTicks = 100;

    // --- Warmup ---
    public boolean warmupEnabled = true;
    public int warmupSeconds = 60;
    public int warmupRespawnDelayTicks = 60;

    // --- Bot / AI ---
    public int botSquadSearchRadius = 64;
    public double botFollowRange = 128.0;
    /**
     * Danno per proiettile prima del moltiplicatore di difficoltà.
     * Con gli invulnerability frame ora rispettati, una raffica arriva davvero a segno: 5.0 dà il
     * classico TTK da 4 colpi al corpo su un giocatore da 20 HP.
     */
    public float fallbackWeaponDamage = 5.0f;
    /** Se true usa il danno dichiarato dall'arma Point-Blank invece di quello qui sopra. */
    public boolean usePointBlankDamage = false;
    /** Fattore di scala applicato al danno Point-Blank (i suoi valori sono tarati per i giocatori). */
    public float pointBlankDamageScale = 0.35f;
    public boolean botsCanHeadshot = true;
    public float headshotMultiplier = 2.5f;
    /** Ogni bot riceve un piccolo scarto casuale su reazione e precisione: meno robotici. */
    public boolean botVariance = true;

    // --- HUD / feedback ---
    /** Contorno di particelle sull'area del Bomb Site per chi porta la C4. */
    public boolean showSiteOutlineToCarrier = true;
    public int siteOutlineDurationTicks = 200;
    /** Bussola verso la C4 piazzata nell'HUD dei CT. */
    public boolean showBombIndicator = true;
    public boolean showKillFeed = true;

    // --- Difficoltà ---
    public DifficultySettings easy = new DifficultySettings(15.0, 0.25, 6, 10, 0.060, 0.70f);
    public DifficultySettings normal = new DifficultySettings(20.0, 0.28, 3, 5, 0.020, 1.00f);
    public DifficultySettings hard = new DifficultySettings(26.0, 0.32, 1, 2, 0.005, 1.25f);
    public DifficultySettings unfair = new DifficultySettings(30.0, 0.35, 0, 0, 0.000, 1.60f);

    public static class DifficultySettings {
        public double health;
        public double speed;
        public int reactionTicksMin;
        public int reactionTicksMax;
        public double spread;
        public float damageMultiplier;

        public DifficultySettings() {
        }

        public DifficultySettings(double health, double speed, int reactionMin, int reactionMax,
                                  double spread, float damageMultiplier) {
            this.health = health;
            this.speed = speed;
            this.reactionTicksMin = reactionMin;
            this.reactionTicksMax = reactionMax;
            this.spread = spread;
            this.damageMultiplier = damageMultiplier;
        }
    }

    public static CsgoConfig get() {
        return instance;
    }

    public int roundTimeTicks() {
        return Math.max(1, roundTimeSeconds) * 20;
    }

    public int roundEndDelayTicks() {
        return Math.max(1, roundEndDelaySeconds) * 20;
    }

    public int bombTimerTicks() {
        return Math.max(1, bombTimerSeconds) * 20;
    }

    public int warmupTicks() {
        return Math.max(1, warmupSeconds) * 20;
    }

    public static Path configDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("csgo_mc");
    }

    public static void load(MinecraftServer server) {
        Path file = configDir(server).resolve("config.json");
        if (!Files.exists(file)) {
            instance = new CsgoConfig();
            save(server);
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CsgoConfig loaded = GSON.fromJson(reader, CsgoConfig.class);
            if (loaded != null) {
                loaded.sanitize();
                instance = loaded;
            }
        } catch (Exception e) {
            LOGGER.error("Could not read config.json, using defaults", e);
            instance = new CsgoConfig();
        }
    }

    public static void save(MinecraftServer server) {
        Path dir = configDir(server);
        try {
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(dir.resolve("config.json"), StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Could not write config.json", e);
        }
    }

    /** Rimpiazza eventuali sezioni mancanti in un config scritto a mano. */
    private void sanitize() {
        CsgoConfig def = new CsgoConfig();
        if (easy == null) easy = def.easy;
        if (normal == null) normal = def.normal;
        if (hard == null) hard = def.hard;
        if (unfair == null) unfair = def.unfair;
        if (roundsToWin < 1) roundsToWin = def.roundsToWin;
        if (botsT < 0) botsT = 0;
        if (botsCT < 0) botsCT = 0;
    }
}

package com.example.csgocompat.manager;

import com.example.csgocompat.config.CsgoConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Salvataggio e caricamento delle mappe (spawn, bot, bomb site) in
 * &lt;mondo&gt;/csgo_mc/maps/&lt;nome&gt;.json.
 *
 * <p>La mappa attiva viene inoltre salvata in {@code maps/_active.json} e ricaricata all'avvio del
 * server, così spawn e site non si perdono più al riavvio.
 */
public final class MapStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("csgo_mc/maps");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ACTIVE = "_active";

    private MapStorage() {
    }

    // ------------------------------------------------------------------ modello

    public static class MapData {
        public String name;
        public int[] spawnT;
        public int[] spawnCT;
        public int botsT = -1;
        public int botsCT = -1;
        public String difficulty;
        public List<SiteData> sites = new ArrayList<>();
    }

    public static class SiteData {
        public String name;
        public int[] pos;
        public int[] corner1;
        public int[] corner2;
    }

    private static int[] toArray(BlockPos pos) {
        return pos == null ? null : new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }

    private static BlockPos toPos(int[] arr) {
        return arr == null || arr.length != 3 ? null : new BlockPos(arr[0], arr[1], arr[2]);
    }

    // ------------------------------------------------------------------ percorsi

    private static Path mapsDir(MinecraftServer server) {
        return CsgoConfig.configDir(server).resolve("maps");
    }

    /** Impedisce che un nome mappa esca dalla cartella maps/. */
    private static String sanitize(String name) {
        if (name == null) return null;
        String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
        return clean.isEmpty() ? null : clean;
    }

    // ------------------------------------------------------------------ API

    public static MapData capture(String name) {
        MapData data = new MapData();
        data.name = name;
        data.spawnT = toArray(ArenaState.getSpawnT());
        data.spawnCT = toArray(ArenaState.getSpawnCT());
        data.botsT = ArenaState.getBotsT();
        data.botsCT = ArenaState.getBotsCT();
        data.difficulty = CsgoMatchState.currentDifficulty.name();
        for (SiteRegistry.Site site : SiteRegistry.all()) {
            SiteData sd = new SiteData();
            sd.name = site.name;
            sd.pos = toArray(site.pos);
            sd.corner1 = toArray(site.corner1);
            sd.corner2 = toArray(site.corner2);
            data.sites.add(sd);
        }
        return data;
    }

    public static void apply(MapData data) {
        if (data == null) return;
        ArenaState.setSpawnT(toPos(data.spawnT));
        ArenaState.setSpawnCT(toPos(data.spawnCT));
        if (data.botsT >= 0) ArenaState.setBotsT(data.botsT);
        if (data.botsCT >= 0) ArenaState.setBotsCT(data.botsCT);
        if (data.difficulty != null) {
            try {
                CsgoMatchState.currentDifficulty =
                        CsgoMatchState.MatchDifficulty.valueOf(data.difficulty.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }

        List<SiteRegistry.Site> sites = new ArrayList<>();
        if (data.sites != null) {
            for (SiteData sd : data.sites) {
                BlockPos pos = toPos(sd.pos);
                if (pos == null) continue;
                SiteRegistry.Site site = new SiteRegistry.Site(pos, sd.name == null ? "?" : sd.name);
                site.corner1 = toPos(sd.corner1);
                site.corner2 = toPos(sd.corner2);
                sites.add(site);
            }
        }
        SiteRegistry.replaceAll(sites);
        ArenaState.setLoadedMapName(data.name);
    }

    public static boolean save(MinecraftServer server, String rawName) {
        String name = sanitize(rawName);
        if (name == null) return false;
        try {
            Path dir = mapsDir(server);
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(dir.resolve(name + ".json"), StandardCharsets.UTF_8)) {
                GSON.toJson(capture(name), writer);
            }
            ArenaState.setLoadedMapName(name);
            return true;
        } catch (Exception e) {
            LOGGER.error("Could not save map {}", name, e);
            return false;
        }
    }

    public static boolean load(MinecraftServer server, String rawName) {
        String name = sanitize(rawName);
        if (name == null) return false;
        Path file = mapsDir(server).resolve(name + ".json");
        if (!Files.exists(file)) return false;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MapData data = GSON.fromJson(reader, MapData.class);
            if (data == null) return false;
            data.name = name;
            apply(data);
            return true;
        } catch (Exception e) {
            LOGGER.error("Could not load map {}", name, e);
            return false;
        }
    }

    public static boolean delete(MinecraftServer server, String rawName) {
        String name = sanitize(rawName);
        if (name == null || ACTIVE.equals(name)) return false;
        try {
            return Files.deleteIfExists(mapsDir(server).resolve(name + ".json"));
        } catch (Exception e) {
            LOGGER.error("Could not delete map {}", name, e);
            return false;
        }
    }

    public static List<String> list(MinecraftServer server) {
        Path dir = mapsDir(server);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .filter(n -> !ACTIVE.equals(n))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Persiste l'arena corrente (chiamato ad ogni modifica di spawn/site). */
    public static void saveActive(MinecraftServer server) {
        if (server == null) return;
        try {
            Path dir = mapsDir(server);
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(dir.resolve(ACTIVE + ".json"), StandardCharsets.UTF_8)) {
                GSON.toJson(capture(ArenaState.getLoadedMapName()), writer);
            }
        } catch (Exception e) {
            LOGGER.error("Could not save the current arena state", e);
        }
    }

    public static void loadActive(MinecraftServer server) {
        Path file = mapsDir(server).resolve(ACTIVE + ".json");
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            apply(GSON.fromJson(reader, MapData.class));
        } catch (Exception e) {
            LOGGER.error("Could not restore the current arena state", e);
        }
    }
}

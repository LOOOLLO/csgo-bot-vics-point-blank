package com.example.csgocompat.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro dei Bomb Site.
 *
 * <p>È la fonte di verità: prima la lista veniva ricostruita da {@code BlockEntity#clearRemoved}
 * e svuotata da {@code setRemoved}, che Minecraft chiama anche allo <i>scaricamento</i> dei chunk.
 * Il risultato era che i site sparivano quando nessuno era nei paraggi e che i nomi (derivati
 * dall'indice nella lista) cambiavano da soli. Qui i site sopravvivono allo unload e il nome viene
 * assegnato una volta sola.
 */
public final class SiteRegistry {

    public static class Site {
        public final BlockPos pos;
        public String name;
        public BlockPos corner1;
        public BlockPos corner2;

        public Site(BlockPos pos, String name) {
            this.pos = pos.immutable();
            this.name = name;
        }

        public boolean hasArea() {
            return corner1 != null && corner2 != null;
        }
    }

    private static final List<Site> SITES = new CopyOnWriteArrayList<>();

    private SiteRegistry() {
    }

    public static List<Site> all() {
        return Collections.unmodifiableList(SITES);
    }

    public static int size() {
        return SITES.size();
    }

    public static boolean isEmpty() {
        return SITES.isEmpty();
    }

    public static Site byPos(BlockPos pos) {
        if (pos == null) return null;
        for (Site site : SITES) {
            if (site.pos.equals(pos)) return site;
        }
        return null;
    }

    public static Site byIndex(int index) {
        if (index < 0 || index >= SITES.size()) return null;
        return SITES.get(index);
    }

    /** Registra il site se assente e restituisce sempre l'istanza corrente. Idempotente. */
    public static Site register(BlockPos pos) {
        Site existing = byPos(pos);
        if (existing != null) return existing;
        Site site = new Site(pos, nextFreeName());
        SITES.add(site);
        return site;
    }

    public static void remove(BlockPos pos) {
        SITES.removeIf(site -> site.pos.equals(pos));
    }

    public static void clear() {
        SITES.clear();
    }

    /** Prima lettera libera fra A e Z, poi "Site 27", "Site 28", ... */
    private static String nextFreeName() {
        for (char c = 'A'; c <= 'Z'; c++) {
            String candidate = String.valueOf(c);
            boolean taken = false;
            for (Site site : SITES) {
                if (candidate.equalsIgnoreCase(site.name)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return candidate;
        }
        return String.valueOf(SITES.size() + 1);
    }

    public static Site nearest(Vec3 pos) {
        if (pos == null || SITES.isEmpty()) return null;
        Site best = null;
        double bestDist = Double.MAX_VALUE;
        for (Site site : SITES) {
            double dist = site.pos.distToCenterSqr(pos.x, pos.y, pos.z);
            if (dist < bestDist) {
                bestDist = dist;
                best = site;
            }
        }
        return best;
    }

    public static String nearestName(Vec3 pos) {
        Site site = nearest(pos);
        return site == null ? "Area" : "Site " + site.name;
    }

    /**
     * Punto di piazzamento all'interno del site: X/Z casuali nell'area, Y fissa a un blocco sopra la
     * quota del site.
     *
     * <p>Il blocco Bomb Site è conficcato nel pavimento, quindi Y+1 è il piano calpestabile: è lì
     * che va piazzata la bomba, ed è la stessa quota che {@code C4BombBlock#canPlantAt} accetta dai
     * giocatori. La versione precedente scendeva dalla cima dell'area cercando il primo blocco
     * libero, e su mappe a più piani finiva sul livello sbagliato.
     */
    public static BlockPos randomTargetInside(Level level, Site site) {
        if (site == null) return null;

        int plantY = site.pos.getY() + 1;
        if (!site.hasArea()) {
            return new BlockPos(site.pos.getX(), plantY, site.pos.getZ());
        }

        int minX = Math.min(site.corner1.getX(), site.corner2.getX());
        int minZ = Math.min(site.corner1.getZ(), site.corner2.getZ());
        int maxX = Math.max(site.corner1.getX(), site.corner2.getX());
        int maxZ = Math.max(site.corner1.getZ(), site.corner2.getZ());

        for (int attempt = 0; attempt < 12; attempt++) {
            int rx = minX + level.random.nextInt(maxX - minX + 1);
            int rz = minZ + level.random.nextInt(maxZ - minZ + 1);
            BlockPos candidate = new BlockPos(rx, plantY, rz);
            if (level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.below()).isSolidRender()) {
                return candidate;
            }
        }
        return new BlockPos(site.pos.getX(), plantY, site.pos.getZ());
    }

    /** Snapshot modificabile, per la serializzazione. */
    public static List<Site> snapshot() {
        return new ArrayList<>(SITES);
    }

    public static void replaceAll(List<Site> sites) {
        SITES.clear();
        if (sites != null) SITES.addAll(sites);
    }
}

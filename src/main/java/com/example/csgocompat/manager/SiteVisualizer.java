package com.example.csgocompat.manager;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.config.CsgoConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Contorno di particelle sulle aree dei Bomb Site.
 *
 * <p>Il blocco site è conficcato nel pavimento, quindi in partita la zona è invisibile. Il contorno
 * viene mostrato a chi porta la C4 (così sa dove andare) e, su richiesta, a chiunque via
 * {@code /csgo site show}.
 */
public final class SiteVisualizer {

    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final int MAX_POINTS_PER_SITE = 96;
    /** Rosso C4 per il contorno, verde per il blocco site stesso. */
    private static final DustParticleOptions OUTLINE = new DustParticleOptions(0xFF4436, 1.0f);
    private static final DustParticleOptions CENTER = new DustParticleOptions(0x36FF6B, 1.2f);

    private static final Map<UUID, Integer> SHOW_TIMERS = new HashMap<>();

    private SiteVisualizer() {
    }

    public static void onPlayerDisconnect(UUID uuid) {
        SHOW_TIMERS.remove(uuid);
    }

    /** Mostra i site a un giocatore per un certo numero di tick. */
    public static void showFor(ServerPlayer player, int ticks) {
        SHOW_TIMERS.put(player.getUUID(), ticks);
    }

    public static void tick(MinecraftServer server) {
        if (SiteRegistry.isEmpty()) return;
        if (server.getTickCount() % REFRESH_INTERVAL_TICKS != 0) {
            decayTimers();
            return;
        }

        CsgoConfig cfg = CsgoConfig.get();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean explicitlyRequested = SHOW_TIMERS.getOrDefault(player.getUUID(), 0) > 0;
            boolean carriesBomb = cfg.showSiteOutlineToCarrier
                    && "T".equals(CsgoMatchState.resolveTeam(player))
                    && player.getInventory().contains(stack -> stack.is(CsGoCompatMod.C4_BOMB.asItem()));

            if (!explicitlyRequested && !carriesBomb) continue;
            if (!(player.level() instanceof ServerLevel level)) continue;

            for (SiteRegistry.Site site : SiteRegistry.all()) {
                drawSite(level, player, site);
            }
        }

        decayTimers();
    }

    private static void decayTimers() {
        SHOW_TIMERS.entrySet().removeIf(entry -> {
            int left = entry.getValue() - 1;
            entry.setValue(left);
            return left <= 0;
        });
    }

    private static void drawSite(ServerLevel level, ServerPlayer player, SiteRegistry.Site site) {
        double y = site.pos.getY() + 1.1;

        // Marcatore sul blocco del site.
        sendPoint(level, player, CENTER, site.pos.getX() + 0.5, y + 0.4, site.pos.getZ() + 0.5);

        if (!site.hasArea()) {
            // Senza area definita si disegna un cerchietto attorno al blocco.
            for (int i = 0; i < 16; i++) {
                double angle = (Math.PI * 2 * i) / 16.0;
                sendPoint(level, player, OUTLINE,
                        site.pos.getX() + 0.5 + Math.cos(angle) * 2.0,
                        y,
                        site.pos.getZ() + 0.5 + Math.sin(angle) * 2.0);
            }
            return;
        }

        int minX = Math.min(site.corner1.getX(), site.corner2.getX());
        int minZ = Math.min(site.corner1.getZ(), site.corner2.getZ());
        int maxX = Math.max(site.corner1.getX(), site.corner2.getX()) + 1;
        int maxZ = Math.max(site.corner1.getZ(), site.corner2.getZ()) + 1;

        int spanX = maxX - minX;
        int spanZ = maxZ - minZ;
        int perimeter = 2 * (spanX + spanZ);
        int step = Math.max(1, perimeter / MAX_POINTS_PER_SITE);

        for (int x = minX; x <= maxX; x += step) {
            sendPoint(level, player, OUTLINE, x, y, minZ);
            sendPoint(level, player, OUTLINE, x, y, maxZ);
        }
        for (int z = minZ; z <= maxZ; z += step) {
            sendPoint(level, player, OUTLINE, minX, y, z);
            sendPoint(level, player, OUTLINE, maxX, y, z);
        }
    }

    private static void sendPoint(ServerLevel level, ServerPlayer player, DustParticleOptions options,
                                  double x, double y, double z) {
        // Solo al giocatore interessato: il contorno non deve rivelare nulla agli avversari.
        level.sendParticles(player, options, true, false, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Posizione del site più vicino, usata dal comando per un feedback testuale. */
    public static BlockPos nearestSitePos(ServerPlayer player) {
        SiteRegistry.Site site = SiteRegistry.nearest(player.position());
        return site == null ? null : site.pos;
    }
}

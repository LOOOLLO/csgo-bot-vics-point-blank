package com.example.csgocompat.manager;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.block.C4BombBlock;
import com.example.csgocompat.config.CsgoConfig;
import com.example.csgocompat.util.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Piazzamento della C4 da parte dei giocatori, con tempo di canalizzazione.
 *
 * <p>Prima il giocatore appoggiava semplicemente il blocco e la bomba era piazzata all'istante,
 * mentre i bot impiegavano 3,2 secondi con barra di avanzamento: un'asimmetria enorme. Ora vale la
 * stessa regola per tutti, e il plant si annulla se ci si muove, si viene colpiti o si smette di
 * tenere premuto.
 *
 * <p>Il "tenere premuto" si rileva come per il defuse: Minecraft ripete {@code useOn} ogni pochi
 * tick finché il tasto è giù, quindi basta misurare quanto tempo è passato dall'ultima interazione.
 */
public final class PlantingManager {

    private static final int INTERACTION_TIMEOUT_TICKS = 10;

    private static class Session {
        BlockPos pos;
        Vec3 origin;
        int progress;
        long lastInteraction;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private PlantingManager() {
    }

    public static void onPlayerDisconnect(UUID uuid) {
        SESSIONS.remove(uuid);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    public static boolean isPlanting(UUID uuid) {
        return SESSIONS.containsKey(uuid);
    }

    /** Chiamato ad ogni click destro con la C4 in mano. */
    public static void beginOrRefresh(ServerPlayer player, BlockPos pos) {
        C4BombBlock.PlantCheck check = C4BombBlock.canPlantAt(player.level(), pos);
        if (!check.allowed()) {
            player.displayClientMessage(Component.literal(check.reason()), true);
            SESSIONS.remove(player.getUUID());
            return;
        }

        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.pos.equals(pos)) {
            session = new Session();
            session.pos = pos.immutable();
            session.origin = player.position();
            session.progress = 0;
            SESSIONS.put(player.getUUID(), session);
            player.level().playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.BLOCKS, 0.7f, 1.4f);
        }
        session.lastInteraction = player.level().getGameTime();
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;

        CsgoConfig cfg = CsgoConfig.get();
        int required = Math.max(1, cfg.playerPlantTicks);
        double cancelDistSq = cfg.plantCancelDistance * cfg.plantCancelDistance;

        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();

            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            long gameTime = player.level().getGameTime();
            String cancelReason = null;

            if (gameTime - session.lastInteraction > INTERACTION_TIMEOUT_TICKS) {
                cancelReason = "§7Plant interrupted.";
            } else if (!player.getMainHandItem().is(CsGoCompatMod.C4_BOMB.asItem())) {
                cancelReason = "§7Plant interrupted — the C4 is no longer in your hand.";
            } else if (player.position().distanceToSqr(session.origin) > cancelDistSq) {
                cancelReason = "§cPlant cancelled — you moved!";
            } else if (cfg.plantCancelsOnDamage && player.hurtTime > 0) {
                cancelReason = "§cPlant cancelled — you were hit!";
            } else if (!C4BombBlock.canPlantAt(player.level(), session.pos).allowed()) {
                cancelReason = "§cPlant cancelled — that spot is no longer valid.";
            }

            if (cancelReason != null) {
                player.displayClientMessage(Component.literal(cancelReason), true);
                it.remove();
                continue;
            }

            session.progress++;

            if (session.progress % 8 == 0) {
                player.level().playSound(null, session.pos, SoundEvents.UI_BUTTON_CLICK.value(),
                        SoundSource.BLOCKS, 0.8f, 1.5f);
            }

            int percent = Math.min(100, (int) ((session.progress / (float) required) * 100));
            int filled = percent / 5;
            player.displayClientMessage(Component.literal(
                    "§cPlanting C4 §7[" + "=".repeat(filled) + " ".repeat(20 - filled) + "§7] §e" + percent + "%"), true);

            if (session.progress >= required) {
                complete(player, session);
                it.remove();
            }
        }
    }

    private static void complete(ServerPlayer player, Session session) {
        ServerLevel level = (ServerLevel) player.level();
        level.setBlock(session.pos, CsGoCompatMod.C4_BOMB.defaultBlockState(), 3);

        // Consuma una sola C4 dall'inventario.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(CsGoCompatMod.C4_BOMB.asItem())) {
                stack.shrink(1);
                break;
            }
        }

        BombRegistry.add(level, session.pos);
        level.playSound(null, session.pos, ModSounds.C4_PLANT, SoundSource.BLOCKS, 1.4f, 1.0f);
        if (level.getServer() != null) {
            CsgoMatchState.broadcast(level.getServer(), "§c§l[C4] The bomb has been planted!");
        }
    }
}

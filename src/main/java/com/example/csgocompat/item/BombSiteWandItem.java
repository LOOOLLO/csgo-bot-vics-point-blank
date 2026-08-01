package com.example.csgocompat.item;

import com.example.csgocompat.block.BombSiteBlock;
import com.example.csgocompat.block.entity.BombSiteBlockEntity;
import com.example.csgocompat.manager.MapStorage;
import com.example.csgocompat.manager.SiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BombSiteWandItem extends Item {

    public static class WandSession {
        public BlockPos activeSite = null;
        public BlockPos pos1 = null;
    }

    public static final Map<UUID, WandSession> SESSIONS = new ConcurrentHashMap<>();

    public BombSiteWandItem(Properties settings) {
        super(settings);
    }

    public static void clearSession(UUID uuid) {
        SESSIONS.remove(uuid);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.SUCCESS;

        BlockPos clickPos = context.getClickedPos();
        WandSession session = SESSIONS.computeIfAbsent(player.getUUID(), k -> new WandSession());

        if (world.getBlockState(clickPos).getBlock() instanceof BombSiteBlock) {
            session.activeSite = clickPos.immutable();
            session.pos1 = null;
            SiteRegistry.Site site = SiteRegistry.register(clickPos);
            player.displayClientMessage(Component.literal(
                    "§a[Wand] Bomb Site §b" + site.name + " §aselected at " + clickPos.toShortString()
                            + ". Next click sets Pos 1."), false);
            return InteractionResult.SUCCESS;
        }

        if (session.activeSite == null) {
            player.displayClientMessage(Component.literal(
                    "§c[Wand] Click a Bomb Site block first to select it!"), false);
            return InteractionResult.SUCCESS;
        }

        if (session.pos1 == null) {
            session.pos1 = clickPos.immutable();
            player.displayClientMessage(Component.literal(
                    "§e[Wand] Pos 1 = " + clickPos.toShortString()
                            + ". Next click sets Pos 2 and saves the area."), false);
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = world.getBlockEntity(session.activeSite);
        if (be instanceof BombSiteBlockEntity siteEntity) {
            // Il nome viene assegnato una sola volta dal registro: prima veniva ricalcolato
            // dall'indice nella lista e cambiava da solo quando i chunk si scaricavano.
            SiteRegistry.Site site = SiteRegistry.register(session.activeSite);
            site.corner1 = session.pos1;
            site.corner2 = clickPos.immutable();

            siteEntity.pos1 = site.corner1;
            siteEntity.pos2 = site.corner2;
            siteEntity.siteName = site.name;
            siteEntity.setChanged();

            if (world.getServer() != null) {
                MapStorage.saveActive(world.getServer());
            }

            player.displayClientMessage(Component.literal(
                    "§b[Wand] Bomb Site " + site.name + " area saved! ("
                            + site.corner1.toShortString() + " → " + site.corner2.toShortString() + ")"), false);
        } else {
            player.displayClientMessage(Component.literal(
                    "§c[Wand] The selected Bomb Site is no longer valid!"), false);
            session.activeSite = null;
        }

        session.pos1 = null;
        return InteractionResult.SUCCESS;
    }
}

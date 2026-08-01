package com.example.csgocompat.item;

import com.example.csgocompat.manager.PlantingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Item della C4.
 *
 * <p>Non piazza il blocco direttamente: delega a {@link PlantingManager}, che applica il tempo di
 * canalizzazione. Serve un item dedicato perché {@code BlockItem#useOn} piazzerebbe all'istante.
 */
public class C4BombItem extends BlockItem {

    public C4BombItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            // CONSUME lato client evita l'animazione di piazzamento e mantiene la ripetizione
            // del tasto destro, che è ciò che alimenta il progresso del plant.
            return InteractionResult.CONSUME;
        }

        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.CONSUME;
        }

        BlockPos target = new BlockPlaceContext(context).getClickedPos();
        PlantingManager.beginOrRefresh(player, target);
        return InteractionResult.CONSUME;
    }
}

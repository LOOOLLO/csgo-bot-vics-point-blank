package com.example.csgocompat.block;

import com.example.csgocompat.block.entity.BombSiteBlockEntity;
import com.example.csgocompat.manager.SiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BombSiteBlock extends Block implements EntityBlock {

    public BombSiteBlock(Properties settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BombSiteBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        if (world instanceof ServerLevel) {
            SiteRegistry.register(pos);
        }
    }

    /**
     * Deregistra il site solo quando il blocco viene davvero rimosso.
     * Lo scaricamento del chunk non deve più cancellare i site registrati.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        SiteRegistry.remove(pos);
    }
}

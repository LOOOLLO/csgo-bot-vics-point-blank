package com.example.csgocompat.block;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.block.entity.C4BombBlockEntity;
import com.example.csgocompat.config.CsgoConfig;
import com.example.csgocompat.manager.BombRegistry;
import com.example.csgocompat.manager.CsgoMatchState;
import com.example.csgocompat.manager.SiteRegistry;
import com.example.csgocompat.util.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class C4BombBlock extends BaseEntityBlock {

    public static final MapCodec<C4BombBlock> CODEC = simpleCodec(C4BombBlock::new);
    public static final BooleanProperty DEFUSED = BooleanProperty.create("defused");

    protected static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 2.0D, 12.0D, 4.0D, 14.0D);

    public C4BombBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(DEFUSED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DEFUSED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new C4BombBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(DEFUSED)) {
            return InteractionResult.SUCCESS;
        }

        if (!world.isClientSide()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof C4BombBlockEntity c4) {
                String team = CsgoMatchState.resolveTeam(player);
                if (team.equals("CT")) {
                    c4.startDefusal(player);
                    c4.lastInteractionTime = world.getGameTime();
                    return InteractionResult.CONSUME;
                }
                player.displayClientMessage(
                        Component.literal("§cOnly Counter-Terrorists can defuse the C4!"), false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        PlantCheck check = canPlantAt(ctx.getLevel(), ctx.getClickedPos());
        if (!check.allowed()) {
            if (!ctx.getLevel().isClientSide() && ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(Component.literal(check.reason()), true);
            }
            return null;
        }
        return this.defaultBlockState();
    }

    public record PlantCheck(boolean allowed, String reason) {
        static final PlantCheck OK = new PlantCheck(true, "");
    }

    /**
     * Regola di piazzamento della C4.
     *
     * <p>Il blocco Bomb Site sta a filo del pavimento, quindi la bomba va appoggiata <b>sopra</b> di
     * esso: la posizione deve stare nell'area del site sul piano XZ e a non più di
     * {@code plantMaxYAboveSite} blocchi sopra la quota del site (di default esattamente Y+1), con
     * terreno solido sotto. Prima bastava essere entro 10 blocchi in qualsiasi direzione, quindi si
     * poteva piazzare anche a mezz'aria o su un tetto sopra il site.
     */
    public static PlantCheck canPlantAt(BlockGetter level, BlockPos pos) {
        if (SiteRegistry.isEmpty()) {
            return new PlantCheck(false, "§cNo Bomb Site is registered on this map!");
        }

        CsgoConfig cfg = CsgoConfig.get();
        int maxAbove = Math.max(1, cfg.plantMaxYAboveSite);
        boolean matchedColumn = false;

        for (SiteRegistry.Site site : SiteRegistry.all()) {
            if (!withinFootprint(site, pos, cfg.plantRadiusFromSite)) continue;
            matchedColumn = true;

            int deltaY = pos.getY() - site.pos.getY();
            if (deltaY >= 1 && deltaY <= maxAbove) {
                if (cfg.plantRequiresSolidGround && !level.getBlockState(pos.below()).isSolidRender()) {
                    return new PlantCheck(false, "§cThe C4 must rest on solid ground!");
                }
                return PlantCheck.OK;
            }
        }

        if (matchedColumn) {
            return new PlantCheck(false, "§cPlant the C4 on the ground, on top of the Bomb Site!");
        }
        return new PlantCheck(false, "§cYou must plant the C4 inside a Bomb Site!");
    }

    /** Impronta orizzontale del site: l'area della wand se definita, altrimenti un raggio XZ. */
    private static boolean withinFootprint(SiteRegistry.Site site, BlockPos pos, int fallbackRadius) {
        if (site.hasArea()) {
            int minX = Math.min(site.corner1.getX(), site.corner2.getX());
            int minZ = Math.min(site.corner1.getZ(), site.corner2.getZ());
            int maxX = Math.max(site.corner1.getX(), site.corner2.getX());
            int maxZ = Math.max(site.corner1.getZ(), site.corner2.getZ());
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        int radius = Math.max(1, fallbackRadius);
        int dx = pos.getX() - site.pos.getX();
        int dz = pos.getZ() - site.pos.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        // Senza il controllo su DEFUSED, il setBlock del disinnesco rimetteva la bomba nel registro.
        if (world instanceof ServerLevel serverLevel && !state.getValue(DEFUSED)) {
            BombRegistry.add(serverLevel, pos);
        }
    }

    /** L'annuncio va qui: getStateForPlacement viene chiamato anche quando il piazzamento poi fallisce. */
    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (world.isClientSide()) return;

        world.playSound(null, pos, ModSounds.C4_PLANT, SoundSource.BLOCKS, 1.4f, 1.0f);
        world.players().forEach(p -> p.displayClientMessage(
                Component.literal("§c§l[C4] The bomb has been planted!"), false));
    }

    /**
     * Hook di rimozione reale. Prima la deregistrazione stava in {@code BlockEntity#setRemoved},
     * che Minecraft chiama anche allo scaricamento del chunk: la bomba spariva dal registro pur
     * restando piazzata nel mondo.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BombRegistry.remove(level, pos);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, CsGoCompatMod.C4_BOMB_ENTITY_TYPE, C4BombBlockEntity::tick);
    }
}

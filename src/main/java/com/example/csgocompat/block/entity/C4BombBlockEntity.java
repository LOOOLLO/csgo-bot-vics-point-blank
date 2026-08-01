package com.example.csgocompat.block.entity;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.block.C4BombBlock;
import com.example.csgocompat.config.CsgoConfig;
import com.example.csgocompat.item.ModItems;
import com.example.csgocompat.manager.BombRegistry;
import com.example.csgocompat.manager.CsgoMatchState;
import com.example.csgocompat.util.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class C4BombBlockEntity extends BlockEntity {

    private int ticksRemaining = -1;
    private boolean defused = false;
    private int beepCooldown = 20;
    private int defuseProgress = 0;

    /** UUID invece del riferimento diretto: il vecchio campo Player teneva vivo un giocatore disconnesso. */
    private UUID defuserId = null;
    private Vec3 defuserOrigin = null;
    public long lastInteractionTime = 0;

    private int totalTicks = -1;

    public C4BombBlockEntity(BlockPos pos, BlockState state) {
        super(CsGoCompatMod.C4_BOMB_ENTITY_TYPE, pos, state);
    }

    public int getTicksRemaining() {
        return Math.max(0, ticksRemaining);
    }

    public boolean isDefused() {
        return defused;
    }

    private int totalTicks() {
        if (totalTicks <= 0) {
            totalTicks = CsgoConfig.get().bombTimerTicks();
        }
        return totalTicks;
    }

    public static void tick(Level world, BlockPos pos, BlockState state, C4BombBlockEntity blockEntity) {
        boolean stateDefused = state.hasProperty(C4BombBlock.DEFUSED) && state.getValue(C4BombBlock.DEFUSED);

        if (world.isClientSide()) {
            // Una bomba disinnescata non deve continuare a fumare.
            if (!stateDefused && world.random.nextFloat() < 0.4f) {
                world.addParticle(ParticleTypes.FLAME,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0.05, 0);
            }
            return;
        }

        if (blockEntity.defused || stateDefused) {
            return;
        }

        if (blockEntity.ticksRemaining < 0) {
            blockEntity.ticksRemaining = blockEntity.totalTicks();
        }

        blockEntity.ticksRemaining--;
        blockEntity.tickDefusal(world, pos);

        if (blockEntity.ticksRemaining <= 0) {
            blockEntity.explode();
            return;
        }

        blockEntity.tickBeep(world, pos);
    }

    private void tickDefusal(Level world, BlockPos pos) {
        if (defuserId == null) return;

        CsgoConfig cfg = CsgoConfig.get();
        Player defuser = world.getPlayerByUUID(defuserId);

        String cancelReason = null;
        if (defuser == null || !defuser.isAlive()) {
            cancelReason = "";
        } else if (world.getGameTime() - lastInteractionTime > 15) {
            cancelReason = "§7Defuse interrupted.";
        } else if (defuser.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 36.0) {
            cancelReason = "§cDefuse cancelled — too far away!";
        } else if (defuserOrigin != null
                && defuser.position().distanceToSqr(defuserOrigin) > cfg.plantCancelDistance * cfg.plantCancelDistance) {
            // Come per il plant: muoversi annulla il defuse.
            cancelReason = "§cDefuse cancelled — you moved!";
        } else if (cfg.plantCancelsOnDamage && defuser.hurtTime > 0) {
            cancelReason = "§cDefuse cancelled — you were hit!";
        }

        if (cancelReason != null) {
            if (defuser != null && !cancelReason.isEmpty()) {
                defuser.displayClientMessage(Component.literal(cancelReason), true);
            }
            defuserId = null;
            defuserOrigin = null;
            defuseProgress = 0;
            return;
        }
        boolean hasKit = defuser.getOffhandItem().is(ModItems.DEFUSAL_KIT)
                || defuser.getInventory().contains(stack -> stack.is(ModItems.DEFUSAL_KIT));

        int totalNeeded = hasKit ? cfg.playerDefuseTicksWithKit : cfg.playerDefuseTicks;
        defuseProgress++;

        int percent = Math.min(100, (int) ((defuseProgress / (float) totalNeeded) * 100));
        int filled = percent / 5;
        String bar = "=".repeat(filled) + " ".repeat(20 - filled);
        defuser.displayClientMessage(Component.literal(
                "§eDefusing C4 " + (hasKit ? "§7(kit) " : "") + "§7[" + bar + "] §a" + percent + "%"), true);

        if (defuseProgress >= totalNeeded) {
            defuse();
        }
    }

    private void tickBeep(Level world, BlockPos pos) {
        beepCooldown--;
        if (beepCooldown > 0) return;

        float progress = (totalTicks() - ticksRemaining) / (float) totalTicks();
        beepCooldown = Math.max(2, (int) (20.0f * (1.0f - progress)));

        world.playSound(null, pos, ModSounds.C4_BEEP, SoundSource.BLOCKS,
                2.0f, 1.0f + progress * 0.35f);

        if (ticksRemaining % 200 == 0 || (ticksRemaining <= 200 && ticksRemaining % 100 == 0)) {
            int secs = ticksRemaining / 20;
            world.players().forEach(p -> p.displayClientMessage(
                    Component.literal("§c[C4] " + secs + " seconds!"), true));
        }
    }

    /** Avvia (o rinnova) il disinnesco da parte di un giocatore. */
    public void startDefusal(Player player) {
        if (player == null) return;
        if (defuserId == null) {
            defuserId = player.getUUID();
            defuserOrigin = player.position();
            defuseProgress = 0;
            if (level != null && !level.isClientSide()) {
                level.playSound(null, worldPosition, ModSounds.DEFUSING, SoundSource.VOICE, 1.0f, 1.0f);
            }
        }
    }

    public void defuse() {
        if (defused) return;
        this.defused = true;
        this.defuserId = null;
        this.defuserOrigin = null;

        if (level == null || level.isClientSide()) return;

        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(C4BombBlock.DEFUSED)) {
            level.setBlock(worldPosition, state.setValue(C4BombBlock.DEFUSED, true), 3);
        }

        if (level instanceof ServerLevel serverLevel) {
            BombRegistry.markDefused(serverLevel, worldPosition);
        }

        setChanged();

        if (level.getServer() != null) {
            CsgoMatchState.onBombDefused(level.getServer());
        }
    }

    private void explode() {
        if (level == null || level.isClientSide()) return;

        CsgoConfig cfg = CsgoConfig.get();
        Vec3 center = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        double radius = Math.max(1.0, cfg.bombRadius);

        level.playSound(null, worldPosition, ModSounds.C4_EXPLODE, SoundSource.BLOCKS, 4.0f, 1.0f);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 5, 2.0, 2.0, 2.0, 0.1);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, center.x, center.y, center.z, 30, 3.0, 3.0, 3.0, 0.05);
        }

        if (cfg.bombDestroysBlocks) {
            // Esplosione vanilla: gestisce da sola danni e distruzione, niente loop manuale.
            level.explode(null, center.x, center.y, center.z, (float) radius, Level.ExplosionInteraction.BLOCK);
        } else {
            // Solo danno, applicato una volta sola. La versione precedente sommava il danno vanilla
            // di Level.explode a questo loop, e il floor Math.max(20, ...) annullava il falloff.
            AABB damageArea = new AABB(center.x - radius, center.y - radius, center.z - radius,
                    center.x + radius, center.y + radius, center.z + radius);
            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, damageArea);

            for (LivingEntity entity : entities) {
                double dist = Math.sqrt(entity.distanceToSqr(center));
                if (dist > radius) continue;
                float damage = (float) (cfg.bombDamage * (1.0 - dist / radius));
                if (damage <= 0.0f) continue;
                entity.invulnerableTime = 0;
                if (level instanceof ServerLevel serverLevel) {
                    entity.hurtServer(serverLevel, level.damageSources().explosion(null, null), damage);
                }
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            BombRegistry.remove(serverLevel, worldPosition);
        }
        level.removeBlock(worldPosition, false);

        if (level.getServer() != null) {
            CsgoMatchState.onBombExploded(level.getServer());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ticksRemaining = input.getIntOr("TicksRemaining", -1);
        this.totalTicks = input.getIntOr("TotalTicks", -1);
        this.defused = input.getBooleanOr("Defused", false);
        this.defuseProgress = input.getIntOr("DefuseProgress", 0);
        this.beepCooldown = input.getIntOr("BeepCooldown", 20);
        this.lastInteractionTime = input.getLongOr("LastInteractionTime", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("TicksRemaining", this.ticksRemaining);
        output.putInt("TotalTicks", this.totalTicks);
        output.putBoolean("Defused", this.defused);
        output.putInt("DefuseProgress", this.defuseProgress);
        output.putInt("BeepCooldown", this.beepCooldown);
        output.putLong("LastInteractionTime", this.lastInteractionTime);
    }
}

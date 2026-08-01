package com.example.csgocompat.entity;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.ai.ShootEnemyGoal;
import com.example.csgocompat.block.entity.C4BombBlockEntity;
import com.example.csgocompat.config.CsgoConfig;
import com.example.csgocompat.item.ModItems;
import com.example.csgocompat.manager.BombRegistry;
import com.example.csgocompat.manager.CsgoMatchState;
import com.example.csgocompat.manager.MatchManager;
import com.example.csgocompat.manager.SiteRegistry;
import com.example.csgocompat.util.GunUtil;
import com.example.csgocompat.util.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class CounterTerroristEntity extends Monster {

    /** Chiamata radio della squadra: spinge il bot a ruotare, non gli impedisce di disinnescare. */
    public int ctCombatAlertTimer = 0;
    /** Ultimo momento in cui <b>questo</b> bot è stato colpito. */
    public long lastHurtGameTime = Long.MIN_VALUE / 2;
    /** Site assegnato per la difesa (0 = Site A, 1 = Site B, ...). */
    public int assignedSiteIndex = -1;

    public CounterTerroristEntity(EntityType<? extends Monster> entityType, Level world) {
        super(entityType, world);
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.CT_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.CT_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.CT_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.CT_BOOTS));
        this.setItemSlot(EquipmentSlot.MAINHAND, GunUtil.getGunItemStack("m4a1"));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);

        if (this.getNavigation() instanceof GroundPathNavigation groundNav) {
            groundNav.setCanOpenDoors(true);
        }
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createCTAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.STEP_HEIGHT, 1.25);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new DefuseBombGoal(this));
        this.goalSelector.addGoal(3, new CTRotateToEnemyAlertGoal(this));
        this.goalSelector.addGoal(4, new ShootEnemyGoal(this));
        this.goalSelector.addGoal(5, new CTTacticsGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (livingEntity, serverLevel) -> CsGoCompatMod.getTeam(livingEntity).equals("T")
        ));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0) {
            this.lastHurtGameTime = level.getGameTime();
            triggerTeamCombatAlert(level, this);
        }
        return hurt;
    }

    /** Allerta i CT vicini e aggiorna la blackboard di squadra. */
    public static void triggerTeamCombatAlert(ServerLevel level, Mob hitMob) {
        if (hitMob == null) return;
        MatchManager.reportEnemySpotted("CT", hitMob.position(), level);

        List<CounterTerroristEntity> allies = level.getEntitiesOfClass(
                CounterTerroristEntity.class, hitMob.getBoundingBox().inflate(64.0), LivingEntity::isAlive);
        for (CounterTerroristEntity ct : allies) {
            ct.ctCombatAlertTimer = 60;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.ctCombatAlertTimer > 0) {
            this.ctCombatAlertTimer--;
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) {
            CsgoMatchState.unregisterBot(this.getUUID());
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("AssignedSiteIndex", this.assignedSiteIndex);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.assignedSiteIndex = input.getIntOr("AssignedSiteIndex", -1);
    }

    /** Anti-blocco condiviso dai goal di movimento. */
    private static boolean handleStuck(Mob mob, Vec3[] lastPos, int[] stuckTicks, int jumpAt, int resetAt, double speed) {
        if (lastPos[0] != null && mob.position().distanceToSqr(lastPos[0]) < 0.25) {
            stuckTicks[0]++;
            if (stuckTicks[0] == jumpAt) {
                mob.getJumpControl().jump();
                mob.getNavigation().recomputePath();
            } else if (stuckTicks[0] > resetAt) {
                mob.getNavigation().stop();
                double angle = mob.getRandom().nextDouble() * Math.PI * 2;
                BlockPos target = mob.blockPosition().offset(
                        (int) (Math.cos(angle) * 4.0), 0, (int) (Math.sin(angle) * 4.0));
                mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
                stuckTicks[0] = 0;
                lastPos[0] = mob.position();
                return true;
            }
        } else {
            stuckTicks[0] = 0;
            lastPos[0] = mob.position();
        }
        return false;
    }

    // ------------------------------------------------------------------ goals

    static class CTRotateToEnemyAlertGoal extends Goal {
        private final CounterTerroristEntity ct;
        private final Vec3[] lastPos = new Vec3[1];
        private final int[] stuckTicks = new int[1];
        private Vec3 lastAlertPos = null;

        public CTRotateToEnemyAlertGoal(CounterTerroristEntity ct) {
            this.ct = ct;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (ct.getTarget() != null) return false;
            if (!MatchManager.hasRecentEnemyPosition("CT", ct.level(), 400)) return false;

            Vec3 alertPos = MatchManager.getLastKnownEnemyPosition("CT");
            if (alertPos == null) return false;

            if (!BombRegistry.isEmpty()) return true;

            // Un solo difensore rimasto sull'altro lato resta come anchor a coprire il flank.
            List<CounterTerroristEntity> nearbyAllies = ct.level().getEntitiesOfClass(
                    CounterTerroristEntity.class, ct.getBoundingBox().inflate(25.0), LivingEntity::isAlive);
            return !(ct.distanceToSqr(alertPos) > 625.0 && nearbyAllies.size() <= 1);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            lastAlertPos = MatchManager.getLastKnownEnemyPosition("CT");
            if (lastAlertPos != null) {
                ct.getNavigation().moveTo(lastAlertPos.x, lastAlertPos.y, lastAlertPos.z, 1.3);
            }
            lastPos[0] = ct.position();
            stuckTicks[0] = 0;
        }

        @Override
        public void stop() {
            lastAlertPos = null;
            lastPos[0] = null;
            stuckTicks[0] = 0;
        }

        @Override
        public void tick() {
            if (handleStuck(ct, lastPos, stuckTicks, 15, 25, 1.25)) return;

            Vec3 currentAlert = MatchManager.getLastKnownEnemyPosition("CT");
            if (currentAlert == null) return;

            if (lastAlertPos == null || currentAlert.distanceToSqr(lastAlertPos) > 4.0) {
                lastAlertPos = currentAlert;
                ct.getNavigation().moveTo(currentAlert.x, currentAlert.y, currentAlert.z, 1.3);
            }
            ct.getLookControl().setLookAt(currentAlert.x, currentAlert.y + 1.5, currentAlert.z, 30.0f, 30.0f);
        }
    }

    static class CTTacticsGoal extends Goal {
        private final CounterTerroristEntity ct;
        private final Vec3[] lastPos = new Vec3[1];
        private final int[] stuckTicks = new int[1];
        private BlockPos targetSite = null;
        private int searchCooldown = 0;

        public CTTacticsGoal(CounterTerroristEntity ct) {
            this.ct = ct;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return BombRegistry.isEmpty() && ct.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            lastPos[0] = ct.position();
            stuckTicks[0] = 0;
        }

        @Override
        public void tick() {
            if (handleStuck(ct, lastPos, stuckTicks, 15, 25, 1.2)) return;

            if (targetSite == null && !SiteRegistry.isEmpty()) {
                int siteIdx = ct.assignedSiteIndex >= 0 ? ct.assignedSiteIndex % SiteRegistry.size() : 0;
                SiteRegistry.Site site = SiteRegistry.byIndex(siteIdx);
                if (site != null) targetSite = site.pos;
            }
            if (targetSite == null) return;

            double distSq = ct.distanceToSqr(targetSite.getX() + 0.5, targetSite.getY() + 0.5, targetSite.getZ() + 0.5);
            if (distSq > 64.0) {
                ct.getNavigation().moveTo(targetSite.getX() + 0.5, targetSite.getY() + 0.5, targetSite.getZ() + 0.5, 1.25);
                return;
            }

            searchCooldown--;
            if (searchCooldown <= 0 || ct.getNavigation().isDone()) {
                // Quota presa dal site, non dalla heightmap: al chiuso la heightmap portava sul tetto.
                BlockPos dest = targetSite.offset(
                        ct.getRandom().nextInt(16) - 8, 0, ct.getRandom().nextInt(16) - 8);
                ct.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 0.85);
                searchCooldown = 80 + ct.getRandom().nextInt(60);
            }
        }
    }

    static class DefuseBombGoal extends Goal {
        private final CounterTerroristEntity ct;
        private final Vec3[] lastPos = new Vec3[1];
        private final int[] stuckTicks = new int[1];
        private BlockPos bombPos = null;
        private int defuseTicks = 0;

        public DefuseBombGoal(CounterTerroristEntity ct) {
            this.ct = ct;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        /**
         * Prima il defuse era bloccato da un campo statico globale: bastava che un CT qualsiasi
         * venisse colpito ovunque nella mappa perché nessuno disinnescasse per 60 tick. Ora conta
         * solo il fuoco subito da questo bot, e con poco tempo rimasto ci prova comunque.
         */
        private boolean underPersonalFire() {
            if (ct.getTarget() != null && ct.getTarget().isAlive()) return true;
            return ct.level().getGameTime() - ct.lastHurtGameTime < 40;
        }

        private int bombTicksLeft() {
            if (bombPos == null) return Integer.MAX_VALUE;
            BlockEntity be = ct.level().getBlockEntity(bombPos);
            return be instanceof C4BombBlockEntity c4 ? c4.getTicksRemaining() : Integer.MAX_VALUE;
        }

        @Override
        public boolean canUse() {
            if (BombRegistry.isEmpty()) return false;

            BlockPos candidate = BombRegistry.first();
            if (candidate == null) return false;
            this.bombPos = candidate;

            // Clutch: sotto i 10 secondi si va comunque, fuoco o no.
            boolean clutch = bombTicksLeft() < 200;
            if (!clutch && underPersonalFire()) {
                this.bombPos = null;
                return false;
            }

            // Se un compagno più vicino è già sul posto, lo lascia fare.
            List<CounterTerroristEntity> allies = ct.level().getEntitiesOfClass(
                    CounterTerroristEntity.class, ct.getBoundingBox().inflate(32.0), LivingEntity::isAlive);
            double myDist = ct.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            for (CounterTerroristEntity ally : allies) {
                if (ally == ct) continue;
                double allyDist = ally.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
                if (allyDist < myDist && allyDist <= 6.0) {
                    this.bombPos = null;
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (bombPos == null || !BombRegistry.contains(bombPos)) return false;
            return bombTicksLeft() < 200 || !underPersonalFire();
        }

        @Override
        public void start() {
            if (bombPos != null) {
                ct.getNavigation().moveTo(bombPos.getX() + 0.5, bombPos.getY() + 0.5, bombPos.getZ() + 0.5, 1.25);
            }
            lastPos[0] = ct.position();
            stuckTicks[0] = 0;
        }

        @Override
        public void stop() {
            bombPos = null;
            defuseTicks = 0;
            lastPos[0] = null;
            stuckTicks[0] = 0;
            ct.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (bombPos == null) return;
            if (handleStuck(ct, lastPos, stuckTicks, 12, 20, 1.25)) return;

            ct.getLookControl().setLookAt(bombPos.getX() + 0.5, bombPos.getY() + 0.5, bombPos.getZ() + 0.5, 30.0f, 30.0f);

            double distSq = ct.distanceToSqr(bombPos.getX() + 0.5, bombPos.getY() + 0.5, bombPos.getZ() + 0.5);
            if (distSq > 6.0) {
                ct.getNavigation().moveTo(bombPos.getX() + 0.5, bombPos.getY() + 0.5, bombPos.getZ() + 0.5, 1.25);
                return;
            }

            ct.getNavigation().stop();
            if (defuseTicks == 0) {
                ct.level().playSound(null, bombPos, ModSounds.DEFUSING, SoundSource.VOICE, 1.0f, 1.0f);
            }
            defuseTicks++;

            int required = Math.max(1, CsgoConfig.get().botDefuseTicks);

            if (ct.level() instanceof ServerLevel serverLevel && serverLevel.getRandom().nextFloat() < 0.3f) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        bombPos.getX() + 0.2 + serverLevel.getRandom().nextDouble() * 0.6,
                        bombPos.getY() + 0.3,
                        bombPos.getZ() + 0.2 + serverLevel.getRandom().nextDouble() * 0.6,
                        1, 0, 0.05, 0, 0);
            }

            if (defuseTicks % 20 == 0) {
                ct.level().playSound(null, bombPos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.5f, 1.2f);
                int percent = Math.min(100, (int) ((defuseTicks / (float) required) * 100));
                Component msg = Component.literal("§e[CT] Defusing bomb: §a" + percent + "%");
                for (Player player : ct.level().players()) {
                    if (player.distanceToSqr(ct) <= 1024.0) {
                        player.displayClientMessage(msg, true);
                    }
                }
            }

            if (defuseTicks >= required) {
                BlockEntity be = ct.level().getBlockEntity(bombPos);
                if (be instanceof C4BombBlockEntity c4) {
                    c4.defuse();
                }
                stop();
            }
        }
    }
}

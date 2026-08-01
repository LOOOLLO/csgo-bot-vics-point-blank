package com.example.csgocompat.entity;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.ai.ShootEnemyGoal;
import com.example.csgocompat.block.C4BombBlock;
import com.example.csgocompat.config.CsgoConfig;
import com.example.csgocompat.item.ModItems;
import com.example.csgocompat.manager.BombRegistry;
import com.example.csgocompat.manager.CsgoMatchState;
import com.example.csgocompat.manager.MatchManager;
import com.example.csgocompat.manager.SiteRegistry;
import com.example.csgocompat.util.GunUtil;
import com.example.csgocompat.util.ModSounds;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class TerroristEntity extends Monster {

    public enum SquadRole {
        UNASSIGNED, POINT_MAN, CARRIER, FOLLOWER
    }

    public SquadRole squadRole = SquadRole.UNASSIGNED;
    public UUID leaderId = null;
    /** Timer di combattimento del Carrier: mentre è attivo impugna l'arma invece della C4. */
    public int carrierCombatTimer = 0;

    public TerroristEntity(EntityType<? extends Monster> entityType, Level world) {
        super(entityType, world);
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.T_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.T_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.T_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.T_BOOTS));
        this.equipAK();
        // Le armi non vengono droppate: prima ogni morte lasciava un AK a terra.
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);

        if (this.getNavigation() instanceof GroundPathNavigation groundNav) {
            groundNav.setCanOpenDoors(true);
        }
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createTAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.STEP_HEIGHT, 1.25);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RetrieveBombGoal(this));
        this.goalSelector.addGoal(3, new ShootEnemyGoal(this));
        this.goalSelector.addGoal(4, new PlantBombGoal(this));
        this.goalSelector.addGoal(5, new PatrolBombGoal(this));
        this.goalSelector.addGoal(6, new PointManLeadGoal(this));
        this.goalSelector.addGoal(7, new SquadFollowGoal(this));
        this.goalSelector.addGoal(8, new RotateToTeamBlackboardGoal(this, "T"));
        this.goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (livingEntity, serverLevel) -> CsGoCompatMod.getTeam(livingEntity).equals("CT")
        ));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0 && this.squadRole == SquadRole.CARRIER) {
            this.carrierCombatTimer = 100;
            if (this.getMainHandItem().is(CsGoCompatMod.C4_BOMB.asItem())) {
                this.equipAK();
            }
        }
        return hurt;
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

    // --- persistenza dello stato di squadra (prima andava perso ad ogni reload) ---

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("SquadRole", this.squadRole.name());
        output.putInt("CarrierCombatTimer", this.carrierCombatTimer);
        if (this.leaderId != null) {
            output.putString("LeaderId", this.leaderId.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            this.squadRole = SquadRole.valueOf(input.getStringOr("SquadRole", SquadRole.UNASSIGNED.name()));
        } catch (IllegalArgumentException e) {
            this.squadRole = SquadRole.UNASSIGNED;
        }
        this.carrierCombatTimer = input.getIntOr("CarrierCombatTimer", 0);
        String leader = input.getStringOr("LeaderId", "");
        try {
            this.leaderId = leader.isEmpty() ? null : UUID.fromString(leader);
        } catch (IllegalArgumentException e) {
            this.leaderId = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        if (this.squadRole == SquadRole.CARRIER && this.carrierCombatTimer > 0) {
            this.carrierCombatTimer--;
            if (this.carrierCombatTimer == 0 && BombRegistry.isEmpty()) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(CsGoCompatMod.C4_BOMB.asItem()));
            }
        }

        if (this.tickCount % 20 == 0) {
            maintainSquad();
        }

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()
                && this.squadRole != SquadRole.CARRIER
                && this.getMainHandItem().is(CsGoCompatMod.C4_BOMB.asItem())) {
            this.equipAK();
        }
    }

    /**
     * Elezione dei ruoli, eseguita da tutti i T e deterministica (ordinamento per UUID) così ogni
     * membro calcola lo stesso risultato senza conflitti.
     *
     * <p>Prima l'assegnazione girava solo sui mob {@code UNASSIGNED} e nessuno tornava mai a quello
     * stato: alla morte del POINT_MAN i FOLLOWER restavano senza leader e senza alcun goal che li
     * portasse al site, bloccando il round.
     */
    private void maintainSquad() {
        double radius = CsgoConfig.get().botSquadSearchRadius;
        List<TerroristEntity> squad = this.level().getEntitiesOfClass(
                TerroristEntity.class, this.getBoundingBox().inflate(radius), LivingEntity::isAlive);
        if (squad.isEmpty()) return;
        squad.sort(Comparator.comparing(TerroristEntity::getUUID));

        // 1. Point man: se quello attuale è morto o assente, se ne elegge uno nuovo.
        TerroristEntity pointMan = null;
        for (TerroristEntity t : squad) {
            if (t.squadRole == SquadRole.POINT_MAN) {
                pointMan = t;
                break;
            }
        }
        if (pointMan == null) {
            pointMan = squad.get(0);
            pointMan.squadRole = SquadRole.POINT_MAN;
            pointMan.leaderId = null;
            pointMan.equipAK();
        }

        // 2. Carrier: chi ha già la C4 in mano, altrimenti l'ultimo della lista.
        TerroristEntity carrier = null;
        for (TerroristEntity t : squad) {
            if (t.getMainHandItem().is(CsGoCompatMod.C4_BOMB.asItem()) || t.squadRole == SquadRole.CARRIER) {
                carrier = t;
                break;
            }
        }
        // Se la C4 è in mano a un giocatore, i bot non devono generarne una seconda.
        if (carrier == null && BombRegistry.isEmpty() && squad.size() > 1
                && !CsgoMatchState.playerCarriesBomb(this.level())) {
            for (int i = squad.size() - 1; i >= 0; i--) {
                if (squad.get(i) != pointMan) {
                    carrier = squad.get(i);
                    break;
                }
            }
            if (carrier != null && carrier.carrierCombatTimer == 0) {
                carrier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(CsGoCompatMod.C4_BOMB.asItem()));
            }
        }

        // 3. Ruoli finali.
        for (TerroristEntity t : squad) {
            if (t == pointMan) {
                t.squadRole = SquadRole.POINT_MAN;
                t.leaderId = null;
            } else if (t == carrier) {
                t.squadRole = SquadRole.CARRIER;
                t.leaderId = pointMan.getUUID();
            } else {
                if (t.squadRole != SquadRole.FOLLOWER) {
                    t.equipAK();
                }
                t.squadRole = SquadRole.FOLLOWER;
                t.leaderId = pointMan.getUUID();
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.squadRole == SquadRole.CARRIER && this.level() instanceof ServerLevel serverLevel) {
            this.spawnAtLocation(serverLevel, new ItemStack(CsGoCompatMod.C4_BOMB.asItem()));
        }
    }

    public void equipAK() {
        this.setItemSlot(EquipmentSlot.MAINHAND, GunUtil.getGunItemStack("ak47"));
    }

    public TerroristEntity getLeader() {
        if (this.leaderId == null) return null;
        if (this.level() instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntityInAnyDimension(this.leaderId);
            if (entity instanceof TerroristEntity te && te.isAlive()) {
                return te;
            }
        }
        return null;
    }

    /** Destinazione di piazzamento scelta all'interno di un site registrato. */
    public static BlockPos pickSiteTarget(Level level, SiteRegistry.Site site) {
        return SiteRegistry.randomTargetInside(level, site);
    }

    // ------------------------------------------------------------------ goals

    /** Logica anti-blocco condivisa dai goal di movimento. */
    private static class StuckTracker {
        private Vec3 lastPos = null;
        private int stuckTicks = 0;

        void reset(Mob mob) {
            lastPos = mob.position();
            stuckTicks = 0;
        }

        /** @return true se il goal deve interrompere il tick corrente (manovra di sblocco avviata). */
        boolean tick(Mob mob, double unstuckSpeed) {
            if (lastPos != null && mob.position().distanceToSqr(lastPos) < 0.25) {
                stuckTicks++;
                if (stuckTicks == 15) {
                    mob.getJumpControl().jump();
                    mob.getNavigation().recomputePath();
                } else if (stuckTicks > 25) {
                    mob.getNavigation().stop();
                    double angle = mob.getRandom().nextDouble() * Math.PI * 2;
                    BlockPos target = mob.blockPosition().offset(
                            (int) (Math.cos(angle) * 4.0), 0, (int) (Math.sin(angle) * 4.0));
                    mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, unstuckSpeed);
                    stuckTicks = 0;
                    lastPos = mob.position();
                    return true;
                }
            } else {
                stuckTicks = 0;
                lastPos = mob.position();
            }
            return false;
        }
    }

    static class RetrieveBombGoal extends Goal {
        private final TerroristEntity t;
        private ItemEntity targetItem = null;

        public RetrieveBombGoal(TerroristEntity t) {
            this.t = t;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            // Anche il POINT_MAN può recuperare la C4: prima ne era escluso, e se restava solo lui
            // la bomba caduta non veniva più raccolta e il round non era più vincibile.
            if (t.squadRole == SquadRole.CARRIER) return false;
            if (t.getMainHandItem().is(CsGoCompatMod.C4_BOMB.asItem())) return false;
            if (!BombRegistry.isEmpty()) return false;

            List<ItemEntity> items = t.level().getEntitiesOfClass(ItemEntity.class,
                    t.getBoundingBox().inflate(128.0),
                    item -> item.getItem().is(CsGoCompatMod.C4_BOMB.asItem()));
            if (items.isEmpty()) return false;
            targetItem = items.get(0);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return targetItem != null && targetItem.isAlive()
                    && t.squadRole != SquadRole.CARRIER && BombRegistry.isEmpty();
        }

        @Override
        public void stop() {
            targetItem = null;
        }

        @Override
        public void tick() {
            if (targetItem == null || !targetItem.isAlive()) return;
            t.getNavigation().moveTo(targetItem, 1.25);
            if (t.distanceToSqr(targetItem) < 3.0) {
                targetItem.discard();
                t.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(CsGoCompatMod.C4_BOMB.asItem()));
                t.squadRole = SquadRole.CARRIER;
                t.level().playSound(null, t.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 1.0f, 1.0f);
            }
        }
    }

    static class PointManLeadGoal extends Goal {
        private final TerroristEntity t;
        private final StuckTracker stuck = new StuckTracker();
        private BlockPos targetSite = null;

        public PointManLeadGoal(TerroristEntity t) {
            this.t = t;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return t.squadRole == SquadRole.POINT_MAN && BombRegistry.isEmpty() && t.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            if (targetSite == null && !SiteRegistry.isEmpty()) {
                SiteRegistry.Site chosen = SiteRegistry.byIndex(t.getRandom().nextInt(SiteRegistry.size()));
                targetSite = pickSiteTarget(t.level(), chosen);
            }
            stuck.reset(t);
        }

        @Override
        public void stop() {
            targetSite = null;
        }

        @Override
        public void tick() {
            if (targetSite == null) return;
            if (stuck.tick(t, 1.0)) return;

            List<TerroristEntity> team = t.level().getEntitiesOfClass(TerroristEntity.class,
                    t.getBoundingBox().inflate(80.0),
                    other -> other != t && other.squadRole != SquadRole.POINT_MAN
                            && t.getUUID().equals(other.leaderId));

            boolean shouldWait = false;
            for (TerroristEntity member : team) {
                if (t.distanceToSqr(member) > 100.0) {
                    shouldWait = true;
                    break;
                }
            }

            if (shouldWait) {
                t.getNavigation().stop();
            } else if (t.distanceToSqr(targetSite.getX(), targetSite.getY(), targetSite.getZ()) > 9.0) {
                t.getNavigation().moveTo(targetSite.getX() + 0.5, targetSite.getY() + 0.5, targetSite.getZ() + 0.5, 0.85);
            } else {
                t.getNavigation().stop();
            }
        }
    }

    static class SquadFollowGoal extends Goal {
        private final TerroristEntity t;
        private final StuckTracker stuck = new StuckTracker();
        private int repathCooldown = 0;

        public SquadFollowGoal(TerroristEntity t) {
            this.t = t;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (t.squadRole != SquadRole.FOLLOWER && t.squadRole != SquadRole.CARRIER) return false;
            if (!BombRegistry.isEmpty()) return false;
            if (t.getTarget() != null) return false;
            return t.getLeader() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            stuck.reset(t);
            repathCooldown = 0;
        }

        @Override
        public void tick() {
            TerroristEntity leader = t.getLeader();
            if (leader == null) return;
            if (stuck.tick(t, 1.2)) return;

            double distSq = t.distanceToSqr(leader);
            repathCooldown--;

            double keepAway = t.squadRole == SquadRole.CARRIER ? 49.0 : 16.0;
            double tooClose = t.squadRole == SquadRole.CARRIER ? 9.0 : 4.0;
            double speed = t.squadRole == SquadRole.CARRIER ? 1.1 : 1.15;

            if (distSq > keepAway) {
                if (repathCooldown <= 0) {
                    t.getNavigation().moveTo(leader, speed);
                    repathCooldown = 10;
                }
            } else if (distSq < tooClose) {
                t.getNavigation().stop();
            }
        }
    }

    static class PlantBombGoal extends Goal {
        private final TerroristEntity t;
        private BlockPos sitePos = null;
        private int plantTicks = 0;

        public PlantBombGoal(TerroristEntity t) {
            this.t = t;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (t.carrierCombatTimer > 0) return false;
            if (!t.getMainHandItem().is(CsGoCompatMod.C4_BOMB.asItem())) return false;
            if (t.squadRole != SquadRole.CARRIER && t.squadRole != SquadRole.POINT_MAN) return false;
            if (!BombRegistry.isEmpty()) return false;

            SiteRegistry.Site closest = null;
            double minDist = Double.MAX_VALUE;
            for (SiteRegistry.Site site : SiteRegistry.all()) {
                double dist = t.distanceToSqr(site.pos.getX() + 0.5, site.pos.getY() + 0.5, site.pos.getZ() + 0.5);
                if (dist < minDist) {
                    minDist = dist;
                    closest = site;
                }
            }

            if (closest != null && minDist < 100.0) {
                sitePos = pickSiteTarget(t.level(), closest);
                return sitePos != null;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return t.carrierCombatTimer <= 0
                    && t.getMainHandItem().is(CsGoCompatMod.C4_BOMB.asItem())
                    && BombRegistry.isEmpty()
                    && sitePos != null;
        }

        @Override
        public void start() {
            if (sitePos != null) {
                t.getNavigation().moveTo(sitePos.getX() + 0.5, sitePos.getY() + 0.5, sitePos.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public void stop() {
            sitePos = null;
            plantTicks = 0;
            t.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (sitePos == null) return;

            t.getLookControl().setLookAt(sitePos.getX() + 0.5, sitePos.getY() + 0.5, sitePos.getZ() + 0.5, 30.0f, 30.0f);
            double distSq = t.distanceToSqr(sitePos.getX() + 0.5, sitePos.getY() + 0.5, sitePos.getZ() + 0.5);

            if (distSq > 4.0) {
                t.getNavigation().moveTo(sitePos.getX() + 0.5, sitePos.getY() + 0.5, sitePos.getZ() + 0.5, 1.0);
                return;
            }

            t.getNavigation().stop();
            plantTicks++;

            int required = Math.max(1, CsgoConfig.get().botPlantTicks);

            if (plantTicks % 10 == 0) {
                int percent = Math.min(100, (int) ((plantTicks / (float) required) * 100));
                // Solo chi è nei paraggi riceve l'avanzamento: prima finiva a tutti i giocatori
                // del mondo, per ogni T che stava piazzando.
                notifyNearby("§c[T] Planting C4: §e" + percent + "%", true);
                t.level().playSound(null, t.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                        SoundSource.HOSTILE, 1.0f, 1.5f);
            }

            if (plantTicks >= required) {
                // Stessa regola applicata ai giocatori: la C4 va appoggiata sopra il site.
                BlockPos plantPos = t.blockPosition();
                if (!C4BombBlock.canPlantAt(t.level(), plantPos).allowed()) {
                    plantPos = sitePos;
                }
                if (!C4BombBlock.canPlantAt(t.level(), plantPos).allowed()) {
                    stop();
                    return;
                }
                t.level().setBlock(plantPos, CsGoCompatMod.C4_BOMB.defaultBlockState(), 3);
                t.equipAK();

                if (t.level() instanceof ServerLevel serverLevel) {
                    BombRegistry.add(serverLevel, plantPos);
                }
                t.level().playSound(null, plantPos, ModSounds.C4_PLANT, SoundSource.BLOCKS, 1.4f, 1.0f);
                t.level().players().forEach(p -> p.displayClientMessage(
                        Component.literal("§c§l[C4] The bomb has been planted!"), false));

                stop();
            }
        }

        private void notifyNearby(String message, boolean actionBar) {
            Component component = Component.literal(message);
            for (Player player : t.level().players()) {
                if (player.distanceToSqr(t) <= 1024.0) {
                    player.displayClientMessage(component, actionBar);
                }
            }
        }
    }

    static class PatrolBombGoal extends Goal {
        private final TerroristEntity t;
        private int searchCooldown = 0;

        public PatrolBombGoal(TerroristEntity t) {
            this.t = t;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !BombRegistry.isEmpty() && t.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            BlockPos bombPos = BombRegistry.first();
            if (bombPos == null) return;

            double distSq = t.distanceToSqr(bombPos.getX(), bombPos.getY(), bombPos.getZ());
            if (distSq > 900.0) {
                t.getNavigation().moveTo(bombPos.getX() + 0.5, bombPos.getY() + 0.5, bombPos.getZ() + 0.5, 1.25);
                searchCooldown = 40;
                return;
            }

            searchCooldown--;
            if (searchCooldown <= 0 || t.getNavigation().isDone()) {
                // Quota presa dalla bomba, non dalla heightmap: su mappe al chiuso la heightmap
                // mandava i bot sul tetto.
                BlockPos dest = bombPos.offset(
                        t.getRandom().nextInt(30) - 15, 0, t.getRandom().nextInt(30) - 15);
                t.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 1.0);
                searchCooldown = 150 + t.getRandom().nextInt(100);
            }
        }
    }

    static class RotateToTeamBlackboardGoal extends Goal {
        private final Mob mob;
        private final String teamName;

        public RotateToTeamBlackboardGoal(Mob mob, String teamName) {
            this.mob = mob;
            this.teamName = teamName;
            this.setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() == null
                    && MatchManager.hasRecentEnemyPosition(teamName, mob.level(), 200);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            Vec3 enemyPos = MatchManager.getLastKnownEnemyPosition(teamName);
            if (enemyPos == null) return;
            mob.getLookControl().setLookAt(enemyPos.x, enemyPos.y, enemyPos.z, 30.0f, 30.0f);
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(enemyPos.x, enemyPos.y, enemyPos.z, 1.0);
            }
        }
    }
}

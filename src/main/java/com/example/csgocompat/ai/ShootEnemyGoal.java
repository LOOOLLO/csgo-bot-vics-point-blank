package com.example.csgocompat.ai;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.config.CsgoConfig;
import com.example.csgocompat.manager.CsgoMatchState;
import com.example.csgocompat.manager.MatchManager;
import com.example.csgocompat.util.PointBlankBridge;
import com.vicmatskiv.pointblank.item.GunItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Goal di combattimento con armi da fuoco per i bot.
 *
 * <p>Le statistiche (RPM, gittata, pallettoni, capacità caricatore) vengono lette dall'arma reale di
 * Point-Blank tramite {@link PointBlankBridge}; il danno resta governato dalla difficoltà perché è
 * tarato sui 20 HP del giocatore, ma può essere agganciato ai valori originali dell'arma dal config.
 */
public class ShootEnemyGoal extends Goal {

    private final Mob mob;
    private final String teamName;

    private int reactionTicks = 0;
    private int shotCooldown = 0;
    private int currentBurstShots = 0;
    private int reloadCooldown = 0;

    /** Scarto individuale: senza, tutti i bot di una difficoltà reagiscono e mirano identici. */
    private final int reactionBias;
    private final double spreadBias;

    public ShootEnemyGoal(Mob mob) {
        this.mob = mob;
        this.teamName = CsGoCompatMod.getTeam(mob);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));

        if (CsgoConfig.get().botVariance) {
            this.reactionBias = mob.getRandom().nextInt(3) - 1;
            this.spreadBias = 0.8 + mob.getRandom().nextDouble() * 0.5;
        } else {
            this.reactionBias = 0;
            this.spreadBias = 1.0;
        }
    }

    private boolean holdingBomb() {
        return this.mob.getMainHandItem().is(CsGoCompatMod.C4_BOMB.asItem());
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive() || holdingBomb()) {
            return false;
        }
        if (this.mob.distanceToSqr(target) > weaponRangeSq()) {
            return false;
        }
        return this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && !holdingBomb();
    }

    @Override
    public void start() {
        resetReactionTime();
        this.shotCooldown = 0;
        this.currentBurstShots = 0;
        this.reloadCooldown = 0;
        this.mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.reactionTicks = 0;
        this.shotCooldown = 0;
        this.currentBurstShots = 0;
        this.reloadCooldown = 0;
    }

    private double weaponRangeSq() {
        double range = PointBlankBridge.maxRange(this.mob.getMainHandItem(), 60.0);
        return range * range;
    }

    private void resetReactionTime() {
        CsgoConfig.DifficultySettings diff = CsgoMatchState.difficultySettings();
        int min = Math.max(0, diff.reactionTicksMin);
        int max = Math.max(min, diff.reactionTicksMax);
        int base = min + (max > min ? this.mob.getRandom().nextInt(max - min + 1) : 0);
        this.reactionTicks = Math.max(0, base + this.reactionBias);
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        // Counter-strafing: fermarsi azzera l'imprecisione da movimento.
        this.mob.getNavigation().stop();

        Vec3 targetPos = target.getEyePosition();
        float turnSpeed = CsgoMatchState.currentDifficulty == CsgoMatchState.MatchDifficulty.UNFAIR ? 180.0f : 30.0f;
        this.mob.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z, turnSpeed, turnSpeed);

        MatchManager.reportEnemySpotted(this.teamName, target.position(), this.mob.level());

        if (!this.mob.getSensing().hasLineOfSight(target)) {
            resetReactionTime();
            return;
        }

        if (this.reactionTicks > 0) {
            this.reactionTicks--;
            return;
        }

        ItemStack mainHand = this.mob.getMainHandItem();

        if (this.reloadCooldown > 0) {
            this.reloadCooldown--;
            if (this.reloadCooldown == 0) {
                PointBlankBridge.refill(mainHand);
                this.mob.level().playSound(null, this.mob.blockPosition(),
                        SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.HOSTILE, 1.0f, 1.0f);
            }
            return;
        }

        if (PointBlankBridge.ammo(mainHand) <= 0) {
            this.reloadCooldown = 40;
            this.currentBurstShots = 0;
            this.mob.level().playSound(null, this.mob.blockPosition(),
                    SoundEvents.ITEM_BREAK.value(), SoundSource.HOSTILE, 0.8f, 1.2f);
            return;
        }

        if (this.shotCooldown > 0) {
            this.shotCooldown--;
            return;
        }

        double distance = this.mob.distanceTo(target);
        shootWeapon(target, mainHand, distance);

        // Un solo decremento, tramite una sola API: prima ne venivano applicati fino a tre per colpo.
        PointBlankBridge.consumeAmmo(mainHand);
        this.currentBurstShots++;

        applyBurstCooldown(mainHand, distance);
    }

    private void applyBurstCooldown(ItemStack gun, double distance) {
        boolean unfair = CsgoMatchState.currentDifficulty == CsgoMatchState.MatchDifficulty.UNFAIR;

        // Cadenza reale dell'arma, mai più veloce di 2 tick.
        int intraBurst = Math.max(2, PointBlankBridge.fireIntervalTicks(gun, 3));

        int weaponBurst = PointBlankBridge.burstShots(gun);
        int burstLimit;
        if (unfair) {
            burstLimit = 10;
        } else if (weaponBurst > 0) {
            burstLimit = weaponBurst;
        } else if (distance < 10.0) {
            burstLimit = 4;
        } else if (distance < 25.0) {
            burstLimit = 3;
        } else {
            burstLimit = 2;
        }

        if (this.currentBurstShots >= burstLimit) {
            this.currentBurstShots = 0;
            if (unfair) {
                this.shotCooldown = 2;
            } else if (distance < 10.0) {
                this.shotCooldown = 8 + this.mob.getRandom().nextInt(5);
            } else if (distance < 25.0) {
                this.shotCooldown = 14 + this.mob.getRandom().nextInt(7);
            } else {
                this.shotCooldown = 24 + this.mob.getRandom().nextInt(10);
            }
        } else {
            this.shotCooldown = intraBurst;
        }
    }

    private void shootWeapon(LivingEntity target, ItemStack mainHand, double distance) {
        SoundEvent fireSound = SoundEvents.GENERIC_EXPLODE.value();
        float volume = 1.5f;
        if (mainHand.getItem() instanceof GunItem gun) {
            try {
                fireSound = gun.getFireSound();
                volume = gun.getFireSoundVolume();
            } catch (Throwable ignored) {
            }
        }

        this.mob.swing(InteractionHand.MAIN_HAND);
        this.mob.level().playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                fireSound, SoundSource.HOSTILE, volume,
                1.15f + this.mob.getRandom().nextFloat() * 0.2f);

        CsgoConfig cfg = CsgoConfig.get();
        CsgoConfig.DifficultySettings diff = CsgoMatchState.difficultySettings();

        double spread = (diff.spread
                + (this.currentBurstShots * diff.spread * 0.4)
                + (distance > 20.0 ? diff.spread * 0.5 : 0.0)
                + PointBlankBridge.inaccuracy(mainHand) * 0.01) * this.spreadBias;

        float baseDamage = cfg.usePointBlankDamage
                ? PointBlankBridge.damage(mainHand, cfg.fallbackWeaponDamage) * cfg.pointBlankDamageScale
                : cfg.fallbackWeaponDamage;
        baseDamage *= diff.damageMultiplier;

        int pellets = PointBlankBridge.pelletCount(mainHand);
        double pelletSpread = PointBlankBridge.pelletSpread(mainHand);
        float perPellet = baseDamage / pellets;
        double range = PointBlankBridge.maxRange(mainHand, 60.0);

        Vec3 start = this.mob.getEyePosition();
        Vec3 aim = target.getEyePosition();
        Vec3 baseDir = aim.subtract(start).normalize();

        Vec3 lastImpact = null;
        float accumulated = 0.0f;
        boolean headshot = false;

        for (int i = 0; i < pellets; i++) {
            double thisSpread = spread + (i > 0 ? pelletSpread : 0.0);
            Vec3 dir = applySpread(baseDir, thisSpread);
            Vec3 end = start.add(dir.scale(range));

            // Il muro vince sempre: la versione precedente calcolava questo raycast e poi lo
            // ignorava, quindi i bot colpivano attraverso i blocchi (in UNFAIR sempre).
            BlockHitResult blockHit = this.mob.level().clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
            Vec3 blockPoint = blockHit.getLocation();
            double blockDistSq = start.distanceToSqr(blockPoint);

            Optional<Vec3> entityHit = target.getBoundingBox().inflate(0.3).clip(start, end);
            lastImpact = blockPoint;

            if (entityHit.isPresent() && start.distanceToSqr(entityHit.get()) <= blockDistSq) {
                Vec3 impact = entityHit.get();
                lastImpact = impact;
                float damage = perPellet;
                if (cfg.botsCanHeadshot && PointBlankBridge.isHeadshot(target, impact)) {
                    damage *= cfg.headshotMultiplier;
                    headshot = true;
                }
                accumulated += damage;
            }
        }

        if (accumulated > 0.0f) {
            applyDamage(target, accumulated, headshot);
        }

        spawnTracer(start, baseDir, lastImpact);
    }

    private Vec3 applySpread(Vec3 dir, double spread) {
        if (spread <= 0.0) return dir;
        return dir.add(
                this.mob.getRandom().nextGaussian() * spread,
                this.mob.getRandom().nextGaussian() * spread,
                this.mob.getRandom().nextGaussian() * spread
        ).normalize();
    }

    /**
     * Applica il danno azzerando gli invulnerability frame.
     *
     * <p>Senza questo, con 2-3 tick fra un colpo e l'altro, {@code LivingEntity} scartava ogni
     * proiettile successivo al primo entro 10 tick (danno uguale al precedente): di una raffica da
     * 4 colpi ne arrivava uno solo.
     */
    private void applyDamage(LivingEntity target, float amount, boolean headshot) {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) return;

        target.invulnerableTime = 0;
        target.hurtServer(serverLevel, this.mob.damageSources().mobAttack(this.mob), amount);

        if (headshot) {
            CsgoMatchState.markHeadshot(target.getUUID(), serverLevel.getGameTime());
            serverLevel.playSound(null, target.getX(), target.getEyeY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 0.9f, 1.6f);
        }
    }

    private void spawnTracer(Vec3 start, Vec3 dir, Vec3 impact) {
        if (!(this.mob.level() instanceof ServerLevel serverLevel) || impact == null) return;

        Vec3 muzzle = start.add(dir.scale(0.6));
        serverLevel.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.05, 0.05, 0.05, 0.02);

        Vec3 trace = impact.subtract(muzzle);
        double length = trace.length();
        int steps = Math.min(48, Math.max(1, (int) (length * 0.8)));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = muzzle.add(trace.scale(i / (double) steps));
            serverLevel.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}

package com.example.csgocompat.util;

import com.vicmatskiv.pointblank.item.FireModeInstance;
import com.vicmatskiv.pointblank.item.GunItem;
import com.vicmatskiv.pointblank.util.HitScan;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ponte verso Vic's Point-Blank.
 *
 * <p>La pipeline di fuoco di Point-Blank è guidata dal client: {@code GunClientState} intercetta
 * l'input, manda {@code HitScanFireRequestPacket} al server e l'handler
 * ({@code GunItem#handleClientHitScanFireRequest}) accetta esclusivamente un {@code ServerPlayer}.
 * Un {@code Mob} non ha stato client e non è un ServerPlayer, quindi non può entrare in quel percorso.
 *
 * <p>Quello che invece è utilizzabile server-side da qualsiasi {@code LivingEntity} sono le
 * <b>statistiche reali dell'arma</b> ({@link FireModeInstance}) e l'helper
 * {@link HitScan#isHeadshot}. Questa classe le espone in modo difensivo: se l'API di Point-Blank
 * cambia, i metodi degradano su valori di fallback invece di crashare.
 */
public final class PointBlankBridge {

    public static final int DEFAULT_MAGAZINE = 30;

    private PointBlankBridge() {
    }

    public static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GunItem;
    }

    public static FireModeInstance fireMode(ItemStack stack) {
        if (!isGun(stack)) return null;
        try {
            return GunItem.getFireModeInstance(stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Colpi al minuto dichiarati dall'arma; 0 se sconosciuti. */
    public static int rpm(ItemStack stack) {
        FireModeInstance fm = fireMode(stack);
        if (fm != null) {
            try {
                int rpm = fm.getRpm();
                if (rpm > 0) return rpm;
            } catch (Throwable ignored) {
            }
        }
        if (stack.getItem() instanceof GunItem gun) {
            try {
                int rpm = gun.getRpm();
                if (rpm > 0) return rpm;
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    /** Intervallo fra i colpi in tick, derivato dagli RPM reali. Minimo 1 tick. */
    public static int fireIntervalTicks(ItemStack stack, int fallback) {
        int rpm = rpm(stack);
        if (rpm <= 0) return fallback;
        return Math.max(1, Math.round(1200.0f / rpm));
    }

    /** Danno per proiettile dichiarato dall'arma; {@code fallback} se sconosciuto. */
    public static float damage(ItemStack stack, float fallback) {
        FireModeInstance fm = fireMode(stack);
        if (fm != null) {
            try {
                float dmg = fm.getDamage();
                if (dmg > 0.0f) return dmg;
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    /** Gittata utile dell'arma in blocchi. */
    public static double maxRange(ItemStack stack, double fallback) {
        FireModeInstance fm = fireMode(stack);
        if (fm != null) {
            try {
                int range = fm.getMaxShootingDistance();
                if (range > 0) return range;
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    /** Numero di pallettoni per colpo (1 per le armi normali, >1 per i fucili a pompa). */
    public static int pelletCount(ItemStack stack) {
        FireModeInstance fm = fireMode(stack);
        if (fm != null) {
            try {
                return Math.max(1, fm.getPelletCount());
            } catch (Throwable ignored) {
            }
        }
        return 1;
    }

    public static double pelletSpread(ItemStack stack) {
        FireModeInstance fm = fireMode(stack);
        if (fm != null) {
            try {
                return Math.max(0.0, fm.getPelletSpread());
            } catch (Throwable ignored) {
            }
        }
        return 0.0;
    }

    /** Colpi per raffica dichiarati dalla modalità di fuoco; 0 se non è una modalità burst. */
    public static int burstShots(ItemStack stack) {
        FireModeInstance fm = fireMode(stack);
        if (fm != null) {
            try {
                return Math.max(0, fm.getBurstShots());
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    /** Imprecisione intrinseca dell'arma. */
    public static double inaccuracy(ItemStack stack) {
        if (stack.getItem() instanceof GunItem gun) {
            try {
                return Math.max(0.0, gun.getInacuracy());
            } catch (Throwable ignored) {
            }
        }
        return 0.0;
    }

    public static int magazineSize(ItemStack stack) {
        FireModeInstance fm = fireMode(stack);
        if (fm != null) {
            try {
                int cap = fm.getMaxAmmoCapacity();
                if (cap > 0) return cap;
            } catch (Throwable ignored) {
            }
        }
        return DEFAULT_MAGAZINE;
    }

    /**
     * Munizioni residue nel caricatore. Percorso unico: l'API di Point-Blank è l'unica fonte di
     * verità, così non si rischia di contare (o decrementare) due volte.
     */
    public static int ammo(ItemStack stack) {
        FireModeInstance fm = fireMode(stack);
        if (fm == null) return DEFAULT_MAGAZINE;
        try {
            return Math.max(0, GunItem.getAmmo(stack, fm));
        } catch (Throwable ignored) {
            return DEFAULT_MAGAZINE;
        }
    }

    public static void setAmmo(ItemStack stack, int amount) {
        FireModeInstance fm = fireMode(stack);
        if (fm == null) return;
        try {
            GunItem.setAmmo(stack, fm, Math.max(0, amount));
        } catch (Throwable ignored) {
        }
    }

    /** Consuma un colpo. Una sola volta, tramite una sola API. */
    public static void consumeAmmo(ItemStack stack) {
        if (!isGun(stack)) return;
        try {
            GunItem.decrementAmmo(stack);
        } catch (Throwable ignored) {
            setAmmo(stack, Math.max(0, ammo(stack) - 1));
        }
    }

    /** Riempie il caricatore alla capacità reale dell'arma. */
    public static void refill(ItemStack stack) {
        setAmmo(stack, magazineSize(stack));
    }

    /** Delega a Point-Blank la determinazione dell'headshot sul punto d'impatto. */
    public static boolean isHeadshot(LivingEntity target, Vec3 hitPos) {
        try {
            return HitScan.isHeadshot(target, hitPos);
        } catch (Throwable ignored) {
            // Fallback geometrico: impatto sopra l'85% dell'altezza degli occhi.
            double headY = target.getY() + target.getEyeHeight() * 0.85;
            return hitPos.y >= headY;
        }
    }
}

package com.example.csgocompat.util;

import com.vicmatskiv.pointblank.item.AmmoItem;
import com.vicmatskiv.pointblank.item.FireModeInstance;
import com.vicmatskiv.pointblank.item.GunItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

public final class GunUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger("csgo_mc/guns");

    private GunUtil() {
    }

    /** Recupera un Item registrato da Point-Blank tramite ID esatto, poi con ricerca fuzzy. */
    public static Item getGunItem(String gunId) {
        Identifier id = Identifier.fromNamespaceAndPath("pointblank", gunId);
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item != null && item != Items.AIR) {
                return item;
            }
        }

        // Fuzzy solo su prefisso: un semplice "contains" faceva sì che cercare "ammo" restituisse
        // il primo calibro qualsiasi del registro (di qui le munizioni sbagliate).
        String needle = gunId.toLowerCase(Locale.ROOT);
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Identifier itemId = entry.getKey().identifier();
            if (itemId.getNamespace().equalsIgnoreCase("pointblank")
                    && itemId.getPath().toLowerCase(Locale.ROOT).startsWith(needle)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Crea uno ItemStack dell'arma richiesta, con caricatore pieno. */
    public static ItemStack getGunItemStack(String gunId) {
        Item item = getGunItem(gunId);
        if (item == null) {
            LOGGER.warn("Point-Blank weapon '{}' not found, falling back to an iron sword", gunId);
            return new ItemStack(Items.IRON_SWORD);
        }
        return initializeGunStack(new ItemStack(item));
    }

    /**
     * Munizioni compatibili con l'arma passata.
     *
     * <p>Il calibro viene chiesto <b>all'arma stessa</b> invece di indovinarlo dal nome: i vecchi ID
     * inventati ({@code ammo_762x39}, {@code ammo_556x45}) non esistono in Point-Blank, il fallback
     * fuzzy su "ammo" pescava un calibro a caso e i bot ricevevano munizioni sbagliate.
     * Gli ID veri sono {@code pointblank:ammo762} (7.62mm) e {@code pointblank:ammo556} (5.56mm).
     */
    public static ItemStack getAmmoItemStack(ItemStack gunStack, String gunId, int count) {
        // 1. Calibro noto dell'arma (verificato in data/pointblank/items/<arma>.json).
        String mapped = switch (gunId.toLowerCase(Locale.ROOT)) {
            case "ak47" -> "ammo762";
            case "m4a1" -> "ammo556";
            default -> null;
        };
        if (mapped != null) {
            Item item = getGunItem(mapped);
            if (isRealAmmo(item)) return new ItemStack(item, count);
        }

        // 2. Elenco di compatibilità dichiarato dall'arma.
        if (gunStack.getItem() instanceof GunItem gun) {
            try {
                ItemStack picked = firstRealAmmo(gun.getCompatibleAmmo(), count);
                if (picked != null) return picked;
            } catch (Throwable ignored) {
            }
        }

        // 3. Munizioni effettive della modalità di fuoco selezionata.
        FireModeInstance fireMode = PointBlankBridge.fireMode(gunStack);
        if (fireMode != null) {
            try {
                ItemStack picked = firstRealAmmo(fireMode.getActualAmmo(), count);
                if (picked != null) return picked;
            } catch (Throwable ignored) {
            }
            try {
                // getAmmo() va usato solo se la modalità NON attinge al pool generico, altrimenti
                // restituisce il segnaposto interno "ammodefault" (nessun modello, nessuna lang).
                if (!fireMode.isUsingDefaultAmmoPool()) {
                    AmmoItem ammo = fireMode.getAmmo();
                    if (isRealAmmo(ammo)) return new ItemStack(ammo, count);
                }
            } catch (Throwable ignored) {
            }
        }

        LOGGER.warn("No compatible ammo found for '{}'", gunId);
        return new ItemStack(Items.IRON_NUGGET, count);
    }

    private static ItemStack firstRealAmmo(List<AmmoItem> candidates, int count) {
        if (candidates == null) return null;
        for (AmmoItem candidate : candidates) {
            if (isRealAmmo(candidate)) return new ItemStack(candidate, count);
        }
        return null;
    }

    /**
     * Scarta i segnaposto di Point-Blank: {@code ammodefault} è il pool generico interno e
     * {@code ammocreative} sono le munizioni infinite della creativa. Nessuno dei due è un calibro
     * reale, ed erano proprio quelli che finivano nell'inventario.
     */
    private static boolean isRealAmmo(Item item) {
        if (item == null || item == Items.AIR) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return !path.equals("ammodefault") && !path.equals("ammocreative");
    }

    /**
     * Inizializza uno stack di arma Point-Blank con il caricatore pieno.
     *
     * <p>Un solo percorso di scrittura, quello ufficiale di Point-Blank: la versione precedente
     * scriveva le munizioni tre volte (API, tag MiscUtil e CUSTOM_DATA) e in lettura le tre fonti
     * potevano divergere.
     */
    public static ItemStack initializeGunStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem)) {
            return stack;
        }
        try {
            GunItem.initStackForCrafting(stack);
            PointBlankBridge.refill(stack);
        } catch (Throwable t) {
            LOGGER.warn("Failed to initialise Point-Blank weapon", t);
        }
        return stack;
    }
}

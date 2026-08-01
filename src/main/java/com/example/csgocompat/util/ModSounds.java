package com.example.csgocompat.util;

import com.example.csgocompat.CsGoCompatMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Suoni originali CS:GO registrati dalla mod.
 * I file .ogg vivono in assets/csgo_mc/sounds/ e sono dichiarati in assets/csgo_mc/sounds.json.
 */
public final class ModSounds {

    public static SoundEvent C4_PLANT;
    public static SoundEvent C4_BEEP;
    public static SoundEvent C4_EXPLODE;
    public static SoundEvent BOMB_DEFUSED;
    public static SoundEvent DEFUSING;
    public static SoundEvent ROUND_START;

    private ModSounds() {
    }

    public static void register() {
        C4_PLANT = register("c4_plant");
        C4_BEEP = register("c4_beep");
        C4_EXPLODE = register("c4_explode");
        BOMB_DEFUSED = register("bomb_defused");
        DEFUSING = register("defusing");
        ROUND_START = register("round_start");
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(CsGoCompatMod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}

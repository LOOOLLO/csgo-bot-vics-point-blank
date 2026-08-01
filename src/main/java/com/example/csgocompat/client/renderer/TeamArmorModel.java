package com.example.csgocompat.client.renderer;

import com.example.csgocompat.item.TeamArmorItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class TeamArmorModel extends GeoModel<TeamArmorItem> {
    private final String team;
    private final String piece;

    public TeamArmorModel(String team, String piece) {
        this.team = team;
        this.piece = piece;
    }

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return Identifier.fromNamespaceAndPath("csgo_mc", "armor/" + team + "_" + piece);
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return Identifier.fromNamespaceAndPath("csgo_mc", "textures/armor/" + team + "_" + piece + ".png");
    }

    @Override
    public Identifier getAnimationResource(TeamArmorItem animatable) {
        return Identifier.fromNamespaceAndPath("csgo_mc", "armor");
    }
}

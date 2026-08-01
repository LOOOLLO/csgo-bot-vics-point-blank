package com.example.csgocompat.client.renderer;

import com.example.csgocompat.item.DefusalKitItem;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class DefusalKitModel extends GeoModel<DefusalKitItem> {

    private static final Identifier MODEL =
            Identifier.fromNamespaceAndPath("csgo_mc", "item/defusal_kit");
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("csgo_mc", "textures/item/defusal_kit.png");
    private static final Identifier ANIMATION =
            Identifier.fromNamespaceAndPath("csgo_mc", "defusal_kit");

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(DefusalKitItem animatable) {
        return ANIMATION;
    }
}

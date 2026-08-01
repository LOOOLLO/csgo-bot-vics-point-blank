package com.example.csgocompat.client.renderer;

import com.example.csgocompat.item.DefusalKitItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public class DefusalKitRenderer extends GeoItemRenderer<DefusalKitItem> {

    public DefusalKitRenderer() {
        super(new DefusalKitModel());
    }

    /**
     * GeckoLib appoggia l'origine del modello Bedrock al centro del blocco, con
     * {@code translate(0.5, 0.51, 0.5)}. Le trasformazioni display che Blockbench scrive in
     * {@code models/item/defusal_kit.json} sono invece tarate su un modello che parte
     * dall'angolo del blocco: 0,51 blocchi di scarto, cioè poco più di 8 pixel troppo in alto.
     *
     * <p>Si vedeva come kit alto in mano e disallineato nella GUI, dove la rotazione di 35° su Z
     * trasformava lo scarto verticale in uno spostamento diagonale. Azzerando la componente Y il
     * modello torna esattamente dove Blockbench lo mostra in anteprima.
     */
    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPass) {
        renderPass.poseStack().translate(0.5f, 0.0f, 0.5f);
    }
}

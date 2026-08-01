package com.example.csgocompat.client.renderer;

import com.example.csgocompat.item.DefusalKitItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class DefusalKitRenderer extends GeoItemRenderer<DefusalKitItem> {

    public DefusalKitRenderer() {
        super(new DefusalKitModel());
    }
}

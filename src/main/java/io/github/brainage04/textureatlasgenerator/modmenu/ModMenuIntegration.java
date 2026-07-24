package io.github.brainage04.textureatlasgenerator.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.brainage04.textureatlasgenerator.screen.TextureAtlasScreen;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TextureAtlasScreen::new;
    }
}

package com.github.brainage04.textureatlasgenerator;

import com.github.brainage04.textureatlasgenerator.command.core.ModCommands;
import com.github.brainage04.textureatlasgenerator.config.core.ModConfig;
import com.github.brainage04.textureatlasgenerator.key.core.ModKeys;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextureAtlasGenerator implements ClientModInitializer {
	public static final String MOD_ID = "textureatlasgenerator";
	public static final String MOD_NAME = "TextureAtlasGenerator";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("{} initialising...", MOD_NAME);

		AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
		ModCommands.initialize();
		ModKeys.initialize();

		LOGGER.info("{} initialised.", MOD_NAME);
	}
}
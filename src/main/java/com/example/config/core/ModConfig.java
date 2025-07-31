package com.example.config.core;

import com.example.ExampleMod;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

// final fields will crash the game when saving
@SuppressWarnings("CanBeFinal")
@Config(name = ExampleMod.MOD_ID)
public class ModConfig implements ConfigData {
    public static boolean test = true;
}

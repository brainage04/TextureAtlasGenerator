package com.example.command.core;

import com.example.command.ExampleCommand;
import com.example.command.screen.OpenExampleScreenCommand;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class ModCommands {
    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            OpenExampleScreenCommand.initialize(dispatcher);

            ExampleCommand.initialize(dispatcher);
        });
    }
}

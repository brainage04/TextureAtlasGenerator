package com.github.brainage04.textureatlasgenerator.util;

import com.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.io.File;

public class ChatUtils {
    public enum MessageType {
        INFO,
        WARNING,
        ERROR,
    }

    public static Text formatMessage(String message, String tooltip, MessageType messageType) {
        MutableText text = Text.literal(message);

        if (tooltip != null) {
            text.setStyle(
                    text.getStyle().withHoverEvent(
                            new HoverEvent.ShowText(Text.literal(tooltip))
                    ).withUnderline(true)
            );
        }

        switch (messageType) {
            case WARNING:
                text.setStyle(
                        text.getStyle().withColor(Formatting.YELLOW)
                );
                break;
            case ERROR:
                text.setStyle(
                        text.getStyle().withColor(Formatting.RED)
                );
                break;
        }

        return Text.translatable(
                "message.tooltip.format",
                TextureAtlasGenerator.MOD_NAME,
                text
        );
    }

    public static Text formatMessage(String message, MessageType messageType) {
        return formatMessage(message, null, messageType);
    }

    public static void addChatMessage(String message, MessageType messageType) {
        MinecraftClient.getInstance().player.sendMessage(formatMessage(message, messageType), false);
    }

    public static void addChatMessage(String message, String tooltip, MessageType messageType) {
        MinecraftClient.getInstance().player.sendMessage(formatMessage(message, tooltip, messageType), false);
    }

    public static void addAtlasComponent(File output, String message) {
        Text text = Text.literal(output.getName()).setStyle(
                Style.EMPTY.withClickEvent(
                        new ClickEvent.OpenUrl(output.toURI())
                ).withUnderline(true)
        );

        MinecraftClient.getInstance().player.sendMessage(
                Text.translatable(
                        "atlas.save.success",
                        TextureAtlasGenerator.MOD_NAME,
                        message,
                        text
                ), false
        );
    }
}
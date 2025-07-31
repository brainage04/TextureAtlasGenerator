package com.example.screen;

import com.example.ExampleMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ExampleScreen extends Screen {
    private static final int PADDING = 2;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;

    public ButtonWidget button;

    public ExampleScreen(Screen parent) {
        super(Text.literal("Example Screen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        button = ButtonWidget.builder(Text.literal("Button"), button -> ExampleMod.LOGGER.info("You clicked a button!"))
                .dimensions((width - BUTTON_WIDTH) / 2, height * 3 / 4, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.literal("This is a tooltip.")))
                .build();

        addDrawableChild(button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);

        // at some point in between 1.21.6 and 1.21.8, the way color arguments are parsed was changed.
        // you now need to use a 32-bit literal instead of a 24-bit literal, otherwise your text
        // will be completely transparent! (color was changed from RGB to ARGB format)
        context.drawCenteredTextWithShadow(
                textRenderer,
                title,
                width / 2,
                textRenderer.fontHeight + PADDING,
                0xffffffff
        );
    }

    @Override
    public void close() {
        // todo: why is client annotated as nullable? why does the wiki not check if client is null?
        //  https://wiki.fabricmc.net/tutorial:screen?s[]=screen#the_parent_screen
        if (client != null) client.setScreen(parent);
    }
}
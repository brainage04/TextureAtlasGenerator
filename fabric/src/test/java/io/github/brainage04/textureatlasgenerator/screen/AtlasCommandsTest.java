package io.github.brainage04.textureatlasgenerator.screen;

import com.mojang.brigadier.CommandDispatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtlasCommandsTest {
    @Test
    void sharedCommandTreeAcceptsEveryExportAndBothSizeBoundaries() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(AtlasCommands.create());
        Object source = new Object();
        for (String command : new String[] {"atlas", "atlas 1", "atlas 1024", "atlas vanilla", "atlas vanilla 13",
                "atlas skyblock-all 1", "atlas skyblock-bazaar 1024", "atlas glint", "atlas glint 64"}) {
            var parsed = dispatcher.parse(command, source);
            assertFalse(parsed.getReader().canRead(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
            assertTrue(parsed.getExceptions().isEmpty(), command);
        }
    }

    @Test
    void sharedCommandTreeRejectsInvalidSizesAndUnknownAtlas() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(AtlasCommands.create());
        for (String command : new String[] {"atlas 0", "atlas 1025", "atlas -1", "atlas glint 0", "atlas vanilla 1.5", "atlas unknown"}) {
            var parsed = dispatcher.parse(command, new Object());
            assertTrue(parsed.getReader().canRead(), command);
        }
    }
}

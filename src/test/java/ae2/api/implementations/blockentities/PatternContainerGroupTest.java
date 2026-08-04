package ae2.api.implementations.blockentities;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;

class PatternContainerGroupTest {
    @Test
    void nullNameUsesSafeFallback() {
        PatternContainerGroup group = new PatternContainerGroup(null, null, Collections.emptyList());

        assertNotNull(group.name());
        assertDoesNotThrow(group.name()::createCopy);
    }

    @Test
    void validNameIsPreserved() {
        ITextComponent name = new TextComponentString("Machine");

        PatternContainerGroup group = new PatternContainerGroup(null, name, Collections.emptyList());

        assertSame(name, group.name());
    }

    @Test
    void nullTooltipUsesEmptyList() {
        PatternContainerGroup group = new PatternContainerGroup(null, new TextComponentString("Machine"), null);

        assertEquals(Collections.emptyList(), group.tooltip());
    }

    @Test
    void nullTooltipLinesAreDiscarded() {
        ITextComponent validLine = new TextComponentString("Valid");
        PatternContainerGroup group = new PatternContainerGroup(null, new TextComponentString("Machine"),
            Arrays.asList(null, validLine));

        assertEquals(Collections.singletonList(validLine), group.tooltip());
    }
}

package ae2.text;

import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextComponentsTest {

    @Test
    void nullComponentsHaveStableKey() {
        assertEquals("null", TextComponents.componentKey(null));
    }

    @Test
    void componentKeyIncludesSiblingsAndStyle() {
        var component = new TextComponentString("hello");
        component.getStyle().setBold(true);
        component.appendSibling(new TextComponentString(" world"));

        var key = TextComponents.componentKey(component);

        assertTrue(key.contains("\"kind\":\"vanilla\""));
        assertTrue(key.contains("\"siblings\""));
        assertTrue(key.contains("\"bold\":true"));
    }
}

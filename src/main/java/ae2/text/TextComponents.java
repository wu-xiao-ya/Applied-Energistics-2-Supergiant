package ae2.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.TreeSet;

public final class TextComponents {
    private TextComponents() {
    }

    public static void writeToPacket(PacketBuffer buffer, @Nullable ITextComponent value) {
        buffer.writeBoolean(value != null);
        if (value == null) {
            return;
        }

        buffer.writeBoolean(value instanceof ICustomTextComponent);
        if (value instanceof ICustomTextComponent customTextComponent) {
            buffer.writeString(customTextComponent.getTypeId());
            customTextComponent.writeToPacket(buffer);
            return;
        }

        buffer.writeTextComponent(value);
    }

    @Nullable
    public static ITextComponent readFromPacket(PacketBuffer buffer) {
        try {
            if (!buffer.readBoolean()) {
                return null;
            }

            if (buffer.readBoolean()) {
                return CustomTextComponents.decodePacket(buffer.readString(256), buffer);
            }

            return buffer.readTextComponent();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read text component", e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not read text component packet", e);
        }
    }

    public static String componentKey(@Nullable ITextComponent component) {
        if (component == null) {
            return "null";
        }

        return canonicalize(componentKeyData(component)).toString();
    }

    private static JsonObject componentKeyData(ITextComponent component) {
        JsonObject key = new JsonObject();
        if (component instanceof ICustomTextComponent customTextComponent) {
            key.addProperty("kind", "custom");
            key.addProperty("type", customTextComponent.getTypeId());
            key.add("data", customTextComponent.writeJson());
            key.add("style", serializeStyle(component));
        } else {
            key.addProperty("kind", "vanilla");
            ITextComponent shallowCopy = component.createCopy();
            shallowCopy.getSiblings().clear();
            key.add("component", JsonParser.parseString(ITextComponent.Serializer.componentToJson(shallowCopy)));
        }

        JsonArray siblings = new JsonArray();
        for (ITextComponent sibling : component.getSiblings()) {
            siblings.add(componentKeyData(sibling));
        }
        key.add("siblings", siblings);
        return key;
    }

    private static JsonElement serializeStyle(ITextComponent component) {
        TextComponentString styleHolder = new TextComponentString("");
        styleHolder.setStyle(component.getStyle().createDeepCopy());
        return JsonParser.parseString(ITextComponent.Serializer.componentToJson(styleHolder));
    }

    private static JsonElement canonicalize(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                result.add(canonicalize(child));
            }
            return result;
        }
        if (element.isJsonObject()) {
            JsonObject source = element.getAsJsonObject();
            JsonObject result = new JsonObject();
            for (String key : new TreeSet<>(source.keySet())) {
                result.add(key, canonicalize(source.get(key)));
            }
            return result;
        }
        return element;
    }
}

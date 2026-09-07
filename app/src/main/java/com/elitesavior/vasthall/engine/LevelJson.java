package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a level JSON document into a {@link LevelDefinition}.
 *
 * <p>Schema:
 * <pre>
 * {
 *   "name": "Hall",
 *   "gameMode": "HallGameMode",
 *   "actors": [
 *     {
 *       "class": "HallBeaconActor",
 *       "name": "HallBeacon",
 *       "location": [0, 1.5, 4],
 *       "rotation": [0, 0, 0],
 *       "scale": [1, 1, 1],
 *       "tickEnabled": true
 *     }
 *   ]
 * }
 * </pre>
 */
public final class LevelJson {
    private LevelJson() {
    }

    public static LevelDefinition parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("level json");
        }
        Object rootValue = new Reader(json).parseValue();
        if (!(rootValue instanceof JsonObject)) {
            throw new IllegalArgumentException("level json must be an object");
        }
        JsonObject root = (JsonObject) rootValue;
        String name = root.reqString("name");
        LevelDefinition def = LevelDefinition.named(name);
        if (root.has("gameMode")) {
            def.gameMode(root.reqString("gameMode"));
        }
        if (root.has("actors")) {
            JsonArray actors = root.reqArray("actors");
            for (int i = 0; i < actors.size(); i++) {
                def.actor(parseActor(actors.reqObject(i)));
            }
        }
        return def;
    }

    private static ActorTemplate parseActor(JsonObject actor) {
        ActorTemplate template = ActorTemplate.of(actor.reqString("class"));
        if (actor.has("name")) {
            template.named(actor.reqString("name"));
        }
        if (actor.has("location")) {
            float[] loc = actor.reqVec3("location");
            template.at(loc[0], loc[1], loc[2]);
        }
        if (actor.has("rotation")) {
            float[] rot = actor.reqVec3("rotation");
            template.rotation(rot[0], rot[1], rot[2]);
        }
        if (actor.has("scale")) {
            float[] scale = actor.reqVec3("scale");
            template.scale(scale[0], scale[1], scale[2]);
        }
        if (actor.has("tickEnabled")) {
            template.tickEnabled(actor.reqBoolean("tickEnabled"));
        }
        return template;
    }

    private static final class JsonObject {
        final Map<String, Object> fields = new LinkedHashMap<>();

        boolean has(String key) {
            return fields.containsKey(key);
        }

        String reqString(String key) {
            Object value = fields.get(key);
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("expected string \"" + key + "\"");
            }
            return (String) value;
        }

        boolean reqBoolean(String key) {
            Object value = fields.get(key);
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("expected boolean \"" + key + "\"");
            }
            return (Boolean) value;
        }

        JsonArray reqArray(String key) {
            Object value = fields.get(key);
            if (!(value instanceof JsonArray)) {
                throw new IllegalArgumentException("expected array \"" + key + "\"");
            }
            return (JsonArray) value;
        }

        float[] reqVec3(String key) {
            JsonArray array = reqArray(key);
            if (array.size() != 3) {
                throw new IllegalArgumentException("expected [x,y,z] for \"" + key + "\"");
            }
            return new float[]{array.reqNumber(0), array.reqNumber(1), array.reqNumber(2)};
        }
    }

    private static final class JsonArray {
        final List<Object> items = new ArrayList<>();

        int size() {
            return items.size();
        }

        JsonObject reqObject(int index) {
            Object value = items.get(index);
            if (!(value instanceof JsonObject)) {
                throw new IllegalArgumentException("expected object at " + index);
            }
            return (JsonObject) value;
        }

        float reqNumber(int index) {
            Object value = items.get(index);
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("expected number at " + index);
            }
            return ((Number) value).floatValue();
        }
    }

    private static final class Reader {
        private final String src;
        private int i;

        Reader(String src) {
            this.src = src;
        }

        Object parseValue() {
            skipWs();
            if (i >= src.length()) {
                throw new IllegalArgumentException("unexpected end of level json");
            }
            char c = src.charAt(i);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                parseNull();
                return null;
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }
            throw new IllegalArgumentException("unexpected char in level json at " + i);
        }

        private JsonObject parseObject() {
            expect('{');
            JsonObject object = new JsonObject();
            skipWs();
            if (peek('}')) {
                i++;
                return object;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                object.fields.put(key, parseValue());
                skipWs();
                if (peek('}')) {
                    i++;
                    return object;
                }
                expect(',');
            }
        }

        private JsonArray parseArray() {
            expect('[');
            JsonArray array = new JsonArray();
            skipWs();
            if (peek(']')) {
                i++;
                return array;
            }
            while (true) {
                array.items.add(parseValue());
                skipWs();
                if (peek(']')) {
                    i++;
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (i < src.length()) {
                char c = src.charAt(i++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (i >= src.length()) {
                        throw new IllegalArgumentException("unterminated escape");
                    }
                    char e = src.charAt(i++);
                    switch (e) {
                        case '"':
                        case '\\':
                        case '/':
                            out.append(e);
                            break;
                        case 'n':
                            out.append('\n');
                            break;
                        case 't':
                            out.append('\t');
                            break;
                        case 'r':
                            out.append('\r');
                            break;
                        default:
                            throw new IllegalArgumentException("bad escape \\" + e);
                    }
                } else {
                    out.append(c);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("expected true/false at " + i);
        }

        private void parseNull() {
            if (!src.startsWith("null", i)) {
                throw new IllegalArgumentException("expected null at " + i);
            }
            i += 4;
        }

        private Number parseNumber() {
            int start = i;
            if (peek('-')) {
                i++;
            }
            digits();
            if (peek('.')) {
                i++;
                digits();
            }
            if (peek('e') || peek('E')) {
                i++;
                if (peek('+') || peek('-')) {
                    i++;
                }
                digits();
            }
            String raw = src.substring(start, i);
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                return Double.valueOf(raw);
            }
            long whole = Long.parseLong(raw);
            if (whole >= Integer.MIN_VALUE && whole <= Integer.MAX_VALUE) {
                return (int) whole;
            }
            return whole;
        }

        private void digits() {
            int start = i;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (c < '0' || c > '9') {
                    break;
                }
                i++;
            }
            if (i == start) {
                throw new IllegalArgumentException("expected digit at " + i);
            }
        }

        private void skipWs() {
            while (i < src.length()) {
                char c = src.charAt(i);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    break;
                }
                i++;
            }
        }

        private boolean peek(char c) {
            return i < src.length() && src.charAt(i) == c;
        }

        private void expect(char c) {
            skipWs();
            if (!peek(c)) {
                throw new IllegalArgumentException("expected '" + c + "' at " + i);
            }
            i++;
        }
    }
}

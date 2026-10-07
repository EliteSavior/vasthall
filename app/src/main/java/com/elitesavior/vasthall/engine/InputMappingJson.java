package com.elitesavior.vasthall.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses an Input Mapping Context JSON document.
 *
 * <p>Schema:
 * <pre>
 * {
 *   "name": "Default",
 *   "actions": [
 *     { "name": "Jump", "valueType": "DIGITAL" }
 *   ],
 *   "mappings": [
 *     { "action": "Jump", "key": "Space" },
 *     { "action": "Move", "key": "W", "axis": "Y", "scale": 1 }
 *   ]
 * }
 * </pre>
 */
public final class InputMappingJson {
    public static final String DEFAULT_RESOURCE = "input/DefaultMapping.json";

    private InputMappingJson() {
    }

    public static InputMappingContext loadDefault() {
        InputStream stream = InputMappingJson.class.getClassLoader()
                .getResourceAsStream(DEFAULT_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("missing " + DEFAULT_RESOURCE);
        }
        try (InputStream in = stream) {
            return parse(readAll(in));
        } catch (IOException failed) {
            throw new IllegalStateException("failed to read " + DEFAULT_RESOURCE, failed);
        }
    }

    public static InputMappingContext parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("input mapping json");
        }
        Object rootValue = new Reader(json).parseValue();
        if (!(rootValue instanceof JsonObject)) {
            throw new IllegalArgumentException("input mapping json must be an object");
        }
        JsonObject root = (JsonObject) rootValue;
        InputMappingContext context = new InputMappingContext(root.reqString("name"));
        if (root.has("actions")) {
            JsonArray actions = root.reqArray("actions");
            for (int i = 0; i < actions.size(); i++) {
                JsonObject row = actions.reqObject(i);
                context.addAction(new InputAction(
                        row.reqString("name"),
                        parseValueType(row.reqString("valueType"))));
            }
        }
        if (root.has("mappings")) {
            JsonArray mappings = root.reqArray("mappings");
            for (int i = 0; i < mappings.size(); i++) {
                context.map(parseMapping(mappings.reqObject(i)));
            }
        }
        return context;
    }

    private static InputMapping parseMapping(JsonObject row) {
        String action = row.reqString("action");
        String key = row.reqString("key");
        InputMapping.Axis axis = InputMapping.Axis.NONE;
        float scale = 1.0f;
        if (row.has("axis")) {
            axis = parseAxis(row.reqString("axis"));
        }
        if (row.has("scale")) {
            scale = row.reqNumber("scale");
        }
        return new InputMapping(action, key, axis, scale);
    }

    private static InputValueType parseValueType(String raw) {
        try {
            return InputValueType.valueOf(raw.trim().toUpperCase(Locale.US));
        } catch (RuntimeException failed) {
            throw new IllegalArgumentException("unknown valueType: " + raw);
        }
    }

    private static InputMapping.Axis parseAxis(String raw) {
        String token = raw.trim().toUpperCase(Locale.US);
        if ("X".equals(token)) {
            return InputMapping.Axis.X;
        }
        if ("Y".equals(token)) {
            return InputMapping.Axis.Y;
        }
        if ("NONE".equals(token) || token.isEmpty()) {
            return InputMapping.Axis.NONE;
        }
        throw new IllegalArgumentException("unknown axis: " + raw);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
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

        JsonArray reqArray(String key) {
            Object value = fields.get(key);
            if (!(value instanceof JsonArray)) {
                throw new IllegalArgumentException("expected array \"" + key + "\"");
            }
            return (JsonArray) value;
        }

        float reqNumber(String key) {
            Object value = fields.get(key);
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("expected number \"" + key + "\"");
            }
            return ((Number) value).floatValue();
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
                throw new IllegalArgumentException("unexpected end of input mapping json");
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
            throw new IllegalArgumentException("unexpected char in input mapping json at " + i);
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

package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JSON encode/decode for {@link SaveGame}. Format version 1:
 * {@code version}, {@code level}, {@code gameMode}, {@code actors[]}.
 */
final class SaveGameJson {
    private SaveGameJson() {
    }

    static String write(SaveGame save) {
        StringBuilder out = new StringBuilder();
        out.append('{');
        writeKey(out, "version").append(save.version()).append(',');
        writeKey(out, "level");
        writeString(out, save.levelName()).append(',');
        writeKey(out, "gameMode");
        writeString(out, save.gameMode()).append(',');
        writeKey(out, "actors").append('[');
        List<SaveGame.ActorRecord> actors = save.actors();
        for (int i = 0; i < actors.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            writeActor(out, actors.get(i));
        }
        out.append("]}");
        return out.toString();
    }

    static SaveGame parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("save json");
        }
        Object rootValue = new Reader(json).parseValue();
        if (!(rootValue instanceof JsonObject)) {
            throw new IllegalArgumentException("save json must be an object");
        }
        JsonObject root = (JsonObject) rootValue;
        SaveGame save = new SaveGame();
        if (root.has("version")) {
            int version = root.reqInt("version");
            if (version < 1 || version > SaveGame.FORMAT_VERSION) {
                throw new IllegalArgumentException("unsupported save version: " + version);
            }
            save.setVersion(version);
        }
        if (root.has("level")) {
            save.setLevelName(root.reqString("level"));
        }
        if (root.has("gameMode")) {
            save.setGameMode(root.reqString("gameMode"));
        }
        if (root.has("actors")) {
            JsonArray actors = root.reqArray("actors");
            for (int i = 0; i < actors.size(); i++) {
                save.addActor(parseActor(actors.reqObject(i)));
            }
        }
        return save;
    }

    private static void writeActor(StringBuilder out, SaveGame.ActorRecord actor) {
        out.append('{');
        writeKey(out, "name");
        writeString(out, actor.name()).append(',');
        writeKey(out, "class");
        writeString(out, actor.className()).append(',');
        writeKey(out, "level");
        writeString(out, actor.levelName()).append(',');
        writeKey(out, "location");
        writeVec3(out, actor.location()).append(',');
        writeKey(out, "rotation");
        writeRotator(out, actor.rotation()).append(',');
        writeKey(out, "scale");
        writeVec3(out, actor.scale()).append(',');
        writeKey(out, "tickEnabled").append(actor.tickEnabled()).append(',');
        writeKey(out, "tags").append('[');
        List<String> tags = actor.tags();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            writeString(out, tags.get(i));
        }
        out.append("]}");
    }

    private static SaveGame.ActorRecord parseActor(JsonObject actor) {
        SaveGame.ActorRecord record = new SaveGame.ActorRecord();
        if (actor.has("name")) {
            record.setName(actor.reqString("name"));
        }
        if (actor.has("class")) {
            record.setClassName(actor.reqString("class"));
        }
        if (actor.has("level")) {
            record.setLevelName(actor.reqString("level"));
        }
        if (actor.has("location")) {
            float[] loc = actor.reqVec3("location");
            record.setLocation(loc[0], loc[1], loc[2]);
        }
        if (actor.has("rotation")) {
            float[] rot = actor.reqVec3("rotation");
            record.setRotation(rot[0], rot[1], rot[2]);
        }
        if (actor.has("scale")) {
            float[] scale = actor.reqVec3("scale");
            record.setScale(scale[0], scale[1], scale[2]);
        }
        if (actor.has("tickEnabled")) {
            record.setTickEnabled(actor.reqBoolean("tickEnabled"));
        }
        if (actor.has("tags")) {
            JsonArray tags = actor.reqArray("tags");
            for (int i = 0; i < tags.size(); i++) {
                record.addTag(tags.reqString(i));
            }
        }
        return record;
    }

    private static StringBuilder writeKey(StringBuilder out, String key) {
        writeString(out, key).append(':');
        return out;
    }

    private static StringBuilder writeString(StringBuilder out, String value) {
        out.append('"');
        String text = value == null ? "" : value;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    out.append(c);
            }
        }
        out.append('"');
        return out;
    }

    private static StringBuilder writeVec3(StringBuilder out, Vec3 value) {
        out.append('[');
        writeNumber(out, value.x).append(',');
        writeNumber(out, value.y).append(',');
        writeNumber(out, value.z).append(']');
        return out;
    }

    private static StringBuilder writeRotator(StringBuilder out, Rotator value) {
        out.append('[');
        writeNumber(out, value.pitch).append(',');
        writeNumber(out, value.yaw).append(',');
        writeNumber(out, value.roll).append(']');
        return out;
    }

    private static StringBuilder writeNumber(StringBuilder out, float value) {
        if (value == (long) value) {
            out.append((long) value);
        } else {
            out.append(String.format(Locale.US, "%s", value));
        }
        return out;
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

        int reqInt(String key) {
            Object value = fields.get(key);
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("expected number \"" + key + "\"");
            }
            return ((Number) value).intValue();
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

        String reqString(int index) {
            Object value = items.get(index);
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("expected string at " + index);
            }
            return (String) value;
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
                throw new IllegalArgumentException("unexpected end of save json");
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
            throw new IllegalArgumentException("unexpected char in save json at " + i);
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

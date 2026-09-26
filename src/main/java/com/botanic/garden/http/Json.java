package com.botanic.garden.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器 / 序列化器（零依赖）。
 * 支持对象、数组、字符串、数字、布尔、null。
 */
public final class Json {

    private Json() {}

    // ---------------- 解析 ----------------

    public static Object parse(String text) {
        return new Parser(text).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("请求体必须是 JSON 对象");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s == null ? "" : s; }

        Object parseValue() {
            skipWs();
            if (pos >= s.length()) throw error("内容为空");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default:  return parseNumber();
            }
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (peek('}')) { pos++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (!peek(':')) throw error("缺少 ':'");
                pos++;
                map.put(key, parseValue());
                skipWs();
                if (peek(',')) { pos++; continue; }
                if (peek('}')) { pos++; break; }
                throw error("缺少 ',' 或 '}'");
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (peek(']')) { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (peek(',')) { pos++; continue; }
                if (peek(']')) { pos++; break; }
                throw error("缺少 ',' 或 ']'");
            }
            return list;
        }

        private String parseString() {
            if (!peek('"')) throw error("缺少字符串");
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= s.length()) break;
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > s.length()) throw error("非法 \\u 转义");
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: throw error("非法转义 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("字符串未闭合");
        }

        private Object parseNumber() {
            int start = pos;
            if (peek('-')) pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            if (peek('.')) { pos++; while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++; }
            if (peek('e') || peek('E')) {
                pos++;
                if (peek('+') || peek('-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (start == pos) throw error("非法数值");
            return Double.parseDouble(s.substring(start, pos));
        }

        private void expect(String word) {
            if (!s.startsWith(word, pos)) throw error("应为 " + word);
            pos += word.length();
        }

        private boolean peek(char c) { return pos < s.length() && s.charAt(pos) == c; }

        private void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++; else break;
            }
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON 解析失败（位置 " + pos + "）：" + msg);
        }
    }

    // ---------------- 序列化 ----------------

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object v, StringBuilder sb) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String) { writeString((String) v, sb); return; }
        if (v instanceof Boolean) { sb.append(v); return; }
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
            return;
        }
        if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(e.getKey(), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<Object>) v) {
                if (!first) sb.append(',');
                first = false;
                write(item, sb);
            }
            sb.append(']');
            return;
        }
        writeString(v.toString(), sb);
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---------------- 取值辅助 ----------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    public static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    public static Double asDouble(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static Boolean asBoolean(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        return null;
    }
}

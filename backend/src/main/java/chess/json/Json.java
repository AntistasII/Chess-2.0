package chess.json;

import java.util.*;

/**
 * Преобразование данных локального API в JSON и обратно без внешних библиотек.
 */
public final class Json {
    private Json() {
    }

    /** Кодирует простые значения, словари и списки, используемые API. */
    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + escape(s) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            var entries = new ArrayList<String>();
            map.forEach((k, v) -> entries.add(stringify(k.toString()) + ":" + stringify(v)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Iterable<?> list) {
            var entries = new ArrayList<String>();
            list.forEach(v -> entries.add(stringify(v)));
            return "[" + String.join(",", entries) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }

    /** Экранирует кавычки, обратную косую черту и управляющие символы. */
    private static String escape(String s) {
        var out = new StringBuilder();
        for (char c : s.toCharArray())
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        return out.toString();
    }

    /** Принимает только JSON-объект на верхнем уровне запроса. */
    public static Map<String, Object> object(String s) {
        Object value = new Parser(s).parse();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        var result = new LinkedHashMap<String, Object>();
        map.forEach((k, v) -> result.put((String) k, v));
        return result;
    }

    /** Рекурсивный разбор JSON; курсор i указывает на следующий непрочитанный символ. */
    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        /** Не допускает лишние символы после завершённого значения. */
        Object parse() {
            Object v = value();
            ws();
            if (i != s.length()) fail();
            return v;
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        void fail() {
            throw new IllegalArgumentException("Invalid JSON at " + i);
        }

        char next() {
            if (i >= s.length()) fail();
            return s.charAt(i++);
        }

        /** Разбирает строку, объект, массив, логическое значение, null или число. */
        Object value() {
            ws();
            if (i >= s.length()) {
                fail();
                return null;
            }
            char c = s.charAt(i);
            if (c == '"') return string();
            if (c == '{') {
                i++;
                var map = new LinkedHashMap<String, Object>();
                ws();
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    return map;
                }
                while (true) {
                    ws();
                    if (i >= s.length() || s.charAt(i) != '"') fail();
                    String key = string();
                    ws();
                    if (next() != ':') fail();
                    map.put(key, value());
                    ws();
                    char end = next();
                    if (end == '}') return map;
                    if (end != ',') fail();
                }
            }
            if (c == '[') {
                i++;
                var list = new ArrayList<Object>();
                ws();
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                    return list;
                }
                while (true) {
                    list.add(value());
                    ws();
                    char end = next();
                    if (end == ']') return list;
                    if (end != ',') fail();
                }
            }
            for (String token : List.of("true", "false", "null"))
                if (s.startsWith(token, i)) {
                    i += token.length();
                    return token.equals("null") ? null : token.equals("true");
                }
            int start = i;
            while (i < s.length() && "-+0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) fail();
            String n = s.substring(start, i);
            return n.contains(".") || n.contains("e") || n.contains("E") ? Double.valueOf(n) : Long.valueOf(n);
        }

        /** Читает строку с экранированием и последовательностями Unicode. */
        String string() {
            if (next() != '"') fail();
            var out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return out.toString();
                if (c < 32) fail();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) fail();
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> fail();
                }
            }
        }
    }
}

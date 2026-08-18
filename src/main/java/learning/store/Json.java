package learning.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return '"' + escape(text) + '"';
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(write(entry.getKey().toString())).append(':').append(write(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                first = false;
                out.append(write(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Cannot encode " + value.getClass().getName());
    }

    static Object read(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (parser.position != source.length()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String source;
        private int position;

        private Parser(String source) { this.source = source; }

        private void space() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }

        private Object value() {
            space();
            if (position >= source.length()) throw new IllegalArgumentException("Empty JSON value");
            return switch (source.charAt(position)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            position++;
            Map<String, Object> result = new LinkedHashMap<>();
            space();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space();
                expect(':');
                result.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            position++;
            List<Object> result = new ArrayList<>();
            space();
            if (take(']')) return result;
            do {
                result.add(value());
                space();
            } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < source.length()) {
                char c = source.charAt(position++);
                if (c == '"') return result.toString();
                if (c != '\\') {
                    result.append(c);
                    continue;
                }
                if (position >= source.length()) throw new IllegalArgumentException("Incomplete JSON escape");
                char escaped = source.charAt(position++);
                result.append(switch (escaped) {
                    case '"', '\\', '/' -> escaped;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> unicode();
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                });
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private char unicode() {
            if (position + 4 > source.length()) throw new IllegalArgumentException("Incomplete unicode escape");
            char value = (char) Integer.parseInt(source.substring(position, position + 4), 16);
            position += 4;
            return value;
        }

        private Object number() {
            int start = position;
            while (position < source.length() && "-+0123456789.eE".indexOf(source.charAt(position)) >= 0) position++;
            String token = source.substring(start, position);
            try {
                return token.contains(".") || token.contains("e") || token.contains("E")
                        ? Double.parseDouble(token) : Long.parseLong(token);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid JSON number", error);
            }
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, position)) throw new IllegalArgumentException("Invalid JSON literal");
            position += token.length();
            return value;
        }

        private boolean take(char wanted) {
            if (position < source.length() && source.charAt(position) == wanted) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char wanted) {
            if (!take(wanted)) throw new IllegalArgumentException("Expected " + wanted);
        }
    }
}

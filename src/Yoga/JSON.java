    // Port of Yoga/JSON.js: stringify/parse over this backend's representation
    // (null, Boolean, Double, String, Object[], LinkedHashMap). `undefined`
    // collapses to null, like Foreign.isUndefined does in this backend.
    public static Object _undefined = null;

    private static String __yogaQuote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (current < 0x20) out.append(String.format("\\u%04x", (int) current));
                    else out.append(current);
            }
        }
        return out.append('"').toString();
    }

    private static String __yogaNumber(Number value) {
        double number = value.doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number)) return "null";
        if (number == Math.rint(number) && Math.abs(number) < 1.0e15) return Long.toString((long) number);
        return Double.toString(number);
    }

    private static void __yogaWrite(StringBuilder out, Object value, int indent, int level) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof Boolean) {
            out.append(((Boolean) value) ? "true" : "false");
            return;
        }
        if (value instanceof Number) {
            out.append(__yogaNumber((Number) value));
            return;
        }
        if (value instanceof String) {
            out.append(__yogaQuote((String) value));
            return;
        }
        if (value instanceof Object[] || value instanceof java.util.List) {
            Object[] items = value instanceof Object[] ? (Object[]) value : ((java.util.List<?>) value).toArray();
            if (items.length == 0) { out.append("[]"); return; }
            out.append('[');
            for (int index = 0; index < items.length; index++) {
                if (index > 0) out.append(',');
                if (indent > 0) { out.append('\n'); out.append(" ".repeat(indent * (level + 1))); }
                __yogaWrite(out, items[index], indent, level + 1);
            }
            if (indent > 0) { out.append('\n'); out.append(" ".repeat(indent * level)); }
            out.append(']');
            return;
        }
        if (value instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
            if (map.isEmpty()) { out.append("{}"); return; }
            out.append('{');
            boolean first = true;
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                if (indent > 0) { out.append('\n'); out.append(" ".repeat(indent * (level + 1))); }
                out.append(__yogaQuote(String.valueOf(entry.getKey())));
                out.append(indent > 0 ? ": " : ":");
                __yogaWrite(out, entry.getValue(), indent, level + 1);
            }
            if (indent > 0) { out.append('\n'); out.append(" ".repeat(indent * level)); }
            out.append('}');
            return;
        }
        out.append("null");
    }

    private static String __yogaStringify(Object value, int indent) {
        StringBuilder out = new StringBuilder();
        __yogaWrite(out, value, indent, 0);
        return out.toString();
    }

    public static Object _unsafeStringify = (java.util.function.Function<Object, Object>) (value) ->
        __yogaStringify(value, 0);

    public static Object _unsafePrettyStringify = (java.util.function.Function<Object, Object>) (spaces) ->
        (java.util.function.Function<Object, Object>) (value) ->
            __yogaStringify(value, Math.max(0, ((Number) spaces).intValue()));

    public static Object _parseJSON = (java.util.function.Function<Object, Object>) (input) ->
        (java.util.function.Supplier<Object>) () -> __yogaParse((String) input);

    private static Object __yogaParse(String input) {
        int[] index = { 0 };
        Object value = __yogaValue(input, index);
        __yogaSkip(input, index);
        if (index[0] != input.length()) throw new IllegalArgumentException("Unexpected JSON characters");
        return value;
    }

    private static void __yogaSkip(String input, int[] index) {
        while (index[0] < input.length()) {
            char current = input.charAt(index[0]);
            if (current == ' ' || current == '\t' || current == '\n' || current == '\r') index[0]++;
            else break;
        }
    }

    private static char __yogaPeek(String input, int[] index) {
        if (index[0] >= input.length()) throw new IllegalArgumentException("Unexpected end of JSON input");
        return input.charAt(index[0]);
    }

    private static Object __yogaValue(String input, int[] index) {
        __yogaSkip(input, index);
        switch (__yogaPeek(input, index)) {
            case 'n': __yogaWord(input, index, "null"); return null;
            case 't': __yogaWord(input, index, "true"); return Boolean.TRUE;
            case 'f': __yogaWord(input, index, "false"); return Boolean.FALSE;
            case '"': return __yogaString(input, index);
            case '[': return __yogaArray(input, index);
            case '{': return __yogaObject(input, index);
            default: return __yogaNumberParse(input, index);
        }
    }

    private static void __yogaWord(String input, int[] index, String word) {
        if (!input.startsWith(word, index[0])) throw new IllegalArgumentException("Invalid JSON literal");
        index[0] += word.length();
    }

    private static String __yogaString(String input, int[] index) {
        index[0]++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (index[0] >= input.length()) throw new IllegalArgumentException("Unterminated JSON string");
            char current = input.charAt(index[0]++);
            if (current == '"') return out.toString();
            if (current != '\\') { out.append(current); continue; }
            char escape = input.charAt(index[0]++);
            switch (escape) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    out.append((char) Integer.parseInt(input.substring(index[0], index[0] + 4), 16));
                    index[0] += 4;
                    break;
                default: throw new IllegalArgumentException("Invalid JSON escape");
            }
        }
    }

    private static Object __yogaNumberParse(String input, int[] index) {
        int start = index[0];
        if (__yogaPeek(input, index) == '-') index[0]++;
        while (index[0] < input.length() && Character.isDigit(input.charAt(index[0]))) index[0]++;
        if (index[0] < input.length() && input.charAt(index[0]) == '.') {
            index[0]++;
            while (index[0] < input.length() && Character.isDigit(input.charAt(index[0]))) index[0]++;
        }
        if (index[0] < input.length() && (input.charAt(index[0]) == 'e' || input.charAt(index[0]) == 'E')) {
            index[0]++;
            if (index[0] < input.length() && (input.charAt(index[0]) == '+' || input.charAt(index[0]) == '-')) index[0]++;
            while (index[0] < input.length() && Character.isDigit(input.charAt(index[0]))) index[0]++;
        }
        if (index[0] == start) throw new IllegalArgumentException("Invalid JSON value");
        return Double.parseDouble(input.substring(start, index[0]));
    }

    private static Object __yogaArray(String input, int[] index) {
        index[0]++;
        java.util.List<Object> items = new java.util.ArrayList<>();
        __yogaSkip(input, index);
        if (__yogaPeek(input, index) == ']') { index[0]++; return items.toArray(); }
        while (true) {
            items.add(__yogaValue(input, index));
            __yogaSkip(input, index);
            char current = __yogaPeek(input, index);
            if (current == ',') { index[0]++; continue; }
            if (current == ']') { index[0]++; return items.toArray(); }
            throw new IllegalArgumentException("Expected ',' or ']'");
        }
    }

    private static Object __yogaObject(String input, int[] index) {
        index[0]++;
        java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
        __yogaSkip(input, index);
        if (__yogaPeek(input, index) == '}') { index[0]++; return fields; }
        while (true) {
            __yogaSkip(input, index);
            String key = __yogaString(input, index);
            __yogaSkip(input, index);
            if (__yogaPeek(input, index) != ':') throw new IllegalArgumentException("Expected ':'");
            index[0]++;
            fields.put(key, __yogaValue(input, index));
            __yogaSkip(input, index);
            char current = __yogaPeek(input, index);
            if (current == ',') { index[0]++; continue; }
            if (current == '}') { index[0]++; return fields; }
            throw new IllegalArgumentException("Expected ',' or '}'");
        }
    }

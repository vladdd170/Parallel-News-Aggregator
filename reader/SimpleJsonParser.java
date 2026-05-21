package reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsez JSON folosit pentru articole.
 * Functioneaza printr-o parcurgere caracter cu caracter
 */
public class SimpleJsonParser {

    /**
     * Parsez un array JSON care contine obiecte.
     * Identifica obiectele prin numararea acoladelor
     * si sare peste zonele din interiorul string-urilor
     */
    public static List<JsonObject> parseArray(String json) {
        List<JsonObject> result = new ArrayList<>();

        int depth = 0; // nivel de imbricare al acoladelor
        int start = -1; // index la care inecepe un obiect
        boolean inString = false; // true cand e in int unui string
        boolean escaped = false; // gestioneaza caractere cu backslash

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    String objStr = json.substring(start, i + 1);
                    JsonObject obj = parseObject(objStr);
                    if (obj != null) {
                        result.add(obj);
                    }
                    start = -1;
                }
            }
        }

        return result;
    }

    /**
     * Parsez un singur obiect JSON.
     */
    private static JsonObject parseObject(String json) {
        Map<String, Object> map = new HashMap<>();
        int idx = 0;
        int len = json.length();

        // Cautam prima acolada "{"
        while (idx < len && Character.isWhitespace(json.charAt(idx))) {
            idx++;
        }
        if (idx >= len || json.charAt(idx) != '{') {
            return null;
        }
        idx++; // sarim peste "{"

        while (idx < len) {
            // Sarim peste spatii si delimitatori
            idx = skipWhitespace(json, idx);
            if (idx >= len) {
                break;
            }

            char ch = json.charAt(idx);
            if (ch == '}') {
                idx++;
                break;
            }
            // Cheia trebuie sa fie un string intre ghilimele
            if (ch != '"') {
                idx++;
                continue;
            }
            ParseResult keyRes = readString(json, idx + 1);
            if (!keyRes.valid) {
                return null;
            }
            String key = keyRes.value;
            idx = skipWhitespace(json, keyRes.nextIndex);
            if (idx >= len || json.charAt(idx) != ':') {
                return null;
            }
            idx = skipWhitespace(json, idx + 1);
            if (idx >= len) {
                return null;
            }

            char valStart = json.charAt(idx);
            if (valStart == '"') {
                ParseResult valRes = readString(json, idx + 1);
                if (!valRes.valid) {
                    return null;
                }
                map.put(key, valRes.value);
                idx = valRes.nextIndex;
            } else if (valStart == '[') {
                ParseArrayResult arrRes = readStringArray(json, idx + 1);
                if (!arrRes.valid) {
                    return null;
                }
                map.put(key, arrRes.values);
                idx = arrRes.nextIndex;
            } else if (valStart == 'n') { // null
                idx += 4; // Valoarea ignorata
            } else {
                // Valorea necunoscuta skip
                idx++;
            }

            idx = skipWhitespace(json, idx);
            if (idx < len && json.charAt(idx) == ',') {
                idx++;
            }
        }

        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            obj.put(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    /**
    Sare peste whitespace
     */
    private static int skipWhitespace(String json, int idx) {
        int len = json.length();
        while (idx < len && Character.isWhitespace(json.charAt(idx))) {
            idx++;
        }
        return idx;
    }

    /**
     * Citeste un string JSON, tratand caracterele escape
     */
    private static ParseResult readString(String json, int idx) {
        StringBuilder sb = new StringBuilder();
        int len = json.length();
        boolean escaped = false;
        while (idx < len) {
            char c = json.charAt(idx);
            if (escaped) {
                switch (c) {
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(c);
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        // Parsam secventele unicod
                        if (idx + 4 < len) {
                            String hex = json.substring(idx + 1, idx + 5);
                            try {
                                char unicodeChar = (char) Integer.parseInt(hex, 16);
                                sb.append(unicodeChar);
                                idx += 4;
                            } catch (NumberFormatException e) {
                                sb.append('u');
                            }
                        } else {
                            sb.append('u');
                        }
                        break;
                    default:
                        sb.append(c);
                        break;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return new ParseResult(true, sb.toString(), idx + 1);
            } else {
                sb.append(c);
            }
            idx++;
        }
        return new ParseResult(false, "", idx);
    }

    /**
     * Citeste un array de string-uri
     */
    private static ParseArrayResult readStringArray(String json, int idx) {
        List<String> values = new ArrayList<>();
        int len = json.length();
        while (idx < len) {
            idx = skipWhitespace(json, idx);
            if (idx >= len) {
                return new ParseArrayResult(false, values, idx);
            }
            char c = json.charAt(idx);
            if (c == ']') {
                return new ParseArrayResult(true, values, idx + 1);
            }
            if (c != '"') {
                // skip daca nu incepe cu ghilimele
                idx++;
                continue;
            }
            ParseResult item = readString(json, idx + 1);
            if (!item.valid) {
                return new ParseArrayResult(false, values, idx);
            }
            values.add(item.value);
            idx = skipWhitespace(json, item.nextIndex);
            if (idx < len && json.charAt(idx) == ',') {
                idx++;
            }
        }
        return new ParseArrayResult(false, values, idx);
    }

    /** Rezultatul citirii unui string JSON */
    private static class ParseResult {
        final boolean valid;
        final String value;
        final int nextIndex;

        ParseResult(boolean valid, String value, int nextIndex) {
            this.valid = valid;
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }

    /** Rezultatul citirii unui array JSON de string-uri */
    private static class ParseArrayResult {
        final boolean valid;
        final List<String> values;
        final int nextIndex;

        ParseArrayResult(boolean valid, List<String> values, int nextIndex) {
            this.valid = valid;
            this.values = values;
            this.nextIndex = nextIndex;
        }
    }

    /**
     * Obiect JSON simplificat.
     * Valorile pot fi: sring, lista de sring-uri, null
     */
    public static class JsonObject {
        private final java.util.Map<String, Object> map = new java.util.HashMap<>();

        public void put(String key, Object value) {
            map.put(key, value);
        }

        public String optString(String key, String defaultValue) {
            Object value = map.get(key);
            if (value == null) {
                return defaultValue;
            }
            return value.toString();
        }

        public boolean has(String key) {
            return map.containsKey(key);
        }

        public boolean isNull(String key) {
            return map.get(key) == null;
        }

        @SuppressWarnings("unchecked")
        public List<String> getStringList(String key) {
            Object value = map.get(key);
            if (value instanceof List) {
                return (List<String>) value;
            }
            return new ArrayList<>();
        }
    }
}

package com.dziubek.boxpvp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimalny, bezzależnościowy parser JSON (bez Gson - niepewne czy jest dostępny w compile-time
 * na classpath paper-api). Zwraca zagnieżdżone Map&lt;String,Object&gt;/List&lt;Object&gt;/String/
 * Double/Boolean/null - wystarczające do odczytania własnych plików modeli (CrateModel).
 */
public final class SimpleJson {

    private final String s;
    private int i;

    private SimpleJson(String s) {
        this.s = s;
        this.i = 0;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        SimpleJson p = new SimpleJson(json);
        p.skipWs();
        Object v = p.parseValue();
        return (Map<String, Object>) v;
    }

    private Object parseValue() {
        skipWs();
        char c = s.charAt(i);
        if (c == '{') return parseObj();
        if (c == '[') return parseArr();
        if (c == '"') return parseStr();
        if (c == 't') { i += 4; return Boolean.TRUE; }
        if (c == 'f') { i += 5; return Boolean.FALSE; }
        if (c == 'n') { i += 4; return null; }
        return parseNum();
    }

    private Map<String, Object> parseObj() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++; // {
        skipWs();
        if (s.charAt(i) == '}') { i++; return map; }
        while (true) {
            skipWs();
            String key = parseStr();
            skipWs();
            i++; // :
            Object val = parseValue();
            map.put(key, val);
            skipWs();
            char c = s.charAt(i);
            if (c == ',') { i++; continue; }
            if (c == '}') { i++; break; }
        }
        return map;
    }

    private List<Object> parseArr() {
        List<Object> list = new ArrayList<>();
        i++; // [
        skipWs();
        if (s.charAt(i) == ']') { i++; return list; }
        while (true) {
            Object val = parseValue();
            list.add(val);
            skipWs();
            char c = s.charAt(i);
            if (c == ',') { i++; continue; }
            if (c == ']') { i++; break; }
        }
        return list;
    }

    private String parseStr() {
        StringBuilder sb = new StringBuilder();
        i++; // opening quote
        while (s.charAt(i) != '"') {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                char esc = s.charAt(i);
                switch (esc) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'u':
                        String hex = s.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                        break;
                    default: sb.append(esc);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        i++; // closing quote
        return sb.toString();
    }

    private Double parseNum() {
        int start = i;
        while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        return Double.parseDouble(s.substring(start, i));
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }
}

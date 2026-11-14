package br.store.capivariashop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Locale;

/** Leitura tolerante de JSON: tenta várias chaves possíveis para o mesmo campo. */
final class J {

    static String str(JSONObject o, String... keys) {
        if (o == null) return null;
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) {
                Object v = o.opt(k);
                if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                    String s = String.valueOf(v);
                    if (!s.isEmpty()) return s;
                }
            }
        }
        return null;
    }

    static double num(JSONObject o, double def, String... keys) {
        if (o == null) return def;
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) {
                Object v = o.opt(k);
                if (v instanceof Number) return ((Number) v).doubleValue();
                if (v instanceof String) {
                    try {
                        return Double.parseDouble(((String) v).replace(",", "."));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return def;
    }

    static boolean bool(JSONObject o, String... keys) {
        if (o == null) return false;
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) {
                Object v = o.opt(k);
                if (v instanceof Boolean) return (Boolean) v;
                if (v instanceof Number) return ((Number) v).intValue() != 0;
                if (v instanceof String) {
                    String s = ((String) v).toLowerCase(Locale.ROOT);
                    if (s.equals("true") || s.equals("1") || s.equals("sim")) return true;
                }
            }
        }
        return false;
    }

    static JSONArray arr(JSONObject o, String key) {
        JSONArray a = o == null ? null : o.optJSONArray(key);
        return a != null ? a : new JSONArray();
    }

    static String findDeep(JSONObject o, String key) {
        if (o == null) return null;
        if (o.has(key) && !o.isNull(key) && o.opt(key) instanceof String) {
            return o.optString(key);
        }
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            Object v = o.opt(it.next());
            if (v instanceof JSONObject) {
                String r = findDeep((JSONObject) v, key);
                if (r != null) return r;
            }
        }
        return null;
    }

    static String money(double v) {
        return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(v);
    }

    private J() {}
}

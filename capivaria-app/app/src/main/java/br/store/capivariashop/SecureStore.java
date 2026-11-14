package br.store.capivariashop;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Guarda tokens cifrados com uma chave AES que fica no Android Keystore (nunca sai do aparelho). */
final class SecureStore {
    private static final String ALIAS = "capivaria_store_key";
    private static final String PREFS = "secure_store";
    private static final String BLOB = "blob";

    private final SharedPreferences prefs;
    private JSONObject data = new JSONObject();

    SecureStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    private void load() {
        String s = prefs.getString(BLOB, null);
        if (s == null) return;
        try {
            byte[] all = Base64.decode(s, Base64.NO_WRAP);
            byte[] iv = Arrays.copyOfRange(all, 0, 12);
            byte[] ct = Arrays.copyOfRange(all, 12, all.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            data = new JSONObject(new String(c.doFinal(ct), "UTF-8"));
        } catch (Exception e) {
            data = new JSONObject();
            prefs.edit().remove(BLOB).apply();
        }
    }

    private void save() {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(data.toString().getBytes("UTF-8"));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            prefs.edit().putString(BLOB, Base64.encodeToString(all, Base64.NO_WRAP)).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized String get(String k) {
        return data.has(k) && !data.isNull(k) ? data.optString(k) : null;
    }

    synchronized void put(String k, String v) {
        if (v == null) return;
        try {
            data.put(k, v);
        } catch (Exception ignored) {
        }
        save();
    }

    synchronized void remove(String... keys) {
        for (String k : keys) data.remove(k);
        save();
    }
}

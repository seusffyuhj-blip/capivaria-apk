package br.store.capivariashop;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Login OAuth 2.0 (Authorization Code + PKCE S256) pelo navegador do sistema.
 * O app nunca vê a senha da loja e não guarda nenhum segredo: o cliente é público.
 */
final class Auth {
    private final SecureStore store;

    Auth(Context ctx) {
        store = new SecureStore(ctx);
    }

    synchronized boolean isLoggedIn() {
        return store.get("refresh_token") != null || store.get("access_token") != null;
    }

    synchronized String accessToken() {
        return store.get("access_token");
    }

    /** Bloqueante: chamar fora da thread principal. Devolve o endereço de login para abrir no navegador. */
    Uri beginLogin() throws Exception {
        Http.Response d = Http.request("GET", Config.API + "/oauth", null, null, null);
        JSONObject disc = parseObj(d.body);
        String authEp = J.findDeep(disc, "authorization_endpoint");
        String tokenEp = J.findDeep(disc, "token_endpoint");
        String regEp = J.findDeep(disc, "registration_endpoint");
        if (d.code >= 400 || authEp == null || tokenEp == null) {
            throw new IOException("Não consegui descobrir o provedor de login da loja.");
        }

        String clientId = store.get("client_id");
        boolean needRegister = clientId == null
                || (regEp != null && !regEp.equals(store.get("registration_endpoint")));
        if (needRegister) {
            if (regEp == null) {
                throw new IOException("A loja não permite registrar o aplicativo.");
            }
            JSONObject req = new JSONObject();
            req.put("client_name", Config.CLIENT_NAME);
            req.put("redirect_uris", new JSONArray().put(Config.REDIRECT_URI));
            req.put("token_endpoint_auth_method", "none");
            req.put("grant_types", new JSONArray().put("authorization_code").put("refresh_token"));
            req.put("response_types", new JSONArray().put("code"));
            req.put("scope", Config.SCOPE);
            Http.Response r = Http.request("POST", regEp, "application/json", req.toString(), null);
            clientId = J.findDeep(parseObj(r.body), "client_id");
            if (r.code >= 400 || clientId == null) {
                throw new IOException("A loja recusou o registro do aplicativo.");
            }
            store.put("client_id", clientId);
            store.put("registration_endpoint", regEp);
        }
        store.put("token_endpoint", tokenEp);

        SecureRandom rnd = new SecureRandom();
        byte[] vb = new byte[64];
        rnd.nextBytes(vb);
        String verifier = b64(vb);
        String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("US-ASCII")));
        byte[] sb = new byte[24];
        rnd.nextBytes(sb);
        String state = b64(sb);
        store.put("pending_verifier", verifier);
        store.put("pending_state", state);

        return Uri.parse(authEp).buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", Config.REDIRECT_URI)
                .appendQueryParameter("scope", Config.SCOPE)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build();
    }

    /** Bloqueante: troca o código recebido no callback pelos tokens. */
    void finishLogin(Uri callback) throws Exception {
        String error = callback.getQueryParameter("error");
        if (error != null) {
            throw new IOException("Login cancelado ou recusado.");
        }
        String code = callback.getQueryParameter("code");
        String state = callback.getQueryParameter("state");
        String expected = store.get("pending_state");
        String verifier = store.get("pending_verifier");
        if (code == null || expected == null || !expected.equals(state) || verifier == null) {
            throw new IOException("Resposta de login inválida. Tente entrar de novo.");
        }
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(Config.REDIRECT_URI)
                + "&client_id=" + enc(store.get("client_id"))
                + "&code_verifier=" + enc(verifier);
        int status = tokenRequest(form);
        store.remove("pending_state", "pending_verifier");
        if (status >= 400) {
            throw new IOException("A loja não aceitou o login. Tente de novo.");
        }
    }

    /** Bloqueante. Renova o access_token com o refresh_token; devolve false se não deu. */
    synchronized boolean refresh() {
        String rt = store.get("refresh_token");
        String cid = store.get("client_id");
        if (rt == null || cid == null || store.get("token_endpoint") == null) return false;
        try {
            int status = tokenRequest("grant_type=refresh_token&refresh_token=" + enc(rt) + "&client_id=" + enc(cid));
            if (status == 400 || status == 401) {
                // refresh recusado de vez: sessão encerrada
                store.remove("access_token", "refresh_token");
                return false;
            }
            return status < 400;
        } catch (Exception e) {
            return false;
        }
    }

    synchronized void logout() {
        store.remove("access_token", "refresh_token", "pending_state", "pending_verifier");
    }

    private int tokenRequest(String form) throws Exception {
        Http.Response r = Http.request("POST", store.get("token_endpoint"),
                "application/x-www-form-urlencoded", form, null);
        if (r.code >= 400) return r.code;
        JSONObject j = parseObj(r.body);
        String at = j.optString("access_token", "");
        if (at.isEmpty()) return 500;
        store.put("access_token", at);
        String rt = j.optString("refresh_token", "");
        if (!rt.isEmpty()) store.put("refresh_token", rt);
        return r.code;
    }

    private static JSONObject parseObj(String s) {
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
}

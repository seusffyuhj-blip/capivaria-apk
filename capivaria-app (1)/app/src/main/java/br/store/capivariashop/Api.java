package br.store.capivariashop;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cliente da API da loja (https://capivariashopbr.store/api/v1). */
final class Api {

    interface Task<T> {
        T run() throws Exception;
    }

    interface Callback<T> {
        void onResult(T result, Exception error);
    }

    static final class ApiException extends Exception {
        final int code;

        ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private final Auth auth;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

    Api(Auth auth) {
        this.auth = auth;
    }

    <T> void async(Task<T> task, Callback<T> cb) {
        pool.execute(() -> {
            T result = null;
            Exception error = null;
            try {
                result = task.run();
            } catch (Exception e) {
                error = e;
            }
            final T r = result;
            final Exception er = error;
            main.post(() -> cb.onResult(r, er));
        });
    }

    JSONObject json(String method, String path, JSONObject body, boolean authed) throws Exception {
        String payload = body != null ? body.toString() : ("GET".equals(method) ? null : "{}");
        Http.Response r = Http.request(method, Config.API + path, "application/json", payload,
                authed ? auth.accessToken() : null);
        if (r.code == 401 && authed && auth.refresh()) {
            r = Http.request(method, Config.API + path, "application/json", payload, auth.accessToken());
        }
        JSONObject j;
        try {
            j = new JSONObject(r.body);
        } catch (Exception e) {
            j = new JSONObject();
        }
        if (r.code >= 400) {
            String m = j.optString("error", "");
            if (m.isEmpty()) m = "Erro " + r.code + " na loja.";
            throw new ApiException(r.code, m);
        }
        return j;
    }
}

package br.store.capivariashop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class Http {

    static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    // Sem cookies: o Android não instala CookieHandler por padrão, então nada é enviado além do que definimos aqui.
    static Response request(String method, String url, String contentType, String body, String bearer)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setRequestProperty("Accept", "application/json");
            if (bearer != null) {
                c.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            if (body != null) {
                byte[] data = body.getBytes("UTF-8");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                c.setFixedLengthStreamingMode(data.length);
                OutputStream os = c.getOutputStream();
                os.write(data);
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Response(code, is == null ? "" : read(is));
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        is.close();
        return out.toString("UTF-8");
    }
}

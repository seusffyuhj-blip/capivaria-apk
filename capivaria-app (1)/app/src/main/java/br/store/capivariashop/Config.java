package br.store.capivariashop;

final class Config {
    static final String SITE = "https://capivariashopbr.store";
    static final String API = SITE + "/api/v1";
    // Callback exato registrado no provedor OAuth (precisa bater com o intent-filter do AndroidManifest)
    static final String REDIRECT_URI = "br.store.capivariashop:/oauth2redirect";
    static final String REDIRECT_SCHEME = "br.store.capivariashop";
    static final String SCOPE = "openid email profile";
    static final String CLIENT_NAME = "Capivaria Shop BR (Android)";

    private Config() {}
}

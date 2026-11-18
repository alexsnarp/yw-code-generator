package com.lge.ywcodegenerator;

import android.content.Context;
import android.os.Build;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.*;
import javax.net.ssl.*;

final class LegacyTlsCompat {
    private static volatile SSLSocketFactory factory;
    private LegacyTlsCompat() { }

    static void configure(Context context, HttpsURLConnection connection) throws Exception {
        if (Build.VERSION.SDK_INT > 23) return;
        if (factory == null) synchronized (LegacyTlsCompat.class) {
            if (factory == null) factory = build(context.getApplicationContext());
        }
        connection.setSSLSocketFactory(factory);
    }

    private static SSLSocketFactory build(Context context) throws Exception {
        X509TrustManager system = manager(null);
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        try (InputStream in = context.getResources().openRawResource(R.raw.isrg_root_x1)) {
            store.setCertificateEntry("isrg-root-x1", certificates.generateCertificate(in));
        }
        X509TrustManager extra = manager(store);
        X509TrustManager combined = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                system.checkClientTrusted(chain, authType);
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try { system.checkServerTrusted(chain, authType); }
                catch (CertificateException original) {
                    if (!missingAnchor(original)) throw original;
                    try { extra.checkServerTrusted(chain, authType); }
                    catch (CertificateException fallback) { fallback.addSuppressed(original); throw fallback; }
                }
            }
            @Override public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] a = system.getAcceptedIssuers(), b = extra.getAcceptedIssuers();
                X509Certificate[] all = new X509Certificate[a.length + b.length];
                System.arraycopy(a, 0, all, 0, a.length);
                System.arraycopy(b, 0, all, a.length, b.length);
                return all;
            }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[]{combined}, null);
        return ssl.getSocketFactory();
    }

    private static X509TrustManager manager(KeyStore store) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers())
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        throw new CertificateException("No X509 trust manager");
    }

    private static boolean missingAnchor(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("Trust anchor for certification path not found")) return true;
        }
        return false;
    }
}

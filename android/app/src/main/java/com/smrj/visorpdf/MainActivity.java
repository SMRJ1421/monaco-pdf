package com.smrj.visorpdf;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends BridgeActivity {
    static JSObject pending; // PDF recibido por "Abrir con", aún no leído por la web

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PdfIntent.class);
        super.onCreate(savedInstanceState);
        // zoom con los dedos (pellizco), sin los botones +/-
        android.webkit.WebSettings s = getBridge().getWebView().getSettings();
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
        PdfIntent p = (PdfIntent) getBridge().getPlugin("PdfIntent").getInstance();
        if (pending != null) { p.emit(pending); pending = null; }
    }

    // Copia el PDF a la caché para que la WebView lo lea por URL local
    private void handle(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        Uri uri = intent.getData();
        String name = "documento.pdf";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) {}
        File out = new File(getCacheDir(), "abierto.pdf");
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            pending = new JSObject();
            pending.put("path", out.getAbsolutePath() + "?t=" + System.currentTimeMillis());
            pending.put("name", name);
        } catch (Exception e) {
            pending = new JSObject();
            pending.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @CapacitorPlugin(name = "PdfIntent")
    public static class PdfIntent extends Plugin {
        @PluginMethod
        public void getPending(PluginCall call) {
            JSObject r = pending != null ? pending : new JSObject();
            pending = null;
            call.resolve(r);
        }

        void emit(JSObject data) { notifyListeners("pdf", data); }

        // Descarga el APK de la actualización y abre el instalador de Android (sin navegador)
        @PluginMethod
        public void downloadUpdate(PluginCall call) {
            String url = call.getString("url");
            if (url == null || !url.startsWith("https://github.com/SMRJ1421/monaco-pdf/")) {
                call.reject("URL de actualización no válida");
                return;
            }
            android.app.Activity act = getActivity();
            if (android.os.Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
                act.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + act.getPackageName())));
                call.reject("PERMISO: activa «Permitir de esta fuente» y vuelve a tocar Actualizar");
                return;
            }
            new Thread(() -> {
                File apk = new File(act.getCacheDir(), "actualizacion.apk");
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setInstanceFollowRedirects(true); // GitHub redirige al archivo real
                    int total = c.getContentLength();
                    try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(apk)) {
                        byte[] buf = new byte[65536];
                        int n, done = 0, last = -1;
                        while ((n = in.read(buf)) > 0) {
                            os.write(buf, 0, n);
                            done += n;
                            int pct = total > 0 ? done * 100 / total : -1;
                            if (pct != last) {
                                last = pct;
                                JSObject p = new JSObject();
                                p.put("percent", pct);
                                notifyListeners("progress", p);
                            }
                        }
                    }
                    Uri uri = androidx.core.content.FileProvider.getUriForFile(act, act.getPackageName() + ".fileprovider", apk);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(i);
                    call.resolve();
                } catch (Exception e) {
                    call.reject("Falló la descarga: " + e.getMessage());
                }
            }).start();
        }
    }
}

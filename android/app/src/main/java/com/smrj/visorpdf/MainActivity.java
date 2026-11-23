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
    }
}

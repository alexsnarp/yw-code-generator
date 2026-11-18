package com.lge.ywcodegenerator;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;

public final class MainActivity extends Activity {
    private static final String DEFAULT_API = "https://yw-app-store-api.yw-app-installer-store-2026.workers.dev";
    private static final int INK = 0xff17243a, MUTED = 0xff64748b, BLUE = 0xff1769e0, BG = 0xfff4f7fb;
    private EditText endpoint, secret, count, maxUses, expiry;
    private TextView result, status;
    private Button generate;
    private String generatedCodes = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildUi();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable rounded(int color, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(16));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private EditText field(String hint, String value, int inputType) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setText(value); e.setHint(hint);
        e.setTextSize(16); e.setTextColor(INK); e.setHintTextColor(MUTED); e.setInputType(inputType);
        e.setPadding(dp(13), dp(8), dp(13), dp(8)); e.setBackground(rounded(Color.WHITE, 0xffdbe3ef));
        return e;
    }
    private void addField(LinearLayout card, String title, EditText input) {
        TextView caption = label(title, 13, MUTED, true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.topMargin = dp(12); card.addView(caption, cp);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(50)); ep.topMargin = dp(5); card.addView(input, ep);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(24)); scroll.addView(page); setContentView(scroll);
        page.addView(label("YW Code Generator", 24, INK, true));
        TextView intro = label("Create short authorization codes for the YW App Store.", 14, MUTED, false);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2); introLp.topMargin = dp(6); page.addView(intro, introLp);

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(14), dp(16), dp(16));
        card.setBackground(rounded(Color.WHITE, 0xffe2e8f0));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2); cardLp.topMargin = dp(18); page.addView(card, cardLp);
        endpoint = field("https://your-worker.workers.dev", DEFAULT_API, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        secret = field("Administrator secret", "", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        count = field("1", "1", android.text.InputType.TYPE_CLASS_NUMBER);
        maxUses = field("1–100", "1", android.text.InputType.TYPE_CLASS_NUMBER);
        expiry = field("168", "168", android.text.InputType.TYPE_CLASS_NUMBER);
        addField(card, "SERVER URL", endpoint);
        addField(card, "ADMINISTRATOR SECRET (NOT SAVED)", secret);
        addField(card, "NUMBER OF CODES (1–50)", count);
        addField(card, "REDEMPTIONS PER CODE (1–100)", maxUses);
        addField(card, "EXPIRES AFTER HOURS (1–720)", expiry);

        generate = new Button(this); generate.setText("Generate codes"); generate.setAllCaps(false); generate.setTextSize(16);
        generate.setTypeface(Typeface.DEFAULT, Typeface.BOLD); generate.setTextColor(Color.WHITE); generate.setBackground(rounded(BLUE, 0));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(52)); gp.topMargin = dp(18); card.addView(generate, gp);
        status = label("The administrator secret is used only for this request and is never stored.", 12, MUTED, false);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2); statusLp.topMargin = dp(12); card.addView(status, statusLp);

        LinearLayout outputCard = new LinearLayout(this); outputCard.setOrientation(LinearLayout.VERTICAL); outputCard.setPadding(dp(16), dp(14), dp(16), dp(16));
        outputCard.setBackground(rounded(Color.WHITE, 0xffe2e8f0));
        LinearLayout.LayoutParams outputLp = new LinearLayout.LayoutParams(-1, -2); outputLp.topMargin = dp(16); page.addView(outputCard, outputLp);
        outputCard.addView(label("Generated codes", 18, INK, true));
        result = label("Your codes will appear here. Each code is shown only once.", 18, INK, true);
        result.setTextIsSelectable(true); result.setPadding(0, dp(12), 0, dp(12)); outputCard.addView(result);
        Button copy = new Button(this); copy.setText("Copy codes"); copy.setAllCaps(false); outputCard.addView(copy, new LinearLayout.LayoutParams(-1, dp(46)));
        copy.setOnClickListener(v -> {
            if (generatedCodes.isEmpty()) { status.setText("Generate codes first."); return; }
            ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("YW authorization codes", generatedCodes));
            status.setText("Codes copied to clipboard.");
        });
        generate.setOnClickListener(v -> generateCodes());
    }

    private int number(EditText input, int min, int max, String name) {
        try { int value = Integer.parseInt(input.getText().toString().trim()); if (value < min || value > max) throw new NumberFormatException(); return value; }
        catch (Exception e) { throw new IllegalArgumentException(name + " must be between " + min + " and " + max + "."); }
    }

    private void generateCodes() {
        final String base = endpoint.getText().toString().trim().replaceAll("/+$", "");
        final String adminSecret = secret.getText().toString();
        final int howMany, uses, hours;
        try {
            URL parsed = new URL(base);
            if (!"https".equalsIgnoreCase(parsed.getProtocol()) || parsed.getHost().isEmpty()) throw new IllegalArgumentException("Use an HTTPS server URL.");
            if (adminSecret.trim().length() < 32) throw new IllegalArgumentException("Enter the administrator secret.");
            howMany = number(count, 1, 50, "Code count");
            uses = number(maxUses, 1, 100, "Redemptions per code");
            hours = number(expiry, 1, 720, "Expiry");
        } catch (Exception e) { status.setText(e.getMessage()); return; }

        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(generate.getWindowToken(), 0);
        generate.setEnabled(false); generate.setText("Generating…"); status.setText("Contacting the server securely…");
        new Thread(() -> {
            HttpsURLConnection connection = null;
            try {
                JSONObject body = new JSONObject(); body.put("count", howMany); body.put("maxUses", uses); body.put("ttlHours", hours);
                connection = (HttpsURLConnection)new URL(base + "/admin/codes").openConnection();
                LegacyTlsCompat.configure(this, connection);
                connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
                connection.setUseCaches(false); connection.setRequestProperty("Cache-Control", "no-store");
                connection.setRequestProperty("Accept", "application/json"); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + adminSecret);
                try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes("UTF-8")); }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (stream != null) try (InputStream in = stream) { byte[] buffer = new byte[4096]; int n; while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n); }
                JSONObject response = new JSONObject(bytes.toString("UTF-8"));
                if (code < 200 || code >= 300) throw new IOException(response.optString("error", "Server returned HTTP " + code));
                JSONArray codes = response.optJSONArray("codes"); if (codes == null || codes.length() != howMany) throw new IOException("The server returned an incomplete code list.");
                StringBuilder display = new StringBuilder();
                for (int i = 0; i < codes.length(); i++) display.append(codes.optString(i)).append('\n');
                String output = display.toString().trim();
                runOnUiThread(() -> { generatedCodes = output; result.setText(output); status.setText("Created " + howMany + " code(s), " + uses + " redemption(s) each. Expires in " + hours + " hours."); generate.setEnabled(true); generate.setText("Generate codes"); secret.setText(""); });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Could not contact the code server." : e.getMessage();
                runOnUiThread(() -> { status.setText(message); generate.setEnabled(true); generate.setText("Generate codes"); secret.setText(""); });
            } finally { if (connection != null) connection.disconnect(); }
        }).start();
    }
}

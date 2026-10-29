package app.cardchess.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.JavascriptInterface;
import chess.serialization.JsonCodec;
import chess.game.GameSession;
import chess.opponent.ChatGptOpponent;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONTokener;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Bundled interface and shared Java engine communicate in-process, without a game server. */
public final class MainActivity extends Activity {
    private static final String GAME_URL = "https://appassets.androidplatform.net/";
    private static final int OPEN_GAME = 100, SAVE_GAME = 101;
    private static final int MAX_SAVE_BYTES = 10_000_000;
    private WebView web;
    private LinearLayout errorPanel;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingSave;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(245, 247, 242));
        // Keep the game clear of the status bar, camera cutout, navigation and keyboard.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            WebView.setWebContentsDebuggingEnabled(true);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(32, 32, 32, 32);
        TextView message = new TextView(this);
        message.setText("Не удалось открыть игру.\nПопробуйте ещё раз.");
        message.setTextSize(18);
        message.setGravity(Gravity.CENTER);
        errorPanel.addView(message);
        Button retry = new Button(this);
        retry.setText("Повторить");
        retry.setOnClickListener(v -> web.loadUrl(GAME_URL));
        errorPanel.addView(retry);
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.addJavascriptInterface(new EngineBridge(), "AndroidGame");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!trusted(uri)) return assetError();
                String path = uri.getPath();
                if (path == null || path.contains("..")) return assetError();
                if (path.equals("/")) path = "/index.html";
                String mime = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css"
                        : path.endsWith(".html") ? "text/html" : path.endsWith(".svg") ? "image/svg+xml"
                        : path.endsWith(".png") ? "image/png" : "application/octet-stream";
                try {
                    InputStream stream = getAssets().open("web" + path);
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", Map.of(
                            "Cache-Control", "no-store",
                            "Content-Security-Policy", "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-src 'none'; object-src 'none'"), stream);
                } catch (java.io.IOException ignored) { return assetError(); }
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                web.setVisibility(View.VISIBLE);
                errorPanel.setVisibility(View.GONE);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (trusted(uri)) return false;
                if ("https".equals(uri.getScheme()) && request.hasGesture()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) { toast("Не удалось открыть ссылку"); }
                }
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showConnectionError();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 500) showConnectionError();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                        .setPositiveButton("ОК", (dialog, which) -> result.confirm())
                        .setOnCancelListener(dialog -> result.cancel()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                        .setPositiveButton("Да", (dialog, which) -> result.confirm())
                        .setNegativeButton("Отмена", (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel()).show();
                return true;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json")
                        .addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(intent, OPEN_GAME); }
                catch (ActivityNotFoundException ignored) { finishFileChoice(null); toast("Не удалось открыть выбор файла"); }
                return true;
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, length) -> saveDownload(url));
        if (state == null || web.restoreState(state) == null) web.loadUrl(GAME_URL);
    }

    private static WebResourceResponse assetError() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Map.of(), new ByteArrayInputStream(new byte[0]));
    }

    private final class EngineBridge {
        @JavascriptInterface public void request(String json) {
            if (json == null || json.length() > 12_000_000) return;
            ChessApplication application = (ChessApplication)getApplication();
            application.commands.execute(() -> {
                int id = 0;
                Map<String, Object> result;
                try {
                    Map<String, Object> request = JsonCodec.readObject(json);
                    id = ((Number)request.get("id")).intValue();
                    @SuppressWarnings("unchecked") var body = (Map<String, Object>)request.get("body");
                    result = Map.of("status", 200, "body", application.game().execute((String)request.get("path"), body));
                } catch (GameSession.StalePositionException error) {
                    result = Map.of("status", 409, "body", Map.of("error", error.getMessage()));
                } catch (ChatGptOpponent.Failure error) {
                    result = Map.of("status", error.retryable() ? 502 : 400, "body", Map.of("error", error.getMessage()));
                } catch (IllegalArgumentException error) {
                    result = Map.of("status", 400, "body", Map.of("error", error.getMessage() == null ? "Не удалось выполнить действие" : error.getMessage()));
                } catch (Exception | LinkageError error) {
                    android.util.Log.e("CardChess", "Local command failed", error);
                    result = Map.of("status", 500, "body", Map.of("error", "Не удалось выполнить действие. Попробуйте ещё раз."));
                }
                String callback = "window.__resolveAndroidGame(" + id + "," + JsonCodec.toJson(result) + ")";
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) web.evaluateJavascript(callback, null);
                });
            });
        }
    }

    private static boolean trusted(Uri uri) {
        return "https".equals(uri.getScheme()) && Uri.parse(GAME_URL).getHost().equals(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    /** Saves the existing browser Blob export through Android's document picker, without storage permissions. */
    private void saveDownload(String url) {
        if (!trusted(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))
                || !url.startsWith("blob:" + GAME_URL)) {
            toast("Не удалось сохранить партию");
            return;
        }
        // evaluateJavascript doesn't await Promises; the async result is retrieved by a short poll.
        String script = "window.__androidSaveResult = null; fetch(" + JSONObject.quote(url) + ")"
                + ".then(r=>r.blob()).then(b=>{if(b.size>10000000)throw Error();"
                + "const f=new FileReader();f.onload=()=>window.__androidSaveResult=f.result;"
                + "f.onerror=()=>window.__androidSaveResult='error';f.readAsDataURL(b);})"
                + ".catch(()=>window.__androidSaveResult='error');";
        web.evaluateJavascript(script, ignored -> pollDownload(0));
    }

    private void pollDownload(int attempt) {
        if (isFinishing() || isDestroyed()) return;
        web.evaluateJavascript("window.__androidSaveResult", encoded -> {
            try {
                Object value = new JSONTokener(encoded).nextValue();
                if (!(value instanceof String data)) {
                    if (attempt >= 100) { toast("Не удалось сохранить партию"); return; }
                    web.postDelayed(() -> pollDownload(attempt + 1), 100);
                    return;
                }
                if (!data.startsWith("data:")) throw new IllegalArgumentException();
                int comma = data.indexOf(',');
                if (comma < 0 || data.length() > 14_000_000) throw new IllegalArgumentException();
                byte[] bytes = Base64.decode(data.substring(comma + 1), Base64.DEFAULT);
                if (bytes.length > MAX_SAVE_BYTES) throw new IllegalArgumentException();
                // Reject arbitrary downloads; only a valid game JSON document can be exported.
                new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                pendingSave = bytes;
                web.evaluateJavascript("delete window.__androidSaveResult", null);
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json").putExtra(Intent.EXTRA_TITLE, "chess-game.json");
                startActivityForResult(intent, SAVE_GAME);
            } catch (Exception ignored) { pendingSave = null; toast("Не удалось сохранить партию"); }
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
        if (request == OPEN_GAME) finishFileChoice(uri);
        if (request == SAVE_GAME) {
            byte[] bytes = pendingSave;
            pendingSave = null;
            if (uri != null && bytes != null) {
                try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                    if (stream == null) throw new java.io.IOException();
                    stream.write(bytes);
                    toast("Партия сохранена");
                } catch (Exception ignored) { toast("Не удалось сохранить партию"); }
            }
        }
    }

    private void finishFileChoice(Uri uri) {
        if (fileCallback != null) fileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
        fileCallback = null;
    }
    private void showConnectionError() { web.setVisibility(View.GONE); errorPanel.setVisibility(View.VISIBLE); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); web.saveState(state); }
    @Override protected void onPause() { web.onPause(); CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() { finishFileChoice(null); web.destroy(); super.onDestroy(); }
}

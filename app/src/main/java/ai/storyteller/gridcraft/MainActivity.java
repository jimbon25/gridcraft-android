package ai.storyteller.gridcraft;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "GridCraft";
    private WebView webView;
    private ProgressBar loadingSpinner;
    private ValueCallback<Uri[]> fileUploadCallback;

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (fileUploadCallback != null) {
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = data.getClipData().getItemAt(i).getUri();
                            }
                        } else if (data.getData() != null) {
                            results = new Uri[]{data.getData()};
                        }
                    }
                    fileUploadCallback.onReceiveValue(results);
                    fileUploadCallback = null;
                }
            });

    private boolean isKeyboardOpen = false;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        loadingSpinner = findViewById(R.id.loading_spinner);

        // Track real-time keyboard insets visibility
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(webView, (v, insets) -> {
            boolean open = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (isKeyboardOpen != open) {
                isKeyboardOpen = open;
                if (!open) {
                    webView.evaluateJavascript("if (window.__onKeyboardHidden) { window.__onKeyboardHidden(); }", null);
                }
            }
            return insets;
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Hardware acceleration for high-refresh-rate tablet screens
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        FloatingActionButton fabKeyboard = findViewById(R.id.fab_keyboard);
        fabKeyboard.setOnClickListener(v -> toggleKeyboard());

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                String path = url.getPath();
                if (path != null && path.endsWith(".wasm")) {
                    try {
                        String assetPath = path.startsWith("/assets/") ? path.substring(8) : path;
                        InputStream is = getAssets().open(assetPath);
                        Map<String, String> headers = new HashMap<>();
                        headers.put("Access-Control-Allow-Origin", "*");
                        return new WebResourceResponse("application/wasm", "binary", 200, "OK", headers, is);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to load wasm asset: " + path, e);
                    }
                }
                return assetLoader.shouldInterceptRequest(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingSpinner.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    fileUploadCallback = null;
                    Toast.makeText(MainActivity.this, "File manager not available", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("GridCraftJS", consoleMessage.message() + " [" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + "]");
                return true;
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        // Load offline GridCraft web package from assets
        webView.loadUrl("https://appassets.androidplatform.net/assets/gridcraft/index.html");
    }

    public void toggleKeyboard() {
        if (isKeyboardOpen) {
            hideKeyboard();
        } else {
            showKeyboard();
        }
    }

    public void showKeyboard() {
        runOnUiThread(() -> {
            webView.requestFocus();
            webView.evaluateJavascript(
                "(function() {" +
                "  if (window.__showKeyboard) { window.__showKeyboard(); }" +
                "})()",
                null
            );
            WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(getWindow(), webView);
            if (insets != null) {
                insets.show(WindowInsetsCompat.Type.ime());
            } else {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(webView, InputMethodManager.SHOW_FORCED);
                }
            }
        });
    }

    public void hideKeyboard() {
        runOnUiThread(() -> {
            webView.evaluateJavascript(
                "(function() {" +
                "  if (window.__hideKeyboard) { window.__hideKeyboard(); }" +
                "})()",
                null
            );
            WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(getWindow(), webView);
            if (insets != null) {
                insets.hide(WindowInsetsCompat.Type.ime());
            } else {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
                }
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            new AlertDialog.Builder(this)
                    .setTitle("Exit GridCraft")
                    .setMessage("Make sure you have saved your workbook. Are you sure you want to exit?")
                    .setPositiveButton("Exit", (dialog, which) -> finish())
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void saveFile(String base64Data, String filename, String mimeType) {
            try {
                byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                saveWorkbookToDownloads(filename, mimeType, data);
            } catch (Exception e) {
                Log.e(TAG, "Save failed", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public void showKeyboard() {
            MainActivity.this.showKeyboard();
        }

        @JavascriptInterface
        public void hideKeyboard() {
            MainActivity.this.hideKeyboard();
        }
    }

    private void saveWorkbookToDownloads(String filename, String mimeType, byte[] data) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GridCraft");

            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(data);
                        os.flush();
                    }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved: Downloads/GridCraft/" + filename, Toast.LENGTH_LONG).show());
            } else {
                throw new IOException("Failed to create MediaStore entry");
            }
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GridCraft");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Cannot create directory " + dir.getAbsolutePath());
            }
            File file = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
                fos.flush();
            }
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
        }
    }
}

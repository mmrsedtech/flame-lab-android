package com.virtualflamelab.education;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Offline Android container for the educational Flame Test laboratory.
 * The HTML/CSS/JavaScript simulation is fully bundled in the app.
 * No INTERNET permission is requested; all unexpected network requests are blocked.
 */
public final class MainActivity extends Activity {
    private static final String LOCAL_HOST = "appassets.androidplatform.net";
    private static final String LOCAL_PATH = "/assets/virtual-flame-lab.html";
    private static final String APP_URL = "https://" + LOCAL_HOST + LOCAL_PATH;
    private static final int REQUEST_SAVE_CSV = 8001;
    private WebView labView;
    private WebView printView;
    private String pendingCsv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(9, 18, 30));
        getWindow().setNavigationBarColor(Color.rgb(9, 18, 30));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(9, 18, 30));
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(root);

        labView = new WebView(this);
        labView.setBackgroundColor(Color.rgb(9, 18, 30));
        labView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings webSettings = labView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("UTF-8");
        labView.addJavascriptInterface(new FlameAndroidBridge(), "FlameAndroid");

        labView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !APP_URL.equals(request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if ("https".equalsIgnoreCase(url.getScheme())
                        && LOCAL_HOST.equalsIgnoreCase(url.getHost())
                        && LOCAL_PATH.equals(url.getPath())) {
                    try {
                        InputStream stream = getAssets().open("virtual-flame-lab.html");
                        return new WebResourceResponse("text/html", "UTF-8", stream);
                    } catch (IOException ignored) {
                        return emptyResponse();
                    }
                }
                // Intentionally deny all external resources, including network requests.
                return emptyResponse();
            }
        });
        labView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("تأیید عملیات")
                        .setMessage(message)
                        .setPositiveButton("تأیید", (dialog, which) -> result.confirm())
                        .setNegativeButton("انصراف", (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
        });

        root.addView(labView, new FrameLayout.LayoutParams(-1, -1));
        labView.loadUrl(APP_URL);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBackButton);
        }
    }

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream(new byte[0]));
    }

    private final class FlameAndroidBridge {
        @JavascriptInterface
        public void exportCsv(String csv) {
            runOnUiThread(() -> openCsvSaveDialog(csv));
        }

        @JavascriptInterface
        public void printJournal(String html) {
            runOnUiThread(() -> printJournalFromHtml(html));
        }
    }

    private void openCsvSaveDialog(String csv) {
        if (csv == null || csv.isEmpty()) {
            notifyUser("داده‌ای برای ذخیره وجود ندارد.");
            return;
        }
        pendingCsv = csv;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "flame-lab-observations.csv");
        try {
            startActivityForResult(intent, REQUEST_SAVE_CSV);
        } catch (Exception e) {
            pendingCsv = null;
            notifyUser("برنامه ذخیره‌سازی در دستگاه موجود نیست.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE_CSV) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingCsv != null) {
                try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                    if (stream == null) throw new IOException("No output stream");
                    stream.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
                    stream.flush();
                    notifyUser("دفتر مشاهدات ذخیره شد.");
                } catch (IOException e) {
                    notifyUser("ذخیره‌سازی ناموفق بود؛ دوباره تلاش کنید.");
                }
            }
            pendingCsv = null;
        }
    }

    private void printJournalFromHtml(String html) {
        if (html == null || html.isEmpty()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            notifyUser("چاپ در این نسخه اندروید پشتیبانی نمی‌شود.");
            return;
        }
        if (printView != null) {
            printView.destroy();
            printView = null;
        }
        printView = new WebView(this);
        final WebView currentPrintView = printView;
        currentPrintView.getSettings().setDefaultTextEncodingName("UTF-8");
        currentPrintView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (printView != currentPrintView) return;
                PrintManager manager = (PrintManager) getSystemService(PRINT_SERVICE);
                if (manager == null) {
                    notifyUser("خدمات چاپ در دستگاه در دسترس نیست.");
                    return;
                }
                PrintAttributes attrs = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build();
                manager.print("دفتر مشاهدات آزمایش شعله",
                        currentPrintView.createPrintDocumentAdapter("Flame-Lab-Observations"), attrs);
            }
        });
        currentPrintView.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null);
    }

    private void notifyUser(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        handleBackButton();
    }

    private void handleBackButton() {
        if (labView == null) {
            finish();
            return;
        }
        String js = "window.FlameLabGoBack ? window.FlameLabGoBack() : false";
        labView.evaluateJavascript(js, value -> {
            if ("true".equals(value)) return;
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("خروج از آزمایشگاه")
                    .setMessage("پیشرفت ذخیره‌شده روی دستگاه باقی می‌ماند. از برنامه خارج می‌شوید؟")
                    .setPositiveButton("خروج", (d, which) -> finish())
                    .setNegativeButton("ماندن", null)
                    .show();
        });
    }

    @Override
    protected void onPause() {
        if (labView != null) labView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (labView != null) labView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (printView != null) {
            printView.destroy();
            printView = null;
        }
        if (labView != null) {
            labView.removeJavascriptInterface("FlameAndroid");
            labView.destroy();
            labView = null;
        }
        super.onDestroy();
    }
}

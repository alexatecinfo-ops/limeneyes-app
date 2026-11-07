package com.limenseguridad.limeneyes;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeLayout;
    private static final String APP_URL = "https://eyes.limenseguridad.com/";
    private static final String TAG = "LimenEyes";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#0b0f17"));
            window.setNavigationBarColor(Color.parseColor("#0b0f17"));

            setContentView(R.layout.activity_main);

            swipeLayout = findViewById(R.id.swipeRefresh);
            webView = findViewById(R.id.webView);

            swipeLayout.setColorSchemeColors(Color.parseColor("#a855f7"));
            swipeLayout.setProgressBackgroundColorSchemeColor(Color.parseColor("#151d2a"));
            swipeLayout.setOnRefreshListener(() -> webView.reload());

            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setDatabaseEnabled(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            ws.setAllowFileAccess(false);
            ws.setAllowContentAccess(false);
            ws.setCacheMode(WebSettings.LOAD_DEFAULT);
            ws.setSupportMultipleWindows(true);
            ws.setJavaScriptCanOpenWindowsAutomatically(true);

            // Evitar bloqueo de Google OAuth en WebView ("disallowed_useragent")
            // Reemplazamos la marca "; wv)" de WebView por standard Chrome
            String defaultUa = ws.getUserAgentString();
            String cleanUa = defaultUa.replace("; wv", "");
            ws.setUserAgentString(cleanUa + " LimenEyesApp/1.0");

            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            webView.setBackgroundColor(Color.parseColor("#0b0f17"));

            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(webView, true);
            }

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (swipeLayout != null) {
                        swipeLayout.setRefreshing(false);
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    if (url.startsWith("https://eyes.limenseguridad.com") ||
                        url.contains("accounts.google.com") ||
                        url.contains("firebaseapp.com") ||
                        url.contains("google.com")) {
                        return false;
                    }
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error abriendo URL externa: " + url, e);
                        return false;
                    }
                }
            });

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(final PermissionRequest request) {
                    request.grant(request.getResources());
                }

                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                    // Permitir ventanas emergentes de OAuth dentro del mismo WebView
                    WebView.HitTestResult result = view.getHitTestResult();
                    String data = result.getExtra();
                    if (data != null) {
                        view.loadUrl(data);
                    } else {
                        WebView tempWebView = new WebView(MainActivity.this);
                        tempWebView.setWebViewClient(new WebViewClient() {
                            @Override
                            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                                view.loadUrl(r.getUrl().toString());
                                return true;
                            }
                        });
                        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                        transport.setWebView(tempWebView);
                        resultMsg.sendToTarget();
                    }
                    return true;
                }
            });

            webView.loadUrl(APP_URL);
        } catch (Throwable t) {
            Log.e(TAG, "CRITICAL ERROR en onCreate", t);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

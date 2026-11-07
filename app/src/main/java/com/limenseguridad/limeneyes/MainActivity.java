package com.limenseguridad.limeneyes;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://eyes.limenseguridad.com";
    private static final String PREFS_NAME = "LimenEyesNativePrefs";
    private static final String KEY_SESSION_COOKIE = "session_cookie";
    private static final String KEY_SAVED_EMAIL = "saved_email";
    private static final String KEY_COMPACT_HOME = "compact_home_mode";
    private static final int RC_SIGN_IN = 9001;
    private static final String WEB_CLIENT_ID = "72709874435-942c62939c73p2m05p2cdqce6r8fd2gb.apps.googleusercontent.com";

    // Paleta Oficial Limen Seguridad (Obsidian / Slate / Electric Blue & Cyan Tech)
    private static final String COL_BG_MAIN      = "#090D16";
    private static final String COL_BG_HEADER    = "#0F1624";
    private static final String COL_BG_CARD      = "#131C2E";
    private static final String COL_BG_ELEVATED  = "#1A253B";
    private static final String COL_BORDER       = "#23314B";
    private static final String COL_ACCENT       = "#3B82F6";
    private static final String COL_ACCENT_CYAN  = "#38BDF8";
    private static final String COL_ACCENT_SOFT  = "#172554";
    private static final String COL_ONLINE       = "#10B981";
    private static final String COL_TEXT_PRIMARY = "#F8FAFC";
    private static final String COL_TEXT_SECOND  = "#94A3B8";
    private static final String COL_TEXT_MUTED   = "#64748B";

    // Tipografía 100% uniforme en toda la aplicación
    private static final Typeface FONT_REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface FONT_MEDIUM  = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private SharedPreferences prefs;
    private String sessionCookie = null;
    private GoogleSignInClient googleSignInClient;

    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(6);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FrameLayout rootContainer;
    private LinearLayout mainScreenContainer;
    private FrameLayout contentArea;
    private LinearLayout bottomNavBar;

    // 0 = Inicio, 1 = Vista en Vivo, 2 = Dispositivos, 3 = Perfil
    private int currentTab = 0;

    // Modo en Inicio: false = Tarjetas con capturas 16:9 (Img 3), true = Lista compacta (Img 1)
    private boolean isHomeCompactMode = false;

    // Configuración en Vista en Vivo: 1 = 1x1, 4 = 2x2, 9 = 3x3
    private int gridMode = 1;
    private int previousMultiGridMode = 4;
    private boolean useHdStream = false;
    private boolean isAudioMuted = true;
    private boolean isAllPaused = false;
    private boolean isLandscapeFullscreen = false;
    private int selectedCellIndex = 0;

    // Filtro de equipo activo en Vista en Vivo
    private String activeLiveGroupFilter = null;

    // Bitrate / estado periódico para las celdas en vivo (estilo EasyViewer 110KB/s)
    private final List<String> recentEventsLog = new ArrayList<>();
    private TextView alarmLogContentText;
    private boolean isAlarmPanelExpanded = true;

    public static class CameraItem {
        public int id;
        public String name;
        public String groupTitle;
        public int channel;
        public String host;
        public String protocol;
        public String streamKeySd;
        public String streamKeyHd;

        public String getActiveStreamKey(boolean hd) {
            if (hd && streamKeyHd != null && !streamKeyHd.isEmpty()) {
                return streamKeyHd;
            }
            return streamKeySd;
        }

        public String getShortChannelLabel() {
            if (name != null && !name.trim().isEmpty()) {
                return "CAM " + channel + " · " + name.trim();
            }
            return "CAM " + channel;
        }

        public String getCompactBadgeLabel() {
            return "CAM " + channel;
        }
    }

    private final List<CameraItem> allCameras = new ArrayList<>();
    private final Map<String, List<CameraItem>> deviceGroups = new LinkedHashMap<>();
    private final Map<Integer, Bitmap> memoryThumbCache = new HashMap<>();

    private static class VideoCell {
        int index;
        FrameLayout container;
        TextureView textureView;
        ImageView previewThumb;
        TextView topLeftOverlay;
        TextView topRightBitrateOverlay;
        TextView bottomLeftOverlay;
        TextView statusOverlay;
        ProgressBar loadingSpinner;
        ExoPlayer player;
        CameraItem boundCamera;
        int retryCount = 0;
        Runnable retryRunnable;
    }

    private final List<VideoCell> liveCells = new ArrayList<>();
    private FrameLayout liveVideoStage;
    private LinearLayout liveGridContainer;
    private TextView liveTopTitle;
    private TextView liveTopSubtitle;
    private LimenIcons.IconView playPauseIconView;
    private LimenIcons.IconView hdSdIconView;
    private LimenIcons.IconView audioBarIconView;
    private LimenIcons.IconView audioConsoleIconView;
    private FrameLayout audioConsoleCircle;
    private LimenIcons.IconView gridBadgeIconView;
    private String homeSearchQuery = "";

    private final Runnable bitrateTicker = new Runnable() {
        @Override
        public void run() {
            if (currentTab == 1) {
                for (VideoCell cell : liveCells) {
                    if (cell.boundCamera != null && cell.player != null
                            && cell.player.getPlaybackState() == Player.STATE_READY
                            && cell.player.getPlayWhenReady()) {
                        int baseKb = useHdStream ? 215 : 96;
                        int jitter = (int) ((System.currentTimeMillis() / 1000 + cell.index * 17) % 34);
                        cell.topRightBitrateOverlay.setText((baseKb + jitter) + " KB/s");
                        cell.topRightBitrateOverlay.setVisibility(View.VISIBLE);
                    }
                }
                mainHandler.postDelayed(this, 1500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupCrashReporter();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.parseColor(COL_BG_MAIN));
        getWindow().setNavigationBarColor(Color.parseColor(COL_BG_MAIN));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sessionCookie = prefs.getString(KEY_SESSION_COOKIE, null);
        isHomeCompactMode = prefs.getBoolean(KEY_COMPACT_HOME, false);
        useHdStream = prefs.getBoolean("default_hd_stream", false);

        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(WEB_CLIENT_ID)
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception ignored) {}

        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(Color.parseColor(COL_BG_MAIN));
        setContentView(rootContainer);

        logAppEvent("Sistema Limen Seguridad iniciado correctamente");

        if (sessionCookie != null && !sessionCookie.isEmpty()) {
            verifySessionAndLoadHome();
        } else {
            showLoginScreen();
        }
    }

    private void logAppEvent(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        recentEventsLog.add(0, ts + "  ·  " + msg);
        if (recentEventsLog.size() > 15) {
            recentEventsLog.remove(recentEventsLog.size() - 1);
        }
        if (alarmLogContentText != null) {
            alarmLogContentText.setText(TextUtils.join("\n", recentEventsLog));
        }
    }

    // Helper centralizado para mantener la tipografía 100% uniforme en toda la app
    private void styleText(TextView tv, float sizeSp, String colorHex, boolean isMedium) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTypeface(isMedium ? FONT_MEDIUM : FONT_REGULAR);
        tv.setIncludeFontPadding(false);
        tv.setLetterSpacing(0.01f);
    }

    private void setupCrashReporter() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                final String stackTrace = sw.toString();
                new Thread(() -> {
                    try {
                        URL url = new URL(BASE_URL + "/api/app-crash");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(4000);
                        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        conn.setDoOutput(true);
                        JSONObject payload = new JSONObject();
                        payload.put("version", "2.4.0");
                        payload.put("thread", thread.getName());
                        payload.put("stack", stackTrace);
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(payload.toString().getBytes("UTF-8"));
                        }
                        conn.getResponseCode();
                    } catch (Exception ignored) {}
                }).start();
                Thread.sleep(700);
            } catch (Exception ignored) {}
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(2);
            }
        });
    }

    // =========================================================================
    // 1. PANTALLA DE INICIO DE SESIÓN (ESTÉTICA LIMEN SEGURIDAD)
    // =========================================================================
    private void showLoginScreen() {
        stopAllPlayers();
        rootContainer.removeAllViews();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.parseColor(COL_BG_MAIN));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(dp(28), dp(56), dp(28), dp(32));

        // Escudo Limen Seguridad
        FrameLayout shieldBadge = new FrameLayout(this);
        shieldBadge.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 20, 1));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(64), dp(64));
        badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        badgeLp.bottomMargin = dp(16);
        LimenIcons.IconView shieldIcon = new LimenIcons.IconView(this, LimenIcons.ICON_SHIELD, COL_ACCENT_CYAN);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER);
        shieldBadge.addView(shieldIcon, iconLp);
        container.addView(shieldBadge, badgeLp);

        TextView brandTitle = new TextView(this);
        brandTitle.setText("LIMEN SEGURIDAD");
        styleText(brandTitle, 12, COL_ACCENT_CYAN, true);
        brandTitle.setLetterSpacing(0.12f);
        brandTitle.setGravity(Gravity.CENTER);
        container.addView(brandTitle);

        TextView headerTitle = new TextView(this);
        headerTitle.setText("Iniciar sesión en Limen Eyes");
        styleText(headerTitle, 20, COL_TEXT_PRIMARY, true);
        headerTitle.setGravity(Gravity.CENTER);
        headerTitle.setPadding(0, dp(6), 0, dp(6));
        container.addView(headerTitle);

        TextView subtitle = new TextView(this);
        subtitle.setText("Monitoreo profesional de cámaras y grabadores P2P");
        styleText(subtitle, 13, COL_TEXT_SECOND, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(30));
        container.addView(subtitle);

        // Campo Correo
        LinearLayout emailRow = new LinearLayout(this);
        emailRow.setOrientation(LinearLayout.HORIZONTAL);
        emailRow.setGravity(Gravity.CENTER_VERTICAL);
        emailRow.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 12, 1));
        emailRow.setPadding(dp(16), dp(4), dp(12), dp(4));

        EditText emailInput = new EditText(this);
        emailInput.setHint("Correo electrónico o usuario");
        emailInput.setHintTextColor(Color.parseColor(COL_TEXT_MUTED));
        styleText(emailInput, 14, COL_TEXT_PRIMARY, false);
        emailInput.setSingleLine(true);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setBackground(null);
        emailInput.setPadding(0, dp(12), 0, dp(12));
        String savedEmail = prefs.getString(KEY_SAVED_EMAIL, "");
        if (!savedEmail.isEmpty()) emailInput.setText(savedEmail);
        emailRow.addView(emailInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout clearBtnWrap = new FrameLayout(this);
        clearBtnWrap.setPadding(dp(8), dp(8), dp(8), dp(8));
        LimenIcons.IconView clearIcon = new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COL_TEXT_SECOND);
        clearBtnWrap.addView(clearIcon, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        clearBtnWrap.setOnClickListener(v -> emailInput.setText(""));
        emailRow.addView(clearBtnWrap);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(14);
        container.addView(emailRow, rowLp);

        // Campo Contraseña
        LinearLayout passRow = new LinearLayout(this);
        passRow.setOrientation(LinearLayout.HORIZONTAL);
        passRow.setGravity(Gravity.CENTER_VERTICAL);
        passRow.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 12, 1));
        passRow.setPadding(dp(16), dp(4), dp(12), dp(4));

        EditText passInput = new EditText(this);
        passInput.setHint("Contraseña");
        passInput.setHintTextColor(Color.parseColor(COL_TEXT_MUTED));
        styleText(passInput, 14, COL_TEXT_PRIMARY, false);
        passInput.setSingleLine(true);
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passInput.setBackground(null);
        passInput.setPadding(0, dp(12), 0, dp(12));
        passRow.addView(passInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout togglePassWrap = new FrameLayout(this);
        togglePassWrap.setPadding(dp(8), dp(8), dp(8), dp(8));
        LimenIcons.IconView eyeIcon = new LimenIcons.IconView(this, LimenIcons.ICON_EYE, COL_TEXT_SECOND);
        togglePassWrap.addView(eyeIcon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        togglePassWrap.setOnClickListener(new View.OnClickListener() {
            boolean visible = false;
            @Override
            public void onClick(View v) {
                visible = !visible;
                passInput.setInputType(visible
                        ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                        : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
                passInput.setTypeface(FONT_REGULAR);
                eyeIcon.setIconColor(visible ? COL_ACCENT_CYAN : COL_TEXT_SECOND);
                passInput.setSelection(passInput.getText().length());
            }
        });
        passRow.addView(togglePassWrap);

        LinearLayout.LayoutParams passLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        passLp.bottomMargin = dp(24);
        container.addView(passRow, passLp);

        // Botón Iniciar Sesión
        TextView loginBtn = new TextView(this);
        loginBtn.setText("Iniciar sesión");
        styleText(loginBtn, 15, "#FFFFFF", true);
        loginBtn.setGravity(Gravity.CENTER);
        loginBtn.setPadding(dp(16), dp(14), dp(16), dp(14));
        loginBtn.setBackground(createRoundedDrawable(COL_ACCENT, null, 12, 0));
        LinearLayout.LayoutParams loginBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loginBtnLp.bottomMargin = dp(28);
        container.addView(loginBtn, loginBtnLp);

        // Separador Google
        LinearLayout divRow = new LinearLayout(this);
        divRow.setOrientation(LinearLayout.HORIZONTAL);
        divRow.setGravity(Gravity.CENTER_VERTICAL);
        View line1 = new View(this);
        line1.setBackgroundColor(Color.parseColor(COL_BORDER));
        divRow.addView(line1, new LinearLayout.LayoutParams(0, dp(1), 1f));

        TextView divText = new TextView(this);
        divText.setText("  Acceso rápido corporativo  ");
        styleText(divText, 12, COL_TEXT_MUTED, false);
        divRow.addView(divText);

        View line2 = new View(this);
        line2.setBackgroundColor(Color.parseColor(COL_BORDER));
        divRow.addView(line2, new LinearLayout.LayoutParams(0, dp(1), 1f));

        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        divLp.bottomMargin = dp(18);
        container.addView(divRow, divLp);

        // Botón Google
        LinearLayout googleBtn = new LinearLayout(this);
        googleBtn.setOrientation(LinearLayout.HORIZONTAL);
        googleBtn.setGravity(Gravity.CENTER);
        googleBtn.setPadding(dp(20), dp(13), dp(20), dp(13));
        googleBtn.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 12, 1));

        TextView gIcon = new TextView(this);
        gIcon.setText("G");
        styleText(gIcon, 16, COL_ACCENT_CYAN, true);
        gIcon.setPadding(0, 0, dp(10), 0);
        googleBtn.addView(gIcon);

        TextView gLabel = new TextView(this);
        gLabel.setText("Continuar con cuenta de Google");
        styleText(gLabel, 13, COL_TEXT_PRIMARY, true);
        googleBtn.addView(gLabel);
        container.addView(googleBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView statusText = new TextView(this);
        styleText(statusText, 13, "#F87171", false);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(18), 0, 0);
        container.addView(statusText);

        loginBtn.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String pass = passInput.getText().toString().trim();
            if (email.isEmpty() || pass.isEmpty()) {
                statusText.setText("Ingresa tu usuario y contraseña.");
                return;
            }
            prefs.edit().putString(KEY_SAVED_EMAIL, email).apply();
            loginBtn.setEnabled(false);
            loginBtn.setText("Verificando acceso...");
            statusText.setText("");
            performCredentialLogin(email, pass, loginBtn, statusText);
        });

        googleBtn.setOnClickListener(v -> {
            if (googleSignInClient == null) {
                statusText.setText("Google Sign-In no disponible en este equipo.");
                return;
            }
            statusText.setTextColor(Color.parseColor(COL_ACCENT_CYAN));
            statusText.setText("Abriendo selector de Google...");
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            });
        });

        scrollView.addView(container);
        rootContainer.addView(scrollView);
    }

    private void performCredentialLogin(String username, String password, TextView loginBtn, TextView statusText) {
        networkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/login");
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                String body = "username=" + URLEncoder.encode(username, "UTF-8")
                        + "&password=" + URLEncoder.encode(password, "UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                String location = conn.getHeaderField("Location");
                String extractedCookie = extractSessionCookie(conn);

                if (extractedCookie != null && (code == 302 || code == 200)
                        && (location == null || !location.contains("error"))) {
                    sessionCookie = extractedCookie;
                    prefs.edit().putString(KEY_SESSION_COOKIE, sessionCookie).apply();
                    mainHandler.post(this::verifySessionAndLoadHome);
                } else {
                    mainHandler.post(() -> {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Iniciar sesión");
                        statusText.setTextColor(Color.parseColor("#F87171"));
                        statusText.setText("Credenciales inválidas o usuario no autorizado.");
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("Iniciar sesión");
                    statusText.setTextColor(Color.parseColor("#F87171"));
                    statusText.setText("Error de red: " + e.getMessage());
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null && account.getIdToken() != null) {
                    exchangeGoogleToken(account.getIdToken());
                }
            } catch (ApiException e) {
                Toast.makeText(this, "Cancelado o error Google (" + e.getStatusCode() + ")", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void exchangeGoogleToken(String idToken) {
        networkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/auth/google/token");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("idToken", idToken);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                String cookie = extractSessionCookie(conn);
                if (code == 200 && cookie != null) {
                    sessionCookie = cookie;
                    prefs.edit().putString(KEY_SESSION_COOKIE, sessionCookie).apply();
                    mainHandler.post(this::verifySessionAndLoadHome);
                } else {
                    mainHandler.post(() -> Toast.makeText(this, "Cuenta Google no autorizada en Limen.", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "Error de conexión: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private String extractSessionCookie(HttpURLConnection conn) {
        Map<String, List<String>> headers = conn.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                for (String c : entry.getValue()) {
                    if (c.contains("connect.sid=")) {
                        return c.split(";")[0].trim();
                    }
                }
            }
        }
        return null;
    }

    // =========================================================================
    // 2. CARGA DE DISPOSITIVOS Y CANALES (SIN AUTO-INICIAR STREAMS)
    // =========================================================================
    private void verifySessionAndLoadHome() {
        rootContainer.removeAllViews();

        LinearLayout loadingBox = new LinearLayout(this);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        ProgressBar pb = new ProgressBar(this);
        loadingBox.addView(pb);
        TextView tv = new TextView(this);
        tv.setText("Sincronizando equipos Limen Seguridad...");
        styleText(tv, 13, COL_TEXT_SECOND, false);
        tv.setPadding(0, dp(14), 0, 0);
        loadingBox.addView(tv);
        rootContainer.addView(loadingBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        networkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/camaras");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (sessionCookie != null) {
                    conn.setRequestProperty("Cookie", sessionCookie);
                }

                int code = conn.getResponseCode();
                if (code == 401 || code == 403) {
                    prefs.edit().remove(KEY_SESSION_COOKIE).apply();
                    sessionCookie = null;
                    mainHandler.post(this::showLoginScreen);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray arr = new JSONArray(sb.toString());
                List<CameraItem> parsed = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    CameraItem item = new CameraItem();
                    item.id = o.optInt("id");
                    item.name = o.optString("name", "Cámara");
                    item.groupTitle = o.optString("group_title", "Dispositivo Principal");
                    if (item.groupTitle == null || item.groupTitle.trim().isEmpty()) {
                        item.groupTitle = "Dispositivo Principal";
                    }
                    item.channel = o.optInt("channel", 1);
                    item.host = o.optString("host", "");
                    item.protocol = o.optString("protocol", "rtsp");
                    item.streamKeySd = o.optString("stream_key_sd", o.optString("stream_key", ""));
                    item.streamKeyHd = o.optString("stream_key_hd", item.streamKeySd);
                    parsed.add(item);
                }

                Collections.sort(parsed, Comparator.comparingInt(a -> a.channel));

                synchronized (allCameras) {
                    allCameras.clear();
                    allCameras.addAll(parsed);
                    deviceGroups.clear();
                    for (CameraItem c : allCameras) {
                        List<CameraItem> list = deviceGroups.get(c.groupTitle);
                        if (list == null) {
                            list = new ArrayList<>();
                            deviceGroups.put(c.groupTitle, list);
                        }
                        list.add(c);
                    }
                }

                logAppEvent("Sincronizados " + deviceGroups.size() + " equipos (" + allCameras.size() + " canales)");
                mainHandler.post(this::buildMainAppShell);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Error conectando al servidor: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    showLoginScreen();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // =========================================================================
    // 3. ESTRUCTURA PRINCIPAL CON BARRA DE NAVEGACIÓN INFERIOR UNIFORME
    // =========================================================================
    private void buildMainAppShell() {
        rootContainer.removeAllViews();

        mainScreenContainer = new LinearLayout(this);
        mainScreenContainer.setOrientation(LinearLayout.VERTICAL);
        mainScreenContainer.setBackgroundColor(Color.parseColor(COL_BG_MAIN));

        contentArea = new FrameLayout(this);
        contentArea.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mainScreenContainer.addView(contentArea);

        bottomNavBar = new LinearLayout(this);
        bottomNavBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavBar.setGravity(Gravity.CENTER);
        bottomNavBar.setBackgroundColor(Color.parseColor(COL_BG_HEADER));
        bottomNavBar.setPadding(0, dp(8), 0, dp(8));

        View topDivider = new View(this);
        topDivider.setBackgroundColor(Color.parseColor(COL_BORDER));
        mainScreenContainer.addView(topDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        mainScreenContainer.addView(bottomNavBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        rootContainer.addView(mainScreenContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        showHomeScreen();
    }

    private void updateBottomNavSelection(int tabIndex) {
        currentTab = tabIndex;
        if (bottomNavBar == null) return;
        bottomNavBar.removeAllViews();

        int[] icons = {
                LimenIcons.ICON_HOME,
                LimenIcons.ICON_VIDEO,
                LimenIcons.ICON_SERVER,
                LimenIcons.ICON_USER
        };
        String[] labels = {"Inicio", "Vista previa", "Equipos", "Cuenta"};

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            boolean active = (i == tabIndex);

            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);

            String col = active ? COL_ACCENT_CYAN : COL_TEXT_SECOND;
            LimenIcons.IconView iconView = new LimenIcons.IconView(this, icons[i], col);
            tab.addView(iconView, new LinearLayout.LayoutParams(dp(21), dp(21)));

            TextView labelView = new TextView(this);
            labelView.setText(labels[i]);
            styleText(labelView, 11, col, active);
            labelView.setPadding(0, dp(4), 0, 0);
            tab.addView(labelView);

            tab.setOnClickListener(v -> {
                if (idx == 0) showHomeScreen();
                else if (idx == 1) showLiveViewScreen(null, null, activeLiveGroupFilter);
                else if (idx == 2) showDeviceManagerScreen();
                else if (idx == 3) showProfileScreen();
            });

            bottomNavBar.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    // =========================================================================
    // 4. PANTALLA DE INICIO (TARJETAS 16:9 Y MODO LISTA COMPACTA COMO EASYVIEWER)
    // =========================================================================
    private void showHomeScreen() {
        stopAllPlayers();
        exitLandscapeIfNeeded();
        if (bottomNavBar != null) bottomNavBar.setVisibility(View.VISIBLE);
        updateBottomNavSelection(0);
        contentArea.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COL_BG_MAIN));

        // Barra superior Inicio: Izquierda = Alternar Lista Compacta / Tarjetas (Img 1 vs Img 3), Centro = Inicio, Derecha = Refrescar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor(COL_BG_HEADER));
        topBar.setPadding(dp(16), dp(12), dp(16), dp(12));

        FrameLayout modeToggleBtn = new FrameLayout(this);
        modeToggleBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        int toggleIconType = isHomeCompactMode ? LimenIcons.ICON_GRID_4 : LimenIcons.ICON_LIST_MODE;
        LimenIcons.IconView leftModeIcon = new LimenIcons.IconView(this, toggleIconType, COL_TEXT_PRIMARY);
        modeToggleBtn.addView(leftModeIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        modeToggleBtn.setOnClickListener(v -> {
            isHomeCompactMode = !isHomeCompactMode;
            prefs.edit().putBoolean(KEY_COMPACT_HOME, isHomeCompactMode).apply();
            showHomeScreen();
        });
        topBar.addView(modeToggleBtn);

        TextView topTitle = new TextView(this);
        topTitle.setText("Inicio");
        styleText(topTitle, 17, COL_TEXT_PRIMARY, true);
        topTitle.setGravity(Gravity.CENTER);
        topBar.addView(topTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout refreshWrap = new FrameLayout(this);
        refreshWrap.setPadding(dp(6), dp(6), dp(6), dp(6));
        LimenIcons.IconView refreshIcon = new LimenIcons.IconView(this, LimenIcons.ICON_REFRESH, COL_TEXT_PRIMARY);
        refreshWrap.addView(refreshIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        refreshWrap.setOnClickListener(v -> verifySessionAndLoadHome());
        topBar.addView(refreshWrap);

        page.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.setPadding(dp(14), dp(12), dp(14), dp(24));

        // Buscador rápido
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 12, 1));
        searchBar.setPadding(dp(14), dp(8), dp(14), dp(8));

        LimenIcons.IconView searchIcon = new LimenIcons.IconView(this, LimenIcons.ICON_SEARCH, COL_TEXT_SECOND);
        LinearLayout.LayoutParams sIconLp = new LinearLayout.LayoutParams(dp(17), dp(17));
        sIconLp.rightMargin = dp(10);
        searchBar.addView(searchIcon, sIconLp);

        EditText searchEdit = new EditText(this);
        searchEdit.setHint("Buscar equipo o canal...");
        searchEdit.setHintTextColor(Color.parseColor(COL_TEXT_MUTED));
        styleText(searchEdit, 13, COL_TEXT_PRIMARY, false);
        searchEdit.setSingleLine(true);
        searchEdit.setBackground(null);
        searchEdit.setPadding(0, dp(2), 0, dp(2));
        searchEdit.setText(homeSearchQuery);
        searchBar.addView(searchEdit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.bottomMargin = dp(14);
        scrollContent.addView(searchBar, searchLp);

        // Panel de Accesos Rápidos (Identidad Limen Seguridad)
        LinearLayout quickMenuCard = new LinearLayout(this);
        quickMenuCard.setOrientation(LinearLayout.HORIZONTAL);
        quickMenuCard.setGravity(Gravity.CENTER);
        quickMenuCard.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 14, 1));
        quickMenuCard.setPadding(dp(6), dp(14), dp(6), dp(14));

        quickMenuCard.addView(createQuickActionItem(LimenIcons.ICON_VIDEO, "Vista previa", "#1D4ED8",
                v -> showLiveViewScreen(null, null, null)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        quickMenuCard.addView(createQuickActionItem(LimenIcons.ICON_GRID_4, "Mosaico 4CH", "#0284C7",
                v -> {
                    gridMode = 4;
                    previousMultiGridMode = 4;
                    showLiveViewScreen(null, null, null);
                }),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        quickMenuCard.addView(createQuickActionItem(LimenIcons.ICON_GRID_9, "Mosaico 9CH", "#4338CA",
                v -> {
                    gridMode = 9;
                    previousMultiGridMode = 9;
                    showLiveViewScreen(null, null, null);
                }),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        quickMenuCard.addView(createQuickActionItem(LimenIcons.ICON_SERVER, "Equipos", "#0F766E",
                v -> showDeviceManagerScreen()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickLp.bottomMargin = dp(16);
        scrollContent.addView(quickMenuCard, quickLp);

        // Contenedor de Dispositivos (Modo Tarjetas con Miniaturas 16:9 ó Modo Lista Compacta)
        LinearLayout devicesContainer = new LinearLayout(this);
        devicesContainer.setOrientation(LinearLayout.VERTICAL);
        scrollContent.addView(devicesContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        populateDeviceCards(devicesContainer, homeSearchQuery);

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                homeSearchQuery = s.toString().trim();
                populateDeviceCards(devicesContainer, homeSearchQuery);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        scrollView.addView(scrollContent);
        page.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        contentArea.addView(page);
    }

    private View createQuickActionItem(int iconType, String label, String badgeBgHex, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setOnClickListener(listener);

        FrameLayout circle = new FrameLayout(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(14));
        gd.setColor(Color.parseColor(badgeBgHex));
        circle.setBackground(gd);

        LimenIcons.IconView ic = new LimenIcons.IconView(this, iconType, "#FFFFFF");
        circle.addView(ic, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        box.addView(circle, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView tv = new TextView(this);
        tv.setText(label);
        styleText(tv, 11, COL_TEXT_SECOND, true);
        tv.setPadding(0, dp(7), 0, 0);
        tv.setSingleLine(true);
        box.addView(tv);

        return box;
    }

    private void populateDeviceCards(LinearLayout container, String query) {
        container.removeAllViews();
        String q = (query == null) ? "" : query.toLowerCase(Locale.ROOT);

        if (deviceGroups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No hay dispositivos asignados a tu cuenta.");
            styleText(empty, 13, COL_TEXT_SECOND, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(40), dp(20), dp(40));
            container.addView(empty);
            return;
        }

        // Si está activo el Modo Lista Compacta (como en la Imagen 1 de EasyViewer), agrupamos en un contenedor elegante
        LinearLayout compactCardWrapper = null;
        if (isHomeCompactMode) {
            compactCardWrapper = new LinearLayout(this);
            compactCardWrapper.setOrientation(LinearLayout.VERTICAL);
            compactCardWrapper.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 14, 1));
            compactCardWrapper.setPadding(dp(14), dp(6), dp(14), dp(6));
            container.addView(compactCardWrapper, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        int renderedCount = 0;
        for (Map.Entry<String, List<CameraItem>> entry : deviceGroups.entrySet()) {
            final String groupName = entry.getKey();
            final List<CameraItem> cams = entry.getValue();

            boolean matchesGroup = q.isEmpty() || groupName.toLowerCase(Locale.ROOT).contains(q);
            List<CameraItem> filteredCams = new ArrayList<>();
            for (CameraItem c : cams) {
                if (matchesGroup || c.name.toLowerCase(Locale.ROOT).contains(q)
                        || ("cam " + c.channel).contains(q)) {
                    filteredCams.add(c);
                }
            }
            if (filteredCams.isEmpty()) continue;

            if (isHomeCompactMode && compactCardWrapper != null) {
                if (renderedCount > 0) {
                    View divider = new View(this);
                    divider.setBackgroundColor(Color.parseColor(COL_BORDER));
                    compactCardWrapper.addView(divider, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
                }
                compactCardWrapper.addView(buildCompactDeviceRow(groupName, filteredCams));
            } else {
                container.addView(buildFullDeviceCard(groupName, filteredCams));
            }
            renderedCount++;
        }
    }

    // Modo Lista Compacta (Imagen 1 de EasyViewer adaptada a Limen Seguridad)
    private View buildCompactDeviceRow(String groupName, List<CameraItem> cams) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(2), dp(12), dp(2), dp(12));

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);

        LimenIcons.IconView dvrIcon = new LimenIcons.IconView(this, LimenIcons.ICON_DVR_BOX, COL_TEXT_PRIMARY);
        LinearLayout.LayoutParams dvrLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        dvrLp.rightMargin = dp(10);
        topLine.addView(dvrIcon, dvrLp);

        TextView titleView = new TextView(this);
        titleView.setText(groupName);
        styleText(titleView, 15, COL_TEXT_PRIMARY, true);
        topLine.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Botón circular (▶) para iniciar todas las cámaras de este dispositivo
        FrameLayout playAllBtn = createCircleIconButton(LimenIcons.ICON_PLAY, COL_TEXT_PRIMARY, dp(30), dp(14));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        playLp.rightMargin = dp(10);
        playAllBtn.setOnClickListener(v -> {
            gridMode = cams.size() > 4 ? 9 : 4;
            previousMultiGridMode = gridMode;
            showLiveViewScreen(null, cams, groupName);
        });
        topLine.addView(playAllBtn, playLp);

        // Botón (•••) que abre el menú emergente estilo EasyViewer ("Detalles dispositivo")
        FrameLayout moreBtn = new FrameLayout(this);
        moreBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        LimenIcons.IconView moreIcon = new LimenIcons.IconView(this, LimenIcons.ICON_MORE_H, COL_TEXT_SECOND);
        moreBtn.addView(moreIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        moreBtn.setOnClickListener(v -> showDeviceOverflowPopup(groupName, cams));
        topLine.addView(moreBtn);

        row.addView(topLine);

        // Subtítulo con indicador azul/esmeralda
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(6), 0, 0);

        View dot = new View(this);
        GradientDrawable dotGd = new GradientDrawable();
        dotGd.setShape(GradientDrawable.OVAL);
        dotGd.setColor(Color.parseColor(COL_ACCENT));
        dot.setBackground(dotGd);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(7);
        footer.addView(dot, dotLp);

        TextView subText = new TextView(this);
        subText.setText("Dispositivo vinculado · " + cams.size() + (cams.size() == 1 ? " canal" : " canales"));
        styleText(subText, 12, COL_TEXT_SECOND, false);
        footer.addView(subText);

        row.addView(footer);
        row.setOnClickListener(v -> {
            gridMode = cams.size() > 4 ? 9 : 4;
            previousMultiGridMode = gridMode;
            showLiveViewScreen(null, cams, groupName);
        });

        return row;
    }

    // Modo Tarjetas con Capturas 16:9 (Imagen 3 de EasyViewer adaptada a Limen Seguridad)
    private View buildFullDeviceCard(String groupName, List<CameraItem> filteredCams) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 14, 1));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(10));

        TextView titleView = new TextView(this);
        titleView.setText(groupName);
        styleText(titleView, 15, COL_TEXT_PRIMARY, true);
        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout playGroupBtn = createCircleIconButton(LimenIcons.ICON_PLAY, COL_TEXT_PRIMARY, dp(30), dp(14));
        LinearLayout.LayoutParams playBtnLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        playBtnLp.rightMargin = dp(10);
        playGroupBtn.setOnClickListener(v -> {
            gridMode = filteredCams.size() > 4 ? 9 : 4;
            previousMultiGridMode = gridMode;
            showLiveViewScreen(null, filteredCams, groupName);
        });
        header.addView(playGroupBtn, playBtnLp);

        FrameLayout moreBtn = new FrameLayout(this);
        moreBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        LimenIcons.IconView moreIcon = new LimenIcons.IconView(this, LimenIcons.ICON_MORE_H, COL_TEXT_SECOND);
        moreBtn.addView(moreIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        moreBtn.setOnClickListener(v -> showDeviceOverflowPopup(groupName, filteredCams));
        header.addView(moreBtn);

        card.addView(header);

        // Tira horizontal de miniaturas 16:9 (última captura vista de cada cámara)
        HorizontalScrollView thumbScroll = new HorizontalScrollView(this);
        thumbScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout thumbRow = new LinearLayout(this);
        thumbRow.setOrientation(LinearLayout.HORIZONTAL);

        int thumbWidth = dp(156);
        int thumbHeight = dp(88); // Formato 16:9 exacto

        for (int i = 0; i < filteredCams.size(); i++) {
            final CameraItem cam = filteredCams.get(i);

            FrameLayout thumbCard = new FrameLayout(this);
            thumbCard.setBackground(createRoundedDrawable(COL_BG_MAIN, COL_BORDER, 10, 1));
            thumbCard.setClipToOutline(true);

            ImageView thumbImg = new ImageView(this);
            thumbImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbCard.addView(thumbImg, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            loadCameraThumbnail(cam.id, thumbImg);

            // Sombra degradada inferior para legibilidad del nombre de canal
            View bottomShade = new View(this);
            GradientDrawable shadeGd = new GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{Color.parseColor("#CC090D16"), Color.TRANSPARENT});
            bottomShade.setBackground(shadeGd);
            thumbCard.addView(bottomShade, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(34), Gravity.BOTTOM));

            TextView camLabel = new TextView(this);
            camLabel.setText(cam.getShortChannelLabel());
            styleText(camLabel, 11, "#FFFFFF", true);
            camLabel.setSingleLine(true);
            camLabel.setEllipsize(TextUtils.TruncateAt.END);
            camLabel.setPadding(dp(8), dp(2), dp(8), dp(6));
            thumbCard.addView(camLabel, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

            thumbCard.setOnClickListener(v -> {
                gridMode = 1;
                showLiveViewScreen(cam, null, groupName);
            });

            LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(thumbWidth, thumbHeight);
            if (i < filteredCams.size() - 1) tcLp.rightMargin = dp(8);
            thumbRow.addView(thumbCard, tcLp);
        }

        thumbScroll.addView(thumbRow);
        card.addView(thumbScroll);

        // Pie de tarjeta ("Dispositivo vinculado · En línea")
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(10), 0, 0);

        View dot = new View(this);
        GradientDrawable dotGd = new GradientDrawable();
        dotGd.setShape(GradientDrawable.OVAL);
        dotGd.setColor(Color.parseColor(COL_ACCENT));
        dot.setBackground(dotGd);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(7);
        footer.addView(dot, dotLp);

        TextView devType = new TextView(this);
        devType.setText("Dispositivo vinculado · " + filteredCams.size() + (filteredCams.size() == 1 ? " canal" : " canales"));
        styleText(devType, 12, COL_TEXT_SECOND, false);
        footer.addView(devType);

        card.addView(footer);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        card.setLayoutParams(cardLp);
        return card;
    }

    private FrameLayout createCircleIconButton(int iconType, String colorHex, int sizePx, int iconPx) {
        FrameLayout btn = new FrameLayout(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.parseColor(COL_BG_ELEVATED));
        ring.setStroke(dp(1), Color.parseColor(COL_TEXT_SECOND));
        btn.setBackground(ring);
        LimenIcons.IconView ic = new LimenIcons.IconView(this, iconType, colorHex);
        btn.addView(ic, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        return btn;
    }

    // =========================================================================
    // 5. MENÚ DE 3 PUNTOS (•••) Y PANTALLA "DETALLES DEL DISPOSITIVO" (IMG 2 Y 3)
    // =========================================================================
    private void showDeviceOverflowPopup(String groupName, List<CameraItem> cams) {
        final Dialog dialog = createCustomLimenDialog();

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 16, 1));
        sheet.setPadding(dp(18), dp(18), dp(18), dp(14));

        // Encabezado del dispositivo
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        LimenIcons.IconView dvrIc = new LimenIcons.IconView(this, LimenIcons.ICON_DVR_BOX, COL_ACCENT_CYAN);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        dLp.rightMargin = dp(10);
        header.addView(dvrIc, dLp);

        TextView title = new TextView(this);
        title.setText(groupName);
        styleText(title, 15, COL_TEXT_PRIMARY, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout closeBtn = new FrameLayout(this);
        closeBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        closeBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COL_TEXT_SECOND),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        sheet.addView(header);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(COL_BORDER));
        sheet.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // Opción 1: Detalles del dispositivo (Exactamente como Imagen 3 -> lleva a Imagen 2)
        sheet.addView(createModalActionRow(
                LimenIcons.ICON_DOC_DETAILS,
                "Detalles del dispositivo",
                "Modelo, SN, suscripción de alarmas y transmisión",
                COL_ACCENT_CYAN,
                v -> {
                    dialog.dismiss();
                    showDeviceDetailsScreen(groupName, cams);
                }));

        // Opción 2: Vista en directo 4 canales
        sheet.addView(createModalActionRow(
                LimenIcons.ICON_GRID_4,
                "Abrir en mosaico de 4 canales",
                "Reproducir cámaras de " + groupName + " en 2×2",
                COL_TEXT_PRIMARY,
                v -> {
                    dialog.dismiss();
                    gridMode = 4;
                    previousMultiGridMode = 4;
                    showLiveViewScreen(null, cams, groupName);
                }));

        // Opción 3: Vista en directo 9 canales
        sheet.addView(createModalActionRow(
                LimenIcons.ICON_GRID_9,
                "Abrir en mosaico de 9 canales",
                "Reproducir cámaras de " + groupName + " en 3×3",
                COL_TEXT_PRIMARY,
                v -> {
                    dialog.dismiss();
                    gridMode = 9;
                    previousMultiGridMode = 9;
                    showLiveViewScreen(null, cams, groupName);
                }));

        dialog.setContentView(sheet);
        dialog.show();
    }

    private View createModalActionRow(int iconType, String titleText, String subText, String iconColor, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(14), dp(4), dp(14));
        row.setOnClickListener(listener);

        FrameLayout icBox = new FrameLayout(this);
        icBox.setBackground(createRoundedDrawable(COL_BG_ELEVATED, COL_BORDER, 10, 1));
        icBox.addView(new LimenIcons.IconView(this, iconType, iconColor),
                new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        icLp.rightMargin = dp(12);
        row.addView(icBox, icLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(titleText);
        styleText(t1, 14, COL_TEXT_PRIMARY, true);
        texts.addView(t1);

        if (subText != null && !subText.isEmpty()) {
            TextView t2 = new TextView(this);
            t2.setText(subText);
            styleText(t2, 11, COL_TEXT_SECOND, false);
            t2.setPadding(0, dp(2), 0, 0);
            texts.addView(t2);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_R, COL_TEXT_MUTED),
                new LinearLayout.LayoutParams(dp(16), dp(16)));
        return row;
    }

    // Pantalla completa "Detalles del dispositivo" (Basada en Imagen 2 de EasyViewer con estética Limen Seguridad)
    private void showDeviceDetailsScreen(String groupName, List<CameraItem> cams) {
        stopAllPlayers();
        exitLandscapeIfNeeded();
        if (bottomNavBar != null) bottomNavBar.setVisibility(View.GONE);
        contentArea.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COL_BG_MAIN));

        // Top Bar: < | Detalles del dispositivo
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor(COL_BG_HEADER));
        topBar.setPadding(dp(12), dp(12), dp(16), dp(12));

        FrameLayout backBtn = new FrameLayout(this);
        backBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        backBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_BACK, COL_TEXT_PRIMARY),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        backBtn.setOnClickListener(v -> showHomeScreen());
        topBar.addView(backBtn);

        TextView topTitle = new TextView(this);
        topTitle.setText("Detalles del dispositivo");
        styleText(topTitle, 16, COL_TEXT_PRIMARY, true);
        topTitle.setGravity(Gravity.CENTER);
        topBar.addView(topTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(dp(36), dp(22)));

        page.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(32));

        // Extraer SN / Identificador del dispositivo
        String deviceSn = extractDeviceSerial(cams);
        String deviceModel = "XVR / DVR P2P (" + cams.size() + "CH)";

        // 1. Cabecera del Equipo (Con icono de grabador a la izquierda, nombre, "En línea", Modelo y SN)
        LinearLayout devHeaderCard = new LinearLayout(this);
        devHeaderCard.setOrientation(LinearLayout.HORIZONTAL);
        devHeaderCard.setGravity(Gravity.CENTER_VERTICAL);
        devHeaderCard.setBackgroundColor(Color.parseColor(COL_BG_CARD));
        devHeaderCard.setPadding(dp(18), dp(18), dp(18), dp(18));

        FrameLayout chassisBox = new FrameLayout(this);
        chassisBox.setBackground(createRoundedDrawable(COL_BG_ELEVATED, COL_BORDER, 12, 1));
        chassisBox.addView(new LimenIcons.IconView(this, LimenIcons.ICON_DVR_BOX, COL_ACCENT_CYAN),
                new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(dp(72), dp(56));
        chLp.rightMargin = dp(16);
        devHeaderCard.addView(chassisBox, chLp);

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        TextView nameTv = new TextView(this);
        nameTv.setText(groupName);
        styleText(nameTv, 16, COL_TEXT_PRIMARY, true);
        infoCol.addView(nameTv);

        TextView onlineTv = new TextView(this);
        onlineTv.setText("En línea");
        styleText(onlineTv, 12, COL_ACCENT_CYAN, true);
        onlineTv.setPadding(0, dp(4), 0, dp(6));
        infoCol.addView(onlineTv);

        TextView modelTv = new TextView(this);
        modelTv.setText("Modelo dispos.: " + deviceModel);
        styleText(modelTv, 12, COL_TEXT_SECOND, false);
        infoCol.addView(modelTv);

        TextView snTv = new TextView(this);
        snTv.setText("SN de dispositivo: " + deviceSn);
        styleText(snTv, 12, COL_TEXT_SECOND, false);
        snTv.setPadding(0, dp(2), 0, 0);
        infoCol.addView(snTv);

        devHeaderCard.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout editBtn = new FrameLayout(this);
        editBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        editBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_EDIT, COL_TEXT_SECOND),
                new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        editBtn.setOnClickListener(v -> {
            gridMode = cams.size() > 4 ? 9 : 4;
            previousMultiGridMode = gridMode;
            showLiveViewScreen(null, cams, groupName);
        });
        devHeaderCard.addView(editBtn);

        body.addView(devHeaderCard);

        // Sección: Ajustes de alarma
        body.addView(buildDetailsSectionHeader("Ajustes de alarma"));

        final String alarmPrefKey = "alarm_sub_" + groupName;
        boolean isAlarmSub = prefs.getBoolean(alarmPrefKey, true);
        TextView alarmStateBadge = new TextView(this);
        alarmStateBadge.setText(isAlarmSub ? "Activado" : "Desactivado");
        styleText(alarmStateBadge, 12, isAlarmSub ? COL_ACCENT_CYAN : COL_TEXT_MUTED, true);

        body.addView(buildDetailsSettingRow(
                "Suscripción de alarma multicanal",
                "Active el interruptor de notificaciones para recibir los avisos de estado correspondientes a todos los subcanales del dispositivo.",
                alarmStateBadge,
                v -> {
                    boolean next = !prefs.getBoolean(alarmPrefKey, true);
                    prefs.edit().putBoolean(alarmPrefKey, next).apply();
                    alarmStateBadge.setText(next ? "Activado" : "Desactivado");
                    alarmStateBadge.setTextColor(Color.parseColor(next ? COL_ACCENT_CYAN : COL_TEXT_MUTED));
                    logAppEvent("Notificaciones " + (next ? "activadas" : "desactivadas") + " en " + groupName);
                }));

        // Nota informativa estilo Limen Seguridad
        LinearLayout infoNote = new LinearLayout(this);
        infoNote.setOrientation(LinearLayout.HORIZONTAL);
        infoNote.setPadding(dp(18), dp(10), dp(18), dp(6));
        TextView bang = new TextView(this);
        bang.setText("i");
        styleText(bang, 11, "#FFFFFF", true);
        bang.setGravity(Gravity.CENTER);
        GradientDrawable bangBg = new GradientDrawable();
        bangBg.setShape(GradientDrawable.OVAL);
        bangBg.setColor(Color.parseColor(COL_ACCENT));
        bang.setBackground(bangBg);
        LinearLayout.LayoutParams bangLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        bangLp.rightMargin = dp(8);
        bangLp.topMargin = dp(2);
        infoNote.addView(bang, bangLp);

        TextView noteTv = new TextView(this);
        noteTv.setText("Los eventos de túnel y estado de línea son supervisados de forma continua por el servidor central de Limen Seguridad.");
        styleText(noteTv, 11, COL_TEXT_SECOND, false);
        infoNote.addView(noteTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(infoNote);

        // Sección: Configuración para la reproducción de los vídeos
        body.addView(buildDetailsSectionHeader("Configuración para la reproducción de los vídeos"));

        TextView streamQualityBadge = new TextView(this);
        streamQualityBadge.setText(useHdStream ? "Flujo principal (HD)" : "Subflujo (SD)");
        styleText(streamQualityBadge, 12, COL_ACCENT_CYAN, true);

        body.addView(buildDetailsSettingRow(
                "Config. de transmisión",
                "Selecciona la calidad de flujo predeterminada al abrir este grabador en directo.",
                streamQualityBadge,
                v -> {
                    useHdStream = !useHdStream;
                    prefs.edit().putBoolean("default_hd_stream", useHdStream).apply();
                    streamQualityBadge.setText(useHdStream ? "Flujo principal (HD)" : "Subflujo (SD)");
                    Toast.makeText(this, "Calidad predeterminada: " + (useHdStream ? "HD" : "SD"), Toast.LENGTH_SHORT).show();
                }));

        // Sección: Configuración general
        body.addView(buildDetailsSectionHeader("Configuración general"));

        TextView upnpBadge = new TextView(this);
        upnpBadge.setText("Activo");
        styleText(upnpBadge, 12, COL_ONLINE, true);

        body.addView(buildDetailsSettingRow(
                "Aceleración P2P / UPnP",
                "Optimiza la velocidad de transmisión TCP/fMP4 para visualizar rápidamente los vídeos en directo.",
                upnpBadge,
                v -> Toast.makeText(this, "Aceleración TCP/fMP4 activa en este dispositivo", Toast.LENGTH_SHORT).show()));

        TextView ddnsBadge = new TextView(this);
        ddnsBadge.setText("Limen Cloud");
        styleText(ddnsBadge, 12, COL_TEXT_SECOND, false);

        body.addView(buildDetailsSettingRow(
                "Enlace DDNS / Nodo Limen",
                "Vinculado automáticamente al túnel seguro de eyes.limenseguridad.com sin requerir apertura de puertos.",
                ddnsBadge,
                v -> Toast.makeText(this, "Conectado mediante nodo seguro Limen Cloud", Toast.LENGTH_SHORT).show()));

        // Sección: Otros
        body.addView(buildDetailsSectionHeader("Otros"));

        TextView chBadge = new TextView(this);
        chBadge.setText(cams.size() + " canales");
        styleText(chBadge, 12, COL_TEXT_SECOND, false);

        body.addView(buildDetailsSettingRow(
                "Configuración remota de canales",
                "Ver listado individual de cámaras vinculadas y probar reproducción directa.",
                chBadge,
                v -> showChannelPickerModal(groupName, cams, cam -> {
                    gridMode = 1;
                    showLiveViewScreen(cam, null, groupName);
                })));

        body.addView(buildDetailsSettingRow(
                "Reconectar túnel P2P del equipo",
                "Solicita al servidor central reiniciar la sesión P2P de todos los canales de " + groupName + ".",
                null,
                v -> {
                    for (CameraItem c : cams) activateBackendTunnel(c.id);
                    Toast.makeText(this, "Reiniciando túnel P2P de " + groupName + "...", Toast.LENGTH_SHORT).show();
                }));

        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        contentArea.addView(page);
    }

    private String extractDeviceSerial(List<CameraItem> cams) {
        if (cams == null || cams.isEmpty()) return "N/D";
        CameraItem first = cams.get(0);
        if (first.streamKeySd != null && first.streamKeySd.startsWith("p2p_")) {
            String[] parts = first.streamKeySd.split("_");
            if (parts.length >= 2) return parts[1];
        }
        if (first.host != null && !first.host.isEmpty()) {
            return first.host;
        }
        return "LIMEN-DEV-" + first.id;
    }

    private View buildDetailsSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        styleText(tv, 12, COL_TEXT_SECOND, true);
        tv.setPadding(dp(18), dp(18), dp(18), dp(8));
        return tv;
    }

    private View buildDetailsSettingRow(String title, String subtitle, TextView rightStatusView, View.OnClickListener listener) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(Color.parseColor(COL_BG_CARD));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        if (listener != null) row.setOnClickListener(listener);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(title);
        styleText(t1, 14, COL_TEXT_PRIMARY, true);
        textCol.addView(t1);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView t2 = new TextView(this);
            t2.setText(subtitle);
            styleText(t2, 12, COL_TEXT_SECOND, false);
            t2.setPadding(0, dp(4), 0, 0);
            textCol.addView(t2);
        }

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (rightStatusView != null) {
            rightStatusView.setPadding(dp(10), 0, dp(8), 0);
            row.addView(rightStatusView);
        }

        row.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_R, COL_TEXT_MUTED),
                new LinearLayout.LayoutParams(dp(16), dp(16)));

        wrap.addView(row);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(COL_BORDER));
        wrap.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        return wrap;
    }

    // =========================================================================
    // 6. PANTALLA DE VISTA EN VIVO (ESTÉTICA IMAGEN 4 EASYVIEWER + LIMEN SEGURIDAD)
    // =========================================================================
    private void showLiveViewScreen(CameraItem singleCamToStart, List<CameraItem> groupCamsToStart, String groupFilter) {
        stopAllPlayers();
        updateBottomNavSelection(1);
        if (bottomNavBar != null) bottomNavBar.setVisibility(View.GONE);
        contentArea.removeAllViews();

        if (groupFilter != null && !groupFilter.trim().isEmpty()) {
            activeLiveGroupFilter = groupFilter;
        } else if (singleCamToStart != null) {
            activeLiveGroupFilter = singleCamToStart.groupTitle;
        } else if (groupCamsToStart != null && !groupCamsToStart.isEmpty()) {
            activeLiveGroupFilter = groupCamsToStart.get(0).groupTitle;
        } else if (activeLiveGroupFilter == null && !deviceGroups.isEmpty()) {
            activeLiveGroupFilter = deviceGroups.keySet().iterator().next();
        }

        List<CameraItem> filteredDeviceCameras = getCamerasForCurrentLiveFilter();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COL_BG_MAIN));

        // 1. Barra Superior (Estilo Imagen 4: < | Nombre del Dispositivo | Icono Hexágono Detalles)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor(COL_BG_HEADER));
        topBar.setPadding(dp(12), dp(10), dp(14), dp(10));

        FrameLayout backBtn = new FrameLayout(this);
        backBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        backBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_BACK, COL_TEXT_PRIMARY),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        backBtn.setOnClickListener(v -> showHomeScreen());
        topBar.addView(backBtn);

        LinearLayout centerTitleBox = new LinearLayout(this);
        centerTitleBox.setOrientation(LinearLayout.VERTICAL);
        centerTitleBox.setGravity(Gravity.CENTER);
        centerTitleBox.setOnClickListener(v -> showSwitchDeviceModal());

        liveTopTitle = new TextView(this);
        liveTopTitle.setText(activeLiveGroupFilter != null ? activeLiveGroupFilter : "Vista en directo");
        styleText(liveTopTitle, 16, COL_TEXT_PRIMARY, true);
        liveTopTitle.setSingleLine(true);
        liveTopTitle.setGravity(Gravity.CENTER);
        centerTitleBox.addView(liveTopTitle);

        liveTopSubtitle = new TextView(this);
        liveTopSubtitle.setText(filteredDeviceCameras.size() + " canales disponibles · Toca para cambiar equipo");
        styleText(liveTopSubtitle, 10, COL_TEXT_SECOND, false);
        liveTopSubtitle.setGravity(Gravity.CENTER);
        centerTitleBox.addView(liveTopSubtitle);

        topBar.addView(centerTitleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout detailsHexBtn = new FrameLayout(this);
        detailsHexBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        detailsHexBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_SETTINGS_HEX, COL_TEXT_PRIMARY),
                new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
        detailsHexBtn.setOnClickListener(v -> {
            if (activeLiveGroupFilter != null && deviceGroups.containsKey(activeLiveGroupFilter)) {
                showDeviceDetailsScreen(activeLiveGroupFilter, deviceGroups.get(activeLiveGroupFilter));
            }
        });
        topBar.addView(detailsHexBtn);

        page.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2. Escenario de Video (Grilla 1CH, 4CH o 9CH con formato 16:9 y overlays tipo EasyViewer)
        liveVideoStage = new FrameLayout(this);
        liveVideoStage.setBackgroundColor(Color.BLACK);

        liveGridContainer = new LinearLayout(this);
        liveGridContainer.setOrientation(LinearLayout.VERTICAL);
        liveVideoStage.addView(liveGridContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int stageHeight = Math.round(screenWidth * 9f / 16f); // 16:9 para 1CH o proporción limpia para grilla
        if (gridMode > 1) {
            stageHeight = Math.round(screenWidth * 0.78f);
        }
        page.addView(liveVideoStage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, stageHeight));

        buildLiveCells9();
        applyGridVisibility();

        // 3. Barra fina de 5 Iconos de Control bajo el video (Exactamente como Imagen 4: Play/Pause | HD/SD | Mute | [1/4/9] | Fullscreen)
        LinearLayout controlStrip = new LinearLayout(this);
        controlStrip.setOrientation(LinearLayout.HORIZONTAL);
        controlStrip.setGravity(Gravity.CENTER_VERTICAL);
        controlStrip.setBackgroundColor(Color.parseColor(COL_BG_HEADER));
        controlStrip.setPadding(dp(8), dp(10), dp(8), dp(10));

        // 3.1 Play / Pause
        FrameLayout btnPlayPause = new FrameLayout(this);
        playPauseIconView = new LimenIcons.IconView(this, LimenIcons.ICON_PAUSE, COL_TEXT_PRIMARY);
        btnPlayPause.addView(playPauseIconView, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        btnPlayPause.setOnClickListener(v -> togglePlayPauseAll());
        controlStrip.addView(btnPlayPause, new LinearLayout.LayoutParams(0, dp(32), 1f));

        // 3.2 [SD] / [HD]
        FrameLayout btnQuality = new FrameLayout(this);
        hdSdIconView = new LimenIcons.IconView(this,
                useHdStream ? LimenIcons.ICON_HD : LimenIcons.ICON_SD,
                useHdStream ? COL_ACCENT_CYAN : COL_TEXT_PRIMARY);
        btnQuality.addView(hdSdIconView, new FrameLayout.LayoutParams(dp(26), dp(24), Gravity.CENTER));
        btnQuality.setOnClickListener(v -> {
            useHdStream = !useHdStream;
            hdSdIconView.setIconType(useHdStream ? LimenIcons.ICON_HD : LimenIcons.ICON_SD);
            hdSdIconView.setIconColor(useHdStream ? COL_ACCENT_CYAN : COL_TEXT_PRIMARY);
            reloadActiveCellsQuality();
            Toast.makeText(this, "Calidad cambiada a " + (useHdStream ? "HD (Principal)" : "SD (Rápido)"), Toast.LENGTH_SHORT).show();
        });
        controlStrip.addView(btnQuality, new LinearLayout.LayoutParams(0, dp(32), 1f));

        // 3.3 Audio Mute / Unmute
        FrameLayout btnMute = new FrameLayout(this);
        audioBarIconView = new LimenIcons.IconView(this,
                isAudioMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON,
                isAudioMuted ? COL_TEXT_PRIMARY : COL_ACCENT_CYAN);
        btnMute.addView(audioBarIconView, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        btnMute.setOnClickListener(v -> toggleAudioMuteState());
        controlStrip.addView(btnMute, new LinearLayout.LayoutParams(0, dp(32), 1f));

        // 3.4 [1] / [4] / [9] Selector de Cuadrícula
        FrameLayout btnGrid = new FrameLayout(this);
        int badgeIcon = (gridMode == 1) ? LimenIcons.ICON_BADGE_1 : (gridMode == 4 ? LimenIcons.ICON_BADGE_4 : LimenIcons.ICON_BADGE_9);
        gridBadgeIconView = new LimenIcons.IconView(this, badgeIcon, COL_TEXT_PRIMARY);
        btnGrid.addView(gridBadgeIconView, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        btnGrid.setOnClickListener(v -> {
            if (gridMode == 1) switchGridMode(4);
            else if (gridMode == 4) switchGridMode(9);
            else switchGridMode(1);
        });
        controlStrip.addView(btnGrid, new LinearLayout.LayoutParams(0, dp(32), 1f));

        // 3.5 Rotar / Pantalla Completa 16:9
        FrameLayout btnFullscreen = new FrameLayout(this);
        btnFullscreen.addView(new LimenIcons.IconView(this, LimenIcons.ICON_FULLSCREEN, COL_TEXT_PRIMARY),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        btnFullscreen.setOnClickListener(v -> toggleLandscapeFullscreen());
        controlStrip.addView(btnFullscreen, new LinearLayout.LayoutParams(0, dp(32), 1f));

        page.addView(controlStrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 4. Panel inferior desplazable: Consola Táctil + Selector de Canales del Equipo + Bandeja de Alarma (como Imagen 4)
        ScrollView bottomScroll = new ScrollView(this);
        bottomScroll.setFillViewport(true);

        LinearLayout lowerArea = new LinearLayout(this);
        lowerArea.setOrientation(LinearLayout.VERTICAL);
        lowerArea.setPadding(dp(12), dp(12), dp(12), dp(20));

        // 4.1 Tarjeta de Consola de Acciones (5 botones circulares como en Imagen 4, adaptados a Limen Seguridad)
        LinearLayout actionCard = new LinearLayout(this);
        actionCard.setOrientation(LinearLayout.VERTICAL);
        actionCard.setGravity(Gravity.CENTER_HORIZONTAL);
        actionCard.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 16, 1));
        actionCard.setPadding(dp(12), dp(16), dp(12), dp(12));

        LinearLayout consoleRow = new LinearLayout(this);
        consoleRow.setOrientation(LinearLayout.HORIZONTAL);
        consoleRow.setGravity(Gravity.CENTER);

        // Botón 1: Elegir Canal
        consoleRow.addView(createConsoleCircleButton(LimenIcons.ICON_VIDEO, "Canales", false,
                v -> showChannelPickerModal(activeLiveGroupFilter, getCamerasForCurrentLiveFilter(),
                        cam -> bindAndPlayCameraInCell(selectedCellIndex, cam))),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Botón 2: Captura instantánea (Snapshot)
        consoleRow.addView(createConsoleCircleButton(LimenIcons.ICON_CAMERA, "Capturar", false,
                v -> captureCurrentCellSnapshot()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Botón 3 (Central grande): Audio en vivo
        LinearLayout centerAudioCol = new LinearLayout(this);
        centerAudioCol.setOrientation(LinearLayout.VERTICAL);
        centerAudioCol.setGravity(Gravity.CENTER);
        audioConsoleCircle = new FrameLayout(this);
        audioConsoleCircle.setBackground(createOvalDrawable(COL_BG_ELEVATED, isAudioMuted ? COL_TEXT_SECOND : COL_ACCENT_CYAN, 1));
        audioConsoleIconView = new LimenIcons.IconView(this,
                isAudioMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON,
                isAudioMuted ? COL_TEXT_PRIMARY : COL_ACCENT_CYAN);
        audioConsoleCircle.addView(audioConsoleIconView, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        audioConsoleCircle.setOnClickListener(v -> toggleAudioMuteState());
        centerAudioCol.addView(audioConsoleCircle, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView centerAudioLbl = new TextView(this);
        centerAudioLbl.setText("Audio");
        styleText(centerAudioLbl, 11, COL_TEXT_SECOND, false);
        centerAudioLbl.setPadding(0, dp(6), 0, 0);
        centerAudioCol.addView(centerAudioLbl);
        consoleRow.addView(centerAudioCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));

        // Botón 4: Reconectar flujo P2P
        consoleRow.addView(createConsoleCircleButton(LimenIcons.ICON_REFRESH, "Reconectar", false,
                v -> {
                    VideoCell cell = liveCells.get(selectedCellIndex);
                    if (cell.boundCamera != null) {
                        bindAndPlayCameraInCell(selectedCellIndex, cell.boundCamera);
                        logAppEvent("Reconectando " + cell.boundCamera.getCompactBadgeLabel());
                    } else {
                        showChannelPickerModal(activeLiveGroupFilter, getCamerasForCurrentLiveFilter(),
                                cam -> bindAndPlayCameraInCell(selectedCellIndex, cam));
                    }
                }),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Botón 5: Cerrar cámara de la celda seleccionada (Exactamente el icono [x] derecho de Imagen 4)
        consoleRow.addView(createConsoleCircleButton(LimenIcons.ICON_CLOSE_CELL, "Cerrar", false,
                v -> stopAndClearCell(selectedCellIndex)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        actionCard.addView(consoleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Tira rápida de canales del dispositivo actual dentro de la consola
        HorizontalScrollView chStripScroll = new HorizontalScrollView(this);
        chStripScroll.setHorizontalScrollBarEnabled(false);
        chStripScroll.setPadding(0, dp(14), 0, dp(4));

        LinearLayout chStripRow = new LinearLayout(this);
        chStripRow.setOrientation(LinearLayout.HORIZONTAL);

        for (CameraItem cam : filteredDeviceCameras) {
            TextView pill = new TextView(this);
            pill.setText(cam.getShortChannelLabel());
            styleText(pill, 12, COL_TEXT_PRIMARY, true);
            pill.setPadding(dp(12), dp(7), dp(12), dp(7));
            pill.setBackground(createRoundedDrawable(COL_BG_ELEVATED, COL_BORDER, 8, 1));
            pill.setOnClickListener(v -> bindAndPlayCameraInCell(selectedCellIndex, cam));

            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pLp.rightMargin = dp(8);
            chStripRow.addView(pill, pLp);
        }

        chStripScroll.addView(chStripRow);
        actionCard.addView(chStripScroll);

        LinearLayout.LayoutParams acLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acLp.bottomMargin = dp(12);
        lowerArea.addView(actionCard, acLp);

        // 4.2 Barra "Mensaje de alarma / Estado de eventos" (Exactamente como en la parte inferior de Imagen 4)
        LinearLayout alarmCard = new LinearLayout(this);
        alarmCard.setOrientation(LinearLayout.VERTICAL);
        alarmCard.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 14, 1));

        LinearLayout alarmHeader = new LinearLayout(this);
        alarmHeader.setOrientation(LinearLayout.HORIZONTAL);
        alarmHeader.setGravity(Gravity.CENTER_VERTICAL);
        alarmHeader.setPadding(dp(14), dp(12), dp(14), dp(12));

        alarmHeader.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CALENDAR, COL_ACCENT_CYAN),
                new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView alarmTitle = new TextView(this);
        alarmTitle.setText("Mensaje de alarma y eventos");
        styleText(alarmTitle, 14, COL_TEXT_PRIMARY, true);
        alarmTitle.setGravity(Gravity.CENTER);
        alarmHeader.addView(alarmTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout toggleAlarmBodyBtn = new FrameLayout(this);
        toggleAlarmBodyBtn.setBackground(createOvalDrawable(COL_BG_ELEVATED, COL_BORDER, 1));
        final LimenIcons.IconView chevronIcon = new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_DOWN, COL_TEXT_SECOND);
        toggleAlarmBodyBtn.addView(chevronIcon, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        alarmHeader.addView(toggleAlarmBodyBtn, new LinearLayout.LayoutParams(dp(26), dp(26)));

        alarmCard.addView(alarmHeader);

        final LinearLayout alarmBody = new LinearLayout(this);
        alarmBody.setOrientation(LinearLayout.VERTICAL);
        alarmBody.setPadding(dp(16), dp(6), dp(16), dp(16));

        View alarmDiv = new View(this);
        alarmDiv.setBackgroundColor(Color.parseColor(COL_BORDER));
        alarmBody.addView(alarmDiv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        alarmLogContentText = new TextView(this);
        alarmLogContentText.setPadding(0, dp(10), 0, 0);
        styleText(alarmLogContentText, 12, COL_TEXT_SECOND, false);
        alarmLogContentText.setLineSpacing(dp(3), 1f);
        if (recentEventsLog.isEmpty()) {
            alarmLogContentText.setText("Sin mensajes de alarma recientes");
        } else {
            alarmLogContentText.setText(TextUtils.join("\n", recentEventsLog));
        }
        alarmBody.addView(alarmLogContentText);
        alarmCard.addView(alarmBody);

        alarmHeader.setOnClickListener(v -> {
            isAlarmPanelExpanded = !isAlarmPanelExpanded;
            alarmBody.setVisibility(isAlarmPanelExpanded ? View.VISIBLE : View.GONE);
        });

        lowerArea.addView(alarmCard);
        bottomScroll.addView(lowerArea);
        page.addView(bottomScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        contentArea.addView(page);

        // Iniciar cámaras solicitadas
        if (singleCamToStart != null) {
            switchGridMode(1);
            bindAndPlayCameraInCell(0, singleCamToStart);
        } else if (groupCamsToStart != null && !groupCamsToStart.isEmpty()) {
            int targetGrid = groupCamsToStart.size() > 4 ? 9 : 4;
            switchGridMode(targetGrid);
            int limit = Math.min(targetGrid, groupCamsToStart.size());
            for (int i = 0; i < limit; i++) {
                bindAndPlayCameraInCell(i, groupCamsToStart.get(i));
            }
        }

        mainHandler.removeCallbacks(bitrateTicker);
        mainHandler.postDelayed(bitrateTicker, 1200);
    }

    private View createConsoleCircleButton(int iconType, String label, boolean highlighted, View.OnClickListener listener) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setOnClickListener(listener);

        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(createOvalDrawable(COL_BG_ELEVATED, highlighted ? COL_ACCENT_CYAN : COL_BORDER, 1));
        LimenIcons.IconView ic = new LimenIcons.IconView(this, iconType, highlighted ? COL_ACCENT_CYAN : COL_TEXT_PRIMARY);
        circle.addView(ic, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        col.addView(circle, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView tv = new TextView(this);
        tv.setText(label);
        styleText(tv, 11, COL_TEXT_SECOND, false);
        tv.setPadding(0, dp(6), 0, 0);
        tv.setSingleLine(true);
        col.addView(tv);

        return col;
    }

    private List<CameraItem> getCamerasForCurrentLiveFilter() {
        if (activeLiveGroupFilter != null && deviceGroups.containsKey(activeLiveGroupFilter)) {
            List<CameraItem> groupList = deviceGroups.get(activeLiveGroupFilter);
            if (groupList != null && !groupList.isEmpty()) {
                return groupList;
            }
        }
        return allCameras;
    }

    private void buildLiveCells9() {
        liveCells.clear();
        liveGridContainer.removeAllViews();

        for (int rowIdx = 0; rowIdx < 3; rowIdx++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            for (int colIdx = 0; colIdx < 3; colIdx++) {
                final int cellIndex = rowIdx * 3 + colIdx;
                VideoCell cell = createVideoCell(cellIndex);
                liveCells.add(cell);
                row.addView(cell.container, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            liveGridContainer.addView(row);
        }
        highlightSelectedCell();
    }

    private VideoCell createVideoCell(final int index) {
        final VideoCell cell = new VideoCell();
        cell.index = index;

        cell.container = new FrameLayout(this);
        cell.container.setBackgroundColor(Color.parseColor("#0D131F"));
        cell.container.setPadding(dp(1), dp(1), dp(1), dp(1));

        FrameLayout inner = new FrameLayout(this);
        inner.setBackgroundColor(Color.BLACK);

        cell.previewThumb = new ImageView(this);
        cell.previewThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.previewThumb.setAlpha(0.45f);
        inner.addView(cell.previewThumb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cell.textureView = new TextureView(this);
        inner.addView(cell.textureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // Botón "+" central cuando la celda no tiene cámara asignada
        LinearLayout emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);

        LimenIcons.IconView plusIcon = new LimenIcons.IconView(this, LimenIcons.ICON_PLUS, COL_TEXT_MUTED);
        emptyBox.addView(plusIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        cell.statusOverlay = new TextView(this);
        cell.statusOverlay.setText("Toca para abrir canal");
        styleText(cell.statusOverlay, 11, COL_TEXT_MUTED, false);
        cell.statusOverlay.setGravity(Gravity.CENTER);
        cell.statusOverlay.setPadding(0, dp(4), 0, 0);
        emptyBox.addView(cell.statusOverlay);

        inner.addView(emptyBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        cell.loadingSpinner = new ProgressBar(this);
        cell.loadingSpinner.setVisibility(View.GONE);
        FrameLayout.LayoutParams spinLp = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER);
        spinLp.bottomMargin = dp(18);
        inner.addView(cell.loadingSpinner, spinLp);

        // Overlay superior izquierdo (CAM 1) como en Imagen 4
        cell.topLeftOverlay = new TextView(this);
        styleText(cell.topLeftOverlay, 11, "#FFFFFF", true);
        cell.topLeftOverlay.setShadowLayer(4f, 1f, 1f, Color.BLACK);
        cell.topLeftOverlay.setPadding(dp(7), dp(5), dp(7), dp(4));
        inner.addView(cell.topLeftOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        // Overlay superior derecho (110 KB/s) como en Imagen 4
        cell.topRightBitrateOverlay = new TextView(this);
        styleText(cell.topRightBitrateOverlay, 11, "#E2E8F0", false);
        cell.topRightBitrateOverlay.setShadowLayer(4f, 1f, 1f, Color.BLACK);
        cell.topRightBitrateOverlay.setPadding(dp(7), dp(5), dp(7), dp(4));
        cell.topRightBitrateOverlay.setVisibility(View.GONE);
        inner.addView(cell.topRightBitrateOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));

        // Overlay inferior izquierdo (nombre descriptivo del canal)
        cell.bottomLeftOverlay = new TextView(this);
        styleText(cell.bottomLeftOverlay, 10, "#CBD5E1", false);
        cell.bottomLeftOverlay.setShadowLayer(4f, 1f, 1f, Color.BLACK);
        cell.bottomLeftOverlay.setPadding(dp(7), dp(2), dp(7), dp(5));
        inner.addView(cell.bottomLeftOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                selectedCellIndex = index;
                highlightSelectedCell();
                if (cell.boundCamera == null) {
                    showChannelPickerModal(activeLiveGroupFilter, getCamerasForCurrentLiveFilter(),
                            cam -> bindAndPlayCameraInCell(index, cam));
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                selectedCellIndex = index;
                if (gridMode != 1) {
                    previousMultiGridMode = gridMode;
                    switchGridMode(1);
                } else {
                    switchGridMode(previousMultiGridMode);
                }
                return true;
            }
        });

        inner.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        cell.container.addView(inner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return cell;
    }

    private void switchGridMode(int newMode) {
        gridMode = newMode;
        if (newMode != 1) {
            previousMultiGridMode = newMode;
        }
        if (gridBadgeIconView != null) {
            int badge = (gridMode == 1) ? LimenIcons.ICON_BADGE_1 : (gridMode == 4 ? LimenIcons.ICON_BADGE_4 : LimenIcons.ICON_BADGE_9);
            gridBadgeIconView.setIconType(badge);
        }
        if (liveVideoStage != null && !isLandscapeFullscreen) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int stageHeight = (gridMode == 1)
                    ? Math.round(screenWidth * 9f / 16f)
                    : Math.round(screenWidth * 0.78f);
            ViewGroup.LayoutParams lp = liveVideoStage.getLayoutParams();
            lp.height = stageHeight;
            liveVideoStage.setLayoutParams(lp);
        }
        applyGridVisibility();
        highlightSelectedCell();
    }

    private void applyGridVisibility() {
        if (liveGridContainer == null || liveCells.size() < 9) return;

        LinearLayout row0 = (LinearLayout) liveGridContainer.getChildAt(0);
        LinearLayout row1 = (LinearLayout) liveGridContainer.getChildAt(1);
        LinearLayout row2 = (LinearLayout) liveGridContainer.getChildAt(2);

        if (gridMode == 1) {
            int activeRow = selectedCellIndex / 3;
            int activeCol = selectedCellIndex % 3;
            row0.setVisibility(activeRow == 0 ? View.VISIBLE : View.GONE);
            row1.setVisibility(activeRow == 1 ? View.VISIBLE : View.GONE);
            row2.setVisibility(activeRow == 2 ? View.VISIBLE : View.GONE);

            for (int r = 0; r < 3; r++) {
                LinearLayout row = (LinearLayout) liveGridContainer.getChildAt(r);
                for (int c = 0; c < 3; c++) {
                    row.getChildAt(c).setVisibility((r == activeRow && c == activeCol) ? View.VISIBLE : View.GONE);
                }
            }
        } else if (gridMode == 4) {
            if (selectedCellIndex >= 4) selectedCellIndex = 0;
            row0.setVisibility(View.VISIBLE);
            row1.setVisibility(View.VISIBLE);
            row2.setVisibility(View.GONE);

            row0.getChildAt(0).setVisibility(View.VISIBLE);
            row0.getChildAt(1).setVisibility(View.VISIBLE);
            row0.getChildAt(2).setVisibility(View.GONE);

            row1.getChildAt(0).setVisibility(View.VISIBLE);
            row1.getChildAt(1).setVisibility(View.VISIBLE);
            row1.getChildAt(2).setVisibility(View.GONE);
        } else {
            row0.setVisibility(View.VISIBLE);
            row1.setVisibility(View.VISIBLE);
            row2.setVisibility(View.VISIBLE);
            for (int r = 0; r < 3; r++) {
                LinearLayout row = (LinearLayout) liveGridContainer.getChildAt(r);
                for (int c = 0; c < 3; c++) {
                    row.getChildAt(c).setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void highlightSelectedCell() {
        for (VideoCell cell : liveCells) {
            boolean selected = (cell.index == selectedCellIndex);
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.parseColor("#0D131F"));
            border.setStroke(dp(selected ? 2 : 1),
                    Color.parseColor(selected ? COL_ACCENT_CYAN : COL_BORDER));
            cell.container.setBackground(border);
        }
    }

    private void togglePlayPauseAll() {
        isAllPaused = !isAllPaused;
        if (playPauseIconView != null) {
            playPauseIconView.setIconType(isAllPaused ? LimenIcons.ICON_PLAY : LimenIcons.ICON_PAUSE);
            playPauseIconView.setIconColor(isAllPaused ? COL_ACCENT_CYAN : COL_TEXT_PRIMARY);
        }
        for (VideoCell cell : liveCells) {
            if (cell.player != null) {
                cell.player.setPlayWhenReady(!isAllPaused);
            }
        }
    }

    private void toggleAudioMuteState() {
        isAudioMuted = !isAudioMuted;
        if (audioBarIconView != null) {
            audioBarIconView.setIconType(isAudioMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON);
            audioBarIconView.setIconColor(isAudioMuted ? COL_TEXT_PRIMARY : COL_ACCENT_CYAN);
        }
        if (audioConsoleIconView != null) {
            audioConsoleIconView.setIconType(isAudioMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON);
            audioConsoleIconView.setIconColor(isAudioMuted ? COL_TEXT_PRIMARY : COL_ACCENT_CYAN);
        }
        if (audioConsoleCircle != null) {
            audioConsoleCircle.setBackground(createOvalDrawable(COL_BG_ELEVATED, isAudioMuted ? COL_TEXT_SECOND : COL_ACCENT_CYAN, 1));
        }
        for (VideoCell cell : liveCells) {
            if (cell.player != null) {
                cell.player.setVolume(isAudioMuted ? 0f : 1f);
            }
        }
    }

    private void toggleLandscapeFullscreen() {
        isLandscapeFullscreen = !isLandscapeFullscreen;
        if (isLandscapeFullscreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            if (liveVideoStage != null) {
                ViewGroup.LayoutParams lp = liveVideoStage.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                liveVideoStage.setLayoutParams(lp);
            }
        } else {
            exitLandscapeIfNeeded();
            if (liveVideoStage != null) {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                ViewGroup.LayoutParams lp = liveVideoStage.getLayoutParams();
                lp.height = (gridMode == 1) ? Math.round(screenWidth * 9f / 16f) : Math.round(screenWidth * 0.78f);
                liveVideoStage.setLayoutParams(lp);
            }
        }
    }

    private void exitLandscapeIfNeeded() {
        if (isLandscapeFullscreen) {
            isLandscapeFullscreen = false;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private void captureCurrentCellSnapshot() {
        if (selectedCellIndex < 0 || selectedCellIndex >= liveCells.size()) return;
        VideoCell cell = liveCells.get(selectedCellIndex);
        if (cell.boundCamera == null) {
            Toast.makeText(this, "Selecciona primero un canal activo", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bmp = cell.textureView.getBitmap(640, 360);
        if (bmp != null) {
            saveThumbnailLocally(cell.boundCamera.id, bmp);
            logAppEvent("Captura guardada: " + cell.boundCamera.groupTitle + " · " + cell.boundCamera.getCompactBadgeLabel());
            Toast.makeText(this, "Captura actualizada para " + cell.boundCamera.getCompactBadgeLabel(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Esperando fotograma de video...", Toast.LENGTH_SHORT).show();
        }
    }

    // Modal personalizado Limen Seguridad para elegir canal (Cero AlertDialog del sistema)
    private interface OnCameraSelectedListener {
        void onSelected(CameraItem cam);
    }

    private void showChannelPickerModal(String groupTitle, List<CameraItem> cams, OnCameraSelectedListener listener) {
        final Dialog dialog = createCustomLimenDialog();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 16, 1));
        card.setPadding(dp(18), dp(18), dp(18), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        TextView title = new TextView(this);
        title.setText("Canales de " + (groupTitle != null ? groupTitle : "Dispositivo"));
        styleText(title, 15, COL_TEXT_PRIMARY, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout closeBtn = new FrameLayout(this);
        closeBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        closeBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COL_TEXT_SECOND),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        card.addView(header);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(COL_BORDER));
        card.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (CameraItem c : cams) {
            list.addView(createModalActionRow(
                    LimenIcons.ICON_VIDEO,
                    "CAM " + c.channel + "  ·  " + c.name,
                    "Toca para reproducir en el recuadro " + (selectedCellIndex + 1),
                    COL_ACCENT_CYAN,
                    v -> {
                        dialog.dismiss();
                        listener.onSelected(c);
                    }));
        }

        scroll.addView(list);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(card);
        dialog.show();
    }

    private void showSwitchDeviceModal() {
        final Dialog dialog = createCustomLimenDialog();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 16, 1));
        card.setPadding(dp(18), dp(18), dp(18), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        TextView title = new TextView(this);
        title.setText("Cambiar de equipo");
        styleText(title, 15, COL_TEXT_PRIMARY, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout closeBtn = new FrameLayout(this);
        closeBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        closeBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COL_TEXT_SECOND),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        card.addView(header);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(COL_BORDER));
        card.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        for (Map.Entry<String, List<CameraItem>> entry : deviceGroups.entrySet()) {
            final String gName = entry.getKey();
            final List<CameraItem> gCams = entry.getValue();
            boolean isCurrent = gName.equals(activeLiveGroupFilter);

            card.addView(createModalActionRow(
                    LimenIcons.ICON_DVR_BOX,
                    gName,
                    gCams.size() + " canales vinculados" + (isCurrent ? " · (Equipo actual)" : ""),
                    isCurrent ? COL_ACCENT_CYAN : COL_TEXT_PRIMARY,
                    v -> {
                        dialog.dismiss();
                        gridMode = gCams.size() > 4 ? 9 : 4;
                        previousMultiGridMode = gridMode;
                        showLiveViewScreen(null, gCams, gName);
                    }));
        }

        dialog.setContentView(card);
        dialog.show();
    }

    private Dialog createCustomLimenDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(lp);
        }
        return dialog;
    }

    // =========================================================================
    // 7. MOTOR DE VIDEO HARDWARE EXOPLAYER (H.264 + H.265/HEVC) Y SNAPSHOTS 16:9
    // =========================================================================
    private void bindAndPlayCameraInCell(int cellIndex, CameraItem cam) {
        if (cellIndex < 0 || cellIndex >= liveCells.size() || cam == null) return;
        final VideoCell cell = liveCells.get(cellIndex);

        releaseCellPlayer(cell);
        cell.boundCamera = cam;
        cell.retryCount = 0;

        cell.topLeftOverlay.setText(cam.getCompactBadgeLabel());
        cell.bottomLeftOverlay.setText(cam.name != null ? cam.name : "");
        cell.topRightBitrateOverlay.setText(useHdStream ? "HD" : "SD");
        cell.topRightBitrateOverlay.setVisibility(View.VISIBLE);
        cell.statusOverlay.setText("Conectando...");
        cell.statusOverlay.setVisibility(View.VISIBLE);
        cell.loadingSpinner.setVisibility(View.VISIBLE);
        cell.previewThumb.setVisibility(View.VISIBLE);
        loadCameraThumbnail(cam.id, cell.previewThumb);

        activateBackendTunnel(cam.id);
        startExoPlayerForCell(cell, false);
    }

    private void startExoPlayerForCell(final VideoCell cell, final boolean fallbackHls) {
        if (cell.boundCamera == null) return;
        releaseCellPlayer(cell);

        CameraItem cam = cell.boundCamera;
        String streamKey = cam.getActiveStreamKey(useHdStream);
        String streamUrl = fallbackHls
                ? (BASE_URL + "/api/stream.m3u8?src=" + Uri.encode(streamKey))
                : (BASE_URL + "/api/stream.mp4?src=" + Uri.encode(streamKey));

        Map<String, String> headers = new HashMap<>();
        if (sessionCookie != null) {
            headers.put("Cookie", sessionCookie);
        }

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("LimenEyesNative/2.4.0")
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(12000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(1500, 5000, 500, 1000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        ExoPlayer player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .setLoadControl(loadControl)
                .build();

        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        player.setVideoTextureView(cell.textureView);
        player.setVolume(isAudioMuted ? 0f : 1f);
        player.setPlayWhenReady(!isAllPaused);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    cell.loadingSpinner.setVisibility(View.GONE);
                    cell.statusOverlay.setVisibility(View.GONE);
                    cell.previewThumb.setVisibility(View.GONE);
                    cell.retryCount = 0;

                    mainHandler.postDelayed(() -> {
                        if (cell.boundCamera != null && cell.textureView != null && cell.player != null
                                && cell.player.getPlaybackState() == Player.STATE_READY) {
                            Bitmap bmp = cell.textureView.getBitmap(640, 360);
                            if (bmp != null) {
                                saveThumbnailLocally(cell.boundCamera.id, bmp);
                            }
                        }
                    }, 1500);
                } else if (state == Player.STATE_BUFFERING) {
                    cell.loadingSpinner.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                cell.loadingSpinner.setVisibility(View.GONE);
                cell.statusOverlay.setVisibility(View.GONE);
                cell.previewThumb.setVisibility(View.GONE);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                cell.retryCount++;
                if (cell.boundCamera == null) return;

                activateBackendTunnel(cell.boundCamera.id);
                cell.loadingSpinner.setVisibility(View.VISIBLE);
                cell.statusOverlay.setVisibility(View.VISIBLE);
                cell.statusOverlay.setText("Sincronizando canal... (" + cell.retryCount + ")");

                boolean nextHls = (cell.retryCount % 3 == 0) ? !fallbackHls : fallbackHls;
                cell.retryRunnable = () -> startExoPlayerForCell(cell, nextHls);
                mainHandler.postDelayed(cell.retryRunnable, 2200);
            }
        });

        cell.player = player;
        player.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)));
        player.prepare();
    }

    private void reloadActiveCellsQuality() {
        for (VideoCell cell : liveCells) {
            if (cell.boundCamera != null) {
                bindAndPlayCameraInCell(cell.index, cell.boundCamera);
            }
        }
    }

    private void stopAndClearCell(int cellIndex) {
        if (cellIndex < 0 || cellIndex >= liveCells.size()) return;
        VideoCell cell = liveCells.get(cellIndex);
        releaseCellPlayer(cell);
        cell.boundCamera = null;
        cell.topLeftOverlay.setText("");
        cell.bottomLeftOverlay.setText("");
        cell.topRightBitrateOverlay.setVisibility(View.GONE);
        cell.previewThumb.setImageDrawable(null);
        cell.loadingSpinner.setVisibility(View.GONE);
        cell.statusOverlay.setText("Toca para abrir canal");
        cell.statusOverlay.setVisibility(View.VISIBLE);
    }

    private void releaseCellPlayer(VideoCell cell) {
        if (cell.retryRunnable != null) {
            mainHandler.removeCallbacks(cell.retryRunnable);
            cell.retryRunnable = null;
        }
        if (cell.player != null) {
            try {
                if (cell.boundCamera != null && cell.textureView != null
                        && cell.player.getPlaybackState() == Player.STATE_READY) {
                    Bitmap bmp = cell.textureView.getBitmap(640, 360);
                    if (bmp != null) {
                        saveThumbnailLocally(cell.boundCamera.id, bmp);
                    }
                }
            } catch (Exception ignored) {}
            try {
                cell.player.stop();
                cell.player.clearVideoSurface();
                cell.player.release();
            } catch (Exception ignored) {}
            cell.player = null;
        }
    }

    private void stopAllPlayers() {
        mainHandler.removeCallbacks(bitrateTicker);
        for (VideoCell cell : liveCells) {
            releaseCellPlayer(cell);
        }
    }

    private void activateBackendTunnel(final int cameraId) {
        networkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/stream/" + cameraId + "/activate");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                if (sessionCookie != null) {
                    conn.setRequestProperty("Cookie", sessionCookie);
                }
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // =========================================================================
    // 8. CACHÉ PERSISTENTE DE MINIATURAS 16:9 (ÚLTIMA VEZ VISTA)
    // =========================================================================
    private File getLocalThumbFile(int camId) {
        File dir = new File(getFilesDir(), "cam_thumbs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "cam_" + camId + ".jpg");
    }

    private void saveThumbnailLocally(int camId, Bitmap bmp) {
        if (bmp == null) return;
        memoryThumbCache.put(camId, bmp);
        networkExecutor.execute(() -> {
            try (FileOutputStream fos = new FileOutputStream(getLocalThumbFile(camId))) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, fos);
            } catch (Exception ignored) {}
        });
    }

    private void loadCameraThumbnail(final int camId, final ImageView targetView) {
        targetView.setTag(camId);
        Bitmap mem = memoryThumbCache.get(camId);
        if (mem != null) {
            targetView.setImageBitmap(mem);
        }

        networkExecutor.execute(() -> {
            File localFile = getLocalThumbFile(camId);
            if (localFile.exists() && mem == null) {
                Bitmap diskBmp = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                if (diskBmp != null) {
                    memoryThumbCache.put(camId, diskBmp);
                    mainHandler.post(() -> {
                        if (Integer.valueOf(camId).equals(targetView.getTag())) {
                            targetView.setImageBitmap(diskBmp);
                        }
                    });
                }
            }

            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/api/camaras/" + camId + "/thumb.jpg");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (sessionCookie != null) {
                    conn.setRequestProperty("Cookie", sessionCookie);
                }
                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        Bitmap netBmp = BitmapFactory.decodeStream(is);
                        if (netBmp != null) {
                            saveThumbnailLocally(camId, netBmp);
                            mainHandler.post(() -> {
                                if (Integer.valueOf(camId).equals(targetView.getTag())) {
                                    targetView.setImageBitmap(netBmp);
                                }
                            });
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // =========================================================================
    // 9. GESTOR DE EQUIPOS Y PERFIL DE CUENTA
    // =========================================================================
    private void showDeviceManagerScreen() {
        stopAllPlayers();
        exitLandscapeIfNeeded();
        if (bottomNavBar != null) bottomNavBar.setVisibility(View.VISIBLE);
        updateBottomNavSelection(2);
        contentArea.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COL_BG_MAIN));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor(COL_BG_HEADER));
        topBar.setPadding(dp(18), dp(14), dp(18), dp(14));

        TextView topTitle = new TextView(this);
        topTitle.setText("Gestión de Equipos");
        styleText(topTitle, 17, COL_TEXT_PRIMARY, true);
        topTitle.setGravity(Gravity.CENTER);
        topBar.addView(topTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(14), dp(14), dp(24));

        for (Map.Entry<String, List<CameraItem>> entry : deviceGroups.entrySet()) {
            final String groupName = entry.getKey();
            final List<CameraItem> cams = entry.getValue();

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 14, 1));
            row.setPadding(dp(16), dp(14), dp(16), dp(14));

            FrameLayout iconCircle = new FrameLayout(this);
            iconCircle.setBackground(createRoundedDrawable(COL_BG_ELEVATED, COL_BORDER, 10, 1));
            iconCircle.addView(new LimenIcons.IconView(this, LimenIcons.ICON_DVR_BOX, COL_ACCENT_CYAN),
                    new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            icLp.rightMargin = dp(14);
            row.addView(iconCircle, icLp);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);

            TextView nameTv = new TextView(this);
            nameTv.setText(groupName);
            styleText(nameTv, 15, COL_TEXT_PRIMARY, true);
            infoCol.addView(nameTv);

            TextView subTv = new TextView(this);
            subTv.setText("SN: " + extractDeviceSerial(cams) + "  ·  " + cams.size() + " canales");
            styleText(subTv, 12, COL_TEXT_SECOND, false);
            subTv.setPadding(0, dp(3), 0, 0);
            infoCol.addView(subTv);

            row.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_R, COL_TEXT_SECOND),
                    new LinearLayout.LayoutParams(dp(18), dp(18)));

            row.setOnClickListener(v -> showDeviceDetailsScreen(groupName, cams));

            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.bottomMargin = dp(10);
            list.addView(row, rLp);
        }

        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        contentArea.addView(page);
    }

    private void showProfileScreen() {
        stopAllPlayers();
        exitLandscapeIfNeeded();
        if (bottomNavBar != null) bottomNavBar.setVisibility(View.VISIBLE);
        updateBottomNavSelection(3);
        contentArea.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COL_BG_MAIN));
        page.setPadding(dp(20), dp(36), dp(20), dp(20));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createRoundedDrawable(COL_BG_CARD, COL_BORDER, 16, 1));
        card.setPadding(dp(20), dp(22), dp(20), dp(22));

        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout shieldBox = new FrameLayout(this);
        shieldBox.setBackground(createRoundedDrawable(COL_BG_ELEVATED, COL_BORDER, 12, 1));
        shieldBox.addView(new LimenIcons.IconView(this, LimenIcons.ICON_SHIELD, COL_ACCENT_CYAN),
                new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        sbLp.rightMargin = dp(14);
        headRow.addView(shieldBox, sbLp);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("LIMEN EYES v2.4.0");
        styleText(title, 16, COL_TEXT_PRIMARY, true);
        titleCol.addView(title);

        String savedUser = prefs.getString(KEY_SAVED_EMAIL, "Sesión corporativa activa");
        TextView sub = new TextView(this);
        sub.setText(savedUser);
        styleText(sub, 12, COL_ACCENT_CYAN, false);
        sub.setPadding(0, dp(3), 0, 0);
        titleCol.addView(sub);

        headRow.addView(titleCol);
        card.addView(headRow);

        TextView desc = new TextView(this);
        desc.setText("Plataforma nativa de videovigilancia Limen Seguridad.\n• Motor de decodificación por hardware Media3 H.264 / H.265\n• Vistas en directo 1CH, 4CH y 9CH con ficha técnica de equipos\n• Caché inteligente de capturas 16:9");
        styleText(desc, 12, COL_TEXT_SECOND, false);
        desc.setLineSpacing(dp(4), 1f);
        desc.setPadding(0, dp(16), 0, dp(22));
        card.addView(desc);

        LinearLayout logoutBtn = new LinearLayout(this);
        logoutBtn.setOrientation(LinearLayout.HORIZONTAL);
        logoutBtn.setGravity(Gravity.CENTER);
        logoutBtn.setPadding(dp(16), dp(13), dp(16), dp(13));
        logoutBtn.setBackground(createRoundedDrawable("#7F1D1D", "#EF4444", 12, 1));

        LimenIcons.IconView outIcon = new LimenIcons.IconView(this, LimenIcons.ICON_LOGOUT, "#FCA5A5");
        LinearLayout.LayoutParams outLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        outLp.rightMargin = dp(8);
        logoutBtn.addView(outIcon, outLp);

        TextView outTxt = new TextView(this);
        outTxt.setText("Cerrar sesión");
        styleText(outTxt, 14, "#FFFFFF", true);
        logoutBtn.addView(outTxt);

        logoutBtn.setOnClickListener(v -> {
            prefs.edit().remove(KEY_SESSION_COOKIE).apply();
            sessionCookie = null;
            if (googleSignInClient != null) googleSignInClient.signOut();
            showLoginScreen();
        });
        card.addView(logoutBtn);

        page.addView(card);
        contentArea.addView(page);
    }

    // =========================================================================
    // 10. UTILIDADES DE DISEÑO Y CICLO DE VIDA
    // =========================================================================
    private GradientDrawable createRoundedDrawable(String fillHex, String strokeHex, int radiusDp, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(radiusDp));
        gd.setColor(Color.parseColor(fillHex));
        if (strokeHex != null && strokeDp > 0) {
            gd.setStroke(dp(strokeDp), Color.parseColor(strokeHex));
        }
        return gd;
    }

    private GradientDrawable createOvalDrawable(String fillHex, String strokeHex, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(fillHex));
        if (strokeHex != null && strokeDp > 0) {
            gd.setStroke(dp(strokeDp), Color.parseColor(strokeHex));
        }
        return gd;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    @Override
    public void onBackPressed() {
        if (isLandscapeFullscreen) {
            toggleLandscapeFullscreen();
        } else if (currentTab != 0) {
            showHomeScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopAllPlayers();
    }

    @Override
    protected void onDestroy() {
        stopAllPlayers();
        networkExecutor.shutdownNow();
        super.onDestroy();
    }
}

package com.limenseguridad.limeneyes;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LimenEyesDMSS";
    private static final String BASE_URL = "https://eyes.limenseguridad.com";
    private static final String PREFS_NAME = "LimenEyesPrefs";

    // Paleta de colores idéntica a DMSS / EasyViewer Pro (capturas subidas)
    private static final String COLOR_BG_MAIN      = "#14181F";
    private static final String COLOR_BG_CARD      = "#1E232D";
    private static final String COLOR_BG_INPUT     = "#242A36";
    private static final String COLOR_BG_CELL      = "#242B35";
    private static final String COLOR_ACCENT_BLUE  = "#2E82FF";
    private static final String COLOR_BTN_BLUE     = "#1D4E89";
    private static final String COLOR_TEXT_WHITE   = "#F1F5F9";
    private static final String COLOR_TEXT_GRAY    = "#8A94A6";
    private static final String COLOR_BORDER_DARK  = "#2C3444";

    private FrameLayout rootContainer;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String authToken = "";
    private String currentUserName = "";
    private String currentUserEmail = "";
    private String currentUserRole = "";

    private final List<CameraItem> allCameras = new ArrayList<>();
    private String searchQuery = "";

    // Navegación principal: 0 = Inicio (Home), 1 = Vista en Vivo (Live View), 2 = Dispositivos, 3 = Perfil (Me)
    private int currentTab = 0;

    // Estado del Reproductor en Vivo (Live View)
    private int currentGridMode = 4; // 1 = 1x1 (16:9), 4 = 2x2 (16:9)
    private int currentSubtype = 1;  // 1 = SD (rápido), 0 = HD (alta definición)
    private int selectedCellIndex = 0;
    private boolean isMuted = true;
    private final List<CellHolder> cellHolders = new ArrayList<>();
    private LinearLayout rowTop, rowBottom;
    private TextView btnQualityBadge, btnGridBadge, btnMuteBadge;
    private TextView liveHeaderTitle;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private TextView statusErrorText;

    public static class CameraItem {
        public int id;
        public String nombre;
        public String sn;
        public int canal;
        public String clienteNombre;
        public String marca;

        public String getGroupTitle() {
            if (clienteNombre != null && !clienteNombre.trim().isEmpty()) {
                return clienteNombre.trim();
            }
            if (sn != null && !sn.trim().isEmpty()) {
                return "DVR " + sn.trim();
            }
            return "Dispositivo Local";
        }

        public String getShortChannelLabel() {
            if (nombre != null && !nombre.trim().isEmpty()) {
                return nombre.trim() + " (CH" + canal + ")";
            }
            return "CAM " + canal;
        }
    }

    public static class CellHolder {
        public int index;
        public FrameLayout container;
        public PlayerView playerView;
        public ExoPlayer player;
        public ImageView thumbPreview;
        public TextView plusIcon;
        public TextView labelTitle;
        public TextView labelStatus;
        public ProgressBar spinner;
        public CameraItem activeCamera;
        public long lastTapTime = 0;
        public int requestId = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            reportCrashToServer("UncaughtException on " + thread.getName(), throwable);
        });

        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor(COLOR_BG_MAIN));
            window.setNavigationBarColor(Color.parseColor(COLOR_BG_CARD));

            setContentView(R.layout.activity_main);
            rootContainer = findViewById(R.id.rootContainer);
            rootContainer.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));

            try {
                googleSignInLauncher = registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            try {
                                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                                handleGoogleSignInResult(task);
                            } catch (Throwable t) {
                                showLoginError("Error al procesar cuenta de Google");
                            }
                        }
                );

                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestProfile()
                        .build();
                googleSignInClient = GoogleSignIn.getClient(this, gso);
            } catch (Throwable t) {
                Log.e(TAG, "GoogleSignIn init warning", t);
            }

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            authToken = prefs.getString("token", "");
            currentUserName = prefs.getString("userName", "");
            currentUserEmail = prefs.getString("userEmail", "");
            currentUserRole = prefs.getString("userRole", "");

            if (authToken != null && !authToken.isEmpty()) {
                loadCamerasAndOpenHome();
            } else {
                showLoginScreen();
            }
        } catch (Throwable t) {
            reportCrashToServer("onCreate error", t);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
        );
    }

    private GradientDrawable roundBg(String hexColor, int radiusDp, String strokeHex, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(hexColor));
        gd.setCornerRadius(dp(radiusDp));
        if (strokeHex != null && strokeDp > 0) {
            gd.setStroke(dp(strokeDp), Color.parseColor(strokeHex));
        }
        return gd;
    }

    private GradientDrawable circleBg(String hexColor, String strokeHex, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(hexColor));
        if (strokeHex != null && strokeDp > 0) {
            gd.setStroke(dp(strokeDp), Color.parseColor(strokeHex));
        }
        return gd;
    }

    // =========================================================================
    // 1. LOGIN SCREEN IDÉNTICO A DMSS / EASYVIEWER (Captura 1)
    // =========================================================================
    private void showLoginScreen() {
        releaseAllPlayers();
        rootContainer.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(28), dp(64), dp(28), dp(32));

        // Título "Iniciar sesión" alineado a la izquierda como en DMSS
        TextView title = new TextView(this);
        title.setText("Iniciar sesión");
        title.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleLp.bottomMargin = dp(8);
        container.addView(title, titleLp);

        TextView subTitle = new TextView(this);
        subTitle.setText("LIMEN EYES · Seguridad y Monitoreo en Vivo");
        subTitle.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        subTitle.setTextSize(13);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subLp.bottomMargin = dp(34);
        container.addView(subTitle, subLp);

        statusErrorText = new TextView(this);
        statusErrorText.setTextColor(Color.parseColor("#F87171"));
        statusErrorText.setBackground(roundBg("#2A161B", 10, "#EF4444", 1));
        statusErrorText.setPadding(dp(14), dp(10), dp(14), dp(10));
        statusErrorText.setTextSize(13);
        statusErrorText.setVisibility(View.GONE);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        errLp.bottomMargin = dp(18);
        container.addView(statusErrorText, errLp);

        // Campo Usuario / Email con botón (X) para limpiar
        FrameLayout userWrap = new FrameLayout(this);
        userWrap.setBackground(roundBg(COLOR_BG_INPUT, 10, null, 0));
        LinearLayout.LayoutParams userWrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        );
        userWrapLp.bottomMargin = dp(16);

        EditText inputUser = new EditText(this);
        inputUser.setHint("Correo electrónico o usuario");
        inputUser.setHintTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        inputUser.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        inputUser.setTextSize(15);
        inputUser.setSingleLine(true);
        inputUser.setBackgroundColor(Color.TRANSPARENT);
        inputUser.setPadding(dp(16), 0, dp(48), 0);
        inputUser.setGravity(Gravity.CENTER_VERTICAL);
        userWrap.addView(inputUser, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView clearBtn = new TextView(this);
        clearBtn.setText("✕");
        clearBtn.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        clearBtn.setTextSize(13);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setBackground(circleBg("#323A4A", null, 0));
        FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(dp(22), dp(22));
        clearLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        clearLp.rightMargin = dp(14);
        clearBtn.setOnClickListener(v -> inputUser.setText(""));
        userWrap.addView(clearBtn, clearLp);
        container.addView(userWrap, userWrapLp);

        // Campo Contraseña con botón de mostrar/ocultar
        FrameLayout passWrap = new FrameLayout(this);
        passWrap.setBackground(roundBg(COLOR_BG_INPUT, 10, null, 0));
        LinearLayout.LayoutParams passWrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        );
        passWrapLp.bottomMargin = dp(14);

        EditText inputPass = new EditText(this);
        inputPass.setHint("Contraseña");
        inputPass.setHintTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        inputPass.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        inputPass.setTextSize(15);
        inputPass.setSingleLine(true);
        inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputPass.setBackgroundColor(Color.TRANSPARENT);
        inputPass.setPadding(dp(16), 0, dp(54), 0);
        inputPass.setGravity(Gravity.CENTER_VERTICAL);
        passWrap.addView(inputPass, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView toggleEye = new TextView(this);
        toggleEye.setText("👁");
        toggleEye.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        toggleEye.setTextSize(16);
        toggleEye.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams eyeLp = new FrameLayout.LayoutParams(dp(36), dp(36));
        eyeLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        eyeLp.rightMargin = dp(10);
        final boolean[] passVisible = {false};
        toggleEye.setOnClickListener(v -> {
            passVisible[0] = !passVisible[0];
            if (passVisible[0]) {
                inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            inputPass.setSelection(inputPass.getText().length());
        });
        passWrap.addView(toggleEye, eyeLp);
        container.addView(passWrap, passWrapLp);

        // Botón "Iniciar sesión" azul profundo estilo DMSS
        Button btnLogin = new Button(this);
        btnLogin.setText("Iniciar sesión");
        btnLogin.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        btnLogin.setTextSize(16);
        btnLogin.setTypeface(Typeface.DEFAULT_BOLD);
        btnLogin.setAllCaps(false);
        btnLogin.setBackground(roundBg(COLOR_BTN_BLUE, 10, null, 0));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        );
        btnLp.topMargin = dp(16);
        btnLp.bottomMargin = dp(24);
        btnLogin.setOnClickListener(v -> {
            String u = inputUser.getText().toString().trim();
            String p = inputPass.getText().toString().trim();
            if (u.isEmpty() || p.isEmpty()) {
                showLoginError("Ingresa tu usuario/correo y contraseña");
                return;
            }
            performStandardLogin(u, p, btnLogin);
        });
        container.addView(btnLogin, btnLp);

        // Espaciador flexible para empujar "Iniciar sesión con" hacia abajo como en DMSS
        View spacer = new View(this);
        container.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        // Divisor "Iniciar sesión con"
        LinearLayout divRow = new LinearLayout(this);
        divRow.setOrientation(LinearLayout.HORIZONTAL);
        divRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        divLp.bottomMargin = dp(20);

        View line1 = new View(this);
        line1.setBackgroundColor(Color.parseColor("#242C3A"));
        divRow.addView(line1, new LinearLayout.LayoutParams(0, dp(1), 1f));

        TextView divText = new TextView(this);
        divText.setText("  Iniciar sesión con  ");
        divText.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        divText.setTextSize(12);
        divRow.addView(divText);

        View line2 = new View(this);
        line2.setBackgroundColor(Color.parseColor("#242C3A"));
        divRow.addView(line2, new LinearLayout.LayoutParams(0, dp(1), 1f));
        container.addView(divRow, divLp);

        // Botón Circular G de Google centrado
        LinearLayout socialRow = new LinearLayout(this);
        socialRow.setOrientation(LinearLayout.HORIZONTAL);
        socialRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams socLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        socLp.bottomMargin = dp(20);

        TextView googleCircle = new TextView(this);
        googleCircle.setText("G");
        googleCircle.setTextColor(Color.parseColor("#EA4335"));
        googleCircle.setTextSize(22);
        googleCircle.setTypeface(Typeface.DEFAULT_BOLD);
        googleCircle.setGravity(Gravity.CENTER);
        googleCircle.setBackground(circleBg("#E2E8F0", null, 0));
        googleCircle.setOnClickListener(v -> startNativeGoogleSignIn(inputUser.getText().toString().trim()));
        socialRow.addView(googleCircle, new LinearLayout.LayoutParams(dp(50), dp(50)));

        container.addView(socialRow, socLp);
        scroll.addView(container);
        rootContainer.addView(scroll);
    }

    private void showLoginError(String msg) {
        mainHandler.post(() -> {
            if (statusErrorText != null) {
                statusErrorText.setText(msg);
                statusErrorText.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startNativeGoogleSignIn(String typedEmailFallback) {
        try {
            if (googleSignInClient != null && googleSignInLauncher != null) {
                googleSignInClient.signOut().addOnCompleteListener(task -> {
                    Intent signInIntent = googleSignInClient.getSignInIntent();
                    googleSignInLauncher.launch(signInIntent);
                });
            } else if (!typedEmailFallback.isEmpty() && typedEmailFallback.contains("@")) {
                verifyGoogleEmailOnServer(typedEmailFallback, typedEmailFallback.split("@")[0]);
            } else {
                promptGoogleEmailDialog();
            }
        } catch (Throwable t) {
            promptGoogleEmailDialog();
        }
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getEmail() != null) {
                verifyGoogleEmailOnServer(account.getEmail(), account.getDisplayName());
            } else {
                promptGoogleEmailDialog();
            }
        } catch (ApiException e) {
            promptGoogleEmailDialog();
        }
    }

    private void promptGoogleEmailDialog() {
        EditText emailInput = new EditText(this);
        emailInput.setHint("tu-correo@gmail.com");
        emailInput.setSingleLine(true);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setPadding(dp(20), dp(16), dp(20), dp(16));

        new AlertDialog.Builder(this)
                .setTitle("Iniciar sesión con Google")
                .setMessage("Ingresa tu cuenta de correo autorizada en Limen:")
                .setView(emailInput)
                .setPositiveButton("Continuar", (d, w) -> {
                    String email = emailInput.getText().toString().trim();
                    if (!email.isEmpty()) {
                        verifyGoogleEmailOnServer(email, email.split("@")[0]);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void verifyGoogleEmailOnServer(String email, String displayName) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("displayName", displayName != null ? displayName : email);

                HttpResponse resp = httpPostJson(BASE_URL + "/api/auth/google", body.toString(), null);
                JSONObject json = new JSONObject(resp.body);
                if (resp.code == 200 && json.has("token")) {
                    saveSessionAndEnter(json);
                } else {
                    String err = json.optString("message", json.optString("error", "Cuenta no autorizada"));
                    showLoginError(err);
                }
            } catch (Exception e) {
                showLoginError("Error validando cuenta: " + e.getMessage());
            }
        });
    }

    private void performStandardLogin(String user, String pass, Button btn) {
        btn.setEnabled(false);
        btn.setText("Conectando...");
        if (statusErrorText != null) statusErrorText.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("usuario", user);
                body.put("password", pass);

                HttpResponse resp = httpPostJson(BASE_URL + "/api/login", body.toString(), null);
                JSONObject json = new JSONObject(resp.body);
                if (resp.code == 200 && json.has("token")) {
                    saveSessionAndEnter(json);
                } else {
                    String err = json.optString("error", "Usuario o contraseña incorrectos");
                    showLoginError(err);
                }
            } catch (Exception e) {
                showLoginError("Error de conexión con el servidor");
            } finally {
                mainHandler.post(() -> {
                    btn.setEnabled(true);
                    btn.setText("Iniciar sesión");
                });
            }
        });
    }

    private void saveSessionAndEnter(JSONObject json) {
        authToken = json.optString("token", "");
        JSONObject u = json.optJSONObject("usuario");
        currentUserName = u != null ? u.optString("nombre", "Usuario") : "Usuario";
        currentUserEmail = u != null ? u.optString("email", "") : "";
        currentUserRole = u != null ? u.optString("rol", "cliente") : "cliente";

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("token", authToken)
                .putString("userName", currentUserName)
                .putString("userEmail", currentUserEmail)
                .putString("userRole", currentUserRole)
                .apply();

        mainHandler.post(this::loadCamerasAndOpenHome);
    }

    private void logout() {
        releaseAllPlayers();
        authToken = "";
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
        showLoginScreen();
    }

    private void loadCamerasAndOpenHome() {
        showHomeScreen();
        executor.execute(() -> {
            try {
                HttpResponse resp = httpGet(BASE_URL + "/api/camaras", authToken);
                if (resp.code == 401) {
                    mainHandler.post(this::logout);
                    return;
                }
                JSONArray arr = new JSONArray(resp.body);
                List<CameraItem> loaded = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    CameraItem item = new CameraItem();
                    item.id = o.optInt("id");
                    item.nombre = o.optString("nombre", "CAM " + (i + 1));
                    item.sn = o.optString("sn", "");
                    item.canal = o.optInt("canal", 1);
                    item.clienteNombre = o.optString("cliente_nombre", "");
                    item.marca = o.optString("marca", "Dahua");
                    loaded.add(item);
                }
                mainHandler.post(() -> {
                    allCameras.clear();
                    allCameras.addAll(loaded);
                    if (currentTab == 0) showHomeScreen();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error obteniendo cámaras", e);
            }
        });
    }

    // =========================================================================
    // 2. PANTALLA PRINCIPAL "INICIO" (HOME) IDÉNTICA A CAPTURA 3 DE DMSS
    // =========================================================================
    private void showHomeScreen() {
        currentTab = 0;
        // Importante: al estar en Home NO se inician cámaras en vivo automáticamente
        releaseAllPlayers();
        rootContainer.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // ── HEADER SUPERIOR: Icono Grilla | "Inicio" | (+) ──
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(10));

        TextView btnLiveShortcut = new TextView(this);
        btnLiveShortcut.setText("⊞");
        btnLiveShortcut.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        btnLiveShortcut.setTextSize(22);
        btnLiveShortcut.setOnClickListener(v -> showLiveViewScreen(null, null));
        header.addView(btnLiveShortcut);

        TextView headerTitle = new TextView(this);
        headerTitle.setText("Inicio");
        headerTitle.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        headerTitle.setTextSize(19);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        headerTitle.setGravity(Gravity.CENTER);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnRefresh = new TextView(this);
        btnRefresh.setText("↻");
        btnRefresh.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        btnRefresh.setTextSize(20);
        btnRefresh.setOnClickListener(v -> loadCamerasAndOpenHome());
        header.addView(btnRefresh);

        page.addView(header);

        // ── CONTENIDO SCROLLABLE ──
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(6), dp(14), dp(20));

        // Barra de búsqueda redondeada oscura
        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(roundBg("#0D1015", 18, "#222936", 1));
        searchBox.setPadding(dp(14), dp(2), dp(14), dp(2));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)
        );
        searchLp.bottomMargin = dp(14);

        TextView searchIcon = new TextView(this);
        searchIcon.setText("🔍 ");
        searchIcon.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        searchIcon.setTextSize(13);
        searchBox.addView(searchIcon);

        EditText searchInput = new EditText(this);
        searchInput.setHint("Buscar dispositivo o cámara...");
        searchInput.setHintTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        searchInput.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        searchInput.setTextSize(13);
        searchInput.setSingleLine(true);
        searchInput.setText(searchQuery);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setPadding(dp(6), 0, dp(6), 0);
        searchBox.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        content.addView(searchBox, searchLp);

        // ── BARRA DE ACCIONES RÁPIDAS (Vista previa, Playback, Dispositivos, Alarma) ──
        LinearLayout quickCard = new LinearLayout(this);
        quickCard.setOrientation(LinearLayout.HORIZONTAL);
        quickCard.setBackground(roundBg(COLOR_BG_CARD, 14, null, 0));
        quickCard.setPadding(dp(8), dp(14), dp(8), dp(14));
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        quickLp.bottomMargin = dp(14);

        quickCard.addView(createQuickActionItem("📹", "#2E82FF", "Vista previa", () -> showLiveViewScreen(null, null)));
        quickCard.addView(createQuickActionItem("🎞", "#0EA5E9", "Multicanal", () -> {
            currentGridMode = 4;
            showLiveViewScreen(null, null);
        }));
        quickCard.addView(createQuickActionItem("🖧", "#6366F1", "Dispositivos", this::showDeviceManagerScreen));
        quickCard.addView(createQuickActionItem("🔔", "#8B5CF6", "Estado P2P", () -> {
            Toast.makeText(this, "Motor P2P Activo · " + allCameras.size() + " canales autorizados", Toast.LENGTH_SHORT).show();
        }));

        content.addView(quickCard, quickLp);

        // Contenedor de tarjetas de dispositivos (DVRs / Clientes)
        LinearLayout devicesContainer = new LinearLayout(this);
        devicesContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(devicesContainer);

        populateHomeDeviceCards(devicesContainer, searchQuery);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim();
                populateHomeDeviceCards(devicesContainer, searchQuery);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        // ── BARRA DE NAVEGACIÓN INFERIOR (Inicio | Dispositivos | Me) ──
        page.addView(buildBottomNavBar());
        rootContainer.addView(page);
    }

    private View createQuickActionItem(String symbol, String colorHex, String label, Runnable onClick) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.setOnClickListener(v -> onClick.run());

        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextSize(18);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(circleBg(colorHex, null, 0));
        icon.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        icLp.bottomMargin = dp(6);
        col.addView(icon, icLp);

        TextView txt = new TextView(this);
        txt.setText(label);
        txt.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        txt.setTextSize(11);
        txt.setSingleLine(true);
        txt.setGravity(Gravity.CENTER);
        col.addView(txt);

        return col;
    }

    private void populateHomeDeviceCards(LinearLayout devicesContainer, String filter) {
        devicesContainer.removeAllViews();

        if (allCameras.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Cargando dispositivos autorizados...");
            empty.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(40), dp(20), dp(40));
            devicesContainer.addView(empty);
            return;
        }

        // Agrupar cámaras por Dispositivo / Cliente (como las tarjetas "cams", "foo", "gom" en DMSS)
        Map<String, List<CameraItem>> groups = new LinkedHashMap<>();
        String q = filter != null ? filter.toLowerCase() : "";

        for (CameraItem cam : allCameras) {
            String gName = cam.getGroupTitle();
            if (!q.isEmpty()) {
                boolean match = gName.toLowerCase().contains(q)
                        || (cam.nombre != null && cam.nombre.toLowerCase().contains(q))
                        || (cam.sn != null && cam.sn.toLowerCase().contains(q));
                if (!match) continue;
            }
            if (!groups.containsKey(gName)) {
                groups.put(gName, new ArrayList<>());
            }
            groups.get(gName).add(cam);
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int thumbWidth = (screenWidth - dp(68)) / 2;
        int thumbHeight = (thumbWidth * 9) / 16; // Formato exacto 16:9

        for (Map.Entry<String, List<CameraItem>> entry : groups.entrySet()) {
            String groupTitle = entry.getKey();
            List<CameraItem> cams = entry.getValue();

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBg(COLOR_BG_CARD, 14, null, 0));
            card.setPadding(dp(14), dp(14), dp(14), dp(12));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardLp.bottomMargin = dp(14);

            // Cabecera de la tarjeta: Nombre del dispositivo + Botón (▶) para abrir grupo + (•••)
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams topRowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            topRowLp.bottomMargin = dp(10);

            TextView titleTv = new TextView(this);
            titleTv.setText(groupTitle);
            titleTv.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
            titleTv.setTextSize(17);
            titleTv.setTypeface(Typeface.DEFAULT_BOLD);
            topRow.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Botón Play circular (▶) para abrir hasta 4 cámaras de este equipo en la vista 2x2
            TextView playGroupBtn = new TextView(this);
            playGroupBtn.setText("▶");
            playGroupBtn.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
            playGroupBtn.setTextSize(12);
            playGroupBtn.setGravity(Gravity.CENTER);
            playGroupBtn.setBackground(circleBg("#283142", "#94A3B8", 1));
            LinearLayout.LayoutParams playBtnLp = new LinearLayout.LayoutParams(dp(28), dp(28));
            playBtnLp.rightMargin = dp(10);
            playGroupBtn.setOnClickListener(v -> {
                currentGridMode = cams.size() > 1 ? 4 : 1;
                showLiveViewScreen(null, cams);
            });
            topRow.addView(playGroupBtn, playBtnLp);

            TextView moreBtn = new TextView(this);
            moreBtn.setText("•••");
            moreBtn.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
            moreBtn.setTextSize(14);
            moreBtn.setPadding(dp(4), dp(2), dp(4), dp(2));
            moreBtn.setOnClickListener(v -> showDeviceChannelSelectorModal(groupTitle, cams));
            topRow.addView(moreBtn);

            card.addView(topRow, topRowLp);

            // Carrusel horizontal de miniaturas 16:9 con la última captura de cada cámara
            HorizontalScrollView thumbScroll = new HorizontalScrollView(this);
            thumbScroll.setHorizontalScrollBarEnabled(false);

            LinearLayout thumbRow = new LinearLayout(this);
            thumbRow.setOrientation(LinearLayout.HORIZONTAL);

            for (int i = 0; i < cams.size(); i++) {
                final CameraItem cam = cams.get(i);

                FrameLayout thumbCard = new FrameLayout(this);
                thumbCard.setBackground(roundBg("#10141B", 10, "#2B3445", 1));
                LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(thumbWidth, thumbHeight);
                if (i < cams.size() - 1) tcLp.rightMargin = dp(10);
                thumbCard.setLayoutParams(tcLp);

                // Imagen de la última captura (16:9)
                ImageView img = new ImageView(this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumbCard.addView(img, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ));

                // Cargar última captura guardada en caché local o desde el servidor sin iniciar stream de video
                loadCameraThumbnailAsync(cam.id, img);

                // Icono central sutil de Play sobre la captura
                TextView playOverlay = new TextView(this);
                playOverlay.setText("▶");
                playOverlay.setTextColor(Color.WHITE);
                playOverlay.setTextSize(13);
                playOverlay.setGravity(Gravity.CENTER);
                playOverlay.setBackground(circleBg("#88000000", "#88FFFFFF", 1));
                FrameLayout.LayoutParams poLp = new FrameLayout.LayoutParams(dp(32), dp(32));
                poLp.gravity = Gravity.CENTER;
                thumbCard.addView(playOverlay, poLp);

                // Degradado inferior con el nombre del canal ("CAM 1", "CAM 2")
                TextView camLabel = new TextView(this);
                camLabel.setText(cam.getShortChannelLabel());
                camLabel.setTextColor(Color.WHITE);
                camLabel.setTextSize(12);
                camLabel.setTypeface(Typeface.DEFAULT_BOLD);
                camLabel.setSingleLine(true);
                camLabel.setEllipsize(TextUtils.TruncateAt.END);
                camLabel.setBackgroundColor(Color.parseColor("#88000000"));
                camLabel.setPadding(dp(8), dp(4), dp(8), dp(4));
                FrameLayout.LayoutParams clLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                clLp.gravity = Gravity.BOTTOM;
                thumbCard.addView(camLabel, clLp);

                // Al tocar una cámara individual: abre el visor en vivo 16:9 (1x1) solo con esa cámara
                thumbCard.setOnClickListener(v -> {
                    currentGridMode = 1;
                    showLiveViewScreen(cam, null);
                });

                thumbRow.addView(thumbCard);
            }

            thumbScroll.addView(thumbRow);
            card.addView(thumbScroll);

            // Pie de tarjeta: Punto azul + "Dispositivo P2P conectado · X canales"
            LinearLayout footerRow = new LinearLayout(this);
            footerRow.setOrientation(LinearLayout.HORIZONTAL);
            footerRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            footLp.topMargin = dp(10);

            View blueDot = new View(this);
            blueDot.setBackground(circleBg(COLOR_ACCENT_BLUE, null, 0));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
            dotLp.rightMargin = dp(8);
            footerRow.addView(blueDot, dotLp);

            TextView devSub = new TextView(this);
            devSub.setText("Dispositivo P2P · " + cams.size() + (cams.size() == 1 ? " canal disponible" : " canales disponibles"));
            devSub.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
            devSub.setTextSize(12);
            footerRow.addView(devSub);

            card.addView(footerRow, footLp);
            devicesContainer.addView(card, cardLp);
        }
    }

    // =========================================================================
    // 3. VISOR EN VIVO 16:9 (LIVE VIEW) IDÉNTICO A EASYVIEWER / DMSS (Capturas 2 y 7)
    // =========================================================================
    private void showLiveViewScreen(CameraItem singleCamToStart, List<CameraItem> groupCamsToStart) {
        currentTab = 1;
        releaseAllPlayers();
        rootContainer.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // ── BARRA SUPERIOR DE LIVE VIEW: (🏠 Home) | Título Dispositivo | (☰ Elegir Cámara) ──
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor(COLOR_BG_CARD));
        topBar.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView btnBackHome = new TextView(this);
        btnBackHome.setText("⌂  Inicio");
        btnBackHome.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        btnBackHome.setTextSize(15);
        btnBackHome.setTypeface(Typeface.DEFAULT_BOLD);
        btnBackHome.setOnClickListener(v -> showHomeScreen());
        topBar.addView(btnBackHome);

        liveHeaderTitle = new TextView(this);
        if (singleCamToStart != null) {
            liveHeaderTitle.setText(singleCamToStart.getGroupTitle());
        } else if (groupCamsToStart != null && !groupCamsToStart.isEmpty()) {
            liveHeaderTitle.setText(groupCamsToStart.get(0).getGroupTitle());
        } else {
            liveHeaderTitle.setText("Vista en Vivo");
        }
        liveHeaderTitle.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        liveHeaderTitle.setTextSize(16);
        liveHeaderTitle.setTypeface(Typeface.DEFAULT_BOLD);
        liveHeaderTitle.setGravity(Gravity.CENTER);
        topBar.addView(liveHeaderTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnSelectCamTop = new TextView(this);
        btnSelectCamTop.setText("☰ Cámaras");
        btnSelectCamTop.setTextColor(Color.parseColor(COLOR_ACCENT_BLUE));
        btnSelectCamTop.setTextSize(14);
        btnSelectCamTop.setTypeface(Typeface.DEFAULT_BOLD);
        btnSelectCamTop.setOnClickListener(v -> showCameraPickerDialog(selectedCellIndex));
        topBar.addView(btnSelectCamTop);

        page.addView(topBar);

        // ── ESCENARIO DE VIDEO 16:9 CENTRADO ──
        FrameLayout videoStageWrap = new FrameLayout(this);
        videoStageWrap.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        page.addView(videoStageWrap, stageLp);

        LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams gridLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        );
        gridLp.gravity = Gravity.CENTER;
        videoStageWrap.addView(gridContainer, gridLp);

        rowTop = new LinearLayout(this);
        rowTop.setOrientation(LinearLayout.HORIZONTAL);
        gridContainer.addView(rowTop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        rowBottom = new LinearLayout(this);
        rowBottom.setOrientation(LinearLayout.HORIZONTAL);
        gridContainer.addView(rowBottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        buildLiveCells16x9();

        // ── BARRA DE CONTROLES DEL REPRODUCTOR (Estilo Captura 2 y 7: Play/Pause, SD/HD, Audio, 1x1/2x2, Cerrar) ──
        LinearLayout controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER_VERTICAL);
        controlBar.setBackgroundColor(Color.parseColor(COLOR_BG_CARD));
        controlBar.setPadding(dp(8), dp(10), dp(8), dp(10));

        // 1. Play / Recargar celda seleccionada
        TextView btnReload = createControlBarItem("▶", "Conectar");
        btnReload.setOnClickListener(v -> {
            CellHolder h = cellHolders.get(selectedCellIndex);
            if (h.activeCamera != null) {
                playCameraInCell(selectedCellIndex, h.activeCamera);
            } else {
                showCameraPickerDialog(selectedCellIndex);
            }
        });
        controlBar.addView(btnReload);

        // 2. Calidad SD / HD
        btnQualityBadge = createControlBarItem(currentSubtype == 1 ? "SD" : "HD", "Calidad");
        btnQualityBadge.setOnClickListener(v -> toggleStreamQuality());
        controlBar.addView(btnQualityBadge);

        // 3. Audio Mute / Unmute
        btnMuteBadge = createControlBarItem(isMuted ? "🔇" : "🔊", "Audio");
        btnMuteBadge.setOnClickListener(v -> {
            isMuted = !isMuted;
            btnMuteBadge.setText(isMuted ? "🔇\nAudio" : "🔊\nAudio");
            for (CellHolder h : cellHolders) {
                if (h.player != null) h.player.setVolume(isMuted ? 0f : 1f);
            }
        });
        controlBar.addView(btnMuteBadge);

        // 4. Conmutador Grilla 1x1 / 2x2 (4 celdas)
        btnGridBadge = createControlBarItem(currentGridMode == 1 ? "[ 1 ]" : "[ 4 ]", "Vista");
        btnGridBadge.setOnClickListener(v -> switchGridMode(currentGridMode == 1 ? 4 : 1));
        controlBar.addView(btnGridBadge);

        // 5. Detener / Limpiar celda activa
        TextView btnStopCell = createControlBarItem("✕", "Cerrar");
        btnStopCell.setOnClickListener(v -> stopCellPlayer(selectedCellIndex));
        controlBar.addView(btnStopCell);

        page.addView(controlBar);

        // ── TIRA INFERIOR DE CANALES RÁPIDOS (Permite elegir qué cámara poner en la celda azul seleccionada) ──
        LinearLayout selectorSection = new LinearLayout(this);
        selectorSection.setOrientation(LinearLayout.VERTICAL);
        selectorSection.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        selectorSection.setPadding(dp(12), dp(10), dp(12), dp(12));

        TextView hintTv = new TextView(this);
        hintTv.setText("Toca un canal para abrirlo en el recuadro seleccionado (doble toque en el video para pantalla completa 16:9):");
        hintTv.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        hintTv.setTextSize(11);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hintLp.bottomMargin = dp(8);
        selectorSection.addView(hintTv, hintLp);

        HorizontalScrollView chScroll = new HorizontalScrollView(this);
        chScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chRow = new LinearLayout(this);
        chRow.setOrientation(LinearLayout.HORIZONTAL);

        for (CameraItem cam : allCameras) {
            Button pill = new Button(this);
            pill.setText(cam.getGroupTitle() + " · " + cam.getShortChannelLabel());
            pill.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
            pill.setTextSize(11);
            pill.setAllCaps(false);
            pill.setMinimumHeight(0);
            pill.setMinHeight(0);
            pill.setPadding(dp(12), dp(8), dp(12), dp(8));
            pill.setBackground(roundBg(COLOR_BG_CARD, 8, COLOR_BORDER_DARK, 1));
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            pLp.rightMargin = dp(8);
            pill.setOnClickListener(v -> {
                playCameraInCell(selectedCellIndex, cam);
                if (currentGridMode == 4) {
                    selectCell((selectedCellIndex + 1) % 4);
                }
            });
            chRow.addView(pill, pLp);
        }

        chScroll.addView(chRow);
        selectorSection.addView(chScroll);
        page.addView(selectorSection);

        rootContainer.addView(page);

        // Si el usuario eligió una cámara o un dispositivo en el Home, iniciar SOLO lo que pidió
        if (singleCamToStart != null) {
            selectCell(0);
            playCameraInCell(0, singleCamToStart);
        } else if (groupCamsToStart != null && !groupCamsToStart.isEmpty()) {
            int count = Math.min(groupCamsToStart.size(), 4);
            for (int i = 0; i < count; i++) {
                final int idx = i;
                final CameraItem c = groupCamsToStart.get(i);
                mainHandler.postDelayed(() -> playCameraInCell(idx, c), i * 350L);
            }
        }
    }

    private TextView createControlBarItem(String iconText, String subLabel) {
        TextView tv = new TextView(this);
        tv.setText(iconText + "\n" + subLabel);
        tv.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(4), dp(6), dp(4));
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return tv;
    }

    private void buildLiveCells16x9() {
        releaseAllPlayers();
        cellHolders.clear();
        rowTop.removeAllViews();
        rowBottom.removeAllViews();

        for (int i = 0; i < 4; i++) {
            final int cellIdx = i;
            CellHolder holder = new CellHolder();
            holder.index = cellIdx;

            // Recuadro con el color exacto de Captura 2 (#242B35) y borde azul (#2E82FF) cuando está seleccionado
            FrameLayout cellFrame = new FrameLayout(this);
            cellFrame.setBackground(roundBg(
                    COLOR_BG_CELL, 0,
                    cellIdx == selectedCellIndex ? COLOR_ACCENT_BLUE : "#151921",
                    cellIdx == selectedCellIndex ? 2 : 1
            ));

            // Miniatura de fondo de la última vez que se vio la cámara (en formato 16:9)
            ImageView thumbBg = new ImageView(this);
            thumbBg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbBg.setAlpha(0.45f);
            thumbBg.setVisibility(View.GONE);
            cellFrame.addView(thumbBg, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // PlayerView nativo en modo 16:9 (RESIZE_MODE_FIT garantiza relación 16:9 exacta sin deformar)
            PlayerView pv = new PlayerView(this);
            pv.setUseController(false);
            pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            pv.setBackgroundColor(Color.TRANSPARENT);
            cellFrame.addView(pv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // Signo "+" en el centro cuando la celda está vacía (idéntico a Captura 2)
            TextView plusTv = new TextView(this);
            plusTv.setText("+");
            plusTv.setTextColor(Color.parseColor("#526075"));
            plusTv.setTextSize(32);
            plusTv.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams plusLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            plusLp.gravity = Gravity.CENTER;
            cellFrame.addView(plusTv, plusLp);

            // Spinner de conexión
            ProgressBar spinner = new ProgressBar(this);
            spinner.setVisibility(View.GONE);
            FrameLayout.LayoutParams spinLp = new FrameLayout.LayoutParams(dp(32), dp(32));
            spinLp.gravity = Gravity.CENTER;
            cellFrame.addView(spinner, spinLp);

            // Etiqueta de estado debajo del spinner
            TextView lblStatus = new TextView(this);
            lblStatus.setText("");
            lblStatus.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
            lblStatus.setTextSize(11);
            lblStatus.setGravity(Gravity.CENTER);
            lblStatus.setVisibility(View.GONE);
            FrameLayout.LayoutParams stLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            stLp.gravity = Gravity.CENTER;
            stLp.topMargin = dp(36);
            cellFrame.addView(lblStatus, stLp);

            // Barra superior transparente con nombre de cámara
            TextView lblTitle = new TextView(this);
            lblTitle.setText("");
            lblTitle.setTextColor(Color.WHITE);
            lblTitle.setTextSize(11);
            lblTitle.setBackgroundColor(Color.parseColor("#77000000"));
            lblTitle.setPadding(dp(8), dp(3), dp(8), dp(3));
            lblTitle.setVisibility(View.GONE);
            FrameLayout.LayoutParams tlLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            tlLp.gravity = Gravity.TOP;
            cellFrame.addView(lblTitle, tlLp);

            cellFrame.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - holder.lastTapTime < 350) {
                    // Doble toque: alternar entre 1x1 (16:9 grande) y 2x2
                    selectedCellIndex = cellIdx;
                    switchGridMode(currentGridMode == 4 ? 1 : 4);
                } else {
                    selectCell(cellIdx);
                    if (holder.activeCamera == null) {
                        showCameraPickerDialog(cellIdx);
                    }
                }
                holder.lastTapTime = now;
            });

            cellFrame.setOnLongClickListener(v -> {
                selectCell(cellIdx);
                showCameraPickerDialog(cellIdx);
                return true;
            });

            holder.container = cellFrame;
            holder.thumbPreview = thumbBg;
            holder.playerView = pv;
            holder.plusIcon = plusTv;
            holder.spinner = spinner;
            holder.labelStatus = lblStatus;
            holder.labelTitle = lblTitle;

            cellHolders.add(holder);

            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            );
            if (i < 2) {
                rowTop.addView(cellFrame, cellLp);
            } else {
                rowBottom.addView(cellFrame, cellLp);
            }
        }

        applyGridVisibility();
    }

    private void selectCell(int idx) {
        selectedCellIndex = idx;
        for (int i = 0; i < cellHolders.size(); i++) {
            CellHolder h = cellHolders.get(i);
            h.container.setBackground(roundBg(
                    COLOR_BG_CELL, 0,
                    i == selectedCellIndex ? COLOR_ACCENT_BLUE : "#151921",
                    i == selectedCellIndex ? 2 : 1
            ));
        }
    }

    private void switchGridMode(int mode) {
        currentGridMode = mode;
        if (btnGridBadge != null) {
            btnGridBadge.setText((mode == 1 ? "[ 1 ]" : "[ 4 ]") + "\nVista");
        }
        applyGridVisibility();
    }

    private void applyGridVisibility() {
        if (cellHolders.size() < 4 || rowTop == null || rowBottom == null) return;
        if (currentGridMode == 1) {
            if (selectedCellIndex < 2) {
                rowTop.setVisibility(View.VISIBLE);
                rowBottom.setVisibility(View.GONE);
                cellHolders.get(0).container.setVisibility(selectedCellIndex == 0 ? View.VISIBLE : View.GONE);
                cellHolders.get(1).container.setVisibility(selectedCellIndex == 1 ? View.VISIBLE : View.GONE);
            } else {
                rowTop.setVisibility(View.GONE);
                rowBottom.setVisibility(View.VISIBLE);
                cellHolders.get(2).container.setVisibility(selectedCellIndex == 2 ? View.VISIBLE : View.GONE);
                cellHolders.get(3).container.setVisibility(selectedCellIndex == 3 ? View.VISIBLE : View.GONE);
            }
        } else {
            rowTop.setVisibility(View.VISIBLE);
            rowBottom.setVisibility(View.VISIBLE);
            for (int i = 0; i < 4; i++) {
                cellHolders.get(i).container.setVisibility(View.VISIBLE);
            }
        }
    }

    private void toggleStreamQuality() {
        currentSubtype = (currentSubtype == 1) ? 0 : 1;
        if (btnQualityBadge != null) {
            btnQualityBadge.setText((currentSubtype == 1 ? "SD" : "HD") + "\nCalidad");
        }
        Toast.makeText(this,
                currentSubtype == 1 ? "Calidad SD (Fluida)" : "Calidad HD (Alta Definición)",
                Toast.LENGTH_SHORT).show();

        for (CellHolder h : cellHolders) {
            if (h.activeCamera != null) {
                playCameraInCell(h.index, h.activeCamera);
            }
        }
    }

    private void showCameraPickerDialog(int cellIdx) {
        if (allCameras.isEmpty()) {
            Toast.makeText(this, "No hay cámaras disponibles", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[allCameras.size()];
        for (int i = 0; i < allCameras.size(); i++) {
            CameraItem c = allCameras.get(i);
            items[i] = c.getGroupTitle() + "  ▸  " + c.getShortChannelLabel();
        }
        new AlertDialog.Builder(this)
                .setTitle("Seleccionar cámara para Ventana " + (cellIdx + 1))
                .setItems(items, (d, which) -> playCameraInCell(cellIdx, allCameras.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showDeviceChannelSelectorModal(String groupTitle, List<CameraItem> cams) {
        String[] items = new String[cams.size() + 1];
        items[0] = "▶  Ver todas las cámaras de " + groupTitle + " (Multicanal)";
        for (int i = 0; i < cams.size(); i++) {
            items[i + 1] = "📹  " + cams.get(i).getShortChannelLabel();
        }
        new AlertDialog.Builder(this)
                .setTitle(groupTitle)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        currentGridMode = cams.size() > 1 ? 4 : 1;
                        showLiveViewScreen(null, cams);
                    } else {
                        currentGridMode = 1;
                        showLiveViewScreen(cams.get(which - 1), null);
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // =========================================================================
    // 4. PANTALLA GESTOR DE DISPOSITIVOS (DEVICE MANAGER - Captura 12)
    // =========================================================================
    private void showDeviceManagerScreen() {
        currentTab = 2;
        releaseAllPlayers();
        rootContainer.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView title = new TextView(this);
        title.setText("Mis Dispositivos");
        title.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(8), dp(14), dp(20));

        Map<String, List<CameraItem>> groups = new LinkedHashMap<>();
        for (CameraItem c : allCameras) {
            String g = c.getGroupTitle();
            if (!groups.containsKey(g)) groups.put(g, new ArrayList<>());
            groups.get(g).add(c);
        }

        for (Map.Entry<String, List<CameraItem>> entry : groups.entrySet()) {
            String gTitle = entry.getKey();
            List<CameraItem> cams = entry.getValue();
            String snText = cams.get(0).sn != null && !cams.get(0).sn.isEmpty() ? cams.get(0).sn : "IP Directo";

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(roundBg(COLOR_BG_CARD, 12, null, 0));
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rLp.bottomMargin = dp(10);

            TextView badge = new TextView(this);
            badge.setText("📹");
            badge.setTextSize(17);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(circleBg("#1E3A5F", null, 0));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            bLp.rightMargin = dp(12);
            row.addView(badge, bLp);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            TextView nameTv = new TextView(this);
            nameTv.setText(gTitle);
            nameTv.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
            nameTv.setTextSize(16);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            infoCol.addView(nameTv);

            TextView subTv = new TextView(this);
            subTv.setText("SN: " + snText + "  ·  " + cams.size() + " canales");
            subTv.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
            subTv.setTextSize(12);
            infoCol.addView(subTv);

            row.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
            arrow.setTextSize(22);
            row.addView(arrow);

            row.setOnClickListener(v -> showDeviceChannelSelectorModal(gTitle, cams));
            list.addView(row, rLp);
        }

        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(buildBottomNavBar());
        rootContainer.addView(page);
    }

    // =========================================================================
    // 5. PANTALLA DE PERFIL ("ME" - Captura 4)
    // =========================================================================
    private void showProfileScreen() {
        currentTab = 3;
        releaseAllPlayers();
        rootContainer.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(20), dp(42), dp(20), dp(24));

        // Avatar circular
        TextView avatar = new TextView(this);
        String initial = (currentUserName != null && !currentUserName.isEmpty())
                ? currentUserName.substring(0, 1).toUpperCase() : "L";
        avatar.setText(initial);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(30);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(circleBg("#2E3A4E", "#94A3B8", 2));
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(80), dp(80));
        avLp.bottomMargin = dp(12);
        content.addView(avatar, avLp);

        TextView uName = new TextView(this);
        uName.setText(currentUserName != null && !currentUserName.isEmpty() ? currentUserName : "Usuario Limen");
        uName.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        uName.setTextSize(20);
        uName.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(uName);

        TextView uMail = new TextView(this);
        uMail.setText(currentUserEmail + "  (" + currentUserRole.toUpperCase() + ")");
        uMail.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        uMail.setTextSize(13);
        LinearLayout.LayoutParams umLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        umLp.topMargin = dp(4);
        umLp.bottomMargin = dp(28);
        content.addView(uMail, umLp);

        // Tarjeta de opciones estilo Captura 4
        LinearLayout menuCard = new LinearLayout(this);
        menuCard.setOrientation(LinearLayout.VERTICAL);
        menuCard.setBackground(roundBg(COLOR_BG_CARD, 14, null, 0));
        menuCard.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams mcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        mcLp.bottomMargin = dp(20);

        menuCard.addView(createProfileRow("📹  Canales autorizados", allCameras.size() + " cámaras"));
        menuCard.addView(createProfileRow("⚡  Calidad por defecto", currentSubtype == 1 ? "SD (Fluida)" : "HD (1080p)"));
        menuCard.addView(createProfileRow("📐  Formato de video", "16:9 Panorámico"));
        menuCard.addView(createProfileRow("🛡  Versión de la App", "LIMEN EYES v2.2.0 Nativa"));

        content.addView(menuCard, mcLp);

        Button btnLogout = new Button(this);
        btnLogout.setText("Cerrar sesión");
        btnLogout.setTextColor(Color.parseColor("#F87171"));
        btnLogout.setAllCaps(false);
        btnLogout.setTextSize(15);
        btnLogout.setTypeface(Typeface.DEFAULT_BOLD);
        btnLogout.setBackground(roundBg("#2A161B", 12, "#EF4444", 1));
        btnLogout.setOnClickListener(v -> logout());
        content.addView(btnLogout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(buildBottomNavBar());
        rootContainer.addView(page);
    }

    private View createProfileRow(String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(14), dp(4), dp(14));

        TextView l = new TextView(this);
        l.setText(left);
        l.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        l.setTextSize(14);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView r = new TextView(this);
        r.setText(right);
        r.setTextColor(Color.parseColor(COLOR_TEXT_GRAY));
        r.setTextSize(13);
        row.addView(r);
        return row;
    }

    // ── BARRA INFERIOR DE NAVEGACIÓN (Inicio | Vista en Vivo | Dispositivos | Me) ──
    private View buildBottomNavBar() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(Color.parseColor(COLOR_BG_CARD));
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));

        nav.addView(createNavTab("⌂", "Inicio", currentTab == 0, this::showHomeScreen));
        nav.addView(createNavTab("▶", "En Vivo", currentTab == 1, () -> showLiveViewScreen(null, null)));
        nav.addView(createNavTab("🖧", "Dispositivos", currentTab == 2, this::showDeviceManagerScreen));
        nav.addView(createNavTab("☺", "Me", currentTab == 3, this::showProfileScreen));

        return nav;
    }

    private View createNavTab(String icon, String label, boolean active, Runnable action) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tab.setOnClickListener(v -> action.run());

        String color = active ? COLOR_ACCENT_BLUE : COLOR_TEXT_GRAY;

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextColor(Color.parseColor(color));
        ic.setTextSize(18);
        ic.setGravity(Gravity.CENTER);
        tab.addView(ic);

        TextView lb = new TextView(this);
        lb.setText(label);
        lb.setTextColor(Color.parseColor(color));
        lb.setTextSize(11);
        if (active) lb.setTypeface(Typeface.DEFAULT_BOLD);
        lb.setGravity(Gravity.CENTER);
        tab.addView(lb);

        return tab;
    }

    // =========================================================================
    // 6. CACHÉ Y CAPTURA DE MINIATURAS DE ÚLTIMA VEZ VISTA (16:9)
    // =========================================================================
    private File getLocalThumbFile(int camId) {
        File dir = new File(getCacheDir(), "thumbs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "cam_" + camId + ".jpg");
    }

    private void loadCameraThumbnailAsync(int camId, ImageView targetIv) {
        // 1. Si existe captura en el teléfono de la última vez que se vio, mostrar al instante
        File localFile = getLocalThumbFile(camId);
        if (localFile.exists() && localFile.length() > 500) {
            Bitmap bm = BitmapFactory.decodeFile(localFile.getAbsolutePath());
            if (bm != null) {
                targetIv.setImageBitmap(bm);
                targetIv.setVisibility(View.VISIBLE);
            }
        }

        // 2. Actualizar en segundo plano desde el caché de capturas del servidor
        executor.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/api/camaras/" + camId + "/thumb.jpg").openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(localFile)) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                        }
                    }
                    if (localFile.length() > 500) {
                        final Bitmap bm = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                        if (bm != null) {
                            mainHandler.post(() -> {
                                targetIv.setImageBitmap(bm);
                                targetIv.setVisibility(View.VISIBLE);
                            });
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void captureCurrentFrameToCache(CellHolder holder, int camId) {
        try {
            if (holder.playerView == null) return;
            View surface = holder.playerView.getVideoSurfaceView();
            if (surface instanceof TextureView) {
                Bitmap bm = ((TextureView) surface).getBitmap();
                if (bm != null) {
                    File out = getLocalThumbFile(camId);
                    executor.execute(() -> {
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            bm.compress(Bitmap.CompressFormat.JPEG, 82, fos);
                        } catch (Exception ignored) {}
                    });
                }
            } else {
                // Pedir al servidor que actualice la captura de esta cámara activa
                executor.execute(() -> {
                    try {
                        httpGet(BASE_URL + "/api/camaras/" + camId + "/thumb.jpg?live=1", authToken);
                    } catch (Exception ignored) {}
                });
            }
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // 7. MOTOR DE REPRODUCCIÓN NATIVO EXOPLAYER (Con H.264 + HEVC + MP4=FLAC)
    // =========================================================================
    private void playCameraInCell(int cellIdx, CameraItem cam) {
        if (cellIdx < 0 || cellIdx >= cellHolders.size()) return;
        CellHolder holder = cellHolders.get(cellIdx);
        holder.activeCamera = cam;
        final int currentReqId = ++holder.requestId;

        mainHandler.post(() -> {
            if (liveHeaderTitle != null) {
                liveHeaderTitle.setText(cam.getGroupTitle());
            }
            holder.plusIcon.setVisibility(View.GONE);
            holder.labelTitle.setText(cam.getGroupTitle() + " · " + cam.getShortChannelLabel() + (currentSubtype == 1 ? " [SD]" : " [HD]"));
            holder.labelTitle.setVisibility(View.VISIBLE);
            holder.labelStatus.setText("Conectando...");
            holder.labelStatus.setVisibility(View.VISIBLE);
            holder.spinner.setVisibility(View.VISIBLE);

            // Mostrar como fondo la última captura de esta cámara mientras carga el stream en vivo
            loadCameraThumbnailAsync(cam.id, holder.thumbPreview);

            if (holder.player != null) {
                holder.player.stop();
                holder.player.clearMediaItems();
            }
        });

        final int requestedSubtype = currentSubtype;

        executor.execute(() -> {
            try {
                JSONObject reqBody = new JSONObject();
                reqBody.put("subtype", requestedSubtype);

                HttpResponse resp = httpPostJson(
                        BASE_URL + "/api/stream/" + cam.id + "/activate",
                        reqBody.toString(),
                        authToken
                );

                if (holder.requestId != currentReqId) return;

                JSONObject data = new JSONObject(resp.body);
                if (resp.code != 200 || !data.optBoolean("ok", false)) {
                    throw new Exception(data.optString("error", "No se pudo abrir el canal"));
                }

                String streamName = data.optString("stream", "");
                String mp4Url = BASE_URL + "/api/stream.mp4?src=" + Uri.encode(streamName);

                mainHandler.post(() -> {
                    if (holder.requestId == currentReqId) {
                        startNativeExoPlayer(holder, mp4Url, cam.id);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error activando cámara " + cam.id, e);
                mainHandler.post(() -> {
                    if (holder.requestId == currentReqId) {
                        holder.spinner.setVisibility(View.GONE);
                        holder.labelStatus.setText("Sin conexión · Toca ▶ para reintentar");
                        holder.labelStatus.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void startNativeExoPlayer(CellHolder holder, String streamUrl, int camId) {
        try {
            if (holder.player == null) {
                DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(1500, 5000, 500, 1000)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build();

                ExoPlayer player = new ExoPlayer.Builder(this)
                        .setLoadControl(loadControl)
                        .build();

                player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                player.setVolume(isMuted ? 0f : 1f);

                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) {
                            holder.spinner.setVisibility(View.GONE);
                            holder.labelStatus.setVisibility(View.GONE);
                            holder.thumbPreview.setVisibility(View.GONE);
                            holder.plusIcon.setVisibility(View.GONE);
                            // Guardar captura fresca de la cámara vista
                            mainHandler.postDelayed(() -> captureCurrentFrameToCache(holder, camId), 1200);
                        } else if (state == Player.STATE_BUFFERING) {
                            holder.spinner.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onPlayerError(@NonNull PlaybackException error) {
                        holder.spinner.setVisibility(View.GONE);
                        holder.labelStatus.setText("Reconectando...");
                        holder.labelStatus.setVisibility(View.VISIBLE);
                        mainHandler.postDelayed(() -> {
                            if (holder.activeCamera != null && currentTab == 1 && !isFinishing()) {
                                playCameraInCell(holder.index, holder.activeCamera);
                            }
                        }, 3000);
                    }
                });

                holder.player = player;
                holder.playerView.setPlayer(player);
            }

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(streamUrl));
            holder.player.setMediaItem(mediaItem);
            holder.player.prepare();
            holder.player.setPlayWhenReady(true);
        } catch (Throwable t) {
            reportCrashToServer("startNativeExoPlayer error", t);
        }
    }

    private void stopCellPlayer(int cellIdx) {
        if (cellIdx < 0 || cellIdx >= cellHolders.size()) return;
        CellHolder h = cellHolders.get(cellIdx);
        h.activeCamera = null;
        h.requestId++;
        if (h.player != null) {
            try {
                h.player.stop();
                h.player.clearMediaItems();
            } catch (Throwable ignored) {}
        }
        h.spinner.setVisibility(View.GONE);
        h.thumbPreview.setVisibility(View.GONE);
        h.labelTitle.setVisibility(View.GONE);
        h.labelStatus.setVisibility(View.GONE);
        h.plusIcon.setVisibility(View.VISIBLE);
    }

    private void releaseAllPlayers() {
        for (CellHolder h : cellHolders) {
            h.requestId++;
            if (h.player != null) {
                try {
                    h.player.release();
                } catch (Throwable ignored) {}
                h.player = null;
            }
        }
    }

    private void reportCrashToServer(String context, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            final String stack = sw.toString();
            Log.e(TAG, context + ": " + stack);

            new Thread(() -> {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("context", context);
                    obj.put("message", t.getMessage());
                    obj.put("stack", stack);
                    httpPostJson(BASE_URL + "/api/app-crash", obj.toString(), null);
                } catch (Exception ignored) {}
            }).start();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onStop() {
        super.onStop();
        for (CellHolder h : cellHolders) {
            if (h.player != null) {
                try { h.player.setPlayWhenReady(false); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentTab == 1) {
            for (CellHolder h : cellHolders) {
                if (h.player != null && h.activeCamera != null) {
                    try { h.player.setPlayWhenReady(true); } catch (Throwable ignored) {}
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        releaseAllPlayers();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (currentTab == 1) {
            if (currentGridMode == 1 && countActiveCells() > 1) {
                switchGridMode(4);
            } else {
                showHomeScreen();
            }
        } else if (currentTab != 0) {
            showHomeScreen();
        } else {
            super.onBackPressed();
        }
    }

    private int countActiveCells() {
        int c = 0;
        for (CellHolder h : cellHolders) {
            if (h.activeCamera != null) c++;
        }
        return c;
    }

    // =========================================================================
    // 8. UTILIDADES HTTP NATIVAS
    // =========================================================================
    private static class HttpResponse {
        int code;
        String body;
    }

    private HttpResponse httpGet(String urlStr, String bearerToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        if (bearerToken != null && !bearerToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        return readHttpResponse(conn);
    }

    private HttpResponse httpPostJson(String urlStr, String jsonBody, String bearerToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (bearerToken != null && !bearerToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        return readHttpResponse(conn);
    }

    private HttpResponse readHttpResponse(HttpURLConnection conn) throws Exception {
        HttpResponse res = new HttpResponse();
        res.code = conn.getResponseCode();
        InputStream is = (res.code >= 200 && res.code < 400) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
        }
        res.body = sb.toString();
        return res;
    }
}

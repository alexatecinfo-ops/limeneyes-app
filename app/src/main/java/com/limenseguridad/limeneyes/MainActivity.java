package com.limenseguridad.limeneyes;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LimenEyesNative";
    private static final String BASE_URL = "https://eyes.limenseguridad.com";
    private static final String PREFS_NAME = "LimenEyesPrefs";

    private FrameLayout rootContainer;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String authToken = "";
    private String currentUserName = "";
    private String currentUserEmail = "";
    private String currentUserRole = "";

    private final List<CameraItem> allCameras = new ArrayList<>();
    private String selectedClientFilter = "TODOS";
    private int currentGridMode = 4; // 1 o 4 celdas estilo EasyViewer / gDMSS
    private int currentSubtype = 1;  // 1 = SD (rápido), 0 = HD (alta definición)

    private final List<CellHolder> cellHolders = new ArrayList<>();
    private int selectedCellIndex = 0;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private TextView statusErrorText;
    private LinearLayout rowTop, rowBottom;
    private LinearLayout clientFilterBar;
    private Button btnGrid1, btnGrid4, btnQuality;

    public static class CameraItem {
        public int id;
        public String nombre;
        public String sn;
        public int canal;
        public String clienteNombre;
        public String marca;

        public String getDisplayTitle() {
            if (clienteNombre != null && !clienteNombre.isEmpty()) {
                return clienteNombre + " - " + nombre + " (CH" + canal + ")";
            }
            return nombre + " (CH" + canal + ")";
        }
    }

    public static class CellHolder {
        public int index;
        public FrameLayout container;
        public PlayerView playerView;
        public ExoPlayer player;
        public TextView labelTitle;
        public TextView labelStatus;
        public ProgressBar spinner;
        public CameraItem activeCamera;
        public int activeSubtype = 1;
        public long lastTapTime = 0;
        public int requestId = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Capturador global de excepciones para enviar telemetría y evitar cierre brusco sin rastro
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            reportCrashToServer("UncaughtException on " + thread.getName(), throwable);
        });

        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#0b0f17"));
            window.setNavigationBarColor(Color.parseColor("#0b0f17"));

            setContentView(R.layout.activity_main);
            rootContainer = findViewById(R.id.rootContainer);

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
                showDashboardScreen();
            } else {
                showLoginScreen();
            }
        } catch (Throwable t) {
            reportCrashToServer("onCreate fatal error", t);
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

            mainHandler.post(() -> Toast.makeText(
                    MainActivity.this,
                    "Aviso técnico: " + t.getClass().getSimpleName() + " - " + t.getMessage(),
                    Toast.LENGTH_LONG
            ).show());
        } catch (Throwable ignored) {}
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

    // =========================================================================
    // 1. PANTALLA DE LOGIN NATIVA
    // =========================================================================
    private void showLoginScreen() {
        try {
            releaseAllPlayers();
            rootContainer.removeAllViews();

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(Color.parseColor("#0b0f17"));

            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setGravity(Gravity.CENTER);
            wrapper.setPadding(dp(24), dp(32), dp(24), dp(32));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(26), dp(32), dp(26), dp(28));
            card.setBackground(roundBg("#131b2e", 20, "#26324d", 1));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            card.setLayoutParams(cardParams);

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.mipmap.ic_launcher);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(72), dp(72));
            logoParams.gravity = Gravity.CENTER_HORIZONTAL;
            logoParams.bottomMargin = dp(12);
            card.addView(logo, logoParams);

            TextView title = new TextView(this);
            title.setText("LIMEN EYES");
            title.setTextColor(Color.WHITE);
            title.setTextSize(24);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            card.addView(title);

            TextView subtitle = new TextView(this);
            subtitle.setText("Visor Nativo P2P de Alta Velocidad v2.1");
            subtitle.setTextColor(Color.parseColor("#94a3b8"));
            subtitle.setTextSize(13);
            subtitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            subParams.bottomMargin = dp(22);
            card.addView(subtitle, subParams);

            statusErrorText = new TextView(this);
            statusErrorText.setTextColor(Color.parseColor("#f87171"));
            statusErrorText.setBackground(roundBg("#2c1519", 8, "#dc2626", 1));
            statusErrorText.setPadding(dp(12), dp(10), dp(12), dp(10));
            statusErrorText.setTextSize(13);
            statusErrorText.setVisibility(View.GONE);
            LinearLayout.LayoutParams errParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            errParams.bottomMargin = dp(16);
            card.addView(statusErrorText, errParams);

            TextView lblUser = new TextView(this);
            lblUser.setText("USUARIO O EMAIL AUTORIZADO");
            lblUser.setTextColor(Color.parseColor("#94a3b8"));
            lblUser.setTextSize(11);
            lblUser.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(lblUser);

            EditText inputUser = new EditText(this);
            inputUser.setHint("Ej: admin o tu correo @gmail.com");
            inputUser.setHintTextColor(Color.parseColor("#64748b"));
            inputUser.setTextColor(Color.WHITE);
            inputUser.setSingleLine(true);
            inputUser.setBackground(roundBg("#0b0f17", 10, "#26324d", 1));
            inputUser.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams inUserParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            inUserParams.topMargin = dp(6);
            inUserParams.bottomMargin = dp(14);
            card.addView(inputUser, inUserParams);

            TextView lblPass = new TextView(this);
            lblPass.setText("CONTRASEÑA");
            lblPass.setTextColor(Color.parseColor("#94a3b8"));
            lblPass.setTextSize(11);
            lblPass.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(lblPass);

            EditText inputPass = new EditText(this);
            inputPass.setHint("Contraseña de Limen");
            inputPass.setHintTextColor(Color.parseColor("#64748b"));
            inputPass.setTextColor(Color.WHITE);
            inputPass.setSingleLine(true);
            inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            inputPass.setBackground(roundBg("#0b0f17", 10, "#26324d", 1));
            inputPass.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams inPassParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            inPassParams.topMargin = dp(6);
            inPassParams.bottomMargin = dp(20);
            card.addView(inputPass, inPassParams);

            Button btnLogin = new Button(this);
            btnLogin.setText("INGRESAR AL SISTEMA");
            btnLogin.setTextColor(Color.WHITE);
            btnLogin.setTypeface(Typeface.DEFAULT_BOLD);
            btnLogin.setAllCaps(false);
            btnLogin.setBackground(roundBg("#9333ea", 10, null, 0));
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            );
            btnParams.bottomMargin = dp(12);
            btnLogin.setOnClickListener(v -> {
                String u = inputUser.getText().toString().trim();
                String p = inputPass.getText().toString().trim();
                if (u.isEmpty() || p.isEmpty()) {
                    showLoginError("Ingresa usuario y contraseña");
                    return;
                }
                performStandardLogin(u, p, btnLogin);
            });
            card.addView(btnLogin, btnParams);

            Button btnGoogle = new Button(this);
            btnGoogle.setText("Continuar con Cuenta de Google");
            btnGoogle.setTextColor(Color.parseColor("#0f172a"));
            btnGoogle.setTypeface(Typeface.DEFAULT_BOLD);
            btnGoogle.setAllCaps(false);
            btnGoogle.setBackground(roundBg("#ffffff", 10, null, 0));
            LinearLayout.LayoutParams googleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            );
            btnGoogle.setOnClickListener(v -> startNativeGoogleSignIn(inputUser.getText().toString().trim()));
            card.addView(btnGoogle, googleParams);

            wrapper.addView(card);
            scroll.addView(wrapper);
            rootContainer.addView(scroll);
        } catch (Throwable t) {
            reportCrashToServer("showLoginScreen error", t);
        }
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
            Log.w(TAG, "GoogleSignIn ApiException code=" + e.getStatusCode());
            promptGoogleEmailDialog();
        }
    }

    private void promptGoogleEmailDialog() {
        try {
            EditText emailInput = new EditText(this);
            emailInput.setHint("tu-correo@gmail.com");
            emailInput.setSingleLine(true);
            emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            emailInput.setPadding(dp(20), dp(16), dp(20), dp(16));

            new AlertDialog.Builder(this)
                    .setTitle("Validar Cuenta Google en Limen")
                    .setMessage("Ingresa tu correo registrado en Limen Admin para autorizar el acceso directo:")
                    .setView(emailInput)
                    .setPositiveButton("Validar", (d, w) -> {
                        String email = emailInput.getText().toString().trim();
                        if (!email.isEmpty()) {
                            verifyGoogleEmailOnServer(email, email.split("@")[0]);
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } catch (Throwable t) {
            reportCrashToServer("promptGoogleEmailDialog error", t);
        }
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
                showLoginError("Error de red validando cuenta: " + e.getMessage());
            }
        });
    }

    private void performStandardLogin(String user, String pass, Button btn) {
        btn.setEnabled(false);
        btn.setText("CONECTANDO...");
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
                    String err = json.optString("error", "Credenciales incorrectas");
                    showLoginError(err);
                }
            } catch (Exception e) {
                showLoginError("Error de conexión con el servidor");
            } finally {
                mainHandler.post(() -> {
                    btn.setEnabled(true);
                    btn.setText("INGRESAR AL SISTEMA");
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

        mainHandler.post(this::showDashboardScreen);
    }

    private void logout() {
        releaseAllPlayers();
        authToken = "";
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
        showLoginScreen();
    }

    // =========================================================================
    // 2. DASHBOARD NATIVO ESTILO EASYVIEWER / gDMSS
    // =========================================================================
    private void showDashboardScreen() {
        try {
            releaseAllPlayers();
            rootContainer.removeAllViews();

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setBackgroundColor(Color.parseColor("#0b0f17"));
            mainLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // ── BARRA SUPERIOR (Header) ──
            LinearLayout topBar = new LinearLayout(this);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            topBar.setBackgroundColor(Color.parseColor("#131b2e"));
            topBar.setPadding(dp(12), dp(10), dp(12), dp(10));

            TextView appTitle = new TextView(this);
            appTitle.setText("LIMEN EYES");
            appTitle.setTextColor(Color.WHITE);
            appTitle.setTextSize(15);
            appTitle.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            );
            topBar.addView(appTitle, titleParams);

            btnQuality = createTopButton(currentSubtype == 1 ? "SD" : "HD", "#1e293b");
            btnQuality.setOnClickListener(v -> toggleStreamQuality());
            topBar.addView(btnQuality);

            btnGrid1 = createTopButton("1x1", currentGridMode == 1 ? "#9333ea" : "#1e293b");
            btnGrid1.setOnClickListener(v -> switchGridMode(1));
            topBar.addView(btnGrid1);

            btnGrid4 = createTopButton("2x2", currentGridMode == 4 ? "#9333ea" : "#1e293b");
            btnGrid4.setOnClickListener(v -> switchGridMode(4));
            topBar.addView(btnGrid4);

            Button btnExit = createTopButton("Salir", "#331a24");
            btnExit.setOnClickListener(v -> logout());
            topBar.addView(btnExit);

            mainLayout.addView(topBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            // ── FILTRO DE CLIENTES / GRABADORES (Pills Horizontales) ──
            HorizontalScrollView clientScroll = new HorizontalScrollView(this);
            clientScroll.setHorizontalScrollBarEnabled(false);
            clientScroll.setBackgroundColor(Color.parseColor("#0f1523"));
            clientScroll.setPadding(dp(8), dp(6), dp(8), dp(6));

            clientFilterBar = new LinearLayout(this);
            clientFilterBar.setOrientation(LinearLayout.HORIZONTAL);
            clientScroll.addView(clientFilterBar);
            mainLayout.addView(clientScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            // ── GRILLA DE VIDEO NATIVO (2 Filas LinearLayout con Weight 100% seguro) ──
            LinearLayout gridContainer = new LinearLayout(this);
            gridContainer.setOrientation(LinearLayout.VERTICAL);
            gridContainer.setBackgroundColor(Color.BLACK);
            LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            );
            mainLayout.addView(gridContainer, gridParams);

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

            // ── BARRA INFERIOR DE CANALES RÁPIDOS ──
            HorizontalScrollView camBarScroll = new HorizontalScrollView(this);
            camBarScroll.setHorizontalScrollBarEnabled(false);
            camBarScroll.setBackgroundColor(Color.parseColor("#131b2e"));
            camBarScroll.setPadding(dp(8), dp(8), dp(8), dp(8));

            LinearLayout bottomCamList = new LinearLayout(this);
            bottomCamList.setOrientation(LinearLayout.HORIZONTAL);
            camBarScroll.addView(bottomCamList);
            mainLayout.addView(camBarScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            rootContainer.addView(mainLayout);

            buildVideoCells();
            fetchCamerasFromServer(bottomCamList);
        } catch (Throwable t) {
            reportCrashToServer("showDashboardScreen error", t);
        }
    }

    private Button createTopButton(String text, String bgHex) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinimumWidth(0);
        b.setMinWidth(0);
        b.setMinimumHeight(0);
        b.setMinHeight(0);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setBackground(roundBg(bgHex, 6, "#334155", 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildVideoCells() {
        releaseAllPlayers();
        cellHolders.clear();
        rowTop.removeAllViews();
        rowBottom.removeAllViews();

        for (int i = 0; i < 4; i++) {
            final int cellIdx = i;
            CellHolder holder = new CellHolder();
            holder.index = cellIdx;

            FrameLayout cellFrame = new FrameLayout(this);
            cellFrame.setBackground(roundBg("#05070b", 0, cellIdx == selectedCellIndex ? "#a855f7" : "#1e293b", 2));

            PlayerView pv = new PlayerView(this);
            pv.setUseController(false);
            pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            pv.setBackgroundColor(Color.TRANSPARENT);
            cellFrame.addView(pv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));

            ProgressBar spinner = new ProgressBar(this);
            spinner.setVisibility(View.GONE);
            FrameLayout.LayoutParams spinParams = new FrameLayout.LayoutParams(dp(34), dp(34));
            spinParams.gravity = Gravity.CENTER;
            cellFrame.addView(spinner, spinParams);

            TextView lblStatus = new TextView(this);
            lblStatus.setText("+ Seleccionar Cámara " + (cellIdx + 1));
            lblStatus.setTextColor(Color.parseColor("#64748b"));
            lblStatus.setTextSize(12);
            lblStatus.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams stParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            stParams.gravity = Gravity.CENTER;
            cellFrame.addView(lblStatus, stParams);

            TextView lblTitle = new TextView(this);
            lblTitle.setText("Canal " + (cellIdx + 1));
            lblTitle.setTextColor(Color.WHITE);
            lblTitle.setTextSize(11);
            lblTitle.setBackgroundColor(Color.parseColor("#99000000"));
            lblTitle.setPadding(dp(8), dp(4), dp(8), dp(4));
            FrameLayout.LayoutParams tlParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            tlParams.gravity = Gravity.TOP;
            cellFrame.addView(lblTitle, tlParams);

            cellFrame.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - holder.lastTapTime < 350) {
                    if (currentGridMode == 4) {
                        selectedCellIndex = cellIdx;
                        switchGridMode(1);
                    } else {
                        switchGridMode(4);
                    }
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
            holder.playerView = pv;
            holder.spinner = spinner;
            holder.labelStatus = lblStatus;
            holder.labelTitle = lblTitle;

            cellHolders.add(holder);

            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            );
            if (i < 2) {
                rowTop.addView(cellFrame, cellParams);
            } else {
                rowBottom.addView(cellFrame, cellParams);
            }
        }

        applyGridVisibility();
    }

    private void selectCell(int idx) {
        selectedCellIndex = idx;
        for (int i = 0; i < cellHolders.size(); i++) {
            CellHolder h = cellHolders.get(i);
            h.container.setBackground(roundBg(
                    "#05070b", 0, i == selectedCellIndex ? "#a855f7" : "#1e293b", i == selectedCellIndex ? 2 : 1
            ));
        }
    }

    private void switchGridMode(int mode) {
        currentGridMode = mode;
        btnGrid1.setBackground(roundBg(mode == 1 ? "#9333ea" : "#1e293b", 6, "#334155", 1));
        btnGrid4.setBackground(roundBg(mode == 4 ? "#9333ea" : "#1e293b", 6, "#334155", 1));
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
        btnQuality.setText(currentSubtype == 1 ? "SD" : "HD");
        Toast.makeText(this,
                currentSubtype == 1 ? "Modo SD: Carga rápida" : "Modo HD: Alta definición",
                Toast.LENGTH_SHORT).show();

        for (CellHolder h : cellHolders) {
            if (h.activeCamera != null) {
                playCameraInCell(h.index, h.activeCamera);
            }
        }
    }

    private void fetchCamerasFromServer(LinearLayout bottomCamList) {
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
                    item.nombre = o.optString("nombre", "Cámara");
                    item.sn = o.optString("sn", "");
                    item.canal = o.optInt("canal", 1);
                    item.clienteNombre = o.optString("cliente_nombre", "");
                    item.marca = o.optString("marca", "Dahua");
                    loaded.add(item);
                }

                mainHandler.post(() -> {
                    try {
                        allCameras.clear();
                        allCameras.addAll(loaded);
                        populateClientFilterPills(bottomCamList);
                        populateBottomCameraButtons(bottomCamList);

                        // Auto-cargar de manera escalonada las primeras cámaras para no saturar el decoder de hardware
                        List<CameraItem> filtered = getFilteredCameras();
                        int limit = Math.min(filtered.size(), 4);
                        for (int i = 0; i < limit; i++) {
                            final int cellIdx = i;
                            final CameraItem cam = filtered.get(i);
                            mainHandler.postDelayed(() -> playCameraInCell(cellIdx, cam), i * 350L);
                        }
                    } catch (Throwable t) {
                        reportCrashToServer("fetchCameras post UI error", t);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error cargando cámaras", e);
                mainHandler.post(() -> Toast.makeText(this, "Error cargando lista de cámaras", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private List<CameraItem> getFilteredCameras() {
        if ("TODOS".equals(selectedClientFilter)) {
            return new ArrayList<>(allCameras);
        }
        List<CameraItem> out = new ArrayList<>();
        for (CameraItem c : allCameras) {
            String cl = (c.clienteNombre != null && !c.clienteNombre.isEmpty()) ? c.clienteNombre : "General";
            if (selectedClientFilter.equalsIgnoreCase(cl)) {
                out.add(c);
            }
        }
        return out;
    }

    private void populateClientFilterPills(LinearLayout bottomCamList) {
        clientFilterBar.removeAllViews();
        Set<String> clients = new LinkedHashSet<>();
        clients.add("TODOS");
        for (CameraItem c : allCameras) {
            String cl = (c.clienteNombre != null && !c.clienteNombre.isEmpty()) ? c.clienteNombre : "General";
            clients.add(cl);
        }

        for (String cl : clients) {
            Button pill = new Button(this);
            pill.setText(cl);
            pill.setTextSize(11);
            pill.setAllCaps(false);
            boolean active = cl.equals(selectedClientFilter);
            pill.setTextColor(active ? Color.WHITE : Color.parseColor("#94a3b8"));
            pill.setBackground(roundBg(active ? "#9333ea" : "#1e293b", 14, null, 0));
            pill.setMinimumHeight(0);
            pill.setMinHeight(0);
            pill.setMinimumWidth(0);
            pill.setMinWidth(0);
            pill.setPadding(dp(12), dp(5), dp(12), dp(5));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.rightMargin = dp(6);
            pill.setLayoutParams(lp);

            pill.setOnClickListener(v -> {
                selectedClientFilter = cl;
                populateClientFilterPills(bottomCamList);
                populateBottomCameraButtons(bottomCamList);

                List<CameraItem> cams = getFilteredCameras();
                for (int i = 0; i < 4; i++) {
                    if (i < cams.size()) {
                        final int idx = i;
                        final CameraItem cam = cams.get(i);
                        mainHandler.postDelayed(() -> playCameraInCell(idx, cam), i * 300L);
                    } else {
                        stopCellPlayer(i);
                    }
                }
            });
            clientFilterBar.addView(pill);
        }
    }

    private void populateBottomCameraButtons(LinearLayout bottomCamList) {
        bottomCamList.removeAllViews();
        List<CameraItem> cams = getFilteredCameras();
        for (CameraItem cam : cams) {
            Button b = new Button(this);
            b.setText("CH" + cam.canal + " · " + cam.nombre);
            b.setTextColor(Color.WHITE);
            b.setTextSize(11);
            b.setAllCaps(false);
            b.setBackground(roundBg("#1e293b", 8, "#334155", 1));
            b.setMinimumHeight(0);
            b.setMinHeight(0);
            b.setPadding(dp(12), dp(8), dp(12), dp(8));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.rightMargin = dp(6);
            b.setLayoutParams(lp);

            b.setOnClickListener(v -> {
                playCameraInCell(selectedCellIndex, cam);
                if (currentGridMode == 4) {
                    selectCell((selectedCellIndex + 1) % 4);
                }
            });
            bottomCamList.addView(b);
        }
    }

    private void showCameraPickerDialog(int cellIdx) {
        List<CameraItem> cams = getFilteredCameras();
        if (cams.isEmpty()) {
            Toast.makeText(this, "No hay cámaras disponibles", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[cams.size()];
        for (int i = 0; i < cams.size(); i++) {
            labels[i] = cams.get(i).getDisplayTitle();
        }
        new AlertDialog.Builder(this)
                .setTitle("Abrir en Ventana " + (cellIdx + 1))
                .setItems(labels, (d, which) -> playCameraInCell(cellIdx, cams.get(which)))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // =========================================================================
    // 3. MOTOR DE REPRODUCCIÓN NATIVO EXOPLAYER (Con Buffer Seguro)
    // =========================================================================
    private void playCameraInCell(int cellIdx, CameraItem cam) {
        if (cellIdx < 0 || cellIdx >= cellHolders.size()) return;
        CellHolder holder = cellHolders.get(cellIdx);
        holder.activeCamera = cam;
        holder.activeSubtype = currentSubtype;
        final int currentReqId = ++holder.requestId;

        mainHandler.post(() -> {
            try {
                holder.labelTitle.setText(cam.getDisplayTitle() + (currentSubtype == 1 ? " [SD]" : " [HD]"));
                holder.labelStatus.setText("Conectando P2P...");
                holder.labelStatus.setVisibility(View.VISIBLE);
                holder.spinner.setVisibility(View.VISIBLE);
                if (holder.player != null) {
                    holder.player.stop();
                    holder.player.clearMediaItems();
                }
            } catch (Throwable t) {
                reportCrashToServer("playCameraInCell UI prep error", t);
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
                    throw new Exception(data.optString("error", "No se pudo abrir el túnel P2P"));
                }

                String streamName = data.optString("stream", "");
                String mp4Url = BASE_URL + "/api/stream.mp4?src=" + Uri.encode(streamName);

                mainHandler.post(() -> {
                    if (holder.requestId == currentReqId) {
                        startNativeExoPlayer(holder, mp4Url);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error activando stream P2P para cam " + cam.id, e);
                mainHandler.post(() -> {
                    if (holder.requestId == currentReqId) {
                        holder.spinner.setVisibility(View.GONE);
                        holder.labelStatus.setText("Sin señal (" + cam.nombre + ")\nToca para reintentar");
                        holder.labelStatus.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void startNativeExoPlayer(CellHolder holder, String streamUrl) {
        try {
            if (holder.player == null) {
                // Buffer válido y seguro en Media3 (evita IllegalArgumentException)
                DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                                1500,  // minBufferMs
                                5000,  // maxBufferMs
                                500,   // bufferForPlaybackMs
                                1000   // bufferForPlaybackAfterRebufferMs
                        )
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build();

                ExoPlayer player = new ExoPlayer.Builder(this)
                        .setLoadControl(loadControl)
                        .build();

                player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) {
                            holder.spinner.setVisibility(View.GONE);
                            holder.labelStatus.setVisibility(View.GONE);
                        } else if (state == Player.STATE_BUFFERING) {
                            holder.spinner.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onPlayerError(@NonNull PlaybackException error) {
                        Log.e(TAG, "ExoPlayer error en celda " + holder.index + ": " + error.getMessage());
                        holder.spinner.setVisibility(View.GONE);
                        holder.labelStatus.setText("Reconectando stream...");
                        holder.labelStatus.setVisibility(View.VISIBLE);
                        mainHandler.postDelayed(() -> {
                            if (holder.activeCamera != null && !isFinishing()) {
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
            holder.spinner.setVisibility(View.GONE);
            holder.labelStatus.setText("Error reproductor: " + t.getMessage());
            holder.labelStatus.setVisibility(View.VISIBLE);
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
        h.labelTitle.setText("Canal " + (cellIdx + 1));
        h.labelStatus.setText("+ Seleccionar Cámara " + (cellIdx + 1));
        h.labelStatus.setVisibility(View.VISIBLE);
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

    @Override
    protected void onStop() {
        super.onStop();
        for (CellHolder h : cellHolders) {
            if (h.player != null) {
                try {
                    h.player.setPlayWhenReady(false);
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        for (CellHolder h : cellHolders) {
            if (h.player != null && h.activeCamera != null) {
                try {
                    h.player.setPlayWhenReady(true);
                } catch (Throwable ignored) {}
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
        if (currentGridMode == 1) {
            switchGridMode(4);
        } else {
            super.onBackPressed();
        }
    }

    // =========================================================================
    // 4. UTILIDADES HTTP NATIVAS
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

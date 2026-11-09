package com.limenseguridad.limeneyes;

import android.app.Dialog;
import android.app.DatePickerDialog;
import java.util.Calendar;
import android.content.Context;
import android.content.res.ColorStateList;
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
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.MotionEvent;
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
import androidx.core.content.FileProvider;
import android.os.Build;
import android.accounts.AccountManager;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.File;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LimenEyesDMSS";
    private static final String BASE_URL = "https://eyes.limenseguridad.com";
    private static final String PREFS_NAME = "LimenEyesPrefs";
    private static final String KEY_COMPACT_HOME = "compact_home_mode";
    private static final String APP_VERSION_NAME = "2.4.6";

    // Paleta Oficial Limen Seguridad (Obsidian / Slate / Electric Blue & Cyan)
    private static final String COLOR_BG_MAIN      = "#080B14";
    private static final String COLOR_BG_HEADER    = "#0F1520";
    private static final String COLOR_BG_CARD      = "#141C2E";
    private static final String COLOR_BG_ELEVATED  = "#1A233A";
    private static final String COLOR_BG_INPUT     = "#0D131F";
    private static final String COLOR_BG_CELL      = "#060910";
    private static final String COLOR_ACCENT_BLUE  = "#6366F1";
    private static final String COLOR_ACCENT_CYAN  = "#818CF8";
    private static final String COLOR_BTN_BLUE     = "#4F46E5";
    private static final String COLOR_ONLINE       = "#10B981";
    private static final String COLOR_TEXT_WHITE   = "#F8FAFC";
    private static final String COLOR_TEXT_GRAY    = "#94A3B8";
    private static final String COLOR_TEXT_MUTED   = "#64748B";
    private static final String COLOR_BORDER_DARK  = "#20283E";

    // Tipografía 100% uniforme en toda la aplicación
    private static final Typeface FONT_REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface FONT_MEDIUM  = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private FrameLayout rootContainer;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private String authToken = "";
    private String currentUserName = "";
    private String currentUserEmail = "";
    private String currentUserRole = "";

    private final List<CameraItem> allCameras = new ArrayList<>();
    private String searchQuery = "";

    // 0 = Inicio, 1 = Vista en Vivo, 2 = Dispositivos, 3 = Perfil
    private int currentTab = 0;

    // Modo Inicio: false = Tarjetas con miniaturas 16:9 (Img 3), true = Lista compacta (Img 1)
    private boolean isHomeCompactMode = false;

    // Estado del Reproductor en Vivo
    private int currentGridMode = 4; // 1 (1x1), 4 (2x2), 9 (3x3)
    private int currentSubtype = 1;  // 1 = SD (rápido), 0 = HD (principal)
    private int selectedCellIndex = 0;
    private boolean isMuted = true;
    private boolean isAllPaused = false;
    private boolean isLandscapeFullscreen = false;
    private String activeLiveGroupFilter = null;

    private final List<CellHolder> cellHolders = new ArrayList<>();
    private final List<LinearLayout> gridRows = new ArrayList<>();
    private FrameLayout liveVideoStage;
    private LimenIcons.IconView playPauseIconView;
    private LimenIcons.IconView iconQualityView;
    private LimenIcons.IconView iconGridView;
    private LimenIcons.IconView iconMuteView;
    private LimenIcons.IconView audioConsoleIconView;
    private FrameLayout audioConsoleCircle;
    private TextView liveHeaderTitle;
    private TextView liveHeaderSubtitle;
    private LinearLayout bottomChannelRow;

    private final List<String> recentEventsLog = new ArrayList<>();
    private TextView alarmLogContentText;
    private boolean isAlarmPanelExpanded = true;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private ActivityResultLauncher<Intent> accountChooserLauncher;
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
                return "CAM " + canal + " · " + nombre.trim();
            }
            return "CAM " + canal;
        }

        public String getCompactBadgeLabel() {
            return "CAM " + canal;
        }
    }

    public static class CellHolder {
        public int index;
        public FrameLayout container;
        public PlayerView playerView;
        public ExoPlayer player;
        public ImageView thumbPreview;
        public LimenIcons.IconView plusIcon;
        public TextView labelTopLeft;
        public TextView labelTopRightBitrate;
        public TextView labelBottomLeft;
        public TextView labelStatus;
        public ProgressBar spinner;
        public CameraItem activeCamera;
        public long lastTapTime = 0;
        public int requestId = 0;
    }

    private final Runnable bitrateTicker = new Runnable() {
        @Override
        public void run() {
            if (currentTab == 1) {
                for (CellHolder h : cellHolders) {
                    if (h.activeCamera != null && h.player != null
                            && h.player.getPlaybackState() == Player.STATE_READY
                            && h.player.getPlayWhenReady()) {
                        int baseKb = (currentSubtype == 0) ? 215 : 98;
                        int jitter = (int) ((System.currentTimeMillis() / 1000 + h.index * 19) % 36);
                        h.labelTopRightBitrate.setText((baseKb + jitter) + " KB/s");
                        h.labelTopRightBitrate.setVisibility(View.VISIBLE);
                    }
                }
                mainHandler.postDelayed(this, 1500);
            }
        }
    };

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
            window.setNavigationBarColor(Color.parseColor(COLOR_BG_HEADER));

            setContentView(R.layout.activity_main);
            rootContainer = findViewById(R.id.rootContainer);
            rootContainer.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));

            try {
                accountChooserLauncher = registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                String email = result.getData().getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                                if (email != null && !email.isEmpty() && email.contains("@")) {
                                    verifyGoogleEmailOnServer(email, email.split("@")[0]);
                                    return;
                                }
                            }
                            promptGoogleEmailModal();
                        }
                );

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

            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            authToken = prefs.getString("token", "");
            currentUserName = prefs.getString("userName", "");
            currentUserEmail = prefs.getString("userEmail", "");
            currentUserRole = prefs.getString("userRole", "");
            isHomeCompactMode = prefs.getBoolean(KEY_COMPACT_HOME, false);
            currentSubtype = prefs.getInt("default_subtype", 1);

            logAppEvent("Sistema Limen Seguridad activo");

            if (authToken != null && !authToken.isEmpty()) {
                loadCamerasAndOpenHome();
            } else {
                showLoginScreen();
            }
        } catch (Throwable t) {
            reportCrashToServer("onCreate error", t);
        }
    }

    // Helper centralizado para mantener la fuente 100% uniforme en toda la aplicación
    private void styleText(TextView tv, float sizeSp, String colorHex, boolean isMedium) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTypeface(isMedium ? FONT_MEDIUM : FONT_REGULAR);
        tv.setIncludeFontPadding(false);
        tv.setLetterSpacing(0.01f);
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
    // 1. PANTALLA DE INICIO DE SESIÓN (ESTÉTICA Y TIPOGRAFÍA UNIFORME LIMEN)
    // =========================================================================
    private void showLoginScreen() {
        releaseAllPlayers();
        exitLandscapeIfNeeded();
        rootContainer.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(dp(28), dp(56), dp(28), dp(32));

        FrameLayout shieldBadge = new FrameLayout(this);
        shieldBadge.setBackground(roundBg(COLOR_BG_CARD, 20, COLOR_BORDER_DARK, 1));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(64), dp(64));
        badgeLp.bottomMargin = dp(16);
        ImageView shieldIcon = new ImageView(this);
        shieldIcon.setImageResource(R.drawable.limen_logo);
        shieldIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        shieldBadge.addView(shieldIcon, new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER));
        container.addView(shieldBadge, badgeLp);

        TextView brandTag = new TextView(this);
        brandTag.setText("LIMEN SEGURIDAD");
        styleText(brandTag, 11, COLOR_ACCENT_CYAN, true);
        brandTag.setLetterSpacing(0.12f);
        container.addView(brandTag);

        TextView title = new TextView(this);
        title.setText("Iniciar sesión");
        styleText(title, 22, COLOR_TEXT_WHITE, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleLp.topMargin = dp(6);
        titleLp.bottomMargin = dp(6);
        container.addView(title, titleLp);

        TextView subTitle = new TextView(this);
        subTitle.setText("Monitoreo profesional de videovigilancia privada y control perimetral");
        styleText(subTitle, 13, COLOR_TEXT_GRAY, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subLp.bottomMargin = dp(30);
        container.addView(subTitle, subLp);

        statusErrorText = new TextView(this);
        styleText(statusErrorText, 13, "#F87171", false);
        statusErrorText.setBackground(roundBg("#2A161B", 10, "#EF4444", 1));
        statusErrorText.setPadding(dp(14), dp(10), dp(14), dp(10));
        statusErrorText.setVisibility(View.GONE);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        errLp.bottomMargin = dp(18);
        container.addView(statusErrorText, errLp);

        // Campo Usuario / Email
        FrameLayout userWrap = new FrameLayout(this);
        userWrap.setBackground(roundBg(COLOR_BG_CARD, 12, COLOR_BORDER_DARK, 1));
        LinearLayout.LayoutParams userWrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        userWrapLp.bottomMargin = dp(14);

        EditText inputUser = new EditText(this);
        inputUser.setHint("Correo electrónico o usuario");
        inputUser.setHintTextColor(Color.parseColor(COLOR_TEXT_MUTED));
        styleText(inputUser, 14, COLOR_TEXT_WHITE, false);
        inputUser.setSingleLine(true);
        inputUser.setBackgroundColor(Color.TRANSPARENT);
        inputUser.setPadding(dp(16), 0, dp(48), 0);
        inputUser.setGravity(Gravity.CENTER_VERTICAL);
        userWrap.addView(inputUser, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout clearBtnWrap = new FrameLayout(this);
        clearBtnWrap.setBackground(circleBg(COLOR_BG_ELEVATED, null, 0));
        LimenIcons.IconView clearIcon = new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COLOR_TEXT_GRAY);
        clearBtnWrap.addView(clearIcon, new FrameLayout.LayoutParams(dp(13), dp(13), Gravity.CENTER));
        FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.END | Gravity.CENTER_VERTICAL);
        clearLp.rightMargin = dp(14);
        clearBtnWrap.setOnClickListener(v -> inputUser.setText(""));
        userWrap.addView(clearBtnWrap, clearLp);
        container.addView(userWrap, userWrapLp);

        // Campo Contraseña
        FrameLayout passWrap = new FrameLayout(this);
        passWrap.setBackground(roundBg(COLOR_BG_CARD, 12, COLOR_BORDER_DARK, 1));
        LinearLayout.LayoutParams passWrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        passWrapLp.bottomMargin = dp(18);

        EditText inputPass = new EditText(this);
        inputPass.setHint("Contraseña");
        inputPass.setHintTextColor(Color.parseColor(COLOR_TEXT_MUTED));
        styleText(inputPass, 14, COLOR_TEXT_WHITE, false);
        inputPass.setSingleLine(true);
        inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputPass.setBackgroundColor(Color.TRANSPARENT);
        inputPass.setPadding(dp(16), 0, dp(54), 0);
        inputPass.setGravity(Gravity.CENTER_VERTICAL);
        passWrap.addView(inputPass, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LimenIcons.IconView eyeIcon = new LimenIcons.IconView(this, LimenIcons.ICON_EYE, COLOR_TEXT_GRAY);
        FrameLayout.LayoutParams eyeLp = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.END | Gravity.CENTER_VERTICAL);
        eyeLp.rightMargin = dp(16);
        final boolean[] passVisible = {false};
        eyeIcon.setOnClickListener(v -> {
            passVisible[0] = !passVisible[0];
            eyeIcon.setIconColor(passVisible[0] ? COLOR_ACCENT_CYAN : COLOR_TEXT_GRAY);
            if (passVisible[0]) {
                inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            inputPass.setTypeface(FONT_REGULAR);
            inputPass.setSelection(inputPass.getText().length());
        });
        passWrap.addView(eyeIcon, eyeLp);
        container.addView(passWrap, passWrapLp);

        Button btnLogin = new Button(this);
        btnLogin.setText("Iniciar sesión");
        styleText(btnLogin, 15, "#FFFFFF", true);
        btnLogin.setAllCaps(false);
        btnLogin.setBackground(roundBg(COLOR_BTN_BLUE, 12, null, 0));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        );
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

        LinearLayout divRow = new LinearLayout(this);
        divRow.setOrientation(LinearLayout.HORIZONTAL);
        divRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        divLp.bottomMargin = dp(18);

        View line1 = new View(this);
        line1.setBackgroundColor(Color.parseColor(COLOR_BORDER_DARK));
        divRow.addView(line1, new LinearLayout.LayoutParams(0, dp(1), 1f));

        TextView divText = new TextView(this);
        divText.setText("  Iniciar sesión con  ");
        styleText(divText, 12, COLOR_TEXT_GRAY, false);
        divRow.addView(divText);

        View line2 = new View(this);
        line2.setBackgroundColor(Color.parseColor(COLOR_BORDER_DARK));
        divRow.addView(line2, new LinearLayout.LayoutParams(0, dp(1), 1f));
        container.addView(divRow, divLp);

        LinearLayout googleBtn = new LinearLayout(this);
        googleBtn.setOrientation(LinearLayout.HORIZONTAL);
        googleBtn.setGravity(Gravity.CENTER);
        googleBtn.setPadding(dp(20), dp(13), dp(20), dp(13));
        googleBtn.setBackground(roundBg(COLOR_BG_CARD, 12, COLOR_BORDER_DARK, 1));

        TextView gIcon = new TextView(this);
        gIcon.setText("G");
        styleText(gIcon, 16, COLOR_ACCENT_CYAN, true);
        gIcon.setPadding(0, 0, dp(10), 0);
        googleBtn.addView(gIcon);

        TextView gLabel = new TextView(this);
        gLabel.setText("Continuar con cuenta de Google");
        styleText(gLabel, 13, COLOR_TEXT_WHITE, true);
        googleBtn.addView(gLabel);
        googleBtn.setOnClickListener(v -> startNativeGoogleSignIn(inputUser.getText().toString().trim()));
        container.addView(googleBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout regRow = new LinearLayout(this);
        regRow.setOrientation(LinearLayout.HORIZONTAL);
        regRow.setGravity(Gravity.CENTER);
        regRow.setPadding(0, dp(18), 0, 0);

        TextView noAccText = new TextView(this);
        noAccText.setText("\u00bfNo tienes una cuenta? ");
        styleText(noAccText, 13, COLOR_TEXT_GRAY, false);
        regRow.addView(noAccText);

        TextView regBtn = new TextView(this);
        regBtn.setText("Registrarse");
        styleText(regBtn, 13, COLOR_ACCENT_CYAN, true);
        regBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        regBtn.setOnClickListener(v -> promptRegisterModal());
        regRow.addView(regBtn);

        container.addView(regRow);

        TextView legalFooter = new TextView(this);
        legalFooter.setText("LIMEN EYES v" + APP_VERSION_NAME + " \u00b7 Desarrollada por LIMEN SEGURIDAD\n\u00a9 2026 Limen Seguridad \u00b7 Monitoreo y Auditor\u00eda Perimetral");
        styleText(legalFooter, 11, COLOR_TEXT_MUTED, false);
        legalFooter.setGravity(Gravity.CENTER);
        legalFooter.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams lfLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lfLp.topMargin = dp(32);
        container.addView(legalFooter, lfLp);

        scroll.addView(container);
        rootContainer.addView(scroll);
    }

    private void promptRegisterModal() {
        final Dialog dialog = createCustomLimenDialog();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(22), dp(20), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.limen_logo);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(26), dp(26));
        iLp.rightMargin = dp(10);
        head.addView(icon, iLp);

        TextView title = new TextView(this);
        title.setText("Registrarse en Limen Eyes");
        styleText(title, 16, COLOR_TEXT_WHITE, true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout closeBtn = new FrameLayout(this);
        closeBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        closeBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COLOR_TEXT_GRAY),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        head.addView(closeBtn);
        card.addView(head);

        TextView desc = new TextView(this);
        desc.setText("Crea tu cuenta o solicita la vinculaci\u00f3n de tus c\u00e1maras utilizando tu cuenta de Google. Al registrarte, la administraci\u00f3n de Limen Seguridad autorizar\u00e1 tu acceso.");
        styleText(desc, 13, COLOR_TEXT_GRAY, false);
        desc.setLineSpacing(dp(2), 1.15f);
        desc.setPadding(0, 0, 0, dp(18));
        card.addView(desc);

        LinearLayout googleRegBtn = new LinearLayout(this);
        googleRegBtn.setOrientation(LinearLayout.HORIZONTAL);
        googleRegBtn.setGravity(Gravity.CENTER);
        googleRegBtn.setPadding(dp(16), dp(13), dp(16), dp(13));
        googleRegBtn.setBackground(roundBg(COLOR_BTN_BLUE, 12, null, 0));

        TextView gIcon = new TextView(this);
        gIcon.setText("G");
        styleText(gIcon, 17, COLOR_TEXT_WHITE, true);
        gIcon.setPadding(0, 0, dp(10), 0);
        googleRegBtn.addView(gIcon);

        TextView gLabel = new TextView(this);
        gLabel.setText("Registrarse con Google");
        styleText(gLabel, 14, COLOR_TEXT_WHITE, true);
        googleRegBtn.addView(gLabel);

        googleRegBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startNativeGoogleSignIn("");
        });
        LinearLayout.LayoutParams grLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        grLp.bottomMargin = dp(12);
        card.addView(googleRegBtn, grLp);

        TextView infoNote = new TextView(this);
        infoNote.setText("\u26a1 Se enviar\u00e1 una solicitud al Centro de Control para vincular tus grabadores DVR/NVR.");
        styleText(infoNote, 11, COLOR_TEXT_MUTED, false);
        infoNote.setGravity(Gravity.CENTER);
        card.addView(infoNote);

        dialog.setContentView(card);
        dialog.show();
    }

    private void showAccessRequestPendingModal(String email, String message) {
        mainHandler.post(() -> {
            final Dialog dialog = createCustomLimenDialog();

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
            card.setPadding(dp(22), dp(20), dp(22), dp(18));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(0, 0, 0, dp(12));

            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.limen_logo);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(26), dp(26));
            iLp.rightMargin = dp(10);
            head.addView(icon, iLp);

            TextView title = new TextView(this);
            title.setText("Solicitud de acceso enviada");
            styleText(title, 16, COLOR_ONLINE, true);
            head.addView(title);
            card.addView(head);

            TextView body = new TextView(this);
            body.setText(message != null && !message.isEmpty() ? message :
                    "Tu cuenta de Google (" + email + ") ha sido registrada con \u00e9xito.\n\nEl Centro de Control de Limen Seguridad recibi\u00f3 tu solicitud para vincular tus dispositivos a la brevedad.");
            styleText(body, 13, COLOR_TEXT_WHITE, false);
            body.setLineSpacing(dp(2), 1.2f);
            body.setPadding(0, 0, 0, dp(20));
            card.addView(body);

            Button btnOk = new Button(this);
            btnOk.setText("Entendido");
            styleText(btnOk, 13, COLOR_TEXT_WHITE, true);
            btnOk.setAllCaps(false);
            btnOk.setBackground(roundBg(COLOR_BTN_BLUE, 10, null, 0));
            btnOk.setOnClickListener(v -> dialog.dismiss());
            card.addView(btnOk, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

            dialog.setContentView(card);
            dialog.show();
        });
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
            Intent intent = AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    new String[]{"com.google"},
                    false,
                    null,
                    null,
                    null,
                    null
            );
            accountChooserLauncher.launch(intent);
        } catch (Throwable t) {
            Log.w(TAG, "AccountManager chooser fallback", t);
            try {
                if (googleSignInClient != null && googleSignInLauncher != null) {
                    googleSignInClient.signOut().addOnCompleteListener(task -> {
                        Intent signInIntent = googleSignInClient.getSignInIntent();
                        googleSignInLauncher.launch(signInIntent);
                    });
                } else if (!typedEmailFallback.isEmpty() && typedEmailFallback.contains("@")) {
                    verifyGoogleEmailOnServer(typedEmailFallback, typedEmailFallback.split("@")[0]);
                } else {
                    promptGoogleEmailModal();
                }
            } catch (Throwable t2) {
                promptGoogleEmailModal();
            }
        }
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getEmail() != null) {
                verifyGoogleEmailOnServer(account.getEmail(), account.getDisplayName());
            } else {
                promptGoogleEmailModal();
            }
        } catch (ApiException e) {
            promptGoogleEmailModal();
        }
    }

    private void promptGoogleEmailModal() {
        final Dialog dialog = createCustomLimenDialog();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(20), dp(20), dp(20), dp(16));

        TextView title = new TextView(this);
        title.setText("Iniciar sesión con Google");
        styleText(title, 16, COLOR_TEXT_WHITE, true);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Ingresa tu cuenta de correo autorizada en Limen Seguridad:");
        styleText(sub, 12, COLOR_TEXT_GRAY, false);
        sub.setPadding(0, dp(6), 0, dp(14));
        card.addView(sub);

        EditText emailInput = new EditText(this);
        emailInput.setHint("tu-correo@gmail.com");
        emailInput.setHintTextColor(Color.parseColor(COLOR_TEXT_MUTED));
        styleText(emailInput, 14, COLOR_TEXT_WHITE, false);
        emailInput.setSingleLine(true);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setBackground(roundBg(COLOR_BG_ELEVATED, 10, COLOR_BORDER_DARK, 1));
        emailInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(emailInput);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setPadding(0, dp(16), 0, 0);

        TextView cancelBtn = new TextView(this);
        cancelBtn.setText("Cancelar");
        styleText(cancelBtn, 13, COLOR_TEXT_GRAY, true);
        cancelBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(cancelBtn);

        TextView okBtn = new TextView(this);
        okBtn.setText("Continuar");
        styleText(okBtn, 13, COLOR_ACCENT_CYAN, true);
        okBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        okBtn.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            dialog.dismiss();
            if (!email.isEmpty()) {
                verifyGoogleEmailOnServer(email, email.split("@")[0]);
            }
        });
        btnRow.addView(okBtn);
        card.addView(btnRow);

        dialog.setContentView(card);
        dialog.show();
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
                } else if (resp.code == 403 && (json.optString("error").contains("autorizaci") || json.optString("message").contains("registrada") || json.optString("message").contains("solicitud"))) {
                    showAccessRequestPendingModal(email, json.optString("message"));
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

        prefs.edit()
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
        prefs.edit().remove("token").apply();
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
                    logAppEvent("Sincronizados " + allCameras.size() + " canales en línea");
                    checkForUpdates(false);
                    if (currentTab == 0) showHomeScreen();
                    checkPendingInvitations();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error obteniendo cámaras", e);
            }
        });
    }

    // =========================================================================
    // 2. PANTALLA PRINCIPAL "INICIO" (MODO TARJETAS 16:9 Y MODO LISTA COMPACTA)
    // =========================================================================
    private void showHomeScreen() {
        currentTab = 0;
        releaseAllPlayers();
        exitLandscapeIfNeeded();
        rootContainer.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // ── HEADER SUPERIOR: Alternar Lista Compacta / Tarjetas | "Inicio" | Refrescar ──
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.parseColor(COLOR_BG_HEADER));
        header.setPadding(dp(16), dp(13), dp(16), dp(12));

        int modeIcon = isHomeCompactMode ? LimenIcons.ICON_GRID_4 : LimenIcons.ICON_LIST_MODE;
        LimenIcons.IconView btnToggleMode = new LimenIcons.IconView(this, modeIcon, COLOR_TEXT_WHITE);
        btnToggleMode.setOnClickListener(v -> {
            isHomeCompactMode = !isHomeCompactMode;
            prefs.edit().putBoolean(KEY_COMPACT_HOME, isHomeCompactMode).apply();
            showHomeScreen();
        });
        header.addView(btnToggleMode, new LinearLayout.LayoutParams(dp(23), dp(23)));

        LinearLayout titleLockup = new LinearLayout(this);
        titleLockup.setOrientation(LinearLayout.HORIZONTAL);
        titleLockup.setGravity(Gravity.CENTER);

        ImageView brandLogo = new ImageView(this);
        brandLogo.setImageResource(R.drawable.limen_logo);
        brandLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams blLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        blLp.rightMargin = dp(8);
        titleLockup.addView(brandLogo, blLp);

        TextView headerTitle = new TextView(this);
        headerTitle.setText("LIMEN EYES");
        styleText(headerTitle, 16, COLOR_TEXT_WHITE, true);
        headerTitle.setLetterSpacing(0.06f);
        titleLockup.addView(headerTitle);

        header.addView(titleLockup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LimenIcons.IconView btnRefresh = new LimenIcons.IconView(this, LimenIcons.ICON_REFRESH, COLOR_TEXT_WHITE);
        btnRefresh.setOnClickListener(v -> loadCamerasAndOpenHome());
        header.addView(btnRefresh, new LinearLayout.LayoutParams(dp(21), dp(21)));

        page.addView(header);

        // ── CONTENIDO SCROLLABLE ──
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(20));

        // Barra de búsqueda
        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(roundBg(COLOR_BG_CARD, 12, COLOR_BORDER_DARK, 1));
        searchBox.setPadding(dp(14), dp(2), dp(14), dp(2));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)
        );
        searchLp.bottomMargin = dp(14);

        LimenIcons.IconView searchIcon = new LimenIcons.IconView(this, LimenIcons.ICON_SEARCH, COLOR_TEXT_GRAY);
        searchBox.addView(searchIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));

        EditText searchInput = new EditText(this);
        searchInput.setHint("Buscar dispositivo o cámara...");
        searchInput.setHintTextColor(Color.parseColor(COLOR_TEXT_MUTED));
        styleText(searchInput, 13, COLOR_TEXT_WHITE, false);
        searchInput.setSingleLine(true);
        searchInput.setText(searchQuery);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setPadding(dp(10), 0, dp(6), 0);
        searchBox.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        content.addView(searchBox, searchLp);

        // Barra de accesos rápidos Limen Seguridad
        LinearLayout quickCard = new LinearLayout(this);
        quickCard.setOrientation(LinearLayout.HORIZONTAL);
        quickCard.setBackground(roundBg(COLOR_BG_CARD, 14, COLOR_BORDER_DARK, 1));
        quickCard.setPadding(dp(8), dp(14), dp(8), dp(14));
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        quickLp.bottomMargin = dp(14);

        quickCard.addView(createQuickActionItem(LimenIcons.ICON_VIDEO, "#4F46E5", "Vista previa", () -> showLiveViewScreen(null, null, null)));
        quickCard.addView(createQuickActionItem(LimenIcons.ICON_GRID_4, "#6366F1", "Mosaico 4CH", () -> {
            currentGridMode = 4;
            showLiveViewScreen(null, null, null);
        }));
        quickCard.addView(createQuickActionItem(LimenIcons.ICON_GRID_9, "#7C3AED", "Mosaico 9CH", () -> {
            currentGridMode = 9;
            showLiveViewScreen(null, null, null);
        }));
        quickCard.addView(createQuickActionItem(LimenIcons.ICON_SERVER, "#312E81", "Equipos", this::showDeviceManagerScreen));

        content.addView(quickCard, quickLp);

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

        page.addView(buildBottomNavBar());
        rootContainer.addView(page);
    }

    private View createQuickActionItem(int iconType, String colorHex, String label, Runnable onClick) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.setOnClickListener(v -> onClick.run());

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(roundBg(colorHex, 14, null, 0));
        LimenIcons.IconView iv = new LimenIcons.IconView(this, iconType, "#FFFFFF");
        badge.addView(iv, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));

        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        icLp.bottomMargin = dp(6);
        col.addView(badge, icLp);

        TextView txt = new TextView(this);
        txt.setText(label);
        styleText(txt, 11, COLOR_TEXT_GRAY, true);
        txt.setSingleLine(true);
        txt.setGravity(Gravity.CENTER);
        col.addView(txt);

        return col;
    }

    private Map<String, List<CameraItem>> getGroupedCameras(String filter) {
        Map<String, List<CameraItem>> groups = new LinkedHashMap<>();
        String q = filter != null ? filter.toLowerCase(Locale.ROOT) : "";

        for (CameraItem cam : allCameras) {
            String gName = cam.getGroupTitle();
            if (!q.isEmpty()) {
                boolean match = gName.toLowerCase(Locale.ROOT).contains(q)
                        || (cam.nombre != null && cam.nombre.toLowerCase(Locale.ROOT).contains(q))
                        || (cam.sn != null && cam.sn.toLowerCase(Locale.ROOT).contains(q));
                if (!match) continue;
            }
            if (!groups.containsKey(gName)) {
                groups.put(gName, new ArrayList<>());
            }
            groups.get(gName).add(cam);
        }
        return groups;
    }

    private void populateHomeDeviceCards(LinearLayout devicesContainer, String filter) {
        devicesContainer.removeAllViews();

        if (allCameras.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Sincronizando equipos autorizados...");
            styleText(empty, 13, COLOR_TEXT_GRAY, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(40), dp(20), dp(40));
            devicesContainer.addView(empty);
            return;
        }

        Map<String, List<CameraItem>> groups = getGroupedCameras(filter);

        // Si está activo el Modo Lista Compacta (Imagen 1 de EasyViewer)
        if (isHomeCompactMode) {
            LinearLayout compactCard = new LinearLayout(this);
            compactCard.setOrientation(LinearLayout.VERTICAL);
            compactCard.setBackground(roundBg(COLOR_BG_CARD, 14, COLOR_BORDER_DARK, 1));
            compactCard.setPadding(dp(14), dp(6), dp(14), dp(6));

            int idx = 0;
            for (Map.Entry<String, List<CameraItem>> entry : groups.entrySet()) {
                if (idx > 0) {
                    View div = new View(this);
                    div.setBackgroundColor(Color.parseColor(COLOR_BORDER_DARK));
                    compactCard.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
                }
                compactCard.addView(buildCompactDeviceRow(entry.getKey(), entry.getValue()));
                idx++;
            }
            devicesContainer.addView(compactCard, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int thumbWidth = (screenWidth - dp(68)) / 2;
        int thumbHeight = (thumbWidth * 9) / 16; // Relación 16:9

        for (Map.Entry<String, List<CameraItem>> entry : groups.entrySet()) {
            final String groupTitle = entry.getKey();
            final List<CameraItem> cams = entry.getValue();

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBg(COLOR_BG_CARD, 14, COLOR_BORDER_DARK, 1));
            card.setPadding(dp(14), dp(14), dp(14), dp(12));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardLp.bottomMargin = dp(14);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams topRowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            topRowLp.bottomMargin = dp(10);

            TextView titleTv = new TextView(this);
            titleTv.setText(groupTitle);
            styleText(titleTv, 15, COLOR_TEXT_WHITE, true);
            topRow.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Botón Play circular
            FrameLayout playGroupBtn = new FrameLayout(this);
            playGroupBtn.setBackground(circleBg(COLOR_BG_ELEVATED, COLOR_TEXT_GRAY, 1));
            LimenIcons.IconView playIcon = new LimenIcons.IconView(this, LimenIcons.ICON_PLAY, COLOR_TEXT_WHITE);
            playGroupBtn.addView(playIcon, new FrameLayout.LayoutParams(dp(13), dp(13), Gravity.CENTER));

            LinearLayout.LayoutParams playBtnLp = new LinearLayout.LayoutParams(dp(28), dp(28));
            playBtnLp.rightMargin = dp(10);
            playGroupBtn.setOnClickListener(v -> {
                currentGridMode = cams.size() > 4 ? 9 : (cams.size() > 1 ? 4 : 1);
                showLiveViewScreen(null, cams, groupTitle);
            });
            topRow.addView(playGroupBtn, playBtnLp);

            // Botón 3 puntos (•••) -> Abre el menú flotante con "Detalles del dispositivo" (Imagen 3 -> Imagen 2)
            FrameLayout moreWrap = new FrameLayout(this);
            moreWrap.setPadding(dp(6), dp(6), dp(6), dp(6));
            LimenIcons.IconView moreBtn = new LimenIcons.IconView(this, LimenIcons.ICON_MORE_H, COLOR_TEXT_GRAY);
            moreWrap.addView(moreBtn, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            moreWrap.setOnClickListener(v -> showDeviceOverflowPopup(groupTitle, cams));
            topRow.addView(moreWrap);

            card.addView(topRow, topRowLp);

            HorizontalScrollView thumbScroll = new HorizontalScrollView(this);
            thumbScroll.setHorizontalScrollBarEnabled(false);

            LinearLayout thumbRow = new LinearLayout(this);
            thumbRow.setOrientation(LinearLayout.HORIZONTAL);

            for (int i = 0; i < cams.size(); i++) {
                final CameraItem cam = cams.get(i);

                FrameLayout thumbCard = new FrameLayout(this);
                thumbCard.setBackground(roundBg(COLOR_BG_MAIN, 10, COLOR_BORDER_DARK, 1));
                LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(thumbWidth, thumbHeight);
                if (i < cams.size() - 1) tcLp.rightMargin = dp(10);
                thumbCard.setLayoutParams(tcLp);

                ImageView img = new ImageView(this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumbCard.addView(img, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ));

                loadCameraThumbnailAsync(cam.id, img);

                TextView camLabel = new TextView(this);
                camLabel.setText(cam.getShortChannelLabel());
                styleText(camLabel, 11, "#FFFFFF", true);
                camLabel.setSingleLine(true);
                camLabel.setEllipsize(TextUtils.TruncateAt.END);
                camLabel.setBackgroundColor(Color.parseColor("#99090D16"));
                camLabel.setPadding(dp(8), dp(4), dp(8), dp(4));
                FrameLayout.LayoutParams clLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
                );
                thumbCard.addView(camLabel, clLp);

                thumbCard.setOnClickListener(v -> {
                    currentGridMode = 1;
                    showLiveViewScreen(cam, null, groupTitle);
                });

                thumbRow.addView(thumbCard);
            }

            thumbScroll.addView(thumbRow);
            card.addView(thumbScroll);

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
            devSub.setText("Dispositivo vinculado · " + cams.size() + (cams.size() == 1 ? " canal" : " canales"));
            styleText(devSub, 12, COLOR_TEXT_GRAY, false);
            footerRow.addView(devSub);

            card.addView(footerRow, footLp);
            devicesContainer.addView(card, cardLp);
        }
    }

    private View buildCompactDeviceRow(final String groupTitle, final List<CameraItem> cams) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(2), dp(12), dp(2), dp(12));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LimenIcons.IconView dvrIcon = new LimenIcons.IconView(this, LimenIcons.ICON_DVR_BOX, COLOR_TEXT_WHITE);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        dLp.rightMargin = dp(10);
        topRow.addView(dvrIcon, dLp);

        TextView titleTv = new TextView(this);
        titleTv.setText(groupTitle);
        styleText(titleTv, 15, COLOR_TEXT_WHITE, true);
        topRow.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout playGroupBtn = new FrameLayout(this);
        playGroupBtn.setBackground(circleBg(COLOR_BG_ELEVATED, COLOR_TEXT_GRAY, 1));
        LimenIcons.IconView playIcon = new LimenIcons.IconView(this, LimenIcons.ICON_PLAY, COLOR_TEXT_WHITE);
        playGroupBtn.addView(playIcon, new FrameLayout.LayoutParams(dp(13), dp(13), Gravity.CENTER));
        LinearLayout.LayoutParams playBtnLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        playBtnLp.rightMargin = dp(10);
        playGroupBtn.setOnClickListener(v -> {
            currentGridMode = cams.size() > 4 ? 9 : (cams.size() > 1 ? 4 : 1);
            showLiveViewScreen(null, cams, groupTitle);
        });
        topRow.addView(playGroupBtn, playBtnLp);

        FrameLayout moreWrap = new FrameLayout(this);
        moreWrap.setPadding(dp(6), dp(6), dp(6), dp(6));
        moreWrap.addView(new LimenIcons.IconView(this, LimenIcons.ICON_MORE_H, COLOR_TEXT_GRAY),
                new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        moreWrap.setOnClickListener(v -> showDeviceOverflowPopup(groupTitle, cams));
        topRow.addView(moreWrap);

        row.addView(topRow);

        LinearLayout footerRow = new LinearLayout(this);
        footerRow.setOrientation(LinearLayout.HORIZONTAL);
        footerRow.setGravity(Gravity.CENTER_VERTICAL);
        footerRow.setPadding(0, dp(6), 0, 0);

        View blueDot = new View(this);
        blueDot.setBackground(circleBg(COLOR_ACCENT_BLUE, null, 0));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(8);
        footerRow.addView(blueDot, dotLp);

        TextView devSub = new TextView(this);
        devSub.setText("Dispositivo vinculado · " + cams.size() + (cams.size() == 1 ? " canal" : " canales"));
        styleText(devSub, 12, COLOR_TEXT_GRAY, false);
        footerRow.addView(devSub);

        row.addView(footerRow);
        row.setOnClickListener(v -> {
            currentGridMode = cams.size() > 4 ? 9 : (cams.size() > 1 ? 4 : 1);
            showLiveViewScreen(null, cams, groupTitle);
        });
        return row;
    }

    // =========================================================================
    // 3. MENÚ DE 3 PUNTOS (•••) Y PANTALLA "DETALLES DEL DISPOSITIVO" (IMG 2 y 3)
    // =========================================================================
    private void showDeviceOverflowPopup(String groupTitle, List<CameraItem> cams) {
        final Dialog dialog = createCustomLimenDialog();

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        sheet.setPadding(dp(18), dp(18), dp(18), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        LimenIcons.IconView dvrIc = new LimenIcons.IconView(this, LimenIcons.ICON_DVR_BOX, COLOR_ACCENT_CYAN);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        dLp.rightMargin = dp(10);
        header.addView(dvrIc, dLp);

        TextView title = new TextView(this);
        title.setText(groupTitle);
        styleText(title, 15, COLOR_TEXT_WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout closeBtn = new FrameLayout(this);
        closeBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        closeBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COLOR_TEXT_GRAY),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        sheet.addView(header);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(COLOR_BORDER_DARK));
        sheet.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 0. Compartir dispositivo
        sheet.addView(createModalActionRow(
                LimenIcons.ICON_USER,
                "Compartir dispositivo",
                "Invitar a otro usuario a ver este equipo con permisos específicos",
                COLOR_ACCENT_CYAN,
                v -> {
                    dialog.dismiss();
                    promptShareDeviceModal(groupTitle, cams);
                }));

        // 1. Detalles del dispositivo (Como en Imagen 3 -> lleva a Imagen 2)
        sheet.addView(createModalActionRow(
                LimenIcons.ICON_DOC_DETAILS,
                "Detalles del dispositivo",
                "Modelo, SN, suscripción de alarma y transmisión",
                COLOR_ACCENT_CYAN,
                v -> {
                    dialog.dismiss();
                    showDeviceDetailsScreen(groupTitle, cams);
                }));

        // 2. Abrir en mosaico 4 canales
        sheet.addView(createModalActionRow(
                LimenIcons.ICON_GRID_4,
                "Abrir en vista de 4 canales",
                "Reproducir cámaras de " + groupTitle + " en 2×2",
                COLOR_TEXT_WHITE,
                v -> {
                    dialog.dismiss();
                    currentGridMode = 4;
                    showLiveViewScreen(null, cams, groupTitle);
                }));

        // 3. Abrir en mosaico 9 canales
        sheet.addView(createModalActionRow(
                LimenIcons.ICON_GRID_9,
                "Abrir en vista de 9 canales",
                "Reproducir cámaras de " + groupTitle + " en 3×3",
                COLOR_TEXT_WHITE,
                v -> {
                    dialog.dismiss();
                    currentGridMode = 9;
                    showLiveViewScreen(null, cams, groupTitle);
                }));

        dialog.setContentView(sheet);
        dialog.show();
    }

    private View createModalActionRow(int iconType, String titleText, String subText, String iconColor, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(13), dp(4), dp(13));
        row.setOnClickListener(listener);

        FrameLayout icBox = new FrameLayout(this);
        icBox.setBackground(roundBg(COLOR_BG_ELEVATED, 10, COLOR_BORDER_DARK, 1));
        icBox.addView(new LimenIcons.IconView(this, iconType, iconColor),
                new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        icLp.rightMargin = dp(12);
        row.addView(icBox, icLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(titleText);
        styleText(t1, 14, COLOR_TEXT_WHITE, true);
        texts.addView(t1);

        if (subText != null && !subText.isEmpty()) {
            TextView t2 = new TextView(this);
            t2.setText(subText);
            styleText(t2, 11, COLOR_TEXT_GRAY, false);
            t2.setPadding(0, dp(2), 0, 0);
            texts.addView(t2);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_R, COLOR_TEXT_MUTED),
                new LinearLayout.LayoutParams(dp(16), dp(16)));
        return row;
    }

    // Pantalla completa "Detalles del dispositivo" (Basada en Imagen 2 de EasyViewer con estética Limen Seguridad)
    private void showDeviceDetailsScreen(final String groupTitle, final List<CameraItem> cams) {
        releaseAllPlayers();
        exitLandscapeIfNeeded();
        rootContainer.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Top Bar: < | Detalles del dispositivo
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor(COLOR_BG_HEADER));
        topBar.setPadding(dp(12), dp(12), dp(16), dp(12));

        FrameLayout backBtn = new FrameLayout(this);
        backBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        backBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_BACK, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        backBtn.setOnClickListener(v -> showHomeScreen());
        topBar.addView(backBtn);

        TextView topTitle = new TextView(this);
        topTitle.setText("Detalles del dispositivo");
        styleText(topTitle, 16, COLOR_TEXT_WHITE, true);
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

        String deviceSn = (cams != null && !cams.isEmpty() && cams.get(0).sn != null && !cams.get(0).sn.isEmpty())
                ? cams.get(0).sn
                : "LIMEN-NET";
        String deviceBrand = (cams != null && !cams.isEmpty() && cams.get(0).marca != null && !cams.get(0).marca.isEmpty())
                ? cams.get(0).marca
                : "DVR / XVR";

        // 1. Cabecera con chasis DVR, Nombre, "En línea", Modelo y SN (Imagen 2)
        LinearLayout devHeaderCard = new LinearLayout(this);
        devHeaderCard.setOrientation(LinearLayout.HORIZONTAL);
        devHeaderCard.setGravity(Gravity.CENTER_VERTICAL);
        devHeaderCard.setBackgroundColor(Color.parseColor(COLOR_BG_CARD));
        devHeaderCard.setPadding(dp(18), dp(18), dp(18), dp(18));

        FrameLayout chassisBox = new FrameLayout(this);
        chassisBox.setBackground(roundBg(COLOR_BG_ELEVATED, 12, COLOR_BORDER_DARK, 1));
        chassisBox.addView(new LimenIcons.IconView(this, LimenIcons.ICON_DVR_BOX, COLOR_ACCENT_CYAN),
                new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(dp(72), dp(56));
        chLp.rightMargin = dp(16);
        devHeaderCard.addView(chassisBox, chLp);

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        TextView nameTv = new TextView(this);
        nameTv.setText(groupTitle);
        styleText(nameTv, 16, COLOR_TEXT_WHITE, true);
        infoCol.addView(nameTv);

        TextView onlineTv = new TextView(this);
        onlineTv.setText("En línea");
        styleText(onlineTv, 12, COLOR_ACCENT_CYAN, true);
        onlineTv.setPadding(0, dp(4), 0, dp(6));
        infoCol.addView(onlineTv);

        TextView modelTv = new TextView(this);
        modelTv.setText("Modelo dispos.: " + deviceBrand + " (" + (cams != null ? cams.size() : 0) + "CH)");
        styleText(modelTv, 12, COLOR_TEXT_GRAY, false);
        infoCol.addView(modelTv);

        TextView snTv = new TextView(this);
        snTv.setText("SN de dispositivo: " + deviceSn);
        styleText(snTv, 12, COLOR_TEXT_GRAY, false);
        snTv.setPadding(0, dp(2), 0, 0);
        infoCol.addView(snTv);

        devHeaderCard.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout editBtn = new FrameLayout(this);
        editBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        editBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_EDIT, COLOR_TEXT_GRAY),
                new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        editBtn.setOnClickListener(v -> {
            currentGridMode = (cams != null && cams.size() > 4) ? 9 : 4;
            showLiveViewScreen(null, cams, groupTitle);
        });
        devHeaderCard.addView(editBtn);

        body.addView(devHeaderCard);

        // Sección: Ajustes de alarma
        body.addView(buildDetailsSectionHeader("Ajustes de alarma"));

        final String alarmPrefKey = "alarm_sub_" + groupTitle;
        boolean isAlarmSub = prefs.getBoolean(alarmPrefKey, true);
        TextView alarmStateBadge = new TextView(this);
        alarmStateBadge.setText(isAlarmSub ? "Activado" : "Desactivado");
        styleText(alarmStateBadge, 12, isAlarmSub ? COLOR_ACCENT_CYAN : COLOR_TEXT_MUTED, true);

        body.addView(buildDetailsSettingRow(
                "Suscripción de alarma multicanal",
                "Active el interruptor de notificaciones para recibir los avisos correspondientes a todos los subcanales del dispositivo.",
                alarmStateBadge,
                v -> {
                    boolean next = !prefs.getBoolean(alarmPrefKey, true);
                    prefs.edit().putBoolean(alarmPrefKey, next).apply();
                    alarmStateBadge.setText(next ? "Activado" : "Desactivado");
                    alarmStateBadge.setTextColor(Color.parseColor(next ? COLOR_ACCENT_CYAN : COLOR_TEXT_MUTED));
                    logAppEvent("Suscripción de alarma " + (next ? "activada" : "desactivada") + " en " + groupTitle);
                }));

        LinearLayout infoNote = new LinearLayout(this);
        infoNote.setOrientation(LinearLayout.HORIZONTAL);
        infoNote.setPadding(dp(18), dp(10), dp(18), dp(6));
        TextView bang = new TextView(this);
        bang.setText("i");
        styleText(bang, 11, "#FFFFFF", true);
        bang.setGravity(Gravity.CENTER);
        bang.setBackground(circleBg(COLOR_ACCENT_BLUE, null, 0));
        LinearLayout.LayoutParams bangLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        bangLp.rightMargin = dp(8);
        bangLp.topMargin = dp(2);
        infoNote.addView(bang, bangLp);

        TextView noteTv = new TextView(this);
        noteTv.setText("Los enlaces de datos y túneles P2P son gestionados de forma segura por el nodo central de Limen Seguridad.");
        styleText(noteTv, 11, COLOR_TEXT_GRAY, false);
        infoNote.addView(noteTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(infoNote);

        // Sección: Configuración para la reproducción de los vídeos
        body.addView(buildDetailsSectionHeader("Configuración para la reproducción de los vídeos"));

        TextView streamQualityBadge = new TextView(this);
        streamQualityBadge.setText(currentSubtype == 0 ? "Flujo principal (HD)" : "Subflujo (SD)");
        styleText(streamQualityBadge, 12, COLOR_ACCENT_CYAN, true);

        body.addView(buildDetailsSettingRow(
                "Config. de transmisión",
                "Selecciona la calidad de video inicial al conectar con los canales de este grabador.",
                streamQualityBadge,
                v -> {
                    currentSubtype = (currentSubtype == 1) ? 0 : 1;
                    prefs.edit().putInt("default_subtype", currentSubtype).apply();
                    streamQualityBadge.setText(currentSubtype == 0 ? "Flujo principal (HD)" : "Subflujo (SD)");
                }));

        // Sección: Configuración general
        body.addView(buildDetailsSectionHeader("Configuración general"));

        TextView upnpBadge = new TextView(this);
        upnpBadge.setText("Activo");
        styleText(upnpBadge, 12, COLOR_ONLINE, true);

        body.addView(buildDetailsSettingRow(
                "UPnP / Transporte TCP directo",
                "Activado para mejorar la velocidad de transmisión y visualizar rápidamente los vídeos en directo.",
                upnpBadge,
                v -> Toast.makeText(this, "Optimización TCP/fMP4 activa", Toast.LENGTH_SHORT).show()));

        TextView ddnsBadge = new TextView(this);
        ddnsBadge.setText("Limen Cloud");
        styleText(ddnsBadge, 12, COLOR_TEXT_GRAY, false);

        body.addView(buildDetailsSettingRow(
                "DDNS / Enlace Limen Cloud",
                "Vincula automáticamente el dispositivo a la red de monitoreo Limen Seguridad sin abrir puertos.",
                ddnsBadge,
                v -> Toast.makeText(this, "Enlace activo: eyes.limenseguridad.com", Toast.LENGTH_SHORT).show()));

        // Sección: Otros
        body.addView(buildDetailsSectionHeader("Otros"));

        TextView chBadge = new TextView(this);
        chBadge.setText((cams != null ? cams.size() : 0) + " canales");
        styleText(chBadge, 12, COLOR_TEXT_GRAY, false);

        body.addView(buildDetailsSettingRow(
                "Configuración remota",
                "Inspeccionar los canales individuales del equipo y abrir vista en directo.",
                chBadge,
                v -> showCustomCameraPickerModal(groupTitle, cams, cam -> {
                    currentGridMode = 1;
                    showLiveViewScreen(cam, null, groupTitle);
                })));

        body.addView(buildDetailsSettingRow(
                "Salida de alarma y eventos",
                "Cuando ocurre un evento de conexión o alerta, queda registrado en el panel inferior de Vista en Vivo.",
                null,
                v -> {
                    currentGridMode = (cams != null && cams.size() > 4) ? 9 : 4;
                    showLiveViewScreen(null, cams, groupTitle);
                }));

        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rootContainer.addView(page);
    }

    private View buildDetailsSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        styleText(tv, 12, COLOR_TEXT_GRAY, true);
        tv.setPadding(dp(18), dp(18), dp(18), dp(8));
        return tv;
    }

    private View buildDetailsSettingRow(String title, String subtitle, TextView rightStatusView, View.OnClickListener listener) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(Color.parseColor(COLOR_BG_CARD));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        if (listener != null) row.setOnClickListener(listener);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(title);
        styleText(t1, 14, COLOR_TEXT_WHITE, true);
        textCol.addView(t1);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView t2 = new TextView(this);
            t2.setText(subtitle);
            styleText(t2, 12, COLOR_TEXT_GRAY, false);
            t2.setPadding(0, dp(4), 0, 0);
            textCol.addView(t2);
        }

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (rightStatusView != null) {
            rightStatusView.setPadding(dp(10), 0, dp(8), 0);
            row.addView(rightStatusView);
        }

        row.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_R, COLOR_TEXT_MUTED),
                new LinearLayout.LayoutParams(dp(16), dp(16)));

        wrap.addView(row);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(COLOR_BORDER_DARK));
        wrap.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        return wrap;
    }

    // =========================================================================
    // 4. VISOR EN VIVO (ESTÉTICA IMAGEN 4 EASYVIEWER + IDENTIDAD LIMEN SEGURIDAD)
    // =========================================================================
    private List<CameraItem> getCamerasForCurrentLiveFilter() {
        if (activeLiveGroupFilter == null || activeLiveGroupFilter.isEmpty()) {
            return new ArrayList<>(allCameras);
        }
        List<CameraItem> out = new ArrayList<>();
        for (CameraItem c : allCameras) {
            if (activeLiveGroupFilter.equalsIgnoreCase(c.getGroupTitle())) {
                out.add(c);
            }
        }
        return out.isEmpty() ? new ArrayList<>(allCameras) : out;
    }

    private void showLiveViewScreen(CameraItem singleCamToStart, List<CameraItem> groupCamsToStart, String groupFilter) {
        currentTab = 1;
        if (groupFilter != null) {
            activeLiveGroupFilter = groupFilter;
        } else if (singleCamToStart != null) {
            activeLiveGroupFilter = singleCamToStart.getGroupTitle();
        } else if (groupCamsToStart != null && !groupCamsToStart.isEmpty()) {
            activeLiveGroupFilter = groupCamsToStart.get(0).getGroupTitle();
        } else if (activeLiveGroupFilter == null && !allCameras.isEmpty()) {
            activeLiveGroupFilter = allCameras.get(0).getGroupTitle();
        }

        releaseAllPlayers();
        rootContainer.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // ── 1. BARRA SUPERIOR (Estilo Imagen 4: < | Nombre del Equipo | Hexágono Detalles) ──
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor(COLOR_BG_HEADER));
        topBar.setPadding(dp(12), dp(10), dp(14), dp(10));

        FrameLayout btnBackWrap = new FrameLayout(this);
        btnBackWrap.setPadding(dp(8), dp(6), dp(8), dp(6));
        btnBackWrap.addView(new LimenIcons.IconView(this, LimenIcons.ICON_BACK, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        btnBackWrap.setOnClickListener(v -> showHomeScreen());
        topBar.addView(btnBackWrap);

        LinearLayout centerTitleCol = new LinearLayout(this);
        centerTitleCol.setOrientation(LinearLayout.VERTICAL);
        centerTitleCol.setGravity(Gravity.CENTER);
        centerTitleCol.setOnClickListener(v -> showGroupFilterSwitchModal());

        liveHeaderTitle = new TextView(this);
        liveHeaderTitle.setText(activeLiveGroupFilter != null ? activeLiveGroupFilter : "Vista en directo");
        styleText(liveHeaderTitle, 16, COLOR_TEXT_WHITE, true);
        liveHeaderTitle.setGravity(Gravity.CENTER);
        centerTitleCol.addView(liveHeaderTitle);

        liveHeaderSubtitle = new TextView(this);
        liveHeaderSubtitle.setText("Toca para cambiar de equipo ▾");
        styleText(liveHeaderSubtitle, 10, COLOR_TEXT_GRAY, false);
        liveHeaderSubtitle.setGravity(Gravity.CENTER);
        centerTitleCol.addView(liveHeaderSubtitle);

        topBar.addView(centerTitleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout btnDetailsHex = new FrameLayout(this);
        btnDetailsHex.setPadding(dp(8), dp(6), dp(8), dp(6));
        btnDetailsHex.addView(new LimenIcons.IconView(this, LimenIcons.ICON_SETTINGS_HEX, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
        btnDetailsHex.setOnClickListener(v -> {
            List<CameraItem> currentCams = getCamerasForCurrentLiveFilter();
            String title = activeLiveGroupFilter != null ? activeLiveGroupFilter : "Dispositivo";
            showDeviceDetailsScreen(title, currentCams);
        });
        topBar.addView(btnDetailsHex);

        page.addView(topBar);

        // ── 2. ESCENARIO DE VIDEO (Soporta 1CH, 4CH y 9CH con overlays CAM X y KB/s) ──
        liveVideoStage = new FrameLayout(this);
        liveVideoStage.setBackgroundColor(Color.BLACK);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int stageHeight = (currentGridMode == 1)
                ? Math.round(screenWidth * 9f / 16f)
                : Math.round(screenWidth * 0.78f);
        page.addView(liveVideoStage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, stageHeight
        ));

        LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setGravity(Gravity.CENTER);
        liveVideoStage.addView(gridContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER
        ));

        gridRows.clear();
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            gridContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ));
            gridRows.add(row);
        }

        buildLiveCells9();

        // ── 3. BARRA FINA DE 5 ICONOS BAJO EL VIDEO (Como Imagen 4: Pausa | [SD/HD] | Audio | [1/4/9] | Pantalla Completa) ──
        LinearLayout controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER_VERTICAL);
        controlBar.setBackgroundColor(Color.parseColor(COLOR_BG_HEADER));
        controlBar.setPadding(dp(8), dp(10), dp(8), dp(10));

        // 3.1 Play / Pause
        FrameLayout btnPlayPause = new FrameLayout(this);
        playPauseIconView = new LimenIcons.IconView(this, LimenIcons.ICON_PAUSE, COLOR_TEXT_WHITE);
        btnPlayPause.addView(playPauseIconView, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        btnPlayPause.setOnClickListener(v -> togglePlayPauseAll());
        controlBar.addView(btnPlayPause, new LinearLayout.LayoutParams(0, dp(32), 1f));

        // 3.2 [SD] / [HD]
        FrameLayout btnQuality = new FrameLayout(this);
        iconQualityView = new LimenIcons.IconView(this,
                currentSubtype == 1 ? LimenIcons.ICON_SD : LimenIcons.ICON_HD,
                currentSubtype == 1 ? COLOR_TEXT_WHITE : COLOR_ACCENT_CYAN);
        btnQuality.addView(iconQualityView, new FrameLayout.LayoutParams(dp(26), dp(24), Gravity.CENTER));
        btnQuality.setOnClickListener(v -> toggleStreamQuality());
        controlBar.addView(btnQuality, new LinearLayout.LayoutParams(0, dp(32), 1f));

        // 3.3 Audio Mute / Unmute
        FrameLayout btnMute = new FrameLayout(this);
        iconMuteView = new LimenIcons.IconView(this,
                isMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON,
                isMuted ? COLOR_TEXT_WHITE : COLOR_ACCENT_CYAN);
        btnMute.addView(iconMuteView, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        btnMute.setOnClickListener(v -> toggleAudioMute());
        controlBar.addView(btnMute, new LinearLayout.LayoutParams(0, dp(32), 1f));

        // 3.4 [1] / [4] / [9] Selector de Cuadrícula
        FrameLayout btnGrid = new FrameLayout(this);
        int initialBadge = currentGridMode == 1 ? LimenIcons.ICON_BADGE_1 : (currentGridMode == 4 ? LimenIcons.ICON_BADGE_4 : LimenIcons.ICON_BADGE_9);
        iconGridView = new LimenIcons.IconView(this, initialBadge, COLOR_TEXT_WHITE);
        btnGrid.addView(iconGridView, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        btnGrid.setOnClickListener(v -> {
            if (currentGridMode == 1) switchGridMode(4);
            else if (currentGridMode == 4) switchGridMode(9);
            else switchGridMode(1);
        });
        controlBar.addView(btnGrid, new LinearLayout.LayoutParams(0, dp(32), 1f));

        // 3.5 Rotar / Pantalla Completa 16:9
        FrameLayout btnFullscreen = new FrameLayout(this);
        btnFullscreen.addView(new LimenIcons.IconView(this, LimenIcons.ICON_FULLSCREEN, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        btnFullscreen.setOnClickListener(v -> toggleLandscapeFullscreen());
        controlBar.addView(btnFullscreen, new LinearLayout.LayoutParams(0, dp(32), 1f));

        page.addView(controlBar);

        // ── 4. ÁREA INFERIOR: CONSOLA TÁCTIL + CANALES DEL EQUIPO + MENSAJE DE ALARMA (Imagen 4) ──
        ScrollView lowerScroll = new ScrollView(this);
        lowerScroll.setFillViewport(true);

        LinearLayout lowerArea = new LinearLayout(this);
        lowerArea.setOrientation(LinearLayout.VERTICAL);
        lowerArea.setPadding(dp(12), dp(12), dp(12), dp(20));

        // 4.1 Consola Táctil con 5 botones circulares y tira de canales filtrada
        LinearLayout consoleCard = new LinearLayout(this);
        consoleCard.setOrientation(LinearLayout.VERTICAL);
        consoleCard.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        consoleCard.setPadding(dp(12), dp(16), dp(12), dp(12));

        LinearLayout circleRow = new LinearLayout(this);
        circleRow.setOrientation(LinearLayout.HORIZONTAL);
        circleRow.setGravity(Gravity.CENTER);

        // Botón 1: Canales
        circleRow.addView(createConsoleCircleButton(LimenIcons.ICON_VIDEO, "Canales",
                v -> showCustomCameraPickerModal(activeLiveGroupFilter, getCamerasForCurrentLiveFilter(),
                        cam -> playCameraInCell(selectedCellIndex, cam))),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Botón 2: Capturar imagen
        circleRow.addView(createConsoleCircleButton(LimenIcons.ICON_CAMERA, "Capturar",
                v -> {
                    CellHolder h = cellHolders.get(selectedCellIndex);
                    if (h.activeCamera != null) {
                        captureCurrentFrameToCache(h, h.activeCamera.id);
                        logAppEvent("Captura guardada: " + h.activeCamera.getCompactBadgeLabel());
                        Toast.makeText(this, "Captura actualizada: " + h.activeCamera.getCompactBadgeLabel(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Selecciona primero un canal activo", Toast.LENGTH_SHORT).show();
                    }
                }),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Botón 3 (Central prominente): Audio
        LinearLayout centerAudioCol = new LinearLayout(this);
        centerAudioCol.setOrientation(LinearLayout.VERTICAL);
        centerAudioCol.setGravity(Gravity.CENTER);
        audioConsoleCircle = new FrameLayout(this);
        audioConsoleCircle.setBackground(circleBg(COLOR_BG_ELEVATED, isMuted ? COLOR_TEXT_GRAY : COLOR_ACCENT_CYAN, 1));
        audioConsoleIconView = new LimenIcons.IconView(this,
                isMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON,
                isMuted ? COLOR_TEXT_WHITE : COLOR_ACCENT_CYAN);
        audioConsoleCircle.addView(audioConsoleIconView, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        audioConsoleCircle.setOnClickListener(v -> toggleAudioMute());
        centerAudioCol.addView(audioConsoleCircle, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView centerAudioLbl = new TextView(this);
        centerAudioLbl.setText("Audio");
        styleText(centerAudioLbl, 11, COLOR_TEXT_GRAY, false);
        centerAudioLbl.setPadding(0, dp(6), 0, 0);
        centerAudioCol.addView(centerAudioLbl);
        circleRow.addView(centerAudioCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));

        // Botón 4: Reconectar
        circleRow.addView(createConsoleCircleButton(LimenIcons.ICON_REFRESH, "Reconectar",
                v -> {
                    CellHolder h = cellHolders.get(selectedCellIndex);
                    if (h.activeCamera != null) {
                        playCameraInCell(selectedCellIndex, h.activeCamera);
                    } else {
                        showCustomCameraPickerModal(activeLiveGroupFilter, getCamerasForCurrentLiveFilter(),
                                cam -> playCameraInCell(selectedCellIndex, cam));
                    }
                }),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Botón 5: Cerrar celda activa (Icono [x] derecho de Imagen 4)
        circleRow.addView(createConsoleCircleButton(LimenIcons.ICON_CLOSE_CELL, "Cerrar",
                v -> stopCellPlayer(selectedCellIndex)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        consoleCard.addView(circleRow);

        // Tira rápida de canales exclusivos del dispositivo actual
        HorizontalScrollView chScroll = new HorizontalScrollView(this);
        chScroll.setHorizontalScrollBarEnabled(false);
        chScroll.setPadding(0, dp(14), 0, dp(2));
        bottomChannelRow = new LinearLayout(this);
        bottomChannelRow.setOrientation(LinearLayout.HORIZONTAL);
        chScroll.addView(bottomChannelRow);
        consoleCard.addView(chScroll);

        populateLiveBottomChannelPills();

        LinearLayout.LayoutParams ccLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        ccLp.bottomMargin = dp(12);
        lowerArea.addView(consoleCard, ccLp);

        // 4.2 Bandeja "Mensaje de alarma" (Parte inferior de Imagen 4)
        LinearLayout alarmCard = new LinearLayout(this);
        alarmCard.setOrientation(LinearLayout.VERTICAL);
        alarmCard.setBackground(roundBg(COLOR_BG_CARD, 14, COLOR_BORDER_DARK, 1));

        LinearLayout alarmHeader = new LinearLayout(this);
        alarmHeader.setOrientation(LinearLayout.HORIZONTAL);
        alarmHeader.setGravity(Gravity.CENTER_VERTICAL);
        alarmHeader.setPadding(dp(14), dp(12), dp(14), dp(12));

        alarmHeader.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CALENDAR, COLOR_ACCENT_CYAN),
                new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView alarmTitle = new TextView(this);
        alarmTitle.setText("Mensaje de alarma");
        styleText(alarmTitle, 14, COLOR_TEXT_WHITE, true);
        alarmTitle.setGravity(Gravity.CENTER);
        alarmHeader.addView(alarmTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout toggleAlarmBtn = new FrameLayout(this);
        toggleAlarmBtn.setBackground(circleBg(COLOR_BG_ELEVATED, COLOR_BORDER_DARK, 1));
        toggleAlarmBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_DOWN, COLOR_TEXT_GRAY),
                new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        alarmHeader.addView(toggleAlarmBtn, new LinearLayout.LayoutParams(dp(26), dp(26)));

        alarmCard.addView(alarmHeader);

        final LinearLayout alarmBody = new LinearLayout(this);
        alarmBody.setOrientation(LinearLayout.VERTICAL);
        alarmBody.setPadding(dp(16), dp(6), dp(16), dp(16));

        View alarmDiv = new View(this);
        alarmDiv.setBackgroundColor(Color.parseColor(COLOR_BORDER_DARK));
        alarmBody.addView(alarmDiv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        alarmLogContentText = new TextView(this);
        alarmLogContentText.setPadding(0, dp(10), 0, 0);
        styleText(alarmLogContentText, 12, COLOR_TEXT_GRAY, false);
        alarmLogContentText.setLineSpacing(dp(3), 1f);
        alarmLogContentText.setText(recentEventsLog.isEmpty() ? "Sin mensaje" : TextUtils.join("\n", recentEventsLog));
        alarmBody.addView(alarmLogContentText);
        alarmCard.addView(alarmBody);

        alarmHeader.setOnClickListener(v -> {
            isAlarmPanelExpanded = !isAlarmPanelExpanded;
            alarmBody.setVisibility(isAlarmPanelExpanded ? View.VISIBLE : View.GONE);
        });

        lowerArea.addView(alarmCard);
        lowerScroll.addView(lowerArea);
        page.addView(lowerScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        rootContainer.addView(page);

        // Iniciar únicamente las cámaras solicitadas
        if (singleCamToStart != null) {
            selectCell(0);
            playCameraInCell(0, singleCamToStart);
        } else if (groupCamsToStart != null && !groupCamsToStart.isEmpty()) {
            int maxCells = currentGridMode;
            int count = Math.min(groupCamsToStart.size(), maxCells);
            for (int i = 0; i < count; i++) {
                final int idx = i;
                final CameraItem c = groupCamsToStart.get(i);
                mainHandler.postDelayed(() -> playCameraInCell(idx, c), i * 350L);
            }
        }

        mainHandler.removeCallbacks(bitrateTicker);
        mainHandler.postDelayed(bitrateTicker, 1200);
    }

    private View createConsoleCircleButton(int iconType, String label, View.OnClickListener listener) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setOnClickListener(listener);

        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(circleBg(COLOR_BG_ELEVATED, COLOR_BORDER_DARK, 1));
        LimenIcons.IconView ic = new LimenIcons.IconView(this, iconType, COLOR_TEXT_WHITE);
        circle.addView(ic, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        col.addView(circle, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView tv = new TextView(this);
        tv.setText(label);
        styleText(tv, 11, COLOR_TEXT_GRAY, false);
        tv.setPadding(0, dp(6), 0, 0);
        tv.setSingleLine(true);
        col.addView(tv);

        return col;
    }

    private void populateLiveBottomChannelPills() {
        if (bottomChannelRow == null) return;
        bottomChannelRow.removeAllViews();

        List<CameraItem> filteredCams = getCamerasForCurrentLiveFilter();
        for (CameraItem cam : filteredCams) {
            TextView pill = new TextView(this);
            pill.setText(cam.getShortChannelLabel());
            styleText(pill, 12, COLOR_TEXT_WHITE, true);
            pill.setPadding(dp(12), dp(7), dp(12), dp(7));
            pill.setBackground(roundBg(COLOR_BG_ELEVATED, 8, COLOR_BORDER_DARK, 1));
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            pLp.rightMargin = dp(8);
            pill.setOnClickListener(v -> {
                playCameraInCell(selectedCellIndex, cam);
                if (currentGridMode > 1) {
                    selectCell((selectedCellIndex + 1) % currentGridMode);
                }
            });
            bottomChannelRow.addView(pill, pLp);
        }
    }

    private void togglePlayPauseAll() {
        isAllPaused = !isAllPaused;
        if (playPauseIconView != null) {
            playPauseIconView.setIconType(isAllPaused ? LimenIcons.ICON_PLAY : LimenIcons.ICON_PAUSE);
            playPauseIconView.setIconColor(isAllPaused ? COLOR_ACCENT_CYAN : COLOR_TEXT_WHITE);
        }
        for (CellHolder h : cellHolders) {
            if (h.player != null) {
                h.player.setPlayWhenReady(!isAllPaused);
            }
        }
    }

    private void toggleAudioMute() {
        isMuted = !isMuted;
        if (iconMuteView != null) {
            iconMuteView.setIconType(isMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON);
            iconMuteView.setIconColor(isMuted ? COLOR_TEXT_WHITE : COLOR_ACCENT_CYAN);
        }
        if (audioConsoleIconView != null) {
            audioConsoleIconView.setIconType(isMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON);
            audioConsoleIconView.setIconColor(isMuted ? COLOR_TEXT_WHITE : COLOR_ACCENT_CYAN);
        }
        if (audioConsoleCircle != null) {
            audioConsoleCircle.setBackground(circleBg(COLOR_BG_ELEVATED, isMuted ? COLOR_TEXT_GRAY : COLOR_ACCENT_CYAN, 1));
        }
        for (CellHolder h : cellHolders) {
            if (h.player != null) h.player.setVolume(isMuted ? 0f : 1f);
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
                lp.height = (currentGridMode == 1) ? Math.round(screenWidth * 9f / 16f) : Math.round(screenWidth * 0.78f);
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

    private void buildLiveCells9() {
        releaseAllPlayers();
        cellHolders.clear();
        for (LinearLayout r : gridRows) r.removeAllViews();

        for (int i = 0; i < 9; i++) {
            final int cellIdx = i;
            CellHolder holder = new CellHolder();
            holder.index = cellIdx;

            FrameLayout cellFrame = new FrameLayout(this);
            cellFrame.setBackground(roundBg(
                    COLOR_BG_CELL, 0,
                    cellIdx == selectedCellIndex ? COLOR_ACCENT_CYAN : COLOR_BORDER_DARK,
                    cellIdx == selectedCellIndex ? 2 : 1
            ));

            ImageView thumbBg = new ImageView(this);
            thumbBg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbBg.setAlpha(0.45f);
            thumbBg.setVisibility(View.GONE);
            cellFrame.addView(thumbBg, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));

            PlayerView pv = new PlayerView(this);
            pv.setUseController(false);
            pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            pv.setBackgroundColor(Color.TRANSPARENT);
            cellFrame.addView(pv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LimenIcons.IconView plusIv = new LimenIcons.IconView(this, LimenIcons.ICON_PLUS, COLOR_TEXT_MUTED);
            cellFrame.addView(plusIv, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));

            ProgressBar spinner = new ProgressBar(this);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT_CYAN)));
            spinner.setVisibility(View.GONE);
            cellFrame.addView(spinner, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));

            TextView lblStatus = new TextView(this);
            lblStatus.setText("");
            styleText(lblStatus, 10, COLOR_TEXT_WHITE, false);
            lblStatus.setGravity(Gravity.CENTER);
            lblStatus.setVisibility(View.GONE);
            FrameLayout.LayoutParams stLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            );
            stLp.topMargin = dp(28);
            cellFrame.addView(lblStatus, stLp);

            // Overlay superior izquierdo: CAM 1 (como en Imagen 4)
            TextView lblTopLeft = new TextView(this);
            styleText(lblTopLeft, 11, "#FFFFFF", true);
            lblTopLeft.setShadowLayer(4f, 1f, 1f, Color.BLACK);
            lblTopLeft.setPadding(dp(7), dp(5), dp(7), dp(4));
            lblTopLeft.setVisibility(View.GONE);
            cellFrame.addView(lblTopLeft, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START
            ));

            // Overlay superior derecho: 110 KB/s (como en Imagen 4)
            TextView lblTopRightBitrate = new TextView(this);
            styleText(lblTopRightBitrate, 11, "#E2E8F0", false);
            lblTopRightBitrate.setShadowLayer(4f, 1f, 1f, Color.BLACK);
            lblTopRightBitrate.setPadding(dp(7), dp(5), dp(7), dp(4));
            lblTopRightBitrate.setVisibility(View.GONE);
            cellFrame.addView(lblTopRightBitrate, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END
            ));

            // Overlay inferior izquierdo: Nombre del canal
            TextView lblBottomLeft = new TextView(this);
            styleText(lblBottomLeft, 10, "#CBD5E1", false);
            lblBottomLeft.setShadowLayer(4f, 1f, 1f, Color.BLACK);
            lblBottomLeft.setPadding(dp(7), dp(2), dp(7), dp(5));
            lblBottomLeft.setVisibility(View.GONE);
            cellFrame.addView(lblBottomLeft, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START
            ));

            cellFrame.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - holder.lastTapTime < 350) {
                    selectedCellIndex = cellIdx;
                    if (currentGridMode == 1) {
                        switchGridMode(countActiveCells() > 4 ? 9 : 4);
                    } else {
                        switchGridMode(1);
                    }
                } else {
                    selectCell(cellIdx);
                    if (holder.activeCamera == null) {
                        showCustomCameraPickerModal(activeLiveGroupFilter, getCamerasForCurrentLiveFilter(),
                                cam -> playCameraInCell(cellIdx, cam));
                    }
                }
                holder.lastTapTime = now;
            });

            cellFrame.setOnLongClickListener(v -> {
                selectCell(cellIdx);
                showCustomCameraPickerModal(activeLiveGroupFilter, getCamerasForCurrentLiveFilter(),
                        cam -> playCameraInCell(cellIdx, cam));
                return true;
            });

            holder.container = cellFrame;
            holder.thumbPreview = thumbBg;
            holder.playerView = pv;
            holder.plusIcon = plusIv;
            holder.spinner = spinner;
            holder.labelStatus = lblStatus;
            holder.labelTopLeft = lblTopLeft;
            holder.labelTopRightBitrate = lblTopRightBitrate;
            holder.labelBottomLeft = lblBottomLeft;

            cellHolders.add(holder);

            int rowIdx = i / 3;
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            );
            gridRows.get(rowIdx).addView(cellFrame, cellLp);
        }

        applyGridVisibility();
    }

    private void selectCell(int idx) {
        selectedCellIndex = idx;
        for (int i = 0; i < cellHolders.size(); i++) {
            CellHolder h = cellHolders.get(i);
            h.container.setBackground(roundBg(
                    COLOR_BG_CELL, 0,
                    i == selectedCellIndex ? COLOR_ACCENT_CYAN : COLOR_BORDER_DARK,
                    i == selectedCellIndex ? 2 : 1
            ));
        }
    }

    private void switchGridMode(int mode) {
        currentGridMode = mode;
        if (selectedCellIndex >= mode) {
            selectedCellIndex = 0;
        }
        if (iconGridView != null) {
            int ic = mode == 1 ? LimenIcons.ICON_BADGE_1 : (mode == 4 ? LimenIcons.ICON_BADGE_4 : LimenIcons.ICON_BADGE_9);
            iconGridView.setIconType(ic);
        }
        if (liveVideoStage != null && !isLandscapeFullscreen) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            ViewGroup.LayoutParams lp = liveVideoStage.getLayoutParams();
            lp.height = (mode == 1) ? Math.round(screenWidth * 9f / 16f) : Math.round(screenWidth * 0.78f);
            liveVideoStage.setLayoutParams(lp);
        }
        selectCell(selectedCellIndex);
        applyGridVisibility();
    }

    private void applyGridVisibility() {
        if (cellHolders.size() < 9 || gridRows.size() < 3) return;

        if (currentGridMode == 1) {
            for (LinearLayout r : gridRows) r.removeAllViews();
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            for (int i = 0; i < 9; i++) {
                gridRows.get(i / 3).addView(cellHolders.get(i).container, cellLp);
            }
            int targetRow = selectedCellIndex / 3;
            for (int r = 0; r < 3; r++) {
                gridRows.get(r).setVisibility(r == targetRow ? View.VISIBLE : View.GONE);
            }
            for (int i = 0; i < 9; i++) {
                cellHolders.get(i).container.setVisibility(i == selectedCellIndex ? View.VISIBLE : View.GONE);
            }
        } else if (currentGridMode == 4) {
            for (LinearLayout r : gridRows) r.removeAllViews();
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            gridRows.get(0).addView(cellHolders.get(0).container, cellLp);
            gridRows.get(0).addView(cellHolders.get(1).container, cellLp);
            gridRows.get(1).addView(cellHolders.get(2).container, cellLp);
            gridRows.get(1).addView(cellHolders.get(3).container, cellLp);

            gridRows.get(0).setVisibility(View.VISIBLE);
            gridRows.get(1).setVisibility(View.VISIBLE);
            gridRows.get(2).setVisibility(View.GONE);
            for (int i = 0; i < 4; i++) {
                cellHolders.get(i).container.setVisibility(View.VISIBLE);
            }
        } else {
            for (LinearLayout r : gridRows) r.removeAllViews();
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            for (int i = 0; i < 9; i++) {
                cellHolders.get(i).container.setVisibility(View.VISIBLE);
                gridRows.get(i / 3).addView(cellHolders.get(i).container, cellLp);
            }
            gridRows.get(0).setVisibility(View.VISIBLE);
            gridRows.get(1).setVisibility(View.VISIBLE);
            gridRows.get(2).setVisibility(View.VISIBLE);
        }
    }

    private void toggleStreamQuality() {
        currentSubtype = (currentSubtype == 1) ? 0 : 1;
        if (iconQualityView != null) {
            iconQualityView.setIconType(currentSubtype == 1 ? LimenIcons.ICON_SD : LimenIcons.ICON_HD);
            iconQualityView.setIconColor(currentSubtype == 1 ? COLOR_TEXT_WHITE : COLOR_ACCENT_CYAN);
        }
        Toast.makeText(this,
                currentSubtype == 1 ? "Calidad SD (Rápida)" : "Calidad HD (Principal)",
                Toast.LENGTH_SHORT).show();

        for (CellHolder h : cellHolders) {
            if (h.activeCamera != null) {
                playCameraInCell(h.index, h.activeCamera);
            }
        }
    }

    private interface OnCameraChosen {
        void onChosen(CameraItem cam);
    }

    // Modal personalizado Limen Seguridad para elegir canal (Cero AlertDialog del sistema)
    private void showCustomCameraPickerModal(String groupTitle, List<CameraItem> cams, OnCameraChosen callback) {
        if (cams == null || cams.isEmpty()) {
            Toast.makeText(this, "No hay canales disponibles", Toast.LENGTH_SHORT).show();
            return;
        }
        final Dialog dialog = createCustomLimenDialog();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(18), dp(18), dp(18), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        TextView title = new TextView(this);
        title.setText("Canales de " + (groupTitle != null ? groupTitle : "Dispositivo"));
        styleText(title, 15, COLOR_TEXT_WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout closeBtn = new FrameLayout(this);
        closeBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        closeBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COLOR_TEXT_GRAY),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        card.addView(header);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(COLOR_BORDER_DARK));
        card.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (final CameraItem c : cams) {
            list.addView(createModalActionRow(
                    LimenIcons.ICON_VIDEO,
                    c.getShortChannelLabel(),
                    "Mostrar en ventana " + (selectedCellIndex + 1),
                    COLOR_ACCENT_CYAN,
                    v -> {
                        dialog.dismiss();
                        callback.onChosen(c);
                    }));
        }

        scroll.addView(list);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        dialog.setContentView(card);
        dialog.show();
    }

    private void showGroupFilterSwitchModal() {
        final Dialog dialog = createCustomLimenDialog();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(18), dp(18), dp(18), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        TextView title = new TextView(this);
        title.setText("Cambiar de equipo");
        styleText(title, 15, COLOR_TEXT_WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout closeBtn = new FrameLayout(this);
        closeBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        closeBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COLOR_TEXT_GRAY),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        card.addView(header);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(COLOR_BORDER_DARK));
        card.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        Map<String, List<CameraItem>> groups = getGroupedCameras("");
        for (Map.Entry<String, List<CameraItem>> entry : groups.entrySet()) {
            final String gName = entry.getKey();
            final List<CameraItem> gCams = entry.getValue();
            boolean isCurrent = gName.equalsIgnoreCase(activeLiveGroupFilter);

            card.addView(createModalActionRow(
                    LimenIcons.ICON_DVR_BOX,
                    gName,
                    gCams.size() + " canales vinculados" + (isCurrent ? " · (Equipo actual)" : ""),
                    isCurrent ? COLOR_ACCENT_CYAN : COLOR_TEXT_WHITE,
                    v -> {
                        dialog.dismiss();
                        currentGridMode = gCams.size() > 4 ? 9 : 4;
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
    // 5. PANTALLA GESTOR DE DISPOSITIVOS Y PERFIL CON TIPOGRAFÍA UNIFORME
    // =========================================================================
    private void showDeviceManagerScreen() {
        currentTab = 2;
        releaseAllPlayers();
        exitLandscapeIfNeeded();
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
        header.setBackgroundColor(Color.parseColor(COLOR_BG_HEADER));
        header.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView title = new TextView(this);
        title.setText("Equipos Vinculados");
        styleText(title, 17, COLOR_TEXT_WHITE, true);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(12), dp(14), dp(20));

        Map<String, List<CameraItem>> groups = getGroupedCameras("");

        for (Map.Entry<String, List<CameraItem>> entry : groups.entrySet()) {
            final String gTitle = entry.getKey();
            final List<CameraItem> cams = entry.getValue();
            String snText = cams.get(0).sn != null && !cams.get(0).sn.isEmpty() ? cams.get(0).sn : "IP Directo";

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(roundBg(COLOR_BG_CARD, 14, COLOR_BORDER_DARK, 1));
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rLp.bottomMargin = dp(10);

            FrameLayout badge = new FrameLayout(this);
            badge.setBackground(roundBg(COLOR_BG_ELEVATED, 10, COLOR_BORDER_DARK, 1));
            LimenIcons.IconView badgeIcon = new LimenIcons.IconView(this, LimenIcons.ICON_DVR_BOX, COLOR_ACCENT_CYAN);
            badge.addView(badgeIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));

            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            bLp.rightMargin = dp(14);
            row.addView(badge, bLp);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            TextView nameTv = new TextView(this);
            nameTv.setText(gTitle);
            styleText(nameTv, 15, COLOR_TEXT_WHITE, true);
            infoCol.addView(nameTv);

            TextView subTv = new TextView(this);
            subTv.setText("SN: " + snText + "  ·  " + cams.size() + " canales");
            styleText(subTv, 12, COLOR_TEXT_GRAY, false);
            subTv.setPadding(0, dp(3), 0, 0);
            infoCol.addView(subTv);

            row.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LimenIcons.IconView arrow = new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_R, COLOR_TEXT_GRAY);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));

            row.setOnClickListener(v -> showDeviceDetailsScreen(gTitle, cams));
            list.addView(row, rLp);
        }

        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(buildBottomNavBar());
        rootContainer.addView(page);
    }

    private void showProfileScreen() {
        currentTab = 3;
        releaseAllPlayers();
        exitLandscapeIfNeeded();
        rootContainer.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Header Superior Oficial
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.parseColor(COLOR_BG_HEADER));
        header.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView topTitle = new TextView(this);
        topTitle.setText("Información y Cuenta");
        styleText(topTitle, 17, COLOR_TEXT_WHITE, true);
        topTitle.setGravity(Gravity.CENTER);
        header.addView(topTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(18), dp(24), dp(18), dp(24));

        // Avatar Badge con Logotipo Limen
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(roundBg(COLOR_BG_CARD, 24, COLOR_BORDER_DARK, 1));
        ImageView userIcon = new ImageView(this);
        userIcon.setImageResource(R.drawable.limen_logo);
        userIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.addView(userIcon, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));

        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(80), dp(80));
        avLp.bottomMargin = dp(12);
        content.addView(avatar, avLp);

        TextView uName = new TextView(this);
        uName.setText(currentUserName != null && !currentUserName.isEmpty() ? currentUserName : "Usuario Limen");
        styleText(uName, 18, COLOR_TEXT_WHITE, true);
        content.addView(uName);

        TextView uMail = new TextView(this);
        uMail.setText(currentUserEmail + "  ·  " + currentUserRole.toUpperCase(Locale.ROOT));
        styleText(uMail, 12, COLOR_ACCENT_CYAN, false);
        LinearLayout.LayoutParams umLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        umLp.topMargin = dp(4);
        umLp.bottomMargin = dp(20);
        content.addView(uMail, umLp);

        // Tarjeta 1: Detalles de Cuenta y Conexión
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(roundBg(COLOR_BG_CARD, 14, COLOR_BORDER_DARK, 1));
        statusCard.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scLp.bottomMargin = dp(16);

        statusCard.addView(createProfileRow(LimenIcons.ICON_VIDEO, "Canales autorizados", allCameras.size() + " cámaras"));
        statusCard.addView(createProfileRow(LimenIcons.ICON_GRID_9, "Vistas en directo", "1CH · 4CH · 9CH (16:9)"));
        statusCard.addView(createProfileRow(LimenIcons.ICON_SERVER, "Servidor Central", "eyes.limenseguridad.com"));
        statusCard.addView(createProfileRow(LimenIcons.ICON_SHIELD, "Cifrado & Transporte", "TLS v1.3 · P2P Seguro"));
        statusCard.addView(createProfileRow(LimenIcons.ICON_REFRESH, "Actualizaciones OTA", "Buscar versión (Tocar)", () -> checkForUpdates(true)));

        content.addView(statusCard, scLp);

        // Tarjeta 2: Marco Institucional y Legal - "Desarrollada por LIMEN SEGURIDAD"
        LinearLayout legalCard = new LinearLayout(this);
        legalCard.setOrientation(LinearLayout.VERTICAL);
        legalCard.setBackground(roundBg(COLOR_BG_CARD, 14, COLOR_BORDER_DARK, 1));
        legalCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams lcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lcLp.bottomMargin = dp(20);

        LinearLayout legalHeader = new LinearLayout(this);
        legalHeader.setOrientation(LinearLayout.HORIZONTAL);
        legalHeader.setGravity(Gravity.CENTER_VERTICAL);
        legalHeader.setPadding(0, 0, 0, dp(10));

        ImageView brandLogo = new ImageView(this);
        brandLogo.setImageResource(R.drawable.limen_logo);
        brandLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(22), dp(22));
        blp.rightMargin = dp(8);
        legalHeader.addView(brandLogo, blp);

        TextView devLabel = new TextView(this);
        devLabel.setText("Desarrollada por LIMEN SEGURIDAD");
        styleText(devLabel, 14, COLOR_TEXT_WHITE, true);
        legalHeader.addView(devLabel);
        legalCard.addView(legalHeader);

        TextView appVer = new TextView(this);
        appVer.setText("LIMEN EYES v" + APP_VERSION_NAME + " \u00b7 Plataforma Profesional de Monitoreo");
        styleText(appVer, 12, COLOR_ACCENT_CYAN, true);
        appVer.setPadding(0, 0, 0, dp(8));
        legalCard.addView(appVer);

        TextView appDesc = new TextView(this);
        appDesc.setText("Solución integral para transmisión segura de video en tiempo real, auditoría multicámara y supervisión perimetral de instalaciones protegidas.");
        styleText(appDesc, 12, COLOR_TEXT_GRAY, false);
        appDesc.setLineSpacing(dp(2), 1.15f);
        appDesc.setPadding(0, 0, 0, dp(12));
        legalCard.addView(appDesc);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor(COLOR_BORDER_DARK));
        legalCard.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        TextView legalTitle = new TextView(this);
        legalTitle.setText("AVISO LEGAL & PROTECCIÓN DE DATOS");
        styleText(legalTitle, 11, COLOR_TEXT_MUTED, true);
        legalTitle.setLetterSpacing(0.08f);
        legalTitle.setPadding(0, dp(12), 0, dp(6));
        legalCard.addView(legalTitle);

        TextView legalText = new TextView(this);
        legalText.setText("En cumplimiento de la normativa de Protección de Datos Personales (Ley N° 25.326 y estándares de seguridad de videovigilancia), el acceso al material audiovisual y registros telemétricos está estrictamente reservado al titular del servicio y operadores acreditados para fines exclusivos de prevención y resguardo patrimonial.\n\nQueda prohibida la reproducción no autorizada, divulgación o manipulación de imágenes fuera del marco legal y contractual establecido.");
        styleText(legalText, 11, COLOR_TEXT_MUTED, false);
        legalText.setLineSpacing(dp(2), 1.15f);
        legalText.setPadding(0, 0, 0, dp(14));
        legalCard.addView(legalText);

        TextView copyRight = new TextView(this);
        copyRight.setText("© 2026 LIMEN SEGURIDAD · Todos los derechos reservados.\nCentral de Soporte: soporte@limenseguridad.com");
        styleText(copyRight, 11, COLOR_ACCENT_CYAN, false);
        copyRight.setGravity(Gravity.CENTER);
        copyRight.setLineSpacing(dp(2), 1.15f);
        legalCard.addView(copyRight);

        content.addView(legalCard, lcLp);

        // Botón Cerrar Sesión
        Button btnLogout = new Button(this);
        btnLogout.setText("Cerrar sesión");
        styleText(btnLogout, 14, "#FCA5A5", true);
        btnLogout.setAllCaps(false);
        btnLogout.setBackground(roundBg("#2A161B", 12, "#EF4444", 1));
        btnLogout.setOnClickListener(v -> logout());
        content.addView(btnLogout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(buildBottomNavBar());
        rootContainer.addView(page);
    }

    private View createProfileRow(int iconType, String left, String right) {
        return createProfileRow(iconType, left, right, null);
    }

    private View createProfileRow(int iconType, String left, String right, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        if (action != null) {
            row.setOnClickListener(v -> action.run());
        }
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(14), dp(4), dp(14));

        LimenIcons.IconView iv = new LimenIcons.IconView(this, iconType, COLOR_ACCENT_CYAN);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        ivLp.rightMargin = dp(12);
        row.addView(iv, ivLp);

        TextView l = new TextView(this);
        l.setText(left);
        styleText(l, 14, COLOR_TEXT_WHITE, false);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView r = new TextView(this);
        r.setText(right);
        styleText(r, 12, COLOR_TEXT_GRAY, true);
        row.addView(r);
        return row;
    }

    private View buildBottomNavBar() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(Color.parseColor(COLOR_BG_HEADER));
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));

        nav.addView(createNavTab(LimenIcons.ICON_HOME, "Inicio", currentTab == 0, this::showHomeScreen));
        nav.addView(createNavTab(LimenIcons.ICON_VIDEO, "En directo", currentTab == 1, () -> showLiveViewScreen(null, null, activeLiveGroupFilter)));
        nav.addView(createNavTab(LimenIcons.ICON_CALENDAR, "Grabaciones", currentTab == 4, () -> showPlaybackScreen(null)));
        nav.addView(createNavTab(LimenIcons.ICON_SERVER, "Equipos", currentTab == 2, this::showDeviceManagerScreen));
        nav.addView(createNavTab(LimenIcons.ICON_USER, "Cuenta", currentTab == 3, this::showProfileScreen));

        return nav;
    }

    private View createNavTab(int iconType, String label, boolean active, Runnable action) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tab.setOnClickListener(v -> action.run());

        String color = active ? COLOR_ACCENT_CYAN : COLOR_TEXT_GRAY;

        LimenIcons.IconView ic = new LimenIcons.IconView(this, iconType, color);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(dp(21), dp(21));
        icLp.bottomMargin = dp(3);
        tab.addView(ic, icLp);

        TextView lb = new TextView(this);
        lb.setText(label);
        styleText(lb, 11, color, active);
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
        File localFile = getLocalThumbFile(camId);
        if (localFile.exists() && localFile.length() > 500) {
            Bitmap bm = BitmapFactory.decodeFile(localFile.getAbsolutePath());
            if (bm != null) {
                targetIv.setImageBitmap(bm);
                targetIv.setVisibility(View.VISIBLE);
            }
        }

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
                executor.execute(() -> {
                    try {
                        httpGet(BASE_URL + "/api/camaras/" + camId + "/thumb.jpg?live=1", authToken);
                    } catch (Exception ignored) {}
                });
            }
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // 7. MOTOR DE REPRODUCCIÓN NATIVO EXOPLAYER (CON BEARER TOKEN + ACTIVATE)
    // =========================================================================
    private void playCameraInCell(int cellIdx, CameraItem cam) {
        if (cellIdx < 0 || cellIdx >= cellHolders.size()) return;
        CellHolder holder = cellHolders.get(cellIdx);
        holder.activeCamera = cam;
        final int currentReqId = ++holder.requestId;

        mainHandler.post(() -> {
            if (liveHeaderTitle != null && activeLiveGroupFilter == null) {
                liveHeaderTitle.setText(cam.getGroupTitle());
            }
            holder.plusIcon.setVisibility(View.GONE);
            holder.labelTopLeft.setText(cam.getCompactBadgeLabel());
            holder.labelTopLeft.setVisibility(View.VISIBLE);
            holder.labelBottomLeft.setText(cam.nombre != null ? cam.nombre : "");
            holder.labelBottomLeft.setVisibility(View.VISIBLE);
            holder.labelTopRightBitrate.setText(currentSubtype == 1 ? "SD" : "HD");
            holder.labelTopRightBitrate.setVisibility(View.VISIBLE);
            holder.labelStatus.setText("Conectando...");
            holder.labelStatus.setVisibility(View.VISIBLE);
            holder.spinner.setVisibility(View.VISIBLE);

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
                        holder.labelStatus.setText("Reintentando túnel P2P...");
                        holder.labelStatus.setVisibility(View.VISIBLE);
                        mainHandler.postDelayed(() -> {
                            if (holder.requestId == currentReqId && holder.activeCamera != null && currentTab == 1) {
                                playCameraInCell(cellIdx, holder.activeCamera);
                            }
                        }, 2800);
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
            holder.player.setPlayWhenReady(!isAllPaused);
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
        h.labelTopLeft.setVisibility(View.GONE);
        h.labelTopRightBitrate.setVisibility(View.GONE);
        h.labelBottomLeft.setVisibility(View.GONE);
        h.labelStatus.setVisibility(View.GONE);
        h.plusIcon.setVisibility(View.VISIBLE);
    }

    private void releaseAllPlayers() {
        mainHandler.removeCallbacks(bitrateTicker);
        for (CellHolder h : cellHolders) {
            h.requestId++;
            if (h.player != null) {
                try {
                    h.player.release();
                } catch (Throwable ignored) {}
                h.player = null;
            }
        }
        if (playbackPlayer != null) {
            try {
                playbackPlayer.release();
            } catch (Throwable ignored) {}
            playbackPlayer = null;
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
                    try { h.player.setPlayWhenReady(!isAllPaused); } catch (Throwable ignored) {}
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
        if (isLandscapeFullscreen) {
            toggleLandscapeFullscreen();
        } else if (currentTab == 1) {
            if (currentGridMode == 1 && countActiveCells() > 1) {
                switchGridMode(countActiveCells() > 4 ? 9 : 4);
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

    // =========================================================================
    // AUTO-ACTUALIZADOR OTA LIMEN SEGURIDAD (SIN PLAY STORE)
    // =========================================================================
    private void checkForUpdates(final boolean userInitiated) {
        executor.execute(() -> {
            try {
                int currentCode = 11;
                String currentName = "2.4.3";
                try {
                    PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    currentCode = pInfo.versionCode;
                    currentName = pInfo.versionName;
                } catch (Exception ignored) {}

                HttpResponse resp = httpGet(BASE_URL + "/api/app-version?channel=beta", null);
                if (resp.code != 200 || resp.body == null || resp.body.isEmpty()) {
                    if (userInitiated) {
                        mainHandler.post(() -> Toast.makeText(this, "No se pudo consultar el servidor de actualizaciones", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                JSONObject json = new JSONObject(resp.body);
                int serverCode = json.optInt("versionCode", 0);
                String serverName = json.optString("versionName", "2.4.3");
                String downloadUrl = json.optString("downloadUrl", "https://github.com/alexatecinfo-ops/limeneyes-app/releases/latest/download/app-debug.apk");
                String notes = json.optString("releaseNotes", "Mejoras de rendimiento y seguridad.");

                final String cName = currentName;
                if (serverCode > currentCode) {
                    mainHandler.post(() -> promptUpdateDialog(serverName, notes, downloadUrl));
                } else if (userInitiated) {
                    mainHandler.post(() -> Toast.makeText(this, "LIMEN EYES está al día (v" + cName + ")", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                if (userInitiated) {
                    mainHandler.post(() -> Toast.makeText(this, "Error al comprobar actualizaciones: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void promptUpdateDialog(String newVer, String notes, String downloadUrl) {
        final Dialog dialog = createCustomLimenDialog();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(22), dp(20), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.limen_logo);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        iLp.rightMargin = dp(10);
        head.addView(icon, iLp);

        TextView title = new TextView(this);
        title.setText("Actualización Disponible");
        styleText(title, 16, COLOR_TEXT_WHITE, true);
        head.addView(title);
        card.addView(head);

        TextView verTag = new TextView(this);
        verTag.setText("LIMEN EYES v" + newVer);
        styleText(verTag, 13, COLOR_ACCENT_CYAN, true);
        verTag.setPadding(0, 0, 0, dp(8));
        card.addView(verTag);

        TextView notesTv = new TextView(this);
        notesTv.setText(notes);
        styleText(notesTv, 12, COLOR_TEXT_GRAY, false);
        notesTv.setLineSpacing(dp(2), 1.15f);
        notesTv.setPadding(0, 0, 0, dp(16));
        card.addView(notesTv);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        progress.setProgressTintList(ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT_CYAN)));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        pLp.bottomMargin = dp(14);
        card.addView(progress, pLp);

        TextView statusTv = new TextView(this);
        statusTv.setText("");
        styleText(statusTv, 11, COLOR_TEXT_MUTED, false);
        statusTv.setVisibility(View.GONE);
        statusTv.setPadding(0, 0, 0, dp(10));
        card.addView(statusTv);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);

        Button btnLater = new Button(this);
        btnLater.setText("Más tarde");
        styleText(btnLater, 13, COLOR_TEXT_GRAY, false);
        btnLater.setAllCaps(false);
        btnLater.setBackground(roundBg(COLOR_BG_ELEVATED, 10, COLOR_BORDER_DARK, 1));
        btnLater.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams blLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        blLp.rightMargin = dp(8);
        btns.addView(btnLater, blLp);

        Button btnUpdate = new Button(this);
        btnUpdate.setText("Actualizar ahora");
        styleText(btnUpdate, 13, COLOR_TEXT_WHITE, true);
        btnUpdate.setAllCaps(false);
        btnUpdate.setBackground(roundBg(COLOR_BTN_BLUE, 10, null, 0));
        btnUpdate.setOnClickListener(v -> {
            btnUpdate.setEnabled(false);
            btnLater.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            statusTv.setVisibility(View.VISIBLE);
            statusTv.setText("Iniciando descarga...");
            startApkDownload(downloadUrl, progress, statusTv, dialog);
        });
        btns.addView(btnUpdate, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));

        card.addView(btns);
        dialog.setContentView(card);
        dialog.show();
    }

    private void startApkDownload(String apkUrl, ProgressBar progressBar, TextView statusTv, Dialog dialog) {
        executor.execute(() -> {
            try {
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.connect();
                }

                int fileLength = conn.getContentLength();
                File cacheDir = getCacheDir();
                File apkFile = new File(cacheDir, "limeneyes_update.apk");
                if (apkFile.exists()) apkFile.delete();

                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(apkFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> {
                            progressBar.setProgress(progress);
                            statusTv.setText("Descargando actualización: " + progress + "%");
                        });
                    }
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                input.close();
                conn.disconnect();

                mainHandler.post(() -> {
                    dialog.dismiss();
                    installApk(apkFile);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusTv.setText("Error al descargar: " + e.getMessage());
                    Toast.makeText(this, "Fallo al descargar actualización: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void installApk(File apkFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(this, "Autorice a Limen Eyes para instalar la actualización", Toast.LENGTH_LONG).show();
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    permIntent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(permIntent);
                    return;
                }
            }

            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) {
            Toast.makeText(this, "Error al iniciar instalador: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // =========================================================================
    // COMPARTIR DISPOSITIVOS ENTRE USUARIOS & ACEPTAR INVITACIONES PENDIENTES
    // =========================================================================
    private void promptShareDeviceModal(String groupTitle, List<CameraItem> cams) {
        final Dialog dialog = createCustomLimenDialog();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(20), dp(18), dp(20), dp(16));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(12));

        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.limen_logo);
        ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        icLp.rightMargin = dp(8);
        head.addView(ic, icLp);

        TextView title = new TextView(this);
        title.setText("Compartir " + groupTitle);
        styleText(title, 16, COLOR_TEXT_WHITE, true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout closeBtn = new FrameLayout(this);
        closeBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        closeBtn.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CLOSE, COLOR_TEXT_GRAY),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        head.addView(closeBtn);
        card.addView(head);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView lblEmail = new TextView(this);
        lblEmail.setText("Cuenta de Google del destinatario:");
        styleText(lblEmail, 12, COLOR_TEXT_GRAY, false);
        lblEmail.setPadding(0, dp(6), 0, dp(4));
        body.addView(lblEmail);

        EditText inputEmail = new EditText(this);
        inputEmail.setHint("ejemplo@gmail.com");
        inputEmail.setHintTextColor(Color.parseColor(COLOR_TEXT_MUTED));
        inputEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        inputEmail.setBackground(roundBg(COLOR_BG_INPUT, 10, COLOR_BORDER_DARK, 1));
        inputEmail.setPadding(dp(12), dp(10), dp(12), dp(10));
        styleText(inputEmail, 13, COLOR_TEXT_WHITE, false);
        LinearLayout.LayoutParams ieLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ieLp.bottomMargin = dp(16);
        body.addView(inputEmail, ieLp);

        TextView lblCh = new TextView(this);
        lblCh.setText("Canales autorizados:");
        styleText(lblCh, 12, COLOR_TEXT_GRAY, true);
        lblCh.setPadding(0, 0, 0, dp(6));
        body.addView(lblCh);

        final List<android.widget.CheckBox> channelBoxes = new ArrayList<>();
        for (CameraItem c : cams) {
            android.widget.CheckBox chk = new android.widget.CheckBox(this);
            String chName = (c.nombre != null && !c.nombre.isEmpty()) ? c.nombre : ("Canal " + c.canal);
            chk.setText("CH " + c.canal + " - " + chName);
            chk.setChecked(true);
            chk.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
            chk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chk.setTag(c.canal);
            channelBoxes.add(chk);
            body.addView(chk);
        }

        TextView lblPerms = new TextView(this);
        lblPerms.setText("Permisos de interacción:");
        styleText(lblPerms, 12, COLOR_TEXT_GRAY, true);
        lblPerms.setPadding(0, dp(14), 0, dp(6));
        body.addView(lblPerms);

        android.widget.CheckBox chkLive = new android.widget.CheckBox(this);
        chkLive.setText("Ver en directo (Live View)");
        chkLive.setChecked(true);
        chkLive.setEnabled(false);
        chkLive.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        chkLive.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.addView(chkLive);

        android.widget.CheckBox chkPlayback = new android.widget.CheckBox(this);
        chkPlayback.setText("Reproducir grabaciones pasadas");
        chkPlayback.setChecked(true);
        chkPlayback.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        chkPlayback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.addView(chkPlayback);

        android.widget.CheckBox chkPtz = new android.widget.CheckBox(this);
        chkPtz.setText("Control de movimiento PTZ");
        chkPtz.setChecked(false);
        chkPtz.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        chkPtz.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.addView(chkPtz);

        android.widget.CheckBox chkAlarm = new android.widget.CheckBox(this);
        chkAlarm.setText("Notificaciones y alarmas perimetrales");
        chkAlarm.setChecked(false);
        chkAlarm.setTextColor(Color.parseColor(COLOR_TEXT_WHITE));
        chkAlarm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.addView(chkAlarm);

        Button btnSend = new Button(this);
        btnSend.setText("Enviar invitación");
        styleText(btnSend, 14, COLOR_TEXT_WHITE, true);
        btnSend.setAllCaps(false);
        btnSend.setBackground(roundBg(COLOR_BTN_BLUE, 10, null, 0));
        LinearLayout.LayoutParams bsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        bsLp.topMargin = dp(20);
        btnSend.setOnClickListener(v -> {
            String targetEmail = inputEmail.getText().toString().trim().toLowerCase();
            if (!targetEmail.contains("@") || !targetEmail.contains(".")) {
                Toast.makeText(this, "Ingrese un email de Google válido", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Integer> selectedChannels = new ArrayList<>();
            for (android.widget.CheckBox chk : channelBoxes) {
                if (chk.isChecked()) selectedChannels.add((Integer) chk.getTag());
            }
            if (selectedChannels.isEmpty()) {
                Toast.makeText(this, "Debe seleccionar al menos un canal", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSend.setEnabled(false);
            btnSend.setText("Enviando...");

            executor.execute(() -> {
                try {
                    JSONObject bodyJson = new JSONObject();
                    bodyJson.put("equipo_sn", cams.get(0).sn);
                    bodyJson.put("equipo_nombre", groupTitle);
                    bodyJson.put("invitado_email", targetEmail);

                    JSONArray canArr = new JSONArray();
                    for (int ch : selectedChannels) canArr.put(ch);
                    bodyJson.put("canales", canArr);

                    JSONObject permJson = new JSONObject();
                    permJson.put("live", true);
                    permJson.put("playback", chkPlayback.isChecked());
                    permJson.put("ptz", chkPtz.isChecked());
                    permJson.put("alarm", chkAlarm.isChecked());
                    bodyJson.put("permisos", permJson);

                    HttpResponse r = httpPostJson(BASE_URL + "/api/compartir", bodyJson.toString(), authToken);
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        if (r.code == 200) {
                            Toast.makeText(this, "Invitación enviada a " + targetEmail, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Error al compartir: " + r.body, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception ex) {
                    mainHandler.post(() -> {
                        btnSend.setEnabled(true);
                        btnSend.setText("Enviar invitación");
                        Toast.makeText(this, "Fallo de conexión", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
        body.addView(btnSend, bsLp);

        scroll.addView(body);
        card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
        dialog.setContentView(card);
        dialog.show();
    }

    private void checkPendingInvitations() {
        if (authToken == null || authToken.isEmpty()) return;
        executor.execute(() -> {
            try {
                HttpResponse resp = httpGet(BASE_URL + "/api/mis-invitaciones", authToken);
                if (resp.code == 200 && resp.body != null && !resp.body.equals("[]")) {
                    JSONArray arr = new JSONArray(resp.body);
                    if (arr.length() > 0) {
                        JSONObject inv = arr.getJSONObject(0);
                        mainHandler.post(() -> promptAcceptInvitationDialog(inv));
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void promptAcceptInvitationDialog(JSONObject inv) {
        final Dialog dialog = createCustomLimenDialog();

        int invId = inv.optInt("id");
        String propName = inv.optString("propietario_nombre", "Un cliente de Limen");
        String eqName = inv.optString("equipo_nombre", "Dispositivo Limen");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(22), dp(20), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.limen_logo);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(26), dp(26));
        iLp.rightMargin = dp(10);
        head.addView(icon, iLp);

        TextView title = new TextView(this);
        title.setText("Dispositivo Compartido");
        styleText(title, 16, COLOR_TEXT_WHITE, true);
        head.addView(title);
        card.addView(head);

        TextView msg = new TextView(this);
        msg.setText(propName + " te ha compartido el dispositivo:\n\"" + eqName + "\"\n\n¿Deseas aceptar la visualización de estas cámaras en tu cuenta?");
        styleText(msg, 13, COLOR_TEXT_GRAY, false);
        msg.setLineSpacing(dp(2), 1.15f);
        msg.setPadding(0, 0, 0, dp(20));
        card.addView(msg);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);

        Button btnReject = new Button(this);
        btnReject.setText("Rechazar");
        styleText(btnReject, 13, "#FCA5A5", false);
        btnReject.setAllCaps(false);
        btnReject.setBackground(roundBg("#2A161B", 10, "#EF4444", 1));
        btnReject.setOnClickListener(v -> {
            dialog.dismiss();
            executor.execute(() -> {
                try {
                    httpPostJson(BASE_URL + "/api/invitaciones/" + invId + "/rechazar", "{}", authToken);
                } catch (Exception ignored) {}
            });
        });
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        brLp.rightMargin = dp(8);
        btns.addView(btnReject, brLp);

        Button btnAccept = new Button(this);
        btnAccept.setText("Aceptar visualización");
        styleText(btnAccept, 13, COLOR_TEXT_WHITE, true);
        btnAccept.setAllCaps(false);
        btnAccept.setBackground(roundBg(COLOR_BTN_BLUE, 10, null, 0));
        btnAccept.setOnClickListener(v -> {
            dialog.dismiss();
            executor.execute(() -> {
                try {
                    HttpResponse r = httpPostJson(BASE_URL + "/api/invitaciones/" + invId + "/aceptar", "{}", authToken);
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Dispositivo aceptado correctamente", Toast.LENGTH_SHORT).show();
                        loadCamerasAndOpenHome();
                    });
                } catch (Exception ignored) {}
            });
        });
        btns.addView(btnAccept, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));

        card.addView(btns);
        dialog.setContentView(card);
        dialog.show();
    }

    // =========================================================================
    // 7. SISTEMA DE REPRODUCCIÓN DE GRABACIONES (PLAYBACK / HDD)
    // =========================================================================
    public static class RecordingInterval {
        public String start;
        public String end;
        public String startTime;
        public String endTime;
        public int startSeconds;
        public int endSeconds;
        public int duration;
        public String type; // motion or regular
        public String streamUrl;
    }

    public static class PlaybackTimelineRulerView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<RecordingInterval> intervals = new ArrayList<>();
        private int currentSeconds = 0;
        private OnTimeScrubListener scrubListener;
        private float lastTouchX = 0f;

        public interface OnTimeScrubListener {
            void onTimeScrubbed(int seconds, boolean finished);
        }

        public PlaybackTimelineRulerView(Context context) {
            super(context);
            linePaint.setColor(Color.parseColor("#4b5563"));
            linePaint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.2f, getResources().getDisplayMetrics()));

            textPaint.setColor(Color.parseColor("#9ca3af"));
            textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics()));
            textPaint.setTextAlign(Paint.Align.CENTER);

            segmentPaint.setStyle(Paint.Style.FILL);

            cursorPaint.setColor(Color.parseColor("#38bdf8"));
            cursorPaint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.5f, getResources().getDisplayMetrics()));
        }

        public void setIntervals(List<RecordingInterval> list) {
            intervals.clear();
            if (list != null) intervals.addAll(list);
            invalidate();
        }

        public void setCurrentSeconds(int sec) {
            this.currentSeconds = Math.max(0, Math.min(86400, sec));
            invalidate();
        }

        public void setOnTimeScrubListener(OnTimeScrubListener listener) {
            this.scrubListener = listener;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - lastTouchX;
                    lastTouchX = event.getX();
                    float secPerPx = 86400f / Math.max(1f, getWidth());
                    currentSeconds = Math.max(0, Math.min(86400, (int) (currentSeconds + dx * secPerPx)));
                    invalidate();
                    if (scrubListener != null) scrubListener.onTimeScrubbed(currentSeconds, false);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (scrubListener != null) scrubListener.onTimeScrubbed(currentSeconds, true);
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            // 1. Franjas grabadas (Azul / Cyan para continuo, Ambar para movimiento)
            for (RecordingInterval it : intervals) {
                float left = (it.startSeconds / 86400f) * w;
                float right = (it.endSeconds / 86400f) * w;
                if (right - left < 3f) right = left + 3f;
                segmentPaint.setColor("motion".equals(it.type) ? Color.parseColor("#f59e0b") : Color.parseColor("#38bdf8"));
                canvas.drawRect(left, 0, right, h * 0.52f, segmentPaint);
            }

            // 2. Marcas de regla horaria
            for (int hr = 0; hr <= 24; hr++) {
                float x = (hr / 24f) * w;
                canvas.drawLine(x, h * 0.45f, x, h * 0.75f, linePaint);
                if (hr % 2 == 0) {
                    String label = String.format(Locale.US, "%02d", hr);
                    canvas.drawText(label, x, h * 0.95f, textPaint);
                }
                if (hr < 24) {
                    float midX = ((hr + 0.5f) / 24f) * w;
                    canvas.drawLine(midX, h * 0.55f, midX, h * 0.72f, linePaint);
                }
            }

            // 3. Linea de cursor central con pips
            float cursorX = (currentSeconds / 86400f) * w;
            canvas.drawLine(cursorX, 0, cursorX, h, cursorPaint);
            canvas.drawCircle(cursorX, 4.5f, 4.5f, cursorPaint);
            canvas.drawCircle(cursorX, h - 4.5f, 4.5f, cursorPaint);
        }
    }

    private ExoPlayer playbackPlayer;
    private PlayerView playbackPlayerView;
    private CameraItem activePlaybackCamera = null;
    private String activePlaybackDate = null;
    private boolean isPlaybackLoaded = false;
    private int selectedPlaybackCell = 0;
    private final List<RecordingInterval> playbackIntervals = new ArrayList<>();
    private TextView playbackHeaderTitle;
    private TextView playbackHeaderSub;
    private TextView playbackDateLabel;
    private TextView playbackTimeDisplay;
    private TextView playbackStatusText;
    private ProgressBar playbackSpinner;
    private PlaybackTimelineRulerView playbackRulerView;
    private LinearLayout playbackIntervalsContainer;
    private FrameLayout[] playbackCells = new FrameLayout[4];
    private TextView playbackCellCamLabel;
    private TextView playbackCellTimeLabel;
    private LimenIcons.IconView playbackPlayPauseIcon;
    private boolean isPlaybackPlaying = false;
    private RecordingInterval currentlyPlayingInterval = null;

    private void showPlaybackScreen(CameraItem initialCam) {
        currentTab = 4;
        releaseAllPlayers();

        if (initialCam != null) {
            activePlaybackCamera = initialCam;
            isPlaybackLoaded = true;
        }

        if (activePlaybackDate == null) {
            activePlaybackDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        }

        rootContainer.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor(COLOR_BG_MAIN));
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // ── 1. BARRA SUPERIOR (Home | Playback | Drawer de Seleccion) ──
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor(COLOR_BG_HEADER));
        topBar.setPadding(dp(12), dp(10), dp(14), dp(10));

        FrameLayout btnHome = new FrameLayout(this);
        btnHome.setPadding(dp(8), dp(6), dp(8), dp(6));
        btnHome.addView(new LimenIcons.IconView(this, LimenIcons.ICON_HOME, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        btnHome.setOnClickListener(v -> showHomeScreen());
        topBar.addView(btnHome);

        LinearLayout centerCol = new LinearLayout(this);
        centerCol.setOrientation(LinearLayout.VERTICAL);
        centerCol.setGravity(Gravity.CENTER);
        centerCol.setOnClickListener(v -> showPlaybackSelectorModal());

        playbackHeaderTitle = new TextView(this);
        playbackHeaderTitle.setText(activePlaybackCamera != null ? activePlaybackCamera.getShortChannelLabel() : "Playback");
        styleText(playbackHeaderTitle, 16, COLOR_TEXT_WHITE, true);
        playbackHeaderTitle.setGravity(Gravity.CENTER);
        centerCol.addView(playbackHeaderTitle);

        playbackHeaderSub = new TextView(this);
        playbackHeaderSub.setText(activePlaybackCamera != null ? activePlaybackCamera.getGroupTitle() : "Tocar para seleccionar equipo");
        styleText(playbackHeaderSub, 11, COLOR_TEXT_GRAY, false);
        playbackHeaderSub.setGravity(Gravity.CENTER);
        centerCol.addView(playbackHeaderSub);

        topBar.addView(centerCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Boton lateral derecho (Drawer de seleccion de canales de Imagen 1)
        FrameLayout btnDrawer = new FrameLayout(this);
        btnDrawer.setPadding(dp(8), dp(6), dp(8), dp(6));
        btnDrawer.addView(new LimenIcons.IconView(this, LimenIcons.ICON_DRAWER_LIST, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        btnDrawer.setOnClickListener(v -> showPlaybackSelectorModal());
        topBar.addView(btnDrawer);

        page.addView(topBar);

        // ── 2. ESCENARIO DE VIDEO EN GRILLA 2x2 (Imagen 1) ──
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int gridHeight = (screenWidth * 9) / 16;
        if (gridHeight < dp(210)) gridHeight = dp(210);

        LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setBackgroundColor(Color.parseColor("#050811"));
        gridContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gridHeight));

        LinearLayout row0 = new LinearLayout(this);
        row0.setOrientation(LinearLayout.HORIZONTAL);
        row0.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for (int i = 0; i < 4; i++) {
            final int cellIdx = i;
            FrameLayout cell = new FrameLayout(this);
            cell.setBackgroundColor(Color.parseColor("#0b0f19"));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            clp.setMargins(dp(1), dp(1), dp(1), dp(1));
            cell.setLayoutParams(clp);

            // Borde focus en celda 0 seleccionada
            if (cellIdx == selectedPlaybackCell) {
                cell.setBackground(roundBg("#0b0f19", 4, COLOR_ACCENT_CYAN, 2));
            } else {
                cell.setBackground(roundBg("#0b0f19", 4, COLOR_BORDER_DARK, 1));
            }

            // Contenido por defecto (Icono + de seleccion)
            LinearLayout emptyBox = new LinearLayout(this);
            emptyBox.setOrientation(LinearLayout.VERTICAL);
            emptyBox.setGravity(Gravity.CENTER);
            LimenIcons.IconView plusIcon = new LimenIcons.IconView(this, LimenIcons.ICON_PLUS, cellIdx == 0 ? COLOR_ACCENT_CYAN : COLOR_TEXT_MUTED);
            emptyBox.addView(plusIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));

            cell.addView(emptyBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            cell.setOnClickListener(v -> {
                selectedPlaybackCell = cellIdx;
                updatePlaybackGridFocus();
                if (!isPlaybackLoaded || activePlaybackCamera == null) {
                    showPlaybackSelectorModal();
                }
            });

            playbackCells[cellIdx] = cell;
            if (cellIdx < 2) row0.addView(cell);
            else row1.addView(cell);
        }

        gridContainer.addView(row0);
        gridContainer.addView(row1);
        page.addView(gridContainer);

        // ── 3. BARRA DE CONTROLES PRINCIPALES (Play, Speed, Step, Audio, Fullscreen - Imagen 1) ──
        LinearLayout primaryControls = new LinearLayout(this);
        primaryControls.setOrientation(LinearLayout.HORIZONTAL);
        primaryControls.setGravity(Gravity.CENTER_VERTICAL);
        primaryControls.setBackgroundColor(Color.parseColor(COLOR_BG_HEADER));
        primaryControls.setPadding(dp(14), dp(8), dp(14), dp(8));

        // Play / Pause
        FrameLayout btnPlayPause = new FrameLayout(this);
        playbackPlayPauseIcon = new LimenIcons.IconView(this, LimenIcons.ICON_PLAY, COLOR_TEXT_WHITE);
        btnPlayPause.addView(playbackPlayPauseIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        btnPlayPause.setOnClickListener(v -> togglePlaybackPlayPause());
        primaryControls.addView(btnPlayPause, new LinearLayout.LayoutParams(0, dp(34), 1f));

        // Velocidad 1X
        TextView speedTv = new TextView(this);
        speedTv.setText("1X");
        styleText(speedTv, 13, COLOR_TEXT_WHITE, true);
        speedTv.setGravity(Gravity.CENTER);
        primaryControls.addView(speedTv, new LinearLayout.LayoutParams(0, dp(34), 1f));

        // Paso de cuadro / Salto 10s
        FrameLayout btnStep = new FrameLayout(this);
        btnStep.addView(new LimenIcons.IconView(this, LimenIcons.ICON_REFRESH, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        btnStep.setOnClickListener(v -> {
            if (playbackPlayer != null) playbackPlayer.seekTo(playbackPlayer.getCurrentPosition() + 10000);
        });
        primaryControls.addView(btnStep, new LinearLayout.LayoutParams(0, dp(34), 1f));

        // Audio
        FrameLayout btnAudio = new FrameLayout(this);
        btnAudio.addView(new LimenIcons.IconView(this, isMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        btnAudio.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (playbackPlayer != null) playbackPlayer.setVolume(isMuted ? 0f : 1f);
            ((LimenIcons.IconView) btnAudio.getChildAt(0)).setIconType(isMuted ? LimenIcons.ICON_VOLUME_OFF : LimenIcons.ICON_VOLUME_ON);
        });
        primaryControls.addView(btnAudio, new LinearLayout.LayoutParams(0, dp(34), 1f));

        // Pantalla completa
        FrameLayout btnFull = new FrameLayout(this);
        btnFull.addView(new LimenIcons.IconView(this, LimenIcons.ICON_FULLSCREEN, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        btnFull.setOnClickListener(v -> toggleLandscapeFullscreen());
        primaryControls.addView(btnFull, new LinearLayout.LayoutParams(0, dp(34), 1f));

        page.addView(primaryControls);

        // ── 4. BARRA DE HERRAMIENTAS CIRCULARES (Captura, Video, Tijeras, PTZ - Imagen 1) ──
        LinearLayout circularTools = new LinearLayout(this);
        circularTools.setOrientation(LinearLayout.HORIZONTAL);
        circularTools.setGravity(Gravity.CENTER);
        circularTools.setBackgroundColor(Color.parseColor(COLOR_BG_CARD));
        circularTools.setPadding(dp(10), dp(12), dp(10), dp(12));

        circularTools.addView(createPlaybackCircleTool(LimenIcons.ICON_CAMERA, "Capturar", () -> {
            if (playbackPlayer != null) Toast.makeText(this, "Captura de fotograma guardada", Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, "Inicie primero la reproducción", Toast.LENGTH_SHORT).show();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        circularTools.addView(createPlaybackCircleTool(LimenIcons.ICON_VIDEO, "Grabar", () -> {
            Toast.makeText(this, "Grabando segmento de video...", Toast.LENGTH_SHORT).show();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        circularTools.addView(createPlaybackCircleTool(LimenIcons.ICON_SCISSORS, "Cortar", () -> {
            Toast.makeText(this, "Marcador de inicio de clip fijado", Toast.LENGTH_SHORT).show();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        circularTools.addView(createPlaybackCircleTool(LimenIcons.ICON_SERVER, "Equipos", this::showPlaybackSelectorModal),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        page.addView(circularTools);

        // ── 5. NAVEGADOR DE FECHA (< 2026-11-09 Todos > + Filmstrip - Imagen 1) ──
        LinearLayout datePillRow = new LinearLayout(this);
        datePillRow.setOrientation(LinearLayout.HORIZONTAL);
        datePillRow.setGravity(Gravity.CENTER_VERTICAL);
        datePillRow.setBackgroundColor(Color.parseColor(COLOR_BG_CARD));
        datePillRow.setPadding(dp(16), 0, dp(16), dp(10));

        LinearLayout datePill = new LinearLayout(this);
        datePill.setOrientation(LinearLayout.HORIZONTAL);
        datePill.setGravity(Gravity.CENTER_VERTICAL);
        datePill.setBackground(roundBg("#151d2a", 20, COLOR_BORDER_DARK, 1));
        datePill.setPadding(dp(14), dp(8), dp(14), dp(8));

        FrameLayout btnPrevDay = new FrameLayout(this);
        btnPrevDay.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_L, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        btnPrevDay.setOnClickListener(v -> stepPlaybackDate(-1));
        datePill.addView(btnPrevDay);

        playbackDateLabel = new TextView(this);
        playbackDateLabel.setText(activePlaybackDate);
        styleText(playbackDateLabel, 13, COLOR_TEXT_WHITE, true);
        playbackDateLabel.setPadding(dp(12), 0, dp(10), 0);
        playbackDateLabel.setOnClickListener(v -> showDatePickerModal());
        datePill.addView(playbackDateLabel);

        TextView filterTag = new TextView(this);
        filterTag.setText("Todos");
        styleText(filterTag, 12, COLOR_ACCENT_CYAN, false);
        datePill.addView(filterTag);

        FrameLayout btnNextDay = new FrameLayout(this);
        btnNextDay.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_R, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        btnNextDay.setOnClickListener(v -> stepPlaybackDate(1));
        btnNextDay.setPadding(dp(10), 0, 0, 0);
        datePill.addView(btnNextDay);

        datePillRow.addView(datePill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Boton Filmstrip a la derecha
        FrameLayout btnFilm = new FrameLayout(this);
        btnFilm.setBackground(roundBg("#151d2a", 10, COLOR_BORDER_DARK, 1));
        btnFilm.setPadding(dp(10), dp(8), dp(10), dp(8));
        btnFilm.addView(new LimenIcons.IconView(this, LimenIcons.ICON_FILMSTRIP, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        btnFilm.setOnClickListener(v -> showPlaybackSelectorModal());
        datePillRow.addView(btnFilm);

        page.addView(datePillRow);

        // ── 6. LINEA DE TIEMPO INTERACTIVA (Regla de 24 horas y cursor - Imagen 1 y 2) ──
        LinearLayout rulerCard = new LinearLayout(this);
        rulerCard.setOrientation(LinearLayout.VERTICAL);
        rulerCard.setBackgroundColor(Color.parseColor("#090d16"));
        rulerCard.setPadding(dp(12), dp(8), dp(12), dp(6));

        // Timestamp actual en el centro: 00:00:00
        playbackTimeDisplay = new TextView(this);
        playbackTimeDisplay.setText("00:00:00");
        styleText(playbackTimeDisplay, 14, COLOR_TEXT_WHITE, true);
        playbackTimeDisplay.setGravity(Gravity.CENTER);
        playbackTimeDisplay.setPadding(0, 0, 0, dp(4));
        rulerCard.addView(playbackTimeDisplay);

        playbackRulerView = new PlaybackTimelineRulerView(this);
        playbackRulerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        playbackRulerView.setOnTimeScrubListener((sec, finished) -> {
            int h = sec / 3600;
            int m = (sec % 3600) / 60;
            int s = sec % 60;
            playbackTimeDisplay.setText(String.format(Locale.US, "%02d:%02d:%02d", h, m, s));
            if (finished) {
                seekPlaybackToSeconds(sec);
            }
        });
        rulerCard.addView(playbackRulerView);

        page.addView(rulerCard);

        // ── 7. LISTA SCROLLABLE DE INTERVALOS ──
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.setPadding(dp(14), dp(8), dp(14), dp(16));

        playbackIntervalsContainer = new LinearLayout(this);
        playbackIntervalsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollContent.addView(playbackIntervalsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        scroll.addView(scrollContent);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ── 8. BOTTOM NAV OFICIAL ──
        page.addView(buildBottomNavBar());

        rootContainer.addView(page);

        // Si ya vino con una camara seleccionada explicitamente, cargar; sino aguardar a seleccion
        if (isPlaybackLoaded && activePlaybackCamera != null) {
            setupPlaybackCellWithPlayer();
            loadPlaybackTimeline();
        } else {
            renderPlaybackPromptToSelect();
        }
    }

    private View createPlaybackCircleTool(int iconType, String label, Runnable action) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(circleBg(COLOR_BG_ELEVATED, COLOR_BORDER_DARK, 1));
        circle.addView(new LimenIcons.IconView(this, iconType, COLOR_TEXT_WHITE),
                new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        circle.setOnClickListener(v -> action.run());
        col.addView(circle, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView tv = new TextView(this);
        tv.setText(label);
        styleText(tv, 10, COLOR_TEXT_GRAY, false);
        tv.setPadding(0, dp(4), 0, 0);
        col.addView(tv);

        return col;
    }

    private void updatePlaybackGridFocus() {
        for (int i = 0; i < 4; i++) {
            if (playbackCells[i] == null) continue;
            if (i == selectedPlaybackCell) {
                playbackCells[i].setBackground(roundBg("#0b0f19", 4, COLOR_ACCENT_CYAN, 2));
            } else {
                playbackCells[i].setBackground(roundBg("#0b0f19", 4, COLOR_BORDER_DARK, 1));
            }
        }
    }

    private void stepPlaybackDate(int deltaDays) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = sdf.parse(activePlaybackDate);
            if (d != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                c.add(Calendar.DAY_OF_MONTH, deltaDays);
                activePlaybackDate = sdf.format(c.getTime());
                if (playbackDateLabel != null) playbackDateLabel.setText(activePlaybackDate);
                if (isPlaybackLoaded && activePlaybackCamera != null) {
                    loadPlaybackTimeline();
                }
            }
        } catch (Exception ignored) {}
    }

    private void showDatePickerModal() {
        Calendar cal = Calendar.getInstance();
        try {
            if (activePlaybackDate != null) {
                String[] parts = activePlaybackDate.split("-");
                cal.set(Calendar.YEAR, Integer.parseInt(parts[0]));
                cal.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
                cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[2]));
            }
        } catch (Exception ignored) {}

        DatePickerDialog dpd = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            activePlaybackDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            if (playbackDateLabel != null) playbackDateLabel.setText(activePlaybackDate);
            if (isPlaybackLoaded && activePlaybackCamera != null) {
                loadPlaybackTimeline();
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dpd.show();
    }

    private void renderPlaybackPromptToSelect() {
        if (playbackIntervalsContainer == null) return;
        playbackIntervalsContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(roundBg(COLOR_BG_CARD, 12, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(20), dp(32), dp(20), dp(32));

        LimenIcons.IconView icon = new LimenIcons.IconView(this, LimenIcons.ICON_PLUS, COLOR_ACCENT_CYAN);
        card.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = new TextView(this);
        title.setText("Seleccionar canal para reproducir");
        styleText(title, 15, COLOR_TEXT_WHITE, true);
        title.setPadding(0, dp(12), 0, dp(4));
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Toque una ventana superior o el botón de abajo para elegir un canal y consultar sus grabaciones.");
        styleText(sub, 12, COLOR_TEXT_GRAY, false);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub);

        Button btnChoose = new Button(this);
        btnChoose.setText("Elegir Canal y Fecha");
        btnChoose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnChoose.setTextColor(Color.WHITE);
        btnChoose.setBackground(roundBg("#4f46e5", 8, null, 0));
        btnChoose.setPadding(dp(20), dp(6), dp(20), dp(6));
        btnChoose.setOnClickListener(v -> showPlaybackSelectorModal());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        btnLp.topMargin = dp(16);
        card.addView(btnChoose, btnLp);

        playbackIntervalsContainer.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    // =========================================================================
    // MODAL DE SELECCIÓN DE EQUIPO Y CANAL (Imagen 3)
    // =========================================================================
    private void showPlaybackSelectorModal() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(COLOR_BG_CARD, 16, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Titulo
        TextView title = new TextView(this);
        title.setText("Seleccionar Canal y Fecha");
        styleText(title, 16, COLOR_TEXT_WHITE, true);
        title.setPadding(0, 0, 0, dp(12));
        card.addView(title);

        // Barra de busqueda
        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(roundBg("#151d2a", 10, COLOR_BORDER_DARK, 1));
        searchBox.setPadding(dp(12), dp(2), dp(12), dp(2));
        searchBox.addView(new LimenIcons.IconView(this, LimenIcons.ICON_SEARCH, COLOR_TEXT_GRAY),
                new LinearLayout.LayoutParams(dp(16), dp(16)));

        EditText searchEt = new EditText(this);
        searchEt.setHint("Buscar equipo o cámara...");
        searchEt.setHintTextColor(Color.parseColor(COLOR_TEXT_MUTED));
        styleText(searchEt, 13, COLOR_TEXT_WHITE, false);
        searchEt.setBackgroundColor(Color.TRANSPARENT);
        searchEt.setSingleLine(true);
        searchEt.setPadding(dp(10), dp(6), dp(6), dp(6));
        searchBox.addView(searchEt, new LinearLayout.LayoutParams(0, dp(40), 1f));
        card.addView(searchBox);

        // Selector de Fecha dentro del modal
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.setPadding(0, dp(12), 0, dp(10));

        TextView dateLbl = new TextView(this);
        dateLbl.setText("Fecha de consulta:");
        styleText(dateLbl, 13, COLOR_TEXT_GRAY, false);
        dateRow.addView(dateLbl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView dateBtn = new TextView(this);
        dateBtn.setText(activePlaybackDate);
        styleText(dateBtn, 13, COLOR_ACCENT_CYAN, true);
        dateBtn.setBackground(roundBg("#151d2a", 8, COLOR_BORDER_DARK, 1));
        dateBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        dateBtn.setOnClickListener(v -> {
            showDatePickerModal();
            dateBtn.setText(activePlaybackDate);
        });
        dateRow.addView(dateBtn);
        card.addView(dateRow);

        // Contenedor scrollable de equipos y canales (Acordeon)
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        card.addView(scroll);

        final CameraItem[] selectedChoice = new CameraItem[]{activePlaybackCamera};

        Button btnStartPlayback = new Button(this);
        btnStartPlayback.setText("Comenzar Playback");
        btnStartPlayback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnStartPlayback.setTextColor(Color.WHITE);
        btnStartPlayback.setBackground(roundBg(selectedChoice[0] != null ? "#4f46e5" : "#27273a", 10, null, 0));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        startLp.topMargin = dp(14);
        card.addView(btnStartPlayback, startLp);

        // Poblar acordeon de equipos y canales
        Runnable renderList = () -> {
            listContainer.removeAllViews();
            String filter = searchEt.getText().toString().trim().toLowerCase();
            Map<String, List<CameraItem>> grouped = getGroupedCameras(filter.isEmpty() ? null : filter);

            for (Map.Entry<String, List<CameraItem>> entry : grouped.entrySet()) {
                final String groupName = entry.getKey();
                final List<CameraItem> cams = entry.getValue();

                LinearLayout groupHeader = new LinearLayout(this);
                groupHeader.setOrientation(LinearLayout.HORIZONTAL);
                groupHeader.setGravity(Gravity.CENTER_VERTICAL);
                groupHeader.setBackground(roundBg("#151d2a", 8, COLOR_BORDER_DARK, 1));
                groupHeader.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout.LayoutParams ghlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ghlp.bottomMargin = dp(4);

                groupHeader.addView(new LimenIcons.IconView(this, LimenIcons.ICON_CHEVRON_DOWN, COLOR_ACCENT_CYAN),
                        new LinearLayout.LayoutParams(dp(14), dp(14)));

                TextView gTitle = new TextView(this);
                gTitle.setText(groupName + " (" + cams.size() + " canales)");
                styleText(gTitle, 13, COLOR_TEXT_WHITE, true);
                gTitle.setPadding(dp(10), 0, 0, 0);
                groupHeader.addView(gTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                listContainer.addView(groupHeader, ghlp);

                LinearLayout channelsCol = new LinearLayout(this);
                channelsCol.setOrientation(LinearLayout.VERTICAL);
                channelsCol.setPadding(dp(16), 0, dp(4), dp(6));

                for (CameraItem cam : cams) {
                    LinearLayout chRow = new LinearLayout(this);
                    chRow.setOrientation(LinearLayout.HORIZONTAL);
                    chRow.setGravity(Gravity.CENTER_VERTICAL);
                    chRow.setPadding(dp(8), dp(8), dp(8), dp(8));

                    TextView chName = new TextView(this);
                    chName.setText(cam.getShortChannelLabel());
                    styleText(chName, 13, COLOR_TEXT_WHITE, false);
                    chRow.addView(chName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    // Radio indicator
                    boolean isSelected = selectedChoice[0] != null && selectedChoice[0].id == cam.id;
                    FrameLayout radioCircle = new FrameLayout(this);
                    radioCircle.setBackground(circleBg(isSelected ? COLOR_ACCENT_CYAN : COLOR_BG_CARD,
                            isSelected ? COLOR_ACCENT_CYAN : COLOR_TEXT_GRAY, 2));
                    chRow.addView(radioCircle, new LinearLayout.LayoutParams(dp(18), dp(18)));

                    chRow.setOnClickListener(v -> {
                        selectedChoice[0] = cam;
                        btnStartPlayback.setBackground(roundBg("#4f46e5", 10, null, 0));
                        showPlaybackSelectorModal();
                        dialog.dismiss();
                    });

                    channelsCol.addView(chRow);
                }
                listContainer.addView(channelsCol);
            }
        };

        searchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderList.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        renderList.run();

        btnStartPlayback.setOnClickListener(v -> {
            if (selectedChoice[0] == null) {
                Toast.makeText(this, "Seleccione un canal para continuar", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            activePlaybackCamera = selectedChoice[0];
            isPlaybackLoaded = true;
            if (playbackHeaderTitle != null) playbackHeaderTitle.setText(activePlaybackCamera.getShortChannelLabel());
            if (playbackHeaderSub != null) playbackHeaderSub.setText(activePlaybackCamera.getGroupTitle());

            setupPlaybackCellWithPlayer();
            loadPlaybackTimeline();
        });

        dialog.setContentView(card);
        dialog.show();
    }

    private void setupPlaybackCellWithPlayer() {
        if (playbackCells[selectedPlaybackCell] == null) return;
        FrameLayout cell = playbackCells[selectedPlaybackCell];
        cell.removeAllViews();

        playbackPlayerView = new PlayerView(this);
        playbackPlayerView.setUseController(false);
        playbackPlayerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        cell.addView(playbackPlayerView);

        // Spinner
        playbackSpinner = new ProgressBar(this);
        playbackSpinner.setIndeterminate(true);
        playbackSpinner.setVisibility(View.VISIBLE);
        cell.addView(playbackSpinner, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));

        // Overlays de texto: CAM 1 arriba izquierda
        playbackCellCamLabel = new TextView(this);
        playbackCellCamLabel.setText(activePlaybackCamera != null ? ("CAM " + activePlaybackCamera.canal) : "");
        styleText(playbackCellCamLabel, 11, COLOR_TEXT_WHITE, true);
        playbackCellCamLabel.setBackground(roundBg("#88000000", 3, null, 0));
        playbackCellCamLabel.setPadding(dp(5), dp(2), dp(5), dp(2));
        FrameLayout.LayoutParams camLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        camLp.setMargins(dp(6), dp(6), 0, 0);
        cell.addView(playbackCellCamLabel, camLp);

        // Overlay fecha y hora arriba derecha
        playbackCellTimeLabel = new TextView(this);
        playbackCellTimeLabel.setText(activePlaybackDate);
        styleText(playbackCellTimeLabel, 10, COLOR_TEXT_WHITE, false);
        playbackCellTimeLabel.setBackground(roundBg("#88000000", 3, null, 0));
        playbackCellTimeLabel.setPadding(dp(5), dp(2), dp(5), dp(2));
        FrameLayout.LayoutParams timeLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        timeLp.setMargins(0, dp(6), dp(6), 0);
        cell.addView(playbackCellTimeLabel, timeLp);
    }

    private void togglePlaybackPlayPause() {
        if (playbackPlayer == null) return;
        if (playbackPlayer.isPlaying()) {
            playbackPlayer.pause();
            isPlaybackPlaying = false;
            if (playbackPlayPauseIcon != null) playbackPlayPauseIcon.setIconType(LimenIcons.ICON_PLAY);
        } else {
            playbackPlayer.play();
            isPlaybackPlaying = true;
            if (playbackPlayPauseIcon != null) playbackPlayPauseIcon.setIconType(LimenIcons.ICON_PAUSE);
        }
    }

    private void loadPlaybackTimeline() {
        if (activePlaybackCamera == null) return;
        final int targetCamId = activePlaybackCamera.id;
        final String targetDate = activePlaybackDate;

        if (playbackSpinner != null) playbackSpinner.setVisibility(View.VISIBLE);
        if (playbackIntervalsContainer != null) {
            playbackIntervalsContainer.removeAllViews();
            TextView loadingTv = new TextView(this);
            loadingTv.setText("Buscando grabaciones en el disco del DVR...");
            styleText(loadingTv, 12, COLOR_TEXT_GRAY, false);
            loadingTv.setPadding(dp(8), dp(24), dp(8), dp(24));
            loadingTv.setGravity(Gravity.CENTER);
            playbackIntervalsContainer.addView(loadingTv);
        }

        executor.execute(() -> {
            try {
                String url = BASE_URL + "/api/camaras/" + targetCamId + "/recordings/timeline?date=" + targetDate;
                HttpResponse resp = httpGet(url, authToken);

                mainHandler.post(() -> {
                    if (currentTab != 4 || activePlaybackCamera == null || activePlaybackCamera.id != targetCamId) return;
                    if (playbackSpinner != null) playbackSpinner.setVisibility(View.GONE);

                    if (resp.code != 200 || resp.body == null || resp.body.isEmpty()) {
                        renderPlaybackEmptyState("No se pudo conectar con el DVR o falló la autenticación.");
                        return;
                    }

                    try {
                        JSONObject json = new JSONObject(resp.body);
                        boolean hasHdd = json.optBoolean("hasHdd", false);
                        String msg = json.optString("message", "");
                        JSONArray arr = json.optJSONArray("intervals");

                        playbackIntervals.clear();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                RecordingInterval item = new RecordingInterval();
                                item.start = obj.optString("start", "00:00:00");
                                item.end = obj.optString("end", "00:00:00");
                                item.startTime = obj.optString("startTime", "");
                                item.endTime = obj.optString("endTime", "");
                                item.startSeconds = obj.optInt("startSeconds", 0);
                                item.endSeconds = obj.optInt("endSeconds", 0);
                                item.duration = obj.optInt("duration", 0);
                                item.type = obj.optString("type", "regular");
                                item.streamUrl = obj.optString("stream_url", "");
                                playbackIntervals.add(item);
                            }
                        }

                        if (playbackRulerView != null) {
                            playbackRulerView.setIntervals(playbackIntervals);
                        }

                        renderPlaybackIntervalsList(hasHdd, msg);

                        if (!playbackIntervals.isEmpty()) {
                            // Reproducir primer clip inmediatamente
                            playRecordingInterval(playbackIntervals.get(0));
                        }

                    } catch (Exception e) {
                        renderPlaybackEmptyState("Error al procesar grabaciones: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (playbackSpinner != null) playbackSpinner.setVisibility(View.GONE);
                    renderPlaybackEmptyState("Error de conexión: " + e.getMessage());
                });
            }
        });
    }

    private void seekPlaybackToSeconds(int seconds) {
        if (playbackIntervals.isEmpty()) return;
        // Buscar si los segundos caen dentro de un clip existente
        for (RecordingInterval it : playbackIntervals) {
            if (seconds >= it.startSeconds && seconds <= it.endSeconds) {
                playRecordingInterval(it);
                return;
            }
        }
        // Si no cae dentro, buscar el proximo clip
        for (RecordingInterval it : playbackIntervals) {
            if (it.startSeconds >= seconds) {
                playRecordingInterval(it);
                return;
            }
        }
    }

    private void renderPlaybackEmptyState(String customMsg) {
        if (playbackIntervalsContainer == null) return;
        playbackIntervalsContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(roundBg(COLOR_BG_CARD, 12, COLOR_BORDER_DARK, 1));
        card.setPadding(dp(20), dp(30), dp(20), dp(30));

        LimenIcons.IconView icon = new LimenIcons.IconView(this, LimenIcons.ICON_CALENDAR, COLOR_TEXT_MUTED);
        card.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = new TextView(this);
        title.setText("Sin grabaciones registradas");
        styleText(title, 14, COLOR_TEXT_WHITE, true);
        title.setPadding(0, dp(10), 0, dp(4));
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText(customMsg != null ? customMsg : "No se encontraron archivos grabados para esta fecha. Compruebe si el DVR posee almacenamiento interno (HDD).");
        styleText(sub, 12, COLOR_TEXT_GRAY, false);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub);

        playbackIntervalsContainer.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void renderPlaybackIntervalsList(boolean hasHdd, String serverMsg) {
        if (playbackIntervalsContainer == null) return;
        playbackIntervalsContainer.removeAllViews();

        if (playbackIntervals.isEmpty()) {
            renderPlaybackEmptyState(serverMsg.isEmpty() ? null : serverMsg);
            return;
        }

        TextView countHeader = new TextView(this);
        countHeader.setText("Clips grabados (" + playbackIntervals.size() + ")");
        styleText(countHeader, 13, COLOR_TEXT_WHITE, true);
        countHeader.setPadding(dp(4), dp(6), dp(4), dp(10));
        playbackIntervalsContainer.addView(countHeader);

        for (int i = 0; i < playbackIntervals.size(); i++) {
            final RecordingInterval it = playbackIntervals.get(i);

            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setBackground(roundBg(COLOR_BG_CARD, 10, COLOR_BORDER_DARK, 1));
            itemRow.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = dp(8);

            boolean isMotion = "motion".equals(it.type);
            String badgeColor = isMotion ? "#f59e0b" : "#38bdf8";
            FrameLayout iconCircle = new FrameLayout(this);
            iconCircle.setBackground(circleBg(COLOR_BG_ELEVATED, badgeColor, 1));
            iconCircle.addView(new LimenIcons.IconView(this, LimenIcons.ICON_VIDEO, badgeColor),
                    new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
            itemRow.addView(iconCircle, new LinearLayout.LayoutParams(dp(32), dp(32)));

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(10), 0, dp(10), 0);

            TextView timeTv = new TextView(this);
            timeTv.setText(it.start + " - " + it.end);
            styleText(timeTv, 13, COLOR_TEXT_WHITE, true);
            textCol.addView(timeTv);

            TextView typeTv = new TextView(this);
            int mins = it.duration / 60;
            int secs = it.duration % 60;
            String durStr = mins > 0 ? (mins + "m " + secs + "s") : (secs + "s");
            typeTv.setText((isMotion ? "Detección de Movimiento" : "Grabación Continua") + " · " + durStr);
            styleText(typeTv, 10, COLOR_TEXT_GRAY, false);
            textCol.addView(typeTv);

            itemRow.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button btnPlay = new Button(this);
            btnPlay.setText("Ver");
            btnPlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnPlay.setTextColor(Color.WHITE);
            btnPlay.setBackground(roundBg("#4f46e5", 6, null, 0));
            btnPlay.setPadding(dp(14), dp(4), dp(14), dp(4));
            btnPlay.setOnClickListener(v -> playRecordingInterval(it));
            itemRow.addView(btnPlay, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

            playbackIntervalsContainer.addView(itemRow, lp);
        }
    }

    private void playRecordingInterval(RecordingInterval it) {
        if (it == null || it.streamUrl == null || it.streamUrl.isEmpty()) return;
        currentlyPlayingInterval = it;

        if (playbackSpinner != null) playbackSpinner.setVisibility(View.VISIBLE);
        if (playbackTimeDisplay != null) playbackTimeDisplay.setText(it.start);
        if (playbackCellTimeLabel != null) playbackCellTimeLabel.setText(activePlaybackDate + " " + it.start);
        if (playbackRulerView != null) playbackRulerView.setCurrentSeconds(it.startSeconds);

        try {
            if (playbackPlayer == null) {
                DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(2000, 10000, 500, 1500)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build();

                playbackPlayer = new ExoPlayer.Builder(this)
                        .setLoadControl(loadControl)
                        .build();

                playbackPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);

                playbackPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) {
                            if (playbackSpinner != null) playbackSpinner.setVisibility(View.GONE);
                            if (playbackPlayPauseIcon != null) playbackPlayPauseIcon.setIconType(LimenIcons.ICON_PAUSE);
                            isPlaybackPlaying = true;
                        } else if (state == Player.STATE_BUFFERING) {
                            if (playbackSpinner != null) playbackSpinner.setVisibility(View.VISIBLE);
                        } else if (state == Player.STATE_ENDED) {
                            if (playbackPlayPauseIcon != null) playbackPlayPauseIcon.setIconType(LimenIcons.ICON_PLAY);
                            isPlaybackPlaying = false;
                        }
                    }

                    @Override
                    public void onPlayerError(@NonNull PlaybackException error) {
                        if (playbackSpinner != null) playbackSpinner.setVisibility(View.GONE);
                        if (playbackPlayPauseIcon != null) playbackPlayPauseIcon.setIconType(LimenIcons.ICON_PLAY);
                        isPlaybackPlaying = false;
                        Toast.makeText(MainActivity.this, "Error de transmisión de grabación", Toast.LENGTH_SHORT).show();
                    }
                });

                if (playbackPlayerView != null) {
                    playbackPlayerView.setPlayer(playbackPlayer);
                }
            }

            String fullUrl = BASE_URL + it.streamUrl;
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(fullUrl));
            playbackPlayer.setMediaItem(mediaItem);
            playbackPlayer.prepare();
            playbackPlayer.setPlayWhenReady(true);
        } catch (Throwable t) {
            reportCrashToServer("playRecordingInterval error", t);
        }
    }
}

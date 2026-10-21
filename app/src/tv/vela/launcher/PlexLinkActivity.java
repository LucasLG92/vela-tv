package tv.vela.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.*;

/** Plex's device PIN flow: the user signs in on Plex, never types their password here. */
public class PlexLinkActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();
    private PlexClient plex;
    private LinearLayout panel;
    private TextView detail, code;
    private long pinId, expiresAt;
    private String pinCode;
    private ScheduledFuture<?> poll;
    private boolean closed;
    private boolean retryShown;
    private static String t(String pt,String en,String es){return Ui.tr(pt,en,es);}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this); plex = new PlexClient(this);
        panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setGravity(Gravity.CENTER);
        panel.setPadding(64, 40, 64, 40);
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF15232D, 0xFF080C12}));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(panel,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
        text(t("Sua biblioteca, no início.","Your library, at home.","Tu biblioteca, en el inicio."), 30, Color.WHITE, Ui.displaySemibold);
        detail = text(t("Conecte Plex para ver seus cartazes e continuar assistindo.","Link Plex to see your posters and continue watching.","Vincula Plex para ver tus carteles y continuar lo que estabas viendo."), 17, 0xFFAFBAC5, Ui.regular);
        code = text(t("Conectando ao Plex…","Connecting to Plex…","Conectando con Plex…"), 46, 0xFFFFC36B, Ui.displaySemibold);
        text(t("A senha é informada apenas no Plex.","Enter your password only on Plex.","La contraseña se introduce únicamente en Plex."), 14, 0xFF82909C, Ui.regular);
        io.execute(() -> {
            if (!plex.prefs.getString("accountToken", "").isEmpty()) {
                try { showServers(); return; }
                catch (Exception ignored) { error(t("Não foi possível consultar seus servidores. Sua sessão foi preservada.","Could not reach your servers. Your session has been kept.","No se han podido consultar tus servidores. Tu sesión se conserva."));return; }
            }
            try {
                JSONObject pin = plex.createPin();
                pinId = pin.getLong("id"); pinCode = pin.getString("code");
                expiresAt = android.os.SystemClock.elapsedRealtime() + pin.optLong("expiresIn", 900) * 1000;
                main.post(() -> {
                    if (closed) return;
                    detail.setText(t("Abra plex.tv/link no celular ou computador e digite este código:","Open plex.tv/link on your phone or computer and enter this code:","Abre plex.tv/link en el móvil u ordenador e introduce este código:"));
                    code.setText(pinCode);
                });
                poll = io.scheduleWithFixedDelay(this::check, 3, 3, TimeUnit.SECONDS);
            } catch (Exception e) { error(t("Não foi possível conectar. Verifique a Internet e tente novamente.","Could not connect. Check your Internet connection and try again.","No se ha podido iniciar la conexión. Comprueba Internet y vuelve a intentarlo.")); }
        });
    }
    private TextView text(String value, int size, int color, Typeface face) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(face); text.setGravity(Gravity.CENTER); text.setPadding(0, 12, 0, 12);
        panel.addView(text, new LinearLayout.LayoutParams(-1, -2)); return text;
    }
    private void check() {
        if (closed) return;
        if (android.os.SystemClock.elapsedRealtime() >= expiresAt) {
            if (poll != null) poll.cancel(false);
            error(t("O código expirou. Tente novamente para obter outro.","The code expired. Retry to get another one.","El código ha caducado. Pulsa Reintentar para obtener otro.")); return;
        }
        try {
            if (!plex.checkPin(pinId, pinCode)) return;
            if (poll != null) poll.cancel(false);
            showServers();
        } catch (Exception ignored) { /* Transient connection failures can recover before the PIN expires. */ }
    }
    private void showServers() throws Exception {
            List<PlexClient.Server> servers = plex.servers();
            if (servers.size() == 1) {
                try { plex.select(servers.get(0)); main.post(() -> { if (!closed) { LibraryClient.activate(this,"Plex");setResult(RESULT_OK); finish(); } }); return; }
                catch (Exception e) { error(e.getMessage()); return; }
            }
            main.post(() -> {
                if (closed) return;
                code.setVisibility(android.view.View.GONE);
                detail.setText(servers.isEmpty() ? t("Nenhum servidor seguro encontrado. Verifique Plex Media Server.","No secure server found. Check Plex Media Server.","No se ha encontrado ningún servidor con conexión segura. Comprueba Plex Media Server.")
                        : t("Escolha o servidor que deseja mostrar:","Choose the server to show:","Elige el servidor que quieres mostrar:"));
                for (PlexClient.Server server : servers) {
                    Button button = new Button(this); button.setText(server.name); button.setAllCaps(false);
                    panel.addView(button, new LinearLayout.LayoutParams(550, 70));
                    button.setOnClickListener(v -> {
                        detail.setText(t("Conectando a ","Connecting to ","Conectando con ") + server.name + "…"); button.setEnabled(false);
                        io.execute(() -> {
                            try { plex.select(server); main.post(() -> { if (!closed) { LibraryClient.activate(this,"Plex");setResult(RESULT_OK); finish(); } }); }
                            catch (Exception e) { main.post(() -> { if (!closed) { detail.setText(e.getMessage()); button.setEnabled(true); } }); }
                        });
                    });
                }
                if (servers.size() > 0) panel.getChildAt(panel.getChildCount() - servers.size()).requestFocus();
                else retry();
            });
    }
    private void retry(){if(retryShown)return;retryShown=true;Button retry=new Button(this);retry.setText(t("Tentar novamente","Retry","Reintentar"));retry.setAllCaps(false);panel.addView(retry,new LinearLayout.LayoutParams(550,70));retry.setOnClickListener(v->{startActivity(new android.content.Intent(this,PlexLinkActivity.class));finish();});retry.requestFocus();}
    private void error(String message) { main.post(() -> { if (!closed) { detail.setText(message); code.setText("Plex");retry(); } }); }
    @Override protected void onDestroy() { closed = true; if (poll != null) poll.cancel(true); io.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
}

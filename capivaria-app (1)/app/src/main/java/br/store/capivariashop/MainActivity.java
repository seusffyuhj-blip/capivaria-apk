package br.store.capivariashop;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {

    private static final int GREEN = 0xFF1B5E20;
    private static final int BG = 0xFFF3F6F2;
    private static final int TEXT = 0xFF14211A;
    private static final int MUTED = 0xFF55655B;
    private static final int LINE = 0xFFDFE7DF;
    private static final int SOFT = 0xFFE1EFE2;
    private static final int WARN = 0xFFFFF3CD;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Auth auth;
    private Api api;
    private FrameLayout holder;
    private ScrollView scroll;
    private final TextView[] tabViews = new TextView[3];
    private int tab = 0;
    private boolean inDetail = false;
    private Runnable backAction = null;

    private JSONObject catalog;
    private String catalogError;
    private JSONObject profile;
    private boolean isAdmin;
    private JSONArray orders;
    private String currentOrderId;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            // Atualiza o catálogo a cada 30 s enquanto a loja está visível
            if (tab == 0 && !inDetail) loadCatalog();
            handler.postDelayed(this, 30000);
        }
    };

    // ---------------------------------------------------------------- ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        auth = new Auth(getApplicationContext());
        api = new Api(auth);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        holder = new FrameLayout(this);
        root.addView(holder, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.WHITE);
        String[] names = {"Loja", "Pedidos", "Conta"};
        for (int i = 0; i < 3; i++) {
            TextView t = text(names[i], 14, true, MUTED);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(14), 0, dp(14));
            final int idx = i;
            t.setOnClickListener(v -> selectTab(idx));
            bar.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
            tabViews[i] = t;
        }
        root.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));
        setContentView(root);

        selectTab(0);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCatalog();
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, 30000);
        if (auth.isLoggedIn() && profile == null) {
            api.async(() -> api.json("GET", "/me", null, true), (r, e) -> {
                if (e == null) applyMe(r);
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poll);
    }

    @Override
    public void onBackPressed() {
        if (inDetail && backAction != null) {
            backAction.run();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- navegação

    private void selectTab(int i) {
        tab = i;
        inDetail = false;
        backAction = null;
        for (int k = 0; k < 3; k++) {
            tabViews[k].setTextColor(k == i ? GREEN : MUTED);
        }
        if (i == 0) {
            showStore();
            if (catalog == null) loadCatalog();
        } else if (i == 1) {
            showOrders();
        } else {
            showAccount();
        }
    }

    private LinearLayout newScreen() {
        holder.removeAllViews();
        scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        holder.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        return col;
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        final Uri u = i.getData();
        if (u == null || !Config.REDIRECT_SCHEME.equals(u.getScheme())) return;
        setIntent(new Intent());
        toast("Entrando…");
        api.async(() -> {
            auth.finishLogin(u);
            return api.json("GET", "/me", null, true);
        }, (r, e) -> {
            if (e != null) {
                toast(msgOf(e));
                selectTab(2);
                return;
            }
            applyMe(r);
            orders = null;
            selectTab(2);
            toast("Você entrou na sua conta.");
        });
    }

    private void startLogin() {
        toast("Abrindo o navegador…");
        api.async(() -> auth.beginLogin(), (uri, e) -> {
            if (e != null) {
                toast(msgOf(e));
                return;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception x) {
                toast("Não encontrei um navegador no aparelho.");
            }
        });
    }

    private void logout() {
        auth.logout();
        profile = null;
        isAdmin = false;
        orders = null;
        selectTab(2);
    }

    // ---------------------------------------------------------------- dados da conta

    private void applyMe(JSONObject r) {
        JSONObject p = r.optJSONObject("profile");
        profile = p != null ? p : new JSONObject();
        isAdmin = r.optBoolean("isAdmin", false);
    }

    /** Mensagem de bloqueio da conta, ou null quando a conta está livre. */
    private String blockedMessage() {
        if (profile == null) return null;
        boolean banned = profile.optBoolean("banned", false);
        boolean removed = profile.has("removed_at") && !profile.isNull("removed_at");
        if (!banned && !removed) return null;
        StringBuilder sb = new StringBuilder(removed ? "Sua conta foi removida." : "Sua conta está bloqueada.");
        String reason = J.str(profile, "removal_reason");
        String details = J.str(profile, "removal_details");
        if (reason != null) sb.append("\nMotivo: ").append(reason);
        if (details != null) sb.append("\n").append(details);
        sb.append("\nSeu histórico continua disponível, mas novas compras e mensagens estão bloqueadas.");
        return sb.toString();
    }

    private void ensureProfile(Runnable next) {
        if (profile != null || !auth.isLoggedIn()) {
            next.run();
            return;
        }
        api.async(() -> api.json("GET", "/me", null, true), (r, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            applyMe(r);
            next.run();
        });
    }

    // ---------------------------------------------------------------- LOJA

    private void loadCatalog() {
        api.async(() -> api.json("GET", "/catalog", null, false), (r, e) -> {
            if (e != null) {
                if (catalog == null) {
                    catalogError = "Não consegui carregar a loja. Verifique sua conexão.";
                    if (tab == 0 && !inDetail) showStore();
                }
                return;
            }
            catalog = r;
            catalogError = null;
            if (tab == 0 && !inDetail) {
                final int y = scroll != null ? scroll.getScrollY() : 0;
                showStore();
                scroll.post(() -> scroll.scrollTo(0, y));
            }
        });
    }

    private void showStore() {
        LinearLayout col = newScreen();
        header(col);

        String blocked = blockedMessage();
        if (blocked != null) add(col, notice(blocked), 12);

        if (catalog == null) {
            if (catalogError != null) {
                add(col, text(catalogError, 15, false, MUTED), 16);
                add(col, button("Tentar de novo", true, v -> {
                    catalogError = null;
                    showStore();
                    loadCatalog();
                }), 12);
            } else {
                add(col, text("Carregando produtos…", 15, false, MUTED), 16);
            }
            return;
        }

        JSONArray ann = J.arr(catalog, "announcements");
        for (int i = 0; i < ann.length(); i++) {
            JSONObject a = ann.optJSONObject(i);
            String s = a != null ? J.str(a, "message", "text", "content", "title") : ann.optString(i, null);
            if (s != null) add(col, notice(s), 12);
        }

        JSONArray products = J.arr(catalog, "products");
        if (products.length() == 0) {
            add(col, text("Nenhum produto disponível agora.", 15, false, MUTED), 16);
        }
        for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p != null) add(col, productCard(p), 12);
        }
    }

    private void header(LinearLayout col) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_capivaria);
        logo.setAdjustViewBounds(true);
        row.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(12), 0, 0, 0);
        t.addView(text("Capivaria Shop BR", 20, true, GREEN));
        t.addView(text("Contas e itens de Blox Fruits", 13, false, MUTED));
        row.addView(t);
        col.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private View productCard(final JSONObject p) {
        LinearLayout c = card();
        String title = J.str(p, "title", "titulo", "name", "nome");
        String desc = J.str(p, "description", "descricao");
        double price = J.num(p, -1, "price", "preco", "amount");
        boolean soldOut = J.bool(p, "soldOut", "sold_out", "esgotado", "outOfStock", "out_of_stock");
        boolean isNew = J.bool(p, "isNew", "is_new", "novo", "new", "badgeNew", "new_badge");
        double discount = J.num(p, 0, "discount", "desconto");

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView tt = text(title != null ? title : "Produto", 17, true, TEXT);
        top.addView(tt, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (isNew) top.addView(chip("Novo", SOFT, GREEN));
        if (soldOut) top.addView(chip("Esgotado", WARN, 0xFF7A5B00));
        c.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        if (desc != null) add(c, text(desc, 14, false, MUTED), 6);
        if (price >= 0) add(c, text(J.money(price), 18, true, GREEN), 8);
        if (discount > 0) add(c, text("Desconto da loja incluso. O valor final aparece ao gerar o Pix.", 12, false, MUTED), 2);

        Button buy = button(soldOut ? "Esgotado" : "Comprar", !soldOut, v -> onBuy(p));
        buy.setEnabled(!soldOut);
        buy.setAlpha(soldOut ? 0.5f : 1f);
        add(c, buy, 10);
        return c;
    }

    private void onBuy(final JSONObject p) {
        if (!auth.isLoggedIn()) {
            new AlertDialog.Builder(this)
                    .setTitle("Entre para comprar")
                    .setMessage("Entre ou crie sua conta da loja pelo navegador. O app não vê sua senha.")
                    .setPositiveButton("Entrar", (d, w) -> startLogin())
                    .setNegativeButton("Agora não", null)
                    .show();
            return;
        }
        ensureProfile(() -> {
            String blocked = blockedMessage();
            if (blocked != null) {
                new AlertDialog.Builder(this).setMessage(blocked).setPositiveButton("OK", null).show();
                return;
            }
            askOrder(p);
        });
    }

    private void askOrder(final JSONObject p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText nome = edit("Seu nome");
        final EditText game = edit("Seu nome no Roblox");
        String pn = J.str(profile, "name", "full_name", "display_name");
        if (pn != null) nome.setText(pn);
        box.addView(nome);
        box.addView(game);
        String title = J.str(p, "title", "titulo", "name", "nome");
        new AlertDialog.Builder(this)
                .setTitle("Comprar " + (title != null ? title : "produto"))
                .setView(box)
                .setPositiveButton("Gerar Pix", (d, w) -> {
                    String n = nome.getText().toString().trim();
                    String g = game.getText().toString().trim();
                    if (n.isEmpty() || g.isEmpty()) {
                        toast("Preencha seu nome e o nome no jogo.");
                        return;
                    }
                    createOrder(p, n, g);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void createOrder(final JSONObject p, final String buyerName, final String gameName) {
        toast("Gerando o pedido…");
        api.async(() -> {
            JSONObject b = new JSONObject();
            b.put("productId", p.opt("id"));
            b.put("buyerName", buyerName);
            b.put("gameName", gameName);
            return api.json("POST", "/orders", b, true);
        }, (r, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            orders = null;
            showPix(r.optJSONObject("order"), r.optJSONObject("payment"));
        });
    }

    // ---------------------------------------------------------------- PIX

    private void showPix(JSONObject order, JSONObject payment) {
        tab = 1;
        for (int k = 0; k < 3; k++) tabViews[k].setTextColor(k == 1 ? GREEN : MUTED);
        inDetail = true;
        backAction = () -> selectTab(1);
        LinearLayout col = newScreen();
        backRow(col, "← Pedidos", backAction);

        add(col, text("Pague com Pix", 22, true, TEXT), 8);
        final String orderId = order != null ? J.str(order, "id") : null;
        final String payload = payment != null ? J.str(payment, "payload") : null;
        double amount = payment != null ? J.num(payment, -1, "amount") : -1;

        if (amount >= 0) add(col, text("Valor: " + J.money(amount), 18, true, GREEN), 6);

        if (payload == null) {
            add(col, notice("A loja não enviou o código Pix. Abra o pedido em Pedidos para ver o andamento."), 12);
        } else {
            Bitmap qr = qr(payload, 720);
            if (qr != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(qr);
                iv.setAdjustViewBounds(true);
                iv.setBackgroundColor(Color.WHITE);
                iv.setPadding(dp(8), dp(8), dp(8), dp(8));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), dp(260));
                lp.gravity = Gravity.CENTER_HORIZONTAL;
                lp.topMargin = dp(14);
                col.addView(iv, lp);
            }
            add(col, text("Pix copia e cola", 13, true, MUTED), 14);
            TextView code = text(payload, 12, false, TEXT);
            code.setTextIsSelectable(true);
            code.setPadding(dp(10), dp(10), dp(10), dp(10));
            code.setBackground(rounded(Color.WHITE, 8, LINE));
            add(col, code, 4);
            add(col, button("Copiar código Pix", false, v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Pix", payload));
                toast("Código Pix copiado.");
            }), 10);
        }
        String bank = payment != null ? J.str(payment, "bank") : null;
        if (bank != null) add(col, text("Banco: " + bank, 13, false, MUTED), 8);

        add(col, notice("Depois de pagar, toque em \"Já paguei\". Quem confirma o pagamento é a loja, não o app."), 14);
        if (orderId != null) {
            add(col, button("Já paguei", true, v -> notifyPix(orderId)), 10);
            add(col, button("Ver pedido", false, v -> showOrder(orderId)), 8);
        }
    }

    private Bitmap qr(String content, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    px[y * size + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bmp.setPixels(px, 0, size, 0, 0, size, size);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private void notifyPix(final String orderId) {
        api.async(() -> api.json("POST", "/orders/" + orderId + "/pix", null, true), (r, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            toast("Aviso enviado. A loja vai confirmar o pagamento.");
            showOrder(orderId);
        });
    }

    // ---------------------------------------------------------------- PEDIDOS

    private void showOrders() {
        LinearLayout col = newScreen();
        add(col, text("Meus pedidos", 22, true, TEXT), 0);

        if (!auth.isLoggedIn()) {
            add(col, text("Entre na sua conta para ver seus pedidos.", 15, false, MUTED), 12);
            add(col, button("Entrar com a conta da loja", true, v -> startLogin()), 12);
            return;
        }
        add(col, button("Atualizar", false, v -> loadOrders()), 10);

        if (orders == null) {
            add(col, text("Carregando pedidos…", 15, false, MUTED), 14);
            loadOrders();
            return;
        }
        if (orders.length() == 0) {
            add(col, text("Você ainda não fez pedidos.", 15, false, MUTED), 14);
        }
        for (int i = 0; i < orders.length(); i++) {
            final JSONObject o = orders.optJSONObject(i);
            if (o == null) continue;
            LinearLayout c = card();
            String title = orderTitle(o);
            String status = J.str(o, "status");
            double amount = J.num(o, -1, "amount", "price", "total", "value");
            c.addView(text(title, 16, true, TEXT));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(chip(statusLabel(status), isPaid(status) ? SOFT : WARN, isPaid(status) ? GREEN : 0xFF7A5B00));
            if (amount >= 0) {
                TextView a = text("  " + J.money(amount), 14, true, GREEN);
                row.addView(a);
            }
            add(c, row, 8);
            String date = fmtDate(J.str(o, "createdAt", "created_at", "date"));
            if (!date.isEmpty()) add(c, text(date, 12, false, MUTED), 6);
            final String id = J.str(o, "id");
            if (id != null) c.setOnClickListener(v -> showOrder(id));
            add(col, c, 10);
        }
    }

    private void loadOrders() {
        if (!auth.isLoggedIn()) return;
        ensureProfile(() -> api.async(() -> api.json("GET", "/orders?offset=0&limit=50", null, true), (r, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            orders = ownOrders(J.arr(r, "orders"));
            if (tab == 1 && !inDetail) showOrders();
        }));
    }

    /** O app é de comprador: nunca mostra pedidos de outros clientes, mesmo se a conta for de administrador. */
    private JSONArray ownOrders(JSONArray all) {
        if (!isAdmin) return all;
        String me = J.str(profile, "id", "user_id", "userId", "sub");
        if (me == null) return new JSONArray();
        JSONArray mine = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            String owner = J.str(o, "userId", "user_id", "buyerId", "buyer_id");
            if (owner != null && owner.equals(me)) mine.put(o);
        }
        return mine;
    }

    private void showOrder(final String id) {
        tab = 1;
        for (int k = 0; k < 3; k++) tabViews[k].setTextColor(k == 1 ? GREEN : MUTED);
        inDetail = true;
        currentOrderId = id;
        backAction = () -> selectTab(1);
        LinearLayout col = newScreen();
        backRow(col, "← Pedidos", backAction);
        add(col, text("Carregando pedido…", 15, false, MUTED), 12);

        api.async(() -> api.json("GET", "/orders/" + id, null, true), (r, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            if (!inDetail || !id.equals(currentOrderId)) return;
            final JSONObject order = r.optJSONObject("order");
            final JSONArray draws = J.arr(r, "draws");
            renderOrder(id, order, draws, null);
            if (order != null && isPaid(J.str(order, "status"))) {
                api.async(() -> api.json("GET", "/orders/" + id + "/messages", null, true), (mr, me) -> {
                    if (me != null || !inDetail || !id.equals(currentOrderId)) return;
                    renderOrder(id, order, draws, J.arr(mr, "messages"));
                });
            }
        });
    }

    private void renderOrder(final String id, final JSONObject order, JSONArray draws, JSONArray messages) {
        LinearLayout col = newScreen();
        backRow(col, "← Pedidos", backAction);
        if (order == null) {
            add(col, text("Pedido não encontrado.", 15, false, MUTED), 12);
            return;
        }
        final String status = J.str(order, "status");
        add(col, text(orderTitle(order), 22, true, TEXT), 8);

        LinearLayout info = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(chip(statusLabel(status), isPaid(status) ? SOFT : WARN, isPaid(status) ? GREEN : 0xFF7A5B00));
        info.addView(row);
        double amount = J.num(order, -1, "amount", "price", "total", "value");
        if (amount >= 0) add(info, text("Valor: " + J.money(amount), 15, true, GREEN), 8);
        String buyer = J.str(order, "buyerName", "buyer_name");
        String game = J.str(order, "gameName", "game_name");
        if (buyer != null) add(info, text("Nome: " + buyer, 14, false, TEXT), 6);
        if (game != null) add(info, text("Nome no jogo: " + game, 14, false, TEXT), 2);
        String date = fmtDate(J.str(order, "createdAt", "created_at", "date"));
        if (!date.isEmpty()) add(info, text("Pedido em " + date, 12, false, MUTED), 6);
        add(col, info, 10);

        // Aguardando pagamento
        if (!isPaid(status) && !isClosed(status)) {
            add(col, notice("Pagou o Pix? Avise a loja. Só ela confirma o pagamento."), 12);
            add(col, button("Já paguei", true, v -> notifyPix(id)), 8);
        }

        // Sorteios
        if (isPaid(status)) {
            LinearLayout dc = card();
            dc.addView(text("Sorteio", 16, true, TEXT));
            for (int i = 0; i < draws.length(); i++) {
                Object d = draws.opt(i);
                add(dc, text((i + 1) + "º giro: " + describe(d), 14, false, TEXT), 6);
            }
            final TextView out = text("", 18, true, GREEN);
            out.setVisibility(View.GONE);
            out.setGravity(Gravity.CENTER);
            add(dc, out, 8);
            if (draws.length() < 2) {
                final int next = draws.length() + 1;
                final Button spin = button("Girar", true, null);
                spin.setOnClickListener(v -> spin(id, next, out, spin));
                add(dc, spin, 10);
            }
            add(col, dc, 12);
        }

        // Chat do pedido
        if (isPaid(status)) {
            LinearLayout mc = card();
            mc.addView(text("Mensagens", 16, true, TEXT));
            if (messages != null) {
                if (messages.length() == 0) add(mc, text("Nenhuma mensagem ainda.", 13, false, MUTED), 6);
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject m = messages.optJSONObject(i);
                    if (m == null) continue;
                    String who = J.str(m, "senderName", "sender_name", "sender", "author", "from");
                    String body = J.str(m, "content", "message", "text");
                    if (body == null) continue;
                    add(mc, text((who != null ? who + ": " : "") + body, 14, false, TEXT), 6);
                }
            } else {
                add(mc, text("Carregando mensagens…", 13, false, MUTED), 6);
            }
            String blocked = blockedMessage();
            if (blocked != null) {
                add(mc, notice(blocked), 10);
            } else {
                final EditText msg = edit("Escreva uma mensagem");
                add(mc, msg, 10);
                add(mc, button("Enviar", false, v -> {
                    final String content = msg.getText().toString().trim();
                    if (content.isEmpty()) return;
                    api.async(() -> {
                        JSONObject b = new JSONObject();
                        b.put("content", content);
                        return api.json("POST", "/orders/" + id + "/messages", b, true);
                    }, (r, e) -> {
                        if (e != null) {
                            fail(e);
                            return;
                        }
                        showOrder(id);
                    });
                }), 8);
            }
            add(col, mc, 12);
        }

        // Confirmar recebimento e avaliar (somente após entrega)
        if ("entregue".equalsIgnoreCase(status) && !J.bool(order, "buyerConfirmed", "buyer_confirmed")) {
            add(col, button("Confirmar recebimento e avaliar", true, v -> askReview(id)), 14);
        } else if (J.bool(order, "buyerConfirmed", "buyer_confirmed")) {
            add(col, text("Recebimento confirmado. Obrigado pela avaliação!", 14, false, MUTED), 14);
        }
    }

    private void spin(final String id, final int number, final TextView out, final Button btn) {
        btn.setEnabled(false);
        btn.setAlpha(0.5f);
        out.setVisibility(View.VISIBLE);
        out.setText("Girando…");
        // O resultado vem sempre da loja. O app só anima a revelação.
        api.async(() -> {
            JSONObject b = new JSONObject();
            b.put("spinNumber", number);
            return api.json("POST", "/orders/" + id + "/spin", b, true);
        }, (r, e) -> {
            btn.setEnabled(true);
            btn.setAlpha(1f);
            if (e != null) {
                out.setVisibility(View.GONE);
                fail(e);
                return;
            }
            out.setText("Resultado: " + describe(r.opt("result")));
            out.setScaleX(0.6f);
            out.setScaleY(0.6f);
            out.animate().scaleX(1f).scaleY(1f).setDuration(450).start();
            handler.postDelayed(() -> {
                if (inDetail && id.equals(currentOrderId)) showOrder(id);
            }, 3000);
        });
    }

    private void askReview(final String id) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final RatingBar stars = new RatingBar(this);
        stars.setNumStars(5);
        stars.setStepSize(1f);
        stars.setRating(5f);
        box.addView(stars);
        final EditText review = edit("Conte como foi (opcional)");
        box.addView(review);
        new AlertDialog.Builder(this)
                .setTitle("Avaliar a entrega")
                .setView(box)
                .setPositiveButton("Confirmar", (d, w) -> {
                    final int rating = Math.max(1, Math.round(stars.getRating()));
                    final String text = review.getText().toString().trim();
                    api.async(() -> {
                        JSONObject b = new JSONObject();
                        b.put("buyerConfirmed", true);
                        b.put("rating", rating);
                        b.put("review", text);
                        return api.json("PATCH", "/orders/" + id, b, true);
                    }, (r, e) -> {
                        if (e != null) {
                            fail(e);
                            return;
                        }
                        toast("Obrigado pela avaliação!");
                        showOrder(id);
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------------------------------------------------------- CONTA

    private void showAccount() {
        LinearLayout col = newScreen();
        add(col, text("Minha conta", 22, true, TEXT), 0);

        if (!auth.isLoggedIn()) {
            add(col, text("Entre com a conta da loja para comprar e acompanhar seus pedidos. O login abre no navegador e o app nunca vê sua senha. Se ainda não tem conta, você pode criar durante o login.", 15, false, MUTED), 12);
            add(col, button("Entrar ou criar conta", true, v -> startLogin()), 14);
        } else {
            if (profile == null) {
                add(col, text("Carregando seu perfil…", 15, false, MUTED), 12);
                ensureProfile(() -> {
                    if (tab == 2 && !inDetail) showAccount();
                });
                return;
            }
            LinearLayout c = card();
            String name = J.str(profile, "name", "full_name", "display_name", "username");
            String email = J.str(profile, "email");
            c.addView(text(name != null ? name : "Conta da loja", 18, true, TEXT));
            if (email != null) add(c, text(email, 14, false, MUTED), 4);
            add(col, c, 12);
            String blocked = blockedMessage();
            if (blocked != null) add(col, notice(blocked), 12);
            if (isAdmin) {
                add(col, notice("Esta conta é de administrador. A gestão da loja fica no painel do site."), 12);
                add(col, button("Abrir painel do administrador", false, v -> openUrl(Config.SITE + "/admin")), 8);
            }
            add(col, button("Sair da conta", false, v -> logout()), 14);
        }

        add(col, text("Ajuda", 13, true, MUTED), 24);
        add(col, button("Suporte da loja", false, v -> openUrl(Config.SITE + "/suporte")), 8);
        add(col, button("Abrir a loja no navegador", false, v -> openUrl(Config.SITE + "/")), 8);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("Não encontrei um navegador no aparelho.");
        }
    }

    // ---------------------------------------------------------------- erros e textos

    private void fail(Exception e) {
        if (e instanceof Api.ApiException && ((Api.ApiException) e).code == 401) {
            auth.logout();
            profile = null;
            orders = null;
            toast("Sua sessão expirou. Entre novamente.");
            selectTab(2);
            return;
        }
        toast(msgOf(e));
    }

    private String msgOf(Exception e) {
        if (e instanceof Api.ApiException) return e.getMessage();
        if (e instanceof java.io.IOException && e.getMessage() != null
                && !(e instanceof java.net.UnknownHostException)
                && !(e instanceof java.net.SocketTimeoutException)
                && !(e instanceof java.net.ConnectException)) {
            return e.getMessage();
        }
        return "Sem conexão com a loja. Tente de novo.";
    }

    private String orderTitle(JSONObject o) {
        String t = J.str(o, "productTitle", "product_title", "productName", "product_name", "title");
        if (t == null) {
            JSONObject p = o.optJSONObject("product");
            t = J.str(p, "title", "titulo", "name", "nome");
        }
        return t != null ? t : "Pedido";
    }

    private boolean isPaid(String s) {
        return "pago".equalsIgnoreCase(s) || "entregue".equalsIgnoreCase(s);
    }

    private boolean isClosed(String s) {
        if (s == null) return false;
        String l = s.toLowerCase(Locale.ROOT);
        return l.startsWith("cancel") || l.startsWith("expir") || l.startsWith("recus") || l.startsWith("reembols");
    }

    private String statusLabel(String s) {
        if (s == null || s.isEmpty()) return "—";
        if ("pago".equalsIgnoreCase(s)) return "Pago";
        if ("entregue".equalsIgnoreCase(s)) return "Entregue";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private String describe(Object o) {
        if (o == null || o == JSONObject.NULL) return "—";
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            String s = J.str(j, "name", "title", "prize", "label", "item", "result", "description");
            if (s == null) {
                JSONObject inner = j.optJSONObject("result");
                s = J.str(inner, "name", "title", "prize", "label", "item", "description");
            }
            return s != null ? s : j.toString();
        }
        return String.valueOf(o);
    }

    private String fmtDate(String iso) {
        if (iso == null) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(iso.length() >= 19 ? iso.substring(0, 19) : iso);
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR")).format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    // ---------------------------------------------------------------- helpers de interface

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void add(LinearLayout parent, View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(topDp);
        parent.addView(v, lp);
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(rounded(Color.WHITE, 12, LINE));
        return c;
    }

    private TextView notice(String s) {
        TextView t = text(s, 13, false, TEXT);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackground(rounded(WARN, 10, 0));
        return t;
    }

    private TextView chip(String s, int bg, int fg) {
        TextView t = text(s, 12, true, fg);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(rounded(bg, 20, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : GREEN);
        b.setBackground(rounded(primary ? GREEN : Color.WHITE, 10, primary ? 0 : GREEN));
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    private void backRow(LinearLayout col, String label, final Runnable action) {
        TextView t = text(label, 15, true, GREEN);
        t.setPadding(0, dp(4), dp(12), dp(8));
        t.setOnClickListener(v -> action.run());
        col.addView(t, new LinearLayout.LayoutParams(WRAP, WRAP));
    }
}

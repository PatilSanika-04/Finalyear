package com.example.cardvault;
import android.provider.ContactsContract;
import android.animation.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/**
 * ══════════════════════════════════════════════════════
 *  ProfilePopupActivity
 *  Launched when a CardVault user scans another user's QR.
 *  Shows a full-screen cinematic animated profile reveal.
 * ══════════════════════════════════════════════════════
 *
 *  DEEP LINK FORMAT:
 *    cardvault://profile?name=Alex&role=Developer&phone=...
 *
 *  HOW IT'S TRIGGERED:
 *    • QR code encodes the deep link URL
 *    • Android opens this activity via intent-filter in manifest
 *    • Non-CardVault users get the web fallback URL instead
 */
public class ProfilePopupActivity extends AppCompatActivity {

    // Particle canvas
    private ParticleCanvas particleCanvas;

    // Theme colors pulled from scanned profile
    private int accentColor  = 0xFF7C6FCD;
    private int accent2Color = 0xFFC8A96E;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // True full-screen immersive
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // Parse incoming deep link
        Uri uri = getIntent().getData();
        if (uri == null) { finish(); return; }

        String name      = safe(uri.getQueryParameter("name"));
        String role      = safe(uri.getQueryParameter("role"));
        String bio       = safe(uri.getQueryParameter("bio"));
        String phone     = safe(uri.getQueryParameter("phone"));
        String email     = safe(uri.getQueryParameter("email"));
        String whatsapp  = safe(uri.getQueryParameter("wa"));
        String instagram = safe(uri.getQueryParameter("ig"));
        String linkedin  = safe(uri.getQueryParameter("li"));
        String twitter   = safe(uri.getQueryParameter("tw"));
        String initials  = safe(uri.getQueryParameter("initials"));
        String colorHex  = safe(uri.getQueryParameter("color"));
        String accentHex = safe(uri.getQueryParameter("accent"));

        // Resolve colors
        if (!colorHex.isEmpty())  try { accentColor  = Color.parseColor(colorHex);  } catch (Exception ignored) {}
        if (!accentHex.isEmpty()) try { accent2Color = Color.parseColor(accentHex); } catch (Exception ignored) {}
        if (initials.isEmpty())   initials = makeInitials(name);

        // Build root
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0A0A0F"));
        setContentView(root);

        // ── 1. Particle background ───────────────────────────────────
        particleCanvas = new ParticleCanvas(this, accentColor, accent2Color);
        root.addView(particleCanvas, new FrameLayout.LayoutParams(-1, -1));

        // ── 2. Ambient glow orbs ─────────────────────────────────────
        root.addView(glowOrb(accentColor,  500, Gravity.TOP    | Gravity.CENTER_HORIZONTAL, 0,    -150));
        root.addView(glowOrb(accent2Color, 350, Gravity.BOTTOM | Gravity.END,               -80,  -80));
        root.addView(glowOrb(accentColor,  200, Gravity.START  | Gravity.CENTER_VERTICAL,   -60,  0));

        // ── 3. Dismissable scrim (tap outside to close) ──────────────
        View scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(180, 0, 0, 0));
        scrim.setAlpha(0f);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        scrim.setOnClickListener(v -> dismissWithAnimation(root));

        // ── 4. Main card panel ───────────────────────────────────────
        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setBackground(null);

        LinearLayout card = buildProfileCard(
                name, role, bio, phone, email,
                whatsapp, instagram, linkedin, twitter, initials
        );

        scrollView.addView(card, new FrameLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams cardLP = new FrameLayout.LayoutParams(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        cardLP.bottomMargin = dp(24);
        root.addView(scrollView, cardLP);

        // ── 5. Close X button ────────────────────────────────────────
        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextSize(18); closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setAlpha(0f);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(Color.argb(120, 20, 20, 35));
        closeBg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        closeBtn.setBackground(closeBg);
        closeBtn.setOnClickListener(v -> dismissWithAnimation(root));

        FrameLayout.LayoutParams closeLP = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
        closeLP.topMargin  = dp(56);
        closeLP.rightMargin = dp(16);
        root.addView(closeBtn, closeLP);

        // ── 6. Animate everything in ─────────────────────────────────
        runEntranceAnimation(scrim, scrollView, closeBtn, card);
    }

    // ══════════════════════════════════════════════════════════════
    //  BUILD THE PROFILE CARD CONTENT
    // ══════════════════════════════════════════════════════════════
    private LinearLayout buildProfileCard(
            String name, String role, String bio,
            String phone, String email, String whatsapp,
            String instagram, String linkedin, String twitter,
            String initials
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        // Card background
        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ Color.parseColor("#13131A"), Color.parseColor("#0F0F1A") }
        );
        cardBg.setCornerRadius(dp(28));
        cardBg.setStroke(dp(1), Color.argb(60,
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        card.setBackground(cardBg);
        card.setClipToOutline(true);

        // ── Header gradient strip ─────────────────────────────────
        FrameLayout headerStrip = new FrameLayout(this);
        GradientDrawable stripBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ darken(accentColor, 0.5f), darken(accent2Color, 0.5f) }
        );
        stripBg.setCornerRadii(new float[]{ dp(28),dp(28), dp(28),dp(28), 0,0, 0,0 });
        headerStrip.setBackground(stripBg);

        // Geometric lines decoration on header
        headerStrip.addView(buildHeaderDecoration());

        // Scanned badge
        TextView badge = new TextView(this);
        badge.setText("✦  CardVault Profile");
        badge.setTextSize(9); badge.setTextColor(Color.argb(180, 255, 255, 255));
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setLetterSpacing(0.15f);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(20));
        badgeBg.setColor(Color.argb(40, 255, 255, 255));
        badge.setBackground(badgeBg);
        FrameLayout.LayoutParams bdLP = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        bdLP.topMargin = dp(12); bdLP.rightMargin = dp(14);
        headerStrip.addView(badge, bdLP);

        LinearLayout.LayoutParams stripLP = new LinearLayout.LayoutParams(-1, dp(100));
        card.addView(headerStrip, stripLP);

        // ── Avatar (overlapping header) ───────────────────────────
        FrameLayout avatarWrap = new FrameLayout(this);
        LinearLayout.LayoutParams avWrapLP = new LinearLayout.LayoutParams(-1, dp(56));
        avWrapLP.topMargin = -dp(56);
        card.addView(avatarWrap, avWrapLP);

        FrameLayout avatar = new FrameLayout(this);
        GradientDrawable avBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{ accentColor, accent2Color }
        );
        avBg.setShape(GradientDrawable.OVAL);
        avatar.setBackground(avBg);
        avatar.setElevation(dp(8));

        // Border ring
        GradientDrawable avBorder = new GradientDrawable();
        avBorder.setShape(GradientDrawable.OVAL);
        avBorder.setColor(Color.TRANSPARENT);
        avBorder.setStroke(dp(3), Color.parseColor("#0A0A0F"));

        TextView initialsTV = new TextView(this);
        initialsTV.setText(initials);
        initialsTV.setTextSize(28); initialsTV.setTextColor(Color.WHITE);
        initialsTV.setTypeface(Typeface.DEFAULT_BOLD);
        initialsTV.setGravity(Gravity.CENTER);
        avatar.addView(initialsTV, new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER));

        FrameLayout avOuter = new FrameLayout(this);
        GradientDrawable avOuterBg = new GradientDrawable();
        avOuterBg.setShape(GradientDrawable.OVAL);
        avOuterBg.setColor(Color.parseColor("#0A0A0F"));
        avOuter.setBackground(avOuterBg);
        avOuter.addView(avatar, new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER));
        avOuter.addView(new View(this) {{ setBackground(avBorder); }},
                new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER));

        FrameLayout.LayoutParams avLP = new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        avLP.bottomMargin = -dp(48);
        avatarWrap.addView(avOuter, avLP);

        // ── Name & role section ───────────────────────────────────
        LinearLayout nameSection = new LinearLayout(this);
        nameSection.setOrientation(LinearLayout.VERTICAL);
        nameSection.setGravity(Gravity.CENTER_HORIZONTAL);
        nameSection.setPadding(dp(24), dp(60), dp(24), dp(16));

        if (!name.isEmpty()) {
            TextView nameTv = new TextView(this);
            nameTv.setText(name);
            nameTv.setTextSize(26); nameTv.setTextColor(Color.WHITE);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            nameTv.setGravity(Gravity.CENTER);
            nameTv.setLetterSpacing(-0.02f);
            nameSection.addView(nameTv);
        }
        if (!role.isEmpty()) {
            TextView roleTv = new TextView(this);
            roleTv.setText(role);
            roleTv.setTextSize(13); roleTv.setTextColor(accent2Color);
            roleTv.setGravity(Gravity.CENTER);
            roleTv.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
            rlp.topMargin = dp(4);
            nameSection.addView(roleTv, rlp);
        }
        if (!bio.isEmpty()) {
            TextView bioTv = new TextView(this);
            bioTv.setText(bio);
            bioTv.setTextSize(13); bioTv.setTextColor(Color.argb(160, 200, 200, 220));
            bioTv.setGravity(Gravity.CENTER); bioTv.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = dp(10); blp.leftMargin = dp(12); blp.rightMargin = dp(12);
            nameSection.addView(bioTv, blp);
        }
        card.addView(nameSection);

        // ── Accent divider ────────────────────────────────────────
        View div = new View(this);
        GradientDrawable divBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ Color.TRANSPARENT, accentColor, Color.TRANSPARENT }
        );
        div.setBackground(divBg);
        LinearLayout.LayoutParams divLP = new LinearLayout.LayoutParams(-1, dp(1));
        divLP.leftMargin = dp(32); divLP.rightMargin = dp(32); divLP.bottomMargin = dp(16);
        card.addView(div, divLP);

        // ── Action buttons ────────────────────────────────────────
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(18), 0, dp(18), dp(8));
        actions.setGravity(Gravity.CENTER_HORIZONTAL);

        if (!phone.isEmpty())
            actions.addView(actionBtn("📞", "Call", phone, "tel:" + phone, accentColor, 0));
        if (!whatsapp.isEmpty() || !phone.isEmpty()) {
            String wa = whatsapp.isEmpty() ? phone : whatsapp;
            actions.addView(actionBtn("💬", "WhatsApp", wa, "https://wa.me/" + wa.replaceAll("[^0-9]", ""), 0xFF25D366, 80));
        }
        if (!email.isEmpty())
            actions.addView(actionBtn("✉️", "Email", email, "mailto:" + email, accent2Color, 160));

        // Save contact button — special styled
        if (!phone.isEmpty() || !email.isEmpty()) {
            actions.addView(buildSaveContactBtn(name, role, phone, email, linkedin));
        }
        card.addView(actions);

        // ── Social row ────────────────────────────────────────────
        boolean hasSocials = !instagram.isEmpty() || !linkedin.isEmpty() || !twitter.isEmpty();
        if (hasSocials) {
            LinearLayout socialsSection = new LinearLayout(this);
            socialsSection.setOrientation(LinearLayout.VERTICAL);
            socialsSection.setPadding(dp(18), dp(8), dp(18), dp(16));

            TextView socialLbl = new TextView(this);
            socialLbl.setText("FIND ME ON");
            socialLbl.setTextSize(9); socialLbl.setTextColor(Color.argb(100, 150, 150, 180));
            socialLbl.setTypeface(Typeface.DEFAULT_BOLD); socialLbl.setLetterSpacing(0.18f);
            socialLbl.setGravity(Gravity.CENTER);
            socialsSection.addView(socialLbl, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout socialRow = new LinearLayout(this);
            socialRow.setOrientation(LinearLayout.HORIZONTAL);
            socialRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams srLP = new LinearLayout.LayoutParams(-1, -2);
            srLP.topMargin = dp(10);

            if (!instagram.isEmpty())
                socialRow.addView(socialChip("📸", "Instagram",
                        "https://instagram.com/" + instagram.replace("@",""), 0xFFE1306C));
            if (!linkedin.isEmpty())
                socialRow.addView(socialChip("💼", "LinkedIn",
                        linkedin.startsWith("http") ? linkedin : "https://" + linkedin, 0xFF0A66C2));
            if (!twitter.isEmpty())
                socialRow.addView(socialChip("𝕏", "Twitter",
                        twitter.startsWith("http") ? twitter : "https://twitter.com/" + twitter.replace("@",""), 0xFF1DA1F2));

            socialsSection.addView(socialRow, srLP);
            card.addView(socialsSection);
        }

        // ── Footer ────────────────────────────────────────────────
        TextView footer = new TextView(this);
        footer.setText("Shared via CardVault  ✦");
        footer.setTextSize(10); footer.setTextColor(Color.argb(50, 180, 180, 200));
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dp(4); flp.bottomMargin = dp(20);
        card.addView(footer, flp);

        return card;
    }

    // ══════════════════════════════════════════════════════════════
    //  ACTION BUTTON (call / whatsapp / email)
    // ══════════════════════════════════════════════════════════════
    private View actionBtn(String emoji, String label, String subtitle, String deepLink, int color, long delay) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(android.view.Gravity.CENTER_VERTICAL);
        btn.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.argb(25, Color.red(color), Color.green(color), Color.blue(color)));
        bg.setStroke(dp(1), Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)));
        btn.setBackground(bg);

        // Icon box
        FrameLayout iconBox = new FrameLayout(this);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(dp(10));
        iconBg.setColor(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)));
        iconBox.setBackground(iconBg);
        TextView iconTv = new TextView(this);
        iconTv.setText(emoji); iconTv.setTextSize(18);
        iconTv.setGravity(Gravity.CENTER);
        iconBox.addView(iconTv, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        LinearLayout.LayoutParams iconLP = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconLP.rightMargin = dp(14);
        btn.addView(iconBox, iconLP);

        // Text
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(15); labelTv.setTextColor(Color.WHITE); labelTv.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(labelTv);
        TextView subTv = new TextView(this);
        subTv.setText(subtitle);
        subTv.setTextSize(11); subTv.setTextColor(Color.argb(120, 200, 200, 220));
        subTv.setMaxLines(1);
        textCol.addView(subTv, new LinearLayout.LayoutParams(-2, -2));
        btn.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

        // Arrow
        TextView arrow = new TextView(this);
        arrow.setText("›"); arrow.setTextSize(22);
        arrow.setTextColor(Color.argb(80, 200, 200, 220));
        btn.addView(arrow);

        LinearLayout.LayoutParams btnLP = new LinearLayout.LayoutParams(-1, -2);
        btnLP.bottomMargin = dp(10);
        btn.setLayoutParams(btnLP);

        // Click
        btn.setOnClickListener(v -> {
            pressAnim(btn);
            new Handler().postDelayed(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))); }
                catch (Exception e) { /* fallback */ }
            }, 120);
        });

        // Entrance animation
        btn.setAlpha(0f); btn.setTranslationY(dp(30));
        btn.postDelayed(() ->
                btn.animate().alpha(1f).translationY(0f).setDuration(400)
                        .setInterpolator(new DecelerateInterpolator(2f)).start(), delay + 400);

        return btn;
    }

    // ══════════════════════════════════════════════════════════════
    //  SAVE CONTACT BUTTON
    // ══════════════════════════════════════════════════════════════
    private View buildSaveContactBtn(String name, String role, String phone, String email, String linkedin) {
        TextView btn = new TextView(this);
        btn.setText("👤  Save to Contacts");
        btn.setTextSize(15); btn.setTextColor(Color.WHITE); btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ Color.argb(180, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                        Color.argb(180, Color.red(accent2Color), Color.green(accent2Color), Color.blue(accent2Color)) }
        );
        bg.setCornerRadius(dp(16));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.bottomMargin = dp(10);
        btn.setLayoutParams(lp);

        btn.setAlpha(0f); btn.setTranslationY(dp(30));
        btn.postDelayed(() ->
                btn.animate().alpha(1f).translationY(0f).setDuration(400)
                        .setInterpolator(new DecelerateInterpolator(2f)).start(), 640);

        btn.setOnClickListener(v -> {
            pressAnim(btn);
            // Launch contact intent with pre-filled data
            Intent intent = new Intent(ContactsContract.Intents.Insert.ACTION);
            intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);
            if (!name.isEmpty())   intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
            if (!role.isEmpty())   intent.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, role);
            if (!phone.isEmpty())  intent.putExtra(ContactsContract.Intents.Insert.PHONE, phone);
            if (!email.isEmpty())  intent.putExtra(ContactsContract.Intents.Insert.EMAIL, email);
            if (!linkedin.isEmpty()) intent.putExtra(ContactsContract.Intents.Insert.NOTES, "LinkedIn: " + linkedin);
            new Handler().postDelayed(() -> {
                try { startActivity(intent); }
                catch (Exception e) {
                    Toast.makeText(this, "Could not open Contacts app", Toast.LENGTH_SHORT).show();
                }
            }, 120);
        });
        return btn;
    }

    // ══════════════════════════════════════════════════════════════
    //  SOCIAL CHIP
    // ══════════════════════════════════════════════════════════════
    private View socialChip(String emoji, String label, String url, int color) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
        bg.setStroke(dp(1), Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)));
        chip.setBackground(bg);

        TextView icon = new TextView(this);
        icon.setText(emoji); icon.setTextSize(22); icon.setGravity(Gravity.CENTER);
        chip.addView(icon);
        TextView lbl = new TextView(this);
        lbl.setText(label); lbl.setTextSize(9);
        lbl.setTextColor(Color.argb(160, 200, 200, 220));
        lbl.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = dp(4); chip.addView(lbl, llp);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = dp(6); clp.rightMargin = dp(6);
        chip.setLayoutParams(clp);

        chip.setOnClickListener(v -> {
            pressAnim(chip);
            new Handler().postDelayed(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception ignored) {}
            }, 120);
        });
        return chip;
    }

    // ══════════════════════════════════════════════════════════════
    //  ENTRANCE ANIMATION SEQUENCE
    // ══════════════════════════════════════════════════════════════
    private void runEntranceAnimation(View scrim, View card, View closeBtn, LinearLayout cardContent) {
        // 1. Scrim fade in
        scrim.animate().alpha(1f).setDuration(350).start();

        // 2. Card bursts from center (scale + translate)
        card.setScaleX(0.3f); card.setScaleY(0.3f);
        card.setAlpha(0f);    card.setTranslationY(dp(80));

        card.animate()
                .scaleX(1f).scaleY(1f).alpha(1f).translationY(0f)
                .setDuration(650)
                .setStartDelay(100)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        // 3. Ripple ring effect from centre
        postDelayed(() -> launchRippleEffect((FrameLayout) card.getParent().getParent()), 200);

        // 4. Close button fades in
        closeBtn.animate().alpha(1f).setDuration(300).setStartDelay(500).start();

        // 5. Particle burst
        postDelayed(() -> { if (particleCanvas != null) particleCanvas.burst(accentColor); }, 150);
    }

    // ══════════════════════════════════════════════════════════════
    //  RIPPLE RING EFFECT
    // ══════════════════════════════════════════════════════════════
    private void launchRippleEffect(FrameLayout root) {
        if (root == null) return;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            View ring = new View(this);
            GradientDrawable ringBg = new GradientDrawable();
            ringBg.setShape(GradientDrawable.OVAL);
            ringBg.setColor(Color.TRANSPARENT);
            ringBg.setStroke(dp(2), Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
            ring.setBackground(ringBg);
            int startSz = dp(80);
            FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(startSz, startSz, Gravity.CENTER);
            root.addView(ring, rlp);

            ring.postDelayed(() -> {
                int endSz = root.getWidth() * 2;
                ring.animate()
                        .scaleX((float) endSz / startSz)
                        .scaleY((float) endSz / startSz)
                        .alpha(0f)
                        .setDuration(900)
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .withEndAction(() -> root.removeView(ring))
                        .start();
            }, idx * 180L);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  DISMISS ANIMATION
    // ══════════════════════════════════════════════════════════════
    private void dismissWithAnimation(View root) {
        root.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f)
                .setDuration(280)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(this::finish)
                .start();
        overridePendingTransition(0, 0);
    }

    // ══════════════════════════════════════════════════════════════
    //  HEADER DECORATION (geometric lines)
    // ══════════════════════════════════════════════════════════════
    private View buildHeaderDecoration() {
        return new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(Color.argb(30, 255, 255, 255));
                // Diagonal grid lines
                for (int i = -2; i < getWidth() / dp(40) + 2; i++) {
                    canvas.drawLine(i * dp(40), 0, i * dp(40) + getHeight(), getHeight(), p);
                }
                // Circular ring bottom center
                p.setColor(Color.argb(20, 255, 255, 255));
                p.setStrokeWidth(dp(60));
                canvas.drawCircle(getWidth() / 2f, getHeight() + dp(20), dp(80), p);
            }
        };
    }

    // ══════════════════════════════════════════════════════════════
    //  GLOW ORB
    // ══════════════════════════════════════════════════════════════
    private View glowOrb(int color, int sizeDp, int gravity, int marginRight, int marginTop) {
        View orb = new View(this);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.TRANSPARENT
                }
        );
        gd.setShape(GradientDrawable.OVAL);
        orb.setBackground(gd);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), gravity);
        lp.rightMargin  = dp(Math.abs(marginRight));
        lp.topMargin    = dp(Math.abs(marginTop));
        if (marginRight < 0) lp.rightMargin  = -dp(Math.abs(marginRight));
        if (marginTop   < 0) lp.topMargin    = -dp(Math.abs(marginTop));
        orb.setLayoutParams(lp);
        return orb;
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════
    private String safe(String s) { return s == null ? "" : s.trim(); }

    private String makeInitials(String name) {
        if (name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[parts.length-1].charAt(0)).toUpperCase();
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private int darken(int color, float factor) {
        return Color.argb(
                Color.alpha(color),
                (int)(Color.red(color)   * factor),
                (int)(Color.green(color) * factor),
                (int)(Color.blue(color)  * factor)
        );
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }

    private void pressAnim(View v) {
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
    }

    private void postDelayed(Runnable r, long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(r, delay);
    }

    // ══════════════════════════════════════════════════════════════
    //  PARTICLE CANVAS
    // ══════════════════════════════════════════════════════════════
    static class ParticleCanvas extends android.view.View {
        private final int N = 60;
        private final float[] px, py, pvx, pvy, ps, pa;
        private final int[] pc;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean burstMode = false;
        private int burstColor = 0xFFFFFFFF;

        ParticleCanvas(android.content.Context ctx, int c1, int c2) {
            super(ctx);
            java.util.Random r = new java.util.Random();
            px = new float[N]; py = new float[N]; pvx = new float[N];
            pvy = new float[N]; ps = new float[N]; pa = new float[N];
            pc = new int[N];
            int[] cols = {c1, c2, Color.WHITE,
                    Color.argb(180, Color.red(c1), Color.green(c1), Color.blue(c1))};
            for (int i = 0; i < N; i++) {
                px[i] = r.nextFloat(); py[i] = r.nextFloat();
                pvx[i] = (r.nextFloat() - 0.5f) * 0.0003f;
                pvy[i] = (r.nextFloat() - 0.5f) * 0.0003f;
                ps[i] = 1f + r.nextFloat() * 2.5f;
                pa[i] = 20f + r.nextFloat() * 50f;
                pc[i] = cols[r.nextInt(cols.length)];
            }
        }

        void burst(int color) {
            burstMode = true; burstColor = color;
            postInvalidate();
            postDelayed(() -> { burstMode = false; postInvalidate(); }, 1000);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            for (int i = 0; i < N; i++) {
                px[i] += pvx[i]; py[i] += pvy[i];
                if (px[i] < 0) px[i] = 1f; if (px[i] > 1) px[i] = 0f;
                if (py[i] < 0) py[i] = 1f; if (py[i] > 1) py[i] = 0f;
                float alpha = burstMode ? Math.min(pa[i] * 2f, 255f) : pa[i];
                paint.setColor(Color.argb((int)alpha,
                        Color.red(pc[i]), Color.green(pc[i]), Color.blue(pc[i])));
                float size = burstMode ? ps[i] * 2.5f : ps[i];
                canvas.drawCircle(px[i] * w, py[i] * h, size, paint);
            }
            postInvalidateDelayed(16);
        }
    }
}
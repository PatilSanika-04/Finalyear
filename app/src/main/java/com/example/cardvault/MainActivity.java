package com.example.cardvault;

import android.app.PendingIntent;
import android.Manifest;
import android.animation.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.nfc.*;
import android.nfc.tech.Ndef;
import android.os.*;
import android.provider.MediaStore;
import android.text.*;
import android.util.Base64;
import android.util.Patterns;
import android.view.*;
import android.view.animation.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    /* ═══════════════════════════════════════════════════════════════════
       CONSTANTS
    ═══════════════════════════════════════════════════════════════════ */
    private static final String PREFS       = "cv_prefs";
    private static final String K_PROFILES  = "profiles_json";
    private static final String K_ACTIVE_ID = "active_profile_id";
    private static final String K_FIRST_RUN = "first_run_done";

    private static final String[] TN = {"Neon Cyber","Minimal Pro","Dark Elite","Holographic"};
    private static final int[][] TC = {
            {0xFF0A0A0F,0xFF00F5FF,0xFF7C6FCD},
            {0xFFF5F5F0,0xFF1A1A2E,0xFF4A90D9},
            {0xFF111118,0xFFFFD700,0xFFFF6B35},
            {0xFF0D1B2A,0xFFFF61A6,0xFF00F5FF},
    };
    private static final String[] COLOR_HEX  = {"#00F5FF","#1A1A2E","#FFD700","#FF61A6"};
    private static final String[] ACCENT_HEX = {"#7C6FCD","#4A90D9","#FF6B35","#00F5FF"};
    private static final int[] SKILL_COLORS  = {0xFF00F5FF,0xFFFF61A6,0xFFFFD700,0xFF7C6FCD,0xFF00C896,0xFFFF6B35,0xFF4A90D9,0xFFFF4444};

    // Bottom nav tab indices
    private static final int TAB_HOME    = 0;
    private static final int TAB_CARD    = 1;
    private static final int TAB_FRIENDS = 2;
    private static final int TAB_PROFILE = 3;

    /* ═══════════════════════════════════════════════════════════════════
       PROFILE MODEL
    ═══════════════════════════════════════════════════════════════════ */
    static class Profile {
        String id="",name="",role="",school="",college="",email="",phone="",
                skills="",achievements="",certificates="",github="",linkedin="",
                twitter="",profileImgB64="";
        int cardTheme=0;

        Profile(){ id=String.valueOf(System.currentTimeMillis()); }
        Profile(JSONObject j) throws JSONException {
            id=j.optString("id",String.valueOf(System.currentTimeMillis()));
            name=j.optString("name",""); role=j.optString("role","");
            school=j.optString("school",""); college=j.optString("college","");
            email=j.optString("email",""); phone=j.optString("phone","");
            skills=j.optString("skills",""); achievements=j.optString("achievements","");
            certificates=j.optString("certificates",""); github=j.optString("github","");
            linkedin=j.optString("linkedin",""); twitter=j.optString("twitter","");
            profileImgB64=j.optString("profileImgB64",""); cardTheme=j.optInt("cardTheme",0);
        }
        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id",id).put("name",name).put("role",role)
                    .put("school",school).put("college",college).put("email",email)
                    .put("phone",phone).put("skills",skills).put("achievements",achievements)
                    .put("certificates",certificates).put("github",github)
                    .put("linkedin",linkedin).put("twitter",twitter)
                    .put("profileImgB64",profileImgB64).put("cardTheme",cardTheme);
        }
        Bitmap bitmap(){ if(profileImgB64.isEmpty()) return null;
            try{ byte[] b=Base64.decode(profileImgB64,Base64.DEFAULT); return BitmapFactory.decodeByteArray(b,0,b.length); }catch(Exception e){return null;} }
        void setBitmap(Bitmap bmp){
            try{ ByteArrayOutputStream ba=new ByteArrayOutputStream();
                Bitmap.createScaledBitmap(bmp,300,300,true).compress(Bitmap.CompressFormat.JPEG,80,ba);
                profileImgB64=Base64.encodeToString(ba.toByteArray(),Base64.DEFAULT);
            }catch(Exception ignored){} }
        int completeness(){
            int s=0; if(!name.isEmpty()) s+=20; if(!role.isEmpty()) s+=15; if(!email.isEmpty()) s+=15;
            if(!phone.isEmpty()) s+=10; if(!school.isEmpty()||!college.isEmpty()) s+=10;
            if(!skills.isEmpty()) s+=10; if(!achievements.isEmpty()) s+=8;
            if(!profileImgB64.isEmpty()) s+=7; if(!github.isEmpty()||!linkedin.isEmpty()) s+=5;
            return Math.min(s,100);
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
       FIELDS
    ═══════════════════════════════════════════════════════════════════ */
    private SharedPreferences prefs;
    private final List<Profile> profiles = new ArrayList<>();
    private Profile current, editing;

    // Root layout: content area + bottom nav
    private FrameLayout rootFrame;    // full screen bg
    private FrameLayout contentArea;  // above nav bar
    private LinearLayout bottomNav;   // nav bar
    private View currentScreen;
    private int activeTab = TAB_HOME;
    private final TextView[] navLabels = new TextView[4];
    private final View[]     navDots   = new View[4];

    private static final int STEPS = 3;
    private int step = 0;
    private EditText etName,etRole,etEmail,etPhone,etSchool,etCollege,etSkills,etAch,etCerts,etGithub,etLinkedin,etTwitter;
    private TextView livePreviewName,livePreviewRole;
    private LinearLayout livePreviewTags;
    private FrameLayout livePreviewAv;
    private ImageView profileImageView;
    private FrameLayout profilePickerFrame;
    private ActivityResultLauncher<Intent> pickerLauncher;
    private NfcAdapter nfcAdapter;

    /* ═══════════════════════════════════════════════════════════════════
       LIFECYCLE
    ═══════════════════════════════════════════════════════════════════ */
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        nfcAdapter=NfcAdapter.getDefaultAdapter(this);

        // Root: full-screen dark background
        rootFrame=new FrameLayout(this);
        rootFrame.setBackgroundColor(0xFF0A0A0F);
        setContentView(rootFrame);

        // Particle background (persists behind everything)
        rootFrame.addView(new ParticleView(this), new FrameLayout.LayoutParams(-1,-1));

        // Main vertical layout: content + bottom nav
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        rootFrame.addView(mainLayout, new FrameLayout.LayoutParams(-1,-1));

        // Content area (fills remaining space above nav)
        contentArea = new FrameLayout(this);
        contentArea.setBackgroundColor(Color.TRANSPARENT);
        mainLayout.addView(contentArea, new LinearLayout.LayoutParams(-1,0,1f));

        // Bottom nav bar
        bottomNav = buildBottomNav();
        mainLayout.addView(bottomNav, new LinearLayout.LayoutParams(-1,-2));

        pickerLauncher=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r->{
            if(r.getResultCode()==RESULT_OK&&r.getData()!=null){
                try{ Bitmap bmp=MediaStore.Images.Media.getBitmap(getContentResolver(),r.getData().getData());
                    editing.setBitmap(bmp); refreshAvatarViews();
                }catch(Exception ignored){} }
        });

        loadProfiles();
        if(profiles.isEmpty()) showHome(false);
        else { current=activeProfile(); showHome(true); }
    }

    @Override protected void onResume() {
        super.onResume();
        if(nfcAdapter!=null&&current!=null){
            PendingIntent pi=PendingIntent.getActivity(this,0,
                    new Intent(this,getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_MUTABLE);
            nfcAdapter.enableForegroundDispatch(this,pi,null,null);
        }
    }
    @Override protected void onPause() { super.onPause(); if(nfcAdapter!=null) nfcAdapter.disableForegroundDispatch(this); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())&&current!=null){
            Tag tag=intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            writeNfcTag(tag,qrContent(current));
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
       BOTTOM NAVIGATION BAR
    ═══════════════════════════════════════════════════════════════════ */
    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, dp(8), 0, dp(navBarExtra()));

        // Glassmorphism-style dark bar
        GradientDrawable navBg = new GradientDrawable();
        navBg.setShape(GradientDrawable.RECTANGLE);
        navBg.setColor(Color.argb(230,10,10,18));
        navBg.setStroke(1, Color.argb(40,255,255,255));
        nav.setBackground(navBg);

        String[] icons   = {"⌂","◉","👥","✎"};
        String[] labels  = {"Home","My Card","Friends","Profile"};

        for(int i=0; i<4; i++){
            final int idx=i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0,dp(6),0,dp(6));

            TextView icon = mkTv(icons[i],20,i==activeTab?0xFF7C6FCD:0xFF444466,false);
            icon.setGravity(Gravity.CENTER);
            tab.addView(icon, new LinearLayout.LayoutParams(-1,-2));

            navLabels[i] = mkTv(labels[i],8,i==activeTab?0xFF7C6FCD:0xFF333355,i==activeTab);
            navLabels[i].setGravity(Gravity.CENTER);
            tab.addView(navLabels[i], new LinearLayout.LayoutParams(-1,-2));

            // Active dot
            navDots[i] = new View(this);
            GradientDrawable dotBg = new GradientDrawable(); dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(i==activeTab?0xFF7C6FCD:Color.TRANSPARENT);
            navDots[i].setBackground(dotBg);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(4),dp(4));
            dotLp.gravity=Gravity.CENTER_HORIZONTAL; dotLp.topMargin=dp(3);
            tab.addView(navDots[i],dotLp);

            // Badge for friends
            if(i==TAB_FRIENDS){
                FrameLayout tabFrame = new FrameLayout(this);
                tabFrame.addView(tab, new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
                int fc = FriendsManager.count(this);
                if(fc>0){
                    TextView badge = mkTv(String.valueOf(fc),7,Color.WHITE,true);
                    badge.setGravity(Gravity.CENTER); badge.setPadding(dp(4),dp(1),dp(4),dp(1));
                    GradientDrawable bd = new GradientDrawable(); bd.setShape(GradientDrawable.OVAL); bd.setColor(0xFFFF61A6);
                    badge.setBackground(bd);
                    FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
                    blp.topMargin=dp(2); blp.leftMargin=dp(22);
                    tabFrame.addView(badge,blp);
                }
                tabFrame.setOnClickListener(v->switchTab(idx));
                nav.addView(tabFrame, new LinearLayout.LayoutParams(0,-2,1f));
            } else {
                tab.setOnClickListener(v->switchTab(idx));
                nav.addView(tab, new LinearLayout.LayoutParams(0,-2,1f));
            }
        }
        return nav;
    }

    private void switchTab(int tab) {
        if(tab==activeTab) return;
        updateNavSelection(tab);
        switch(tab){
            case TAB_HOME:    showHome(current!=null); break;
            case TAB_CARD:    if(current!=null) showPreview(); else { showHome(false); showToast("Create a profile first!"); } break;
            case TAB_FRIENDS: showFriends(); break;
            case TAB_PROFILE: if(current!=null){ editing=cloneOf(current); step=0; showForm(); } else { showHome(false); showToast("Create a profile first!"); } break;
        }
    }

    private void updateNavSelection(int tab) {
        activeTab=tab;
        int[] icons = new int[4];
        for(int i=0;i<4;i++){
            boolean sel=i==tab;
            int color=sel?0xFF7C6FCD:0xFF444466;
            navLabels[i].setTextColor(sel?0xFF7C6FCD:0xFF333355);
            navLabels[i].setTypeface(sel?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
            GradientDrawable dotBg = new GradientDrawable(); dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(sel?0xFF7C6FCD:Color.TRANSPARENT);
            navDots[i].setBackground(dotBg);
            // Animate selected tab
            if(sel){ View tabView=(navDots[i].getParent() instanceof LinearLayout)?(LinearLayout)navDots[i].getParent():null;
                if(tabView!=null){ tabView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(80)
                        .withEndAction(()->tabView.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start(); } }
        }
        // Refresh nav to update friend badge
        rootFrame.post(this::refreshFriendBadge);
    }

    private void refreshFriendBadge() {
        // Re-build bottom nav to update badge count
        ViewGroup parent=(ViewGroup)bottomNav.getParent();
        if(parent==null) return;
        int idx2=parent.indexOfChild(bottomNav);
        LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)bottomNav.getLayoutParams();
        parent.removeView(bottomNav);
        bottomNav=buildBottomNav();
        parent.addView(bottomNav,idx2,lp);
    }

    /* ═══════════════════════════════════════════════════════════════════
       SCREEN MANAGEMENT
    ═══════════════════════════════════════════════════════════════════ */
    private void removeScreen(){
        if(currentScreen!=null){ contentArea.removeView(currentScreen); currentScreen=null; }
    }
    private void pushScreen(View v){ removeScreen(); contentArea.addView(v,new FrameLayout.LayoutParams(-1,-1)); currentScreen=v; aIn(v); }

    /* ═══════════════════════════════════════════════════════════════════
       PERSISTENCE
    ═══════════════════════════════════════════════════════════════════ */
    private void loadProfiles(){ profiles.clear();
        try{ JSONArray a=new JSONArray(prefs.getString(K_PROFILES,"[]"));
            for(int i=0;i<a.length();i++) profiles.add(new Profile(a.getJSONObject(i)));
        }catch(Exception ignored){} }
    private void saveProfiles(){ try{ JSONArray a=new JSONArray(); for(Profile p:profiles) a.put(p.toJson()); prefs.edit().putString(K_PROFILES,a.toString()).apply(); }catch(Exception ignored){} }
    private Profile activeProfile(){ String aid=prefs.getString(K_ACTIVE_ID,""); for(Profile p:profiles) if(p.id.equals(aid)) return p; return profiles.isEmpty()?null:profiles.get(0); }
    private void setActive(Profile p){ current=p; prefs.edit().putString(K_ACTIVE_ID,p.id).apply(); }
    private void deleteProfile(Profile p){ profiles.remove(p); saveProfiles(); if(profiles.isEmpty()){current=null;prefs.edit().remove(K_ACTIVE_ID).apply();}else setActive(profiles.get(0)); }
    private Profile cloneOf(Profile p){ try{ return new Profile(p.toJson()); }catch(Exception e){return new Profile();} }

    /* ═══════════════════════════════════════════════════════════════════
       QR / NFC CONTENT
    ═══════════════════════════════════════════════════════════════════ */
    private String qrContent(Profile p){
        StringBuilder sb=new StringBuilder("cardvault://profile?");
        qp(sb,"name",p.name); qp(sb,"role",p.role); qp(sb,"phone",p.phone);
        qp(sb,"email",p.email); qp(sb,"wa",p.phone); qp(sb,"li",p.linkedin);
        qp(sb,"tw",p.twitter); qp(sb,"gh",p.github); qp(sb,"initials",makeInitials(p.name));
        qp(sb,"color",COLOR_HEX[p.cardTheme]); qp(sb,"accent",ACCENT_HEX[p.cardTheme]);
        String url=sb.toString(); if(url.endsWith("&")) url=url.substring(0,url.length()-1); return url;
    }
    private void qp(StringBuilder sb,String key,String val){
        if(val!=null&&!val.trim().isEmpty()){ try{ sb.append(key).append("=").append(java.net.URLEncoder.encode(val.trim(),"UTF-8")).append("&"); }catch(Exception ignored){} } }

    private Bitmap buildRealQR(String text,int sizePx){
        try{ Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.MARGIN,2); hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");
            BitMatrix matrix=new QRCodeWriter().encode(text,BarcodeFormat.QR_CODE,sizePx,sizePx,hints);
            int w=matrix.getWidth(),h=matrix.getHeight();
            Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            for(int x=0;x<w;x++) for(int y=0;y<h;y++) bmp.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
            return bmp;
        }catch(WriterException e){return null;} }

    private String makeInitials(String name){
        if(name==null||name.isEmpty()) return "?";
        String[] parts=name.trim().split("\\s+");
        if(parts.length==1) return name.substring(0,Math.min(2,name.length())).toUpperCase();
        return (""+parts[0].charAt(0)+parts[parts.length-1].charAt(0)).toUpperCase();
    }

    /* ═══════════════════════════════════════════════════════════════════
       HOME TAB
    ═══════════════════════════════════════════════════════════════════ */
    private void showHome(boolean hasData) {
        updateNavSelection(TAB_HOME);
        ScrollView sv=new ScrollView(this); sv.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout center=vll(dp(24),dp(50),dp(24),dp(30));

        // App logo + title
        FrameLayout logo=new FrameLayout(this);
        LinearLayout.LayoutParams lgLP=cwrap(); lgLP.width=dp(80); lgLP.height=dp(80);
        View glow=new View(this); glow.setBackground(glowBg());
        logo.addView(glow,new FrameLayout.LayoutParams(dp(80),dp(80),Gravity.CENTER));
        TextView cvTxt=mkTv("CV",30,Color.WHITE,true); cvTxt.setGravity(Gravity.CENTER);
        logo.addView(cvTxt,new FrameLayout.LayoutParams(dp(80),dp(80),Gravity.CENTER));
        center.addView(logo,lgLP);
        center.addView(spacer(dp(12)));

        TextView appName=mkTv("CardVault",28,Color.WHITE,true); appName.setGravity(Gravity.CENTER); appName.setLetterSpacing(0.10f);
        center.addView(appName,cwrap());
        TextView tagline=mkTv("Your Identity. Redefined.",12,0xFF7C6FCD,false); tagline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp=cwrap(); tlp.topMargin=dp(4); center.addView(tagline,tlp);

        if(hasData&&current!=null){
            String first=current.name.trim().split("\\s+")[0];
            TextView pill=mkTv("✦  Welcome back, "+first,12,0xFF00F5FF,false);
            pill.setPadding(dp(14),dp(7),dp(14),dp(7)); pill.setBackground(tagBg(0xFF00F5FF));
            LinearLayout.LayoutParams plp=cwrap(); plp.topMargin=dp(14);
            center.addView(pill,plp); center.addView(spacer(dp(18)));

            // Stats card
            int comp=current.completeness();
            int friendCount=FriendsManager.count(this);
            LinearLayout statsCard=vll(dp(16),dp(14),dp(16),dp(14));
            statsCard.setBackground(iBg());
            LinearLayout.LayoutParams sclp=fullW(-2); sclp.bottomMargin=dp(14);

            // Profile completeness bar
            TextView compLbl=mkTv("Profile "+comp+"% complete",10,comp>=80?0xFF00C896:comp>=50?0xFFFFD700:0xFFFF6B35,true);
            statsCard.addView(compLbl,mg(0,0,0,dp(6)));
            FrameLayout barBg=new FrameLayout(this);
            GradientDrawable barBgD=new GradientDrawable(); barBgD.setCornerRadius(dp(4)); barBgD.setColor(0xFF1E1E30); barBg.setBackground(barBgD);
            View barFill=new View(this);
            GradientDrawable barFillD=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    comp>=80?new int[]{0xFF00C896,0xFF00F5FF}:comp>=50?new int[]{0xFFFFD700,0xFFFF6B35}:new int[]{0xFFFF4444,0xFFFF6B35});
            barFillD.setCornerRadius(dp(4)); barFill.setBackground(barFillD); barBg.addView(barFill);
            barFill.getViewTreeObserver().addOnGlobalLayoutListener(()->barFill.setLayoutParams(new FrameLayout.LayoutParams((int)(barBg.getWidth()*comp/100f),dp(6))));
            statsCard.addView(barBg,new LinearLayout.LayoutParams(-1,dp(6)));
            statsCard.addView(spacer(dp(12)));

            // Stats row
            LinearLayout statsRow=hll(Gravity.CENTER); statsRow.setGravity(Gravity.CENTER);
            addStat(statsRow,csvCount(current.skills),"Skills");
            addStatDiv(statsRow);
            addStat(statsRow,profiles.size(),"Cards");
            addStatDiv(statsRow);
            addStat(statsRow,friendCount,"Friends");
            statsCard.addView(statsRow,new LinearLayout.LayoutParams(-1,-2));
            center.addView(statsCard,sclp);

            // Quick actions
            TextView vBtn=mkPBtn("View My Card  →");
            center.addView(vBtn,fullW(dp(52)));
            vBtn.setOnClickListener(v->switchTab(TAB_CARD));
            center.addView(spacer(dp(10)));

            // Current card mini-preview
            View miniCard=buildMiniPreviewCard(current);
            LinearLayout.LayoutParams mcLP=fullW(-2); mcLP.bottomMargin=dp(14);
            center.addView(miniCard,mcLP);

        } else {
            center.addView(spacer(dp(30)));
            LinearLayout chips=hll(Gravity.CENTER); chips.setGravity(Gravity.CENTER);
            for(String s:new String[]{"📇 Multi-Profile","✨ Themes","🔲 QR Share","👥 Friends"}){
                TextView ch=mkTv(s,9,0xFF9999CC,false); ch.setPadding(dp(8),dp(5),dp(8),dp(5)); ch.setBackground(iBg());
                LinearLayout.LayoutParams clp2=new LinearLayout.LayoutParams(-2,-2); clp2.rightMargin=dp(6);
                chips.addView(ch,clp2);
            }
            LinearLayout.LayoutParams cLP=fullW(-2); cLP.bottomMargin=dp(24);
            center.addView(chips,cLP);
            TextView sBtn=mkPBtn("Create My Card  →");
            center.addView(sBtn,fullW(dp(52)));
            sBtn.setOnClickListener(v->{ editing=new Profile(); step=0; switchTab(TAB_PROFILE); showForm(); });
        }

        sv.addView(center,mww()); pushScreen(sv); pulse(logo);
    }

    private View buildMiniPreviewCard(Profile p){
        int idx=p.cardTheme,bg=TC[idx][0],a1=TC[idx][1],a2=TC[idx][2]; boolean lt=idx==1;
        CardView card=new CardView(this); card.setCardBackgroundColor(bg); card.setRadius(dp(16)); card.setCardElevation(dp(8));
        FrameLayout inner=new FrameLayout(this); inner.setBackground(cGrad(idx));
        View blb=new View(this); blb.setBackground(blob(a1,12));
        inner.addView(blb,new FrameLayout.LayoutParams(dp(90),dp(90),Gravity.END|Gravity.TOP));
        LinearLayout row=hll(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(12),dp(14),dp(12));
        FrameLayout av=new FrameLayout(this); av.setBackground(avRing(a1,a2)); av.setClipToOutline(true);
        fillAvatar(av,dp(44),a1,lt,p.bitmap(),p.name);
        LinearLayout.LayoutParams avlp=new LinearLayout.LayoutParams(dp(44),dp(44)); avlp.rightMargin=dp(12); row.addView(av,avlp);
        LinearLayout info=vll(0,0,0,0);
        TextView nameTv=mkTv(p.name.isEmpty()?"Your Name":p.name,13,lt?0xFF1A1A2E:Color.WHITE,true);
        nameTv.setMaxLines(1); nameTv.setEllipsize(TextUtils.TruncateAt.END); info.addView(nameTv);
        info.addView(mkTv(p.role.isEmpty()?"Role / Title":p.role,10,a1,false),mg(0,dp(2),0,dp(4)));
        if(!p.skills.isEmpty()) info.addView(colorTagsRow(p.skills,3));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
        TextView editHint=mkTv("tap to edit →",8,Color.argb(80,Color.red(a1),Color.green(a1),Color.blue(a1)),false);
        editHint.setGravity(Gravity.END|Gravity.BOTTOM);
        FrameLayout.LayoutParams elhp=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.BOTTOM);
        elhp.rightMargin=dp(10); elhp.bottomMargin=dp(6);
        inner.addView(row,mpFP()); inner.addView(editHint,elhp); card.addView(inner,mpFP());
        card.setOnClickListener(v->{ editing=cloneOf(p); step=0; switchTab(TAB_PROFILE); showForm(); });
        return card;
    }

    private void addStat(LinearLayout p,int n,String label){
        LinearLayout col=vll(dp(14),0,dp(14),0); col.setGravity(Gravity.CENTER);
        col.addView(mkTv(String.valueOf(n),20,Color.WHITE,true));
        col.addView(mkTv(label,10,0xFF666680,false));
        p.addView(col,new LinearLayout.LayoutParams(-2,-2));
    }
    private void addStatDiv(LinearLayout p){ View d=new View(this); d.setBackgroundColor(0xFF1E1E30); p.addView(d,new LinearLayout.LayoutParams(1,dp(30))); }

    /* ═══════════════════════════════════════════════════════════════════
       MY CARD TAB (preview + flip)
    ═══════════════════════════════════════════════════════════════════ */
    private void showPreview() {
        if(current==null){ switchTab(TAB_HOME); return; }
        updateNavSelection(TAB_CARD);
        ScrollView sv=new ScrollView(this); sv.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout content=vll(dp(20),dp(56),dp(20),dp(20));

        TextView title=mkTv(current.name,20,Color.WHITE,true); title.setMaxLines(1); title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp=fullW(-2); tlp.bottomMargin=dp(4);
        content.addView(title,tlp);
        content.addView(mkTv(TN[current.cardTheme]+" Theme",11,0xFF555570,false),mg(0,0,0,dp(18)));

        // Flip card
        FrameLayout flipContainer=new FrameLayout(this);
        View cardFront=buildFinalCard(current);
        View cardBack=buildCardBack(current);
        cardBack.setVisibility(View.GONE);
        flipContainer.addView(cardFront,new FrameLayout.LayoutParams(-1,dp(280)));
        flipContainer.addView(cardBack, new FrameLayout.LayoutParams(-1,dp(280)));
        LinearLayout.LayoutParams fcLP=fullW(dp(280)); fcLP.bottomMargin=dp(6);
        content.addView(flipContainer,fcLP);

        flipContainer.setAlpha(0f); flipContainer.setScaleX(0.87f); flipContainer.setScaleY(0.87f);
        flipContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(560)
                .setInterpolator(new OvershootInterpolator(1.3f)).setStartDelay(100).start();

        TextView flipHint=mkTv("↕ Tap card to flip  •  Scan QR on back",10,0xFF444466,false);
        flipHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hLP=fullW(-2); hLP.bottomMargin=dp(16); content.addView(flipHint,hLP);

        final boolean[] flipped={false};
        flipContainer.setOnClickListener(v->{
            float cd=8000*getResources().getDisplayMetrics().density;
            cardFront.setCameraDistance(cd); cardBack.setCameraDistance(cd);
            if(!flipped[0]){
                ObjectAnimator oa=ObjectAnimator.ofFloat(cardFront,"rotationY",0f,90f); oa.setDuration(200); oa.setInterpolator(new AccelerateInterpolator());
                oa.addListener(new AnimatorListenerAdapter(){ @Override public void onAnimationEnd(Animator a){ cardFront.setVisibility(View.GONE); cardBack.setVisibility(View.VISIBLE); cardBack.setRotationY(-90f); ObjectAnimator ia=ObjectAnimator.ofFloat(cardBack,"rotationY",-90f,0f); ia.setDuration(200); ia.setInterpolator(new DecelerateInterpolator()); ia.start(); }});
                oa.start(); flipped[0]=true; flipHint.setText("↕ Tap to flip back");
            } else {
                ObjectAnimator oa=ObjectAnimator.ofFloat(cardBack,"rotationY",0f,90f); oa.setDuration(200); oa.setInterpolator(new AccelerateInterpolator());
                oa.addListener(new AnimatorListenerAdapter(){ @Override public void onAnimationEnd(Animator a){ cardBack.setVisibility(View.GONE); cardFront.setVisibility(View.VISIBLE); cardFront.setRotationY(-90f); ObjectAnimator ia=ObjectAnimator.ofFloat(cardFront,"rotationY",-90f,0f); ia.setDuration(200); ia.setInterpolator(new DecelerateInterpolator()); ia.start(); }});
                oa.start(); flipped[0]=false; flipHint.setText("↕ Tap card to flip  •  Scan QR on back");
            }
        });

        // Action buttons
        LinearLayout r1=hll(Gravity.CENTER); LinearLayout.LayoutParams r1lp=fullW(-2); r1lp.bottomMargin=dp(10);
        TextView saveBtn=mkPBtn("⬇  Save to Gallery"); LinearLayout.LayoutParams s1=new LinearLayout.LayoutParams(0,dp(52),1f); s1.rightMargin=dp(8);
        r1.addView(saveBtn,s1); saveBtn.setOnClickListener(v->saveImg(cardFront));
        TextView shareBtn=mkSBtn("↗  Share"); r1.addView(shareBtn,new LinearLayout.LayoutParams(0,dp(52),1f));
        shareBtn.setOnClickListener(v->shareCard(cardFront)); content.addView(r1,r1lp);

        LinearLayout r2=hll(Gravity.CENTER); LinearLayout.LayoutParams r2lp=fullW(-2); r2lp.bottomMargin=dp(10);
        TextView copyBtn=mkSBtn("📋  Copy Info"); LinearLayout.LayoutParams c1=new LinearLayout.LayoutParams(0,dp(46),1f); c1.rightMargin=dp(8);
        r2.addView(copyBtn,c1); copyBtn.setOnClickListener(v->copyContactToClipboard());
        TextView nfcBtn=mkSBtn("📡  NFC Tag"); nfcBtn.setTextColor((nfcAdapter!=null&&nfcAdapter.isEnabled())?0xFF7C6FCD:0xFF444444);
        r2.addView(nfcBtn,new LinearLayout.LayoutParams(0,dp(46),1f));
        nfcBtn.setOnClickListener(v->{ if(nfcAdapter==null){showToast("NFC not available"); return;} if(!nfcAdapter.isEnabled()){startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS)); return;} showToast("Tap phone to NFC tag to write!"); });
        content.addView(r2,r2lp);

        LinearLayout r3=hll(Gravity.CENTER); LinearLayout.LayoutParams r3lp=fullW(-2); r3lp.bottomMargin=dp(16);
        TextView chBtn=mkSBtn("🎨 Theme"); LinearLayout.LayoutParams chlp=new LinearLayout.LayoutParams(0,dp(46),1f); chlp.rightMargin=dp(8);
        r3.addView(chBtn,chlp); chBtn.setOnClickListener(v->showThemes());
        TextView allBtn=mkSBtn("All Profiles ("+profiles.size()+")"); r3.addView(allBtn,new LinearLayout.LayoutParams(0,dp(46),1f));
        allBtn.setOnClickListener(v->showAllProfiles()); content.addView(r3,r3lp);

        // Info cards
        iCard(content,"📧 Email",current.email,"mailto:"+current.email);
        iCard(content,"📞 Phone",current.phone,"tel:"+current.phone);
        iCard(content,"🎓 Education",eduText(current),null);
        iCard(content,"⚡ Skills",current.skills,null);
        iCard(content,"🏆 Achievements",current.achievements,null);
        iCard(content,"📜 Certificates",current.certificates,null);
        iCard(content,"💻 GitHub",current.github,"https://"+current.github);
        iCard(content,"🔗 LinkedIn",current.linkedin,"https://"+current.linkedin);
        iCard(content,"🐦 Twitter",current.twitter,"https://"+current.twitter);

        sv.addView(content,mww()); pushScreen(sv);
    }

    /* ═══════════════════════════════════════════════════════════════════
       FRIENDS TAB
    ═══════════════════════════════════════════════════════════════════ */
    private void showFriends() {
        updateNavSelection(TAB_FRIENDS);
        ScrollView sv=new ScrollView(this); sv.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout content=vll(dp(20),dp(50),dp(20),dp(30));

        List<FriendsManager.Friend> friends=FriendsManager.load(this);

        // Header
        TextView titleTv=mkTv("Friends",24,Color.WHITE,true);
        LinearLayout.LayoutParams ttp=fullW(-2); ttp.bottomMargin=dp(2); content.addView(titleTv,ttp);
        content.addView(mkTv(friends.size()+" contacts saved",11,0xFF555570,false),mg(0,0,0,dp(20)));

        if(friends.isEmpty()){
            // Empty state
            LinearLayout empty=vll(dp(20),dp(40),dp(20),dp(40)); empty.setGravity(Gravity.CENTER);
            TextView emIco=mkTv("👥",52,Color.WHITE,false); emIco.setGravity(Gravity.CENTER); empty.addView(emIco,cwrap());
            TextView emTitle=mkTv("No friends yet",18,Color.WHITE,true); emTitle.setGravity(Gravity.CENTER);
            empty.addView(emTitle,mg(0,dp(14),0,dp(8)));
            TextView emSub=mkTv("Scan a friend's CardVault QR\nand tap \"Add as Friend\"",12,0xFF555570,false);
            emSub.setGravity(Gravity.CENTER); emSub.setLineSpacing(dp(3),1f); empty.addView(emSub,cwrap());
            content.addView(empty,mww());
        } else {
            for(FriendsManager.Friend f:friends) content.addView(makeFriendRow(f,sv));
        }

        sv.addView(content,mww()); pushScreen(sv);
    }

    private View makeFriendRow(FriendsManager.Friend f, View parent) {
        int accentColor,bgColor;
        try{ accentColor=Color.parseColor(f.accent); }catch(Exception e){ accentColor=0xFF7C6FCD; }
        try{ bgColor=Color.parseColor(f.color); }catch(Exception e){ bgColor=0xFF00F5FF; }

        CardView card=new CardView(this); card.setCardBackgroundColor(0xFF0E0E1A);
        card.setRadius(dp(18)); card.setCardElevation(dp(5));
        FrameLayout inner=new FrameLayout(this);
        inner.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF0E0E1A,0xFF131320}));

        // Accent blob
        View blb=new View(this); blb.setBackground(blob(accentColor,10));
        inner.addView(blb,new FrameLayout.LayoutParams(dp(60),dp(60),Gravity.END|Gravity.TOP));

        LinearLayout row=hll(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(14),dp(14),dp(14));

        // Avatar with friend's theme color
        FrameLayout av=new FrameLayout(this); av.setBackground(avRing(accentColor,accentColor)); av.setClipToOutline(true);
        String ini=(f.initials==null||f.initials.isEmpty())?makeInitials(f.name):f.initials;
        TextView iniTv=mkTv(ini,14,accentColor,true); iniTv.setGravity(Gravity.CENTER);
        av.addView(iniTv,new FrameLayout.LayoutParams(dp(50),dp(50),Gravity.CENTER));
        LinearLayout.LayoutParams avLP=new LinearLayout.LayoutParams(dp(50),dp(50)); avLP.rightMargin=dp(12); row.addView(av,avLP);

        // Info column
        LinearLayout info=vll(0,0,0,0);
        TextView nameTv=mkTv(f.name.isEmpty()?"Unknown":f.name,14,Color.WHITE,true);
        nameTv.setMaxLines(1); nameTv.setEllipsize(TextUtils.TruncateAt.END); info.addView(nameTv);
        if(f.role!=null&&!f.role.isEmpty()){ TextView rt=mkTv(f.role,10,accentColor,false); rt.setMaxLines(1); info.addView(rt,mg(0,dp(2),0,dp(5))); }

        // Quick action chips: Call, WA, Email
        LinearLayout chips=hll(Gravity.CENTER_VERTICAL);
        if(f.phone!=null&&!f.phone.isEmpty()) addMiniActionChip(chips,"📞","Call",0xFF00C896,v->dialFriend(f.phone));
        if(f.whatsapp!=null&&!f.whatsapp.isEmpty()) addMiniActionChip(chips,"💬","WA",0xFF25D366,v->whatsappFriend(f.whatsapp));
        if(f.email!=null&&!f.email.isEmpty()) addMiniActionChip(chips,"✉","Email",0xFF4A90D9,v->emailFriend(f.email));
        info.addView(chips);

        info.addView(mkTv("Added "+friendTimeAgo(f.addedAt),8,0xFF2A2A42,false),mg(0,dp(5),0,0));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));

        // Right: View card + Delete
        LinearLayout btns=vll(0,0,0,0); btns.setGravity(Gravity.CENTER);
        TextView viewBtn=mkTv("View",10,accentColor,true); viewBtn.setPadding(dp(9),dp(5),dp(9),dp(5)); viewBtn.setBackground(tagBg(accentColor));
        LinearLayout.LayoutParams vbLP=new LinearLayout.LayoutParams(-2,-2); vbLP.bottomMargin=dp(6); btns.addView(viewBtn,vbLP);
        viewBtn.setOnClickListener(v->showFriendCardPopup(f));

        TextView delBtn=mkTv("✕",11,0xFFFF4444,true); delBtn.setPadding(dp(9),dp(5),dp(9),dp(5)); delBtn.setBackground(tagBg(0xFFFF4444));
        btns.addView(delBtn);
        delBtn.setOnClickListener(v->{ FriendsManager.deleteFriend(this,f.id); btnPress(delBtn,()->{ refreshFriendBadge(); showFriends(); }); });
        row.addView(btns,new LinearLayout.LayoutParams(-2,-2));

        inner.addView(row,new FrameLayout.LayoutParams(-1,-2)); card.addView(inner,new FrameLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams wLP=fullW(-2); wLP.bottomMargin=dp(10);
        LinearLayout wrap=vll(0,0,0,0); wrap.addView(card,new LinearLayout.LayoutParams(-1,-2));
        wrap.setLayoutParams(wLP); return wrap;
    }

    // ── Animated popup when tapping "View" on a friend ───────────────────
    private void showFriendCardPopup(FriendsManager.Friend f) {
        int accentColor,bgColor;
        try{ accentColor=Color.parseColor(f.accent); }catch(Exception e){ accentColor=0xFF7C6FCD; }
        try{ bgColor=Color.parseColor(f.color); }catch(Exception e){ bgColor=0xFF00F5FF; }

        // Overlay
        FrameLayout overlay=new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(180,0,0,0));
        overlay.setClickable(true);
        rootFrame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));

        // Ripple rings
        final int acc=accentColor;
        for(int i=0;i<3;i++){ final int d=i; View ring=new View(this);
            GradientDrawable rd=new GradientDrawable(); rd.setShape(GradientDrawable.OVAL); rd.setColor(Color.TRANSPARENT); rd.setStroke(dp(2),Color.argb(55,Color.red(acc),Color.green(acc),Color.blue(acc))); ring.setBackground(rd);
            FrameLayout.LayoutParams rlp=new FrameLayout.LayoutParams(dp(140),dp(140),Gravity.CENTER); overlay.addView(ring,rlp);
            ring.setAlpha(0f); ring.setScaleX(0.2f); ring.setScaleY(0.2f);
            Runnable anim=new Runnable(){ @Override public void run(){ ring.setAlpha(0.6f); ring.setScaleX(0.2f); ring.setScaleY(0.2f); ring.animate().alpha(0f).scaleX(3.5f).scaleY(3.5f).setDuration(2000).setInterpolator(new DecelerateInterpolator(1.8f)).withEndAction(this).start(); } };
            ring.postDelayed(anim,300L+d*650L);
        }

        // Card popup
        androidx.cardview.widget.CardView popup=new androidx.cardview.widget.CardView(this);
        popup.setCardBackgroundColor(0xFF0E0E1A); popup.setRadius(dp(24)); popup.setCardElevation(dp(30));
        popup.setClickable(true);

        FrameLayout popInner=new FrameLayout(this);
        popInner.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF0E0E1A,0xFF131325}));

        View popBlob=new View(this); popBlob.setBackground(blob(accentColor,12));
        popInner.addView(popBlob,new FrameLayout.LayoutParams(dp(110),dp(110),Gravity.END|Gravity.TOP));

        LinearLayout popContent=vll(dp(22),dp(22),dp(22),dp(22));

        // Header: avatar + name/role + close
        FrameLayout hdr=new FrameLayout(this);
        TextView closeBtn=mkTv("✕",18,Color.WHITE,false); closeBtn.setPadding(dp(8),dp(4),dp(8),dp(4));
        closeBtn.setOnClickListener(v->{ overlay.animate().alpha(0f).setDuration(200).withEndAction(()->rootFrame.removeView(overlay)).start(); });
        hdr.addView(closeBtn,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END));

        LinearLayout nameRow=hll(Gravity.CENTER_VERTICAL);
        FrameLayout av=new FrameLayout(this); av.setBackground(avRing(accentColor,accentColor)); av.setClipToOutline(true);
        String ini=(f.initials==null||f.initials.isEmpty())?makeInitials(f.name):f.initials;
        TextView iniTv=mkTv(ini,22,accentColor,true); iniTv.setGravity(Gravity.CENTER);
        av.addView(iniTv,new FrameLayout.LayoutParams(dp(66),dp(66),Gravity.CENTER));
        pulse(av);
        LinearLayout.LayoutParams avLP2=new LinearLayout.LayoutParams(dp(66),dp(66)); avLP2.rightMargin=dp(14); nameRow.addView(av,avLP2);

        LinearLayout nameCol=vll(0,0,0,0);
        TextView pNameTv=mkTv(f.name.isEmpty()?"Unknown":f.name,20,Color.WHITE,true);
        pNameTv.setMaxLines(2); pNameTv.setEllipsize(TextUtils.TruncateAt.END); nameCol.addView(pNameTv);
        if(f.role!=null&&!f.role.isEmpty()){ TextView rt=mkTv(f.role,12,accentColor,false); rt.setAlpha(0.9f); nameCol.addView(rt,mg(0,dp(3),0,0)); }
        nameRow.addView(nameCol,new LinearLayout.LayoutParams(0,-2,1f));
        hdr.addView(nameRow,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL));
        popContent.addView(hdr,mww());

        // Divider
        View div2=new View(this); div2.setBackgroundColor(Color.argb(35,Color.red(accentColor),Color.green(accentColor),Color.blue(accentColor)));
        popContent.addView(div2,mg(0,dp(14),0,dp(14)));
        View divLp=div2; ((LinearLayout.LayoutParams)div2.getLayoutParams()).height=1;

        // Action buttons: Call, WhatsApp, Email, Save Contact
        LinearLayout actions=hll(Gravity.CENTER);
        if(f.phone!=null&&!f.phone.isEmpty())    addPopupAction(actions,"📞","Call",    accentColor,  v->dialFriend(f.phone));
        if(f.whatsapp!=null&&!f.whatsapp.isEmpty()) addPopupAction(actions,"💬","WA",  0xFF25D366,   v->whatsappFriend(f.whatsapp));
        if(f.email!=null&&!f.email.isEmpty())    addPopupAction(actions,"✉", "Email",   0xFF4A90D9,   v->emailFriend(f.email));
        addPopupAction(actions,"💾","Save",0xFFFFD700,v->saveContactFromFriend(f));
        popContent.addView(actions,mww());

        // Social links
        boolean hasSocial=(f.linkedin!=null&&!f.linkedin.isEmpty())||(f.twitter!=null&&!f.twitter.isEmpty())||(f.github!=null&&!f.github.isEmpty());
        if(hasSocial){
            View div3=new View(this); div3.setBackgroundColor(Color.argb(35,Color.red(accentColor),Color.green(accentColor),Color.blue(accentColor)));
            LinearLayout.LayoutParams div3lp=new LinearLayout.LayoutParams(-1,1); div3lp.setMargins(0,dp(14),0,dp(14)); popContent.addView(div3,div3lp);
            LinearLayout soc=hll(Gravity.CENTER_VERTICAL); soc.setGravity(Gravity.CENTER);
            if(f.linkedin!=null&&!f.linkedin.isEmpty()) addSocChip(soc,"🔗 LinkedIn",0xFF0077B5,v->openUrl("https://"+f.linkedin));
            if(f.twitter!=null&&!f.twitter.isEmpty())  addSocChip(soc,"🐦 Twitter",0xFF1DA1F2,v->openUrl("https://"+f.twitter));
            if(f.github!=null&&!f.github.isEmpty())    addSocChip(soc,"💻 GitHub",0xFFAAAAAA,v->openUrl("https://"+f.github));
            popContent.addView(soc,mww());
        }

        // Delete from list button
        View div4=new View(this); div4.setBackgroundColor(Color.argb(35,Color.red(accentColor),Color.green(accentColor),Color.blue(accentColor)));
        LinearLayout.LayoutParams div4lp=new LinearLayout.LayoutParams(-1,1); div4lp.setMargins(0,dp(16),0,dp(16)); popContent.addView(div4,div4lp);
        TextView removeBtn=mkSBtn("🗑  Remove from Friends");
        removeBtn.setTextColor(0xFFFF4444);
        GradientDrawable removeBg=new GradientDrawable(); removeBg.setShape(GradientDrawable.RECTANGLE); removeBg.setCornerRadius(dp(12)); removeBg.setColor(Color.argb(20,255,68,68)); removeBg.setStroke(dp(1),Color.argb(60,255,68,68));
        removeBtn.setBackground(removeBg);
        popContent.addView(removeBtn,fullW(dp(46)));
        removeBtn.setOnClickListener(v->{ FriendsManager.deleteFriend(this,f.id);
            overlay.animate().alpha(0f).setDuration(200).withEndAction(()->{rootFrame.removeView(overlay); refreshFriendBadge(); showFriends();}).start(); });

        popInner.addView(popContent,mpFP()); popup.addView(popInner,new FrameLayout.LayoutParams(-1,-2));
        FrameLayout.LayoutParams plp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        plp.setMargins(dp(18),dp(60),dp(18),dp(60));
        overlay.addView(popup,plp);

        // Entrance animation
        popup.setAlpha(0f); popup.setScaleX(0.72f); popup.setScaleY(0.72f); popup.setTranslationY(60f);
        popup.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(480)
                .setInterpolator(new OvershootInterpolator(1.3f)).setStartDelay(80).start();

        overlay.setOnClickListener(v->{ overlay.animate().alpha(0f).setDuration(200).withEndAction(()->rootFrame.removeView(overlay)).start(); });
    }

    private void addPopupAction(LinearLayout parent,String icon,String label,int color,View.OnClickListener cl){
        LinearLayout col=vll(dp(4),dp(2),dp(4),dp(2)); col.setGravity(Gravity.CENTER);
        FrameLayout circle=new FrameLayout(this);
        GradientDrawable bg=new GradientDrawable(); bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(30,Color.red(color),Color.green(color),Color.blue(color)));
        bg.setStroke(dp(1),Color.argb(70,Color.red(color),Color.green(color),Color.blue(color)));
        circle.setBackground(bg);
        TextView ico=mkTv(icon,20,color,false); ico.setGravity(Gravity.CENTER);
        circle.addView(ico,new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.CENTER));
        col.addView(circle,new LinearLayout.LayoutParams(dp(54),dp(54)));
        TextView lbl=mkTv(label,9,Color.argb(180,255,255,255),false); lbl.setGravity(Gravity.CENTER);
        col.addView(lbl,mg(0,dp(4),0,0));
        col.setOnClickListener(cl); col.setClickable(true);
        parent.addView(col,new LinearLayout.LayoutParams(0,-2,1f));
    }

    private void addSocChip(LinearLayout p,String label,int color,View.OnClickListener cl){
        TextView chip=mkTv(label,11,color,true); chip.setPadding(dp(11),dp(6),dp(11),dp(6));
        GradientDrawable bg=new GradientDrawable(); bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(dp(20));
        bg.setColor(Color.argb(25,Color.red(color),Color.green(color),Color.blue(color)));
        bg.setStroke(dp(1),Color.argb(60,Color.red(color),Color.green(color),Color.blue(color)));
        chip.setBackground(bg); chip.setOnClickListener(cl);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.rightMargin=dp(8);
        p.addView(chip,lp);
    }

    private void addMiniActionChip(LinearLayout p,String icon,String label,int color,View.OnClickListener cl){
        LinearLayout chip=hll(Gravity.CENTER_VERTICAL); chip.setPadding(dp(7),dp(3),dp(7),dp(3));
        GradientDrawable bg=new GradientDrawable(); bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(dp(8));
        bg.setColor(Color.argb(25,Color.red(color),Color.green(color),Color.blue(color)));
        bg.setStroke(1,Color.argb(60,Color.red(color),Color.green(color),Color.blue(color)));
        chip.setBackground(bg); chip.setOnClickListener(cl);
        TextView icTv=mkTv(icon,11,color,false); icTv.setPadding(0,0,dp(3),0); chip.addView(icTv);
        chip.addView(mkTv(label,8,Color.argb(160,200,200,220),false));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.rightMargin=dp(6);
        p.addView(chip,lp);
    }

    // ── Friend contact actions ────────────────────────────────────────────
    private void dialFriend(String phone){ try{ startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+phone))); }catch(Exception e){ showToast("No phone app"); } }
    private void whatsappFriend(String phone){ String num=phone.replaceAll("[^0-9]",""); try{ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+num))); }catch(Exception e){ showToast("WhatsApp not installed"); } }
    private void emailFriend(String email){ try{ startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+email))); }catch(Exception e){ showToast("No email app"); } }
    private void openUrl(String url){ try{ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }catch(Exception e){ showToast("Cannot open link"); } }
    private void saveContactFromFriend(FriendsManager.Friend f){
        Intent i=new Intent(Intent.ACTION_INSERT); i.setType(android.provider.ContactsContract.Contacts.CONTENT_TYPE);
        if(f.name!=null&&!f.name.isEmpty()) i.putExtra(android.provider.ContactsContract.Intents.Insert.NAME,f.name);
        if(f.phone!=null&&!f.phone.isEmpty()) i.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE,f.phone);
        if(f.email!=null&&!f.email.isEmpty()) i.putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL,f.email);
        if(f.role!=null&&!f.role.isEmpty()) i.putExtra(android.provider.ContactsContract.Intents.Insert.JOB_TITLE,f.role);
        try{ startActivity(i); }catch(Exception e){ showToast("No contacts app found"); }
    }

    private String friendTimeAgo(long ts){
        long diff=System.currentTimeMillis()-ts; long mins=diff/60000; long hours=mins/60; long days=hours/24;
        if(days>0) return days+"d ago"; if(hours>0) return hours+"h ago"; if(mins>0) return mins+"m ago"; return "just now";
    }

    /* ═══════════════════════════════════════════════════════════════════
       PROFILE TAB — Multi-step form
    ═══════════════════════════════════════════════════════════════════ */
    private void showForm() {
        updateNavSelection(TAB_PROFILE);
        if(editing==null) editing=new Profile();
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(Color.TRANSPARENT);
        sv.setClipToPadding(false); sv.setPadding(0,0,0,dp(240));
        LinearLayout root=vll(dp(20),dp(52),dp(20),dp(10));

        // Step dots
        String[] stepTitles={"Personal","Education","Skills"};
        LinearLayout dotRow=hll(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams drLP=fullW(-2); drLP.bottomMargin=dp(18);
        for(int i=0;i<STEPS;i++){
            LinearLayout col=vll(0,0,0,0); col.setGravity(Gravity.CENTER);
            TextView dot=mkTv(i<step?"✓":String.valueOf(i+1),10,i<=step?Color.WHITE:0xFF2A2A45,true); dot.setGravity(Gravity.CENTER);
            GradientDrawable dotBg=new GradientDrawable(); dotBg.setShape(GradientDrawable.OVAL);
            if(i<step) dotBg.setColor(0xFF00C896); else if(i==step) dotBg.setColor(0xFF7C6FCD); else dotBg.setColor(0xFF1A1A2E);
            dot.setBackground(dotBg); col.addView(dot,new LinearLayout.LayoutParams(dp(34),dp(34)));
            int lblC=i<step?0xFF00C896:i==step?0xFF7C6FCD:0xFF333355;
            TextView lbl=mkTv(stepTitles[i],9,lblC,i==step); lbl.setGravity(Gravity.CENTER);
            col.addView(lbl,mg(0,dp(4),0,0)); dotRow.addView(col,new LinearLayout.LayoutParams(0,-2,1f));
            if(i<STEPS-1){ View line=new View(this); line.setBackgroundColor(i<step?0xFF00C896:0xFF1A1A2E);
                LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,dp(2),0.4f); llp.gravity=Gravity.CENTER_VERTICAL; dotRow.addView(line,llp); }
        }
        root.addView(dotRow,drLP);

        // Live preview
        View miniCard=buildLivePreview();
        LinearLayout.LayoutParams mcLP=fullW(dp(74)); mcLP.bottomMargin=dp(18);
        root.addView(miniCard,mcLP);

        // Panels
        LinearLayout[] panels=new LinearLayout[STEPS];
        panels[0]=vll(0,0,0,0); secLabel(panels[0],"PERSONAL INFO");
        addPhotoPicker(panels[0]);
        etName =validField(panels[0],"Full Name *",    "Arjun Kumar Sharma",    false,editing.name,  "name");
        etRole =validField(panels[0],"Role / Title *", "Android Developer",     false,editing.role,  "role");
        etEmail=validField(panels[0],"Email",          "arjun@gmail.com",       false,editing.email,"email");
        etPhone=validField(panels[0],"Phone",          "+91 98765 43210",       false,editing.phone,"phone");

        panels[1]=vll(0,0,0,0); secLabel(panels[1],"EDUCATION");
        etSchool =validField(panels[1],"School",               "DY Patil High School",      false,editing.school, "");
        etCollege=validField(panels[1],"College / University", "MSBTE Polytechnic, Pune",   false,editing.college,"");

        panels[2]=vll(0,0,0,0); secLabel(panels[2],"SKILLS & ACHIEVEMENTS");
        etSkills=validField(panels[2],"Skills (comma separated)","Java, Android, Python...",true,editing.skills,"");
        etAch   =validField(panels[2],"Achievements",            "1st in Hackathon...",     true,editing.achievements,"");
        etCerts =validField(panels[2],"Certificates",            "AWS Certified...",        true,editing.certificates,"");
        secLabel(panels[2],"SOCIAL & LINKS");
        etGithub  =validField(panels[2],"GitHub",      "github.com/yourname",      false,editing.github,  "github");
        etLinkedin=validField(panels[2],"LinkedIn",    "linkedin.com/in/yourname", false,editing.linkedin,"linkedin");
        etTwitter =validField(panels[2],"Twitter / X", "@yourhandle",              false,editing.twitter, "twitter");

        for(int i=0;i<STEPS;i++){ panels[i].setVisibility(i==step?View.VISIBLE:View.GONE); root.addView(panels[i],mww()); }

        // Nav
        LinearLayout nav=hll(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams navLP=fullW(-2); navLP.topMargin=dp(22);
        TextView backBtn=mkSBtn("← Back"); backBtn.setVisibility(step>0?View.VISIBLE:View.INVISIBLE);
        nav.addView(backBtn,new LinearLayout.LayoutParams(dp(100),dp(50)));
        boolean isLast=step==STEPS-1;
        TextView nextBtn=mkPBtn(isLast?"Save & Choose Theme  ✦":"Next  →");
        LinearLayout.LayoutParams nbLP=new LinearLayout.LayoutParams(0,dp(50),1f); nbLP.leftMargin=dp(10);
        nav.addView(nextBtn,nbLP); root.addView(nav,navLP);

        nextBtn.setOnClickListener(v->{
            collectStep(step); String err=validateStep(step);
            if(err!=null){ showToast(err); shakeView(step==0?etName:null); return; }
            if(!isLast){ hideKb(); panels[step].setVisibility(View.GONE); step++;
                removeScreen(); showForm();
            } else {
                collectAllSteps();
                if(editing.name.trim().isEmpty()){ shakeView(etName); showToast("Name is required!"); return; }
                hideKb();
                boolean found=false,isNew=true;
                for(int k=0;k<profiles.size();k++) if(profiles.get(k).id.equals(editing.id)){ profiles.set(k,editing); found=true; isNew=false; break; }
                if(!found) profiles.add(editing);
                saveProfiles(); setActive(editing); step=0;
                boolean sc=isNew&&!prefs.getBoolean(K_FIRST_RUN,false);
                prefs.edit().putBoolean(K_FIRST_RUN,true).apply();
                btnPress(nextBtn,()->{ showThemes(); if(sc) rootFrame.postDelayed(this::launchConfetti,400); });
            }
        });
        backBtn.setOnClickListener(v->{ if(step>0){ collectStep(step); hideKb(); step--; removeScreen(); showForm(); } });

        sv.addView(root,mww()); pushScreen(sv);
    }

    private View buildLivePreview(){
        int idx=editing.cardTheme,bg=TC[idx][0],a1=TC[idx][1],a2=TC[idx][2]; boolean lt=idx==1;
        CardView card=new CardView(this); card.setCardBackgroundColor(bg); card.setRadius(dp(14)); card.setCardElevation(dp(6));
        FrameLayout inner=new FrameLayout(this); inner.setBackground(cGrad(idx));
        View blb=new View(this); blb.setBackground(blob(a1,14)); inner.addView(blb,new FrameLayout.LayoutParams(dp(70),dp(70),Gravity.END|Gravity.TOP));
        LinearLayout row=hll(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(10),dp(12),dp(10));
        livePreviewAv=new FrameLayout(this); livePreviewAv.setBackground(avRing(a1,a2)); livePreviewAv.setClipToOutline(true);
        fillAvatar(livePreviewAv,dp(42),a1,lt,editing.bitmap(),editing.name);
        LinearLayout.LayoutParams avlp=new LinearLayout.LayoutParams(dp(42),dp(42)); avlp.rightMargin=dp(12); row.addView(livePreviewAv,avlp);
        LinearLayout info=vll(0,0,0,0);
        livePreviewName=mkTv(editing.name.isEmpty()?"Your Name":editing.name,13,lt?0xFF1A1A2E:Color.WHITE,true);
        livePreviewName.setMaxLines(2); livePreviewName.setEllipsize(TextUtils.TruncateAt.END); info.addView(livePreviewName);
        livePreviewRole=mkTv(editing.role.isEmpty()?"Role / Title":editing.role,10,a1,false);
        info.addView(livePreviewRole,mg(0,dp(2),0,dp(4)));
        livePreviewTags=hll(Gravity.CENTER_VERTICAL); refreshPreviewTags(editing.skills,a2); info.addView(livePreviewTags);
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
        TextView lpLbl=mkTv("LIVE PREVIEW",7,Color.argb(70,Color.red(a1),Color.green(a1),Color.blue(a1)),true); lpLbl.setLetterSpacing(0.10f); lpLbl.setGravity(Gravity.END);
        FrameLayout.LayoutParams lpLP=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.BOTTOM); lpLP.rightMargin=dp(10); lpLP.bottomMargin=dp(5);
        inner.addView(row,mpFP()); inner.addView(lpLbl,lpLP); card.addView(inner,mpFP()); return card;
    }

    private void refreshPreviewTags(String csv,int color){
        if(livePreviewTags==null) return; livePreviewTags.removeAllViews();
        if(csv==null||csv.isEmpty()) return; String[] parts=csv.split(",");
        for(int i=0;i<Math.min(3,parts.length);i++){ String t=parts[i].trim(); if(t.length()>8) t=t.substring(0,7)+"…"; if(t.isEmpty()) continue;
            int sc=SKILL_COLORS[i%SKILL_COLORS.length]; TextView tag=mkTv(t,7,sc,true); tag.setPadding(dp(5),dp(2),dp(5),dp(2)); tag.setBackground(tagBg(sc));
            LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-2,-2); if(i>0) tlp.leftMargin=dp(4); livePreviewTags.addView(tag,tlp); }
    }

    private TextWatcher nameWatcher(){ return new SimpleTextWatcher(s->{ if(livePreviewName!=null) livePreviewName.setText(s.isEmpty()?"Your Name":s); }); }
    private TextWatcher roleWatcher(){ return new SimpleTextWatcher(s->{ if(livePreviewRole!=null) livePreviewRole.setText(s.isEmpty()?"Role / Title":s); }); }
    private TextWatcher skillsWatcher(int a2){ return new SimpleTextWatcher(s->refreshPreviewTags(s,a2)); }

    private void addPhotoPicker(LinearLayout parent){
        LinearLayout row=hll(Gravity.CENTER_VERTICAL); row.setBackground(iBg()); row.setPadding(dp(14),dp(14),dp(14),dp(14));
        LinearLayout.LayoutParams rlp=fullW(-2); rlp.bottomMargin=dp(10);
        profilePickerFrame=new FrameLayout(this); profilePickerFrame.setClipToOutline(true);
        profileImageView=new ImageView(this); profileImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profilePickerFrame.addView(profileImageView,new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.CENTER));
        refreshAvatarViews();
        LinearLayout.LayoutParams pplp=new LinearLayout.LayoutParams(dp(56),dp(56)); pplp.rightMargin=dp(14); row.addView(profilePickerFrame,pplp);
        LinearLayout col=vll(0,0,0,0); col.addView(mkTv("Profile Photo",13,Color.WHITE,true));
        boolean has=!editing.profileImgB64.isEmpty();
        col.addView(mkTv(has?"✓ Uploaded — tap to change":"Tap to upload (optional)",11,has?0xFF00C896:0xFF555570,false),mg(0,dp(3),0,0));
        row.addView(col,new LinearLayout.LayoutParams(0,-2,1f));
        row.setOnClickListener(v->pickerLauncher.launch(new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)));
        parent.addView(row,rlp);
    }

    private void refreshAvatarViews(){
        if(profileImageView==null||editing==null) return;
        Bitmap bmp=editing.bitmap();
        if(bmp!=null){ profileImageView.setImageBitmap(circle(bmp)); profilePickerFrame.setBackground(circleStroke(0xFF7C6FCD)); }
        else{ profileImageView.setImageDrawable(null); profilePickerFrame.setBackground(avatarPh()); }
        if(livePreviewAv!=null){ int idx=editing.cardTheme; livePreviewAv.removeAllViews(); fillAvatar(livePreviewAv,dp(42),TC[idx][1],idx==1,editing.bitmap(),editing.name); }
    }

    private EditText validField(LinearLayout parent,String label,String hint,boolean multi,String prefill,String type){
        LinearLayout container=vll(dp(14),dp(10),dp(14),dp(12)); container.setBackground(iBg());
        LinearLayout.LayoutParams clp=fullW(-2); clp.bottomMargin=dp(10);
        TextView lv=mkTv(label,9,0xFF7C6FCD,true); lv.setLetterSpacing(0.12f); container.addView(lv,mg(0,0,0,dp(4)));
        EditText et=new EditText(this); et.setHint(hint); et.setHintTextColor(0xFF252540); et.setTextColor(Color.WHITE); et.setTextSize(14); et.setBackground(null); et.setPadding(0,0,0,0);
        if(!prefill.isEmpty()) et.setText(prefill);
        if(multi){ et.setMinLines(2); et.setMaxLines(6); et.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); et.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION); }
        else{ et.setMaxLines(1); switch(type){ case"email": et.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); break; case"phone": et.setInputType(android.text.InputType.TYPE_CLASS_PHONE); break; default: et.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS); break; } et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT); }
        TextView errLabel=mkTv("",9,0xFFFF6B6B,false); errLabel.setVisibility(View.GONE); errLabel.setPadding(0,dp(4),0,0);
        container.addView(et,mww()); container.addView(errLabel,mww());
        et.setOnFocusChangeListener((v,focused)->{ if(focused){ container.setBackground(iBgFocus()); container.postDelayed(()->{
            android.view.ViewParent vp=container.getParent(); while(vp!=null&&!(vp instanceof ScrollView)) vp=vp.getParent();
            if(vp instanceof ScrollView){ ScrollView sv2=(ScrollView)vp; int[]loc=new int[2]; container.getLocationInWindow(loc); int[]svl=new int[2]; sv2.getLocationInWindow(svl); sv2.smoothScrollTo(0,Math.max(0,loc[1]-svl[1]+sv2.getScrollY()-dp(120))); } },350); }
        else{ String val=et.getText().toString().trim(); String err=checkField(type,val); if(!err.isEmpty()){ errLabel.setText("⚠  "+err); errLabel.setVisibility(View.VISIBLE); container.setBackground(iBgError()); } else{ errLabel.setVisibility(View.GONE); container.setBackground(iBg()); } } });
        switch(type){ case"name": et.addTextChangedListener(nameWatcher()); break; case"role": et.addTextChangedListener(roleWatcher()); break; }
        if(label.startsWith("Skills")) et.addTextChangedListener(skillsWatcher(TC[editing.cardTheme][2]));
        parent.addView(container,clp); return et;
    }

    private String checkField(String type,String val){
        if(val.isEmpty()) return "";
        switch(type){
            case"email": if(!Patterns.EMAIL_ADDRESS.matcher(val).matches()) return "Enter a valid email"; break;
            case"phone": { String d=val.replaceAll("[^0-9]",""); if(d.length()<10||d.length()>13) return "Phone must be 10 digits"; break; }
            case"github": if(!val.contains("github.com")&&!val.startsWith("http")&&!val.startsWith("github")) return "Should look like: github.com/yourname"; break;
            case"linkedin": if(!val.contains("linkedin.com")&&!val.startsWith("http")) return "Should look like: linkedin.com/in/yourname"; break;
            case"twitter": if(!val.contains("twitter.com")&&!val.contains("x.com")&&!val.startsWith("@")) return "Use @handle or twitter.com/yourname"; break;
        } return "";
    }

    private String validateStep(int s){
        if(s==0){ if(etName.getText().toString().trim().isEmpty()) return "Name is required to continue";
            String e=etEmail.getText().toString().trim(); if(!e.isEmpty()&&!Patterns.EMAIL_ADDRESS.matcher(e).matches()) return "Fix the email format";
            String p=etPhone.getText().toString().trim(); if(!p.isEmpty()&&p.replaceAll("[^0-9]","").length()<10) return "Phone must be at least 10 digits"; }
        return null;
    }

    private void collectStep(int s){
        switch(s){
            case 0: editing.name=etName.getText().toString().trim(); editing.role=etRole.getText().toString().trim(); editing.email=etEmail.getText().toString().trim(); editing.phone=etPhone.getText().toString().trim(); break;
            case 1: editing.school=etSchool.getText().toString().trim(); editing.college=etCollege.getText().toString().trim(); break;
            case 2: editing.skills=etSkills.getText().toString().trim(); editing.achievements=etAch.getText().toString().trim(); editing.certificates=etCerts.getText().toString().trim(); editing.github=etGithub.getText().toString().trim(); editing.linkedin=etLinkedin.getText().toString().trim(); editing.twitter=etTwitter.getText().toString().trim(); break;
        }
    }
    private void collectAllSteps(){ collectStep(0); collectStep(1); collectStep(2); }
    private void secLabel(LinearLayout p,String text){ TextView tv=mkTv(text,10,0xFF3A3A55,true); tv.setLetterSpacing(0.14f); LinearLayout.LayoutParams lp=fullW(-2); lp.topMargin=dp(14); lp.bottomMargin=dp(8); p.addView(tv,lp); }

    /* ═══════════════════════════════════════════════════════════════════
       THEMES
    ═══════════════════════════════════════════════════════════════════ */
    private void showThemes(){
        ScrollView sv=new ScrollView(this); sv.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout content=vll(dp(20),dp(56),dp(20),dp(40));
        TextView bk=mkTv("← Back",13,0xFF7C6FCD,false); bk.setPadding(0,0,0,dp(12)); bk.setOnClickListener(v->{ step=2; removeScreen(); showForm(); });
        content.addView(bk);
        TextView hdr=mkTv("Choose Your Style",22,Color.WHITE,true); LinearLayout.LayoutParams hlp=fullW(-2); hlp.bottomMargin=dp(4); content.addView(hdr,hlp);
        content.addView(mkTv("Tap a theme to preview it",12,0xFF555570,false),mg(0,0,0,dp(22)));
        for(int i=0;i<4;i++){ final int idx=i; boolean sel=idx==current.cardTheme;
            LinearLayout wrap=vll(0,0,0,0); if(sel) wrap.addView(mkTv("  ✓  Selected",10,0xFF00C896,true),mg(0,0,0,dp(4)));
            View card=buildMiniThemeCard(idx,sel,current); wrap.addView(card,fullW(dp(110)));
            TextView nl=mkTv(TN[idx],12,sel?0xFF7C6FCD:0xFF444460,sel); nl.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nlp=fullW(-2); nlp.topMargin=dp(6); nlp.bottomMargin=dp(16);
            wrap.addView(nl,nlp); content.addView(wrap,mww());
            card.setOnClickListener(v->{ current.cardTheme=idx; for(int k=0;k<profiles.size();k++) if(profiles.get(k).id.equals(current.id)){profiles.set(k,current); break;} saveProfiles(); btnPress(card,()->showPreview()); });
        }
        sv.addView(content,mww()); pushScreen(sv);
    }

    private View buildMiniThemeCard(int idx,boolean sel,Profile p){
        int bg=TC[idx][0],a1=TC[idx][1],a2=TC[idx][2]; boolean lt=idx==1;
        CardView card=new CardView(this); card.setCardBackgroundColor(bg); card.setRadius(dp(18)); card.setCardElevation(sel?dp(18):dp(6));
        FrameLayout inner=new FrameLayout(this); inner.setBackground(cGrad(idx));
        View blb=new View(this); blb.setBackground(blob(a1,18)); inner.addView(blb,new FrameLayout.LayoutParams(dp(110),dp(110),Gravity.END|Gravity.TOP));
        LinearLayout row=hll(Gravity.CENTER_VERTICAL); row.setPadding(dp(18),dp(18),dp(18),dp(18));
        FrameLayout av=new FrameLayout(this); av.setBackground(avRing(a1,a2)); av.setClipToOutline(true);
        fillAvatar(av,dp(46),a1,lt,p.bitmap(),p.name);
        LinearLayout.LayoutParams avlp=new LinearLayout.LayoutParams(dp(46),dp(46)); avlp.rightMargin=dp(14); row.addView(av,avlp);
        LinearLayout info=vll(0,0,0,0);
        TextView nt=mkTv(p.name.isEmpty()?"Your Name":p.name,15,lt?0xFF1A1A2E:Color.WHITE,true); nt.setMaxLines(2); nt.setEllipsize(TextUtils.TruncateAt.END); info.addView(nt);
        TextView rt=mkTv(p.role.isEmpty()?"Developer":p.role,10,a1,false); rt.setAlpha(0.85f); info.addView(rt,mg(0,dp(2),0,dp(5)));
        if(!p.skills.isEmpty()) info.addView(colorTagsRow(p.skills,2));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1f)); inner.addView(row,mpFP());
        if(sel){ View bord=new View(this); GradientDrawable bd=new GradientDrawable(); bd.setShape(GradientDrawable.RECTANGLE); bd.setCornerRadius(dp(18)); bd.setColor(Color.TRANSPARENT); bd.setStroke(dp(2),0xFF7C6FCD); bord.setBackground(bd); inner.addView(bord,mpFP()); }
        card.addView(inner,mpFP()); return card;
    }

    /* ═══════════════════════════════════════════════════════════════════
       ALL PROFILES (accessed from My Card tab)
    ═══════════════════════════════════════════════════════════════════ */
    private void showAllProfiles(){
        ScrollView sv=new ScrollView(this); sv.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout content=vll(dp(20),dp(56),dp(20),dp(40));
        TextView bk=mkTv("← Back",13,0xFF7C6FCD,false); bk.setPadding(0,0,0,dp(12)); bk.setOnClickListener(v->showPreview()); content.addView(bk);
        TextView hdr=mkTv("My Profiles",22,Color.WHITE,true); LinearLayout.LayoutParams hlp=fullW(-2); hlp.bottomMargin=dp(4); content.addView(hdr,hlp);
        content.addView(mkTv(profiles.size()+" saved",12,0xFF555570,false),mg(0,0,0,dp(20)));
        for(Profile p:new ArrayList<>(profiles)) content.addView(makeProfileRow(p,current!=null&&current.id.equals(p.id),sv));
        TextView addBtn=mkPBtn("+ Create New Profile"); LinearLayout.LayoutParams alp=fullW(dp(52)); alp.topMargin=dp(12); content.addView(addBtn,alp);
        addBtn.setOnClickListener(v->{ editing=new Profile(); step=0; switchTab(TAB_PROFILE); showForm(); });
        sv.addView(content,mww()); pushScreen(sv);
    }

    private View makeProfileRow(Profile p,boolean active,View parent){
        int bg=TC[p.cardTheme][0],a1=TC[p.cardTheme][1],a2=TC[p.cardTheme][2]; boolean lt=p.cardTheme==1;
        CardView card=new CardView(this); card.setCardBackgroundColor(bg); card.setRadius(dp(18)); card.setCardElevation(active?dp(14):dp(5));
        FrameLayout inner=new FrameLayout(this); inner.setBackground(cGrad(p.cardTheme));
        View blb=new View(this); blb.setBackground(blob(a1,14)); inner.addView(blb,new FrameLayout.LayoutParams(dp(80),dp(80),Gravity.END|Gravity.TOP));
        LinearLayout row=hll(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(14),dp(14),dp(14));
        FrameLayout av=new FrameLayout(this); av.setBackground(avRing(a1,a2)); av.setClipToOutline(true);
        Bitmap bmp=p.bitmap();
        if(bmp!=null){ ImageView iv=new ImageView(this); iv.setImageBitmap(circle(bmp)); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); av.addView(iv,new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.CENTER)); }
        else{ TextView ini=mkTv(makeInitials(p.name),14,a1,true); ini.setGravity(Gravity.CENTER); av.addView(ini,new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.CENTER)); }
        LinearLayout.LayoutParams avLP=new LinearLayout.LayoutParams(dp(46),dp(46)); avLP.rightMargin=dp(12); row.addView(av,avLP);
        LinearLayout info=vll(0,0,0,0);
        TextView nameTv=mkTv(p.name.isEmpty()?"Unnamed":p.name,13,lt?0xFF1A1A2E:Color.WHITE,true); nameTv.setMaxLines(2); nameTv.setEllipsize(TextUtils.TruncateAt.END); info.addView(nameTv);
        info.addView(mkTv(p.role.isEmpty()?TN[p.cardTheme]:p.role,10,a1,false),mg(0,dp(2),0,dp(4)));
        if(!p.skills.isEmpty()) info.addView(colorTagsRow(p.skills,2));
        int comp=p.completeness(); TextView compPill=mkTv(comp+"%",8,comp>=80?0xFF00C896:comp>=50?0xFFFFD700:0xFFFF6B35,true);
        compPill.setPadding(dp(5),dp(2),dp(5),dp(2)); compPill.setBackground(tagBg(comp>=80?0xFF00C896:comp>=50?0xFFFFD700:0xFFFF6B35));
        info.addView(compPill,mg(0,dp(2),0,0)); row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout btns=vll(0,0,0,0); btns.setGravity(Gravity.CENTER);
        if(active){ TextView ap=mkTv("✓",9,0xFF00C896,true); ap.setPadding(dp(7),dp(3),dp(7),dp(3)); ap.setBackground(tagBg(0xFF00C896)); btns.addView(ap,mg(0,0,0,dp(5))); }
        rowActionBtn(btns,"Use",a1,v->{ setActive(p); btnPress(v,()->showPreview()); });
        rowActionBtn(btns,"Edit",a2,v->{ editing=cloneOf(p); setActive(p); step=0; switchTab(TAB_PROFILE); showForm(); });
        rowActionBtn(btns,"Del",0xFFFF4444,v->{ deleteProfile(p); if(profiles.isEmpty()) switchTab(TAB_HOME); else showAllProfiles(); });
        row.addView(btns,new LinearLayout.LayoutParams(-2,-2));
        inner.addView(row,mpFP());
        if(active){ View bord=new View(this); GradientDrawable bd=new GradientDrawable(); bd.setShape(GradientDrawable.RECTANGLE); bd.setCornerRadius(dp(18)); bd.setColor(Color.TRANSPARENT); bd.setStroke(dp(2),0xFF7C6FCD); bord.setBackground(bd); inner.addView(bord,mpFP()); }
        card.addView(inner,mpFP());
        LinearLayout.LayoutParams wLP=fullW(-2); wLP.bottomMargin=dp(10);
        LinearLayout wrap=vll(0,0,0,0); wrap.addView(card,new LinearLayout.LayoutParams(-1,-2)); wrap.setLayoutParams(wLP); return wrap;
    }

    private void rowActionBtn(LinearLayout p,String label,int color,View.OnClickListener cl){
        TextView btn=mkTv(label,10,color,true); btn.setPadding(dp(9),dp(4),dp(9),dp(4)); btn.setBackground(tagBg(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.bottomMargin=dp(4); p.addView(btn,lp); btn.setOnClickListener(cl);
    }

    /* ═══════════════════════════════════════════════════════════════════
       CARD VISUALS
    ═══════════════════════════════════════════════════════════════════ */
    private View buildFinalCard(Profile p){
        int idx=p.cardTheme,bg=TC[idx][0],a1=TC[idx][1],a2=TC[idx][2]; boolean lt=idx==1;
        CardView card=new CardView(this); card.setCardBackgroundColor(bg); card.setRadius(dp(24)); card.setCardElevation(dp(28));
        FrameLayout inner=new FrameLayout(this); inner.setBackground(cGrad(idx));
        View bl1=new View(this); bl1.setBackground(blob(a1,13)); inner.addView(bl1,new FrameLayout.LayoutParams(dp(200),dp(200),Gravity.END|Gravity.BOTTOM));
        View bl2=new View(this); bl2.setBackground(blob(a2,9)); inner.addView(bl2,new FrameLayout.LayoutParams(dp(80),dp(80),Gravity.START|Gravity.TOP));
        LinearLayout main=vll(dp(18),dp(18),dp(18),dp(12));
        LinearLayout top=hll(Gravity.CENTER_VERTICAL);
        FrameLayout av=new FrameLayout(this); av.setBackground(avRing(a1,a2)); av.setClipToOutline(true);
        fillAvatar(av,dp(56),a1,lt,p.bitmap(),p.name);
        LinearLayout.LayoutParams avlp=new LinearLayout.LayoutParams(dp(56),dp(56)); avlp.rightMargin=dp(12); top.addView(av,avlp);
        LinearLayout nc=vll(0,0,0,0);
        String dn=p.name.isEmpty()?"Your Name":p.name;
        int ns=dn.length()>20?14:dn.length()>14?16:18;
        TextView nameTv=mkTv(dn,ns,lt?0xFF1A1A2E:Color.WHITE,true); nameTv.setMaxLines(2); nameTv.setEllipsize(TextUtils.TruncateAt.END); nc.addView(nameTv);
        nc.addView(mkTv(p.role.isEmpty()?"Developer":p.role,11,a1,false),mg(0,dp(2),0,dp(5)));
        if(!p.skills.isEmpty()) nc.addView(colorTagsRow(p.skills,3));
        top.addView(nc,new LinearLayout.LayoutParams(0,-2,1f)); main.addView(top,mg(0,0,0,dp(10)));
        View div=new View(this); div.setBackgroundColor(Color.argb(35,Color.red(a1),Color.green(a1),Color.blue(a1))); main.addView(div,new LinearLayout.LayoutParams(-1,1));
        LinearLayout bot=hll(Gravity.CENTER_VERTICAL); bot.setPadding(0,dp(8),0,0);
        LinearLayout lc=vll(0,0,0,0);
        if(!p.college.isEmpty()) metaRow(lc,"🎓",p.college,lt); else if(!p.school.isEmpty()) metaRow(lc,"🏫",p.school,lt);
        if(!p.email.isEmpty()) metaRow(lc,"✉",p.email,lt); if(!p.phone.isEmpty()) metaRow(lc,"📞",p.phone,lt);
        if(!p.github.isEmpty()) metaRow(lc,"💻",p.github,lt); if(!p.linkedin.isEmpty()) metaRow(lc,"🔗",p.linkedin,lt);
        bot.addView(lc,new LinearLayout.LayoutParams(0,-2,1f));
        int qrPx=dp(68); Bitmap qrBmp=buildRealQR(qrContent(p),qrPx);
        if(qrBmp!=null){ LinearLayout qrCol=vll(0,0,0,0); qrCol.setGravity(Gravity.CENTER);
            FrameLayout qrBox=new FrameLayout(this); qrBox.setPadding(dp(4),dp(4),dp(4),dp(4));
            GradientDrawable qrBg=new GradientDrawable(); qrBg.setShape(GradientDrawable.RECTANGLE); qrBg.setCornerRadius(dp(8)); qrBg.setColor(Color.WHITE); qrBg.setStroke(dp(2),Color.argb(50,Color.red(a1),Color.green(a1),Color.blue(a1))); qrBox.setBackground(qrBg);
            ImageView qi=new ImageView(this); qi.setImageBitmap(qrBmp); qi.setScaleType(ImageView.ScaleType.FIT_XY); qrBox.addView(qi,new FrameLayout.LayoutParams(qrPx,qrPx,Gravity.CENTER));
            qrCol.addView(qrBox,new LinearLayout.LayoutParams(qrPx+dp(8),qrPx+dp(8)));
            TextView ql=mkTv("TAP TO FLIP\nFOR QR",6,a1,true); ql.setLetterSpacing(0.06f); ql.setGravity(Gravity.CENTER); ql.setMaxLines(2); qrCol.addView(ql,mg(0,dp(2),0,0));
            bot.addView(qrCol,new LinearLayout.LayoutParams(-2,-2)); }
        main.addView(bot,mww());
        TextView wm=mkTv("CardVault  ✦  "+TN[idx].toUpperCase(),7,Color.argb(40,Color.red(a2),Color.green(a2),Color.blue(a2)),true); wm.setLetterSpacing(0.10f); wm.setGravity(Gravity.END); main.addView(wm,mg(0,dp(5),0,0));
        inner.addView(main,mpFP()); card.addView(inner,mpFP()); return card;
    }

    private View buildCardBack(Profile p){
        int idx=p.cardTheme,bg=TC[idx][0],a1=TC[idx][1],a2=TC[idx][2]; boolean lt=idx==1;
        CardView card=new CardView(this); card.setCardBackgroundColor(bg); card.setRadius(dp(24)); card.setCardElevation(dp(28));
        FrameLayout inner=new FrameLayout(this); inner.setBackground(cGrad(idx));
        LinearLayout main=vll(dp(20),dp(16),dp(20),dp(16)); main.setGravity(Gravity.CENTER);
        int qrPx=dp(160); Bitmap qrBmp=buildRealQR(qrContent(p),qrPx);
        if(qrBmp!=null){ FrameLayout qrBox=new FrameLayout(this); qrBox.setPadding(dp(10),dp(10),dp(10),dp(10));
            GradientDrawable qrBg=new GradientDrawable(); qrBg.setShape(GradientDrawable.RECTANGLE); qrBg.setCornerRadius(dp(14)); qrBg.setColor(Color.WHITE); qrBg.setStroke(dp(3),Color.argb(60,Color.red(a1),Color.green(a1),Color.blue(a1))); qrBox.setBackground(qrBg);
            ImageView qi=new ImageView(this); qi.setImageBitmap(qrBmp); qi.setScaleType(ImageView.ScaleType.FIT_XY); qrBox.addView(qi,new FrameLayout.LayoutParams(qrPx,qrPx,Gravity.CENTER));
            main.addView(qrBox,new LinearLayout.LayoutParams(qrPx+dp(20),qrPx+dp(20))); main.addView(spacer(dp(8))); }
        TextView scanLbl=mkTv("Scan with camera to connect",12,a1,true); scanLbl.setGravity(Gravity.CENTER); main.addView(scanLbl,mg(0,0,0,dp(6)));
        TextView nameLbl=mkTv(p.name,16,lt?0xFF1A1A2E:Color.WHITE,true); nameLbl.setGravity(Gravity.CENTER); nameLbl.setMaxLines(2); nameLbl.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp2=new LinearLayout.LayoutParams(-1,-2); nlp2.gravity=Gravity.CENTER_HORIZONTAL; main.addView(nameLbl,nlp2);
        if(!p.email.isEmpty()){ TextView el=mkTv(p.email,11,a2,false); el.setGravity(Gravity.CENTER); main.addView(el,mg(0,dp(3),0,0)); }
        if(!p.phone.isEmpty()){ TextView pl=mkTv(p.phone,11,a2,false); pl.setGravity(Gravity.CENTER); main.addView(pl,mg(0,dp(2),0,0)); }
        main.addView(spacer(dp(6))); if(!p.skills.isEmpty()) main.addView(colorTagsRow(p.skills,4));
        main.addView(spacer(dp(8)));
        TextView hint=mkTv("📲  Need CardVault app to open",9,Color.argb(160,Color.red(a1),Color.green(a1),Color.blue(a1)),false);
        hint.setPadding(dp(12),dp(6),dp(12),dp(6)); hint.setGravity(Gravity.CENTER);
        GradientDrawable hintBg=new GradientDrawable(); hintBg.setCornerRadius(dp(20)); hintBg.setColor(Color.argb(30,Color.red(a1),Color.green(a1),Color.blue(a1))); hintBg.setStroke(1,Color.argb(50,Color.red(a1),Color.green(a1),Color.blue(a1))); hint.setBackground(hintBg);
        LinearLayout.LayoutParams hlp2=new LinearLayout.LayoutParams(-2,-2); hlp2.gravity=Gravity.CENTER_HORIZONTAL; main.addView(hint,hlp2);
        inner.addView(main,mpFP()); card.addView(inner,mpFP()); return card;
    }

    /* ═══════════════════════════════════════════════════════════════════
       SHARE / SAVE / NFC
    ═══════════════════════════════════════════════════════════════════ */
    private void shareCard(View cardView){
        cardView.post(()->{
            Bitmap bmp=captureView(cardView); if(bmp==null){showToast("Could not capture card"); return;}
            try{ File cache=new File(getCacheDir(),"cardvault"); cache.mkdirs(); File f=new File(cache,"share_card.png");
                try(FileOutputStream fos=new FileOutputStream(f)){bmp.compress(Bitmap.CompressFormat.PNG,100,fos);}
                Uri uri=FileProvider.getUriForFile(this,getPackageName()+".provider",f);
                Intent si=new Intent(Intent.ACTION_SEND); si.setType("image/png"); si.putExtra(Intent.EXTRA_STREAM,uri);
                si.putExtra(Intent.EXTRA_TEXT,"Check out my CardVault card!\n"+(current.email.isEmpty()?"":"Email: "+current.email+"\n")+(current.phone.isEmpty()?"":"Phone: "+current.phone));
                si.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(si,"Share my card via..."));
            }catch(Exception e){showToast("Share failed: "+e.getMessage());}
        });
    }

    private void copyContactToClipboard(){
        StringBuilder sb=new StringBuilder(); sb.append(current.name);
        if(!current.role.isEmpty()) sb.append(" | ").append(current.role);
        if(!current.email.isEmpty()) sb.append("\n✉ ").append(current.email);
        if(!current.phone.isEmpty()) sb.append("\n📞 ").append(current.phone);
        if(!current.github.isEmpty()) sb.append("\n💻 ").append(current.github);
        if(!current.linkedin.isEmpty()) sb.append("\n🔗 ").append(current.linkedin);
        if(!current.skills.isEmpty()) sb.append("\n⚡ Skills: ").append(current.skills);
        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Contact Card",sb.toString()));
        showToast("✓ Contact info copied!");
    }

    private void writeNfcTag(Tag tag,String content){
        try{ Ndef ndef=Ndef.get(tag); if(ndef==null){showToast("Tag doesn't support NDEF"); return;}
            android.nfc.NdefMessage msg=new android.nfc.NdefMessage(android.nfc.NdefRecord.createTextRecord("en",content));
            ndef.connect(); ndef.writeNdefMessage(msg); ndef.close(); showToast("✓ Written to NFC tag!");
        }catch(Exception e){showToast("NFC write failed: "+e.getMessage());} }

    private void saveImg(View v){
        v.post(()->{
            Bitmap b=captureView(v); if(b==null){showToast("Capture failed"); return;}
            try{ String fn="CardVault_"+current.name.replace(" ","_")+".png";
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
                    ContentValues cv=new ContentValues(); cv.put(MediaStore.Images.Media.DISPLAY_NAME,fn); cv.put(MediaStore.Images.Media.MIME_TYPE,"image/png"); cv.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/CardVault");
                    Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
                    if(uri!=null){try(OutputStream os=getContentResolver().openOutputStream(uri)){b.compress(Bitmap.CompressFormat.PNG,100,os);} showToast("✓ Saved to Pictures/CardVault!");}
                } else {
                    if(ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){ ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},101); showToast("Grant storage permission then try again"); return; }
                    File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"CardVault"); dir.mkdirs(); File f2=new File(dir,fn);
                    try(FileOutputStream fo=new FileOutputStream(f2)){b.compress(Bitmap.CompressFormat.PNG,100,fo);}
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.fromFile(f2))); showToast("✓ Saved!");
                }
            }catch(Exception e){showToast("Save failed: "+e.getMessage());}
        });
    }

    private void launchConfetti(){
        int[]colors={0xFF7C6FCD,0xFF00F5FF,0xFFFF61A6,0xFFFFD700,0xFF00C896,0xFFFF6B35};
        Random rnd=new Random(); int W=rootFrame.getWidth(),H=rootFrame.getHeight();
        for(int i=0;i<60;i++){ View dot=new View(this); int sz=dp(6+rnd.nextInt(8));
            GradientDrawable d=new GradientDrawable(); d.setShape(rnd.nextBoolean()?GradientDrawable.RECTANGLE:GradientDrawable.OVAL); d.setColor(colors[rnd.nextInt(colors.length)]);
            if(d.getShape()==GradientDrawable.RECTANGLE) d.setCornerRadius(dp(2)); dot.setBackground(d);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(sz,sz); lp.leftMargin=rnd.nextInt(W); lp.topMargin=-sz; rootFrame.addView(dot,lp);
            dot.animate().translationX((rnd.nextFloat()-0.5f)*dp(120)).translationY(H+dp(50)).rotation(rnd.nextInt(720)-360).alpha(0f)
                    .setDuration(1200+rnd.nextInt(800)).setStartDelay(rnd.nextInt(600)).setInterpolator(new AccelerateInterpolator(0.8f))
                    .withEndAction(()->rootFrame.removeView(dot)).start(); }
    }

    private Bitmap captureView(View v){ try{ int w=v.getWidth(),h=v.getHeight(); if(w==0||h==0) return null; Bitmap b=Bitmap.createBitmap(w*2,h*2,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b); c.scale(2f,2f); v.draw(c); return b; }catch(Exception e){return null;} }

    /* ═══════════════════════════════════════════════════════════════════
       UI HELPERS
    ═══════════════════════════════════════════════════════════════════ */
    private void fillAvatar(FrameLayout av,int size,int accent,boolean light,Bitmap bmp,String name){
        av.removeAllViews();
        if(bmp!=null){ ImageView iv=new ImageView(this); iv.setImageBitmap(circle(bmp)); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); av.addView(iv,new FrameLayout.LayoutParams(size,size,Gravity.CENTER)); }
        else{ String ini=makeInitials(name); int sp=Math.max(size/dp(4),12); TextView t=mkTv(ini,sp,accent,true); t.setGravity(Gravity.CENTER); av.addView(t,new FrameLayout.LayoutParams(size,size,Gravity.CENTER)); }
    }

    private void iCard(LinearLayout p,String title,String val,String deepLink){
        if(val==null||val.trim().isEmpty()) return;
        LinearLayout c=vll(dp(16),dp(12),dp(16),dp(14)); c.setBackground(iBg());
        LinearLayout.LayoutParams lp=fullW(-2); lp.bottomMargin=dp(10);
        c.addView(mkTv(title,10,0xFF7C6FCD,true),mg(0,0,0,dp(4)));
        TextView tv=mkTv(val,13,0xFFCCCCDD,false); tv.setLineSpacing(dp(2),1f); c.addView(tv,mww());
        if(deepLink!=null&&!deepLink.endsWith(":")){ c.setOnClickListener(v->{ try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(deepLink)));}catch(Exception ignored){ ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("",val)); showToast("Copied!"); } }); c.addView(mkTv("tap to open  →",8,0xFF3A3A55,false),mg(0,dp(2),0,0)); }
        p.addView(c,lp);
    }

    private LinearLayout colorTagsRow(String csv,int max){
        LinearLayout r=hll(Gravity.CENTER_VERTICAL); String[]parts=csv.split(",");
        for(int i=0;i<Math.min(max,parts.length);i++){ String t=parts[i].trim(); if(t.isEmpty()) continue; if(t.length()>9) t=t.substring(0,8)+"…";
            int c=SKILL_COLORS[i%SKILL_COLORS.length]; TextView tag=mkTv(t,8,c,true); tag.setPadding(dp(6),dp(2),dp(6),dp(2)); tag.setBackground(tagBg(c));
            LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-2,-2); if(i>0) tlp.leftMargin=dp(5); r.addView(tag,tlp); } return r;
    }

    private void metaRow(LinearLayout p,String icon,String text,boolean light){
        LinearLayout r=hll(Gravity.CENTER_VERTICAL); LinearLayout.LayoutParams rp=fullW(-2); rp.bottomMargin=dp(3);
        r.addView(mkTv(icon,10,Color.WHITE,false),new LinearLayout.LayoutParams(dp(18),-2));
        TextView tv=mkTv(text,10,light?0xFF555575:0xFFAAAAAA,false); tv.setMaxLines(1); r.addView(tv); p.addView(r,rp);
    }

    private String eduText(Profile p){ String s=""; if(!p.college.isEmpty()) s+=p.college; if(!p.school.isEmpty()){if(!s.isEmpty()) s+="\n"; s+=p.school;} return s; }
    private int csvCount(String s){ if(s==null||s.trim().isEmpty()) return 0; return s.split(",").length; }
    private int navBarExtra(){ if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){ android.view.WindowInsets wi=getWindow().getDecorView().getRootWindowInsets(); if(wi!=null) return wi.getSystemWindowInsetBottom(); } return 0; }

    // Animations
    private void aIn(View v){ v.setAlpha(0f); v.setTranslationX(40f); v.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start(); }
    private void aOut(View v,Runnable r){ v.animate().alpha(0f).translationX(-40f).setDuration(200).setInterpolator(new AccelerateInterpolator()).withEndAction(r).start(); }
    private void btnPress(View v,Runnable r){ v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction(r).start()).start(); }
    private void pulse(View v){ ObjectAnimator sx=ObjectAnimator.ofFloat(v,"scaleX",1f,1.07f,1f); ObjectAnimator sy=ObjectAnimator.ofFloat(v,"scaleY",1f,1.07f,1f); for(ObjectAnimator a:new ObjectAnimator[]{sx,sy}){a.setDuration(2200); a.setRepeatCount(ValueAnimator.INFINITE); a.setInterpolator(new AccelerateDecelerateInterpolator());} AnimatorSet as=new AnimatorSet(); as.playTogether(sx,sy); as.setStartDelay(800); as.start(); }
    private void shakeView(View v){ if(v!=null) ObjectAnimator.ofFloat(v,"translationX",0,-18,18,-14,14,-8,8,0).setDuration(400).start(); }
    private void hideKb(){ View f=getCurrentFocus(); if(f!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(),0); }
    private void showToast(String m){ Toast.makeText(this,m,Toast.LENGTH_SHORT).show(); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density); }

    // Layout params
    private FrameLayout.LayoutParams mpFP(){ return new FrameLayout.LayoutParams(-1,-1); }
    private LinearLayout.LayoutParams mww(){ return new LinearLayout.LayoutParams(-1,-2); }
    private LinearLayout.LayoutParams fullW(int h){ return new LinearLayout.LayoutParams(-1,h); }
    private LinearLayout.LayoutParams mg(int l,int t,int r,int b){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2); p.setMargins(l,t,r,b); return p; }
    private LinearLayout.LayoutParams cwrap(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2); p.gravity=Gravity.CENTER_HORIZONTAL; return p; }

    // View factories
    private LinearLayout vll(int pl,int pt,int pr,int pb){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(pl,pt,pr,pb); return l; }
    private LinearLayout hll(int g){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(g); return l; }
    private View spacer(int h){ View v=new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(-1,h)); return v; }
    private TextView mkTv(String t,int sp,int color,boolean bold){ TextView v=new TextView(this); v.setText(t); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT); return v; }
    private TextView mkPBtn(String t){ TextView b=mkTv(t,14,Color.WHITE,true); b.setGravity(Gravity.CENTER); GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFF7C6FCD,0xFF4A90D9}); d.setCornerRadius(dp(14)); b.setBackground(d); return b; }
    private TextView mkSBtn(String t){ TextView b=mkTv(t,13,0xFF7C6FCD,false); b.setGravity(Gravity.CENTER); GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadius(dp(14)); d.setColor(0xFF12121E); d.setStroke(dp(1),0xFF7C6FCD); b.setBackground(d); return b; }

    // Drawables
    private Drawable iBg(){ GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadius(dp(14)); d.setColor(0xFF12121E); d.setStroke(1,0xFF1E1E30); return d; }
    private Drawable iBgFocus(){ GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadius(dp(14)); d.setColor(0xFF14142A); d.setStroke(dp(1),0xFF7C6FCD); return d; }
    private Drawable iBgError(){ GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadius(dp(14)); d.setColor(0xFF1A0A0A); d.setStroke(dp(1),0xFFFF4444); return d; }
    private Drawable glowBg(){ GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF7C6FCD,0xFF4A90D9,0xFF00F5FF}); d.setShape(GradientDrawable.OVAL); return d; }
    private Drawable cGrad(int i){ int[][]g={{0xFF0E0E1A,0xFF141428},{0xFFFAFAFA,0xFFEEEEF8},{0xFF111118,0xFF1C1C24},{0xFF0D1B2A,0xFF1A1A38}}; return new GradientDrawable(GradientDrawable.Orientation.TL_BR,g[i]); }
    private Drawable blob(int c,int a){ GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(Color.argb(a,Color.red(c),Color.green(c),Color.blue(c))); return d; }
    private Drawable avRing(int c1,int c2){ GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(55,Color.red(c1),Color.green(c1),Color.blue(c1)),Color.argb(25,Color.red(c2),Color.green(c2),Color.blue(c2))}); d.setShape(GradientDrawable.OVAL); d.setStroke(dp(2),c1); return d; }
    private Drawable circleStroke(int c){ GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(0xFF1E1E2E); d.setStroke(dp(2),c); return d; }
    private Drawable avatarPh(){ GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF1E1E2E,0xFF2A2A40}); d.setShape(GradientDrawable.OVAL); d.setStroke(dp(2),0xFF333350); return d; }
    private Drawable tagBg(int c){ GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadius(dp(6)); d.setColor(Color.argb(28,Color.red(c),Color.green(c),Color.blue(c))); d.setStroke(1,Color.argb(70,Color.red(c),Color.green(c),Color.blue(c))); return d; }

    private Bitmap circle(Bitmap src){ int sz=Math.min(src.getWidth(),src.getHeight()); Bitmap out=Bitmap.createBitmap(sz,sz,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(out); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); c.drawOval(new RectF(0,0,sz,sz),p); p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN)); int x=(src.getWidth()-sz)/2,y=(src.getHeight()-sz)/2; c.drawBitmap(src,new Rect(x,y,x+sz,y+sz),new RectF(0,0,sz,sz),p); return out; }

    interface StringConsumer{ void accept(String s); }
    static class SimpleTextWatcher implements TextWatcher {
        private final StringConsumer fn;
        SimpleTextWatcher(StringConsumer fn){ this.fn=fn; }
        public void beforeTextChanged(CharSequence s,int a,int b,int c){}
        public void onTextChanged(CharSequence s,int a,int b,int c){ fn.accept(s.toString()); }
        public void afterTextChanged(Editable s){}
    }

    private class ParticleView extends View {
        private final int N=50;
        private final float[]px=new float[N],py=new float[N],pv=new float[N],pa=new float[N],ps=new float[N];
        private final int[]pc=new int[N];
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[]COLS={0xFF7C6FCD,0xFF4A90D9,0xFF00F5FF,0xFFFF61A6};
        ParticleView(Context ctx){ super(ctx); Random r=new Random(); for(int i=0;i<N;i++){px[i]=r.nextFloat(); py[i]=r.nextFloat(); pv[i]=0.0002f+r.nextFloat()*0.0003f; pa[i]=r.nextFloat()*6.28f; ps[i]=1.5f+r.nextFloat()*2.5f; pc[i]=COLS[r.nextInt(COLS.length)];} }
        @Override protected void onDraw(Canvas canvas){ super.onDraw(canvas); int w=getWidth(),h=getHeight(); for(int i=0;i<N;i++){px[i]+=(float)Math.cos(pa[i])*pv[i]; py[i]+=(float)Math.sin(pa[i])*pv[i]; pa[i]+=0.008f; if(px[i]<0)px[i]=1f; if(px[i]>1)px[i]=0f; if(py[i]<0)py[i]=1f; if(py[i]>1)py[i]=0f; paint.setColor(Color.argb(65,Color.red(pc[i]),Color.green(pc[i]),Color.blue(pc[i]))); canvas.drawCircle(px[i]*w,py[i]*h,ps[i],paint);} postInvalidateDelayed(16); }
    }
}
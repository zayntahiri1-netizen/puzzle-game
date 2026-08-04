package com.deciphertahiro.app;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.getcapacitor.BridgeActivity;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;

import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decipher Tahiro — MainActivity
 *
 * يدمج:
 *  1) UMP (موافقة GDPR/US) — تُفحص عند التشغيل، تظهر لأوروبا فقط.
 *  2) Google Mobile Ads (AdMob) — إعلانات مقابل مكافأة (Rewarded) + Interstitial.
 *
 * مبدأ الامتثال الصارم: لا تُهيّأ الإعلانات إطلاقاً قبل اكتمال فحص الموافقة.
 * حماية اللعب: الإعلانات البينية لا تُعرض أثناء التحديات (يتحكم بها كود الويب).
 */
public class MainActivity extends BridgeActivity {

    // معرّفات الوحدات الإعلانية (من AdMob)
    private static final String REWARDED_500       = "ca-app-pub-1725525147318224/8186294544";
    private static final String REWARDED_HEART      = "ca-app-pub-1725525147318224/6290337489";
    private static final String REWARDED_500_HEART  = "ca-app-pub-1725525147318224/7778110751";
    private static final String REWARDED_1000       = "ca-app-pub-1725525147318224/1934757136";
    private static final String REWARDED_2000       = "ca-app-pub-1725525147318224/8533357446";
    private static final String INTERSTITIAL_ID     = "ca-app-pub-1725525147318224/9515266622";
    private static final String APP_OPEN_ID         = "ca-app-pub-1725525147318224/4776542896";
    private static final String BANNER_ID           = "ca-app-pub-1725525147318224/8202184955";
    // ملاحظة: الإعلان المدمج (Native) لم يُدمج — يتطلب تخطيطاً مخصّصاً معقّداً
    // داخل WebView لا يستحق المخاطرة. المعرّف محفوظ هنا للمستقبل فقط:
    // Native: ca-app-pub-1725525147318224/8691737172

    private ConsentInformation consentInformation;
    private volatile boolean privacyOptionsAvailable = false;
    private final AtomicBoolean adsInitialized = new AtomicBoolean(false);

    private InterstitialAd interstitialAd;

    // ── App Open ──
    private AppOpenAd appOpenAd;
    private boolean appOpenLoading = false;
    private boolean appOpenShowing = false;
    private long appOpenLastShown = 0L;
    private boolean firstLaunch = true; // تخطَّ أول تشغيل (cold start)
    private static final long APP_OPEN_MIN_GAP_MS = 4L * 60L * 60L * 1000L; // 4 ساعات

    // ── Banner ──
    private AdView bannerView;
    private FrameLayout bannerContainer;
    private boolean bannerVisible = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            WebView webView = getBridge().getWebView();
            if (webView != null) {
                webView.addJavascriptInterface(new ConsentBridge(), "AndroidConsent");
                webView.addJavascriptInterface(new AdsBridge(), "AndroidAds");
            }
        } catch (Exception ignored) {}

        // App Open يُعرض عند العودة للواجهة — نتتبّع ذلك عبر onResume (بلا اعتماديات إضافية)

        initConsent();
    }

    @Override
    public void onResume() {
        super.onResume();
        // أول onResume = أول تشغيل → نتخطّاه. اللاحقة = عودة للتطبيق.
        if (firstLaunch) { firstLaunch = false; return; }
        showAppOpenIfReady();
    }

    /* ═══════════════════ UMP CONSENT ═══════════════════ */

    private void initConsent() {
        try {
            ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                    .setTagForUnderAgeOfConsent(false)
                    .build();
            consentInformation = UserMessagingPlatform.getConsentInformation(this);
            consentInformation.requestConsentInfoUpdate(
                    this, params,
                    () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            this,
                            (FormError loadAndShowError) -> {
                                updatePrivacyButtonState();
                                // ✅ بعد قرار المستخدم: هيّئ الإعلانات إن كان مسموحاً
                                maybeInitAds();
                            }
                    ),
                    (FormError requestError) -> {
                        updatePrivacyButtonState();
                        // حتى لو فشل تحديث الموافقة، الإعلانات غير الشخصية مسموحة افتراضياً
                        maybeInitAds();
                    }
            );
        } catch (Exception ignored) {
            // أي خطأ في UMP لا يُعطّل التطبيق؛ نحاول تهيئة الإعلانات بحذر
            maybeInitAds();
        }
    }

    private void updatePrivacyButtonState() {
        try {
            privacyOptionsAvailable = (consentInformation != null
                    && consentInformation.getPrivacyOptionsRequirementStatus()
                       == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED);
            final boolean show = privacyOptionsAvailable;
            runOnUiThread(() -> evalJs(
                    "window.__umpPrivacyAvailable=" + show + ";"
                  + "if(window.onUmpPrivacyState)window.onUmpPrivacyState(" + show + ");"));
        } catch (Exception ignored) {}
    }

    private void presentPrivacyOptions() {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(this, (FormError error) -> {});
        } catch (Exception ignored) {}
    }

    /* ═══════════════════ ADMOB ═══════════════════ */

    /** يهيّئ Mobile Ads مرة واحدة فقط، فقط إذا سمح UMP بطلب الإعلانات. */
    private void maybeInitAds() {
        try {
            if (consentInformation != null && !consentInformation.canRequestAds()) {
                // المستخدم لم يوافق وطلب الإعلانات غير مسموح → لا تُهيّأ
                return;
            }
        } catch (Exception ignored) {}
        if (adsInitialized.getAndSet(true)) return;
        try {
            // إعلان صريح: التطبيق ليس موجّهاً للأطفال (متّسق مع تصنيف Play: لعبة/ألغاز)
            MobileAds.setRequestConfiguration(
                new RequestConfiguration.Builder()
                    .setTagForChildDirectedTreatment(
                        RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                    .setTagForUnderAgeOfConsent(
                        RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                    .build());
            MobileAds.initialize(this, status -> {
                // جاهز — حمّل أول إعلان بيني للاستخدام لاحقاً (شاشة النتائج فقط)
                runOnUiThread(() -> {
                    loadInterstitial();
                    loadAppOpen(); // حمّل App Open مسبقاً (لا يُعرض الآن)
                });
                evalJs("if(window.onAdsReady)window.onAdsReady();");
            });
        } catch (Exception ignored) {}
    }

    private String rewardedUnitFor(String key) {
        if (key == null) return REWARDED_500;
        switch (key) {
            case "heart":     return REWARDED_HEART;
            case "g500heart": return REWARDED_500_HEART;
            case "g1000":     return REWARDED_1000;
            case "g2000":     return REWARDED_2000;
            case "g500":
            default:          return REWARDED_500;
        }
    }

    /** هل هذا المفتاح من نوع Rewarded Interstitial (بيني مقابل مكافأة)؟ */
    private boolean isRewardedInterstitial(String key) {
        return "g500".equals(key) || "heart".equals(key);
    }

    /** يحمّل ويعرض إعلان مكافأة بالنوع الصحيح (Rewarded أو Rewarded Interstitial). */
    private void showRewarded(final String rewardKey) {
        if (!adsInitialized.get()) {
            evalJs("if(window.onRewardFailed)window.onRewardFailed('" + js(rewardKey) + "','not_ready');");
            return;
        }
        final String unitId = rewardedUnitFor(rewardKey);

        if (isRewardedInterstitial(rewardKey)) {
            // ── النوع الأول: Rewarded Interstitial ──
            RewardedInterstitialAd.load(this, unitId, new AdRequest.Builder().build(),
                    new RewardedInterstitialAdLoadCallback() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                            evalJs("if(window.onRewardFailed)window.onRewardFailed('" + js(rewardKey) + "','no_fill');");
                        }
                        @Override
                        public void onAdLoaded(@NonNull RewardedInterstitialAd ad) {
                            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                                @Override public void onAdDismissedFullScreenContent() {}
                                @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                                    evalJs("if(window.onRewardFailed)window.onRewardFailed('" + js(rewardKey) + "','show_failed');");
                                }
                            });
                            ad.show(MainActivity.this, rewardItem ->
                                    evalJs("if(window.onRewardEarned)window.onRewardEarned('" + js(rewardKey) + "');"));
                        }
                    });
            return;
        }

        // ── النوع الثاني: Rewarded (مكافأة عادية) ──
        RewardedAd.load(this, unitId, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                        evalJs("if(window.onRewardFailed)window.onRewardFailed('" + js(rewardKey) + "','no_fill');");
                    }
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override public void onAdDismissedFullScreenContent() {}
                            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                                evalJs("if(window.onRewardFailed)window.onRewardFailed('" + js(rewardKey) + "','show_failed');");
                            }
                        });
                        ad.show(MainActivity.this, new OnUserEarnedRewardListener() {
                            @Override public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                                evalJs("if(window.onRewardEarned)window.onRewardEarned('" + js(rewardKey) + "');");
                            }
                        });
                    }
                });
    }

    private void loadInterstitial() {
        if (!adsInitialized.get()) return;
        try {
            InterstitialAd.load(this, INTERSTITIAL_ID, new AdRequest.Builder().build(),
                    new InterstitialAdLoadCallback() {
                        @Override public void onAdLoaded(@NonNull InterstitialAd ad) { interstitialAd = ad; }
                        @Override public void onAdFailedToLoad(@NonNull LoadAdError e) { interstitialAd = null; }
                    });
        } catch (Exception ignored) {}
    }

    /** يعرض إعلاناً بينياً (يُستدعى فقط من شاشة النتائج، لا أثناء اللعب). */
    private void showInterstitial() {
        if (interstitialAd == null) { loadInterstitial(); return; }
        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { interstitialAd = null; loadInterstitial(); }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) { interstitialAd = null; loadInterstitial(); }
        });
        interstitialAd.show(this);
    }

    /* ═══════════════════ APP OPEN (بحذر شديد) ═══════════════════ */

    private void loadAppOpen() {
        if (!adsInitialized.get() || appOpenLoading || appOpenAd != null) return;
        appOpenLoading = true;
        try {
            AppOpenAd.load(this, APP_OPEN_ID, new AdRequest.Builder().build(),
                    new AppOpenAd.AppOpenAdLoadCallback() {
                        @Override public void onAdLoaded(@NonNull AppOpenAd ad) {
                            appOpenAd = ad; appOpenLoading = false;
                        }
                        @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                            appOpenAd = null; appOpenLoading = false;
                        }
                    });
        } catch (Exception ignored) { appOpenLoading = false; }
    }

    /** يعرض App Open فقط ضمن شروط صارمة لحماية التجربة. */
    private void showAppOpenIfReady() {
        // 1) جاهز ومحمّل
        if (!adsInitialized.get() || appOpenAd == null || appOpenShowing) { loadAppOpen(); return; }
        // 2) ليس أثناء تحدٍّ حيّ (يسأل كود الويب)
        //    نتحقق عبر متغيّر JS currentChallengeId — إن كان موجوداً نتخطّى.
        //    (الفحص يتم في JS أيضاً؛ هنا حارس إضافي بالوقت)
        // 3) احترم الفجوة الزمنية (4 ساعات)
        long now = System.currentTimeMillis();
        if (now - appOpenLastShown < APP_OPEN_MIN_GAP_MS) return;

        // اسأل كود الويب: هل نحن في تحدٍّ؟ إن نعم لا تعرض.
        // نستخدم فحصاً متزامناً بسيطاً عبر علامة يضبطها JS.
        if (webSaysInChallenge) return;

        try {
            appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdShowedFullScreenContent() { appOpenShowing = true; }
                @Override public void onAdDismissedFullScreenContent() {
                    appOpenShowing = false; appOpenAd = null; appOpenLastShown = System.currentTimeMillis();
                    loadAppOpen(); // حضّر التالي
                }
                @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                    appOpenShowing = false; appOpenAd = null; loadAppOpen();
                }
            });
            appOpenAd.show(this);
        } catch (Exception ignored) { appOpenShowing = false; appOpenAd = null; }
    }

    // علامة يضبطها كود الويب لإبلاغ الأصلي بحالة التحدي (حماية إضافية)
    private volatile boolean webSaysInChallenge = false;

    /* ═══════════════════ BANNER (مخفي افتراضياً) ═══════════════════ */

    /** ينشئ ويعرض بانراً سفلياً متكيّفاً مع عرض الشاشة. مخفي افتراضياً. */
    private void showBanner() {
        if (!adsInitialized.get()) return;
        runOnUiThread(() -> {
            try {
                if (bannerView == null) {
                    bannerView = new AdView(this);
                    bannerView.setAdSize(getAdaptiveBannerSize()); // ← يتكيّف مع كل الشاشات
                    bannerView.setAdUnitId(BANNER_ID);

                    bannerContainer = new FrameLayout(this);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.BOTTOM;
                    bannerContainer.addView(bannerView);

                    addContentView(bannerContainer, lp);
                    bannerView.loadAd(new AdRequest.Builder().build());
                }
                bannerContainer.setVisibility(View.VISIBLE);
                bannerVisible = true;
            } catch (Exception ignored) {}
        });
    }

    /** يحسب حجم البانر المتكيّف حسب عرض الشاشة (هاتف صغير → تابلت كبير). */
    private AdSize getAdaptiveBannerSize() {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            float density = dm.density;
            int widthPx = dm.widthPixels;
            // إن أمكن، استخدم عرض نافذة المحتوى الفعلي
            try {
                View content = findViewById(android.R.id.content);
                if (content != null && content.getWidth() > 0) widthPx = content.getWidth();
            } catch (Exception ignored) {}
            int adWidthDp = (int) (widthPx / density);
            if (adWidthDp <= 0) adWidthDp = 320;
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidthDp);
        } catch (Exception e) {
            return AdSize.BANNER; // احتياطي
        }
    }

    private void hideBanner() {
        runOnUiThread(() -> {
            try { if (bannerContainer != null) bannerContainer.setVisibility(View.GONE); } catch (Exception ignored) {}
            bannerVisible = false;
        });
    }

    @Override
    public void onDestroy() {
        try { if (bannerView != null) bannerView.destroy(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    /* ═══════════════════ JS BRIDGES ═══════════════════ */

    private void evalJs(final String code) {
        try {
            WebView webView = getBridge().getWebView();
            if (webView != null) webView.post(() -> webView.evaluateJavascript(code, null));
        } catch (Exception ignored) {}
    }

    private static String js(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'"); }

    /** جسر الموافقة (لزر الخصوصية). */
    private class ConsentBridge {
        @JavascriptInterface public void openPrivacyOptions() { runOnUiThread(MainActivity.this::presentPrivacyOptions); }
        @JavascriptInterface public boolean isPrivacyOptionsAvailable() { return privacyOptionsAvailable; }
    }

    /** جسر الإعلانات (لأزرار اكسب العملات + شاشة النتائج). */
    private class AdsBridge {
        @JavascriptInterface public void showRewarded(String rewardKey) { runOnUiThread(() -> MainActivity.this.showRewarded(rewardKey)); }
        @JavascriptInterface public void showInterstitial() { runOnUiThread(MainActivity.this::showInterstitial); }
        @JavascriptInterface public boolean isReady() { return adsInitialized.get(); }
        // ── Banner ──
        @JavascriptInterface public void showBanner() { MainActivity.this.showBanner(); }
        @JavascriptInterface public void hideBanner() { MainActivity.this.hideBanner(); }
        // ── إبلاغ الأصلي بحالة التحدي (لمنع App Open أثناء اللعب) ──
        @JavascriptInterface public void setInChallenge(boolean v) { webSaysInChallenge = v; }
    }
}

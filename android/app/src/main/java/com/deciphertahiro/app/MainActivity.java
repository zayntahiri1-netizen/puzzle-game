package com.deciphertahiro.app;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

    /* ── حارس مشترك لكل الإعلانات بملء الشاشة ──────────────────────────
       التطبيق يستعمل أربع صيغ بملء الشاشة: App Open و Interstitial و
       Rewarded و Rewarded Interstitial. ولكل صيغة رايتها وحدها، فلا شيء
       يمنع تتابعها. وإعلان فتح التطبيق يُعرض في onResume — و onResume يقع
       أيضاً عند إغلاق إعلان مكافأة أو بيني، فيظهر إعلانان متتاليان بملء
       الشاشة. هذا ما تسمّيه Google «تراكم الإعلانات» وتعاقب عليه.

       هذه الراية مشتركة بين الصيغ الأربع، ومعها فترة تهدئة تمنع عرض أي
       إعلان بملء الشاشة قبل مرور 45 ثانية على إغلاق سابقه. */
    private volatile boolean fullScreenAdShowing = false;
    private volatile long    fullScreenAdLastEnd = 0L;
    private static final long FULLSCREEN_COOLDOWN_MS = 45L * 1000L;

    /** هل يجوز عرض إعلان بملء الشاشة الآن؟ */
    private boolean canShowFullScreenAd() {
        if (fullScreenAdShowing) return false;
        return System.currentTimeMillis() - fullScreenAdLastEnd >= FULLSCREEN_COOLDOWN_MS;
    }
    private void onFullScreenAdShown()     { fullScreenAdShowing = true; }
    private void onFullScreenAdFinished()  {
        fullScreenAdShowing = false;
        fullScreenAdLastEnd = System.currentTimeMillis();
    }

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
                webView.addJavascriptInterface(new LinksBridge(), "AndroidLinks");
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
                                @Override public void onAdShowedFullScreenContent() { onFullScreenAdShown(); }
                                @Override public void onAdDismissedFullScreenContent() { onFullScreenAdFinished(); }
                                @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                                    onFullScreenAdFinished();
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
                            @Override public void onAdShowedFullScreenContent() { onFullScreenAdShown(); }
                            @Override public void onAdDismissedFullScreenContent() { onFullScreenAdFinished(); }
                            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                                onFullScreenAdFinished();
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
        if (!canShowFullScreenAd()) return;   // لا تُكدّس فوق إعلان آخر
        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() { onFullScreenAdShown(); }
            @Override public void onAdDismissedFullScreenContent() {
                onFullScreenAdFinished(); interstitialAd = null; loadInterstitial();
            }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                onFullScreenAdFinished(); interstitialAd = null; loadInterstitial();
            }
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

        // 4) لا تُكدّس فوق إعلان آخر. هذا هو الحارس الحاسم: onResume يقع
        //    أيضاً عند إغلاق إعلان مكافأة أو بيني، فلولاه لظهر إعلانان
        //    متتاليان بملء الشاشة.
        if (!canShowFullScreenAd()) return;

        try {
            appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdShowedFullScreenContent() {
                    appOpenShowing = true; onFullScreenAdShown();
                }
                @Override public void onAdDismissedFullScreenContent() {
                    appOpenShowing = false; appOpenAd = null; appOpenLastShown = System.currentTimeMillis();
                    onFullScreenAdFinished();
                    loadAppOpen(); // حضّر التالي
                }
                @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) {
                    appOpenShowing = false; appOpenAd = null;
                    onFullScreenAdFinished();
                    loadAppOpen();
                }
            });
            appOpenAd.show(this);
        } catch (Exception ignored) {
            appOpenShowing = false; appOpenAd = null; onFullScreenAdFinished();
        }
    }

    // علامة يضبطها كود الويب لإبلاغ الأصلي بحالة التحدي (حماية إضافية)
    private volatile boolean webSaysInChallenge = false;

    /* ═══════════════════ BANNER (مخفي افتراضياً) ═══════════════════ */

    /* ── لماذا كان البانر يغطّي الأزرار ─────────────────────────────────
       البانر عنصر أصلي يُرسم «فوق» WebView لا «تحته». الويب كان يعوّض
       بحشوة ثابتة 64px على #mainApp وحدها، وهذا لا يكفي لثلاثة أسباب:
         ① البانر المتكيّف ليس 60px دائماً: يبلغ 90px وأكثر على الشاشات
            العريضة والتابلت.
         ② النوافذ (position:fixed; inset:0) والإشعارات ولوحة الأرقام لا
            تتأثّر بحشوة #mainApp، فتبقى أزرارها السفلية تحت البانر.
         ③ Android 16 يفرض edge-to-edge، فيمتدّ المحتوى تحت شريط التنقّل،
            وقد يقع البانر نفسه خلفه — وهذا مخالف لسياسة AdMob أيضاً
            (إعلان محجوب جزئياً).

       الحلّ من الجذر: نرفع البانر فوق شريط التنقّل، ثم نُقصّر WebView نفسه
       بمقدار ارتفاع البانر الفعلي المقيس. فلا يبقى في صفحة الويب أي جزء
       تحت البانر — مهما كان العنصر أو النافذة أو حجم الشاشة. */

    /** الارتفاع الفعلي للبانر (مع حشوة شريط التنقّل) بالبكسل. */
    private int bannerHeightPx = 0;

    /** يُقصّر WebView بمقدار البانر الظاهر، أو يعيده كاملاً إن أُخفي. */
    private void applyWebViewBottomInset() {
        try {
            WebView wv = getBridge().getWebView();
            if (wv == null) return;
            int target = (bannerVisible && bannerContainer != null
                          && bannerContainer.getVisibility() == View.VISIBLE) ? bannerHeightPx : 0;
            ViewGroup.LayoutParams p = wv.getLayoutParams();
            if (p instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) p;
                if (mp.bottomMargin != target) {
                    mp.bottomMargin = target;
                    wv.setLayoutParams(mp);
                }
            } else if (wv.getParent() instanceof View) {
                // احتياط: إن لم تقبل الحاوية الهوامش، نحشو الحاوية نفسها
                View parent = (View) wv.getParent();
                if (parent.getPaddingBottom() != target) {
                    parent.setPadding(parent.getPaddingLeft(), parent.getPaddingTop(),
                                      parent.getPaddingRight(), target);
                }
            }
        } catch (Exception ignored) {}
    }

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
                    // لون اللعبة خلف البانر وخلف شريط التنقّل، بدل شفافية تُظهر ما تحتها
                    bannerContainer.setBackgroundColor(0xFF1A0D2E);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.BOTTOM;
                    FrameLayout.LayoutParams adLp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    adLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                    bannerContainer.addView(bannerView, adLp);

                    // ① ارفع البانر فوق شريط التنقّل (edge-to-edge في Android 15/16).
                    //    لا نستهلك الـ insets كي تصل كما هي إلى WebView وبقية العناصر.
                    ViewCompat.setOnApplyWindowInsetsListener(bannerContainer, (v, insets) -> {
                        Insets bars = insets.getInsets(
                                WindowInsetsCompat.Type.systemBars()
                              | WindowInsetsCompat.Type.displayCutout());
                        v.setPadding(bars.left, 0, bars.right, bars.bottom);
                        return insets;
                    });

                    // ② كلما تغيّر الارتفاع الفعلي (تحميل الإعلان، تدوير، تغيّر
                    //    شريط التنقّل)، أعد قياس WebView. القياس لا التقدير.
                    bannerContainer.addOnLayoutChangeListener(
                            (v, l, t, r, b, ol, ot, orr, ob) -> {
                                int h = b - t;
                                if (h != bannerHeightPx) {
                                    bannerHeightPx = h;
                                    v.post(this::applyWebViewBottomInset);
                                }
                            });

                    addContentView(bannerContainer, lp);
                    ViewCompat.requestApplyInsets(bannerContainer);
                    bannerView.loadAd(new AdRequest.Builder().build());
                }
                bannerContainer.setVisibility(View.VISIBLE);
                bannerVisible = true;
                bannerContainer.post(this::applyWebViewBottomInset);
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
            // أعد WebView إلى ارتفاعه الكامل — أثناء اللعب تُستعمل الشاشة كلها
            applyWebViewBottomInset();
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
    /**
     * فتح الروابط القانونية (الخصوصية، البنود، حذف البيانات) في متصفّح
     * الجهاز خارج التطبيق. نستعمل ACTION_VIEW بدل window.open لأن الأخير
     * قد يفتح نافذة داخل WebView على بعض الأجهزة، فيبقى المستخدم داخل
     * التطبيق — وGoogle Play تطلب أن تكون هذه الصفحات متاحة خارجه.
     * نقبل https فقط حتى لا يُستغلّ الجسر لإطلاق نوايا أخرى.
     */
    private class LinksBridge {
        @JavascriptInterface public void openExternal(String url) {
            if (url == null) return;
            final String u = url.trim();
            if (!u.startsWith("https://")) return;
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.addCategory(Intent.CATEGORY_BROWSABLE);
                    startActivity(i);
                } catch (Exception ignored) {}
            });
        }
    }

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

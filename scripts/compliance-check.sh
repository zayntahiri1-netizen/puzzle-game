#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
#  compliance-check.sh — يفحص الكود قبل كل رفع إلى Google Play
#
#  يفحص ما يمكن فحصه آلياً من سياسات Google Play. ما لا يمكن فحصه من
#  الكود (حالة الجهة التجارية، استمارة أمان البيانات، إبطال المفاتيح
#  من لوحات المزوّدين) مذكور في نهاية التقرير كإجراءات يدوية.
#
#  الاستعمال:  bash scripts/compliance-check.sh
# ════════════════════════════════════════════════════════════════════
set -uo pipefail
cd "$(dirname "$0")/.."

G='\033[0;32m'; R='\033[0;31m'; Y='\033[1;33m'; C='\033[0;36m'; B='\033[1m'; N='\033[0m'
FAIL=0
MANIFEST="android/app/src/main/AndroidManifest.xml"
VARS="android/variables.gradle"

ok()   { echo -e "  ${G}✓${N} $1"; }
bad()  { echo -e "  ${R}✗${N} $1"; FAIL=1; }
note() { echo -e "  ${Y}•${N} $1"; }

echo -e "${B}════════════════════════════════════════════════${N}"
echo -e "${B}  فحص امتثال Decipher Tahiro لسياسات Google Play${N}"
echo -e "${B}════════════════════════════════════════════════${N}"

# ── ① الأذونات المقيَّدة ───────────────────────────────────────────
echo
echo -e "${C}① الأذونات المقيَّدة${N}"
declare -A RESTRICTED=(
  [ACCESS_FINE_LOCATION]="الموقع الدقيق — إلزامي 27 يناير 2027"
  [ACCESS_COARSE_LOCATION]="الموقع التقريبي"
  [READ_CONTACTS]="جهات الاتصال — يلزم بيان في Console"
  [MANAGE_EXTERNAL_STORAGE]="الوصول إلى كل الملفات"
  [READ_SMS]="الرسائل القصيرة"
  [READ_CALL_LOG]="سجلّ المكالمات"
  [BIND_ACCESSIBILITY_SERVICE]="خدمة إمكانية الوصول"
  [NEARBY_WIFI_DEVICES]="الأجهزة القريبة"
  [CHANGE_WIFI_MULTICAST_STATE]="البثّ المتعدّد على الشبكة المحلية"
  [QUERY_ALL_PACKAGES]="استعلام كل التطبيقات"
)
HAS_RESTRICTED=0
for perm in "${!RESTRICTED[@]}"; do
  if grep -q "$perm" "$MANIFEST" 2>/dev/null; then
    bad "$perm مُعلَن — ${RESTRICTED[$perm]}"
    HAS_RESTRICTED=1
  fi
done
[ "$HAS_RESTRICTED" -eq 0 ] && ok "لا إذن مقيَّد — لا يلزم أي نموذج بيان في Play Console"
note "الأذونات المُعلَنة: $(grep -o 'android.permission.[A-Z_]*\|gms.permission.[A-Z_]*' "$MANIFEST" 2>/dev/null | sed 's/.*permission\.//' | sort -u | tr '\n' ' ')"

# ── ② اللعب الجماعي ───────────────────────────────────────────────
# هذا القسم للعلم فقط ولا يُفشل الفحص: قرار العرض قرار المطوّر.
echo
echo -e "${C}② اللعب الجماعي${N}"
if grep -q "isBot:true" www/index.html 2>/dev/null; then
  note "اللعبة تولّد خصوماً آليين في الوضع العالمي لتقليل زمن الانتظار"
  grep -q "🤖" www/terms.html 2>/dev/null \
    && ok "صفحة شروط الاستخدام تُفصح عن ذلك" \
    || note "صفحة شروط الاستخدام لا تذكره"
else
  ok "لا خصوم آليين في الكود"
fi

# ── ③ المزوّدون الخارجيون ─────────────────────────────────────────
# للعلم فقط: الإعداد والمفاتيح من اختيار المطوّر.
echo
echo -e "${C}③ المزوّدون الخارجيون${N}"
if [ -f www/hydra-engine.js ]; then
  # يتعامل مع الشكلين: المزوّد على عدّة أسطر، أو على سطر واحد
  # مثل  peerjs: { enabled: true }
  ACTIVE=$(awk '
    /^[[:space:]]*(photon|ably|pubnub|supabase|peerjs)[[:space:]]*:[[:space:]]*\{/ {
      match($0, /(photon|ably|pubnub|supabase|peerjs)/)
      cur = substr($0, RSTART, RLENGTH)
      if ($0 ~ /enabled[[:space:]]*:[[:space:]]*true/) { print cur; cur = "" }
      next
    }
    cur != "" && /enabled[[:space:]]*:[[:space:]]*true/ { print cur; cur = "" }
    cur != "" && /^[[:space:]]*\}/ { cur = "" }
  ' www/hydra-engine.js | tr '\n' ' ')
  CNT=$(echo "$ACTIVE" | wc -w | tr -d ' ')
  note "$CNT مزوّد مُفعَّل: ${ACTIVE:-—}"
  note "كل مزوّد مُفعَّل يجب أن يكون مُعلَناً في استمارة أمان البيانات في Play Console"
else
  ok "لا طبقة مزوّدين خارجيين"
fi

# ── ④ إعدادات الإعلانات ───────────────────────────────────────────
echo
echo -e "${C}④ AdMob والجمهور المستهدف${N}"
MA="android/app/src/main/java/com/deciphertahiro/app/MainActivity.java"
for pair in "TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE:ليس موجّهاً للأطفال" \
            "TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE:ليس لمن هم دون سنّ الموافقة" \
            "MAX_AD_CONTENT_RATING_T:تصنيف محتوى الإعلانات = للمراهقين"; do
  k="${pair%%:*}"; d="${pair#*:}"
  grep -q "$k" "$MA" 2>/dev/null && ok "$d" || bad "$k غير مضبوط — $d"
done
grep -q "user-messaging-platform" android/app/build.gradle 2>/dev/null \
  && ok "UMP مُدمج (موافقة GDPR وولايات أمريكية)" || bad "UMP غير مُدمج"
[ -f www/app-ads.txt ] && ok "app-ads.txt موجود" || bad "app-ads.txt مفقود"

# ── ⑤ إصدارات SDK ─────────────────────────────────────────────────
echo
echo -e "${C}⑤ إصدارات SDK${N}"
TS=$(grep -o "targetSdkVersion = [0-9]*" "$VARS" 2>/dev/null | grep -o '[0-9]*')
MS=$(grep -o "minSdkVersion = [0-9]*"    "$VARS" 2>/dev/null | grep -o '[0-9]*')
[ "${TS:-0}" -ge 36 ] && ok "targetSdk = $TS" || bad "targetSdk = ${TS:-?} — المطلوب 36 على الأقل"
note "minSdk = ${MS:-?}"
if [ "${TS:-0}" -ge 37 ]; then
  note "تستهدف Android 17: إن أعدت تفعيل PeerJS فيلزمك نموذج بيان «الوصول إلى الشبكة المحلية»"
fi

# ── ⑥ الصفحات القانونية ───────────────────────────────────────────
echo
echo -e "${C}⑥ الصفحات القانونية${N}"
for f in privacy-policy terms delete-account; do
  if [ -f "www/$f.html" ]; then
    grep -q "Ahmed Bakkali Tahiri" "www/$f.html" \
      && ok "$f — تذكر اسم المطوّر" \
      || bad "$f — لا تذكر اسم المطوّر (سبب رفض سابق)"
  else
    bad "www/$f.html مفقود"
  fi
done

# ── الخلاصة ───────────────────────────────────────────────────────
echo
if [ "$FAIL" -eq 0 ]; then
  echo -e "${G}${B}════ فحص الكود: نجح بالكامل ════${N}"
else
  echo -e "${R}${B}════ فحص الكود: توجد إخفاقات — أصلحها قبل الرفع ════${N}"
fi

echo
echo -e "${Y}${B}إجراءات لا تُفحص من الكود — في Play Console:${N}"
echo -e "  ${B}1.${N} حالة الجهة التجارية (Trader status) — مطلوبة من نوفمبر 2026"
echo -e "  ${B}2.${N} استمارة أمان البيانات — يجب أن تُعلِن كل مزوّد مُفعَّل في القسم ③"
echo -e "  ${B}3.${N} تفويض اليابان قبل 1 ديسمبر 2026 — إن ظهر إشعار في Console"
echo -e "  ${B}4.${N} اسم المطوّر في Play Console = Ahmed Bakkali Tahiri"

[ "$FAIL" -eq 0 ] || exit 1

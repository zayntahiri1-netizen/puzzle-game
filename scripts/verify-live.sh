#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
#  verify-live.sh — يفحص الموقع المنشور كما تفحصه Google و AdMob
#
#  شغّله بعد كل نشر، وقبل أن تحفظ الروابط في Play Console
#  أو تضغط "البحث عن تحديثات" في AdMob.
#
#  الاستعمال:  bash scripts/verify-live.sh  [النطاق]
# ════════════════════════════════════════════════════════════════════
set -uo pipefail

BASE="${1:-https://puzzle-game-2026.web.app}"
PUB="pub-1725525147318224"
DEV="Ahmed Bakkali Tahiri"

G='\033[0;32m'; R='\033[0;31m'; Y='\033[1;33m'; N='\033[0m'
FAIL=0

command -v curl >/dev/null 2>&1 || { echo "ثبّت curl أولاً:  pkg install curl"; exit 1; }

echo "════════════════════════════════════════════════"
echo "  فحص الموقع المنشور: $BASE"
echo "════════════════════════════════════════════════"

check() {                        # check <المسار> <الوصف> <نص1> [نص2...]
  local path="$1" label="$2"; shift 2
  local url="$BASE$path" body code
  body=$(curl -sSL -w $'\n__CODE__%{http_code}' --max-time 25 "$url" 2>/dev/null) || {
    echo -e "${R}✗${N} $label — تعذّر الاتصال بـ $url"; FAIL=1; return; }
  code="${body##*__CODE__}"; body="${body%$'\n'__CODE__*}"

  if [ "$code" != "200" ]; then
    echo -e "${R}✗${N} $label — HTTP $code   ($url)"
    [ "$code" = "404" ] && echo -e "    ${Y}الموقع لم يُنشر بعد. شغّل: bash deploy-firebase.sh${N}"
    FAIL=1; return
  fi

  local missing=""
  for needle in "$@"; do
    case "$body" in *"$needle"*) ;; *) missing="$missing\n      ▸ $needle";; esac
  done

  if [ -n "$missing" ]; then
    echo -e "${R}✗${N} $label — HTTP 200 لكن ينقصه:"; printf "%b\n" "$missing"; FAIL=1
  else
    echo -e "${G}✓${N} $label — HTTP 200، وكل العناصر المطلوبة موجودة"
  fi
}

echo
echo "── ما تفحصه Google Play ──"
check "/privacy-policy" "سياسة الخصوصية" \
      "applies to the mobile application" "Decipher Tahiro" "com.deciphertahiro.app" "$DEV"
check "/terms"          "شروط الاستخدام" \
      "applies to the mobile application" "Decipher Tahiro" "com.deciphertahiro.app" "$DEV"
check "/delete-account" "رابط حذف البيانات" \
      "applies to the mobile application" "com.deciphertahiro.app" "$DEV"

echo
echo "── ما تفحصه AdMob ──"
check "/app-ads.txt" "app-ads.txt" "google.com" "$PUB" "DIRECT"

echo
echo "── إضافي ──"
check "/robots.txt" "robots.txt" "Allow"

echo
echo "── نظافة الروابط (لا امتداد .html) ──"
# أي رابط بامتداد يجب أن يُحوَّل 301 إلى الشكل النظيف،
# فلا يظهر الامتداد في شريط العنوان ولا في فهرسة Google.
redirects_to_clean() {
  local from="$1" to="$2"
  local code loc
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$BASE$from")
  loc=$(curl -s -o /dev/null -w '%{redirect_url}' --max-time 20 "$BASE$from")
  if [ "$code" = "301" ] || [ "$code" = "308" ]; then
    case "$loc" in
      *"$to") echo -e "${G}✓${N} $from → $to" ;;
      *) echo -e "${R}✗${N} $from يحوّل إلى $loc بدل $to"; FAIL=1 ;;
    esac
  else
    echo -e "${R}✗${N} $from أعاد HTTP $code بدل تحويل إلى $to"; FAIL=1
  fi
}
redirects_to_clean "/terms.html"          "/terms"
redirects_to_clean "/privacy-policy.html" "/privacy-policy"
redirects_to_clean "/delete-account.html" "/delete-account"

echo
if [ "$FAIL" -eq 0 ]; then
  echo -e "${G}════ كل الفحوص نجحت ════${N}"
  echo "الآن يمكنك:"
  echo "  • Play Console ← احفظ روابط الخصوصية والبنود وحذف البيانات"
  echo "  • AdMob ← انتظر 24 ساعة ثم اضغط \"البحث عن تحديثات\""
else
  echo -e "${R}════ توجد إخفاقات — لا تحفظ الروابط بعد ════${N}"
  echo "أصلح ما سبق، ثم:  bash deploy-firebase.sh  &&  bash scripts/verify-live.sh"
  exit 1
fi

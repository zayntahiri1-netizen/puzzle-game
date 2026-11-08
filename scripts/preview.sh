#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
#  preview.sh — معاينة اللعبة في متصفّح الهاتف قبل بناء التطبيق
#
#  يشغّل خادماً محلياً على مجلّد www/ ثم يفتح المتصفّح.
#  السبب في استعمال خادم بدل فتح الملف مباشرةً (file://):
#  المتصفّحات تمنع localStorage وطلبات الشبكة على مسار الملفات،
#  فلا تُحفظ بيانات اللاعب ولا يعمل Firebase — وهو ما يجعل اللعبة
#  تبدو معطوبة بينما هي سليمة.
#
#  الاستعمال:  bash scripts/preview.sh  [المنفذ]
#  الإيقاف:    Ctrl + C
# ════════════════════════════════════════════════════════════════════
set -uo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
WWW="$ROOT/www"

G='\033[0;32m'; R='\033[0;31m'; Y='\033[1;33m'; C='\033[0;36m'; B='\033[1m'; N='\033[0m'

[ -f "$WWW/index.html" ] || { echo -e "${R}✗ لم أجد www/index.html — شغّل الأمر من داخل مجلّد المشروع${N}"; exit 1; }

# ── اختيار منفذ شاغر ──────────────────────────────────────────────
port_busy() {
  if command -v nc >/dev/null 2>&1; then nc -z 127.0.0.1 "$1" >/dev/null 2>&1; return $?; fi
  return 1
}
PORT="${1:-8080}"
TRIES=0
while port_busy "$PORT" && [ "$TRIES" -lt 20 ]; do
  PORT=$((PORT+1)); TRIES=$((TRIES+1))
done

URL="http://localhost:$PORT"

# ── اختيار خادم متاح ──────────────────────────────────────────────
SERVER=""
if   command -v python3 >/dev/null 2>&1; then SERVER="python3"
elif command -v python  >/dev/null 2>&1; then SERVER="python"
elif command -v php     >/dev/null 2>&1; then SERVER="php"
elif command -v npx     >/dev/null 2>&1; then SERVER="npx"
fi

if [ -z "$SERVER" ]; then
  echo -e "${R}✗ لا يوجد خادم محلي على الجهاز.${N}"
  echo -e "${C}ثبّت بايثون مرّة واحدة ثم أعد الأمر:${N}"
  echo    "    pkg install python -y"
  exit 1
fi

echo -e "${B}═══════════════════════════════════════════${N}"
echo -e "  ${B}معاينة Decipher Tahiro${N}"
echo -e "${B}═══════════════════════════════════════════${N}"
echo -e "  الرابط : ${G}${B}$URL${N}"
echo -e "  الخادم : $SERVER"
echo -e "  الإيقاف: ${Y}Ctrl + C${N}"
echo
echo -e "${C}روابط الصفحات (بلا امتداد .html):${N}"
echo -e "  • ${G}$URL/${N}                اللعبة"
echo -e "  • ${G}$URL/terms${N}           شروط الاستخدام"
echo -e "  • ${G}$URL/privacy-policy${N}  سياسة الخصوصية"
echo -e "  • ${G}$URL/delete-account${N}  حذف البيانات"
echo
echo -e "${C}ما يجب أن تراه فور فتح الصفحة:${N}"
echo -e "  • الأدلّة وخانات الإجابة ${B}أسفل${N} صفّ العدّاد ومستوى الصعوبة"
echo -e "  • الشارة تقرأ ${B}«مستوى 1 - سهل 🟢»${N} — النقطة الخضراء دليل أن اللغز وُلِّد"
echo -e "  • العدّاد يتحرّك من 00:00"
echo
echo -e "${Y}ملاحظة: الإعلانات لا تظهر في المتصفّح (تحتاج التطبيق الأصلي) — هذا طبيعي.${N}"
echo

# ── فتح المتصفّح تلقائياً على Termux ──────────────────────────────
if command -v termux-open-url >/dev/null 2>&1; then
  ( sleep 2; termux-open-url "$URL" >/dev/null 2>&1 ) &
  echo -e "${C}سيُفتح المتصفّح تلقائياً خلال ثانيتين...${N}"
else
  echo -e "${C}افتح الرابط أعلاه في متصفّحك.${N}"
fi
echo

# ── التشغيل ───────────────────────────────────────────────────────
# نستعمل خادماً خاصاً يحاكي Firebase: /terms يعمل بلا امتداد،
# و /terms.html يُحوَّل إليه — تماماً كالموقع المنشور.
case "$SERVER" in
  python3) exec python3 "$ROOT/scripts/preview-server.py" "$PORT" "$WWW" ;;
  python)  exec python  "$ROOT/scripts/preview-server.py" "$PORT" "$WWW" ;;
  php)     exec php -S "127.0.0.1:$PORT" -t "$WWW" ;;
  npx)     exec npx --yes http-server "$WWW" -p "$PORT" -a 127.0.0.1 -c-1 --ext html ;;
esac

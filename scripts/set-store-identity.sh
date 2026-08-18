#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════
#  set-store-identity.sh — يضبط اسم المطوّر واسم التطبيق في الصفحات
#  القانونية ليطابقا بطاقة متجر Google Play حرفياً.
#
#  سبب وجوده: Google رفضت الرابطين برسالة واحدة في الحالتين:
#    "missing reference to the app or to the entity (developer, company)
#     named in the app's Google Play listing"
#  المطابقة حرفية — أي اختلاف في الاسم = نفس الرفض مجدداً.
#
#  أين تجد الاسمين بالضبط؟
#    اسم المطوّر : Play Console ← الإعدادات ← تفاصيل حساب المطوّر ← اسم المطوّر
#                  (أو افتح بطاقة التطبيق على Play: الاسم تحت عنوان التطبيق)
#    اسم التطبيق : Play Console ← بطاقة بيانات المتجر الرئيسية ← اسم التطبيق
#
#  الاستعمال:
#    bash scripts/set-store-identity.sh "اسم المطوّر" ["اسم التطبيق"]
#
#  أمثلة:
#    bash scripts/set-store-identity.sh "Ahmed Bakkali Tahiri"
#    bash scripts/set-store-identity.sh "Tahiro Studio" "Decipher Tahiro"
# ════════════════════════════════════════════════════════════════════
set -euo pipefail

cd "$(dirname "$0")/.."

NEW_DEV="${1:-}"
NEW_APP="${2:-}"

if [ -z "$NEW_DEV" ]; then
  echo "الاستعمال: bash scripts/set-store-identity.sh \"اسم المطوّر\" [\"اسم التطبيق\"]"
  echo
  echo "الاسم الحالي في الصفحات:"
  python3 -c "import re;print(' ',(re.search(r'published by <strong>([^<]*)',open('www/privacy-policy.html',encoding='utf-8').read()) or [None,'؟']).group(1) if re.search(r'published by <strong>([^<]*)',open('www/privacy-policy.html',encoding='utf-8').read()) else '؟')"
  exit 1
fi

PAGES="www/privacy-policy.html www/delete-account.html"
for f in $PAGES; do
  [ -f "$f" ] || { echo "::خطأ:: $f غير موجود"; exit 1; }
done

# ── اسم المطوّر ────────────────────────────────────────────────────
OLD_DEV=$(python3 - <<'PY'
import re
s = open('www/privacy-policy.html', encoding='utf-8').read()
m = re.search(r'developed and published by <strong>([^<]*)</strong>', s)
print(m.group(1) if m else '')
PY
)
if [ -z "$OLD_DEV" ]; then
  echo "::خطأ:: لم أجد كتلة الهوية في www/privacy-policy.html — الملف ليس النسخة المُصلَحة."
  exit 1
fi

if [ "$OLD_DEV" != "$NEW_DEV" ]; then
  for f in $PAGES; do
    python3 - "$f" "$OLD_DEV" "$NEW_DEV" <<'PY'
import sys
path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(path, encoding='utf-8').read()
n = s.count(old)
s = s.replace(old, new)
open(path, 'w', encoding='utf-8').write(s)
print(f"  {path}: {n} موضع")
PY
  done
  echo "✅ اسم المطوّر: \"$OLD_DEV\" ← \"$NEW_DEV\""
else
  echo "ℹ️  اسم المطوّر مطابق أصلاً: \"$NEW_DEV\""
fi

# ── اسم التطبيق (اختياري) ─────────────────────────────────────────
if [ -n "$NEW_APP" ] && [ "$NEW_APP" != "Decipher Tahiro" ]; then
  for f in $PAGES; do
    python3 - "$f" "$NEW_APP" <<'PY'
import sys
path, new = sys.argv[1], sys.argv[2]
s = open(path, encoding='utf-8').read()
n = s.count("Decipher Tahiro")
s = s.replace("Decipher Tahiro", new)
open(path, 'w', encoding='utf-8').write(s)
print(f"  {path}: {n} موضع")
PY
  done
  echo "✅ اسم التطبيق: \"Decipher Tahiro\" ← \"$NEW_APP\""
fi

# ── تحقّق ─────────────────────────────────────────────────────────
echo
echo "════ ما سيراه فاحص Google ════"
for f in $PAGES; do
  echo "── $f"
  python3 - "$f" <<'PY'
import sys, re, html
s = open(sys.argv[1], encoding='utf-8').read()
s = re.sub(r'<style.*?</style>', '', s, flags=re.S)
s = re.sub(r'<script.*?</script>', '', s, flags=re.S)
t = html.unescape(re.sub(r'<[^>]+>', ' ', s))
t = re.sub(r'\s+', ' ', t)
i = t.find('applies to the mobile application')
print("   " + (t[i:i+190] if i >= 0 else "⚠️ كتلة الهوية غير موجودة!"))
PY
done

echo
echo "الخطوة التالية:  bash deploy-firebase.sh"

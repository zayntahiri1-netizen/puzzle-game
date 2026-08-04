#!/data/data/com.termux/files/usr/bin/bash
# ══════════════════════════════════════════════════════════════════
# 🚀 push-to-github.sh — رفع Decipher Tahiro تلقائياً
# ------------------------------------------------------------------
# الاستخدام:
#   ./scripts/push-to-github.sh                 (رسالة تلقائية)
#   ./scripts/push-to-github.sh "رسالة مخصصة"
# ══════════════════════════════════════════════════════════════════
set -e

# ═══ عدّل هذه مرة واحدة ═══
GH_USER="zayntahiri1-netizen"
GH_REPO="puzzle-game"        # ← puzzle-game (Public)
GH_BRANCH="main"
GIT_EMAIL="zayntahiri1@gmail.com"
# ═════════════════════════

C='\033[0;36m'; G='\033[0;32m'; Y='\033[1;33m'; R='\033[0;31m'; N='\033[0m'
say(){ echo -e "${C}▶ $1${N}"; }
ok(){ echo -e "${G}✓ $1${N}"; }
warn(){ echo -e "${Y}⚠ $1${N}"; }
err(){ echo -e "${R}✗ $1${N}"; }

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

[ -f "capacitor.config.json" ] && [ -d "android" ] || { err "لست في مجلد المشروع الصحيح."; exit 1; }
ok "المشروع: $ROOT"

command -v git >/dev/null 2>&1 || { err "git غير مثبّت. شغّل: pkg install git -y"; exit 1; }

[ -z "$(git config --global user.email 2>/dev/null)" ] && {
  git config --global user.email "$GIT_EMAIL"
  git config --global user.name "$GH_USER"
}
git config --global credential.helper store 2>/dev/null || true

if [ ! -d ".git" ]; then
  say "تهيئة مستودع git…"; git init -q; git branch -M "$GH_BRANCH"
fi

REMOTE_URL="https://github.com/${GH_USER}/${GH_REPO}.git"
if git remote | grep -q origin; then git remote set-url origin "$REMOTE_URL"
else git remote add origin "$REMOTE_URL"; fi
ok "الـ remote: ${GH_USER}/${GH_REPO}"

for f in $(git ls-files 2>/dev/null | grep -iE '\.(keystore|jks)$' || true); do
  err "خطر: مفتاح متتبَّع ($f)! أزِله: git rm --cached '$f'"; exit 1
done

say "إضافة الملفات…"; git add -A
MSG="${1:-🚀 تحديث Decipher Tahiro — $(date '+%Y-%m-%d %H:%M')}"
if git diff --cached --quiet && [ -n "$(git rev-list -n1 --all 2>/dev/null)" ]; then
  warn "لا تغييرات — إعادة رفع لتشغيل البناء."
  git commit --allow-empty -q -m "$MSG"
else
  git commit -q -m "$MSG"
fi
ok "حُفظ: $MSG"

say "الرفع وتشغيل البناء…"
if git push -u origin "$GH_BRANCH" --force; then
  echo ""; ok "تم الرفع بنجاح! 🎉"; echo ""
  echo -e "${G}➡ تابع البناء:${N} https://github.com/${GH_USER}/${GH_REPO}/actions"
  echo -e "${C}بعد ~10 دقائق حمّل الـ AAB من Artifacts.${N}"
else
  echo ""; err "فشل الرفع:"
  echo "  1) المستودع '${GH_REPO}' غير موجود → أنشئه: github.com/new (Private)"
  echo "  2) كلمة المرور مرفوضة → استخدم Personal Access Token"
  echo "     من: github.com/settings/tokens (صلاحية repo)"
  exit 1
fi

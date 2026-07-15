#!/data/data/com.termux/files/usr/bin/bash
# ══════════════════════════════════════════════════════════════════
# 🚀 نشر Decipher Tahiro على Firebase Hosting عبر Termux
# ------------------------------------------------------------------
# يتعامل مع مشاكل Termux الشائعة (فشل firebase login على Node الحديث).
# يدعم 3 طرق للمصادقة (بالترتيب حسب الأفضلية):
#   1) FIREBASE_TOKEN  (الأسهل على Termux — من: firebase login:ci)
#   2) service account (GOOGLE_APPLICATION_CREDENTIALS)
#   3) firebase login التفاعلي (احتياطي)
# ══════════════════════════════════════════════════════════════════

set -e
PROJECT="puzzle-game-2026"
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${GREEN}══ نشر Decipher Tahiro على Firebase ══${NC}"

# ── 1) تأكد من وجود firebase-tools ──
if ! command -v firebase >/dev/null 2>&1; then
  echo -e "${YELLOW}⚙️  firebase-tools غير مثبّت — جارٍ التثبيت...${NC}"
  npm install -g firebase-tools
fi

echo -e "الإصدار: $(firebase --version 2>/dev/null || echo 'غير معروف')"

# ── 2) تأكد من مجلد www ──
if [ ! -f "www/index.html" ]; then
  echo -e "${RED}✗ لم أجد www/index.html — شغّل السكربت من جذر المشروع${NC}"
  exit 1
fi

# ── 3) اختر طريقة المصادقة ──
DEPLOY_ARGS="--only hosting --project $PROJECT --non-interactive"

if [ -n "$FIREBASE_TOKEN" ]; then
  echo -e "${GREEN}🔑 استخدام FIREBASE_TOKEN${NC}"
  DEPLOY_ARGS="$DEPLOY_ARGS --token $FIREBASE_TOKEN"
elif [ -n "$GOOGLE_APPLICATION_CREDENTIALS" ] && [ -f "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
  echo -e "${GREEN}🔑 استخدام service account: $GOOGLE_APPLICATION_CREDENTIALS${NC}"
  # firebase CLI يلتقط GOOGLE_APPLICATION_CREDENTIALS تلقائياً
else
  echo -e "${YELLOW}⚠️  لا يوجد FIREBASE_TOKEN ولا service account.${NC}"
  echo -e "${YELLOW}   سأحاول firebase login التفاعلي (قد يفشل على بعض إصدارات Node).${NC}"
  echo -e "${YELLOW}   البديل الموصى به على Termux:${NC}"
  echo -e "     firebase login:ci   # على جهاز فيه متصفّح، ثم:"
  echo -e "     export FIREBASE_TOKEN=\"<الرمز>\"  &&  bash deploy-firebase.sh"
  firebase login || true
fi

# ── 4) النشر ──
echo -e "${GREEN}📤 جارٍ النشر...${NC}"
firebase deploy $DEPLOY_ARGS

echo ""
echo -e "${GREEN}✅ تم النشر بنجاح!${NC}"
echo -e "🌐 اللعبة:        https://${PROJECT}.web.app"
echo -e "🔒 الخصوصية:      https://${PROJECT}.web.app/privacy-policy"
echo -e "🗑️  حذف الحساب:    https://${PROJECT}.web.app/delete-account"

# 🚀 نشر Decipher Tahiro على Firebase Hosting عبر Termux

هذا الدليل ينشر **نسخة الويب** من اللعبة (المتصفّح) وصفحات الخصوصية/الحذف.

> ⚠️ **مهم:** Firebase Hosting ينشر الويب فقط. الإعلانات (AdMob) والميزات الأصلية (UMP، الاهتزاز) تعمل فقط في تطبيق Android (APK/AAB)، لا في نسخة الويب.

---

## الطريقة الأسهل (موصى بها لـ Termux)

مشكلة Termux الشهيرة: `firebase login` التفاعلي يفشل أحياناً على إصدارات Node الحديثة. الحل الأنظف = **رمز CI** (`FIREBASE_TOKEN`).

### الخطوة 1 — احصل على الرمز (مرة واحدة)

على **أي جهاز فيه متصفّح** (حاسوب، أو Termux مع متصفّح):

```bash
npm install -g firebase-tools
firebase login:ci
```

سيفتح المتصفّح لتسجّل الدخول، ثم يطبع رمزاً طويلاً مثل:
```
1//abc123XYZ...
```
انسخه واحفظه (يبقى صالحاً طويلاً).

### الخطوة 2 — انشر من Termux

```bash
# ادخل مجلد المشروع
cd decipher-tahiro

# ضع الرمز (استبدل بالرمز الحقيقي)
export FIREBASE_TOKEN="1//abc123XYZ..."

# شغّل السكربت الجاهز
bash deploy-firebase.sh
```

انتهى! سيطبع لك الروابط.

---

## الطريقة اليدوية (بدون السكربت)

```bash
cd decipher-tahiro
npm install -g firebase-tools
export FIREBASE_TOKEN="1//abc123XYZ..."
firebase deploy --only hosting --project puzzle-game-2026 --token "$FIREBASE_TOKEN"
```

---

## طريقة service account (بديل متقدّم)

إن كنت تفضّل service account (مثل ما فعلت سابقاً مع `puzzle-game-2026`):

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/data/data/com.termux/files/home/service-account.json"
bash deploy-firebase.sh
```

> احصل على ملف service account من: Firebase Console → Project Settings → Service accounts → Generate new private key.

---

## الروابط بعد النشر

| الصفحة | الرابط |
|--------|--------|
| 🎮 اللعبة | `https://puzzle-game-2026.web.app` |
| 🔒 سياسة الخصوصية | `https://puzzle-game-2026.web.app/privacy-policy` |
| 🗑️ حذف الحساب | `https://puzzle-game-2026.web.app/delete-account` |

استخدم رابطَي الخصوصية والحذف في **Google Play Console** عند رفع التطبيق.

---

## حلّ المشاكل الشائعة في Termux

| المشكلة | الحل |
|---------|------|
| `firebase: command not found` | `npm install -g firebase-tools` |
| `firebase login` يتجمّد/يفشل | استخدم `FIREBASE_TOKEN` بدلاً منه (الطريقة الأسهل أعلاه) |
| خطأ صلاحيات npm | `npm config set prefix ~/.npm-global` ثم أضف `~/.npm-global/bin` لـ PATH |
| `Error: Failed to get Firebase project` | تأكد أن الرمز صحيح وأن حسابك يملك صلاحية على `puzzle-game-2026` |
| Node قديم جداً | `pkg install nodejs-lts` |

---

## ملاحظة أمان

- لا تشارك `FIREBASE_TOKEN` أو ملف service account مع أحد — يمنحان صلاحية النشر على مشروعك.
- إن تسرّب الرمز: `firebase logout` ثم أنشئ رمزاً جديداً بـ `firebase login:ci`.

# 🚀 رفع Decipher Tahiro عبر Termux

## ⚡ الطريقة (مثل Ronda تماماً)

```bash
cd ~
rm -rf decipher-tahiro                          # احذف القديم أولاً
unzip /sdcard/Download/decipher-tahiro.zip
cd decipher-tahiro
chmod +x scripts/*.sh
./scripts/push-to-github.sh
```

هذا كل شيء! السكربت يتكفّل بالباقي ويرفع بـ force-push لتشغيل البناء.

للتحديثات مع رسالة مخصصة:
```bash
./scripts/push-to-github.sh "وصف التحديث"
```

---

## 📋 التحضيرات (مرة واحدة فقط)

### 1️⃣ المستودع على GitHub
تستخدم المستودع الموجود **`puzzle-game`** (عام/Public) — السكربت مضبوط عليه مسبقاً.

> إن أردت اسماً آخر، عدّل سطر `GH_REPO="..."` في أعلى `scripts/push-to-github.sh`.

⚠️ **بما أن المستودع عام:** فعّل قواعد أمان Firebase و Supabase RLS، واستبدل مفتاح Ably بمفتاح publish/subscribe فقط (المفتاح الحالي admin مكشوف علناً).

### 2️⃣ Personal Access Token
أول رفع سيطلب اسم المستخدم + التوكن (بدل كلمة المرور):
- [github.com/settings/tokens](https://github.com/settings/tokens) ← Generate new token (classic) ← صلاحية `repo`
- انسخه واستخدمه عند السؤال. سيُحفظ تلقائياً للمرات القادمة.

### 3️⃣ أسرار التوقيع (لبناء AAB موقّع)
```bash
pkg install openjdk-17 -y
keytool -genkey -v -keystore decipher-release.keystore \
  -alias decipher -keyalg RSA -keysize 2048 -validity 10000

base64 -w 0 decipher-release.keystore > keystore.base64.txt
cat keystore.base64.txt
```
في GitHub ← **Settings → Secrets → Actions**، أضِف 4 أسرار:

| السر | القيمة |
|------|--------|
| `DECIPHER_KEYSTORE_BASE64` | محتوى `keystore.base64.txt` |
| `DECIPHER_KEYSTORE_PASSWORD` | كلمة مرور الـ keystore |
| `DECIPHER_KEY_ALIAS` | `decipher` |
| `DECIPHER_KEY_PASSWORD` | كلمة مرور المفتاح |

⚠️ **لا ترفع ملف `.keystore` نفسه** — السكربت و `.gitignore` يمنعانه تلقائياً.

---

## 📥 بعد الرفع
1. `github.com/zayntahiri1-netizen/puzzle-game/actions`
2. انتظر ~10 دقائق
3. حمّل **`decipher-tahiro-aab`** من Artifacts
4. ارفعه على [Google Play Console](https://play.google.com/console)

> بدون أسرار التوقيع → ينتج APK تجريبي فقط (للتجربة، لا يصلح للنشر).

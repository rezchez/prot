# اپلیکیشن prot vpn برای اندروید

کپی‌رایت (c) 2019 Proton AG — نسخه سفارشی‌سازی شده و بومی

پروژه **prot vpn** (نسخه توسعه‌یافته بر پایه Proton VPN برای اندروید)، یک کلاینت قدرتمند، امن و متن‌باز برای دسترسی به اینترنت آزاد و حفظ حریم خصوصی است که همراه با قابلیت‌های بومی و اختصاصی از جمله **اشتراک‌گذاری اینترنت و پروکسی در شبکه محلی (LAN Proxy Sharing)**، **بای‌پس وب‌سایت‌های ایرانی (.ir)** و **سیستم پیشرفته تست پینگ سرورها** ارائه می‌شود.

---

## 📱 اسکرین‌شات‌ها و دریافت برنامه

<p align="center">
    <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/2.jpg" height="400" alt="صفحه اصلی">
    <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/3.jpg" height="400" alt="لیست سرورها">
    <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/4.jpg" height="400" alt="پروفایل‌ها">
    <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/5.jpg" height="400" alt="تنظیمات">
</p>

<p align="center">
    <a href="https://play.google.com/store/apps/details?id=ch.protonvpn.android">
        <img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" alt="دریافت از گوگل پلی" height="70">
    </a>
    <a href="https://f-droid.org/packages/ch.protonvpn.android">
        <img src="https://f-droid.org/badge/get-it-on.png" alt="دریافت از F-Droid" height="70">
    </a>
    <a href="https://github.com/ProtonVPN/android-app/releases">
        <img src="https://i.postimg.cc/kXfbyWjL/get-it-on-github.png" alt="دریافت از گیت‌هاب" height="70">
    </a>
</p>

---

## ✨ قابلیت‌های برجسته و تغییرات اخیر

- 🔄 **اشتراک‌گذاری اینترنت VPN در شبکه محلی (VPN & Proxy Sharing):**
  - **پروکسی SOCKS5:** گوش دادن روی آدرس `0.0.0.0` و پورت پیش‌فرض `10808` با قابلیت اتصال تلگرام و مرورگرها بدون نیاز به پسورد.
  - **پروکسی HTTP / HTTPS CONNECT:** پشتیبانی کامل از تانل CONNECT روی پورت `10809` برای ویندوز، مک و تلویزیون هوشمند.
  - **سرور خودکار PAC (Proxy Auto-Configuration):** آدرس اختصاصی `http://<IP>:port/proxy.pac` جهت تنظیم فوق‌العاده ساده در آیفون، آیپد و مک.
  - **پایش و نمودار زنده ترافیک:** نمایش لحظه‌ای سرعت دانلود و آپلود بر حسب Mbps، نمودار تغییرات سرعت در زمان (Time-Series Graph) و آمار حجم مصرفی با قابلیت ریست.
  - **تغییر آسان پورت‌ها:** پشتیبانی از پورت‌های آماده (Presets) و امکان وارد کردن پورت دلخواه با اعتبارسنجی.
- 🇮🇷 **بای‌پس وب‌سایت‌های ایرانی (.ir) و دامنه‌های دلخواه:**
  - تفکیک خودکار دامنه‌های ملی (`.ir`) و بیش از ۹۰ محدوده آی‌پی داخلی ایران از تونل VPN جهت فعال ماندن درگاه‌های بانکی و سرویس‌های داخلی.
  - امکان اضافه کردن آدرس وب‌سایت دلخواه (Custom URL) جهت عبور مستقیم بدون استفاده از فیلترشکن.
- ⚡ **تست پینگ پیشرفته و اختصاصی (Ping Test Screen):**
  - صفحه اختصاصی پینگ در نوار پایینی برنامه جهت پایش پایداری و سلامت اتصال سرورها.
  - تست پینگ پروتکل Stealth با هندشیک TLS روی پورت ۴۴۳ به طور مستقیم از طریق اینترنت فیزیکی دستگاه.
  - بررسی تاخیر شبکه با سرویس Cloudflare Trace (`1.1.1.1`).
  - چیپ‌های پینگ سریع (Server Ping Chips) روی کارت سرورها و کشورها همراه با نشانگرهای رنگی (سبز، زرد، قرمز).
- 🔍 **فیلتر و جستجوی پیشرفته در اسپلیت‌تانلینگ:**
  - جستجوی سریع بین برنامه‌های کاربردی، منتخب و سیستمی بر اساس نام و شناسه پکیج.
- 🛡️ **پروتکل‌های پیشرفته ضد فیلترینگ و امنیتی:**
  - پشتیبانی از پروتکل‌های Stealth، WireGuard و OpenVPN.
  - معماری امنیتی چند لایه، سرورهای Secure Core و حفاظت از نشت DNS.

---

## 🛠️ راهنمای کامپایل و بیلد (Build Instructions)

### پیش‌نیازها
- نصب **Android SDK** (نسخه API 34 یا بالاتر)
- نصب **Android NDK**، **CMake** و **SWIG**
- جاوا نسخه **JDK 17**
- گیت (Git)

### مراحل بیلد نسخه Debug
مخزن را کلون کرده و با اجرای دستور زیر در ترمینال یا از طریق Android Studio پروژه را بیلد کنید:
```bash
./gradlew assembleProductionVanillaOpenSourceDebug
```

### ساخت نسخه Release
برای ساخت نسخه نهایی (Release)، مشخصات کلید امضا را ارسال کنید:
```bash
./gradlew assembleProductionVanillaOpenSourceRelease \
    -PkeyStoreFilePath=<path_to_keystore> \
    -PkeyStoreKeyAlias=<key_alias> \
    -PkeyStorePassword=<keystore_password> \
    -PkeyStoreKeyPassword=<key_password>
```

> **نکته:** در این نسخه، کی‌استور داخلی `prot_vpn.keystore` برای بیلد‌های آزمایشی در ریشه پروژه قرار داده شده و در تنظیمات Gradle پیکربندی شده است.

---

## 📐 سبک کدنویسی (Code Style)

- **Java:** این پروژه از طرح کدنویسی توافق‌شده در فایل `ProtonStyle.xml` استفاده می‌کند. برای اعمال آن در اندروید استودیو از مسیر زیر اقدام کنید:
  ```text
  File >> Settings >> Editor >> Code Style >> Import Scheme
  ```
- **Kotlin:** رعایت استانداردهای رسمی کاتلین به همراه لینترهای **ktlint** و **detekt**.

---

## 🤝 مشارکت در پروژه (Contributing)

برای مشارکت در توسعه پروژه، لطفاً موارد زیر را مد نظر داشته باشید:
- پایبندی به اصول نام‌گذاری و سبک معماری موجود در پروژه.
- کدهای جدید باید ترجیحاً به زبان **Kotlin** و با بهره‌گیری از **Jetpack Compose**، **MVVM** و **Coroutines** پیاده‌سازی شوند.
- پس از افزودن کتابخانه‌های متن‌باز جدید، دستور `gradlew updateLicensesJson` را جهت به‌روزرسانی لایسنس‌ها اجرا نمایید.

### بررسی کیفیت کد و تست‌ها
پیش از ارسال Pull Request می‌توانید تست‌ها و بررسی‌های زیر را به صورت محلی اجرا کنید:
```bash
./gradlew checkstyle
./gradlew detekt
./gradlew test
./gradlew androidTest
```

---

## 🔐 امضا و اصالت (Signature)

اثر انگشت SHA256 گواهی امضای رسمی Proton VPN:
```text
ch.protonvpn.android
DC:C9:43:9E:C1:A6:C6:A8:D0:20:3F:34:23:EE:42:BC:C8:B9:70:62:8E:53:CB:73:A0:39:3F:39:8D:D5:B8:53
```

---

## 🏷️ سیستم نسخه‌گذاری (Versioning)

شماره‌گذاری نسخه‌ها از قالب استاندارد زیر پیروی می‌کند:
```text
[major].[minor].[patch].[hotfix]
```

---

## 📜 مجوز (License)

کدها و محتوای این مخزن تحت مجوز **GPLv3** (منتشر شده توسط بنیاد نرم‌افزارهای آزاد) ارائه شده‌اند. برای مشاهده متن کامل مجوز، به فایل [LICENSE](LICENSE) یا نشانی <https://www.gnu.org/licenses/> مراجعه کنید.

Copyright (c) 2019 Proton AG

# CloudLine VPN

**POWERED BY HDNTEAM**

یک اپلیکیشن VPN اندروید با هسته Xray، طراحی شده برای کار با پنل Hiddify.

---

## ساختار پروژه

```
CloudLine-VPN/
├── app/
│   ├── src/main/
│   │   ├── java/com/hdnteam/cloudlinevpn/
│   │   │   ├── CloudLineApp.kt              # Application class + Hilt
│   │   │   ├── data/
│   │   │   │   ├── db/                      # Room database
│   │   │   │   ├── model/                   # Data models
│   │   │   │   ├── parser/                  # Subscription & Xray config parser
│   │   │   │   └── repository/              # Data repository
│   │   │   ├── di/                          # Hilt dependency injection
│   │   │   ├── receivers/                   # Boot receiver
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt          # Main activity + navigation
│   │   │   │   ├── screen/                  # Compose screens
│   │   │   │   ├── theme/                   # Dark cloud theme
│   │   │   │   └── viewmodel/               # ViewModels
│   │   │   ├── vpn/
│   │   │   │   ├── CloudLineVpnService.kt   # VPN service
│   │   │   │   └── LatencyTester.kt         # Ping/latency testing
│   │   │   └── worker/
│   │   │       ├── SubscriptionUpdateWorker.kt  # Auto-update subs (5 min)
│   │   │       └── AppUpdateWorker.kt           # Daily app/xray update
│   │   └── res/
│   │       ├── drawable/                    # Icons + launcher
│   │       ├── values/                      # Strings, colors, themes
│   │       └── xml/                         # Config files
│   └── build.gradle.kts
├── gradle/libs.versions.toml                # Version catalog
└── settings.gradle.kts
```

## مشخصات فنی

| مورد | مقدار |
|------|-------|
| زبان | Kotlin |
| UI | Jetpack Compose |
| هسته VPN | Xray-core (AndroidLibXrayLite) |
| دیتابیس | Room |
| آپدیت خودکار | WorkManager |
| معماری | MVVM + Hilt |
| حداقل SDK | API 24 (Android 7.0) |

## نحوه Build

### پیش‌نیازها
- Android Studio Ladybug یا بالاتر
- JDK 17
- Android SDK 35

### مراحل
1. پروژه را در Android Studio باز کنید
2. Sync Gradle را اجرا کنید
3. Build > Build APK یا Run روی دستگاه

## قابلیت‌ها

### پروتکل‌های پشتیبانی‌شده
- VMess (TCP, WS, gRPC, XHTTP)
- VLESS (TCP, WS, gRPC, Reality, TLS)
- Trojan
- Shadowsocks

### ویژگی‌های اصلی
- ✅ اتصال خودکار به بهترین سرور (Auto-Connect)
- ✅ تست latency همه سرورها
- ✅ آپدیت خودکار Subscription هر ۵ دقیقه
- ✅ آپدیت روزانه Xray core از GitHub
- ✅ آپدیت خودکار برنامه از GitHub
- ✅ نمایش سرعت لحظه‌ای دانلود/آپلود
- ✅ نمایش اطلاعات حساب (حجم + روزهای باقی‌مانده)
- ✅ Proxy Share از طریق هات‌اسپات
- ✅ خرید اشتراک از طریق ربات تلگرام @vpnxubot
- ✅ تم تیره ابری پیش‌فرض
- ✅ پشتیبانی فارسی و انگلیسی

## نکات مهم

### GitHub Releases
برای فعال‌سازی آپدیت خودکار برنامه، در `AppUpdateWorker.kt` آدرس ریپو را تنظیم کنید:
```kotlin
const val GITHUB_API_BASE = "https://api.github.com/repos/YOUR_USERNAME/cloudline-vpn"
```

### Xray Core
کتابخانه `AndroidLibXrayLite` از JitPack بارگذاری می‌شود:
```
implementation("com.github.2dust:AndroidLibXrayLite:1.8.11")
```

---

*CloudLine VPN - POWERED BY HDNTEAM*

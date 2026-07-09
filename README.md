# Plants vs Zombies 2 - CLI

پیاده‌سازی متنی (CLI) بازی Plants vs. Zombies 2 برای درس برنامه‌سازی پیشرفته - دانشگاه صنعتی شریف (نیمسال دوم 04-05).

## پیش‌نیازها

- JDK 17 یا جدیدتر (پروژه با `--release 17` کامپایل می‌شود)
- نیازی به نصب Gradle نیست؛ از Gradle Wrapper همراه مخزن استفاده کنید (`./gradlew`، در ویندوز `gradlew.bat`)

## دستورات

```bash
./gradlew build      # کامپایل + تست + Checkstyle + PMD (طبق قوانین لینتر سند پروژه)
./gradlew test       # فقط اجرای تست‌ها
./gradlew check      # فقط تست + لینترها
./gradlew run        # اجرای بازی (ورودی/خروجی ترمینال)
./gradlew installDist   # ساخت توزیع اجرایی در build/install/plants-vs-zombies/
```

## معماری (MVC)

```
src/main/java/ir/sharif/pvz/
├── Main.java          نقطه‌ی ورود
├── model/             دامنه و منطق کسب‌وکار (User، AuthService، اعتبارسنجی، ذخیره‌سازی JSON)
├── view/              تمام خروجی کنسول (ConsoleView) — کنترلرها مستقیم print نمی‌کنند
├── controller/        حلقه‌ی اصلی (GameApp)، کنترلر هر منو و ناوبری بین منوها
└── util/              ابزارها (هش SHA-256)
```

سایر مسیرها:

```
src/main/resources/            دیتای استاتیک بازی (گیاهان، زامبی‌ها، ...)
src/test/java/ir/sharif/pvz/   تست‌ها
docs/                          دیاگرام‌های UML و مستندات طراحی
checkstyle.xml / pmd-ruleset.xml   قوانین لینتر مطابق سند پروژه
data/                          دادهٔ ذخیره‌شده در زمان اجرا (users.json، سشن) — در گیت نیست
```

## وضعیت پیاده‌سازی فاز ۱

- [x] سیستم منوها (`menu enter/exit/show current`) و ناوبری طبق سند
- [x] منوی ثبت‌نام: `register` با تمام اعتبارسنجی‌ها، تکرار رمز، سوال امنیتی (`pick question`)
- [x] منوی ورود: `login` (با `-stay-logged-in`)، فراموشی رمز (`forget password` / `answer`)
- [x] هش SHA-256 رمز و پاسخ امنیتی + ذخیره‌سازی کاربران بین اجراها (بخش امتیازی)
- [x] `menu logout`
- [ ] منوی اصلی: پروفایل، تنظیمات (سختی)، اخبار
- [ ] منوی بازی: فصل‌ها، کلکسیون، گلخانه، فروشگاه، کوئست‌ها، لیدربورد
- [ ] موتور بازی: tick، خورشید، موج زامبی، کاشت گیاه، چمن‌زن، برد/باخت
- [ ] مراحل ویژه و مینی‌گیم‌ها

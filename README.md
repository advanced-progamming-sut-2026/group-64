# Plants vs Zombies 2 - CLI

پیاده‌سازی متنی (CLI) بازی Plants vs. Zombies 2 برای درس برنامه‌سازی پیشرفته - دانشگاه صنعتی شریف (نیمسال دوم 04-05).

## پیش‌نیازها

- JDK 17 (برای اجرای Checkstyle/PMD؛ نصب سیستم ممکن است JDK جدیدتری هم داشته باشد که با PMD 7 سازگار نیست)
- Maven 3.9+

اگر `java -version` نسخه‌ای غیر از 17 نشان می‌دهد، قبل از اجرای دستورات زیر:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
```

## دستورات

```bash
mvn compile        # فقط کامپایل
mvn test           # اجرای تست‌ها
mvn verify         # کامپایل + تست + Checkstyle + PMD (طبق قوانین لینتر سند پروژه)
mvn package         # ساخت jar اجرایی در target/plants-vs-zombies.jar
java -jar target/plants-vs-zombies.jar
```

## ساختار پروژه

```
src/main/java/ir/sharif/pvz/   کد اصلی
src/main/resources/            دیتای استاتیک بازی (گیاهان، زامبی‌ها، ...)
src/test/java/ir/sharif/pvz/   تست‌ها
docs/                          دیاگرام‌های UML و مستندات طراحی
checkstyle.xml / pmd-ruleset.xml   قوانین لینتر مطابق سند پروژه
```

# LexiFlash — وضعیت پروژه، ساختار، و خطاهای باز

> این سند برای یک مدل/مهندس بیرونی نوشته شده که پروژه را نمی‌شناسد.
> هدف: فهم سریع معماری + دیدن دقیق خطاها + دادن راه‌حل عملی.
> تاریخ: ۲۰۲۶-۱۱-۲۶ · نسخهٔ در حال انتشار: **1.0.2** (versionCode 3 — هنوز بیلد نشده)

---

## ۱. این پروژه چیست

اپلیکیشن اندروید فلش‌کارت واژگان انگلیسی → فارسی برای فارسی‌زبانان.
تمام رابط کاربری دوزبانه (فارسی/انگلیسی) و RTL است.

| | |
|---|---|
| زبان | Kotlin 1.9.24 |
| UI | Jetpack Compose + Material 3 |
| معماری | MVVM + دستی DI (`AppContainer`)، بدون Hilt |
| دیتابیس | Room |
| تنظیمات | DataStore Preferences |
| حداقل اندروید | API 26 (اندروید ۸) |
| Gradle | 8.7 / AGP 8.5.x |

### دو flavour

| flavour | applicationId | مجوزها | کد شبکه |
|---|---|---|---|
| `offline` | `ir.laxipatem.lexiflash` | **هیچ** | ندارد (stub) |
| `online` | `ir.laxipatem.lexiflash.online` | `INTERNET`, `ACCESS_NETWORK_STATE` | `HttpURLConnection` + ML Kit |

هر دو flavour از **همان `OnlineFeatures` interface** استفاده می‌کنند و هر کدام یک
`OnlineFeaturesFactory.create(context)` جدا دارند. فقط یکی کامپایل می‌شود.
build type `debug` هم `applicationIdSuffix = ".debug"` دارد.

### درخت فایل‌های مهم

```
app/src/main/java/ir/laxipatem/lexiflash/
├── LexiFlashApp.kt          Application؛ می‌سازد AppContainer و warmUp()
├── AppContainer.kt          گراف وابستگی دستی، lazy
├── MainActivity.kt          تنها Activity؛ setContent { LexiFlashTheme { AppNavHost } }
├── data/
│   ├── db/                  Room: AppDatabase, DeckDao, ReviewDao, QuizDao
│   ├── model/               DeckJson, DeckJsonCodec, JsonParser, JsonValue
│   └── repo/                DeckRepository, ReviewRepository, StatsRepository, SettingsRepository
├── domain/unlock/           UnlockPolicy (باز شدن تدریجی بخش‌ها)
├── importer/                ImportService + پارسرهای SRT/VTT/ASS/CSV/TXT/JSON
├── online/CatalogDefaults.kt
├── platform/                OnlineFeatures (interface), TtsController, DeckExporter
└── ui/
    ├── common/              RaisedCard, StateViews, FlipCard, Formatters, ViewModelFactory
    ├── home/                HomeScreen, HomeViewModel, CatalogScreen, CatalogViewModel
    ├── importer/            ImportScreen, ImportViewModel
    ├── study/ quiz/ review/ progress/ settings/ about/
    ├── nav/AppNavHost.kt
    └── theme/               Color.kt, Theme.kt, Shapes.kt, Type.kt
app/src/online/java/.../platform/   CatalogClient, HttpFetcher, MlKitTranslator, OnlineFeaturesFactory
app/src/offline/java/.../platform/  OnlineFeaturesFactory (stub)
app/src/main/assets/content/sample_deck.json   ۳۰ کارت، ۳ بخش
catalog/catalog.json + catalog/decks/*.json    کاتالوگ آنلاین (۸۰ کارت، ۳ دِک)
scripts/smoke-test.sh                         تست دود روی امولاتور
```

### مسیر راه‌اندازی (مهم برای دیباگ کرش)

```
Application.onCreate()
  └─ AppContainer(this)
       └─ warmUp() → IO scope → decks.ensureCoreDeck() + exporter.clearCache()
MainActivity.onCreate()
  └─ setContent { appContainer.settings.settings.collectAsStateWithLifecycle() }
       └─ LexiFlashTheme { Surface { AppNavHost } }
            └─ HOME → HomeScreen → HomeViewModel
                 uiState = combine(decksFlow, partsFlow, settingsFlow).stateIn(initialValue = HomeUiState())
                 HomeUiState() یعنی isLoading=false, decks=[], parts=[]
                 ⟹ ابتدا EmptyState؛ به‌محض seed شدن دِک ⟹ LazyColumn + PartsGrid
```

---

## ۲. تاریخچهٔ خطاها — چه چیزی حل شد

### 2.1 کرش فوری هنگام باز شدن (نسخهٔ ۱.۰.۰) — **حل شد**

**علامت:** اپ نیم‌ثانیه باز می‌شد و بسته می‌شد. هر دو flavour.

**استک‌تریس واقعی (از امولاتور):**
```
java.lang.IllegalStateException: Vertically scrollable component was measured
with an infinity maximum height constraints, which is disallowed.
  at androidx.compose.foundation.CheckScrollableContainerConstraintsKt
  at androidx.compose.foundation.lazy.grid.LazyGridKt$rememberLazyGridMeasurePolicy$1$1
```

**دلیل:** `PartsGrid` یک `LazyVerticalGrid` داخل `item {}` از `LazyColumn` می‌ساخت.
والد lazy به فرزندش ارتفاع بی‌نهایت می‌دهد و lazy grid آن را رد می‌کند و
در measure کرش می‌کند. دو scrollable هم‌محور نمی‌توانند تودرتو شوند.

**راه‌حل:** `Column` از `Row`ها با `parts.chunked(3)`.

### 2.2 کاتالوگ آنلاین ۴۰۴ — **حل شد**

**علامت:** در صفحهٔ کاتالوگ، دکمهٔ «دریافت کاتالوگ» ⟹
`The server answered with HTTP 404`.

**دلیل:** آدرس پیش‌فرض `https://raw.githubusercontent.com/laxipatem/lexiflash-catalog/main/catalog.json`
بود — آن مخزن **هرگز ساخته نشده**.

**راه‌حل:** کاتالوگ داخل همین مخزن (`catalog/`) و آدرس به
`https://raw.githubusercontent.com/sarzaminaryan-arch/LearnEn/main/catalog/catalog.json` تغییر کرد.
صفحه هم حالا هنگام ورود خودکار load می‌کند، نه با دکمه.

### 2.3 مراحل خطای smoke test در CI (همه حل شد)

| # | خطا | دلیل |
|---|---|---|
| 1 | `exit code 2` بدون هیچ خروجی | اکشن `android-emulator-runner` اسکریپت را با `sh -c` اجرا می‌کند و `/bin/sh` روی Ubuntu همان `dash` است؛ `set -o pipefail` در dash وجود ندارد |
| 2 | `EACCES: permission denied, mkdir '/cmdline-tools'` | `ANDROID_HOME: ${{ env.ANDROID_SDK_ROOT }}` در زمان parse خالی بود ⟹ مسیر `/` |
| 3 | `package=ir.laxipatem.lexiflash.` (نقطهٔ اضافی) | استخراج پکیج از string pool باینری مانیفست؛ `\0` حذف می‌شد و رشته‌ها به هم می‌چسبیدند |
| 4 | `"process alive: no"` ولی هیچ اثری از اپ در logcat نبود | کامپوننت اشتباه: `$PKG/.MainActivity` — با `applicationIdSuffix`، پکیج `.debug` می‌شود ولی کلاس همان `ir.laxipatem.lexiflash.MainActivity` می‌ماند. حل با `aapt2 dump badging` |
| 5 | log job در دسترس نبود | لاگ از طریق **annotation** منتقل می‌شود، نه artifact (که به Azure blob ریدایرکت می‌شود و بلاک است) |

### 2.4 خطاهای کامپایل در همین تغییرات آخر (همه حل شد)

| خطا | دلیل | راه‌حل |
|---|---|---|
| `Unresolved reference: item` | بدنهٔ `PickStep` را جایگزین کردم و خودِ `LazyColumn(...) {` هم پاک شد | تابع کامل بازنویسی شد |
| `Unresolved reference: shouldUseDarkTheme` | تابع کمکی تم همراه با پالت قدیمی پاک شد | تابع برگردانده شد |

---

## ۳. خطاهایی که **باز** هستند (به این‌ها نیاز به راهنمایی دارم)

### ❗ 3.1 کرش روی «ایمپورت» و «دانلود» روی دستگاه واقعی — **تشخیص نهایی نشده**

این مهم‌ترین مورد است. کاربر روی گوشی شیائومی (اندروید ~۱۴) گزارش داد:

> «سی تا جمله داشت، و روی بخش دانلود و ایمپورت کراش می‌کرد»

یعنی: صفحهٔ اصلی بالا می‌آید، ۳۰ کارت دِک نمونه دیده می‌شود، ولی رفتن به
**ایمپورت** یا **دانلود (کاتالوگ)** اپ را می‌بندد.

#### چه چیزی را قبلاً رد کردم (با دلیل)

| فرضیه | چرا رد شد |
|---|---|
| کرش compose به‌خاطر `LazyVerticalGrid` در Home | آن در ۱.۰.۱ حل شد؛ Home حالا باز می‌شود |
| `Column` غیرقابل‌اسکرول در `PickStep` سرریز می‌کند | سرریز `Column` در Compose کرش نمی‌کند، فقط کلیپ می‌شود. با این حال به `LazyColumn` تبدیل شد |
| `ImportService` پرتاب استثنا می‌کند | تمام مسیر در `runCatching` است و `Result` برمی‌گرداند |
| `OnlineFeatures` پرتاب می‌کند | `HttpFetcher.getText` همهٔ `IOException`/`IllegalArgumentException`/`SecurityException` را می‌گیرد |
| `aapt2`/پکیج | بی‌ربط به این کرش |

#### فرضیه‌های باقی‌مانده (به ترتیب احتمال)

1. **`rememberLauncherForActivityResult` وقتی `context` عوض می‌شود.**
   `LexiFlashTheme` یک `LocalContext` **جدید** می‌دهد:
   ```kotlin
   val localizedContext = remember(context, language) {
       java.util.Locale.setDefault(locale)
       context.createConfigurationContext(configuration)
   }
   CompositionLocalProvider(LocalContext provides localizedContext) { MaterialTheme(...) }
   ```
   `ImportScreen` این را می‌گیرد:
   ```kotlin
   val context = LocalContext.current   // ← context ساخته‌شده، نه Activity
   val viewModel = viewModel(factory = containerFactory(container) { ImportViewModel(it, context) })
   val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { ... }
   ...
   onPickSingle = { singlePicker.launch(IMPORT_MIME_TYPES) }
   ```
   `rememberLauncherForActivityResult` برای register کردن، `LocalActivityResultRegistryOwner`
   را از composition می‌خواهد. `CompositionLocalProvider` فقط `LocalContext` و
   `LocalLayoutDirection` را جایگزین می‌کند، بقیه (از جمله registry owner) از
   Activity واقعی می‌آیند. **اما** `ImportViewModel` یک `Context` غیر-Activity
   نگه می‌دارد و بعداً `contentResolver.openInputStream(uri)` صدا می‌زند — که روی
   context ساخته‌شده کار می‌کند. ⟹ **حدس من این است که کرش در لحظهٔ `launch`
   نیست، بلکه در ساخت/اندازه‌گیری صفحه است.** باید با لاگ واقعی تأیید شود.

2. **`CircularProgressIndicator` با `Modifier.heightIn(min = 24.dp)`**
   ```kotlin
   CircularProgressIndicator(modifier = Modifier.heightIn(min = 24.dp))
   ```
   این indicator در Compose Material3 یک اندازهٔ پیش‌فرض ۴۰dp دارد و با
   `heightIn(min=)` محدود نمی‌شود؛ ولی کرش نمی‌دهد.

3. **`DeckJsonCodec` روی فایل دانلودی.**
   `saveDeckFromJson` → `DeckJsonCodec.parse` → نیاز به `version`, `title`,
   `source_lang`, `target_lang`, `parts[]` دارد. اگر کاتالوگ لینک به فایل
   نامعتبر بدهد `DeckValidationException` می‌دهد که در `runCatching` گرفته
   می‌شود ⟹ پیام خطا، نه کرش.

4. **چیزی که در `initialize` یا `CompositionLocal` رخ می‌دهد ولی من ندیده‌ام.**

#### چه چیزی لازم است

- `adb logcat -d | grep -A 40 "FATAL EXCEPTION"` از گوشی واقعی، یا
- یک تست instrumented که واقعاً `CatalogScreen` و `ImportScreen` را render کند
  (نوشته‌ام: `app/src/androidTest/.../ui/ScreenRenderTest.kt` ولی در CI اجرا نمی‌شود).

### ❗ 3.2 CI: تست‌های instrumented هیچ‌وقت اجرا نمی‌شوند

`app/src/androidTest/` چهار تست دارد (DatabaseRoundTrip, FlavourContract,
StateViews, ScreenRender) ولی هیچ workflow ای `connectedDebugAndroidTest`
را اجرا نمی‌کند. فقط smoke-test وجود دارد که اپ را لانچ می‌کند و به صفحات
داخلی نمی‌رود.

**پیشنهاد اولیه (نیاز به تأیید):** به `smoke-test.yml` یک step اضافه شود که
`./gradlew :app:connectedDebugAndroidTest` را روی همان امولاتور اجرا کند،
و لاگ را مثل الان از طریق annotation بیرون بدهد.

---

## ۴. وضعیت فعلی مخزن

```
main = ed7075f   (PR #21 مرج شده)
branch: arena/60c76440-learnen @ 65cd719  (هیچ تغییر uncommitted)
```

### آخرین اجراهای CI

| چه | نتیجه |
|---|---|
| Build (۴ variant) روی PR #21 | ✅ همه pass |
| Smoke test release/online روی `main` | ✅ `alive=yes fatal=0`، اولین فریم ۱۲۷۸ms |
| Smoke test v1.0.1 (قبل از تغییرات) | ✅ |

### نسخه‌ها

| نسخه | وضعیت | مشکل |
|---|---|---|
| 1.0.0 | منتشرشده | کرش فوری (LazyVerticalGrid) |
| 1.0.1 | منتشرشده، روی Drive | کرش/۴۰۴ روی ایمپورت و دانلود |
| **1.0.2** | **بیلد نشده** — کد روی `main` هست، tag ندارد | نیاز به تست واقعی |

### Release v1.0.1 (منتشرشده)
https://github.com/sarzaminaryan-arch/LearnEn/releases/tag/v1.0.1

APKها روی Google Drive:
- آفلاین: https://drive.google.com/file/d/1h7j2-qEJK2j2Hu0frAFQzbzy49mcYyVD/view
- آنلاین: https://drive.google.com/file/d/1qssiJeRUy-I4pkfvtfF7z7DS7FAROO-a/view

---

## ۵. محدودیت‌های محیطی (خیلی مهم برای درک خطاها)

اگر مدل بیرونی این‌ها را نداند، راه‌حل‌های غیرقابل‌اجرا می‌دهد:

1. **بدون اندروید SDK / Java / keytool محلی.** اپ فقط در CI بیلد می‌شود.
2. **CDN گیت‌هاب بلاک است**: `release-assets.githubusercontent.com`,
   `raw.githubusercontent.com`, `objects.githubusercontent.com` ⟹ همه ۰۰۰.
   کار می‌کند: `github.com`, `api.github.com`, `codeload.github.com`.
   (این یعنی **کد اپ می‌تواند به `raw.githubusercontent.com` وصل شود چون روی
   گوشی کاربر اجرا می‌شود، نه در sandbox**.)
3. **لاگ job در دسترس نیست.** فقط **annotations** (از طریق
   `api.github.com/repos/.../check-runs/<id>/annotations`) خواندنی است.
   artifact API به Azure blob ریدایرکت می‌شود که بلاک است.
4. **امولاتور فقط در CI** (KVM). هر تست باید در GitHub Actions اجرا شود.
5. `gh workflow run` فقط روی workflow ای کار می‌کند که روی default branch باشد.
6. push به شاخهٔ کاری می‌تواند رد شود اگر remote جلوتر رفته باشد ⟹
   `git reset --hard origin/<branch>` سپس دوباره commit.

---

## ۶. چه چیزی از مدل بیرونی می‌خواهم

برای هر مورد باز (۳.۱ و ۳.۲) مشخصاً:

1. **علت ریشه‌ای محتمل** با ارجاع به کد بالا.
2. **کد یا patch مشخص** — نه توضیح کلی.
3. **راهی برای اثبات** که حدس است یا واقعیت (چه لاگ/تستی آن را تأیید می‌کند).
   چون در این پروژه «حدس زدن» و «بدون لاگ واقعی جلو رفتن» ممنوع است.

### سؤال مشخص ۳.۱

آیا در Compose، `CompositionLocalProvider(LocalContext provides context.createConfigurationContext(cfg))`
می‌تواند باعث شود `rememberLauncherForActivityResult` یا هر چیز دیگری در
`ImportScreen` هنگام ورود به صفحه کرش کند؟ و اگر نه، چه الگویی در این کد
می‌تواند در **اولین اندازه‌گیری** کرش کند که در review ساده دیده نشود؟

### سؤال مشخص ۳.۲

بهترین الگو برای اجرای `connectedAndroidTest` داخل همان
`reactivecircus/android-emulator-runner@v2` با انتقال لاگ از طریق annotation
(چون artifact بلاک است) چیست؟ یک نمونهٔ `script:` معتبر که با `sh -c`/dash
شکست نخورد می‌خواهم.

---

## ۷. قوانین پروژه که نباید نقض شوند

- **حدس نزن؛ فقط با لاگ واقعی جلو برو.**
- محتوا باید اصل باشد (CC/Apache). هیچ نمونه‌ای از فیلم یا TED. Tatoeba فقط CC BY 2.0 FR/CC0.
- flavour آفلاین **هیچ** کد شبکه و **هیچ** مجوزی نداشته باشد.
- APK باید برای کافه‌بازار/مایکت قابل استفاده باشد (کلید امضا فعلاً موقت است).
- همهٔ رشته‌ها باید در `values/strings.xml` و `values-fa/strings.xml` هر دو باشند
  (تست `StringParityTest` این را چک می‌کند).
- `ContrastTest` تمام جفت‌رنگ‌ها را در WCAG AA چک می‌کند.

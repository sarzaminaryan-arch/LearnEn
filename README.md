# LexiFlash Persian · فلش‌لکس

English → Persian sentence flashcards, quizzes and FSRS-5 spaced review for Android.
Offline by default, with an optional build that adds a deck catalogue and on-device
machine translation.

**[English](#english) · [فارسی](#فارسی)**

---

## English

### What it is

A flashcard app that teaches English through **complete sentences** rather than
isolated words, with the Persian translation on the other side. Cards are studied,
quizzed, and then rescheduled by FSRS-5 — the current generation of spaced-repetition
scheduling — so each card returns just before you would forget it.

Everything works with no internet connection and no account. There is no sign-in,
no advertising, no analytics and nothing to subscribe to.

### Features

| | |
|---|---|
| **Sentence flashcards** | A 3D flip card with the English sentence, IPA, and a Persian translation. Tap to flip. |
| **FSRS-5 scheduling** | Nineteen parameter weights, 90% target retention, per-card stability and difficulty. |
| **Quizzes** | Three question types — meaning, reverse, and cloze — generated on device. Score 80% on a part to unlock the next. |
| **Progress** | Streak, daily goal, accuracy, per-part completion and unlock state. |
| **Text to speech** | Reads the English sentence aloud, with an install prompt when no English voice is present. |
| **Import** | Bring your own material: `.srt`, `.vtt`, `.ass`, `.txt`, `.csv` or `deck.json`, through the system file picker. Bilingual subtitle files are paired by timestamp. |
| **Export** | Any deck can be written back out as `deck.json` and shared, so nothing is trapped in the app. |
| **Two languages** | Persian by default, English optional. Full RTL layout in Persian, with bundled Vazirmatn typography for Persian and Latin text. |
| **Two flavours** | `offline` has no network permission at all. `online` adds a catalogue and ML Kit translation. |

### Screens

```
Home ──► Study ──► Quiz ──► Result
  │        (flip cards)  (80% to unlock the next part)
  ├──► Review      (cards due today, across all decks)
  ├──► Progress    (streak, goal, accuracy, per-part status, export)
  ├──► Import      (file picker → preview → save)
  ├──► Catalog     (online flavour only, button-initiated)
  ├──► Settings    (theme, language, digits, goal, speed, delete all)
  └──► About       (licences, attribution, privacy)
```

### Architecture

```
ui/            Compose screens and their view models, one package per feature
domain/        Pure Kotlin: fsrs, unlock rules, streaks, quiz generation
data/
  db/          Room entities, DAOs and the database
  model/       Hand-written JSON parser and the deck.json codec
  repo/        Deck, review, stats and settings repositories
importer/      Six format parsers, encoding detection, cleaning, pairing, splitting
platform/      Text to speech, deck export, the OnlineFeatures contract
online/        HTTP + ML Kit implementation (online source set only)
offline/       No-op implementation (offline source set only)
```

Dependency injection is a single `AppContainer` class — no Hilt, no Dagger. The
full rationale for every non-obvious choice is in
**[docs/DECISIONS.md](docs/DECISIONS.md)**.

### Build from source

Requirements: **JDK 17**, the **Android SDK** with platform 34, and **Gradle 8.7**.

The repository is text-only and therefore does **not** contain
`gradle/wrapper/gradle-wrapper.jar`. Either use a system Gradle, or generate the
wrapper once for yourself (it stays untracked):

```bash
gradle wrapper --gradle-version 8.7    # creates ./gradlew, do not commit the jar
```

Then:

```bash
# Debug APKs
gradle :app:assembleOfflineDebug
gradle :app:assembleOnlineDebug

# Unit tests
gradle :app:testOfflineDebugUnitTest

# Release (unsigned unless a keystore is configured)
gradle :app:assembleOfflineRelease
gradle :app:bundleOfflineRelease
```

Outputs land in `app/build/outputs/apk/<flavour>/<buildType>/`.

On GitHub, **Actions → Build** does all of this on every push and attaches the four
APKs as artefacts. **Actions → Release** additionally produces signed AABs and APKs
when the signing secrets are set.

#### Signing

Precedence, highest first:

1. Environment variables `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`.
2. A `keystore.properties` file in the repository root (`storeFile`,
   `storePassword`, `keyAlias`, `keyPassword`).
3. Neither present → the release build is unsigned. Debug builds never need a key.

No keystore, password or alias is ever committed.

### Building the sentence deck

The bundled deck is a build artefact, not a checked-in binary. To regenerate it
from Tatoeba:

```bash
python -m venv .venv && .venv/bin/pip install -r tools/requirements.txt
python tools/build_deck.py --out app/src/main/assets/content/core_deck.json
python tools/validate_deck.py app/src/main/assets/content/core_deck.json
python -m pytest tools -q          # the tool's own tests
```

The script downloads the small **per-language** Tatoeba exports (three files, a few
dozen megabytes), verifies the link-file column direction at runtime, joins the
human translations, scores difficulty with word frequency, and writes around 3000
cards across 10 parts. It refuses to write a deck with fewer than 300 cards or any
part under 30.

If `espeak-ng` is installed, the `ipa` field is filled in. Otherwise it is left
empty and the app simply hides it.

Without a generated deck the app falls back to the 30 hand-written pairs in
`sample_deck.json`, so a fresh clone always has something to study.

### Import formats

| Format | Pairing |
|---|---|
| `.srt`, `.vtt`, `.ass` | One track: each cue is a line. Two files: cues are paired by at least 30% time overlap. |
| `.txt` | One sentence per line or per blank-line block. |
| `.csv` | English in the first column, Persian in the second. Tab, comma and semicolon are all detected. |
| `deck.json` | The native format — see **[docs/DECK_FORMAT.md](docs/DECK_FORMAT.md)**. |

Encoding is detected automatically: BOM, then strict UTF-8, then windows-1256, then
ISO-8859-1.

### Offline vs online

| | `offline` | `online` |
|---|---|---|
| Application id | `ir.laxipatem.lexiflash` | `ir.laxipatem.lexiflash.online` |
| `INTERNET` permission | **absent** | present |
| Deck catalogue | — | yes, button-initiated |
| Machine translation | — | ML Kit, on device, opt-in |
| Everything else | identical | identical |

The offline build contains no HTTP client and no Google service. CI greps its
**merged** manifest on every run and fails the build if a network or storage
permission appears.

### Privacy

See **[PRIVACY.md](PRIVACY.md)**. In one line: no account, no analytics, no ads, no
tracking, and the offline build cannot reach the network even if it wanted to.

### Credits and licence

Code is **Apache 2.0** — see **[LICENSE](LICENSE)** and **[NOTICE.md](NOTICE.md)**.

Bundled sentences come from **[Tatoeba](https://tatoeba.org)** under
**CC BY 2.0 FR**, with portions under **CC0 1.0**. Per-sentence attribution is in
`docs/CREDITS.csv`. No subtitle, transcript or film dialogue is used.

The sample deck is 30 sentence pairs written for this project and licensed
Apache 2.0.

*Not affiliated with TED, Interstellar or any rights holder.*

### Documentation

| File | Contents |
|---|---|
| [docs/DECK_FORMAT.md](docs/DECK_FORMAT.md) | The `deck.json` schema, field by field |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Every judgement call, with reasons |
| [docs/REVIEW_NEEDED.md](docs/REVIEW_NEEDED.md) | What still needs a human before release |
| [docs/STORE_CHECKLIST.md](docs/STORE_CHECKLIST.md) | Play Store submission, step by step |
| [NOTICE.md](NOTICE.md) | Attribution for data and libraries |
| [PRIVACY.md](PRIVACY.md) | What is and is not stored |

---

## فارسی

### این چیست؟

یک اپلیکیشن فلش‌کارت که انگلیسی را با **جمله‌های کامل** آموزش می‌دهد، نه با کلمه‌های
تک‌افتاده. یک طرف کارت جمله‌ی انگلیسی است و طرف دیگر ترجمه‌ی فارسی آن. کارت‌ها
خوانده می‌شوند، آزمون داده می‌شوند و سپس با الگوریتم FSRS-5 زمان‌بندی می‌شوند؛ یعنی هر
کارت درست پیش از آن‌که فراموشش کنید دوباره ظاهر می‌شود.

همه‌چیز بدون اینترنت و بدون حساب کاربری کار می‌کند. ورود به سیستم، تبلیغات، آمارگیری و
اشتراک وجود ندارد.

### امکانات

| | |
|---|---|
| **فلش‌کارت جمله‌ای** | کارت سه‌بُعدی با جمله‌ی انگلیسی، آوانگاری و ترجمه‌ی فارسی. برای دیدن ترجمه لمس کنید. |
| **زمان‌بندی FSRS-5** | نوزده وزن، هدف نگه‌داشت ۹۰ درصد، پایداری و دشواری جداگانه برای هر کارت. |
| **آزمون** | سه نوع پرسش — معنا، برعکس و جای‌خالی — که روی خود دستگاه ساخته می‌شود. با کسب ۸۰ درصد نمره، بخش بعدی باز می‌شود. |
| **پیشرفت** | زنجیره‌ی روزهای پیوسته، هدف روزانه، دقت، و وضعیت هر بخش. |
| **خواندن با صدا** | جمله‌ی انگلیسی را می‌خواند و اگر صدای انگلیسی نصب نباشد، پیام نصب نشان می‌دهد. |
| **ورود محتوا** | فایل خودتان را بیاورید: `.srt`، `.vtt`، `.ass`، `.txt`، `.csv` یا `deck.json` از طریق انتخابگر فایل سیستم. |
| **خروجی** | هر دسته می‌تواند به‌صورت `deck.json` ذخیره و به اشتراک گذاشته شود. |
| **دو زبان** | فارسی به‌صورت پیش‌فرض و انگلیسی به‌صورت انتخابی، با چیدمان کامل راست‌به‌چپ و فونت وزیرمتن برای متن فارسی و لاتین. |
| **دو نسخه** | نسخه‌ی `offline` هیچ دسترسی اینترنتی ندارد. نسخه‌ی `online` فهرست دسته‌ها و ترجمه‌ی ماشینی افزوده را دارد. |

### ساخت از روی کد

پیش‌نیازها: **JDK 17**، **Android SDK** با پلتفرم ۳۴، و **Gradle 8.7**.

این مخزن فقط متن است و بنابراین فایل `gradle-wrapper.jar` ندارد. یا از Gradle نصب‌شده
استفاده کنید، یا یک بار برای خودتان wrapper بسازید:

```bash
gradle wrapper --gradle-version 8.7
```

سپس:

```bash
gradle :app:assembleOfflineDebug     # نسخه‌ی آفلاین
gradle :app:assembleOnlineDebug      # نسخه‌ی آنلاین
gradle :app:testOfflineDebugUnitTest # آزمون‌ها
```

خروجی در مسیر `app/build/outputs/apk/` قرار می‌گیرد. اگر پروژه را روی GitHub بگذارید،
بخش **Actions** همه‌ی این کارها را خودکار انجام می‌دهد و فایل‌های APK را آماده می‌کند.

### ساختن دسته‌ی جمله‌ها

دسته‌ی همراه برنامه یک محصول ساخت است، نه فایلی که در مخزن نگه داشته شود. برای
ساختن دوباره‌ی آن از داده‌های Tatoeba:

```bash
python -m venv .venv && .venv/bin/pip install -r tools/requirements.txt
python tools/build_deck.py --out app/src/main/assets/content/core_deck.json
python -m pytest tools -q
```

اسکریپت سه فایل کوچک «بر‌اساس‌زبان» را از Tatoeba می‌گیرد، جهت ستون‌های فایل پیوند را
در زمان اجرا بررسی می‌کند، جمله‌های انگلیسی را با ترجمه‌های **انسانی** جفت می‌کند و
حدود ۳۰۰۰ کارت در ۱۰ بخش می‌نویسد. اگر تعداد کارت‌ها کمتر از ۳۰۰ باشد یا بخشی کمتر از
۳۰ کارت داشته باشد، ساخت را متوقف می‌کند.

اگر دسته ساخته نشود، برنامه از نسخه‌ی نمونه با ۳۰ کارت استفاده می‌کند؛ پس همیشه چیزی
برای مطالعه وجود دارد.

### حریم خصوصی

به **[PRIVACY.md](PRIVACY.md)** نگاه کنید. به‌طور خلاصه: بدون حساب کاربری، بدون تبلیغات،
بدون آمارگیری و بدون ردگیری. نسخه‌ی آفلاین حتی دسترسی اینترنت هم ندارد.

### پروانه و اعتبارها

کد زیر پروانه‌ی **Apache 2.0** منتشر می‌شود؛ فایل‌های **[LICENSE](LICENSE)** و
**[NOTICE.md](NOTICE.md)** را ببینید.

جمله‌های همراه برنامه از **[Tatoeba](https://tatoeba.org)** و زیر پروانه‌ی
**CC BY 2.0 FR** (و بخشی **CC0 1.0**) هستند. اعتبار هر جمله در `docs/CREDITS.csv`
آمده است. هیچ زیرنویس، رونوشت فیلم یا گفتار سریالی استفاده نشده است.

*هیچ وابستگی به TED، اینتراستلار یا هیچ دارنده‌ی حقوق دیگری وجود ندارد.*

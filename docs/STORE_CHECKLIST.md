# Google Play release checklist

Ordered so that anything that can block a release is detected as early as
possible. Items marked **[repo]** are already handled in this repository.

---

## 1. Before the first upload

### 1.1 Identity

- [ ] **[repo]** `applicationId` is `ir.laxipatem.lexiflash`.
- [ ] **[repo]** `namespace` matches the application id.
- [ ] **[repo]** `versionCode = 1`, `versionName = "1.0.0"`.
- [ ] Decide whether the domain `laxipatem.ir` is actually owned by the publisher.
      If it is not, the application id still works (Play does not verify ownership),
      but it is misleading and cannot be changed after the first upload. **This is
      the single least reversible decision in the project.**
- [ ] The `online` flavour gets `.online` appended to both the id and the version
      name. Confirm whether both flavours should be published, or only `offline`,
      with `online` distributed separately as an APK.

### 1.2 SDK levels

- [ ] **[repo]** `minSdk = 24` (Android 7.0).
- [ ] **[repo]** `targetSdk = 34`.
- [ ] Play requires a recent target SDK. At the time of writing, new apps must
      target 34. Re-check the current requirement before uploading; it moves every
      August.

### 1.3 Signing

- [ ] Generate an upload keystore (**never commit it**).
- [ ] Add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` as
      repository secrets.
- [ ] Run the Release workflow and confirm the APK verifies:
      `apksigner verify --print-certs app-offline-release.apk`.
- [ ] Enrol in Play App Signing during the first upload.

---

## 2. Store listing assets

These are **not** in the repository: it is text-only. Produce them separately.

- [ ] App title, max 30 characters: `LexiFlash Persian`.
- [ ] Short description, max 80 characters. Suggested:
      *"English to Persian flashcards with spaced review. Works offline."*
- [ ] Full description, max 4000 characters. Mention: offline-first, FSRS
      scheduling, six import formats, TTS, no ads, no tracking, and the
      attribution to Tatoeba.
- [ ] App icon, 512×512 PNG, 32-bit. Must match the in-app `ic_launcher`.
- [ ] Feature graphic, 1024×500 PNG.
- [ ] At least two phone screenshots; four or more is better. Show Home, a
      flashcard mid-study, a quiz question and the progress screen. Take them in
      **both** languages if both locales are offered.
- [ ] Localise the listing into Persian (`fa`) as well as English. Having the app
      translated but the store page English-only loses the audience that needs it.

---

## 3. Content and legal

- [ ] **[repo]** `LICENSE` is the full Apache 2.0 text.
- [ ] **[repo]** `NOTICE.md` credits Tatoeba with both licences and names every
      third-party component.
- [ ] **[repo]** `PRIVACY.md` exists and is accurate.
- [ ] Host the privacy policy at a public HTTPS URL. Play requires a link; a
      markdown file in a repository is not enough. GitHub Pages works.
- [ ] **[repo]** The in-app disclaimer is present verbatim.
- [ ] Confirm no screenshot, listing image or in-app text mentions a film or a talk
      beyond the required disclaimer sentence.
- [ ] Regenerate `docs/CREDITS.csv` alongside the deck and commit both together, so
      attribution always matches the shipped sentences.

### 3.1 Data safety form

Play will ask. Answer, based on `PRIVACY.md`:

- [ ] *Does your app collect or share any user data?* **No.**
- [ ] *Is data encrypted in transit?* **Yes** (HTTPS only; cleartext disabled).
- [ ] *Do you provide a way for users to request data deletion?* **Yes** —
      Settings → Delete all data, and uninstalling removes everything.
- [ ] Note the ML Kit model download in the free-text box: the model file is
      fetched from Google, but user content is never uploaded.

### 3.2 Permissions declaration

- [ ] `offline` declares no permissions. Nothing to declare.
- [ ] `online` declares `INTERNET` only. This is not a sensitive permission, so no
      separate form is required.

---

## 4. Policy compliance specific to this app

- [ ] **No ads, no analytics, no trackers** — already true; keep it that way.
- [ ] **Families policy**: not targeted at children; the listing makes no claim to
      be. The app collects nothing, so it would be compliant either way.
- [ ] **Sensitive content**: none.
- [ ] **Repetitive content / minimum functionality**: the app has distinct
      functionality (spaced review with a real scheduler), so this is fine. Make
      sure the listing describes it as a study tool, not as a "content container".

---

## 5. Build and upload

- [ ] `gradle :app:testOfflineDebugUnitTest` passes.
- [ ] `gradle :app:assembleOfflineRelease` and `assembleOnlineRelease` pass.
- [ ] The `offline` release merged manifest has no `INTERNET` permission (CI checks
      this).
- [ ] Upload the `.aab`, not the `.apk`, to Play.
- [ ] Use a staged rollout (10% → 50% → 100%) on the first production release.

---

## 6. After release

- [ ] Tag the commit `v1.0.0` so the Release workflow attaches the artefacts.
- [ ] Watch the pre-launch report for crashes on the device matrix.
- [ ] Keep `versionCode` increasing monotonically; Play rejects a repeated one.
- [ ] If Tatoeba is refreshed, bump the deck `version` so installed apps reseed
      (`ensureCoreDeck` compares the stored version), and regenerate
      `CREDITS.csv`.

---

## 7. Things that will bite later

| Risk | Mitigation |
|---|---|
| The application id contains a domain that may not be owned. | Decide now; it cannot change later. |
| `targetSdk 34` goes stale. | Bump yearly; the Play deadline moves every August. |
| The deck is a build artefact not in Git. | Add a scheduled workflow that rebuilds it weekly and opens a PR when it changes. |
| Persian translations have had no native review. | See `docs/REVIEW_NEEDED.md`. |
| The catalogue URL is a placeholder. | Publish a real catalogue or remove the entry point. |

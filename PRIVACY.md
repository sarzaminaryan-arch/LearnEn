# Privacy policy — LexiFlash Persian

Last updated: 2026-11-25

## The short version

**The offline build never touches the network.** The online build touches it only
when you press a button that says so. No account, no analytics, no advertising, no
tracking, no crash reporting, nothing sent to the developer.

There is no server. The developer of this app has no way to see your data.

---

## What is stored on your device

Everything the app remembers stays in its private storage, readable only by the
app itself:

| Data | Where | Why |
|---|---|---|
| Your decks and their sentences | Room database | They are the study material. |
| Your review history and FSRS memory state | Room database | Needed to schedule the next review. |
| Quiz scores | Room database | Needed to decide which parts are unlocked. |
| Settings (theme, language, daily goal, speech speed) | DataStore preferences | So the app behaves the way you configured it. |

Uninstalling the app deletes all of it. Android's own backup is disabled
(`allowBackup="false"`), so this data is not copied to Google Drive or anywhere
else.

### What is *not* collected

* No name, email address or phone number.
* No advertising identifier and no other device identifier.
* No location, contacts, calendar, photos, microphone or camera access.
* No usage analytics, session recordings, or crash reports.
* No list of installed apps.

There are no third-party analytics, advertising or tracking libraries in either
flavour. That is a deliberate design constraint, not an accident.

---

## Permissions

### Offline flavour

**None.** The offline build declares no permissions at all. It does not even hold
the `INTERNET` permission, which the build verifies mechanically on every CI run.
The absence of the permission means the operating system would refuse a network
call even if a bug tried to make one.

### Online flavour

| Permission | Why | When |
|---|---|---|
| `INTERNET` | Download a deck catalogue you asked for, download the on-device translation model, and fetch decks you choose. | Only after you tap the relevant button. |

No storage permission is requested in either flavour. File import uses the Android
Storage Access Framework: the system file picker grants the app read access to the
single file you select, and nothing more.

---

## Network activity in the online flavour

The online build has exactly three network features. Each one needs a deliberate
tap:

1. **Deck catalogue.** Opening the catalogue screen shows a button. Nothing is
   fetched until you press it. The app then requests a JSON index from the HTTPS
   address configured in Settings, and later the specific deck you select.
2. **On-device machine translation.** When an imported deck has sentences without
   Persian translations, you may ask the app to translate them. The first time, it
   downloads Google's ML Kit English–Persian model (roughly 30 MB). Translation
   then runs **on your device**; the sentences themselves are not uploaded to
   Google by this app.
3. **Nothing else.**

The catalogue URL is user-configurable but must start with `https://`. Cleartext
traffic is disabled in every build via the network security configuration.

### What a network request reveals to a third party

As with any download over the internet, the server you connect to learns the usual
technical facts: your IP address, the time of the request, and what was requested.
For ML Kit model downloads, that server is operated by Google and is subject to
Google's own privacy policy. The app sends no personal identifiers along with
these requests.

---

## Text-to-speech

Reading a sentence aloud uses the speech engine already installed on your device
(typically Google's or your manufacturer's). The sentence text is handed to that
engine through Android's standard API. What the engine does with it is governed by
the engine's own privacy policy, which the app cannot control. If you never tap a
speaker button, nothing is sent.

---

## Children

The app is a language-learning tool with no user-generated content sharing, no
chat, and no data collection. It is suitable for general audiences. Because it
collects nothing, it collects nothing from children either.

---

## Your control over the data

* **Delete everything:** Settings → *Delete all data* removes every deck, card,
  review and score, and resets the bundled deck. This cannot be undone.
* **Export a copy:** Progress screen → *Export deck* writes a standard `deck.json`
  file to the app's cache and offers it to the share sheet. You choose where it
  goes; the app never uploads it.
* **Remove machine translations:** they are stored in the normal deck file, so
  deleting the deck deletes them.
* **Uninstall:** removes everything.

---

## Changes to this policy

If the app ever gains a feature that sends data anywhere, this document will be
updated before that feature ships, and the change will be visible in the
repository history at
<https://github.com/sarzaminaryan-arch/LearnEn/commits/main/PRIVACY.md>.

---

## Contact

Questions about privacy can be raised as an issue in the project repository:
<https://github.com/sarzaminaryan-arch/LearnEn/issues>

---

Not affiliated with TED, Interstellar or any rights holder.

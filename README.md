# ShieldXMobile — Android SMS Scam Pattern Protection

ShieldXMobile is a Kotlin Android application that screens SMS messages with an offline trained text-classification model plus a transparent rule-based fallback. It supports manual scans and, when the user enables Auto Protection and grants the SMS permission, incoming SMS screening.

> **Important:** ShieldX uses an on-device TF-IDF + Logistic Regression model (ham, spam, smishing) and supplementary keyword rules. Model scores are screening signals, not calibrated real-world probabilities or guarantees. Messages marked low risk can still be fraudulent, and legitimate messages can be flagged.

## Features

- Local 4-digit app PIN gate.
- Salted PIN hashing and persisted failed-attempt lockout.
- Biometric/device-credential authorization for PIN reset.
- Manual SMS text screening with the trained on-device model.
- Optional incoming SMS screening after the user enables Auto Protection and grants permission.
- Three-class offline classification: legitimate (ham), spam and smishing.
- Rule-based heuristic fallback if the trained model asset is missing or cannot load.
- SMS text is classified locally; no message content is sent to a remote model API.
- Multipart SMS handling.
- Local scan history with a bounded 100-item history list.
- High-risk notifications that do not display the message body or OTP.
- English, Hindi and Tamil language selection.
- Light/dark theme settings.

## Direct APK download

[**Download ShieldXMobile – Android APK**](https://api.github.com/repos/Kamleshkumarmandal/ShieldXMobile/actions/artifacts/14759973425/zip)

The download is a ZIP archive containing `app-debug.apk`. Extract the ZIP, then install `app-debug.apk` on an Android device. This debug build passed the Android unit tests and APK build in [GitHub Actions](https://github.com/Kamleshkumarmandal/ShieldXMobile/actions/runs/45037675054).

**Download availability:** This GitHub Actions artifact is retained until **December 6, 2026**. For later builds, open the [latest Android CI runs](https://github.com/Kamleshkumarmandal/ShieldXMobile/actions/workflows/android-ci.yml) and download the `ShieldXMobile-debug-apk` artifact from a successful run.

## Security and reliability changes

- The main dashboard activity is non-exported, so another app cannot launch it to bypass the PIN screen.
- PINs are stored as salted PBKDF2-HMAC-SHA256 hashes. Existing plaintext PINs are migrated after a successful PIN entry.
- Failed PIN attempts are persisted; three failed attempts require biometric/device authentication to reset.
- A one-time random token stored in private app preferences authorizes a PIN reset only after a successful device-authentication flow. A caller-supplied intent extra by itself cannot reset the PIN.
- Private app data backup is disabled because local preferences contain the PIN hash and message history.
- Unnecessary `READ_SMS` and foreground-service permissions have been removed. The app only receives the protected incoming-SMS broadcast; it does not read the SMS inbox.
- Internal scan updates are package-scoped and delivered to a dynamically registered non-exported receiver.
- Multipart SMS messages are combined before scanning.
- Background SMS processing respects the opt-in Auto Protection setting.
- Scan history uses one shared preference key and is bounded to 100 items.
- The message body is not included in scam-alert notifications.

## Scam screening behavior

The primary detector is a compact TF-IDF + multinomial Logistic Regression model trained to distinguish ham, spam and smishing SMS. It runs on-device and uses a packaged JSON model (vocabulary, IDF weights and classifier coefficients). The app also runs the transparent heuristic rules as supplementary signals and uses them as a fallback if the model asset cannot be loaded.

The supplementary rules look for combinations of:
- requests to share an OTP, PIN, password or security code;
- urgent account, KYC or verification actions;
- prize/reward/lottery claims;
- URL links, shortened links and selected higher-risk URL suffixes.

The app displays the model's predicted class and score together with matched rule categories. Model scores are not calibrated probabilities. The app does not fetch URLs, visit suspicious links, contact a reporting database, or block numbers at the carrier/network level. Menu items for reporting or blocking are not connected to a global database or network-level blocking service.

## Build locally

Requirements:
- Android Studio with Android SDK Platform 34
- JDK 17
- Gradle wrapper included in this repository

Open the project folder in Android Studio and allow Gradle sync to finish.

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

macOS / Linux:

```bash
bash ./gradlew testDebugUnitTest
bash ./gradlew assembleDebug
```

The debug APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`

## Permissions and distribution

- **RECEIVE_SMS:** Required only for optional incoming-SMS Auto Protection.
- **POST_NOTIFICATIONS:** Required on Android 13+ to show notifications.

The app does not request `READ_SMS`, contacts, call-log, location, microphone or camera access.

SMS permissions are restricted by Google Play policy. Before publishing through Google Play, verify that the app's core purpose qualifies for the applicable SMS permission policy or use an approved distribution path. A permission declared in the manifest does not guarantee store approval.

## Automated checks

GitHub Actions runs:
- Kotlin/Android unit tests;
- Android debug APK assembly;
- SMS model training, holdout evaluation and JSON model export (GitHub Actions workflow `Train SMS model`).

The current training run cleaned 5,971 source rows to 5,797 unique, non-conflicting messages. On a stratified 20% holdout (1,160 messages), the model achieved 96.9% accuracy and 90.2% macro-F1. Class F1 was 98.9% for ham, 90.9% for smishing and 80.7% for spam. These are results on this dataset's holdout split, not independent real-world validation. The evaluation report is in `ml/generated/evaluation.json`.

## Privacy notes

Scan history and PIN-verification material are stored locally in app-private preferences. Backup is disabled. SMS text is processed on-device by the packaged ML model and heuristic rules; message contents are not sent to a remote API. The raw training dataset is downloaded only during the training workflow and is not committed to this app repository.

## Limitations

- The training corpus is primarily English; Hindi, Tamil and other language predictions may be less reliable even though the app UI supports those languages.
- Dataset holdout metrics may not reflect current or region-specific scam campaigns.
- Model and keyword heuristics can miss new scam patterns and can produce false positives.
- Sender reputation and link-domain reputation are not checked online.
- No carrier-level spam blocking or global reporting service is connected.
- SMS receiver behavior depends on Android permission state, device/OS behavior and distribution policy.
- The PIN is an additional local app gate, not a replacement for Android device security.

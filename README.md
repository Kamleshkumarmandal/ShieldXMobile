# ShieldXMobile — Android SMS Scam Pattern Protection

ShieldXMobile is a Kotlin Android application that lets a user manually screen SMS text and, when the user enables Auto Protection and grants the SMS permission, inspect incoming SMS messages for suspicious wording and links.

> **Important:** The current detector is a transparent keyword/rule-based heuristic, not a trained AI model. Its 0–100 score is a screening signal, not a probability or guarantee. Messages marked low risk can still be fraudulent, and legitimate messages can be flagged.

## Features

- Local 4-digit app PIN gate.
- Salted PIN hashing and persisted failed-attempt lockout.
- Biometric/device-credential authorization for PIN reset.
- Manual SMS text screening.
- Optional incoming SMS screening after the user enables Auto Protection and grants permission.
- Multipart SMS handling.
- Local scan history with a bounded 100-item history list.
- High-risk notifications that do not display the message body or OTP.
- English, Hindi and Tamil language selection.
- Light/dark theme settings.

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

The current rules look for combinations of:
- requests to share an OTP, PIN, password or security code;
- urgent account, KYC or verification actions;
- prize/reward/lottery claims;
- URL links, shortened links and selected higher-risk URL suffixes.

The app displays matched rule categories to make the score more transparent. It does not fetch URLs, visit suspicious links, contact a reporting database, block numbers at the carrier/network level, or use a trained ML model. Menu items for reporting or blocking are not connected to a global database or network-level blocking service.

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
- Android debug APK assembly.

Unit tests cover PIN hash behavior and the rule-based scam screening logic. They do not prove the heuristic can identify every scam or that a specific SMS is safe.

## Privacy notes

Scan history and PIN-verification material are stored locally in app-private preferences. Backup is disabled. SMS text is processed on-device by the current rule-based detector; the current implementation does not send message contents to a remote API.

## Limitations

- Keyword heuristics can miss new scam patterns and can produce false positives.
- Sender reputation and link-domain reputation are not checked online.
- No carrier-level spam blocking or global reporting service is connected.
- SMS receiver behavior depends on Android permission state, device/OS behavior and distribution policy.
- The PIN is an additional local app gate, not a replacement for Android device security.

# Aviator Predictor - Android APK

Native Kotlin Android app with Jetpack Compose. Identical signal calculation
logic to the web app. Uses Google Sign-In + Google Drive for all storage.

---

## Directory Structure

```
aviator-android/
├── app/
│   ├── build.gradle.kts          ← app module, dependencies, signing config
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/aviator/predictor/
│       │   ├── MainActivity.kt           ← Scaffold, nav bar, top bar
│       │   ├── ui/
│       │   │   ├── MainViewModel.kt      ← all state, Drive sync, auto-mark
│       │   │   ├── theme/Theme.kt        ← purple/pink colours matching web
│       │   │   ├── components/
│       │   │   │   └── SharedComponents.kt
│       │   │   └── screens/
│       │   │       ├── SignInScreen.kt
│       │   │       ├── HomeScreen.kt
│       │   │       ├── GenerateScreen.kt
│       │   │       ├── ResultsScreen.kt
│       │   │       └── ProfileScreen.kt
│       │   ├── data/
│       │   │   ├── models/Models.kt      ← Signal, UserProfile, AppSettings…
│       │   │   └── repository/
│       │   │       ├── AuthRepository.kt ← Google Sign-In via Credential Manager
│       │   │       └── DriveRepository.kt← all Drive REST API calls
│       │   └── utils/
│       │       ├── TimeCalculations.kt   ← same algorithm as JS web app
│       │       └── Formatters.kt
│       └── res/…
├── .github/workflows/
│   ├── build-apk.yml   ← builds signed APK, creates GitHub Release
│   └── deploy-web.yml  ← builds web app, deploys to GitHub Pages
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
└── settings.gradle.kts
```

---

## One-time Setup

### 1. Google Cloud Console

1. Go to https://console.cloud.google.com
2. Create a new project (or use existing)
3. Enable these APIs:
   - Google Drive API
   - Google Identity Services
4. OAuth consent screen → External → fill in app name/email
5. Credentials → Create OAuth 2.0 Client ID:
   - Type: **Web application** → copy the **Client ID** (used as `GOOGLE_WEB_CLIENT_ID`)
   - Type: **Android** → enter your package `com.aviator.predictor`
     and your debug/release SHA-1 fingerprints
6. Add scope: `https://www.googleapis.com/auth/drive.appdata`

Get your SHA-1:
```bash
# Debug keystore
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
        -storepass android -keypass android

# Release keystore
keytool -list -v -keystore your-release.jks -alias your-alias
```

### 2. Create a Release Keystore

```bash
keytool -genkey -v \
  -keystore aviator-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias aviator \
  -dname "CN=Aviator Predictor, OU=Dev, O=YourOrg, L=City, S=State, C=US"

# Base64-encode for GitHub secret
base64 -i aviator-release.jks | tr -d '\n'
```

### 3. GitHub Repository Secrets

Go to repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret name          | Value                                              |
|----------------------|----------------------------------------------------|
| `KEYSTORE_BASE64`    | Base64-encoded keystore file (from step above)     |
| `KEYSTORE_PASSWORD`  | Keystore password                                  |
| `KEY_ALIAS`          | Key alias (e.g. `aviator`)                         |
| `KEY_PASSWORD`       | Key password                                       |
| `GOOGLE_WEB_CLIENT_ID` | OAuth Web Client ID from Google Cloud Console    |
| `GOOGLE_CLIENT_ID`   | Same value (used by web app workflow)              |
| `GDRIVE_CREDENTIALS` | (Optional) Service account JSON base64 for APK upload to Drive |
| `GDRIVE_FOLDER_ID`   | (Optional) Google Drive folder ID for APK uploads  |

### 4. app/build.gradle.kts — add Coil for photo loading

Add this line to `dependencies {}`:
```kotlin
implementation("io.coil-kt:coil-compose:2.7.0")
```
And add to `libs.versions.toml`:
```toml
[versions]
coil = "2.7.0"

[libraries]
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
```

---

## Building Locally

```bash
# Debug APK (no signing needed)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (requires env vars)
export KEYSTORE_PATH=/path/to/aviator-release.jks
export KEYSTORE_PASSWORD=yourpassword
export KEY_ALIAS=aviator
export KEY_PASSWORD=yourpassword
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

---

## Triggering a GitHub Release

```bash
git tag v2.0.0
git push origin v2.0.0
```

This triggers `build-apk.yml` which:
1. Decodes your keystore from secrets
2. Injects the Google Client ID
3. Builds a signed release APK
4. Creates a GitHub Release with the APK attached
5. (Optional) Uploads the APK to Google Drive

---

## How Google Drive Storage Works

All user data is stored in the **appDataFolder** — a hidden, private folder
in the user's Google Drive that only your app can see.

Three files are managed:
- `aviator_signals.json` — array of all Signal objects
- `aviator_profile.json` — UserProfile with display name, photo URL, stats
- `aviator_settings.json` — AppSettings (notifications, sound, etc.)

The `DriveRepository` finds or creates these files on first run,
then reads/writes them using the Drive REST API with the user's OAuth token.

---

## Signal Algorithm

The Kotlin code in `TimeCalculations.kt` implements the exact same algorithm
as the JavaScript `timeCalculations.js` in the web app:

```
odd = 2.02
addTime = oddToTime(2.02)  →  { hours:0, minutes:2, seconds:2 }
inputTime = 21:31:22       →  21*3600 + 31*60 + 22 = 77482s
totalSecs = 77482 + 122 = 77604s
resultTime = 21:33:24
betWindow opens  = 21:32:39  (45s before)
betWindow closes = 21:34:09  (45s after)
```

Auto-mark missed fires when countdown < -55s (45s grace + 10s buffer).

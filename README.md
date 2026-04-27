# SecureBank Android — Fingerprint Pro Mobile Demo

A native Android companion to the [SecureBank web prototype](https://github.com/maneesh-fp/test-bank-prototype), demonstrating [Fingerprint Pro](https://fingerprint.com) device intelligence in a mobile banking UI.

The app replicates the full web login flow — Fingerprint identification → sealed result forwarding → server-side decryption — but natively on Android using the Fingerprint Pro Android SDK.

---

## Architecture

```
┌─────────────────────────────────┐        ┌──────────────────────────────┐
│       Android App (this repo)   │        │  Node.js Backend             │
│                                 │        │  (test-bank-prototype repo)  │
│  LoginActivity                  │        │                              │
│   1. Fingerprint Android SDK    │──────▶ │  POST /api/login             │
│      getVisitorId()             │        │   • Unseal AES-256-GCM blob  │
│      → sealedResult + requestId │        │   • Call Fingerprint Server  │
│                                 │        │     API (/events/:id)        │
│   2. POST /api/login            │◀────── │   • Return visitorId         │
│      { credential, password,    │        │                              │
│        sealedResult, requestId }│        └──────────────────────────────┘
│                                 │
│  DashboardActivity              │
│   • Displays account overview   │
│   • Shows visitorId from server │
└─────────────────────────────────┘
```

**The Android app is frontend-only.** It never decrypts the sealed result — that happens exclusively on the Node.js backend. The `sealedResult` is treated as an opaque blob passed straight to the server.

---

## Screens

| Screen | Description |
|--------|-------------|
| **Login** | SecureBank-themed login with Customer ID / Mobile Number tab switcher, password toggle, input validation, and device fingerprinting on submit |
| **Dashboard** | Post-login overview with account summary, quick actions, recent transactions, deposits & loans, and service requests — matching the web dashboard layout |

---

## Fingerprint Integration

| Step | What happens |
|------|-------------|
| User taps **Sign In Securely** | Input validation runs first |
| `fpClient.getVisitorId()` | Fingerprint Android SDK contacts the AP region endpoint |
| SDK returns | `sealedResult` (AES-256-GCM encrypted blob) + `requestId` |
| App POSTs to `/api/login` | `{ credential, password, sealedResult, requestId }` sent to local Node server |
| Server decrypts | Unseals result, extracts `visitorId`, calls Fingerprint Server API, logs signals (bot, VPN, proxy, suspect score) |
| Server responds | `{ success: true, visitorId: "..." }` |
| App navigates | Opens `DashboardActivity` with credential + visitorId |

> **Sealed Client Results are enabled** on this workspace. `visitorId` is never visible client-side — it is only available after server-side decryption.

---

## Project Structure

```
app/src/main/
├── java/com/example/testfingerprintapp/
│   ├── LoginActivity.kt        # Fingerprint SDK init, validation, POST /api/login, navigation
│   └── DashboardActivity.kt   # Post-login dashboard, receives credential + visitorId
│
└── res/
    ├── layout/
    │   ├── activity_login.xml       # Hero section + login card
    │   ├── activity_dashboard.xml   # Full dashboard layout
    │   ├── item_transaction.xml     # Recent transactions list items
    │   ├── item_deposit.xml         # Deposits & loans list items
    │   └── item_service_request.xml # Service request list items
    ├── values/
    │   ├── colors.xml    # SecureBank brand palette (#003c7e navy, #f7941d orange)
    │   ├── strings.xml
    │   ├── themes.xml    # NoActionBar Material Components theme
    │   └── styles.xml    # Quick action button styles
    └── drawable/         # Shape drawables, vector icons, backgrounds
```

---

## Setup

### Prerequisites
- Android Studio Meerkat or later
- Android SDK API 24+ (minSdk) / API 36 (targetSdk)
- The [SecureBank Node.js backend](https://github.com/maneesh-fp/test-bank-prototype) running locally on port 3000

### 1. Clone and open
```bash
git clone https://github.com/maneesh-fp/mobile-bank-prototype.git
```
Open in Android Studio and let Gradle sync.

### 2. Configure secrets — `local.properties`

`local.properties` is **gitignored** and must be created manually on each machine. Add your keys below the auto-generated `sdk.dir` line:

```properties
# DO NOT commit this file
FINGERPRINT_API_KEY=your_fingerprint_public_api_key
SERVER_BASE_URL=http://10.0.2.2:3000
```

> `10.0.2.2` is the Android emulator's alias for `localhost` on the host machine.  
> For a physical device on the same Wi-Fi, replace with your Mac's local IP (e.g. `192.168.x.x`).

These values are injected at compile time via `BuildConfig` — they never appear in committed code.

### 3. Start the backend
```bash
cd path/to/test-bank-prototype
node decrypt.js
```

### 4. Run the app
Select an emulator (API 24+) or physical device in Android Studio and press **▶ Run**.

---

## Credentials

Any `credential` / `password` combination is accepted — the Node server does not enforce authentication, it focuses on Fingerprint signal logging. Use `user1` / `password` to match the web demo.

---

## Key Dependencies

| Library | Purpose |
|---------|---------|
| `com.fingerprint.android:pro:2.14.0` | Fingerprint Pro Android SDK |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `lifecycleScope` for coroutines |
| `com.squareup.okhttp3:okhttp:4.12.0` | HTTP client for `/api/login` call |
| `com.google.android.material` | Material Components UI |

---

## Security Notes

- **No secrets in source.** All API keys live in `local.properties` (gitignored) and are compiled into `BuildConfig` — never in code or resources.
- **Sealed Client Results only.** `visitorId` is never exposed to the Android client. The app forwards the encrypted `sealedResult` blob to the backend for decryption.
- **HTTP allowed for `10.0.2.2` only.** A `network_security_config.xml` permits cleartext traffic exclusively to the local dev server. All other traffic requires HTTPS.
- **Backend handles all trust decisions.** Bot detection, VPN/proxy flags, suspect scores, and visitor history are evaluated server-side only.

---

## Related

- **Web prototype + Node backend:** [maneesh-fp/test-bank-prototype](https://github.com/maneesh-fp/test-bank-prototype)
- **Fingerprint Android SDK docs:** [docs.fingerprint.com/docs/android-sdk](https://docs.fingerprint.com/docs/android-sdk)
- **Fingerprint Pro dashboard:** [dashboard.fingerprint.com](https://dashboard.fingerprint.com)

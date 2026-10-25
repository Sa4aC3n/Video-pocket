# Google Play Console — Data Safety Form Answers
## (إقرارات أمان البيانات لمتجر جوجل بلاي)

Use this guide when filling out the **Data safety** section in the Google Play Console for **Video Pocket** (`org.videopocket.probe`).

---

### 1. Data Collection and Security

| Question | Recommended Answer | Explanation |
| :--- | :---: | :--- |
| **Does your app collect or share any of the required user data types?** | **No** | Video Pocket is a local-first utility. No user data, analytics, IDs, or telemetry are collected or uploaded to any remote server. |
| **Is all user data collected by your app encrypted in transit?** | **Yes** | Any network connection initiated by user action (downloading files or SponsorBlock API) uses HTTPS / TLS encryption. |
| **Do you provide a way for users to request that their data be deleted?** | **Yes (N/A — Local Only)** | Since no data is stored on remote servers, users delete all their data simply by clearing app storage or uninstalling the app. |

---

### 2. Specific Data Types Breakdown

| Category | Data Type | Collected? | Shared? | Purpose |
| :--- | :--- | :---: | :---: | :--- |
| **Location** | Approximate or Precise Location | **No** | **No** | App does not request or access location. |
| **Personal Info** | Name, Email, Address, Phone, User IDs | **No** | **No** | No accounts, logins, or user profiles. |
| **Financial Info** | Credit Card, Bank Info, Purchase History | **No** | **No** | No in-app purchases (IAP) or billing. |
| **Health and Fitness** | Fitness, Health Data | **No** | **No** | N/A |
| **Messages** | Emails, SMS, In-app Messages | **No** | **No** | N/A |
| **Photos and Videos** | Photos, Videos | **No** | **No** | Processed locally on-device. Never uploaded. |
| **Audio Files** | Voice recordings, Music, Sound files | **No** | **No** | Processed locally on-device. Never uploaded. |
| **Files and Docs** | Files and Docs | **No** | **No** | Saved locally in `Download/VideoPocket`. |
| **Calendar** | Calendar events | **No** | **No** | N/A |
| **Contacts** | Contact book | **No** | **No** | N/A |
| **App Activity** | Page views, Clicks, In-app search | **No** | **No** | No analytics SDK embedded. |
| **Web Browsing** | Web browsing history | **No** | **No** | No WebView or internal browser telemetry. |
| **App Info and Performance** | Crash logs, Diagnostics, Performance metrics | **No** | **No** | No Crashlytics, Sentry, or Bugsnag SDKs. |
| **Device or other IDs** | IMEI, MAC, Advertising ID (AAID) | **No** | **No** | Zero device fingerprinting or ad IDs. |

---

### 3. Advertising & Monetization Declarations

| Policy Question | Answer |
| :--- | :--- |
| **Does your app contain ads?** | **No** (Select "No, my app does not contain ads"). |
| **Target Audience and Content** | Ages 13 and above. |
| **News Apps Declaration** | **No**, this is not a news app. |
| **COVID-19 Contact Tracing** | **No**. |
| **Government Apps** | **No**, not affiliated with government entities. |
| **Financial Features** | **No**, does not provide financial services. |

---

### 4. Background Services & Permissions Declarations

- **Foreground Service Type:**
  - Declared type: `dataSync` (`android.permission.FOREGROUND_SERVICE_DATA_SYNC`).
  - Justification for Play Console review:
    > "Video Pocket allows users to download and process user-requested media files (video/audio). A dataSync foreground service with ongoing notification is required to ensure large file downloads and audio processing (voice clarification, silence removal) continue uninterrupted when the user switches apps or turns off the screen."
- **Floating Bubble (System Alert Window):**
  - Justification:
    > "Provides an optional floating quick-download shortcut button that users can enable in settings. The app checks Settings.canDrawOverlays at runtime and operates normally without this feature if permission is not granted."

# Privacy Policy for Video Pocket

**Effective Date:** October 25, 2026  
**Last Updated:** October 25, 2026

Video Pocket ("we", "our", or "the app") is committed to protecting your privacy. This Privacy Policy explains our practices regarding user data, permissions, and device storage.

---

### 1. Zero Personal Data Collection (Local-First Architecture)
Video Pocket is designed from the ground up as a **100% local-first media management and utility application**.
- We **do not** collect, store, transmit, or sell any personal data.
- We **do not** require or support user account registration, passwords, phone numbers, or email addresses.
- We **do not** embed third-party advertising SDKs, tracking pixels, behavioral analytics, or marketing telemetry (No Google AdMob, Facebook SDK, Firebase Analytics, or AppsFlyer).

---

### 2. Permissions & How They Are Used
Video Pocket requests only the minimal permissions necessary to execute features you directly trigger:

| Permission | Android Technical Name | Purpose & User Benefit |
| :--- | :--- | :--- |
| **Internet Access** | `android.permission.INTERNET`, `ACCESS_NETWORK_STATE` | Used solely to stream or download media files from URLs you paste into the app. No telemetry or background tracking is ever transmitted. |
| **Notifications** | `android.permission.POST_NOTIFICATIONS` | Used strictly to display real-time download and processing progress in the system notification shade. |
| **Foreground Service** | `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | Keeps downloads and file processing running reliably in the background without being killed prematurely by the OS. |
| **Floating Action Bubble** | `android.permission.SYSTEM_ALERT_WINDOW` | Optional feature. Allows an on-screen floating bubble for quick download shortcuts. The app functions completely without this permission if declined. |
| **Biometric Authentication** | `android.permission.USE_BIOMETRIC` | Used exclusively locally via Android's hardware-backed Keystore (`BiometricPrompt`) to lock and unlock your private encrypted vault. Biometric data never leaves your device's Secure Element. |

---

### 3. File Storage & Media Handling
- All downloaded and processed media files are saved directly into your device's standard public storage directory (`Download/VideoPocket`) via modern Android `MediaStore` APIs.
- Encrypted vault items are encrypted using **AES-256-GCM** with a hardware-backed key generated in Android KeyStore and stored in the app's sandboxed private directory with `.nomedia` protection.
- Original files are never modified or overwritten; processed outputs are always saved as distinct new files.

---

### 4. Third-Party Web Services & External Content
When you paste a link (e.g., from YouTube, SoundCloud, or other public platforms), Video Pocket communicates directly with the hosting server hosting that media to retrieve the requested stream. Video Pocket does not act as an intermediary proxy or retain connection logs.

For SponsorBlock integration, the app queries the public SponsorBlock community API (`https://sponsor.ajay.app`) using the public video identifier to retrieve sponsor segment timestamps. No personal identification or user telemetry is transmitted.

---

### 5. Children's Privacy (COPPA Compliance)
Video Pocket does not target children under the age of 13 and does not collect any personal information from any user, including children.

---

### 6. Changes to This Privacy Policy
If we update this Privacy Policy, the revised version will be published within the repository and application release notes with an updated revision date.

---

### 7. Contact
For questions or feedback regarding this Privacy Policy or Video Pocket:
- **Project Repository:** [https://github.com/Sa4aC3n/Video-pocket](https://github.com/Sa4aC3n/Video-pocket)
- **Developer:** Sa4aC3n

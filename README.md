# PKR Key

Native Android keyboard (Kotlin, `InputMethodService`, minSdk 26) with a dedicated key for the custom Pakistani Rupee symbol image.

- Key press sends the symbol as an image with `InputConnection.commitContent()` (PNG, FileProvider URI, read permission granted to the target app).
- Apps that do not accept images from keyboards: the image is copied to the clipboard (paste it). Long-press the key to open the share sheet.
- No permissions, no internet, no analytics, no ads. Typed text is never stored or sent. Only theme, key height and symbol-key visibility are saved.

## Build the APK

**GitHub Actions (no local SDK needed)**
1. Push this folder to a GitHub repo (branch `main`).
2. Open the repo > Actions > "Build PKR Key APK" > latest run > Artifacts > `PKR-Key-debug-apk`.
3. Or push a tag like `v1.0` to get `PKR-Key-debug.apk` attached to a GitHub release.

**Android Studio**
Open this folder, let Gradle sync, then Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install and enable on an Infinix phone

1. Copy the APK to the phone and open it. If asked, allow "Install unknown apps" for your file manager/browser. If Play Protect warns, tap "Install anyway".
2. Open the **PKR Key** app and tap **Enable PKR Key**. In the list, switch **PKR Key** on and accept the standard Android warning.
   (Manual path on XOS/HiOS: Settings > System > Language & input > On-screen keyboard > Manage keyboards.)
3. Back in the app, tap **Select PKR Key as default** and choose **PKR Key**.
4. Open any chat, tap the text box, and press the logo key.

## Notes
- The symbol is an image tile (original logo on emerald), not a text character.
- Image insertion depends on the receiving app. Many chat apps accept it; many text fields do not (then it is copied).

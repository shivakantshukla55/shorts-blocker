# Shorts Blocker

Closes YouTube Shorts and Instagram Reels the moment they open. Blocking can be
paused for 5 minutes by entering a long, complex password.

## Get the APK (no Android Studio needed)

1. Create a free GitHub account and a new empty repository.
2. Upload this whole folder to it (including the hidden `.github` folder).
3. Open the **Actions** tab, wait for "Build APK" to finish (about 3 minutes).
4. Open the finished run, download **ShortsBlocker-apk**, unzip it, and you have `app-debug.apk`.
5. Copy it to your phone and tap it to install (allow "Install unknown apps" for your file manager or browser).

Or build locally with Android Studio: open the folder and run Build > Build APK(s).

## First-time setup on the phone

1. Open **Shorts Blocker** and set your unlock password.
2. Tap **Open Accessibility settings**, find **Shorts Blocker** under installed apps, and switch it on.
   If the switch is greyed out: Settings > Apps > Shorts Blocker > ⋮ > **Allow restricted settings**, then try again.
3. Open YouTube and Instagram to test.

## Good to know

- Detection relies on internal view ids of the YouTube and Instagram apps. An app update can
  rename them. If blocking stops working, edit the id lists in `BlockerService.kt`.
- Only the Shorts/Reels player is closed. The rest of both apps keeps working.
- A determined you can still switch the accessibility service off or uninstall the app. The
  password protects the pause, not the app itself.

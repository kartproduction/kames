# Kames

Six small games from Nepal, Bhutan and around the world, in one calm app. By Kart Production.

**Games:** Bagh-chal, chess, Chungi, Keshey, Ludo and Tic-Tac-Toe. Play with a friend on one phone or against the computer. Chungi is also a solo game and a game for up to four players.

- **No permissions.** Kames has no internet permission and collects nothing. Scores stay on your phone.
- **Same look as Kauncher.** Light and dark themes follow your phone.
- **Also on the web:** play in any browser at https://kartproduction.github.io/kauncher/kames.html

## Scores

Scores are saved on your phone, separately from Kauncher. Use **Copy my scores** and **Paste scores** at the bottom of the list to move them between phones or apps.

## Build

**On GitHub (easiest):** add these secrets under Settings > Secrets and variables > Actions: `KEYSTORE_BASE64` (your keystore file, base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Then open the Actions tab, choose **Build Kames** and press **Run workflow**. When it finishes, download `Kames-build`: it holds `Kames.aab` (for Google Play), `Kames.apk` and `sha256.txt`. Pushing a tag such as `v1.0` also builds and attaches them to a release.

**On your computer:** open the folder in Android Studio, then Build > Generate Signed App Bundle.

Package name: `com.kartproduction.kames`. Raise `versionCode` in `app/build.gradle.kts` for every Play upload.

## Licence

See `LICENSE`.

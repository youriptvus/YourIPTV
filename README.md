# YourIPTV

YourIPTV is an independent IPTV player for Android TV, Google TV, Android phones and tablets, written in Kotlin with Media3 ExoPlayer. Add an M3U/M3U Plus playlist, Xtream Codes account and XMLTV guide that you are authorized to use. The app supplies no channels, provider account or subscription. It is a standalone player, not an Android launcher or device-management tool.

Website: [youriptv.us](https://youriptv.us)

## Current build

| Item | Value |
|---|---|
| Project version | **1.1.1 / versionCode 52** |
| Confirmed published baseline | **1.1.0 / versionCode 51**; the local candidate has not replaced it |
| Application ID | `com.youriptv.app` |
| Android | Minimum API23 / Android 6.0; compile and target API36 |
| Distribution | Customer APK with the existing GitHub/website updater |
| Status | Local release preparation; this source change does not publish an APK or website update |
| Final evidence | The matching release's `RELEASE_SUMMARY.md`, `PUBLISH.txt`, `SHA256SUMS.txt` and verification records |

Final test totals, device coverage, limitations and hashes belong to those artifact-bound records. Historical test reports do not certify a different candidate. The Google Play project is maintained separately and uses Play delivery, minimum API24 and its own signing/build configuration.

## Features in 1.1.1

- **Series history:** one parent-series entry with its artwork and last watched episode. Opening it shows seasons and episodes with their individual saved positions. Movies remain individual history entries. Continue prioritizes the most recently watched unfinished episode, or the next available episode after completion.
- **Episode progress:** a thin green line shows partial progress; completed episodes have a full line and unwatched episodes have no line. Resume, Play From Start, manual episode selection and Continue Watching share the same progress records.
- **Autoplay Next Episode:** ON when no preference has been saved, with saved OFF preserved. It is available in normal Playback settings without the Admin PIN. Confirmed completion can advance after a cancellable countdown through later episodes and seasons; the final available episode does not loop to the first. Manual Next remains available when autoplay is OFF.
- **Progress protection:** explicit watched, unwatched and clear actions take precedence over delayed checkpoints from earlier playback. Fresh intentional playback can save again. Playback observations keep their capture times and older same-session observations cannot replace newer progress. Existing history is retained without a destructive migration for this amendment.
- **Live TV and guide:** categories, favorites, preview, fullscreen playback, a timeline with current-program details, XMLTV, supported catch-up, reminders and recording. Explicit guide entry opens the requested source/channel at the current timeslot; ordinary refreshes retain active past/future browsing.
- **Fullscreen return:** holding physical Back returns to the current stream without also sending a short Back action. Short Back retains normal navigation. Launcher re-entry can resume the remembered live channel after leaving any screen, while respecting its playlist and current restrictions.
- **HTTP source compatibility:** explicit HTTP and HTTPS schemes are preserved for M3U, Xtream, guides, artwork and playback. HTTPS certificate validation remains enabled and HTTPS-to-HTTP redirects remain blocked.

Other features include multiple playlists, streaming catalogue imports, provider-account details and connection-limit messages, playlist/EPG refresh, movie details, season rows and episode cards, favorites/search, track and subtitle controls, aspect ratio, supported picture-in-picture, an external player, buffer/decoder preferences, audio delay, optional frame-rate matching, and manual backup/restore. All standard player features remain free. Providers control content, guide coverage, archive availability and connection limits.

## Settings and local data

Settings Lock defaults OFF for fresh or unconfigured state. Saved local On/Off choices and prior Settings PIN protection are preserved. Admin authorization for lock/PIN management and parental restrictions remain independent. With the lock ON, ordinary Settings still offers public controls, including Playback and playlist refresh; protected configuration uses Unlock Settings. About remains public. See [Settings security](docs/SETTINGS_SECURITY.md).

Provider credentials, playlists, history and preferences are stored locally. App-private storage is not additional database encryption. Exported JSON backups may contain readable provider credentials and addresses; keep them private. PIN secrets and the local Settings Lock choice are excluded from portable backups. Compatible in-place updates retain app data; do not uninstall or clear storage to force an upgrade.

HTTP does not encrypt source requests, credentials or content. Prefer working HTTPS when the provider supports it. The matching public privacy policy and any Google Play Data safety declarations must describe the actual transfers; do not claim every source request is encrypted.

## Download and install

[Latest published customer APK](https://github.com/youriptvus/YourIPTV/releases/latest/download/YourIPTV.apk) · [Latest published release](https://github.com/youriptvus/YourIPTV/releases/latest)

These links follow the release marked Latest on GitHub. They do not expose unpublished local 1.1.1 files.

On a phone/tablet, download `YourIPTV.apk` and open it from Downloads or Files. On Android TV/Google TV, download it on the TV or transfer it from a computer/USB drive and open it with a file manager. Android may ask you to allow **Install unknown apps** for the browser or file manager. Follow Android's prompts; managed devices may restrict installation.

For an existing official installation, install a compatible, officially signed APK in place. A debug or differently signed APK cannot replace it. Keep the existing installation and its data if Android reports a signing conflict or downgrade; contact the maintainer rather than uninstalling.

## Set up a provider

Open **Playlists** and choose a setup method. Keep credentials out of screenshots, issue reports and committed source.

### Xtream Codes

Enter the provider's exact server address, scheme and port, username, password and a playlist name. For example, `https://provider.example:8443` is a placeholder, not a supplied service. The provider's `xmltv.php` supplies the default guide; optional EPG URL and User-Agent fields remain available. **Save and load** authenticates and imports the available catalogue.

### M3U / M3U Plus

Enter your complete playlist URL. A placeholder example is:

```text
https://provider.example/get.php?username=YOUR_USERNAME&password=YOUR_PASSWORD&type=m3u_plus
```

An explicit HTTP address is also supported where selected by the user. A provider `get.php` link can be imported as Xtream or kept as M3U. Use the provider's working scheme/port; existing saved addresses are not silently rewritten to another scheme.

### YourIPTV shortcut

The customer build's two-field **YourIPTV** shortcut uses `http://cw-25.com` and the playlist name **YourIPTV**. Enter your own authorized provider username/password. This saves an ordinary Xtream playlist and supplies no account or subscription. Existing playlists are not renamed or converted automatically. The Google Play build uses generic provider setup instead.

### Guide, refresh and editing

The guide uses an explicit EPG URL first, then the provider's Xtream guide or the M3U header's `url-tvg`/derived Xtream guide where available. Channels without a matching guide ID/feed show no program information. Time offset and retention are under **TV Guide / EPG** in full Settings. **Settings > Playlist Settings** provides public **Update Playlist** and **Update EPG** controls and status.

Playlist refresh defaults to 16 hours when no interval was chosen; explicit saved intervals are retained. Cached data remains usable during refresh. Android scheduling, power and network conditions affect background work. Reminders/scheduled recordings retain their app-running limitation, and a recording uses a provider connection.

Use **Playlists > Edit current** or the manager for other saved sources. Password fields start masked; leaving the editor masks them again. Settings and applicable parental authorization still apply. Deleting a playlist requires its confirmation and removes its associated catalogue/history; cancel deletion during tests of real owner accounts.

## Customer automatic updates

Automatic updates default ON. On opening the app, it checks the latest GitHub release's `update.json`; compatible clients with website fallback can use `https://youriptv.us/updates/update.json` when needed. Checks/downloads validate the declared APK size, checksum, package, signing identity, version and permissions.

Technicians can allow YourIPTV to install updates through Android's **Install unknown apps** screen. Supported Android versions may also allow automatic reopening after an update when the required system permission is granted. Android may require confirmation or prevent silent installation/reopening; do not assume every device behaves identically.

The complete candidate can update compatible official installations directly, without intermediate versions. Its `previousVersionCode: 51` affects optional versus required prompts only. The updater does not offer a different APK with the same versionCode. Retired update addresses or missing installation support in old clients require a manual compatible in-place update. See [upgrade notes](docs/UPGRADING_TO_1_0.md).

## Build on Windows

Use a private build checkout for signing and local test configuration. Keep the owner source repository and clean source archives free of keys, credentials and generated outputs.

In the maintainer's current Windows workspace, owner source is `D:\APK\Repository\YourIPTV` and the active private build/test project is `D:\APK\work\YourIPTV-vod-redesign-customer`. Build from that explicit project; synchronize reviewed public source into the owner repository only after final checks. The owner commits/pushes there manually. Private signing material stays outside the owner repository and clean archives.

| Requirement | Project configuration |
|---|---|
| JDK | JDK21 used for current local verification; Java17 language/bytecode target |
| Android SDK | Platform36, Build-Tools36.0.0 and Platform Tools |
| Gradle | Pinned wrapper9.8.1; no separate Gradle install |
| Android Gradle Plugin | Pinned9.5.0 with built-in Kotlin |

The checked-in wrapper/build files define tool versions. A first build needs the configured Google Maven/Maven Central/Gradle dependencies; `--offline` works only after they are cached.

```powershell
cd <private-build-checkout>
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
.\gradlew.bat assembleDebug
.\gradlew.bat testReleaseUnitTest assembleRelease lintRelease
```

Alternatively set `sdk.dir` in an ignored `local.properties`. Debug output is `app\build\outputs\apk\debug\app-debug.apk`; the signed release is `app\build\outputs\apk\release\app-release.apk`. Unit reports are under `app\build\reports\tests\testReleaseUnitTest` and lint reports under `app\build\reports`.

### Release signing

In the private build project only, configure the ignored `keystore.properties` from its public `.example` template with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. `storeFile` resolves from the app module; use the existing official keystore outside the repository. Without signing configuration, release output is unsigned. Do not generate a replacement official key or commit/copy private signing files into repositories or ZIPs.

For an authorized test device, enable USB/wireless debugging and verify the selected serial before installing:

```powershell
adb devices
adb connect <device-ip:port>
adb -s <authorized-device> install -r app\build\outputs\apk\release\app-release.apk
```

Only install the production package after verifying signing/version compatibility and authorization. Shared phone/TV behavior can be checked in the separate Play project's isolated `.playtest` package without replacing customer or Play-installed production data. Isolated tests do not prove actual customer automatic installation or protected Play delivery.

## Release preparation and publication

The owner reviews and commits/pushes locally synchronized source manually. Workspace preparation does not contact GitHub, publish a release, submit an AAB or deploy website files. Final source and artifact hashes belong in release sidecars, not a self-referencing README commit identifier.

Before publishing 1.1.1/code52, verify that code52 and the earlier candidate were not already distributed, and that Play Console has not consumed code52. Preserve earlier local tags/artifacts. The development-local original `v1.1.1` tag belongs to the earlier candidate; do not push it or retarget it. The separately reviewed amendment tag identifies the final local source.

The owner must manually verify the remote canonical `v1.1.1` tag/release is unused. If it already exists, stop for a provenance/version decision. Only after committing/pushing the verified final owner source should the owner create remote `v1.1.1` on that commit. Never push development tags wholesale or force-update an existing tag. Existing updaters require the canonical APK route `/releases/download/v1.1.1/YourIPTV.apk`; an amendment-tag APK URL is not interchangeable while the manifest version remains `1.1.1`.

Publish the reviewed APK and source archive first, verify public bytes/source, then activate matching `update.json`. Upload/verify the mirror APK before its manifest. Use the matching release's `PUBLISH.txt` for exact paths, hashes and remaining gates.

## Documentation and limitations

See [release notes](docs/RELEASE_NOTES.md), [VOD navigation](docs/VOD_NAVIGATION.md), [progress behavior](docs/VOD_PROGRESS.md), [remote controls](docs/REMOTE_CONTROLS.md), [Settings security](docs/SETTINGS_SECURITY.md), [known issues](docs/KNOWN_ISSUES.md), [QA checklist](docs/QA_CHECKLIST.md) and [update mirror setup](docs/UPDATE_SERVER.md). Versioned historical observations retain their original scope.

Codec support, frame-rate modes, picture-in-picture, system keys and storage behavior depend on Android and the device. Providers can omit metadata, reject streams, impose connection limits or offer no catch-up. The player does not include a VPN, create missing guide data or guarantee uninterrupted provider playback. Scheduled recording/reminders require the app's supported running state. Automated checks and scoped device passes do not establish every provider/device combination or a long-duration soak.

## License

No source license has been chosen by the owner. Free application functionality does not grant permission to copy, modify or redistribute the source. Dependency licenses and owner-approved artwork rights remain applicable.

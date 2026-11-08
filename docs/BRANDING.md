# Branding in the 0.5.6 player release

This release uses the official YourIPTV name, package, icons, signing identity and About
information. It does not include the pending company-companion implementation, importer,
public issuer pin or company-branding Settings controls. The separate RebrandTool project
and its unfinished integration are preserved outside this release for planned 0.5.7 work.

## Business rules

All playback, playlist, M3U, Xtream Codes, EPG, catch-up, recording, favorites and settings
features remain free. Custom rebranding is the only paid offering. No billing, trials,
subscription checks or playback feature locks are part of this release. Parental and
Settings PINs, provider limits and Android restrictions remain effective.

## Existing resource structure

| Surface | Source |
|---|---|
| Official app display name | res/values/branding.xml, @string/app_name |
| Company/contact strings | res/values/branding.xml, brand_* |
| Phone launcher icons | res/mipmap-*/ic_launcher*.png and adaptive-icon XML |
| Android TV banner | res/drawable-*/banner.png |
| About body and required notices | ui/Branding.kt |

Branding.displayName() resolves the compiled app-name resource. Branding.aboutBody()
appends mandatory free-use, privacy, provider/Android limitation and third-party notices.
BrandingCentralizationTest guards the resource/name and required-notice contracts.

These compiled resource locations document the existing structure; they are not an
active runtime branding import mechanism. BrandAssets' remote endpoint remains empty.
Changing compiled resources invalidates an existing APK signature and requires an
explicitly authorized source build with appropriate versioning and signing. Never patch
or bypass BrandIntegrity/OfficialSigning, embed signing secrets in profiles, repurpose the
official signing key, or remove required notices. This release changes none of those pins.

The website updater fallback is independent of branding. It retains the official package,
compiled app label, signing certificate and permission checks for every downloaded APK.

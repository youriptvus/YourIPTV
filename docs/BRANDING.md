# YourIPTV branding & rebranding integration

This document records the branding decision and the integration points a **separate, authorized
rebranding tool** will use. The tool itself is **not** built yet; this is preparation only.

## Decision

- **All player features are free.** Every playback, playlist, Xtream Codes, M3U, EPG, catch-up,
  recording, favorites, settings, parental-control, and PIN feature is available to every user with no
  payment, subscription, trial, timer, or feature lock.
- **Custom rebranding is the only paid offering.** The paid product is a customized build of YourIPTV
  for a company or customer. It changes branding only (name, logos, icons, banner, About company info)
  and must **never** make a more capable player or reduce standard-app functionality.
- Still enforced (not premium): provider connection limits, Android permissions, hardware limits, and
  security PINs.
- The normal player app has **no** payment screens, "Unlock Premium" buttons, or branding controls.
  The separate tool performs rebranding offline against the source/resources, then rebuilds and signs.

## What a rebrand may change

| Surface | Where | Type |
|---|---|---|
| App display name | `res/values/branding.xml` → `@string/app_name` | string |
| Company name / tagline / support email, phone, URL (default website: `https://youriptv.us`) | `res/values/branding.xml` → `brand_*` | strings |
| Phone launcher icons | `res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png` (+ `mipmap-anydpi-v26/*.xml`) | images |
| Android TV / Leanback banner (320×180) | `res/drawable-*/banner.png` | image |
| About-screen company block | composed by `ui/Branding.kt` from the `brand_*` strings | code-driven, data in branding.xml |

Keep the **resource names** identical; only replace the values/files. `ui/Branding.kt` is the stable
contract that maps these resources to the UI, so the tool edits resources, not player code.

### Screens that read the name dynamically

Visible screens never hardcode "YourIPTV"; they read `Branding.displayName()` (= `@string/app_name`),
so changing `app_name` updates all of them:

- About screen title (`AboutActivity`, `about_title`) and the About body (`Branding.aboutBody()`).
- Settings → About category entry ("About <name>").
- Main screen welcome text and nav-rail header (`MainActivity`).
- Free-use notice on the About screen, welcome text, and playlist editor (`edit_free_notice`) via
  `Branding.freeNotice()`. `@string/free_notice` starts with a `%1$s` placeholder for the name;
  keep that placeholder when translating or editing.

`BrandingCentralizationTest` guards this: layouts must not contain "YourIPTV", `app_name` must match
`BrandIntegrity.EXPECTED_BRAND_NAME`, `free_notice` must start with `%1$s`, and the About body must
keep the open-source section.

### Strings intentionally still hardcoded (future tool checklist)

These are internal or part of a file/network format identity, so they were left as "YourIPTV".
The rebranding tool should decide per customer whether to change them:

- Log tags and theme style names (internal only).
- `Http.DEFAULT_USER_AGENT` and the Settings "YourIPTV default" user-agent label (providers may
  allow-list it).
- Backup file format messages (`Backup`, `BackupFiles`) and the Settings "Choose a YourIPTV backup
  file" prompt (backup compatibility).
- Scheduler / `StreamDiagnosis` messages.
- Integrity / signing error strings (`BrandIntegrity`, `OfficialSigning`).

## What a rebrand must NOT touch

- Player behavior, user settings, playlists, provider credentials, history, favorites, PIN hashes.
- The mandatory About notices: the "free for everyone" text (`@string/free_notice`) and the
  open-source license acknowledgements are appended by `Branding.aboutBody()` and cannot be dropped.
- `applicationId` / package name, signing keys, and update compatibility — unchanged unless a future
  requirement explicitly calls for it. The runtime integrity check (`BrandIntegrity`,
  `OfficialSigning`) pins the package to `com.youriptv.app` and the name to `YourIPTV`, so an official
  rebrand is expected to update those pinned values **and** re-sign with the authorized key.

## Which changes require rebuild + re-sign

All of the above are **compiled resources** inside the APK. Any change therefore requires:

1. Edit `branding.xml` and/or replace the icon/banner files.
2. If the brand name or package is intentionally changed for a customer, update the pinned values in
   `BrandIntegrity` (`EXPECTED_BRAND_NAME`, `ALLOWED_PACKAGES`, or the reserved
   `CUSTOM_TOOL_EXCEPTION` seam) so the integrity gate matches the authorized build.
3. Rebuild (`assembleRelease`) and **re-sign with the authorized release key**. Editing files inside an
   already-signed APK invalidates the signature and the app refuses to start (by design).

Runtime remote branding (`ui/BrandAssets.kt`) can override the name/logo from an HTTPS endpoint
**without** a rebuild, but it is off by default and is a display-only convenience, not the rebranding
product.

## Reserved integration seam for the tool

`BrandIntegrity.CUSTOM_TOOL_EXCEPTION` (closed/`null` by default) is the single sanctioned place for the
future first-party tool to register an authorized customized package id, so a legitimately rebranded,
re-signed build passes the integrity gate without weakening protection against third-party rebranding.

# Backup update server

YourIPTV 0.5.6 (version code 30) keeps GitHub as its primary update server and adds the owner's
website as a backup. The website serves two static files; no database, account or dynamic API is
needed. Hosting setup and file publication are separate from building the app.

## How the app chooses a source

1. Read `https://github.com/youriptvus/YourIPTV/releases/latest/download/update.json`.
2. If that request fails or the metadata is invalid, read `https://youriptv.us/updates/update.json`.
3. Download the selected release's APK. If a GitHub download is unavailable, try
   `https://youriptv.us/updates/YourIPTV-<versionName>-release.apk` for that same release.

The app uses valid GitHub metadata before considering the website; it does not poll the website
just because it might advertise a newer release. A cached newer release is kept if either server
returns an older one. For an already cached version code, a response cannot change its version
name, size, SHA-256 or `previousVersionCode`; the cached release remains in use if those differ.
To replace an APK or change the required-update policy, publish a new version code.

The website APK must match the selected manifest's exact size and SHA-256. The app also checks
package, version, signing certificate, app label, Android compatibility and the identical requested
permission set before installation. Fallback does not waive those checks. Cached update metadata
continues to enforce an already known required update when neither server is available.

Apps older than 0.5.6 do not know the website address. Deliver their first 0.5.6 update through the
existing GitHub updater or install the official signed APK with `adb install -r`. Keep the same
package and signing key; never uninstall or clear data during an update.

## Website paths

Create this folder under the HTTPS document root for `youriptv.us`:

```text
updates/
  YourIPTV-0.5.6-release.apk
  update.json
```

Both URLs must return the file itself with HTTP 200, without a login, browser challenge or HTML
download page. Keep TLS certificates valid. Allow anonymous HTTPS downloads; FTP/FTPS/SFTP may be
used to place files on the host, but apps only fetch HTTPS URLs. Retain older versioned APKs so an
app with cached metadata can finish downloading the release it already selected.

The APK is named `YourIPTV.apk` on the GitHub `v0.5.6` release and
`YourIPTV-0.5.6-release.apk` on the website. These are two filenames for the same signed APK bytes.
Keep website files in a separate release staging folder so its `update.json` does not overwrite
the GitHub manifest. Do not upload source, signing keys, `keystore.properties`, `local.properties`
or `test-config.local.properties` with a release.

## Generate the two manifests

Run from the project root after building and verifying the signed release APK. The owner
confirmed customers have 0.5.3 / code 27; unpublished 0.5.5 is not an intermediate requirement.
Use code 27 for this release. For later releases use the actual last published version code:
`previousVersionCode` determines which installed versions must update.

```powershell
py scripts/make_update_json.py --previous 27 --out update.json
py scripts/make_update_json.py --previous 27 --mirror --out update-website.json
```

The generator reads `versionCode` and `versionName` from `app/build.gradle.kts` and hashes
`app/build/outputs/apk/release/app-release.apk` by default. Use `--apk <path>` for a saved release
APK. It does not build, sign or upload anything. Both manifests contain the same version, size,
SHA-256 and previous-version code; only the `apk` URL differs:

| Manifest | APK URL for 0.5.6 |
|---|---|
| GitHub `update.json` | `https://github.com/youriptvus/YourIPTV/releases/download/v0.5.6/YourIPTV.apk` |
| Website `update.json` | `https://youriptv.us/updates/YourIPTV-0.5.6-release.apk` |

The existing `--apk-url` option remains available for local device tests and cannot be combined
with `--mirror`. Do not publish test-server URLs.

## Publish and verify

1. Copy the signed APK into the website release staging folder as `YourIPTV-0.5.6-release.apk`.
   Copy `update-website.json` there as `update.json`.
2. Upload the APK to `/updates/YourIPTV-0.5.6-release.apk` first. Download it over public HTTPS and
   compare its byte count and SHA-256 with the generated manifest.
3. Upload the website `update.json` last, preferably by renaming a temporary file on the server
   so clients cannot read a partial manifest. Check its public URL returns JSON and references the
   verified APK. Publish only after the matching APK is ready.
4. Manually publish the normal GitHub release with the same APK bytes named `YourIPTV.apk` and the
   GitHub manifest named `update.json`. Mark it as Latest, without draft or prerelease status.
5. Verify both public manifests describe the same release and exact APK bytes. Test the update
   paths, including unavailable GitHub metadata and an unavailable GitHub APK, on a device with
   0.5.6's fallback-capable updater before relying on the backup server.

Set `Cache-Control: no-cache` on `/updates/update.json` so intermediaries revalidate it. If a CDN
or host ignores that header, exclude this path from caching and purge any old copy at publication.
Versioned APK files can use a long cache lifetime; never replace a versioned APK with different
bytes. Suitable content types are `application/json` for the manifest and
`application/vnd.android.package-archive` for the APK. Do not apply an HTML error page or a
browser-only security challenge to `/updates/`.

Publication and device test results belong in the release QA record; this setup guide does not
assert either has been completed.

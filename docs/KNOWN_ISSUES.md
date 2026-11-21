# Current limitations - 1.1.1 / code 52

This local candidate does not publish or replace the confirmed 1.1.0/code51 releases. Final artifact identity, host checks and test coverage are recorded in matching release sidecars. The historical snapshot below preserves earlier observations; resolved old launcher/held-Back/phone-layout defects are not presented as current failures.

## Provider and device limits

- Providers control stream availability, guide IDs/coverage, metadata, archive timing and simultaneous-connection limits. Catalogue separator entries or unavailable streams may not be playable. The app cannot create missing content or raise a provider limit.
- Networks can block or replace archive/media hosts. YourIPTV reports media/access/network errors and includes no VPN. User-selected HTTP sources are supported but do not encrypt credentials/content; HTTPS certificates are validated and downgrade redirects remain blocked.
- Codec support and decoder performance depend on Android/hardware. Software decoding can be CPU limited; a short decoded-picture check is not a codec-wide or endurance guarantee. The current work adds no new decoder engine.
- Auto frame-rate changes require an exposed supported display mode. PCM audio delay does not affect HDMI bitstream/passthrough. System sleep, HDMI-CEC, picture-in-picture and background behavior remain subject to Android/device policy.
- Google TV may intercept the dedicated GUIDE key. Open the in-app guide from TV or player options instead. Held physical Back uses Android navigation compatibility; this does not change Play protection, signing or permissions.
- Recording uses a provider connection. Scheduled reminders/recording retain the app-running limitation. Force-stop, unavailable storage, power/network restrictions or closing the app can prevent scheduled work. Background refresh timing is not exact and jobs are reconciled on a later foreground launch.
- External players have separate progress/privacy behavior. Legacy history without sufficient stable parent/content identity cannot safely be merged by title. Preserve recoverable context rather than erasing or guessing it.

## Security, data and validation limits

App-private storage is not additional database encryption. Exported backup files can contain readable source credentials; protect copies and destinations. Do not uninstall/clear production storage to force an update or recover a forgotten PIN. Local Settings/Admin/parental controls are not remote identity or storage encryption, and no unauthenticated PIN-reset bypass is provided.

Current isolated phone/TV checks cover generated-fixture navigation, progress/defaults, playback, source guards, guide time/focus and held Back. They do not establish every provider, all codecs, long uninterrupted playback, physical tablet behavior, OEM backup/transfer, actual customer automatic installation or protected Play delivery. Sound is assumed per owner direction, not newly confirmed. Passing local checks does not guarantee Google approval.

The current guide, manual-progress and phone-focus fixes passed scoped checks described in [QA_CHECKLIST.md](QA_CHECKLIST.md). Historical failures and superseded candidates remain preserved separately; they are not counted as fresh passes.

## Historical evidence

The [preserved earlier document](history/KNOWN_ISSUES-before-1.1.1.md) retains older implementation/QA records with their original dates and limits. It does not override the maintained 1.1.1/code52 guidance above.

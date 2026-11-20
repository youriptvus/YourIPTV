# VOD progress and navigation

Movies and episodes use the existing Media3 player and an additive SQLite v8 migration. Live playback never enters VOD progress. A serialized, app-owned writer receives snapshots taken on the player looper; pending snapshots are immediately visible to details screens. Saves run every five seconds and on pause, seek, stop and navigation, rather than relying on Activity destruction.

Progress is scoped to playlist and provider account. Xtream provider IDs identify content across catalog row/URL changes; M3U uses normalized stream URLs, retaining meaningful query parameters. Changed accounts revoke old playback writes. Unknown or genuinely changed content identities cannot safely be merged by title. Legacy history positions remain readable; they have no trustworthy explicit completion flag and are not guessed complete from their position.

Completion requires a natural player END event within at most two seconds of known duration, with one-percent tolerance for short clips. Stopping in credits remains resumable. Manual watched/unwatched and individual reset are available. Reset records suppress older legacy resume positions. External players maintain their own progress; these controls track the built-in player.

The series queue spans all supplied seasons in numeric order. Episode cards are recycled and artwork is loaded only for visible cards. Metadata is requested for the selected title; Xtream's series-detail response supplies its episode list together. Existing bounded metadata/image caches remain. No stream is opened to generate a thumbnail.

Next-episode autoplay defaults off. When enabled, a ten-second countdown can be canceled, including with Back. Backgrounding cancels it; returning to a completed episode restores its choices without replaying it or starting another countdown.

Use `py scripts/check_shared_vod.py --other-repo PATH` to check shared implementation parity. Signing, updater, HTTPS policy and approved security initialization remain distribution-specific. Release artifacts and actual validation results are documented separately; implementation notes are not a claim that every provider/device combination was tested.

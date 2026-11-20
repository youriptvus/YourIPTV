"""Read-only parity check for shared player features; leaves distribution-specific policies separate."""
from pathlib import Path
import argparse, hashlib, json, re

JAVA = "app/src/main/java/com/youriptv/app/"
COMMON = [JAVA + name for name in (
    "data/SettingsLockPolicy.kt", "data/Models.kt", "data/Db.kt", "data/Repository.kt", "data/VodMetadata.kt", "data/WatchPolicy.kt", "data/WatchProgressStore.kt",
    "data/Backup.kt", "data/ContentLengthInputStream.kt", "ui/MainActivity.kt", "ui/Adapters.kt", "ui/MovieDetailsActivity.kt", "ui/SeriesActivity.kt",
    "ui/SeriesBrowseState.kt", "ui/SeriesEpisodeAdapters.kt", "ui/PlayerActivity.kt", "ui/RemoteActivity.kt", "ui/AudioDelayProcessor.kt",
    "ui/CustomerSettingsPanel.kt", "ui/EpgSettingsPanel.kt")] + ["app/src/main/res/" + name for name in (
    "layout/series_activity.xml", "layout/series_episode_card.xml", "layout/vod_poster_item.xml", "layout/activity_movie_details.xml",
    "layout/activity_player.xml", "layout-port/activity_main.xml", "values/series_dimensions.xml", "values-port/series_dimensions.xml",
    "values/player_dimensions.xml", "values-port/player_dimensions.xml", "values/themes.xml", "values/mobile_strings.xml")]

def normalized(path): return path.read_text(encoding="utf-8-sig").replace("\r\n", "\n")
def signature(text, start, end): return text[text.index(start):text.index(end, text.index(start))].strip()
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--other-repo", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]; other = args.other_repo.resolve()
    checks = {}; hashes = {}
    for name in COMMON:
        try:
            left, right = normalized(root/name), normalized(other/name)
            checks[name] = left == right
            hashes[name] = hashlib.sha256(left.encode()).hexdigest()
        except OSError: checks[name] = False
    nav = [normalized(p/(JAVA+"ui/Nav.kt")) for p in (root, other)]
    for start, end in (("    fun open(", "    /** [unlockedGroup]"), ("    fun play(", "    fun fullscreenPreview")):
        checks["Nav: " + start.strip()] = signature(nav[0], start, end) == signature(nav[1], start, end)
    for p in (root, other):
        settings = normalized(p/(JAVA+"data/Settings.kt"))
        checks[p.name+": auto next defaults off"] = 'sp.getBoolean("auto_next_episode", false)' in settings
    versions = []
    for p in (root, other):
        build = normalized(p/"app/build.gradle.kts")
        line = next(line for line in build.splitlines() if re.search(r"\bversionCode\s*=", line))
        code = int(re.findall(r"\b\d+\b", line)[-1])
        name = re.search(r'versionName[^\n]*?"(\d+\.\d+\.\d+)"', build)[1]
        versions.append((name, code))
    checks["matching release version"] = versions[0] == versions[1]
    result = {"passed": all(checks.values()), "versions": versions, "checks": checks, "shared_sha256": hashes,
        "intentional_differences": ["distribution signing/build configuration", "customer updater and permissions", "Play HTTPS transport policy",
            "approved Admin initialization and customer source presets"]}
    if args.output: args.output.write_text(json.dumps(result, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"passed": result["passed"], "checks": len(checks), "failed": [k for k,v in checks.items() if not v]}))
    raise SystemExit(0 if result["passed"] else 1)
if __name__ == "__main__": main()

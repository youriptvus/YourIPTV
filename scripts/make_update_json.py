"""Write update.json for a YourIPTV GitHub release (run with: py scripts/make_update_json.py --previous N).

Upload the result next to YourIPTV.apk on the release. Installed apps read it from
https://github.com/youriptvus/YourIPTV/releases/latest/download/update.json
"""
import argparse
import hashlib
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def gradle_version():
    text = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    code = re.search(r'versionCode\s*=.*\?:\s*(\d+)', text).group(1)
    name = re.search(r'versionName\s*=.*\?:\s*"([^"]+)"', text).group(1)
    return int(code), name


def main():
    code, name = gradle_version()
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--apk", default=os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk"))
    p.add_argument("--previous", type=int, required=True,
                   help="versionCode of the release published before this one (devices below it must update)")
    p.add_argument("--version-code", type=int, default=code)
    p.add_argument("--version-name", default=name)
    p.add_argument("--apk-url", help="Override the APK URL (local device tests only)")
    p.add_argument("--out", default="update.json")
    a = p.parse_args()
    if not 0 <= a.previous < a.version_code:
        p.error("--previous must be lower than the new versionCode")
    data = open(a.apk, "rb").read()
    info = {
        "versionCode": a.version_code,
        "versionName": a.version_name,
        "apk": a.apk_url or f"https://github.com/youriptvus/YourIPTV/releases/download/v{a.version_name}/YourIPTV.apk",
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "previousVersionCode": a.previous,
    }
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(info, f, indent=2)
    print(json.dumps(info, indent=2))


if __name__ == "__main__":
    main()

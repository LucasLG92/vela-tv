"""Check the compiled APK as an Android TV app catalog sees it."""
import re
import subprocess
import sys


def check_tv_support(badging):
    launcher = re.search(r"^leanback-launchable-activity: name='[^']+'.*$", badging, re.M)
    application = re.search(r"^application:.*$", badging, re.M)
    if not launcher:
        raise ValueError("Missing Android TV launcher entry")
    if not any(re.search(r" banner='[^']+'", line.group()) for line in (launcher, application) if line):
        raise ValueError("Missing Android TV launcher banner")

    required = re.findall(r"^\s*uses-(?:implied-)?feature: name='([^']+)'", badging, re.M)
    optional = {"android.hardware.touchscreen", "android.hardware.microphone", "android.hardware.location"}
    incompatible = sorted({feature for feature in required
                           if feature in optional or feature.startswith("android.hardware.location.")})
    if incompatible:
        raise ValueError("The APK requires optional TV hardware: " + ", ".join(incompatible))


if __name__ == "__main__":
    aapt2, apk = sys.argv[1:]
    badging = subprocess.check_output([aapt2, "dump", "badging", apk], encoding="utf-8")
    check_tv_support(badging)
    print("PASS TV launcher, banner and optional hardware:", apk)

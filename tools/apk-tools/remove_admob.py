#!/usr/bin/env python3
"""
Sirf apne khud ke decompile kiye hue APK ke AndroidManifest.xml aur smali se
Google AdMob / Google Mobile Ads SDK (com.google.android.gms.ads) ka hissa hatao.

Note (zaroor padhna): ye sirf us SDK ki apni classes aur manifest entries hatata hai.
Agar app ka APNA code (jaise koi AdsHelper.smali, MainActivity.smali) seedha
ads classes ko call karta hai (loadAd(), AdView, MobileAds.initialize() waghera),
to wo call-site is script se nahi badalti -- usse alag se, us specific smali method
ko dekh kar no-op banana padega (chhota, scoped kaam; Claude se bol kar karwaya ja sakta hai).
"""
import re
import shutil
import sys
from pathlib import Path

def strip_manifest(manifest_path: Path) -> int:
    text = manifest_path.read_text(encoding="utf-8", errors="replace")
    before = text

    # <meta-data>, <activity>, <provider>, <service>, <receiver> jinka name/android:name
    # com.google.android.gms.ads se shuru hota hai (poora tag, self-closing ya block dono)
    tag_names = ["meta-data", "activity", "activity-alias", "service", "receiver", "provider"]
    for tag in tag_names:
        # self-closing: <tag ... android:name="com.google.android.gms.ads...." ... />
        text = re.sub(
            rf'<{tag}\b[^>]*android:(?:name|value)="com\.google\.android\.gms\.ads[^"]*"[^>]*/>',
            "", text, flags=re.S)
        # block form: <tag ...>...</tag>
        text = re.sub(
            rf'<{tag}\b(?:(?!</?{tag}\b)[^>])*android:(?:name|value)="com\.google\.android\.gms\.ads[^"]*"(?:(?!</?{tag}\b).)*?</{tag}>',
            "", text, flags=re.S)

    # AdMob application id meta-data (value doesn't contain the package name)
    text = re.sub(
        r'<meta-data\b[^>]*android:name="com\.google\.android\.gms\.ads\.APPLICATION_ID"[^>]*/>',
        "", text)

    # Ad SDK ki apni permissions
    for perm in ["com.google.android.gms.permission.AD_ID"]:
        text = re.sub(rf'<uses-permission\b[^>]*android:name="{re.escape(perm)}"[^>]*/>', "", text)

    manifest_path.write_text(text, encoding="utf-8")
    return 0 if text != before else 1  # 1 = kuch nahi mila/hata

def strip_smali(decompiled_root: Path) -> list:
    removed = []
    for smali_dir in sorted(decompiled_root.glob("smali*")):
        target = smali_dir / "com" / "google" / "android" / "gms" / "ads"
        if target.exists():
            removed.append(str(target.relative_to(decompiled_root)))
            shutil.rmtree(target)
    return removed

def main():
    if len(sys.argv) != 2:
        print("Usage: remove_admob.py <decompiled-folder>")
        sys.exit(1)
    root = Path(sys.argv[1])
    manifest = root / "AndroidManifest.xml"
    if not manifest.exists():
        print(f"AndroidManifest.xml nahi mila: {manifest}")
        sys.exit(1)
    nothing = strip_manifest(manifest)
    removed = strip_smali(root)
    print("Manifest se AdMob entries hata di gayi" + (" (kuch mila hi nahi)" if nothing else ""))
    if removed:
        print("Smali se hataye gaye folders:")
        for r in removed:
            print("  - " + r)
    else:
        print("Koi com.google.android.gms.ads smali folder nahi mila (SDK shayad already nahi tha, ya naam alag hai)")
    print("\nYAAD RAKHO: agar app ka apna code seedha ads ki classes call karta hai, wo yahan nahi badla.")
    print("Us call-site ko alag se dekhna/patch karna padega.")

if __name__ == "__main__":
    main()

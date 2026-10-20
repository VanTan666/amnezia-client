#!/usr/bin/env python3
"""Fetch the exact GPL engine shipped with the pinned official Amnezia release.
No user configuration or credentials are accessed by this script.
"""
import hashlib, pathlib, urllib.request, zipfile, io
root=pathlib.Path(__file__).resolve().parents[1]
url='https://github.com/amnezia-vpn/amnezia-client/releases/download/5.0.3.0/AmneziaVPN_5.0.3.0_android9-10_armeabi-v7a.apk'
expected='1fdf920faa8e96eae7b5907dfe2afaaf8db7dc06597d57ebd43495e27a1c0e10'
engine='3797906c7c12dca61e74800ea6d193f9965ac34e5b60fbe18fe2142be95fe66e'
target=root/'app/src/main/jniLibs/armeabi-v7a/libwg-go.so'
if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest()==engine:
 print('Pinned engine already verified');raise SystemExit
with urllib.request.urlopen(url,timeout=120) as response:
 data=response.read()
if hashlib.sha256(data).hexdigest()!=expected:raise SystemExit('Official APK SHA256 mismatch')
with zipfile.ZipFile(io.BytesIO(data)) as archive:
 native=archive.read('lib/armeabi-v7a/libwg-go.so')
if hashlib.sha256(native).hexdigest()!=engine:raise SystemExit('Native engine SHA256 mismatch')
target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(native)
print('Official AWG engine downloaded and SHA256 verified')

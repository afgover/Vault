#!/usr/bin/env python3
"""
APK içindeki yerel kütüphanelerin 16 KB sayfa boyutuna uyumunu ölçer.

Android 15+ cihazlarda 4 KB hizalı bir `.so` "uygulama 16 KB ile uyumlu
değil" uyarısını doğurur ve mağaza şartıdır. Uyarı yalnız cihazda görünür,
derlemede hiçbir şey söylemez — bu yüzden ölçüm depoya kondu
(vault_takip B-074).

Kullanım: tools/apk-hizalama.py app/build/outputs/apk/debug/app-debug.apk
"""
import sys, zipfile, struct

def p_aligns(data):
    assert data[:4] == b"\x7fELF"
    is64 = data[4] == 2
    endian = "<" if data[5] == 1 else ">"
    if is64:
        e_phoff, = struct.unpack_from(endian + "Q", data, 0x20)
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", data, 0x36)
    else:
        e_phoff, = struct.unpack_from(endian + "I", data, 0x1C)
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", data, 0x2A)
    out = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, = struct.unpack_from(endian + "I", data, off)
        if p_type != 1:  # PT_LOAD
            continue
        align, = struct.unpack_from(endian + "Q" if is64 else endian + "I",
                                    data, off + (0x30 if is64 else 0x1C))
        out.append(align)
    return out

apk = sys.argv[1]
kotu = 0
with zipfile.ZipFile(apk) as z:
    for name in sorted(n for n in z.namelist() if n.endswith(".so") and "arm64" in n):
        aligns = p_aligns(z.read(name))
        en_kucuk = min(aligns) if aligns else 0
        durum = "16 KB ✓" if en_kucuk >= 0x4000 else f"{en_kucuk//1024} KB ✗"
        if en_kucuk < 0x4000: kotu += 1
        print(f"  {name.split('/')[-1]:42s} {durum}")
print(("UYUMSUZ: %d kütüphane" % kotu) if kotu else "TÜMÜ 16 KB UYUMLU")
sys.exit(1 if kotu else 0)

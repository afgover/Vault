#!/usr/bin/env python3
"""
Mac panosundaki metni Vault'a aktarılabilir şifreli yedeğe (.vaultbak) çevirir.

Neden bu yol: uygulamanın ağ izni yok, cihazın panosuna dışarıdan yazılamıyor
(`cmd clipboard` Samsung'da yok) ve `adb shell input text` özel karakterlerde
güvenilmez. Yedek biçimi ise cihazdan bağımsız ve her karakteri taşıyor.

Akış:
    1. Bu betik panodakini alır, tek kayıtlık şifreli yedek üretir.
    2. --push ile dosyayı telefonun Download klasörüne kopyalar.
    3. Telefonda: Ayarlar → Yedekten geri yükle → dosyayı seç → yedek parolası
       → "Mevcuta ekle".

Biçim, app/src/main/java/com/afgover/vault/backup/BackupManager.kt ile birebir:
    {"app":"vault","version":1,
     "kdf":{"algo":"PBKDF2WithHmacSHA256","iterations":310000,"salt":b64},
     "cipher":"AES-256-GCM",
     "data":b64( iv(12) || şifreli+tag )}

Panodaki değer diske hiç düz metin olarak yazılmaz; yalnızca şifreli dosya
oluşur. Yedek parolası sorulur, hiçbir yerde saklanmaz.

Örnekler:
    ./vault-clip.py "Ev adresi" --tur gundelik --push
    ./vault-clip.py "Banka" --tur hesap --alan sifre --push
    ./vault-clip.py "Wi-Fi" --tur not --alan not --korumali
"""

import argparse
import base64
import getpass
import hashlib
import json
import os
import subprocess
import sys
import time

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

KDF_ITERATIONS = 310_000
KEY_BYTES = 32
IV_BYTES = 12
PUSH_DIR = "/sdcard/Download"
ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")

# --tur değeri -> uygulamadaki EntryType
TYPES = {
    "hesap": "LOGIN",
    "gundelik": "EVERYDAY",
    "kart": "CARD",
    "not": "NOTE",
}

# --alan değeri -> EntryData alanı (app/src/main/java/.../data/EntryData.kt)
FIELDS = {
    "sifre": "password",
    "kullanici": "username",
    "site": "url",
    "not": "notes",
    "ad": "fullName",
    "telefon": "phone",
    "eposta": "email",
    "adres": "address",
    "kart-no": "cardNumber",
    "iban": "iban",
}

# Tür başına, --alan verilmezse kullanılacak alan
DEFAULT_FIELD = {
    "hesap": "sifre",
    "gundelik": "adres",
    "kart": "kart-no",
    "not": "not",
}


def die(message):
    print(f"hata: {message}", file=sys.stderr)
    sys.exit(1)


def clipboard_text():
    try:
        result = subprocess.run(["pbpaste"], capture_output=True, text=True, check=True)
    except (OSError, subprocess.CalledProcessError) as exc:
        die(f"pano okunamadı: {exc}")
    text = result.stdout
    if not text.strip():
        die("pano boş (ya da metin değil). Önce kopyala, sonra çalıştır.")
    return text


def ask_password():
    """
    Yedek parolası. Yalnızca ASCII: Android tarafındaki PBKDF2'nin char->bayt
    dönüşümü ASCII dışında sağlayıcıya göre değişebiliyor; ASCII'de her
    uygulamada aynı sonucu verir, yani dosya kesin açılır.
    """
    while True:
        pw = getpass.getpass("Yedek parolası (uygulamada bunu gireceksin): ")
        if len(pw) < 8:
            print("En az 8 karakter olmalı.")
            continue
        if not pw.isascii():
            print("Türkçe/özel harf kullanma — yalnızca ASCII (a-z, 0-9, noktalama).")
            continue
        if pw != getpass.getpass("Parolayı tekrar gir: "):
            print("Parolalar eşleşmedi.")
            continue
        return pw


def build_entry(args, value):
    now = int(time.time() * 1000)
    data = {}
    if args.ek:
        data["custom"] = [{"label": args.ek, "value": value}]
    else:
        data[FIELDS[args.alan]] = value
    if args.kullanici:
        data["username"] = args.kullanici
    return {
        "type": TYPES[args.tur],
        "title": args.baslik,
        "createdAt": now,
        "updatedAt": now,
        "quick": args.hizli,
        "data": data,
    }


def encrypt(payload, password):
    salt = os.urandom(16)
    key = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, KDF_ITERATIONS, KEY_BYTES)
    iv = os.urandom(IV_BYTES)
    blob = iv + AESGCM(key).encrypt(iv, payload.encode("utf-8"), None)
    return {
        "app": "vault",
        "version": 1,
        "kdf": {
            "algo": "PBKDF2WithHmacSHA256",
            "iterations": KDF_ITERATIONS,
            "salt": base64.b64encode(salt).decode("ascii"),
        },
        "cipher": "AES-256-GCM",
        "data": base64.b64encode(blob).decode("ascii"),
    }


def push(path):
    if not os.path.exists(ADB):
        die(f"adb bulunamadı: {ADB}")
    result = subprocess.run([ADB, "push", path, PUSH_DIR + "/"], capture_output=True, text=True)
    if result.returncode != 0:
        die(f"adb push başarısız: {result.stderr.strip() or result.stdout.strip()}")
    return f"{PUSH_DIR}/{os.path.basename(path)}"


def main():
    parser = argparse.ArgumentParser(
        description="Mac panosundaki metni Vault yedeği (.vaultbak) olarak paketler.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("baslik", help="Kaydın başlığı (uygulamada listede görünen ad)")
    parser.add_argument("--tur", choices=sorted(TYPES), default="hesap", help="Kayıt türü (varsayılan: hesap)")
    parser.add_argument("--alan", choices=sorted(FIELDS), help="Panodakinin yazılacağı alan")
    parser.add_argument("--ek", metavar="ETIKET", help="Hazır alan yerine bu adla bir ek alan oluştur")
    parser.add_argument("--kullanici", metavar="AD", help="Kullanıcı adını da doldur")
    hizli = parser.add_mutually_exclusive_group()
    hizli.add_argument("--hizli", action="store_true", default=None,
                       help="Klavyede parolasız kullanılabilir olarak işaretle")
    hizli.add_argument("--korumali", dest="hizli", action="store_false",
                       help="Kilit açmadan görünmesin (varsayılan)")
    parser.add_argument("--cikti", metavar="DOSYA", help="Çıktı dosyası yolu")
    parser.add_argument("--push", action="store_true", help="Dosyayı telefonun Download klasörüne kopyala")
    args = parser.parse_args()

    if args.alan and args.ek:
        die("--alan ile --ek birlikte kullanılamaz")
    if not args.alan:
        args.alan = DEFAULT_FIELD[args.tur]
    if args.hizli is None:
        # Uygulamadaki kuralın aynısı: Gündelik kayıtlar hızlı erişimle başlar.
        args.hizli = args.tur == "gundelik"

    value = clipboard_text()
    print(f"Panodan alınan: {len(value)} karakter")
    print(f"Kayıt: \"{args.baslik}\" · {args.tur} · alan: {args.ek or args.alan} · "
          f"{'klavyede parolasız' if args.hizli else 'korumalı'}")

    password = ask_password()
    payload = json.dumps({"entries": [build_entry(args, value)]}, ensure_ascii=False)
    envelope = encrypt(payload, password)

    path = args.cikti or os.path.expanduser(
        f"~/Desktop/vault-{time.strftime('%Y%m%d-%H%M%S')}.vaultbak"
    )
    with open(path, "w", encoding="utf-8") as f:
        json.dump(envelope, f, indent=2, ensure_ascii=False)
    os.chmod(path, 0o600)
    print(f"\nŞifreli dosya: {path}")

    if args.push:
        remote = push(path)
        print(f"Telefona kopyalandı: {remote}")

    print(
        "\nTelefonda: Ayarlar → Yedekten geri yükle → dosyayı seç → yedek parolası\n"
        "→ \"Mevcuta ekle\" (mevcut kayıtların korunur).\n"
        "İçe aktardıktan sonra dosyayı sil:\n"
        f"  rm {path}"
        + (f"\n  {ADB} shell rm {remote}" if args.push else "")
    )


if __name__ == "__main__":
    main()

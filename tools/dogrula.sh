#!/bin/sh
# dogrula.sh — aktarım araçlarının bütünlük denetimi (vault_takip SEC-023).
#
# Zincir üç halkalı ve her halka bir öncekini doğrular:
#   1. Dosyalar   -> tools/SHA256SUMS  (yerel, git'te izlenir)
#   2. SHA256SUMS -> GitHub'daki kopya (uzak: yerel değişiklik gizlenemez)
#   3. SHA256SUMS -> Vault'taki çıpa   (telefon: ele geçmiş Mac değiştiremez)
#
# Kullanım:
#   tools/dogrula.sh          # denetle
#   tools/dogrula.sh --yaz    # araçlar değiştiyse referansı yenile
#   tools/dogrula.sh --cipa   # telefondaki kayıtla karşılaştırılacak tek değer
set -eu

cd "$(dirname "$0")/.."
DOSYALAR="tools/aktar.html tools/vault-clip.py tools/dogrula.sh tools/vendor/segno/encoder.py tools/vendor/segno/__init__.py"
MASA="$HOME/Desktop/aktar.html"

case "${1:-}" in
  --yaz)
    shasum -a 256 $DOSYALAR > tools/SHA256SUMS
    echo "tools/SHA256SUMS yenilendi. Yeni çıpa (Vault'taki kayda yaz):"
    shasum -a 256 tools/SHA256SUMS | cut -d' ' -f1
    echo "Not: değişikliği commit'lemeyi unutma — uzak halka ona bakıyor."
    exit 0
    ;;
  --cipa)
    shasum -a 256 tools/SHA256SUMS | cut -d' ' -f1
    exit 0
    ;;
esac

hata=0

echo "1) Dosyalar → SHA256SUMS"
if shasum -a 256 -c tools/SHA256SUMS >/dev/null 2>&1; then
  echo "   ✓ listedeki dosyaların hepsi referansla aynı (bu betik dâhil)"
else
  echo "   ✗ FARK VAR:"
  shasum -a 256 -c tools/SHA256SUMS 2>&1 | grep -v ': OK$' | sed 's/^/     /'
  hata=1
fi

echo "2) Masaüstü kopyası → depo kopyası"
if [ -L "$MASA" ]; then
  hedef=$(readlink "$MASA")
  case "$hedef" in
    *tools/aktar.html) echo "   ✓ masaüstündeki bir BAĞ (kopya yok, sürüklenme imkânsız)" ;;
    *) echo "   ✗ masaüstündeki bağ beklenmedik yeri gösteriyor: $hedef"; hata=1 ;;
  esac
elif [ -f "$MASA" ]; then
  a=$(shasum -a 256 "$MASA" | cut -d' ' -f1)
  b=$(shasum -a 256 tools/aktar.html | cut -d' ' -f1)
  if [ "$a" = "$b" ]; then
    echo "   ✓ masaüstündeki aktar.html depodakiyle aynı"
  else
    echo "   ✗ masaüstü kopyası FARKLI — depodan yeniden kopyala:"
    echo "     cp tools/aktar.html \"$MASA\""
    hata=1
  fi
else
  echo "   – masaüstünde kopya yok (atlandı)"
fi

echo "3) Yerel → GitHub (uzak halka)"
dal=$(git rev-parse --abbrev-ref HEAD)
if git fetch -q origin "$dal" 2>/dev/null; then
  if git diff --quiet "origin/$dal" -- tools/; then
    echo "   ✓ tools/ GitHub'daki hâliyle birebir"
  else
    echo "   ✗ yerel tools/ uzaktan FARKLI:"
    git diff --stat "origin/$dal" -- tools/ | sed 's/^/     /'
    hata=1
  fi
else
  echo "   – uzak sorgulanamadı (ağ yok?) — bu halka KOŞMADI, 'temiz' sayma"
fi

echo "4) Çıpa (telefondaki kayıtla elle karşılaştır)"
echo "   Not: kurcalanmış bir betik kendi hakkında yalan söyleyebilir."
echo "   Şüphede betiğe hiç güvenme, iki komutu elle koş:"
echo "     shasum -a 256 -c tools/SHA256SUMS"
echo "     git fetch -q && git diff --stat origin/$dal -- tools/"
echo "   $(shasum -a 256 tools/SHA256SUMS | cut -d' ' -f1)"

[ "$hata" -eq 0 ] && echo "SONUÇ: temiz" || echo "SONUÇ: FARK VAR — kullanmadan önce incele"
exit "$hata"

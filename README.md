# Vault 🔐

Kişisel kullanım için Android şifre / kart / hassas veri kasası.
Tamamen çevrimdışı çalışır; hiçbir veri internete gönderilmez.

## Özellikler

- **Kayıt türleri**: Hesap/Şifre, Kart (kart no, son kullanma, CVV, IBAN), Güvenli Not
- **Güçlü şifreleme**: Tüm hassas alanlar AES-256-GCM ile şifrelenir; anahtar ana
  paroladan PBKDF2-HMAC-SHA256 (310.000 tur) ile türetilir
- **Biyometrik kilit açma** (opsiyonel, parmak izi/yüz) + otomatik kilitlenme
- **Şifreli yedekleme**: `.vaultbak` dosyası olarak istediğin yere (Drive, SD kart,
  USB...) kaydet; **telefon sıfırlansa veya değişse bile** dosya + yedek parolası
  ile tüm veriler geri yüklenir
- **Vault Klavyesi**: Klavye eklentisi ile herhangi bir uygulamada şifre, kart no
  vb. bilgileri doğrudan ilgili alana yazdır (panoya kopyalamadan)
- **Pano koruması**: Kopyalanan değerler 45 saniye sonra panodan otomatik silinir,
  Android 13+ pano önizlemesinde gizli işaretlenir
- Ekran görüntüsü ve "son uygulamalar" önizlemesi engellenir (`FLAG_SECURE`)
- Sistem yedeklemesi (Google yedekleme / cihaz aktarımı) bilinçli olarak kapalıdır;
  yedekleme yalnızca şifreli dışa aktarma ile yapılır

## Güvenlik modeli

```
Ana parola ──PBKDF2(310k)──▶ KEK ──AES-GCM sarma──▶ dataKey (rastgele 256 bit)
                                                        │
                                                        ▼
                                          Tüm kayıtlar AES-256-GCM ile şifreli
```

- `dataKey` yalnızca kilit açıkken bellekte tutulur; zaman aşımında silinir.
- Biyometrik açma, `dataKey`'in Android Keystore'daki donanım destekli bir anahtarla
  ikinci kez sarılmasıyla çalışır. Keystore cihaz sıfırlamada kaybolur ama bu sadece
  kolaylık katmanıdır — ana parola her zaman çalışır.
- Yedek dosyası cihazdan tamamen bağımsızdır: kendi tuzu (salt) ve PBKDF2
  parametreleri dosya başlığında durur, içerik AES-256-GCM ile şifrelidir.
  Yanlış parola GCM doğrulamasında yakalanır.
- **Ana parolanı veya yedek parolanı unutursan verilerin kurtarılamaz.** Bu bir
  hata değil, tasarım gereğidir (arka kapı yok).

## Kurulum

1. Projeyi Android Studio ile aç (veya `gradle assembleRelease`)
2. Oluşan APK'yı telefonuna kur (bilinmeyen kaynaklara izin vermen gerekir)
3. İlk açılışta ana parolanı belirle (en az 8 karakter; unutma!)

### Klavyeyi etkinleştirme

1. Vault → Ayarlar → **Klavye ayarlarını aç** → Vault Klavyesi'ni etkinleştir
2. Herhangi bir uygulamada metin alanına dokun, klavye değiştiriciden
   (genelde sağ alttaki klavye simgesi) **Vault Klavyesi**'ni seç
3. Kaydı seç → hangi alanı yazmak istediğine dokun ("Şifre yaz" vb.)
4. `ABC` tuşu ile normal klavyene geri dön

> Not: Klavyenin bilgileri yazabilmesi için kasanın kilidinin açık olması gerekir
> (uygulamayı açıp kilidini açman yeterli; otomatik kilitlenme süresi ayarlardan
> değiştirilebilir).

## Yedekleme / Geri yükleme

- **Yedek al**: Ayarlar → *Şifreli yedek al* → konum seç → yedek parolası belirle.
  Dosyayı Google Drive'a, e-postana, SD karta — istediğin yere koyabilirsin;
  içerik şifreli olduğu için dosyanın ele geçmesi tek başına bir şey ifade etmez
  (parolan güçlüyse).
- **Geri yükle**: Ayarlar → *Yedekten geri yükle* → dosyayı seç → yedek parolasını
  gir → "Mevcuta ekle" veya "Tümünü değiştir".
- Yeni/sıfırlanmış telefonda: uygulamayı kur → yeni ana parola belirle →
  yedekten geri yükle. Hepsi bu.

💡 Öneri: Önemli bir değişiklikten sonra yeni bir yedek al ve en az iki farklı
yerde (ör. Drive + fiziksel ortam) sakla.

## Teknik

- Kotlin, Jetpack Compose (Material 3), Room, androidx.biometric
- minSdk 26 (Android 8.0), targetSdk 35
- Üçüncü taraf ağ/analitik kütüphanesi yok; `INTERNET` izni bile yok

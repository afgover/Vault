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
- **Otomatik doldurma**: Android'in otomatik doldurma servisi olarak çalışır;
  uygulama ve tarayıcılardaki giriş/kart formlarına dokununca kayıtların
  doldurma seçeneği olarak çıkar, yeni girdiğin bilgileri kaydetmeyi teklif eder
- **Vault Klavyesi**: Klavye eklentisi ile herhangi bir uygulamada şifre, kart no
  vb. bilgileri doğrudan ilgili alana yazdır (panoya kopyalamadan)
- **Bilgisayara yazma (Bluetooth klavye)**: Telefon, bilgisayara Bluetooth
  klavye olarak bağlanır ve seçtiğin şifreyi/alanı tuş basımları halinde
  doğrudan bilgisayardaki imlecin olduğu alana yazar. Bilgisayara hiçbir
  yazılım kurulmaz; Windows/Mac/Linux fark etmez (Android 9+ gerektirir)
- **Şifre üretici**: Kriptografik rastgelelikle (SecureRandom) 8–64 karakter
  şifre üretimi; karakter sınıfı seçimi, karışan karakterleri eleme ve entropi
  (bit) göstergesi. Ana ekrandaki 🎲 simgesinden ya da kayıt düzenlerken şifre
  alanının yanından erişilir
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

1. Projeyi Android Studio ile aç ve "Run" ile telefonuna yükle
   (veya komut satırından `./gradlew assembleDebug` → APK:
   `app/build/outputs/apk/debug/app-debug.apk`)
2. APK'yı elle kuruyorsan bilinmeyen kaynaklara izin vermen gerekir
3. İlk açılışta ana parolanı belirle (en az 8 karakter; unutma!)

### Otomatik doldurmayı etkinleştirme

1. Vault → Ayarlar → **Otomatik doldurmayı etkinleştir** → açılan sistem
   ekranında Vault'u seç (Android 8.0+)
2. Herhangi bir uygulamada/tarayıcıda kullanıcı adı, şifre veya kart alanına
   dokun → klavyenin üstünde Vault kayıtların çıkar → birine dokun, alanlar dolar
3. Kasa kilitliyse önce **"Doldurmak için kilidi aç"** çıkar; parola ya da
   parmak izi ile açtıktan sonra seçenekler listelenir
4. Bir sitede yeni kullanıcı adı/şifre girip gönderdiğinde Android
   "Vault'a kaydedilsin mi?" diye sorar (kasa açıkken)

> Alanlar `autofillHints` ile beyan edilmemişse alan adı/ipucu metni ve klavye
> türünden tahmin edilir (Türkçe ve İngilizce anahtar kelimeler). Kasa kilitliyken
> sisteme hiçbir kayıt verilmez — kilit açma ekranı doldurma seçeneklerini ancak
> kilit açıldıktan sonra üretir.

### Klavyeyi etkinleştirme

1. Vault → Ayarlar → **Klavye ayarlarını aç** → Vault Klavyesi'ni etkinleştir
2. Herhangi bir uygulamada metin alanına dokun, klavye değiştiriciden
   (genelde sağ alttaki klavye simgesi) **Vault Klavyesi**'ni seç
3. Kaydı seç → hangi alanı yazmak istediğine dokun ("Şifre yaz" vb.)
4. `ABC` tuşu ile normal klavyene geri dön

> Not: Klavyenin bilgileri yazabilmesi için kasanın kilidinin açık olması gerekir
> (uygulamayı açıp kilidini açman yeterli; otomatik kilitlenme süresi ayarlardan
> değiştirilebilir).

### Bilgisayara yazma (Bluetooth klavye)

1. Telefonu bilgisayarla Bluetooth'tan **bir kez eşleştir** (normal klavye
   eşleştirir gibi; telefon Bluetooth ayarlarından)
2. Vault'ta kaydı aç → ilgili alanın yanındaki 💻 simgesine dokun
3. Listeden bilgisayarını seç → bağlanınca bilgisayarın klavye düzenini seç
   (Türkçe Q / US)
4. Bilgisayarda imleci şifre kutusuna getir → telefonda **Yaz**'a bas →
   3 saniyelik geri sayımdan sonra değer tuş tuş yazılır

> Değer panodan veya ağdan geçmez; Bluetooth bağlantısının kendi şifrelemesi
> içinde iletilir. Türkçe Q düzeninde birkaç nadir özel karakter yazılamazsa
> uygulama uyarır — o durumda US düzenini seçip bilgisayarı da geçici olarak
> İngilizce düzene almak yeterlidir.

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

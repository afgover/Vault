# vault-relay — vault.gover.us

Tek kullanımlık şifreli zarf relay'i (Cloudflare Worker + KV).

- `POST /api/b?ttl=<sn>` — token'lı yükleme (`Authorization: Bearer …`,
  token Mac Keychain'de: `vault.gover.us-relay`). Yalnız Vault zarfı kabul
  edilir, 64 KB sınır. Cevap: `{url, ttl}`.
- `GET /b/<32 hex>` — sayfayı bir kez gösterir ve blobu siler (yak-oku);
  TTL en fazla 24 saat. `HEAD` blobu yakmaz (ön-getirme koruması).
- Sunucu yalnız **şifreli** zarf görür; başlıklar dahil her şey zarfın
  içinde AES-256-GCM ile şifrelidir. Duvar anahtardır, URL azaltıcıdır.
- Yak-oku "en iyi çaba"dır: KV nihai tutarlı olduğundan silme birkaç
  saniyelik pencerede ikinci bir okumaya yenik düşebilir; asıl sınır TTL.

Deploy: `cd relay && npx wrangler deploy` · Token yenileme:
`openssl rand -hex 32` → `wrangler secret put UPLOAD_TOKEN` + Keychain güncelle.

Takip kaydı: `afgover/vault_takip` → SEC-020, K-020, A-2026-08-21-003.

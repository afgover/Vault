/**
 * vault.gover.us — tek kullanımlık şifreli zarf relay'i.
 *
 * Güvenlik modeli (vault_takip SEC-020):
 *  - Buraya yalnız ŞİFRELİ .vaultbak zarfı gelir; başlıklar dahil her şey
 *    zarfın içinde AES-256-GCM ile şifrelidir. Sunucu hiçbir düz metin görmez.
 *  - Duvar anahtardır; URL yalnız azaltıcıdır (128-bit rastgele yol).
 *  - Yak-oku: ilk sayfa görüntülemesinde blob silinir. KV nihai tutarlı
 *    olduğu için silme birkaç saniyelik pencerede ikinci bir okumaya yenik
 *    düşebilir — asıl sınır TTL'dir (≤24 saat, varsayılan 24 saat).
 *  - Yükleme token'lıdır; açık uç anonim depoya dönüşür.
 */

const MAX_BODY = 64 * 1024;
const TTL_MIN = 60;
const TTL_MAX = 86_400;

/**
 * Yerel dosya istemcisi (aktar.html, file:// kökeni) POST atabilsin diye
 * yükleme ucu CORS'a açık. Güvenlik katmanı CORS değil Bearer token'dır;
 * çerez/oturum olmadığı için "*" ek yüzey açmaz.
 */
const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Access-Control-Max-Age": "86400",
};

const TEXT_HEADERS = {
  "Content-Type": "text/plain; charset=utf-8",
  "Referrer-Policy": "no-referrer",
  "X-Robots-Tag": "noindex, nofollow, noarchive",
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Content-Security-Policy": "default-src 'none'; sandbox",
};

function notFound() {
  return new Response(
    "Burada bir sey yok. Baglanti ya hic var olmadi, ya suresi doldu, " +
      "ya da bir kez goruntulenip silindi.\n",
    { status: 404, headers: TEXT_HEADERS }
  );
}

/*
 * Not: sayfa (HTML) sunumu 2026-08-24'te kaldırıldı — Güvenli Tarama
 * işaretlemesinin sinyallerini taşıyordu (SEC-027). Zarf artık düz metin
 * olarak sunulur; indirme `?indir=1` ile aynı içeriği dosya olarak verir.
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS" && url.pathname === "/api/b") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    if (request.method === "POST" && url.pathname === "/api/b") {
      const auth = request.headers.get("Authorization") ?? "";
      if (!env.UPLOAD_TOKEN || auth !== `Bearer ${env.UPLOAD_TOKEN}`) {
        return new Response("unauthorized", { status: 401, headers: CORS_HEADERS });
      }
      const body = await request.text();
      if (!body || body.length > MAX_BODY) {
        return new Response("bad size", { status: 400, headers: CORS_HEADERS });
      }
      // Yalnız Vault zarfı kabul edilir — uç, genel amaçlı depo değildir.
      let ok = false;
      try {
        const j = JSON.parse(body);
        ok = j.app === "vault" && typeof j.data === "string" && typeof j.kdf === "object";
      } catch {}
      if (!ok) return new Response("bad format", { status: 400, headers: CORS_HEADERS });

      const reqTtl = parseInt(url.searchParams.get("ttl") ?? "", 10);
      const ttl = Math.min(Math.max(Number.isFinite(reqTtl) ? reqTtl : TTL_MAX, TTL_MIN), TTL_MAX);
      const id = [...crypto.getRandomValues(new Uint8Array(16))]
        .map((b) => b.toString(16).padStart(2, "0"))
        .join("");
      await env.BLOBS.put(id, body, { expirationTtl: ttl });
      return Response.json({ url: `${url.origin}/b/${id}`, ttl }, { headers: CORS_HEADERS });
    }

    const m = url.pathname.match(/^\/b\/([0-9a-f]{32})$/);
    if (m && (request.method === "GET" || request.method === "HEAD")) {
      const blob = await env.BLOBS.get(m[1]);
      if (blob === null) return notFound();
      if (request.method === "HEAD") {
        // Ön-getirme/önizleme HEAD atarsa blob yakılmaz.
        return new Response(null, { headers: TEXT_HEADERS });
      }
      await env.BLOBS.delete(m[1]); // yak-oku

      // İndirme isteği: aynı içerik, dosya olarak.
      if (url.searchParams.has("indir")) {
        return new Response(blob, {
          headers: {
            ...TEXT_HEADERS,
            "Content-Type": "application/octet-stream",
            "Content-Disposition": 'attachment; filename="vault-aktarim.vaultbak"',
          },
        });
      }
      return new Response(blob, { headers: TEXT_HEADERS });
    }

    return new Response("vault relay", {
      status: 404,
      headers: { "X-Robots-Tag": "noindex", "Cache-Control": "no-store" },
    });
  },
};

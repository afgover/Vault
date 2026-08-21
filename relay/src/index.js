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

const PAGE_HEADERS = {
  "Content-Type": "text/html; charset=utf-8",
  "Referrer-Policy": "no-referrer",
  "X-Robots-Tag": "noindex, nofollow, noarchive",
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Content-Security-Policy":
    "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'",
};

function esc(s) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function notFound() {
  return new Response(
    `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Vault Relay</title>
<body style="font-family:system-ui;background:#111;color:#ddd;display:grid;place-items:center;min-height:100vh;margin:0">
<div style="text-align:center;padding:2rem">
<h1 style="font-size:1.2rem">Burada bir şey yok</h1>
<p style="color:#888">Bağlantı ya hiç var olmadı, ya süresi doldu, ya da bir kez görüntülenip yandı.</p>
</div></body>`,
    { status: 404, headers: PAGE_HEADERS }
  );
}

function blobPage(blob) {
  return `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Vault aktarımı</title>
<body style="font-family:system-ui;background:#111;color:#ddd;margin:0;padding:1.2rem;max-width:640px;margin-inline:auto">
<h1 style="font-size:1.1rem">🔐 Şifreli Vault zarfı</h1>
<p style="color:#e0a800;font-size:.9rem">⚠️ Bu sayfa <b>bir kez</b> görüntülenir — kapatırsan içerik sunucudan silinmiş olur.
Aşağıdaki metin şifrelidir; yedek parolan olmadan hiçbir şey ifade etmez.</p>
<p style="color:#888;font-size:.85rem">${blob.length} karakter</p>
<textarea id="z" readonly style="width:100%;height:9rem;background:#1a1a1a;color:#9c9;border:1px solid #333;border-radius:8px;padding:.6rem;font-family:monospace;font-size:.7rem">${esc(blob)}</textarea>
<div style="display:flex;gap:.6rem;margin-top:.8rem;flex-wrap:wrap">
<button id="dl" style="flex:1;padding:.9rem;background:#2a6;border:0;border-radius:8px;color:#fff;font-size:1rem">💾 .vaultbak indir</button>
<button id="cp" style="flex:1;padding:.9rem;background:#246;border:0;border-radius:8px;color:#fff;font-size:1rem">📋 Kopyala</button>
</div>
<p style="color:#888;font-size:.85rem;margin-top:1rem">Telefonda: <b>Vault → Ayarlar → Yedekten geri yükle</b> → indirilen dosyayı seç
→ yedek parolası → <b>Mevcuta ekle</b>. Bittiğinde dosyayı Download'dan sil.</p>
<script>
const t=document.getElementById("z");
document.getElementById("cp").onclick=async e=>{await navigator.clipboard.writeText(t.value);e.target.textContent="✓ Kopyalandı"};
document.getElementById("dl").onclick=e=>{
  const a=document.createElement("a");
  a.href=URL.createObjectURL(new Blob([t.value],{type:"application/octet-stream"}));
  a.download="vault-aktarim.vaultbak";a.click();URL.revokeObjectURL(a.href);
  e.target.textContent="✓ İndirildi";
};
</script></body>`;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "POST" && url.pathname === "/api/b") {
      const auth = request.headers.get("Authorization") ?? "";
      if (!env.UPLOAD_TOKEN || auth !== `Bearer ${env.UPLOAD_TOKEN}`) {
        return new Response("unauthorized", { status: 401 });
      }
      const body = await request.text();
      if (!body || body.length > MAX_BODY) {
        return new Response("bad size", { status: 400 });
      }
      // Yalnız Vault zarfı kabul edilir — uç, genel amaçlı depo değildir.
      let ok = false;
      try {
        const j = JSON.parse(body);
        ok = j.app === "vault" && typeof j.data === "string" && typeof j.kdf === "object";
      } catch {}
      if (!ok) return new Response("bad format", { status: 400 });

      const reqTtl = parseInt(url.searchParams.get("ttl") ?? "", 10);
      const ttl = Math.min(Math.max(Number.isFinite(reqTtl) ? reqTtl : TTL_MAX, TTL_MIN), TTL_MAX);
      const id = [...crypto.getRandomValues(new Uint8Array(16))]
        .map((b) => b.toString(16).padStart(2, "0"))
        .join("");
      await env.BLOBS.put(id, body, { expirationTtl: ttl });
      return Response.json({ url: `${url.origin}/b/${id}`, ttl });
    }

    const m = url.pathname.match(/^\/b\/([0-9a-f]{32})$/);
    if (m && (request.method === "GET" || request.method === "HEAD")) {
      const blob = await env.BLOBS.get(m[1]);
      if (blob === null) return notFound();
      if (request.method === "HEAD") {
        // Ön-getirme/önizleme HEAD atarsa blob yakılmaz.
        return new Response(null, { headers: PAGE_HEADERS });
      }
      await env.BLOBS.delete(m[1]); // yak-oku
      return new Response(blobPage(blob), { headers: PAGE_HEADERS });
    }

    return new Response("vault relay", {
      status: 404,
      headers: { "X-Robots-Tag": "noindex", "Cache-Control": "no-store" },
    });
  },
};

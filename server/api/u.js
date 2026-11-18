// Profil bağlantısı: https://aurachat-amber.vercel.app/api/u?u=UID
// Telefonda AuraChat yüklüyse uygulamayı açar, değilse bilgi sayfası gösterir.
module.exports = (req, res) => {
  const u = String((req.query && req.query.u) || '').replace(/[^A-Za-z0-9]/g, '').slice(0, 64);
  const app = 'aurachat://u/' + u;
  const intent = 'intent://u/' + u + '#Intent;scheme=aurachat;package=com.aurachat;end';
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  res.status(200).send(`<!doctype html>
<html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>AuraChat</title>
<style>
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0b141a;color:#e9edef;font-family:system-ui,sans-serif;text-align:center}
.c{padding:28px;max-width:360px}
h1{font-size:22px;margin:0 0 10px}
p{color:#8696a0;font-size:15px;line-height:1.5;margin:0 0 22px}
a{display:inline-block;background:#22c55e;color:#04210f;text-decoration:none;font-weight:600;padding:13px 28px;border-radius:50px;font-size:16px}
</style></head><body><div class="c">
<h1>AuraChat'te sohbet et</h1>
<p>Bu bağlantı bir AuraChat profiline ait. AuraChat yüklüyse uygulama otomatik açılır.</p>
<a id="open" href="${intent}">AuraChat'te aç</a>
</div>
<script>
${u ? `setTimeout(function(){ location.href = ${JSON.stringify(intent)}; }, 250);` : ''}
</script></body></html>`);
};

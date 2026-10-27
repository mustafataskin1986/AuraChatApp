import { admin } from './_push.js';

// Yönetici e-postaları (virgülle ayrılmış). Vercel ortam değişkeni ADMIN_EMAILS ile değiştirilebilir.
const ADMINS = (process.env.ADMIN_EMAILS || 'mustafataskin1986@gmail.com')
  .split(',').map((s) => s.trim().toLowerCase()).filter(Boolean);

const MAX_TITLE = 100;
const MAX_BODY = 400;

function tokenOf(u) {
  return u.fcmToken || u.fcm_token || u.pushToken || '';
}

function ms(v) {
  if (!v) return 0;
  if (typeof v.toMillis === 'function') return v.toMillis();
  if (typeof v === 'number') return v;
  return 0;
}

export default async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Yalnızca POST kabul edilir.' });

  const authHeader = req.headers.authorization || '';
  const idToken = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : '';
  if (!idToken) return res.status(401).json({ error: 'Yetkisiz.' });

  let decoded;
  try {
    decoded = await admin.auth().verifyIdToken(idToken);
  } catch (e) {
    return res.status(401).json({ error: 'Geçersiz oturum.' });
  }
  const email = String(decoded.email || '').toLowerCase();
  if (!email || decoded.email_verified === false || !ADMINS.includes(email)) {
    return res.status(403).json({ error: 'Yönetici değilsiniz.' });
  }

  const { action } = req.body || {};
  const db = admin.firestore();

  try {
    if (action === 'users') {
      const snap = await db.collection('users').limit(3000).get();
      const users = snap.docs.map((d) => {
        const u = d.data();
        return {
          uid: d.id,
          name: u.name || '',
          email: u.email || '',
          phone: u.phone || '',
          platform: u.platform || '',
          hasToken: !!tokenOf(u),
          lastSeen: ms(u.lastSeen),
        };
      }).sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
      return res.status(200).json({ users });
    }

    if (action === 'send') {
      const { title, body, mode, uids } = req.body;
      if (!title || !body) return res.status(400).json({ error: 'Başlık ve metin gerekli.' });
      const kind = mode === 'inapp' ? 'admin_inapp' : 'admin';
      let docs;
      if (Array.isArray(uids) && uids.length) {
        const refs = uids.slice(0, 500).map((u) => db.doc(`users/${String(u)}`));
        docs = await db.getAll(...refs);
        docs = docs.filter((d) => d.exists);
      } else {
        docs = (await db.collection('users').limit(3000).get()).docs;
      }
      const tokens = docs.map((d) => tokenOf(d.data())).filter(Boolean);
      const data = { kind, title: String(title).slice(0, MAX_TITLE), body: String(body).slice(0, MAX_BODY) };
      let sent = 0;
      let failed = 0;
      for (let i = 0; i < tokens.length; i += 500) {
        const batch = tokens.slice(i, i + 500);
        const r = await admin.messaging().sendEachForMulticast({
          tokens: batch,
          data,
          android: { priority: 'high', ttl: kind === 'admin_inapp' ? 60000 : 86400000 },
        });
        sent += r.successCount;
        failed += r.failureCount;
      }
      return res.status(200).json({ sent, failed, targets: docs.length, noToken: docs.length - tokens.length });
    }

    return res.status(400).json({ error: 'Bilinmeyen işlem.' });
  } catch (e) {
    console.error('admin hata', e && e.message);
    return res.status(500).json({ error: 'Sunucu hatası.', detail: String((e && e.message) || '').slice(0, 200) });
  }
}

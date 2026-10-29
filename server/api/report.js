import { admin } from './_push.js';

// Kullanıcıdan gelen şikayetler: oturum açmış herkes gönderebilir, yalnızca yönetici (api/admin) okur
const REASONS = ['Spam', 'Taciz / zorbalık', 'Uygunsuz içerik', 'Sahte hesap', 'Diğer'];

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
  const b = req.body || {};
  const reason = REASONS.includes(b.reason) ? b.reason : 'Diğer';
  const chatId = String(b.chatId || '').slice(0, 200);
  if (!chatId) return res.status(400).json({ error: 'Sohbet bilgisi eksik.' });
  const db = admin.firestore();
  try {
    // spam'i önle: aynı kişi aynı sohbeti son 10 dakikada tekrar şikayet edemez
    const since = Date.now() - 10 * 60 * 1000;
    const dup = await db.collection('reports').where('reporterUid', '==', decoded.uid).where('chatId', '==', chatId).where('at', '>', since).limit(1).get();
    if (!dup.empty) return res.status(200).json({ ok: true });
    await db.collection('reports').add({
      reporterUid: decoded.uid,
      reporterName: String(b.reporterName || '').slice(0, 100),
      reportedUid: String(b.reportedUid || '').slice(0, 128),
      reportedName: String(b.reportedName || '').slice(0, 100),
      chatId,
      isGroup: b.isGroup === true,
      reason,
      note: String(b.note || '').slice(0, 500),
      at: Date.now(),
      status: 'open',
    });
    return res.status(200).json({ ok: true });
  } catch (e) {
    return res.status(500).json({ error: 'Kaydedilemedi.' });
  }
}

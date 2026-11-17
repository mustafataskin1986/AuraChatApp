import { admin, ID_RE } from './_push.js';

// Bir kişi durum ekleyince, onun bildirimlerini açmış kişilere "Durum eklendi" bildirimi gönderir.
// Abonelik: users/{alıcı}.statusNotifUids dizisi (alıcının kendi belgesi).
export default async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).json({ error: 'Yalnızca POST kabul edilir.' });
  const authHeader = req.headers.authorization || '';
  const idToken = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : '';
  if (!idToken) return res.status(401).json({ error: 'Yetkisiz.' });
  let senderUid;
  try {
    senderUid = (await admin.auth().verifyIdToken(idToken)).uid;
  } catch (e) {
    return res.status(401).json({ error: 'Geçersiz oturum.' });
  }
  const statusId = String((req.body || {}).statusId || '');
  if (!ID_RE.test(statusId)) return res.status(400).json({ error: 'Geçersiz durum.' });

  try {
    const db = admin.firestore();
    const st = await db.doc(`statuses/${statusId}`).get();
    // yalnızca durumun sahibi bildirim tetikleyebilir, ve yalnızca yeni (son 10 dk) durum için
    if (!st.exists || st.data().uid !== senderUid) return res.status(403).json({ error: 'İzin yok.' });
    if (Date.now() - (st.data().createdAt || 0) > 10 * 60 * 1000 + 60000) return res.status(200).json({ success: false, reason: 'old' });

    const senderSnap = await db.doc(`users/${senderUid}`).get();
    const sd = senderSnap.exists ? senderSnap.data() : {};
    const name = String(sd.name || sd.displayName || 'AuraChat').slice(0, 100);
    const senderBlocked = Array.isArray(sd.blockedUids) ? sd.blockedUids : [];

    const subs = await db.collection('users').where('statusNotifUids', 'array-contains', senderUid).limit(500).get();
    let sent = 0;
    await Promise.all(subs.docs.map(async (doc) => {
      const u = doc.data();
      const uid = doc.id;
      if (uid === senderUid) return;
      const blocked = Array.isArray(u.blockedUids) ? u.blockedUids : [];
      if (blocked.includes(senderUid) || senderBlocked.includes(uid)) return;
      const token = u.fcmToken || u.fcm_token || u.pushToken;
      if (!token) return;
      const data = { kind: 'status', title: name, body: 'Durum eklendi', statusId, senderUid };
      const platformStr = String(u.platform || '').toLowerCase();
      const isWeb = platformStr.includes('pwa') || platformStr.includes('web');
      const message = (isWeb || u.nativeMsgStyle === true)
        ? { data, android: { priority: 'high' }, token }
        : { notification: { title: name, body: 'Durum eklendi' }, data, android: { priority: 'high', notification: { channelId: 'aurachat-messages' } }, token };
      try {
        await admin.messaging().send(message);
        sent++;
      } catch (err) {
        const code = (err && err.code) || '';
        if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-registration-token') {
          try { await db.doc(`users/${uid}`).update({ fcmToken: admin.firestore.FieldValue.delete() }); } catch (e) {}
        }
      }
    }));
    return res.status(200).json({ success: true, sent });
  } catch (error) {
    console.error('status-notify hatası:', error && error.message);
    return res.status(500).json({ error: 'Bildirim gönderilemedi.' });
  }
}

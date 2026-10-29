package com.aurachat.app.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurachat.app.data.ChatTarget
import com.aurachat.app.data.Repo
import com.aurachat.app.data.UserProfile
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

private const val QR_PREFIX = "aurachat://u/"

private fun makeQr(text: String): Bitmap? = try {
    val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val scale = 10
    val bmp = Bitmap.createBitmap(m.width * scale, m.height * scale, Bitmap.Config.ARGB_8888)
    val px = IntArray(bmp.width * bmp.height)
    for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
        px[y * bmp.width + x] = if (m.get(x / scale, y / scale)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    bmp
} catch (e: Throwable) { null }

/** QR kodum / Kodu tara: sayfadayken ekran parlaklığı tam olur, çıkınca eski hâline döner */
@Composable
fun QrScreen(me: UserProfile, onBack: () -> Unit, onOpenChat: (ChatTarget) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    BackHandler { onBack() }

    // ekran ışığı: telefonda kısık olsa bile bu sayfalarda tam
    DisposableEffect(Unit) {
        var c: android.content.Context? = ctx
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        val w = (c as? android.app.Activity)?.window
        if (w != null) w.attributes = w.attributes.also { it.screenBrightness = 1f }
        onDispose {
            if (w != null) w.attributes = w.attributes.also { it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    fun handle(raw: String) {
        val uid = Regex("(?:aurachat://u/|[?&]u=)([A-Za-z0-9]{10,64})").find(raw)?.groupValues?.get(1)
        if (uid == null) { Toast.makeText(ctx, "Bu bir AuraChat QR kodu değil", Toast.LENGTH_SHORT).show(); return }
        if (uid == me.uid) { Toast.makeText(ctx, "Bu senin kodun", Toast.LENGTH_SHORT).show(); return }
        scope.launch {
            val p = try { Repo.loadProfile(uid) } catch (e: Throwable) { null }
            if (p == null) { Toast.makeText(ctx, "Kişi bulunamadı", Toast.LENGTH_SHORT).show(); return@launch }
            onOpenChat(ChatTarget(Repo.chatId(me.uid, uid), false, uid, p.name))
        }
    }

    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ScanContract()) { r ->
        r.contents?.let { handle(it) }
    }
    fun startScan() {
        try {
            val o = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("")
                .setBeepEnabled(false)
                .setOrientationLocked(true)
            scanLauncher.launch(o)
        } catch (e: Throwable) { Toast.makeText(ctx, "Tarayıcı açılamadı", Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(tab) { if (tab == 1) startScan() }

    Column(Modifier.fillMaxSize().background(AuraBg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White) }
            Text("QR kodu", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("QR KODUM", "KODU TARA").forEachIndexed { i, l ->
                Column(Modifier.weight(1f).clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l, color = if (tab == i) Color.White else AuraMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 14.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (tab == i) AuraGreen else Color.Transparent))
                }
            }
        }
        if (tab == 0) {
            val bmp = remember(me.uid) { makeQr(QR_PREFIX + me.uid) }
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(50.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AuraSurface).padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(me.name, me.avatar, 64.dp)
                    Text(me.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
                    Text("AuraChat kişisi", color = AuraMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                    Box(Modifier.size(210.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp)) {
                        if (bmp != null) Image(bmp.asImageBitmap(), "QR kodum", Modifier.fillMaxSize(), colorFilter = auraImageFilter())
                    }
                }
                Text(
                    "QR kodunuz size özeldir. QR kodunuzu paylaştığınız kişiler AuraChat ile bu kodu tarayıp sizinle sohbet başlatabilir.",
                    color = AuraMuted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp)
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Icon(Icons.Default.QrCodeScanner, null, tint = AuraMuted, modifier = Modifier.size(72.dp))
                Text("Bir kişinin AuraChat QR kodunu tara", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                Box(
                    Modifier.padding(top = 20.dp).clip(RoundedCornerShape(50)).background(AuraGreen).clickable { startScan() }.padding(horizontal = 28.dp, vertical = 12.dp)
                ) { Text("Taramayı başlat", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

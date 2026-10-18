@file:OptIn(ExperimentalFoundationApi::class)

package com.ion.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Data ----------

data class AppItem(
    val label: String,
    val pkg: String,
    val cls: String,
    val cat: String,
    val icon: ImageBitmap
)

val CATS = listOf("Social", "Work", "Media", "Finance", "Tools", "Games", "Camera", "System", "Other")
val ACCENTS = listOf(Color(0xFF6EA8FF), Color(0xFFFF6FAE), Color(0xFF3DDBB5), Color(0xFFFFB347), Color(0xFFB388FF))
val SHAPES: List<Shape> = listOf(RoundedCornerShape(30), CircleShape, RoundedCornerShape(12))
val FONTS = listOf(FontFamily.SansSerif, FontFamily.Serif, FontFamily.Monospace)

fun categorize(pkg: String, label: String, info: ApplicationInfo): String {
    val k = "$pkg $label".lowercase()
    fun has(vararg w: String) = w.any { k.contains(it) }
    return when {
        info.category == ApplicationInfo.CATEGORY_GAME || has("game", "games") -> "Games"
        has("whatsapp", "instagram", "telegram", "facebook", "twitter", "snapchat", "discord",
            "tiktok", "messenger", "linkedin", "reddit", "signal") -> "Social"
        has("bank", "wallet", "upi", "finance", "invest", "stock", "crypto", "paytm", "phonepe", "gpay") -> "Finance"
        has("camera", "gallery", "photos", "video editor", "snapseed") -> "Camera"
        has("gmail", "mail", "docs", "sheets", "slides", "drive", "calendar", "slack", "teams",
            "office", "notes", "zoom", "meet", "outlook", "notion", "keep") -> "Work"
        has("youtube", "music", "spotify", "netflix", "video", "player", "podcast", "prime", "hotstar") -> "Media"
        has("calculator", "clock", "files", "weather", "translate", "compass", "maps", "browser",
            "chrome", "flashlight", "recorder", "scanner", "vpn", "firefox") -> "Tools"
        has("settings", "dialer", "phone", "contacts", "messaging", "mms", "hihonor", "huawei", "honor") ||
            pkg.startsWith("com.android.") -> "System"
        else -> when (info.category) {
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Media"
            ApplicationInfo.CATEGORY_IMAGE -> "Camera"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_NEWS -> "Work"
            ApplicationInfo.CATEGORY_MAPS -> "Tools"
            else -> "Other"
        }
    }
}

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            val ai = it.activityInfo
            val label = it.loadLabel(pm).toString()
            AppItem(
                label, ai.packageName, ai.name,
                categorize(ai.packageName, label, ai.applicationInfo),
                it.loadIcon(pm).toBitmap(120, 120).asImageBitmap()
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun launch(ctx: Context, a: AppItem) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(a.pkg, a.cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

fun safeStart(ctx: Context, i: Intent) {
    try {
        ctx.startActivity(i)
    } catch (_: Exception) {
    }
}

// ---------- Saved settings ----------

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ion", Context.MODE_PRIVATE)
    var accent by mutableIntStateOf(sp.getInt("accent", 0)); private set
    var shape by mutableIntStateOf(sp.getInt("shape", 0)); private set
    var theme by mutableIntStateOf(sp.getInt("theme", 0)); private set
    var font by mutableIntStateOf(sp.getInt("font", 0)); private set
    var dock by mutableStateOf((sp.getString("dock", "") ?: "").split(",").filter { it.isNotBlank() }); private set

    fun set(key: String, v: Int) {
        when (key) {
            "accent" -> accent = v
            "shape" -> shape = v
            "theme" -> theme = v
            "font" -> font = v
        }
        sp.edit().putInt(key, v).apply()
    }

    fun togglePin(p: String) {
        dock = if (p in dock) dock - p else (dock + p).takeLast(4)
        sp.edit().putString("dock", dock.joinToString(",")).apply()
    }
}

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    private var homeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent { Launcher(prefs, homeTick) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeTick++ // Home button pressed: return to the home screen
    }
}

// ---------- UI ----------

@Composable
fun Launcher(prefs: Prefs, homeTick: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var reload by remember { mutableIntStateOf(0) }
    var drawer by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var startCat by remember { mutableStateOf("All") }
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(reload) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }
    LaunchedEffect(homeTick) { drawer = false; settings = false }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { reload++ }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    BackHandler(enabled = drawer || settings) { if (settings) settings = false else drawer = false }

    val dark = when (prefs.theme) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    val ink = if (dark) Color.White else Color(0xFF12141F)
    val mute = ink.copy(alpha = 0.65f)
    val glass = if (dark) Color(0x66181A2E) else Color(0x99FFFFFF)
    val sheetBg = if (dark) Color(0xFF12142A) else Color(0xFFF6F7FD)
    val drawerBg = if (dark) Color(0xF2070813) else Color(0xF2F1F2F8)
    val accent = ACCENTS[prefs.accent]
    val shape = SHAPES[prefs.shape]

    val is24 = DateFormat.is24HourFormat(ctx)
    val time = remember(now, is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now) }

    val groups = apps.groupBy { it.cat }
    val folderCats = CATS.filter { groups[it] != null }.take(6)
    val defaults = listOf("phone", "dialer", "message", "camera", "chrome")
        .mapNotNull { k -> apps.find { it.label.lowercase().contains(k) } }.distinct().take(4)
    val dockApps = prefs.dock.mapNotNull { p -> apps.find { it.pkg == p } }.ifEmpty { defaults }

    fun openDrawer(cat: String) { startCat = cat; drawer = true }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                if (dark) listOf(Color(0x66000000), Color(0x99000000)) else listOf(Color(0x33FFFFFF), Color(0x66FFFFFF))
            )
        )
    ) {
        // Home
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
                .pointerInput(drawer) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -120f) openDrawer("All") },
                        onVerticalDrag = { _, d -> total += d }
                    )
                }
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Text("⚙", fontSize = 22.sp, color = ink, modifier = Modifier.clickable { settings = true }.padding(8.dp))
            }
            Text(time, fontSize = 88.sp, fontWeight = FontWeight.ExtraLight, fontFamily = FONTS[prefs.font], color = ink)
            Text(date, color = mute, fontSize = 15.sp)
            Spacer(Modifier.height(28.dp))

            folderCats.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { c ->
                        val list = groups[c]!!.take(4)
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(glass)
                                .clickable { openDrawer(c) }.padding(14.dp)
                        ) {
                            Text(c, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { Image(it.icon, null, Modifier.weight(1f).aspectRatio(1f).clip(shape)) }
                                repeat(4 - list.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Swipe up for all apps", color = mute, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { openDrawer("All") }.padding(8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(32.dp)).background(glass).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                dockApps.forEach { a -> AppCell(a, shape, ink, a.pkg in prefs.dock, 56.dp, false, {}) { prefs.togglePin(a.pkg) } }
            }
        }

        AnimatedVisibility(
            drawer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Drawer(apps, prefs, shape, ink, mute, accent, drawerBg, startCat) { drawer = false }
        }

        if (settings) SettingsSheet(prefs, ink, mute, sheetBg, accent) { settings = false }
    }
}

@Composable
fun AppCell(
    a: AppItem, shape: Shape, ink: Color, pinned: Boolean, size: Dp, label: Boolean,
    onOpen: () -> Unit, onPin: () -> Unit
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.combinedClickable(onClick = { launch(ctx, a); onOpen() }, onLongClick = { menu = true }).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(a.icon, a.label, Modifier.size(size).clip(shape))
        if (label) {
            Text(a.label, color = ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp).widthIn(max = 72.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (pinned) "Remove from dock" else "Pin to dock") },
                onClick = { menu = false; onPin() })
            DropdownMenuItem(text = { Text("App info") }, onClick = {
                menu = false
                safeStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
            })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = {
                menu = false
                safeStart(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
            })
        }
    }
}

@Composable
fun Drawer(
    apps: List<AppItem>, prefs: Prefs, shape: Shape, ink: Color, mute: Color, accent: Color,
    bg: Color, initialCat: String, onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var cat by remember(initialCat) { mutableStateOf(initialCat) }
    val groups = apps.groupBy { it.cat }
    val shown: List<Pair<String, List<AppItem>>> =
        if (q.isNotBlank()) listOf("Results" to apps.filter { it.label.contains(q.trim(), true) })
        else CATS.filter { groups[it] != null && (cat == "All" || cat == it) }.map { it to groups[it]!! }

    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            placeholder = { Text("Search apps") },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ink, unfocusedTextColor = ink,
                focusedBorderColor = accent, unfocusedBorderColor = mute.copy(alpha = 0.4f),
                focusedPlaceholderColor = mute, unfocusedPlaceholderColor = mute, cursorColor = accent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                shown.firstOrNull()?.second?.firstOrNull()?.let { launch(ctx, it); onClose() }
            })
        )
        LazyRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All") + CATS.filter { groups[it] != null }) { c ->
                val on = c == cat && q.isBlank()
                Text(
                    c, fontSize = 13.sp, color = if (on) Color.White else ink,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) accent else ink.copy(alpha = 0.08f))
                        .clickable { cat = c; q = "" }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            shown.forEach { (name, list) ->
                item {
                    Text(name, color = mute, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
                items(list.chunked(4)) { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        row.forEach { a ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                AppCell(a, shape, ink, a.pkg in prefs.dock, 56.dp, true, onClose) { prefs.togglePin(a.pkg) }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSheet(prefs: Prefs, ink: Color, mute: Color, bg: Color, accent: Color, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = none, indication = null, onClick = onClose)
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .navigationBarsPadding().padding(20.dp)
        ) {
            Text("Customize", color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            Text("Accent", color = mute, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENTS.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c)
                            .border(2.dp, if (i == prefs.accent) ink else Color.Transparent, CircleShape)
                            .clickable { prefs.set("accent", i) }
                    )
                }
            }
            Opts("Icon shape", listOf("Squircle", "Circle", "Square"), prefs.shape, ink, mute, accent) { prefs.set("shape", it) }
            Opts("Clock font", listOf("Modern", "Serif", "Mono"), prefs.font, ink, mute, accent) { prefs.set("font", it) }
            Opts("Theme", listOf("Auto", "Dark", "Light"), prefs.theme, ink, mute, accent) { prefs.set("theme", it) }

            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Wallpaper" to { safeStart(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) },
                    "Display & AOD" to { safeStart(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS)) }).forEach { (t, f) ->
                    Text(t, color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(accent).clickable { f() }
                            .padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
fun Opts(title: String, items: List<String>, sel: Int, ink: Color, mute: Color, accent: Color, onSel: (Int) -> Unit) {
    Text(title, color = mute, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, s ->
            Text(
                s, color = if (i == sel) Color.White else ink, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (i == sel) accent else ink.copy(alpha = 0.08f))
                    .clickable { onSel(i) }.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

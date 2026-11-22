package com.namazim.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// Koyu Yeşil & Altın Renk Paleti
val EmeraldDark = Color(0xFF07261E)
val EmeraldCard = Color(0xFF0E3B2F)
val EmeraldSurface = Color(0xFF144D3E)
val GoldPrimary = Color(0xFFD4AF37)
val GoldLight = Color(0xFFF3E5AB)
val TextCream = Color(0xFFF7F5EE)
val TextMuted = Color(0xFFAEC7BE)

enum class Vakit(val key: String, val title: String, val apiKey: String) {
    IMSAK("imsak", "İmsak", "Fajr"),
    GUNES("gunes", "Güneş", "Sunrise"),
    OGLE("ogle", "Öğle", "Dhuhr"),
    IKINDI("ikindi", "İkindi", "Asr"),
    AKSAM("aksam", "Akşam", "Maghrib"),
    YATSI("yatsi", "Yatsı", "Isha")
}

enum class SesModu(val key: String, val label: String, val durationHint: String) {
    TAM("tam", "Tam Ezan", "Yaklaşık 3–4 dakika"),
    KISA("kisa", "Kısa Ezan", "30–60 saniye"),
    OZEL("ozel", "Özel Ses Dosyası", "Telefondan MP3 / M4A")
}

object NamazPrefs {
    private const val PREF_NAME = "namazim_prefs_v3"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveLocation(ctx: Context, lat: Double, lon: Double, city: String) {
        prefs(ctx).edit()
            .putString("lat", lat.toString())
            .putString("lon", lon.toString())
            .putString("city", city)
            .apply()
    }

    fun getLat(ctx: Context): Double = prefs(ctx).getString("lat", "49.4774")?.toDoubleOrNull() ?: 49.4774
    fun getLon(ctx: Context): Double = prefs(ctx).getString("lon", "8.4452")?.toDoubleOrNull() ?: 8.4452
    fun getCity(ctx: Context): String = prefs(ctx).getString("city", "Konum Bekleniyor") ?: "Konum Bekleniyor"

    fun saveTimes(ctx: Context, map: Map<Vakit, String>) {
        val ed = prefs(ctx).edit()
        map.forEach { (k, v) -> ed.putString("time_${k.key}", v) }
        ed.apply()
    }

    fun getTimes(ctx: Context): Map<Vakit, String> {
        val p = prefs(ctx)
        return Vakit.entries.associateWith { p.getString("time_${it.key}", "--:--") ?: "--:--" }
    }

    fun isAlarmEnabled(ctx: Context, v: Vakit): Boolean =
        prefs(ctx).getBoolean("alarm_${v.key}", v != Vakit.GUNES)

    fun setAlarmEnabled(ctx: Context, v: Vakit, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("alarm_${v.key}", enabled).apply()
    }

    fun isSoundEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("sound_enabled", true)
    fun setSoundEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun isVibrateEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("vibrate_enabled", true)
    fun setVibrateEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("vibrate_enabled", enabled).apply()
    }

    fun getGlobalMode(ctx: Context): SesModu {
        val k = prefs(ctx).getString("global_mode", SesModu.TAM.key)
        return SesModu.entries.find { it.key == k } ?: SesModu.TAM
    }

    fun setGlobalMode(ctx: Context, mode: SesModu) {
        prefs(ctx).edit().putString("global_mode", mode.key).apply()
    }

    fun getGlobalCustomUri(ctx: Context): String? = prefs(ctx).getString("global_custom_uri", null)
    fun getGlobalCustomName(ctx: Context): String =
        prefs(ctx).getString("global_custom_name", "Seçili dosya yok") ?: "Seçili dosya yok"

    fun setGlobalCustomAudio(ctx: Context, uri: String?, name: String) {
        prefs(ctx).edit()
            .putString("global_custom_uri", uri)
            .putString("global_custom_name", name)
            .apply()
    }

    fun getVakitMode(ctx: Context, v: Vakit): SesModu? {
        val k = prefs(ctx).getString("mode_${v.key}", null) ?: return null
        return SesModu.entries.find { it.key == k }
    }

    fun setVakitMode(ctx: Context, v: Vakit, mode: SesModu?) {
        val ed = prefs(ctx).edit()
        if (mode == null) ed.remove("mode_${v.key}") else ed.putString("mode_${v.key}", mode.key)
        ed.apply()
    }

    fun getVakitCustomUri(ctx: Context, v: Vakit): String? = prefs(ctx).getString("uri_${v.key}", null)
    fun getVakitCustomName(ctx: Context, v: Vakit): String =
        prefs(ctx).getString("uri_name_${v.key}", "Varsayılan / Genel ayar") ?: "Varsayılan / Genel ayar"

    fun setVakitCustomAudio(ctx: Context, v: Vakit, uri: String?, name: String) {
        prefs(ctx).edit()
            .putString("uri_${v.key}", uri)
            .putString("uri_name_${v.key}", name)
            .apply()
    }

    fun resolveAudioForVakit(ctx: Context, v: Vakit): Pair<SesModu, Uri?> {
        val specificMode = getVakitMode(ctx, v)
        if (specificMode != null) {
            val u = getVakitCustomUri(ctx, v)?.let { Uri.parse(it) }
            return specificMode to u
        }
        val gMode = getGlobalMode(ctx)
        val gUri = getGlobalCustomUri(ctx)?.let { Uri.parse(it) }
        return gMode to gUri
    }
}

object DiyanetApi {
    // Aladhan API method=13 -> Diyanet İşleri Başkanlığı hesaplama yöntemi
    suspend fun fetchTodayTimes(lat: Double, lon: Double): Map<Vakit, String>? = withContext(Dispatchers.IO) {
        try {
            val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
            val urlStr = "https://api.aladhan.com/v1/timings/$dateStr?latitude=$lat&longitude=$lon&method=13"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val timings = JSONObject(body).getJSONObject("data").getJSONObject("timings")
            Vakit.entries.associateWith { v ->
                timings.getString(v.apiKey).substringBefore(" ").trim()
            }
        } catch (e: Exception) {
            null
        }
    }
}

object AlarmScheduler {
    fun scheduleAll(ctx: Context) {
        val times = NamazPrefs.getTimes(ctx)
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()

        Vakit.entries.forEachIndexed { index, vakit ->
            val intent = Intent(ctx, EzanAlarmReceiver::class.java).apply {
                putExtra("vakit_key", vakit.key)
                putExtra("vakit_title", vakit.title)
            }
            val pi = PendingIntent.getBroadcast(
                ctx,
                100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)

            if (!NamazPrefs.isAlarmEnabled(ctx, vakit)) return@forEachIndexed
            val tStr = times[vakit] ?: return@forEachIndexed
            val parts = tStr.split(":")
            if (parts.size != 2) return@forEachIndexed
            val h = parts[0].toIntOrNull() ?: return@forEachIndexed
            val m = parts[1].toIntOrNull() ?: return@forEachIndexed

            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
                } else {
                    val info = AlarmManager.AlarmClockInfo(target.timeInMillis, pi)
                    alarmManager.setAlarmClock(info, pi)
                }
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}

class EzanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val vakitKey = intent.getStringExtra("vakit_key") ?: Vakit.OGLE.key
        val vakitTitle = intent.getStringExtra("vakit_title") ?: "Namaz"

        val serviceIntent = Intent(context, EzanPlaybackService::class.java).apply {
            action = EzanPlaybackService.ACTION_START
            putExtra("vakit_key", vakitKey)
            putExtra("vakit_title", vakitTitle)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        AlarmScheduler.scheduleAll(context)
    }
}

class EzanPlaybackService : Service() {
    companion object {
        const val CHANNEL_ID = "namazim_ezan_alarm_channel"
        const val NOTIF_ID = 9001
        const val ACTION_START = "ACTION_START_EZAN"
        const val ACTION_STOP = "ACTION_STOP_EZAN"
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlaybackAndSelf()
            return START_NOT_STICKY
        }

        val vakitKey = intent?.getStringExtra("vakit_key") ?: Vakit.OGLE.key
        val vakitTitle = intent?.getStringExtra("vakit_title") ?: "Namaz"
        val vakit = Vakit.entries.find { it.key == vakitKey } ?: Vakit.OGLE

        acquireWakeLock()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(vakitTitle))
        startAudioAndVibration(vakit)

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Namazim::EzanWakeLock").apply {
            acquire(5 * 60 * 1000L)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Namaz Vakti Ezan Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Beş vakit ezan alarmı ve tam ekran uyarısı"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(vakitTitle: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alarm_active_vakit", vakitTitle)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, 201, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, EzanPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 202, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$vakitTitle Vakti Girdi")
            .setContentText("Hayya ale's-salâh • Ezanı durdurmak için dokunun")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPi, true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Ezanı Durdur", stopPi)
            .build()
    }

    private fun startAudioAndVibration(vakit: Vakit) {
        if (NamazPrefs.isVibrateEnabled(this)) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 700, 500, 700, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }

        if (!NamazPrefs.isSoundEnabled(this)) return

        val (mode, customUri) = NamazPrefs.resolveAudioForVakit(this, vakit)
        val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val playUri = if (mode == SesModu.OZEL && customUri != null) customUri else fallbackUri
        val maxDurationMs = when (mode) {
            SesModu.TAM -> 210_000L   // 3.5 dakika
            SesModu.KISA -> 45_000L   // 45 saniye
            SesModu.OZEL -> 240_000L  // 4 dakika
        }

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(applicationContext, playUri)
                isLooping = (playUri == fallbackUri)
                setOnCompletionListener { stopPlaybackAndSelf() }
                prepare()
                start()
            }
            handler.postDelayed({ stopPlaybackAndSelf() }, maxDurationMs)
        } catch (_: Exception) {
            try {
                player = MediaPlayer.create(this, fallbackUri)?.apply {
                    isLooping = true
                    start()
                }
                handler.postDelayed({ stopPlaybackAndSelf() }, maxDurationMs)
            } catch (_: Exception) {
                stopPlaybackAndSelf()
            }
        }
    }

    private fun stopPlaybackAndSelf() {
        handler.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlaybackAndSelf()
        super.onDestroy()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContent {
            NamazimApp()
        }
    }
}

@Composable
fun NamazimApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    var city by remember { mutableStateOf(NamazPrefs.getCity(ctx)) }
    var lat by remember { mutableDoubleStateOf(NamazPrefs.getLat(ctx)) }
    var lon by remember { mutableDoubleStateOf(NamazPrefs.getLon(ctx)) }
    var times by remember { mutableStateOf(NamazPrefs.getTimes(ctx)) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("Diyanet Hesaplama Yöntemi") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val locGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locGranted) {
            fetchAutoLocationAndTimes(ctx, scope,
                onStart = { isLoading = true },
                onDone = { c, la, lo, t, msg ->
                    city = c; lat = la; lon = lo; times = t; statusMsg = msg; isLoading = false
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        val reqs = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reqs.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(reqs.toTypedArray())

        if (times.values.all { it == "--:--" }) {
            isLoading = true
            DiyanetApi.fetchTodayTimes(lat, lon)?.let {
                NamazPrefs.saveTimes(ctx, it)
                times = it
                AlarmScheduler.scheduleAll(ctx)
            }
            isLoading = false
        }
    }

    Scaffold(
        containerColor = EmeraldDark,
        bottomBar = {
            NavigationBar(containerColor = EmeraldCard, contentColor = GoldPrimary) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                    label = { Text("Vakitler") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldDark,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                    label = { Text("Kıble") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldDark,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                    label = { Text("Ezan & Ses") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldDark,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(EmeraldDark, Color(0xFF041712))))
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> VakitlerScreen(
                    city = city,
                    times = times,
                    isLoading = isLoading,
                    statusMsg = statusMsg,
                    onRefreshLocation = {
                        fetchAutoLocationAndTimes(ctx, scope,
                            onStart = { isLoading = true },
                            onDone = { c, la, lo, t, msg ->
                                city = c; lat = la; lon = lo; times = t; statusMsg = msg; isLoading = false
                            }
                        )
                    },
                    onStopEzan = {
                        val stopIntent = Intent(ctx, EzanPlaybackService::class.java).apply {
                            action = EzanPlaybackService.ACTION_STOP
                        }
                        ctx.startService(stopIntent)
                    }
                )
                1 -> QiblaScreen(lat = lat, lon = lon, city = city)
                2 -> EzanSettingsScreen()
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchAutoLocationAndTimes(
    ctx: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onStart: () -> Unit,
    onDone: (String, Double, Double, Map<Vakit, String>, String) -> Unit
) {
    onStart()
    val fused = LocationServices.getFusedLocationProviderClient(ctx)
    fused.lastLocation.addOnSuccessListener { loc ->
        scope.launch {
            val la = loc?.latitude ?: NamazPrefs.getLat(ctx)
            val lo = loc?.longitude ?: NamazPrefs.getLon(ctx)
            var cityName = NamazPrefs.getCity(ctx)
            if (loc != null) {
                cityName = withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        val list = Geocoder(ctx, Locale("tr")).getFromLocation(la, lo, 1)
                        list?.firstOrNull()?.let { it.subAdminArea ?: it.locality ?: it.adminArea }
                            ?: String.format(Locale.US, "%.2f, %.2f", la, lo)
                    } catch (_: Exception) {
                        String.format(Locale.US, "%.2f, %.2f", la, lo)
                    }
                }
                NamazPrefs.saveLocation(ctx, la, lo, cityName)
            }
            val fetched = DiyanetApi.fetchTodayTimes(la, lo)
            val finalTimes = fetched ?: NamazPrefs.getTimes(ctx)
            if (fetched != null) {
                NamazPrefs.saveTimes(ctx, fetched)
                AlarmScheduler.scheduleAll(ctx)
            }
            val msg = if (fetched != null) "Diyanet Yöntemi • Güncel" else "Çevrimdışı Kayıtlı Vakitler"
            onDone(cityName, la, lo, finalTimes, msg)
        }
    }.addOnFailureListener {
        scope.launch {
            val la = NamazPrefs.getLat(ctx)
            val lo = NamazPrefs.getLon(ctx)
            val fetched = DiyanetApi.fetchTodayTimes(la, lo) ?: NamazPrefs.getTimes(ctx)
            onDone(NamazPrefs.getCity(ctx), la, lo, fetched, "Konum alınamadı, kayıtlı konum kullanıldı")
        }
    }
}

@Composable
fun VakitlerScreen(
    city: String,
    times: Map<Vakit, String>,
    isLoading: Boolean,
    statusMsg: String,
    onRefreshLocation: () -> Unit,
    onStopEzan: () -> Unit
) {
    val ctx = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var alarmStates by remember {
        mutableStateOf(Vakit.entries.associateWith { NamazPrefs.isAlarmEnabled(ctx, it) })
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val nextInfo = remember(times, nowMillis) { computeNextVakit(times) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("NAMAZIM", color = GoldPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(city, color = TextCream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(statusMsg, color = TextMuted, fontSize = 12.sp)
                }
                Row {
                    IconButton(
                        onClick = onStopEzan,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(EmeraldCard)
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = "Ezanı Durdur", tint = GoldPrimary)
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onRefreshLocation,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(EmeraldCard)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Konumu Yenile", tint = GoldPrimary)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = EmeraldCard),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("SIRADAKİ VAKİT", color = GoldLight, fontSize = 12.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(nextInfo.first, color = GoldPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(nextInfo.second, color = TextCream, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    if (isLoading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(color = GoldPrimary, trackColor = EmeraldSurface, modifier = Modifier.fillMaxWidth(0.6f))
                    }
                }
            }
        }

        items(Vakit.entries.size) { idx ->
            val vakit = Vakit.entries[idx]
            val timeVal = times[vakit] ?: "--:--"
            val enabled = alarmStates[vakit] ?: false
            val isNext = nextInfo.first == vakit.title

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isNext) 1.5.dp else 0.5.dp,
                        color = if (isNext) GoldPrimary else GoldPrimary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = if (isNext) EmeraldSurface else EmeraldCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(vakit.title, color = if (isNext) GoldPrimary else TextCream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        val modeLabel = NamazPrefs.resolveAudioForVakit(ctx, vakit).first.label
                        Text("Ses: $modeLabel", color = TextMuted, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeVal, color = GoldLight, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        IconButton(onClick = {
                            val newState = !enabled
                            NamazPrefs.setAlarmEnabled(ctx, vakit, newState)
                            alarmStates = alarmStates.toMutableMap().apply { put(vakit, newState) }
                            AlarmScheduler.scheduleAll(ctx)
                        }) {
                            Icon(
                                imageVector = if (enabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                contentDescription = "Alarm",
                                tint = if (enabled) GoldPrimary else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun computeNextVakit(times: Map<Vakit, String>): Pair<String, String> {
    val now = Calendar.getInstance()
    for (v in Vakit.entries) {
        val parts = (times[v] ?: continue).split(":")
        if (parts.size != 2) continue
        val h = parts[0].toIntOrNull() ?: continue
        val m = parts[1].toIntOrNull() ?: continue
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
        }
        if (cal.after(now)) {
            val diffSec = (cal.timeInMillis - now.timeInMillis) / 1000
            val hh = diffSec / 3600
            val mm = (diffSec % 3600) / 60
            val ss = diffSec % 60
            return v.title to String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss)
        }
    }
    return Vakit.IMSAK.title to "Yarın"
}

@Composable
fun QiblaScreen(lat: Double, lon: Double, city: String) {
    val ctx = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }

    val qiblaAngle = remember(lat, lon) { calculateQiblaDirection(lat, lon) }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, geomag, 0, 3)
                }
                val r = FloatArray(9)
                val i = FloatArray(9)
                if (SensorManager.getRotationMatrix(r, i, gravity, geomag)) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(r, orientation)
                    val deg = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
                    azimuth = deg
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (accel != null && magnet != null) {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            sm.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }

    val diff = ((qiblaAngle - azimuth + 540f) % 360f) - 180f
    val aligned = abs(diff) < 6f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("KIBLE PUSULASI", color = GoldPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text("$city • Kıble Açısı: ${qiblaAngle.toInt()}°", color = TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(270.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 10f

                drawCircle(color = EmeraldCard, radius = radius, center = center)
                drawCircle(
                    color = if (aligned) Color(0xFF4ADE80) else GoldPrimary,
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                )

                rotate(degrees = -azimuth, pivot = center) {
                    drawLine(
                        color = TextMuted,
                        start = Offset(center.x, center.y - radius + 16f),
                        end = Offset(center.x, center.y + radius - 16f),
                        strokeWidth = 3f
                    )
                }

                rotate(degrees = qiblaAngle - azimuth, pivot = center) {
                    val arrow = Path().apply {
                        moveTo(center.x, center.y - radius + 24f)
                        lineTo(center.x - 18f, center.y + 12f)
                        lineTo(center.x, center.y - 6f)
                        lineTo(center.x + 18f, center.y + 12f)
                        close()
                    }
                    drawPath(arrow, color = if (aligned) Color(0xFF4ADE80) else GoldPrimary)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("KÂBE", color = GoldLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${((qiblaAngle - azimuth + 360f) % 360f).toInt()}°", color = TextCream, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = if (aligned) "Kıble Yönündesiniz" else "Altın oku yukarı hizalayın • Telefonu düz tutun",
            color = if (aligned) Color(0xFF4ADE80) else GoldLight,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

private fun calculateQiblaDirection(lat: Double, lon: Double): Float {
    val kaabaLat = Math.toRadians(21.422487)
    val kaabaLon = Math.toRadians(39.826206)
    val userLat = Math.toRadians(lat)
    val dLon = kaabaLon - Math.toRadians(lon)
    val y = sin(dLon)
    val x = cos(userLat) * tan(kaabaLat) - sin(userLat) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)).toFloat()) + 360f) % 360f
}

@Composable
fun EzanSettingsScreen() {
    val ctx = LocalContext.current
    var soundEnabled by remember { mutableStateOf(NamazPrefs.isSoundEnabled(ctx)) }
    var vibrateEnabled by remember { mutableStateOf(NamazPrefs.isVibrateEnabled(ctx)) }
    var globalMode by remember { mutableStateOf(NamazPrefs.getGlobalMode(ctx)) }
    var globalCustomName by remember { mutableStateOf(NamazPrefs.getGlobalCustomName(ctx)) }
    var pickingForVakit by remember { mutableStateOf<Vakit?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val fileName = getFileDisplayName(ctx, uri)
            val targetVakit = pickingForVakit
            if (targetVakit == null) {
                NamazPrefs.setGlobalCustomAudio(ctx, uri.toString(), fileName)
                NamazPrefs.setGlobalMode(ctx, SesModu.OZEL)
                globalMode = SesModu.OZEL
                globalCustomName = fileName
            } else {
                NamazPrefs.setVakitCustomAudio(ctx, targetVakit, uri.toString(), fileName)
                NamazPrefs.setVakitMode(ctx, targetVakit, SesModu.OZEL)
                refreshTrigger++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { previewPlayer?.stop(); previewPlayer?.release() } catch (_: Exception) {}
        }
    }

    fun playPreview(mode: SesModu, customUri: Uri?) {
        try { previewPlayer?.stop(); previewPlayer?.release() } catch (_: Exception) {}
        previewPlayer = null
        if (isPreviewPlaying) {
            isPreviewPlaying = false
            return
        }
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val target = if (mode == SesModu.OZEL && customUri != null) customUri else fallback
        try {
            previewPlayer = MediaPlayer().apply {
                setDataSource(ctx, target)
                setOnCompletionListener { isPreviewPlaying = false }
                prepare()
                start()
            }
            isPreviewPlaying = true
        } catch (_: Exception) {
            isPreviewPlaying = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("EZAN VE SES AYARLARI", color = GoldPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Genel ezan süresini veya her vakit için özel MP3/M4A dosyanızı seçin", color = TextMuted, fontSize = 13.sp)
        }

        // Bildirim Sesi & Titreşim + Arka Plan İzni
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(0.5.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Bildirim / Ezan Sesi", color = TextCream, fontSize = 16.sp)
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it; NamazPrefs.setSoundEnabled(ctx, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldDark, checkedTrackColor = GoldPrimary)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Titreşim", color = TextCream, fontSize = 16.sp)
                        Switch(
                            checked = vibrateEnabled,
                            onCheckedChange = { vibrateEnabled = it; NamazPrefs.setVibrateEnabled(ctx, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldDark, checkedTrackColor = GoldPrimary)
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        if (!am.canScheduleExactAlarms()) {
                            OutlinedButton(
                                onClick = {
                                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary)
                            ) {
                                Icon(Icons.Default.AlarmOn, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Ekran Kapalıyken Tam Vakit İzni Ver")
                            }
                        }
                    }
                }
            }
        }

        // Varsayılan Ezan Modu (Tam / Kısa / Özel Dosya)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(0.5.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Varsayılan Ezan Seçimi", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    SesModu.entries.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (globalMode == m) EmeraldSurface else Color.Transparent)
                                .clickable {
                                    globalMode = m
                                    NamazPrefs.setGlobalMode(ctx, m)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = globalMode == m,
                                onClick = {
                                    globalMode = m
                                    NamazPrefs.setGlobalMode(ctx, m)
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary, unselectedColor = TextMuted)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.label, color = TextCream, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (m == SesModu.OZEL) globalCustomName else m.durationHint,
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                pickingForVakit = null
                                audioPicker.launch(arrayOf("audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/*"))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("MP3 / M4A Seç", fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                val u = NamazPrefs.getGlobalCustomUri(ctx)?.let { Uri.parse(it) }
                                playPreview(globalMode, u)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = EmeraldDark)
                        ) {
                            Icon(if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isPreviewPlaying) "Durdur" else "Önizle / Test", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Her Vakit İçin Ayrı Ezan Sesi Seçimi
        item {
            Text("HER VAKİT İÇİN AYRI SES", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        }

        items(Vakit.entries.size) { idx ->
            val v = Vakit.entries[idx]
            val keyTrigger = refreshTrigger
            val currentVakitMode = remember(keyTrigger) { NamazPrefs.getVakitMode(ctx, v) }
            val currentFileName = remember(keyTrigger) { NamazPrefs.getVakitCustomName(ctx, v) }

            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldCard),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().border(0.5.dp, GoldPrimary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.title, color = TextCream, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            val desc = when (currentVakitMode) {
                                null -> "Genel ayar (${globalMode.label})"
                                SesModu.OZEL -> "Özel: $currentFileName"
                                else -> currentVakitMode.label
                            }
                            Text(desc, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            val (m, u) = NamazPrefs.resolveAudioForVakit(ctx, v)
                            playPreview(m, u)
                        }) {
                            Icon(Icons.Default.PlayCircleFilled, contentDescription = "Test Et", tint = GoldPrimary)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = currentVakitMode == null,
                            onClick = { NamazPrefs.setVakitMode(ctx, v, null); refreshTrigger++ },
                            label = { Text("Genel", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = currentVakitMode == SesModu.TAM,
                            onClick = { NamazPrefs.setVakitMode(ctx, v, SesModu.TAM); refreshTrigger++ },
                            label = { Text("Tam", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = currentVakitMode == SesModu.KISA,
                            onClick = { NamazPrefs.setVakitMode(ctx, v, SesModu.KISA); refreshTrigger++ },
                            label = { Text("Kısa", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = currentVakitMode == SesModu.OZEL,
                            onClick = {
                                pickingForVakit = v
                                audioPicker.launch(arrayOf("audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/*"))
                            },
                            label = { Text("MP3/M4A", fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    }
}

private fun getFileDisplayName(ctx: Context, uri: Uri): String {
    var name = "Özel Ezan Sesi"
    ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            name = cursor.getString(idx) ?: name
        }
    }
    return name
}

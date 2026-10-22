package com.hoerire.tools.zafiro.app.ui.content

import android.content.Context
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * DeepSeek 高峰／低谷时段规则。
 *
 * 官方口径（api-docs 定价页）：高峰 = UTC 01:00-04:00 与 06:00-10:00，周一至周五，
 * 中国法定节假日除外；其余时间（含周末与节假日全天）一律低谷。
 * 规则优先从官方定价页实时解析，失败时回退内置规则。
 */
data class PeakSchedule(
    val windowsUtc: List<Pair<LocalTime, LocalTime>>,
    val weekdaysOnly: Boolean,
    val holidays: Set<LocalDate>,
    val source: String,
    val fetchedAtMs: Long,
) {
    fun isPeak(nowUtc: Instant, displayZone: ZoneId): Boolean {
        if (nowUtc.atZone(displayZone).toLocalDate() in holidays) return false
        val utc = nowUtc.atZone(ZoneOffset.UTC)
        if (weekdaysOnly &&
            (utc.dayOfWeek == DayOfWeek.SATURDAY || utc.dayOfWeek == DayOfWeek.SUNDAY)
        ) {
            return false
        }
        val time = utc.toLocalTime()
        return windowsUtc.any { (start, end) -> !time.isBefore(start) && time.isBefore(end) }
    }

    companion object {
        const val SOURCE_DOC = "doc"
        const val SOURCE_BUILTIN = "builtin"
        val BuiltinWindows: List<Pair<LocalTime, LocalTime>> = listOf(
            LocalTime.of(1, 0) to LocalTime.of(4, 0),
            LocalTime.of(6, 0) to LocalTime.of(10, 0),
        )
    }
}

/** 把 UTC 高峰窗口格式化成展示时区（北京时间）的文本，如 09:00–12:00、14:00–18:00。 */
internal fun formatPeakWindows(schedule: PeakSchedule, zone: ZoneId): String {
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val hourFmt = DateTimeFormatter.ofPattern("HH")
    val base = LocalDate.of(2000, 1, 6) // 周四，避开跨日换算歧义
    return schedule.windowsUtc.joinToString(", ") { (start, end) ->
        val s = start.atDate(base).atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalTime()
        val e = end.atDate(base).atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalTime()
        // 整点窗口用紧凑写法（09–12），否则保留分钟
        if (s.minute == 0 && e.minute == 0) {
            hourFmt.format(s) + "-" + hourFmt.format(e)
        } else {
            fmt.format(s) + "-" + fmt.format(e)
        }
    }
}

/** 解析官方定价页里的高峰描述：返回 (UTC 窗口列表, 是否仅工作日)。 */
internal fun parsePeakScheduleFromDoc(html: String): Pair<List<Pair<LocalTime, LocalTime>>, Boolean>? {
    val idx = html.indexOf("Peak hours are", ignoreCase = true)
    if (idx < 0) return null
    val segment = html.substring(idx, minOf(html.length, idx + 400))
    val rangeRegex = Regex("(\\d{1,2}):(\\d{2})\\s*[-\u2013\u2014]\\s*(\\d{1,2}):(\\d{2})")
    val windows = rangeRegex.findAll(segment).mapNotNull { match ->
        val (h1, m1, h2, m2) = match.destructured
        runCatching {
            val start = LocalTime.of(h1.toInt(), m1.toInt())
            val end = LocalTime.of(h2.toInt(), m2.toInt())
            if (start < end) start to end else null
        }.getOrNull()
    }.toList()
    if (windows.isEmpty()) return null
    val weekdaysOnly = segment.contains("Monday through Friday", ignoreCase = true) ||
        segment.contains("Monday to Friday", ignoreCase = true)
    return windows to weekdaysOnly
}

/** 规则仓库：官方文档优先，12 小时缓存，失败回退内置规则。 */
object DeepSeekPeakScheduleRepo {

    private const val DOC_URL = "https://api-docs.deepseek.com/quick_start/pricing"
    private const val HOLIDAY_URL = "https://timor.tech/api/holiday/year/"
    private const val CACHE_FILE = "deepseek_peak_schedule.json"
    private const val TTL_MS = 12L * 60L * 60L * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: PeakSchedule? = null

    fun current(): PeakSchedule = cached ?: builtin(emptySet())

    private fun builtin(holidays: Set<LocalDate>): PeakSchedule = PeakSchedule(
        windowsUtc = PeakSchedule.BuiltinWindows,
        weekdaysOnly = true,
        holidays = holidays,
        source = PeakSchedule.SOURCE_BUILTIN,
        fetchedAtMs = 0L,
    )

    suspend fun load(context: Context, force: Boolean = false): PeakSchedule =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val file = File(context.filesDir, CACHE_FILE)
            val fileCached = readCache(file)
            if (!force && fileCached != null && now - fileCached.fetchedAtMs < TTL_MS) {
                cached = fileCached
                return@withContext fileCached
            }
            val holidays = fetchHolidays(now) ?: fileCached?.holidays ?: emptySet()
            val parsed = runCatching { fetchDocWindows() }.getOrNull()
            val schedule = if (parsed != null) {
                PeakSchedule(
                    windowsUtc = parsed.first,
                    weekdaysOnly = parsed.second,
                    holidays = holidays,
                    source = PeakSchedule.SOURCE_DOC,
                    fetchedAtMs = now,
                )
            } else {
                fileCached?.copy(holidays = holidays, source = PeakSchedule.SOURCE_BUILTIN) ?: builtin(holidays)
            }
            cached = schedule
            runCatching { writeCache(file, schedule) }
            schedule
        }

    private fun httpGet(url: String): String? = runCatching {
        client.newCall(
            Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build(),
        ).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()

    private fun fetchDocWindows(): Pair<List<Pair<LocalTime, LocalTime>>, Boolean>? =
        httpGet(DOC_URL)?.let { parsePeakScheduleFromDoc(it) }

    /** 中国法定节假日（timor.tech 公共节假日接口），失败返回 null。 */
    private fun fetchHolidays(nowMs: Long): Set<LocalDate>? {
        val year = Instant.ofEpochMilli(nowMs).atZone(ZoneId.of("Asia/Shanghai")).year
        val body = httpGet(HOLIDAY_URL + year) ?: return null
        return runCatching {
            val holiday = JSONObject(body).optJSONObject("holiday") ?: return null
            val out = mutableSetOf<LocalDate>()
            holiday.keys().forEach { key ->
                val entry = holiday.optJSONObject(key)
                if (entry?.optBoolean("holiday") == true) {
                    runCatching { out += LocalDate.parse(year.toString() + "-" + key) }
                }
            }
            out
        }.getOrNull()
    }

    private fun readCache(file: File): PeakSchedule? = runCatching {
        if (!file.isFile) return null
        val obj = JSONObject(file.readText())
        val windows = mutableListOf<Pair<LocalTime, LocalTime>>()
        val array = obj.optJSONArray("windows") ?: JSONArray()
        for (i in 0 until array.length()) {
            val pair = array.optJSONArray(i) ?: continue
            windows += LocalTime.parse(pair.getString(0)) to LocalTime.parse(pair.getString(1))
        }
        if (windows.isEmpty()) return null
        val holidays = mutableSetOf<LocalDate>()
        val holidayArray = obj.optJSONArray("holidays") ?: JSONArray()
        for (i in 0 until holidayArray.length()) {
            runCatching { holidays += LocalDate.parse(holidayArray.getString(i)) }
        }
        PeakSchedule(
            windowsUtc = windows,
            weekdaysOnly = obj.optBoolean("weekdaysOnly", true),
            holidays = holidays,
            source = obj.optString("source", PeakSchedule.SOURCE_BUILTIN),
            fetchedAtMs = obj.optLong("fetchedAt", 0L),
        )
    }.getOrNull()

    private fun writeCache(file: File, schedule: PeakSchedule) {
        val obj = JSONObject()
        val array = JSONArray()
        schedule.windowsUtc.forEach { (start, end) ->
            array.put(JSONArray().put(start.toString()).put(end.toString()))
        }
        obj.put("windows", array)
        obj.put("weekdaysOnly", schedule.weekdaysOnly)
        obj.put("holidays", JSONArray(schedule.holidays.map { it.toString() }))
        obj.put("source", schedule.source)
        obj.put("fetchedAt", schedule.fetchedAtMs)
        file.writeText(obj.toString())
    }
}

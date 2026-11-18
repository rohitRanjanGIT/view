package com.rohit.gallery.feature.search

import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.model.MediaType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Kinds of media a search word like "videos" or "screenshots" stands for. */
enum class MediaKind(val label: String) {
    Videos("Videos"),
    Photos("Photos"),
    Screenshots("Screenshots"),
    Gifs("GIFs"),
    Favorites("Favourites"),
}

/** One part of a query. An item matches the query when it matches every term. */
sealed interface SearchTerm {
    /** Taken in [fromMs, untilMs): a year, "today", "last week" and so on. */
    data class InRange(val fromMs: Long, val untilMs: Long) : SearchTerm

    /** Taken in [month] (1-12) of any year, or in an album whose name contains [word] ("May trip"). */
    data class Month(val month: Int, val word: String) : SearchTerm

    data class Kind(val kind: MediaKind) : SearchTerm

    /** Album name or file name contains [text] (any case). Quoted phrases arrive here whole. */
    data class Text(val text: String) : SearchTerm
}

/** Words that stand for a [MediaKind]. */
private val KIND_WORDS: Map<String, MediaKind> = buildMap {
    listOf("video", "videos", "vid", "vids").forEach { put(it, MediaKind.Videos) }
    listOf("photo", "photos", "image", "images", "picture", "pictures", "pic", "pics").forEach { put(it, MediaKind.Photos) }
    listOf("screenshot", "screenshots").forEach { put(it, MediaKind.Screenshots) }
    listOf("gif", "gifs").forEach { put(it, MediaKind.Gifs) }
    listOf("favourite", "favourites", "favorite", "favorites", "fav", "favs", "fave", "faves")
        .forEach { put(it, MediaKind.Favorites) }
}

private val MONTH_NAMES = listOf(
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december",
)

private val YEAR = Regex("""(19|20)\d\d""")

/**
 * Turns what the user typed into terms. Words are matched loosely: "2024" is a year, "march" or "mar" a
 * month, "videos" a kind, "today" / "yesterday" / "this week" / "last month" / "last year" a date range, and
 * anything else (or a "quoted phrase") is looked for in album and file names. [today] and [zone] fix what
 * the relative dates mean; [weekStart] is the locale's first day of the week.
 */
fun parseSearch(
    query: String,
    today: LocalDate,
    zone: ZoneId,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): List<SearchTerm> {
    fun startMs(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun range(from: LocalDate, until: LocalDate) = SearchTerm.InRange(startMs(from), startMs(until))

    val words = tokenize(query)
    val terms = ArrayList<SearchTerm>()
    var i = 0
    while (i < words.size) {
        val (word, quoted) = words[i]
        val next = words.getOrNull(i + 1)?.takeUnless { it.second }?.first
        i++
        if (quoted) {
            terms += SearchTerm.Text(word)
            continue
        }
        if (word == "this" || word == "last") {
            val back = if (word == "last") 1L else 0L
            val unitRange = when (next) {
                "week" -> today.with(TemporalAdjusters.previousOrSame(weekStart)).minusWeeks(back)
                    .let { range(it, it.plusWeeks(1)) }
                "month" -> today.withDayOfMonth(1).minusMonths(back).let { range(it, it.plusMonths(1)) }
                "year" -> today.withDayOfYear(1).minusYears(back).let { range(it, it.plusYears(1)) }
                else -> null
            }
            if (unitRange != null) {
                terms += unitRange
                i++
                continue
            }
        }
        terms += when {
            word == "today" -> range(today, today.plusDays(1))
            word == "yesterday" -> range(today.minusDays(1), today)
            YEAR.matches(word) -> word.toInt().let { y -> range(LocalDate.of(y, 1, 1), LocalDate.of(y + 1, 1, 1)) }
            word in KIND_WORDS -> SearchTerm.Kind(KIND_WORDS.getValue(word))
            else -> monthOf(word)?.let { SearchTerm.Month(it, word) } ?: SearchTerm.Text(word)
        }
    }
    return terms
}

/** [parseSearch] for the device's current date, time zone and first day of the week. */
fun parseSearchNow(query: String, zone: ZoneId = ZoneId.systemDefault()): List<SearchTerm> =
    parseSearch(query, LocalDate.now(zone), zone, WeekFields.of(Locale.getDefault()).firstDayOfWeek)

/** 1-12 for a month name or its start ("mar", "sept"); at least three letters. */
private fun monthOf(word: String): Int? {
    if (word.length < 3) return null
    val index = MONTH_NAMES.indexOfFirst { it.startsWith(word) }
    return if (index >= 0) index + 1 else null
}

/** Lower-case words, each flagged when it came from a "quoted phrase" (kept whole). */
private fun tokenize(query: String): List<Pair<String, Boolean>> {
    val out = ArrayList<Pair<String, Boolean>>()
    val parts = query.lowercase().split('"')
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            part.trim().takeIf { it.isNotEmpty() }?.let { out += it to true }
        } else {
            part.split(' ', '\t', '\n', ',', ';').filter { it.isNotEmpty() }.forEach { out += it to false }
        }
    }
    return out
}

/**
 * The library prepared for searching: each item's month is worked out once, so a query over 50k items is a
 * plain loop. Build it off the main thread whenever the library changes.
 */
class SearchIndex(val items: List<MediaItem>, zone: ZoneId) {

    private val months = ByteArray(items.size)
    internal val years = ShortArray(items.size)

    init {
        val rules = zone.rules
        items.forEachIndexed { i, item ->
            val instant = Instant.ofEpochMilli(item.sortDateMs)
            val date = instant.atOffset(rules.getOffset(instant))
            months[i] = date.monthValue.toByte()
            years[i] = date.year.toShort()
        }
    }

    /** Items matching every term, in library order. No terms: nothing. */
    fun search(terms: List<SearchTerm>): List<MediaItem> {
        if (terms.isEmpty()) return emptyList()
        val out = ArrayList<MediaItem>()
        for (i in items.indices) {
            val item = items[i]
            if (terms.all { matches(it, item, i) }) out += item
        }
        return out
    }

    private fun matches(term: SearchTerm, item: MediaItem, index: Int): Boolean = when (term) {
        is SearchTerm.InRange -> item.sortDateMs >= term.fromMs && item.sortDateMs < term.untilMs
        is SearchTerm.Month -> months[index].toInt() == term.month || item.bucketName.contains(term.word, ignoreCase = true)
        is SearchTerm.Kind -> item.isKind(term.kind)
        is SearchTerm.Text -> item.bucketName.contains(term.text, ignoreCase = true) ||
            item.displayName.contains(term.text, ignoreCase = true)
    }
}

fun MediaItem.isKind(kind: MediaKind): Boolean = when (kind) {
    MediaKind.Videos -> type == MediaType.Video
    MediaKind.Photos -> type == MediaType.Image
    MediaKind.Screenshots -> bucketName.contains("screenshot", ignoreCase = true) ||
        displayName.startsWith("screenshot", ignoreCase = true)
    MediaKind.Gifs -> mimeType.equals("image/gif", ignoreCase = true)
    MediaKind.Favorites -> isFavorite
}

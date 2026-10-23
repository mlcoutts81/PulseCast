package com.example.data.local

import android.content.Context
import com.example.ui.screens.HomeEpisodeUiModel
import com.example.ui.theme.AppThemeConfig
import com.example.ui.theme.ColorSource
import com.example.ui.theme.CustomAccentColor
import com.example.ui.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

class PodcastLocalStorage(context: Context) {

    private val prefs = context.getSharedPreferences("google_podcasts_m3_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RSS_URLS = "subscribed_rss_urls"
        private const val KEY_QUEUE = "listening_queue_json"
        private const val KEY_HISTORY = "listening_history_json"
        private const val KEY_INITIALIZED = "has_initialized_starter"
        private const val KEY_DOWNLOADS = "downloaded_episodes_json"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COLOR_SOURCE = "color_source"
        private const val KEY_SELECTED_ACCENT = "selected_accent"
        private const val KEY_USE_AMOLED_BLACK = "use_amoled_black"
        private const val KEY_POSITIONS = "episode_playback_positions_json"
        private const val KEY_PLAYED_IDS = "played_episode_ids_json"
        private const val KEY_SKIP_SILENCE = "skip_silence_enabled"
        private const val KEY_VOICE_BOOST_DB = "voice_boost_db"
        private const val KEY_HIDE_PLAYED = "hide_played_episodes_flag"
    }

    // --- 1. Subscribed RSS Feed URLs ---
    fun isFirstLaunch(): Boolean {
        return !prefs.getBoolean(KEY_INITIALIZED, false)
    }

    fun markFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
    }

    fun saveSubscribedUrls(urls: Collection<String>) {
        val jsonArray = JSONArray()
        urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach {
            jsonArray.put(it)
        }
        // Use commit() instead of apply() so removals write to disk immediately before app close
        prefs.edit()
            .putString(KEY_RSS_URLS, jsonArray.toString())
            .putBoolean(KEY_INITIALIZED, true)
            .commit()
    }

    fun loadSubscribedUrls(): List<String> {
        val raw = prefs.getString(KEY_RSS_URLS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i -> array.getString(i).trim() }.distinct()
        }.getOrDefault(emptyList())
    }

    // --- 2. Listening Queue Episodes ---
    fun saveQueue(episodes: List<HomeEpisodeUiModel>) {
        prefs.edit().putString(KEY_QUEUE, encodeEpisodeList(episodes)).apply()
    }

    fun loadQueue(): List<HomeEpisodeUiModel> {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return decodeEpisodeList(raw, forceQueuedFlag = true)
    }

    // --- 3. Listening History Episodes ---
    fun saveHistory(episodes: List<HomeEpisodeUiModel>) {
        // Keep the 50 most recent episodes to stay lightweight
        prefs.edit().putString(KEY_HISTORY, encodeEpisodeList(episodes.take(50))).apply()
    }

    fun loadHistory(): List<HomeEpisodeUiModel> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return decodeEpisodeList(raw, forceQueuedFlag = false)
    }

    // --- 4. Downloaded Offline Episodes ---
    fun saveDownloads(episodes: List<HomeEpisodeUiModel>) {
        prefs.edit().putString(KEY_DOWNLOADS, encodeEpisodeList(episodes)).apply()
    }

    fun loadDownloads(): List<HomeEpisodeUiModel> {
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return decodeEpisodeList(raw, forceQueuedFlag = false)
    }

    // --- 5. Theme Configuration ---
    fun saveThemeConfig(config: AppThemeConfig) {
        prefs.edit()
            .putString(KEY_THEME_MODE, config.themeMode.name)
            .putString(KEY_COLOR_SOURCE, config.colorSource.name)
            .putString(KEY_SELECTED_ACCENT, config.selectedAccent.name)
            .putBoolean(KEY_USE_AMOLED_BLACK, config.useAmoledBlack)
            .apply()
    }

    fun loadThemeConfig(): AppThemeConfig {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val sourceName = prefs.getString(KEY_COLOR_SOURCE, ColorSource.DYNAMIC_SYSTEM.name)
            ?: ColorSource.DYNAMIC_SYSTEM.name
        val accentName = prefs.getString(KEY_SELECTED_ACCENT, CustomAccentColor.GOOGLE_BLUE.name)
            ?: CustomAccentColor.GOOGLE_BLUE.name
        val useAmoled = prefs.getBoolean(KEY_USE_AMOLED_BLACK, false)

        val themeMode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.SYSTEM)
        val colorSource = runCatching { ColorSource.valueOf(sourceName) }.getOrDefault(ColorSource.DYNAMIC_SYSTEM)
        val selectedAccent = runCatching { CustomAccentColor.valueOf(accentName) }
            .getOrDefault(CustomAccentColor.GOOGLE_BLUE)

        return AppThemeConfig(
            themeMode = themeMode,
            colorSource = colorSource,
            selectedAccent = selectedAccent,
            useAmoledBlack = useAmoled
        )
    }

    // --- 6. Episode Playback Resume Positions ---
    private var cachedPositionsMap: MutableMap<String, Long>? = null

    fun loadAllPositions(): Map<String, Long> {
        cachedPositionsMap?.let { return it }
        val raw = prefs.getString(KEY_POSITIONS, null) ?: return mutableMapOf<String, Long>().also {
            cachedPositionsMap = it
        }
        return runCatching {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.optLong(key, 0L)
            }
            cachedPositionsMap = map
            map
        }.getOrDefault(mutableMapOf())
    }

    fun saveEpisodePosition(episodeId: String, positionMs: Long, durationMs: Long) {
        if (episodeId.isBlank()) return
        val currentMap = (cachedPositionsMap ?: loadAllPositions().toMutableMap()).also {
            cachedPositionsMap = it
        }

        // If within the first 5 seconds or within 15 seconds of the end, clear the saved position
        if (positionMs < 5_000L || (durationMs > 0L && positionMs >= durationMs - 15_000L)) {
            currentMap.remove(episodeId)
        } else {
            currentMap[episodeId] = positionMs
        }

        val jsonObj = JSONObject()
        currentMap.forEach { (id, pos) -> jsonObj.put(id, pos) }
        prefs.edit().putString(KEY_POSITIONS, jsonObj.toString()).apply()
    }

    fun getEpisodePosition(episodeId: String): Long {
        return loadAllPositions()[episodeId] ?: 0L
    }

    // --- 6B. Episode Played Status Persistence ---
    private var cachedPlayedIds: MutableSet<String>? = null

    fun loadPlayedEpisodeIds(): Set<String> {
        cachedPlayedIds?.let { return it }
        val raw = prefs.getString(KEY_PLAYED_IDS, null) ?: return mutableSetOf<String>().also {
            cachedPlayedIds = it
        }
        return runCatching {
            val array = JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                set.add(array.getString(i))
            }
            cachedPlayedIds = set
            set
        }.getOrDefault(mutableSetOf())
    }

    fun savePlayedEpisodeIds(ids: Set<String>) {
        cachedPlayedIds = ids.toMutableSet()
        val array = JSONArray()
        ids.forEach { array.put(it) }
        prefs.edit().putString(KEY_PLAYED_IDS, array.toString()).apply()
    }

    fun setEpisodePlayedStatus(episodeId: String, isPlayed: Boolean) {
        if (episodeId.isBlank()) return
        val currentSet = (cachedPlayedIds ?: loadPlayedEpisodeIds().toMutableSet()).also {
            cachedPlayedIds = it
        }
        if (isPlayed) {
            currentSet.add(episodeId)
            // Also clear any partial resume timestamp when marked as completed
            saveEpisodePosition(episodeId, 0L, 0L)
        } else {
            currentSet.remove(episodeId)
        }

        val array = JSONArray()
        currentSet.forEach { array.put(it) }
        prefs.edit().putString(KEY_PLAYED_IDS, array.toString()).apply()
    }

    fun saveHidePlayed(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_PLAYED, hide).apply()
    }

    fun loadHidePlayed(): Boolean {
        return prefs.getBoolean(KEY_HIDE_PLAYED, false)
    }

    // --- 7. Audio FX (Trim Silence & Voice Boost) ---
    fun saveSkipSilence(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
    }

    fun loadSkipSilence(): Boolean {
        return prefs.getBoolean(KEY_SKIP_SILENCE, false)
    }

    fun saveVoiceBoostDb(gainDb: Int) {
        prefs.edit().putInt(KEY_VOICE_BOOST_DB, gainDb).apply()
    }

    fun loadVoiceBoostDb(): Int {
        return prefs.getInt(KEY_VOICE_BOOST_DB, 0)
    }

    // --- Internal JSON Helpers (Zero external library dependencies) ---
    private fun encodeEpisodeList(episodes: List<HomeEpisodeUiModel>): String {
        val array = JSONArray()
        episodes.forEach { ep ->
            val obj = JSONObject().apply {
                put("id", ep.id)
                put("title", ep.title)
                put("podcastName", ep.podcastName)
                put("publishedDate", ep.publishedDate)
                put("description", ep.description)
                put("durationText", ep.durationText)
                put("durationMs", ep.durationMs)
                put("audioUrl", ep.audioUrl)
                put("artworkUrl", ep.artworkUrl)
                put("isQueued", ep.isQueued)
                put("isPlayed", ep.isPlayed)
                put("resumePositionMs", ep.resumePositionMs)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodeEpisodeList(jsonString: String, forceQueuedFlag: Boolean): List<HomeEpisodeUiModel> {
        return runCatching {
            val array = JSONArray(jsonString)
            val list = mutableListOf<HomeEpisodeUiModel>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HomeEpisodeUiModel(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        podcastName = obj.optString("podcastName"),
                        publishedDate = obj.optString("publishedDate"),
                        description = obj.optString("description"),
                        durationText = obj.optString("durationText"),
                        durationMs = obj.optLong("durationMs", 1800_000L),
                        audioUrl = obj.optString("audioUrl"),
                        artworkUrl = obj.optString("artworkUrl"),
                        isQueued = if (forceQueuedFlag) true else obj.optBoolean("isQueued", false),
                        isPlayed = obj.optBoolean("isPlayed", false),
                        resumePositionMs = obj.optLong("resumePositionMs", 0L)
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }
}

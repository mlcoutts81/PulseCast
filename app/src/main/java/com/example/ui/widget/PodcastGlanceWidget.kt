package com.example.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.data.local.PodcastLocalStorage
import com.example.service.player.PodcastMediaService
import com.example.ui.screens.HomeEpisodeUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PodcastGlanceWidget : GlanceAppWidget() {

    companion object {
        // The 5 target responsive grid sizes
        val SIZE_3X1 = DpSize(180.dp, 56.dp)
        val SIZE_4X1 = DpSize(250.dp, 56.dp)
        val SIZE_2X2 = DpSize(110.dp, 110.dp)
        val SIZE_4X2 = DpSize(250.dp, 110.dp)
        val SIZE_4X3 = DpSize(250.dp, 220.dp)

        suspend fun updateAllWidgets(context: Context) {
            runCatching {
                PodcastGlanceWidget().updateAll(context)
            }
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_3X1, SIZE_4X1, SIZE_2X2, SIZE_4X2, SIZE_4X3)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val localStorage = PodcastLocalStorage(context)
        val queueList = localStorage.loadQueue()
        val currentEp = localStorage.loadHistory().firstOrNull()
            ?: queueList.firstOrNull()
        val isPlaying = PodcastMediaService.isCurrentlyPlaying
        val queueCount = queueList.size

        // Load a rounded 128x128 bitmap for the widget cover art
        val artworkBitmap = currentEp?.artworkUrl?.let { url ->
            loadWidgetArtworkBitmap(context, url)
        }

        provideContent {
            // GlanceTheme automatically uses Android 12+ Dynamic System Colors (Material You)
            GlanceTheme {
                val size = LocalSize.current
                val isExtraTall = size.height >= 180.dp
                val isTall = size.height >= 100.dp
                val isWide = size.width >= 220.dp

                when {
                    // 0. 4x3 Interactive Up Next Queue Widget (Header + Scrollable List)
                    isExtraTall -> Widget4x3QueueLayout(
                        title = currentEp?.title ?: "No episode playing",
                        podcastName = currentEp?.podcastName ?: "Tap to open Podcasts",
                        artwork = artworkBitmap,
                        isPlaying = isPlaying,
                        queueEpisodes = queueList
                    )
                    // 1. 2x2 Square Material You Tile
                    isTall && !isWide -> Widget2x2SquareLayout(
                        title = currentEp?.title ?: "No episode playing",
                        podcastName = currentEp?.podcastName ?: "Tap to open",
                        artwork = artworkBitmap,
                        isPlaying = isPlaying
                    )
                    // 2. 4x2 Full Material 3 Media Card
                    isTall && isWide -> Widget4x2FullLayout(
                        title = currentEp?.title ?: "No episode playing",
                        podcastName = currentEp?.podcastName ?: "Tap to open Podcasts",
                        artwork = artworkBitmap,
                        isPlaying = isPlaying,
                        queueCount = queueCount
                    )
                    // 3. 4x1 Wide Single-Row Bar (Artwork + Title + -15s / Play / +30s)
                    !isTall && isWide -> Widget4x1BarLayout(
                        title = currentEp?.title ?: "No episode playing",
                        podcastName = currentEp?.podcastName ?: "Podcasts",
                        artwork = artworkBitmap,
                        isPlaying = isPlaying
                    )
                    // 4. 3x1 Compact Pill (Artwork + Title + Play/Pause)
                    else -> Widget3x1CompactLayout(
                        title = currentEp?.title ?: "No episode playing",
                        podcastName = currentEp?.podcastName ?: "Podcasts",
                        artwork = artworkBitmap,
                        isPlaying = isPlaying
                    )
                }
            }
        }
    }

    // --- LAYOUT 1: 3x1 COMPACT PILL ---
    @Composable
    private fun Widget3x1CompactLayout(
        title: String,
        podcastName: String,
        artwork: Bitmap?,
        isPlaying: Boolean
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.surface)
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetArtworkThumb(artwork = artwork, sizeDp = 42)
            Spacer(modifier = GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = podcastName,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))
            PlayPauseSquircleButton(isPlaying = isPlaying, sizeDp = 42)
        }
    }

    // --- LAYOUT 2: 4x1 WIDE MEDIA BAR ---
    @Composable
    private fun Widget4x1BarLayout(
        title: String,
        podcastName: String,
        artwork: Bitmap?,
        isPlaying: Boolean
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.surface)
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetArtworkThumb(artwork = artwork, sizeDp = 44)
            Spacer(modifier = GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = podcastName,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))
            TonalSkipButton(
                iconRes = android.R.drawable.ic_media_rew,
                command = PodcastMediaService.ACTION_WIDGET_REWIND_15,
                sizeDp = 38
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            PlayPauseSquircleButton(isPlaying = isPlaying, sizeDp = 42)
            Spacer(modifier = GlanceModifier.width(6.dp))
            TonalSkipButton(
                iconRes = android.R.drawable.ic_media_ff,
                command = PodcastMediaService.ACTION_WIDGET_FORWARD_30,
                sizeDp = 38
            )
        }
    }

    // --- LAYOUT 3: 2x2 SQUARE MATERIAL YOU TILE ---
    @Composable
    private fun Widget2x2SquareLayout(
        title: String,
        podcastName: String,
        artwork: Bitmap?,
        isPlaying: Boolean
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionStartActivity<MainActivity>())
                .padding(14.dp)
        ) {
            // Top Row: Cover Art + Play/Pause Squircle
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidgetArtworkThumb(artwork = artwork, sizeDp = 52)
                Spacer(modifier = GlanceModifier.defaultWeight())
                PlayPauseSquircleButton(isPlaying = isPlaying, sizeDp = 48)
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Bottom Metadata
            Text(
                text = title,
                maxLines = 2,
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = podcastName,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    // --- LAYOUT 4: 4x2 FULL MATERIAL 3 MEDIA CARD ---
    @Composable
    private fun Widget4x2FullLayout(
        title: String,
        podcastName: String,
        artwork: Bitmap?,
        isPlaying: Boolean,
        queueCount: Int
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.surface)
                .clickable(actionStartActivity<MainActivity>())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top Section: Large Artwork + Episode Info
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidgetArtworkThumb(artwork = artwork, sizeDp = 58)
                Spacer(modifier = GlanceModifier.width(14.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = title,
                        maxLines = 2,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "$podcastName • $queueCount in Up Next",
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Bottom Section: Material 3 Pill Transport Bar
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WideTonalActionPill(
                    label = "↺ 15s",
                    isPrimary = false,
                    command = PodcastMediaService.ACTION_WIDGET_REWIND_15,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                WideTonalActionPill(
                    label = if (isPlaying) "⏸ Pause" else "▶ Play",
                    isPrimary = true,
                    command = PodcastMediaService.ACTION_WIDGET_TOGGLE_PLAY,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                WideTonalActionPill(
                    label = "30s ↻",
                    isPrimary = false,
                    command = PodcastMediaService.ACTION_WIDGET_FORWARD_30,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }

    // --- LAYOUT 5: 4x3 INTERACTIVE UP NEXT QUEUE WIDGET (NOW PLAYING + SCROLLABLE LIST) ---
    @Composable
    private fun Widget4x3QueueLayout(
        title: String,
        podcastName: String,
        artwork: Bitmap?,
        isPlaying: Boolean,
        queueEpisodes: List<HomeEpisodeUiModel>
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.surface)
                .padding(14.dp)
        ) {
            // 1. Top "Now Playing" Header Card
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(20.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidgetArtworkThumb(artwork = artwork, sizeDp = 46)
                Spacer(modifier = GlanceModifier.width(10.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = title,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = podcastName,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))
                TonalSkipButton(
                    iconRes = android.R.drawable.ic_media_rew,
                    command = PodcastMediaService.ACTION_WIDGET_REWIND_15,
                    sizeDp = 36
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                PlayPauseSquircleButton(isPlaying = isPlaying, sizeDp = 40)
                Spacer(modifier = GlanceModifier.width(6.dp))
                TonalSkipButton(
                    iconRes = android.R.drawable.ic_media_ff,
                    command = PodcastMediaService.ACTION_WIDGET_FORWARD_30,
                    sizeDp = 36
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // 2. Section Header
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UP NEXT QUEUE (${queueEpisodes.size})",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // 3. Scrollable Native Home-Screen Queue List
            if (queueEpisodes.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your Up Next queue is empty • Tap to browse",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(queueEpisodes.take(15), itemId = { it.id.hashCode().toLong() }) { ep ->
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            WidgetQueueRowItem(episode = ep)
                            Spacer(modifier = GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetQueueRowItem(episode: HomeEpisodeUiModel) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(
                    actionRunCallback<PlayEpisodeFromWidgetAction>(
                        actionParametersOf(WidgetEpisodeIdKey to episode.id)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = episode.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${episode.podcastName} • ${episode.durationFormatted}",
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            Box(
                modifier = GlanceModifier
                    .size(30.dp)
                    .cornerRadius(15.dp)
                    .background(GlanceTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(android.R.drawable.ic_media_play),
                    contentDescription = "Play episode",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier = GlanceModifier.size(16.dp)
                )
            }
        }
    }

    // --- SHARED MATERIAL 3 WIDGET BUILDING BLOCKS ---
    @Composable
    private fun WidgetArtworkThumb(artwork: Bitmap?, sizeDp: Int) {
        if (artwork != null) {
            Image(
                provider = ImageProvider(artwork),
                contentDescription = "Podcast Artwork",
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .size(sizeDp.dp)
                    .cornerRadius(14.dp)
            )
        } else {
            Box(
                modifier = GlanceModifier
                    .size(sizeDp.dp)
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(android.R.drawable.ic_btn_speak_now),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier = GlanceModifier.size((sizeDp / 2).dp)
                )
            }
        }
    }

    @Composable
    private fun PlayPauseSquircleButton(isPlaying: Boolean, sizeDp: Int) {
        Box(
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .cornerRadius(if (isPlaying) 14.dp else (sizeDp / 2).dp)
                .background(GlanceTheme.colors.primary)
                .clickable(
                    actionRunCallback<WidgetCommandAction>(
                        actionParametersOf(WidgetCommandKey to PodcastMediaService.ACTION_WIDGET_TOGGLE_PLAY)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                ),
                contentDescription = if (isPlaying) "Pause" else "Play",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size((sizeDp * 0.52f).dp)
            )
        }
    }

    @Composable
    private fun TonalSkipButton(iconRes: Int, command: String, sizeDp: Int) {
        Box(
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .cornerRadius((sizeDp / 2).dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(
                    actionRunCallback<WidgetCommandAction>(
                        actionParametersOf(WidgetCommandKey to command)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size((sizeDp * 0.5f).dp)
            )
        }
    }

    @Composable
    private fun WideTonalActionPill(
        label: String,
        isPrimary: Boolean,
        command: String,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val bg = if (isPrimary) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
        val fg = if (isPrimary) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer

        Box(
            modifier = modifier
                .height(40.dp)
                .cornerRadius(20.dp)
                .background(bg)
                .clickable(
                    actionRunCallback<WidgetCommandAction>(
                        actionParametersOf(WidgetCommandKey to command)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    private suspend fun loadWidgetArtworkBitmap(context: Context, url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(144, 144)
                    .allowHardware(false)
                    .build()
                val result = ImageLoader(context).execute(request)
                ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        }
}

private val WidgetCommandKey = ActionParameters.Key<String>("widget_media_command")
private val WidgetEpisodeIdKey = ActionParameters.Key<String>("widget_episode_id")

class WidgetCommandAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val command = parameters[WidgetCommandKey] ?: return
        val serviceIntent = Intent(context, PodcastMediaService::class.java).apply {
            action = command
        }
        runCatching {
            context.startService(serviceIntent)
        }
    }
}

class PlayEpisodeFromWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val episodeId = parameters[WidgetEpisodeIdKey] ?: return
        val serviceIntent = Intent(context, PodcastMediaService::class.java).apply {
            action = PodcastMediaService.ACTION_WIDGET_PLAY_EPISODE_ID
            putExtra(PodcastMediaService.EXTRA_WIDGET_EPISODE_ID, episodeId)
        }
        runCatching {
            context.startService(serviceIntent)
        }
    }
}

// --- 5 Distinct Widget Picker Entries (All backed by the same Responsive Engine!) ---
class PodcastWidget4x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodcastGlanceWidget()
}

class PodcastWidget4x1Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodcastGlanceWidget()
}

class PodcastWidget3x1Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodcastGlanceWidget()
}

class PodcastWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodcastGlanceWidget()
}

class PodcastWidget4x3Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodcastGlanceWidget()
}

class PodcastGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodcastGlanceWidget()
}

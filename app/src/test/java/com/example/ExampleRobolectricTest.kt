package com.example

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.EpisodeEntity
import com.example.data.rss.OpmlParser
import com.example.data.rss.RssFeedParser
import com.example.service.player.PlayerController
import com.example.service.player.PodcastMediaLibraryService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeExploreAndActivityTabs_displayClassicGooglePodcastsM3Layout() {
        composeTestRule.waitForIdle()

        // Verify GooglePodcastsHomeScreen top bar, squircle carousel, 'For you' header, and episodes
        composeTestRule.onNodeWithText("Podcasts").assertIsDisplayed()
        composeTestRule.onNodeWithText("For you").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("99% Invisible")[0].assertIsDisplayed()

        // Verify clicking Add RSS or OPML opens the Add or Backup Podcasts dialog
        composeTestRule.onNodeWithContentDescription("Add RSS or OPML").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add or Backup Podcasts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // Verify clicking Play on an episode triggers playback and shows the bottom mini-player
        composeTestRule.onAllNodesWithContentDescription("Play Episode")[0].performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mini_player_bar").assertIsDisplayed()

        // Navigate to Explore tab and verify Material 3 SearchBar
        composeTestRule.onNodeWithTag("nav_tab_explore").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("explore_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explore_search_bar").assertIsDisplayed()

        // Navigate to Activity tab and verify SingleChoiceSegmentedButtonRow (Queue, Downloads, History)
        composeTestRule.onNodeWithTag("nav_tab_activity").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("activity_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_segmented_button_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("segment_queue").assertIsDisplayed()
        composeTestRule.onNodeWithTag("segment_downloads").assertIsDisplayed()
        composeTestRule.onNodeWithTag("segment_history").assertIsDisplayed()
    }

    @Test
    fun rssAndOpmlParsers_extractPodcastEpisodesAndOutlinesWithXmlPullParser() {
        val sampleRssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                <channel>
                    <title>99% Invisible</title>
                    <description>Design and architecture podcast</description>
                    <itunes:author>Roman Mars</itunes:author>
                    <itunes:image href="https://example.com/art.jpg" />
                    <item>
                        <guid>ep-test-1</guid>
                        <title>The Sound of Space</title>
                        <description>Exploring urban acoustics.</description>
                        <pubDate>Tue, 18 Feb 2025 12:00:00 +0000</pubDate>
                        <enclosure url="https://example.com/ep1.mp3" length="123456" type="audio/mpeg" />
                        <itunes:duration>45:00</itunes:duration>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val parsed = RssFeedParser().parseRssStream(
            inputStream = ByteArrayInputStream(sampleRssXml.toByteArray(Charsets.UTF_8)),
            feedUrl = "https://feeds.99percentinvisible.org/99percentinvisible"
        )
        assertEquals("99% Invisible", parsed.podcast.title)
        assertEquals("https://example.com/art.jpg", parsed.podcast.artworkUrl)
        assertEquals(1, parsed.episodes.size)
        assertEquals("The Sound of Space", parsed.episodes[0].title)
        assertEquals("https://example.com/ep1.mp3", parsed.episodes[0].audioUrl)
        assertEquals(2700L, parsed.episodes[0].durationSeconds)

        val xmlParsed = com.example.data.rss.PodcastXmlParser.parseRssStream(
            inputStream = ByteArrayInputStream(sampleRssXml.toByteArray(Charsets.UTF_8)),
            feedUrl = "https://feeds.99percentinvisible.org/99percentinvisible"
        )
        assertEquals("99% Invisible", xmlParsed.subscription.title)
        assertEquals(1, xmlParsed.episodes.size)
        assertEquals("45 min", xmlParsed.episodes[0].durationText)

        val sampleOpmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
                <body>
                    <outline text="Podcasts">
                        <outline type="rss" title="Hard Fork" xmlUrl="https://feeds.simplecast.com/l2i9YnTd" />
                        <outline type="rss" title="Planet Money" xmlUrl="https://feeds.npr.org/510289/podcast.xml" />
                    </outline>
                </body>
            </opml>
        """.trimIndent()

        val outlines = OpmlParser.parseXmlString(sampleOpmlXml)
        assertEquals(2, outlines.size)
        assertEquals("https://feeds.simplecast.com/l2i9YnTd", outlines[0].xmlUrl)
        assertEquals("https://feeds.npr.org/510289/podcast.xml", outlines[1].xmlUrl)
    }

    @Test
    fun playerController_playsImmediatelyAndAppendsQueueToExoPlayer() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = PlayerController(context) { _, _, _ -> }
        try {
            val ep1 = EpisodeEntity(
                id = "ep-play-1",
                podcastFeedUrl = "https://example.com/feed",
                podcastTitle = "Show One",
                podcastAuthor = "Host",
                title = "Episode One",
                description = "First episode",
                pubDate = "2 hr ago",
                audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                durationSeconds = 2700L,
                artworkUrl = ""
            )
            val ep2 = ep1.copy(
                id = "ep-queue-2",
                title = "Episode Two",
                audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"
            )

            controller.playEpisode(ep1)
            assertEquals("ep-play-1", controller.playbackState.value.currentEpisode?.id)
            assertTrue(controller.playlistMediaIds.value.contains("ep-play-1"))

            controller.appendEpisodeToQueue(ep2)
            assertEquals(listOf("ep-play-1", "ep-queue-2"), controller.playlistMediaIds.value)
        } finally {
            controller.release()
        }
    }

    @Test
    fun androidAutoManifestAndRootTabs_exposeSubscriptionsAndUpNextQueue() {
        val rootTabs = PodcastMediaLibraryService.buildRootBrowsableTabs()
        assertEquals(2, rootTabs.size)
        assertEquals("Subscriptions", rootTabs[0].mediaMetadata.title.toString())
        assertEquals("Up Next Queue", rootTabs[1].mediaMetadata.title.toString())

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val autoResId = appInfo.metaData?.getInt("com.google.android.gms.car.application") ?: 0
        assertTrue("Expected com.google.android.gms.car.application meta-data", autoResId != 0)

        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, PodcastMediaLibraryService::class.java),
            PackageManager.GET_META_DATA
        )
        assertNotNull(serviceInfo)
    }
}

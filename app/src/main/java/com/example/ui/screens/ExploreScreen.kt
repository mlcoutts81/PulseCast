package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.models.ItunesPodcastItem
import com.example.data.models.PodcastEntity
import com.example.ui.components.PodcastArtwork
import com.example.viewmodel.SearchUiState

/**
 * Explore tab featuring a Material 3 [DockedSearchBar] connected to the iTunes Search API
 * (`https://itunes.apple.com/search?media=podcast&term=`) to discover and subscribe to shows.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GooglePodcastsExploreScreen(
    searchQuery: String,
    searchUiState: SearchUiState,
    subscribedPodcasts: List<PodcastEntity>,
    onQueryChange: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onSubscribeItunesItem: (ItunesPodcastItem) -> Unit,
    modifier: Modifier = Modifier
) {
    ExploreScreen(
        searchQuery = searchQuery,
        searchUiState = searchUiState,
        subscribedPodcasts = subscribedPodcasts,
        onQueryChange = onQueryChange,
        onExecuteSearch = onExecuteSearch,
        onSubscribeItunesItem = onSubscribeItunesItem,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    searchQuery: String,
    searchUiState: SearchUiState,
    subscribedPodcasts: List<PodcastEntity>,
    onQueryChange: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onSubscribeItunesItem: (ItunesPodcastItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val subscribedUrls = subscribedPodcasts.map { it.feedUrl }.toSet()

    val discoveryTopics = listOf(
        "Technology",
        "Design",
        "Daily News",
        "Science",
        "Economics",
        "History",
        "Comedy",
        "True Crime"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("explore_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Text(
                        text = stringResource(R.string.nav_explore),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {

        // Material 3 SearchBar connected to iTunes Search API
        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = onQueryChange,
                    onSearch = {
                        searchActive = false
                        onExecuteSearch(it)
                    },
                    expanded = searchActive,
                    onExpandedChange = { searchActive = it },
                    placeholder = { Text(stringResource(R.string.explore_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onQueryChange("")
                                    searchActive = false
                                },
                                modifier = Modifier.testTag("explore_clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("explore_search_input")
                )
            },
            expanded = false,
            onExpandedChange = { searchActive = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("explore_search_bar")
        ) {}

        // Topic Filter Chips for instant discovery
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            discoveryTopics.forEach { topic ->
                val isSelected = searchQuery.equals(topic, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onQueryChange(topic)
                        onExecuteSearch(topic)
                    },
                    label = { Text(topic) },
                    modifier = Modifier.testTag("topic_chip_$topic")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        when (searchUiState) {
            is SearchUiState.Idle -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Podcasts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Discover Shows on iTunes",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Search millions of podcasts or tap a category chip above to fetch live RSS feeds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is SearchUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is SearchUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = searchUiState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { onExecuteSearch(searchQuery) }) {
                            Text("Retry Search")
                        }
                    }
                }
            }

            is SearchUiState.Success -> {
                if (searchUiState.results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No podcasts found for \"${searchUiState.query}\".",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("itunes_search_results_list"),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(
                            items = searchUiState.results,
                            key = { it.collectionId ?: it.feedUrl.hashCode().toLong() }
                        ) { item ->
                            val isAlreadySubscribed = item.feedUrl != null && item.feedUrl in subscribedUrls
                            ItunesPodcastResultRow(
                                item = item,
                                isSubscribed = isAlreadySubscribed,
                                onSubscribeClick = { onSubscribeItunesItem(item) }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ItunesPodcastResultRow(
    item: ItunesPodcastItem,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("itunes_result_${item.collectionId ?: 0L}"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PodcastArtwork(
                    artworkUrl = item.bestArtworkUrl,
                    title = item.collectionName ?: "Podcast",
                    cornerRadius = 16.dp,
                    modifier = Modifier.size(68.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.collectionName ?: "Untitled Podcast",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.artistName ?: "Podcast Publisher",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!item.primaryGenreName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.primaryGenreName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                FilledTonalButton(
                    onClick = onSubscribeClick,
                    enabled = !isSubscribed,
                    shape = CircleShape,
                    modifier = Modifier.testTag("subscribe_itunes_${item.collectionId ?: 0L}")
                ) {
                    Icon(
                        imageVector = if (isSubscribed) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isSubscribed) {
                            stringResource(R.string.action_subscribed)
                        } else {
                            stringResource(R.string.action_subscribe)
                        }
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.screens.search

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.AlbumSummary
import dev.nocturn.app.data.model.ArtistSummary
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.EmptyState
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.LocalBottomChromePadding
import dev.nocturn.app.ui.components.PlaylistCard
import dev.nocturn.app.ui.components.ScreenError
import dev.nocturn.app.ui.components.ScreenLoading
import dev.nocturn.app.ui.components.SongRow
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.SearchTab
import dev.nocturn.app.ui.state.SearchUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.util.formatCount

/**
 * Search tab: an expressive two-state search surface.
 *
 * The collapsed state is a landing page with a large welcome title, the M3 [TopSearchBar], hot
 * keywords, local history and the service default keyword. Tapping the field expands the bar into
 * the real Material 3 Expressive [ExpandedFullScreenSearchBar], which hosts suggestions, the four
 * result tabs and their paginated lists.
 *
 * Expansion animation is owned by a locally remembered [SearchBarState] and mirrored to the
 * hoisted [SearchUiState.expanded] flag: when the local bar expands or collapses the optional
 * [onExpand] / [onCollapse] callbacks fire, while the other direction replays the same animation
 * when the model's flag changes. That keeps the screen stateless with respect to business data
 * while still using the real component state required by the M3 search API.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onSuggestionClick: (String) -> Unit = {},
    onHotClick: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onTabChange: (SearchTab) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onLikeClick: (Song) -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onExpand: () -> Unit = {},
    onCollapse: () -> Unit = {},
) {
    val textFieldState = rememberTextFieldState(state.query)
    val searchBarState = rememberSearchBarState(
        initialValue = if (state.expanded) SearchBarValue.Expanded else SearchBarValue.Collapsed,
    )
    val expanded = searchBarState.currentValue == SearchBarValue.Expanded

    // Hoisted state -> M3 component state. Covers external expansion (home search button, back
    // handling, a submitted keyword) and keeps the dialog in sync when the model collapses it.
    LaunchedEffect(state.expanded) {
        if (state.expanded) {
            searchBarState.animateToExpanded()
        } else {
            searchBarState.animateToCollapsed()
        }
    }

    // M3 component state -> hoisted state. Observing the *target* value reports focus-driven
    // expansion to the shell immediately; comparing against the hoisted flag makes the two
    // effects converge instead of ping-ponging.
    LaunchedEffect(searchBarState, state.expanded) {
        val hoistedExpanded = state.expanded
        snapshotFlow { searchBarState.targetValue }.collect { target ->
            val localExpanded = target == SearchBarValue.Expanded
            if (localExpanded != hoistedExpanded) {
                if (localExpanded) onExpand() else onCollapse()
            }
        }
    }

    // Hoisted query -> text field. A submitted search or a history chip replaces the whole query.
    LaunchedEffect(state.query) {
        if (textFieldState.text.toString() != state.query) {
            textFieldState.setTextAndPlaceCursorAtEnd(state.query)
        }
    }

    // Text field -> hoisted query. The view model fetches suggestions as the user types.
    val currentQuery by rememberUpdatedState(state.query)
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { value ->
            if (value != currentQuery) onQueryChange(value)
        }
    }

    Box(modifier.fillMaxSize()) {
        SearchCollapsedSurface(
            state = state,
            expanded = expanded,
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onSuggestionClick = onSuggestionClick,
            onHotClick = onHotClick,
            onClearHistory = onClearHistory,
            onTabChange = onTabChange,
            onLoadMore = onLoadMore,
            onSongClick = onSongClick,
            onLikeClick = onLikeClick,
            onPlaylistClick = onPlaylistClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onRetry = onRetry,
            onBack = onBack,
            onExpand = onExpand,
        )

        // The expanded surface is a full-screen dialog. It renders itself only while the shared
        // SearchBarState is expanded, so it can stay in the composition unconditionally.
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = {
                SearchInputField(
                    textFieldState = textFieldState,
                    searchBarState = searchBarState,
                    onSearch = onSearch,
                    trailing = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("search-expanded-close"),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "退出搜索")
                        }
                    },                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search-input-expanded"),
                )
            },
        ) {
            // The dialog covers the shell chrome, so lists only need to clear the gesture area.
            CompositionLocalProvider(
                LocalBottomChromePadding provides
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ) {
                if (state.submittedKeyword == null) {
                    SearchExploreContent(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onSuggestionClick = onSuggestionClick,
                        onHotClick = onHotClick,
                        onClearHistory = onClearHistory,
                    )
                } else {
                    SearchResultsContent(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onTabChange = onTabChange,
                        onLoadMore = onLoadMore,
                        onRetry = onRetry,
                        onSongClick = onSongClick,
                        onLikeClick = onLikeClick,
                        onPlaylistClick = onPlaylistClick,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Collapsed landing surface
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchCollapsedSurface(
    state: SearchUiState,
    expanded: Boolean,
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onHotClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTabChange: (SearchTab) -> Unit,
    onLoadMore: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onExpand: () -> Unit,
) {
    val exploreScroll = rememberScrollState()
    val exploreScrolled by remember(exploreScroll) { derivedStateOf { exploreScroll.value > 0 } }
    val barScrolled = state.submittedKeyword != null || exploreScrolled
    GlassScaffold(
        topBar = {
            GlassTopBar(scrolled = barScrolled) {
                SearchTopBar(
                    query = state.query,
                    textFieldState = textFieldState,
                    searchBarState = searchBarState,
                    onSearch = onSearch,
                    onClearQuery = { onQueryChange("") },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { topPadding ->
        // While the full-screen dialog is open it covers this surface. Keeping the lists out of
        // the composition avoids duplicated test tags and paging effects behind the dialog.
        if (!expanded) {
            if (state.submittedKeyword == null) {
                // The landing page scrolls under the frosted bar, title included.
                SearchExploreContent(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    topPadding = topPadding,
                    scrollState = exploreScroll,
                    header = { SearchWelcomeHeader() },
                    onSuggestionClick = onSuggestionClick,
                    onHotClick = onHotClick,
                    onClearHistory = onClearHistory,
                    onQueryPicked = onExpand,
                )
            } else {
                SearchResultsContent(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding),
                    onTabChange = onTabChange,
                    onLoadMore = onLoadMore,
                    onRetry = onRetry,
                    onSongClick = onSongClick,
                    onLikeClick = onLikeClick,
                    onPlaylistClick = onPlaylistClick,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun SearchWelcomeHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 18.dp),
    ) {
        Text(
            text = "搜索",
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "网易云音乐曲库，一搜即达",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The real Material 3 Expressive top search bar. */
@Suppress("DEPRECATION")
@Composable
private fun SearchTopBar(
    query: String,
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    onSearch: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Upstream renamed this composable to AppBarWithSearch in material3 1.5; the deprecated alias
    // is kept because the app contract names TopSearchBar and both draw the same component.
    val haptics = rememberHaptics()
    TopSearchBar(
        state = searchBarState,
        inputField = {
            SearchInputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = onSearch,
                trailing = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                haptics.tap()
                                onClearQuery()
                            },
                            modifier = Modifier.testTag("search-clear-query"),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "清除搜索内容")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search-input"),
            )
        },
        modifier = modifier.testTag("search-top-bar"),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@Composable
private fun SearchInputField(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    onSearch: (String) -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        onSearch = { keyword -> if (keyword.isNotBlank()) onSearch(keyword.trim()) },
        modifier = modifier,
        placeholder = {
            Text(
                text = "搜索歌曲、歌手、歌单",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = trailing,
    )
}

// ---------------------------------------------------------------------------------------------
// Landing content: suggestions, hot keywords and local history
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchExploreContent(
    state: SearchUiState,
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit,
    onHotClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onQueryPicked: (() -> Unit)? = null,
    topPadding: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
) {
    val haptics = rememberHaptics()
    val pick: (String) -> Unit = { keyword ->
        haptics.tap()
        onQueryPicked?.invoke()
        onHotClick(keyword)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .testTag("search-explore"),
    ) {
        Spacer(Modifier.height(topPadding))
        header?.invoke()
        if (state.suggestions.isNotEmpty()) {
            SearchSectionTitle(title = "搜索建议")
            state.suggestions.forEach { keyword ->
                SuggestionRow(
                    keyword = keyword,
                    onClick = {
                        haptics.tap()
                        onQueryPicked?.invoke()
                        onSuggestionClick(keyword)
                    },
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        val defaultKeyword = state.defaultKeyword?.takeIf { it.isNotBlank() }
        if (defaultKeyword != null) {
            SearchSectionTitle(title = "默认搜索")
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = { pick(defaultKeyword) },
                label = { Text(defaultKeyword, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.testTag("search-default-keyword"),
            )
        }

        if (state.hotKeywords.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SearchSectionTitle(title = "热门搜索")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.hotKeywords.forEachIndexed { index, keyword ->
                    SuggestionChip(
                        onClick = { pick(keyword) },
                        label = { Text(keyword, style = MaterialTheme.typography.labelLarge) },
                        icon = if (index == 0) {
                            {
                                Icon(
                                    Icons.Filled.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.testTag("search-hot"),
                    )
                }
            }
        }

        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SearchSectionTitle(
                title = "搜索历史",
                trailing = {
                    IconButton(
                        onClick = {
                            haptics.tap()
                            onClearHistory()
                        },
                        modifier = Modifier.testTag("search-clear-history"),
                    ) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = "清空搜索历史",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.history.forEach { keyword ->
                    SuggestionChip(
                        onClick = { pick(keyword) },
                        label = { Text(keyword, style = MaterialTheme.typography.labelLarge) },
                        icon = {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.testTag("search-history"),
                    )
                }
            }
        }

        Spacer(Modifier.height(bottomContentPadding(40.dp)))
    }
}

@Composable
private fun SuggestionRow(
    keyword: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 12.dp)
            .testTag("search-suggestion"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = keyword,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SearchSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------------------------
// Results: segmented tabs + paginated lists
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchResultsContent(
    state: SearchUiState,
    modifier: Modifier = Modifier,
    onTabChange: (SearchTab) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onBack: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchResultTabs(selected = state.tab, onTabChange = onTabChange)
        val errorMessage = state.error
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                state.loading -> ScreenLoading(label = "正在搜索「${state.submittedKeyword.orEmpty()}」")
                errorMessage != null -> ScreenError(
                    message = errorMessage.orEmpty(),
                    onRetry = onRetry,
                    modifier = Modifier.testTag("search-error"),
                )
                state.resultCount(state.tab) == 0 -> EmptyState(
                    title = "没有找到相关内容",
                    message = "换个关键词试试，或检查网络连接",
                    actionLabel = "重新搜索",
                    onAction = onBack,
                    modifier = Modifier.testTag("search-empty"),
                )
                else -> when (state.tab) {
                    SearchTab.Songs -> SearchSongsTab(
                        state = state,
                        onSongClick = onSongClick,
                        onLikeClick = onLikeClick,
                        onLoadMore = onLoadMore,
                    )
                    SearchTab.Playlists -> SearchPlaylistsTab(
                        state = state,
                        onPlaylistClick = onPlaylistClick,
                        onLoadMore = onLoadMore,
                    )
                    SearchTab.Albums -> SearchAlbumsTab(
                        state = state,
                        onAlbumClick = onAlbumClick,
                        onLoadMore = onLoadMore,
                    )
                    SearchTab.Artists -> SearchArtistsTab(
                        state = state,
                        onArtistClick = onArtistClick,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultTabs(
    selected: SearchTab,
    onTabChange: (SearchTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val tabs = SearchTab.entries
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("search-tabs"),
    ) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = selected == tab,
                onClick = {
                    haptics.step()
                    onTabChange(tab)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                icon = {},
                label = { Text(tab.label, maxLines = 1) },
                modifier = Modifier.testTag(searchTabTag(tab)),
            )
        }
    }
}

private fun searchTabTag(tab: SearchTab): String = when (tab) {
    SearchTab.Songs -> "search-tab-songs"
    SearchTab.Playlists -> "search-tab-playlists"
    SearchTab.Albums -> "search-tab-albums"
    SearchTab.Artists -> "search-tab-artists"
}

@Composable
private fun SearchSongsTab(
    state: SearchUiState,
    onSongClick: (Song, List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search-results-songs"),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = bottomContentPadding()),
    ) {
        itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song,
                onClick = { onSongClick(song, state.songs) },
                modifier = Modifier.testTag("search-result-song"),
                index = index + 1,
                isPlaying = song.id == state.nowPlayingSongId,
                isLiked = state.likedSongIds.contains(song.id),
                onLikeClick = { onLikeClick(song) },
            )
        }
        item(key = "paging-songs") {
            SearchPagingFooter(state = state, onLoadMore = onLoadMore)
        }
    }
}

@Composable
private fun SearchPlaylistsTab(
    state: SearchUiState,
    onPlaylistClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("search-results-playlists"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomContentPadding()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(state.playlists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search-result-playlist"),
                showCreator = true,
            )
        }
        item(key = "paging-playlists") {
            SearchPagingFooter(state = state, onLoadMore = onLoadMore)
        }
    }
}

@Composable
private fun SearchAlbumsTab(
    state: SearchUiState,
    onAlbumClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search-results-albums"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomContentPadding()),
    ) {
        items(state.albums, key = { it.id }) { album ->
            SearchAlbumRow(album = album, onClick = { onAlbumClick(album.id) })
        }
        item(key = "paging-albums") {
            SearchPagingFooter(state = state, onLoadMore = onLoadMore)
        }
    }
}

@Composable
private fun SearchAlbumRow(
    album: AlbumSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("search-result-album"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            imageUrl = album.coverUrl,
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.small,
            seed = album.id.hashCode(),
            contentDescription = album.name,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    if (album.artistText.isNotBlank()) append(album.artistText)
                    if (album.trackCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${album.trackCount} 首")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchArtistsTab(
    state: SearchUiState,
    onArtistClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search-results-artists"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomContentPadding()),
    ) {
        items(state.artists, key = { it.id }) { artist ->
            SearchArtistRow(artist = artist, onClick = { onArtistClick(artist.id) })
        }
        item(key = "paging-artists") {
            SearchPagingFooter(state = state, onLoadMore = onLoadMore)
        }
    }
}

@Composable
private fun SearchArtistRow(
    artist: ArtistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("search-result-artist"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            imageUrl = artist.avatarUrl,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            seed = artist.id.hashCode(),
            contentDescription = artist.name,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    if (artist.songCount > 0) append("${artist.songCount} 首单曲")
                    if (artist.fansCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${formatCount(artist.fansCount)} 粉丝")
                    }
                    if (isEmpty()) append("歌手")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Infinite-scroll footer. It asks for the next page as soon as it enters the composition; the view
 * model de-duplicates requests through [SearchUiState.loadingMore] and [SearchUiState.hasMore].
 */
@Composable
private fun SearchPagingFooter(
    state: SearchUiState,
    onLoadMore: () -> Unit,
) {
    if (!state.hasMore && !state.loadingMore) return
    LaunchedEffect(state.tab, state.resultCount(state.tab), state.hasMore) {
        if (state.hasMore && !state.loadingMore) onLoadMore()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .testTag("search-paging"),
        contentAlignment = Alignment.Center,
    ) {
        if (state.loadingMore) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "正在加载更多…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews (light / dark, deterministic in-memory data, no network artwork)
// ---------------------------------------------------------------------------------------------

@Preview(name = "搜索首页 · 深色", showBackground = true)
@Composable
private fun SearchLandingPreviewDark() {
    NocturnTheme(themeMode = ThemeMode.Dark) {
        SearchScreen(state = PreviewData.searchIdle)
    }
}

@Preview(name = "搜索首页 · 浅色", showBackground = true)
@Composable
private fun SearchLandingPreviewLight() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        SearchScreen(state = PreviewData.searchIdle)
    }
}

@Preview(name = "搜索结果 · 深色", showBackground = true)
@Composable
private fun SearchResultsPreviewDark() {
    // Previews cannot render the full-screen search dialog, so the same result surface is shown
    // inline from the collapsed state.
    NocturnTheme(themeMode = ThemeMode.Dark) {
        SearchScreen(state = PreviewData.searchResults.copy(expanded = false))
    }
}

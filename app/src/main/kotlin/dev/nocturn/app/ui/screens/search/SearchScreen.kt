@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularWavyProgressIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.nocturn.app.data.model.AlbumSummary
import dev.nocturn.app.data.model.ArtistSummary
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.EmptyState
import dev.nocturn.app.ui.components.GlassMaterials
import dev.nocturn.app.ui.components.LocalBottomChromePadding
import dev.nocturn.app.ui.components.PlaylistCard
import dev.nocturn.app.ui.components.ScreenError
import dev.nocturn.app.ui.components.ScreenLoading
import dev.nocturn.app.ui.components.SongRow
import dev.nocturn.app.ui.components.blurAvailable
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.drawNocturnBackdrop
import dev.nocturn.app.ui.components.glass
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.SearchTab
import dev.nocturn.app.ui.state.SearchUiState
import dev.nocturn.app.ui.theme.LocalIsDarkTheme
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.util.formatCount
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** Material 3 Expressive non-linear physical spring specs. */
private val M3SpringSpatialFloat = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private val M3SpringSpatialDp = spring<Dp>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * Search screen: Material 3 Expressive floating expandable search surface.
 *
 * The collapsed state presents an organic floating search capsule resting over a welcoming
 * discover landing page with hot keywords and local history. Tapping the search bar triggers
 * a physics-based Material 3 non-linear spring animation that smoothly morphs the capsule
 * geometry (corner radius, elevation, horizontal margins and height) into an elevated floating
 * search sheet. Suggestions, exploration items, and tabbed search results animate within this
 * floating window with natural spatial continuity, retracting seamlessly on dismiss.
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
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val textFieldState = rememberTextFieldState(state.query)
    val searchBarState = rememberSearchBarState(
        initialValue = if (state.expanded) SearchBarValue.Expanded else SearchBarValue.Collapsed,
    )

    // Hoisted state -> M3 component state.
    LaunchedEffect(state.expanded) {
        if (state.expanded) {
            searchBarState.animateToExpanded()
        } else {
            searchBarState.animateToCollapsed()
        }
    }

    // M3 component state -> hoisted state.
    LaunchedEffect(searchBarState, state.expanded) {
        val hoistedExpanded = state.expanded
        snapshotFlow { searchBarState.targetValue }.collect { target ->
            val localExpanded = target == SearchBarValue.Expanded
            if (localExpanded != hoistedExpanded) {
                if (localExpanded) onExpand() else onCollapse()
            }
        }
    }

    // Hoisted query -> text field.
    LaunchedEffect(state.query) {
        if (textFieldState.text.toString() != state.query) {
            textFieldState.setTextAndPlaceCursorAtEnd(state.query)
        }
    }

    // Text field -> hoisted query.
    val currentQuery by rememberUpdatedState(state.query)
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { value ->
            if (value != currentQuery) onQueryChange(value)
        }
    }

    val isExpanded = state.expanded || searchBarState.currentValue == SearchBarValue.Expanded ||
        searchBarState.targetValue == SearchBarValue.Expanded || state.submittedKeyword != null

    // Material 3 Expressive Non-linear Spring Physics
    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = M3SpringSpatialFloat,
        label = "search-expansion-progress",
    )
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 24.dp else 28.dp,
        animationSpec = M3SpringSpatialDp,
        label = "search-corner-radius",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isExpanded) 10.dp else 4.dp,
        animationSpec = M3SpringSpatialDp,
        label = "search-elevation",
    )
    val animatedHorizontalPadding by animateDpAsState(
        targetValue = if (isExpanded) 8.dp else 16.dp,
        animationSpec = M3SpringSpatialDp,
        label = "search-horizontal-padding",
    )

    val colors = MaterialTheme.colorScheme
    val dark = LocalIsDarkTheme.current
    val searchBackdrop = if (blurAvailable) {
        rememberLayerBackdrop {
            drawNocturnBackdrop(colors, dark)
            drawContent()
        }
    } else {
        null
    }
    val baseContainer = MaterialTheme.colorScheme.surfaceContainerHighest
    val fallbackContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val glassMaterial = remember(dark, baseContainer, fallbackContainer) {
        dev.nocturn.app.ui.components.GlassMaterial(
            blurRadius = 32f,
            colors = top.yukonga.miuix.kmp.blur.BlurColors(
                blendColors = listOf(top.yukonga.miuix.kmp.blur.BlendColorEntry(baseContainer.copy(alpha = if (dark) 0.75f else 0.82f))),
                saturation = if (dark) 1.25f else 1.45f,
            ),
            fallback = fallbackContainer,
            highlight = null,
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("search-screen-root"),
    ) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomChromePadding = LocalBottomChromePadding.current
        val bottomPadding = maxOf(bottomChromePadding, navBarBottom) + 8.dp

        val animatedTopPadding by animateDpAsState(
            targetValue = if (isExpanded) (statusBarTop + 4.dp) else (statusBarTop + 8.dp),
            animationSpec = M3SpringSpatialDp,
            label = "search-top-padding",
        )

        val targetExpandedHeight = (maxHeight - statusBarTop - 6.dp - bottomPadding).coerceAtLeast(56.dp)
        val animatedHeight by animateDpAsState(
            targetValue = if (isExpanded) targetExpandedHeight else 56.dp,
            animationSpec = M3SpringSpatialDp,
            label = "search-animated-height",
        )

        val floatingShape = RoundedCornerShape(animatedCornerRadius)

        // -----------------------------------------------------------------------------------------
        // Background Page: recorded into searchBackdrop so the floating window can frost it
        // -----------------------------------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (searchBackdrop != null) Modifier.layerBackdrop(searchBackdrop) else Modifier),
        ) {
            // Layer 1: Collapsed Landing Explore Surface (Underneath Floating Search Bar)
            val exploreScroll = rememberScrollState()
            SearchExploreContent(
                state = state,
                modifier = Modifier.fillMaxSize(),
                topPadding = statusBarTop + 76.dp,
                scrollState = exploreScroll,
                onSuggestionClick = onSuggestionClick,
                onHotClick = onHotClick,
                onClearHistory = onClearHistory,
                onQueryPicked = onExpand,
            )

            // Layer 2: Expressive Scrim Fade Behind the Floating Window
            if (expansionProgress > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (expansionProgress * 0.42f).coerceIn(0f, 0.42f) }
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (state.submittedKeyword == null) {
                                haptics.tap()
                                focusManager.clearFocus()
                                onCollapse()
                                scope.launch { searchBarState.animateToCollapsed() }
                            }
                        },
                )
            }
        }

        // -----------------------------------------------------------------------------------------
        // Layer 3: Floating Search Bar / Window (Expands & Retracts with M3 Non-Linear Spring & Frosted Glass)
        // -----------------------------------------------------------------------------------------
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = animatedTopPadding)
                .padding(horizontal = animatedHorizontalPadding)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .height(animatedHeight)
                .shadow(
                    elevation = animatedElevation,
                    shape = floatingShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                )
                .then(
                    if (searchBackdrop != null) {
                        Modifier.glass(searchBackdrop, floatingShape, glassMaterial)
                    } else {
                        Modifier
                    },
                )
                .testTag("search-top-bar"),
            shape = floatingShape,
            color = if (searchBackdrop != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = animatedElevation,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Search Bar Row (Input + Trailing Actions)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(
                            if (!isExpanded) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptics.tap()
                                    onExpand()
                                    scope.launch { searchBarState.animateToExpanded() }
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    SearchInputField(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        defaultKeyword = state.defaultKeyword,
                        onSearch = onSearch,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (textFieldState.text.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            haptics.tap()
                                            onQueryChange("")
                                        },
                                        modifier = Modifier.testTag("search-clear-query"),
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "清除搜索内容")
                                    }
                                }
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
                                    exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)) +
                                        scaleOut(spring(stiffness = Spring.StiffnessHigh)),
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptics.tap()
                                            focusManager.clearFocus()
                                            onBack()
                                        },
                                        modifier = Modifier.testTag("search-expanded-close"),
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "退出搜索")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search-input"),
                    )
                }

                // Divider separating the input bar from the expanded body
                if (expansionProgress > 0.01f) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = (expansionProgress * 0.7f).coerceIn(0f, 1f),
                        ),
                    )
                }

                // Expanded Floating Body
                if (expansionProgress > 0.05f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .graphicsLayer { alpha = expansionProgress },
                    ) {
                        CompositionLocalProvider(LocalBottomChromePadding provides 16.dp) {
                            if (state.submittedKeyword == null) {
                                SearchFloatingExploreContent(
                                    state = state,
                                    onSuggestionClick = onSuggestionClick,
                                    onHotClick = onHotClick,
                                    onClearHistory = onClearHistory,
                                    onQueryChange = onQueryChange,
                                )
                            } else {
                                SearchResultsContent(
                                    state = state,
                                    modifier = Modifier.fillMaxSize(),
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
            }
        }
    }
}

@Composable
private fun SearchFloatingExploreContent(
    state: SearchUiState,
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit,
    onHotClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onQueryChange: (String) -> Unit = {},
) {
    val haptics = rememberHaptics()
    val scrollState = rememberScrollState()
    val isQueryActive = state.query.trim().isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (isQueryActive) {
            SearchSectionTitle(title = "搜索建议")
            Spacer(Modifier.height(4.dp))

            val trimmedQuery = state.query.trim()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        haptics.tap()
                        onSuggestionClick(trimmedQuery)
                    }
                    .padding(horizontal = 4.dp, vertical = 12.dp)
                    .testTag("search-direct-query"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "搜索 \"$trimmedQuery\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }

            state.suggestions.forEach { keyword ->
                SuggestionRow(
                    keyword = keyword,
                    onClick = {
                        haptics.tap()
                        onSuggestionClick(keyword)
                    },
                    onFillQuery = {
                        haptics.tap()
                        onQueryChange(keyword)
                    },
                )
            }

            val matchingHistory = state.history.filter {
                it.contains(trimmedQuery, ignoreCase = true) &&
                    it != trimmedQuery &&
                    it !in state.suggestions
            }
            if (matchingHistory.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SearchSectionTitle(title = "历史匹配")
                Spacer(Modifier.height(4.dp))
                matchingHistory.forEach { keyword ->
                    SuggestionRow(
                        keyword = keyword,
                        icon = Icons.Filled.History,
                        onClick = {
                            haptics.tap()
                            onSuggestionClick(keyword)
                        },
                        onFillQuery = {
                            haptics.tap()
                            onQueryChange(keyword)
                        },
                    )
                }
            }
        } else {
            val defaultKeyword = state.defaultKeyword?.takeIf { it.isNotBlank() }
            if (defaultKeyword != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            haptics.tap()
                            onSuggestionClick(defaultKeyword)
                        }
                        .padding(horizontal = 4.dp, vertical = 10.dp)
                        .testTag("search-default-keyword"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = defaultKeyword,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "默认推荐 · 点击立即搜索",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.tap()
                            onQueryChange(defaultKeyword)
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.NorthWest,
                            contentDescription = "填入搜索框",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.tap()
                            onSuggestionClick(defaultKeyword)
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "立即搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
                            onClick = {
                                haptics.tap()
                                onHotClick(keyword)
                            },
                            label = { Text(keyword, style = MaterialTheme.typography.labelLarge) },
                            icon = if (index == 0) {
                                {
                                    Icon(
                                        Icons.AutoMirrored.Filled.TrendingUp,
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
                            onClick = {
                                haptics.tap()
                                onHotClick(keyword)
                            },
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
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SearchInputField(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    onSearch: (String) -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    defaultKeyword: String? = null,
) {
    val placeholderText = defaultKeyword?.takeIf { it.isNotBlank() } ?: "搜索歌曲、歌手、歌单"
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        onSearch = { keyword ->
            val effective = keyword.trim().ifEmpty { defaultKeyword?.trim().orEmpty() }
            if (effective.isNotBlank()) onSearch(effective)
        },
        modifier = modifier,
        placeholder = {
            Text(
                text = placeholderText,
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
                        Icons.AutoMirrored.Filled.TrendingUp,
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
                                    Icons.AutoMirrored.Filled.TrendingUp,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Search,
    onFillQuery: (() -> Unit)? = null,
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
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .testTag("search-suggestion"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
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
        if (onFillQuery != null) {
            IconButton(
                onClick = {
                    haptics.tap()
                    onFillQuery()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.NorthWest,
                    contentDescription = "填入搜索框",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(
            onClick = {
                haptics.tap()
                onClick()
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "立即搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
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

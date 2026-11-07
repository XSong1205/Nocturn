package dev.nocturn.app.ui.preview

import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.data.model.AccountStats
import dev.nocturn.app.data.model.AlbumDetail
import dev.nocturn.app.data.model.AlbumRef
import dev.nocturn.app.data.model.AlbumSummary
import dev.nocturn.app.data.model.ArtistDetail
import dev.nocturn.app.data.model.ArtistRef
import dev.nocturn.app.data.model.ArtistSummary
import dev.nocturn.app.data.model.LyricLine
import dev.nocturn.app.data.model.LyricWord
import dev.nocturn.app.data.model.Lyrics
import dev.nocturn.app.data.model.PlaylistDetail
import dev.nocturn.app.data.model.PlaylistSummary
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.Toplist
import dev.nocturn.app.ui.state.AlbumUiState
import dev.nocturn.app.ui.state.ArtistUiState
import dev.nocturn.app.ui.state.HomeUiState
import dev.nocturn.app.ui.state.LibraryUiState
import dev.nocturn.app.ui.state.LikedSongsUiState
import dev.nocturn.app.ui.state.MiniPlayerUiState
import dev.nocturn.app.ui.state.PlayerUiState
import dev.nocturn.app.ui.state.PlaylistUiState
import dev.nocturn.app.ui.state.SearchTab
import dev.nocturn.app.ui.state.SearchUiState
import dev.nocturn.app.ui.state.SettingsUiState
import dev.nocturn.localapi.client.AudioQuality
import dev.nocturn.app.playback.RepeatMode

/**
 * Deterministic in-memory content for Compose previews, Robolectric screenshot tests and
 * component tests. Artwork URLs are intentionally empty so previews and tests never touch the
 * network: the generated cover art is part of the design language.
 */
object PreviewData {

    private val jay = ArtistRef(6452, "周杰伦")
    private val eason = ArtistRef(2116, "陈奕迅")
    private val escapePlan = ArtistRef(11127, "逃跑计划")
    private val buy = ArtistRef(12085537, "买辣椒也用券")
    private val pu = ArtistRef(4721, "朴树")
    private val chen = ArtistRef(1058228, "陈鸿宇")
    private val beyond = ArtistRef(12392, "Beyond")
    private val karen = ArtistRef(8926, "莫文蔚")
    private val zhao = ArtistRef(6731, "赵雷")
    private val sodagreen = ArtistRef(9348, "苏打绿")

    private fun song(
        id: Long,
        name: String,
        artists: List<ArtistRef>,
        albumName: String,
        durationMs: Long,
        fee: Int = 0,
    ) = Song(
        id = id,
        name = name,
        artists = artists,
        album = AlbumRef(id / 7 + 1000, albumName, null),
        durationMs = durationMs,
        fee = fee,
    )

    val songs: List<Song> = listOf(
        song(186016, "晴天", listOf(jay), "叶惠美", 269_000),
        song(1330348068, "起风了", listOf(buy), "起风了", 325_000, fee = 1),
        song(347230, "海阔天空", listOf(beyond), "乐与怒", 326_000),
        song(28815250, "平凡之路", listOf(pu), "猎户星座", 302_000),
        song(31445772, "理想三旬", listOf(chen), "浓烟下的诗歌电台", 210_000),
        song(65538, "好久不见", listOf(eason), "认了吧", 250_000),
        song(25727803, "夜空中最亮的星", listOf(escapePlan), "世界", 252_000),
        song(1330348069, "慢慢喜欢你", listOf(karen), "我们在中场相遇", 265_000),
        song(29567189, "成都", listOf(zhao), "无法长大", 328_000),
        song(375137, "小情歌", listOf(sodagreen), "小宇宙", 273_000),
    )

    val albums: List<AlbumSummary> = listOf(
        AlbumSummary(1001, "叶惠美", null, "周杰伦", 11, 1_060_000_000_000),
        AlbumSummary(1002, "猎户星座", null, "朴树", 11, 1_508_000_000_000),
        AlbumSummary(1003, "我们在中场相遇", null, "莫文蔚", 10, 1_530_000_000_000),
    )

    val playlists: List<PlaylistSummary> = listOf(
        PlaylistSummary(
            id = 24381616,
            name = "深夜霓虹 · 城市夜行歌单",
            coverUrl = null,
            playCount = 2_458_000,
            trackCount = 50,
            creatorName = "Nocturn 编辑部",
            description = "把霓虹揉进耳机里，给夜行的人一点光。",
            updateFrequency = "每周三更新",
        ),
        PlaylistSummary(
            id = 24381617,
            name = "华语流行 · 单曲循环",
            coverUrl = null,
            playCount = 8_640_000,
            trackCount = 100,
            creatorName = "音乐研究所",
        ),
        PlaylistSummary(
            id = 24381618,
            name = "落日飞车：公路与海",
            coverUrl = null,
            playCount = 1_020_000,
            trackCount = 42,
            creatorName = "公路电台",
        ),
        PlaylistSummary(
            id = 24381619,
            name = "自习室 · 纯音乐",
            coverUrl = null,
            playCount = 560_000,
            trackCount = 80,
            creatorName = "专注研究所",
        ),
        PlaylistSummary(
            id = 24381620,
            name = "周四的爵士夜",
            coverUrl = null,
            playCount = 98_000,
            trackCount = 30,
            creatorName = "Blue Note",
        ),
    )


    val charts: List<Toplist> = listOf(
        Toplist(
            id = 19723756,
            name = "飙升榜",
            coverUrl = null,
            updateFrequency = "每天更新",
            trackCount = 100,
            tracks = songs.take(3),
        ),
        Toplist(
            id = 3779629,
            name = "热歌榜",
            coverUrl = null,
            updateFrequency = "每周更新",
            trackCount = 100,
            tracks = songs.drop(2).take(3),
        ),
        Toplist(
            id = 3778678,
            name = "新歌榜",
            coverUrl = null,
            updateFrequency = "每天更新",
            trackCount = 50,
            tracks = songs.drop(4).take(3),
        ),
    )

    /** "根据你喜爱的歌曲推荐": deliberately a different order from the chart tracks. */
    val personalizedSongs: List<Song> = songs.reversed().take(6)

    val lyrics: Lyrics = Lyrics(
        listOf(
            LyricLine(0, "晴天 - 周杰伦"),
            LyricLine(19_000, "故事的小黄花"),
            LyricLine(24_000, "从出生那年就飘着"),
            LyricLine(29_000, "童年的荡秋千"),
            LyricLine(34_000, "随记忆一直晃到现在"),
            LyricLine(40_000, "吹着前奏望着天空"),
            LyricLine(46_000, "我想起花瓣试着掉落"),
            LyricLine(52_000, "为你翘课的那一天"),
            LyricLine(58_000, "花落的那一天"),
            LyricLine(64_000, "教室的那一间"),
            LyricLine(70_000, "我怎么看不见"),
            LyricLine(76_000, "消失的下雨天"),
            LyricLine(82_000, "我好想再淋一遍"),
            LyricLine(90_000, "没想到失去的勇气我还留着"),
            LyricLine(98_000, "好想再问一遍"),
            LyricLine(104_000, "你会等待还是离开"),
            LyricLine(112_000, "刮风这天 我试过握着你手"),
            LyricLine(120_000, "但偏偏 雨渐渐 大到我看你不见"),
            LyricLine(130_000, "还要多久 我才能在你身边"),
            LyricLine(140_000, "等到放晴的那天"),
            LyricLine(146_000, "也许我会比较好一点"),
        ),
    )

    val playlistsDetail: PlaylistDetail = PlaylistDetail(
        id = 24381616,
        name = "深夜霓虹 · 城市夜行歌单",
        coverUrl = null,
        description = "收录 50 首适合夜行的歌。把霓虹揉进耳机里，给夜行的人一点光。" +
            "从 City Pop 到华语独立，从合成器到钢琴，愿你在城市的缝隙里找到自己的节奏。",
        creatorName = "Nocturn 编辑部",
        creatorAvatarUrl = null,
        playCount = 2_458_000,
        trackCount = 50,
        subscribed = false,
        tags = listOf("华语", "流行", "夜晚"),
        updateFrequency = "每周三更新",
        createTime = 1_600_000_000_000,
    )

    val albumDetail: AlbumDetail = AlbumDetail(
        id = 1001,
        name = "叶惠美",
        coverUrl = null,
        artists = listOf(jay),
        artistText = "周杰伦",
        publishTime = 1_060_000_000_000,
        company = "杰威尔音乐",
        description = "《叶惠美》是周杰伦的第四张录音室专辑，收录《晴天》《东风破》等作品。",
        songs = songs.take(5),
    )

    val artistDetail: ArtistDetail = ArtistDetail(
        id = 6452,
        name = "周杰伦",
        avatarUrl = null,
        briefDescription = "华语流行乐男歌手、音乐人、演员、导演、编剧。",
        albumCount = 15,
        songCount = 320,
        fansCount = 12_000_000,
        hotSongs = songs.take(6),
        albums = albums,
    )

    val profile = AccountProfile(
        id = 3200001,
        nickname = "夜行电台",
        avatarUrl = null,
        signature = "把夜色交给音乐。",
    )

    val stats = AccountStats(playlistCount = 8, followingCount = 42, followerCount = 128)

    // ---------------------------------------------------------------------------------------
    // Screen states
    // ---------------------------------------------------------------------------------------

    val homeState = HomeUiState(
        loading = false,
        greeting = "晚上好",
        recommendedPlaylists = playlists,
        charts = charts,
        dailySongs = songs.take(4),
        personalizedSongs = personalizedSongs,
        isLoggedIn = true,
        likedSongIds = setOf(SONG_LIKED_ID),
    )

    const val SONG_LIKED_ID = 186016L

    val homeStateAnonymous = HomeUiState(
        loading = false,
        greeting = "晚上好",
        recommendedPlaylists = playlists,
        charts = charts,
        isLoggedIn = false,
    )

    val searchIdle = SearchUiState(
        hotKeywords = listOf("晴天", "夜曲", "海阔天空", "起风了", "慢慢喜欢你", "小情歌"),
        defaultKeyword = "晴天",
        history = listOf("周杰伦", "华语流行"),
    )

    val searchResults = SearchUiState(
        query = "晴天",
        expanded = true,
        submittedKeyword = "晴天",
        tab = SearchTab.Songs,
        songs = songs,
        hasMore = true,
        likedSongIds = setOf(SONG_LIKED_ID),
    )

    val libraryLoggedIn = LibraryUiState(
        loggedIn = true,
        loading = false,
        profile = profile,
        stats = stats,
        playlists = playlists,
        recentSongs = songs.take(6),
        likedCount = 128,
    )

    val libraryAnonymous = LibraryUiState(
        loggedIn = false,
        loading = false,
        recentSongs = songs.take(6),
    )

    val playlistState = PlaylistUiState(
        playlistId = playlistsDetail.id,
        loading = false,
        detail = playlistsDetail,
        songs = songs,
        hasMore = true,
        isLoggedIn = true,
        likedSongIds = setOf(SONG_LIKED_ID),
    )

    val albumState = AlbumUiState(albumId = albumDetail.id, loading = false, detail = albumDetail)

    val artistState = ArtistUiState(artistId = artistDetail.id, loading = false, detail = artistDetail)

    val likedSongsState = LikedSongsUiState(
        loading = false,
        songs = songs.take(6),
        likedSongIds = songs.take(6).map { it.id }.toSet(),
    )

    val playerState = PlayerUiState(
        visible = true,
        song = songs[0],
        playing = true,
        positionMs = 74_000,
        durationMs = 269_000,
        liked = true,
        repeatMode = RepeatMode.All,
        lyrics = lyrics,
        queue = songs,
        currentIndex = 0,
        qualityLabel = "极高",
        isLoggedIn = true,
    )

    val miniPlayerState = MiniPlayerUiState(
        song = songs[0],
        playing = true,
        progress = 74f / 269f,
    )

    val settingsState = SettingsUiState(
        quality = AudioQuality.Exhigh,
        dynamicColor = true,
        hapticsEnabled = true,
        searchHistoryCount = 2,
        cacheSizeBytes = 12_800_000L,
        version = "0.1.28",
    )

    /** Deterministic word timing for reviewing wrapped text, translation and focus blur. */
    val bilingualPlayerState = playerState.copy(
        song = songs[0].copy(name = "Bloom", artists = listOf(ArtistRef(1, "Dabin"), ArtistRef(2, "Dia Frampton"))),
        playing = false,
        positionMs = 115_800,
        durationMs = 251_000,
        qualityLabel = "Hi-Res",
        lyrics = Lyrics(listOf(
            previewKaraokeLine(100_000, "We could start again", "我们可以重新开始"),
            previewKaraokeLine(106_000, "Holding tight to what we had", "将我们所拥有的一切都牢牢抓紧"),
            previewKaraokeLine(114_000, "If it's lost, will it come back?", "倘若失去，是否能否再复返呢"),
            previewKaraokeLine(122_000, "Tell me if this windin' road", "告诉我，这条蜿蜒的路"),
            previewKaraokeLine(130_000, "Will lead us home", "是否会引领我们归家"),
            previewKaraokeLine(138_000, "We could start again", "我们可以重新开始"),
        )),
    )

    private fun previewKaraokeLine(startMs: Long, text: String, translation: String): LyricLine {
        val tokens = text.split(' ')
        return LyricLine(
            startMs = startMs,
            text = text,
            translation = translation,
            durationMs = 6_000,
            words = tokens.mapIndexed { index, token ->
                LyricWord(startMs + index * 700L, 700L, token + if (index < tokens.lastIndex) " " else "")
            },
        )
    }
}
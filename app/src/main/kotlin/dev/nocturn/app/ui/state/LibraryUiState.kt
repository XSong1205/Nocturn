package dev.nocturn.app.ui.state

import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.data.model.AccountStats
import dev.nocturn.app.data.model.PlaylistSummary
import dev.nocturn.app.data.model.QrChallenge
import dev.nocturn.app.data.model.QrLoginState
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.localapi.client.AudioQuality

enum class LoginMode { Qr, Code, Password }

data class LoginUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val mode: LoginMode = LoginMode.Qr,
    val sendingCode: Boolean = false,
    val cooldownSeconds: Int = 0,
    val challenge: QrChallenge? = null,
    val state: QrLoginState = QrLoginState.WaitingForScan,
    val error: String? = null,
    val authorizedProfile: AccountProfile? = null,
)

data class LibraryUiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val profile: AccountProfile? = null,
    val stats: AccountStats? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val recentSongs: List<Song> = emptyList(),
    val likedCount: Int? = null,
    val likedSongIds: Set<Long> = emptySet(),
    val nowPlayingSongId: Long? = null,
    val quality: AudioQuality = AudioQuality.Exhigh,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val login: LoginUiState = LoginUiState(),
)

data class LikedSongsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val songs: List<Song> = emptyList(),
    val likedSongIds: Set<Long> = emptySet(),
    val nowPlayingSongId: Long? = null,
)

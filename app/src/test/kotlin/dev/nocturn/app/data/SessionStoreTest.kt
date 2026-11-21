package dev.nocturn.app.data

import android.content.Context
import dev.nocturn.app.data.model.AccountProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nocturn-session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun cookiesAreNullByDefault() = runBlocking {
        val store = SessionStore(context)
        assertNull(store.cookie())
        assertNull(store.accountCookie())
        assertNull(store.anonymousCookie())
        assertNull(store.session.value)
    }

    @Test
    fun saveAnonymousCookiePersistsAcrossInstances() = runBlocking {
        val firstStore = SessionStore(context)
        firstStore.saveAnonymousCookie("MUSIC_A=anon123456789")

        assertEquals("MUSIC_A=anon123456789", firstStore.anonymousCookie())
        assertEquals("MUSIC_A=anon123456789", firstStore.cookie())
        assertNull(firstStore.accountCookie())

        // Recreate store instance to verify durable Android storage persistence
        val secondStore = SessionStore(context)
        assertEquals("MUSIC_A=anon123456789", secondStore.anonymousCookie())
        assertEquals("MUSIC_A=anon123456789", secondStore.cookie())
        assertNull(secondStore.accountCookie())
    }

    @Test
    fun accountCookieTakesPrecedenceOverAnonymousCookie() = runBlocking {
        val store = SessionStore(context)
        store.saveAnonymousCookie("MUSIC_A=anon-token")

        val profile = AccountProfile(id = 100L, nickname = "测试用户", avatarUrl = null, vipLevel = 0)
        store.save("MUSIC_U=user-auth-token", profile)

        assertEquals("MUSIC_U=user-auth-token", store.accountCookie())
        assertEquals("MUSIC_A=anon-token", store.anonymousCookie())
        assertEquals("MUSIC_U=user-auth-token", store.cookie())
        assertEquals(profile, store.session.value)
    }

    @Test
    fun clearRemovesBothAccountAndAnonymousCookies() = runBlocking {
        val store = SessionStore(context)
        store.saveAnonymousCookie("MUSIC_A=anon-token")
        store.save("MUSIC_U=user-token", AccountProfile(id = 1L, nickname = "U", avatarUrl = null, vipLevel = 0))

        store.clear()

        assertNull(store.accountCookie())
        assertNull(store.anonymousCookie())
        assertNull(store.cookie())
        assertNull(store.session.value)
    }

    @Test
    fun clearAnonymousCookieRetainsAccountCookie() = runBlocking {
        val store = SessionStore(context)
        store.saveAnonymousCookie("MUSIC_A=anon-token")
        val profile = AccountProfile(id = 2L, nickname = "U2", avatarUrl = null, vipLevel = 0)
        store.save("MUSIC_U=user-token-2", profile)

        store.clearAnonymousCookie()

        assertNull(store.anonymousCookie())
        assertEquals("MUSIC_U=user-token-2", store.accountCookie())
        assertEquals("MUSIC_U=user-token-2", store.cookie())
        assertEquals(profile, store.session.value)
    }

    @Test
    fun blankCookiesAreRejected(): Unit = runBlocking {
        val store = SessionStore(context)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.saveAnonymousCookie("   ") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.save("", null) }
        }
    }
}

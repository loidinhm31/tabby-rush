package dev.tabbyrush.shared.ui

import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteInboxNavigationTest {

    private val sampleUuid = "baf6dc3e-3099-4ae0-8767-985552ef0b82"

    @Test
    fun testInitialStateIsTasks() {
        val state = RemoteInboxNavState()
        assertEquals(RemoteInboxRoute.Tasks, state.currentRoute)
        assertTrue(state.backStack.isEmpty())
        assertNull(state.unavailableMessage)
    }

    @Test
    fun testNavigationTransitionsAndBackStack() {
        val s0 = RemoteInboxNavState()
        val s1 = s0.navigateToList()
        assertEquals(RemoteInboxRoute.List, s1.currentRoute)
        assertEquals(listOf(RemoteInboxRoute.Tasks), s1.backStack)

        val s2 = s1.navigateToDetail(sampleUuid)
        assertIs<RemoteInboxRoute.Detail>(s2.currentRoute)
        assertEquals(sampleUuid, (s2.currentRoute as RemoteInboxRoute.Detail).messageId)
        assertEquals(listOf(RemoteInboxRoute.Tasks, RemoteInboxRoute.List), s2.backStack)

        val s3 = s2.popBack()
        assertEquals(RemoteInboxRoute.List, s3.currentRoute)
        assertEquals(listOf(RemoteInboxRoute.Tasks), s3.backStack)

        val s4 = s3.popBack()
        assertEquals(RemoteInboxRoute.Tasks, s4.currentRoute)
        assertTrue(s4.backStack.isEmpty())

        val s5 = s4.popBack()
        assertEquals(RemoteInboxRoute.Tasks, s5.currentRoute)
        assertTrue(s5.backStack.isEmpty())
    }

    @Test
    fun testDirectDetailNavigationFromTasks() {
        val s0 = RemoteInboxNavState()
        val s1 = s0.navigateToDetail(sampleUuid)
        assertEquals(RemoteInboxRoute.Detail(sampleUuid), s1.currentRoute)
        assertEquals(listOf(RemoteInboxRoute.Tasks, RemoteInboxRoute.List), s1.backStack)

        val s2 = s1.popBack()
        assertEquals(RemoteInboxRoute.List, s2.currentRoute)
        assertEquals(listOf(RemoteInboxRoute.Tasks), s2.backStack)

        val s3 = s2.popBack()
        assertEquals(RemoteInboxRoute.Tasks, s3.currentRoute)
        assertTrue(s3.backStack.isEmpty())
    }

    @Test
    fun testNavigateToDetailRejectsInvalidUuid() {
        val s0 = RemoteInboxNavState()
        val s1 = s0.navigateToDetail("invalid-uuid-format")
        assertEquals(RemoteInboxRoute.List, s1.currentRoute)
        assertNotNull(s1.unavailableMessage)
        assertNull(s1.pendingDetailId)
    }

    @Test
    fun testNavigateToTasksDirectlyClearsBackStack() {
        val s0 = RemoteInboxNavState().navigateToDetail(sampleUuid)
        val s1 = s0.navigateToTasks()
        assertEquals(RemoteInboxRoute.Tasks, s1.currentRoute)
        assertTrue(s1.backStack.isEmpty())
        assertNull(s1.unavailableMessage)
    }

    @Test
    fun testOnGenerationChangedDismissesDetailAndClearsNavigation() {
        val s0 = RemoteInboxNavState().navigateToDetail(sampleUuid).copy(unavailableMessage = "Old warning")
        val s1 = s0.onGenerationChanged(newGeneration = 42L)
        assertEquals(RemoteInboxRoute.Tasks, s1.currentRoute)
        assertTrue(s1.backStack.isEmpty())
        assertNull(s1.unavailableMessage)
        assertEquals(42L, s1.generation)
    }

    @Test
    fun testHandleNativeEntryValidAuthenticated() {
        val s0 = RemoteInboxNavState()
        val s1 = s0.handleNativeEntry("tabby-rush", sampleUuid, 7L, 7L, true)
        assertEquals(RemoteInboxRoute.Detail(sampleUuid), s1.currentRoute)
        assertEquals(listOf(RemoteInboxRoute.Tasks, RemoteInboxRoute.List), s1.backStack)
        assertNull(s1.unavailableMessage)
        assertEquals(7L, s1.generation)
    }

    @Test
    fun testHandleNativeEntryUnauthenticatedOpensInboxWithNotice() {
        val s0 = RemoteInboxNavState()
        val s1 = s0.handleNativeEntry("tabby-rush", sampleUuid, 7L, 7L, false)
        assertEquals(RemoteInboxRoute.List, s1.currentRoute)
        assertEquals(listOf(RemoteInboxRoute.Tasks), s1.backStack)
        assertNotNull(s1.unavailableMessage)
        assertTrue(s1.unavailableMessage!!.contains("Sign in"))
    }

    @Test
    fun testHandleNativeEntryMismatchedGeneration() {
        val s0 = RemoteInboxNavState()
        val s1 = s0.handleNativeEntry("tabby-rush", sampleUuid, 6L, 7L, true)
        assertEquals(RemoteInboxRoute.List, s1.currentRoute)
        assertNotNull(s1.unavailableMessage)
        assertTrue(s1.unavailableMessage!!.contains("not available"))
    }

    @Test
    fun testHandleNativeEntryWrongAppId() {
        val s0 = RemoteInboxNavState()
        val s1 = s0.handleNativeEntry("other-app", sampleUuid, 7L, 7L, true)
        assertEquals(RemoteInboxRoute.List, s1.currentRoute)
        assertNotNull(s1.unavailableMessage)
    }

    @Test
    fun testHandleNativeEntryMalformedUuid() {
        val s0 = RemoteInboxNavState()
        val s1 = s0.handleNativeEntry("tabby-rush", "not-a-uuid", 7L, 7L, true)
        assertEquals(RemoteInboxRoute.List, s1.currentRoute)
        assertNotNull(s1.unavailableMessage)
    }

    @Test
    fun testRouteParsingValidAndInvalid() {
        assertEquals(RemoteInboxRoute.Tasks, parseRemoteInboxRoute("tasks"))
        assertEquals(RemoteInboxRoute.Tasks, parseRemoteInboxRoute("tabbyrush://tasks"))
        assertEquals(RemoteInboxRoute.Tasks, parseRemoteInboxRoute("/tasks"))

        assertEquals(RemoteInboxRoute.List, parseRemoteInboxRoute("inbox"))
        assertEquals(RemoteInboxRoute.List, parseRemoteInboxRoute("tabbyrush://inbox"))
        assertEquals(RemoteInboxRoute.List, parseRemoteInboxRoute("/inbox"))

        assertEquals(RemoteInboxRoute.Detail(sampleUuid), parseRemoteInboxRoute("inbox/$sampleUuid"))
        assertEquals(RemoteInboxRoute.Detail(sampleUuid), parseRemoteInboxRoute("tabbyrush://inbox/$sampleUuid"))
        assertEquals(RemoteInboxRoute.Detail(sampleUuid), parseRemoteInboxRoute("/inbox/$sampleUuid"))

        assertNull(parseRemoteInboxRoute(""))
        assertNull(parseRemoteInboxRoute("unknown"))
        assertNull(parseRemoteInboxRoute("tabbyrush://other"))
        assertNull(parseRemoteInboxRoute("inbox/invalid-uuid"))
        assertNull(parseRemoteInboxRoute("inbox/$sampleUuid/extra"))
    }

    @Test
    fun testIsCanonicalUuid() {
        assertTrue(isCanonicalUuid(sampleUuid))
        assertFalse(isCanonicalUuid("BAF6DC3E-3099-4AE0-8767-985552EF0B82")) // uppercase rejected
        assertFalse(isCanonicalUuid("baf6dc3e30994ae08767985552ef0b82")) // dashes missing
        assertFalse(isCanonicalUuid("${sampleUuid}a")) // length 37
        assertFalse(isCanonicalUuid("baf6dc3e-3099-4ae0-8767-985552ef0b8g")) // non-hex character
    }

    @Test
    fun testTransitionFromOnlineToOfflineRevokedSignedOutHidesDetailImmediately() {
        val testItem = RemoteInboxItem(
            id = sampleUuid, appId = "tabby-rush", title = "Maintenance notice",
            body = "Top secret server maintenance body", createdAt = "2026-10-18T00:00:00.000Z", expiresAt = "2027-01-16T00:00:00.000Z"
        )
        // Online: authorized and visible
        assertTrue(isDetailContentVisible(RemoteInboxAvailability.Online, testItem))
        // Offline: private body and detail must be hidden immediately
        assertFalse(isDetailContentVisible(RemoteInboxAvailability.Offline, testItem))
        // Revoked: private body and detail must be hidden immediately
        assertFalse(isDetailContentVisible(RemoteInboxAvailability.Revoked, testItem))
        // SignedOut: private body and detail must be hidden immediately
        assertFalse(isDetailContentVisible(RemoteInboxAvailability.SignedOut, testItem))
        // Null detail: never visible
        assertFalse(isDetailContentVisible(RemoteInboxAvailability.Online, null))
    }
}

# Native Delivery & Activation Research Report: Android, Linux D-Bus, and Windows Win32

## 1. Primary Citations
- **Android FCM**: Firebase Cloud Messaging Receive Messages: https://firebase.google.com/docs/cloud-messaging/android/receive-messages
- **Linux Desktop Notifications**: FreeDesktop.org Desktop Notifications Specification v1.3: https://specifications.freedesktop.org/notification-spec/latest/
- **Windows Toast Activation**: Microsoft Win32 `INotificationActivationCallback`: https://learn.microsoft.com/en-us/windows/win32/api/notificationactivationcallback/nn-notificationactivationcallback-inotificationactivationcallback
- **Windows Desktop Toast Quickstart**: Microsoft Learn Desktop Notifications: https://learn.microsoft.com/en-us/windows/apps/develop/notifications/app-notifications/

---

## 2. Platform Delivery, Activation, and Lifetime Matrix

| Platform | Channel / Transport | Background / App Closed | Foreground / Running | Tap / Click Activation | Lifetime & Reconnection |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Android** | FCM HTTP v1 (Generic title/body + opaque data payload) | OS system tray displays notification automatically; wakes process only on tap | `onMessageReceived` invoked directly; app displays local notification or silent UI update | **Cold**: Intent extras delivered to launch Activity `onCreate`. **Warm**: `onNewIntent` | FCM token bound to `(user, device, appVersion)`. Sync on `onDeletedMessages` or token refresh |
| **Linux Desktop** | Authenticated SSE stream -> `org.freedesktop.Notifications` via `dbus-java` | **No live delivery** (SSE disconnected, process closed; per agreed spec) | SSE delivers event -> D-Bus `Notify` method called -> desktop toast popup | D-Bus signal `ActionInvoked(id, action_key)` received by app session connection | SSE client reconnects with exponential backoff (1s..30s); drops D-Bus listener on shutdown |
| **Windows Desktop** | Authenticated SSE stream -> Win32/WinRT Toast via JNA | **No live delivery** (SSE disconnected, process closed; per agreed spec) | SSE delivers event -> WinRT Toast displayed with registered AUMID | In-proc COM / WinRT `Activated` event handler triggers window restore and navigation | Shortcut AUMID set via MSI/WiX; `SetCurrentProcessExplicitAppUserModelID` on startup |

---

## 3. Platform Mechanics & Implementation Blueprint

### A. Android Native FCM (Client Mechanics)
- **Source Facts**:
  - API 33+ (Android 13+) mandates `android.permission.POST_NOTIFICATIONS` runtime permission.
  - When payload contains both `notification` (generic title/body) and `data` (opaque `announcementId`), background receipt is managed entirely by Android system tray; app code runs **only** when user taps.
  - Tapping launches the launcher Activity. Cold start passes extras via `activity.intent.extras`; warm start (with `launchMode="singleTop"` or `singleTask`) delivers via `onNewIntent(Intent intent)`.
- **Design Proposal**:
  - `FirebaseMessagingService`:
    - `onNewToken(token)`: Register token to Glean Hub `/api/devices/register` with `(deviceId, userId, platform="android", appVersion)`.
    - `onMessageReceived(remoteMessage)`: If foreground, update in-memory unread badge; optionally post low-priority `NotificationChannel` notification if user is outside announcement screen.
  - Launcher / DeepLink Activity:
    - Extract `announcementId = intent.getStringExtra("announcement_id")`.
    - Guard against stale or wrong-account intents: verify current authenticated user matches active session before navigating; fetch full announcement content via GET `/api/announcements/{id}`.

### B. Linux Desktop D-Bus (`dbus-java`)
- **Source Facts**:
  - Standard service `org.freedesktop.Notifications` on session bus. Method: `Notify(app_name, replaces_id, app_icon, summary, body, actions, hints, expire_timeout) -> uint32 id`.
  - Notification servers (GNOME Shell, Dunst, Mako, KDE) emit signal `ActionInvoked(uint32 id, string action_key)`.
  - `GetCapabilities()` indicates `"actions"` and `"persistence"`. If `"actions"` missing, server ignores interactive buttons; clicking body falls back to `"default"`.
- **Design Proposal**:
  - Tabby Rush Linux JVM app registers `DBusSigHandler<ActionInvoked>` on the session bus at startup.
  - When SSE receives an announcement: call `Notify` with `actions = ["default", "View"]`.
  - On `ActionInvoked`: if signal `id` matches active announcement notification, invoke Swing/Compose window unminimize, bring to front (`_NET_ACTIVE_WINDOW`), and route to announcement route.
  - On shutdown / window close to exit: unregister signal handler and close D-Bus session.

### C. Windows Win32 Toast & JNA Bridge
- **Source Facts**:
  - Current state: `JnaWindowsToastBridge` returns `Unavailable` because direct WinRT invocation via JNA requires COM initialization, correct WinRT vtables, and Start Menu AUMID registration.
  - Unpackaged Win32 apps require a Start Menu shortcut (.lnk) with property `System.AppUserModel.ID` matching process AUMID (`SetCurrentProcessExplicitAppUserModelID`).
  - Cold activation without a running process requires registering a COM server in `HKCU\Software\Classes\CLSID\{GUID}\LocalServer32` implementing `INotificationActivationCallback`.
- **Design Proposal (Running Process Only)**:
  - Per architecture, desktop delivery is **only active while process is running (window or tray)**. Cold COM activation is out of scope.
  - MSI Installer (WiX / jpackage): Creates Start Menu shortcut with `System.AppUserModel.ID = "TabbyRush.App"`.
  - App Startup: Call `Shell32.SetCurrentProcessExplicitAppUserModelID("TabbyRush.App")` via JNA.
  - Toast Display & Activation:
    - Replace fragile handwritten JNA WinRT vtables with either:
      1. Minimal C++/WinRT companion DLL (`tabby_toast.dll`, <50KB) exposing `DisplayToast(aumid, xml, onClickCallback)` linking directly to Windows.UI.Notifications.
      2. Or JNA bridging to `IToastNotification` with an in-process `IToastNotificationActivatedEventArgs` COM event sink.
    - Fallback: If bridge fails, gracefully degrade to `java.awt.SystemTray.displayMessage(title, body, MessageType.INFO)` without crashing.

---

## 4. SSE Subscription, Reconcile Race & Security Lifecycle

### A. Reconcile vs. Live Alerts
- **The Race**: SSE stream establishes while pending announcements exist or network hiccups occur.
- **Protocol**:
  1. **Connect SSE**: GET `/api/announcements/stream`. Keep stream active.
  2. **Fetch Catch-up**: Concurrently or immediately after connection handshake, GET `/api/announcements/inbox?since={lastSeenId}&limit=50`.
  3. **Alert Gating**: Inbox catch-up items update the unread count/inbox list silently (`silentCatchUp = true`).
  4. **Live Events**: Only events received on the SSE connection with `createdAt >= connectionStartTime` trigger OS popups (D-Bus / WinRT Toast).
  5. **Deduplication**: Monotonic `announcementId` deduplicated in local memory set before UI dispatch.

### B. Access Revocation & Account Switching
- **Account Logout**:
  - Desktop: Abort SSE stream immediately; invoke `NotificationManager.clearAll()` (Linux `CloseNotification`, WinRT `ToastNotificationManager.History.Clear`). Clear in-memory unread state.
  - Android: Call `FirebaseMessaging.getInstance().deleteToken()` and inform backend `/api/devices/unregister`.
- **User Switch**:
  - Late generic FCM taps or cached intents are verified against currently authenticated `userId`. If mismatch or unauthenticated, prompt login and discard deep-link auto-navigation.

---

## 5. Risk-First Spikes & Acceptance Gates

1. **Linux D-Bus Spike Gate**:
   - Verify `dbus-java` connects to `org.freedesktop.Notifications` under Wayland/X11 on GNOME and KDE.
   - Acceptance: Showing toast and clicking `"default"` action successfully triggers callback in running Kotlin process.
2. **Windows Toast JNA Spike Gate**:
   - Repair `JnaWindowsToastBridge` using AUMID-bound WinRT or C++/WinRT companion DLL.
   - Acceptance: Toast displays in Windows 10/11 Action Center with app icon, clicking it fires callback to bring minimized tray app to focus.
3. **Android FCM Foreground/Background Spike Gate**:
   - Test FCM HTTP v1 notification + data payload on Android 13+ emulator.
   - Acceptance: Background tap delivers `announcement_id` via Intent extras in cold launch; foreground delivers via `onMessageReceived`.

---

## 6. Unresolved Native Prerequisites
1. **Windows Native Bridge Strategy**: Decision needed between embedding a tiny C++/WinRT JNI/JNA DLL (~50KB) vs writing complex pure JNA WinRT COM event handlers.
2. **Linux Desktop Environments**: Some minimal window managers (e.g., i3/sway without `mako`/`dunst`) have no active notification daemon; app must check `dbus.getNameOwner("org.freedesktop.Notifications")` before dispatching.

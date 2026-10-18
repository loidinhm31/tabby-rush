# TabbyRush Remote Notification Client Setup & Operator Guide

This guide covers the operator prerequisites, build configuration, credential isolation, and platform requirements for the TabbyRush client remote notification and inbox subsystem across Android, Linux, and Windows.

---

## 1. Subsystem Overview & Security Model

The TabbyRush remote notification architecture separates notification delivery from message content delivery to ensure end-to-end privacy:

```
[Server Admin Send]
       │
       ▼
[Canonical Hub Inbox] ──────► [Push Alert / SSE Hint]
                                    │
                                    ▼
                         [Generic System Shade Alert]
                         "You have a new announcement."
                                    │
                         (User taps notification)
                                    │
                                    ▼
                         [Authenticated Remote Inbox]
                         (Full title & body loaded securely)
```

### Key Security Invariants
- **No Private Content in Push Payloads:** Mobile push (FCM) and desktop alerts contain only fixed generic copy (`"TabbyRush"`, `"You have a new announcement."`) and routing metadata (`type`, `appId`, `messageId`). Title and body are never transmitted through third-party push networks.
- **Client Credential Separation:** Android and desktop clients hold **zero** server credentials. Android holds only public Google project options; desktop connects via user bearer tokens to the authenticated server SSE stream.
- **Cryptographic Install Fencing:** Android clients generate a random 128-bit UUID and a 32-byte cryptographic proof stored in Android Keystore (no-backup storage). Server stores only the SHA-256 hash of the proof and strictly enforces monotonic numeric generation fencing to prevent token theft or replay.

---

## 2. Android Client Configuration

### 2.1 External Public Client Configuration

Android builds consume public Firebase client settings via an external configuration path:

```bash
export TABBYRUSH_FIREBASE_CONFIG_PATH="/path/to/tabbyrush-firebase-client.json"
```

The file must contain public client configuration matching package `dev.tabbyrush.android`:

```json
{
  "project_id": "tabby-rush-prod",
  "application_id": "dev.tabbyrush.android",
  "api_key": "AIzaSyC...",
  "gcm_sender_id": "123456789012"
}
```

> **CRITICAL SECURITY REQUIREMENT:**  
> The `TABBYRUSH_FIREBASE_CONFIG_PATH` file contains **public client configuration only**.  
> The Google Cloud Service Account JSON (containing `private_key` and used by `glean-hub-server`) must **NEVER** be placed into the Android project, referenced by Android build scripts, or committed to version control.

### 2.2 Permissions, Channels, and Icons

- **Android 13+ (API 33+) Permission:** Requires the `POST_NOTIFICATIONS` runtime permission. TabbyRush requests this in context; if denied, the app gracefully falls back to displaying announcements exclusively within the in-app inbox.
- **Notification Channel:**
  - Channel ID: `tabbyrush.remote.announcements`
  - Name: "Admin Announcements"
  - Importance: Default (`IMPORTANCE_DEFAULT`)
- **Notification Tagging:**
  - System Tag: `remote:<messageId>`
  - Notification ID: `0`
  - Group: `tabbyrush.remote.announcements`
  - Icon: `@drawable/remote_notification_icon` (dedicated monochrome system icon)

### 2.3 Merged Manifest Security Check

Before release, the merged Android manifest is audited by `scripts/release-security-check.mjs`:
- Exported receiver allowlist: Only `com.google.firebase.iid.FirebaseInstanceIdReceiver` is permitted, protected strictly by `com.google.android.c2dm.permission.SEND` and action `com.google.android.c2dm.intent.RECEIVE`.
- `FirebaseInitProvider` must remain `exported="false"` with exact authority `dev.tabbyrush.android.firebaseinitprovider`.
- No Google Analytics or Measurement receivers are permitted.

---

## 3. Linux Desktop Client Setup

### 3.1 D-Bus Session Requirements

The Linux desktop runtime integrates natively with the FreeDesktop notification specification over the user D-Bus session bus:
- **D-Bus Bus:** Session Bus (`DBUS_SESSION_BUS_ADDRESS`).
- **Destination:** `org.freedesktop.Notifications`
- **Path:** `/org/freedesktop/Notifications`
- **Signals Observed:**
  - `ActionInvoked`: Routes notification click to inbox view model.
  - `NotificationClosed`: Cleans up tracked notification IDs.
  - `NameOwnerChanged`: Detects notification daemon restarts and re-establishes subscriptions without replaying old toasts.

### 3.2 Action Support & Fallback

- The adapter queries notification daemon capabilities (`GetCapabilities`).
- **When `actions` is supported:** Sends `["default", "Open inbox"]` actions. Clicking the toast triggers window restoration and opens the remote announcement.
- **When `actions` is unsupported:** Displays the generic toast notification without action buttons. Users can open the announcement directly from the in-app navigation bar.
- Supported desktop environments include GNOME Shell, KDE Plasma, Dunst, Mako, and SwayNotificationCenter.

### 3.3 Wayland Considerations

Under Wayland, compositors restrict background windows from stealing focus. When an announcement notification is clicked, TabbyRush issues an activation request (`xdg_activation_v1` or window unminimize). If the compositor denies focus, the application indicates pending unread announcements in the system tray / window title.

---

## 4. Windows Desktop Client Setup

### 4.1 Native C++/WinRT DLL (`tabbyrush-toast.dll`)

Windows desktop notifications use a dedicated native DLL adhering to C ABI v1:

```c
uint32_t tr_toast_abi_version(void); // Returns 1
int32_t  tr_toast_create(const char* aumid, void(*activated)(void*,const char*,uint64_t), void* user, uint64_t* out_handle);
int32_t  tr_toast_setting(uint64_t handle, int32_t* out_setting);
int32_t  tr_toast_show(uint64_t handle, const char* message_id, uint64_t generation, const char* tag, const char* group);
int32_t  tr_toast_show_local(uint64_t handle, const char* title, const char* body, const char* tag, const char* group);
int32_t  tr_toast_remove(uint64_t handle, const char* tag, const char* group);
int32_t  tr_toast_clear_group(uint64_t handle, const char* group);
int32_t  tr_toast_destroy(uint64_t handle);
```

- **Thread Model:** WinRT initialization runs on a dedicated MTA thread.
- **Timeouts:** Synchronous native commands have a 2-second timeout to prevent deadlocks.
- **Tagging:** Remote toast group is fixed to `tr-remote`. Tag is the first 16 lowercase hex characters of SHA-256(`messageId`).

### 4.2 Build Toolchain Prerequisites

To build and package the Windows desktop application:
1. **MSVC Toolchain:** Visual Studio 2022 or Visual Studio Build Tools with C++ x64 native compiler and Windows 10/11 SDK (version 10.0.19041.0 or newer).
2. **CMake:** Version 3.25 or newer.
3. **WiX Toolset:** WiX Toolset v3.11+ for MSI package compilation.
4. **Gradle Task:** Run `./gradlew :desktopApp:packageRemoteMsi` to generate the release installer.

### 4.3 AUMID & Shortcut Identity

Windows Action Center requires a registered Application User Model ID (AUMID):
- **AUMID:** Exactly `dev.tabbyrush.desktop`
- **Shortcut Requirement:** The per-user WiX MSI installer creates a Start Menu shortcut with the `System.AppUserModel.ID` property set to `dev.tabbyrush.desktop`.
- **Runtime Identity:** On startup, the process explicitly registers its AUMID via `SetCurrentProcessExplicitAppUserModelID`.

### 4.4 Process Quit vs Toast Activation

- **Normal Shutdown:** When TabbyRush quits normally, it calls `tr_toast_clear_group(handle, "tr-remote")` to remove pending announcement toasts before calling `tr_toast_destroy`.
- **Crash Recovery:** If an unexpected crash leaves a toast in Windows Action Center, cold COM activation is intentionally disabled. Clicking the orphaned toast will not execute arbitrary processes. Upon the next application launch, TabbyRush clears the `tr-remote` group and silently catches up with the canonical inbox.

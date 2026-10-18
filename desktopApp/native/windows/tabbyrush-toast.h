#pragma once

#include <stdint.h>

#ifdef _WIN32
  #ifdef TABBYRUSH_TOAST_EXPORTS
    #define TR_TOAST_API extern "C" __declspec(dllexport)
  #else
    #define TR_TOAST_API extern "C" __declspec(dllimport)
  #endif
  #define TR_CALL __cdecl
#else
  #define TR_TOAST_API extern "C"
  #define TR_CALL
#endif

#define TR_TOAST_ABI_VERSION 1

// HRESULT error constants
#define TR_S_OK                   ((int32_t)0x00000000)
#define TR_E_FAIL                 ((int32_t)0x80004005)
#define TR_E_POINTER              ((int32_t)0x80004003)
#define TR_E_INVALIDARG           ((int32_t)0x80070057)
#define TR_E_HANDLE               ((int32_t)0x80070006)
#define TR_E_TIMEOUT              ((int32_t)0x800705B4)
#define TR_RO_E_CLOSED            ((int32_t)0x80000013)

// NotificationSetting enum values matching Windows.UI.Notifications.NotificationSetting
#define TR_SETTING_ENABLED                  0
#define TR_SETTING_DISABLED_FOR_APP         1
#define TR_SETTING_DISABLED_FOR_USER        2
#define TR_SETTING_DISABLED_BY_GROUP_POLICY 3
#define TR_SETTING_DISABLED_BY_MANIFEST     4
#define TR_SETTING_UNAVAILABLE              5

#ifdef __cplusplus
extern "C" {
#endif

typedef void (TR_CALL *tr_toast_activated_cb)(void* user, const char* message_id, uint64_t generation);

/**
 * Returns the ABI version of the TabbyRush native toast library.
 * Exactly 1 for ABI v1.
 */
TR_TOAST_API uint32_t TR_CALL tr_toast_abi_version(void);

/**
 * Creates an MTA worker thread and toast notifier bound to the given AUMID.
 * Registers optional activation callback.
 * Returns S_OK (0) on success, or an HRESULT error code.
 */
TR_TOAST_API int32_t TR_CALL tr_toast_create(
    const char* aumid,
    tr_toast_activated_cb activated,
    void* user,
    uint64_t* out_handle
);

/**
 * Queries current notification setting for the notifier handle.
 * Returns S_OK (0) with out_setting populated, or an HRESULT error code.
 */
TR_TOAST_API int32_t TR_CALL tr_toast_setting(
    uint64_t handle,
    int32_t* out_setting
);

/**
 * Displays a remote announcement toast notification with generic title and body.
 * Attaches activation handler that routes click to registered callback.
 * Returns S_OK (0) on success, or an HRESULT error code.
 */
TR_TOAST_API int32_t TR_CALL tr_toast_show(
    uint64_t handle,
    const char* message_id,
    uint64_t generation,
    const char* tag,
    const char* group
);

/**
 * Displays a local reminder toast notification with specified title and body.
 * Uses native XML escaping. Rejects group "tr-remote" with E_INVALIDARG.
 * Returns S_OK (0) on success, or an HRESULT error code.
 */
TR_TOAST_API int32_t TR_CALL tr_toast_show_local(
    uint64_t handle,
    const char* title,
    const char* body,
    const char* tag,
    const char* group
);

/**
 * Removes a toast notification by tag and group from Action Center history.
 * Returns S_OK (0) on success, or an HRESULT error code.
 */
TR_TOAST_API int32_t TR_CALL tr_toast_remove(
    uint64_t handle,
    const char* tag,
    const char* group
);

/**
 * Removes all notifications belonging to group from Action Center history.
 * Returns S_OK (0) on success, or an HRESULT error code.
 */
TR_TOAST_API int32_t TR_CALL tr_toast_clear_group(
    uint64_t handle,
    const char* group
);

/**
 * Revokes event handlers, drains in-flight callbacks, clears "tr-remote" group,
 * stops worker thread, uninitializes WinRT, and frees handle.
 * If drain times out (2s), returns E_TIMEOUT without freeing handle so caller retains ownership.
 * Returns S_OK (0) on successful destruction.
 */
TR_TOAST_API int32_t TR_CALL tr_toast_destroy(
    uint64_t handle
);

#ifdef __cplusplus
}
#endif

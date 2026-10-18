#include "tabbyrush-toast.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <functional>
#include <future>
#include <memory>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include <cstring>

#ifdef _WIN32
  #include <windows.h>
  #include <winrt/base.h>
  #include <winrt/Windows.Foundation.h>
  #include <winrt/Windows.Data.Xml.Dom.h>
  #include <winrt/Windows.UI.Notifications.h>
#endif

namespace {

std::string escape_xml(const std::string& input) {
    std::string out;
    out.reserve(input.size() * 11 / 10);
    for (char c : input) {
        switch (c) {
            case '&':  out.append("&amp;"); break;
            case '<':  out.append("&lt;"); break;
            case '>':  out.append("&gt;"); break;
            case '\"': out.append("&quot;"); break;
            case '\'': out.append("&apos;"); break;
            default:   out.push_back(c); break;
        }
    }
    return out;
}

#ifdef _WIN32
std::wstring to_wstring(const std::string& utf8) {
    if (utf8.empty()) return std::wstring();
    int size = MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), (int)utf8.size(), nullptr, 0);
    if (size <= 0) return std::wstring();
    std::wstring result(size, 0);
    MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), (int)utf8.size(), &result[0], size);
    return result;
}

winrt::hstring to_hstring(const std::string& utf8) {
    return winrt::to_hstring(to_wstring(utf8));
}
#endif

struct ToastContext {
    uint64_t id{0};
    std::string aumid;
    tr_toast_activated_cb callback{nullptr};
    void* user_data{nullptr};

    std::atomic<bool> is_destroying{false};
    std::atomic<bool> is_destroyed{false};
    std::atomic<bool> remote_group_cleared{false};
    std::mutex destroy_mutex;
    std::atomic<int32_t> in_flight_callbacks{0};
    std::mutex callback_mutex;
    std::condition_variable cv_callbacks;
    // Dedicated MTA worker thread queue
    std::thread worker_thread;
    std::mutex queue_mutex;
    std::condition_variable queue_cv;
    std::queue<std::function<void()>> command_queue;
    std::atomic<bool> stop_worker{false};

#ifdef _WIN32
    winrt::Windows::UI::Notifications::ToastNotifier notifier{nullptr};
#endif

    ToastContext(uint64_t handle_id, std::string app_id, tr_toast_activated_cb cb, void* user)
        : id(handle_id), aumid(std::move(app_id)), callback(cb), user_data(user) {}

    ~ToastContext() {
        stop_and_join();
    }

    void start_worker() {
        std::promise<int32_t> init_promise;
        auto init_future = init_promise.get_future();

        worker_thread = std::thread([this, p = std::move(init_promise)]() mutable {
#ifdef _WIN32
            try {
                winrt::init_apartment(winrt::apartment_type::multi_threaded);
                notifier = winrt::Windows::UI::Notifications::ToastNotificationManager::CreateToastNotifier(to_hstring(aumid));
                p.set_value(TR_S_OK);
            } catch (const winrt::hresult_error& ex) {
                p.set_value((int32_t)ex.code().value);
                return;
            } catch (...) {
                p.set_value(TR_E_FAIL);
                return;
            }
#else
            p.set_value(TR_S_OK);
#endif

            while (true) {
                std::function<void()> task;
                {
                    std::unique_lock<std::mutex> lock(queue_mutex);
                    queue_cv.wait(lock, [this]() {
                        return stop_worker.load() || !command_queue.empty();
                    });

                    if (stop_worker.load() && command_queue.empty()) {
                        break;
                    }

                    if (!command_queue.empty()) {
                        task = std::move(command_queue.front());
                        command_queue.pop();
                    }
                }

                if (task) {
                    try {
                        task();
                    } catch (...) {
                        // Suppress task exceptions on worker thread
                    }
                }
            }

#ifdef _WIN32
            try {
                winrt::uninit_apartment();
            } catch (...) {}
#endif
        });

        if (init_future.wait_for(std::chrono::seconds(2)) != std::future_status::ready) {
            stop_and_join();
            throw std::runtime_error("Worker thread initialization timed out");
        }

        int32_t res = init_future.get();
        if (res != TR_S_OK) {
            stop_and_join();
            throw std::runtime_error("Worker thread initialization failed with HRESULT " + std::to_string(res));
        }
    }

    void stop_and_join() {
        stop_worker.store(true);
        queue_cv.notify_all();
        if (worker_thread.joinable()) {
            worker_thread.join();
        }
    }

    template <typename Func>
    int32_t post_and_wait(Func&& func) {
        return post_and_wait_internal(std::forward<Func>(func), false);
    }

    template <typename Func>
    int32_t post_teardown_and_wait(Func&& func) {
        return post_and_wait_internal(std::forward<Func>(func), true);
    }

    template <typename Func>
    int32_t post_and_wait_internal(Func&& func, bool allow_during_destroy) {
        if (is_destroying.load() && !allow_during_destroy) {
            return TR_RO_E_CLOSED;
        }

        auto promise = std::make_shared<std::promise<int32_t>>();
        auto future = promise->get_future();

        {
            std::lock_guard<std::mutex> lock(queue_mutex);
            if (stop_worker.load()) {
                return TR_RO_E_CLOSED;
            }
            if (is_destroying.load() && !allow_during_destroy) {
                return TR_RO_E_CLOSED;
            }
            command_queue.push([p = promise, f = std::forward<Func>(func)]() mutable {
                try {
                    int32_t result = f();
                    p->set_value(result);
                } catch (...) {
                    p->set_value(TR_E_FAIL);
                }
            });
        }
        queue_cv.notify_one();

        if (future.wait_for(std::chrono::seconds(2)) != std::future_status::ready) {
            return TR_E_TIMEOUT;
        }

        return future.get();
    }
};

std::mutex g_handles_mutex;
std::unordered_map<uint64_t, std::shared_ptr<ToastContext>> g_handles;
std::atomic<uint64_t> g_next_handle{1};

std::shared_ptr<ToastContext> get_context(uint64_t handle) {
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    auto it = g_handles.find(handle);
    if (it != g_handles.end()) {
        return it->second;
    }
    return nullptr;
}

} // namespace

TR_TOAST_API uint32_t TR_CALL tr_toast_abi_version(void) {
    return TR_TOAST_ABI_VERSION;
}

TR_TOAST_API int32_t TR_CALL tr_toast_create(
    const char* aumid,
    tr_toast_activated_cb activated,
    void* user,
    uint64_t* out_handle
) {
    if (!out_handle) {
        return TR_E_POINTER;
    }
    if (!aumid || std::strlen(aumid) == 0) {
        return TR_E_INVALIDARG;
    }

    try {
        uint64_t handle = g_next_handle.fetch_add(1);
        auto ctx = std::make_shared<ToastContext>(handle, std::string(aumid), activated, user);
        ctx->start_worker();

        {
            std::lock_guard<std::mutex> lock(g_handles_mutex);
            g_handles[handle] = ctx;
        }

        *out_handle = handle;
        return TR_S_OK;
    } catch (const std::exception&) {
        return TR_E_FAIL;
    } catch (...) {
        return TR_E_FAIL;
    }
}

TR_TOAST_API int32_t TR_CALL tr_toast_setting(
    uint64_t handle,
    int32_t* out_setting
) {
    if (!out_setting) {
        return TR_E_POINTER;
    }

    auto ctx = get_context(handle);
    if (!ctx) {
        return TR_E_HANDLE;
    }

    try {
        auto shared_setting = std::make_shared<std::atomic<int32_t>>(TR_SETTING_UNAVAILABLE);

        int32_t hr = ctx->post_and_wait([ctx, shared_setting]() -> int32_t {
#ifdef _WIN32
            try {
                auto setting = ctx->notifier.Setting();
                switch (setting) {
                    case winrt::Windows::UI::Notifications::NotificationSetting::Enabled:
                        shared_setting->store(TR_SETTING_ENABLED);
                        break;
                    case winrt::Windows::UI::Notifications::NotificationSetting::DisabledForApplication:
                        shared_setting->store(TR_SETTING_DISABLED_FOR_APP);
                        break;
                    case winrt::Windows::UI::Notifications::NotificationSetting::DisabledForUser:
                        shared_setting->store(TR_SETTING_DISABLED_FOR_USER);
                        break;
                    case winrt::Windows::UI::Notifications::NotificationSetting::DisabledByGroupPolicy:
                        shared_setting->store(TR_SETTING_DISABLED_BY_GROUP_POLICY);
                        break;
                    case winrt::Windows::UI::Notifications::NotificationSetting::DisabledByManifest:
                        shared_setting->store(TR_SETTING_DISABLED_BY_MANIFEST);
                        break;
                    default:
                        shared_setting->store(TR_SETTING_UNAVAILABLE);
                        break;
                }
                return TR_S_OK;
            } catch (const winrt::hresult_error& ex) {
                return (int32_t)ex.code().value;
            } catch (...) {
                return TR_E_FAIL;
            }
#else
            shared_setting->store(TR_SETTING_ENABLED);
            return TR_S_OK;
#endif
        });

        if (hr == TR_S_OK) {
            *out_setting = shared_setting->load();
        }
        return hr;
    } catch (...) {
        return TR_E_FAIL;
    }
}

TR_TOAST_API int32_t TR_CALL tr_toast_show(
    uint64_t handle,
    const char* message_id,
    uint64_t generation,
    const char* tag,
    const char* group
) {
    if (!message_id || !tag || !group) {
        return TR_E_INVALIDARG;
    }

    auto ctx = get_context(handle);
    if (!ctx) {
        return TR_E_HANDLE;
    }

    // Capture arguments by value
    std::string msg_id_str(message_id);
    std::string tag_str(tag);
    std::string group_str(group);
    uint64_t gen = generation;

    return ctx->post_and_wait([ctx, msg_id_str, gen, tag_str, group_str]() -> int32_t {
#ifdef _WIN32
        try {
            std::string generic_xml =
                "<toast>"
                "<visual>"
                "<binding template=\"ToastGeneric\">"
                "<text>TabbyRush Announcement</text>"
                "<text>You have a new announcement in TabbyRush.</text>"
                "</binding>"
                "</visual>"
                "<audio src=\"ms-winsoundevent:Notification.Default\"/>"
                "</toast>";

            winrt::Windows::Data::Xml::Dom::XmlDocument xml_doc;
            xml_doc.LoadXml(to_hstring(generic_xml));

            winrt::Windows::UI::Notifications::ToastNotification toast(xml_doc);
            toast.Tag(to_hstring(tag_str));
            toast.Group(to_hstring(group_str));

            // Attach Activated handler with callback fencing
            toast.Activated([ctx, msg_id = msg_id_str, gen](auto const&, auto const&) {
                std::unique_lock<std::mutex> lock(ctx->callback_mutex);
                if (ctx->is_destroying.load() || ctx->callback == nullptr) {
                    return;
                }
                ctx->in_flight_callbacks.fetch_add(1);
                lock.unlock();

                try {
                    // Borrowed string passed to callback, JNA copies before returning
                    ctx->callback(ctx->user_data, msg_id.c_str(), gen);
                } catch (...) {
                    // Suppress all exceptions at C ABI boundary
                }

                ctx->in_flight_callbacks.fetch_sub(1);
                ctx->cv_callbacks.notify_all();
            });

            ctx->notifier.Show(toast);
            return TR_S_OK;
        } catch (const winrt::hresult_error& ex) {
            return (int32_t)ex.code().value;
        } catch (...) {
            return TR_E_FAIL;
        }
#else
        return TR_S_OK;
#endif
    });
}

TR_TOAST_API int32_t TR_CALL tr_toast_show_local(
    uint64_t handle,
    const char* title,
    const char* body,
    const char* tag,
    const char* group
) {
    if (!title || !body || !tag || !group) {
        return TR_E_INVALIDARG;
    }

    // Rejects reserved group "tr-remote" for local notifications
    if (std::strcmp(group, "tr-remote") == 0) {
        return TR_E_INVALIDARG;
    }

    auto ctx = get_context(handle);
    if (!ctx) {
        return TR_E_HANDLE;
    }

    std::string safe_title = escape_xml(title);
    std::string safe_body = escape_xml(body);
    std::string tag_str(tag);
    std::string group_str(group);

    return ctx->post_and_wait([ctx, safe_title, safe_body, tag_str, group_str]() -> int32_t {
#ifdef _WIN32
        try {
            std::string xml =
                "<toast>"
                "<visual>"
                "<binding template=\"ToastGeneric\">"
                "<text>" + safe_title + "</text>"
                "<text>" + safe_body + "</text>"
                "</binding>"
                "</visual>"
                "<audio src=\"ms-winsoundevent:Notification.Default\"/>"
                "</toast>";

            winrt::Windows::Data::Xml::Dom::XmlDocument xml_doc;
            xml_doc.LoadXml(to_hstring(xml));

            winrt::Windows::UI::Notifications::ToastNotification toast(xml_doc);
            toast.Tag(to_hstring(tag_str));
            toast.Group(to_hstring(group_str));

            ctx->notifier.Show(toast);
            return TR_S_OK;
        } catch (const winrt::hresult_error& ex) {
            return (int32_t)ex.code().value;
        } catch (...) {
            return TR_E_FAIL;
        }
#else
        return TR_S_OK;
#endif
    });
}

TR_TOAST_API int32_t TR_CALL tr_toast_remove(
    uint64_t handle,
    const char* tag,
    const char* group
) {
    if (!tag || !group) {
        return TR_E_INVALIDARG;
    }

    auto ctx = get_context(handle);
    if (!ctx) {
        return TR_E_HANDLE;
    }

    std::string tag_str(tag);
    std::string group_str(group);

    return ctx->post_and_wait([ctx, tag_str, group_str]() -> int32_t {
#ifdef _WIN32
        try {
            auto history = winrt::Windows::UI::Notifications::ToastNotificationManager::History();
            history.Remove(to_hstring(tag_str), to_hstring(group_str), to_hstring(ctx->aumid));
            return TR_S_OK;
        } catch (const winrt::hresult_error& ex) {
            return (int32_t)ex.code().value;
        } catch (...) {
            return TR_E_FAIL;
        }
#else
        return TR_S_OK;
#endif
    });
}

TR_TOAST_API int32_t TR_CALL tr_toast_clear_group(
    uint64_t handle,
    const char* group
) {
    if (!group) {
        return TR_E_INVALIDARG;
    }

    auto ctx = get_context(handle);
    if (!ctx) {
        return TR_E_HANDLE;
    }

    std::string group_str(group);

    return ctx->post_and_wait([ctx, group_str]() -> int32_t {
#ifdef _WIN32
        try {
            auto history = winrt::Windows::UI::Notifications::ToastNotificationManager::History();
            history.RemoveGroup(to_hstring(group_str), to_hstring(ctx->aumid));
            return TR_S_OK;
        } catch (const winrt::hresult_error& ex) {
            return (int32_t)ex.code().value;
        } catch (...) {
            return TR_E_FAIL;
        }
#else
        return TR_S_OK;
#endif
    });
}

TR_TOAST_API int32_t TR_CALL tr_toast_destroy(
    uint64_t handle
) {
    auto ctx = get_context(handle);
    if (!ctx) {
        return TR_E_HANDLE;
    }

    try {
        std::unique_lock<std::mutex> destroy_lock(ctx->destroy_mutex, std::try_to_lock);
        if (!destroy_lock.owns_lock()) {
            return TR_RO_E_CLOSED;
        }

        if (ctx->is_destroyed.load()) {
            return TR_E_HANDLE;
        }

        {
            std::lock_guard<std::mutex> lock(g_handles_mutex);
            if (g_handles.find(handle) == g_handles.end()) {
                return TR_E_HANDLE;
            }
        }

        // 1. Immediately fence public admission under queue_mutex
        {
            std::lock_guard<std::mutex> q_lock(ctx->queue_mutex);
            ctx->is_destroying.store(true);
        }

        // 2. Clear remote group on worker thread (bypassing public closed fence for teardown)
        if (!ctx->remote_group_cleared.load()) {
            int32_t clear_res = ctx->post_teardown_and_wait([ctx]() -> int32_t {
#ifdef _WIN32
                try {
                    auto history = winrt::Windows::UI::Notifications::ToastNotificationManager::History();
                    history.RemoveGroup(to_hstring("tr-remote"), to_hstring(ctx->aumid));
                    return TR_S_OK;
                } catch (const winrt::hresult_error& ex) {
                    return (int32_t)ex.code().value;
                } catch (...) {
                    return TR_E_FAIL;
                }
#else
                return TR_S_OK;
#endif
            });

            if (clear_res != TR_S_OK) {
                // Teardown cleanup failed or timed out: retain handle for retry; admission stays fenced
                return clear_res;
            }
            ctx->remote_group_cleared.store(true);
        }

        // 3. Drain in-flight callbacks with 2s timeout
        {
            std::unique_lock<std::mutex> lock(ctx->callback_mutex);
            bool drained = ctx->cv_callbacks.wait_for(lock, std::chrono::seconds(2), [ctx]() {
                return ctx->in_flight_callbacks.load() == 0;
            });

            if (!drained) {
                // Drain timed out: return TR_E_TIMEOUT without freeing context.
                // The handle remains owned and valid for shutdown retry.
                return TR_E_TIMEOUT;
            }
        }
        // 4. Stop worker thread and uninitialize apartment
        ctx->stop_and_join();

        // 5. Mark destroyed, remove handle from registry and let shared_ptr clean up
        ctx->is_destroyed.store(true);
        {
            std::lock_guard<std::mutex> lock(g_handles_mutex);
            g_handles.erase(handle);
        }
        return TR_S_OK;
    } catch (...) {
        return TR_E_FAIL;
    }
}

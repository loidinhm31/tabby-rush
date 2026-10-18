#include "tabbyrush-toast.h"

#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <iostream>

void test_abi_version() {
    uint32_t ver = tr_toast_abi_version();
    assert(ver == 1);
    std::cout << "[PASS] test_abi_version (ver=" << ver << ")" << std::endl;
}

void test_create_validation() {
    int32_t r1 = tr_toast_create("dev.tabbyrush.desktop", nullptr, nullptr, nullptr);
    assert(r1 == TR_E_POINTER);

    uint64_t handle = 0;
    int32_t r2 = tr_toast_create(nullptr, nullptr, nullptr, &handle);
    assert(r2 == TR_E_INVALIDARG);

    int32_t r3 = tr_toast_create("", nullptr, nullptr, &handle);
    assert(r3 == TR_E_INVALIDARG);

    std::cout << "[PASS] test_create_validation" << std::endl;
}

void test_invalid_handles() {
    uint64_t invalid_handle = 0xDEADBEEF;
    int32_t setting = 0;
    assert(tr_toast_setting(invalid_handle, &setting) == TR_E_HANDLE);
    assert(tr_toast_show(invalid_handle, "msg1", 1, "tag1", "tr-remote") == TR_E_HANDLE);
    assert(tr_toast_show_local(invalid_handle, "T", "B", "tag1", "grp1") == TR_E_HANDLE);
    assert(tr_toast_remove(invalid_handle, "tag1", "grp1") == TR_E_HANDLE);
    assert(tr_toast_clear_group(invalid_handle, "grp1") == TR_E_HANDLE);
    assert(tr_toast_destroy(invalid_handle) == TR_E_HANDLE);

    std::cout << "[PASS] test_invalid_handles" << std::endl;
}

void test_local_rejects_tr_remote() {
    uint64_t handle = 0;
    int32_t cr = tr_toast_create("dev.tabbyrush.desktop", nullptr, nullptr, &handle);
    if (cr == TR_S_OK) {
        int32_t rejected = tr_toast_show_local(handle, "Reminder", "Due soon", "tag1", "tr-remote");
        assert(rejected == TR_E_INVALIDARG);

        int32_t dr = tr_toast_destroy(handle);
        assert(dr == TR_S_OK);
        std::cout << "[PASS] test_local_rejects_tr_remote" << std::endl;
    } else {
        std::cout << "[SKIP] test_local_rejects_tr_remote (WinRT notifier creation unavailable in current environment)" << std::endl;
    }
}

void test_handle_lifecycle() {
    uint64_t handle = 0;
    int32_t cr = tr_toast_create("dev.tabbyrush.desktop", nullptr, nullptr, &handle);
    if (cr == TR_S_OK) {
        assert(handle != 0);

        int32_t setting = -1;
        int32_t sr = tr_toast_setting(handle, &setting);
        assert(sr == TR_S_OK);

        int32_t dr = tr_toast_destroy(handle);
        assert(dr == TR_S_OK);

        // After destroy, handle is no longer valid
        assert(tr_toast_setting(handle, &setting) == TR_E_HANDLE);
        assert(tr_toast_destroy(handle) == TR_E_HANDLE);

        std::cout << "[PASS] test_handle_lifecycle" << std::endl;
    } else {
        std::cout << "[SKIP] test_handle_lifecycle (WinRT notifier creation unavailable in current environment)" << std::endl;
    }
}

int main() {
    std::cout << "=== Running TabbyRush Native Toast Tests ===" << std::endl;
    test_abi_version();
    test_create_validation();
    test_invalid_handles();
    test_local_rejects_tr_remote();
    test_handle_lifecycle();
    std::cout << "=== All Native Tests Passed ===" << std::endl;
    return 0;
}

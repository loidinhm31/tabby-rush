package dev.tabbyrush.android

import dev.tabbyrush.shared.contract.TaskSurfaceAccess

interface TaskSurfaceHostOwner {
    val taskSurfaces: AndroidTaskSurfaceHost
}

interface AndroidTaskSurfaceHost {
    val access: TaskSurfaceAccess
    suspend fun refresh(reason: SurfaceRefreshReason)
}

enum class SurfaceRefreshReason {
    Data,
    Startup,
    Account,
    Clock,
    Capability,
    Widget
}

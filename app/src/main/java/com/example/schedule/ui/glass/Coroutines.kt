package com.example.schedule.ui.glass

import androidx.compose.runtime.withFrameNanos

/** 等待下一帧渲染（对应 catalog 的 expect/actual 实现）。 */
suspend fun awaitFrame() {
    withFrameNanos { }
}

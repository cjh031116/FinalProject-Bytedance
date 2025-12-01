package com.example.finalwork.mvp

import com.example.finalwork.VideoItem

/**
 * MVP 契约接口
 * 定义 View 和 Presenter 之间的通信协议
 */
interface VideoFeedContract {

    /**
     * View 接口 - 定义 Activity 能做什么
     */
    interface View {
        // 显示视频列表
        fun showVideos(videos: List<VideoItem>)

        // 追加更多视频
        fun appendVideos(videos: List<VideoItem>)

        // 播放指定位置的视频
        fun playVideo(position: Int)

        // 暂停当前视频
        fun pauseVideo()

        // 显示测试模式提示
        fun showTestMode(isPreloadEnabled: Boolean)

        // 显示性能报告
        fun showPerformanceReport(report: String)
    }

    /**
     * Presenter 接口 - 定义业务逻辑接口
     */
    interface Presenter {
        // 绑定 View
        fun attachView(view: View)

        // 解绑 View
        fun detachView()

        // 加载初始数据
        fun loadInitialVideos()

        // 加载更多视频
        fun loadMoreVideos()

        // 视频滚动到指定位置
        fun onVideoScrolled(position: Int)

        // 视频播放完成回调（记录性能）
        fun onVideoLoaded(position: Int, loadTimeMs: Long, isCached: Boolean)

        // 暂停当前视频
        fun onPause()

        // 释放资源
        fun onDestroy()

        // 设置预加载开关
        fun setPreloadEnabled(enabled: Boolean)
    }
}


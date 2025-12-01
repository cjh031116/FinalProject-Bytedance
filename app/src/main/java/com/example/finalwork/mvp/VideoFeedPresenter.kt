package com.example.finalwork.mvp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * Presenter 层 - 业务逻辑
 * 负责：协调 View 和 Model、管理业务流程、性能统计
 */
class VideoFeedPresenter(private val context: Context) : VideoFeedContract.Presenter {

    private var view: VideoFeedContract.View? = null
    private val model = VideoModel(context)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val loadMetrics = mutableListOf<LoadMetric>()
    private var testStartTime = System.currentTimeMillis()
    private var currentVideos: List<com.example.finalwork.VideoItem> = emptyList()

    companion object {
        private const val TAG = "VideoPresenter"
    }

    /**
     * 性能指标数据类
     */
    data class LoadMetric(
        val position: Int,
        val loadTimeMs: Long,
        val isCached: Boolean,
        val timestamp: Long
    )

    /**
     * 绑定 View
     */
    override fun attachView(view: VideoFeedContract.View) {
        this.view = view
        Log.d(TAG, "View 已绑定")
    }

    /**
     * 解绑 View
     */
    override fun detachView() {
        this.view = null
        Log.d(TAG, "View 已解绑")
    }

    /**
     * 设置预加载开关
     */
    override fun setPreloadEnabled(enabled: Boolean) {
        model.enablePreload = enabled
        view?.showTestMode(enabled)

        Log.d(TAG, "========================================")
        Log.d(TAG, "Presenter 初始化")
        Log.d(TAG, "预加载功能: ${if (enabled) "已启用" else "已禁用"}")
        Log.d(TAG, "测试开始时间: $testStartTime")
        Log.d(TAG, "========================================")
    }

    /**
     * 加载初始视频列表
     */
    override fun loadInitialVideos() {
        scope.launch {
            try {
                val videos = withContext(Dispatchers.IO) {
                    model.loadInitialVideos()
                }
                currentVideos = videos
                view?.showVideos(videos)
                Log.d(TAG, "初始视频列表已加载: ${videos.size} 个")
            } catch (e: Exception) {
                Log.e(TAG, "加载初始视频失败", e)
            }
        }
    }

    /**
     * 加载更多视频
     */
    override fun loadMoreVideos() {
        scope.launch {
            try {
                // 延迟500ms防抖
                delay(500)
                val moreVideos = withContext(Dispatchers.IO) {
                    model.loadMoreVideos()
                }
                if (moreVideos.isNotEmpty()) {
                    currentVideos = currentVideos + moreVideos
                    view?.appendVideos(moreVideos)
                    Log.d(TAG, "追加视频: ${moreVideos.size} 个")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载更多视频失败", e)
            }
        }
    }

    /**
     * 视频滚动到指定位置
     */
    override fun onVideoScrolled(position: Int) {
        Log.d(TAG, "--------------------------------------")
        Log.d(TAG, "准备播放视频 #$position")

        // 通知 View 播放视频
        view?.playVideo(position)

        // 触发预加载
        if (currentVideos.isNotEmpty()) {
            model.preloadNextVideos(position, currentVideos.size, currentVideos)
        }
    }

    /**
     * 视频加载完成回调
     */
    override fun onVideoLoaded(position: Int, loadTimeMs: Long, isCached: Boolean) {
        val metric = LoadMetric(position, loadTimeMs, isCached, System.currentTimeMillis())
        loadMetrics.add(metric)

        Log.d(TAG, "✓ 视频 #$position 加载完成")
        Log.d(TAG, "加载耗时: ${loadTimeMs}ms")
        Log.d(TAG, "是否命中缓存: ${if (isCached) "是" else "否"}")

        // 打印实时统计
        printStatistics()
    }

    /**
     * 暂停当前视频
     */
    override fun onPause() {
        view?.pauseVideo()
        Log.d(TAG, "视频已暂停")
    }

    /**
     * 释放资源
     */
    override fun onDestroy() {
        Log.d(TAG, "🔴 onDestroy 开始执行")
        Log.d(TAG, "loadMetrics 大小: ${loadMetrics.size}")

        // 生成并显示最终报告
        val report = generatePerformanceReport()

        // 🆕 直接打印到 Logcat（确保能看到）
        Log.i(TAG, "")
        Log.i(TAG, "========================================")
        Log.i(TAG, "           最终性能测试报告")
        Log.i(TAG, "========================================")
        report.lines().forEach { line ->
            if (line.isNotBlank()) {
                Log.i(TAG, line.replace("|", ""))
            }
        }
        Log.i(TAG, "========================================")

        view?.showPerformanceReport(report)

        // 清理资源
        model.cleanup()
        scope.cancel()
        view = null

        Log.d(TAG, "✅ Presenter 资源已释放")
    }

    /**
     * 打印实时统计
     */
    private fun printStatistics() {
        if (loadMetrics.isEmpty()) return

        val totalCount = loadMetrics.size
        val avgLoadTime = loadMetrics.map { it.loadTimeMs }.average()
        val cachedMetrics = loadMetrics.filter { it.isCached }
        val nonCachedMetrics = loadMetrics.filter { !it.isCached }
        val cacheHitRate = if (totalCount > 0) (cachedMetrics.size.toFloat() / totalCount * 100) else 0f

        val avgCachedTime = if (cachedMetrics.isNotEmpty()) cachedMetrics.map { it.loadTimeMs }.average() else 0.0
        val avgNonCachedTime = if (nonCachedMetrics.isNotEmpty()) nonCachedMetrics.map { it.loadTimeMs }.average() else 0.0

        Log.d(TAG, "")
        Log.d(TAG, "====== 性能统计摘要 ======")
        Log.d(TAG, "预加载状态: ${if (model.enablePreload) "开启" else "关闭"}")
        Log.d(TAG, "已播放视频数: $totalCount")
        Log.d(TAG, "平均加载时间: ${avgLoadTime.toInt()}ms")
        Log.d(TAG, "缓存命中率: ${"%.1f".format(cacheHitRate)}%")
        Log.d(TAG, "缓存视频平均加载: ${avgCachedTime.toInt()}ms (${cachedMetrics.size}次)")
        Log.d(TAG, "非缓存视频平均加载: ${avgNonCachedTime.toInt()}ms (${nonCachedMetrics.size}次)")
        Log.d(TAG, "==========================")
        Log.d(TAG, "")
    }

    /**
     * 生成性能报告
     */
    private fun generatePerformanceReport(): String {
        if (loadMetrics.isEmpty()) {
            return "暂无数据"
        }

        val totalCount = loadMetrics.size
        val avgLoadTime = loadMetrics.map { it.loadTimeMs }.average()
        val cachedMetrics = loadMetrics.filter { it.isCached }
        val nonCachedMetrics = loadMetrics.filter { !it.isCached }
        val cacheHitRate = (cachedMetrics.size.toFloat() / totalCount * 100)

        val avgCachedTime = if (cachedMetrics.isNotEmpty()) cachedMetrics.map { it.loadTimeMs }.average() else 0.0
        val avgNonCachedTime = if (nonCachedMetrics.isNotEmpty()) nonCachedMetrics.map { it.loadTimeMs }.average() else 0.0

        val minLoadTime = loadMetrics.minOf { it.loadTimeMs }
        val maxLoadTime = loadMetrics.maxOf { it.loadTimeMs }

        val testDuration = System.currentTimeMillis() - testStartTime

        return """
            |
            |========================================
            |           性能测试报告 (MVP架构)
            |========================================
            |预加载状态: ${if (model.enablePreload) "✓ 已启用" else "✗ 已禁用"}
            |测试时长: ${testDuration / 1000}秒
            |----------------------------------------
            |播放统计:
            |  总播放次数: $totalCount
            |  平均加载时间: ${avgLoadTime.toInt()}ms
            |  最快加载: ${minLoadTime}ms
            |  最慢加载: ${maxLoadTime}ms
            |----------------------------------------
            |缓存统计:
            |  缓存命中率: ${"%.1f".format(cacheHitRate)}%
            |  缓存命中次数: ${cachedMetrics.size}
            |  缓存未命中次数: ${nonCachedMetrics.size}
            |  缓存视频平均加载: ${avgCachedTime.toInt()}ms
            |  非缓存视频平均加载: ${avgNonCachedTime.toInt()}ms
            |----------------------------------------
            |详细数据:
            |${loadMetrics.joinToString("\n") { 
                "  视频#${it.position}: ${it.loadTimeMs}ms (${if (it.isCached) "缓存" else "网络"})"
            }}
            |========================================
        """.trimMargin()
    }
}


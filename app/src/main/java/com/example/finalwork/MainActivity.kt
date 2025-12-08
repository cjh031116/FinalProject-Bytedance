package com.example.finalwork

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.finalwork.mvp.VideoFeedContract
import com.example.finalwork.mvp.VideoFeedMvpAdapter
import com.example.finalwork.mvp.VideoFeedPresenter

/**
 * MainActivity - MVP 架构的 View 层
 * 职责：只负责 UI 显示和用户交互，不处理业务逻辑
 */
class MainActivity : AppCompatActivity(), VideoFeedContract.View {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoFeedMvpAdapter
    private lateinit var presenter: VideoFeedPresenter

    //  新增：全屏状态标记
    private var isFullscreen = false

    companion object {
        private const val TAG = "MainActivity"
        private const val ENABLE_PRELOAD = true //测试开关
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        // 测试时清除缓存
         clearCacheForTest()

        // 初始化 Presenter
        presenter = VideoFeedPresenter(this)
        presenter.attachView(this)
        presenter.setPreloadEnabled(ENABLE_PRELOAD)

        // 设置 RecyclerView
        setupRecyclerView()

        // 加载初始数据
        presenter.loadInitialVideos()

        // ✅ 注册返回键处理（替代已弃用的 onBackPressed）
        setupBackPressHandler()
    }

    /**
     * 设置返回键处理
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    // 横屏时按返回键退出全屏
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    // 竖屏时正常退出
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * 清除缓存用于测试
     * 在测试前取消注释 onCreate 中的调用
     */
    private fun clearCacheForTest() {
        try {
            CacheUtil.releaseCache()
            val cacheDir = java.io.File(cacheDir, "media")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d(TAG, "✓ 缓存已清除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除缓存失败: ${e.message}")
        }
    }

    /**
     * 设置 RecyclerView
     */
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.rv_videos)
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layoutManager

        // 设置 SnapHelper
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        // 创建 Adapter，传入回调
        adapter = VideoFeedMvpAdapter(this) { position, loadTime, isCached ->
            // 视频加载完成回调到 Presenter
            presenter.onVideoLoaded(position, loadTime, isCached)
        }
        recyclerView.adapter = adapter

        // 监听滚动事件
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                // ✅ 全屏时禁止滚动
                if (isFullscreen) {
                    recyclerView.stopScroll()
                    return
                }

                // 滚动停止时播放当前可见视频
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val position = getCurrentVisiblePosition()
                    if (position != RecyclerView.NO_POSITION) {
                        presenter.onVideoScrolled(position)

                        // ✅ 设置全屏按钮点击事件
                        setupFullscreenButton(position)
                    }
                }

                // 滚动到底部时加载更多
                if (!recyclerView.canScrollVertically(1)) {
                    presenter.loadMoreVideos()
                }
            }

            // ✅ 全屏时阻止滚动
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (isFullscreen && dy != 0) {
                    recyclerView.stopScroll()
                }
            }
        })

        // 初始播放第一个视频
        recyclerView.post {
            val position = getCurrentVisiblePosition()
            if (position != RecyclerView.NO_POSITION) {
                presenter.onVideoScrolled(position)
                setupFullscreenButton(position)
            }
        }
    }

    /**
     * 设置全屏按钮点击事件
     */
    private fun setupFullscreenButton(position: Int) {
        val holder = recyclerView.findViewHolderForAdapterPosition(position)
            as? VideoFeedMvpAdapter.VideoViewHolder

        holder?.binding?.btnFullscreen?.setOnClickListener {
            toggleFullscreen()
        }
    }

    /**
     * 切换全屏/退出全屏
     */
    private fun toggleFullscreen() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            // 切换到横屏
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            // 切换回竖屏
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    /**
     * 监听屏幕旋转
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        when (newConfig.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                Log.d(TAG, "切换到横屏模式")
                isFullscreen = true
                hideSystemUI()
                // 禁止 RecyclerView 滚动
                recyclerView.suppressLayout(true)
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                Log.d(TAG, "切换到竖屏模式")
                isFullscreen = false
                showSystemUI()
                // 恢复 RecyclerView 滚动
                recyclerView.suppressLayout(false)
            }
        }
    }

    /**
     * 隐藏系统 UI（沉浸式全屏）
     */
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, recyclerView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * 显示系统 UI
     */
    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, recyclerView)
            .show(WindowInsetsCompat.Type.systemBars())
    }


    /**
     * 获取当前可见的视频位置
     */
    private fun getCurrentVisiblePosition(): Int {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return RecyclerView.NO_POSITION
        return layoutManager.findFirstCompletelyVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: layoutManager.findFirstVisibleItemPosition()
    }

    // View 接口实现

    override fun showVideos(videos: List<VideoItem>) {
        adapter.submitList(videos)
        Log.d(TAG, "显示视频列表: ${videos.size} 个")
    }

    override fun appendVideos(videos: List<VideoItem>) {
        val currentList = adapter.currentList.toMutableList()
        currentList.addAll(videos)
        adapter.submitList(currentList)
        Log.d(TAG, "追加视频: ${videos.size} 个")
    }

    override fun playVideo(position: Int) {
        adapter.playVideoAt(position, recyclerView)
    }

    override fun pauseVideo() {
        adapter.pauseCurrent()
    }

    override fun showTestMode(isPreloadEnabled: Boolean) {
        val testMode = if (isPreloadEnabled) "启用预加载" else "禁用预加载"
        Toast.makeText(this, "测试模式: $testMode (MVP架构)", Toast.LENGTH_LONG).show()
        Log.i(TAG, "========================================")
        Log.i(TAG, "应用启动 - 测试模式: $testMode")
        Log.i(TAG, "架构: MVP")
        Log.i(TAG, "========================================")
    }

    override fun showPerformanceReport(report: String) {
        Log.i(TAG, "\n$report")
    }


    override fun onPause() {
        super.onPause()
        presenter.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
        adapter.release()
        CacheUtil.releaseCache()
    }
}

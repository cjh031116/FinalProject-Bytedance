package com.example.finalwork

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoFeedAdapter
    private val dataSource = VideoRepository()
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MainActivity"
        // ⚠️ 测试开关：改为 false 测试"优化前"，改为 true 测试"优化后"
        private const val ENABLE_PRELOAD = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        // 显示当前测试模式
        val testMode = if (ENABLE_PRELOAD) "启用预加载" else "禁用预加载"
        Toast.makeText(this, "测试模式: $testMode", Toast.LENGTH_LONG).show()
        Log.i(TAG, "========================================")
        Log.i(TAG, "应用启动 - 测试模式: $testMode")
        Log.i(TAG, "========================================")

        //layoutmanager来设置滑动样式
        recyclerView = findViewById(R.id.rv_videos)
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layoutManager

        //总能找到一个完全可见的视频项来播放
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        //配置好适配器 - 传入预加载开关
        adapter = VideoFeedAdapter(this, enablePreload = ENABLE_PRELOAD)
        recyclerView.adapter = adapter

        adapter.submitList(dataSource.loadInitial())

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    autoPlayCurrentVisible()
                }

                if (!recyclerView.canScrollVertically(1)) {
                    loadMore()
                }
            }
        })

        recyclerView.post { autoPlayCurrentVisible() }
    }

    private fun autoPlayCurrentVisible() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstCompletelyVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: layoutManager.findFirstVisibleItemPosition()

        if (position == RecyclerView.NO_POSITION) return

        adapter.playVideoAt(position, recyclerView)
    }

    private fun loadMore() {
        handler.postDelayed({ //防抖
            val more = dataSource.loadMore()
            if (more.isNotEmpty()) {
                adapter.appendList(more)
            }
        }, 500)
    }

    override fun onPause() {
        super.onPause()
        adapter.pauseCurrent()
    }

    override fun onDestroy() {
        super.onDestroy()

        // 输出完整性能报告
        Log.i(TAG, "\n" + adapter.getPerformanceReport())

        adapter.release()
        handler.removeCallbacksAndMessages(null) //针对handler.postDelayed 防止内存泄漏，移除所有待处理的回调和消息
        CacheUtil.releaseCache()
    }
}

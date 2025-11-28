package com.example.finalwork

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoFeedAdapter
    private val dataSource = VideoRepository()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        recyclerView = findViewById(R.id.rv_videos)
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layoutManager

        adapter = VideoFeedAdapter(this)
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
        adapter.release()
        handler.removeCallbacksAndMessages(null) //针对handler.postDelayed 防止内存泄漏，移除所有待处理的回调和消息
    }
}

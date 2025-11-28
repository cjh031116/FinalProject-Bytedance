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

        recyclerView = findViewById(R.id.rv_videos)
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layoutManager

        adapter = VideoFeedAdapter(this)
        recyclerView.adapter = adapter

        // Initial data
        adapter.submitList(dataSource.loadInitial())

        // Auto play current item on scroll idle
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    autoPlayCurrentVisible()
                }

                // Load more when near bottom
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

        adapter.playVideoAt(position)
    }

    private fun loadMore() {
        // Simulate async loading
        handler.postDelayed({
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
    }
}

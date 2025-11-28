package com.example.finalwork

data class VideoItem(
    val id: String,
    val title: String,
    val authorName: String,
    val avatarUrl: String?,
    val videoUrl: String,
    val likeCount: Int,
    val commentCount: Int,
    val collectCount: Int,
    val shareCount: Int,
)

class VideoRepository {
    private var page = 0

    fun loadInitial(): List<VideoItem> {
        page = 0
        return generatePage(page)
    }

    fun loadMore(): List<VideoItem> {
        page++
        return generatePage(page)
    }

    private fun generatePage(page: Int): List<VideoItem> {
        // In a real app, request from network. Here we just generate mock data with sample video URLs.
        val items = mutableListOf<VideoItem>()
        val baseIndex = page * 5

        // Replace these URLs with your own mp4 links reachable from device/emulator.
        val demoUrls = listOf(
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/frame-counter-one-hour.mp4"
        )

        for (i in 0 until 5) {
            val index = baseIndex + i
            items += VideoItem(
                id = index.toString(),
                title = "演示视频 #$index",
                authorName = "作者 $index",
                avatarUrl = null,
                videoUrl = demoUrls[index % demoUrls.size],
                likeCount = 100 + index,
                commentCount = 20 + index,
                collectCount = 10 + index,
                shareCount = 5 + index,
            )
        }
        return items
    }
}


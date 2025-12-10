package com.example.finalwork

// 1. 定义内容类型的枚举
enum class ItemType {
    VIDEO,
    IMAGE_CAROUSEL
}

// 2. 修改数据类以支持不同类型
data class VideoItem(
    val id: String,
    val title: String,
    val authorName: String,
    val avatarUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val collectCount: Int,
    val shareCount: Int,
    // 新增字段
    val type: ItemType,
    val videoUrl: String?, // 视频URL，对于图片类型可为null
    val imageUrls: List<String>? // 图片URL列表，对于视频类型可为null
)

class VideoRepository {
    private val masterVideoList = listOf(

        VideoItem("0", "Ocean scene", "作者 1", null, 101, 10, 20, 5, ItemType.VIDEO, "http://vjs.zencdn.net/v/oceans.mp4", null),

        VideoItem(
            id = "image_carousel_1",
            title = "一组漂亮的风景图",
            authorName = "摄影师大C",
            avatarUrl = null,
            likeCount = 550,
            commentCount = 60,
            collectCount = 120,
            shareCount = 30,
            type = ItemType.IMAGE_CAROUSEL,
            videoUrl = null, // 图片类型没有视频URL
            imageUrls = listOf( // 提供图片URL列表
                "https://images.pexels.com/photos/3225517/pexels-photo-3225517.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=1",
                "https://images.pexels.com/photos/2387873/pexels-photo-2387873.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=1",
                "https://images.pexels.com/photos/1528640/pexels-photo-1528640.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=1",
                "https://images.pexels.com/photos/33041/antelope-canyon-lower-canyon-arizona.jpg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=1"
            )
        ),

        // 视频项 2
        VideoItem("1", "Trailer", "作者 2", null, 152, 12, 22, 8, ItemType.VIDEO, "https://media.w3.org/2010/05/sintel/trailer.mp4", null),
        VideoItem("2", "TED Talk", "作者 3", null, 203, 15, 25, 3, ItemType.VIDEO, "http://mirror.aarnet.edu.au/pub/TED-talks/911Mothers_2010W-480p.mp4", null),
        VideoItem("3", "Bunny", "作者 4", null, 98, 8, 15, 1, ItemType.VIDEO, "https://cesium.com/public/SandcastleSampleData/big-buck-bunny_trailer.mp4", null),
        VideoItem("4", "News 1", "作者 5", null, 305, 30, 40, 15, ItemType.VIDEO, "https://stream7.iqilu.com/10339/upload_transcode/202002/09/20200209105011F0zPoYzHry.mp4", null),
        VideoItem("5", "News 2", "作者 6", null, 410, 45, 50, 20, ItemType.VIDEO, "https://stream7.iqilu.com/10339/upload_transcode/202002/09/20200209104902N3v5Vpxuvb.mp4", null),
        VideoItem("6", "Bear", "作者 7", null, 250, 22, 33, 11, ItemType.VIDEO, "https://www.w3schools.com/html/movie.mp4", null),
        VideoItem("7", "Popular", "作者 8", null, 180, 18, 28, 9, ItemType.VIDEO, "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4", null),
        VideoItem("8", "Big Rabit", "作者 9", null, 500, 50, 60, 25, ItemType.VIDEO, "https://www.w3school.com.cn/example/html5/mov_bbb.mp4", null),
        VideoItem("9", "Time", "作者 10", null, 333, 33, 44, 12, ItemType.VIDEO, "http://devimages.apple.com/iphone/samples/bipbop/bipbopall.m3u8", null)
    )
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
        val itemsPerPage = 5
        val items = mutableListOf<VideoItem>()
        val startIndex = page * itemsPerPage

        for (i in 0 until itemsPerPage) {
            val indexInMasterList = (startIndex + i) % masterVideoList.size
            val originalItem = masterVideoList[indexInMasterList]
            items.add(originalItem.copy(id = "instance_${startIndex + i}_${originalItem.id}"))
        }
        return items
    }
}

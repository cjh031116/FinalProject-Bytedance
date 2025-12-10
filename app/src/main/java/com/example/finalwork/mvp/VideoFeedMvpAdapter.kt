@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.finalwork.mvp

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.finalwork.CacheUtil
import com.example.finalwork.ItemType
import com.example.finalwork.R
import com.example.finalwork.VideoItem
import com.example.finalwork.databinding.ItemImageCarouselBinding
import com.example.finalwork.databinding.ItemImagePageBinding
import com.example.finalwork.databinding.ItemVideoBinding
import com.google.android.material.tabs.TabLayoutMediator

class VideoFeedMvpAdapter(
    private val context: Context,
    private val onVideoLoadComplete: (position: Int, loadTimeMs: Long, isCached: Boolean) -> Unit
) : ListAdapter<VideoItem, RecyclerView.ViewHolder>(VideoDiffCallback()) {

    private var player: ExoPlayer
    private var currentPlayingPosition = RecyclerView.NO_POSITION
    private var currentPlayingHolder: VideoViewHolder? = null
    private val cacheDataSourceFactory: CacheDataSource.Factory

    companion object {
        private const val TAG = "VideoMvpAdapter"
        private const val VIEW_TYPE_VIDEO = 1
        private const val VIEW_TYPE_IMAGE_CAROUSEL = 2
    }

    init {
        val simpleCache = CacheUtil.getCache(context)
        val upstreamFactory = DefaultDataSource.Factory(context)
        cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 5000, 200, 400)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(-1)
            .build()

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()

        player.setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        Log.d(TAG, "Adapter 初始化完成")
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).type) {
            ItemType.VIDEO -> VIEW_TYPE_VIDEO
            ItemType.IMAGE_CAROUSEL -> VIEW_TYPE_IMAGE_CAROUSEL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_VIDEO -> {
                val binding = ItemVideoBinding.inflate(inflater, parent, false)
                VideoViewHolder(binding)
            }
            VIEW_TYPE_IMAGE_CAROUSEL -> {
                val binding = ItemImageCarouselBinding.inflate(inflater, parent, false)
                ImageCarouselViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is VideoViewHolder -> holder.bind(item)
            is ImageCarouselViewHolder -> holder.bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder && holder == currentPlayingHolder) {
            holder.binding.playerView.player = null
        }
    }

    fun playVideoAt(position: Int, recyclerView: RecyclerView) {
        if (position == RecyclerView.NO_POSITION) return

        if (getItem(position).type == ItemType.IMAGE_CAROUSEL) {
            if (currentPlayingPosition != RecyclerView.NO_POSITION) {
                Log.d(TAG, "滚动到图片项，暂停视频")
                pauseCurrent()
                currentPlayingHolder?.binding?.playerView?.player = null
                currentPlayingPosition = RecyclerView.NO_POSITION
                currentPlayingHolder = null
            }
            return
        }

        if (position == currentPlayingPosition) return

        currentPlayingHolder?.binding?.playerView?.player = null

        val newHolder = recyclerView.findViewHolderForAdapterPosition(position) as? VideoViewHolder
        if (newHolder != null) {
            currentPlayingPosition = position
            currentPlayingHolder = newHolder
            newHolder.binding.playerView.player = player

            val videoItem = getItem(position)
            val videoUrl = videoItem.videoUrl ?: return
            val mediaItem = buildMediaItem(videoUrl)
            val startTime = System.currentTimeMillis()
            val isCached = checkIfCached(videoUrl)

            Log.d(TAG, "开始播放视频 #$position: ${videoItem.title}")

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val loadTime = System.currentTimeMillis() - startTime
                        onVideoLoadComplete(position, loadTime, isCached)
                        player.removeListener(this)
                    }
                }
            })

            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun pauseCurrent() {
        player.pause()
    }

    fun release() {
        player.release()
        Log.d(TAG, "播放器资源已释放")
    }

    private fun buildMediaItem(videoUrl: String): MediaItem {
        val cacheKey = buildCacheKey(videoUrl)
        return MediaItem.Builder().setUri(Uri.parse(cacheKey)).setMediaId(cacheKey).build()
    }

    private fun checkIfCached(videoUrl: String): Boolean {
        val cacheKey = buildCacheKey(videoUrl)
        val preloadSize = 2 * 1024 * 1024L
        val cacheHitThreshold = 1024 * 1024L
        val cachedBytes = CacheUtil.getCache(context).getCachedBytes(cacheKey, 0, preloadSize)
        return cachedBytes >= cacheHitThreshold
    }

    private fun buildCacheKey(videoUrl: String?): String {
        if (videoUrl == null) return ""
        return if (videoUrl.startsWith("http")) {
            videoUrl
        } else {
            val videoId = context.resources.getIdentifier(videoUrl, "raw", context.packageName)
            "android.resource://${context.packageName}/$videoId"
        }
    }

    class VideoViewHolder(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VideoItem) {
            binding.tvAuthor.text = item.authorName
            binding.tvTitle.text = item.title
            binding.tvLikeCount.text = item.likeCount.toString()
            binding.tvCommentCount.text = item.commentCount.toString()
            binding.tvCollectCount.text = item.collectCount.toString()
            binding.tvShareCount.text = item.shareCount.toString()
        }
    }

    class ImageCarouselViewHolder(private val binding: ItemImageCarouselBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VideoItem) {
            item.imageUrls?.let { urls ->
                val imagePagerAdapter = ImagePagerAdapter(urls)
                binding.imagePager.adapter = imagePagerAdapter
                binding.dotsIndicator.attachTo(binding.imagePager)
            }
            binding.bottomInfoContainer.tvAuthor.text = item.authorName
            binding.bottomInfoContainer.tvTitle.text = item.title
            binding.sideActionsContainer.tvLikeCount.text = item.likeCount.toString()
            binding.sideActionsContainer.tvCommentCount.text = item.commentCount.toString()
            binding.sideActionsContainer.tvCollectCount.text = item.collectCount.toString()
            binding.sideActionsContainer.tvShareCount.text = item.shareCount.toString()
        }
    }

    class ImagePagerAdapter(private val imageUrls: List<String>) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {
        class ImageViewHolder(val binding: ItemImagePageBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val binding = ItemImagePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ImageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            Glide.with(holder.itemView.context)
                .load(imageUrls[position])
                .into(holder.binding.imageView)
        }

        override fun getItemCount(): Int = imageUrls.size
    }
}

class VideoDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
    override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        return oldItem == newItem
    }
}

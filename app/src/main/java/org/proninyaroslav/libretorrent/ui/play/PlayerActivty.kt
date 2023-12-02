package org.proninyaroslav.libretorrent.ui.play

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import org.proninyaroslav.libretorrent.MainApplication


class PlayerActivty : AppCompatActivity() {
    companion object {
        private val TAG = PlayerActivty::class.java.simpleName

        @JvmField
        val PLAY_FROM_VIDEOGRID =
            MainApplication.mainAppInstance.packageName + "gui.video.PLAY_FROM_VIDEOGRID"

        const val PLAY_EXTRA_ITEM_LOCATION = "item_location"
        const val PLAY_EXTRA_ITEM_TITLE = "title"
        const val PLAY_EXTRA_FROM_START = "from_start"
        const val PLAY_EXTRA_START_TIME = "position"
        const val PLAY_EXTRA_OPENED_POSITION = "opened_position"
        const val PLAY_DISABLE_HARDWARE = "disable_hardware"

        fun start(context: Context, uri: Uri) {
            start(context, uri, null, false, -1)
        }

        fun start(context: Context, uri: Uri, fromStart: Boolean) {
            start(context, uri, null, fromStart, -1)
        }

        fun start(context: Context, uri: Uri, title: String) {
            start(context, uri, title, false, -1)
        }

        fun startOpened(context: Context, uri: Uri, openedPosition: Int) {
            start(context, uri, null, false, openedPosition)
        }

        private fun start(
            context: Context,
            uri: Uri,
            title: String?,
            fromStart: Boolean,
            openedPosition: Int
        ) {
            val intent = getIntent(context, uri, title, fromStart, openedPosition)
            context.startActivity(intent)
        }


        fun getIntent(
            context: Context,
            uri: Uri,
            title: String?,
            fromStart: Boolean,
            openedPosition: Int
        ): Intent {
            return getIntent(PLAY_FROM_VIDEOGRID, context, uri, title, fromStart, openedPosition)
        }

        fun getIntent(
            action: String,
            context: Context,
            uri: Uri,
            title: String?,
            fromStart: Boolean,
            openedPosition: Int
        ): Intent {
            val intent = Intent(context, PlayerActivty::class.java)
            intent.action = action
            intent.putExtra(PLAY_EXTRA_ITEM_LOCATION, uri)
            intent.putExtra(PLAY_EXTRA_ITEM_TITLE, title)
            intent.putExtra(PLAY_EXTRA_FROM_START, fromStart)

            if (openedPosition != -1 || context !is Activity) {
                if (openedPosition != -1)
                    intent.putExtra(PLAY_EXTRA_OPENED_POSITION, openedPosition)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return intent
        }
    }

    private lateinit var player: ExoPlayer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var playerView = PlayerView(this)
        setContentView(playerView)

        player = ExoPlayer.Builder(this).build()

        playerView.player = player

        initPlayer(intent)
    }

    private fun initPlayer(intent: Intent) {
        var uri: Uri? = if (intent.hasExtra(PLAY_EXTRA_ITEM_LOCATION))
            intent.extras!!.getParcelable<Parcelable>(PLAY_EXTRA_ITEM_LOCATION) as Uri?
        else
            intent.data

        uri!!.apply {
            Log.e(TAG, "uri: ${scheme?.toCharArray().toString()} ${host?.split(".")} $authority $path $query $fragment")
            val mediaItem = MediaItem.fromUri(this)
            player.setMediaItem(mediaItem)//准备媒体资源
            player.prepare()
            player.play()//开始播放
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.stop()
        player.release()
    }
}
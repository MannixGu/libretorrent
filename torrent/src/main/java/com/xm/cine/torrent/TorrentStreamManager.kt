package com.xm.cine.torrent

import android.content.Context
import android.net.Uri
import android.util.Log
import com.xm.cine.torrent.addtorrent.AddTorrentViewModel
import com.xm.cine.torrent.core.model.TorrentEngine
import com.xm.cine.torrent.core.model.TorrentEngineListener
import java.lang.Exception

object TorrentStreamManager : TorrentEngineListener() {
    private const val TAG = "TorrentStreamManager"

    interface Listener {
        fun onTorrentStreamReady(id: String)
        fun onTorrentStreamError(id: String, e: Exception?)
    }

    private lateinit var engine: TorrentEngine
    private lateinit var context: Context
    private val urlIdMap = mutableMapOf<String, String>()

    var listener: Listener? = null

    fun init(context: Context) {
        this.context = context
        engine = TorrentEngine.getInstance(context)
        engine.doStart()
        engine.addListener(this)
    }

    fun startTorrentStream(torrentUrl: String) {
        observeDecodeState(context, torrentUrl)
    }

    fun getFirstTorrentStream(id: String): String? {
        return engine.getFirstMediaFile(id)?.run {
            engine.getStreamUrl(this)
        }
    }

    fun getAllTorrentStream(id: String): List<String>? {
        return engine.getAllMediaFile(id)?.map {
            engine.getStreamUrl(it)
        }
    }

    fun stopTorrentStream(id: String) {
        TODO()
    }

    fun stopAllTorrentStream() {
        TODO()
    }

    private fun observeDecodeState(context: Context, url: String) {
        var uri = Uri.parse(url)

        val addTorrentViewModel = AddTorrentViewModel(context)
        addTorrentViewModel.getDecodeState()
            .observeForever { state: AddTorrentViewModel.DecodeState ->
                when (state.status) {
                    AddTorrentViewModel.Status.UNKNOWN -> {
                        if (uri != null) addTorrentViewModel.startDecode(uri)
                    }

                    AddTorrentViewModel.Status.DECODE_TORRENT_FILE, AddTorrentViewModel.Status.FETCHING_HTTP, AddTorrentViewModel.Status.FETCHING_MAGNET ->
                        onStartDecode(
                            addTorrentViewModel,
                            state.status == AddTorrentViewModel.Status.DECODE_TORRENT_FILE
                        )

                    AddTorrentViewModel.Status.FETCHING_HTTP_COMPLETED, AddTorrentViewModel.Status.DECODE_TORRENT_COMPLETED, AddTorrentViewModel.Status.FETCHING_MAGNET_COMPLETED, AddTorrentViewModel.Status.ERROR ->
                        onStopDecode(addTorrentViewModel, state.error)
                }
            }
    }

    private fun onStartDecode(viewModel: AddTorrentViewModel, isTorrentFile: Boolean) {

    }

    private fun onStopDecode(viewModel: AddTorrentViewModel, e: Throwable?) {
        if (e != null) {
            e.printStackTrace()
            return
        }
        viewModel.makeFileTree()

        viewModel.addTorrent { params, url ->
            Log.d(TAG, "onStopDecode: $params $url")
            urlIdMap[url] = params.sha1hash
        }
    }

    override fun onTorrentAdded(id: String) {
        Log.d(TAG, "onTorrentAdded: $id")
        listener?.onTorrentStreamReady(id)
    }

    override fun onTorrentError(id: String, e: Exception?) {
        listener?.onTorrentStreamError(id, e)
    }
}
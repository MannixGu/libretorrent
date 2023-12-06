/*
 * Copyright (C) 2019-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.xm.cine.torrent.core.model;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.xm.cine.unit.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;

import com.xm.cine.torrent.core.FileInTorrent;
import com.xm.cine.torrent.core.RepositoryHelper;
import com.xm.cine.torrent.core.TorrentFileObserver;
import com.xm.cine.torrent.core.exception.DecodeException;
import com.xm.cine.torrent.core.exception.FreeSpaceException;
import com.xm.cine.torrent.core.exception.TorrentAlreadyExistsException;
import com.xm.cine.torrent.core.exception.UnknownUriException;
import com.xm.cine.torrent.core.model.data.AdvancedTorrentInfo;
import com.xm.cine.torrent.core.model.data.MagnetInfo;
import com.xm.cine.torrent.core.model.data.PeerInfo;
import com.xm.cine.torrent.core.model.data.Priority;
import com.xm.cine.torrent.core.model.data.TorrentInfo;
import com.xm.cine.torrent.core.model.data.TrackerInfo;
import com.xm.cine.torrent.core.model.data.entity.Torrent;
import com.xm.cine.torrent.core.model.data.metainfo.TorrentMetaInfo;
import com.xm.cine.torrent.core.model.session.TorrentDownload;
import com.xm.cine.torrent.core.model.session.TorrentSession;
import com.xm.cine.torrent.core.model.session.TorrentSessionImpl;
import com.xm.cine.torrent.core.model.stream.TorrentInputStream;
import com.xm.cine.torrent.core.model.stream.TorrentStream;
import com.xm.cine.torrent.core.model.stream.TorrentStreamServer;
import com.xm.cine.torrent.core.settings.SessionSettings;
import com.xm.cine.torrent.core.settings.SettingsRepository;
import com.xm.cine.torrent.core.storage.TorrentRepository;
import com.xm.cine.torrent.core.system.FileDescriptorWrapper;
import com.xm.cine.torrent.core.system.FileSystemFacade;
import com.xm.cine.torrent.core.system.SystemFacadeHelper;
import com.xm.cine.torrent.core.utils.Utils;

import org.apache.commons.io.filefilter.FileFilterUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;

public class TorrentEngine {
    private static final String TAG = TorrentEngine.class.getSimpleName();

    private Context appContext;
    private TorrentSession session;
    private TorrentStreamServer torrentStreamServer;
    private TorrentRepository repo;
    private SettingsRepository pref;
    private CompositeDisposable disposables = new CompositeDisposable();
    private TorrentFileObserver fileObserver;
    private FileSystemFacade fs;
    private DownloadsCompletedListener downloadsCompleted;
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private SessionErrorFilter errorFilter = new SessionErrorFilter();

    private static volatile TorrentEngine INSTANCE;

    public static TorrentEngine getInstance(@NonNull Context appContext) {
        if (INSTANCE == null) {
            synchronized (TorrentEngine.class) {
                if (INSTANCE == null)
                    INSTANCE = new TorrentEngine(appContext);
            }
        }

        return INSTANCE;
    }

    private TorrentEngine(@NonNull Context appContext) {
        this.appContext = appContext;
        repo = RepositoryHelper.getTorrentRepository(appContext);
        fs = SystemFacadeHelper.getFileSystemFacade(appContext);
        pref = RepositoryHelper.getSettingsRepository(appContext);
        downloadsCompleted = new DownloadsCompletedListener(this);
        session = new TorrentSessionImpl(repo,
                fs,
                SystemFacadeHelper.getSystemFacade(appContext));
        session.setSettings(pref.readSessionSettings(), false);
        session.addListener(engineListener);
    }


    private void handleAutoStop() {
        if (pref.shutdownDownloadsComplete()) {
//            forceStop();
            doStop();
        }
    }

//    public void start()
//    {
//        if (isRunning())
//            return;
//
//        Utils.startServiceBackground(appContext, new Intent(appContext, TorrentService.class));
//    }

//    public void restartForegroundNotification()
//    {
//        Intent i = new Intent(appContext, TorrentService.class);
//        i.setAction(TorrentService.ACTION_RESTART_FOREGROUND_NOTIFICATION);
//        Utils.startServiceBackground(appContext, i);
//    }

    public Flowable<Boolean> observeNeedStartEngine() {
        return Flowable.create((emitter) -> {
            if (emitter.isCancelled())
                return;

            Runnable emitLoop = () -> {
                while (!Thread.interrupted()) {
                    try {
                        Thread.sleep(1000);

                    } catch (InterruptedException e) {
                        return;
                    }

                    if (emitter.isCancelled() || isRunning())
                        return;

                    emitter.onNext(true);
                }
            };

            Disposable d = observeEngineRunning()
                    .subscribeOn(Schedulers.io())
                    .subscribe((isRunning) -> {
                        if (emitter.isCancelled())
                            return;

                        if (!isRunning) {
                            emitter.onNext(true);
                            exec.submit(emitLoop);
                        }
                    });

            if (!emitter.isCancelled()) {
                emitter.onNext(!isRunning());
                emitter.setDisposable(d);
            }

        }, BackpressureStrategy.LATEST);
    }

    public Flowable<Boolean> observeEngineRunning() {
        return Flowable.create((emitter) -> {
            if (emitter.isCancelled())
                return;

            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onSessionStarted() {
                    if (!emitter.isCancelled())
                        emitter.onNext(true);
                }

                @Override
                public void onSessionStopped() {
                    if (!emitter.isCancelled())
                        emitter.onNext(false);
                }
            };

            if (!emitter.isCancelled()) {
                emitter.onNext(isRunning());
                addListener(listener);
                emitter.setDisposable(Disposables.fromAction(() -> removeListener(listener)));
            }

        }, BackpressureStrategy.LATEST);
    }


    /*
     * Only calls from TorrentService
     */
    public void doStart() {
        if (isRunning())
            return;

//        switchConnectionReceiver();
//        switchPowerReceiver();
//        disposables.add(pref.observeSettingsChanged()
//                .subscribe(this::handleSettingsChanged));
//
        disposables.add(downloadsCompleted.listen()
                .subscribe(
                        this::handleAutoStop,
                        (err) -> {
                            Log.e(TAG, "Auto stop error: ", err);
                            handleAutoStop();
                        }
                ));
//
//        disposables.add(session.getLogger().observeDataSetChanged()
//                .subscribe((change) -> {
//                    if (change.reason == Logger.DataSetChange.Reason.NEW_ENTRIES && change.entries != null)
//                        printSessionLog(change.entries);
//                }));

        session.start();
    }

    /*
     * Leaves the right to the engine to decide whether to shutdown or not
     */

//    public void requestStop()
//    {
//        if (pref.keepAlive())
//            return;
//
//        forceStop();
//    }
//
//    public void forceStop()
//    {
//        Intent i = new Intent(appContext, TorrentService.class);
//        i.setAction(TorrentService.ACTION_SHUTDOWN);
//        Utils.startServiceBackground(appContext, i);
//    }

    public void doStop() {
        if (!isRunning())
            return;

        disposables.clear();
        stopWatchDir();
        stopStreamingServer();
        session.requestStop();
        cleanTemp();
    }

    public boolean isRunning() {
        return session.isRunning();
    }

    public void addListener(TorrentEngineListener listener) {
        session.addListener(listener);
    }

    public void removeListener(TorrentEngineListener listener) {
        session.removeListener(listener);
    }

    public void rescheduleTorrents() {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    if (checkPauseTorrents())
                        session.pauseAll();
                    else
                        session.resumeAll();

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void addTorrent(@NonNull AddTorrentParams params,
                           boolean removeFile) {
        disposables.add(Completable.fromRunnable(() -> {
                    try {
                        addTorrentSync(params, removeFile);

                    } catch (Exception e) {
                        handleAddTorrentError(params.name, e);
                    }
                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void addTorrents(@NonNull List<AddTorrentParams> paramsList,
                            boolean removeFile) {
        if (!isRunning())
            return;

        disposables.add(Observable.fromIterable(paramsList)
                .subscribeOn(Schedulers.io())
                .subscribe((params) -> {
                    try {
                        session.addTorrent(params, removeFile);

                    } catch (Exception e) {
                        handleAddTorrentError(params.name, e);
                    }
                }));
    }

    public void addTorrent(@NonNull Uri file) {
        addTorrent(file, null);
    }

    public void addTorrent(@NonNull Uri file, @Nullable Uri savePath) {
        disposables.add(addTorrentCompletable(file, savePath)
                .subscribeOn(Schedulers.io())
                .subscribe()
        );
    }

    public Completable addTorrentCompletable(@NonNull Uri file) {
        return addTorrentCompletable(file, null);
    }

    public Completable addTorrentCompletable(@NonNull Uri file, @Nullable Uri savePath) {
        return Completable.fromRunnable(() -> {
            if (!isRunning())
                return;

            TorrentMetaInfo info = null;
            try (FileDescriptorWrapper w = fs.getFD(file)) {
                FileDescriptor outFd = w.open("r");

                try (FileInputStream is = new FileInputStream(outFd)) {
                    info = new TorrentMetaInfo(is);

                } catch (Exception e) {
                    throw new DecodeException(e);
                }
                addTorrentSync(file, info, savePath);

            } catch (Exception e) {
                handleAddTorrentError((info == null ? file.getPath() : info.torrentName), e);
            }
        });
    }

    /*
     * Do not run in the UI thread
     */

    public Torrent addTorrentSync(
            @NonNull AddTorrentParams params,
            boolean removeFile
    ) throws
            IOException,
            TorrentAlreadyExistsException,
            DecodeException,
            UnknownUriException {
        if (!isRunning())
            return null;

        return session.addTorrent(params, removeFile);
    }

    public Pair<MagnetInfo, Single<TorrentMetaInfo>> fetchMagnet(@NonNull String uri) throws Exception {
        if (!isRunning())
            return null;

        MagnetInfo info = session.fetchMagnet(uri);
        if (info == null)
            return null;
        Single<TorrentMetaInfo> res = createFetchMagnetSingle(info.getSha1hash());

        return Pair.create(info, res);
    }

    public MagnetInfo parseMagnet(@NonNull String uri) {
        return session.parseMagnet(uri);
    }

    private Single<TorrentMetaInfo> createFetchMagnetSingle(String targetHash) {
        return Single.create((emitter) -> {
            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onMagnetLoaded(@NonNull String hash, byte[] bencode) {
                    if (!targetHash.equals(hash))
                        return;

                    if (!emitter.isDisposed()) {
                        if (bencode == null)
                            emitter.onError(new IOException(new NullPointerException("bencode is null")));
                        else
                            sendInfoToEmitter(emitter, bencode);
                    }
                }
            };
            if (!emitter.isDisposed()) {
                /* Check if metadata is already loaded */
                byte[] bencode = session.getLoadedMagnet(targetHash);
                if (bencode == null) {
                    session.addListener(listener);
                    emitter.setDisposable(Disposables.fromAction(() ->
                            session.removeListener(listener)));
                } else {
                    sendInfoToEmitter(emitter, bencode);
                }
            }
        });
    }

    private void sendInfoToEmitter(SingleEmitter<TorrentMetaInfo> emitter, byte[] bencode) {
        TorrentMetaInfo info;
        try {
            info = new TorrentMetaInfo(bencode);

        } catch (DecodeException e) {
            Log.e(TAG, e);
            if (!emitter.isDisposed())
                emitter.onError(e);
            return;
        }

        if (!emitter.isDisposed())
            emitter.onSuccess(info);
    }

    /*
     * Used only for magnets from the magnetList (non added magnets)
     */

    public void cancelFetchMagnet(@NonNull String infoHash) {
        if (!isRunning())
            return;

        session.cancelFetchMagnet(infoHash);
    }

    public void pauseResumeTorrent(@NonNull String id) {
        disposables.add(Completable.fromRunnable(() -> {
                    TorrentDownload task = session.getTask(id);
                    if (task == null)
                        return;
                    try {
                        if (task.isPaused())
                            task.resumeManually();
                        else
                            task.pauseManually();

                    } catch (Exception e) {
                        /* Ignore */
                    }

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void forceRecheckTorrents(@NonNull List<String> ids) {
        disposables.add(Observable.fromIterable(ids)
                .filter(Objects::nonNull)
                .subscribe((id) -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.forceRecheck();
                }));
    }

    public void resumeIfPausedTorrent(@NonNull String id) {
        disposables.add(Completable.fromRunnable(() -> {
                    TorrentDownload task = session.getTask(id);
                    if (task == null)
                        return;
                    try {
                        if (task.isPaused())
                            task.resumeManually();

                    } catch (Exception e) {
                        /* Ignore */
                    }

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void forceAnnounceTorrents(@NonNull List<String> ids) {
        disposables.add(Observable.fromIterable(ids)
                .filter(Objects::nonNull)
                .subscribe((id) -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.requestTrackerAnnounce();
                }));
    }

    public void deleteTorrents(@NonNull List<String> ids, boolean withFiles) {
        disposables.add(Observable.fromIterable(ids)
                .observeOn(Schedulers.io())
                .subscribe((id) -> {
                    if (!isRunning())
                        return;
                    session.deleteTorrent(id, withFiles);
                }));
    }

    public void deleteTrackers(@NonNull String id, @NonNull List<String> urls) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        Set<String> trackers = task.getTrackersUrl();
        trackers.removeAll(urls);

        task.replaceTrackers(trackers);
    }

    public void replaceTrackers(@NonNull String id, @NonNull List<String> urls) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        task.replaceTrackers(new HashSet<>(urls));
    }

    public void addTrackers(@NonNull String id, @NonNull List<String> urls) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task != null)
            task.addTrackers(new HashSet<>(urls));
    }


    public FileInTorrent getFirstMediaFile(String id) {
        if (!isRunning())
            return null;

        if (id == null)
            return null;

        TorrentDownload task = session.getTask(id);
        if (task != null)
            return task.getFirstMediaFile();

        return null;
    }

    public List<FileInTorrent> getAllMediaFile(String id) {
        if (!isRunning())
            return null;

        if (id == null)
            return null;

        TorrentDownload task = session.getTask(id);
        if (task != null)
            return task.getAllMediaFile();

        return null;
    }

    public boolean torrentHasMediaFile(String id) {
        if (!isRunning())
            return false;

        if (id == null)
            return false;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return false;

        return task.torrentHasMediaFile();
    }

    public boolean isTorrentNeedStartStreamOnAdded(String id) {
        if (!isRunning())
            return false;

        if (id == null)
            return false;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return false;

        return task.isNeedStartStreamOnAdded();
    }

    public String getFileOrStreamUrl(FileInTorrent mediaFile) {
        if (mediaFile == null)
            return null;

        if (mediaFile.getIsDownloaded()) {
            Uri fileUri = FileProvider.getUriForFile(appContext, appContext.getPackageName() + ".provider", mediaFile.getFile());
            return fileUri.toString();
        } else {
            String url = getStreamUrl(mediaFile);

            resumeIfPausedTorrent(mediaFile.getTorrentID());
            return url;
        }
    }

    public String getStreamUrl(FileInTorrent mediaFile) {
        if (mediaFile == null)
            return null;

        String hostname = pref.streamingHostname();
        int port = pref.streamingPort();

        return TorrentStreamServer.makeStreamUrl(hostname, port, mediaFile.getTorrentID(), mediaFile.getIndex());
    }

    public String makeMagnet(@NonNull String id, boolean includePriorities) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.makeMagnet(includePriorities);
    }

    public Flowable<TorrentMetaInfo> observeTorrentMetaInfo(@NonNull String id) {
        return Flowable.create((emitter) -> {
            TorrentEngineListener listener = new TorrentEngineListener() {
                @Override
                public void onTorrentMetadataLoaded(@NonNull String torrentId, Exception err) {
                    if (!id.equals(torrentId) || emitter.isCancelled())
                        return;

                    if (err == null) {
                        TorrentMetaInfo info = getTorrentMetaInfo(id);
                        if (info == null)
                            emitter.onError(new NullPointerException());
                        else
                            emitter.onNext(info);
                    } else {
                        emitter.onError(err);
                    }
                }
            };
            if (!emitter.isCancelled()) {
                TorrentMetaInfo info = getTorrentMetaInfo(id);
                if (info == null)
                    emitter.onError(new NullPointerException());
                else
                    emitter.onNext(info);

                session.addListener(listener);
                emitter.setDisposable(Disposables.fromAction(() ->
                        session.removeListener(listener)));
            }
        }, BackpressureStrategy.LATEST);
    }

    public TorrentMetaInfo getTorrentMetaInfo(@NonNull String id) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        TorrentMetaInfo info = null;
        try {
            info = task.getTorrentMetaInfo();

        } catch (DecodeException e) {
            Log.e(TAG, "Can't decode torrent info: ");
            Log.e(TAG, e);
        }

        return info;
    }

    public boolean[] getPieces(@NonNull String id) {
        if (!isRunning())
            return new boolean[0];

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new boolean[0];

        return task.pieces();
    }

    public void pauseAll() {
        disposables.add(Completable.fromRunnable(() -> {
                    if (isRunning())
                        session.pauseAllManually();

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void resumeAll() {
        disposables.add(Completable.fromRunnable(() -> {
                    if (isRunning())
                        session.resumeAllManually();

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void setTorrentName(@NonNull String id, @NonNull String name) {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.setTorrentName(name);

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void setDownloadPath(@NonNull String id, @NonNull Uri path) {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.setDownloadPath(path);

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void setSequentialDownload(@NonNull String id, boolean sequential) {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.setSequentialDownload(sequential);

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void setFirstLastPiecePriority(@NonNull String id, boolean enabled) {
        disposables.add(Completable.fromRunnable(() -> {
            if (!isRunning()) {
                return;
            }
            var task = session.getTask(id);
            if (task != null) {
                task.setFirstLastPiecePriority(enabled);
            }
        }).subscribeOn(Schedulers.io()).subscribe());
    }

    public boolean isFirstLastPiecePriority(@NonNull String id) {
        if (!isRunning()) {
            return false;
        }

        var task = session.getTask(id);
        if (task == null) {
            return false;
        }

        return task.isFirstLastPiecePriority();
    }

    public void prioritizeFiles(@NonNull String id, @NonNull Priority[] priorities) {
        disposables.add(Completable.fromRunnable(() -> {
                    if (!isRunning())
                        return;

                    TorrentDownload task = session.getTask(id);
                    if (task != null)
                        task.prioritizeFiles(priorities);

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

    public TorrentStream getStream(@NonNull String id, int fileIndex) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.getStream(fileIndex);
    }

    public TorrentInputStream getTorrentInputStream(@NonNull TorrentStream stream) {
        return new TorrentInputStream(session, stream);
    }

    /*
     * Do not run in the UI thread
     */

    public TorrentInfo makeInfoSync(@NonNull String id) {
        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null) {
            return null;
        }

        return makeInfo(torrent);
    }

    private TorrentInfo makeInfo(Torrent torrent) {
        TorrentDownload task = session.getTask(torrent.id);
        if (task == null || !task.isValid() || task.isStopped()) {
            return new TorrentInfo(
                    torrent.id,
                    torrent.name,
                    torrent.dateAdded,
                    torrent.error
            );
        } else {
            return new TorrentInfo(
                    torrent.id,
                    torrent.name,
                    task.getStateCode(),
                    task.getProgress(),
                    task.getReceivedBytes(),
                    task.getTotalSentBytes(),
                    task.getTotalWanted(),
                    task.getDownloadSpeed(),
                    task.getUploadSpeed(),
                    task.getETA(),
                    torrent.dateAdded,
                    task.getTotalPeers(),
                    task.getConnectedPeers(),
                    torrent.error,
                    task.isSequentialDownload(),
                    task.getFilePriorities(),
                    task.isFirstLastPiecePriority()
            );
        }
    }

    /*
     * Do not run in the UI thread
     */

    public List<TorrentInfo> makeInfoListSync() {
        ArrayList<TorrentInfo> stateList = new ArrayList<>();

        for (Torrent torrent : repo.getAllTorrents()) {
            if (torrent == null) {
                continue;
            }
            stateList.add(makeInfo(torrent));
        }

        return stateList;
    }


    /*
     * Do not run in the UI thread
     */

    public AdvancedTorrentInfo makeAdvancedInfoSync(@NonNull String id) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        Torrent torrent = repo.getTorrentById(id);
        if (torrent == null)
            return null;

        int[] piecesAvail = task.getPiecesAvailability();

        return new AdvancedTorrentInfo(
                torrent.id,
                task.getFilesReceivedBytes(),
                task.getTotalSeeds(),
                task.getConnectedSeeds(),
                task.getNumDownloadedPieces(),
                task.getShareRatio(),
                task.getActiveTime(),
                task.getSeedingTime(),
                task.getAvailability(piecesAvail),
                task.getFilesAvailability(piecesAvail),
                task.getConnectedLeechers(),
                task.getTotalLeechers());
    }

    public List<TrackerInfo> makeTrackerInfoList(@NonNull String id) {
        if (!isRunning())
            return new ArrayList<>();

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new ArrayList<>();

        return task.getTrackerInfoList();
    }

    public List<PeerInfo> makePeerInfoList(@NonNull String id) {
        if (!isRunning())
            return new ArrayList<>();

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return new ArrayList<>();

        return task.getPeerInfoList();
    }

    public int getUploadSpeedLimit(@NonNull String id) {
        if (!isRunning())
            return -1;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return -1;

        return task.getUploadSpeedLimit();
    }

    public int getDownloadSpeedLimit(@NonNull String id) {
        if (!isRunning())
            return -1;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return -1;

        return task.getDownloadSpeedLimit();
    }

    public void setDownloadSpeedLimit(@NonNull String id, int limit) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        task.setDownloadSpeedLimit(limit);
    }

    public void setUploadSpeedLimit(@NonNull String id, int limit) {
        if (!isRunning())
            return;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return;

        task.setUploadSpeedLimit(limit);
    }

    public byte[] getBencode(@NonNull String id) {
        if (!isRunning())
            return null;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return null;

        return task.getBencode();
    }

    public boolean isSequentialDownload(@NonNull String id) {
        if (!isRunning())
            return false;

        TorrentDownload task = session.getTask(id);
        if (task == null)
            return false;

        return task.isSequentialDownload();
    }

    public int[] getPieceSizeList() {
        return session.getPieceSizeList();
    }
//
//    public Logger getSessionLogger()
//    {
//        return session.getLogger();
//    }

    private void saveTorrentFileIn(@NonNull Torrent torrent,
                                   @NonNull Uri saveDir) {
        String torrentFileName = torrent.name + ".torrent";
        try {
            if (!saveTorrentFile(torrent.id, saveDir, torrentFileName))
                Log.w(TAG, "Could not save torrent file + " + torrentFileName);

        } catch (Exception e) {
            Log.w(TAG, "Could not save torrent file + " + torrentFileName + ": ", e);
        }
    }

    private boolean saveTorrentFile(String id, Uri destDir, String fileName) throws IOException, UnknownUriException {
        byte[] bencode = getBencode(id);
        if (bencode == null)
            return false;

        String name = (fileName != null ? fileName : id);

        Uri path = fs.createFile(destDir, name, true);
        if (path == null)
            return false;

        fs.write(bencode, path);

        return true;
    }


    private boolean checkPauseTorrents() {
        boolean batteryControl = pref.batteryControl();
        boolean customBatteryControl = pref.customBatteryControl();
        int customBatteryControlValue = pref.customBatteryControlValue();
        boolean onlyCharging = pref.onlyCharging();
        boolean unmeteredOnly = pref.unmeteredConnectionsOnly();
        boolean roaming = pref.enableRoaming();

        boolean stop = false;
        if (roaming)
            stop = Utils.isRoaming(appContext);
        if (unmeteredOnly)
            stop = Utils.isMetered(appContext);
        if (onlyCharging)
            stop |= !Utils.isBatteryCharging(appContext);
        if (customBatteryControl)
            stop |= Utils.isBatteryBelowThreshold(appContext, customBatteryControlValue);
        else if (batteryControl)
            stop |= Utils.isBatteryLow(appContext);

        return stop;
    }

    private void handleOnSessionStarted() {
        if (pref.enableIpFiltering()) {
            String path = pref.ipFilteringFile();
            if (path != null)
                session.enableIpFilter(Uri.parse(path));
        }

        if (pref.watchDir())
            startWatchDir();

        boolean enableStreaming = pref.enableStreaming();
        if (enableStreaming)
            startStreamingServer();

        loadTorrents();
    }

    private void startStreamingServer() {
        stopStreamingServer();

        String hostname = pref.streamingHostname();
        int port = pref.streamingPort();

        Log.i(TAG, "Starting streaming server on " + hostname + ":" + port);
        torrentStreamServer = new TorrentStreamServer(hostname, port);
        try {
            torrentStreamServer.start(appContext);

        } catch (IOException e) {
            Log.e(TAG, e);
        }
    }

    private void stopStreamingServer() {
        if (torrentStreamServer != null)
            torrentStreamServer.stop();
        torrentStreamServer = null;
    }

    private void loadTorrents() {
        disposables.add(Completable.fromRunnable(() -> {
                    if (isRunning())
                        session.restoreTorrents();

                }).subscribeOn(Schedulers.io())
                .subscribe());
    }

//    private void setProxy()
//    {
//        SessionSettings s = session.getSettings();
//
//        s.proxyType = SessionSettings.ProxyType.fromValue(pref.proxyType());
//        s.proxyAddress = pref.proxyAddress();
//        s.proxyPort = pref.proxyPort();
//        s.proxyPeersToo = pref.proxyPeersToo();
//        s.proxyRequiresAuth = pref.proxyRequiresAuth();
//        s.proxyLogin = pref.proxyLogin();
//        s.proxyPassword = pref.proxyPassword();
//
//        session.setSettings(s);
//    }

    private SessionSettings.EncryptMode getEncryptMode() {
        return SessionSettings.EncryptMode.fromValue(pref.encryptMode());
    }

    private void startWatchDir() {
        String dir = pref.dirToWatch();
        Uri uri = Uri.parse(dir);
        if (!Utils.isFileSystemPath(uri))
            throw new IllegalArgumentException("SAF is not supported:" + uri);
        dir = uri.getPath();

        scanTorrentsInDir(dir);
        fileObserver = makeTorrentFileObserver(dir);
        fileObserver.startWatching();
    }

    private void stopWatchDir() {
        if (fileObserver == null)
            return;

        fileObserver.stopWatching();
        fileObserver = null;
    }

    private TorrentFileObserver makeTorrentFileObserver(String pathToDir) {
        return new TorrentFileObserver(pathToDir) {
            @Override
            public void onEvent(int event, @Nullable String name) {
                if (name == null)
                    return;

                File f = new File(pathToDir, name);
                if (!f.exists())
                    return;
                if (f.isDirectory() || !f.getName().endsWith(".torrent"))
                    return;
                Uri uri = Uri.fromFile(f);
                disposables.add(addTorrentCompletable(uri)
                                .subscribeOn(Schedulers.io())
                                .subscribe(() -> {
//                            if (pref.watchDirDeleteFile()) {
                                    if (true) {
                                        try {
                                            fs.deleteFile(uri);
                                        } catch (IOException | UnknownUriException e) {
                                            Log.w(TAG, "[Watch] Unable to delete file: "
                                                    + e);
                                        }
                                    }
                                })
                );
            }
        };
    }

    private void scanTorrentsInDir(String pathToDir) {
        File dir = new File(pathToDir);
        if (!dir.exists())
            return;
        for (File file : org.apache.commons.io.FileUtils.listFiles(dir, FileFilterUtils.suffixFileFilter(".torrent"), null)) {
            if (!file.exists())
                continue;
            addTorrent(Uri.fromFile(file));
        }
    }

    private Torrent addTorrentSync(Uri file, TorrentMetaInfo info, Uri savePath)
            throws IOException,
            FreeSpaceException,
            TorrentAlreadyExistsException,
            DecodeException,
            UnknownUriException {
        Priority[] priorities = new Priority[info.fileCount];
        Arrays.fill(priorities, Priority.DEFAULT);
        Uri downloadPath = (savePath == null ? Uri.parse(pref.saveTorrentsIn()) : savePath);

        AddTorrentParams params = new AddTorrentParams(
                file.toString(),
                false,
                info.sha1Hash,
                info.torrentName,
                priorities,
                downloadPath,
                false,
                false,
                false
        );

        if (fs.getDirAvailableBytes(downloadPath) < info.torrentSize) {
            throw new FreeSpaceException();
        }

        return addTorrentSync(params, false);
    }

    private void handleAddTorrentError(String name, Throwable e) {
//        if (e instanceof TorrentAlreadyExistsException) {
//            notifier.makeTorrentInfoNotify(name, appContext.getString(R.string.torrent_exist));
//            return;
//        }
//        Log.e(TAG, e);
//        String message;
//        if (e instanceof FileNotFoundException)
//            message = appContext.getString(R.string.error_file_not_found_add_torrent);
//        else if (e instanceof IOException)
//            message = appContext.getString(R.string.error_io_add_torrent);
//        else
//            message = appContext.getString(R.string.error_add_torrent);
//        notifier.makeTorrentErrorNotify(name, message);
    }

    private void cleanTemp() {
        try {
            fs.cleanTempDir();

        } catch (Exception e) {
            Log.e(TAG, "Error during setup of temp directory: ", e);
        }
    }

    private void setRandomPortRange(boolean useRandomPort) {
        SessionSettings settings = session.getSettings();
        settings.useRandomPort = useRandomPort;
        if (!useRandomPort) {
            int first = SessionSettings.DEFAULT_PORT_RANGE_FIRST;
            int second = SessionSettings.DEFAULT_PORT_RANGE_SECOND;
            if (first != -1 && second != -1) {
                settings.portRangeFirst = first;
                settings.portRangeSecond = second;
            }
        }
        session.setSettings(settings, false);
    }

    private void setPortRange(int first, int second) {
        if (first == -1 || second == -1)
            return;

        SessionSettings settings = session.getSettings();
        settings.portRangeFirst = first;
        settings.portRangeSecond = second;
        session.setSettings(settings, false);
    }

    /*
     * Disable notifications for torrent
     */

    private void markAsHiddenSync(Torrent torrent) {
        torrent.visibility = Torrent.VISIBILITY_HIDDEN;
        repo.updateTorrent(torrent);
    }

    private final TorrentEngineListener engineListener = new TorrentEngineListener() {
        @Override
        public void onSessionStarted() {
            handleOnSessionStarted();
        }

        @Override
        public void onTorrentAdded(@NonNull String id) {
//            if (pref.saveTorrentFiles())
//                saveTorrentFileIn(repo.getTorrentById(id),
//                        Uri.parse(pref.saveTorrentFilesIn()));

            if (checkPauseTorrents()) {
                disposables.add(Completable.fromRunnable(() -> {
                            if (!isRunning())
                                return;
                            TorrentDownload task = session.getTask(id);
                            if (task != null)
                                task.pause();

                        }).subscribeOn(Schedulers.io())
                        .subscribe());
            }
        }

        @Override
        public void onTorrentLoaded(@NonNull String id) {
            if (checkPauseTorrents()) {
                disposables.add(Completable.fromRunnable(() -> {
                            if (!isRunning())
                                return;
                            TorrentDownload task = session.getTask(id);
                            if (task != null)
                                task.pause();

                        }).subscribeOn(Schedulers.io())
                        .subscribe());
            }
        }

        @Override
        public void onTorrentFinished(@NonNull String id) {
//            disposables.add(repo.getTorrentByIdSingle(id)
//                    .subscribeOn(Schedulers.io())
//                    .filter(Objects::nonNull)
//                    .subscribe((torrent) -> {
//                                notifier.makeTorrentFinishedNotify(torrent);
//                                if (torrent.visibility != Torrent.VISIBILITY_HIDDEN)
//                                    markAsHiddenSync(torrent);
//
//                                if (pref.moveAfterDownload()) {
//                                    String curPath = torrent.downloadPath.toString();
//                                    String newPath = pref.moveAfterDownloadIn();
//
//                                    if (!curPath.equals(newPath))
//                                        setDownloadPath(id, Uri.parse(newPath));
//                                }
//                            },
//                            (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
//                                    e))
//            );
        }

        @Override
        public void onTorrentMoving(@NonNull String id) {
//            disposables.add(repo.getTorrentByIdSingle(id)
//                    .subscribeOn(Schedulers.io())
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe((torrent) -> {
//                                String name;
//                                if (torrent == null)
//                                    name = id;
//                                else
//                                    name = torrent.name;
//
//                                notifier.makeMovingTorrentNotify(name);
//                            },
//                            (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
//                                    e))
//            );
        }

        @Override
        public void onTorrentMoved(@NonNull String id, boolean success) {
//            disposables.add(repo.getTorrentByIdSingle(id)
//                    .subscribeOn(Schedulers.io())
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe((torrent) -> {
//                                String name;
//                                if (torrent == null)
//                                    name = id;
//                                else
//                                    name = torrent.name;
//
//                                if (success)
//                                    notifier.makeTorrentInfoNotify(name,
//                                            appContext.getString(R.string.torrent_move_success));
//                                else
//                                    notifier.makeTorrentErrorNotify(name,
//                                            appContext.getString(R.string.torrent_move_fail));
//                            },
//                            (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
//                                    e))
//            );
        }

        @Override
        public void onIpFilterParsed(int ruleCount) {
//            disposables.add(Completable.fromRunnable(() -> Toast.makeText(appContext,
//                    (ruleCount > 0 ?
//                            appContext.getString(R.string.ip_filter_add_success) :
//                            appContext.getString(R.string.ip_filter_add_error, ruleCount)),
//                    Toast.LENGTH_LONG)
//                    .show())
//                    .subscribeOn(AndroidSchedulers.mainThread())
//                    .subscribe()
//            );
        }

        @Override
        public void onSessionError(@NonNull String errorMsg) {
//            if (errorFilter.skip(errorMsg)) {
//                return;
//            }
//            notifier.makeSessionErrorNotify(errorMsg);
        }

        @Override
        public void onNatError(@NonNull String errorMsg) {
//            Log.e(TAG, "NAT error: " + errorMsg);
//            if (pref.showNatErrors())
//                notifier.makeNatErrorNotify(errorMsg);
        }

        @Override
        public void onRestoreSessionError(@NonNull String id) {
//            disposables.add(repo.getTorrentByIdSingle(id)
//                    .subscribeOn(Schedulers.io())
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe((torrent) -> {
//                                String name;
//                                if (torrent == null)
//                                    name = id;
//                                else
//                                    name = torrent.name;
//
//                                notifier.makeTorrentErrorNotify(name,
//                                        appContext.getString(R.string.restore_torrent_error));
//                            },
//                            (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
//                                    e))
//            );
        }

        @Override
        public void onTorrentMetadataLoaded(@NonNull String id, Exception err) {
            if (err != null) {
                Log.e(TAG, "Load metadata error: ");
                Log.e(TAG, err);
            }

            disposables.add(repo.getTorrentByIdSingle(id)
                            .subscribeOn(Schedulers.io())
                            .filter(Objects::nonNull)
                            .subscribe((torrent) -> {
//                                if (err == null) {
//                                    if (pref.saveTorrentFiles())
//                                        saveTorrentFileIn(torrent, Uri.parse(pref.saveTorrentFilesIn()));
//
//                                } else if (err instanceof FreeSpaceException) {
//                                    notifier.makeTorrentErrorNotify(torrent.name, appContext.getString(R.string.error_free_space));
//                                }
                                    },
                                    (Throwable t) -> Log.e(TAG, "Getting torrent " + id + " error: " +
                                            t))
            );

            if (checkPauseTorrents()) {
                disposables.add(Completable.fromRunnable(() -> {
                            if (!isRunning())
                                return;
                            TorrentDownload task = session.getTask(id);
                            if (task != null)
                                task.pause();

                        }).subscribeOn(Schedulers.io())
                        .subscribe());
            }
        }
    };
}
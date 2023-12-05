/*
 * Copyright (C) 2018-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.xm.cine.torrent.addtorrent;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableField;

import java.util.concurrent.atomic.AtomicReference;

public class AddTorrentMutableParams {
    /* File path or magnet link */
    private String url;
    private String source;
    private boolean fromMagnet;
    private String name;
    private ObservableField<Uri> dirPath = new ObservableField<>();
    private String dirName;
    private long storageFreeSpace = -1;
    private boolean sequentialDownload = false;
    private boolean startAfterAdd = true;
    private boolean ignoreFreeSpace = false;
    private boolean firstLastPiecePriority = false;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isFromMagnet() {
        return fromMagnet;
    }

    public void setFromMagnet(boolean fromMagnet) {
        this.fromMagnet = fromMagnet;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ObservableField<Uri> getDirPath() {
        return dirPath;
    }

    public String getDirName() {
        return dirName;
    }

    public void setDirName(String dirName) {
        this.dirName = dirName;
    }

    public long getStorageFreeSpace() {
        return storageFreeSpace;
    }

    public void setStorageFreeSpace(long storageFreeSpace) {
        this.storageFreeSpace = storageFreeSpace;
    }

    public boolean isSequentialDownload() {
        return sequentialDownload;
    }

    public void setSequentialDownload(boolean sequentialDownload) {
        this.sequentialDownload = sequentialDownload;
    }

    public boolean isStartAfterAdd() {
        return startAfterAdd;
    }

    public void setStartAfterAdd(boolean startAfterAdd) {
        this.startAfterAdd = startAfterAdd;
    }

    public boolean isIgnoreFreeSpace() {
        return ignoreFreeSpace;
    }

    public void setIgnoreFreeSpace(boolean ignoreFreeSpace) {
        this.ignoreFreeSpace = ignoreFreeSpace;
    }

    public boolean isFirstLastPiecePriority() {
        return firstLastPiecePriority;
    }

    public void setFirstLastPiecePriority(boolean firstLastPiecePriority) {
        this.firstLastPiecePriority = firstLastPiecePriority;
    }

    @NonNull
    @Override
    public String toString() {
        return "AddTorrentMutableParams{" +
                "source='" + source + '\'' +
                ", fromMagnet=" + fromMagnet +
                ", name='" + name + '\'' +
                ", dirPath=" + dirPath +
                ", dirName='" + dirName + '\'' +
                ", storageFreeSpace=" + storageFreeSpace +
                ", sequentialDownload=" + sequentialDownload +
                ", startAfterAdd=" + startAfterAdd +
                ", ignoreFreeSpace=" + ignoreFreeSpace +
                ", firstLastPiecePriority=" + firstLastPiecePriority +
                '}';
    }
}

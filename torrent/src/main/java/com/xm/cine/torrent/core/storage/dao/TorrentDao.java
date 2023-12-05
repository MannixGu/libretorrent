/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package com.xm.cine.torrent.core.storage.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.xm.cine.torrent.core.model.data.entity.Torrent;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

@Dao
public abstract class TorrentDao {
    @Insert
    public abstract void add(Torrent torrent);

    @Update
    public abstract void update(Torrent torrent);

    @Delete
    public abstract void delete(Torrent torrent);

    @Query("SELECT * FROM Torrent")
    public abstract List<Torrent> getAllTorrents();

    @Query("SELECT * FROM Torrent WHERE id = :id")
    public abstract Torrent getTorrentById(String id);

    @Query("SELECT * FROM Torrent WHERE id = :id")
    public abstract Single<Torrent> getTorrentByIdSingle(String id);

    @Query("SELECT * FROM Torrent WHERE id = :id")
    public abstract Flowable<Torrent> observeTorrentById(String id);

}

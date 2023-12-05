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

package com.xm.cine.torrent.core.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.xm.cine.torrent.core.model.data.entity.FastResume;
import com.xm.cine.torrent.core.model.data.entity.Torrent;
import com.xm.cine.torrent.core.storage.converter.UriConverter;
import com.xm.cine.torrent.core.storage.dao.FastResumeDao;
import com.xm.cine.torrent.core.storage.dao.TorrentDao;

@Database(
        entities = {
                Torrent.class,
                FastResume.class
        },
        version = 9,
        exportSchema = false
)
@TypeConverters({UriConverter.class})

public abstract class AppDatabase extends RoomDatabase
{
    private static final String DATABASE_NAME = "libretorrent.db";

    private static volatile AppDatabase INSTANCE;

    public abstract TorrentDao torrentDao();

    public abstract FastResumeDao fastResumeDao();

    public static AppDatabase getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null)
                    INSTANCE = buildDatabase(appContext);
            }
        }

        return INSTANCE;
    }

    private static AppDatabase buildDatabase(Context appContext)
    {
        return Room.databaseBuilder(appContext, AppDatabase.class, DATABASE_NAME)
                .addMigrations(DatabaseMigration.getMigrations(appContext))
                .build();
    }
}
package com.xm.cine.torrent.core.storage.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.xm.cine.torrent.core.model.data.entity.FastResume;

@Dao
public interface FastResumeDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void add(FastResume fastResume);

    @Query("SELECT * FROM FastResume WHERE torrentId = :torrentId")
    FastResume getByTorrentId(String torrentId);
}

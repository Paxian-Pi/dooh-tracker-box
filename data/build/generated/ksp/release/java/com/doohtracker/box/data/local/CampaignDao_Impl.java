package com.doohtracker.box.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CampaignDao_Impl implements CampaignDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CampaignEntity> __insertionAdapterOfCampaignEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteExpiredCampaigns;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  private final SharedSQLiteStatement __preparedStmtOfIncrementSyncedCount;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLastHourlyTrackedAt;

  public CampaignDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCampaignEntity = new EntityInsertionAdapter<CampaignEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `campaigns` (`id`,`name`,`campaign`,`campaignTag`,`user`,`startTime`,`endTime`,`mediaUrl`,`syncedCount`,`lastSyncedAt`,`lastHourlyTrackedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CampaignEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getCampaign());
        statement.bindString(4, entity.getCampaignTag());
        statement.bindString(5, entity.getUser());
        statement.bindLong(6, entity.getStartTime());
        statement.bindLong(7, entity.getEndTime());
        if (entity.getMediaUrl() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getMediaUrl());
        }
        statement.bindLong(9, entity.getSyncedCount());
        statement.bindLong(10, entity.getLastSyncedAt());
        statement.bindLong(11, entity.getLastHourlyTrackedAt());
      }
    };
    this.__preparedStmtOfDeleteExpiredCampaigns = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM campaigns WHERE endTime < ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM campaigns";
        return _query;
      }
    };
    this.__preparedStmtOfIncrementSyncedCount = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE campaigns SET syncedCount = syncedCount + 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateLastHourlyTrackedAt = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE campaigns SET lastHourlyTrackedAt = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertCampaigns(final List<CampaignEntity> campaigns,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCampaignEntity.insert(campaigns);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteExpiredCampaigns(final long currentTime,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteExpiredCampaigns.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, currentTime);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteExpiredCampaigns.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementSyncedCount(final String campaignId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementSyncedCount.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, campaignId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncrementSyncedCount.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateLastHourlyTrackedAt(final String campaignId, final long time,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateLastHourlyTrackedAt.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, time);
        _argIndex = 2;
        _stmt.bindString(_argIndex, campaignId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateLastHourlyTrackedAt.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CampaignEntity>> getAllCampaigns() {
    final String _sql = "SELECT * FROM campaigns";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"campaigns"}, new Callable<List<CampaignEntity>>() {
      @Override
      @NonNull
      public List<CampaignEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfCampaign = CursorUtil.getColumnIndexOrThrow(_cursor, "campaign");
          final int _cursorIndexOfCampaignTag = CursorUtil.getColumnIndexOrThrow(_cursor, "campaignTag");
          final int _cursorIndexOfUser = CursorUtil.getColumnIndexOrThrow(_cursor, "user");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfSyncedCount = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedCount");
          final int _cursorIndexOfLastSyncedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSyncedAt");
          final int _cursorIndexOfLastHourlyTrackedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastHourlyTrackedAt");
          final List<CampaignEntity> _result = new ArrayList<CampaignEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CampaignEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpCampaign;
            _tmpCampaign = _cursor.getString(_cursorIndexOfCampaign);
            final String _tmpCampaignTag;
            _tmpCampaignTag = _cursor.getString(_cursorIndexOfCampaignTag);
            final String _tmpUser;
            _tmpUser = _cursor.getString(_cursorIndexOfUser);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final long _tmpEndTime;
            _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final int _tmpSyncedCount;
            _tmpSyncedCount = _cursor.getInt(_cursorIndexOfSyncedCount);
            final long _tmpLastSyncedAt;
            _tmpLastSyncedAt = _cursor.getLong(_cursorIndexOfLastSyncedAt);
            final long _tmpLastHourlyTrackedAt;
            _tmpLastHourlyTrackedAt = _cursor.getLong(_cursorIndexOfLastHourlyTrackedAt);
            _item = new CampaignEntity(_tmpId,_tmpName,_tmpCampaign,_tmpCampaignTag,_tmpUser,_tmpStartTime,_tmpEndTime,_tmpMediaUrl,_tmpSyncedCount,_tmpLastSyncedAt,_tmpLastHourlyTrackedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<CampaignEntity>> getActiveCampaigns(final long currentTime) {
    final String _sql = "SELECT * FROM campaigns WHERE startTime <= ? AND endTime >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, currentTime);
    _argIndex = 2;
    _statement.bindLong(_argIndex, currentTime);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"campaigns"}, new Callable<List<CampaignEntity>>() {
      @Override
      @NonNull
      public List<CampaignEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfCampaign = CursorUtil.getColumnIndexOrThrow(_cursor, "campaign");
          final int _cursorIndexOfCampaignTag = CursorUtil.getColumnIndexOrThrow(_cursor, "campaignTag");
          final int _cursorIndexOfUser = CursorUtil.getColumnIndexOrThrow(_cursor, "user");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfSyncedCount = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedCount");
          final int _cursorIndexOfLastSyncedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSyncedAt");
          final int _cursorIndexOfLastHourlyTrackedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastHourlyTrackedAt");
          final List<CampaignEntity> _result = new ArrayList<CampaignEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CampaignEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpCampaign;
            _tmpCampaign = _cursor.getString(_cursorIndexOfCampaign);
            final String _tmpCampaignTag;
            _tmpCampaignTag = _cursor.getString(_cursorIndexOfCampaignTag);
            final String _tmpUser;
            _tmpUser = _cursor.getString(_cursorIndexOfUser);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final long _tmpEndTime;
            _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final int _tmpSyncedCount;
            _tmpSyncedCount = _cursor.getInt(_cursorIndexOfSyncedCount);
            final long _tmpLastSyncedAt;
            _tmpLastSyncedAt = _cursor.getLong(_cursorIndexOfLastSyncedAt);
            final long _tmpLastHourlyTrackedAt;
            _tmpLastHourlyTrackedAt = _cursor.getLong(_cursorIndexOfLastHourlyTrackedAt);
            _item = new CampaignEntity(_tmpId,_tmpName,_tmpCampaign,_tmpCampaignTag,_tmpUser,_tmpStartTime,_tmpEndTime,_tmpMediaUrl,_tmpSyncedCount,_tmpLastSyncedAt,_tmpLastHourlyTrackedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}

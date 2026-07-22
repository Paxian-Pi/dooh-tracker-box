package com.doohtracker.box.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile CampaignDao _campaignDao;

  private volatile TrackingLogDao _trackingLogDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(6) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `campaigns` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `campaign` TEXT NOT NULL, `campaignTag` TEXT NOT NULL, `user` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `mediaUrl` TEXT, `mediaType` TEXT, `localPath` TEXT, `syncedCount` INTEGER NOT NULL, `lastSyncedAt` INTEGER NOT NULL, `lastHourlyTrackedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `tracking_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `campaignId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e704515755edd234844fd176d0e7836a')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `campaigns`");
        db.execSQL("DROP TABLE IF EXISTS `tracking_logs`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsCampaigns = new HashMap<String, TableInfo.Column>(13);
        _columnsCampaigns.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("campaign", new TableInfo.Column("campaign", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("campaignTag", new TableInfo.Column("campaignTag", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("user", new TableInfo.Column("user", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("startTime", new TableInfo.Column("startTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("endTime", new TableInfo.Column("endTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("mediaUrl", new TableInfo.Column("mediaUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("mediaType", new TableInfo.Column("mediaType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("localPath", new TableInfo.Column("localPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("syncedCount", new TableInfo.Column("syncedCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("lastSyncedAt", new TableInfo.Column("lastSyncedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCampaigns.put("lastHourlyTrackedAt", new TableInfo.Column("lastHourlyTrackedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCampaigns = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCampaigns = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCampaigns = new TableInfo("campaigns", _columnsCampaigns, _foreignKeysCampaigns, _indicesCampaigns);
        final TableInfo _existingCampaigns = TableInfo.read(db, "campaigns");
        if (!_infoCampaigns.equals(_existingCampaigns)) {
          return new RoomOpenHelper.ValidationResult(false, "campaigns(com.doohtracker.box.data.local.CampaignEntity).\n"
                  + " Expected:\n" + _infoCampaigns + "\n"
                  + " Found:\n" + _existingCampaigns);
        }
        final HashMap<String, TableInfo.Column> _columnsTrackingLogs = new HashMap<String, TableInfo.Column>(4);
        _columnsTrackingLogs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackingLogs.put("campaignId", new TableInfo.Column("campaignId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackingLogs.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackingLogs.put("isSynced", new TableInfo.Column("isSynced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTrackingLogs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTrackingLogs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTrackingLogs = new TableInfo("tracking_logs", _columnsTrackingLogs, _foreignKeysTrackingLogs, _indicesTrackingLogs);
        final TableInfo _existingTrackingLogs = TableInfo.read(db, "tracking_logs");
        if (!_infoTrackingLogs.equals(_existingTrackingLogs)) {
          return new RoomOpenHelper.ValidationResult(false, "tracking_logs(com.doohtracker.box.data.local.TrackingLogEntity).\n"
                  + " Expected:\n" + _infoTrackingLogs + "\n"
                  + " Found:\n" + _existingTrackingLogs);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "e704515755edd234844fd176d0e7836a", "3b869e4fc5b9c120e2473a8397507e0d");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "campaigns","tracking_logs");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `campaigns`");
      _db.execSQL("DELETE FROM `tracking_logs`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(CampaignDao.class, CampaignDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TrackingLogDao.class, TrackingLogDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public CampaignDao campaignDao() {
    if (_campaignDao != null) {
      return _campaignDao;
    } else {
      synchronized(this) {
        if(_campaignDao == null) {
          _campaignDao = new CampaignDao_Impl(this);
        }
        return _campaignDao;
      }
    }
  }

  @Override
  public TrackingLogDao trackingLogDao() {
    if (_trackingLogDao != null) {
      return _trackingLogDao;
    } else {
      synchronized(this) {
        if(_trackingLogDao == null) {
          _trackingLogDao = new TrackingLogDao_Impl(this);
        }
        return _trackingLogDao;
      }
    }
  }
}

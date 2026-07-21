package com.doohtracker.box.data.di;

import com.doohtracker.box.data.local.AppDatabase;
import com.doohtracker.box.data.local.TrackingLogDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class DataModule_ProvideTrackingLogDaoFactory implements Factory<TrackingLogDao> {
  private final Provider<AppDatabase> databaseProvider;

  public DataModule_ProvideTrackingLogDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public TrackingLogDao get() {
    return provideTrackingLogDao(databaseProvider.get());
  }

  public static DataModule_ProvideTrackingLogDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DataModule_ProvideTrackingLogDaoFactory(databaseProvider);
  }

  public static TrackingLogDao provideTrackingLogDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideTrackingLogDao(database));
  }
}

package com.doohtracker.box.data.di;

import com.doohtracker.box.data.local.AppDatabase;
import com.doohtracker.box.data.local.CampaignDao;
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
public final class DataModule_ProvideCampaignDaoFactory implements Factory<CampaignDao> {
  private final Provider<AppDatabase> databaseProvider;

  public DataModule_ProvideCampaignDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public CampaignDao get() {
    return provideCampaignDao(databaseProvider.get());
  }

  public static DataModule_ProvideCampaignDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DataModule_ProvideCampaignDaoFactory(databaseProvider);
  }

  public static CampaignDao provideCampaignDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideCampaignDao(database));
  }
}

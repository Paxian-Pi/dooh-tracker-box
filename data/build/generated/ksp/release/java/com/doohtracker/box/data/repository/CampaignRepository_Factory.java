package com.doohtracker.box.data.repository;

import com.doohtracker.box.data.local.CampaignDao;
import com.doohtracker.box.data.local.TrackingLogDao;
import com.doohtracker.box.data.manager.DeviceManager;
import com.doohtracker.box.data.remote.CampaignApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class CampaignRepository_Factory implements Factory<CampaignRepository> {
  private final Provider<CampaignApi> apiProvider;

  private final Provider<CampaignDao> campaignDaoProvider;

  private final Provider<TrackingLogDao> trackingLogDaoProvider;

  private final Provider<DeviceManager> deviceManagerProvider;

  public CampaignRepository_Factory(Provider<CampaignApi> apiProvider,
      Provider<CampaignDao> campaignDaoProvider, Provider<TrackingLogDao> trackingLogDaoProvider,
      Provider<DeviceManager> deviceManagerProvider) {
    this.apiProvider = apiProvider;
    this.campaignDaoProvider = campaignDaoProvider;
    this.trackingLogDaoProvider = trackingLogDaoProvider;
    this.deviceManagerProvider = deviceManagerProvider;
  }

  @Override
  public CampaignRepository get() {
    return newInstance(apiProvider.get(), campaignDaoProvider.get(), trackingLogDaoProvider.get(), deviceManagerProvider.get());
  }

  public static CampaignRepository_Factory create(Provider<CampaignApi> apiProvider,
      Provider<CampaignDao> campaignDaoProvider, Provider<TrackingLogDao> trackingLogDaoProvider,
      Provider<DeviceManager> deviceManagerProvider) {
    return new CampaignRepository_Factory(apiProvider, campaignDaoProvider, trackingLogDaoProvider, deviceManagerProvider);
  }

  public static CampaignRepository newInstance(CampaignApi api, CampaignDao campaignDao,
      TrackingLogDao trackingLogDao, DeviceManager deviceManager) {
    return new CampaignRepository(api, campaignDao, trackingLogDao, deviceManager);
  }
}

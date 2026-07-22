package com.doohtracker.box.data.repository;

import com.doohtracker.box.data.hardware.UsbConnectionMonitor;
import com.doohtracker.box.data.local.CampaignDao;
import com.doohtracker.box.data.local.TrackingLogDao;
import com.doohtracker.box.data.manager.DeviceManager;
import com.doohtracker.box.data.remote.CampaignApi;
import com.doohtracker.box.data.remote.MediaDownloader;
import com.doohtracker.box.data.time.TimeProvider;
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

  private final Provider<UsbConnectionMonitor> usbMonitorProvider;

  private final Provider<MediaDownloader> mediaDownloaderProvider;

  private final Provider<TimeProvider> timeProvider;

  public CampaignRepository_Factory(Provider<CampaignApi> apiProvider,
      Provider<CampaignDao> campaignDaoProvider, Provider<TrackingLogDao> trackingLogDaoProvider,
      Provider<DeviceManager> deviceManagerProvider,
      Provider<UsbConnectionMonitor> usbMonitorProvider,
      Provider<MediaDownloader> mediaDownloaderProvider, Provider<TimeProvider> timeProvider) {
    this.apiProvider = apiProvider;
    this.campaignDaoProvider = campaignDaoProvider;
    this.trackingLogDaoProvider = trackingLogDaoProvider;
    this.deviceManagerProvider = deviceManagerProvider;
    this.usbMonitorProvider = usbMonitorProvider;
    this.mediaDownloaderProvider = mediaDownloaderProvider;
    this.timeProvider = timeProvider;
  }

  @Override
  public CampaignRepository get() {
    return newInstance(apiProvider.get(), campaignDaoProvider.get(), trackingLogDaoProvider.get(), deviceManagerProvider.get(), usbMonitorProvider.get(), mediaDownloaderProvider.get(), timeProvider.get());
  }

  public static CampaignRepository_Factory create(Provider<CampaignApi> apiProvider,
      Provider<CampaignDao> campaignDaoProvider, Provider<TrackingLogDao> trackingLogDaoProvider,
      Provider<DeviceManager> deviceManagerProvider,
      Provider<UsbConnectionMonitor> usbMonitorProvider,
      Provider<MediaDownloader> mediaDownloaderProvider, Provider<TimeProvider> timeProvider) {
    return new CampaignRepository_Factory(apiProvider, campaignDaoProvider, trackingLogDaoProvider, deviceManagerProvider, usbMonitorProvider, mediaDownloaderProvider, timeProvider);
  }

  public static CampaignRepository newInstance(CampaignApi api, CampaignDao campaignDao,
      TrackingLogDao trackingLogDao, DeviceManager deviceManager, UsbConnectionMonitor usbMonitor,
      MediaDownloader mediaDownloader, TimeProvider timeProvider) {
    return new CampaignRepository(api, campaignDao, trackingLogDao, deviceManager, usbMonitor, mediaDownloader, timeProvider);
  }
}

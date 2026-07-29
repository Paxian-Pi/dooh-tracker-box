package com.doohtracker.box.data.hardware;

import com.doohtracker.box.data.repository.CampaignRepository;
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
public final class HeartbeatServer_Factory implements Factory<HeartbeatServer> {
  private final Provider<SignalMonitor> signalMonitorProvider;

  private final Provider<CampaignRepository> campaignRepositoryProvider;

  public HeartbeatServer_Factory(Provider<SignalMonitor> signalMonitorProvider,
      Provider<CampaignRepository> campaignRepositoryProvider) {
    this.signalMonitorProvider = signalMonitorProvider;
    this.campaignRepositoryProvider = campaignRepositoryProvider;
  }

  @Override
  public HeartbeatServer get() {
    return newInstance(signalMonitorProvider.get(), campaignRepositoryProvider.get());
  }

  public static HeartbeatServer_Factory create(Provider<SignalMonitor> signalMonitorProvider,
      Provider<CampaignRepository> campaignRepositoryProvider) {
    return new HeartbeatServer_Factory(signalMonitorProvider, campaignRepositoryProvider);
  }

  public static HeartbeatServer newInstance(SignalMonitor signalMonitor,
      CampaignRepository campaignRepository) {
    return new HeartbeatServer(signalMonitor, campaignRepository);
  }
}

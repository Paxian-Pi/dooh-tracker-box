package com.doohtracker.box.data.logic;

import com.doohtracker.box.data.hardware.UsbConnectionMonitor;
import com.doohtracker.box.data.repository.CampaignRepository;
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
public final class VerificationGatekeeper_Factory implements Factory<VerificationGatekeeper> {
  private final Provider<CampaignRepository> campaignRepositoryProvider;

  private final Provider<UsbConnectionMonitor> usbConnectionMonitorProvider;

  private final Provider<TimeProvider> timeProvider;

  public VerificationGatekeeper_Factory(Provider<CampaignRepository> campaignRepositoryProvider,
      Provider<UsbConnectionMonitor> usbConnectionMonitorProvider,
      Provider<TimeProvider> timeProvider) {
    this.campaignRepositoryProvider = campaignRepositoryProvider;
    this.usbConnectionMonitorProvider = usbConnectionMonitorProvider;
    this.timeProvider = timeProvider;
  }

  @Override
  public VerificationGatekeeper get() {
    return newInstance(campaignRepositoryProvider.get(), usbConnectionMonitorProvider.get(), timeProvider.get());
  }

  public static VerificationGatekeeper_Factory create(
      Provider<CampaignRepository> campaignRepositoryProvider,
      Provider<UsbConnectionMonitor> usbConnectionMonitorProvider,
      Provider<TimeProvider> timeProvider) {
    return new VerificationGatekeeper_Factory(campaignRepositoryProvider, usbConnectionMonitorProvider, timeProvider);
  }

  public static VerificationGatekeeper newInstance(CampaignRepository campaignRepository,
      UsbConnectionMonitor usbConnectionMonitor, TimeProvider timeProvider) {
    return new VerificationGatekeeper(campaignRepository, usbConnectionMonitor, timeProvider);
  }
}

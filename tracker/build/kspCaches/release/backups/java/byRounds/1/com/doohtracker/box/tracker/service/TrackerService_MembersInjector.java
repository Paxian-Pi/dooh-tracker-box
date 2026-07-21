package com.doohtracker.box.tracker.service;

import com.doohtracker.box.data.location.LocationMonitor;
import com.doohtracker.box.data.logic.VerificationGatekeeper;
import com.doohtracker.box.data.repository.CampaignRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class TrackerService_MembersInjector implements MembersInjector<TrackerService> {
  private final Provider<VerificationGatekeeper> gatekeeperProvider;

  private final Provider<CampaignRepository> campaignRepositoryProvider;

  private final Provider<LocationMonitor> locationMonitorProvider;

  public TrackerService_MembersInjector(Provider<VerificationGatekeeper> gatekeeperProvider,
      Provider<CampaignRepository> campaignRepositoryProvider,
      Provider<LocationMonitor> locationMonitorProvider) {
    this.gatekeeperProvider = gatekeeperProvider;
    this.campaignRepositoryProvider = campaignRepositoryProvider;
    this.locationMonitorProvider = locationMonitorProvider;
  }

  public static MembersInjector<TrackerService> create(
      Provider<VerificationGatekeeper> gatekeeperProvider,
      Provider<CampaignRepository> campaignRepositoryProvider,
      Provider<LocationMonitor> locationMonitorProvider) {
    return new TrackerService_MembersInjector(gatekeeperProvider, campaignRepositoryProvider, locationMonitorProvider);
  }

  @Override
  public void injectMembers(TrackerService instance) {
    injectGatekeeper(instance, gatekeeperProvider.get());
    injectCampaignRepository(instance, campaignRepositoryProvider.get());
    injectLocationMonitor(instance, locationMonitorProvider.get());
  }

  @InjectedFieldSignature("com.doohtracker.box.tracker.service.TrackerService.gatekeeper")
  public static void injectGatekeeper(TrackerService instance, VerificationGatekeeper gatekeeper) {
    instance.gatekeeper = gatekeeper;
  }

  @InjectedFieldSignature("com.doohtracker.box.tracker.service.TrackerService.campaignRepository")
  public static void injectCampaignRepository(TrackerService instance,
      CampaignRepository campaignRepository) {
    instance.campaignRepository = campaignRepository;
  }

  @InjectedFieldSignature("com.doohtracker.box.tracker.service.TrackerService.locationMonitor")
  public static void injectLocationMonitor(TrackerService instance,
      LocationMonitor locationMonitor) {
    instance.locationMonitor = locationMonitor;
  }
}

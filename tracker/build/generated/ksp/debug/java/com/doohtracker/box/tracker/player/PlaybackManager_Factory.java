package com.doohtracker.box.tracker.player;

import android.content.Context;
import com.doohtracker.box.data.logic.VerificationGatekeeper;
import com.doohtracker.box.data.repository.CampaignRepository;
import com.doohtracker.box.data.time.TimeProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class PlaybackManager_Factory implements Factory<PlaybackManager> {
  private final Provider<Context> contextProvider;

  private final Provider<CampaignRepository> campaignRepositoryProvider;

  private final Provider<VerificationGatekeeper> gatekeeperProvider;

  private final Provider<TimeProvider> timeProvider;

  public PlaybackManager_Factory(Provider<Context> contextProvider,
      Provider<CampaignRepository> campaignRepositoryProvider,
      Provider<VerificationGatekeeper> gatekeeperProvider, Provider<TimeProvider> timeProvider) {
    this.contextProvider = contextProvider;
    this.campaignRepositoryProvider = campaignRepositoryProvider;
    this.gatekeeperProvider = gatekeeperProvider;
    this.timeProvider = timeProvider;
  }

  @Override
  public PlaybackManager get() {
    return newInstance(contextProvider.get(), campaignRepositoryProvider.get(), gatekeeperProvider.get(), timeProvider.get());
  }

  public static PlaybackManager_Factory create(Provider<Context> contextProvider,
      Provider<CampaignRepository> campaignRepositoryProvider,
      Provider<VerificationGatekeeper> gatekeeperProvider, Provider<TimeProvider> timeProvider) {
    return new PlaybackManager_Factory(contextProvider, campaignRepositoryProvider, gatekeeperProvider, timeProvider);
  }

  public static PlaybackManager newInstance(Context context, CampaignRepository campaignRepository,
      VerificationGatekeeper gatekeeper, TimeProvider timeProvider) {
    return new PlaybackManager(context, campaignRepository, gatekeeper, timeProvider);
  }
}

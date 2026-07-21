package com.doohtracker.box.data.usecase;

import com.doohtracker.box.data.repository.CampaignRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class SyncCampaignsUseCase_Factory implements Factory<SyncCampaignsUseCase> {
  private final Provider<CampaignRepository> repositoryProvider;

  public SyncCampaignsUseCase_Factory(Provider<CampaignRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public SyncCampaignsUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static SyncCampaignsUseCase_Factory create(
      Provider<CampaignRepository> repositoryProvider) {
    return new SyncCampaignsUseCase_Factory(repositoryProvider);
  }

  public static SyncCampaignsUseCase newInstance(CampaignRepository repository) {
    return new SyncCampaignsUseCase(repository);
  }
}

package com.doohtracker.box.data.sync;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.doohtracker.box.data.repository.CampaignRepository;
import dagger.internal.DaggerGenerated;
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
public final class CampaignSyncWorker_Factory {
  private final Provider<CampaignRepository> repositoryProvider;

  public CampaignSyncWorker_Factory(Provider<CampaignRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  public CampaignSyncWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, repositoryProvider.get());
  }

  public static CampaignSyncWorker_Factory create(Provider<CampaignRepository> repositoryProvider) {
    return new CampaignSyncWorker_Factory(repositoryProvider);
  }

  public static CampaignSyncWorker newInstance(Context context, WorkerParameters params,
      CampaignRepository repository) {
    return new CampaignSyncWorker(context, params, repository);
  }
}

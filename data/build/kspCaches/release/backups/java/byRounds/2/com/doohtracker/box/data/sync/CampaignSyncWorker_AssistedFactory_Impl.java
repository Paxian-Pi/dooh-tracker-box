package com.doohtracker.box.data.sync;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class CampaignSyncWorker_AssistedFactory_Impl implements CampaignSyncWorker_AssistedFactory {
  private final CampaignSyncWorker_Factory delegateFactory;

  CampaignSyncWorker_AssistedFactory_Impl(CampaignSyncWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public CampaignSyncWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<CampaignSyncWorker_AssistedFactory> create(
      CampaignSyncWorker_Factory delegateFactory) {
    return InstanceFactory.create(new CampaignSyncWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<CampaignSyncWorker_AssistedFactory> createFactoryProvider(
      CampaignSyncWorker_Factory delegateFactory) {
    return InstanceFactory.create(new CampaignSyncWorker_AssistedFactory_Impl(delegateFactory));
  }
}

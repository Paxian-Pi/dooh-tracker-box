package com.doohtracker.box.data.sync;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = CampaignSyncWorker.class
)
public interface CampaignSyncWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.doohtracker.box.data.sync.CampaignSyncWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(
      CampaignSyncWorker_AssistedFactory factory);
}

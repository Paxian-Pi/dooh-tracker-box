package com.doohtracker.box.data.di;

import com.doohtracker.box.data.remote.CampaignApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class DataModule_ProvideCampaignApiFactory implements Factory<CampaignApi> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  public DataModule_ProvideCampaignApiFactory(Provider<OkHttpClient> okHttpClientProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
  }

  @Override
  public CampaignApi get() {
    return provideCampaignApi(okHttpClientProvider.get());
  }

  public static DataModule_ProvideCampaignApiFactory create(
      Provider<OkHttpClient> okHttpClientProvider) {
    return new DataModule_ProvideCampaignApiFactory(okHttpClientProvider);
  }

  public static CampaignApi provideCampaignApi(OkHttpClient okHttpClient) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideCampaignApi(okHttpClient));
  }
}

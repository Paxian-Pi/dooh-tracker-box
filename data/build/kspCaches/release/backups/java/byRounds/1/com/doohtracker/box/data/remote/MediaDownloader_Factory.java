package com.doohtracker.box.data.remote;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class MediaDownloader_Factory implements Factory<MediaDownloader> {
  private final Provider<Context> contextProvider;

  private final Provider<OkHttpClient> okHttpClientProvider;

  public MediaDownloader_Factory(Provider<Context> contextProvider,
      Provider<OkHttpClient> okHttpClientProvider) {
    this.contextProvider = contextProvider;
    this.okHttpClientProvider = okHttpClientProvider;
  }

  @Override
  public MediaDownloader get() {
    return newInstance(contextProvider.get(), okHttpClientProvider.get());
  }

  public static MediaDownloader_Factory create(Provider<Context> contextProvider,
      Provider<OkHttpClient> okHttpClientProvider) {
    return new MediaDownloader_Factory(contextProvider, okHttpClientProvider);
  }

  public static MediaDownloader newInstance(Context context, OkHttpClient okHttpClient) {
    return new MediaDownloader(context, okHttpClient);
  }
}

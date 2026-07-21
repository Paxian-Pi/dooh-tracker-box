package com.doohtracker.box.data.sync;

import android.content.Context;
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
public final class SyncManager_Factory implements Factory<SyncManager> {
  private final Provider<Context> contextProvider;

  public SyncManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SyncManager get() {
    return newInstance(contextProvider.get());
  }

  public static SyncManager_Factory create(Provider<Context> contextProvider) {
    return new SyncManager_Factory(contextProvider);
  }

  public static SyncManager newInstance(Context context) {
    return new SyncManager(context);
  }
}

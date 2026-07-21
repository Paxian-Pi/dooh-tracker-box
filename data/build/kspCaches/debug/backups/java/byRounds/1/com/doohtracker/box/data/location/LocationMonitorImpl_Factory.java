package com.doohtracker.box.data.location;

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
public final class LocationMonitorImpl_Factory implements Factory<LocationMonitorImpl> {
  private final Provider<Context> contextProvider;

  public LocationMonitorImpl_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public LocationMonitorImpl get() {
    return newInstance(contextProvider.get());
  }

  public static LocationMonitorImpl_Factory create(Provider<Context> contextProvider) {
    return new LocationMonitorImpl_Factory(contextProvider);
  }

  public static LocationMonitorImpl newInstance(Context context) {
    return new LocationMonitorImpl(context);
  }
}

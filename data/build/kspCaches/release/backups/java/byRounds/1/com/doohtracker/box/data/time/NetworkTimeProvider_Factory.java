package com.doohtracker.box.data.time;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class NetworkTimeProvider_Factory implements Factory<NetworkTimeProvider> {
  @Override
  public NetworkTimeProvider get() {
    return newInstance();
  }

  public static NetworkTimeProvider_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NetworkTimeProvider newInstance() {
    return new NetworkTimeProvider();
  }

  private static final class InstanceHolder {
    private static final NetworkTimeProvider_Factory INSTANCE = new NetworkTimeProvider_Factory();
  }
}

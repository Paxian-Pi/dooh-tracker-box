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
public final class SystemTimeProvider_Factory implements Factory<SystemTimeProvider> {
  @Override
  public SystemTimeProvider get() {
    return newInstance();
  }

  public static SystemTimeProvider_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SystemTimeProvider newInstance() {
    return new SystemTimeProvider();
  }

  private static final class InstanceHolder {
    private static final SystemTimeProvider_Factory INSTANCE = new SystemTimeProvider_Factory();
  }
}

package com.doohtracker.box.data.manager;

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
public final class DeviceManager_Factory implements Factory<DeviceManager> {
  private final Provider<Context> contextProvider;

  public DeviceManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DeviceManager get() {
    return newInstance(contextProvider.get());
  }

  public static DeviceManager_Factory create(Provider<Context> contextProvider) {
    return new DeviceManager_Factory(contextProvider);
  }

  public static DeviceManager newInstance(Context context) {
    return new DeviceManager(context);
  }
}

package com.doohtracker.box.data.hardware;

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
public final class UsbConnectionMonitorImpl_Factory implements Factory<UsbConnectionMonitorImpl> {
  private final Provider<Context> contextProvider;

  public UsbConnectionMonitorImpl_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public UsbConnectionMonitorImpl get() {
    return newInstance(contextProvider.get());
  }

  public static UsbConnectionMonitorImpl_Factory create(Provider<Context> contextProvider) {
    return new UsbConnectionMonitorImpl_Factory(contextProvider);
  }

  public static UsbConnectionMonitorImpl newInstance(Context context) {
    return new UsbConnectionMonitorImpl(context);
  }
}

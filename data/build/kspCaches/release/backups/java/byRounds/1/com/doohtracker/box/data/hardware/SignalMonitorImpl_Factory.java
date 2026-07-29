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
public final class SignalMonitorImpl_Factory implements Factory<SignalMonitorImpl> {
  private final Provider<Context> contextProvider;

  private final Provider<UsbConnectionMonitor> usbConnectionMonitorProvider;

  public SignalMonitorImpl_Factory(Provider<Context> contextProvider,
      Provider<UsbConnectionMonitor> usbConnectionMonitorProvider) {
    this.contextProvider = contextProvider;
    this.usbConnectionMonitorProvider = usbConnectionMonitorProvider;
  }

  @Override
  public SignalMonitorImpl get() {
    return newInstance(contextProvider.get(), usbConnectionMonitorProvider.get());
  }

  public static SignalMonitorImpl_Factory create(Provider<Context> contextProvider,
      Provider<UsbConnectionMonitor> usbConnectionMonitorProvider) {
    return new SignalMonitorImpl_Factory(contextProvider, usbConnectionMonitorProvider);
  }

  public static SignalMonitorImpl newInstance(Context context,
      UsbConnectionMonitor usbConnectionMonitor) {
    return new SignalMonitorImpl(context, usbConnectionMonitor);
  }
}

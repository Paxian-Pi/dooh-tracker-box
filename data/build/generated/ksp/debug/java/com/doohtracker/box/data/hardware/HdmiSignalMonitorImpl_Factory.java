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
public final class HdmiSignalMonitorImpl_Factory implements Factory<HdmiSignalMonitorImpl> {
  private final Provider<Context> contextProvider;

  private final Provider<UsbConnectionMonitor> usbConnectionMonitorProvider;

  public HdmiSignalMonitorImpl_Factory(Provider<Context> contextProvider,
      Provider<UsbConnectionMonitor> usbConnectionMonitorProvider) {
    this.contextProvider = contextProvider;
    this.usbConnectionMonitorProvider = usbConnectionMonitorProvider;
  }

  @Override
  public HdmiSignalMonitorImpl get() {
    return newInstance(contextProvider.get(), usbConnectionMonitorProvider.get());
  }

  public static HdmiSignalMonitorImpl_Factory create(Provider<Context> contextProvider,
      Provider<UsbConnectionMonitor> usbConnectionMonitorProvider) {
    return new HdmiSignalMonitorImpl_Factory(contextProvider, usbConnectionMonitorProvider);
  }

  public static HdmiSignalMonitorImpl newInstance(Context context,
      UsbConnectionMonitor usbConnectionMonitor) {
    return new HdmiSignalMonitorImpl(context, usbConnectionMonitor);
  }
}

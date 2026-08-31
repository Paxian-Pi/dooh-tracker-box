# App Optimization for Google Play

This plan aims to address the "Low" app optimization score reported by Google Play Console by implementing industry-standard performance and size optimizations.

## User Review Required

> [!IMPORTANT]
> Enabling R8 minification and obfuscation can sometimes break code that relies heavily on reflection (like some JSON serializers or DI frameworks). While Hilt and Retrofit are generally well-supported, we should verify the app's functionality after these changes.

## Proposed Changes

### Build Configuration

#### [MODIFY] [app/build.gradle.kts](file:///Users/pax/Documents/app/DoohTrackerBox/app/build.gradle.kts)
- Enable `isMinifyEnabled` and `isShrinkResources` for release builds.
- Add `androidx.profileinstaller` to dependencies.

#### [MODIFY] [tracker/build.gradle.kts](file:///Users/pax/Documents/app/DoohTrackerBox/tracker/build.gradle.kts)
#### [MODIFY] [data/build.gradle.kts](file:///Users/pax/Documents/app/DoohTrackerBox/data/build.gradle.kts)
- Enable `isMinifyEnabled` for release builds in library modules.

#### [MODIFY] [libs.versions.toml](file:///Users/pax/Documents/app/DoohTrackerBox/gradle/libs.versions.toml)
- Add `androidx-profileinstaller` library definition.

### Resource & Performance Optimization

#### [NEW] [proguard-rules.pro](file:///Users/pax/Documents/app/DoohTrackerBox/app/proguard-rules.pro)
- Create a production-ready ProGuard configuration to ensure Hilt, Room, and Retrofit work correctly with minification.

#### [MODIFY] [CampaignRepository.kt](file:///Users/pax/Documents/app/DoohTrackerBox/data/src/main/java/com/doohtracker/box/data/repository/CampaignRepository.kt)
- Minor optimization: Cache the list of campaigns instead of querying the database every 2 seconds in the tracking loop.

---

## Verification Plan

### Automated Tests
- Run `gradle :app:assembleRelease` to ensure the build completes with minification enabled.
- Verify APK size reduction compared to the previous version.

### Manual Verification
- Deploy the release build to a device.
- Verify that Site Activation, Tracking, and Cloud Sync still work (ensures ProGuard didn't strip essential code).
- Check Logcat for any `ClassNotFoundException` or JSON parsing errors.

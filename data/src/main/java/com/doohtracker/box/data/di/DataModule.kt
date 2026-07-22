package com.doohtracker.box.data.di

import android.content.Context
import androidx.room.Room
import com.doohtracker.box.data.local.AppDatabase
import com.doohtracker.box.data.local.CampaignDao
import com.doohtracker.box.data.local.TrackingLogDao
import com.doohtracker.box.data.remote.CampaignApi
import com.doohtracker.box.data.util.STATIC_BEARER_TOKEN
import com.doohtracker.box.data.util.baseUrl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "dooh_tracker.db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCampaignDao(database: AppDatabase): CampaignDao {
        return database.campaignDao()
    }

    @Provides
    fun provideTrackingLogDao(database: AppDatabase): TrackingLogDao {
        return database.trackingLogDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $STATIC_BEARER_TOKEN")
                .build()
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideCampaignApi(okHttpClient: OkHttpClient): CampaignApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CampaignApi::class.java)
    }
}

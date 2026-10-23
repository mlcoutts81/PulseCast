package com.example.data.rss

import com.example.data.models.ItunesSearchResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesSearchApi {

    @GET("search")
    suspend fun searchPodcasts(
        @Query("term") term: String,
        @Query("media") media: String = "podcast",
        @Query("entity") entity: String = "podcast",
        @Query("limit") limit: Int = 30
    ): ItunesSearchResponse

    companion object {
        private const val BASE_URL = "https://itunes.apple.com/"

        val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "PulseCast/1.0 (Android; MaterialYou Podcast Client)")
                        .header("Accept", "application/json, application/xml, text/xml, */*")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }

        fun create(): ItunesSearchApi {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ItunesSearchApi::class.java)
        }
    }
}

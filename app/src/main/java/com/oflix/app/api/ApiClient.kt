package com.oflix.app.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var retrofit: Retrofit? = null
    private var fallbackRetrofit: Retrofit? = null

    private fun buildHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun getClient(): OflixApiService {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(Secrets.getApiBaseUrl())
                .client(buildHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(OflixApiService::class.java)
    }

    fun getFallbackClient(): OflixApiService {
        if (fallbackRetrofit == null) {
            fallbackRetrofit = Retrofit.Builder()
                .baseUrl("https://netnaija.film/wefeed-h5api-bff/")
                .client(buildHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return fallbackRetrofit!!.create(OflixApiService::class.java)
    }
}

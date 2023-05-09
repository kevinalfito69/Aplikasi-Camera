package com.dicoding.picodiploma.mycamera.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiConfig {
    fun getApiService(): ApiService{
        val logingInterceptor = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        val client = OkHttpClient.Builder()
            .addInterceptor(logingInterceptor)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://story-api.dicoding.dev/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        return retrofit.create(ApiService::class.java)

    }
}
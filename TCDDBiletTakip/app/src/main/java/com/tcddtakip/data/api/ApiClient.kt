package com.tcddtakip.data.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var retrofit: Retrofit? = null
    private var currentUrl: String = ""

    fun getService(context: Context): BackendApiService {
        val prefs = context.getSharedPreferences("tcdd_prefs", Context.MODE_PRIVATE)
        val backendUrl = prefs.getString("backend_url", "https://tcdd-bilet-takip.onrender.com/")
            ?: "https://tcdd-bilet-takip.onrender.com/"

        if (retrofit == null || currentUrl != backendUrl) {
            currentUrl = backendUrl
            retrofit = buildRetrofit(backendUrl)
        }
        return retrofit!!.create(BackendApiService::class.java)
    }

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun invalidate() {
        retrofit = null
    }
}

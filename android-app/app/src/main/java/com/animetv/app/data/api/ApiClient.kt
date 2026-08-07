package com.animetv.app.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object ApiClient {
    // Production Render backend API URL
    private const val BASE_URL = "https://apk-animetv-backend.onrender.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val hlsTelemetryInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val isM3u8 = url.contains(".m3u8") || url.contains("/proxy/m3u8")
        val isTs = url.contains(".ts") || url.contains("/proxy/ts")

        if (!isM3u8 && !isTs) {
            return@Interceptor chain.proceed(request)
        }

        val tag = if (isM3u8) "M3U8" else "TS_SEGMENT"
        val start = System.currentTimeMillis()
        android.util.Log.d("PlayerTelemetry", "📡 [NETWORK] Started $tag request...")

        val response = chain.proceed(request)
        val ttfb = System.currentTimeMillis() - start
        android.util.Log.d("PlayerTelemetry", "📡 [NETWORK] $tag TTFB: $ttfb ms | Code: ${response.code}")

        response
    }

    private val mediaHeaderInterceptor = okhttp3.Interceptor { chain ->
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        if (originalRequest.header("User-Agent") == null) {
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        }

        val urlStr = originalRequest.url.toString()
        if (originalRequest.header("Referer") == null) {
            if (urlStr.contains("yourupload")) {
                builder.header("Referer", "https://www.yourupload.com/")
            } else if (urlStr.contains("mp4upload")) {
                builder.header("Referer", "https://www.mp4upload.com/")
            } else if (urlStr.contains("filemoon")) {
                builder.header("Referer", "https://filemoon.sx/")
            } else if (urlStr.contains("ok.ru")) {
                builder.header("Referer", "https://ok.ru/")
            } else {
                builder.header("Referer", "https://jkanime.net/")
            }
        }

        chain.proceed(builder.build())
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
        .addInterceptor(mediaHeaderInterceptor)
        .addInterceptor(hlsTelemetryInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val apiService: AnimeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AnimeApiService::class.java)
    }
}

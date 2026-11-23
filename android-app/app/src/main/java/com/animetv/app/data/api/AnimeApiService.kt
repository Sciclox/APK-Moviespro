package com.animetv.app.data.api

import com.animetv.app.data.model.ZonaExtractResponse
import com.animetv.app.data.model.ZonaListResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface AnimeApiService {

    @GET("list")
    suspend fun getList(
        @Query("page") page: Int = 1
    ): ZonaListResponse

    @GET("extract")
    suspend fun extractMovie(
        @Query("url") movieUrl: String
    ): ZonaExtractResponse
}


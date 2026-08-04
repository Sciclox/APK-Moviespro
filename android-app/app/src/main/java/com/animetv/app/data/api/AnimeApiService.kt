package com.animetv.app.data.api

import com.animetv.app.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AnimeApiService {

    @GET("api/v1/anime/hero")
    suspend fun getHeroBanners(@Query("tipo") tipo: String? = null): ApiResponse<List<AnimeItem>>

    @GET("api/v1/anime/latest")
    suspend fun getLatest(): ApiResponse<List<AnimeItem>>

    @GET("api/v1/anime/search")
    suspend fun searchAnime(@Query("q") query: String): ApiResponse<List<AnimeItem>>

    @GET("api/v1/anime/catalog")
    suspend fun getCatalog(
        @Query("genre") genre: String? = null,
        @Query("categoria") categoria: String? = null,
        @Query("tipo") tipo: String? = null,
        @Query("estado") estado: String? = null,
        @Query("demografia") demografia: String? = null,
        @Query("filtro") filtro: String? = null,
        @Query("page") page: Int = 1
    ): ApiResponse<List<AnimeItem>>

    @GET("api/v1/anime/details/{slug}")
    suspend fun getAnimeDetails(@Path("slug") slug: String): ApiResponse<AnimeDetail>

    @GET("api/v1/anime/watch/{animeSlug}/{episodeNum}")
    suspend fun getStreamSources(
        @Path("animeSlug") animeSlug: String,
        @Path("episodeNum") episodeNum: String
    ): ApiResponse<WatchResponse>
}

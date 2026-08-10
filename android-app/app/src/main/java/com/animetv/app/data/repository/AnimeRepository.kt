package com.animetv.app.data.repository

import com.animetv.app.data.api.ApiClient
import com.animetv.app.data.model.AnimeDetail
import com.animetv.app.data.model.CategoryHubResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Failure(val message: String) : RepositoryResult<Nothing>
}

/** Single access point for remote catalog data. Composables must not call Retrofit directly. */
object AnimeRepository {
    suspend fun getCategoryHub(category: String): RepositoryResult<CategoryHubResponse> = request {
        ApiClient.apiService.getCategoryHub(category)
    }

    suspend fun getDetails(slug: String): RepositoryResult<AnimeDetail> = request {
        ApiClient.apiService.getAnimeDetails(slug)
    }

    private suspend fun <T> request(call: suspend () -> com.animetv.app.data.model.ApiResponse<T>): RepositoryResult<T> =
        withContext(Dispatchers.IO) {
            try {
                val response = call()
                if (response.success) RepositoryResult.Success(response.data)
                else RepositoryResult.Failure("El servicio no pudo completar la solicitud")
            } catch (error: Exception) {
                RepositoryResult.Failure(error.message ?: "No se pudo conectar con el servidor")
            }
        }
}

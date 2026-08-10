package com.animetv.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animetv.app.data.model.AnimeItem
import com.animetv.app.data.model.CategoryHubSection
import com.animetv.app.data.repository.AnimeRepository
import com.animetv.app.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryHubUiState(
    val isLoading: Boolean = true,
    val hero: List<AnimeItem> = emptyList(),
    val sections: List<CategoryHubSection> = emptyList(),
    val error: String? = null,
)

class CategoryHubViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryHubUiState())
    val uiState: StateFlow<CategoryHubUiState> = _uiState.asStateFlow()

    fun load(category: String) {
        viewModelScope.launch {
            _uiState.value = CategoryHubUiState(isLoading = true)
            _uiState.value = when (val result = AnimeRepository.getCategoryHub(category)) {
                is RepositoryResult.Success -> CategoryHubUiState(
                    isLoading = false,
                    hero = result.value.hero,
                    sections = result.value.sections,
                )
                is RepositoryResult.Failure -> CategoryHubUiState(isLoading = false, error = result.message)
            }
        }
    }
}

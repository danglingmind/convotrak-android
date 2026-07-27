package com.danglingmind.convotrack.ui.common

/** Generic screen state for a single async load: loading → success | error. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

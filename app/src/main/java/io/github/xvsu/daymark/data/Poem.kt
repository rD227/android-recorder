package io.github.xvsu.daymark.data

data class Poem(
    val content: String,
    val title: String,
    val author: String,
    val dynasty: String,
)

data class PoetryUiState(
    val poem: Poem? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

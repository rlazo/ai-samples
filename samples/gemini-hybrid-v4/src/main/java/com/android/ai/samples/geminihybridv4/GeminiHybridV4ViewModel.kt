/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ai.samples.geminihybridv4

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.DownloadStatus
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.OnDeviceModelOption
import com.google.firebase.ai.OnDeviceModelStatus
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class GeminiHybridV4UiState(
    val expense: Expense? = null,
    val isScanning: Boolean = false,
    val modelStatus: String = "Checking model status...",
    val errorMessage: String? = null,
    val receiptImageUri: String? = null
)

@OptIn(PublicPreviewAPI::class)
@HiltViewModel
class GeminiHybridV4ViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(
        GeminiHybridV4UiState()
    )
    val uiState: StateFlow<GeminiHybridV4UiState> = _uiState.asStateFlow()

    private val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = "gemini-3.1-flash-lite-preview",
        onDeviceConfig = OnDeviceConfig(
            mode = InferenceMode.PREFER_ON_DEVICE,
            modelOption = OnDeviceModelOption.PREVIEW)
    )

    init {
        checkAndDownloadModel()
    }

    private fun checkAndDownloadModel() {
        viewModelScope.launch {
            try {
                val status = model.onDeviceExtension?.checkStatus() ?: return@launch

                updateStatus(status)

                if (status == OnDeviceModelStatus.DOWNLOADABLE) {
                    model.onDeviceExtension?.download()?.collect { downloadStatus ->
                        when (downloadStatus) {
                            is DownloadStatus.DownloadStarted -> {
                                _uiState.update { it.copy(modelStatus = "Downloading model...") }
                            }

                            is DownloadStatus.DownloadInProgress -> {
                                val progress = downloadStatus.totalBytesDownloaded
                                _uiState.update { it.copy(modelStatus = "Downloading: $progress bytes downloaded") }
                            }

                            is DownloadStatus.DownloadCompleted -> {
                                _uiState.update { it.copy(modelStatus = "Model ready") }
                            }

                            is DownloadStatus.DownloadFailed -> {
                                _uiState.update {
                                    it.copy(
                                        modelStatus = "Download failed", errorMessage = "Model download failed"
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(modelStatus = "Error checking status", errorMessage = e.message) }
            }
        }
    }

    private fun updateStatus(status: OnDeviceModelStatus) {
        val statusText = when (status) {
            OnDeviceModelStatus.AVAILABLE -> "Model available"
            OnDeviceModelStatus.DOWNLOADABLE -> "Model downloadable"
            OnDeviceModelStatus.DOWNLOADING -> "Model downloading..."
            OnDeviceModelStatus.UNAVAILABLE -> "On-device model unavailable"
            else -> "Unknown"
        }
        _uiState.update { it.copy(modelStatus = statusText) }
    }

    fun scanReceipt(bitmap: Bitmap, uriString: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, errorMessage = null, receiptImageUri = uriString) }
            try {
                val prompt = content {
                    image(bitmap)
                    text(
                        """
                        Extract the store name and the total price from this receipt. Store names are usually at the top of the document, first thing in them. Total price is usually indicated by text like "Total" or "Total Due"
                        Output only in JSON format containg 2 fields '{name,price}'.
                        Do not include any currency signs or backticks or any text around it.
                        Use dots for decimals.
                        Examples:
                        - {"name": "FakeStore", "price": "2.0"}
                        - {"name": "SomeMarket", "price": "3.5"}
                        """.trimIndent()
                    )
                }

                val response = model.generateContent(prompt)
                val text = response.text
                val inferenceMode = if (response.inferenceSource == InferenceSource.ON_DEVICE) {
                    "On-device"
                } else {
                    "Cloud"
                }
                Log.d("HybridVM", "$inferenceMode response: $text")
                if (text != null) {
                    parseAndAddExpense(text, inferenceMode)
                } else {
                    _uiState.update { it.copy(errorMessage = "Could not extract data") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    private fun parseAndAddExpense(text: String, inferenceMode: String) {
        val json = text
            // The on-device model sometimes outputs backticks, so we remove those
            .replace("```json", "")
            .replace("```", "")
        try {
            val newExpense = Json.decodeFromString<Expense>(json).copy(inferenceMode = inferenceMode)
            _uiState.update { it.copy(expense = newExpense) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.localizedMessage) }
        }
    }
}

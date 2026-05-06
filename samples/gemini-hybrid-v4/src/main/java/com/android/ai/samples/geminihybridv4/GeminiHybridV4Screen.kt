/*
 * Copyright 2025 The Android Open Project
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
@file:OptIn(PublicPreviewAPI::class)

package com.android.ai.samples.geminihybridv4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import android.graphics.Bitmap
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.ai.theme.AISampleCatalogTheme
import com.android.ai.theme.surfaceContainerHighestLight
import com.android.ai.uicomponent.SampleDetailTopAppBar
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.OnDeviceModelOption
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiHybridV4Screen(viewModel: GeminiHybridV4ViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var tempUri by remember { mutableStateOf<Uri?>(null) }
    var showImageDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                tempUri?.let { uri ->
                    try {
                        val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                        bitmap?.let { viewModel.scanReceipt(it, uri.toString()) }
                    } catch (e: Exception) {
                        Log.e("GeminiHybridV4Screen", "Failed to load image", e)
                    }
                }
            }
        }
    )

    fun launchCamera() {
        try {
            val uri = getTmpFileUri(context)
            tempUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("GeminiHybridV4Screen", "Failed to create temp file", e)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        }
    }

    AISampleCatalogTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                SampleDetailTopAppBar(
                    sampleName = stringResource(R.string.gemini_hybrid_v4_title),
                    sampleDescription = stringResource(R.string.gemini_hybrid_v4_description),
                    sourceCodeUrl = "https://github.com/android/ai-samples/tree/main/samples/gemini-hybrid-v4",
                    onBackClick = { backDispatcher?.onBackPressed() },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        val permissionCheckResult =
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.scan_receipt))
                    }
                }
            }
        ) { innerPadding ->
            Box(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(40.dp))
                    .background(color = surfaceContainerHighestLight)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .padding(top = 16.dp)
                        .widthIn(max = 646.dp)
                        .fillMaxHeight(),
                ) {
                    // Model Status Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Column {
                                Text(
                                    stringResource(R.string.hybrid_v4_ai_status),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    uiState.modelStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            ModelOptionDropdown(
                                selectedOption = uiState.selectedModelOption,
                                onOptionSelected = viewModel::setModelOption
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(R.string.section_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.expense == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_expenses), color = Color.Gray)
                        }
                    } else {
                        ExpenseResultUi(
                            expense = uiState.expense!!,
                            hasImage = uiState.receiptImageUri != null,
                            onShowImage = { showImageDialog = true }
                        )
                    }

                    if (showImageDialog && uiState.receiptImageUri != null) {
                        val imageUri = Uri.parse(uiState.receiptImageUri)
                        var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(imageUri) {
                            withContext(Dispatchers.IO) {
                                try {
                                    bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(imageUri))
                                } catch (e: Exception) {
                                    Log.e("GeminiHybridV4Screen", "Failed to load image for dialog", e)
                                }
                            }
                        }

                        if (bitmap != null) {
                            Dialog(onDismissRequest = { showImageDialog = false }) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(400.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            bitmap = bitmap!!.asImageBitmap(),
                                            contentDescription = "Receipt Image",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        } else {
                            Dialog(onDismissRequest = { showImageDialog = false }) {
                                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }

                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseResultUi(
    expense: Expense,
    hasImage: Boolean,
    onShowImage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = expense.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "$${String.format("%.2f", expense.price)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (expense.inferenceMode.isNotEmpty()) {
                Text(
                    text = "Extracted via ${expense.inferenceMode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            if (hasImage) {
                Button(
                    onClick = onShowImage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show Receipt Image")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Split Check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            var expanded by remember { mutableStateOf(false) }
            var numPeople by remember(expense) { mutableStateOf(1) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Number of people:")
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text("$numPeople")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        (1..10).forEach { count ->
                            DropdownMenuItem(
                                text = { Text("$count") },
                                onClick = {
                                    numPeople = count
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            val perPersonAmount = expense.price / numPeople
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Per person:")
                Text(
                    text = "$${String.format("%.2f", perPersonAmount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

private fun getTmpFileUri(context: Context): Uri {
    val tmpFile = File.createTempFile("tmp_image_file", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", tmpFile)
}

@Composable
fun ModelOptionDropdown(
    selectedOption: OnDeviceModelOption,
    onOptionSelected: (OnDeviceModelOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        OnDeviceModelOption.STABLE to stringResource(R.string.gemini_hybrid_option_stable),
        OnDeviceModelOption.PREVIEW to stringResource(R.string.gemini_hybrid_option_preview),
        OnDeviceModelOption.PREVIEW_FAST to stringResource(R.string.gemini_hybrid_option_preview_fast),
    )
    val selectedText = options.find { it.first == selectedOption }?.second ?: ""

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(selectedText)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

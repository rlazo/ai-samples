package com.android.ai.samples.geminihybridv4

import kotlinx.serialization.Serializable

@Serializable
data class Expense(
    val name: String,
    val price: Double,
    val inferenceMode: String = ""
)

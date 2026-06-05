package com.plantdiseasedetector

data class DiseaseInfo(
    val name: String,
    val commonName: String? = null,
    val cause: String? = null,
    val treatment: String? = null,
    val observation: String? = null,
    val notes: String? = null
)

data class DetectionResult(
    val className: String,
    val confidence: Float,
    val diseaseInfo: DiseaseInfo? = null
)

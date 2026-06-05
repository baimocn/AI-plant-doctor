package com.plantdiseasedetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ModelHelper(private val context: Context) {

    private var module: Module? = null
    private lateinit var classNames: List<String>
    private var diseaseData: Map<String, DiseaseInfo> = emptyMap()

    companion object {
        private const val MODEL_FILE = "model.ptl"
        private const val CLASS_NAMES_FILE = "class_names_134.txt"
        private const val DISEASE_DATA_FILE = "disease_data.json"
        private const val INPUT_SIZE = 224
        private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    @Throws(IOException::class)
    fun initialize() {
        module = LiteModuleLoader.load(assetFilePath(MODEL_FILE))
        classNames = loadClassNames()
        diseaseData = loadDiseaseData()
    }

    private fun assetFilePath(assetName: String): String {
        val file = File(context.filesDir, assetName)
        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return file.absolutePath
    }

    private fun loadClassNames(): List<String> {
        return context.assets.open(CLASS_NAMES_FILE).bufferedReader().readLines()
    }

    private fun loadDiseaseData(): Map<String, DiseaseInfo> {
        return try {
            val jsonString = context.assets.open(DISEASE_DATA_FILE).bufferedReader().use { it.readText() }
            val type = object : com.google.gson.reflect.TypeToken<Map<String, DiseaseInfo>>() {}.type
            com.google.gson.Gson().fromJson(jsonString, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun detectDisease(bitmap: Bitmap): DetectionResult {
        val resizedBitmap = preprocessBitmap(bitmap)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            IMAGENET_MEAN,
            IMAGENET_STD
        )

        val outputTensor = module?.forward(IValue.from(inputTensor))?.toTensor()
        val outputs = outputTensor?.dataAsFloatArray ?: floatArrayOf()

        var maxScore = -Float.MAX_VALUE
        var maxIdx = -1
        outputs.forEachIndexed { index, score ->
            if (score > maxScore) {
                maxScore = score
                maxIdx = index
            }
        }

        val className = if (maxIdx >= 0 && maxIdx < classNames.size) classNames[maxIdx] else "Unknown"
        val confidence = applySoftmax(outputs)[maxIdx]

        val diseaseInfo = diseaseData[className] ?: diseaseData[className.replace("_", "")]

        return DetectionResult(
            className = className,
            confidence = confidence,
            diseaseInfo = diseaseInfo
        )
    }

    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.postScale(256f / bitmap.width, 256f / bitmap.height)
        val scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        val x = (scaled.width - INPUT_SIZE) / 2
        val y = (scaled.height - INPUT_SIZE) / 2
        return Bitmap.createBitmap(scaled, x, y, INPUT_SIZE, INPUT_SIZE)
    }

    private fun applySoftmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val expSum = logits.sumOf { Math.exp((it - maxLogit).toDouble()).toFloat() }
        return logits.map { Math.exp((it - maxLogit).toDouble()).toFloat() / expSum }.toFloatArray()
    }

    fun rotateBitmapIfNeeded(bitmap: Bitmap, filePath: String): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}

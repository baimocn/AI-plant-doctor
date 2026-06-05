package com.plantdiseasedetector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()
    private lateinit var photoFile: File

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.setPhotoUri(Uri.fromFile(photoFile))
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            viewModel.setPhotoUri(result.data?.data)
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.permissionGranted()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlantDiseaseDetectorApp(
                viewModel = viewModel,
                onTakePhoto = { takePhoto() },
                onSelectImage = { selectImageFromGallery() },
                onRequestPermission = { requestPermissions() }
            )
        }
        initializeModel()
    }

    private fun initializeModel() {
        viewModel.initializeModel(this)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            viewModel.permissionGranted()
        }
    }

    private fun takePhoto() {
        photoFile = File(externalCacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val photoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }
        cameraLauncher.launch(intent)
    }

    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }
}

class MainViewModel : androidx.lifecycle.ViewModel() {
    private val _uiState = mutableStateOf(MainUiState())
    val uiState: State<MainUiState> = _uiState

    private var modelHelper: ModelHelper? = null
    private val coroutineScope = androidx.lifecycle.viewModelScope

    fun initializeModel(context: Context) {
        coroutineScope.launch {
            _uiState.value = _uiState.value.copy(isInitializing = true)
            try {
                withContext(Dispatchers.IO) {
                    modelHelper = ModelHelper(context)
                    modelHelper?.initialize()
                }
                _uiState.value = _uiState.value.copy(isInitializing = false, modelReady = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInitializing = false,
                    errorMessage = "模型加载失败: ${e.message}"
                )
            }
        }
    }

    fun setPhotoUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(
            photoUri = uri,
            detectionResult = null
        )
    }

    fun permissionGranted() {
        _uiState.value = _uiState.value.copy(hasPermission = true)
    }

    fun detectDisease(context: Context, bitmap: Bitmap) {
        coroutineScope.launch {
            _uiState.value = _uiState.value.copy(isDetecting = true)
            try {
                val result = withContext(Dispatchers.IO) {
                    modelHelper?.detectDisease(bitmap)
                }
                _uiState.value = _uiState.value.copy(
                    isDetecting = false,
                    detectionResult = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDetecting = false,
                    errorMessage = "检测失败: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class MainUiState(
    val isInitializing: Boolean = false,
    val modelReady: Boolean = false,
    val hasPermission: Boolean = false,
    val photoUri: Uri? = null,
    val isDetecting: Boolean = false,
    val detectionResult: DetectionResult? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDiseaseDetectorApp(
    viewModel: MainViewModel,
    onTakePhoto: () -> Unit,
    onSelectImage: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2E7D32),
            secondary = Color(0xFF4CAF50),
            background = Color(0xFFF0F7EA)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("🌿 植物病害识别助手") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF2E7D32),
                        titleContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isInitializing) {
                    LoadingScreen("正在加载模型...")
                } else if (!uiState.modelReady) {
                    ErrorScreen(uiState.errorMessage ?: "未知错误") {
                        viewModel.initializeModel(context)
                    }
                } else {
                    MainContent(
                        uiState = uiState,
                        onTakePhoto = {
                            if (!uiState.hasPermission) {
                                onRequestPermission()
                            } else {
                                onTakePhoto()
                            }
                        },
                        onSelectImage = {
                            if (!uiState.hasPermission) {
                                onRequestPermission()
                            } else {
                                onSelectImage()
                            }
                        },
                        onDetect = { bitmap ->
                            viewModel.detectDisease(context, bitmap)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF2E7D32))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("❌ $message", color = Color.Red)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
fun MainContent(
    uiState: MainUiState,
    onTakePhoto: () -> Unit,
    onSelectImage: () -> Unit,
    onDetect: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uiState.photoUri) {
        uiState.photoUri?.let { uri ->
            currentBitmap = loadBitmapFromUri(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentBitmap != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Image(
                    bitmap = currentBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { currentBitmap?.let(onDetect) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isDetecting
            ) {
                if (uiState.isDetecting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检测中...")
                } else {
                    Text("🔍 开始检测")
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("请选择图片或拍照")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onTakePhoto,
                modifier = Modifier.weight(1f)
            ) {
                Text("📸 拍照")
            }
            Button(
                onClick = onSelectImage,
                modifier = Modifier.weight(1f)
            ) {
                Text("🖼️ 选择图片")
            }
        }

        uiState.detectionResult?.let { result ->
            Spacer(modifier = Modifier.height(24.dp))
            DetectionResultCard(result)
        }
    }
}

@Composable
fun DetectionResultCard(result: DetectionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "识别结果",
                fontSize = 14.sp,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val displayName = result.diseaseInfo?.name ?: result.className.replace("_", " ")
            Text(
                displayName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "置信度: ${(result.confidence * 100).format(1)}%",
                color = Color(0xFF388E3C),
                fontSize = 16.sp
            )

            result.diseaseInfo?.let { info ->
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFFA5D6A7))
                Spacer(modifier = Modifier.height(16.dp))

                info.cause?.let { Cause ->
                    InfoSection("🔬 病因", Cause)
                }
                info.treatment?.let { treatment ->
                    InfoSection("💊 防治方法", treatment)
                }
                info.observation?.let { observation ->
                    InfoSection("👀 观察重点", observation)
                }
                info.notes?.let { notes ->
                    InfoSection("📌 注意事项", notes)
                }
            } ?: run {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFFA5D6A7))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "暂无详细信息",
                    color = Color(0xFF616161)
                )
            }
        }
    }
}

@Composable
fun InfoSection(title: String, content: String) {
    Column {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            content,
            lineHeight = 20.sp,
            color = Color(0xFF1B5E20)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}

fun Float.format(digits: Int) = "%.${digits}f".format(this)

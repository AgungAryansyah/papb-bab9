package com.example.praktukumcamerax

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.praktukumcamerax.ui.theme.PraktukumCameraXTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PraktukumCameraXTheme {
                val cameraPermission = android.Manifest.permission.CAMERA
                var permissionGranted by remember { mutableStateOf(false) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> permissionGranted = granted }

                LaunchedEffect(Unit) { launcher.launch(cameraPermission) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (permissionGranted) {
                        CameraScreen(modifier = Modifier.padding(innerPadding))
                    } else {
                        PermissionDeniedScreen(
                            onRequestAgain = { launcher.launch(cameraPermission) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionDeniedScreen(onRequestAgain: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Izin Kamera Diperlukan",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Aplikasi membutuhkan akses kamera untuk mengambil foto. Silakan berikan izin di pengaturan.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onRequestAgain) {
                Text("Minta Izin Lagi")
            }
        }
    }
}

@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreview(onPreviewReady = { view ->
            previewView = view
            scope.launch {
                val (preview, camera) = bindPreview(context, lifecycleOwner, view)
                val provider = ProcessCameraProvider.getInstance(context).get()
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                val ic = bindWithImageCapture(provider, lifecycleOwner, preview, selector)
                ic.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                imageCapture = ic
            }
        })
        
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            lastCapturedUri?.let { uri ->
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = "Last Photo",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            
            Button(
                onClick = {
                    imageCapture?.let { ic ->
                        takePhoto(context, ic) { uri ->
                            lastCapturedUri = uri
                        }
                    }
                }
            ) {
                Text("Ambil Foto")
            }
        }
    }
}

@Composable
fun CameraPreview(onPreviewReady: (PreviewView) -> Unit) {
    AndroidView(factory = { c -> PreviewView(c).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        post { onPreviewReady(this) }
    }})
}

suspend fun bindPreview(
    context: Context,
    owner: LifecycleOwner,
    view: PreviewView
): Pair<Preview, androidx.camera.core.Camera> {
    val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
        val f = ProcessCameraProvider.getInstance(context)
        f.addListener({ cont.resume(f.get()) }, ContextCompat.getMainExecutor(context))
    }
    val preview = Preview.Builder()
        .setResolutionSelector(
            androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                )
                .build()
        )
        .build().also {
        it.setSurfaceProvider(view.surfaceProvider)
    }
    val selector = CameraSelector.DEFAULT_BACK_CAMERA
    provider.unbindAll()
    val camera = provider.bindToLifecycle(owner, selector, preview)
    return preview to camera
}

fun bindWithImageCapture(
    provider: ProcessCameraProvider,
    owner: LifecycleOwner,
    preview: Preview,
    selector: CameraSelector
): ImageCapture {
    val ic = ImageCapture.Builder()
        .setResolutionSelector(
            androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                )
                .build()
        )
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()
    provider.unbindAll()
    provider.bindToLifecycle(owner, selector, preview, ic)
    return ic
}

fun outputOptions(ctx: Context, name: String): ImageCapture.OutputFileOptions {
    val v = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KameraKu")
    }
    return ImageCapture.OutputFileOptions.Builder(
        ctx.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        v
    ).build()
}

fun takePhoto(ctx: Context, ic: ImageCapture, onSaved: (Uri) -> Unit) {
    val opt = outputOptions(ctx, "IMG_" + System.currentTimeMillis())
    ic.takePicture(opt, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(res: ImageCapture.OutputFileResults) {
            val uri = res.savedUri!!
            Log.d("CameraX", "Photo saved: $uri")
            Toast.makeText(ctx, "Foto tersimpan", Toast.LENGTH_SHORT).show()
            onSaved(uri)
        }
        override fun onError(e: ImageCaptureException) {
            Log.e("CameraX", "Photo capture failed", e)
            Toast.makeText(ctx, "Gagal mengambil foto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    })
}
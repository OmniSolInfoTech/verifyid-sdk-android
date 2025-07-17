package com.verifyid_sdk_android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

fun writeToDownloadFolder(context: Context, filename: String, content: String) {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val file = File(downloadDir, filename)
    FileOutputStream(file).use { it.write(content.toByteArray()) }
}

class KycWizardActivity : ComponentActivity() {
    companion object {
        fun launch(context: Context, apiKey: String) {
            val intent = android.content.Intent(context, KycWizardActivity::class.java)
            intent.putExtra("API_KEY", apiKey)
            context.startActivity(intent)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("KYC", "KycWizardActivity created")
        val apiKey = intent.getStringExtra("API_KEY") ?: ""
        setContent {
            MaterialTheme {
                KycWizardScreen(apiKey = apiKey)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("KYC", "KycWizardActivity destroyed")
    }
}

enum class WizardScreen { Welcome, DocType, DocFront, DocBack, Selfie, Review, Submitted }

// --- Bitmap helper ---
fun rotateBitmapIfNeeded(path: String): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(path) ?: return null
    try {
        val exif = androidx.exifinterface.media.ExifInterface(path)
        val orientation = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        return bitmap
    }
}

// Encode file to base64 for API
fun fileToBase64(path: String?): String? {
    if (path == null) return null
    val file = File(path)
    if (!file.exists()) return null
    val bytes = file.readBytes()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

@Composable
fun KycWizardScreen(apiKey: String) {
    var screen by remember { mutableStateOf(WizardScreen.Welcome) }
    var selectedDocType by remember { mutableStateOf<String?>(null) }
    var docFrontImage by remember { mutableStateOf<Bitmap?>(null) }
    var docFrontPath by remember { mutableStateOf<String?>(null) }
    var docBackImage by remember { mutableStateOf<Bitmap?>(null) }
    var docBackPath by remember { mutableStateOf<String?>(null) }
    var selfieImage by remember { mutableStateOf<Bitmap?>(null) }
    var selfieImagePath by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var apiResult by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission required.\nPlease enable in Settings.", color = Color.Red)
        }
    } else {
        when (screen) {
            WizardScreen.Welcome -> KycWelcomeScreen(onContinue = { screen = WizardScreen.DocType })
            WizardScreen.DocType -> KycDocumentTypeScreen(
                selected = selectedDocType,
                onSelect = { selectedDocType = it },
                onNext = { if (selectedDocType != null) screen = WizardScreen.DocFront }
            )
            WizardScreen.DocFront -> if (docFrontImage == null) {
                InAppCapture(
                    label = "Front of ${selectedDocType ?: ""}",
                    useFrontCamera = false,
                    onCapture = { bitmap, path ->
                        docFrontImage = bitmap
                        docFrontPath = path
                    },
                    onCancel = { screen = WizardScreen.DocType }
                )
            } else {
                KycDocImageScreen(
                    title = "Front of ${selectedDocType ?: ""}",
                    image = docFrontImage,
                    onRetake = { docFrontImage = null },
                    onNext = {
                        if (selectedDocType == "Passport") screen = WizardScreen.Selfie
                        else screen = WizardScreen.DocBack
                    }
                )
            }
            WizardScreen.DocBack -> if (docBackImage == null) {
                InAppCapture(
                    label = "Back of ${selectedDocType ?: ""}",
                    useFrontCamera = false,
                    onCapture = { bitmap, path ->
                        docBackImage = bitmap
                        docBackPath = path
                    },
                    onCancel = { screen = WizardScreen.DocFront }
                )
            } else {
                KycDocImageScreen(
                    title = "Back of ${selectedDocType ?: ""}",
                    image = docBackImage,
                    onRetake = { docBackImage = null },
                    onNext = { screen = WizardScreen.Selfie }
                )
            }
            WizardScreen.Selfie -> if (selfieImage == null) {
                InAppCapture(
                    label = "Take a Selfie",
                    useFrontCamera = true,
                    onCapture = { bitmap, path ->
                        selfieImage = bitmap
                        selfieImagePath = path
                    },
                    onCancel = {
                        if (selectedDocType == "Passport") screen = WizardScreen.DocFront
                        else screen = WizardScreen.DocBack
                    },
                    overlayOval = true
                )
            } else {
                KycDocImageScreen(
                    title = "Selfie",
                    image = selfieImage,
                    onRetake = { selfieImage = null },
                    onNext = { screen = WizardScreen.Review }
                )
            }
            WizardScreen.Review -> ReviewScreen(
                docFrontImage = docFrontImage,
                docBackImage = if (selectedDocType != "Passport") docBackImage else null,
                selfieImage = selfieImage,
                onSubmit = {
                    submitting = true
                    submitError = null
                    val frontBase64 = fileToBase64(docFrontPath)
                    val backBase64 = if (selectedDocType != "Passport") fileToBase64(docBackPath) else ""
                    val selfieBase64 = fileToBase64(selfieImagePath)
                    if (frontBase64.isNullOrEmpty() || selfieBase64.isNullOrEmpty() || (selectedDocType != "Passport" && backBase64.isNullOrEmpty())) {
                        submitError = "Could not read one or more images. Please retake the photos."
                        submitting = false
                        return@ReviewScreen
                    }
                    val bodyJson = buildString {
                        append("{")
                        append("\"front_image\":\"$frontBase64\",")
                        if (selectedDocType != "Passport")
                            append("\"back_image\":\"$backBase64\",")
                        else
                            append("\"back_image\":\"\",")
                        append("\"selfie_image\":\"$selfieBase64\",")
                        append("\"threshold\":0.6")
                        append("}")
                    }
                    val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), bodyJson)
                    writeToDownloadFolder(context, "selfie_b64.txt", bodyJson ?: "")
                    val client = OkHttpClient.Builder()
                        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(240, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url("https://api.verifyid.io/kyc/full_verification")
                        .addHeader("x-api-key", apiKey)
                        .post(requestBody)
                        .build()
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            mainHandler.post {
                                submitting = false
                                submitError = "Failed: ${e.message}"
                            }
                        }
                        override fun onResponse(call: Call, response: Response) {
                            try {
                                val resultText = response.body?.string() ?: ""
                                mainHandler.post {
                                    submitting = false
                                    if (response.isSuccessful) {
                                        android.util.Log.d("KYC", "KycWizardActivity Success")
                                        apiResult = resultText
                                        screen = WizardScreen.Submitted
                                    } else {
                                        submitError = "Failed: ${response.code} - ${response.message}\n$resultText"
                                    }
                                }
                            } catch (e: Exception) {
                                mainHandler.post {
                                    submitting = false
                                    submitError = "Error handling response: ${e.message}"
                                }
                            }
                        }
                    })
                },
                submitting = submitting,
                submitError = submitError
            )
            WizardScreen.Submitted -> SubmitSuccessScreen(apiResult)
        }
    }
}

@Composable
fun KycWelcomeScreen(onContinue: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F6F6)) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to KYC Verification", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF037BBA))
            Spacer(Modifier.height(16.dp))
            Text("You’ll need:\n- Your ID Document\n- A live selfie\n\nAll data is secure and private.", fontSize = 18.sp, color = Color(0xFF555555))
            Spacer(Modifier.height(32.dp))
            Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF037BBA),
                contentColor = Color.White
            ),
                modifier = Modifier.fillMaxWidth()) {
                Text("Get Started", fontSize = 20.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text("Powered by VerifyID", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
fun KycDocumentTypeScreen(selected: String?, onSelect: (String) -> Unit, onNext: () -> Unit) {
    val options = listOf("National ID Card", "Driver’s License", "Passport")
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F6F6)) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Select Document Type", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF037BBA))
            Spacer(Modifier.height(24.dp))
            options.forEach { docType ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onSelect(docType) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected == docType, onClick = { onSelect(docType) })
                    Spacer(Modifier.width(8.dp))
                    Text(docType, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onNext,
                enabled = selected != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF037BBA),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Next", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun KycDocImageScreen(
    title: String,
    image: Bitmap?,
    onRetake: () -> Unit,
    onNext: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F6F6)) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
            Spacer(Modifier.height(24.dp))
            if (image != null) {
                val aspectRatio = image.width.toFloat() / image.height.toFloat()
                Box(
                    Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(aspectRatio)
                        .background(Color.LightGray, shape = MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onRetake, colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF037BBA),
                contentColor = Color.White
            ),
                modifier = Modifier.fillMaxWidth()) {
                Text("Retake", fontSize = 20.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNext, enabled = image != null, colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF037BBA),
                contentColor = Color.White
            ),
                modifier = Modifier.fillMaxWidth()) {
                Text("Next", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun InAppCapture(
    label: String,
    useFrontCamera: Boolean,
    onCapture: (Bitmap, String) -> Unit, // bitmap for UI, path for API
    onCancel: () -> Unit,
    overlayOval: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val executor = ContextCompat.getMainExecutor(context)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageCap = ImageCapture.Builder().build()
                    imageCapture = imageCap
                    val cameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCap
                        )
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                }, executor)
                previewView
            },
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .aspectRatio(0.75f)
                .align(Alignment.Center)
        )

        if (overlayOval) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .aspectRatio(0.75f)
                    .align(Alignment.Center)
            ) {
                val ovalWidth = size.width * 0.85f
                val ovalHeight = size.height * 1.10f
                drawOval(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        (size.width - ovalWidth) / 2f,
                        (size.height - ovalHeight) / 2f
                    ),
                    size = androidx.compose.ui.geometry.Size(ovalWidth, ovalHeight),
                    style = Stroke(width = 6.dp.toPx())
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    imageCapture?.let { imageCap ->
                        val photoFile = File(context.cacheDir, "kyc_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        imageCap.takePicture(
                            outputOptions,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    // Always EXIF-correct the bitmap for preview, use path for API
                                    val rotatedBitmap = rotateBitmapIfNeeded(photoFile.absolutePath)
                                    if (rotatedBitmap != null) {
                                        FileOutputStream(photoFile).use { out ->
                                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                                        }
                                        onCapture(rotatedBitmap, photoFile.absolutePath)
                                    }
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    exception.printStackTrace()
                                }
                            }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF037BBA),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Text("Capture", fontSize = 18.sp)
            }
            Text(label, color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF037BBA),
                contentColor = Color.White
            ),
                modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun ReviewScreen(
    docFrontImage: Bitmap?,
    docBackImage: Bitmap?,
    selfieImage: Bitmap?,
    onSubmit: () -> Unit,
    submitting: Boolean,
    submitError: String?
) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F6F6)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())   // <-- ADD THIS LINE!
                .padding(32.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Review Details", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF037BBA))
            Spacer(Modifier.height(24.dp))
            if (docFrontImage != null) {
                val aspectRatio = docFrontImage.width.toFloat() / docFrontImage.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(aspectRatio)
                        .background(Color.LightGray, shape = MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = docFrontImage.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            if (docBackImage != null) {
                val aspectRatio = docBackImage.width.toFloat() / docBackImage.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(aspectRatio)
                        .background(Color.LightGray, shape = MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = docBackImage.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            if (selfieImage != null) {
                val aspectRatio = selfieImage.width.toFloat() / selfieImage.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(aspectRatio)
                        .background(Color.LightGray, shape = MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = selfieImage.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (submitError != null) {
                Text(submitError, color = Color.Red, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onSubmit,
                enabled = !submitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF037BBA),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (submitting) "Submitting..." else "Submit", fontSize = 20.sp)
            }
        }
    }
}


@Composable
fun SubmitSuccessScreen(apiResult: String?) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F6F6)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("KYC Verification Submitted!", fontSize = 26.sp, color = Color(0xFF32CD32), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Thank you! We will review your submission and notify you.", fontSize = 18.sp)
                Spacer(Modifier.height(32.dp))
                if (!apiResult.isNullOrEmpty()) {
                    // Save full result for debug:
                    val context = LocalContext.current
                    LaunchedEffect(apiResult) {
                        writeToDownloadFolder(context, "kyc_api_result.txt", apiResult)
                    }
                    Text("API Response (truncated):", fontWeight = FontWeight.Bold)
                    Text(apiResult.take(2000), fontSize = 14.sp, color = Color.DarkGray)
                    if (apiResult.length > 2000) {
                        Text("...(output truncated, see file in Downloads)...", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}


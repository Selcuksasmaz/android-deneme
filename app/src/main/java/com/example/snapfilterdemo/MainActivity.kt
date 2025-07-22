package com.example.snapfilterdemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera  // Bu satırı ekleyin
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.snapfilterdemo.facerecognition.FaceRecognizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: FaceOverlayView
    private lateinit var capturedImageOverlay: View
    private lateinit var recognitionResultText: TextView
    private lateinit var addFaceButton: Button
    private lateinit var recognizeFaceButton: Button
    private lateinit var toggleFilterButton: Button

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var processingFrame = false
    private var displayId = -1

    // ML Kit yüz algılama
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build()

    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    // Yüz filtreleri
    private val faceFilterRenderer = FaceFilterRenderer()

    // Filtreler için bitmap'ler
    private var glassesBitmap: Bitmap? = null
    private var mustacheBitmap: Bitmap? = null
    private var hatBitmap: Bitmap? = null

    // Aktif filtreler
    private var activeFilters = mutableSetOf<String>()

    // Son algılanan yüzler
    private var lastDetectedFaces = listOf<Face>()

    // Yüz tanıma nesnesi
    private lateinit var faceRecognizer: FaceRecognizer

    // Coroutine scope
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    // Tanıma modunda mı?
    private var recognitionMode = false

    // Son yakalanan görüntü
    private var lastCapturedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlay)
        capturedImageOverlay = findViewById(R.id.captured_image_overlay)
        recognitionResultText = findViewById(R.id.recognition_result)
        addFaceButton = findViewById(R.id.btn_add_face)
        recognizeFaceButton = findViewById(R.id.btn_recognize_face)
        toggleFilterButton = findViewById(R.id.btn_toggle_filter)

        // Kamera izni kontrolü
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        // Filtre bitmap'lerini yükle
        loadFilterBitmaps()

        // Yüz tanıma sınıfını başlat
        faceRecognizer = FaceRecognizer(this)

        // Buton tıklama olayları
        setupButtons()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupButtons() {
        // Filtre değiştirme butonu
        findViewById<Button>(R.id.btn_toggle_filter).setOnClickListener {
            cycleFilters()
        }

        // Debug modu butonu
        findViewById<Button>(R.id.btn_debug_mode).setOnClickListener {
            overlayView.toggleDebugMode()
        }

        // Yüz ekleme butonu
        addFaceButton.setOnClickListener {
            if (lastCapturedBitmap != null) {
                showAddFaceDialog()
            } else {
                captureImage { bitmap ->
                    lastCapturedBitmap = bitmap
                    showAddFaceDialog()
                }
            }
        }

        // Yüz tanıma butonu
        recognizeFaceButton.setOnClickListener {
            if (lastCapturedBitmap != null) {
                recognizeFace(lastCapturedBitmap!!)
            } else {
                captureImage { bitmap ->
                    lastCapturedBitmap = bitmap
                    recognizeFace(bitmap)
                }
            }
        }

        // Kamera değiştirme butonu
        findViewById<Button>(R.id.btn_switch_camera).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            startCamera()
        }
    }

    private fun captureImage(onCaptured: (Bitmap) -> Unit) {
        val bitmap = viewFinder.bitmap
        if (bitmap != null) {
            onCaptured(bitmap)
            capturedImageOverlay.visibility = View.VISIBLE
        } else {
            Toast.makeText(this, "Görüntü yakalanamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddFaceDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Kişi Adı"

        AlertDialog.Builder(this)
            .setTitle("Yeni Yüz Ekle")
            .setMessage("Bu yüz için bir isim girin:")
            .setView(input)
            .setPositiveButton("Ekle") { dialog, which ->
                val name = input.text.toString()
                if (name.isNotEmpty() && lastCapturedBitmap != null) {
                    addNewFace(name, lastCapturedBitmap!!)
                }
            }
            .setNegativeButton("İptal", null)
            .setOnDismissListener {
                capturedImageOverlay.visibility = View.GONE
                lastCapturedBitmap = null
            }
            .show()
    }

    private fun addNewFace(name: String, bitmap: Bitmap) {
        coroutineScope.launch {
            try {
                Log.d(TAG, "addNewFace başlatıldı: $name")
                val success = faceRecognizer.addNewFace(name, bitmap)
                if (success) {
                    Log.d(TAG, "$name için yüz başarıyla kaydedildi")
                    Toast.makeText(
                        this@MainActivity,
                        "$name için yüz başarıyla kaydedildi",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.e(TAG, "$name için yüz kaydedilemedi")
                    Toast.makeText(
                        this@MainActivity,
                        "Yüz kaydedilemedi, tekrar deneyin",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Yüz ekleme hatası: ${e.message}")
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "Hata: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                capturedImageOverlay.visibility = View.GONE
                lastCapturedBitmap = null
            }
        }
    }
    private fun recognizeFace(bitmap: Bitmap) {
        recognitionResultText.visibility = View.VISIBLE
        recognitionResultText.text = "Tanınıyor..."

        coroutineScope.launch {
            try {
                // Önce yüzü algıla
                val faces = faceRecognizer.detectFace(bitmap)
                if (faces.isEmpty()) {
                    recognitionResultText.text = "Yüz algılanamadı!"
                    delay(2000)
                    recognitionResultText.visibility = View.GONE
                    capturedImageOverlay.visibility = View.GONE
                    lastCapturedBitmap = null
                    return@launch
                }

                // En büyük yüzü seç
                val largestFace = faces.maxByOrNull {
                    it.boundingBox.width() * it.boundingBox.height()
                } ?: return@launch

                // Yüzü kırp
                val croppedFace = faceRecognizer.cropFace(largestFace, bitmap)

                // Yüz embedding'i çıkar
                val embedding = faceRecognizer.extractFaceEmbedding(croppedFace)

                // Yüzü tanı
                val result = faceRecognizer.recognizeFace(embedding)

                if (result.isRecognized) {
                    val confidence = (result.confidenceScore * 100).toInt()
                    recognitionResultText.text = "Merhaba, ${result.personName}!\nEşleşme: %$confidence"
                } else {
                    recognitionResultText.text = "Tanınmadı. Yeni yüz ekleyebilirsiniz."
                }

                delay(3000) // 3 saniye sonra sonucu gizle
                recognitionResultText.visibility = View.GONE
                capturedImageOverlay.visibility = View.GONE
                lastCapturedBitmap = null

            } catch (e: Exception) {
                Log.e(TAG, "Yüz tanıma hatası: ${e.message}")
                recognitionResultText.text = "Hata: ${e.message}"
                delay(3000)
                recognitionResultText.visibility = View.GONE
                capturedImageOverlay.visibility = View.GONE
                lastCapturedBitmap = null
            }
        }
    }

    private fun loadFilterBitmaps() {
        try {
            // Bitmap'leri assets'ten yükle
            val assetManager = assets
            val inputStream1 = assetManager.open("glasses.png")
            glassesBitmap = BitmapFactory.decodeStream(inputStream1)
            inputStream1.close()

            val inputStream2 = assetManager.open("mustache.png")
            mustacheBitmap = BitmapFactory.decodeStream(inputStream2)
            inputStream2.close()

            val inputStream3 = assetManager.open("hat.png")
            hatBitmap = BitmapFactory.decodeStream(inputStream3)
            inputStream3.close()

            Log.d(TAG, "Filtre bitmap'leri başarıyla yüklendi")
        } catch (e: Exception) {
            Log.e(TAG, "Filtre bitmap'leri yüklenemedi: ${e.message}")
        }
    }

    private fun cycleFilters() {
        // Filtre döngüsü: Yok -> Gözlük -> Bıyık -> Şapka -> Hepsi -> Yok
        when {
            activeFilters.isEmpty() -> {
                activeFilters.add("glasses")
                Toast.makeText(this, "Filtre: Gözlük", Toast.LENGTH_SHORT).show()
            }
            activeFilters.size == 1 && activeFilters.contains("glasses") -> {
                activeFilters.remove("glasses")
                activeFilters.add("mustache")
                Toast.makeText(this, "Filtre: Bıyık", Toast.LENGTH_SHORT).show()
            }
            activeFilters.size == 1 && activeFilters.contains("mustache") -> {
                activeFilters.remove("mustache")
                activeFilters.add("hat")
                Toast.makeText(this, "Filtre: Şapka", Toast.LENGTH_SHORT).show()
            }
            activeFilters.size == 1 && activeFilters.contains("hat") -> {
                activeFilters.add("glasses")
                activeFilters.add("mustache")
                Toast.makeText(this, "Filtre: Tümü", Toast.LENGTH_SHORT).show()
            }
            else -> {
                activeFilters.clear()
                Toast.makeText(this, "Filtre: Yok", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Önizleme kullanım durumu
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Görüntü yakalama kullanım durumu
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Görüntü analizi kullanım durumu
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

            // Kamera seçici
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                // Bağlantıyı yeniden bağlamadan önce tüm kullanım durumlarını ayır
                cameraProvider.unbindAll()

                // Kullanım durumlarını kameraya bağla
                camera = cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Kamera bağlama hatası", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            if (processingFrame) {
                imageProxy.close()
                return
            }

            processingFrame = true

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            lastDetectedFaces = faces

                            // Landmark'ları ve filtreleri çiz
                            overlayView.updateFaces(
                                faces,
                                imageProxy.width,
                                imageProxy.height,
                                viewFinder.width,
                                viewFinder.height,
                                imageProxy.imageInfo.rotationDegrees,
                                activeFilters,
                                glassesBitmap,
                                mustacheBitmap,
                                hatBitmap
                            )
                        }
                        processingFrame = false
                        imageProxy.close()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Yüz algılama hatası", e)
                        processingFrame = false
                        imageProxy.close()
                    }
            } else {
                processingFrame = false
                imageProxy.close()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Kamera izni gereklidir",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceRecognizer.close()
        coroutineScope.cancel()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
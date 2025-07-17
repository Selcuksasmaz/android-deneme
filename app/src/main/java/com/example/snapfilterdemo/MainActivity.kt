package com.example.snapfilterdemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
// MainActivity sınıfının dışında, dosyanın üst kısmında tanımlayın
class FaceFilterRenderer {

    // Gözlük çizim fonksiyonu
    fun drawGlasses(canvas: Canvas, face: Face, glassesBitmap: Bitmap) {
        try {
            // Göz landmark'larını al
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

            if (leftEye != null && rightEye != null) {
                // Gözlerin X koordinat farkını bul
                val eyeDiffX = rightEye.position.x - leftEye.position.x

                // Gözlük bitmap'inin orijinal boyutları
                val originalWidth = glassesBitmap.width
                val originalHeight = glassesBitmap.height

                // Gözlük genişliği, göz mesafesinin 3 katı olsun
                val glassesWidth = eyeDiffX * 3.0f

                // Gözlük yüksekliğini orantılı hesapla
                val aspectRatio = originalWidth.toFloat() / originalHeight
                val glassesHeight = glassesWidth / aspectRatio

                // Gözlük merkezini gözlerin ortasına konumlandır
                val centerX = (leftEye.position.x + rightEye.position.x) / 2
                val centerY = (leftEye.position.y + rightEye.position.y) / 2

                // Gözlüğü biraz yukarı kaydır
                val offsetY = glassesHeight * 0.1f

                // Dönüşüm matrisi oluştur
                val matrix = Matrix()

                // Ölçekle
                matrix.postScale(
                    glassesWidth / originalWidth,
                    glassesHeight / originalHeight
                )

                // Gözlüğün ortalanmış konumunu hesapla
                val left = centerX - (glassesWidth / 2)
                val top = centerY - (glassesHeight / 2) - offsetY

                // Konumlandır
                matrix.postTranslate(left, top)

                // Gözlüğü çiz
                canvas.drawBitmap(glassesBitmap, matrix, null)
            }
        } catch (e: Exception) {
            Log.e("FaceFilterRenderer", "Gözlük çizim hatası: ${e.message}")
        }
    }

    // Bıyık çizim fonksiyonu
    fun drawMustache(canvas: Canvas, face: Face, mustacheBitmap: Bitmap) {
        try {
            // İlgili landmarkları al
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)
            val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
            val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)
            val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)

            if (nose != null && mouthBottom != null && leftMouth != null && rightMouth != null) {
                // Ağız genişliği
                val mouthWidth = Math.abs(rightMouth.position.x - leftMouth.position.x)

                // Bıyık bitmap'inin orijinal boyutları
                val originalWidth = mustacheBitmap.width
                val originalHeight = mustacheBitmap.height

                // Bıyık genişliği, ağız genişliğinin 1.5 katı olsun
                val mustacheWidth = mouthWidth * 1.5f

                // Bıyık yüksekliğini orantılı hesapla
                val aspectRatio = originalWidth.toFloat() / originalHeight
                val mustacheHeight = mustacheWidth / aspectRatio

                // Bıyık merkezi, burun ve ağız arasında olsun
                val centerX = (leftMouth.position.x + rightMouth.position.x) / 2
                val centerY = (nose.position.y + mouthBottom.position.y) / 2 * 0.95f

                // Dönüşüm matrisi oluştur
                val matrix = Matrix()

                // Ölçekle
                matrix.postScale(
                    mustacheWidth / originalWidth,
                    mustacheHeight / originalHeight
                )

                // Bıyığın ortalanmış konumunu hesapla
                val left = centerX - (mustacheWidth / 2)
                val top = centerY - (mustacheHeight / 2)

                // Konumlandır
                matrix.postTranslate(left, top)

                // Bıyığı çiz
                canvas.drawBitmap(mustacheBitmap, matrix, null)
            }
        } catch (e: Exception) {
            Log.e("FaceFilterRenderer", "Bıyık çizim hatası: ${e.message}")
        }
    }

    // Şapka çizim fonksiyonu
    fun drawHat(canvas: Canvas, face: Face, hatBitmap: Bitmap) {
        try {
            // İlgili landmarkları al
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

            // Yüz kutusu
            val faceBox = face.boundingBox
            val faceWidth = faceBox.width()

            // Şapka bitmap'inin orijinal boyutları
            val originalWidth = hatBitmap.width
            val originalHeight = hatBitmap.height

            // Şapka genişliği, yüz genişliğinin 1.8 katı olsun
            val hatWidth = faceWidth * 1.8f

            // Şapka yüksekliğini orantılı hesapla
            val aspectRatio = originalWidth.toFloat() / originalHeight
            val hatHeight = hatWidth / aspectRatio

            // Şapka merkezi X - yüz merkezi ile aynı
            val centerX = faceBox.centerX().toFloat()

            // Şapka Y pozisyonu
            var hatY = faceBox.top - (hatHeight * 0.5f)

            // Eğer gözler tespit edildiyse, onları kullan
            if (leftEye != null && rightEye != null) {
                val eyeY = Math.min(leftEye.position.y, rightEye.position.y)
                hatY = eyeY - (hatHeight * 0.9f)
            }

            // Dönüşüm matrisi oluştur
            val matrix = Matrix()

            // Ölçekle
            matrix.postScale(
                hatWidth / originalWidth,
                hatHeight / originalHeight
            )

            // Şapkanın ortalanmış konumunu hesapla
            val left = centerX - (hatWidth / 2)

            // Konumlandır
            matrix.postTranslate(left, hatY)

            // Şapkayı çiz
            canvas.drawBitmap(hatBitmap, matrix, null)
        } catch (e: Exception) {
            Log.e("FaceFilterRenderer", "Şapka çizim hatası: ${e.message}")
        }
    }
}
class MainActivity : AppCompatActivity() {
    // Log etiketleri
    companion object {
        private const val TAG = "SnapFilter"
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }

    // UI bileşenleri
    private lateinit var previewView: PreviewView
    private lateinit var effectView: ImageView
    private lateinit var statusText: TextView

    // Kamera ve İşleyici
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var faceDetector: FaceDetector? = null

    // Filtreler için bitmap'ler
    private var glassesBitmap: Bitmap? = null
    private var hatBitmap: Bitmap? = null
    private var mustacheBitmap: Bitmap? = null

    // Filtre tipi
    private var currentFilterType = FilterType.GLASSES // Varsayılan olarak gözlük seçili

    // Debug modu
    private var isDebugMode = true // Debug modu varsayılan olarak açık

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI bileşenlerini ayarla
        previewView = findViewById(R.id.previewView)
        effectView = findViewById(R.id.effectView)
        statusText = findViewById(R.id.statusText)

        statusText.text = "Başlatılıyor..."

        // Filtreleri yükle
        loadFilterBitmaps()

        // Yüz dedektörünü ayarla
        setupFaceDetector()

        // Debug butonunu ayarla
        setupDebugButton()

        // Filtre değiştirme tıklama olayını ayarla
        setupClickListeners()

        // Kamera için executor oluştur
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Kamera izinlerini kontrol et
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST
            )
        }
    }

    private fun setupDebugButton() {
        // Debug butonu var mı kontrol et
        val debugButton = findViewById<Button>(R.id.debugButton)
        if (debugButton != null) {
            debugButton.setOnClickListener {
                isDebugMode = !isDebugMode
                Toast.makeText(this,
                    if (isDebugMode) "Debug modu: AÇIK" else "Debug modu: KAPALI",
                    Toast.LENGTH_SHORT).show()

                Log.d(TAG, "Debug modu: ${if (isDebugMode) "AÇIK" else "KAPALI"}")
            }
        } else {
            Log.e(TAG, "Debug butonu bulunamadı! Layout'unuza eklediğinizden emin olun.")
        }
    }
    // MainActivity sınıfının içinde, sınıf değişkenleri bölümünde tanımlayın
    private val filterRenderer = FaceFilterRenderer()
    private fun loadFilterBitmaps() {
        try {
            // Bitmap'leri yüklemeden önce kontrol et
            val glassesId = resources.getIdentifier("glasses", "drawable", packageName)
            val hatId = resources.getIdentifier("hat", "drawable", packageName)
            val mustacheId = resources.getIdentifier("mustache", "drawable", packageName)

            if (glassesId == 0) Log.e(TAG, "Gözlük resmi (glasses.png) drawable klasöründe bulunamadı!")
            if (hatId == 0) Log.e(TAG, "Şapka resmi (hat.png) drawable klasöründe bulunamadı!")
            if (mustacheId == 0) Log.e(TAG, "Bıyık resmi (mustache.png) drawable klasöründe bulunamadı!")

            // Bitmap'leri yükle
            if (glassesId != 0) glassesBitmap = BitmapFactory.decodeResource(resources, glassesId)
            if (hatId != 0) hatBitmap = BitmapFactory.decodeResource(resources, hatId)
            if (mustacheId != 0) mustacheBitmap = BitmapFactory.decodeResource(resources, mustacheId)

            // Bitmap'lerin doğru yüklenip yüklenmediğini kontrol et
            val loadedFilters = mutableListOf<String>()
            if (glassesBitmap != null) loadedFilters.add("Gözlük")
            if (hatBitmap != null) loadedFilters.add("Şapka")
            if (mustacheBitmap != null) loadedFilters.add("Bıyık")

            if (loadedFilters.isNotEmpty()) {
                Log.d(TAG, "Yüklenen filtreler: ${loadedFilters.joinToString(", ")}")
            } else {
                Log.e(TAG, "Hiçbir filtre yüklenemedi! Drawable klasörünü kontrol edin.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Filtre yükleme hatası: ${e.message}")
        }
    }

    private fun setupFaceDetector() {
        try {
            // Yüksek doğruluklu yüz dedektörü ayarları - maksimum hassasiyet
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.1f)
                .enableTracking()
                .build()

            // Dedektörü oluştur
            faceDetector = FaceDetection.getClient(options)
            Log.d(TAG, "Yüz dedektörü yüksek hassasiyetle yapılandırıldı")
        } catch (e: Exception) {
            Log.e(TAG, "Yüz dedektörü oluşturma hatası: ${e.message}")
        }
    }

    private fun setupClickListeners() {
        // Filtre tipini değiştir
        previewView.setOnClickListener {
            currentFilterType = when (currentFilterType) {
                FilterType.GLASSES -> FilterType.HAT
                FilterType.HAT -> FilterType.MUSTACHE
                FilterType.MUSTACHE -> FilterType.NONE
                FilterType.NONE -> FilterType.GLASSES
                // Diğer filtre tipleri varsa buraya ekleyin
                else -> FilterType.NONE
            }

            Log.d(TAG, "Filtre değiştirildi: ${currentFilterType.name}")
            Toast.makeText(this, "Filtre: ${currentFilterType.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                // Camera provider
                cameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Image Analysis
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FaceAnalyzer())
                    }

                // Ön kamerayı seç (selfie modu)
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // Bağlantıları kaldır ve yeniden bağla
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

                Log.d(TAG, "Kamera başlatıldı")
                statusText.text = "Kamera hazır. Filtre: ${currentFilterType.name}"

            } catch (e: Exception) {
                Log.e(TAG, "Kamera bağlama hatası: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Kamera izni gerekli!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            try {
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    // Burada kamera rotasyonu düzeltme ekliyoruz
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    Log.d(TAG, "Kamera rotasyonu: $rotationDegrees derece")

                    // Görüntüyü doğru yönde işle
                    val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

                    faceDetector?.process(image)
                        ?.addOnSuccessListener { faces ->
                            try {
                                if (faces.isEmpty()) {
                                    runOnUiThread {
                                        statusText.text = "Yüz algılanamadı"
                                        effectView.setImageBitmap(null)
                                    }
                                    Log.d(TAG, "Yüz algılanamadı")
                                } else {
                                    Log.d(TAG, "${faces.size} yüz algılandı. Yüz koordinatları: ${faces[0].boundingBox}")

                                    // Koordinat dönüşümü için görüntü boyutlarını alalım
                                    val targetWidth = imageProxy.height  // Not: Burada width ve height değişti - 90 derece çevrildiyse
                                    val targetHeight = imageProxy.width

                                    if (isDebugMode) {
                                        // Debug modunda yüz noktalarını göster - rotasyon düzeltmeli
                                        val debugBitmap = drawDebugLandmarks(faces, imageProxy.width, imageProxy.height, rotationDegrees)
                                        runOnUiThread {
                                            effectView.setImageBitmap(debugBitmap)
                                            statusText.text = "Yüz algılandı: ${faces.size} | DEBUG MOD"
                                        }
                                        Log.d(TAG, "Debug görünümü güncellendi")
                                    } else {
                                        // Normal modda filtreleri göster
                                        processFaces(faces, imageProxy.width, imageProxy.height, rotationDegrees)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Yüz işleme hatası: ${e.message}")
                                e.printStackTrace()
                            } finally {
                                imageProxy.close()
                            }
                        }
                        ?.addOnFailureListener { e ->
                            Log.e(TAG, "Yüz algılama hatası: ${e.message}")
                            e.printStackTrace()
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Görüntü analiz hatası: ${e.message}")
                e.printStackTrace()
                imageProxy.close()
            }
        }
    }

    // drawDebugLandmarks fonksiyonunu tamamen değiştirin
// drawDebugLandmarks fonksiyonunu düzenleyelim
    private fun drawDebugLandmarks(faces: List<Face>, width: Int, height: Int, rotationDegrees: Int): Bitmap {
        // Boş bir bitmap oluştur
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for (face in faces) {
            try {
                // Yüz boyutları
                val faceWidth = face.boundingBox.width()
                val faceHeight = face.boundingBox.height()
                val faceCenterX = face.boundingBox.centerX().toFloat()
                val faceCenterY = face.boundingBox.centerY().toFloat()

                // Genişletilmiş yüz kutusu
                val expandedWidth = faceWidth * 1.5f
                val expandedHeight = faceHeight * 1.5f

                val expandedRect = RectF(
                    faceCenterX - expandedWidth / 2,
                    faceCenterY - expandedHeight / 2,
                    faceCenterX + expandedWidth / 2,
                    faceCenterY + expandedHeight / 2
                )

                // Yüz çerçevesini çiz
                val boundsPaint = Paint().apply {
                    color = Color.GREEN
                    style = Paint.Style.STROKE
                    strokeWidth = 8f
                }

                canvas.drawRect(expandedRect, boundsPaint)

                // *** ÖNEMLİ: GERÇEKTEKİ LANDMARK KONUMLARINI KULLAN ***

                // Göz landmark'larını al
                val leftEyeLandmark = face.getLandmark(FaceLandmark.LEFT_EYE)
                val rightEyeLandmark = face.getLandmark(FaceLandmark.RIGHT_EYE)

                // Landmark renkleri
                val eyePaint = Paint().apply {
                    color = Color.BLUE
                    style = Paint.Style.FILL
                }

                val nosePaint = Paint().apply {
                    color = Color.YELLOW
                    style = Paint.Style.FILL
                }

                val mouthPaint = Paint().apply {
                    color = Color.MAGENTA
                    style = Paint.Style.FILL
                }

                // İşaret boyutu
                val landmarkRadius = expandedWidth * 0.05f

                // Gerçek landmark pozisyonlarını kullanarak gözleri çiz
                if (leftEyeLandmark != null) {
                    canvas.drawCircle(
                        leftEyeLandmark.position.x,
                        leftEyeLandmark.position.y,
                        landmarkRadius,
                        eyePaint
                    )
                }

                if (rightEyeLandmark != null) {
                    canvas.drawCircle(
                        rightEyeLandmark.position.x,
                        rightEyeLandmark.position.y,
                        landmarkRadius,
                        eyePaint
                    )
                }

                // Burun landmark'ını al ve çiz
                face.getLandmark(FaceLandmark.NOSE_BASE)?.let {
                    canvas.drawCircle(
                        it.position.x,
                        it.position.y,
                        landmarkRadius,
                        nosePaint
                    )
                }

                // Ağız landmark'larını al ve çiz
                face.getLandmark(FaceLandmark.MOUTH_LEFT)?.let {
                    canvas.drawCircle(
                        it.position.x,
                        it.position.y,
                        landmarkRadius,
                        mouthPaint
                    )
                }

                face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.let {
                    canvas.drawCircle(
                        it.position.x,
                        it.position.y,
                        landmarkRadius,
                        mouthPaint
                    )
                }

                face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.let {
                    canvas.drawCircle(
                        it.position.x,
                        it.position.y,
                        landmarkRadius,
                        mouthPaint
                    )
                }

                // Yüz kontürlerini çiz
                val contourPaint = Paint().apply {
                    color = Color.CYAN
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                }

                // Tüm yüz kontürlerini çiz
                face.allContours.forEach { contour ->
                    val path = Path()
                    val points = contour.points

                    if (points.isNotEmpty()) {
                        path.moveTo(points[0].x, points[0].y)

                        for (i in 1 until points.size) {
                            path.lineTo(points[i].x, points[i].y)
                        }

                        canvas.drawPath(path, contourPaint)
                    }
                }

            } catch (e: Exception) {
                Log.e("SnapFilterDemo", "Yüz çizim hatası: ${e.message}")
                e.printStackTrace()
            }
        }

        // Bitmap'i ön kamera için aynala
        val matrix = Matrix()
        matrix.preScale(-1.0f, 1.0f)
        val flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()

        // Son bitmap'i oluştur
        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalBitmap)

        // Aynalanan bitmap'i çiz
        finalCanvas.drawBitmap(flippedBitmap, 0f, 0f, null)

        // Bilgileri ekle
        val infoPaint = Paint().apply {
            color = Color.WHITE
            textSize = width * 0.04f
            textAlign = Paint.Align.LEFT
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        if (faces.isNotEmpty()) {
            val face = faces[0]
            val smileProb = face.smilingProbability?.times(100)?.toInt() ?: 0
            val leftEyeOpen = face.leftEyeOpenProbability?.times(100)?.toInt() ?: 0
            val rightEyeOpen = face.rightEyeOpenProbability?.times(100)?.toInt() ?: 0

            finalCanvas.drawText("Gülümseme: $smileProb%", width * 0.05f, height - height * 0.15f, infoPaint)
            finalCanvas.drawText("Sol Göz: $leftEyeOpen% | Sağ Göz: $rightEyeOpen%", width * 0.05f, height - height * 0.10f, infoPaint)
            finalCanvas.drawText("Açılar: X=${face.headEulerAngleX.toInt()}°, Y=${face.headEulerAngleY.toInt()}°, Z=${face.headEulerAngleZ.toInt()}°",
                width * 0.05f, height - height * 0.05f, infoPaint)
        }

        finalCanvas.drawText("Yüz algılandı: ${faces.size} | DEBUG MOD", width * 0.03f, height - height * 0.03f, infoPaint)

        flippedBitmap.recycle()

        return finalBitmap
    }    // Yardımcı fonksiyon - kontürleri çizmek için
    private fun drawContour(canvas: Canvas, contour: FaceContour?, paint: Paint, closePath: Boolean) {
        contour?.points?.let { points ->
            if (points.isNotEmpty()) {
                val path = Path()
                path.moveTo(points[0].x, points[0].y)

                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }

                if (closePath) {
                    path.close()
                }

                canvas.drawPath(path, paint)
            }
        }
    }
    private fun processFaces(faces: List<Face>, width: Int, height: Int, rotationDegrees: Int) {
        if (currentFilterType == FilterType.NONE) {
            runOnUiThread {
                effectView.setImageBitmap(null)
                statusText.text = "Yüz algılandı: ${faces.size} | Filtre: Yok"
            }
            Log.d(TAG, "Filtre tipi NONE, görüntü temizlendi")
            return
        }

        try {
            // Boş bir bitmap oluştur
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Her bir yüz için filtre uygula
            for (face in faces) {
                try {
                    when (currentFilterType) {
                        FilterType.GLASSES -> {
                            if (glassesBitmap == null) {
                                Log.e(TAG, "Gözlük bitmap'i yüklü değil!")
                            } else {
                                filterRenderer.drawGlasses(canvas, face, glassesBitmap!!)
                            }
                        }
                        FilterType.HAT -> {
                            if (hatBitmap == null) {
                                Log.e(TAG, "Şapka bitmap'i yüklü değil!")
                            } else {
                                filterRenderer.drawHat(canvas, face, hatBitmap!!)
                            }
                        }
                        FilterType.MUSTACHE -> {
                            if (mustacheBitmap == null) {
                                Log.e(TAG, "Bıyık bitmap'i yüklü değil!")
                            } else {
                                filterRenderer.drawMustache(canvas, face, mustacheBitmap!!)
                            }
                        }
                        else -> {
                            Log.d(TAG, "Bilinmeyen filtre tipi: ${currentFilterType.name}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Filtre uygulama hatası: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Ön kamera için yatay aynalama yap
            val matrix = Matrix()
            matrix.postScale(-1f, 1f)  // Sadece X ekseni boyunca aynala (yatay aynalama)

            // Bitmap'i dönüştür
            val transformedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, width, height, matrix, true
            )
            bitmap.recycle()

            // UI'ı güncelle
            runOnUiThread {
                effectView.setImageBitmap(transformedBitmap)
                statusText.text = "Yüz algılandı: ${faces.size} | Filtre: ${currentFilterType.name}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Yüz işleme hatası: ${e.message}")
            e.printStackTrace()
        }
    }
    private fun drawGlasses(canvas: Canvas, face: Face, width: Int, height: Int, rotationDegrees: Int) {
        try {
            // Göz landmarklarını al
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

            // Göz koordinatları varsa, bunları kullan
            if (leftEye != null && rightEye != null) {
                // İki göz arasındaki mesafeyi hesapla
                val eyeDistance = Math.sqrt(
                    Math.pow((rightEye.position.x - leftEye.position.x).toDouble(), 2.0) +
                            Math.pow((rightEye.position.y - leftEye.position.y).toDouble(), 2.0)
                ).toFloat()

                // Gözlerin orta noktası
                val centerX = (leftEye.position.x + rightEye.position.x) / 2
                val centerY = (leftEye.position.y + rightEye.position.y) / 2

                // Gözlük boyutunu göz mesafesine göre ayarla
                val glassesWidth = eyeDistance * 3.0f

                if (glassesBitmap != null) {
                    // Ölçek faktörü hesapla
                    val scaleFactor = glassesWidth / glassesBitmap!!.width
                    val scaledHeight = glassesBitmap!!.height * scaleFactor

                    // Dönüşüm matrisi
                    val matrix = Matrix()
                    matrix.postScale(scaleFactor, scaleFactor)

                    // İki göz arasındaki açıyı hesapla
                    val angle = Math.toDegrees(
                        Math.atan2(
                            (rightEye.position.y - leftEye.position.y).toDouble(),
                            (rightEye.position.x - leftEye.position.x).toDouble()
                        )
                    ).toFloat()

                    // Yüz açısına göre döndür
                    matrix.postRotate(angle)

                    // Gözlüğü pozisyonla - göz hizasında
                    matrix.postTranslate(
                        centerX - (glassesWidth / 2),
                        centerY - (scaledHeight / 2)
                    )

                    // Gözlüğü çiz
                    canvas.drawBitmap(glassesBitmap!!, matrix, null)
                    Log.d(TAG, "Gözlük çizildi: merkez($centerX, $centerY), ölçek:$scaleFactor, açı:$angle")
                }
            } else {
                // Yüz boyutuna göre oransal hesaplama yap (göz landmark'ları yoksa)
                val faceWidth = face.boundingBox.width()
                val faceHeight = face.boundingBox.height()
                val faceCenterX = face.boundingBox.centerX().toFloat()
                val faceCenterY = (face.boundingBox.top + (faceHeight * 0.35f)).toFloat()

                // Gözlük boyutunu yüz genişliğine göre ayarla
                val glassesWidth = faceWidth * 1.1f

                if (glassesBitmap != null) {
                    // Ölçek faktörü hesapla
                    val scaleFactor = glassesWidth / glassesBitmap!!.width
                    val scaledHeight = glassesBitmap!!.height * scaleFactor

                    // Dönüşüm matrisi
                    val matrix = Matrix()
                    matrix.postScale(scaleFactor, scaleFactor)

                    // Yüz açısına göre döndür
                    val angle = face.headEulerAngleZ
                    matrix.postRotate(angle)

                    // Gözlüğü pozisyonla
                    matrix.postTranslate(
                        faceCenterX - (glassesWidth / 2),
                        faceCenterY - (scaledHeight / 2)
                    )

                    // Gözlüğü çiz
                    canvas.drawBitmap(glassesBitmap!!, matrix, null)
                    Log.d(TAG, "Gözlük (yedek yöntem) çizildi: merkez($faceCenterX, $faceCenterY), ölçek:$scaleFactor")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gözlük çizme hatası: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun drawHat(canvas: Canvas, face: Face, width: Int, height: Int, rotationDegrees: Int) {
        try {
            // Yüz boyutuna göre oransal hesaplama yap
            val faceWidth = face.boundingBox.width()
            val faceCenterX = face.boundingBox.centerX().toFloat()

            // Göz ve burun landmarklarını kullan
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)

            // Şapkanın üst pozisyonunu belirle
            var hatTopY = face.boundingBox.top.toFloat() - (faceWidth * 0.3f)

            // Göz landmarkları varsa, daha doğru pozisyon hesapla
            if (leftEye != null && rightEye != null) {
                // Gözlerin daha yukarısına yerleştir
                val eyeY = Math.min(leftEye.position.y, rightEye.position.y)
                hatTopY = eyeY - (faceWidth * 0.5f)
            }

            if (hatBitmap != null) {
                // Şapka boyutunu yüz genişliğine göre ayarla
                val hatWidth = faceWidth * 1.5f
                val scaleFactor = hatWidth / hatBitmap!!.width
                val scaledHeight = hatBitmap!!.height * scaleFactor

                // Dönüşüm matrisi
                val matrix = Matrix()
                matrix.postScale(scaleFactor, scaleFactor)

                // Açıyı uygulamayı kaldır - şapka her zaman düz dursun
                // Açı düzeltmesi istenirse, açı değerini 0 yaparak hep düz dursun
                // Eski kod: matrix.postRotate(angle)

                // Şapkayı pozisyonla
                matrix.postTranslate(
                    faceCenterX - (hatWidth / 2),
                    hatTopY
                )

                // Şapkayı çiz
                canvas.drawBitmap(hatBitmap!!, matrix, null)
                Log.d(TAG, "Şapka çizildi: pozisyon($faceCenterX, $hatTopY), ölçek:$scaleFactor")
            } else {
                Log.e(TAG, "Şapka bitmap'i yüklü değil!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Şapka çizme hatası: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun drawMustache(canvas: Canvas, face: Face, width: Int, height: Int, rotationDegrees: Int) {
        try {
            // Yüz boyutuna göre oransal hesaplama yap
            val faceWidth = face.boundingBox.width()
            val faceCenterX = face.boundingBox.centerX().toFloat()

            // İlgili landmarkları al
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)
            val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
            val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)
            val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)

            // Bıyık konumunu hesapla
            var mustacheY = (face.boundingBox.top + (face.boundingBox.height() * 0.65f)).toFloat()

            // Burun ve ağız landmarkları varsa, daha doğru konum belirle
            if (nose != null && mouthBottom != null) {
                // Bıyığı burun ve ağız arasına koy
                mustacheY = (nose.position.y + mouthBottom.position.y) / 2
            }

            // Açı hesaplamayı kaldır - bıyık her zaman düz dursun
            // Eski kod: var mustacheAngle = face.headEulerAngleZ
            // Eski kod: if (mouthLeft != null && mouthRight != null) { mustacheAngle = ... }

            if (mustacheBitmap != null) {
                // Bıyık boyutunu yüz genişliğine göre ayarla
                val mustacheWidth = faceWidth * 0.7f
                val scaleFactor = mustacheWidth / mustacheBitmap!!.width
                val scaledHeight = mustacheBitmap!!.height * scaleFactor

                // Dönüşüm matrisi
                val matrix = Matrix()
                matrix.postScale(scaleFactor, scaleFactor)

                // Açıya göre döndürmeyi kaldır - bıyık her zaman düz dursun
                // Eski kod: matrix.postRotate(mustacheAngle)

                // Bıyığı pozisyonla
                matrix.postTranslate(
                    faceCenterX - (mustacheWidth / 2),
                    mustacheY - (scaledHeight / 2)
                )

                // Bıyığı çiz
                canvas.drawBitmap(mustacheBitmap!!, matrix, null)
                Log.d(TAG, "Bıyık çizildi: pozisyon($faceCenterX, $mustacheY)")
            } else {
                Log.e(TAG, "Bıyık bitmap'i yüklü değil!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bıyık çizme hatası: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
package com.example.snapfilterdemo.facerecognition

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Yüz tanıma işlemlerini yöneten sınıf
 */
class FaceRecognizer(private val context: Context) {
    private val TAG = "FaceRecognizer"

    // ML Kit yüz algılama seçenekleri
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build()

    // ML Kit yüz algılayıcı
    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    // TensorFlow Lite yorumlayıcı - yüz embedding vektörü çıkarmak için
    private val interpreter: Interpreter by lazy {
        val tfliteModel = loadModelFile("facenet_model.tflite")
        Interpreter(tfliteModel)
    }
    // Model boyutları
    private val inputSize = 160 // FaceNet modelinin giriş boyutu
    private val embeddingDim = 128 // Çıktı vektörünün boyutu

    /**
     * TFLite modelini yükler
     */
    private fun loadModelFile(modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Görüntüden yüz algılar
     */
    suspend fun detectFace(bitmap: Bitmap): List<Face> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    Log.d(TAG, "${faces.size} yüz algılandı")
                    continuation.resume(faces)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Yüz algılama hatası: ${e.message}")
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * Algılanan yüzün kırpılmış görüntüsünü alır
     */
    fun cropFace(face: Face, sourceBitmap: Bitmap): Bitmap {
        // Yüz kutusu sınırlarını al
        val bounds = face.boundingBox

        // Bitmap sınırları içinde kaldığından emin ol
        val left = Math.max(bounds.left, 0)
        val top = Math.max(bounds.top, 0)
        val right = Math.min(bounds.right, sourceBitmap.width)
        val bottom = Math.min(bounds.bottom, sourceBitmap.height)

        // Yüzü kırp
        return Bitmap.createBitmap(
            sourceBitmap,
            left,
            top,
            right - left,
            bottom - top
        )
    }

    /**
     * Yüz görüntüsünden embedding vektörü çıkarır
     */
    fun extractFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        // Yüz görüntüsünü model giriş boyutuna ölçeklendir
        val scaledBitmap = Bitmap.createScaledBitmap(
            faceBitmap,
            inputSize,
            inputSize,
            false
        )

        // Bitmap'i normalleştirilmiş float dizisine dönüştür
        val imgData = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.width, 0, 0,
            scaledBitmap.width, scaledBitmap.height)

        imgData.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                // Normalize pixel value to [-1,1]
                imgData.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f - 0.5f) * 2)
                imgData.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f - 0.5f) * 2)
                imgData.putFloat(((pixelValue and 0xFF) / 255.0f - 0.5f) * 2)
            }
        }

        // Çıktı vektörü
        val embeddings = Array(1) { FloatArray(embeddingDim) }

        // TFLite çıkarımını çalıştır
        interpreter.run(imgData, embeddings)

        return embeddings[0]
    }

    /**
     * İki yüz vektörü arasındaki benzerliği hesaplar (cosine benzerliği)
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        return dotProduct / (Math.sqrt(norm1.toDouble()) * Math.sqrt(norm2.toDouble())).toFloat()
    }

    /**
     * Verilen yüz vektörünün kayıtlı yüzlerle eşleşip eşleşmediğini kontrol eder
     * Eşleşme skoru ve kişi adını döndürür
     */
    suspend fun recognizeFace(faceEmbedding: FloatArray): RecognitionResult = withContext(Dispatchers.IO) {
        // Veritabanından kayıtlı tüm yüz vektörlerini al
        val faceRepository = FaceRepository(context)
        val savedFaces = faceRepository.getAllFaces()

        var highestScore = -1f
        var bestMatch: SavedFace? = null

        // En iyi eşleşmeyi bul
        for (savedFace in savedFaces) {
            val similarity = calculateSimilarity(faceEmbedding, savedFace.faceVector)
            if (similarity > highestScore) {
                highestScore = similarity
                bestMatch = savedFace
            }
        }

        // Eşleşme eşiği (0.7 iyi bir başlangıç değeridir)
        val matchThreshold = 0.7f

        return@withContext if (highestScore > matchThreshold && bestMatch != null) {
            RecognitionResult(true, bestMatch.personName, highestScore)
        } else {
            RecognitionResult(false, "", highestScore)
        }
    }

    /**
     * Yeni yüz ekler
     */
    suspend fun addNewFace(personName: String, faceBitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Yüz algılaması başlıyor...")
            // Yüzü algıla
            val faces = detectFace(faceBitmap)
            if (faces.isEmpty()) {
                Log.e(TAG, "Yüz algılanamadı")
                return@withContext false
            }

            Log.d(TAG, "${faces.size} yüz algılandı")

            // En büyük yüzü seç
            val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                ?: return@withContext false

            Log.d(TAG, "Yüz kırpılıyor...")
            // Yüzü kırp
            val croppedFace = cropFace(largestFace, faceBitmap)

            Log.d(TAG, "Embedding vektörü çıkarılıyor...")
            // Embedding vektörü çıkar
            val embedding = extractFaceEmbedding(croppedFace)

            Log.d(TAG, "Veritabanına kaydediliyor: $personName")
            // Veritabanına kaydet
            val faceRepository = FaceRepository(context)
            val savedFace = SavedFace(
                id = 0, // Otomatik oluşturulacak
                personName = personName,
                faceVector = embedding,
                timestamp = System.currentTimeMillis()
            )
            val id = faceRepository.addFace(savedFace)

            Log.d(TAG, "$personName için yüz başarıyla kaydedildi, ID: $id")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Yüz ekleme hatası: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }
    /**
     * Kaynakları temizle
     */
    fun close() {
        faceDetector.close()
        interpreter.close()
    }
}

/**
 * Yüz tanıma sonucunu temsil eden veri sınıfı
 */
data class RecognitionResult(
    val isRecognized: Boolean,
    val personName: String,
    val confidenceScore: Float
)
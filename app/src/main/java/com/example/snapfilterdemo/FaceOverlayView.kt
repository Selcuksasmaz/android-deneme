package com.example.snapfilterdemo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Dik konumda telefon kullanımı için optimize edilmiş FaceOverlayView
 * Sadece portre mod (0°) desteklenir
 */
class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val TAG = "FaceOverlayView"

    private val faceBoxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val landmarkPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var faces: List<Face> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var activeFilters = mutableSetOf<String>()

    // Filtreler için bitmap'ler
    private var glassesBitmap: Bitmap? = null
    private var mustacheBitmap: Bitmap? = null
    private var hatBitmap: Bitmap? = null

    // Debug modu
    private var debugMode = false

    // DİK KONUM için sabit kalibrasyon değerleri
    private val offsetX = -0.25f      // Sağa hafif kaydır
    private val offsetY = -0.15f     // Yukarı kaydır
    private val scaleX = 1.95f       // X ekseninde hafif küçült
    private val scaleY = 0.85f        // Y ekseni normal
    private val mirrorX = true       // Ön kamera için aynalama

    fun updateFaces(
        faces: List<Face>,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        rotationDegrees: Int, // Bu parametre artık göz ardı edilecek
        activeFilters: Set<String>,
        glassesBitmap: Bitmap?,
        mustacheBitmap: Bitmap?,
        hatBitmap: Bitmap?
    ) {
        this.faces = faces
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        this.activeFilters = activeFilters.toMutableSet()
        this.glassesBitmap = glassesBitmap
        this.mustacheBitmap = mustacheBitmap
        this.hatBitmap = hatBitmap

        Log.d(TAG, "Faces updated: ${faces.size} (Portrait mode only)")
        Log.d(TAG, "Image: $imageWidth x $imageHeight, View: $viewWidth x $viewHeight")

        invalidate()
    }

    fun toggleDebugMode() {
        debugMode = !debugMode
        invalidate()
    }

    /**
     * DİK KONUM için basitleştirilmiş koordinat dönüşümü
     */
    private fun mapPoint(facePoint: PointF, isBoundingBox: Boolean = false): PointF {
        // 1. Normalize et (0-1 aralığına çevir)
        var normalizedX = facePoint.x / imageWidth
        var normalizedY = facePoint.y / imageHeight

        // 2. Ön kamera için X ekseninde aynalama (sadece landmark'lar için)
        if (mirrorX && !isBoundingBox) {
            normalizedX = 1f - normalizedX
        }

        // 3. Kalibrasyon uygula
        val calibratedX = (normalizedX - 0.5f) * scaleX + 0.5f + offsetX
        val calibratedY = (normalizedY - 0.5f) * scaleY + 0.5f + offsetY

        // 4. View koordinatlarına çevir
        val finalX = calibratedX * viewWidth
        val finalY = calibratedY * viewHeight

        return PointF(finalX, finalY)
    }

    /**
     * Dikdörtgen dönüşümü
     */
    /**
     * Yüz kutusunu doğru şekilde aynalama
     */
    private fun mapRect(rect: Rect): RectF {
        // ÖNCEKİ KOD (yanlış):
        // val topLeft = mapPoint(PointF(rect.left.toFloat(), rect.top.toFloat()), true)
        // val bottomRight = mapPoint(PointF(rect.right.toFloat(), rect.bottom.toFloat()), true)

        // YENİ KOD (doğru): isBoundingBox = false yaparak kutuyu da aynala
        val topLeft = mapPoint(PointF(rect.left.toFloat(), rect.top.toFloat()), false)
        val bottomRight = mapPoint(PointF(rect.right.toFloat(), rect.bottom.toFloat()), false)

        return RectF(
            minOf(topLeft.x, bottomRight.x),
            minOf(topLeft.y, bottomRight.y),
            maxOf(topLeft.x, bottomRight.x),
            maxOf(topLeft.y, bottomRight.y)
        )
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (faces.isEmpty()) return

        for (face in faces) {
            // Debug modu
            if (debugMode) {
                // Yüz kutusu
                val faceBox = face.boundingBox
                val mappedBox = mapRect(faceBox)
                canvas.drawRect(mappedBox, faceBoxPaint)

                // Landmark'ları çiz
                for (landmark in face.allLandmarks) {
                    val point = mapPoint(landmark.position)
                    canvas.drawCircle(point.x, point.y, 12f, landmarkPaint)

                    // Landmark türünü yazdır
                    val landmarkName = getLandmarkName(landmark.landmarkType)
                    canvas.drawText(
                        landmarkName,
                        point.x + 20f,
                        point.y,
                        textPaint.apply { textSize = 25f }
                    )
                }

                // Yüz ID'si
                val id = face.trackingId ?: -1
                canvas.drawText(
                    "Face ID: $id (Portrait)",
                    mappedBox.left,
                    mappedBox.top - 20f,
                    textPaint.apply { textSize = 35f }
                )
            }

            // Filtreleri çiz
            if ("glasses" in activeFilters && glassesBitmap != null) {
                drawGlasses(canvas, face, glassesBitmap!!)
            }

            if ("mustache" in activeFilters && mustacheBitmap != null) {
                drawMustache(canvas, face, mustacheBitmap!!)
            }

            if ("hat" in activeFilters && hatBitmap != null) {
                drawHat(canvas, face, hatBitmap!!)
            }
        }
    }

    /**
     * Landmark türü ismini al
     */
    private fun getLandmarkName(landmarkType: Int): String {
        return when (landmarkType) {
            FaceLandmark.LEFT_EYE -> "L_EYE"
            FaceLandmark.RIGHT_EYE -> "R_EYE"
            FaceLandmark.NOSE_BASE -> "NOSE"
            FaceLandmark.MOUTH_LEFT -> "M_LEFT"
            FaceLandmark.MOUTH_RIGHT -> "M_RIGHT"
            FaceLandmark.MOUTH_BOTTOM -> "M_BOT"
            FaceLandmark.LEFT_EAR -> "L_EAR"
            FaceLandmark.RIGHT_EAR -> "R_EAR"
            FaceLandmark.LEFT_CHEEK -> "L_CHEEK"
            FaceLandmark.RIGHT_CHEEK -> "R_CHEEK"
            else -> "UNK"
        }
    }

    /**
     * Gözlük çizimi - DİK KONUM optimized
     */
    private fun drawGlasses(canvas: Canvas, face: Face, glassesBitmap: Bitmap) {
        try {
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

            if (leftEye != null && rightEye != null) {
                val leftEyePos = mapPoint(leftEye.position)
                val rightEyePos = mapPoint(rightEye.position)

                // Gözler arası mesafe
                val eyeDistance = Math.sqrt(
                    Math.pow((rightEyePos.x - leftEyePos.x).toDouble(), 2.0) +
                            Math.pow((rightEyePos.y - leftEyePos.y).toDouble(), 2.0)
                ).toFloat()

                // Gözlük boyutu
                val glassesWidth = eyeDistance * 2.3f
                val aspectRatio = glassesBitmap.width.toFloat() / glassesBitmap.height
                val glassesHeight = glassesWidth / aspectRatio

                // Pozisyon
                val centerX = (leftEyePos.x + rightEyePos.x) / 2
                val centerY = (leftEyePos.y + rightEyePos.y) / 2
                val left = centerX - (glassesWidth / 2)
                val top = centerY - (glassesHeight * 0.55f)

                // Yüz eğimi
                val angle = Math.toDegrees(Math.atan2(
                    (rightEyePos.y - leftEyePos.y).toDouble(),
                    (rightEyePos.x - leftEyePos.x).toDouble()
                )).toFloat()

                // Matrix
                val matrix = Matrix().apply {
                    postScale(
                        glassesWidth / glassesBitmap.width,
                        glassesHeight / glassesBitmap.height
                    )
                    postScale(1f, -1f, glassesWidth / 2, glassesHeight / 2)
                    postRotate(angle, glassesWidth / 2, glassesHeight / 2)
                    postTranslate(left, top)
                }

                canvas.drawBitmap(glassesBitmap, matrix, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gözlük çizim hatası", e)
        }
    }

    /**
     * Bıyık çizimi - DİK KONUM optimized
     */
    private fun drawMustache(canvas: Canvas, face: Face, mustacheBitmap: Bitmap) {
        try {
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)
            val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)
            val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)

            if (nose != null && mouthLeft != null && mouthRight != null) {
                val nosePos = mapPoint(nose.position)
                val mouthLeftPos = mapPoint(mouthLeft.position)
                val mouthRightPos = mapPoint(mouthRight.position)

                // Ağız genişliği
                val mouthWidth = Math.abs(mouthRightPos.x - mouthLeftPos.x)

                // Bıyık boyutu
                val mustacheWidth = mouthWidth * 1.6f
                val aspectRatio = mustacheBitmap.width.toFloat() / mustacheBitmap.height
                val mustacheHeight = mustacheWidth / aspectRatio

                // Pozisyon
                val centerX = (mouthLeftPos.x + mouthRightPos.x) / 2
                val centerY = (nosePos.y + (mouthLeftPos.y + mouthRightPos.y) / 2) / 2
                val left = centerX - (mustacheWidth / 2)
                val top = centerY - (mustacheHeight / 2)

                // Yüz eğimi
                val angle = Math.toDegrees(Math.atan2(
                    (mouthRightPos.y - mouthLeftPos.y).toDouble(),
                    (mouthRightPos.x - mouthLeftPos.x).toDouble()
                )).toFloat()

                // Matrix
                val matrix = Matrix().apply {
                    postScale(
                        mustacheWidth / mustacheBitmap.width,
                        mustacheHeight / mustacheBitmap.height
                    )
                    postScale(1f, -1f, mustacheWidth / 2, mustacheHeight / 2)
                    postRotate(angle, mustacheWidth / 2, mustacheHeight / 2)
                    postTranslate(left, top)
                }

                canvas.drawBitmap(mustacheBitmap, matrix, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bıyık çizim hatası", e)
        }
    }

    /**
     * Şapka çizimi - DİK KONUM optimized
     */
    private fun drawHat(canvas: Canvas, face: Face, hatBitmap: Bitmap) {
        try {
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

            if (leftEye != null && rightEye != null) {
                val leftEyePos = mapPoint(leftEye.position)
                val rightEyePos = mapPoint(rightEye.position)

                // Gözler arası mesafe
                val eyeDistance = Math.abs(rightEyePos.x - leftEyePos.x)

                // Şapka boyutu
                val hatWidth = eyeDistance * 3.0f
                val aspectRatio = hatBitmap.width.toFloat() / hatBitmap.height
                val hatHeight = hatWidth / aspectRatio

                // Pozisyon
                val centerX = (leftEyePos.x + rightEyePos.x) / 2
                val top = (leftEyePos.y + rightEyePos.y) / 2 - hatHeight * 0.85f
                val left = centerX - (hatWidth / 2)

                // Yüz eğimi
                val angle = Math.toDegrees(Math.atan2(
                    (rightEyePos.y - leftEyePos.y).toDouble(),
                    (rightEyePos.x - leftEyePos.x).toDouble()
                )).toFloat()

                // Matrix
                val matrix = Matrix().apply {
                    postScale(
                        hatWidth / hatBitmap.width,
                        hatHeight / hatBitmap.height
                    )
                    postScale(1f, -1f, hatWidth / 2, hatHeight / 2)
                    postRotate(angle, hatWidth / 2, hatHeight / 2)
                    postTranslate(left, top)
                }

                canvas.drawBitmap(hatBitmap, matrix, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Şapka çizim hatası", e)
        }
    }

    /**
     * Fine-tuning için kalibrasyon değerlerini ayarla
     */
    fun adjustPosition(deltaX: Float, deltaY: Float) {
        // Bu değerleri global değişken yapıp ayarlayabilirsiniz
        Log.d(TAG, "Position adjusted: deltaX=$deltaX, deltaY=$deltaY")
        invalidate()
    }
}
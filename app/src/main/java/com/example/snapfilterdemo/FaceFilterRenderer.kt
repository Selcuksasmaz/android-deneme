package com.example.snapfilterdemo

import android.graphics.*
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

// Yüz filtrelerini çizen sınıf
class FaceFilterRenderer {
    private val TAG = "FaceFilterRenderer"

    // Gözlük çizim fonksiyonu
    fun drawGlasses(
        canvas: Canvas,
        face: Face,
        glassesBitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        rotationDegrees: Int
    ) {
        try {
            // Göz landmark'larını al
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

            if (leftEye != null && rightEye != null) {
                // Landmark pozisyonlarını dönüştür
                val leftEyePos = CoordinateMapper.mapPoint(
                    leftEye.position,
                    imageWidth,
                    imageHeight,
                    targetWidth,
                    targetHeight,
                    rotationDegrees
                )

                val rightEyePos = CoordinateMapper.mapPoint(
                    rightEye.position,
                    imageWidth,
                    imageHeight,
                    targetWidth,
                    targetHeight,
                    rotationDegrees
                )

                // Gözlerin X koordinat farkını bul
                val eyeDiffX = Math.abs(rightEyePos.x - leftEyePos.x)

                // Gözlük bitmap'inin orijinal boyutları
                val originalWidth = glassesBitmap.width
                val originalHeight = glassesBitmap.height

                // Gözlük genişliği, göz mesafesinin 2.5 katı olsun
                val glassesWidth = eyeDiffX * 2.5f

                // Gözlük yüksekliğini orantılı hesapla
                val aspectRatio = originalWidth.toFloat() / originalHeight
                val glassesHeight = glassesWidth / aspectRatio

                // İki göz arasındaki orta nokta
                val centerX = (leftEyePos.x + rightEyePos.x) / 2
                val centerY = (leftEyePos.y + rightEyePos.y) / 2

                // Gözlüğün sol üst köşesini hesapla
                val left = centerX - (glassesWidth / 2)
                val top = centerY - (glassesHeight / 2)

                // Yüzün açısını hesapla
                val angle = Math.toDegrees(Math.atan2(
                    (rightEyePos.y - leftEyePos.y).toDouble(),
                    (rightEyePos.x - leftEyePos.x).toDouble()
                )).toFloat()

                // Dönüşüm matrisi oluştur
                val matrix = Matrix()

                // Ölçekle
                matrix.postScale(
                    glassesWidth / originalWidth,
                    glassesHeight / originalHeight
                )

                // X ekseni etrafında flip uygula (yukarıdan aşağı çevir)
                matrix.postScale(1f, -1f, glassesWidth / 2, glassesHeight / 2)

                // Açıya göre döndür
                matrix.postRotate(angle, glassesWidth / 2, glassesHeight / 2)

                // Konumlandır
                matrix.postTranslate(left, top)

                // Gözlüğü çiz
                canvas.drawBitmap(glassesBitmap, matrix, null)
                Log.d(TAG, "Gözlük çizildi: Pozisyon($left,$top), Boyut($glassesWidth,$glassesHeight)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gözlük çizim hatası: ${e.message}")
            e.printStackTrace()
        }
    }

    // Bıyık çizim fonksiyonu
    fun drawMustache(
        canvas: Canvas,
        face: Face,
        mustacheBitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        rotationDegrees: Int
    ) {
        try {
            // İlgili landmarkları al
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)
            val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
            val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)
            val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)

            if (nose != null && mouthBottom != null && leftMouth != null && rightMouth != null) {
                // Landmark pozisyonlarını dönüştür
                val nosePos = CoordinateMapper.mapPoint(
                    nose.position,
                    imageWidth,
                    imageHeight,
                    targetWidth,
                    targetHeight,
                    rotationDegrees
                )

                val mouthBottomPos = CoordinateMapper.mapPoint(
                    mouthBottom.position,
                    imageWidth,
                    imageHeight,
                    targetWidth,
                    targetHeight,
                    rotationDegrees
                )

                val leftMouthPos = CoordinateMapper.mapPoint(
                    leftMouth.position,
                    imageWidth,
                    imageHeight,
                    targetWidth,
                    targetHeight,
                    rotationDegrees
                )

                val rightMouthPos = CoordinateMapper.mapPoint(
                    rightMouth.position,
                    imageWidth,
                    imageHeight,
                    targetWidth,
                    targetHeight,
                    rotationDegrees
                )

                // Ağız genişliği
                val mouthWidth = Math.abs(rightMouthPos.x - leftMouthPos.x)

                // Bıyık bitmap'inin orijinal boyutları
                val originalWidth = mustacheBitmap.width
                val originalHeight = mustacheBitmap.height

                // Bıyık genişliği, ağız genişliğinin 1.5 katı olsun
                val mustacheWidth = mouthWidth * 1.5f

                // Bıyık yüksekliğini orantılı hesapla
                val aspectRatio = originalWidth.toFloat() / originalHeight
                val mustacheHeight = mustacheWidth / aspectRatio

                // Orta noktalar
                val centerX = (leftMouthPos.x + rightMouthPos.x) / 2
                // Burun ve ağız arasında bir konum
                val centerY = (nosePos.y + mouthBottomPos.y) / 2 - mustacheHeight * 0.3f

                // Dönüşüm matrisi oluştur
                val matrix = Matrix()

                // Ölçekle
                matrix.postScale(
                    mustacheWidth / originalWidth,
                    mustacheHeight / originalHeight
                )

                // X ekseni etrafında flip uygula (yukarıdan aşağı çevir)
                matrix.postScale(1f, -1f, mustacheWidth / 2, mustacheHeight / 2)

                // Bıyığın ortalanmış konumunu hesapla
                val left = centerX - (mustacheWidth / 2)
                val top = centerY - (mustacheHeight / 2)

                // Yüzün açısını hesapla
                val angle = Math.toDegrees(Math.atan2(
                    (rightMouthPos.y - leftMouthPos.y).toDouble(),
                    (rightMouthPos.x - leftMouthPos.x).toDouble()
                )).toFloat()

                // Açıya göre döndür
                matrix.postRotate(angle, mustacheWidth / 2, mustacheHeight / 2)

                // Konumlandır
                matrix.postTranslate(left, top)

                // Bıyığı çiz
                canvas.drawBitmap(mustacheBitmap, matrix, null)
                Log.d(TAG, "Bıyık çizildi: Pozisyon($left,$top), Boyut($mustacheWidth,$mustacheHeight)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bıyık çizim hatası: ${e.message}")
            e.printStackTrace()
        }
    }

    // Şapka çizim fonksiyonu
    fun drawHat(
        canvas: Canvas,
        face: Face,
        hatBitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        rotationDegrees: Int
    ) {
        try {
            // İlgili landmarkları al
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)

            // Yüz kutusu
            val faceBox = face.boundingBox
            // Dönüştürülmüş yüz kutusu
            val mappedFaceBox = CoordinateMapper.mapRect(
                faceBox,
                imageWidth,
                imageHeight,
                targetWidth,
                targetHeight,
                rotationDegrees
            )

            val faceWidth = mappedFaceBox.width()
            val faceCenterX = mappedFaceBox.centerX()

            // Şapka bitmap'inin orijinal boyutları
            val originalWidth = hatBitmap.width
            val originalHeight = hatBitmap.height

            // Şapka genişliği, yüz genişliğinin 1.8 katı olsun
            val hatWidth = faceWidth * 1.8f

            // Şapka yüksekliğini orantılı hesapla
            val aspectRatio = originalWidth.toFloat() / originalHeight
            val hatHeight = hatWidth / aspectRatio

            // Şapka merkezi X - yüz merkezi ile aynı
            val centerX = faceCenterX

            // Şapka Y pozisyonu - yüzün üst kısmından biraz yukarıda
            var hatTopY = mappedFaceBox.top - (hatHeight * 0.6f)

            // Göz landmarkları varsa, daha doğru pozisyon hesapla
            if (leftEye != null && rightEye != null) {
                val leftEyePos = CoordinateMapper.mapPoint(
                    leftEye.position,
                    imageWidth,
                    imageHeight,
                    targetWidth,
                    targetHeight,
                    rotationDegrees
                )

                val rightEyePos = CoordinateMapper.mapPoint(
                    rightEye.position,
                    imageWidth,
                    imageHeight,
                    targetWidth,
                    targetHeight,
                    rotationDegrees
                )

                // Gözlerin daha yukarısına yerleştir
                val eyeY = Math.min(leftEyePos.y, rightEyePos.y)
                hatTopY = eyeY - (faceWidth * 0.7f)
            }

            // Dönüşüm matrisi oluştur
            val matrix = Matrix()

            // Ölçekle
            matrix.postScale(
                hatWidth / originalWidth,
                hatHeight / originalHeight
            )

            // X ekseni etrafında flip uygula (yukarıdan aşağı çevir)
            matrix.postScale(1f, -1f, hatWidth / 2, hatHeight / 2)

            // Şapkanın sol üst köşesini hesapla
            val left = centerX - (hatWidth / 2)
            val top = hatTopY

            // Yüz eğimine göre şapkayı döndür
            val angle = face.headEulerAngleZ
            if (Math.abs(angle) > 5) {
                matrix.postRotate(angle, hatWidth / 2, hatHeight / 2)
            }

            // Konumlandır
            matrix.postTranslate(left, top)

            // Şapkayı çiz
            canvas.drawBitmap(hatBitmap, matrix, null)
            Log.d(TAG, "Şapka çizildi: Pozisyon($left,$top), Boyut($hatWidth,$hatHeight), Açı($angle)")
        } catch (e: Exception) {
            Log.e(TAG, "Şapka çizim hatası: ${e.message}")
            e.printStackTrace()
        }
    }
}
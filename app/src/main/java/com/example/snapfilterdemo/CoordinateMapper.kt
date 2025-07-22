package com.example.snapfilterdemo

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.cos
import kotlin.math.sin

// Kamera koordinatlarını ekran koordinatlarına dönüştürmek için yardımcı sınıf
object CoordinateMapper {
    private const val TAG = "CoordinateMapper"

    // Nokta dönüştürme metodu - Landmark'lar için tam düzeltme
    fun mapPoint(
        point: PointF,
        imageWidth: Int,
        imageHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        rotationDegrees: Int
    ): PointF {
        val mappedPoint = PointF()

        // CameraX ile ML Kit arasındaki koordinat sistemindeki farkı düzelt
        when (rotationDegrees) {
            0 -> {
                // Doğrudan ölçekleme ve X eksenini aynala (ön kamera için)
                mappedPoint.x = imageWidth - point.x
                mappedPoint.y = point.y
            }
            90 -> {
                // 90 derece rotasyon için düzeltme ve X eksenini aynala (ön kamera için)
                mappedPoint.x = imageHeight - point.y
                mappedPoint.y = point.x
            }
            180 -> {
                // 180 derece rotasyon için düzeltme
                mappedPoint.x = point.x
                mappedPoint.y = imageHeight - point.y
            }
            270 -> {
                // 270 derece rotasyon için düzeltme
                mappedPoint.x = point.y
                mappedPoint.y = imageWidth - point.x
            }
            else -> {
                Log.e(TAG, "Desteklenmeyen rotasyon açısı: $rotationDegrees")
                mappedPoint.x = point.x
                mappedPoint.y = point.y
            }
        }

        // İlk orijinal görüntü boyutundan hedef boyuta ölçekle
        mappedPoint.x = mappedPoint.x * targetWidth / imageWidth
        mappedPoint.y = mappedPoint.y * targetHeight / imageHeight

        // Ek düzeltmeler - gerçek cihaz ve kamera kombinasyonu için ince ayar
        // Landmark düzeltme faktörleri
        val xOffset = 0f        // X ekseninde kaydırma (pozitif değer sağa kaydırır)
        val yOffset = 0f        // Y ekseninde kaydırma (pozitif değer aşağı kaydırır)
        val xScale = 1.0f       // X ekseninde ölçekleme faktörü
        val yScale = 1.0f       // Y ekseninde ölçekleme faktörü

        // Merkez noktaya göre ölçekleme yap
        val centerX = targetWidth / 2f
        val centerY = targetHeight / 2f

        // Merkeze göre vektör hesapla
        val dx = mappedPoint.x - centerX
        val dy = mappedPoint.y - centerY

        // Ölçekleme faktörlerini uygula
        mappedPoint.x = centerX + dx * xScale + xOffset
        mappedPoint.y = centerY + dy * yScale + yOffset

        // Ek olarak, cihaza özgü (Xiaomi M2101K7BG için) düzeltme
        // Bu değerleri deneme yanılma ile ayarlayın
        if (rotationDegrees == 90) {
            // Ön kamerada 90 derece rotasyon için ek düzeltme
            // X ekseninde çevirme işlemi (ayna etkisi)
            mappedPoint.x = targetWidth - mappedPoint.x

            // Y ekseninde küçük bir düzeltme - landmark'ları daha doğru konumlandırmak için
            // Bu değeri yüz landmark'larının konumu için ayarlayın
            mappedPoint.y = mappedPoint.y * 0.95f + targetHeight * 0.025f
        }

        return mappedPoint
    }

    // Dikdörtgen dönüştürme
    fun mapRect(
        rect: android.graphics.Rect,
        imageWidth: Int,
        imageHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        rotationDegrees: Int
    ): RectF {
        // Dörtgenin dört köşesini dönüştürelim
        val topLeft = mapPoint(
            PointF(rect.left.toFloat(), rect.top.toFloat()),
            imageWidth, imageHeight, targetWidth, targetHeight, rotationDegrees
        )
        val topRight = mapPoint(
            PointF(rect.right.toFloat(), rect.top.toFloat()),
            imageWidth, imageHeight, targetWidth, targetHeight, rotationDegrees
        )
        val bottomLeft = mapPoint(
            PointF(rect.left.toFloat(), rect.bottom.toFloat()),
            imageWidth, imageHeight, targetWidth, targetHeight, rotationDegrees
        )
        val bottomRight = mapPoint(
            PointF(rect.right.toFloat(), rect.bottom.toFloat()),
            imageWidth, imageHeight, targetWidth, targetHeight, rotationDegrees
        )

        // Min ve max değerleri bularak yeni dikdörtgeni oluştur
        return RectF(
            minOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x),
            minOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y),
            maxOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x),
            maxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
        )
    }
}
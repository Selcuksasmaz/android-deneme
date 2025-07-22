package com.example.snapfilterdemo

import com.google.mlkit.vision.face.FaceLandmark as MLKitFaceLandmark

object FaceLandmark {
    // ML Kit'in standart sabitleri ile değiştiriliyor
    const val LEFT_EYE = MLKitFaceLandmark.LEFT_EYE
    const val RIGHT_EYE = MLKitFaceLandmark.RIGHT_EYE
    const val NOSE_BASE = MLKitFaceLandmark.NOSE_BASE
    const val MOUTH_BOTTOM = MLKitFaceLandmark.MOUTH_BOTTOM
    const val MOUTH_LEFT = MLKitFaceLandmark.MOUTH_LEFT
    const val MOUTH_RIGHT = MLKitFaceLandmark.MOUTH_RIGHT
}
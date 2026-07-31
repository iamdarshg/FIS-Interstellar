package org.hound.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await
import org.hound.domain.BoundingBox

class MlKitCandidateFinder : CandidateFinder {

    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()

    private val detector = ObjectDetection.getClient(options)

    override suspend fun candidates(frame: Frame, isLearning: Boolean): List<Candidate> {
        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(frame.width * frame.height)

        for (i in 0 until frame.width * frame.height) {
            val r = frame.rgbData[i * 3].toInt() and 0xFF
            val g = frame.rgbData[i * 3 + 1].toInt() and 0xFF
            val b = frame.rgbData[i * 3 + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val objects: List<DetectedObject> = try {
            detector.process(inputImage).await()
        } catch (e: Exception) {
            emptyList()
        } finally {
            bitmap.recycle()
        }

        val candidatesList = mutableListOf<Candidate>()

        for (obj in objects) {
            val rect = obj.boundingBox
            val xMin = (rect.left.toFloat() / frame.width).coerceIn(0.0f, 1.0f)
            val yMin = (rect.top.toFloat() / frame.height).coerceIn(0.0f, 1.0f)
            val xMax = (rect.right.toFloat() / frame.width).coerceIn(xMin, 1.0f)
            val yMax = (rect.bottom.toFloat() / frame.height).coerceIn(yMin, 1.0f)

            val widthFrac = xMax - xMin
            val heightFrac = yMax - yMin
            val areaFraction = widthFrac * heightFrac

            if (areaFraction in 0.01f..0.90f) {
                candidatesList.add(
                    Candidate(
                        box = BoundingBox(xMin, yMin, xMax, yMax),
                        areaFraction = areaFraction,
                        source = CandidateSource.DETECTOR
                    )
                )
            }
        }

        val sorted = candidatesList.sortedByDescending { it.areaFraction }.take(5)

        if (sorted.isEmpty() && isLearning) {
            val fallbackBox = BoundingBox(0.2f, 0.2f, 0.8f, 0.8f)
            return listOf(
                Candidate(
                    box = fallbackBox,
                    areaFraction = 0.36f,
                    source = CandidateSource.LEARN_CENTER_FALLBACK
                )
            )
        }

        return sorted
    }
}

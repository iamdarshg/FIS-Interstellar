package org.hound.vision

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null
    private var isRunning = false

    fun start(onFrame: (Frame) -> Unit) {
        if (isRunning) return
        isRunning = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(320, 240))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    if (!isRunning) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val frame = convertImageProxyToFrame(imageProxy)
                    if (frame != null) {
                        onFrame(frame)
                    }
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, analysis)
            imageAnalysis = analysis
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        isRunning = false
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraExecutor.shutdown()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    companion object {
        fun convertImageProxyToFrame(imageProxy: ImageProxy): Frame? {
            val planes = imageProxy.planes
            if (planes.size < 3) return null

            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuffer = ByteArray(yPlane.buffer.remaining())
            yPlane.buffer.get(yBuffer)

            val uBuffer = ByteArray(uPlane.buffer.remaining())
            uPlane.buffer.get(uBuffer)

            val vBuffer = ByteArray(vPlane.buffer.remaining())
            vPlane.buffer.get(vBuffer)

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val width = imageProxy.width
            val height = imageProxy.height

            val rgbData = YuvToRgb.convertYuv420ToRgb(
                yBuffer = yBuffer,
                uBuffer = uBuffer,
                vBuffer = vBuffer,
                width = width,
                height = height,
                yRowStride = yPlane.rowStride,
                uvRowStride = uPlane.rowStride,
                uvPixelStride = uPlane.pixelStride,
                rotationDegrees = rotationDegrees
            )

            val outWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
            val outHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

            return Frame(
                width = outWidth,
                height = outHeight,
                rgbData = rgbData,
                timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L,
                onClose = { }
            )
        }
    }
}

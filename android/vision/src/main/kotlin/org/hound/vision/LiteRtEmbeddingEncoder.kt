package org.hound.vision

import android.content.Context
import org.hound.domain.l2Normalize
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class LiteRtEmbeddingEncoder(
    private val interpreter: Interpreter
) : EmbeddingEncoder {

    private val lock = Any()

    init {
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)

        val inputShape = inputTensor.shape()
        val outputShape = outputTensor.shape()

        require(inputShape.contentEquals(intArrayOf(1, 128, 128, 3))) {
            "MODEL_CONTRACT_MISMATCH: Input shape must be [1, 128, 128, 3], was ${inputShape.contentToString()}"
        }
        require(outputShape.contentEquals(intArrayOf(1, 576))) {
            "MODEL_CONTRACT_MISMATCH: Output shape must be [1, 576], was ${outputShape.contentToString()}"
        }
    }

    override fun encode(input: ByteBuffer): FloatArray {
        synchronized(lock) {
            val outputBuffer = ByteBuffer.allocateDirect(576).order(ByteOrder.nativeOrder())
            interpreter.run(input, outputBuffer)
            outputBuffer.rewind()

            val quantized = ByteArray(576)
            outputBuffer.get(quantized)

            val outputTensor = interpreter.getOutputTensor(0)
            val quantParams = outputTensor.quantizationParams()
            val scale = if (quantParams.scale != 0.0f) quantParams.scale else 1.0f
            val zeroPoint = quantParams.zeroPoint

            val floatValues = FloatArray(576) { i ->
                val qVal = quantized[i].toInt()
                scale * (qVal - zeroPoint)
            }

            return l2Normalize(floatValues)
        }
    }

    override fun close() {
        synchronized(lock) {
            interpreter.close()
        }
    }

    companion object {
        fun fromAssets(context: Context, assetName: String = "hound_embedding_v1.tflite"): LiteRtEmbeddingEncoder {
            val fileDescriptor = context.assets.openFd(assetName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val interpreter = Interpreter(mappedByteBuffer, options)
            return LiteRtEmbeddingEncoder(interpreter)
        }
    }
}

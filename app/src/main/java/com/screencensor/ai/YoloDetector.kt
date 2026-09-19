package com.screencensor.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.screencensor.model.CensorConfig
import com.screencensor.model.DetailedCategory
import com.screencensor.model.DetectionBox
import com.screencensor.model.YoloLabels
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

class YoloDetector(private val context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "YoloDetector"
        const val INPUT_SIZE = 320
        const val NUM_CLASSES = 16
        const val NUM_CHANNELS = 20  // 4 coords + 16 classes
        const val NUM_ANCHORS = 2100 // YOLOv11 Nano 320 anchors
        const val DEFAULT_IOU_THRESHOLD = 0.45f
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    private val pixelArray = IntArray(INPUT_SIZE * INPUT_SIZE)
    private val inputFloatBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(1 * 3 * INPUT_SIZE * INPUT_SIZE * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var phraseIndex = 0

    init {
        initSession()
    }

    private fun initSession() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            val modelBytes = context.assets.open("model.onnx").use { it.readBytes() }
            ortSession = ortEnv?.createSession(modelBytes, sessionOptions)
            Log.i(TAG, "ONNX Runtime YOLOv11 Nano model loaded successfully (${modelBytes.size / 1024 / 1024} MB).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX Runtime: ${e.message}", e)
        }
    }

    @Synchronized
    fun detect(bitmap: Bitmap, config: CensorConfig): List<DetectionBox> {
        val session = ortSession ?: return emptyList()
        val env = ortEnv ?: return emptyList()

        // 1. Scale down to 320x320 if needed
        val scaledBitmap = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }

        // 2. Preprocess: Extract RGB and normalize to [0..1] in NCHW format
        scaledBitmap.getPixels(pixelArray, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        inputFloatBuffer.clear()
        val channelSize = INPUT_SIZE * INPUT_SIZE
        val rOffset = 0
        val gOffset = channelSize
        val bOffset = channelSize * 2

        for (i in 0 until channelSize) {
            val color = pixelArray[i]
            inputFloatBuffer.put(rOffset + i, (((color shr 16) and 0xFF) / 255.0f))
            inputFloatBuffer.put(gOffset + i, (((color shr 8) and 0xFF) / 255.0f))
            inputFloatBuffer.put(bOffset + i, ((color and 0xFF) / 255.0f))
        }
        inputFloatBuffer.position(0)

        // 3. Create Input Tensor [1, 3, 320, 320]
        val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val inputTensor = OnnxTensor.createTensor(env, inputFloatBuffer, inputShape)

        val candidates = mutableListOf<DetectionBox>()

        try {
            // 4. Run Model
            val results = session.run(Collections.singletonMap("images", inputTensor))
            val outputTensor = results[0] as? OnnxTensor ?: return emptyList()

            @Suppress("UNCHECKED_CAST")
            val outputData = outputTensor.value as Array<Array<FloatArray>>
            val tensor2D = outputData[0] // [20][2100]

            val phrases = if (config.phrases.isNotEmpty()) config.phrases else listOf("CENSORED")

            // 5. Parse 2100 Anchors
            for (anchorIdx in 0 until NUM_ANCHORS) {
                var maxScore = -1.0f
                var bestClassId = -1

                for (c in 0 until NUM_CLASSES) {
                    val score = tensor2D[4 + c][anchorIdx]
                    if (score > maxScore) {
                        maxScore = score
                        bestClassId = c
                    }
                }

                if (maxScore >= config.confidenceThreshold && bestClassId >= 0) {
                    val category = YoloLabels.getCategory(bestClassId)

                    // Check enabled category in user config
                    val isEnabled = when (category) {
                        DetailedCategory.BREASTS_EXPOSED -> config.censorBreastsExposed
                        DetailedCategory.BREASTS_COVERED -> config.censorBreastsCovered
                        DetailedCategory.GENITALIA_EXPOSED -> config.censorGenitaliaExposed
                        DetailedCategory.GENITALIA_COVERED -> config.censorGenitaliaCovered
                        DetailedCategory.BUTTOCKS_EXPOSED -> config.censorButtocksExposed
                        DetailedCategory.BUTTOCKS_COVERED -> config.censorButtocksCovered
                        DetailedCategory.EYE -> config.censorEyes
                        DetailedCategory.FACE -> config.censorFace
                        DetailedCategory.BELLY -> config.censorBelly
                        DetailedCategory.ARMPITS -> config.censorArmpits
                        DetailedCategory.FEET -> config.censorFeet
                        DetailedCategory.OTHER -> false
                    }

                    if (isEnabled) {
                        val cx = tensor2D[0][anchorIdx]
                        val cy = tensor2D[1][anchorIdx]
                        val w = tensor2D[2][anchorIdx]
                        val h = tensor2D[3][anchorIdx]

                        val left = max(0f, (cx - w / 2f) / INPUT_SIZE)
                        val top = max(0f, (cy - h / 2f) / INPUT_SIZE)
                        val right = min(1f, (cx + w / 2f) / INPUT_SIZE)
                        val bottom = min(1f, (cy + h / 2f) / INPUT_SIZE)

                        val className = if (bestClassId < YoloLabels.NAMES.size) YoloLabels.NAMES[bestClassId] else "UNKNOWN"
                        val phrase = phrases[phraseIndex % phrases.size]
                        phraseIndex++

                        candidates.add(
                            DetectionBox(
                                rect = RectF(left, top, right, bottom),
                                classId = bestClassId,
                                className = className,
                                score = maxScore,
                                category = category,
                                phrase = phrase
                            )
                        )
                    }
                }
            }

            results.close()
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}", e)
        } finally {
            inputTensor.close()
        }

        // 6. NMS Suppression
        return applyNms(candidates, DEFAULT_IOU_THRESHOLD)
    }

    private fun applyNms(boxes: List<DetectionBox>, iouThreshold: Float): List<DetectionBox> {
        if (boxes.isEmpty()) return emptyList()

        val sorted = boxes.sortedByDescending { it.score }.toMutableList()
        val selected = mutableListOf<DetectionBox>()

        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            selected.add(current)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (computeIoU(current.rect, next.rect) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selected
    }

    private fun computeIoU(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        if (interArea <= 0f) return 0f

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    override fun close() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX session: ${e.message}")
        }
    }
}

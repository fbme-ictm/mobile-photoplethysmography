package dev.albertov.pletysmo_fbmi

import android.Manifest.permission.CAMERA
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.os.Bundle
import android.util.Range
import android.util.Size
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

const val SAMPLING_FREQ = 30
const val SECONDS = 3

class MainActivity : AppCompatActivity() {
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var previewView: PreviewView
    private lateinit var camera: Camera
    private lateinit var textView: TextView
    private lateinit var graph: LineChart
    private var arrayData = mutableListOf<Entry>()
    private var values = mutableListOf<Float>()
    private lateinit var lineData: LineData
    private var hrComp = HRComputer()

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startMeasuring()
            } else {
                Toast.makeText(
                     this,
                    "No permission, will not work.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        graph = findViewById(R.id.graph)
        textView = findViewById(R.id.textView)

        when {
            ContextCompat.checkSelfPermission(
                this,
                CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startMeasuring()
            }
            shouldShowRequestPermissionRationale(CAMERA) -> {
                Toast.makeText(this, "Need permission for camera", Toast.LENGTH_SHORT)
                    .show()
            }
            else -> {
                requestPermissionLauncher.launch(CAMERA)
            }
        }
    }

    private fun startMeasuring() {
        for (i in 0..SAMPLING_FREQ - 3) {
            values.add(0f)
        }

        graph.xAxis.isEnabled = false
        graph.axisLeft.isEnabled = false
        graph.axisRight.isEnabled = false
        graph.description.isEnabled = false
        graph.legend.isEnabled = false

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageAnalysisBase = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        val camera2InterOp = Camera2Interop.Extender(imageAnalysisBase)
        camera2InterOp.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_OFF
        )
        camera2InterOp.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            Range(SAMPLING_FREQ, SAMPLING_FREQ)
        )

        val imageAnalysis = imageAnalysisBase.build()

        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
            val bitmap = imageProxy.image?.toBitmap()
            if (bitmap !== null) {
                var rColor = 0
                val height = bitmap.height
                val width = bitmap.width
                var n = 0
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                var i = 0
                while (i < pixels.size) {
                    val color = pixels[i]
                    rColor += Color.red(color)
                    n++
                    i += 1
                }
                runOnUiThread {
                    if (values.size > SAMPLING_FREQ * SECONDS) {
                        values.remove(values[0])
                    }
                    values.add(rColor.toFloat() / n.toFloat())
                    arrayData.clear()
                    var a = 0f
                    for (value in values) {
                        arrayData.add(Entry(a, value))
                        a++
                    }
                    textView.text = hrComp.getHr(values).toInt().toString()
                    val dataSet = LineDataSet(arrayData, "")
                    lineData = LineData(dataSet)
                    dataSet.setDrawCircles(false)
                    dataSet.setDrawValues(false)
                    dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
                    graph.data = lineData
                    graph.invalidate()
                }
                bitmap.recycle()
            }
            imageProxy.close()
        }

        camera = cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            imageAnalysis,
            preview
        )
        camera.cameraControl.enableTorch(true)
    }

    private fun Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val vuBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
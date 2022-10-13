package dev.albertov.pletysmo_fbmi

import android.Manifest.permission.CAMERA
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
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

        checkPermissions()
    }

    private fun checkPermissions(){
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

    private fun setupGraph() {
        graph.xAxis.isEnabled = false
        graph.axisLeft.isEnabled = false
        graph.axisRight.isEnabled = false
        graph.description.isEnabled = false
        graph.legend.isEnabled = false
    }

    private fun startMeasuring() {
        fillArrayOfValuesWithZeros()
        setupGraph()
        binCameraToPreview()
    }

    private fun fillArrayOfValuesWithZeros(){
        for (i in 0..SAMPLING_FREQ - 3) {
            values.add(0f)
        }
    }

    private fun binCameraToPreview(){
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun graphData() {
        val dataSet = LineDataSet(arrayData, "")
        lineData = LineData(dataSet)
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        graph.data = lineData
        graph.invalidate()
    }

    private fun computeAndPrintHR() {
        arrayData.clear()

        var a = 0f
        for (value in values) {
            arrayData.add(Entry(a, value))
            a++
        }

        textView.text = hrComp.getHr(values).toInt().toString()
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
                val rColor = bitmap.getAverageRedColor()
                runOnUiThread {
                    if (rColor > 100) {
                        if (values.size > SAMPLING_FREQ * SECONDS) {
                            values.remove(values[0])
                        }
                        values.add(rColor)

                        computeAndPrintHR()
                        graphData()
                    }
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
}
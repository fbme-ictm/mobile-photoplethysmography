package dev.albertov.pletysmo_fbmi

import android.Manifest.permission.CAMERA
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Range
import android.util.Size
import android.widget.Button
import android.widget.EditText
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.Executors


const val SAMPLING_FREQ = 30
const val SECONDS = 5

class MainActivity : AppCompatActivity() {
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var previewView: PreviewView
    private lateinit var camera: Camera
    private lateinit var textView: TextView
    private lateinit var editTextTextPersonName: EditText
    private lateinit var thermoButton: Button
    private lateinit var photoButton: Button
    private lateinit var graph: LineChart
    private var arrayData = mutableListOf<Entry>()
    private var values = mutableListOf<Float>()
    private lateinit var lineData: LineData
    private var hrComp = HRComputer()
    val REQUESTCODE = 555

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        graph = findViewById(R.id.graph)
        textView = findViewById(R.id.textView)
        thermoButton = findViewById(R.id.thermoButton)
        photoButton = findViewById(R.id.photoButton)
        editTextTextPersonName = findViewById(R.id.editTextTextPersonName)

        checkPermissions(CAMERA)

        thermoButton.setOnClickListener {
            thermalPrinter()
        }

        photoButton.setOnClickListener {
            exportImage()
        }


    }

    private fun exportImage() {
        val filename = "${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            val canvasManipulator = CanvasManipulator(this)
            canvasManipulator.drawGraph(graph)
            canvasManipulator.drawText(textView.text.toString(), editTextTextPersonName.text.toString())
            canvasManipulator.drawQR()
            val bmp = canvasManipulator.getBitmap()
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
    }

    private fun thermalPrinter() {
        ThermalPrinter().print(values)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUESTCODE){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMeasuring()
            } else {
                Toast.makeText(
                    this,
                    "No permission, will not work.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkPermissions(permission: String ) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                startMeasuring()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "Need all permissions", Toast.LENGTH_SHORT)
                    .show()
            }
            else -> {
                //requestPermissionLauncher.launch(permission)
                ActivityCompat.requestPermissions(this, arrayOf(CAMERA, WRITE_EXTERNAL_STORAGE), REQUESTCODE)
            }
        }
    }

    private fun setupGraph() {
        graph.setBackgroundColor(Color.BLACK);
        graph.setViewPortOffsets(0f,0f,0f,0f)
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

    private fun fillArrayOfValuesWithZeros() {
        for (i in 0..SAMPLING_FREQ - 3) {
            values.add(0f)
        }
    }

    private fun binCameraToPreview() {
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
        dataSet.setDrawFilled(true)
        val drawable = ContextCompat.getDrawable(this, R.drawable.shader)
        dataSet.setFillDrawable(drawable)
        dataSet.setColor(ContextCompat.getColor(this, R.color.purple_dark))
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

        val old = textView.text.toString().toFloat()
        val new = hrComp.getHr(values)

        textView.text = ((old + new) / 2).toInt().toString()
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
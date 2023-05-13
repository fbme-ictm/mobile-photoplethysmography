package dev.albertov.pletysmo_fbmi

import android.Manifest.permission.CAMERA
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CaptureRequest
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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
import com.brother.sdk.lmprinter.Channel
import com.brother.sdk.lmprinter.OpenChannelError
import com.brother.sdk.lmprinter.PrintError
import com.brother.sdk.lmprinter.PrinterDriverGenerateResult
import com.brother.sdk.lmprinter.PrinterDriverGenerator
import com.brother.sdk.lmprinter.PrinterModel
import com.brother.sdk.lmprinter.setting.PrintImageSettings
import com.brother.sdk.lmprinter.setting.QLPrintSettings
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
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
    private var camera: Camera? = null
    private lateinit var textView: TextView
    private lateinit var editTextTextPersonName: EditText
    private lateinit var brotherButton: Button
    private lateinit var graph: LineChart
    private var arrayData = mutableListOf<Entry>()
    private var values = mutableListOf<Float>()
    private lateinit var lineData: LineData
    private var hrComp = HRComputer()
    val REQUESTCODE = 555
    private val scope = CoroutineScope(newSingleThreadContext("name"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = "¯\\_(ツ)_/¯  FBMI - Fotopletysmo"

        previewView = findViewById(R.id.previewView)
        graph = findViewById(R.id.graph)
        textView = findViewById(R.id.textView)
        brotherButton = findViewById(R.id.brotherButton)
        editTextTextPersonName = findViewById(R.id.editTextTextPersonName)

        checkPermissions(CAMERA)

        brotherButton.setOnClickListener {
            Toast.makeText(this, "Printing!", Toast.LENGTH_LONG).show()
            runBlocking {
                exportImageBrother()
            }
        }
    }

    fun printImage(file: Bitmap) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val channel: Channel =
            Channel.newBluetoothChannel("58:93:D8:AB:C6:5F", bluetoothManager.adapter)
        val result: PrinterDriverGenerateResult = PrinterDriverGenerator.openChannel(channel)
        if (result.error.code !== OpenChannelError.ErrorCode.NoError) {
            Log.e("", "Error - Open Channel: " + result.error.code)
            return
        }
        val printerDriver = result.driver

        val printSettings = QLPrintSettings(PrinterModel.QL_820NWB)
        printSettings.labelSize = QLPrintSettings.LabelSize.DieCutW29H90
        printSettings.workPath = filesDir.absolutePath
        printSettings.imageRotation = PrintImageSettings.Rotation.Rotate90
        printSettings.isAutoCut = true


        val printError: PrintError = printerDriver.printImage(file, printSettings)
        if (printError.code != PrintError.ErrorCode.NoError) {
            Log.d("", "Error - Print Image: " + printError.code);
        } else {
            Log.d("", "Success - Print Image");
        }
        printerDriver.closeChannel();
    }

    private fun exportImageBrother() {
        scope.launch {
            val filename = "${System.currentTimeMillis()}.jpg"
            var fos: OutputStream? = null
            var imageF: File? = null

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
                MediaScannerConnection.scanFile(
                    this@MainActivity,
                    arrayOf(image.toString()),
                    null,
                    null
                )
                fos = FileOutputStream(image)
            }

            fos?.use {
                val canvasManipulator = CanvasManipulatorBrother(this@MainActivity)
                canvasManipulator.drawGraph(graph)
                canvasManipulator.drawText(
                    textView.text.toString(),
                    editTextTextPersonName.text.toString()
                )
                canvasManipulator.drawQR()
                val bmp = canvasManipulator.getBitmap()
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, it)
                printImage(bmp)

            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUESTCODE) {
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

    private fun checkPermissions(permission: String) {
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
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(CAMERA, WRITE_EXTERNAL_STORAGE),
                    REQUESTCODE
                )
            }
        }
    }

    private fun setupGraph() {
        graph.setBackgroundColor(Color.BLACK);
        graph.setViewPortOffsets(0f, 0f, 0f, 0f)
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

    public override fun onResume() {
        super.onResume()
            camera?.cameraControl?.enableTorch(true)
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
        camera?.cameraControl?.enableTorch(true)
    }
}
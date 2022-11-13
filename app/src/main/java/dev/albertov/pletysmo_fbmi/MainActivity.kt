package dev.albertov.pletysmo_fbmi

import android.Manifest.permission.CAMERA
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
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.common.util.concurrent.ListenableFuture
import com.indvd00m.ascii.render.Region
import com.indvd00m.ascii.render.Render
import com.indvd00m.ascii.render.api.IRender
import com.indvd00m.ascii.render.elements.Rectangle
import com.indvd00m.ascii.render.elements.plot.Axis
import com.indvd00m.ascii.render.elements.plot.Plot
import com.indvd00m.ascii.render.elements.plot.misc.PlotPoint
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
    private lateinit var thermoButton: Button
    private lateinit var photoButton: Button
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
        thermoButton = findViewById(R.id.thermoButton)
        photoButton = findViewById(R.id.photoButton)
        checkPermissions()

        thermoButton.setOnClickListener {
            thermalPrinter()
        }

        photoButton.setOnClickListener {
            exportImage()
        }


    }

    private fun exportImage() {
        //Generating a file name
        val filename = "${System.currentTimeMillis()}.jpg"

        //Output stream
        var fos: OutputStream? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //getting the contentResolver
            contentResolver?.also { resolver ->

                //Content resolver will process the contentvalues
                val contentValues = ContentValues().apply {

                    //putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                //Inserting the contentValues to contentResolver and getting the Uri
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                //Opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            //These for devices running on android < Q
            //So I don't think an explanation is needed here
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            val w: Int = 1024
            val h: Int = 651
            val p: Int = 80
            val conf = Bitmap.Config.ARGB_8888
            val bmp = Bitmap.createBitmap(w, h, conf)
            val canvas = Canvas(bmp)
            val paint = Paint()
            paint.setAntiAlias(true)
            paint.setFilterBitmap(true)
            paint.setDither(true)
            canvas.drawBitmap(graph.chartBitmap, null, RectF(0f+p, h.toFloat()-256f, w.toFloat()-p, h.toFloat()-p), null)
            val icon = BitmapFactory.decodeResource(resources, R.drawable.qr_v3)
            canvas.drawBitmap(icon, null, RectF(w.toFloat()-256f-p, 0f+p, w.toFloat()-p, 256f+p), null )

            var plain = resources.getFont(R.font.roboto_regular)

            val paint2 = Paint()
            paint2.setColor(ContextCompat.getColor(this, R.color.white))
            paint2.setTypeface(plain);
            paint2.setStyle(Paint.Style.FILL)
            val textSize = 50f
            paint2.textSize = textSize

            val paint3 = Paint()
            paint3.setTypeface(plain);
            paint3.setColor(ContextCompat.getColor(this, R.color.white))
            paint3.setStyle(Paint.Style.FILL)
            paint3.textSize = 25f
            canvas.drawText("FBMI ČVUT", p.toFloat(), p.toFloat()+textSize+2f, paint2)
            canvas.drawText("BPM: ${textView.text}", p.toFloat(), p.toFloat()+2*textSize+10f+2f, paint2)
            val date = DateFormat.format("dd-MM-yyyy HH:mm", Date())
            canvas.drawText("${date}", p.toFloat(), p.toFloat()+3*textSize+10f+2f, paint3)
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
    }

    private fun thermalPrinter() {
        Thread {
            val points = mutableListOf<PlotPoint>()

            values.forEachIndexed { index, fl ->
                points.add(PlotPoint(fl.toDouble(), index.toDouble()))
            }

            val render: IRender = Render()
            val builder = render.newBuilder()
            builder.width(28).height(40)
            builder.element(Rectangle(0, 0, 28, 40))
            builder.layer(Region(1, 1, 26, 38))
            builder.element(Axis(points.toList(), Region(0, 0, 26, 38)))
            //builder.element(AxisLabels(points.toList(), Region(0, 0, 26, 38)))
            builder.element(Plot(points.toList(), Region(0, 0, 26, 38)))
            val canvas = render.render(builder.build())
            var s = canvas.text
            s = s.replace("│", "|")
            s = s.replace("─", "_")
            var lines = "";
            s.lines().forEach {
                var line = "[L]$it\n"
                lines += line
            }

            val printer =
                EscPosPrinter(BluetoothPrintersConnections.selectFirstPaired(), 203, 48f, 32)
            printer
                .printFormattedText(
                    "[L]\n" +
                            lines +
                            "[L]\n" +
                            "[L]\n" +
                            "[L]\n" +
                            "[C]<qrcode size='45'>http://www.fbmi.cvut.cz/</qrcode>\n" +
                            "[L]\n"
                )
        }.start()
    }

    private fun checkPermissions() {
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
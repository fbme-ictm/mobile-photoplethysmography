package dev.albertov.pletysmo_fbmi

import android.content.Context
import android.graphics.*
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import java.util.*

class CanvasManipulatorBrother(val context: Context) {
    private val w = 1024
    private val h = 330
    private val p = 10

    private val conf = Bitmap.Config.ARGB_8888
    private val bmp = Bitmap.createBitmap(w, h, conf)
    private val canvas = Canvas(bmp)

    fun getBitmap(): Bitmap {
        return bmp
    }

    private fun getPaint(textSize: Float) : Paint {
        val paint = Paint()
        paint.setColor(ContextCompat.getColor(context, R.color.black))
        paint.setStyle(Paint.Style.FILL)
        paint.textSize = textSize
        return paint
    }

    fun drawText(hr: String, text:String){
        val mainTextSize = 30f
        canvas.drawText("Fotopletysmografie", p.toFloat(), p.toFloat()+mainTextSize+2f, getPaint(mainTextSize))
        canvas.drawText("BPM: ${hr}", p.toFloat(), p.toFloat()+2*mainTextSize+10f+2f, getPaint(mainTextSize))
        val date = DateFormat.format("dd-MM-yyyy HH:mm", Date())
        canvas.drawText("${date}", p.toFloat(), p.toFloat()+3*mainTextSize+10f+2f, getPaint(25f))
        canvas.drawText("${text}", p.toFloat(), p.toFloat()+4*mainTextSize+10f+2f, getPaint(25f))
    }

    fun drawGraph(graph: LineChart){
        bmp.eraseColor(Color.WHITE)
        val paint = Paint()
        paint.setAntiAlias(true)
        paint.setFilterBitmap(true)
        paint.setDither(true)
        graph.setBackgroundColor(Color.WHITE)
        canvas.drawBitmap(graph.chartBitmap, null, RectF(0f+p, h.toFloat()-130f, w.toFloat()-p, h.toFloat()-p), null)
        graph.setBackgroundColor(Color.BLACK)
    }

    fun drawQR(){
        val icon = BitmapFactory.decodeResource(context.resources, R.drawable.qr_v2)
        canvas.drawBitmap(icon, null, RectF(w.toFloat()-160f-p, 0f+p, w.toFloat()-p, 184f+p), null )
    }

}
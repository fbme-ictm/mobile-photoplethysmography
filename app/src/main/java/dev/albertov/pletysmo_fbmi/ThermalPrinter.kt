package dev.albertov.pletysmo_fbmi

import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.indvd00m.ascii.render.Region
import com.indvd00m.ascii.render.Render
import com.indvd00m.ascii.render.api.IRender
import com.indvd00m.ascii.render.elements.Rectangle
import com.indvd00m.ascii.render.elements.plot.Axis
import com.indvd00m.ascii.render.elements.plot.Plot
import com.indvd00m.ascii.render.elements.plot.misc.PlotPoint

class ThermalPrinter {
    val w = 28
    val h = 40

    fun print(values: MutableList<Float>){
        Thread {
            val points = mutableListOf<PlotPoint>()

            values.forEachIndexed { index, fl ->
                points.add(PlotPoint(fl.toDouble(), index.toDouble()))
            }

            val render: IRender = Render()
            val builder = render.newBuilder()
            builder.width(w).height(h)
            builder.element(Rectangle(0, 0, w, h))
            builder.layer(Region(1, 1, w-2, h-2))
            builder.element(Axis(points.toList(), Region(0, 0, w-2, h-2)))
            builder.element(Plot(points.toList(), Region(0, 0, w-2, h-2)))
            val canvas = render.render(builder.build())
            var s = canvas.text

            // replacing chars that printer not know
            s = s.replace("│", "|")
            s = s.replace("─", "_")

            var lines = ""
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
}
package dev.albertov.pletysmo_fbmi

class HRComputer {
    private var peaks = mutableListOf<Int>()
    private var k = 10

    fun getHr(events: MutableList<Float>): Float{
        peaks.clear()
        for(i in k..events.size-(k+1)){
                val slicer = events.slice(i-k..i+k)
                if(slicer.max() == events[i]){
                    peaks.add(i)
                }
        }
        if(peaks.size>0){
            var avgPeak = 0f
            var rrs = mutableListOf<Float>()
            var ss =0
            for(i in 0..peaks.size-2){
                rrs.add((peaks[i+1] - peaks[i]) / SAMPLING_FREQ.toFloat())
            }

            rrs = rrs.filter { a ->
                a > rrs.max() / 2
            }.toMutableList()

            for(rr in rrs){
                avgPeak += rr
                ss++
            }
            return (60f / (avgPeak/ss))
        }
        return 0f
    }
}
package tracker

import feign.Headers
import feign.QueryMap
import feign.RequestLine

interface TrackerApi {
    @RequestLine("GET")
    @Headers("Content-Type: plain/text")

    fun getTrackerInfo(@QueryMap map: Map<String,String>): ByteArray
}

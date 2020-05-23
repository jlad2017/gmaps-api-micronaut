package google.api.test

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.URL
import javax.inject.Singleton

@Singleton
class DistanceMatrixService(private val apiKey: String) {
    /**
     * Makes calls to the Google Maps Distance Matrix API and
     * parses the response into DistanceMatrixItem objects
     */

    private val BASE_URL = "https://maps.googleapis.com/maps/api/distancematrix/json?units=imperial"

    fun getDistanceMatrix(origin: String, destination: String): DistanceMatrixResponse {
        /**
         * Makes a call to Google Maps Distance Matrix API
         * and returns a DistanceMatrixResponse object
         */
        // convert origin/destination API-friendly strings
        val origin: String = origin.replace(" ", "+")
        val destination: String = destination.replace(" ", "+")

        // build the url for the API call and get response
        val url: String = "$BASE_URL&origins=$origin&destinations=$destination&key=$apiKey"
        val response: String = URL(url).readText()

        return DistanceMatrixResponse(response)
    }

    class DistanceMatrixResponse(response: String) {
        /**
         * Parses the JSON response string given by the service
         * and stores the Distance Matrix data in a list
         */

        var status: String = ""
        var message: String = ""
        var items: MutableList<DistanceMatrixItem> = ArrayList()

        init {
            val moshi: Moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .build()
            val jsonAdapter: JsonAdapter<DistanceMatrix> = moshi.adapter(DistanceMatrix::class.java)
            val data: DistanceMatrix? = jsonAdapter.fromJson(response)

            status = data?.status ?: ""
            when (status) {
                "OK"                    -> { items = getItems(data!!); items.forEach { message += getItemMessage(it) } }
                "INVALID_REQUEST"       -> message = "The given request was invalid."
                "MAX_ELEMENTS_EXCEEDED" -> message = "The requests exceed the qer-query limit."
                "OVER_DAILY_LIMIT"      -> message = "There was an issue with the API key."
                "OVER_QUERY_LIMIT"      -> message = "The API has received too many requests from this application."
                "REQUEST_DENIED"        -> message = "This application cannot use the Distance Matrix API."
                "UNKNOWN_ERROR"         -> message = "The request could not be processed due to a server error. Please try again."
            }
        }

        private fun getItems(data: DistanceMatrix): MutableList<DistanceMatrixItem> {
            /**
             * Return a list of DistanceMatrixItems
             * for each origin/destination pair
             */
            // flatten rows (list of list of elements) into one list of elements
            val elements: MutableList<Element> = ArrayList()
            data.rows.forEach { list -> elements.addAll(list.elements) }

            // TODO(): get rid of the nested for-loop
            // add a DistanceMatrixItem to item list for each origin/destination pair
            var i = 0
            for (origin: String in data.origin_addresses) {
                for (destination: String in data.destination_addresses) {
                    val element: Element = elements[i]
                    items.add(DistanceMatrixItem(origin, destination, element.distance.text, element.duration.text, element.status))
                    i++
                }
            }
            return items
        }

        private fun getItemMessage(item: DistanceMatrixItem): String {
            /**
             * Return the message to be displayed
             * for the origin/destination pair
             */

            return when (item.status) {
                "OK"                       -> """
                                              The distance from ${item.origin} to ${item.destination} is ${item.distance}.
                                              The drive will take ${item.duration}. 
                                              ${System.lineSeparator()} ${System.lineSeparator()}
                                              """.trimIndent()
                "NOT_FOUND"                 -> "The origin ${item.origin} and/or destination ${item.destination} could not be geocoded."
                "ZERO_RESULTS"              -> "No route from ${item.origin} to ${item.destination} could be found."
                "MAX_ROUTE_LENGTH_EXCEEDED" -> "The requested route is too long and cannot be processed."
                else                        -> "Undefined error."
            }
        }
    }
}



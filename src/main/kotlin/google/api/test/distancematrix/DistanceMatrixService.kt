package google.api.test.distancematrix

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.URL
import javax.inject.Singleton

/**
 * A service that makes calls to the Google Maps Distance Matrix API
 * and parses the response into DistanceMatrixItem objects
 * (Uses Moshi for the JSON string parsing)
 *
 * @param apiKey my MEGA SECRET Google Maps API key
 */
@Singleton
class DistanceMatrixService(private val apiKey: String) {

    private val baseURL = "https://maps.googleapis.com/maps/api/distancematrix/json"

    /**
     * Makes a call to Google Maps Distance Matrix API
     * and returns a DistanceMatrixResponse object
     *
     * @param origin the start location
     * @param destination the end location
     */
    fun getResponse(origin: String, destination: String): DistanceMatrixResponse {
        val units = "imperial"

        // convert origin/destination to API-friendly strings
        val origin: String = origin.replace(" ", "+")
        val destination: String = destination.replace(" ", "+")

        // build the url for the API call and get response
        val url = "$baseURL?units=$units&origins=$origin&destinations=$destination&key=$apiKey"
        val response: String = URL(url).readText()

        return DistanceMatrixResponse(response)
    }

    /**
     * Parses the JSON response string given by the service
     * and stores the Distance Matrix data in a list
     *
     * @param response the API response, formatted as a JSON string
     */
    class DistanceMatrixResponse(response: String) {

        var status: String = ""
        var message: String = ""
        var items: MutableList<DistanceMatrixItem> = ArrayList()

        init {
            val moshi: Moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .build()
            val jsonAdapter: JsonAdapter<DistanceMatrix> = moshi.adapter(DistanceMatrix::class.java)
            // deserialize the JSON response string into a DistanceMatrix object
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
                else                    -> message = "Internal server error."
            }
        }

        /**
         * Returns a list of DistanceMatrixItem for each
         * origin/destination pair in the Distance Matrix
         *
         * @param data the DistanceMatrix representating the JSON response
         */
        private fun getItems(data: DistanceMatrix): MutableList<DistanceMatrixItem> {
            // flatten rows (list of list of elements) into one list of elements
            val elements: MutableList<DistanceMatrix.Row.Element> = ArrayList()
            data.rows.forEach { list -> elements.addAll(list.elements) }

            // TODO: get rid of the nested for-loop
            // add a DistanceMatrixItem to item list for each origin/destination pair
            var i = 0
            val itemList = ArrayList<DistanceMatrixItem>()
            for (origin: String in data.origin_addresses) {
                for (destination: String in data.destination_addresses) {
                    val element: DistanceMatrix.Row.Element = elements[i]
                    itemList.add(DistanceMatrixItem(origin,
                            destination,
                            element.distance.text,
                            element.duration.text,
                            element.status
                    ))
                    i++
                }
            }
            return itemList
        }

        /**
         * Returns the message to be displayed
         * for the given origin/destination pair
         *
         * @param item the DistanceMatrixItem containing
         *             the endpoints, distance, and duration
         */
        private fun getItemMessage(item: DistanceMatrixItem): String {
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



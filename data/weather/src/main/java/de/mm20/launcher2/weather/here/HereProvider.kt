package de.mm20.launcher2.weather.here

import android.content.Context
import android.content.SharedPreferences
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.weather.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class HereProvider(override val context: Context) : LatLonWeatherProvider() {

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://weather.ls.hereapi.com/weather/1.0/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val hereWeatherService by lazy {
        retrofit.create<HereWeatherApi>()
    }

    override val preferences: SharedPreferences
        get() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)


    override suspend fun loadWeatherData(location: LatLonWeatherLocation): WeatherUpdateResult<LatLonWeatherLocation>? {
        return loadWeatherData(location.lat, location.lon)
    }

    override suspend fun loadWeatherData(
        lat: Double,
        lon: Double
    ): WeatherUpdateResult<LatLonWeatherLocation>? {
        val updateTime = System.currentTimeMillis()

        val lang = Locale.getDefault().language
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT)

        val forecastList = mutableListOf<HourlyForecast>()

        try {
            val apiKey = getApiKey() ?: return null

            val response = hereWeatherService.report(
                apiKey = apiKey,
                language = lang,
                latitude = lat,
                longitude = lon
            )

            val forecastLocation = response.hourlyForecasts?.forecastLocation ?: return null
            val forecasts = forecastLocation.forecast ?: return null

            val location = forecastLocation.city ?: return null


            for (forecast in forecasts) {

                val timestamp = try {
                    dateFormat.parse(forecast.utcTime ?: continue)?.time ?: continue
                } catch (e: ParseException) {
                    CrashReporter.logException(e)
                    return null
                }

                // We don't want old weather data
                if (timestamp + 1000 * 60 * 30 < System.currentTimeMillis()) continue

                val condition = when {
                    !forecast.precipitationDesc.isNullOrEmpty() -> forecast.precipitationDesc
                    !forecast.skyDescription.isNullOrEmpty() -> forecast.skyDescription
                    !forecast.temperatureDesc.isNullOrEmpty() -> forecast.temperatureDesc
                    else -> forecast.description ?: continue
                }
                val humidity = forecast.humidity?.toIntOrNull() ?: 0
                val icon = getIcon(forecast.iconName ?: continue)
                val night = forecast.daylight == "N"
                val rain = forecast.rainFall?.toDoubleOrNull() ?: 0.0
                val rainPercent = forecast.precipitationProbability?.toIntOrNull() ?: 0
                val temperature = forecast.temperature?.toDoubleOrNull()?.plus(273.15)
                    ?: 0.0
                val windDir = forecast.windDirection?.toIntOrNull() ?: 0
                val windSpeed = forecast.windSpeed?.toDoubleOrNull() ?: 0.0

                forecastList.add(
                    HourlyForecast(
                        timestamp = timestamp,
                        clouds = -1,
                        condition = condition,
                        humidity = humidity.toDouble(),
                        icon = icon,
                        location = location,
                        night = night,
                        pressure = -1.0,
                        provider = context.getString(R.string.provider_here),
                        providerUrl = "",
                        precipitation = rain * 10,
                        precipProbability = rainPercent,
                        temperature = temperature,
                        windDirection = windDir.toDouble(),
                        windSpeed = windSpeed,
                        updateTime = updateTime
                    )
                )
            }

            return WeatherUpdateResult(
                forecasts = forecastList,
                location = LatLonWeatherLocation(
                    name = location,
                    lat = lat,
                    lon = lon
                )
            )


        } catch (e: Exception) {
            CrashReporter.logException(e)
            return null
        }
    }


    private fun getIcon(iconName: String): Int {
        with(HourlyForecast) {
            return when (iconName) {
                "sunny" -> CLEAR
                "clear" -> CLEAR
                "mostly_sunny" -> PARTLY_CLOUDY
                "mostly_clear" -> PARTLY_CLOUDY
                "passing_clounds" -> MOSTLY_CLOUDY
                "more_sun_than_clouds" -> PARTLY_CLOUDY
                "scattered_clouds" -> PARTLY_CLOUDY
                "partly_cloudy" -> PARTLY_CLOUDY
                "a_mixture_of_sun_and_clouds" -> PARTLY_CLOUDY
                "increasing_cloudiness" -> MOSTLY_CLOUDY
                "breaks_of_sun_late" -> MOSTLY_CLOUDY
                "afternoon_clouds" -> MOSTLY_CLOUDY
                "morning_clouds" -> MOSTLY_CLOUDY
                "partly_sunny" -> MOSTLY_CLOUDY
                "high_level_clouds" -> PARTLY_CLOUDY
                "decreasing_cloudiness" -> PARTLY_CLOUDY
                "clearing_skies" -> PARTLY_CLOUDY
                "high_clouds" -> PARTLY_CLOUDY
                "rain_early" -> SHOWERS
                "heavy_rain_early" -> SHOWERS
                "strong_thunderstorms" -> HEAVY_THUNDERSTORM
                "severe_thunderstorms" -> HEAVY_THUNDERSTORM
                "thundershowers" -> THUNDERSTORM_WITH_RAIN
                "thunderstorms" -> THUNDERSTORM
                "tstorms_early" -> THUNDERSTORM_WITH_RAIN
                "isolated_tstorms_late" -> THUNDERSTORM
                "scattered_tstorms_late" -> THUNDERSTORM
                "tstorms_late" -> THUNDERSTORM_WITH_RAIN
                "tstorms" -> THUNDERSTORM_WITH_RAIN
                "ice_fog" -> FOG
                "more_clouds_than_sun" -> MOSTLY_CLOUDY
                "broken_clouds" -> MOSTLY_CLOUDY
                "scattered_showers" -> SHOWERS
                "a_few_showers" -> SHOWERS
                "light_showers" -> SHOWERS
                "passing_showers" -> SHOWERS
                "rain_showers" -> SHOWERS
                "showers" -> SHOWERS
                "widely_scattered_tstorms" -> THUNDERSTORM
                "isolated_tstorms" -> THUNDERSTORM
                "a_few_tstorms" -> THUNDERSTORM
                "scattered_tstorms" -> THUNDERSTORM
                "hazy_sunshine" -> HAZE
                "haze" -> HAZE
                "smoke" -> FOG
                "low_level_haze" -> HAZE
                "early_fog_followed_by_sunny_skies" -> HAZE
                "early_fog" -> FOG
                "light_fog" -> FOG
                "fog" -> FOG
                "dense_fog" -> FOG
                "night_haze" -> HAZE
                "night_smoke" -> FOG
                "night_low_level_haze" -> HAZE
                "night_widely_scattered_tstorms" -> THUNDERSTORM
                "night_isolated_tstorms" -> THUNDERSTORM
                "night_a_few_tstorms" -> THUNDERSTORM
                "night_scattered_tstorms" -> THUNDERSTORM
                "night_tstorms" -> THUNDERSTORM
                "night_clear" -> CLEAR
                "mostly_cloudy" -> MOSTLY_CLOUDY
                "cloudy" -> CLOUDY
                "overcast" -> CLOUDY
                "low_clouds" -> MOSTLY_CLOUDY
                "hail" -> HAIL
                "sleet" -> SLEET
                "light_mixture_of_precip" -> SLEET
                "icy_mix" -> SLEET
                "mixture_of_precip" -> SLEET
                "heavy_mixture_of_precip" -> SLEET
                "snow_changing_to_rain" -> SLEET
                "snow_changing_to_an_icy_mix" -> SLEET
                "an_icy_mix_changing_to_snow" -> SLEET
                "an_icy_mix_changing_to_rain" -> SLEET
                "rain_changing_to_snow" -> SLEET
                "rain_changing_to_an_icy_mix" -> SLEET
                "light_icy_mix_early" -> SLEET
                "icy_mix_early" -> SLEET
                "light_icy_mix_late" -> SLEET
                "icy_mix_late" -> SLEET
                "snow_rain_mix" -> SLEET
                "scattered_flurries" -> SNOW
                "snow_flurries" -> SNOW
                "light_snow_showers" -> SLEET
                "snow_showers" -> SLEET
                "light_snow" -> SNOW
                "flurries_early" -> SNOW
                "snow_showers_early" -> SLEET
                "light_snow_early" -> SNOW
                "flurries_late" -> SNOW
                "snow_showers_late" -> SLEET
                "light_snow_late" -> SNOW
                "night_decreasing_cloudiness" -> PARTLY_CLOUDY
                "night_clearing_skies" -> PARTLY_CLOUDY
                "night_high_level_clouds" -> PARTLY_CLOUDY
                "night_high_clouds" -> PARTLY_CLOUDY
                "night_scattered_showers" -> SHOWERS
                "night_a_few_showers" -> SHOWERS
                "night_light_showers" -> SHOWERS
                "night_passing_showers" -> SHOWERS
                "night_rain_showers" -> SHOWERS
                "night_sprinkles" -> DRIZZLE
                "night_showers" -> SHOWERS
                "night_mostly_clear" -> PARTLY_CLOUDY
                "night_passing_clouds" -> MOSTLY_CLOUDY
                "night_scattered_clouds" -> PARTLY_CLOUDY
                "night_partly_cloudy" -> PARTLY_CLOUDY
                "night_afternoon_clouds" -> MOSTLY_CLOUDY
                "night_morning_clouds" -> MOSTLY_CLOUDY
                "night_broken_clouds" -> MOSTLY_CLOUDY
                "night_mostly_cloudy" -> MOSTLY_CLOUDY
                "light_freezing_rain" -> HAIL
                "freezing_rain" -> HAIL
                "heavy_rain" -> SHOWERS
                "lots_of_rain" -> SHOWERS
                "tons_of_rain" -> SHOWERS
                "heavy_rain_late" -> SHOWERS
                "flash_floods" -> SHOWERS
                "flood" -> SHOWERS
                "drizzle" -> DRIZZLE
                "sprinkles" -> DRIZZLE
                "light_rain" -> DRIZZLE
                "sprinkles_early" -> DRIZZLE
                "light_rain_early" -> SHOWERS
                "sprinkles_late" -> DRIZZLE
                "light_rain_late" -> SHOWERS
                "rain" -> SHOWERS
                "numerous_showers" -> SHOWERS
                "showery" -> SHOWERS
                "showers_early" -> SHOWERS
                "showers_late" -> SHOWERS
                "rain_late" -> SHOWERS
                "snow" -> SNOW
                "moderate_snow" -> SNOW
                "snow_early" -> SNOW
                "snow_late" -> SNOW
                "heavy_snow" -> SNOW
                "heavy_snow_early" -> SNOW
                "heavy_snow_late" -> SNOW
                "tornado" -> STORM
                "tropical_storm" -> STORM
                "hurricane" -> STORM
                "sandstorm" -> STORM
                "duststorm" -> STORM
                "snowstorm" -> STORM
                "blizzard" -> STORM
                else -> NONE
            }
        }
    }


    override fun isUpdateRequired(): Boolean {
        return getLastUpdate() + (1000 * 60 * 60) <= System.currentTimeMillis()
    }

    override suspend fun lookupLocation(query: String): List<LatLonWeatherLocation> {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://geocoder.ls.hereapi.com/6.2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val geocodeService = retrofit.create<HereGeocodeApi>()
        try {
            val apiKey = getApiKey() ?: return emptyList()
            val response = geocodeService.geocode(apiKey, query)

            return response.Response.View?.getOrNull(0)?.Result?.mapNotNull {
                LatLonWeatherLocation(
                    name = it.Location?.Address?.Label ?: return@mapNotNull null,
                    lat = it.Location.DisplayPosition?.Latitude ?: return@mapNotNull null,
                    lon = it.Location.DisplayPosition.Longitude ?: return@mapNotNull null,
                )
            } ?: emptyList()
        } catch (e: Exception) {
            CrashReporter.logException(e)
        }
        return emptyList()
    }

    private fun getApiKey(): String? {
        val resId = getApiKeyResId()
        if (resId != 0) return context.getString(resId)
        return null
    }

    override fun isAvailable(): Boolean {
        return getApiKeyResId() != 0
    }


    override val name: String
        get() = context.getString(R.string.provider_here)

    private fun getApiKeyResId(): Int {
        return context.resources.getIdentifier("here_key", "string", context.packageName)
    }

    companion object {
        private const val PREFERENCES = "here"
    }
}

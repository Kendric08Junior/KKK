package com.example.fitkagehealth

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitkagehealth.adapters.WeatherActivityAdapter
import com.example.fitkagehealth.databinding.ActivityWeatherBinding
import com.example.fitkagehealth.model.WeatherData
import com.example.fitkagehealth.model.WeatherActivity
import com.google.android.gms.location.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class WeatherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeatherBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var currentLocation: Location? = null

    // TODO: move this to a secure config in production
    private val API_KEY = "16f0dedaf11e602dea2c265b7894baa1"
    private val PERMISSION_ID = 1010
    private val executor = Executors.newSingleThreadExecutor()

    private val weatherActivities = mutableListOf<WeatherActivity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViews()
        setupLocationClient()
        checkPermissions()
    }

    private fun initializeViews() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            checkPermissions()
        }

        binding.activitiesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.activitiesRecyclerView.adapter = WeatherActivityAdapter(weatherActivities)
    }

    private fun setupLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun checkPermissions() {
        if (isPermissionGranted()) {
            if (isLocationEnabled()) {
                getLastLocation()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.location_enable_prompt),
                    Toast.LENGTH_LONG
                ).show()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        } else {
            requestPermissions()
        }
    }

    private fun isPermissionGranted(): Boolean {
        return (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (isPermissionGranted()) {
            fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                val location = task.result
                if (location != null) {
                    currentLocation = location
                    getWeatherData(location.latitude, location.longitude)
                } else {
                    requestNewLocation()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocation() {
        if (isPermissionGranted()) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        super.onLocationResult(locationResult)
                        currentLocation = locationResult.lastLocation
                        currentLocation?.let {
                            getWeatherData(it.latitude, it.longitude)
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                },
                Looper.getMainLooper()
            )
        }
    }

    private fun getWeatherData(lat: Double, lon: Double) {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        binding.mainContainer.visibility = View.GONE

        executor.execute {
            try {
                val url =
                    "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$API_KEY"
                val response = URL(url).readText(Charsets.UTF_8)

                runOnUiThread {
                    parseWeatherData(response)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.errorText.visibility = View.VISIBLE
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(
                        this,
                        getString(R.string.error_fetch_weather),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun parseWeatherData(result: String) {
        try {
            val jsonObj = JSONObject(result)
            val main = jsonObj.getJSONObject("main")
            val sys = jsonObj.getJSONObject("sys")
            val wind = jsonObj.getJSONObject("wind")
            val weather = jsonObj.getJSONArray("weather").getJSONObject(0)
            val visibility = jsonObj.optInt("visibility", 10000)

            val updatedAt: Long = jsonObj.getLong("dt")
            val updatedAtText = getString(
                R.string.weather_updated_at,
                SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
                    .format(Date(updatedAt * 1000))
            )

            val tempValue = main.getString("temp").toFloat().roundToInt()
            val feelsLikeValue = main.getString("feels_like").toFloat().roundToInt()
            val tempMinValue = main.getString("temp_min").toFloat().roundToInt()
            val tempMaxValue = main.getString("temp_max").toFloat().roundToInt()

            val temp = getString(R.string.temp_celsius_format, tempValue)
            val feelsLike = getString(R.string.feels_like_format, feelsLikeValue)
            val tempMin = getString(R.string.temp_min_format, tempMinValue)
            val tempMax = getString(R.string.temp_max_format, tempMaxValue)

            val pressure = getString(
                R.string.pressure_format,
                main.getString("pressure")
            )
            val humidity = getString(
                R.string.humidity_format,
                main.getString("humidity")
            )
            val windSpeed = getString(
                R.string.wind_speed_format,
                wind.getString("speed")
            )

            val weatherDescription = weather.getString("description")
            val weatherIcon = weather.getString("icon")
            val weatherId = weather.getInt("id")

            val sunrise: Long = sys.getLong("sunrise")
            val sunset: Long = sys.getLong("sunset")
            val sunriseText = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                .format(Date(sunrise * 1000))
            val sunsetText = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                .format(Date(sunset * 1000))

            val address = jsonObj.getString("name") + ", " + sys.getString("country")

            val weatherData = WeatherData(
                address = address,
                updatedAt = updatedAtText,
                temperature = temp,
                feelsLike = feelsLike,
                tempMin = tempMin,
                tempMax = tempMax,
                pressure = pressure,
                humidity = humidity,
                windSpeed = windSpeed,
                weatherDescription = weatherDescription,
                weatherIcon = weatherIcon,
                sunrise = sunriseText,
                sunset = sunsetText,
                visibility = visibility,
                conditionId = weatherId
            )

            updateUI(weatherData)
            generateActivityRecommendations(weatherData)

        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.errorText.visibility = View.VISIBLE
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun updateUI(weatherData: WeatherData) {
        binding.address.text = weatherData.address
        binding.updatedAt.text = weatherData.updatedAt
        binding.status.text =
            weatherData.weatherDescription.replaceFirstChar { it.uppercase() }
        binding.temp.text = weatherData.temperature
        binding.feelsLike.text = weatherData.feelsLike
        binding.tempMin.text = weatherData.tempMin
        binding.tempMax.text = weatherData.tempMax
        binding.sunrise.text = weatherData.sunrise
        binding.sunset.text = weatherData.sunset
        binding.wind.text = weatherData.windSpeed
        binding.pressure.text = weatherData.pressure
        binding.humidity.text = weatherData.humidity
        binding.visibilityText.text = getString(
            R.string.visibility_km_format,
            weatherData.visibility / 1000
        )

        setWeatherIcon(weatherData.weatherIcon)

        binding.progressBar.visibility = View.GONE
        binding.mainContainer.visibility = View.VISIBLE
        binding.swipeRefreshLayout.isRefreshing = false
    }

    private fun setWeatherIcon(iconCode: String) {
        val iconRes = when (iconCode) {
            "01d" -> R.drawable.ic_sunny
            "01n" -> R.drawable.ic_clear_night
            "02d", "03d", "04d" -> R.drawable.ic_cloudy_day
            "02n", "03n", "04n" -> R.drawable.ic_cloudy_night
            "09d", "09n", "10d", "10n" -> R.drawable.ic_rain
            "11d", "11n" -> R.drawable.ic_storm
            "13d", "13n" -> R.drawable.ic_snow
            "50d", "50n" -> R.drawable.ic_mist
            else -> R.drawable.ic_sunny
        }
        binding.weatherIcon.setImageResource(iconRes)
    }

    private fun generateActivityRecommendations(weatherData: WeatherData) {
        weatherActivities.clear()

        val temp = weatherData.temperature.replace("°C", "").toFloatOrNull() ?: 20f
        val conditionId = weatherData.conditionId
        val windSpeedValue = weatherData.windSpeed
            .replace(getString(R.string.wind_speed_suffix), "")
            .trim()
            .toFloatOrNull() ?: 0f
        val visibility = weatherData.visibility

        // Running
        val runningScore = calculateRunningScore(temp, conditionId, windSpeedValue)
        weatherActivities.add(
            WeatherActivity(
                name = getString(R.string.activity_running),
                icon = R.drawable.ic_running,
                score = runningScore,
                recommendation = getRunningRecommendation(runningScore),
                isRecommended = runningScore >= 7
            )
        )

        // Cycling
        val cyclingScore = calculateCyclingScore(temp, conditionId, windSpeedValue, visibility)
        weatherActivities.add(
            WeatherActivity(
                name = getString(R.string.activity_cycling),
                icon = R.drawable.ic_cycling,
                score = cyclingScore,
                recommendation = getCyclingRecommendation(cyclingScore),
                isRecommended = cyclingScore >= 6
            )
        )

        // Hiking
        val hikingScore = calculateHikingScore(temp, conditionId, visibility)
        weatherActivities.add(
            WeatherActivity(
                name = getString(R.string.activity_hiking),
                icon = R.drawable.ic_hiking,
                score = hikingScore,
                recommendation = getHikingRecommendation(hikingScore),
                isRecommended = hikingScore >= 6
            )
        )

        // Outdoor gym
        val gymScore = calculateOutdoorGymScore(temp, conditionId, windSpeedValue)
        weatherActivities.add(
            WeatherActivity(
                name = getString(R.string.activity_outdoor_gym),
                icon = R.drawable.ic_gym,
                score = gymScore,
                recommendation = getGymRecommendation(gymScore),
                isRecommended = gymScore >= 6
            )
        )

        // Swimming
        if (temp >= 20) {
            val swimmingScore = calculateSwimmingScore(temp, conditionId, windSpeedValue)
            weatherActivities.add(
                WeatherActivity(
                    name = getString(R.string.activity_swimming),
                    icon = R.drawable.ic_swimming,
                    score = swimmingScore,
                    recommendation = getSwimmingRecommendation(swimmingScore),
                    isRecommended = swimmingScore >= 7
                )
            )
        }

        binding.activitiesRecyclerView.adapter?.notifyDataSetChanged()
    }

    // --------------------
    // SCORE CALCULATIONS
    // --------------------
    private fun calculateRunningScore(
        temp: Float,
        conditionId: Int,
        windSpeed: Float
    ): Int {
        var score = 0

        when {
            temp in 15f..20f -> score += 3
            temp in 10f..14f -> score += 2
            temp in 21f..25f -> score += 2
            temp in 5f..9f -> score += 1
            temp in 26f..30f -> score += 1
        }

        when {
            conditionId == 800 -> score += 3 // Clear
            conditionId in 801..804 -> score += 2 // Clouds
            conditionId in 300..321 -> score += 1 // Drizzle
        }

        when {
            windSpeed < 5 -> score += 2
            windSpeed in 5f..10f -> score += 1
        }

        return score.coerceIn(0, 10)
    }

    private fun calculateCyclingScore(
        temp: Float,
        conditionId: Int,
        windSpeed: Float,
        visibility: Int
    ): Int {
        var score = 0

        when {
            temp in 15f..25f -> score += 3
            temp in 10f..14f -> score += 2
            temp in 26f..30f -> score += 2
            temp in 5f..9f -> score += 1
        }

        when {
            conditionId == 800 -> score += 3
            conditionId in 801..804 -> score += 2
            conditionId in 300..321 -> score += 1
        }

        when {
            windSpeed < 3 -> score += 2
            windSpeed in 3f..7f -> score += 1
        }

        if (visibility >= 5000) score += 2

        return score.coerceIn(0, 10)
    }

    private fun calculateHikingScore(
        temp: Float,
        conditionId: Int,
        visibility: Int
    ): Int {
        var score = 0

        when {
            temp in 10f..25f -> score += 3
            temp in 5f..9f -> score += 2
            temp in 26f..30f -> score += 2
        }

        when {
            conditionId == 800 -> score += 3
            conditionId in 801..804 -> score += 2
            conditionId in 300..321 -> score += 1
        }

        when {
            visibility >= 10000 -> score += 3
            visibility >= 5000 -> score += 2
            visibility >= 2000 -> score += 1
        }

        return score.coerceIn(0, 10)
    }

    private fun calculateOutdoorGymScore(
        temp: Float,
        conditionId: Int,
        windSpeed: Float
    ): Int {
        var score = 0

        when {
            temp in 15f..25f -> score += 3
            temp in 10f..14f -> score += 2
            temp in 26f..30f -> score += 2
        }

        when {
            conditionId == 800 -> score += 3
            conditionId in 801..804 -> score += 2
            conditionId in 300..321 -> score += 1
        }

        when {
            windSpeed < 5 -> score += 2
            windSpeed in 5f..10f -> score += 1
        }

        return score.coerceIn(0, 10)
    }

    private fun calculateSwimmingScore(
        temp: Float,
        conditionId: Int,
        windSpeed: Float
    ): Int {
        var score = 0

        when {
            temp >= 25f -> score += 3
            temp in 20f..24f -> score += 2
        }

        when {
            conditionId == 800 -> score += 3
            conditionId in 801..804 -> score += 2
        }

        when {
            windSpeed < 5 -> score += 2
            windSpeed in 5f..10f -> score += 1
        }

        return score.coerceIn(0, 10)
    }

    // --------------------
    // RECOMMENDATION TEXT
    // --------------------
    private fun getRunningRecommendation(score: Int): String {
        return when {
            score >= 8 -> getString(R.string.running_recommendation_perfect)
            score >= 6 -> getString(R.string.running_recommendation_good)
            score >= 4 -> getString(R.string.running_recommendation_fair)
            else -> getString(R.string.running_recommendation_poor)
        }
    }

    private fun getCyclingRecommendation(score: Int): String {
        return when {
            score >= 7 -> getString(R.string.cycling_recommendation_excellent)
            score >= 5 -> getString(R.string.cycling_recommendation_good)
            score >= 3 -> getString(R.string.cycling_recommendation_fair)
            else -> getString(R.string.cycling_recommendation_poor)
        }
    }

    private fun getHikingRecommendation(score: Int): String {
        return when {
            score >= 7 -> getString(R.string.hiking_recommendation_perfect)
            score >= 5 -> getString(R.string.hiking_recommendation_good)
            score >= 3 -> getString(R.string.hiking_recommendation_fair)
            else -> getString(R.string.hiking_recommendation_poor)
        }
    }

    private fun getGymRecommendation(score: Int): String {
        return when {
            score >= 7 -> getString(R.string.gym_recommendation_ideal)
            score >= 5 -> getString(R.string.gym_recommendation_good)
            score >= 3 -> getString(R.string.gym_recommendation_fair)
            else -> getString(R.string.gym_recommendation_indoor)
        }
    }

    private fun getSwimmingRecommendation(score: Int): String {
        return when {
            score >= 7 -> getString(R.string.swimming_recommendation_perfect)
            score >= 5 -> getString(R.string.swimming_recommendation_good)
            else -> getString(R.string.swimming_recommendation_cool)
        }
    }

    // --------------------
    // PERMISSIONS
    // --------------------
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PERMISSION_ID
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_ID) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                getLastLocation()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_location_permission_denied),
                    Toast.LENGTH_LONG
                ).show()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
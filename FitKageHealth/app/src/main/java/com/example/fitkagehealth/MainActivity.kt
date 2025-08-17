package com.example.fitkagehealth

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var totalSteps = 0
    private var previousTotalSteps = 0
    private lateinit var progressBar: ProgressBar
    private lateinit var stepsText: TextView
    private lateinit var distanceText: TextView
    private lateinit var timeText: TextView
    private val strideLength = 0.8
    private var startTime = 0L
    private var elapsedTime = 0L
    private var stepGoal = 0
    private lateinit var waterView: WaterView
    private lateinit var drinkButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        stepsText = findViewById(R.id.txtSteps)
        distanceText = findViewById(R.id.txtDistance)
        timeText = findViewById(R.id.txtTime)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        waterView = findViewById(R.id.waterView)
        drinkButton = findViewById(R.id.drink)

        drinkButton.setOnClickListener {
            val waterIncrement = 0.125f
            waterView.addWater(waterIncrement)
        }

        loadGoal()
        resetSteps()
    }

    override fun onResume() {
        super.onResume()

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: run {
            Toast.makeText(this, "This device has no Step Counter Sensor", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()

        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            // Step count initialization: capturing the first reading after start
            if (totalSteps < previousTotalSteps) {
                previousTotalSteps = totalSteps
            }

            totalSteps = event.values[0].toInt()
            val currentSteps = totalSteps - previousTotalSteps  // Count steps since last reset
            val distance = currentSteps * strideLength

            // Update time calculations
            elapsedTime = SystemClock.elapsedRealtime() - startTime
            val timeInSeconds = elapsedTime / 1000
            val minutes = timeInSeconds / 60
            val seconds = timeInSeconds % 60

            // Update UI
            stepsText.text = currentSteps.toString()
            distanceText.text = "%.2f meters".format(distance)
            timeText.text = String.format("%02d:%02d", minutes, seconds)  // Format time as MM:SS
            progressBar.progress = currentSteps

            if (currentSteps >= stepGoal) {
                Toast.makeText(this, "You've reached your goal!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun resetSteps() {
        stepsText.setOnClickListener {
            Toast.makeText(this, "Long press to reset steps", Toast.LENGTH_SHORT).show()
        }

        stepsText.setOnLongClickListener {
            resetAllValues()
            saveData()
            true
        }
    }

    private fun resetAllValues() {
        totalSteps = 0
        previousTotalSteps = 0
        elapsedTime = 0
        startTime = SystemClock.elapsedRealtime()  // Reset start time

        stepsText.text = "0"
        progressBar.progress = 0
        distanceText.text = "0.00 meters"
        timeText.text = "00:00"

        val sharedPreferences = getSharedPreferences("myPref", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("key1", 0)
        editor.apply()
    }

    private fun saveData() {
        val sharedPreferences = getSharedPreferences("myPref", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("key1", totalSteps)
        editor.apply()
    }

    private fun loadGoal() {
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("step_goals").child("user_123")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val goal = snapshot.getValue(String::class.java)
                stepGoal = goal?.toInt() ?: 0
                Toast.makeText(this@MainActivity, "Step goal: $stepGoal", Toast.LENGTH_SHORT).show()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Failed to load step goal", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
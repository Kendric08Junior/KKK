package com.example.fitkagehealth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class Personal_info : AppCompatActivity() {
    private lateinit var editTextSteps: EditText
    private lateinit var btnConfirmSteps: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_info)

        editTextSteps = findViewById(R.id.editTextSteps)
        btnConfirmSteps = findViewById(R.id.btnConfirmSteps)

        btnConfirmSteps.setOnClickListener {
            val stepsGoal = editTextSteps.text.toString().trim()

            if (stepsGoal.isEmpty()) {
                Toast.makeText(this, "Please enter your step goal", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Saving goal: $stepsGoal", Toast.LENGTH_SHORT).show()

            val database = FirebaseDatabase.getInstance()
            val ref = database.getReference("step_goals")

            val userId = "user_123" // Assuming the user is identified as "user_123"
            ref.child(userId).setValue(stepsGoal)
                .addOnSuccessListener {
                    Toast.makeText(this, "Goal saved successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed to save goal: ${exception.message}", Toast.LENGTH_LONG).show()
                    exception.printStackTrace()
                }

            // Navigate immediately regardless of save success/failure
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // prevent back navigation to this activity
        }
    }
}
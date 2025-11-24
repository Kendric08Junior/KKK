package com.example.fitkagehealth.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import com.example.fitkagehealth.BaseActivity
import com.example.fitkagehealth.MainActivity
import com.example.fitkagehealth.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlin.math.roundToInt

class Personal_info : BaseActivity() {

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var btnNextSave: Button
    private lateinit var btnBack: Button
    private lateinit var tvStep: TextView

    private lateinit var editTextName: EditText
    private lateinit var editTextSurname: EditText
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPhone: EditText
    private lateinit var editTextSteps: EditText
    private lateinit var editTextAge: EditText
    private lateinit var editTextGender: EditText
    private lateinit var editTextHeight: EditText
    private lateinit var editTextWeight: EditText

    private lateinit var spnHeightUnit: Spinner
    private lateinit var spnWeightUnit: Spinner
    private lateinit var summaryText: TextView

    private var currentStep = 0
    private var heightUnit = "cm"
    private var weightUnit = "kg"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_info)

        viewFlipper = findViewById(R.id.viewFlipper)
        btnNextSave = findViewById(R.id.idAdd)
        btnBack = findViewById(R.id.btnBack)
        tvStep = findViewById(R.id.tvStep)

        editTextName = findViewById(R.id.editTextName)
        editTextSurname = findViewById(R.id.editTextSurname)
        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPhone = findViewById(R.id.editTextPhone)
        editTextSteps = findViewById(R.id.editTextSteps)
        editTextAge = findViewById(R.id.editTextAge)
        editTextGender = findViewById(R.id.editTextGender)
        editTextHeight = findViewById(R.id.editTextHeight)
        editTextWeight = findViewById(R.id.editTextWeight)
        spnHeightUnit = findViewById(R.id.spnHeightUnit)
        spnWeightUnit = findViewById(R.id.spnWeightUnit)
        summaryText = findViewById(R.id.summaryText)

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        firebaseUser?.let { user ->
            user.email?.let { editTextEmail.setText(it) }
            val display = user.displayName ?: user.email?.substringBefore("@")
            display?.let {
                val parts = it.split(" ")
                if (parts.isNotEmpty()) editTextName.setText(parts[0])
                if (parts.size > 1) editTextSurname.setText(parts.subList(1, parts.size).joinToString(" "))
            }
        }

        val heightOptions = arrayOf("cm", "ft")
        val weightOptions = arrayOf("kg", "lb")

        spnHeightUnit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, heightOptions)
        spnWeightUnit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weightOptions)

        updateHints()

        spnHeightUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newUnit = heightOptions[position]
                if (newUnit != heightUnit) {
                    convertHeightTo(newUnit)
                    heightUnit = newUnit
                    updateHints()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spnWeightUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val newUnit = weightOptions[position]
                if (newUnit != weightUnit) {
                    convertWeightTo(newUnit)
                    weightUnit = newUnit
                    updateHints()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnNextSave.setOnClickListener {
            when (currentStep) {
                0 -> {
                    if (!validateStep1()) return@setOnClickListener
                    goToStep(1)
                }
                1 -> {
                    if (!validateStep2()) return@setOnClickListener
                    populateSummary()
                    goToStep(2)
                }
                2 -> { saveData() }
            }
        }

        btnBack.setOnClickListener {
            when (currentStep) {
                1 -> goToStep(0)
                2 -> goToStep(1)
            }
        }

        goToStep(0)
    }

    private fun updateHints() {
        editTextHeight.hint = if (heightUnit == "cm") {
            getString(R.string.height_cm_hint)
        } else {
            getString(R.string.height_ft_hint)
        }

        editTextWeight.hint = if (weightUnit == "kg") {
            getString(R.string.weight_kg_hint)
        } else {
            getString(R.string.weight_lb_hint)
        }
    }

    private fun convertHeightTo(newUnit: String) {
        val raw = editTextHeight.text.toString().trim().toDoubleOrNull() ?: return
        if (newUnit == "cm" && heightUnit == "ft") {
            editTextHeight.setText(String.format("%.0f", raw * 30.48))
        } else if (newUnit == "ft" && heightUnit == "cm") {
            editTextHeight.setText(String.format("%.2f", raw / 30.48))
        }
    }

    private fun convertWeightTo(newUnit: String) {
        val raw = editTextWeight.text.toString().trim().toDoubleOrNull() ?: return
        if (newUnit == "kg" && weightUnit == "lb") {
            editTextWeight.setText(String.format("%.1f", raw * 0.45359237))
        } else if (newUnit == "lb" && weightUnit == "kg") {
            editTextWeight.setText(String.format("%.1f", raw / 0.45359237))
        }
    }

    private fun goToStep(step: Int) {
        currentStep = step
        viewFlipper.displayedChild = step
        btnBack.visibility = if (step == 0) View.GONE else View.VISIBLE

        when (step) {
            0 -> {
                btnNextSave.text = getString(R.string.next_button)
                tvStep.text = getString(R.string.step1_title)
            }
            1 -> {
                btnNextSave.text = getString(R.string.next_button)
                tvStep.text = getString(R.string.step2_title)
            }
            2 -> {
                btnNextSave.text = getString(R.string.save_button)
                tvStep.text = getString(R.string.step3_title)
            }
        }
        updateHints()
    }

    private fun validateStep1(): Boolean {
        val name = editTextName.text.toString().trim()
        val surname = editTextSurname.text.toString().trim()
        val email = editTextEmail.text.toString().trim()
        val phone = editTextPhone.text.toString().trim()

        if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, getString(R.string.fill_name_fields), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun validateStep2(): Boolean {
        val steps = editTextSteps.text.toString().trim()
        val age = editTextAge.text.toString().trim()
        val height = editTextHeight.text.toString().trim()
        val weight = editTextWeight.text.toString().trim()

        if (steps.isEmpty() || age.isEmpty() || height.isEmpty() || weight.isEmpty()) {
            Toast.makeText(this, getString(R.string.fill_body_fields), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun populateSummary() {
        val sb = StringBuilder()
        sb.append(getString(R.string.summary_name, editTextName.text)).append("\n")
        sb.append(getString(R.string.summary_surname, editTextSurname.text)).append("\n")
        sb.append(getString(R.string.summary_email, editTextEmail.text)).append("\n")
        sb.append(getString(R.string.summary_phone, editTextPhone.text)).append("\n\n")
        sb.append(getString(R.string.summary_steps, editTextSteps.text)).append("\n")
        sb.append(getString(R.string.summary_age, editTextAge.text)).append("\n")
        sb.append(getString(R.string.summary_gender, editTextGender.text)).append("\n")
        sb.append(getString(R.string.summary_height, editTextHeight.text, heightUnit)).append("\n")
        sb.append(getString(R.string.summary_weight, editTextWeight.text, weightUnit))

        summaryText.text = sb.toString()
    }

    private fun saveData() {
        val name = editTextName.text.toString().trim()
        val surname = editTextSurname.text.toString().trim()
        val email = editTextEmail.text.toString().trim()
        val phone = editTextPhone.text.toString().trim()
        val stepsGoalStr = editTextSteps.text.toString().trim()
        val ageStr = editTextAge.text.toString().trim()
        val gender = editTextGender.text.toString().trim()

        val rawHeight = editTextHeight.text.toString().trim().toDoubleOrNull() ?: 0.0
        val rawWeight = editTextWeight.text.toString().trim().toDoubleOrNull() ?: 0.0

        val heightCm = if (heightUnit == "cm") rawHeight else rawHeight * 30.48
        val weightKg = if (weightUnit == "kg") rawWeight else rawWeight * 0.45359237

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, getString(R.string.user_not_logged_in), Toast.LENGTH_SHORT).show()
            return
        }

        val stepsGoalInt = stepsGoalStr.replace(",", "").toIntOrNull() ?: 0
        val ageInt = ageStr.toIntOrNull() ?: 0

        val userData = mapOf(
            "name" to name,
            "surname" to surname,
            "email" to email,
            "phone" to phone,
            "stepsGoal" to stepsGoalInt,
            "age" to ageInt,
            "gender" to gender,
            "height_cm" to heightCm.roundToInt(),
            "weight_kg" to String.format("%.1f", weightKg),
            "height_unit_entered" to heightUnit,
            "weight_unit_entered" to weightUnit
        )

        val database = FirebaseDatabase.getInstance()
        val userRef = database.getReference("users").child(userId)

        userRef.setValue(userData)
            .addOnSuccessListener {
                database.getReference("step_goals").child(userId).setValue(stepsGoalInt)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, getString(R.string.goal_save_failed), Toast.LENGTH_SHORT).show()
                        }
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
            }
            .addOnFailureListener { ex ->
                Toast.makeText(this, getString(R.string.failed_to_save, ex.message ?: ""), Toast.LENGTH_LONG).show()
            }
    }
}
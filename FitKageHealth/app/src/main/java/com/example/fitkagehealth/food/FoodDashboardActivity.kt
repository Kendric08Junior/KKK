package com.example.fitkagehealth.food

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.fitkagehealth.R
import com.example.fitkagehealth.databinding.ActivityFoodDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class FoodDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFoodDashboardBinding
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private var userRef: DatabaseReference? = null
    private var foodLogRef: DatabaseReference? = null
    private var userListener: ValueEventListener? = null
    private var foodLogListener: ValueEventListener? = null

    private var dailyCalorieGoal = 2000
    private var consumedCalories = 0
    private var remainingCalories = 2000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFoodDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        setupUI()
        attachUserListener()
        attachTodayFoodLogListener()
    }

    private fun setupUI() {
        binding.recipeSearchCard.setOnClickListener {
            startActivity(Intent(this, RecipeSearchActivity::class.java))
        }

        binding.randomRecipesCard.setOnClickListener {
            startActivity(Intent(this, RandomRecipesActivity::class.java))
        }

        binding.mealPlanCard.setOnClickListener {
            startActivity(Intent(this, MealPlanActivity::class.java))
        }

        binding.foodLogCard.setOnClickListener {
            startActivity(Intent(this, FoodLogActivity::class.java))
        }

        binding.nutritionCard.setOnClickListener {
            startActivity(Intent(this, NutritionAnalysisActivity::class.java))
        }

        binding.mealPrepCard.setOnClickListener {
            startActivity(Intent(this, MealPrepActivity::class.java))
        }

        // Default UI values
        binding.consumedCaloriesText.text = getString(R.string.calories_format, 0)
        binding.remainingCaloriesText.text = getString(R.string.calories_left, dailyCalorieGoal)
        binding.calorieGoalText.text = getString(R.string.calorie_goal, dailyCalorieGoal)

        binding.caloriesProgress.max = dailyCalorieGoal
        updateCalorieProgress(animated = false)
    }

    private fun attachUserListener() {
        val userId = auth.currentUser?.uid ?: return
        userRef = database.getReference("users").child(userId)

        userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val calorieGoal = snapshot.child("calorieGoal").getValue(Int::class.java)
                dailyCalorieGoal = calorieGoal ?: 2000

                binding.caloriesProgress.max = dailyCalorieGoal

                remainingCalories = (dailyCalorieGoal - consumedCalories).coerceAtLeast(0)
                binding.calorieGoalText.text = getString(R.string.calorie_goal, dailyCalorieGoal)

                updateCalorieProgress()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@FoodDashboardActivity,
                    getString(R.string.error_load_user_data),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        userRef?.addValueEventListener(userListener!!)
    }

    private fun attachTodayFoodLogListener() {
        val userId = auth.currentUser?.uid ?: return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        foodLogRef = database.getReference("food_logs").child(userId).child(today)

        foodLogListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var total = 0
                snapshot.children.forEach { mealSnapshot ->
                    mealSnapshot.children.forEach { foodSnapshot ->
                        val calories = foodSnapshot.child("calories").getValue(Int::class.java) ?: 0
                        total += calories
                    }
                }

                consumedCalories = total
                remainingCalories = (dailyCalorieGoal - consumedCalories).coerceAtLeast(0)

                updateCalorieProgress()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@FoodDashboardActivity,
                    getString(R.string.error_load_food_log),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        foodLogRef?.addValueEventListener(foodLogListener!!)
    }

    private fun updateCalorieProgress(animated: Boolean = true) {

        binding.caloriesProgress.max = dailyCalorieGoal

        val target = consumedCalories.coerceAtMost(dailyCalorieGoal)

        if (animated) {
            ObjectAnimator.ofInt(
                binding.caloriesProgress,
                "progress",
                binding.caloriesProgress.progress,
                target
            ).apply {
                duration = 400
                start()
            }
        } else {
            binding.caloriesProgress.progress = target
        }

        binding.consumedCaloriesText.text =
            getString(R.string.calories_format, consumedCalories)

        binding.remainingCaloriesText.text =
            getString(R.string.calories_left, remainingCalories)

        binding.calorieGoalText.text =
            getString(R.string.calorie_goal, dailyCalorieGoal)

        val colorRes = when {
            consumedCalories > dailyCalorieGoal -> R.color.red
            consumedCalories > (dailyCalorieGoal * 0.8).toInt() -> R.color.orange
            else -> R.color.green
        }

        val color = ContextCompat.getColor(this, colorRes)
        binding.caloriesProgress.progressTintList = ColorStateList.valueOf(color)

        if (consumedCalories > dailyCalorieGoal) {
            binding.remainingCaloriesText.setTextColor(
                ContextCompat.getColor(this, R.color.red)
            )
        } else {
            binding.remainingCaloriesText.setTextColor(
                ContextCompat.getColor(this, R.color.white)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            userListener?.let { userRef?.removeEventListener(it) }
            foodLogListener?.let { foodLogRef?.removeEventListener(it) }
        } catch (_: Exception) {}
    }
}
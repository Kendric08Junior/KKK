package com.example.fitkagehealth

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.fitkagehealth.R
import com.example.fitkagehealth.adapters.RecipeHistoryAdapter
import com.example.fitkagehealth.adapters.WorkoutHistoryAdapter
import com.example.fitkagehealth.databinding.ActivityProfileBinding
import com.example.fitkagehealth.databinding.StatItemBinding
import com.example.fitkagehealth.model.Achievement
import com.example.fitkagehealth.model.ProgressEntry
import com.example.fitkagehealth.model.RecipeHistory
import com.example.fitkagehealth.model.WorkoutSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private lateinit var workoutHistoryAdapter: WorkoutHistoryAdapter
    private lateinit var recipeHistoryAdapter: RecipeHistoryAdapter

    private var selectedFilter = "week" // week, month, year, all
    private var selectedStartDate: Date? = null
    private var selectedEndDate: Date? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadProfileImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupAdapters()
        loadUserData()
        setupFilterListeners()
        loadStatistics()
        loadWorkoutHistory()
        loadRecipeHistory()
        loadProgressData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                exportData()
                true
            }
            R.id.action_goals -> {
                setupGoals()
                true
            }
            R.id.action_achievements -> {
                showAchievements()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.profileImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        // Filter options localized
        val filterOptions = arrayOf(
            getString(R.string.filter_this_week),
            getString(R.string.filter_this_month),
            getString(R.string.filter_this_year),
            getString(R.string.filter_all_time)
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.filterSpinner.adapter = adapter

        binding.selectStartDate.setOnClickListener { showDatePicker(true) }
        binding.selectEndDate.setOnClickListener { showDatePicker(false) }
        binding.applyDateRange.setOnClickListener { applyDateRangeFilter() }
        binding.clearDateRange.setOnClickListener { clearDateRangeFilter() }

        binding.statsTab.setOnClickListener { showStatsTab() }
        binding.workoutsTab.setOnClickListener { showWorkoutsTab() }
        binding.recipesTab.setOnClickListener { showRecipesTab() }
        binding.progressTab.setOnClickListener { showProgressTab() }

        showStatsTab()
    }

    private fun setupAdapters() {
        workoutHistoryAdapter = WorkoutHistoryAdapter { session ->
            showWorkoutDetails(session)
        }

        recipeHistoryAdapter = RecipeHistoryAdapter { recipe ->
            showRecipeDetails(recipe)
        }

        binding.workoutHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            adapter = workoutHistoryAdapter
        }

        binding.recipeHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            adapter = recipeHistoryAdapter
        }
    }

    private fun setupFilterListeners() {
        binding.filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedFilter = when (position) {
                    0 -> "week"
                    1 -> "month"
                    2 -> "year"
                    else -> "all"
                }

                loadStatistics()
                loadWorkoutHistory()
                loadRecipeHistory()
                loadProgressData()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users").child(userId)

        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val name = snapshot.child("name").getValue(String::class.java) ?: ""
                val surname = snapshot.child("surname").getValue(String::class.java) ?: ""
                val email = snapshot.child("email").getValue(String::class.java) ?: ""
                val weight = snapshot.child("weight_kg").getValue(String::class.java) ?: "0.0"

                binding.userName.text = "$name $surname"
                binding.userEmail.text = email
                binding.currentWeight.text = getString(R.string.kg_format, weight)

                val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                val base64Image = snapshot.child("profileImageBase64").getValue(String::class.java)

                when {
                    !imageUrl.isNullOrEmpty() -> {
                        Glide.with(this@ProfileActivity)
                            .load(imageUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_default_profile)
                            .into(binding.profileImage)
                    }

                    !base64Image.isNullOrEmpty() -> {
                        try {
                            val imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.profileImage.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            binding.profileImage.setImageResource(R.drawable.ic_default_profile)
                        }
                    }

                    else -> {
                        binding.profileImage.setImageResource(R.drawable.ic_default_profile)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileActivity", getString(R.string.failed_to_load_data_generic))
            }
        })
    }
    private fun loadStatistics() {
        val userId = auth.currentUser?.uid ?: return
        val (startDate, endDate) = getDateRange(selectedFilter)

        // Load workout statistics
        database.getReference("workout_sessions").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalWorkouts = 0
                    var totalCalories = 0
                    var totalDuration = 0L
                    var completedExercises = 0

                    snapshot.children.forEach { sessionSnapshot ->
                        val session = sessionSnapshot.getValue(WorkoutSession::class.java)
                        session?.let {
                            val sessionDate = Date(it.startTime)
                            if (isDateInRange(sessionDate, startDate, endDate)) {
                                totalWorkouts++
                                totalCalories += it.caloriesBurned
                                totalDuration += it.totalDuration
                                completedExercises += it.completedExercises
                            }
                        }
                    }

                    updateStatItem(
                        binding.totalWorkoutsItem,
                        totalWorkouts.toString(),
                        getString(R.string.label_workouts)
                    )
                    updateStatItem(
                        binding.caloriesItem,
                        totalCalories.toString(),
                        getString(R.string.label_calories)
                    )
                    updateStatItem(
                        binding.timeItem,
                        formatDuration(totalDuration),
                        getString(R.string.label_exercise_time)
                    )
                    updateStatItem(
                        binding.exercisesItem,
                        completedExercises.toString(),
                        getString(R.string.label_exercises)
                    )
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        // Load recipe statistics
        database.getReference("recipe_history").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var recipesCooked = 0
                    var totalRecipeCalories = 0

                    snapshot.children.forEach { recipeSnapshot ->
                        val recipe = recipeSnapshot.getValue(RecipeHistory::class.java)
                        recipe?.let {
                            val recipeDate = Date(it.timestamp)
                            if (isDateInRange(recipeDate, startDate, endDate)) {
                                recipesCooked++
                                totalRecipeCalories += it.calories
                            }
                        }
                    }

                    // If you add recipe stat items, they should be localized like:
                    // updateStatItem(binding.recipesCookedItem, recipesCooked.toString(), getString(R.string.label_recipes_cooked))
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateStatItem(itemBinding: StatItemBinding, value: String, label: String) {
        itemBinding.statValue.text = value
        itemBinding.statLabel.text = label
    }

    private fun loadWorkoutHistory() {
        val userId = auth.currentUser?.uid ?: return
        val (startDate, endDate) = getDateRange(selectedFilter)

        database.getReference("workout_sessions").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sessions = mutableListOf<WorkoutSession>()

                    snapshot.children.forEach { sessionSnapshot ->
                        val session = sessionSnapshot.getValue(WorkoutSession::class.java)
                        session?.let {
                            val sessionDate = Date(it.startTime)
                            if (isDateInRange(sessionDate, startDate, endDate)) {
                                sessions.add(it)
                            }
                        }
                    }

                    sessions.sortByDescending { it.startTime }
                    workoutHistoryAdapter.submitList(sessions)

                    binding.emptyWorkouts.visibility =
                        if (sessions.isEmpty()) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadRecipeHistory() {
        val userId = auth.currentUser?.uid ?: return
        val (startDate, endDate) = getDateRange(selectedFilter)

        database.getReference("recipe_history").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val recipes = mutableListOf<RecipeHistory>()

                    snapshot.children.forEach { recipeSnapshot ->
                        val recipe = recipeSnapshot.getValue(RecipeHistory::class.java)
                        recipe?.let {
                            val recipeDate = Date(it.timestamp)
                            if (isDateInRange(recipeDate, startDate, endDate)) {
                                recipes.add(it)
                            }
                        }
                    }

                    recipes.sortByDescending { it.timestamp }
                    recipeHistoryAdapter.submitList(recipes)

                    binding.emptyRecipes.visibility =
                        if (recipes.isEmpty()) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadProgressData() {
        val userId = auth.currentUser?.uid ?: return
        val (startDate, endDate) = getDateRange(selectedFilter)

        database.getReference("progress_entries").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val progressEntries = mutableListOf<ProgressEntry>()
                    var weightChange = 0.0
                    var initialWeight: Double? = null
                    var currentWeight: Double? = null

                    snapshot.children.forEach { entrySnapshot ->
                        val entry = entrySnapshot.getValue(ProgressEntry::class.java)
                        entry?.let {
                            val entryDate = Date(it.timestamp)
                            if (isDateInRange(entryDate, startDate, endDate)) {
                                progressEntries.add(it)

                                if (initialWeight == null) {
                                    initialWeight = it.weightKg
                                }
                                currentWeight = it.weightKg
                            }
                        }
                    }

                    if (initialWeight != null && currentWeight != null) {
                        weightChange = currentWeight!! - initialWeight!!
                    }

                    binding.weightChange.text =
                        getString(R.string.kg_format_decimal, weightChange)

                    binding.weightChange.setTextColor(
                        if (weightChange >= 0)
                            getColor(R.color.red)
                        else
                            getColor(R.color.green)
                    )

                    progressEntries.sortedByDescending { it.timestamp }
                        .firstOrNull()?.photos?.take(3)?.forEachIndexed { index, photoUrl ->
                            when (index) {
                                0 -> loadProgressPhoto(photoUrl, binding.progressPhoto1)
                                1 -> loadProgressPhoto(photoUrl, binding.progressPhoto2)
                                2 -> loadProgressPhoto(photoUrl, binding.progressPhoto3)
                            }
                        }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadProgressPhoto(photoUrl: String, imageView: android.widget.ImageView) {
        Glide.with(this)
            .load(photoUrl)
            .centerCrop()
            .placeholder(R.drawable.ic_default_progress)
            .into(imageView)
    }

    private fun isDateInRange(date: Date, startDate: Date, endDate: Date): Boolean {
        return date.time >= startDate.time && date.time <= endDate.time
    }

    private fun getDateRange(filter: String): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        calendar.time = endDate

        when (filter) {
            "week" -> calendar.add(Calendar.WEEK_OF_YEAR, -1)
            "month" -> calendar.add(Calendar.MONTH, -1)
            "year" -> calendar.add(Calendar.YEAR, -1)
            else -> calendar.time = Date(0)
        }

        return Pair(calendar.time, endDate)
    }

    private fun formatDuration(millis: Long): String {
        val minutes = millis / 60000
        val seconds = (millis % 60000) / 1000
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun showWorkoutDetails(session: WorkoutSession) {
        android.app.AlertDialog.Builder(this)
            .setTitle(session.workoutPlan.name)
            .setMessage(
                getString(R.string.workout_details_date,
                    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        .format(Date(session.startTime))
                ) + "\n" +
                        getString(R.string.workout_details_duration, formatDuration(session.totalDuration)) + "\n" +
                        getString(R.string.workout_details_calories, session.caloriesBurned) + "\n" +
                        getString(R.string.workout_details_exercises,
                            session.completedExercises,
                            session.totalExercises
                        ) + "\n" +
                        getString(R.string.workout_details_rating, session.rating) + "\n" +
                        getString(R.string.workout_details_notes, session.notes)
            )
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showRecipeDetails(recipe: RecipeHistory) {
        android.app.AlertDialog.Builder(this)
            .setTitle(recipe.recipeTitle)
            .setMessage(
                getString(R.string.recipe_details_date,
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(recipe.timestamp))
                ) + "\n" +
                        getString(R.string.recipe_details_calories, recipe.calories) + "\n" +
                        getString(R.string.recipe_details_protein, recipe.protein) + "\n" +
                        getString(R.string.recipe_details_carbs, recipe.carbs) + "\n" +
                        getString(R.string.recipe_details_fat, recipe.fat) + "\n" +
                        getString(
                            R.string.recipe_details_rating,
                            recipe.rating ?: getString(R.string.not_rated)
                        )
            )
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun exportData() {
        lifecycleScope.launch {
            try {
                val workoutData = getWorkoutDataForExport()
                val recipeData = getRecipeDataForExport()
                val progressData = getProgressDataForExport()

                val csvContent = buildCsvContent(workoutData, recipeData, progressData)

                saveAndShareCsv(csvContent, "fitkage_data_export.csv")

            } catch (e: Exception) {
                Toast.makeText(
                    this@ProfileActivity,
                    getString(R.string.export_failed, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun getWorkoutDataForExport(): List<WorkoutSession> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return database.getReference("workout_sessions").child(userId).get().await()
            .children.mapNotNull { it.getValue(WorkoutSession::class.java) }
    }

    private suspend fun getRecipeDataForExport(): List<RecipeHistory> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return database.getReference("recipe_history").child(userId).get().await()
            .children.mapNotNull { it.getValue(RecipeHistory::class.java) }
    }

    private suspend fun getProgressDataForExport(): List<ProgressEntry> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return database.getReference("progress_entries").child(userId).get().await()
            .children.mapNotNull { it.getValue(ProgressEntry::class.java) }
    }

    private fun buildCsvContent(
        workouts: List<WorkoutSession>,
        recipes: List<RecipeHistory>,
        progress: List<ProgressEntry>
    ): String {

        val sb = StringBuilder()

        sb.append(getString(R.string.csv_workout_header)).append("\n")
        sb.append(getString(R.string.csv_workout_columns)).append("\n")

        workouts.forEach { session ->
            sb.append(
                "${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(session.startTime))},"
            )
            sb.append("${session.workoutPlan.name.replace(",", ";")},")
            sb.append("${formatDuration(session.totalDuration)},")
            sb.append("${session.caloriesBurned},")
            sb.append("${session.completedExercises},")
            sb.append("${session.rating}\n")
        }

        sb.append("\n").append(getString(R.string.csv_recipe_header)).append("\n")
        sb.append(getString(R.string.csv_recipe_columns)).append("\n")

        recipes.forEach { recipe ->
            sb.append(
                "${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(recipe.timestamp))},"
            )
            sb.append("${recipe.recipeTitle.replace(",", ";")},")
            sb.append("${recipe.calories},")
            sb.append("${recipe.protein},")
            sb.append("${recipe.carbs},")
            sb.append("${recipe.fat},")
            sb.append("${recipe.rating ?: ""}\n")
        }

        return sb.toString()
    }

    private fun saveAndShareCsv(content: String, fileName: String) {
        try {
            val file = java.io.File(getExternalFilesDir(null), fileName)
            file.writeText(content)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(
                    Intent.EXTRA_STREAM,
                    androidx.core.content.FileProvider.getUriForFile(
                        this@ProfileActivity,
                        "${packageName}.provider",
                        file
                    )
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_data)))

        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_failed_generic), Toast.LENGTH_SHORT).show()
        }
    }

    // Fitness Goals
    private fun setupGoals() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_fitness_goals, null)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            database.getReference("user_goals").child(userId).get()
                .addOnSuccessListener { snapshot ->
                    snapshot.child("targetWeight").getValue(Double::class.java)?.let {
                        dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
                            R.id.targetWeight
                        ).setText(it.toString())
                    }
                    snapshot.child("dailyCalorieGoal").getValue(Int::class.java)?.let {
                        dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
                            R.id.dailyCalorieGoal
                        ).setText(it.toString())
                    }
                    snapshot.child("weeklyWorkoutsGoal").getValue(Int::class.java)?.let {
                        dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
                            R.id.weeklyWorkoutsGoal
                        ).setText(it.toString())
                    }
                    snapshot.child("dailyStepsGoal").getValue(Int::class.java)?.let {
                        dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
                            R.id.dailyStepsGoal
                        ).setText(it.toString())
                    }
                }
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_goals))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                saveFitnessGoals(dialogView)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveFitnessGoals(dialogView: View) {
        val targetWeight = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.targetWeight
        ).text.toString().toDoubleOrNull()

        val dailyCalorieGoal = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.dailyCalorieGoal
        ).text.toString().toIntOrNull()

        val weeklyWorkoutsGoal = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.weeklyWorkoutsGoal
        ).text.toString().toIntOrNull()

        val dailyStepsGoal = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.dailyStepsGoal
        ).text.toString().toIntOrNull()

        val userId = auth.currentUser?.uid ?: return

        val goals = mapOf(
            "targetWeight" to targetWeight,
            "dailyCalorieGoal" to dailyCalorieGoal,
            "weeklyWorkoutsGoal" to weeklyWorkoutsGoal,
            "dailyStepsGoal" to dailyStepsGoal
        )

        database.getReference("user_goals").child(userId).setValue(goals)
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.goals_saved), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.goals_failed), Toast.LENGTH_SHORT).show()
            }
    }

    // Achievements
    private fun showAchievements() {
        loadAchievements()
    }

    private fun loadAchievements() {
        val userId = auth.currentUser?.uid ?: return

        database.getReference("user_achievements").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val achievements = mutableListOf<Achievement>()
                    snapshot.children.forEach { item ->
                        val achievement = item.getValue(Achievement::class.java)
                        achievement?.let { achievements.add(it) }
                    }

                    if (achievements.isEmpty()) {
                        initializeDefaultAchievements(userId)
                    } else {
                        displayAchievements(achievements)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun initializeDefaultAchievements(userId: String) {
        val defaultAchievements = listOf(
            Achievement(
                "first_workout",
                getString(R.string.ach_first_workout),
                getString(R.string.ach_first_workout_desc),
                R.drawable.ic_achievement,
                false, 0, 1
            ),
            Achievement(
                "workout_streak_7",
                getString(R.string.ach_streak_7),
                getString(R.string.ach_streak_7_desc),
                R.drawable.ic_achievement,
                false, 0, 7
            ),
            Achievement(
                "calories_10000",
                getString(R.string.ach_calories_10000),
                getString(R.string.ach_calories_10000_desc),
                R.drawable.ic_achievement,
                false, 0, 10000
            ),
            Achievement(
                "recipes_10",
                getString(R.string.ach_recipes_10),
                getString(R.string.ach_recipes_10_desc),
                R.drawable.ic_achievement,
                false, 0, 10
            )
        )

        val map = defaultAchievements.associateBy { it.id }

        database.getReference("user_achievements").child(userId).setValue(map)
            .addOnSuccessListener {
                displayAchievements(defaultAchievements)
            }
    }

    private fun displayAchievements(list: List<Achievement>) {
        val items = list.map { ach ->
            "${if (ach.unlocked) "✓" else "○"} ${ach.title} - ${ach.progress}/${ach.target}"
        }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.your_achievements))
            .setItems(items) { _, index ->
                showAchievementDetails(list[index])
            }
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showAchievementDetails(ach: Achievement) {
        val unlockedDateText =
            if (ach.unlocked && ach.unlockedDate != null)
                getString(
                    R.string.ach_unlocked_on,
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(ach.unlockedDate))
                )
            else
                ""

        android.app.AlertDialog.Builder(this)
            .setTitle(ach.title)
            .setMessage(
                ach.description + "\n\n" +
                        getString(R.string.ach_progress, ach.progress, ach.target) + "\n" +
                        getString(R.string.ach_status, if (ach.unlocked) getString(R.string.unlocked) else getString(R.string.locked)) +
                        "\n$unlockedDateText"
            )
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
}
package com.example.fitkagehealth.progress

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitkagehealth.R
import com.example.fitkagehealth.adapters.ProgressAdapter
import com.example.fitkagehealth.databinding.ActivityProgressTrackerBinding
import com.example.fitkagehealth.databinding.DialogAddProgressBinding
import com.example.fitkagehealth.model.ProgressEntry
import com.example.fitkagehealth.repository.ProgressRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProgressTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressTrackerBinding
    private val repo = ProgressRepository()
    private val auth = FirebaseAuth.getInstance()

    private val adapter by lazy {
        ProgressAdapter(::onEntryClicked, ::onEntryMenuClicked)
    }
    private val entries = mutableListOf<ProgressEntry>()

    // Multiphoto picker
    private var imageUrisToUpload: List<Uri> = emptyList()
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            imageUrisToUpload = uris ?: emptyList()
            if (imageUrisToUpload.isNotEmpty()) {
                showToast(getString(R.string.photos_selected, imageUrisToUpload.size))
            }
        }

    // Firebase listeners
    private var entriesListener: ValueEventListener? = null
    private var profileListener: ValueEventListener? = null
    private var profileRef: DatabaseReference? = null

    private var profileWeightKg: Double? = null
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        attachUserProfileListener()
        attachRealtimeListener()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.progress_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_stats -> {
                showToast(getString(R.string.stats_coming_soon))
                true
            }
            R.id.action_export -> {
                showToast(getString(R.string.export_coming_soon))
                true
            }
            R.id.action_settings -> {
                showToast(getString(R.string.settings_coming_soon))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.fabAddEntry.setOnClickListener { showAddEditDialog() }
        binding.emptyState.setOnClickListener { showAddEditDialog() }
        binding.timelineFilter.setOnClickListener { showFilterMenu() }
    }

    private fun setupRecyclerView() {
        binding.progressRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.progressRecyclerView.adapter = adapter
    }

    // --------------------------
    // REALTIME LISTENER
    // --------------------------
    private fun attachRealtimeListener() {
        entriesListener?.let {
            repo.detachEntriesListener(it)
        }

        val uid = auth.currentUser?.uid ?: run {
            showToast(getString(R.string.error_login_required))
            finish()
            return
        }

        entriesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ProgressEntry>()
                snapshot.children.forEach { child ->
                    try {
                        list.add(ProgressEntry.fromSnapshot(child))
                    } catch (e: Exception) {
                        Log.e("ProgressTracker", "Parse error", e)
                    }
                }

                val filtered = applyFilter(list, currentFilter)
                val sorted = filtered.sortedByDescending { it.timestamp }

                entries.clear()
                entries.addAll(sorted)
                adapter.submitList(ArrayList(entries))

                updateHeaderStats(list)
                updateEmptyState()
            }

            override fun onCancelled(error: DatabaseError) {
                showToast(getString(R.string.error_failed_to_load_progress))
            }
        }

        repo.attachEntriesListener(entriesListener!!)
    }

    // --------------------------
    // PROFILE LISTENER
    // --------------------------
    private fun attachUserProfileListener() {
        profileRef?.removeEventListener(profileListener ?: return)

        val uid = auth.currentUser?.uid ?: return
        profileRef = FirebaseDatabase.getInstance().getReference("users").child(uid)

        profileListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                profileWeightKg = snapshot.child("weight_kg").value?.toString()?.toDoubleOrNull()

                if (entries.isEmpty()) {
                    if (profileWeightKg != null) {
                        binding.latestWeightText.text =
                            getString(R.string.weight_format_kg, profileWeightKg)
                        binding.progressSummary.text =
                            getString(R.string.weight_from_profile)
                        binding.weightDeltaText.isVisible = false
                    } else {
                        binding.latestWeightText.text = "—"
                        binding.progressSummary.text =
                            getString(R.string.start_your_journey)
                        binding.weightDeltaText.isVisible = false
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        profileRef?.addValueEventListener(profileListener!!)
    }

    // --------------------------
    // FILTER
    // --------------------------
    private fun showFilterMenu() {
        val labels = arrayOf(
            getString(R.string.filter_all),
            getString(R.string.filter_week),
            getString(R.string.filter_month),
            getString(R.string.filter_year)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_timeline))
            .setItems(labels) { _, which ->
                currentFilter = when (which) {
                    1 -> "week"
                    2 -> "month"
                    3 -> "year"
                    else -> "all"
                }
                binding.timelineFilter.text = labels[which]
                attachRealtimeListener()
            }
            .show()
    }

    // --------------------------
    // EMPTY STATE
    // --------------------------
    private fun updateEmptyState() {
        binding.emptyState.isVisible = entries.isEmpty()
        binding.progressRecyclerView.isVisible = entries.isNotEmpty()
    }

    // --------------------------
    // HEADER STATS
    // --------------------------
    private fun updateHeaderStats(all: List<ProgressEntry>) {
        if (all.isEmpty()) {
            profileWeightKg?.let {
                binding.latestWeightText.text = getString(R.string.weight_format_kg, it)
                binding.progressSummary.text = getString(R.string.weight_from_profile)
            } ?: run {
                binding.latestWeightText.text = "—"
                binding.progressSummary.text = getString(R.string.start_your_journey)
            }
            binding.weightDeltaText.isVisible = false
            binding.totalEntries.text = "0"
            binding.photoCount.text = "0"
            return
        }

        val latest = all.maxByOrNull { it.timestamp } ?: return
        binding.latestWeightText.text = getString(R.string.weight_format_kg, latest.weightKg)
        binding.progressSummary.text =
            getString(R.string.latest_entry_label, formatDate(latest.timestamp))

        val sorted = all.sortedByDescending { it.timestamp }
        if (sorted.size > 1) {
            val previous = sorted[1]
            val diff = latest.weightKg - previous.weightKg
            val sign = if (diff > 0) "+" else ""
            binding.weightDeltaText.isVisible = true
            binding.weightDeltaText.text =
                getString(R.string.weight_delta_format, sign + "%.1f".format(diff))
            binding.weightDeltaText.setTextColor(
                if (diff > 0) getColor(R.color.red) else getColor(R.color.green_light)
            )
        } else {
            binding.weightDeltaText.isVisible = false
        }

        binding.totalEntries.text = all.size.toString()
        binding.photoCount.text = all.sumOf { it.photos.size }.toString()
    }

    // --------------------------
    // ADD / EDIT ENTRY DIALOG
    // --------------------------
    private fun showAddEditDialog(editEntry: ProgressEntry? = null) {
        val dialogBinding = DialogAddProgressBinding.inflate(layoutInflater)

        if (editEntry != null) populateEditDialog(dialogBinding, editEntry)
        else setupNewEntryDialog(dialogBinding)

        val dialog = AlertDialog.Builder(this)
            .setTitle(
                if (editEntry == null)
                    getString(R.string.dialog_add_progress)
                else
                    getString(R.string.dialog_edit_entry)
            )
            .setView(dialogBinding.root)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(
                if (editEntry == null) getString(R.string.action_add)
                else getString(R.string.action_save)
                , null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateAndSaveEntry(dialogBinding, editEntry)) dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun populateEditDialog(dialogBinding: DialogAddProgressBinding, entry: ProgressEntry) {
        dialogBinding.weightEdit.setText(entry.weightKg.toString())
        dialogBinding.notesEdit.setText(entry.notes)
        dialogBinding.liftsEdit.setText(
            entry.lifts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        )
        dialogBinding.photosCount.text =
            getString(R.string.photos_selected, entry.photos.size)
    }

    private fun setupNewEntryDialog(dialogBinding: DialogAddProgressBinding) {
        dialogBinding.photosCount.text = getString(R.string.photos_selected, 0)
        dialogBinding.pickPhotosBtn.setOnClickListener { pickImages.launch("image/*") }
    }

    private fun validateAndSaveEntry(
        dialogBinding: DialogAddProgressBinding,
        editEntry: ProgressEntry?
    ): Boolean {
        val weight = dialogBinding.weightEdit.text.toString().toDoubleOrNull()
        if (weight == null) {
            showToast(getString(R.string.error_invalid_weight))
            return false
        }

        val notes = dialogBinding.notesEdit.text.toString().trim()
        val liftsRaw = dialogBinding.liftsEdit.text.toString().trim()
        val liftsMap = parseLifts(liftsRaw)

        val entryId = editEntry?.id ?: UUID.randomUUID().toString()
        val existingPhotos = editEntry?.photos ?: emptyList()

        val newEntry = ProgressEntry(
            id = entryId,
            timestamp = System.currentTimeMillis(),
            weightKg = weight,
            photos = existingPhotos,
            notes = notes,
            lifts = liftsMap
        )

        saveEntryWithPhotos(newEntry)
        return true
    }

    private fun parseLifts(raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (raw.isNotEmpty()) {
            raw.lines().forEach { line ->
                val parts = line.split(":").map { it.trim() }
                if (parts.size >= 2) map[parts[0]] = parts.drop(1).joinToString(":")
            }
        }
        return map
    }

    private fun saveEntryWithPhotos(entry: ProgressEntry) {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val uploaded =
                    if (imageUrisToUpload.isNotEmpty())
                        repo.uploadPhotos(entry.id, imageUrisToUpload, this@ProgressTrackerActivity)
                    else emptyList()

                val updatedEntry = entry.copy(photos = entry.photos + uploaded)
                repo.saveEntry(updatedEntry)

                showToast(getString(R.string.entry_saved))
                imageUrisToUpload = emptyList()
                showLoading(false)
                attachRealtimeListener()
            } catch (e: Exception) {
                showLoading(false)
                showToast(getString(R.string.error_failed_to_save, e.message ?: ""))
            }
        }
    }

    // --------------------------
    // ENTRY DETAILS
    // --------------------------
    private fun onEntryClicked(entry: ProgressEntry) = showEntryDetails(entry)

    private fun showEntryDetails(entry: ProgressEntry) {
        val dialogBinding = DialogAddProgressBinding.inflate(layoutInflater)

        dialogBinding.weightEdit.setText(entry.weightKg.toString())
        dialogBinding.notesEdit.setText(entry.notes)
        dialogBinding.liftsEdit.setText(
            entry.lifts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        )

        dialogBinding.weightEdit.isEnabled = false
        dialogBinding.notesEdit.isEnabled = false
        dialogBinding.liftsEdit.isEnabled = false
        dialogBinding.pickPhotosBtn.visibility = View.GONE
        dialogBinding.photosCount.text =
            getString(R.string.photos_selected, entry.photos.size)

        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.entry_details_title,
                    "%.1f".format(entry.weightKg),
                    formatDate(entry.timestamp)
                )
            )
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.action_edit)) { _, _ ->
                showAddEditDialog(entry)
            }
            .setNegativeButton(getString(R.string.action_close), null)
            .show()
    }

    private fun onEntryMenuClicked(entry: ProgressEntry, view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.entry_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit -> {
                    showAddEditDialog(entry)
                    true
                }
                R.id.menu_delete -> {
                    showDeleteConfirmation(entry)
                    true
                }
                R.id.menu_share -> {
                    shareEntry(entry)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun showDeleteConfirmation(entry: ProgressEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_entry_title))
            .setMessage(getString(R.string.delete_entry_message))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                repo.deleteEntry(entry.id)
                showToast(getString(R.string.entry_deleted))
                attachRealtimeListener()
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun shareEntry(entry: ProgressEntry) {
        val shareText = buildShareText(entry)
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_progress_entry)))
    }

    private fun buildShareText(entry: ProgressEntry): String {
        return getString(
            R.string.share_template,
            "%.1f".format(entry.weightKg),
            formatDate(entry.timestamp),
            entry.notes
        )
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.isVisible = show
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
            .format(Date(timestamp))

    override fun onDestroy() {
        super.onDestroy()

        entriesListener?.let { repo.detachEntriesListener(it) }
        profileRef?.let { ref ->
            profileListener?.let { ref.removeEventListener(it) }
        }
    }
}
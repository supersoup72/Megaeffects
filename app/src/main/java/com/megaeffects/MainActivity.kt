package com.megaeffects

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private val projects = mutableListOf<Project>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions()
        RenderEngine.appContext = applicationContext

        recycler   = findViewById(R.id.recycler)
        emptyText  = findViewById(R.id.empty_text)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = ProjectAdapter(projects,
            onOpen   = { openProject(it) },
            onDelete = { deleteProject(it) }
        )

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fab_new
        ).setOnClickListener { showNewProjectDialog() }
    }

    override fun onResume() {
        super.onResume()
        refreshProjects()
    }

    private fun refreshProjects() {
        projects.clear()
        projects.addAll(Storage.listProjects(this))
        recycler.adapter?.notifyDataSetChanged()
        emptyText.visibility = if (projects.isEmpty())
            android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showNewProjectDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_new_project, null)
        val nameInput    = view.findViewById<EditText>(R.id.input_name)
        val durInput     = view.findViewById<EditText>(R.id.input_duration)
        val resSpinner   = view.findViewById<Spinner>(R.id.spinner_res)

        val resOptions = arrayOf("1080x1920", "1920x1080", "1080x1080", "720x1280", "Custom")
        resSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resOptions)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        AlertDialog.Builder(this)
            .setTitle("New Project")
            .setView(view)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().ifBlank { "My Project" }
                val dur  = durInput.text.toString().toFloatOrNull() ?: 10f
                val res  = resSpinner.selectedItem.toString()
                val (w, h) = when (res) {
                    "1920x1080" -> 1920 to 1080
                    "1080x1080" -> 1080 to 1080
                    "720x1280"  -> 720  to 1280
                    else        -> 1080 to 1920
                }
                val project = Project(name = name, duration = dur, width = w, height = h)
                Storage.saveProject(this, project)
                openProject(project)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openProject(project: Project) {
        val intent = Intent(this, ProjectActivity::class.java)
        intent.putExtra("project_id", project.id)
        startActivity(intent)
    }

    private fun deleteProject(project: Project) {
        AlertDialog.Builder(this)
            .setTitle("Delete Project")
            .setMessage("Delete \"${project.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Storage.deleteProject(this, project.id)
                refreshProjects()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        }
    }
}

// ── Project list adapter ──────────────────────────────────────────────────────

class ProjectAdapter(
    private val projects: List<Project>,
    private val onOpen: (Project) -> Unit,
    private val onDelete: (Project) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.VH>() {

    inner class VH(val view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name:     TextView = view.findViewById(R.id.tv_name)
        val info:     TextView = view.findViewById(R.id.tv_info)
        val btnOpen:  Button   = view.findViewById(R.id.btn_open)
        val btnDel:   Button   = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return VH(v)
    }

    override fun getItemCount() = projects.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = projects[position]
        holder.name.text = p.name
        holder.info.text = "${p.duration}s  •  ${p.width}x${p.height}  •  ${p.layers.size} layers"
        holder.btnOpen.setOnClickListener  { onOpen(p) }
        holder.btnDel.setOnClickListener   { onDelete(p) }
    }
}

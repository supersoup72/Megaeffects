package com.megaeffects

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class ProjectActivity : AppCompatActivity() {

    private lateinit var project: Project
    private var currentTime = 0f
    private var selectedLayerIdx = -1
    private var isPlaying = false
    private var playJob: Job? = null

    private lateinit var previewImage: ImageView
    private lateinit var previewHint: TextView
    private lateinit var timeLabel: TextView
    private lateinit var scrubber: SeekBar
    private lateinit var btnPlay: Button
    private lateinit var layerRecycler: RecyclerView
    private lateinit var layerAdapter: LayerAdapter
    private lateinit var transformPanel: LinearLayout
    private lateinit var timelineView: TimelineView

    private val PICK_FILE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project)

        val projectId = intent.getStringExtra("project_id") ?: run { finish(); return }
        project = Storage.loadProject(this, projectId) ?: run { finish(); return }

        bindViews()
        setupTimeline()
        setupScrubber()
        setupButtons()
        refreshLayers()
    }

    private fun bindViews() {
        previewImage   = findViewById(R.id.preview_image)
        previewHint    = findViewById(R.id.preview_hint)
        timeLabel      = findViewById(R.id.time_label)
        scrubber       = findViewById(R.id.scrubber)
        btnPlay        = findViewById(R.id.btn_play)
        layerRecycler  = findViewById(R.id.layer_recycler)
        transformPanel = findViewById(R.id.transform_panel)
        timelineView   = findViewById(R.id.timeline_view)

        title = project.name
        scrubber.max = (project.duration * 100).toInt()
    }

    private fun setupTimeline() {
        timelineView.project = project
        timelineView.onSeek = { t ->
            currentTime = t
            updateTimeLabel()
            scrubber.progress = (t * 100).toInt()
        }
        timelineView.onSelectLayer = { idx -> selectLayer(idx) }
    }

    private fun setupScrubber() {
        scrubber.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentTime = progress / 100f
                    updateTimeLabel()
                    timelineView.currentTime = currentTime
                    timelineView.invalidate()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_back).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.btn_export).setOnClickListener { startExport() }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { showSettings() }
        findViewById<Button>(R.id.btn_rewind).setOnClickListener { setTime(0f) }
        btnPlay.setOnClickListener { togglePlay() }
        findViewById<Button>(R.id.btn_render).setOnClickListener { renderCurrentFrame() }
        findViewById<Button>(R.id.btn_add_layer).setOnClickListener { showAddLayerDialog() }
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun togglePlay() {
        if (isPlaying) stopPlay() else startPlay()
    }

    private fun startPlay() {
        isPlaying = true
        btnPlay.text = "Pause"
        playJob = lifecycleScope.launch {
            val frameTime = 1000L / project.fps
            while (isPlaying) {
                currentTime += 1f / project.fps
                if (currentTime >= project.duration) {
                    currentTime = 0f; stopPlay(); break
                }
                withContext(Dispatchers.Main) {
                    setTime(currentTime)
                    renderCurrentFrame(auto = true)
                }
                delay(frameTime)
            }
        }
    }

    private fun stopPlay() {
        isPlaying = false
        btnPlay.text = "Play"
        playJob?.cancel()
    }

    private fun setTime(t: Float) {
        currentTime = t
        updateTimeLabel()
        scrubber.progress = (t * 100).toInt()
        timelineView.currentTime = t
        timelineView.invalidate()
    }

    private fun updateTimeLabel() {
        timeLabel.text = "%.2fs".format(currentTime)
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private fun renderCurrentFrame(auto: Boolean = false) {
        val t = currentTime
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = Renderer.renderFrame(project, t)
            withContext(Dispatchers.Main) {
                previewImage.setImageBitmap(bitmap)
                previewImage.visibility = View.VISIBLE
                previewHint.visibility = View.GONE
            }
        }
    }

    // ── Layers ────────────────────────────────────────────────────────────────

    private fun refreshLayers() {
        layerAdapter = LayerAdapter(
            project.layers,
            onSelect  = { idx -> selectLayer(idx) },
            onFilters = { idx -> openFilters(idx) },
            onDelete  = { idx -> deleteLayer(idx) },
            onVisibility = { idx, vis ->
                project.layers[idx].visible = vis
                Storage.saveProject(this, project)
                timelineView.invalidate()
            }
        )
        layerRecycler.layoutManager = LinearLayoutManager(this)
        layerRecycler.adapter = layerAdapter

        // Drag to reorder
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to   = target.adapterPosition
                val layer = project.layers.removeAt(from)
                project.layers.add(to, layer)
                layerAdapter.notifyItemMoved(from, to)
                Storage.saveProject(this@ProjectActivity, project)
                timelineView.invalidate()
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        }
        ItemTouchHelper(callback).attachToRecyclerView(layerRecycler)
        timelineView.invalidate()
    }

    private fun selectLayer(idx: Int) {
        selectedLayerIdx = idx
        val layer = project.layers.getOrNull(idx) ?: run {
            transformPanel.visibility = View.GONE; return
        }
        transformPanel.visibility = View.VISIBLE
        transformPanel.removeAllViews()

        val t = Keyframes.getTransformAtTime(layer, currentTime)
        val props = listOf(
            "x" to t.x, "y" to t.y,
            "scaleX" to t.scaleX, "scaleY" to t.scaleY,
            "rotateX" to t.rotateX, "rotateY" to t.rotateY,
            "rotateZ" to t.rotateZ, "opacity" to t.opacity
        )

        val header = TextView(this).apply {
            text = "Transform: ${layer.name}"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(8, 8, 8, 4)
        }
        transformPanel.addView(header)

        props.forEach { (propName, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(4, 2, 4, 2)
            }
            val label = TextView(this).apply {
                text = propName
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0.28f)
            }
            val input = EditText(this).apply {
                setText("%.3f".format(value))
                textSize = 11f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF2A2A2A.toInt())
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0.38f)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            val btnAdd = Button(this).apply {
                text = "+KF"; textSize = 9f
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0.17f)
                setOnClickListener {
                    val v = input.text.toString().toFloatOrNull() ?: return@setOnClickListener
                    Keyframes.addKeyframe(layer, propName, currentTime, v)
                    Storage.saveProject(this@ProjectActivity, project)
                    Toast.makeText(this@ProjectActivity, "Keyframe added", Toast.LENGTH_SHORT).show()
                }
            }
            val btnDel = Button(this).apply {
                text = "-KF"; textSize = 9f
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0.17f)
                setOnClickListener {
                    Keyframes.removeKeyframe(layer, propName, currentTime)
                    Storage.saveProject(this@ProjectActivity, project)
                    Toast.makeText(this@ProjectActivity, "Keyframe removed", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(label); row.addView(input)
            row.addView(btnAdd); row.addView(btnDel)
            transformPanel.addView(row)
        }
    }

    private fun deleteLayer(idx: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Layer")
            .setMessage("Delete \"${project.layers[idx].name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                project.layers.removeAt(idx)
                Storage.saveProject(this, project)
                refreshLayers()
                transformPanel.visibility = View.GONE
                selectedLayerIdx = -1
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFilters(layerIdx: Int) {
        val intent = Intent(this, FilterEditorActivity::class.java)
        intent.putExtra("project_id", project.id)
        intent.putExtra("layer_id", project.layers[layerIdx].id)
        startActivity(intent)
    }

    // ── Add layer ─────────────────────────────────────────────────────────────

    private fun showAddLayerDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_layer, null)
        val nameInput  = view.findViewById<EditText>(R.id.input_name)
        val typeGroup  = view.findViewById<RadioGroup>(R.id.radio_type)
        val srcInput   = view.findViewById<EditText>(R.id.input_source)
        val btnBrowse  = view.findViewById<Button>(R.id.btn_browse)

        btnBrowse.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }
            startActivityForResult(intent, PICK_FILE)
            // Store reference to fill srcInput later
            pendingSrcInput = srcInput
        }

        AlertDialog.Builder(this)
            .setTitle("Add Layer")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val typeId = typeGroup.checkedRadioButtonId
                val type = when (typeId) {
                    R.id.radio_video -> "video"
                    R.id.radio_image -> "image"
                    else -> "color"
                }
                val layer = Layer(
                    name       = nameInput.text.toString().ifBlank { "Layer" },
                    sourceType = type,
                    source     = srcInput.text.toString().trim(),
                    end        = project.duration
                )
                project.layers.add(layer)
                Storage.saveProject(this, project)
                refreshLayers()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private var pendingSrcInput: EditText? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val path = getRealPath(uri)
            pendingSrcInput?.setText(path ?: uri.toString())
            pendingSrcInput = null
        }
    }

    private fun getRealPath(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri,
                arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) { uri.path }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private fun showSettings() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val wInput   = view.findViewById<EditText>(R.id.input_width)
        val hInput   = view.findViewById<EditText>(R.id.input_height)
        val fpsInput = view.findViewById<EditText>(R.id.input_fps)
        val durInput = view.findViewById<EditText>(R.id.input_duration)

        wInput.setText(project.width.toString())
        hInput.setText(project.height.toString())
        fpsInput.setText(project.fps.toString())
        durInput.setText(project.duration.toString())

        AlertDialog.Builder(this)
            .setTitle("Project Settings")
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                val oldW = project.width
                val oldH = project.height
                val newW = wInput.text.toString().toIntOrNull() ?: oldW
                val newH = hInput.text.toString().toIntOrNull() ?: oldH

                // Scale layer positions
                if (newW != oldW || newH != oldH) {
                    for (layer in project.layers) {
                        layer.x = layer.x * newW / oldW
                        layer.y = layer.y * newH / oldH
                        layer.keyframes["x"]?.forEach { it.value = it.value * newW / oldW }
                        layer.keyframes["y"]?.forEach { it.value = it.value * newH / oldH }
                    }
                }
                project.width    = newW
                project.height   = newH
                project.fps      = fpsInput.text.toString().toIntOrNull() ?: project.fps
                project.duration = durInput.text.toString().toFloatOrNull() ?: project.duration
                scrubber.max     = (project.duration * 100).toInt()
                Storage.saveProject(this, project)
                timelineView.invalidate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private fun startExport() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Exporting...")
            .setMessage("0%")
            .setCancelable(false)
            .create()
        dialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val outDir  = Storage.exportDir(this@ProjectActivity)
            val outPath = "${outDir.absolutePath}/megaeffects_${project.id}.mp4"
            val result  = Exporter.export(project, outPath) { progress, msg ->
                lifecycleScope.launch(Dispatchers.Main) {
                    dialog.setMessage("$msg\n${(progress * 100).toInt()}%")
                }
            }
            withContext(Dispatchers.Main) {
                dialog.dismiss()
                AlertDialog.Builder(this@ProjectActivity)
                    .setTitle(if (result.first) "Export Done" else "Export Failed")
                    .setMessage(result.second)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun saveAndFinish() {
        Storage.saveProject(this, project)
        finish()
    }

    override fun onPause() {
        super.onPause()
        stopPlay()
        Storage.saveProject(this, project)
    }
}

// ── Layer adapter ──────────────────────────────────────────────────────────────

class LayerAdapter(
    private val layers: List<Layer>,
    private val onSelect:     (Int) -> Unit,
    private val onFilters:    (Int) -> Unit,
    private val onDelete:     (Int) -> Unit,
    private val onVisibility: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<LayerAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val name:    TextView  = view.findViewById(R.id.tv_layer_name)
        val src:     TextView  = view.findViewById(R.id.tv_layer_src)
        val vis:     CheckBox  = view.findViewById(R.id.cb_visible)
        val btnFx:   Button    = view.findViewById(R.id.btn_fx)
        val btnDel:  Button    = view.findViewById(R.id.btn_del)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_layer, parent, false)
        return VH(v)
    }

    override fun getItemCount() = layers.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val layer = layers[position]
        holder.name.text  = layer.name
        holder.src.text   = if (layer.source.isNotBlank())
            layer.source.substringAfterLast("/") else layer.sourceType
        holder.vis.isChecked = layer.visible
        holder.vis.setOnCheckedChangeListener { _, checked -> onVisibility(position, checked) }
        holder.view.setOnClickListener  { onSelect(position) }
        holder.btnFx.setOnClickListener { onFilters(position) }
        holder.btnDel.setOnClickListener { onDelete(position) }
    }
}

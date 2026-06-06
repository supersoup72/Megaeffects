package com.megaeffects

import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class FilterEditorActivity : AppCompatActivity() {

    private lateinit var project: Project
    private lateinit var layer: Layer
    private var filterIdx: Int = -1

    private lateinit var codeInput: EditText
    private lateinit var statusText: TextView
    private lateinit var modeGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter_editor)

        val projectId = intent.getStringExtra("project_id") ?: run { finish(); return }
        val layerId   = intent.getStringExtra("layer_id")   ?: run { finish(); return }
        filterIdx     = intent.getIntExtra("filter_idx", -1)

        project = Storage.loadProject(this, projectId) ?: run { finish(); return }
        layer   = project.layers.find { it.id == layerId } ?: run { finish(); return }

        bindViews()
        loadFilter()
    }

    private fun bindViews() {
        codeInput  = findViewById(R.id.code_input)
        statusText = findViewById(R.id.status_text)
        modeGroup  = findViewById(R.id.mode_group)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_compile).setOnClickListener { compile() }

        // Show filter list button
        findViewById<Button>(R.id.btn_filters_list).setOnClickListener { showFiltersList() }
    }

    private fun loadFilter() {
        val filter = layer.filters.getOrNull(filterIdx)
        if (filter != null) {
            codeInput.setText(filter.sourceCode.ifBlank { defaultCode("c") })
            title = "Filter: ${filter.name}"
            if (filter.mode == "glsl") {
                modeGroup.check(R.id.radio_glsl)
            }
        } else {
            codeInput.setText(defaultCode("c"))
            title = "New Filter"
        }
    }

    private fun compile() {
        val code = codeInput.text.toString().trim()
        if (code.isEmpty()) {
            statusText.text = "Write some code first"
            statusText.setTextColor(0xFFFFAA00.toInt())
            return
        }

        val isGlsl = modeGroup.checkedRadioButtonId == R.id.radio_glsl
        statusText.text = "Compiling..."
        statusText.setTextColor(0xFFFFFF00.toInt())

        lifecycleScope.launch(Dispatchers.IO) {
            val pluginsDir = Storage.pluginsDir(this@FilterEditorActivity, project.id)
            val idx = if (filterIdx >= 0) filterIdx else layer.filters.size
            val soPath = "${pluginsDir.absolutePath}/filter_$idx.so"

            val result = Compiler.compile(this@FilterEditorActivity, code, soPath, isGlsl)

            withContext(Dispatchers.Main) {
                if (result.success) {
                    statusText.text = "✓ ${result.message}"
                    statusText.setTextColor(0xFF44FF44.toInt())

                    val filter = Filter(
                        name       = "filter_$idx",
                        soPath     = soPath,
                        sourceCode = code,
                        mode       = if (isGlsl) "glsl" else "c",
                        enabled    = true
                    )
                    if (filterIdx < 0 || filterIdx >= layer.filters.size) {
                        layer.filters.add(filter)
                    } else {
                        layer.filters[filterIdx] = filter
                    }
                    Storage.saveProject(this@FilterEditorActivity, project)
                } else {
                    statusText.text = "✗ ${result.message}"
                    statusText.setTextColor(0xFFFF4444.toInt())
                }
            }
        }
    }

    private fun showFiltersList() {
        val filters = layer.filters
        if (filters.isEmpty()) {
            Toast.makeText(this, "No filters yet. Write code and compile.", Toast.LENGTH_SHORT).show()
            return
        }
        val names = filters.map { "${if (it.enabled) "ON" else "OFF"} ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Filters on ${layer.name}")
            .setItems(names) { _, i -> filterIdx = i; loadFilter() }
            .setNeutralButton("New Filter") { _, _ ->
                filterIdx = -1
                codeInput.setText(defaultCode("c"))
                title = "New Filter"
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun defaultCode(mode: String) = if (mode == "glsl") DEFAULT_GLSL else DEFAULT_C

    companion object {
        val DEFAULT_C = """
#include "filter_sdk.h"

void filter_init(void) {}
void filter_destroy(void) {}
const char *filter_name(void)        { return "My Filter"; }
const char *filter_description(void) { return "Custom filter"; }
int  filter_param_count(void)        { return 0; }
FilterParam filter_param_info(int i) { FilterParam p={0}; return p; }

void filter_process(FilterFrame *f) {
    int n = f->width * f->height * 4;
    for (int i = 0; i < n; i += 4) {
        f->pixels[i]   = 255 - f->pixels[i];
        f->pixels[i+1] = 255 - f->pixels[i+1];
        f->pixels[i+2] = 255 - f->pixels[i+2];
    }
}
""".trimIndent()

        val DEFAULT_GLSL = """
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec4 col = texture(iChannel0, uv);
    // Edit col here
    fragColor = col;
}
""".trimIndent()
    }
}

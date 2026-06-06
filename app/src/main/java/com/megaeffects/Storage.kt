package com.megaeffects

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object Storage {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private fun projectsDir(context: Context): File {
        return File(context.filesDir, "projects").also { it.mkdirs() }
    }

    fun pluginsDir(context: Context, projectId: String): File {
        return File(context.filesDir, "projects/$projectId/plugins").also { it.mkdirs() }
    }

    fun exportDir(context: Context): File {
        val movies = File(context.getExternalFilesDir(null), "MegaEffects")
        movies.mkdirs()
        return movies
    }

    fun listProjects(context: Context): List<Project> {
        val dir = projectsDir(context)
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { folder ->
                val file = File(folder, "project.json")
                if (file.exists()) {
                    try {
                        gson.fromJson(file.readText(), Project::class.java)
                    } catch (e: Exception) { null }
                } else null
            }
            ?.sortedByDescending { it.modified }
            ?: emptyList()
    }

    fun loadProject(context: Context, projectId: String): Project? {
        val file = File(projectsDir(context), "$projectId/project.json")
        return try {
            gson.fromJson(file.readText(), Project::class.java)
        } catch (e: Exception) { null }
    }

    fun saveProject(context: Context, project: Project) {
        project.modified = System.currentTimeMillis()
        val dir = File(projectsDir(context), project.id).also { it.mkdirs() }
        File(dir, "project.json").writeText(gson.toJson(project))
    }

    fun deleteProject(context: Context, projectId: String) {
        File(projectsDir(context), projectId).deleteRecursively()
    }
}

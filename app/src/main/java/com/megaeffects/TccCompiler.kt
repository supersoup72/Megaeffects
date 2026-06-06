package com.megaeffects

import android.content.Context
import android.util.Log
import java.io.File

object TccCompiler {

    private var loaded = false

    init {
        try {
            // Load libtcc first, then the wrapper that depends on it
            System.loadLibrary("tcc")
            Log.i("MegaEffects", "libtcc.so loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("MegaEffects", "libtcc.so not found: ${e.message}")
        }

        try {
            System.loadLibrary("tcc_wrapper")
            loaded = true
            Log.i("MegaEffects", "tcc_wrapper loaded: ${nativeVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("MegaEffects", "tcc_wrapper not loaded: ${e.message}")
        }
    }

    fun isAvailable() = loaded

    private external fun nativeCompile(
        source: String,
        outputPath: String,
        includePath: String
    ): String

    external fun nativeVersion(): String

    fun compile(context: Context, source: String, outputPath: String): CompileResult {
        if (!loaded) {
            return CompileResult(false, "libtcc not available — rebuild APK")
        }
        val sdkDir = File(context.filesDir, "sdk").also { it.mkdirs() }
        File(sdkDir, "filter_sdk.h").writeText(Compiler.SDK_HEADER)
        return try {
            val result = nativeCompile(source, outputPath, sdkDir.absolutePath)
            if (result.startsWith("OK")) {
                CompileResult(true, "Compiled OK (libtcc)")
            } else {
                CompileResult(false, result.removePrefix("ERROR:").trim())
            }
        } catch (e: Exception) {
            CompileResult(false, "JNI error: ${e.message}")
        }
    }
}

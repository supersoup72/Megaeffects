package com.megaeffects

import android.content.Context
import android.util.Log
import java.io.File

object TccCompiler {

    private var loaded = false
    private var loadError = ""

    init {
        // libtcc.so must be loaded before tcc_wrapper
        try {
            System.loadLibrary("tcc")
            Log.i("MegaEffects", "libtcc.so loaded OK")
        } catch (e: UnsatisfiedLinkError) {
            loadError += "libtcc: ${e.message}\n"
            Log.e("MegaEffects", "libtcc.so failed: ${e.message}")
        }

        try {
            System.loadLibrary("tcc_wrapper")
            loaded = true
            Log.i("MegaEffects", "tcc_wrapper loaded OK: ${nativeVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            loadError += "tcc_wrapper: ${e.message}"
            Log.e("MegaEffects", "tcc_wrapper failed: ${e.message}")
        }
    }

    fun isAvailable() = loaded
    fun getLoadError() = loadError

    private external fun nativeCompile(
        source: String, outputPath: String, includePath: String
    ): String
    external fun nativeVersion(): String

    fun compile(context: Context, source: String, outputPath: String): CompileResult {
        if (!loaded) {
            return CompileResult(false,
                "libtcc load failed:\n$loadError\n\n" +
                "libtcc IS in APK — this is a runtime linking error.\n" +
                "Check logcat for details.")
        }
        val sdkDir = File(context.filesDir, "sdk").also { it.mkdirs() }
        File(sdkDir, "filter_sdk.h").writeText(Compiler.SDK_HEADER)
        return try {
            val result = nativeCompile(source, outputPath, sdkDir.absolutePath)
            if (result.startsWith("OK"))
                CompileResult(true, "Compiled OK (libtcc)")
            else
                CompileResult(false, result.removePrefix("ERROR:").trim())
        } catch (e: Exception) {
            CompileResult(false, "JNI error: ${e.message}")
        }
    }
}

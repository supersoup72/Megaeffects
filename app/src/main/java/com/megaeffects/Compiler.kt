package com.megaeffects

import android.content.Context
import android.util.Log
import java.io.File

data class CompileResult(val success: Boolean, val message: String)

object Compiler {

    private val SDK_HEADER = """
#ifndef FILTER_SDK_H
#define FILTER_SDK_H
#include <stdint.h>
typedef struct {
    uint8_t *pixels; int width, height;
    double time, duration;
    float *params; int param_count;
    uint8_t *prev_pixels;
    float transform[16];
    float opacity; int layer_index, layer_count;
} FilterFrame;
typedef struct {
    char name[64], description[256];
    float default_value, min_value, max_value;
} FilterParam;
void filter_init(void);
void filter_process(FilterFrame *frame);
void filter_destroy(void);
const char *filter_name(void);
const char *filter_description(void);
int filter_param_count(void);
FilterParam filter_param_info(int index);
static inline uint8_t clamp_u8(int v){return(uint8_t)(v<0?0:v>255?255:v);}
static inline float clampf(float v,float lo,float hi){return v<lo?lo:v>hi?hi:v;}
#endif
""".trimIndent()

    private val GLSL_PREAMBLE = """
#include <math.h>
typedef struct{float x,y;}vec2;
typedef struct{float x,y,z;}vec3;
typedef struct{float x,y,z,w;}vec4;
static inline vec2 vec2_new(float x,float y){vec2 v={x,y};return v;}
static inline vec4 vec4_new(float x,float y,float z,float w){vec4 v={x,y,z,w};return v;}
static inline vec4 texture_sample(const unsigned char*px,int w,int h,vec2 uv){
    int ix=(int)(uv.x*w)%w;if(ix<0)ix+=w;
    int iy=(int)(uv.y*h)%h;if(iy<0)iy+=h;
    int i=(iy*w+ix)*4;
    return vec4_new(px[i]/255.f,px[i+1]/255.f,px[i+2]/255.f,px[i+3]/255.f);
}
#define texture(ch,uv) texture_sample(ch,_fw,_fh,uv)
#define iResolution vec2_new((float)_fw,(float)_fh)
#define iTime ((float)_ft)
static int _fw,_fh;static double _ft;
""".trimIndent()

    fun extractAssets(context: Context) {
        // Extract TCC binary from assets to files dir on first run
        val tccDst = File(context.filesDir, "tcc_arm64")
        if (!tccDst.exists() || tccDst.length() < 1000) {
            try {
                context.assets.open("tcc_arm64").use { inp ->
                    tccDst.outputStream().use { out -> inp.copyTo(out) }
                }
                tccDst.setExecutable(true)
                Log.i("MegaEffects", "TCC extracted: ${tccDst.length()} bytes")
            } catch (e: Exception) {
                Log.w("MegaEffects", "TCC asset not found: ${e.message}")
            }
        }
    }

    fun compile(context: Context, code: String, outputPath: String, isGlsl: Boolean): CompileResult {
        // Ensure assets extracted
        extractAssets(context)

        val src = if (isGlsl) buildGlslSource(code) else buildCSource(code, context.filesDir.absolutePath)

        // Write SDK header
        val sdkDir = File(context.filesDir, "sdk").also { it.mkdirs() }
        File(sdkDir, "filter_sdk.h").writeText(SDK_HEADER)

        // Write source
        val srcFile = File(context.filesDir, "filter_tmp.c")
        srcFile.writeText(src)

        val tcc = findTcc(context)
            ?: return CompileResult(false,
                "No C compiler found.\n\n" +
                "Options:\n" +
                "1. Rebuild APK (bundles TCC automatically)\n" +
                "2. Install Termux + run: pkg install tcc\n\n" +
                "Searched:\n${getTccCandidates(context).joinToString("\n")}")

        return try {
            val env = mutableMapOf(
                "LD_LIBRARY_PATH" to "/data/data/com.termux/files/usr/lib",
                "PATH" to "/data/data/com.termux/files/usr/bin:/system/bin"
            )
            val cmd = listOf(
                tcc, "-shared",
                "-I${sdkDir.absolutePath}",
                "-o", outputPath,
                srcFile.absolutePath,
                "-lm"
            )
            Log.i("MegaEffects", "Compiling: $cmd")
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .also { pb -> pb.environment().putAll(env) }
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val exit = proc.waitFor()
            srcFile.delete()

            if (exit == 0 && File(outputPath).exists() && File(outputPath).length() > 0) {
                CompileResult(true, "Compiled OK")
            } else {
                CompileResult(false, output.take(600).ifBlank { "Unknown error (exit $exit)" })
            }
        } catch (e: Exception) {
            CompileResult(false, "Error: ${e.message}")
        }
    }

    private fun getTccCandidates(context: Context) = listOf(
        "${context.filesDir}/tcc_arm64",
        "/data/data/com.termux/files/usr/bin/tcc",
        "/data/data/com.termux/files/usr/bin/clang",
    )

    private fun findTcc(context: Context): String? {
        val candidates = getTccCandidates(context)
        for (path in candidates) {
            val f = File(path)
            if (f.exists()) {
                f.setExecutable(true)
                if (f.canExecute()) {
                    Log.i("MegaEffects", "Found compiler: $path")
                    return path
                }
            }
        }
        // Try system PATH
        return try {
            val p = ProcessBuilder("tcc", "--version").redirectErrorStream(true).start()
            if (p.waitFor() == 0) "tcc" else null
        } catch (e: Exception) { null }
    }

    private fun buildCSource(code: String, filesDir: String): String {
        val sdkPath = "$filesDir/sdk/filter_sdk.h"
        return if ("filter_sdk.h" !in code) "#include \"$sdkPath\"\n$code" else code
    }

    private fun buildGlslSource(glslCode: String): String {
        val code = glslCode
            .replace(Regex("""void\s+mainImage\s*\(\s*out\s+vec4\s+(\w+)\s*,\s*(?:in\s+)?vec2\s+(\w+)\s*\)""")) {
                "void mainImage(vec4 *${it.groupValues[1]}_ptr, vec2 ${it.groupValues[2]})"
            }
            .replace(Regex("""\b(fragColor)\s*="""), "*$1_ptr =")

        return "$GLSL_PREAMBLE\n$SDK_HEADER\n$code\n" + """
void filter_init(void){}
void filter_destroy(void){}
const char *filter_name(void){return "GLSL Filter";}
const char *filter_description(void){return "Custom GLSL";}
int filter_param_count(void){return 0;}
FilterParam filter_param_info(int i){FilterParam p={0};return p;}
void filter_process(FilterFrame *f){
    _fw=f->width;_fh=f->height;_ft=f->time;
    for(int y=0;y<f->height;y++){for(int x=0;x<f->width;x++){
        vec2 coord=vec2_new((float)x,(float)y);
        vec4 color=texture_sample(f->pixels,f->width,f->height,vec2_new((float)x/f->width,(float)y/f->height));
        mainImage(&color,coord);
        int i=(y*f->width+x)*4;
        f->pixels[i]=(unsigned char)(color.x*255.f<0?0:color.x*255.f>255?255:color.x*255.f);
        f->pixels[i+1]=(unsigned char)(color.y*255.f<0?0:color.y*255.f>255?255:color.y*255.f);
        f->pixels[i+2]=(unsigned char)(color.z*255.f<0?0:color.z*255.f>255?255:color.z*255.f);
        f->pixels[i+3]=(unsigned char)(color.w*255.f<0?0:color.w*255.f>255?255:color.w*255.f);
    }}
}
""".trimIndent()
    }
}

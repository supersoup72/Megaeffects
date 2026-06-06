package com.megaeffects

import android.content.Context
import android.util.Log
import java.io.File

data class CompileResult(val success: Boolean, val message: String)

object Compiler {

    // Public so TccCompiler can use it
    val SDK_HEADER_PUBLIC = SDK_HEADER

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

    fun compile(context: Context, code: String, outputPath: String, isGlsl: Boolean): CompileResult {
        val src = if (isGlsl) buildGlslSource(code) else buildCSource(code, context)

        // Method 1: libtcc via JNI (no SELinux issues, preferred)
        if (TccCompiler.isAvailable()) {
            Log.i("MegaEffects", "Compiling via libtcc JNI")
            return TccCompiler.compile(context, src, outputPath)
        }

        // Method 2: subprocess (works on desktop/rooted, fails on stock Android)
        Log.w("MegaEffects", "libtcc not available, trying subprocess")
        return compileSubprocess(context, src, outputPath)
    }

    private fun buildCSource(code: String, context: Context): String {
        val sdkDir = File(context.filesDir, "sdk").also { it.mkdirs() }
        File(sdkDir, "filter_sdk.h").writeText(SDK_HEADER)
        val sdkPath = "${sdkDir.absolutePath}/filter_sdk.h"
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
        vec4 color=texture_sample(f->pixels,f->width,f->height,
            vec2_new((float)x/f->width,(float)y/f->height));
        mainImage(&color,coord);
        int i=(y*f->width+x)*4;
        f->pixels[i  ]=(unsigned char)(color.x*255.f<0?0:color.x*255.f>255?255:color.x*255.f);
        f->pixels[i+1]=(unsigned char)(color.y*255.f<0?0:color.y*255.f>255?255:color.y*255.f);
        f->pixels[i+2]=(unsigned char)(color.z*255.f<0?0:color.z*255.f>255?255:color.z*255.f);
        f->pixels[i+3]=(unsigned char)(color.w*255.f<0?0:color.w*255.f>255?255:color.w*255.f);
    }}
}
""".trimIndent()
    }

    private fun compileSubprocess(context: Context, src: String, outputPath: String): CompileResult {
        val sdkDir = File(context.filesDir, "sdk").also { it.mkdirs() }
        File(sdkDir, "filter_sdk.h").writeText(SDK_HEADER)
        val srcFile = File(context.filesDir, "filter_tmp.c")
        srcFile.writeText(src)

        val candidates = listOf(
            "/data/data/com.termux/files/usr/bin/tcc",
            "/data/data/com.termux/files/usr/bin/clang",
            "tcc"
        )
        val tcc = candidates.firstOrNull { path ->
            try { File(path).also { it.setExecutable(true) }.canExecute() } catch (e: Exception) { false }
        } ?: return CompileResult(false,
            "No compiler available.\nlibtcc not bundled and no subprocess compiler found.\nRebuild APK to bundle libtcc.")

        return try {
            val proc = ProcessBuilder(tcc, "-shared",
                "-I${sdkDir.absolutePath}", "-o", outputPath,
                srcFile.absolutePath, "-lm")
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment()["LD_LIBRARY_PATH"] = "/data/data/com.termux/files/usr/lib"
                }
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val exit = proc.waitFor()
            srcFile.delete()
            if (exit == 0 && File(outputPath).exists())
                CompileResult(true, "Compiled OK (subprocess)")
            else
                CompileResult(false, output.take(500).ifBlank { "Exit $exit" })
        } catch (e: Exception) {
            CompileResult(false, "Subprocess error: ${e.message}")
        }
    }
}

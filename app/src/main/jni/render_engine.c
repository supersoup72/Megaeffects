/*
 * Mega Effects Render Engine
 * Compiled for ARM64 Android via GitHub Actions
 * Called from Python via ctypes
 */

#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <math.h>

/* ── Types ──────────────────────────────────────────────────────────────── */

typedef struct {
    uint8_t *pixels;   /* RGBA */
    int      width;
    int      height;
} Frame;

typedef struct {
    float x, y;          /* normalized 0..1 */
    float scale_x, scale_y;
    float rotate_x, rotate_y, rotate_z;
    float opacity;
    float perspective;
} Transform;

/* ── Math helpers ───────────────────────────────────────────────────────── */

static inline float clampf(float v, float lo, float hi) {
    return v < lo ? lo : v > hi ? hi : v;
}

static inline uint8_t clamp_u8(int v) {
    return (uint8_t)(v < 0 ? 0 : v > 255 ? 255 : v);
}

static inline float lerp(float a, float b, float t) {
    return a + (b - a) * t;
}

/* Bilinear sample from RGBA buffer */
static uint32_t sample_bilinear(const uint8_t *px, int w, int h,
                                 float fx, float fy) {
    int x0 = (int)fx, y0 = (int)fy;
    int x1 = x0 + 1, y1 = y0 + 1;
    float tx = fx - x0, ty = fy - y0;

    x0 = x0 < 0 ? 0 : x0 >= w ? w-1 : x0;
    x1 = x1 < 0 ? 0 : x1 >= w ? w-1 : x1;
    y0 = y0 < 0 ? 0 : y0 >= h ? h-1 : y0;
    y1 = y1 < 0 ? 0 : y1 >= h ? h-1 : y1;

    const uint8_t *p00 = px + (y0*w+x0)*4;
    const uint8_t *p10 = px + (y0*w+x1)*4;
    const uint8_t *p01 = px + (y1*w+x0)*4;
    const uint8_t *p11 = px + (y1*w+x1)*4;

    uint32_t result = 0;
    uint8_t *out = (uint8_t*)&result;
    for (int c = 0; c < 4; c++) {
        float v = lerp(lerp(p00[c], p10[c], tx),
                       lerp(p01[c], p11[c], tx), ty);
        out[c] = clamp_u8((int)v);
    }
    return result;
}

/* ── Transform ──────────────────────────────────────────────────────────── */

void engine_transform(
    const uint8_t *src, uint8_t *dst,
    int width, int height,
    float tx_norm, float ty_norm,   /* normalized -1..1 */
    float scale_x, float scale_y,
    float rot_z_deg,
    float rot_x_deg, float rot_y_deg,
    float opacity,
    float perspective
) {
    memset(dst, 0, width * height * 4);

    /* Convert normalized translation to pixels */
    float tx = tx_norm * width;
    float ty = ty_norm * height;

    float cx = width  * 0.5f;
    float cy = height * 0.5f;
    float rz = rot_z_deg * (float)M_PI / 180.0f;
    float cos_z = cosf(rz), sin_z = sinf(rz);

    float rx_rad = rot_x_deg * (float)M_PI / 180.0f;
    float ry_rad = rot_y_deg * (float)M_PI / 180.0f;
    float p_x = sinf(ry_rad) / perspective;
    float p_y = sinf(rx_rad) / perspective;

    /* Inverse affine for each output pixel */
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            /* Perspective divide */
            float px = (float)x - cx - tx;
            float py = (float)y - cy - ty;
            float pw = 1.0f + p_x * px + p_y * py;
            if (pw < 0.001f) { dst[(y*width+x)*4+3] = 0; continue; }
            float ndx = px / pw + cx;
            float ndy = py / pw + cy;

            /* Inverse rotate Z */
            float dx = ndx - cx, dy = ndy - cy;
            float sx = ( cos_z * dx + sin_z * dy) / scale_x + cx;
            float sy = (-sin_z * dx + cos_z * dy) / scale_y + cy;

            if (sx < 0 || sx >= width || sy < 0 || sy >= height) continue;

            uint32_t samp = sample_bilinear(src, width, height, sx, sy);
            uint8_t *s = (uint8_t*)&samp;
            uint8_t *d = dst + (y*width+x)*4;
            d[0] = s[0]; d[1] = s[1]; d[2] = s[2];
            d[3] = clamp_u8((int)(s[3] * opacity));
        }
    }
}

/* ── Alpha compositing ──────────────────────────────────────────────────── */

void engine_composite(
    uint8_t *dst,           /* canvas (modified in-place) */
    const uint8_t *src,     /* layer to composite on top */
    int width, int height
) {
    int n = width * height;
    for (int i = 0; i < n; i++) {
        const uint8_t *s = src + i*4;
        uint8_t       *d = dst + i*4;
        float sa = s[3] / 255.0f;
        float da = d[3] / 255.0f;
        float oa = sa + da * (1.0f - sa);
        if (oa < 1e-6f) { d[3] = 0; continue; }
        for (int c = 0; c < 3; c++)
            d[c] = clamp_u8((int)((s[c]*sa + d[c]*da*(1.0f-sa)) / oa));
        d[3] = clamp_u8((int)(oa * 255.0f));
    }
}

/* ── Solid color fill ───────────────────────────────────────────────────── */

void engine_fill(uint8_t *dst, int width, int height,
                  uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    int n = width * height;
    for (int i = 0; i < n; i++) {
        dst[i*4]   = r; dst[i*4+1] = g;
        dst[i*4+2] = b; dst[i*4+3] = a;
    }
}

/* ── Clear canvas ───────────────────────────────────────────────────────── */

void engine_clear(uint8_t *dst, int width, int height) {
    memset(dst, 0, width * height * 4);
}

/* ── Resize frame ───────────────────────────────────────────────────────── */

void engine_resize(
    const uint8_t *src, int sw, int sh,
    uint8_t *dst,       int dw, int dh
) {
    float sx = (float)sw / dw;
    float sy = (float)sh / dh;
    for (int y = 0; y < dh; y++) {
        for (int x = 0; x < dw; x++) {
            uint32_t s = sample_bilinear(src, sw, sh, x*sx, y*sy);
            uint8_t *d = dst + (y*dw+x)*4;
            memcpy(d, &s, 4);
        }
    }
}

/* ── Version check ──────────────────────────────────────────────────────── */

const char *engine_version(void) {
    return "MegaEffects RenderEngine 1.0 (C/ARM64)";
}

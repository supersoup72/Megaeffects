#ifndef FILTER_SDK_H
#define FILTER_SDK_H

#include <stdint.h>

typedef struct {
    uint8_t *pixels;      // RGBA pixel buffer
    int width;
    int height;
    double time;          // current time in seconds
    double duration;      // clip duration in seconds
    float *params;        // user-exposed parameters
    int param_count;
    uint8_t *prev_pixels; // previous frame (may be NULL)
    float transform[16];  // 4x4 transform matrix (row-major)
    float opacity;
    int layer_index;
    int layer_count;
} FilterFrame;

typedef struct {
    char name[64];
    char description[256];
    float default_value;
    float min_value;
    float max_value;
} FilterParam;

// Every plugin must implement these:
void        filter_init(void);
void        filter_process(FilterFrame *frame);
void        filter_destroy(void);
const char *filter_name(void);
const char *filter_description(void);
int         filter_param_count(void);
FilterParam filter_param_info(int index);

// Helpers available to filters
static inline uint8_t clamp_u8(int v) {
    return (uint8_t)(v < 0 ? 0 : v > 255 ? 255 : v);
}

static inline float clampf(float v, float lo, float hi) {
    return v < lo ? lo : v > hi ? hi : v;
}

static inline int pixel_index(int x, int y, int width) {
    return (y * width + x) * 4;
}

#endif // FILTER_SDK_H

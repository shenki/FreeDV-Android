#ifndef FREEDV_JNI_H
#define FREEDV_JNI_H

#include <stdbool.h>
#include <codec2_fdmdv.h>

void jni_update_sync(bool state);
void jni_update_stats(const struct FDMDV_STATS *stats, const float *spectrum);
void jni_cb(const signed char *data, int len);

#endif /* FREEDV_JNI_H */

/*
 *
 * FreeDV for Android JNI interface
 *
 * Copyright 2012 Joel Stanley <joel@jms.id.au>
 *
 */

#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <stdbool.h>
#include <assert.h>

#include <libusb.h>

#include <jni.h>
#include <android/log.h>

#include "freedv_usb.h"
#include "freedv_rx.h"

#include "freedv_jni.h"

#define UNUSED __attribute__((unused))

#define LOGD(...) \
        __android_log_print(ANDROID_LOG_DEBUG, "FreedvJNINative", __VA_ARGS__)
#define LOGE(...) \
        __android_log_print(ANDROID_LOG_ERROR, "FreedvJNINative", __VA_ARGS__)

struct app_ctx {
    int quitfd;
    int logfd;
    pthread_key_t env_key;
    pthread_t usb_thread;
    bool usb_thread_run;
};

struct app_ctx *ctx;

bool cb_attached = 0;
jobject audioPlaybackObj;
jmethodID AudioPlayback_write;
jmethodID AudioPlayback_sync;
jmethodID AudioPlayback_stats;
jclass AudioPlayback;
JavaVM* java_vm;

static void *usb_thread_entry(void *data) {
    struct app_ctx *ctx = (struct app_ctx *)data;
    LOGD("usb_thread started\n");
    prctl(PR_SET_NAME, "usb_thread");
    while (ctx->usb_thread_run) {
        /* Handle libusb event. This call is blocking. */
        int rc = libusb_handle_events(NULL);
        if (rc != LIBUSB_SUCCESS) {
            LOGE("libusb_handle_events: %s.\n", libusb_error_name(rc));
            break;
        }
    }
    LOGD("usb_thread exiting");
    return NULL;
}


int init_jni_cb(JNIEnv *env, jobject audioPlayback) {
    // Get write callback handle
    AudioPlayback = (*env)->FindClass(env,
            "au/id/jms/freedvdroid/AudioPlayback");
    if (!AudioPlayback) {
        LOGE("Could not find au.id.jms.freedvdroid.AudioPlayback");
        return -1;
    }
    // Store ref to audio class instance
    audioPlaybackObj = (*env)->NewGlobalRef(env, audioPlayback);
    AudioPlayback_write = (*env)->GetMethodID(env, AudioPlayback, "write",
            "([B)V");
    if (!AudioPlayback_write) {
        LOGE("Could not find au.id.jms.freedvdroid.AudioPlayback.write()");
        (*env)->DeleteGlobalRef(env, AudioPlayback);
        return -1;
    }
    AudioPlayback_sync = (*env)->GetMethodID(env, AudioPlayback, "sync",
            "(Z)V");
    if (!AudioPlayback_sync) {
        LOGE("Could not find au.id.jms.freedvdroid.AudioPlayback.sync()");
        (*env)->DeleteGlobalRef(env, AudioPlayback);
        return -1;
    }
    AudioPlayback_stats = (*env)->GetMethodID(env, AudioPlayback, "stats",
            "(FF[F[F)V");
    if (!AudioPlayback_sync) {
        LOGE("Could not find au.id.jms.freedvdroid.AudioPlayback.stats()");
        (*env)->DeleteGlobalRef(env, AudioPlayback);
        return -1;
    }
    cb_attached = false;
    return 0;
}

static void destroy_jni_cb(void* data) {
    if (data != NULL) {
        (*java_vm)->DetachCurrentThread(java_vm);
        pthread_setspecific(ctx->env_key, NULL);
        LOGD("Detached jni_cb thread");
    }
}

void jni_cb(const signed char *data, int len) {
    JNIEnv *env;
    if (cb_attached == false) {
        (*java_vm)->AttachCurrentThread(java_vm, &env, NULL);
        pthread_setspecific(ctx->env_key, (void *)env);
    } else {
        env = pthread_getspecific(ctx->env_key);
    }

    jbyteArray dataArray = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, dataArray, 0, len, data);
    (*env)->CallVoidMethod(env, audioPlaybackObj, AudioPlayback_write,
            dataArray);
    (*env)->DeleteLocalRef(env, dataArray);
}

void jni_update_sync(bool state) {
    JNIEnv *env;
    if (cb_attached == false) {
        (*java_vm)->AttachCurrentThread(java_vm, &env, NULL);
        pthread_setspecific(ctx->env_key, (void *)env);
    } else {
        env = pthread_getspecific(ctx->env_key);
    }
    (*env)->CallVoidMethod(env, audioPlaybackObj, AudioPlayback_sync, state);
}


void jni_update_stats(const struct FDMDV_STATS *stats, const float *spectrum) {
    JNIEnv *env;
    if (cb_attached == false) {
        (*java_vm)->AttachCurrentThread(java_vm, &env, NULL);
        pthread_setspecific(ctx->env_key, (void *)env);
    } else {
        env = pthread_getspecific(ctx->env_key);
    }

    /* FDMDV_STATS structure:
     * float snr_est - estimated SNR of rx signal in dB (3 kHz noise BW)
     * COMP rx_symbols[FDMDV_NSYM] - latest received symbols, for scatter plot 
     * int fest_coarse_fine - freq est state, 0-coarse 1-fine
     * float foff - estimated freq offset in Hz
     * float rx_timing - estimated optimum timing offset in samples
     * float clock_offset - Estimated tx/rx sample clock offset in ppm
    */

    /* Should size be the number of floats, or number of bytes? */
    /* Using numbero f floats. COMP is two floats big. */
    jfloatArray symbolArray = (*env)->NewFloatArray(env, 2*FDMDV_NSYM);
    (*env)->SetFloatArrayRegion(env, symbolArray, 0, 2*FDMDV_NSYM,
            (float *)stats->rx_symbols);

    jfloatArray specArray = (*env)->NewFloatArray(env, FDMDV_NSPEC);
    (*env)->SetFloatArrayRegion(env, specArray, 0, FDMDV_NSPEC, spectrum);

    (*env)->CallVoidMethod(env, audioPlaybackObj, AudioPlayback_stats,
            stats->foff, stats->rx_timing/FDMDV_NOM_SAMPLES_PER_FRAME,
            symbolArray, specArray);

    (*env)->DeleteLocalRef(env, symbolArray);
    (*env)->DeleteLocalRef(env, specArray);
}


JNIEXPORT jboolean JNICALL
Java_au_id_jms_freedvdroid_Freedv_setup(JNIEnv *env, jclass class,
        jobject audioPlayback) {
    int rc;

    ctx = calloc(sizeof(struct app_ctx), 1);

    rc = init_jni_cb(env, audioPlayback);
    if (rc != 0) {
        LOGE("init_jni_cb: %d\n", rc);
        goto out;
    }

    rc = pthread_key_create(&ctx->env_key, destroy_jni_cb);
    if (rc) {
        LOGE("Error creating thread key");
        goto out;
    }

    rc = usb_setup();
    if (rc != 0) {
        LOGE("usb_setup: %d\n" ,rc);
        goto out;
    }

    rc = usb_start_transfers();
    if (rc != 0) {
        LOGE("usb_start_transfers: %d\n" ,rc);
        goto out;
    }

    rc = freedv_create();
    if (rc == 0) {
        LOGE("freedv_create: %d\n" ,rc);
        goto out;
    }

    ctx->usb_thread_run = true;
    rc = pthread_create(&ctx->usb_thread, NULL, usb_thread_entry, ctx);
    if (rc < 0) {
        LOGE("pthread_create: usb_thread failed: %d\n", rc);
        goto out;
    }

    LOGD("Initalised\n");
    return true;

out:
    LOGE("Failed to initalise (%d)\n", rc);
    if (ctx->usb_thread)
        pthread_kill(ctx->usb_thread, SIGKILL);
    return false;
}

JNIEXPORT jboolean JNICALL
Java_au_id_jms_freedvdroid_Freedv_close(JNIEnv *env, jclass class) {
    // Stop thread
    ctx->usb_thread_run = false;
    cb_attached = false;
    if (pthread_join(ctx->usb_thread, NULL) < 0) {
        LOGE("Could not join usb_thread");
    }
    // Destroy JNI global references
    (*env)->DeleteGlobalRef(env, audioPlaybackObj);
    // Cleanup the rest
    usb_exit();
    fdmdv_close();
    free(ctx);
    LOGD("Closed");
    return true;
}


JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved UNUSED)
{
    LOGD("loaded");
    java_vm = vm;
    return JNI_VERSION_1_6;
}

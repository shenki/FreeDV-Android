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

#include <libusb.h>

#include <jni.h>
#include <android/log.h>

#define UNUSED __attribute__((unused))

#define LOGD(...) \
        __android_log_print(ANDROID_LOG_DEBUG, "FreedvJNINative", __VA_ARGS__)
#define LOGE(...) \
        __android_log_print(ANDROID_LOG_ERROR, "FreedvJNINative", __VA_ARGS__)

struct app_ctx {
    int quitfd;
    int logfd;
    pthread_t usb_thread;
};

jobject audioPlaybackObj;
jmethodID AudioPlayback_write;
jclass AudioPlayback;
JavaVM* java_vm;

static void *usb_thread_entry(void *data) {
    struct app_ctx *ctx = (struct app_ctx *)data;
    LOGD("usb_thread started\n");
    prctl(PR_SET_NAME, "usb_thread");
    while (1) {
#if 0
        uint8_t buf[1];
        if (read(ctx->quitfd, buf, 1) != -EAGAIN) {
            LOGD("usb_thread: exit due to quitfd\n");
            break;
        }
#endif
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
        LOGE("Could not find au.id.jms.freedvdroid.AudioPlayback");
        (*env)->DeleteGlobalRef(env, AudioPlayback);
        return -1;
    }
    return 0;
}

int jni_cb(uint8_t *data, int len) {
    JNIEnv* env;
    (*java_vm)->AttachCurrentThread(java_vm, &env, NULL);

    jbyteArray dataArray;
    dataArray = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, dataArray, 0, len, data);
    (*env)->CallVoidMethod(env, audioPlaybackObj, AudioPlayback_write,
            dataArray);
    (*env)->DeleteLocalRef(env, dataArray);
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_au_id_jms_freedvdroid_Freedv_setup(JNIEnv *env, jclass class,
        jobject audioPlayback) {
    int rc;

    struct app_ctx* ctx = calloc(sizeof(struct app_ctx), 1);

    rc = init_jni_cb(env, audioPlayback);
    if (rc != 0) {
        LOGE("init_jni_cb: %d\n", rc);
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

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved UNUSED)
{
    LOGD("loaded");
    java_vm = vm;
    return JNI_VERSION_1_6;
}

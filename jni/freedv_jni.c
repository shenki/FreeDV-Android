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

#include <libusb.h>

#include "freedv_usb.h"
#include "freedv_decode.h"

#define UNUSED __attribute__((unused))

#define MAX_EVENTS 8

struct app_ctx {
    int quitfd;
    int audiofd;
    int logfd;
    pthread_t usb_thread;
    pthread_t freedv_thread;
};

static void *usb_thread_entry(void *data) {
    struct app_ctx *ctx = (struct app_ctx *)data;
    fprintf(stderr, "usb_thread started\n");
    prctl(PR_SET_NAME, "usb_thread");
    while (1) {
#if 0
        uint8_t buf[1];
        if (read(ctx->quitfd, buf, 1) != -EAGAIN) {
            fprintf(stderr, "usb_thread: exit due to quitfd\n");
            break;
        }
#endif
        /* Handle libusb event. This call is blocking. */
        int rc = libusb_handle_events(NULL);
        if (rc != LIBUSB_SUCCESS) {
            fprintf(stderr, "libusb_handle_events: %s.\n", libusb_error_name(rc));
            break;
        }
    }
    fprintf(stderr, "usb_thread exiting");
    return NULL;
}

int main(int argc, char** argv) {
    int rc;
    if (argc != 2) {
        fprintf(stderr, "usage: %s [filename.raw]\n", argv[0]); 
        exit(EXIT_FAILURE);
    }

    struct app_ctx* ctx = calloc(sizeof(struct app_ctx), 1);

    /* logfd is used for testing. */
    ctx->logfd = open(argv[1], O_RDWR | O_CREAT, S_IRUSR | S_IWUSR);
    if (ctx->audiofd < 0) {
        perror(argv[1]);
        return errno;
    }

    printf("logfd: %d\n", ctx->logfd);
    printf("audiofd: %d\n", ctx->audiofd);
    printf("quitfd: %d\n", ctx->quitfd);

    rc = usb_setup();
    if (rc != 0) {
        fprintf(stderr, "usb_setup: %d\n" ,rc);
        goto out;
    }

    rc = usb_start_transfers(ctx->logfd);
    if (rc != 0) {
        fprintf(stderr, "usb_start_transfers: %d\n" ,rc);
        goto out;
    }

    rc = freedv_create();
    if (rc == 0) {
        fprintf(stderr, "freedv_create: %d\n" ,rc);
        goto out;
    }

    rc = pthread_create(&ctx->usb_thread, NULL, usb_thread_entry, ctx);
    if (rc < 0) {
        fprintf(stderr, "pthread_create: usb_thread failed: %d\n", rc);
        goto out;
    }

    /* This code is designed to be run from within an Android app. */
    while(1) {
        pthread_yield();
    }

out:
    fprintf(stderr, "Exiting\n");
    if (ctx->usb_thread)
        pthread_kill(ctx->usb_thread, SIGKILL);
    if (ctx->freedv_thread)
        pthread_kill(ctx->freedv_thread, SIGKILL);
    return rc;
}

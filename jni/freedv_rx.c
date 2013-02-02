/*
  fl_fdmdv.cxx
  Created 14 June 2012
  David Rowe

  Fltk 1.3 based GUI program to prototype FDMDV & Codec 2 integration
  issues such as:

    + spectrum, waterfall, and other FDMDV GUI displays
    + integration with real time audio I/O using portaudio
    + what we do with audio when out of sync
*/

#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <stdint.h>
#include <samplerate.h>
#include <pthread.h>
#include <stdbool.h>

#include "codec2_fdmdv.h"
#include "codec2.h"
//#include "varicode.h"

#include <android/log.h>
#define LOGD(...) \
        __android_log_print(ANDROID_LOG_DEBUG, "FreedvRxNative", __VA_ARGS__)
#define LOGE(...) \
        __android_log_print(ANDROID_LOG_ERROR, "FreedvRxNative", __VA_ARGS__)

#define MIN_DB             -40.0 
#define MAX_DB               0.0
#define BETA                 0.1  // constant for time averageing spectrum data
#define MIN_HZ               0
#define MAX_HZ            4000
#define WATERFALL_SECS_Y     5    // number of seconds respresented by y axis of waterfall
#define DT                   0.02 // time between samples 
#define FS                8000    // FDMDV modem sample rate

#define SCATTER_MEM       (FDMDV_NSYM)*50
#define SCATTER_X_MAX        3.0
#define SCATTER_Y_MAX        3.0

// main window params

#define W                  1200
#define W3                 (W/3)
#define H                  600
#define H2                 (H/2)
#define SP                  20

// sound card

#define SAMPLE_RATE  48000                        /* 48 kHz sampling rate rec. as we
                                                     can trust accuracy of sound
                                                     card                                    */
#define N8           FDMDV_NOM_SAMPLES_PER_FRAME  /* processing buffer size at 8 kHz         */
#define MEM8 (FDMDV_OS_TAPS/FDMDV_OS)
#define N48          (N8*FDMDV_OS)                /* processing buffer size at 48 kHz        */
#define NUM_CHANNELS 2                            /* I think most sound cards prefer stereo,
                                                     we will convert to mono                 */

#define BITS_PER_CODEC_FRAME (2*FDMDV_BITS_PER_FRAME)
#define BYTES_PER_CODEC_FRAME (BITS_PER_CODEC_FRAME/8)

// Globals --------------------------------------

char         *fin_name = NULL;
char         *fout_name = NULL;
char         *sound_dev_name = NULL;
FILE         *fin = NULL;
FILE         *fout = NULL;
struct FDMDV *fdmdv;
struct CODEC2 *codec2;

SRC_STATE *insrc1;

float g_avmag[FDMDV_NSPEC];
struct FDMDV_STATS stats;
int count;
//struct VARICODE_DEC  g_varicode_dec_states;

pthread_mutex_t mutex;

// Main processing loop states ------------------

short  input_buf[2*FDMDV_NOM_SAMPLES_PER_FRAME];
int    n_input_buf = 0;
int    g_nin = FDMDV_NOM_SAMPLES_PER_FRAME;
short *output_buf;
int    n_output_buf = 0;
int    codec_bits[2*FDMDV_BITS_PER_FRAME];
int    g_state = 0;

// Portaudio states -----------------------------

typedef struct {
    float               in48k[FDMDV_OS_TAPS + N48];
    float               in8k[MEM8 + N8];
} paCallBackData;

/*------------------------------------------------------------------*\

  FUNCTION: per_frame_rx_processing()
  AUTHOR..: David Rowe
  DATE....: July 2012
  
  Called every rx frame to take a buffer of input modem samples and
  convert them to a buffer of output speech samples.

  The sample source could be a sound card or file.  The sample source
  supplies a fixed number of samples with each call.  However
  fdmdv_demod requires a variable number of samples for each call.
  This function will buffer as appropriate and call fdmdv_demod with
  the correct number of samples.

  The processing sequence is:

  collect demod input samples from sound card 1 A/D
  while we have enough samples:
    demod samples into bits
    decode bits into speech samples
    output a buffer of speech samples to sound card 2 D/A

  Note that sound card 1 and sound card 2 will have slightly different
  sample rates, as their sample clocks are not syncronised.  We
  effectively lock the system to the demod A/D (sound card 1) sample
  rate. This ensures the demod gets a continuous sequence of samples,
  maintaining sync. Sample underflow or overflow will instead occur on
  the sound card 2 D/A.  This is acceptable as a buffer of lost or
  extra speech samples is unlikely to be noticed.

  The situation is actually a little more complex than that.  Through
  the demod timing estimation the buffers supplied to sound card D/A 2
  are effectively clocked at the remote modulator sound card D/A clock
  rate.  We slip/gain buffers supplied to sound card 2 to compensate.

  The current demod handles varying clock rates by having a variable
  number of input samples, e.g. 120 160 (nominal) or 200.  However the
  A/D always delivers a fixed number of samples.

  So we currently need some logic between the A/D and the demod:
    + A/D delivers fixed number of samples
    + demod processes a variable number of samples
    + this means we run demod 0,1 or 2 times, depending 
      on number of buffered A/D samples
    + demod always outputs 1 frame of bits
    + so run demod and speech decoder 0, 1 or 2 times
  
  The ouput of the demod is codec voice data so it's OK if we miss or
  repeat a frame every now and again.

  TODOs:

    + this might work with arbitrary input and output buffer lengths,
    0,1, or 2 only apply if we are inputting the nominal number of
    samples on every call.

    + so the I/O buffer sizes might not matter, as long as they of
    reasonable size (say twice the nominal size).

\*------------------------------------------------------------------*/

void per_frame_rx_processing(short  output_buf[], /* output buf of decoded speech samples          */
                             int   *n_output_buf, /* how many samples currently in output_buf[]    */
                             int    codec_bits[], /* current frame of bits for decoder             */
                             short  input_buf[],  /* input buf of modem samples input to demod     */ 
                             int   *n_input_buf   /* how many samples currently in input_buf[]     */
                             )
{
    int    sync_bit;
    COMP  rx_fdm[FDMDV_MAX_SAMPLES_PER_FRAME];
    int    rx_bits[FDMDV_BITS_PER_FRAME];
    unsigned char  packed_bits[BYTES_PER_CODEC_FRAME];
    float  rx_spec[FDMDV_NSPEC];
    int    i, nin_prev, bit, byte;
    int    next_state;

    assert(*n_input_buf <= (2*FDMDV_NOM_SAMPLES_PER_FRAME));    
   
    /*
      This while loop will run the demod 0, 1 (nominal) or 2 times:

      0: when tx sample clock runs faster than rx, occasionally we
         will run out of samples

      1: normal, run decoder once, every 2nd frame output a frame of
         speech samples to D/A

      2: when tx sample clock runs slower than rx, occasionally we will
         have enough samples to run demod twice.

      With a +/- 10 Hz sample clock difference at FS=8000Hz (+/- 1250
      ppm), case 0 or 1 occured about once every 30 seconds.  This is
      no problem for the decoded audio.
    */

    while(*n_input_buf >= g_nin) {

        // demod per frame processing

        for(i=0; i<g_nin; i++) {
            rx_fdm[i].real = (float)input_buf[i]/FDMDV_SCALE;
            rx_fdm[i].imag = 0.0;
        }
        nin_prev = g_nin;
        fdmdv_demod(fdmdv, rx_bits, &sync_bit, rx_fdm, &g_nin);
        *n_input_buf -= nin_prev;
        assert(*n_input_buf >= 0);

        // shift input buffer

        for(i=0; i<*n_input_buf; i++)
            input_buf[i] = input_buf[i+nin_prev];

        // compute rx spectrum & get demod stats, and update GUI plot data

        fdmdv_get_rx_spectrum(fdmdv, rx_spec, rx_fdm, nin_prev);

        // Average rx spectrum data using a simple IIR low pass filter
        for(i = 0; i < FDMDV_NSPEC; i++) 
        {
            g_avmag[i] = BETA * g_avmag[i] + (1.0 - BETA) * rx_spec[i];
        }

        fdmdv_get_demod_stats(fdmdv, &stats);
        jni_update_stats(&stats, g_avmag);
        count++;

        /* 
           State machine to:

           + Mute decoded audio when out of sync.  The demod is synced
             when we are using the fine freq estimate and SNR is above
             a thresh.

           + Decode codec bits only if we have a 0,1 sync bit
             sequence.  Collects two frames of demod bits to decode
             one frame of codec bits.
        */

        next_state = g_state;
        switch (g_state) {
        case 0:
            /* mute output audio when out of sync */

            if (*n_output_buf < 2*codec2_samples_per_frame(codec2) - N8) {
                for(i=0; i<N8; i++)
                    output_buf[*n_output_buf + i] = 0;
                *n_output_buf += N8;
            }
            if (!(*n_output_buf <= (2*codec2_samples_per_frame(codec2)))) {
                LOGE("*n_output_buf <= (2*codec2_samples_per_frame(codec2))");
            }

            if ((stats.fest_coarse_fine == 1))// && (stats.snr_est > 3.0))
                next_state = 1;

            break;
        case 1:
            if (sync_bit == 0) {
                next_state = 2;

                /* first half of frame of codec bits */

                memcpy(codec_bits, rx_bits, FDMDV_BITS_PER_FRAME*sizeof(int));
            }
            else
                next_state = 1;

            if (stats.fest_coarse_fine == 0)
                next_state = 0;

            break;
        case 2:
            next_state = 1;

            if (stats.fest_coarse_fine == 0)
                next_state = 0;

            if (sync_bit == 1) {
                /* second half of frame of codec bits */

                memcpy(&codec_bits[FDMDV_BITS_PER_FRAME], rx_bits, FDMDV_BITS_PER_FRAME*sizeof(int));

#if 0
                // extract data bit

                int data_flag_index = codec2_get_spare_bit_index(codec2);
                assert(data_flag_index != -1); // not supported for all rates

                short abit = codec_bits[data_flag_index];
                char  ascii_out;

                int n_ascii = varicode_decode(&g_varicode_dec_states, &ascii_out, &abit, 1, 1);
                assert((n_ascii == 0) || (n_ascii == 1));
                if (n_ascii) {
                    short ashort = ascii_out;
                    LOGE("%c", ashort);
                }
#endif
                // reconstruct missing bit we steal for data bit and decode speech
                codec2_rebuild_spare_bit(codec2, codec_bits);

                /* pack bits, MSB received first  */

                bit = 7; byte = 0;
                memset(packed_bits, 0, BYTES_PER_CODEC_FRAME);
                for(i=0; i<BITS_PER_CODEC_FRAME; i++) {
                    packed_bits[byte] |= (codec_bits[i] << bit);
                    bit--;
                    if (bit < 0) {
                        bit = 7;
                        byte++;
                    }
                }
                assert(byte == BYTES_PER_CODEC_FRAME);

                /* add decoded speech to end of output buffer */

                if (*n_output_buf <= codec2_samples_per_frame(codec2)) {
                    codec2_decode(codec2, &output_buf[*n_output_buf], packed_bits);
                    *n_output_buf += codec2_samples_per_frame(codec2);
                }
                assert(*n_output_buf <= (2*codec2_samples_per_frame(codec2)));

            }
            break;
        }
        if (!!g_state != !!next_state) {
            jni_update_sync(g_state == 0);
        }
        g_state = next_state;
    }
}

int resample_48k_to_8k(
        short      output_short[],
        short      input_short[],
        int        length_output_short, // maximum output array length in samples 
        int        length_input_short
        )
{
    SRC_DATA src_data;
    float    input[N48*2];
    float    output[N48*2];

    const int input_sample_rate = 48000;
    const int output_sample_rate = 8000;

    assert(length_input_short <= N48*2);
    assert(length_output_short <= N48*2);

    src_short_to_float_array(input_short, input, length_input_short);

    src_data.data_in = input;
    src_data.data_out = output;
    src_data.input_frames = length_input_short;
    src_data.output_frames = length_output_short;
    src_data.end_of_input = 0;
    src_data.src_ratio = (float)output_sample_rate/input_sample_rate;

    src_process(insrc1, &src_data);

    assert(src_data.output_frames_gen <= length_output_short);
    src_float_to_short_array(output, output_short, src_data.output_frames_gen);

    return src_data.output_frames_gen;
}

int decode_file(short *buf_48k_stereo, int num_bytes_48k_stereo) {

    pthread_mutex_lock(&mutex);
    int ret = 0, i;

    int num_shorts_48k_stereo = num_bytes_48k_stereo/2;

    unsigned int num_shorts_48k_mono = num_shorts_48k_stereo/2;
    short buf_8k_mono[N48*2];
    short buf_48k_mono[1920];

    assert(num_shorts_48k_mono < sizeof(buf_48k_mono)/sizeof(short));

    for(i = 0; i < num_shorts_48k_mono; i++, buf_48k_stereo += 2) {
        buf_48k_mono[i] = *buf_48k_stereo;
    }
    int shorts_in_8kbuf = resample_48k_to_8k(buf_8k_mono, buf_48k_mono, N48*2, i);
    assert(shorts_in_8kbuf >= FDMDV_NOM_SAMPLES_PER_FRAME);

    /* Copy NOM_SAMPLES of shorts into &input_buf[n_input_buf] */
    memcpy(&input_buf[n_input_buf], buf_8k_mono,
            FDMDV_NOM_SAMPLES_PER_FRAME*sizeof(short));
    n_input_buf += FDMDV_NOM_SAMPLES_PER_FRAME;

    per_frame_rx_processing(output_buf, &n_output_buf,
            codec_bits,
            input_buf, &n_input_buf);

    if (n_output_buf > N8) {
        jni_cb(output_buf, N8*sizeof(short));

        n_output_buf -= N8;
        assert(n_output_buf >= 0);

        /* shift speech sample output buffer */
        for(i=0; i<n_output_buf; i++)
            output_buf[i] = output_buf[i+N8];
    }

    pthread_mutex_unlock(&mutex);
    return ret;
}

int freedv_create() {
    pthread_mutex_init(&mutex, NULL);

    int src_error;
    insrc1 = src_new(SRC_SINC_FASTEST, 1, &src_error);

    fdmdv = fdmdv_create();
    codec2 = codec2_create(CODEC2_MODE_1400);
    output_buf =
        (short*)malloc(2*sizeof(short)*codec2_samples_per_frame(codec2));

    return 1;
}

void fdmdv_close() {
    if (fdmdv)
        fdmdv_destroy(fdmdv);
    if (codec2)
        codec2_destroy(codec2);
    if (output_buf)
        free(output_buf);
    if (insrc1)
        free(insrc1);
}

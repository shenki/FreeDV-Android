#ifndef FREEDV_RX_H
#define FREEDV_RX_H

int freedv_create(void);
void fdmdv_close(void);
int rx_decode_buffer(const short *buf_48k_stereo, int num_bytes_48k_stereo);

#endif /* FREEDV_RX_H */

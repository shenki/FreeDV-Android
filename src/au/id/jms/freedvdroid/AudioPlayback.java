package au.id.jms.freedvdroid;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class AudioPlayback {
	private static final String TAG = "AudioPlayback";
	
	private static final int SAMPLE_RATE_HZ = 8000;

	private AudioTrack track = null;
		
	public void setup() {
		Log.i(TAG, "Audio Playback");
		
		int bufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_HZ, 
				AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
		Log.d(TAG, "Buf size: " + bufSize);
		
		track = new AudioTrack(AudioManager.STREAM_MUSIC,
				SAMPLE_RATE_HZ,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				bufSize,
				AudioTrack.MODE_STREAM);
		track.play();
	}
	
	public void write(byte[] decodedAudio) {
		track.write(decodedAudio, 0, decodedAudio.length);
	}
	
	public void stop() {
		track.stop();
	}
	
	public void pause() {
		track.pause();
		track.flush();
	}
}

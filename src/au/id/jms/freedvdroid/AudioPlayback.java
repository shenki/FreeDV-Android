package au.id.jms.freedvdroid;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.util.Log;

public class AudioPlayback {
	private static final String TAG = "AudioPlayback";
	
	private static final int SAMPLE_RATE_HZ = 8000;

	private AudioTrack track;
	private Handler mStatsHandler;
	private Handler mSyncHandler;
	
	AudioPlayback(Handler syncHandler, Handler statsHandler) {
		mSyncHandler = syncHandler;
		mStatsHandler = statsHandler;
		int bufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_HZ, 
				AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
		Log.d(TAG, "Buf size: " + bufSize);
		
		track = new AudioTrack(AudioManager.STREAM_MUSIC,
				SAMPLE_RATE_HZ,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				bufSize,
				AudioTrack.MODE_STREAM);
	}
		
	public void setup() {
		Log.i(TAG, "Audio Playback");
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
	
	public void sync(boolean sync) {
//		Log.d(TAG, "Sync is now " + sync);
        mSyncHandler.obtainMessage(1, sync).sendToTarget();
	}
	
	public void stats(float[] stats) {
		float[] symbols = java.util.Arrays.copyOfRange(stats, 2, 32);
		FdmdvStats s = new FdmdvStats(stats[0], stats[1], symbols);
        mStatsHandler.obtainMessage(1, s).sendToTarget();
	}
}

package au.id.jms.freedvdroid;

public class FdmdvStats {
	
	public float freqOffEstHz;
	public float rxTimingEstSamples;

	FdmdvStats(float foff, float rx_timing) {
		freqOffEstHz = foff;
		rxTimingEstSamples = rx_timing;
	}
}

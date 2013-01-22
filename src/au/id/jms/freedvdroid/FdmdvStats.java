package au.id.jms.freedvdroid;

public class FdmdvStats {
	
	public float freqOffEstHz;
	public float rxTimingEstSamples;
	public float[] rxSymbols;

	FdmdvStats(float foff, float rx_timing, float[] symbols) {
		freqOffEstHz = foff;
		rxTimingEstSamples = rx_timing;
		rxSymbols = symbols;
	}
}

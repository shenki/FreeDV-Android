package au.id.jms.freedvdroid;

public class FdmdvStats {
	
	public float freqOffEstHz;
	public float rxTimingEstSamples;
	public float[] rxSymbols;
	public float[] avgSpectrum;

	FdmdvStats(float foff, float rx_timing, float[] symbols, float[] spectrum) {
		freqOffEstHz = foff;
		rxTimingEstSamples = rx_timing;
		rxSymbols = symbols;
		avgSpectrum = spectrum;
	}
}

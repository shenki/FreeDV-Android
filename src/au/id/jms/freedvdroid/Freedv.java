package au.id.jms.freedvdroid;

public class Freedv {
    static {
        System.loadLibrary("freedv");
        System.loadLibrary("samplerate");
        System.loadLibrary("usb-1.0");
        System.loadLibrary("droidfreedv");
    }

    public native boolean setup(AudioPlayback c);
    public native boolean close();
}
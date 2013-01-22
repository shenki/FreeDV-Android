package au.id.jms.freedvdroid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.libusb.UsbHelper;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import au.id.jms.graphview.GraphView;
import au.id.jms.graphview.GraphView.GraphViewData;
import au.id.jms.graphview.GraphView.GraphViewSeries;
import au.id.jms.graphview.GraphView.GraphViewStyle;
import au.id.jms.graphview.LineGraphView;

public class MainActivity extends Activity {
	
	private static final String TAG = "UsbAudio";
	
    private static final String ACTION_USB_PERMISSION = "au.id.jms.freedvdroid.USB_PERMISSION";

	private static final int HISTORY_SIZE = 60;
    PendingIntent mPermissionIntent = null;
    UsbManager mUsbManager = null;
    UsbDevice mAudioDevice = null;
    
    Freedv mUsbAudio = null;

	Thread mUsbThread = null;

	private UsbReciever mUsbPermissionReciever;

	private AudioPlayback mAudioPlayback;

	private static Handler mSyncHandler;
	private static Handler mStatsHandler;
	
	private int graphOffsetX;
	private GraphView freqOffsetGraphView;
	private ArrayList<GraphViewData> freqOffsetData;
	private GraphViewSeries freqOffsetSeries;
	private GraphView timingEstGraphView;
	private ArrayList<GraphViewData> timingEstData;
	private GraphViewSeries timingEstSeries;
	
	private ScatterGraphView mScatter;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "Hello, World!");
        
        // Grab the USB Device so we can get permission
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
    		UsbInterface intf = device.getInterface(0);
    		if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) {
    			Log.d(TAG, "Audio class device: " + device);
    			mAudioDevice = device;
    		}
        }
        
        // Load native lib
        System.loadLibrary("usb-1.0");
        UsbHelper.useContext(getApplicationContext());
        
		mSyncHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				updateSyncState((Boolean) msg.obj);
			}
		};
		mStatsHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				FdmdvStats stats = (FdmdvStats) msg.obj;
				updateStatsGraph(stats);
			}
		};
		
		freqOffsetGraphView = new LineGraphView(this, "Frequency Estimation");
		((LinearLayout) findViewById(R.id.graph1)).addView(freqOffsetGraphView);
		
		timingEstGraphView = new LineGraphView(this, "Timing Offset");
		((LinearLayout) findViewById(R.id.graph2)).addView(timingEstGraphView);
        
    	mUsbAudio = new Freedv();
    	mAudioPlayback = new AudioPlayback(mSyncHandler, mStatsHandler);
    	
    	mScatter = (ScatterGraphView) findViewById(R.id.scattergraph);
    	
    	// Buttons
		final Button startButton = (Button) findViewById(R.id.button1);
		final Button stopButton = (Button) findViewById(R.id.button2);
		
		startButton.setEnabled(true);
		stopButton.setEnabled(false);
		
		startButton.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				Log.d(TAG, "Start pressed");
		    	if (mUsbAudio.setup(mAudioPlayback) == true) {
		        	mAudioPlayback.setup();
		    		startButton.setEnabled(false);
		    		stopButton.setEnabled(true);
		    		
					graphOffsetX = 0;
					
					Log.d(TAG, "Graph setup");
					freqOffsetData = new ArrayList<GraphViewData>(HISTORY_SIZE); 
			        freqOffsetSeries = new GraphViewSeries("Frequency",
			        		new GraphViewStyle(Color.rgb(200, 50, 00), 3), freqOffsetData);
			        freqOffsetGraphView.addSeries(freqOffsetSeries);
					timingEstData = new ArrayList<GraphViewData>(HISTORY_SIZE); 
					timingEstSeries = new GraphViewSeries("Samples",
			        		new GraphViewStyle(Color.rgb(50, 200, 00), 3), timingEstData);
					timingEstGraphView.addSeries(timingEstSeries);

		    	}
			}
		});
		
		stopButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.d(TAG, "Stop pressed");
		    	mUsbAudio.close();
		    	mAudioPlayback.pause();
		    	
				// Clear graph
		        freqOffsetGraphView.removeSeries(freqOffsetSeries);
		        freqOffsetData = null;
		        freqOffsetSeries = null;
		        timingEstGraphView.removeSeries(freqOffsetSeries);
		        timingEstData = null;
		        timingEstSeries = null;
		    	
	    		startButton.setEnabled(true);
	    		stopButton.setEnabled(false);
			}
		});
        
        // Register for permission
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        mUsbPermissionReciever = new UsbReciever();
        registerReceiver(mUsbPermissionReciever, filter);
        
        // Request permission from user
        if (mAudioDevice != null && mPermissionIntent != null) {
        	mUsbManager.requestPermission(mAudioDevice, mPermissionIntent);
        } else {
        	Log.e(TAG, "Device not present? Can't request peremission");
        }
    }
    
    public void updateSyncState(boolean state) {
    	RadioButton r = (RadioButton) findViewById(R.id.radioButton1);
    	r.setChecked(state);
    }
    
    public void updateStatsGraph(FdmdvStats stats) {
    	
    	mScatter.addPoint(stats.rxSymbols);

    	if (freqOffsetSeries != null) { 
    		while (freqOffsetSeries.getItemCount() > HISTORY_SIZE) {
    			freqOffsetSeries.removeValue();
    		}
    		freqOffsetSeries.addValue(new GraphViewData(graphOffsetX, stats.freqOffEstHz));

    		// YAARRR, Here be hacks.
    		// Need to work out how to force a redraw code to be called without removing and 
    		// re-adding the view.
    		LinearLayout layout = (LinearLayout) findViewById(R.id.graph1);
    		layout.removeView(freqOffsetGraphView);
    		layout.addView(freqOffsetGraphView);
    	}
    	
    	if (timingEstSeries != null) { 
    		while (timingEstSeries.getItemCount() > HISTORY_SIZE) {
    			timingEstSeries.removeValue();
    		}
    		timingEstSeries.addValue(new GraphViewData(graphOffsetX, stats.rxTimingEstSamples));

    		// YAARRR, Here be hacks.
    		// Need to work out how to force a redraw code to be called without removing and 
    		// re-adding the view.
    		LinearLayout layout = (LinearLayout) findViewById(R.id.graph2);
    		layout.removeView(timingEstGraphView);
    		layout.addView(timingEstGraphView);
    	}
    	graphOffsetX++;
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	if (mAudioPlayback != null) {
    		mAudioPlayback.pause();
    	}
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(mUsbPermissionReciever);
    	if (mAudioPlayback != null) {
    		mAudioPlayback.stop();
    		mAudioPlayback = null;
    	}
    	if (mUsbAudio != null) {
    		mUsbAudio.close();
    		mUsbAudio = null;
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    private void setDevice(UsbDevice device) {
    	// Set button to enabled when permission is obtained
    	((Button) findViewById(R.id.button1)).setEnabled(device != null);
    }
    
    private class UsbReciever extends BroadcastReceiver {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
			if (ACTION_USB_PERMISSION.equals(action)) {
				if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
					setDevice(device);
				} else {
					Log.d(TAG, "Permission denied for device " + device);
				}
			}
		}
    }
}

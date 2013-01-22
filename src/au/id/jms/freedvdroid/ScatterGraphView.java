package au.id.jms.freedvdroid;

import java.util.LinkedList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class ScatterGraphView extends View {
	
	private static final int NUM_POINTS = 60;
	private static final float POINT_RADIUS = 2;
	private static final String TAG = "ScatterGraphView";
	Paint paint;
	LinkedList<Coord>mPointList = new LinkedList<Coord>();
	private int mWidth = 100;
	private int mHeight = 100;

	private static final float BETA = 0.95f;
	private float mFilter = 0.1f;
	
	private class Coord {
		public float x, y;
		Coord(float x, float y) {
			this.x = x;
			this.y = y;
		}
	}

	public ScatterGraphView(Context context) {
		super(context);
		paint = new Paint();
	}
	
	public ScatterGraphView(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint = new Paint();
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		Log.d(TAG, "Width: " + h + " Height: " + w);
		mWidth = w;
		mHeight = h;
	}
	
	public void addPoint(float[] newPoints) {
		// Age the old points out
		while (mPointList.size() > NUM_POINTS) {
			mPointList.remove();
		}
		// Add the new points
		for (int i=0; i<newPoints.length; i+=2) {
			mPointList.add(new Coord(newPoints[i], newPoints[i+1]));
		}
		invalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		paint.setStyle(Paint.Style.FILL);
		
		// Make canvas white
		paint.setColor(Color.WHITE);
		canvas.drawPaint(paint);
		
		paint.setAntiAlias(true);
		paint.setColor(Color.BLUE);
		
		// Filter points - this could go where the points are added if
		// proves to be too costly to do in onDraw
		float max = 1e-12f;
		for (Coord c: mPointList) {
			max = Math.min(max, Math.abs(c.x));
			max = Math.min(max, Math.abs(c.y));
		}
		mFilter = Math.max(0.001f, BETA*mFilter + (1- BETA)*2.5f*max);

		// 10000 is a magic factor to make the plot fit on the screen.
		// TODO: Work out if the scale changes much between radios, and if not,
		// use a constant. 36 works for the laptop with the vk5qi test file.
		float x_scale = mWidth/mFilter / 10000;
		float y_scale = mHeight/mFilter / 10000;
		
		for (Coord c: mPointList) {
			float x = x_scale*c.x + mWidth/2;
			float y = y_scale*c.y + mHeight/2;
			canvas.drawCircle(x, y, POINT_RADIUS, paint);
		}
	}
	
	

}

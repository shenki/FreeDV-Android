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
	private int xOffset = 0;
	private int yOffset = 0;
	
	private class Coord {
		public float x, y;
		private final int SCALE = 100;
		Coord(float x, float y) {
			// Not doing abs here - should I?
			this.x = SCALE * Math.abs(x) + 100;
			this.y = SCALE * Math.abs(y) + 100;
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
		xOffset = (int)(w/3.5);
		yOffset = (int)(h/3.5);
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
		
		canvas.drawCircle(50, 50, POINT_RADIUS, paint);
		
		for (Coord c: mPointList) {
			canvas.drawCircle(c.x, c.y, POINT_RADIUS, paint);
		}
	}
	
	

}

package au.id.jms.graphview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;

/**
 * Line Graph View. This draws a line chart.
 * @author jjoe64 - jonas gehring - http://www.jjoe64.com
 *
 * Copyright (C) 2011 Jonas Gehring
 * Licensed under the GNU Lesser General Public License (LGPL)
 * http://www.gnu.org/licenses/lgpl.html
 */
public class LineGraphView extends GraphView {
	
	private static final String TAG = "LineGraphView";
	
	private final Paint paintBackground;
	private boolean drawBackground;

	public LineGraphView(Context context, String title) {
		super(context, title);

		paintBackground = new Paint();
		paintBackground.setARGB(255, 20, 40, 60);
		paintBackground.setStrokeWidth(4);
	}

	@Override
	public void drawSeries(Canvas canvas, GraphViewData[] values, float graphwidth, float graphheight,
			float border, double minX, double minY, double diffX, double diffY, float horstart) {
		// draw background
		double lastEndY = 0;
		double lastEndX = 0;
		if (drawBackground) {
			float startY = graphheight + border;
			for (int i = 0; i < values.length; i++) {
				double valY = values[i].valueY - minY;
				double ratY = valY / diffY;
				double y = graphheight * ratY;

				double valX = values[i].valueX - minX;
				double ratX = diffX == 0.0 ? 1 : valX / diffX;
				Log.d("LineGraphView", "ratX: " + ratX);
				double x = graphwidth * ratX;

				float endX = (float) x + (horstart + 1);
				float endY = (float) (border - y) + graphheight +2;

				if (i > 0) {
					// fill space between last and current point
					int numSpace = (int) ((endX - lastEndX) / 3f) +1;
					for (int xi=0; xi<numSpace; xi++) {
						float spaceX = (float) (lastEndX + ((endX-lastEndX)*xi/(numSpace-1)));
						float spaceY = (float) (lastEndY + ((endY-lastEndY)*xi/(numSpace-1)));

						// start => bottom edge
						float startX = spaceX;

						// do not draw over the left edge
						if (startX-horstart > 1) {
							canvas.drawLine(startX, startY, spaceX, spaceY, paintBackground);
						}
					}
				}

				lastEndY = endY;
				lastEndX = endX;
			}
		}

		// draw data
		lastEndY = 0;
		lastEndX = 0;
		boolean firstValue = true;
		for (GraphViewData value: values) {
			double valY;
			double valX;
			if (value == null) {
				Log.e("LineGraphView", "value is null?!");
				continue;
			}
			synchronized (value) {
				valY = value.valueY - minY;
				valX = value.valueX - minX;
			}
			double ratY = valY / diffY;
			double y = graphheight * ratY;

			// XXX HAX! What should ratX be when the two X values are the same?
			double ratX = diffX == 0.0 ? valX : valX / diffX;
			double x = graphwidth * ratX;

			// Skip these operations on first value, as we need two points to draw a line
			if (firstValue) {
				firstValue = false;
				lastEndY = y;
				lastEndX = x;
				continue;
			}

			float startX = (float) lastEndX + (horstart + 1);
			float startY = (float) (border - lastEndY) + graphheight;
			float endX = (float) x + (horstart + 1);
			float endY = (float) (border - y) + graphheight;

			canvas.drawLine(startX, startY, endX, endY, paint);

			//				Log.d("LineGraphView", "Draw: (" + startX + ", " + startY + "), (" + endX + ", " + endY + ")");
			lastEndY = y;
			lastEndX = x;
		}
	}

	public boolean getDrawBackground() {
		return drawBackground;
	}

	/**
	 * @param drawBackground true for a light blue background under the graph line
	 */
	public void setDrawBackground(boolean drawBackground) {
		this.drawBackground = drawBackground;
	}
}

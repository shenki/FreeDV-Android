package au.id.jms.graphview;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

/**
 * GraphView is a Android View for creating zoomable and scrollable graphs.
 * This is the abstract base class for all graphs. Extend this class and implement {@link #drawSeries(Canvas, GraphViewData[], float, float, float, double, double, double, double, float)} to display a custom graph.
 * Use {@link LineGraphView} for creating a line chart.
 *
 * @author jjoe64 - jonas gehring - http://www.jjoe64.com
 *
 * Copyright (C) 2011 Jonas Gehring
 * Licensed under the GNU Lesser General Public License (LGPL)
 * http://www.gnu.org/licenses/lgpl.html
 */
abstract public class GraphView extends LinearLayout {
	
	private static final String TAG = "GraphView";
	
	private static final int DEFAULT_MAX_HEIGHT = 10;
	private static final int DEFAULT_MIN_HEIGHT = -10;
	private static final int DEFAULT_MAX_WIDTH = 10;
	private static final int DEFAULT_MIN_WIDTH = 0;//-10;

	static final private class GraphViewConfig {
		static final float BORDER = 40;
		static final float VERTICAL_LABEL_WIDTH = 40;
		static final float HORIZONTAL_LABEL_HEIGHT = 80;
	}

	private class GraphViewContentView extends View {
		private float lastTouchEventX;
		private float graphwidth;

		/**
		 * @param context
		 */
		public GraphViewContentView(Context context) {
			super(context);
			setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		}

		/**
		 * @param canvas
		 */
		@Override
		protected void onDraw(Canvas canvas) {

            paint.setAntiAlias(true);

			// normal
			paint.setStrokeWidth(0);

			float border = GraphViewConfig.BORDER;
			float horstart = 0;
			float height = getHeight();
			float width = getWidth() - 1;
			double maxY = getMaxY();
			double minY = getMinY();
			double diffY = maxY - minY;
			double maxX = getMaxX(false);
			double minX = getMinX(false);
			double diffX = maxX - minX;

			float graphheight = height - (2 * border);
			graphwidth = width;

			paint.setTextAlign(Align.CENTER);
			paint.setTextSize(18);
			paint.setColor(Color.BLACK);
			canvas.drawText(title, (graphwidth / 2) + horstart, border - 4, paint);
			paint.setTextSize(20 * getResources().getDisplayMetrics().density);

			if (maxY != minY) {
				paint.setStrokeCap(Paint.Cap.ROUND);

				for (int i=0; i<graphSeries.size(); i++) {
					paint.setStrokeWidth(graphSeries.get(i).style.thickness);
					paint.setColor(graphSeries.get(i).style.color);
					drawSeries(canvas, _values(i), graphwidth, graphheight, border, minX, minY, diffX, diffY, horstart);
				}

				if (showLegend) drawLegend(canvas, height, width);
			}
		}
	}

	/**
	 * graph series style: color and thickness
	 */
	static public class GraphViewStyle {
		public int color = 0xff0077cc;
		public int thickness = 3;
		public GraphViewStyle() {
			super();
		}
		public GraphViewStyle(int color, int thickness) {
			super();
			this.color = color;
			this.thickness = thickness;
		}
	}

	/**
	 * one data set for a graph series
	 */
	static public class GraphViewData {
		public final double valueX;
		public final double valueY;
		public GraphViewData(double valueX, double valueY) {
			super();
			this.valueX = valueX;
			this.valueY = valueY;
		}
	}

	/**
	 * a graph series
	 */
	static public class GraphViewSeries {
		final String description;
		final GraphViewStyle style;
		List<GraphViewData> values;
		double minValueX = DEFAULT_MIN_WIDTH;
		double minValueY = DEFAULT_MIN_HEIGHT;
		double maxValueX = DEFAULT_MAX_WIDTH;
		double maxValueY = DEFAULT_MAX_HEIGHT;
		public GraphViewSeries(List<GraphViewData> values) {
			description = null;
			style = new GraphViewStyle();
			this.values = values;
		}
		public GraphViewSeries(String description, GraphViewStyle style, List<GraphViewData> values) {
			super();
			this.description = description;
			if (style == null) {
				style = new GraphViewStyle();
			}
			this.style = style;
			this.values = values;
			
			if (values.isEmpty() != true) {
				minValueY = Double.MAX_VALUE;
				for (GraphViewData point: values) {
					minValueY = Math.min(point.valueY, minValueY); 
				}
				minValueX = Double.MAX_VALUE;
				for (GraphViewData point: values) {
					minValueX = Math.min(point.valueX, minValueX); 
				}

				maxValueY = Double.MIN_VALUE;
				for (GraphViewData point: values) {
					maxValueY = Math.max(point.valueY, maxValueY);
				}

				maxValueX = Double.MIN_VALUE;
				for (GraphViewData point: values) {
					maxValueX = Math.max(point.valueX, maxValueX); 
				}
			}
		}
		/**
		 * Add a value to the series.
		 * 
		 * @param value GraphViewData object to add
		 */
		public void addValue(GraphViewData value) {
			synchronized (this.values) {
				// Add value
				this.values.add(value);
				// If it is largest or smallest, update
				if (value.valueX < minValueX) {
					minValueX = value.valueX;
				} else if (value.valueX > maxValueX) {
					maxValueX = value.valueX;
				}
				if (value.valueY < minValueY) {
					minValueY = value.valueY;
				} else if (value.valueY > maxValueY) {
					maxValueY = value.valueY;
				}
			}
		}
		/**
		 * Remove value from a given position in the series.
		 * 
		 * @param position element's position, zero indexed.
		 */
		public void removeValue(int position) {
			GraphViewData removed;
			synchronized (this.values) {
				removed = this.values.remove(position);
			}
			if (this.values.isEmpty() == true) {
				minValueX = DEFAULT_MIN_WIDTH;
				minValueY = DEFAULT_MIN_HEIGHT;
				maxValueX = DEFAULT_MAX_WIDTH;
				maxValueY = DEFAULT_MAX_HEIGHT;
			} else {
				// If value is smallest/largest, re-search for bounds
				if (removed.valueX <= minValueX) {
					minValueX = Double.MAX_VALUE;
					for (GraphViewData point: values) {
						minValueX = Math.min(point.valueX, minValueX); 
					}
				} else if (removed.valueX >= maxValueX) {
					maxValueX = Double.MIN_VALUE;
					for (GraphViewData point: values) {
						maxValueX = Math.max(point.valueX, maxValueX); 
					}
				}
				if (removed.valueY <= minValueY) {
					minValueY = Double.MAX_VALUE;
					for (GraphViewData point: values) {
						minValueY = Math.min(point.valueY, minValueY); 
					}
				} else if (removed.valueY >= maxValueY) {
					maxValueY = Double.MIN_VALUE;
					for (GraphViewData point: values) {
						maxValueY = Math.max(point.valueY, maxValueY); 
					}
				}
			}
			
			if (maxValueY == Double.MIN_VALUE) {
				Log.e(TAG, "maxValueY was not changed");
			} else if (minValueY == Double.MAX_VALUE) {
				Log.e(TAG, "minValueY was not changed");
			}
			if (maxValueX == Double.MIN_VALUE) {
				Log.e(TAG, "maxValueX was not changed");
			} else if (minValueX == Double.MAX_VALUE) {
				Log.e(TAG, "minValueX was not changed");
			}
		}
		/**
		 * Remove the first value from the series.
		 */
		public void removeValue() {
			removeValue(0);
		}
		/**
		 * Return the number of elements in the series.
		 * @return 
		 */
		public int getItemCount() {
			return this.values.size();
		}
	}

	public enum LegendAlign {
		TOP, MIDDLE, BOTTOM
	}

	private class VerLabelsView extends View {
		/**
		 * @param context
		 */
		public VerLabelsView(Context context) {
			super(context);
			setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 10));
		}

		/**
		 * @param canvas
		 */
		@Override
		protected void onDraw(Canvas canvas) {
			// normal
			paint.setStrokeWidth(0);

			float border = GraphViewConfig.BORDER;
			float height = getHeight();
			float graphheight = height - (2 * border);

			if (verlabels == null) {
				verlabels = generateVerlabels(graphheight);
			}

			// vertical labels
			paint.setTextAlign(Align.LEFT);
			int vers = verlabels.length - 1;
			for (int i = 0; i < verlabels.length; i++) {
				float y = ((graphheight / vers) * i) + border/2;
				paint.setColor(Color.WHITE);
				canvas.drawText(verlabels[i], 0, y, paint);
			}
		}
	}

	protected final Paint paint;
	private String[] horlabels;
	private String[] verlabels;
	private String title;
	private boolean scrollable;
	private double viewportStart;
	private double viewportSize;
//	private final View viewVerLabels;
	private boolean scalable;
	private NumberFormat numberformatter;
	private final List<GraphViewSeries> graphSeries;
	private boolean showLegend = false;
	private float legendWidth = 120;
	private LegendAlign legendAlign = LegendAlign.MIDDLE;
	private boolean manualYAxis;
	private double manualMaxYValue;
	private double manualMinYValue;

	/**
	 *
	 * @param context
	 * @param title [optional]
	 */
	public GraphView(Context context, String title) {
		super(context);
		setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

		if (title == null)
			title = "";
		else
			this.title = title;

		paint = new Paint();
		graphSeries = new ArrayList<GraphViewSeries>();

//		viewVerLabels = new VerLabelsView(context);
//		addView(viewVerLabels);
		addView(new GraphViewContentView(context), new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1));
	}

	private GraphViewData[] _values(int idxSeries) {
		List<GraphViewData> values = graphSeries.get(idxSeries).values;
		if (viewportStart == 0 && viewportSize == 0) {
			// all data
			// TODO Change this to return List
			return values.toArray(new GraphViewData[values.size()]);
		} else {
			// viewport
			List<GraphViewData> listData = new ArrayList<GraphViewData>();
			for (GraphViewData value : values) {
				if (value.valueX >= viewportStart) {
					if (value.valueX > viewportStart+viewportSize) {
						listData.add(value); // one more for nice scrolling
						break;
					} else {
						listData.add(value);
					}
				} else {
					if (listData.isEmpty()) {
						listData.add(value);
					}
					listData.set(0, value); // one before, for nice scrolling
				}
			}
			// TODO Change this to return List
			return listData.toArray(new GraphViewData[listData.size()]);
		}
	}

	public void addSeries(GraphViewSeries series) {
		graphSeries.add(series);
	}

	public void removeSeries(int index)
	{
		if (index < 0 || index >= graphSeries.size())
		{
			throw new IndexOutOfBoundsException("No series at index " + index);
		}
		
		graphSeries.remove(index);
	}
	
	public void removeSeries(GraphViewSeries series)
	{
		graphSeries.remove(series);
	}
	
	protected void drawLegend(Canvas canvas, float height, float width) {
		int shapeSize = 15;

		// rect
		paint.setARGB(180, 100, 100, 100);
		float legendHeight = (shapeSize+5)*graphSeries.size() +5;
		float lLeft = width-legendWidth - 10;
		float lTop;
		switch (legendAlign) {
		case TOP:
			lTop = 10;
			break;
		case MIDDLE:
			lTop = height/2 - legendHeight/2;
			break;
		default:
			lTop = height - GraphViewConfig.BORDER - legendHeight -10;
		}
		float lRight = lLeft+legendWidth;
		float lBottom = lTop+legendHeight;
		canvas.drawRoundRect(new RectF(lLeft, lTop, lRight, lBottom), 8, 8, paint);

		for (int i=0; i<graphSeries.size(); i++) {
			paint.setColor(graphSeries.get(i).style.color);
			canvas.drawRect(new RectF(lLeft+5, lTop+5+(i*(shapeSize+5)), lLeft+5+shapeSize, lTop+((i+1)*(shapeSize+5))), paint);
			if (graphSeries.get(i).description != null) {
				paint.setColor(Color.WHITE);
				paint.setTextAlign(Align.LEFT);
				canvas.drawText(graphSeries.get(i).description, lLeft+5+shapeSize+5, lTop+shapeSize+(i*(shapeSize+5)), paint);
			}
		}
	}

	abstract public void drawSeries(Canvas canvas, GraphViewData[] values, float graphwidth, float graphheight, float border, double minX, double minY, double diffX, double diffY, float horstart);

	/**
	 * formats the label
	 * can be overwritten
	 * @param value x and y values
	 * @param isValueX if false, value y wants to be formatted
	 * @return value to display
	 */
	protected String formatLabel(double value, boolean isValueX) {
		if (numberformatter == null) {
			numberformatter = NumberFormat.getNumberInstance();
			double range = Math.abs(getMaxY() - getMinY());

			if (range < 0.1) {
				numberformatter.setMaximumFractionDigits(6);
			} else if (range < 1) {
				numberformatter.setMaximumFractionDigits(4);
			} else if (range < 5) {
				numberformatter.setMaximumFractionDigits(3);
			} else if (range < 10) {
				numberformatter.setMaximumFractionDigits(1);
			} else {
				numberformatter.setMaximumFractionDigits(0);
			}
		}
		return numberformatter.format(value);
	}

	private String[] generateHorlabels(float graphwidth) {
		int numLabels = (int) (graphwidth/GraphViewConfig.VERTICAL_LABEL_WIDTH);
		String[] labels = new String[numLabels+1];
		double min = getMinX(false);
		double max = getMaxX(false);
		for (int i=0; i<=numLabels; i++) {
			labels[i] = formatLabel(min + ((max-min)*i/numLabels), true);
		}
		return labels;
	}

	synchronized private String[] generateVerlabels(float graphheight) {
		int numLabels = (int) (graphheight/GraphViewConfig.HORIZONTAL_LABEL_HEIGHT);
		String[] labels = new String[numLabels+1];
		double min = getMinY();
		double max = getMaxY();
		for (int i=0; i<=numLabels; i++) {
			labels[numLabels-i] = formatLabel(min + ((max-min)*i/numLabels), false);
		}
		return labels;
	}

	public LegendAlign getLegendAlign() {
		return legendAlign;
	}

	public float getLegendWidth() {
		return legendWidth;
	}

	/**
	 * returns the maximal X value of the current viewport (if viewport is set)
	 * otherwise maximal X value of all data.
	 * @param ignoreViewport
	 *
	 * warning: only override this, if you really know want you're doing!
	 */
	protected double getMaxX(boolean ignoreViewport) {
		// if viewport is set, use this
		if (!ignoreViewport && viewportSize != 0) {
			return viewportStart+viewportSize;
		} else {
			// otherwise use the max x value
			// values must be sorted by x, so the last value has the largest X value
			if (graphSeries.isEmpty()) {
				return DEFAULT_MAX_HEIGHT;
			}
			double highest = Double.MIN_VALUE;
			for (GraphViewSeries series: graphSeries) {
				highest = Math.max(series.maxValueX, highest);
			}
			
			if (graphSeries.isEmpty() == false && highest == Double.MIN_VALUE) {
				Log.e(TAG, "maxValueX caluclated wrong");
			}
			return highest;
		}
	}

	/**
	 * returns the maximal Y value of all data.
	 *
	 * warning: only override this, if you really know want you're doing!
	 */
	protected double getMaxY() {
		double largest;
		if (manualYAxis) {
			largest = manualMaxYValue;
		} else {
			largest = Double.MIN_VALUE;
			for (GraphViewSeries series: graphSeries) {
				largest = Math.max(series.maxValueY, largest);
			}
			if (graphSeries.isEmpty() == false && largest == Double.MIN_VALUE) {
				Log.e(TAG, "maxValueY caluclated wrong");
			}
		}
		return largest;
	}

	/**
	 * returns the minimal X value of the current viewport (if viewport is set)
	 * otherwise minimal X value of all data.
	 * @param ignoreViewport
	 *
	 * warning: only override this, if you really know want you're doing!
	 */
	protected double getMinX(boolean ignoreViewport) {
		// if viewport is set, use this
		if (!ignoreViewport && viewportSize != 0) {
			return viewportStart;
		} else {
			// otherwise use the min x value
			// values must be sorted by x, so the first value has the smallest X value
			double lowest = 0;
			if (graphSeries.size() > 0)
			{
				lowest = Double.MAX_VALUE;
				for (GraphViewSeries series: graphSeries) {
					lowest = Math.min(series.minValueX, lowest);
				}
			}
			if (graphSeries.isEmpty() == false && lowest == Double.MAX_VALUE) {
				Log.e(TAG, "minValueX caluclated wrong");
			}
			return lowest;
		}
	}

	/**
	 * returns the minimal Y value of all data.
	 *
	 * warning: only override this, if you really know want you're doing!
	 */
	protected double getMinY() {
		double smallest;
		if (manualYAxis) {
			smallest = manualMinYValue;
		} else {
			smallest = Double.MAX_VALUE;
			for (GraphViewSeries series: graphSeries) {
				smallest = Math.min(series.minValueY, smallest);
			}
			if (graphSeries.isEmpty() == false && smallest == Double.MAX_VALUE) {
				Log.e(TAG, "minValueY calculated wrong");
			}
		}
		return smallest;
	}

	public boolean isScrollable() {
		return scrollable;
	}

	public boolean isShowLegend() {
		return showLegend;
	}

	/**
	 * set's static horizontal labels (from left to right)
	 * @param horlabels if null, labels were generated automatically
	 */
	public void setHorizontalLabels(String[] horlabels) {
		this.horlabels = horlabels;
	}

	public void setLegendAlign(LegendAlign legendAlign) {
		this.legendAlign = legendAlign;
	}

	public void setLegendWidth(float legendWidth) {
		this.legendWidth = legendWidth;
	}

	/**
	 * you have to set the bounds {@link #setManualYAxisBounds(double, double)}. That automatically enables manualYAxis-flag.
	 * if you want to disable the menual y axis, call this method with false.
	 * @param manualYAxis
	 */
	public void setManualYAxis(boolean manualYAxis) {
		this.manualYAxis = manualYAxis;
	}

	/**
	 * set manual Y axis limit
	 * @param max
	 * @param min
	 */
	public void setManualYAxisBounds(double max, double min) {
		manualMaxYValue = max;
		manualMinYValue = min;
		manualYAxis = true;
	}

	/**
	 * the user can scroll (horizontal) the graph. This is only useful if you use a viewport {@link #setViewPort(double, double)} which doesn't displays all data.
	 * @param scrollable
	 */
	public void setScrollable(boolean scrollable) {
		this.scrollable = scrollable;
	}

	public void setShowLegend(boolean showLegend) {
		this.showLegend = showLegend;
	}

	/**
	 * set's static vertical labels (from top to bottom)
	 * @param verlabels if null, labels were generated automatically
	 */
	public void setVerticalLabels(String[] verlabels) {
		this.verlabels = verlabels;
	}

	/**
	 * set's the viewport for the graph.
	 * @param start x-value
	 * @param size
	 */
	public void setViewPort(double start, double size) {
		viewportStart = start;
		viewportSize = size;
	}
}

package org.tsp.draw;

import static com.googlecode.javacv.cpp.opencv_calib3d.cvFindHomography;
import static com.googlecode.javacv.cpp.opencv_core.CV_WHOLE_SEQ;
import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;
import static com.googlecode.javacv.cpp.opencv_core.cvClearMemStorage;
import static com.googlecode.javacv.cpp.opencv_core.cvGetSeqElem;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_CHAIN_APPROX_SIMPLE;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_POLY_APPROX_DP;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_RETR_LIST;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvApproxPoly;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvCanny;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvContourArea;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvContourPerimeter;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvDilate;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvFindContours;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.tsp.activity.MainScreen;
import org.tsp.activity.Prefs;
import org.tsp.comm.ImageDownloadThread;
import org.tsp.comm.ImageDownloadThreadResult;
import org.tsp.comm.ORSMatchThread;
import org.tsp.comm.ORSMatchThreadResult;
import org.tsp.cv.descriptor.FeatureVector;
import org.tsp.cv.descriptor.strategy.DescriptorContext;
import org.tsp.cv.detector.fast.Fast12;
import org.tsp.cv.detector.fast.Fast9;
import org.tsp.cv.detector.fast.FeaturePoint;
import org.tsp.model.Painting;
import org.tsp.stability.StabilityListener;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.googlecode.javacpp.Loader;
import com.googlecode.javacv.cpp.opencv_core.CvContour;
import com.googlecode.javacv.cpp.opencv_core.CvMat;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvPoint;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

/**
 * DrawingBob is responsible for drawing stuff on top of the Camera preview.
 */
public class DrawingBob extends View implements StabilityListener
{
	private final int SWIPE_MIN_DISTANCE = 120;
    private final int SWIPE_MAX_OFF_PATH = 250;
    private final int SWIPE_THRESHOLD_VELOCITY = 200;
	
	private final int FAST_THRESHOLD = 18;
	private final int MAX_NUM_OF_FEATURES = 150;
	
	private final int SHOWOFF_BEHAVIOUR = 0;
	private final int NORMAL_BEHAVIOUR = 1;

	
	private boolean firstShow = true;
	
	Canvas canvas = null;
	
	public static final int READY_FOR_SNAPSHOT = 2;
	public static final int WAIT_FOR_MATCH_RESULTS = 3;
	public static final int WAIT_FOR_IMAGE_DOWNLOAD = 4;
	public static final int AUGMENTING_VIDEO = 5;
	public static final int IDLE = 6;
	public static final int AWAITING_INPUT = 7;
	public static final int MIN_AREA_THRESHOLD = 19000;  // (~1:6 ratio)
	
	private int currentBehaviour = NORMAL_BEHAVIOUR;
	private int currentState = IDLE; 
	private int currentImgIndex = 0;
	
	private int downloadImagesCounter = 0;
	
	private boolean isPaused = false;
	
	private double[][] grayScale2D = null;

	private int imageWidth = 0;
	private int imageHeight = 0;
	
	private float parentWidth = 0f;
	private float parentHeight = 0f;
	
	private float aspectX = 0f;
	private float aspectY = 0f;
	
	private MainScreen parentContext = null;
	
	private Paint paintRed = null;
	private Paint paintWhite = null;
	private Paint paintBlack = null;
	private Paint paintGreen = null;
	
	private IplImage grayImage = null;
	private IplImage edgeImage = null;

    private CvMemStorage storage = null;
    
    private DescriptorContext descriptorContext = null;
    
	private ORSMatchThreadResult ORSMatchResult = new ORSMatchThreadResult();
	private ImageDownloadThreadResult imageDownloadResult = new ImageDownloadThreadResult();
	
	private List<Bitmap> relevantPaintingsImgs = null;
	private List<Painting> relevantPaintings = null;
	private List<CvMat> relevantPaintingsMats = null;
	
	private CvMat currImgSrcMatrix = null;
	
	private CvPoint refCorners = null;
	private GestureDetector gestureDetector;
	private View.OnTouchListener gestureListener;
	
	private ProgressDialog progressDialog = null;
	
	public DrawingBob(MainScreen context, int w, int h) 
	{
		super(context);
		
		parentContext = context;
		
		gestureDetector = new GestureDetector(new MyGestureDetector());
	    gestureListener = new View.OnTouchListener() {
	    	public boolean onTouch(View v, MotionEvent event) 
	    	{
	    		return gestureDetector.onTouchEvent(event);
	        }
	    };
	    
	    this.setOnTouchListener(gestureListener);
	    
		paintRed = new Paint();
		paintRed.setStyle(Paint.Style.FILL);
		paintRed.setColor(Color.RED);
		paintRed.setStrokeWidth(2.3f);
		
		paintGreen = new Paint();
		paintGreen.setStyle(Paint.Style.FILL);
		paintGreen.setColor(Color.GREEN);
		paintGreen.setStrokeWidth(3.3f);
		
		paintWhite = new Paint();
		paintWhite.setStyle(Paint.Style.FILL);
		paintWhite.setColor(Color.WHITE);
		paintWhite.setStrokeWidth(1.7f);
		
		paintBlack = new Paint();
		paintBlack.setStyle(Paint.Style.FILL);
		paintBlack.setColor(Color.BLACK);
		paintBlack.setStrokeWidth(5f);
		
		setPreviewDimensions(w,h);
		
		storage = CvMemStorage.create();
		
		currImgSrcMatrix = CvMat.create(4, 2);
		
		System.load("/data/data/org.tsp/lib/libopencv_imgproc.so");
		System.load("/data/data/org.tsp/lib/libopencv_highgui.so");
		System.load("/data/data/org.tsp/lib/libopencv_calib3d.so");
		
	}
	
	/**
	 * Once its parent container is created, this method needs
	 * to be called to set the dimensions inside Bob and also
	 * compute the aspect ratio of the captures preview frame with 
	 * respect to the View that's displaying the preview.
	 * 
	 * @param parentWidth
	 * 					The width of the parent View.
	 * @param parentHeight
	 * 					The height of the parent View.
	 */
	public DrawingBob setParentDimensions(float parentWidth, float parentHeight)
	{
		this.parentWidth = parentWidth;
		this.parentHeight = parentHeight;
		calcAspectRatios();
		return this;
	}
	
	public int getImageWidth() 
	{
		return imageWidth;
	}

	public int getImageHeight() 
	{
		return imageHeight;
	}
	
	public synchronized void setState(int state)
	{
		currentState = state;
	}
	
	public synchronized DrawingBob pause()
	{
		isPaused = true;
		currentState = IDLE;
		relevantPaintings = null;
		relevantPaintingsImgs = null;
		relevantPaintingsMats = null;
		currentImgIndex = 0;
		downloadImagesCounter = 0;
		for (int i = 1; i < 7; i++) {
			dismiss(i);
		}	
		return this;
	}
	
	public synchronized DrawingBob resume()
	{
		isPaused = false;
		// If the activity was paused while a thread was running
		// the thread will continue to execute and populate the result objects.
		// We thus need to reset them on resume.
		ORSMatchResult.reset();
		imageDownloadResult.reset();
		parentContext.setCameraPreviewGeneral();
		return this;
	}
	
	public boolean isPaused()
	{
		return isPaused;
	}
	
	/**
	 * Extracts the luminance information from the YUV420SP byte
	 * array received from the onPreviewFrame callback. According to
	 * http://code.google.com/p/android/issues/detail?id=823 the first
	 * [width * height] bytes represent the luminance information in the
	 * captured frame. During extraction the bytes are converted to integers
	 * and are stored rotated by 90 degrees clockwise to get a portrait view.
	 
	 * @param yuv420sp
	 * 				 The byte[] data received from onPreviewFrame.
	 */
	public DrawingBob extractGrayScaleData(byte[] yuv420sp) 
	{
		int imageStride = grayImage.widthStep();
		ByteBuffer imageBuffer = grayImage.getByteBuffer();
		for (int i = 0; i < imageWidth; ++i) {
			int dataLine  = i * imageHeight;
    		for (int j = 0; j < imageHeight; ++j) {
    			int imageLine = j * imageStride;
    			int pixVal = (0xff & ((int) yuv420sp[dataLine + j])) - 16;
        		if (pixVal < 0) pixVal = 0;
        		if (pixVal > 255) pixVal = 255;
        		if (currentState != AUGMENTING_VIDEO)
        			imageBuffer.putInt(imageLine + i, pixVal);
        		grayScale2D[j][imageWidth-1-i] = pixVal;
    		}
		}
		return this;
	}
	
	public synchronized void setPreviewDimensions(int w, int h)
	{
		imageHeight = w;
		imageWidth = h;
		// We are going to deal with integers so we allocate 4x the size (int = 4 bytes).
		grayImage = IplImage.create(imageWidth * 4, imageHeight * 4, IPL_DEPTH_8U, 1);
		grayScale2D = new double[imageHeight][imageWidth];
		calcAspectRatios();
	}
	
	private synchronized void calcAspectRatios() 
	{
		aspectY = parentWidth / imageHeight;
		aspectX = parentHeight / imageWidth;
	}
	
	public DrawingBob update(byte[] yuv420sp)
	{
		extractGrayScaleData(yuv420sp);
		return this;
	}
	
	/**
	 * On every drawing request we compute the FAST corners and display them.
	 * This will probably change since we want to compute corners only when 
	 * the frame is stable.
	 */
	@Override
    protected void onDraw(Canvas _canvas) 
	{
		// The drawing procedure follows a Finite State Machine.
		
        if (!isPaused()) {
        	
        	// Read behaviour from preferences.
        	currentBehaviour = Prefs.getPref_isShowoffBehaviourEnabled(parentContext)? SHOWOFF_BEHAVIOUR : NORMAL_BEHAVIOUR;
        	canvas = _canvas;
        	switch (currentBehaviour) {
        		
				////////////////////////////////////////
				// NORMAL BEHAVIOUR					  //
				// -----------------				  //
		 		// Normal mode - recognition and all. //
				////////////////////////////////////////
				case NORMAL_BEHAVIOUR:
			
					switch (currentState) {
						
						////////////////////////////////////////////////
						// READY_FOR_SNAPSHOT						  //
						// -------------------						  //
					 	// Once the continuously stable method		  //
						// is called, we enter this state to try	  //
						// and find a painting in the camera preview. //
						////////////////////////////////////////////////
						case READY_FOR_SNAPSHOT:
							
							// Extract corners.
							refCorners = new CvPoint(4);
							double area = extractCorners(refCorners);
							
							if (area > MIN_AREA_THRESHOLD) {
								// Extract feature points.
								//double[][] subregion = extractBoundingRect(refCorners);
								drawPaintingOutline(refCorners, paintGreen);
								parentContext.playSound();
								notify(0);
								//List<FeaturePoint> featurePoints 
								//	= Fast12.detect(subregion, subregion[0].length, subregion.length, 
								//	
								List<FeaturePoint> featurePoints 
									= Fast12.detect(grayScale2D, grayScale2D[0].length, grayScale2D.length, 
												FAST_THRESHOLD, MAX_NUM_OF_FEATURES);
								// Run descriptor.
								int strategy = Integer.valueOf(Prefs.getPref_selectDescriptor(parentContext));
								descriptorContext = new DescriptorContext(strategy);
								List<FeatureVector> featureVectors = descriptorContext.getFeatureVectors(featurePoints, grayScale2D);
								// Upload feature vectors to ORS.
								String serverIP = getServerIP();
								if (serverIP == null)
									return;
								new ORSMatchThread(serverIP, featureVectors, strategy, ORSMatchResult).start();
								// Change state.
								synchronized (this) {
									currentState = WAIT_FOR_MATCH_RESULTS;
								}
				        	}
							break;
						
						////////////////////////////////////////////////
						// WAIT_FOR_MATCH_RESULTS					  //
						// -----------------------					  //
					 	// Wait for the feature vectors to be matched //
						// by the ORS and a matching to be returned.  //
						////////////////////////////////////////////////
						case WAIT_FOR_MATCH_RESULTS: 
						
							if (ORSMatchResult.isFinished()) {
								if (ORSMatchResult.isSuccessful()) {
									Painting matchedPainting = ORSMatchResult.getMatchedPainting();
									if (matchedPainting == null) {
										synchronized (this) {
											currentState = AWAITING_INPUT;
											ORSMatchResult.reset();
										}
										dismiss(0);
										notify(4);
									} else {
										//notify();
										relevantPaintings = ORSMatchResult.getRelevantPaintings();
										if (relevantPaintings == null || relevantPaintings.size() == 0) {
											synchronized (this) {
												currentState = AWAITING_INPUT;
												ORSMatchResult.reset();
											}
											dismiss(0);
											notify(3);
										} else {
											List<String> imageUIDs = new ArrayList<String>();
											for (Painting p : relevantPaintings) {
												imageUIDs.add(p.pid());
											}
											String serverIP = getServerIP();
											if (serverIP == null)
												return;
											new ImageDownloadThread(serverIP, imageUIDs, imageDownloadResult).start();
											// Change state.
											sendToParentDialog(matchedPainting.title() + " by " + matchedPainting.artist());
											dismiss(0);
											notify(1);
											synchronized (this) {
												ORSMatchResult.reset();
											}
										}
									}
								} else {
									synchronized (this) {
										currentState = AWAITING_INPUT;
										ORSMatchResult.reset();
									}
									dismiss(0);
									notify(2);
								}
							}
							break;
						
						////////////////////////////////////////////////
						// WAIT_FOR_IMAGE_DOWNLOAD					  //
						// ------------------------					  //
					 	// Wait for the relevant painting images to	  //
						// be downloaded by the downloader thread.	  //
						////////////////////////////////////////////////
						case WAIT_FOR_IMAGE_DOWNLOAD:
						
							if (imageDownloadResult.isFinished()) {
								if (imageDownloadResult.isSuccessful()) {
									downloadImagesCounter = 0;
									synchronized (this) {
										relevantPaintingsImgs = imageDownloadResult.getDownloadedImages();
										if (relevantPaintingsImgs != null && relevantPaintingsImgs.size() > 0) {
											if (relevantPaintingsMats == null) {
												relevantPaintingsMats = new ArrayList<CvMat>();
											}
											for (Bitmap p : relevantPaintingsImgs) {
												relevantPaintingsMats.add(cvMatFromBitmap(p));
											}
											dismiss(6);
											dismiss(1);
											setARMode();
											notify(5);
										} else {
											dismiss(6);
											dismiss(1);
											notify(2);
											currentState = IDLE;
										}
										imageDownloadResult.reset();
									}
								} else {
									synchronized(this) {
										currentState = IDLE;
										imageDownloadResult.reset();
									}
									dismiss(1);
									notify(2);
								}
							} else {
								downloadImagesCounter++;
								if (downloadImagesCounter > 100) {
									downloadImagesCounter = 0;
									notify(6);
								}
							}
							break;
						
						/////////////////////////////////////////////////
						// AUGMENTING_VIDEO						  	   //
						// -----------------						   //
						// We have the images to overlay, we now track //
						// the corners, calculate a transformation	   //
						// matrix and overlay the current image.	   //
						/////////////////////////////////////////////////
						case AUGMENTING_VIDEO:
						
							List<FeaturePoint> corners = Fast9.detect(grayScale2D, grayScale2D[0].length, grayScale2D.length, 24, 200);
							findNextLikelyCorners(corners);
							Bitmap currentImage;
							CvMat srcMatrix;
							synchronized(this) {
								currentImage = relevantPaintingsImgs.get(currentImgIndex);
								srcMatrix = relevantPaintingsMats.get(currentImgIndex);
								if (firstShow) {
									Painting firstPainting = relevantPaintings.get(currentImgIndex);
				                	Toast.makeText(DrawingBob.this.parentContext, firstPainting.title() + " by " + firstPainting.artist(), 500).show();
				                	firstShow = false;
								}
							}
							CvMat dstMatrix = CvMat.create(4, 2);
							updateCvMat(dstMatrix, refCorners);
							// Find the transformation matrix.
							CvMat hMatrix = CvMat.create(3, 3);
							cvFindHomography(srcMatrix, dstMatrix, hMatrix);

							// Draw the transformed bitmap.
							float[] values = new float[9];
							for (int i = 0; i < 9; i++) {
								values[i] = (float) hMatrix.get(i);
							}
							Matrix transformMatrix = new Matrix();
							transformMatrix.setValues(values);
							canvas.drawBitmap(currentImage, transformMatrix, null);
							drawPaintingOutline(refCorners, paintBlack);
							break;
							
							
						//////////////////////////////////////////////
						// IDLE							 		 	//
						// -----						  			//
						// "Waits" for the mobile to get stable 	//
						// enough for painting detection.			//
						//////////////////////////////////////////////
						case IDLE: 
							break;
						default:
							break;
					}
					break;
					
				case SHOWOFF_BEHAVIOUR:
					
					/////////////////////////////////////////////////////
					// SHOWOFF BEHAVIOUR					  	       //
					// ------------------						       //
				 	// Presentation-oriented mode - no recognition is  //
					// done, but everything is drawn on the screen.	   //
					/////////////////////////////////////////////////////
					CvPoint corners = new CvPoint(4);
					extractCorners(corners);
					drawPaintingOutline(corners, paintGreen);
					List<FeaturePoint> fastCorners
						= Fast9.detect(grayScale2D, grayScale2D[0].length, grayScale2D.length, 20, 100);
					drawFastCorners(fastCorners);
				default:
					break;
        	}
        	
            cvClearMemStorage(storage);

        }
        super.onDraw(canvas);
	}

	private void notify(int type)
	{
		parentContext.onShowDialog(type);
	}
	
	private void dismiss(int type)
	{
		parentContext.onDismissDialog(type);
	}
	
	private void setARMode()
	{
		parentContext.setCameraPreviewAR();
		float multX = (float) 176 / (float) 480;
		float multY = (float) 144 / (float) 320;
		//for (int i = 0; i < 4; i++) {
		//	refCorners.position(i).x((int) (refCorners.position(i).x() * multY));
		//	refCorners.position(i).y((int) (refCorners.position(i).y() * multX));
		//}
	}
	
	private void sendToParentDialog(String message)
	{
		parentContext.receiveDialogMessage(message);
	}
	
	private String getServerIP()
	{
		String serverIP = Prefs.getPref_serverIP(parentContext);
		if (serverIP == null || serverIP.equals("")) {
			//notify("Server IP address was not specified.");
			return null;
		}
		if (serverIP.split(":").length < 2) {
			//notify("Server IP address does not include port.");
			return null;
		}
		return serverIP;
	}
	
	private synchronized CvMat cvMatFromBitmap(Bitmap painting) 
	{
		CvMat matrix = CvMat.create(4, 2);
		int width = painting.getWidth();
		int height = painting.getHeight();
		matrix.put(0, 0, width);
		matrix.put(0, 1, 0);
		matrix.put(1, 0, 0);
		matrix.put(1, 1, 0);
		matrix.put(2, 0, 0);
		matrix.put(2, 1, height);
		matrix.put(3, 0, width);
		matrix.put(3, 1, height);
		return matrix;
	}
	
	private synchronized void updateCvMat(CvMat matrix, CvPoint corners) 
	{
		for (int i = 0; i < 4; i++) {
			CvPoint p = corners.position(i);
			matrix.put(i, 0, p.x() * aspectX);
			matrix.put(i, 1, p.y() * aspectY);
		}
	}
	
	private double[][] extractBoundingRect(CvPoint paintingCorners) {
		int minX = imageWidth;
		int maxX = 0;
		int minY = imageHeight;
		int maxY = 0;
		for (int i = 0; i< 4; i++) {
			CvPoint p = paintingCorners.position(i);
			minX = Math.min(minX, p.x());
			minY = Math.min(minY, p.y());
			maxX = Math.max(maxX, p.x());
			maxY = Math.max(maxY, p.y());
		}
		double[][] subregion = new double[maxY - minY][maxX - minX];
		for (int y = minY; y < maxY; y++) {
			for (int x = minX; x <maxX; x++) {
				subregion[y - minY][x - minX] = grayScale2D[y][x];
			}
		}
		return subregion;
	}

	private void findNextLikelyCorners(List<FeaturePoint> fastCorners) 
	{
		//drawFastCorners(fastCorners);
		for (int i = 0; i < 4; i++) {
			FeaturePoint closest = new FeaturePoint(refCorners.position(i).x(), refCorners.position(i).y());
			double minDist = Integer.MAX_VALUE;
			int currX = refCorners.position(i).x();
			int currY = refCorners.position(i).y();
			for (FeaturePoint fp : fastCorners) {
				double d = fp.getDistanceTo(currX, currY); 
				if (d < minDist && d < 20) {
					closest = fp;
					minDist = d;
				}
			}
			refCorners.position(i).x(closest.x());
			refCorners.position(i).y(closest.y());
		}
	}
	
	private void drawFastCorners(List<FeaturePoint> corners) 
	{
		Rect rect = new Rect();
		for (FeaturePoint fp : corners) {
			rect.top = (int) (fp.y() * aspectY);
			rect.bottom = rect.top + 5;
			rect.left = (int) (fp.x() * aspectX);
			rect.right = rect.left + 5;
			canvas.drawRect(rect, paintRed);
		}
	}

	private void drawPaintingOutline(CvPoint corners, Paint paint) 
	{
		int prevX = -1;
		int prevY = -1;
		int currX = -1;
		int currY = -1;
		for (int i = 0; i < 5; i++) {
			if (currX != -1) {
				prevX = currX;
				prevY = currY;
				currX = corners.position(i % 4).x();
				currY = corners.position(i % 4).y();
				canvas.drawLine(prevX * aspectX, prevY * aspectY, currX * aspectX, currY * aspectY, paint);
			} else {
				currX = corners.position(i % 4).x();
				currY = corners.position(i % 4).y();
			}
		}
		
	}
	
	private void drawCVCorners(CvPoint corners) 
	{
		int currX = -1;
		int currY = -1;
		Rect rect = new Rect();
		for (int i = 0; i < 5; i++) {
			if (currX != -1) {
				currX = corners.position(i % 4).x();
				currY = corners.position(i % 4).y();
				rect.top = (int) (currY * aspectY);
				rect.bottom = rect.top + 5;
				rect.left = (int) (currX * aspectX);
				rect.right = rect.left + 5;
				canvas.drawRect(rect, paintGreen);
			} else {
				currX = corners.position(i % 4).x();
				currY = corners.position(i % 4).y();
			}
		}
		
	}

	private void drawEdgeImage(Canvas canvas) 
	{
		Paint currPaint = new Paint();
		currPaint.setStrokeWidth(1.7f);
		currPaint.setStyle(Paint.Style.FILL);
		ByteBuffer imageBuffer = grayImage.getByteBuffer();
		int imageStride = grayImage.widthStep();
		for (int i = 0; i < imageWidth; ++i) {
    		for (int j = 0; j < imageHeight; ++j) {
    			int imageLine = j * imageStride;
    			int pixVal = imageBuffer.getInt(imageLine + i);
    			currPaint.setARGB(255, pixVal, pixVal, pixVal);
    			canvas.drawPoint((imageWidth - 1 - i) * aspectX, j * aspectY, currPaint);
    		}
		}
	}

	@Override
	public void onBecomingStable() {;}

	@Override
	public void onBecomingUnstable() 
	{
		synchronized (this) {
			if (currentState != WAIT_FOR_MATCH_RESULTS 
				&& currentState != AUGMENTING_VIDEO
				&& currentState != WAIT_FOR_IMAGE_DOWNLOAD 
				&& currentState != AWAITING_INPUT) {
				currentState = IDLE;
			}
		}
	}
	
	@Override
	public void onBecomingContinuouslyStable() 
	{
		synchronized(this) {
			if (currentState != WAIT_FOR_MATCH_RESULTS 
				&& currentState != AUGMENTING_VIDEO
				&& currentState != WAIT_FOR_IMAGE_DOWNLOAD
				&& currentState != AWAITING_INPUT) {				
				currentState = READY_FOR_SNAPSHOT;
			}
		}
	}
	
	private double extractCorners(CvPoint corners)
	{
		cvCanny(grayImage, grayImage, 1, 254, 3);
		cvDilate(grayImage, grayImage, null, 1);
		CvSeq contour = new CvSeq(null);
        cvFindContours(grayImage, storage, contour, Loader.sizeof(CvContour.class),
                 CV_RETR_LIST, CV_CHAIN_APPROX_SIMPLE);
        double maxArea = 0;
        while (contour != null && !contour.isNull()) {
        	if (contour.elem_size() > 0) {
        		CvSeq points = cvApproxPoly(contour, Loader.sizeof(CvContour.class),
                         storage, CV_POLY_APPROX_DP, cvContourPerimeter(contour)*0.02, 0);
        		double area = Math.abs(cvContourArea(contour, CV_WHOLE_SEQ, 0));
        		if (points.total() == 4) {
        			if (area > maxArea) {
        				maxArea = area;
        				for (int i = 0; i < 4; ++i) {
        					CvPoint point = new CvPoint(cvGetSeqElem(points, i));
        					corners.position(i).x(imageWidth - point.x());
        					corners.position(i).y(point.y());
        				}
        			}
        		}
            }
            contour = contour.h_next();
        }
        return maxArea;
	}

	class MyGestureDetector extends SimpleOnGestureListener 
	{
		@Override
		public boolean onDown(MotionEvent e) 
		{
		    return true;
		}
		
		@Override 
		public boolean onSingleTapConfirmed(MotionEvent e)
		{
			return super.onSingleTapConfirmed(e);
		}
		
		@Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) 
		{
        	try {
                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
                    return false;
                if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    // Left Swipe
                	synchronized (this) {
                		if (relevantPaintings != null && relevantPaintings.size() > 0) {
                			currentImgIndex--;
                			if (currentImgIndex < 0) {
                				currentImgIndex = relevantPaintings.size() - 1;
                			}
                		}
                    }
                }  else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                	// Right Swipe
                	synchronized (this) {
                		if (relevantPaintings != null && relevantPaintings.size() > 0) {
                			currentImgIndex = (currentImgIndex + 1) % relevantPaintings.size();
                		}
                    }
                }
                synchronized (this) {
                	Painting newPainting = relevantPaintings.get(currentImgIndex);
                	Toast.makeText(DrawingBob.this.parentContext, newPainting.title() + " by " + newPainting.artist(), 500).show();
                }
            } catch (Exception e) {
                // nothing
            }
            return false;
        }

    }
}	

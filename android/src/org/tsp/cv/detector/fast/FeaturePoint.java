package org.tsp.cv.detector.fast;

/**
 * Represents a feature point in the image. 
 * @author Alexandros Michael
 *
 */
public class FeaturePoint implements Comparable<FeaturePoint>{

	private int x = 0;
	private int y = 0;
	private int score = 0;
	
	/**
	 * Create a feature point with zero score.
	 * @param x The x-coordinate of the feature point.
	 * @param y The y-coordinate of the feature point.
	 */
	public FeaturePoint(int x, int y) 
	{
		this.x = x;
		this.y = y;
	}
	
	/**
	 * Create a feature point with a determined score.
	 * @param x The x-coordinate of the feature point.
	 * @param y The y-coordinate of the feature point.
	 * @param score The score of the feature point.
	 */
	public FeaturePoint(int x, int y, int score) 
	{
		this.x = x;
		this.y = y;
		this.score = score;
	}
	
	public int x()
	{
		return x;
	}
	
	public int y()
	{
		return y;
	}
	
	public void x(int x)
	{
		this.x = x;
	}
	
	public void y(int y)
	{
		this.y = y;
	}
	
	public int score()
	{
		return score;
	}
	
	public void score(int score)
	{
		this.score = score;
	}
	
	@Override
	public int compareTo(FeaturePoint fc) 
	{
		if (fc.score < this.score) return 1;
		else if (fc.score == this.score) return 0;
		else return -1;
	}
	
	public double getDistanceTo(FeaturePoint fp)
	{
		return Math.sqrt(( (x - fp.x()) * (x - fp.x())) + ( (y - fp.y()) * (y - fp.y())));
	}
	
	public double getDistanceTo(int _x, int _y)
	{
		return Math.sqrt(( (x - _x) * (x - _x)) + ( (y - _y) * (y - _y)));
	}

}

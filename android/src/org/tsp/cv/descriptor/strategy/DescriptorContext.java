package org.tsp.cv.descriptor.strategy;

import java.util.List;

import org.tsp.cv.descriptor.FeatureVector;
import org.tsp.cv.descriptor.strategy.ihistogram.HistogramDescriptorStrategy;
import org.tsp.cv.descriptor.strategy.sift.SiftDescriptorStrategy;
import org.tsp.cv.detector.fast.FeaturePoint;

public class DescriptorContext implements Context {

	private Strategy strategy;
	private enum DESCRIPTOR_STRATEGIES {
		INTENSITY_HISTOGRAM,
		SIFT,
		SURF
	};
	
	public DescriptorContext(Strategy strategy) 
	{
		this.strategy = strategy;
	}
	
	public DescriptorContext(int keycode) 
	{
		if (keycode == DESCRIPTOR_STRATEGIES.INTENSITY_HISTOGRAM.ordinal()) {
			strategy = new HistogramDescriptorStrategy();
		} else if (keycode == DESCRIPTOR_STRATEGIES.SIFT.ordinal()) {
			strategy = new SiftDescriptorStrategy();
		}
	}
	
	@Override
	public List<FeatureVector> getFeatureVectors(List<FeaturePoint> featurePoints, double[][] pixels) 
	{
		return strategy.calcFeatureVectors(featurePoints, pixels);
	}
	
	public Strategy getStrategy()
	{
		return this.strategy;
	}
	
}

package edu.wisc.jj;

import java.io.File;
import java.util.List;

public interface KNN {

	/**
	 * number of tests
	 */
	public int test=10;	
	
	/**
	 * add one training item to the existing training set
	 * assume that there will be no key strokes that have exactly the same features
	 */
	public void addTrainingItem(String category, double[] features);
	
	/**
	 * add an array of training items to the training set
	 * 
	 * @param category
	 *            : array that contains all the categories for samples to be
	 *            added
	 * @param features
	 *            : 2-D array. Each element is the feature vector of one sample.
	 *            The order of feature vectors should match the order for
	 *            category array. A deep copy is made for the feature vector.
	 */
	public void addTrainingItems(String[] category, double[][] features) ;
	
	/**
	 * Classify one single test data
	 * 
	 * @param testData
	 *            : 1-D array. Feature vector of such sample
	 * @param k
	 *            The number of neighbors to use for classification
	 * @return The object KNNResult contains classification category and an Item
	 *         array of nearest neighbors
	 */
	public String classify(double[] testData, int k) ;
	

	/**
	 * Classify multiple test data
	 * 
	 * @param testData
	 *            : 2-D array. Each row is a feature vector for one sample
	 * @param k
	 *            The number of neighbors to use for classification
	 * @return The object KNNResult contains classification category and an Item
	 *         array of nearest neighbors
	 */
	public String[] classifyMultiple(double[][] testData, int k) ;

	/**
	 * return the closest k neighbors of last classification.
	 * should only be called after classify to get the correct info
	 * note: the returned array contains actual references to the training data, any modification to the any element of array will be reflected in the 
	 * underlying training data. Do not modify the each Item element. 
	 * @return an array of Item that is closest to the point passed into classify method. length of the array is the argument "k" passed to the classify method called last time 
	 */
	public Item[] getClosestList();
	
	/**
	 * remove indexth element in the closestList being passed into this function from the training set 
	 * @param closestList
	 * @param index
	 * @return true -- if there is indeed such an element in original training set, false if there is no such element in traing set
	 */
	public boolean removeItemInClosestList(Item[] closestList, int index);
	
	
	/**
	 * return the labels list from Item arrays
	 * the returned list of labels are doesn't contain repetitive labels
	 * @param nearItems： usually is the array returned by getClosestList 
	 * @return
	 */
	public List<String> getLabelsFromItems(Item[] nearItems) ;
	
	/**
	 * save current training set into a file
	 * The file and its path will be created if not exist
	 * The file will be overwritten if it exists
	 * @param sFile
	 * @return
	 */
	public boolean save(File sFile);
	
	/**
	 * load sFile into the current training set of KNN.
	 * This operations doesn't clear the elements in current training set.
	 * The trainingset after this load function will be the union of exisitng items in trainingset
	 * and the new items loaded from files
	 * To clear current training set, call clear first  
	 * @param sFile
	 * @return
	 */
	public boolean load(File sFile);
	
	/**
	 * clear all the elements in the current trainingset of KNN
	 */
	public void clear();
	
	/**
	 * print all the elements of 
p	 * @return
	 */
	@Override
	public String toString();
	
}

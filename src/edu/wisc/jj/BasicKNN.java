package edu.wisc.jj;
import android.annotation.SuppressLint;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A kNN classification algorithm implementation.
 * 
 */
@SuppressLint("NewApi")
public class BasicKNN implements KNN{
	List<Item> trainingSet;
	Item[] closestList;
	
	public int test;
	
	public BasicKNN() {
		this.trainingSet = new ArrayList<Item>();
	}

	/**
	 * add one training item to the existing training set
	 * assume that there will be no key strokes that have exactly the same features
	 */
	public void addTrainingItem(String category, double[] features) {
		Item nItem = new Item(category, features);
		this.trainingSet.add(nItem);
	}

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
	public void addTrainingItems(String[] category, double[][] features) {
		for (int i = 0; i < category.length; i++) {
			Item nItem = new Item(category[i], features[i]);
			this.trainingSet.add(nItem);
		}
	}
	
	/**
	 * return the closest k neighbors of last classification.
	 * should only be called after classify to get the correct info
	 * note: the returned array contains actual references to the training data, any modification to the any element of array will be reflected in the 
	 * underlying training data. Do not modify the each Item element. 
	 * @return an array of Item that is closest to the point passed into classify method. length of the array is the argument "k" passed to the classify method called last time 
	 */
	public Item[] getClosestList(){
		return Arrays.copyOf(this.closestList,this.closestList.length);
	}
	
	/**
	 * remove indexth element in the closestList being passed into this function from the training set 
	 * @param closestList
	 * @param index
	 * @return true -- if there is indeed such an element in original training set, false if there is no such element in traing set
	 */
	public boolean removeItemInClosestList(Item[] closestList, int index){
		Item removeItem=closestList[index];
		return this.trainingSet.remove(removeItem);
	}
	
	/**
	 * Classify one single test data. Return the top choices onto 
	 * 
	 * @param testData
	 *            : 1-D array. Feature vector of such sample
	 * @param k
	 *            The number of neighbors to use for classification
	 * @return The object KNNResult contains classification category and an Item
	 *         array of nearest neighbors
	 */
	public String classify(double[] testData, int k) {
		//clear closest set
		this.closestList=new Item[k];
		
		// check k's value. if number of k is larger than the total number entry
		// in trainingData, then exit
		if (k > this.trainingSet.size()) {
			System.out
					.println("ERROR: k's value is larger than training set entry numbers");
			System.exit(1);
		}
		String catResult = null;
		// for each test item in testData
		ItemDis[] nns = new ItemDis[k];
		Item tItem = new Item(testData);
		// initialize nns.Consider the first k elements in the training
		// array as closest
		double topBound = Double.MAX_VALUE;
		for (int i = 0; i < nns.length; i++) {
			double distance = findDistance(tItem, this.trainingSet.get(i));
			topBound = addItemToTop(this.trainingSet.get(i), nns, distance);
		}
		// find kNN in trainingData
		for (int i = nns.length; i < this.trainingSet.size(); i++) {
			Item rItem = this.trainingSet.get(i);
			double distance = findDistance(tItem, rItem);
			if (distance < topBound) {
				// add to nns if the distance is smaller than the top bound,
				// also update the topBound
				topBound = addItemToTop(rItem, nns, distance);
			}
		}
		// get predicted category, save in KNNResult.categoryAssignment
		catResult = getCat(nns);
		for (int idx=0; idx<nns.length; idx++){
			this.closestList[idx] = nns[idx].item;
		}
		return catResult;
	}
	
	

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
	public String[] classifyMultiple(double[][] testData, int k) {
		// check k's value. if number of k is larger than the total number entry
		// in trainingData, then exit
		if (k > this.trainingSet.size()) {
			System.out
					.println("ERROR: k's value is larger than training set entry numbers");
			System.exit(1);
		}
		String[] catResult = new String[testData.length];
		// for each test item in testData
		for (int tIndex = 0; tIndex < testData.length; tIndex++) {
			ItemDis[] nns = new ItemDis[k];
			Item tItem = new Item(testData[tIndex]);
			// initialize nns.Consider the first k elements in the training
			// array as closest
			double topBound = Double.MAX_VALUE;
			for (int i = 0; i < nns.length; i++) {
				double distance = findDistance(tItem, this.trainingSet.get(i));
				topBound = addItemToTop(this.trainingSet.get(i), nns, distance);
			}
			// find kNN in trainingData
			for (int i = nns.length; i < this.trainingSet.size(); i++) {
				Item rItem = this.trainingSet.get(i);
				double distance = findDistance(tItem, rItem);
				if (distance < topBound) {
					// add to nns if the distance is smaller than the top bound,
					// also update the topBound
					topBound = addItemToTop(rItem, nns, distance);
				}
			}
			// get predicted category, save in KNNResult.categoryAssignment
			catResult[tIndex] = getCat(nns);
		}
		return catResult;
	}

	/**
	 * save current training set into a file
	 * The file and its path will be created if not exist
	 * The file will be overwritten if it exists
	 * @param sFile
	 * @return
	 */
	public boolean save(File sFile){
		sFile.getParentFile().mkdirs();
//		File parentFile=new File(sFile.getParentFile().toString()+"/");
//		File parentFile=new File("test/");		
//		Boolean createDir=parentFile.mkdirs();
		if (!sFile.exists())
			try {
//				System.out.println(sFile.mkdirs());
				sFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("failed to create file for saving KNN");
				System.out.println(sFile.getAbsolutePath()+"is probably a directory");				
				return false;
			}
		try {
			FileWriter fw = new FileWriter(sFile,false); // overwrite instead of append
			BufferedWriter bw = new BufferedWriter(fw);
			for (Item eachItem:this.trainingSet){

					bw.write(eachItem.toString());
			}
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("failed when trying to save the trainingset");
			return false;
		}
		return true;
	}
	
	/**
	 * load sFile into the current training set of KNN.
	 * This operations doesn't clear the elements in current training set.
	 * The trainingset after this load function will be the union of exisitng items in trainingset
	 * and the new items loaded from files
	 * To clear current training set, call clear first  
	 * @param sFile
	 * @return
	 */
	public boolean load(File sFile){
		if (!sFile.exists()){
			System.out.println("load failed. No such file");
			return false;
		}
		try {
			FileReader fr = new FileReader(sFile);
			BufferedReader br = new BufferedReader(fr);
			String eachLine=null;
			while (null != (eachLine=br.readLine()) ){
				String[] text=eachLine.split(":");
				String category=text[0];
				String featureLine=text[1];
				String[] featuresInString=featureLine.split(",");
				double[] features=new double[featuresInString.length];
				for (int i=0; i<features.length; i++)
					features[i]=Double.valueOf(featuresInString[i]);
				Item nItem=new Item(category, features);
				this.trainingSet.add(nItem);
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("failed when trying to load the trainingset");
			return false;
		}
		return true;
	}
	
	/**
	 * clear all the elements in the current trainingset of KNN
	 */
	public void clear(){
		this.trainingSet.clear(); 
	}
	
	/**
	 * print all the elements of 
	 * @return
	 */
	@Override
	public String toString(){
		StringBuilder result =new StringBuilder();
		for (Item eachItem: this.trainingSet)
			result.append(eachItem.toString());
		return result.toString();
	}
	
	/**
	 * find the distance between two items
	 */
	private double findDistance(Item item1, Item item2) {
		if (item1.features.length != item2.features.length) {
			System.out.println("training point has different features");
			System.exit(1);
		}
		double result = 0.0;
		for (int i = 0; i < item1.features.length; i++) {
			result += (item1.features[i] - item2.features[i])
					* (item1.features[i] - item2.features[i]);
		}
		result = Math.sqrt(result);
		return result;
	}

	/**
	 * add the item to the neareast neighbor, return the loose bound of the
	 * neareast neighbors
	 */
	double addItemToTop(Item rItem, ItemDis[] nns, double distance) {
		ItemDis mItemDis = new ItemDis(rItem, distance);
		for (int i = 0; i < nns.length; i++) {
			if (nns[i] == null) { // hanlde initilization
				nns[i] = mItemDis;
				break;
			} else if (distance < nns[i].distance) {
				for (int j = nns.length - 1; j > i; j--) {
					nns[j] = nns[j - 1];
				}
				nns[i] = mItemDis;
				break;
			}
		}
		// find the loose bound
		for (int i = nns.length - 1; i >= 0; i--) {
			if (nns[i] != null) {
				return nns[i].distance;
			}
		}
		System.out
				.println("Error:tried to get the loose boundary of a null nns array");
		System.exit(1);
		return 0.0;
	}

	/**
	 * get the majority category of the nns
	 * @return return the majority vote of the k nearest neighbors if no tie. Return the closest one's label if there is a tie. 
	 */
	private String getCat(ItemDis[] ori) {
		List<String> category = new ArrayList<String>();
		int[] scores = new int[ori.length];
		for (ItemDis mItem : ori) {
			String mStr = mItem.item.category;
			boolean find = false;
			int index = 0; // position of mCat in category
			for (String mCat : category) {
				if (mCat.equals(mStr)) {
					scores[index]++;
					find = true;
				}
				index++;
			}
			if (!find) {// if there is no such category in the list
				// index is now pointing to the next available space
				category.add(mStr);
				scores[index] = 1;
			}
		}
		int maxIndex = findMaxIndex(scores);
		if (-1 == maxIndex) // if there is a tie, return the closest item's label
			return ori[0].item.category;
		else 
			return category.get(maxIndex);
	}

	/**
	 * find the index of the maximum in the array scores
	 * If there is a tie, return -1
	 * @param scores
	 * @return
	 */
	private int findMaxIndex (int[] scores) {
		int resultIndex=0;
		int max=scores[0];
		for (int index=1;index<scores.length;index++){
			if (scores[index]>max){
				resultIndex=index;
				max=scores[index];
			}				
		}
		boolean tie=false;
		//search for the tie
		for (int index=0; index<scores.length;index++){
			if (max == scores[index] && resultIndex!=index) // when there is another element that is the same as max
				tie=true;
		}
		if (tie)
			resultIndex=-1;
		return resultIndex;		
	}
}

/**
 * an helper class to bundle item and distance together
 * 
 * @author jj
 * 
 */
class ItemDis {
	public double distance;
	public Item item;

	public ItemDis(Item mItem, double mDistance) {
		this.distance = mDistance;
		this.item = mItem;
	}

	@Override
	public String toString() {
		DecimalFormat df = new DecimalFormat("#.##");
		String output = String.valueOf(df.format(distance)) + "=="
				+ this.item.category;
		return output;
	}
}


class KNNResult {
	// The category assignment of each test instance,
	public String[] categoryAssignment;
	// The assignment of nearest neighbors for test instances
	public Item[][] nearestNeighbors;
}

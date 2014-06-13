package edu.wisc.jj;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.util.Log;

/**
 * A kNN classification algorithm implementation.
 * 
 */
public class BasicKNN implements KNN{
	List<Item> trainingSet; // total training set. existing for deletetion without limit
	List <Item> trainingSet_snap;//snap shot of current traing Set
	List<Item> workingSet; // current working set for KNN detection 	
	Item[] closestList;
	public final int DISTTHRE = 1;
	// number of training samples to keep for each
	// category. default 5
	//important: when you change this, change TRAINUM in MainActivity
	private int trainingSize = 5; 
	private Item staged;
	// sorted item's categories given distance. updated each time when classify
	// is called
	// it is just used for hints instead of actual classification
	// onlyclosestList will determine the result of classification
	TreeMap<Double, String> sortedItemMap;

	// predicted label of last classification. helper for getHints
	private String lastPredictedLabel;
	// variances array used for standardizing euclidean distance
	private double[] variances;

	public BasicKNN() {
		this.trainingSet = new ArrayList<Item>();
		this.workingSet = new ArrayList<Item>();		
	}
	
	/**
	 * set the trainingSize for each Key
	 * 
	 * @param num
	 *            : trainingSize
	 */
	public void setTrainingSize(int num) {
		this.trainingSize = num;
	}

	public int getTrainingSize() {
		return this.trainingSize;
	}

	private void addTrainingItem(Item item) {
		this.trainingSet.add(item);
	}

	/**
	 * add one training item to the existing training set assume that there will
	 * be no key strokes that have exactly the same features
	 * 
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
	 * return the closest k neighbors of last classification. should only be
	 * called after classify to get the correct info note: the returned array
	 * contains actual references to the training data, any modification to the
	 * any element of array will be reflected in the underlying training data.
	 * Do not modify the each Item element.
	 * 
	 * @return an array of Item that is closest to the point passed into
	 *         classify method. length of the array is the argument "k" passed
	 *         to the classify method called last time
	 */
	@SuppressLint("NewApi")
	public Item[] getClosestList() {
		return Arrays.copyOf(this.closestList, this.closestList.length);
	}

	/** NOT USED ANYMORE
	 * remove indexth element in the closestList being passed into this function
	 * from the training set
	 * 
	 * @param closestList
	 * @param index
	 * @return true -- if there is indeed such an element in original training
	 *         set, false if there is no such element in traing set
	 */
	public boolean removeItemInClosestList(Item[] closestList, int index) {
		return false;
//		Item removeItem = closestList[index];
//		return this.trainingSet.remove(removeItem);
	}

	/***
	 * remove the latest input item from trainingSet
	 * 
	 * TODO have not tested yet
	 */

	public void removeLatestInput() {
		if(null != this.staged)
			this.staged = null;
		//else
			//trainingSet.remove(trainingSet.size() - 1);
	}


	
	/**
	 * 
	 * Classify one single test data. Return the top choice.
	 * 
	 * if dictHints != null, then
	 * Classify based on the dictionary hints that are given
	 * if the majority vote of knn is not inside the dictHints, then we go for the next
	 * highest votes in the k neighbors. If nothing in the k neighbors are inside the 
	 * dictionary hints, then dictionary hints are ignored, the original majority vote 
	 * is returned  
	 * 
	 * The policy above is implemented in "getCat" method
	 * 
	 * @param testData
	 *            : 1-D array. Feature vector of such sample
	 * @param k
	 *            The number of neighbors to use for classification
	 * @param dictHints
	 * 		The hints given by dictionary
	 * @return The object KNNResult contains classification category and an Item
	 *         array of nearest neighbors
	 */
	public String classify(double[] testData, int k,List<String> dictHints) {
		//IMPORTANT METHOD: generate current working set for detection
		// commit whatever in staging area into trainingset
		this.commit();

		// update variances array for calculating distance
		this.updateVariances(this.workingSet);

		// create new sortedItem map
		this.sortedItemMap = new TreeMap<Double, String>();

		// clear closest set
		this.closestList = new Item[k];

		// check k's value. if number of k is larger than the total number entry
		// in trainingData, then exit
		if (k > this.workingSet.size()) {
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
			double distance = findDistance(tItem, this.workingSet.get(i));
			topBound = addItemToTop(this.workingSet.get(i), nns, distance);

			// update sortedItemMap
			this.sortedItemMap.put(distance, this.workingSet.get(i).category);
		}
		// find kNN in trainingData
		for (int i = nns.length; i < this.workingSet.size(); i++) {
			Item rItem = this.workingSet.get(i);
			double distance = findDistance(tItem, rItem);
			// update sortedItemMap
			this.sortedItemMap.put(distance, rItem.category);

			if (distance < topBound) {
				// add to nns if the distance is smaller than the top bound,
				// also update the topBound
				topBound = addItemToTop(rItem, nns, distance);
			}
		}
		catResult = getCat(nns,dictHints);
		for (int idx = 0; idx < nns.length; idx++) {
			this.closestList[idx] = nns[idx].item;
		}
		updateWrongTimesAfterClassify(this.closestList, catResult);
		// record label predicted
		this.lastPredictedLabel = catResult;
		return catResult;
	}



	/**
	 * based on results, update the item's wrong times according to their label
	 * with majority vote
	 * 
	 * @param closestList
	 *            : list of items that are used to determine the majority vote
	 * @param catResult
	 */
	private void updateWrongTimesAfterClassify(Item[] closestList,
			String catResult) {
		for (Item mItem : closestList) {
			if (mItem.category.equals(catResult))
				mItem.modiWT(-1);
			else
				mItem.modiWT(1);				
		}
	}

	/**
	 * change the label of the sample point in staging area put the bad samples
	 * who causes the wrong detection on record
	 * 
	 * @param correctLabel
	 * @return true, if successfully changed. wrong, if there is no item in
	 *         staging area or no item in cloestList
	 */
	public boolean correctWrongDetection(String correctLabel, String wrongLabel) {
		if (this.staged == null || this.closestList == null)
			return false;
		this.staged.category = correctLabel;
		for (Item mItem : this.closestList) {
			if (correctLabel.equals(mItem.category)) { // positive influence
				mItem.modiWT(-2); // 2 instead of 1 to
															// counter the 1
															// added for
															// detecting wrong
															// in
															// updatewrongTimesAfterClassify
			} else if (wrongLabel.equals(mItem.category)) { // negative influce
				mItem.modiWT(2);
			}
		}
		return true;
	}

	/**
	 * save current training set into a file The file and its path will be
	 * created if not exist The file will be overwritten if it exists
	 * 
	 * @param sFile
	 * @return
	 */
	public boolean save(File sFile) {
		sFile.getParentFile().mkdirs();
		// File parentFile=new File(sFile.getParentFile().toString()+"/");
		// File parentFile=new File("test/");
		// Boolean createDir=parentFile.mkdirs();
		if (!sFile.exists())
			try {
				// System.out.println(sFile.mkdirs());
				sFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("failed to create file for saving KNN");
				System.out.println(sFile.getAbsolutePath()
						+ "is probably a directory");
				return false;
			}
		try {
			FileWriter fw = new FileWriter(sFile, false); // overwrite instead
															// of append
			BufferedWriter bw = new BufferedWriter(fw);
			for (Item eachItem : this.trainingSet) {

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
	 * load sFile into the current training set of KNN. This operations doesn't
	 * clear the elements in current training set. The trainingset after this
	 * load function will be the union of exisitng items in trainingset and the
	 * new items loaded from files To clear current training set, call clear
	 * first
	 * 
	 * @param sFile
	 * @return
	 */
	public boolean load(File sFile) {
		if (!sFile.exists()) {
			System.out.println("load failed. No such file");
			return false;
		}
		try {
			FileReader fr = new FileReader(sFile);
			BufferedReader br = new BufferedReader(fr);
			String eachLine = null;
			while (null != (eachLine = br.readLine())) {
				String[] text = eachLine.split("(:)|(==)");
				String category = text[0];
				String wrongNum = text[2];
				int num = Integer.valueOf(wrongNum);
				String featureLine = text[3];
				String[] featuresInString = featureLine.split(",");
				double[] features = new double[featuresInString.length];
				for (int i = 0; i < features.length; i++)
					features[i] = Double.valueOf(featuresInString[i]);
				Item nItem = new Item(category, features,num);
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
	public void clear() {
		this.trainingSet.clear();
	}

	/**
	 * print all the elements with feature
	 * 
	 * @return
	 */
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		for (Item eachItem : this.trainingSet)
			result.append(eachItem.toString());
		return result.toString();
	}

	/**
	 * return all the characters(label) in the KNN right now
	 * 
	 * @return
	 */
	public String getChars() {
		StringBuilder result = new StringBuilder();
		for (Item eachItem : this.trainingSet)
			result.append(eachItem.category);
		return result.toString();
	}

	/**
	 * update variance array for standardizing euclidean distance
	 */
	private void updateVariances(List<Item> curWorkingSet) {
		if (0 == curWorkingSet.size()) {
			System.out.println("No item in the training set");
			return;
		}
		this.variances = new double[curWorkingSet.get(0).features.length];
		// calculate variances for each dimension
		for (int i = 0; i < this.variances.length; i++) {
			double sumSquare = 0.0;
			double sumMean = 0.0;
			for (int j = 0; j < curWorkingSet.size(); j++) {
				double value = curWorkingSet.get(j).features[i];
				sumSquare += value * value;
				sumMean += value;
			}
			sumMean = sumMean / (double) curWorkingSet.size();
			sumSquare = sumSquare / (double) curWorkingSet.size();
			this.variances[i] = sumSquare - sumMean * sumMean;
		}
	}

	/**
	 * find the distance between two items use standardized euclidean distance
	 */
	private double findDistance(Item item1, Item item2) {
		if (item1.features.length != item2.features.length) {
			System.out.println("training point has different features");
			System.exit(1);
		}
		// use standardized euclidean distance
		double result = 0.0;
		for (int i = 0; i < item1.features.length; i++) {
			double value = (item1.features[i] - item2.features[i]);
			result += value * value;// / this.variances[i];
		}
		result = Math.sqrt(result);
		return result;
	}

	/**
	 * add the item to the nearest neighbor, return the loose bound of the
	 * nearest neighbors
	 */
	double addItemToTop(Item rItem, ItemDis[] nns, double distance) {
		ItemDis mItemDis = new ItemDis(rItem, distance);
		for (int i = 0; i < nns.length; i++) {
			if (nns[i] == null) { // handle initialization
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
	 * get the majority category of the nns while taking considerations of dictHints
	 * 
	 * sort the categories by majority vote. When tie appears, the category whose has a point
	 * closer to the feature input is given priority
	 * 
	 * Return the highest ranked category that also appears in dictHints
	 * If nothing in the knn category results is in dictHints, then the dictHints is ignored and 
	 * teh highest ranked category by knn is returned
	 * 
	 * @param ori
	 *            , the array to get categories from
	 * @param dictHints
	 * 	A list of string that are given by the dictionaries as hints. Should be the outpus of 
	 * dictionary class "getPossibleChar" method 
	 */
	private String getCat(ItemDis[] ori, List<String> dictHints) {
		Map<String,Integer> votes=new HashMap<String, Integer>(); 
		for (ItemDis mItem:ori){
			if (votes.containsKey(mItem.item.category)){
				//add 1 to the value if contains such key
				int value = votes.get(mItem.item.category);
				votes.put(mItem.item.category,value+1);
			} else{ 
				votes.put(mItem.item.category, 1);
			}
		}
		//sort the tree vote from high to low
		TreeMap<Integer,List<String>> rankings= new TreeMap<Integer, List<String>>(Collections.reverseOrder());
		for (Map.Entry<String, Integer> mEntry:votes.entrySet()){
			List<String> categoryWithCertainVotes=rankings.get(mEntry.getValue());
			if (null == categoryWithCertainVotes){ //no such value exists
				List<String> categories=new ArrayList<String>();
				categories.add(mEntry.getKey());
				rankings.put(mEntry.getValue(), categories);
			} else { 
				//there is a tie situation, put the category with closest point in front
				// i.e. if type a,b both have 2 votes, but a's closest point is closer than
				// b's closest point, then a is given priority
				categoryWithCertainVotes.add(mEntry.getKey());
			}
		}
		//break ties in the map
		for (Map.Entry<Integer,List<String>> mEntry : rankings.entrySet()){		
			List<String> categoryWithCertainVotes=mEntry.getValue();
			if (categoryWithCertainVotes.size()!=1){
				List<String> tieBreaker=new ArrayList<String>();
				//sort to break the tie
				for (int i =0; i<ori.length;i++){
					if (categoryWithCertainVotes.contains(ori[i].item.category) && !tieBreaker.contains(ori[i].item.category)){
						tieBreaker.add(ori[i].item.category);
					}
				}
				rankings.put(mEntry.getKey(), tieBreaker);
			}
		}
		
		//convert Map to List
		List<String> resultFromMajorityVote=new ArrayList<String>();
		for (Map.Entry<Integer,List<String>> mEntry : rankings.entrySet()){
			resultFromMajorityVote.addAll(mEntry.getValue());
		}
		//take consideration of dictHints
		for (String potentialCategory:resultFromMajorityVote){
			if (null!=dictHints && dictHints.contains(potentialCategory))
				return potentialCategory;
		}
		//if nothing in the output of knn matches dictHints, then ignore the dictHints
		return resultFromMajorityVote.get(0);
		
//		List<String> resultCategory=new ArrayList<String>();
//		for (ItemDis mItem:ori){
//			if (!resultCategory.contains(mItem.item.category)){
//				
//			}
//		}
//			
//		
//		List<String> category = new ArrayList<String>();
//		int[] scores = new int[ori.length];
//		for (int i = 0; i < validLength; i++) {
//			ItemDis mItem = ori[i];
//			String mStr = mItem.item.category;
//			boolean find = false;
//			int index = 0; // position of mCat in category
//			for (String mCat : category) {
//				if (mCat.equals(mStr)) {
//					scores[index]++;
//					find = true;
//				}
//				index++;
//			}
//			if (!find) {// if there is no such category in the list
//				// index is now pointing to the next available space
//				category.add(mStr);
//				scores[index] = 1;
//			}
//		}
//		int maxIndex = findMaxIndex(scores);
//		// if there is a tie, eliminate the last sample point (farthest) from
//		// consideration
//		if (-1 == maxIndex)
//			return getCat(ori, validLength - 1);
//		else
//			return category.get(maxIndex);
	}

//	/**
//	 * find the index of the maximum in the array scores If there is a tie,
//	 * return -1
//	 * 
//	 * @param scores
//	 * @return
//	 */
//	private int findMaxIndex(int[] scores) {
//		int resultIndex = 0;
//		int max = scores[0];
//		for (int index = 1; index < scores.length; index++) {
//			if (scores[index] > max) {
//				resultIndex = index;
//				max = scores[index];
//			}
//		}
//		boolean tie = false;
//		// search for the tie
//		for (int index = 0; index < scores.length; index++) {
//			if (max == scores[index] && resultIndex != index) // when there is
//																// another
//																// element that
//																// is the same
//																// as max
//				tie = true;
//		}
//		if (tie)
//			resultIndex = -1;
//		return resultIndex;
//	}

	/**
	 * return the labels array from Item arrays the returned array of labels are
	 * doesn't contain repetitive labels
	 * 
	 * @param nearItems
	 *            ： usually is the array returned by getClosestList
	 * @return
	 */
	public List<String> getLabelsFromItems(Item[] nearItems) {
		List<String> labelList = new ArrayList<String>();
		for (Item mitem : nearItems) {
			if (!labelList.contains(mitem.category))
				labelList.add(mitem.category);
		}
		// String[] labelArray=labelList.toArray(new String[labelList.size()]);
		return labelList;
	}

	/**
	 * commit samples in the staging area into the training set reorganize the
	 * training set according to the wrongTime of item, but always add the new
	 * item at staging in (won't kick it out immediately, since there is no prev
	 * history).
	 * 
	 * Right now, it also generates the working set. IMPORTANT
	 */
	public void commit() {
		// make sure the new point get added in ??
		if (null != this.staged) {
			this.staged.wrongTimes = Integer.MIN_VALUE;
			this.addTrainingItem(this.staged);
		}

		//start backward in the training set so that for the latest objects gets 
		// higher priority when their wrong times equals
		Map<String, List<Item>> train_set_per_char = new HashMap<String, List<Item>>();
		for (int idx = this.trainingSet.size()-1; idx>=0; idx--){
			Item mItem = this.trainingSet.get(idx);
			if (!train_set_per_char.containsKey(mItem.category)) {
				List<Item> mList = new ArrayList<Item>();
				mList.add(mItem);
				train_set_per_char.put(mItem.category, mList);
			} else { // contains
				List<Item> mList = train_set_per_char.get(mItem.category);
				mList.add(mItem);
			}
		}
		
		////////////////////////////////////
		//generate a new working set as KNN existing data points
		////////////////////////////////////
		this.workingSet.clear();
		for (Map.Entry<String, List<Item>> mEntry : train_set_per_char.entrySet()) {
			List<Item> mList = mEntry.getValue();
			//1. sort items based on their wrong times
			TreeMap<Integer, List<Item>> spl_lst_per_cat = new TreeMap<Integer, List<Item>>();
			for (Item eachItem: mList){
				// if there is no such wrong time value in the dictionary, then create a new list 
				if (!spl_lst_per_cat.containsKey(eachItem.wrongTimes)) {
					List<Item> item_lst_base_wrongTimes= new ArrayList<Item>();
					item_lst_base_wrongTimes.add(eachItem);
					spl_lst_per_cat.put(eachItem.wrongTimes, item_lst_base_wrongTimes);
				} else {
					// if there is already such a wrong time value as key, just add the new item in
					List<Item> cur_list= spl_lst_per_cat.get(eachItem.wrongTimes);
					cur_list.add(eachItem);
				}
			}
			//2. grab items with smallest value of wrong times
			int cur_num_per_char=0; //number of training item added to working set for a particular character
			for (Map.Entry<Integer, List<Item>> eachEntry: spl_lst_per_cat.entrySet()){
				//double break needed for correct operations
				if (cur_num_per_char == this.trainingSize)
					break;
				//when we've added enough training items for each character, then stop adding more for the same character
				List<Item> item_lst_per_wrongTimes=eachEntry.getValue();
				for (Item eachItem: item_lst_per_wrongTimes){
					Log.d("wrongTimes", mEntry.getKey()+": "+eachItem.toString());
					this.workingSet.add(eachItem);
					cur_num_per_char++;
					if (cur_num_per_char == this.trainingSize)
						break;
				}
			}
		}
		
		// after adding staged in, correct it wrong times
		if (null != this.staged) {
			this.staged.wrongTimes = -2;
			this.staged = null;
		}
	}

	/**
	 * add a sample point to stage. but not commit, not in KNN for classifying
	 * In classify, it will commit first before doing the classification If
	 * there is a sample point in staging area before, replace such sample
	 * (consider such sample as useless)
	 * 
	 * @param category
	 * @param features
	 */
	public void addToStage(String category, double[] features) {
		Item mItem = new Item(category, features);
		this.staged = mItem;
	}

	/**
	 * clear sample points in staging area of this KNN
	 * 
	 * @return true, if successfully dropped; false, if there is no sample in
	 *         staging
	 */
	public boolean dropStage() {
		if (this.staged == null)
			return false;
		else {
			this.staged = null;
			return true;
		}
	}

	/**
	 * return hints for user for each classification these hints are just a
	 * general guidelines of what this KNN thinks may be correct results. not
	 * accurate predication. but right now it's accurate enough for hints
	 * 
	 * @param num
	 *            number of hints needs
	 * @return an array of hints strings (each element is a category)
	 */
	public String[] getHints(int num) {
		System.out.println(this.sortedItemMap);
		List<String> allHints = new ArrayList<String>();
		int addedNum = 0;
		for (Map.Entry<Double, String> mEntry : this.sortedItemMap.entrySet()) {
			String oneHint = mEntry.getValue();
			if ((!this.lastPredictedLabel.equals(oneHint))
					&& (!allHints.contains(oneHint))) {
				allHints.add(oneHint);
				addedNum++;
			}
			if (addedNum == num)
				break;
		}
		String[] hintsArray = allHints.toArray(new String[allHints.size()]);
		return hintsArray;
	}

	/**
	 * return hints for user for each classification these hints are just a
	 * general guidelines of what this KNN thinks may be correct results. not
	 * accurate predication. It also take the results coming from dictionary
	 * into considerations. Only when there is a same hint returned from
	 * dictionary, such character is considered as a hint
	 * 
	 * @param num
	 *            number of hints needs
	 * @return an array of hints strings (each element is a category)
	 */
	public List<String> getHints(int num, List<String> dictionaryResult) {
//		System.out.println(this.sortedItemMap);
		List<String> allHints = new ArrayList<String>();
		int addedNum = 0;
		// whether dictionary is useful		
		boolean useDict=(dictionaryResult != null )&& (!dictionaryResult.isEmpty());
		for (Map.Entry<Double, String> mEntry : this.sortedItemMap.entrySet()) {
			String oneHint = mEntry.getValue();
			if ((!this.lastPredictedLabel.equals(oneHint))
					&& (!allHints.contains(oneHint))) {
				if (useDict){
					if (dictionaryResult.contains(oneHint)){
						allHints.add(oneHint);
						addedNum++;					
					}
				} else {
					allHints.add(oneHint);
					addedNum++;										
				}
			}
			if (addedNum == num)
				break;
		}
//		String[] hintsArray = allHints.toArray(new String[allHints.size()]);
		return allHints;
	}

	/**
	 * take a snapshot of current training set 
	 * shallow copy
	 */
	public void snapShot(){
		if (null==this.trainingSet_snap){
			this.trainingSet_snap= new ArrayList<Item>();
		}
		for (Item eachItem:this.trainingSet)
			this.trainingSet_snap.add(eachItem);
	}

	/**
	 * revert back to the original training set
	 * ignore the online adaptation added training set
	 */
	public void reset() {
		if (null == this.trainingSet_snap){
			Log.e("knn", "try to reset training set. but no snap shot available");
		} else{
			// revert back to snap shot, change all wrong times back to 0
			Log.d("knn", "revert back to original training set");
			this.trainingSet.clear();;			
			for (Item eachItem:this.trainingSet_snap){
				this.trainingSet.add(eachItem);
			}
			for (Item each: this.trainingSet){
				each.wrongTimes=0;
			}
		}
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

package edu.wisc.jj;

/**
 * Item for KNN, item should have a category (label) and features
 */
public class Item {
	// The category of the instance
	public String category;
	// The feature vector of the instance
	public double[] features;
	//number of wrong detection that this item plays a role in determining majority vote
	public int wrongTimes;
	
	public Item(double[] features) {
		this.category = null;
		this.features=new double[features.length];
		this.wrongTimes=0;
		for (int i = 0; i < features.length; i++)
			this.features[i] = features[i];
	}

	public Item(String category, double[] features) {
		this.category = category;
		this.features = new double[features.length];
		this.wrongTimes=0;
		for (int i = 0; i < features.length; i++)
			this.features[i] = features[i];
	}
	
	public Item(String category, double[] features, int wrongNum) {
		this.category = category;
		this.features = new double[features.length];
		this.wrongTimes=wrongNum;
		for (int i = 0; i < features.length; i++)
			this.features[i] = features[i];
	}
	
	/**
	 * modify wrongTimes, inc is the increment adding
	 * @param inc
	 */
	public void modiWT(int inc){
		this.wrongTimes += inc;
		if (this.wrongTimes >= 3){
			this.wrongTimes = 3;
		} else if (this.wrongTimes <=-3)
			this.wrongTimes = -3;
	}
	
	@Override
	/**
	 * Item to string should in the following format: "category:feature1,feature2,feature3...."
	 */
	public String toString() {
		StringBuilder output=new StringBuilder(this.category+":");
		//append wrong times
		output.append("wrong:"+String.valueOf(this.wrongTimes)+"==");
		int printSize=this.features.length;
		if (printSize > 3)
			printSize=3;
		for (int index=0;index<printSize;index++){
			output.append(features[index]);
			output.append(",");
		}
		//replace last "," with "\n"
		output=output.replace(output.length()-1, output.length(), "\n");
		return output.toString();
	}
	
}

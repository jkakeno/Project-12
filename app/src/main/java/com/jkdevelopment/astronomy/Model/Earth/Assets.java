package com.jkdevelopment.astronomy.Model.Earth;

import com.google.gson.annotations.SerializedName;

import java.util.List;

//@Generated("com.robohorse.robopojogenerator")
public class Assets{

	@SerializedName("count")
	private int count;

	@SerializedName("results")
	private List<ResultsItem> results;

	public void setCount(int count){
		this.count = count;
	}

	public int getCount(){
		return count;
	}

	public void setResults(List<ResultsItem> results){
		this.results = results;
	}

	public List<ResultsItem> getResults(){
		return results;
	}

}
package org.math.R;

import java.util.Map;

/**
 * DTO containing arguments of an R function like c(..., ...) or array(data = , ...) etc
 * Arguments are stored in a map (key, value), startIndex and stopIndex are the first and last index of the function.
 * 
 * @author Nicolas Chabalier
 *
 */
public class RFunctionArgumentsDTO {
	/**
	 * First index of the function
	 */
	private int startIndex;
	
	/**
	 * Last index of the function
	 */
	private int stopIndex;
	
	/**
	 * Map containing arguments of the function with their value associated
	 */
	private Map<String, String> argumentsMap;
	
	public RFunctionArgumentsDTO(int startIndex, int stopIndex, Map<String, String> argumentsMap) {
		this.startIndex = startIndex;
		this.stopIndex = stopIndex;
		this.argumentsMap = argumentsMap;
	}
	
	public int getStartIndex() {
		return this.startIndex;
	}
	public int getStopIndex() {
		return this.stopIndex;
	}
	public Map<String, String> getGroups() {
		return this.argumentsMap;
	}
	
	
}

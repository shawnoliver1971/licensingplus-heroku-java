package core.sfdc.responses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SalesforceQueryMoreResponse <T> {
	private List<T> records;
	
	public SalesforceQueryMoreResponse() {
		this.records = new ArrayList<T>();
	}
	
	public void addRecords(T[] records) {
		if (records!=null && records.length>0) {
			this.records.addAll(Arrays.asList(records));
		}
	}
	
	public List<T> getRecords() {
		return this.records;
	}
}

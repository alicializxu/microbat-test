package microbat.recommendation.calculator;

public class VariableSimilarity {
	public int isSameLocalVarType;
	public int isSameLocalVarName;
	
	public int isSameFieldParent;
	public int isSameFieldType;
	public int isSameFieldName;
	
	public int isSameArrayParent;
	public int isSameArrayType;
	public int isSameArrayIndex;
	
	public VariableSimilarity(int isSameLocalVarType, int isSameLocalVarName, int isSameFieldParent,
			int isSameFieldType, int isSameFieldName, int isSameArrayParent, int isSameArrayType,
			int isSameArrayIndex) {
		super();
		this.isSameLocalVarType = isSameLocalVarType;
		this.isSameLocalVarName = isSameLocalVarName;
		this.isSameFieldParent = isSameFieldParent;
		this.isSameFieldType = isSameFieldType;
		this.isSameFieldName = isSameFieldName;
		this.isSameArrayParent = isSameArrayParent;
		this.isSameArrayType = isSameArrayType;
		this.isSameArrayIndex = isSameArrayIndex;
	}
	
	public double computeSimilarity(){
		double numerator = isSameLocalVarType + isSameLocalVarName +
				isSameFieldParent + isSameFieldType +
				isSameFieldName + isSameArrayParent +
				isSameArrayType + isSameArrayIndex;
		double demoninator = 7;
		
		return numerator/demoninator;
	}
}
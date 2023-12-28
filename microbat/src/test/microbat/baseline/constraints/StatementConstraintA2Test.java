package microbat.baseline.constraints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import microbat.debugpilot.propagation.BP.constraint.Constraint;
import microbat.debugpilot.propagation.BP.constraint.StatementConstraintA2;
import microbat.debugpilot.propagation.BP.constraint.WrongConstraintConditionException;
import microbat.debugpilot.propagation.probability.PropProbability;
import microbat.model.trace.TraceNode;
import microbat.model.value.PrimitiveValue;
import microbat.model.value.VarValue;
import microbat.model.variable.ConditionVar;
import microbat.model.variable.LocalVar;
import microbat.model.variable.Variable;

public class StatementConstraintA2Test {
	private VarValue readVar1;
	private VarValue readVar2;
	private VarValue writeVar1;
	private VarValue writeVar2;

	private TraceNode node;
	private TraceNode controlDom;

	private String controlDomValueID;

	private double propagationProbability;
	private String statementID;

	@Before
	public void init() {
		final String var1ID = "readVar1";
		LocalVar var1 = new LocalVar(var1ID, "int", "class", 2);
		var1.setVarID(var1ID);
		this.readVar1 = new PrimitiveValue("1", true, var1);

		final String var2ID = "readVar2";
		LocalVar var2 = new LocalVar(var2ID, "int", "class", 2);
		var2.setVarID(var2ID);
		this.readVar2 = new PrimitiveValue("2", true, var2);

		final String var3ID = "writeVar1";
		LocalVar var3 = new LocalVar(var3ID, "int", "class", 2);
		var3.setVarID(var3ID);
		this.writeVar1 = new PrimitiveValue("3", true, var3);

		final String var4ID = "writeVar2";
		LocalVar var4 = new LocalVar(var4ID, "int", "class", 2);
		var4.setVarID(var4ID);
		this.writeVar2 = new PrimitiveValue("4", true, var4);

		this.node = new TraceNode(null, null, 2, null, "");
		this.controlDom = new TraceNode(null, null, 1, null, "");

		final String type = "boolean";
		final String varName = ConditionVar.CONDITION_RESULT_NAME + this.controlDom.getOrder();

		this.controlDomValueID = ConditionVar.CONDITION_RESULT_ID + this.controlDom.getOrder();

		Variable variable = new ConditionVar(varName, type, "", 1);
		VarValue conditionResult = new PrimitiveValue("1", true, variable);
		conditionResult.setVarID(this.controlDomValueID);
		this.controlDom.addWrittenVariable(conditionResult);

		this.propagationProbability = PropProbability.HIGH;
		this.statementID = "S_2";
	}

	@After
	public void tearDown() throws Exception {
		Constraint.resetID();
	}

	@Test
	public void testConstraintID() {
		this.node.addReadVariable(this.readVar1);
		this.node.addWrittenVariable(this.writeVar1);

		Constraint constraint1 = new StatementConstraintA2(this.node, this.propagationProbability);
		Constraint constraint2 = new StatementConstraintA2(this.node, this.propagationProbability);
		Constraint constraint3 = new StatementConstraintA2(this.node, this.propagationProbability);

		assertEquals("SC2_0", constraint1.getConstraintID());
		assertEquals("SC2_1", constraint2.getConstraintID());
		assertEquals("SC2_2", constraint3.getConstraintID());
	}

	@Test(expected = WrongConstraintConditionException.class)
	public void test0R0W1P() {
		this.node.setControlDominator(this.controlDom);
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
	}

	@Test(expected = WrongConstraintConditionException.class)
	public void test1R0W0P() {
		this.node.addReadVariable(readVar1);
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
	}

	@Test(expected = WrongConstraintConditionException.class)
	public void test0R1W0P() {
		this.node.addWrittenVariable(this.writeVar1);
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidCaseNo() {
		this.node.addReadVariable(this.readVar1);
		this.node.addWrittenVariable(this.writeVar1);
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
		constraint.getProbability(8);
	}

	@Test
	public void testControlDominator() {
		this.node.addReadVariable(this.readVar1);
		this.node.addWrittenVariable(this.writeVar1);
		this.node.setControlDominator(this.controlDom);

		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);

		assertTrue(constraint.haveControlDom());
		assertEquals(this.controlDomValueID, constraint.getControlDomID());
	}

	@Test
	public void testVarIDs() {
		this.node.addReadVariable(this.readVar1);
		this.node.addReadVariable(this.readVar2);
		this.node.addWrittenVariable(this.writeVar1);
		this.node.addWrittenVariable(this.writeVar2);
		this.node.setControlDominator(this.controlDom);

		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);

		List<String> predIDs = constraint.getInvolvedPredIDs();
		assertEquals(this.readVar1.getVarID(), predIDs.get(0));
		assertEquals(this.readVar2.getVarID(), predIDs.get(1));
		assertEquals(this.writeVar1.getVarID(), predIDs.get(2));
		assertEquals(this.writeVar2.getVarID(), predIDs.get(3));
		assertEquals(this.controlDomValueID, predIDs.get(4));
		assertEquals(this.statementID, predIDs.get(5));
	}

	@Test
	public void testNodeOrder() {
		this.node.addReadVariable(this.readVar1);
		this.node.addWrittenVariable(this.writeVar1);

		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
		assertEquals(2, constraint.getOrder());
	}

	@Test
	public void testDuplicatedVar() {
		this.node.addReadVariable(this.readVar1);
		this.node.addReadVariable(this.readVar1);
		this.node.addWrittenVariable(this.writeVar1);

		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);

		assertEquals(3, constraint.getBitLength());
		assertEquals(3, constraint.getPredicateCount());

		List<String> predIDs = constraint.getInvolvedPredIDs();
		assertEquals(this.readVar1.getVarID(), predIDs.get(0));
		assertEquals(this.writeVar1.getVarID(), predIDs.get(1));
		assertEquals(this.statementID, predIDs.get(2));
	}
	
	@Test
	public void test1R1W0P() {
		this.node.addReadVariable(this.readVar1);
		this.node.addWrittenVariable(this.writeVar1);
		
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
		
		// Test bit size
		assertEquals(3, constraint.getBitLength());
		
		// Test conclusion indexes
		assertEquals(2, constraint.getConclusionIdx());
		
		// Test count
		assertEquals(3, constraint.getPredicateCount());
		
		// Test probability
		double[] expected = new double[] {0.05, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95};
		
		final int totalLen = constraint.getPredicateCount();
		final int maxCase = 1 << totalLen;
		
		for (int caseNo=0; caseNo<maxCase; caseNo++) {
			assertEquals(expected[caseNo], constraint.getProbability(caseNo), 0.01);
		}
	}
	
	@Test
	public void test2R1W0P() {
		this.node.addReadVariable(this.readVar1);
		this.node.addReadVariable(this.readVar2);
		this.node.addWrittenVariable(this.writeVar1);
		
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
		
		// Test bit size
		assertEquals(4, constraint.getBitLength());
		
		// Test conclusion indexes
		assertEquals(3, constraint.getConclusionIdx());
		
		// Test count
		assertEquals(4, constraint.getPredicateCount());
		
		// Test probability
		double[] expected = new double[] {0.05, 0.95, 0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95};
		
		final int totalLen = constraint.getPredicateCount();
		final int maxCase = 1 << totalLen;
		
		for (int caseNo=0; caseNo<maxCase; caseNo++) {
			assertEquals(expected[caseNo], constraint.getProbability(caseNo), 0.01);
		}
	}
	
	@Test
	public void test2R2W0P() {
		this.node.addReadVariable(this.readVar1);
		this.node.addReadVariable(this.readVar2);
		this.node.addWrittenVariable(this.writeVar1);
		this.node.addWrittenVariable(this.writeVar2);
		
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
		
		// Test bit size
		assertEquals(5, constraint.getBitLength());
		
		// Test conclusion indexes
		assertEquals(4, constraint.getConclusionIdx());
		
		// Test count
		assertEquals(5, constraint.getPredicateCount());
		
		// Test probability
		double[] expected = new double[] {0.05, 0.95, 0.05, 0.95, 0.05, 0.95, 0.95, 0.95, 
										  0.05, 0.95, 0.05, 0.95, 0.05, 0.95, 0.95, 0.95, 
										  0.05, 0.95, 0.05, 0.95, 0.05, 0.95, 0.95, 0.95,
										  0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95,};
		
		final int totalLen = constraint.getPredicateCount();
		final int maxCase = 1 << totalLen;
		
		for (int caseNo=0; caseNo<maxCase; caseNo++) {
			assertEquals(expected[caseNo], constraint.getProbability(caseNo), 0.01);
		}
	}
	
	@Test
	public void test1R1W1P() {
		this.node.addReadVariable(this.readVar1);
		this.node.addWrittenVariable(this.writeVar1);
		this.node.setControlDominator(controlDom);
		
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
		
		// Test bit size
		assertEquals(4, constraint.getBitLength());
		
		// Test conclusion indexes
		assertEquals(3, constraint.getConclusionIdx());
		
		// Test count
		assertEquals(4, constraint.getPredicateCount());
		
		// Test probability
		double[] expected = new double[] {0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.95, 0.95,
										  0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95};
		
		final int totalLen = constraint.getPredicateCount();
		final int maxCase = 1 << totalLen;
		
		for (int caseNo=0; caseNo<maxCase; caseNo++) {
			assertEquals(expected[caseNo], constraint.getProbability(caseNo), 0.01);
		}
	}
	
	@Test
	public void test2R1W1P() {
		this.node.addReadVariable(this.readVar1);
		this.node.addReadVariable(this.readVar2);
		this.node.addWrittenVariable(this.writeVar1);
		this.node.setControlDominator(this.controlDom);
		
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
		
		// Test bit size
		assertEquals(5, constraint.getBitLength());
		
		// Test conclusion indexes
		assertEquals(4, constraint.getConclusionIdx());
		
		// Test count
		assertEquals(5, constraint.getPredicateCount());
		
		// Test probability
		double[] expected = new double[] {0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.95, 0.95,
										  0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.95, 0.95,
										  0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.95, 0.95,
										  0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95};
		
		final int totalLen = constraint.getPredicateCount();
		final int maxCase = 1 << totalLen;
		
		for (int caseNo=0; caseNo<maxCase; caseNo++) {
			assertEquals(expected[caseNo], constraint.getProbability(caseNo), 0.01);
		}
	}
	
	@Test
	public void test2R2W1P() {
		this.node.addReadVariable(this.readVar1);
		this.node.addReadVariable(this.readVar2);
		this.node.addWrittenVariable(this.writeVar1);
		this.node.addWrittenVariable(this.writeVar2);
		this.node.setControlDominator(this.controlDom);
		
		Constraint constraint = new StatementConstraintA2(this.node, this.propagationProbability);
		
		// Test bit size
		assertEquals(6, constraint.getBitLength());
		
		// Test conclusion indexes
		assertEquals(5, constraint.getConclusionIdx());
		
		// Test count
		assertEquals(6, constraint.getPredicateCount());
		
		// Test probability
		double[] expected = new double[] {0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.05, 0.95,
										  0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.95, 0.95,
										  0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.05, 0.95,
										  0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.95, 0.95,
										  0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.05, 0.95,
										  0.95, 0.95, 0.05, 0.95, 0.95, 0.95, 0.95, 0.95,
										  0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95,
										  0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95};
		
		
		final int totalLen = constraint.getPredicateCount();
		final int maxCase = 1 << totalLen;
		
		for (int caseNo=0; caseNo<maxCase; caseNo++) {
			assertEquals(expected[caseNo], constraint.getProbability(caseNo), 0.01);
		}
	}

}

package microbat.trace;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import microbat.codeanalysis.runtime.InstrumentationExecutor;
import microbat.codeanalysis.runtime.StepLimitException;
import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.ArrayValue;
import microbat.model.value.VarValue;
import microbat.util.Settings;
import sav.strategies.dto.AppJavaClassPath;

public class OperationsInTraceTest {
	@BeforeClass
	public static void beforeClassSetUp() {
		TraceTestHelper.checkEnvVars();
		setupSettings();
	}
	
	private static void setupSettings() {
		TraceTestHelper.setupSettings();
		Settings.launchClass = "sample0.junit4.OperationTest";
	}
	
	@Test
	public void testAssigment() throws StepLimitException {
		Settings.testMethod = "testRunAssignment";
		AppJavaClassPath appClassPath = TraceTestHelper.constructClassPaths(String.join(File.separator, System.getProperty("user.dir"), "src", "test", "samples", "sample0"));

		InstrumentationExecutor executor = new InstrumentationExecutor(appClassPath,
				".", "trace", new ArrayList<>(), new ArrayList<>());
		final RunningInfo result = executor.run();
		Trace mainTrace = result.getMainTrace();
		assertEquals(10, result.getMainTrace().size());
		
		// check for 0 written to a.
		TraceNode writtenToANode = mainTrace.getTraceNode(6);
		List<VarValue> writtenVariables = writtenToANode.getWrittenVariables();
		VarValue writtenToA = writtenVariables.get(0);
		assertEquals("0", writtenToA.getStringValue());
		
		// check for a written to b.
		TraceNode writtenToBNode = mainTrace.getTraceNode(7);
		VarValue writtenToB = writtenToBNode.getWrittenVariables().get(0);
		assertEquals("0", writtenToB.getStringValue());
		VarValue readA = writtenToBNode.getReadVariables().get(0);
		assertEquals("0", readA.getStringValue());
	}
	
	@Test
	public void testWhileLoop() throws StepLimitException {
		Settings.testMethod = "testRunWhileLoop";
		AppJavaClassPath appClassPath = TraceTestHelper.constructClassPaths(String.join(File.separator, System.getProperty("user.dir"), "src", "test", "samples", "sample0"));

		InstrumentationExecutor executor = new InstrumentationExecutor(appClassPath,
				".", "trace", new ArrayList<>(), new ArrayList<>());
		final RunningInfo result = executor.run();
		Trace mainTrace = result.getMainTrace();
		assertEquals(30, result.getMainTrace().size());

		// check read and written variables in loop body
		TraceNode writtenToBNode = mainTrace.getTraceNode(12);
		List<VarValue> writtenVariables = writtenToBNode.getWrittenVariables();
		VarValue writtenToB = writtenVariables.get(0);
		assertEquals("0", writtenToB.getStringValue());
		
		TraceNode incrementANode = mainTrace.getTraceNode(13);
		VarValue readA = incrementANode.getReadVariables().get(0);
		assertEquals("1", readA.getStringValue());
		VarValue writtenToA = incrementANode.getWrittenVariables().get(0);
		assertEquals("2", writtenToA.getStringValue());
		
		// check for failure in loop condition
		TraceNode whileLoopBreakNode = mainTrace.getTraceNode(27);
		VarValue readAInCondition = whileLoopBreakNode.getReadVariables().get(0);
		assertEquals("5", readAInCondition.getStringValue());
		assertNotEquals(25, whileLoopBreakNode.getStepOverNext().getLineNumber());
	}
	
	@Test
	public void testForLoop() throws StepLimitException {
		Settings.testMethod = "testRunForLoop";
		AppJavaClassPath appClassPath = TraceTestHelper.constructClassPaths(String.join(File.separator, System.getProperty("user.dir"), "src", "test", "samples", "sample0"));

		InstrumentationExecutor executor = new InstrumentationExecutor(appClassPath,
				".", "trace", new ArrayList<>(), new ArrayList<>());
		final RunningInfo result = executor.run();
		Trace mainTrace = result.getMainTrace();
		assertEquals(20, result.getMainTrace().size());
		
		// Check initialised var in loop
		TraceNode loopInitNode = mainTrace.getTraceNode(7);
		VarValue initI = loopInitNode.getWrittenVariables().get(0);
		assertEquals("0", initI.getStringValue());
		VarValue readI = loopInitNode.getReadVariables().get(0);
		assertEquals("0", readI.getStringValue());
		
		// check loop body
		TraceNode incrANode = mainTrace.getTraceNode(14);
		VarValue writtenA = incrANode.getWrittenVariables().get(0);
		assertEquals("4", writtenA.getStringValue());
		VarValue readA = incrANode.getReadVariables().get(0);
		assertEquals("3", readA.getStringValue());
		
		// check loop exit
		TraceNode loopExitNode = mainTrace.getTraceNode(17);
		VarValue writtenI = loopExitNode.getWrittenVariables().get(0);
		assertEquals("5", writtenI.getStringValue());
		VarValue lastReadI = loopExitNode.getReadVariables().get(0);
		assertEquals("5", lastReadI.getStringValue());
	}
	
	@Test
	public void testForEachLoop() throws StepLimitException {
		Settings.testMethod = "testRunForEachLoop";
		AppJavaClassPath appClassPath = TraceTestHelper.constructClassPaths(String.join(File.separator, System.getProperty("user.dir"), "src", "test", "samples", "sample0"));

		InstrumentationExecutor executor = new InstrumentationExecutor(appClassPath,
				".", "trace", new ArrayList<>(), new ArrayList<>());
		final RunningInfo result = executor.run();
		Trace mainTrace = result.getMainTrace();
		assertEquals(15, result.getMainTrace().size());
		
		// check forEach loop initialization. (write to num, read arr)
		TraceNode forEachLoopInit = mainTrace.getTraceNode(8);
		VarValue writtenToNum = null;
		for (VarValue varVal : forEachLoopInit.getWrittenVariables()) {
			if (varVal.getVariable().getSimpleName().equals("num")) {
				writtenToNum = varVal;
				break;
			}
		}
		assertEquals("0", writtenToNum.getStringValue());
		
		ArrayValue readArr = null;
		for (VarValue varVal : forEachLoopInit.getReadVariables()) {
			if (varVal.getVariable().getSimpleName().equals("arr")) {
				readArr = (ArrayValue)varVal;
				break;
			}
		}
		
		assertEquals(2, readArr.getChildren().size());
		
		
		// check forEach loop body.
		TraceNode forEachLoopBody = mainTrace.getTraceNode(11);
		VarValue writtenA = forEachLoopBody.getWrittenVariables().get(0);
		assertEquals("1", writtenA.getStringValue());
		
		for (VarValue readVar : forEachLoopBody.getReadVariables()) {
			if (readVar.getVarName().equals("a")) {
				assertEquals("0", readVar.getStringValue());
			} else {
				assertEquals("1", readVar.getStringValue());
			}
		}
		
		// check forEach loop exit.
		TraceNode loopExitNode = mainTrace.getTraceNode(12);
		assertEquals(3, loopExitNode.getReadVariables().size());
		assertEquals(1, loopExitNode.getWrittenVariables().size());
	}
	
	@Test
	public void testCallAnotherMethod() throws StepLimitException {
		Settings.testMethod = "testCallAnotherMethod";
		AppJavaClassPath appClassPath = TraceTestHelper.constructClassPaths(String.join(File.separator, System.getProperty("user.dir"), "src", "test", "samples", "sample0"));

		InstrumentationExecutor executor = new InstrumentationExecutor(appClassPath,
				".", "trace", new ArrayList<>(), new ArrayList<>());
		final RunningInfo result = executor.run();
		Trace mainTrace = result.getMainTrace();
		assertEquals(12, result.getMainTrace().size());
		
		// init other object
		TraceNode otherObjInit = mainTrace.getTraceNode(8);
		assertEquals(1, otherObjInit.getWrittenVariables().size());
		VarValue otherObj = otherObjInit.getWrittenVariables().get(0);
		assertEquals("sample1", otherObj.getVarName());
		
		// method call, 9
		TraceNode methodCallNode = mainTrace.getTraceNode(9);
		assertEquals(1, methodCallNode.getWrittenVariables().size());
		assertEquals(1, methodCallNode.getReadVariables().size());
		VarValue writtenA = methodCallNode.getWrittenVariables().get(0);
		assertEquals("1", writtenA.getStringValue());
		
		// method return 11, 12
		TraceNode methodReturnNode = mainTrace.getTraceNode(11);
		assertEquals(1, methodReturnNode.getReadVariables().size());
		assertEquals(1, methodReturnNode.getWrittenVariables().size());
		assertTrue(methodReturnNode.getWrittenVariables().get(0).getVarName().startsWith("return from"));
		VarValue readA = methodReturnNode.getReadVariables().get(0);
		assertEquals("2", readA.getStringValue());
		assertEquals("a", readA.getVarName());
		

		TraceNode methodAftReturnNode = mainTrace.getTraceNode(12);
		assertEquals(2, methodAftReturnNode.getReadVariables().size());
		assertEquals(1, methodAftReturnNode.getWrittenVariables().size());
		
		for (VarValue readVal : methodAftReturnNode.getReadVariables()) {
			if (readVal.getVarName().startsWith("return from")) {
				assertEquals("2", readVal.getStringValue());
			}
		}
		assertTrue(methodAftReturnNode.getWrittenVariables().get(0).getVarName().startsWith("return from"));
	}
}

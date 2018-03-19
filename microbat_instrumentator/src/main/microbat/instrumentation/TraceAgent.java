package microbat.instrumentation;

import microbat.instrumentation.filter.FilterChecker;
import microbat.instrumentation.instr.TraceTransformer;
import microbat.instrumentation.output.RunningInfo;
import microbat.instrumentation.output.TraceOutputWriter;
import microbat.instrumentation.output.tcp.TcpConnector;
import microbat.instrumentation.runtime.ExecutionTracer;
import microbat.instrumentation.runtime.IExecutionTracer;
import microbat.model.trace.StepVariableRelationEntry;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.model.variable.Variable;
import microbat.sql.TraceRecorder;
import sav.common.core.utils.StopTimer;
import sav.strategies.dto.AppJavaClassPath;

public class TraceAgent implements IAgent {
	private AgentParams agentParams;
	private StopTimer timer;
	
	public TraceAgent(AgentParams agentParams) {
		this.agentParams = agentParams;
	}

	public void startup() {
		timer = new StopTimer("Trace Construction");
		timer.newPoint("Execution");
		/* init filter */
		AppJavaClassPath appPath = agentParams.initAppClassPath();
		FilterChecker.setup(appPath, agentParams.getIncludesExpression(), agentParams.getExcludesExpression());
		ExecutionTracer.appJavaClassPath = appPath;
		ExecutionTracer.variableLayer = agentParams.getVariableLayer();
		ExecutionTracer.stepLimit = agentParams.getStepLimit();
		if (agentParams.getExpectedSteps() > 0) {
			ExecutionTracer.expectedSteps = agentParams.getExpectedSteps();
		}
	}

	public void shutdown() throws Exception {
		ExecutionTracer.shutdown();
		/* collect trace & store */
		AgentLogger.debug("Building trace dependencies ...");
		timer.newPoint("Building trace dependencies");
		IExecutionTracer tracer = ExecutionTracer.getMainThreadStore();
	
		Trace trace = ((ExecutionTracer) tracer).getTrace();
		
//		List<ClassLocation> locs = new ArrayList<>();
//		for (TraceNode node : trace.getExecutionList()) {
//			BreakPoint bkp = node.getBreakPoint();
//			locs.add(new ClassLocation(bkp.getClassCanonicalName(), bkp.getMethodSign(), bkp.getLineNumber()));
//		}
//		FileUtils.writeFile("E:/lyly/WorkingFolder/step_run.txt", StringUtils.join(locs, "\n"));
//		AgentLogger.debug("Trace size = " + trace.getExecutionList().size());
		
		createVirtualDataRelation(trace);
		trace.constructControlDomianceRelation();
//		trace.constructLoopParentRelation();
		timer.newPoint("Saving trace");
		writeOutput(trace);
		AgentLogger.debug(timer.getResultString());
	}

	private void writeOutput(Trace trace) throws Exception {
		AgentLogger.debug("Saving trace...");
		if (agentParams.getDumpFile() != null) {
			RunningInfo result = new RunningInfo();
			result.setProgramMsg(Agent.getProgramMsg());
			result.setTrace(trace);
			result.setCollectedSteps(trace.getExecutionList().size());
			result.setExpectedSteps(agentParams.getExpectedSteps());
			result.saveToFile(agentParams.getDumpFile(), false);
		} else if (agentParams.getTcpPort() != -1) {
			TcpConnector tcpConnector = new TcpConnector(agentParams.getTcpPort());
			TraceOutputWriter traceWriter = tcpConnector.connect();
			traceWriter.writeString(Agent.getProgramMsg());
			traceWriter.writeTrace(trace);
			traceWriter.flush();
			Thread.sleep(10000l);
			tcpConnector.close();
		} else {
			TraceRecorder traceRecorder = new TraceRecorder();
			traceRecorder.storeTrace(trace );
		}
		AgentLogger.debug("Trace saved.");
	}
	
	private void createVirtualDataRelation(Trace trace) {
		for(int i=0; i<trace.size(); i++){
			int order = i+1;
			TraceNode currentNode = trace.getTraceNode(order);
			if(order<trace.size()){
				TraceNode nextNode = trace.getTraceNode(order+1);
				currentNode.setStepInNext(nextNode);
				nextNode.setStepInPrevious(currentNode);
			}
			else if(order==trace.size()){
				if(order>1){
					TraceNode prevNode = trace.getTraceNode(order-1);
					currentNode.setStepInPrevious(prevNode);					
				}
			}
			
			TraceNode previousStepOver = currentNode.getStepOverPrevious();
			if(previousStepOver!=null && previousStepOver.getBreakPoint().equals(currentNode.getBreakPoint())){
				for(VarValue readVar: previousStepOver.getReadVariables()){
					if(!currentNode.getReadVariables().contains(readVar)){
						currentNode.addReadVariable(readVar);
					}
				}
			}
			
			if(currentNode.getInvocationParent()!=null && !currentNode.getPassParameters().isEmpty()){
				TraceNode invocationParent = currentNode.getInvocationParent();
				TraceNode firstChild = invocationParent.getInvocationChildren().get(0);
				if(firstChild.getOrder()==currentNode.getOrder()){
					for(VarValue value: currentNode.getPassParameters()){
						String varID = value.getVarID();
						StepVariableRelationEntry entry = trace.getStepVariableTable().get(varID);
						if(entry==null){
							entry = new StepVariableRelationEntry(varID);
						}
						entry.addProducer(invocationParent);
						trace.getStepVariableTable().put(varID, entry);
					}
				}
			}
			
			if(currentNode.getInvocationParent()!=null && !currentNode.getReturnedVariables().isEmpty()){
				TraceNode invocationParent = currentNode.getInvocationParent();
				TraceNode stepOverNext = invocationParent.getStepOverNext();
				System.currentTimeMillis();
				if(stepOverNext!=null){
					for(VarValue value: currentNode.getReturnedVariables()){
						currentNode.addWrittenVariable(value);
						stepOverNext.addReadVariable(value);
						String varID = value.getVarID();
						String definingOrder = trace.findDefiningNodeOrder(Variable.WRITTEN, currentNode, varID, varID);
						varID = varID+":"+definingOrder;
						value.setVarID(varID);
						StepVariableRelationEntry entry = trace.getStepVariableTable().get(varID);
						if(entry==null){
							entry = new StepVariableRelationEntry(varID);
						}
						entry.addProducer(currentNode);
						entry.addConsumer(stepOverNext);
						trace.getStepVariableTable().put(varID, entry);
					}
				}
			}
		}
	}
	
	public AgentParams getAgentParams() {
		return agentParams;
	}

	@Override
	public void startTest(String junitClass, String junitMethod) {
		ExecutionTracer._start();
		ExecutionTracer.appJavaClassPath.setOptionalTestClass(junitClass);
		ExecutionTracer.appJavaClassPath.setOptionalTestMethod(junitMethod);
	}

	@Override
	public void finishTest(String junitClass, String junitMethod) {
		ExecutionTracer.shutdown();
	}

	@Override
	public TraceTransformer getTransformer() {
		return new TraceTransformer(agentParams);
	}

}
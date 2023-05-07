package microbat.handler;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;

import microbat.bytecode.ByteCode;
import microbat.bytecode.ByteCodeList;
import microbat.bytecode.OpcodeType;
import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.probability.BP.BeliefPropagation;
import microbat.util.JavaUtil;
import microbat.views.MicroBatViews;
import microbat.views.TraceView;
import microbat.probability.SPP.vectorization.vector.EnvironmentVector;
import microbat.probability.SPP.vectorization.vector.FunctionMismatchException;
import microbat.probability.SPP.vectorization.vector.FunctionVector;
import microbat.probability.SPP.vectorization.vector.NodeVector;
import microbat.probability.SPP.vectorization.vector.VariableVector;
import microbat.model.BreakPoint;

import java.util.ArrayList;
import microbat.probability.SPP.vectorization.vector.*;
public class TestHandler extends AbstractHandler {
	
	TraceView traceView = null;
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		JavaUtil.sourceFile2CUMap.clear();
		Job job = new Job("Testing Tregression") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				setup();
				
		        Trace trace = traceView.getTrace();
		        Map<BreakPoint, Integer> repeatedCounts = new HashMap<>();
		        
		        for (TraceNode node : trace.getExecutionList()) {
		        	final BreakPoint bkp = node.getBreakPoint();
		        	if (repeatedCounts.containsKey(bkp)) {
		        		repeatedCounts.put(bkp, repeatedCounts.get(bkp)+1);
		        	} else {
		        		repeatedCounts.put(bkp, 0);
		        	}
		        	node.repeatedCount = repeatedCounts.get(bkp);
		        }
		        
		        for (TraceNode node: trace.getExecutionList()) {
		        	System.out.println(node.getOrder() + "------------------------------------------------");
		        	System.out.println("node.repeatedCount: " + node.repeatedCount);
		        }
		        
				return Status.OK_STATUS;
			}
			
		};
		job.schedule();
		return null;
	}
	
	private void setup() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				traceView = MicroBatViews.getTraceView();
			}
		});
	}

}

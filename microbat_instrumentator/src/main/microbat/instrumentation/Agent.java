package microbat.instrumentation;

import java.lang.instrument.ClassFileTransformer;

import microbat.instrumentation.filter.FilterChecker;

/**
 * @author LLT
 *  the Agent proxy
 * (Real agent would be TraceAgent & PrecheckAgent)
 */
public class Agent implements IAgent {
	private static IAgent agent;
	private static String programMsg = "";
	private volatile static boolean shutdowned = false;
	private static int numberOfThread = 1;
	
	public Agent(AgentParams agentParams) {
		if (agentParams.isPrecheck()) {
			agent = new PrecheckAgent(agentParams);
		} else {
			agent = new TraceAgent(agentParams);
		}
		AgentLogger.setup(agentParams.getLogTypes());
	}

	public void startup() {
		agent.startup();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Agent.stop();
			}
		});
	}

	public static void _exitProgram(String programMsg) {
		Agent.programMsg = programMsg;
		stop();
		Runtime.getRuntime().exit(1); // force program to exit to avoid getting stuck by background running threads.
	}
	
	public static String getProgramMsg() {
		return programMsg;
	}
	
	public static synchronized void stop() {
		try {
			if (!shutdowned) {
				agent.shutdown();
			}
			shutdowned = true;
		} catch (Throwable e) {
			AgentLogger.error(e);
		}
	}
	
	public void shutdown() throws Exception {
		try {
			agent.shutdown();
		} catch (Throwable e) {
			AgentLogger.error(e);
		}
	}
	
	public static void _startTest(String junitClass, String junitMethod) {
		try {
			agent.startTest(junitClass, junitMethod);
		} catch (Throwable e) {
			AgentLogger.error(e);
		}
	}
	
	public static void _finishTest(String junitClass, String junitMethod) {
		try {
			agent.finishTest(junitClass, junitMethod);
		} catch (Throwable e) {
			AgentLogger.error(e);
		}
	}

	public static String extractJarPath() {
		return null;
	}
	
	@Override
	public void startTest(String junitClass, String junitMethod) {
		try {
			agent.startTest(junitClass, junitMethod);
		} catch (Throwable e) {
			AgentLogger.error(e);
		}
	}

	@Override
	public void finishTest(String junitClass, String junitMethod) {
		try {
			agent.finishTest(junitClass, junitMethod);
		} catch (Throwable e) {
			AgentLogger.error(e);
		}
	}
	
	public static void _onStartThread() {
		if (!shutdowned) {
//			boolean isCalledFromApp = false;
//			StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
//			boolean threadStartReached = false;
//			for (StackTraceElement ste : stackTrace) {
//				if ("java.lang.Thread".equals(ste.getClassName()) 
//						&& "start".equals(ste.getMethodName())) {
//					threadStartReached = true;
//				} else if (threadStartReached) {
//					isCalledFromApp = FilterChecker.isAppClazz(ste.getClassName());
//					break;
//				}
//			}
//			if (isCalledFromApp) {
//				numberOfThread++;
//			}
			numberOfThread++;
		}
	}
	
	public static int getNumberOfThread() {
		return numberOfThread;
	}

	@Override
	public ClassFileTransformer getTransformer() {
		return agent.getTransformer();
	}

	@Override
	public void setTransformableClasses(Class<?>[] retransformableClasses) {
		agent.setTransformableClasses(retransformableClasses);
	}
}

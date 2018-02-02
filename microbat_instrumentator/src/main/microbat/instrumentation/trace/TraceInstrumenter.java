package microbat.instrumentation.trace;

import java.util.ArrayList;
import java.util.List;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LineNumberTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.AALOAD;
import org.apache.bcel.generic.AASTORE;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.ANEWARRAY;
import org.apache.bcel.generic.ASTORE;
import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.DUP2;
import org.apache.bcel.generic.DUP2_X1;
import org.apache.bcel.generic.DUP2_X2;
import org.apache.bcel.generic.DUP_X1;
import org.apache.bcel.generic.DUP_X2;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.GETFIELD;
import org.apache.bcel.generic.GETSTATIC;
import org.apache.bcel.generic.IINC;
import org.apache.bcel.generic.ILOAD;
import org.apache.bcel.generic.INVOKESTATIC;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.LineNumberGen;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.POP;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.PUTSTATIC;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.SWAP;
import org.apache.bcel.generic.TargetLostException;
import org.apache.bcel.generic.Type;

import microbat.instrumentation.trace.data.ExecutionTracer;
import microbat.instrumentation.trace.data.TraceUtils;
import microbat.instrumentation.trace.model.ArrayInstructionInfo;
import microbat.instrumentation.trace.model.FieldInstructionInfo;
import microbat.instrumentation.trace.model.LineInstructionInfo;
import microbat.instrumentation.trace.model.LocalVarInstructionInfo;
import microbat.instrumentation.trace.model.RWInstructionInfo;

public class TraceInstrumenter {
	private static final String TRACER_VAR_NAME = "$tracer"; // local var
	private static final String TEMP_VAR_NAME = "$tempVar"; // local var
	private BasicTypeSupporter basicTypeSupporter = new BasicTypeSupporter();
	
	protected byte[] instrument(String className, byte[] classfileBuffer) throws Exception {
		ClassParser cp = new ClassParser(new java.io.ByteArrayInputStream(classfileBuffer), className);
		JavaClass jc = cp.parse();
		// First, make sure we have to instrument this class:
		if (!jc.isClass()) // could be an interface
			return null;
		ClassGen classGen = new ClassGen(jc);
		ConstantPoolGen constPool = classGen.getConstantPool();
		setMethodIndex(constPool);
		JavaClass newJC = null;
		for (Method method : jc.getMethods()) {
			if (method.isNative() || method.isAbstract() || method.getCode() == null) {
				continue; // Only instrument methods with code in them!
			}
			boolean changed = false;
			MethodGen methodGen = new MethodGen(method, className, constPool);
			changed = instrumentMethod(classGen, constPool, methodGen, method);
			if (changed) {
				// All changes made, so finish off the method:
				methodGen.getInstructionList().setPositions();
				methodGen.setMaxStack();
				methodGen.setMaxLocals();
				classGen.replaceMethod(method, methodGen.getMethod());
			}
			newJC = classGen.getJavaClass();
			newJC.setConstantPool(constPool.getFinalConstantPool());
		}
		System.out.println("===============================================");
		dumpClass(jc);
		System.out.println("=================NEW JAVACLASS==============================");
		dumpClass(newJC);
		if (newJC != null) {
			return newJC.getBytes();
		}
		return null;
	}
	
	private void dumpClass(JavaClass jc) {
		ClassGen cg = new ClassGen(jc);
		ConstantPoolGen cpg = cg.getConstantPool();
		System.out.println(jc.toString());
		for (Method meth : jc.getMethods()) {
			MethodGen mg = new MethodGen(meth, jc.getClassName(), cpg);
			System.out.println(mg.getName());
			System.out.println("==========================================");
			for (InstructionHandle ih : mg.getInstructionList()) {
				System.out.println(ih);
			}
		}
	}

	private boolean instrumentMethod(ClassGen classGen, ConstantPoolGen constPool, MethodGen methodGen, Method method) {
		LocalVariableGen tracerVar = injectCodeInitTracer(methodGen);
		if (tracerVar == null) {
			// empty method
			return false;
		}
		InstructionList insnList = methodGen.getInstructionList();
		List<LineInstructionInfo> lineInsnInfos = new ArrayList<>();
	
		LineNumberTable lineNumberTable = 	method.getLineNumberTable();//methodGen.getLineNumberTable(constPool);
		for (LineNumberGen lineGen : methodGen.getLineNumbers()) {
			lineInsnInfos.add(new LineInstructionInfo(method.getLocalVariableTable(), constPool, lineNumberTable,
					lineGen, insnList));
		}
		for (LineInstructionInfo lineInfo : lineInsnInfos) {
			/* instrument RW instructions */
			List<RWInstructionInfo> rwInsns = lineInfo.getRWInstructions();
			if (rwInsns.isEmpty()) {
				injectCodeTracerHitLine(insnList, constPool, tracerVar, lineInfo.getLine(), lineInfo.getLineNumberInsn());
			} else {
				for (RWInstructionInfo rwInsnInfo : rwInsns) {
					InstructionList newInsns = null;
					if (rwInsnInfo instanceof FieldInstructionInfo) {
						newInsns = getInjectCodeTracerRWriteField(constPool, tracerVar, (FieldInstructionInfo)rwInsnInfo);
					} else if (rwInsnInfo instanceof ArrayInstructionInfo) {
						newInsns = getInjectCodeTracerRWriteArray(methodGen, constPool, tracerVar, (ArrayInstructionInfo)rwInsnInfo);
					} else if (rwInsnInfo instanceof LocalVarInstructionInfo) {
						newInsns = getInjectCodeTracerRWLocalVar(constPool, tracerVar, (LocalVarInstructionInfo)rwInsnInfo);
					}
					if ((newInsns != null) && (newInsns.getLength() > 0)) {
						if (rwInsnInfo.isStoreInstruction()) {
							InstructionHandle pos = insnList.insert(rwInsnInfo.getInstructionHandler(), newInsns);
							updateTargeters(rwInsnInfo.getInstructionHandler(), pos);
							newInsns.dispose();
						} else {
							InstructionHandle pos = insnList.insert(rwInsnInfo.getInstructionHandler(), newInsns);
							updateTargeters(rwInsnInfo.getInstructionHandler(), pos);
							try {
								insnList.delete(rwInsnInfo.getInstructionHandler());
								updateTargeters(rwInsnInfo.getInstructionHandler(), pos);
							} catch (TargetLostException e) {
								e.printStackTrace();
							}
							newInsns.dispose();
						}
					}
				}
			}
			int line = lineInfo.getLine();
			/* instrument Invocation instructions */
			InstructionFactory instructionFactory = new InstructionFactory(classGen, constPool);
			for (InstructionHandle insn : lineInfo.getInvokeInstructions()) {
				injectCodeTracerInvokeMethod(methodGen, insnList, constPool, instructionFactory, tracerVar, insn, line);
			}
			/* instrument Return instructions */
			for (InstructionHandle insn : lineInfo.getReturnInsns()) {
				injectCodeTracerReturn(insnList, constPool, tracerVar, insn, line);
			}
			lineInfo.dispose();
		}
		return true;
	}
	
	private void injectCodeTracerReturn(InstructionList insnList, ConstantPoolGen constPool, LocalVariableGen tracerVar,
			InstructionHandle insnHandler, int line) {
		InstructionList newInsns = new InstructionList();
		ReturnInstruction insn = (ReturnInstruction) insnHandler.getInstruction();
		/* on stack: value */
		Type type = insn.getType();
		if (type.getSize() == 1) {
			newInsns.append(new DUP()); // val, val
			newInsns.append(new ALOAD(tracerVar.getIndex())); // val, val, tracer
			newInsns.append(new SWAP()); // val, tracer, val
		} else {
			newInsns.append(new DUP2()); // val*, val*
			newInsns.append(new ALOAD(tracerVar.getIndex())); // val*, val*, tracer
			newInsns.append(new DUP_X2()); // val*, tracer, val*, tracer
			newInsns.append(new POP()); // val*, tracer, val*
		}
		newInsns.append(new PUSH(constPool, type.getSignature())); // val*, tracer, val*, returnGeneralType
		newInsns.append(new PUSH(constPool, line)); // val*, tracer, val*, returnGeneralType, line
		// val
		InstructionHandle pos = insnList.insert(insn, newInsns);
		updateTargeters(insnHandler, pos);
		newInsns.dispose();
	}

	private void injectCodeTracerInvokeMethod(MethodGen methodGen, InstructionList insnList, ConstantPoolGen constPool,
			InstructionFactory instructionFactory, LocalVariableGen tracerVar, InstructionHandle insnHandler, int line) {
		InstructionList newInsns = new InstructionList();
		InvokeInstruction insn = (InvokeInstruction) insnHandler.getInstruction();
		int tracerMethodIdx = executionTracer_hitInvoke_idx;
		boolean isInvokeStatic = insn instanceof INVOKESTATIC;
		if (isInvokeStatic) {
			tracerMethodIdx = executionTracer_hitInvokeStatic_idx;
		}
		/* on stack:  objectRef, arg1, arg2, ... */
		String className = insn.getClassName(constPool);
		ReferenceType returnType = insn.getReferenceType(constPool);
		Type[] argTypes = insn.getArgumentTypes(constPool);
		/*
		 * add tempVar to keep args.
		 * Object[] temp = Object[] {arg1, arg2, ...}
		 */
		LocalVariableGen argObjsVar = addTempVar(methodGen, new ArrayType(Type.OBJECT, 1), insnHandler);
		newInsns.append(new PUSH(constPool, argTypes.length));
		newInsns.append(new ANEWARRAY(constPool.addClass(Object.class.getName())));
		newInsns.append(new ASTORE(argObjsVar.getIndex()));
		/* store args */
		for (int i = (argTypes.length - 1); i >= 0; i--) {
			newInsns.append(new ALOAD(argObjsVar.getIndex())); // [objectRef, arg1, arg2, ...] argn, tempVar
			Type argType = argTypes[i];
			/* swap */
			if (argType.getSize() == 1) {
				newInsns.append(new SWAP()); // tempVar, argn
				newInsns.append(new PUSH(constPool, i)); // tempVar, argn, idx
				newInsns.append(new SWAP()); // tempVar, idx, argn
			} else {
				/* swap */
				newInsns.append(new DUP_X2()); 
				newInsns.append(new POP());
				newInsns.append(new PUSH(constPool, i)); // tempVar, argn, idx
				newInsns.append(new DUP_X2()); 
				newInsns.append(new POP()); // tempVar, idx, argn
			}
			if (argType  instanceof BasicType) {
				newInsns.append(new INVOKESTATIC(basicTypeSupporter.getValueOfMethodIdx((BasicType) argType, constPool)));
			}
			newInsns.append(new AASTORE());
		}
		if (!isInvokeStatic) {
			/* duplicate objectRef */
			newInsns.append(new DUP()); // objectRef, objectRef
			newInsns.append(new ALOAD(tracerVar.getIndex()));
			newInsns.append(new SWAP()); // objectRef, tracer, objectRef
		} else {
			newInsns.append(new ALOAD(tracerVar.getIndex())); // tracer
		}
		newInsns.append(new PUSH(constPool, className)); // ([objectRef], objectRef), invokeType
		newInsns.append(new PUSH(constPool, insn.getMethodName(constPool))); // ([objectRef], objectRef), invokeType, methodName
		newInsns.append(new ALOAD(argObjsVar.getIndex())); // ([objectRef], objectRef), invokeType, methodName, params
		newInsns.append(new PUSH(constPool, TraceUtils.encodeArgTypes(argTypes))); // (objectRef), invokeType, methodName, params, paramTypesCode
		newInsns.append(new PUSH(constPool, returnType.getSignature()));  // (objectRef), invokeType, methodName, params, paramTypesCode, returnTypeSign
		newInsns.append(new PUSH(constPool, line));
		newInsns.append(new INVOKEVIRTUAL(tracerMethodIdx)); // 
		/* on stack: (objectRef) */
		/* restore arg values */
		for (int i = 0; i < argTypes.length; i++) {
			Type argType = argTypes[i];
			newInsns.append(new ALOAD(argObjsVar.getIndex()));
			newInsns.append(new PUSH(constPool, i));
			newInsns.append(new AALOAD());
			if (argType instanceof BasicType) {
				newInsns.append(new INVOKESTATIC(
						basicTypeSupporter.getToPrimitiveValueMethodIdx((BasicType) argType, constPool)));
			}
		}
		InstructionHandle pos = insnList.insert(insn, newInsns);
		updateTargeters(insnHandler, pos);
		newInsns.dispose();
	}

	private InstructionList getInjectCodeTracerRWriteField(ConstantPoolGen constPool, LocalVariableGen tracerVar,
			FieldInstructionInfo info) {
		FieldInstruction insn = (FieldInstruction) info.getInstruction();
		if (insn instanceof PUTFIELD) {
			return getInjectCodePutField(constPool, tracerVar, info);
		} else if (insn instanceof PUTSTATIC) {
			return getInjectCodePutStatic(constPool, tracerVar, info);
		} else if (insn instanceof GETFIELD) {
			return getInjectCodeGetField(constPool, tracerVar, info);
		} else if (insn instanceof GETSTATIC) {
			return getInjectCodeGetStatic(constPool, tracerVar, info);
		}
		return null;
	}

	private InstructionList getInjectCodePutField(ConstantPoolGen constPool, LocalVariableGen tracerVar,
			FieldInstructionInfo info) {
		InstructionList newInsns = new InstructionList();
		if (info.isComputationalType1()) {
			newInsns.append(new DUP2()); // Duplicates object and value: [obj, val], obj, val
			newInsns.append(new ALOAD(tracerVar.getIndex())); // [obj, val], obj, val, tracer
			newInsns.append(new DUP_X2()); // [obj, val], tracer, obj, val, tracer
			newInsns.append(new POP()); // [obj, val], tracer, obj, val
		} else { // obj, val*
			/**/
			newInsns.append(new DUP2_X1()); // val*, obj, val*
			newInsns.append(new POP()); // val*, obj
			newInsns.append(new DUP_X2()); // obj, val*, obj
			newInsns.append(new DUP_X2()); // obj, obj, val*, obj
			newInsns.append(new POP()); // obj, obj, val*
			newInsns.append(new DUP2_X1()); // obj, val*, obj, val*
			
			/* swap obj, var* */
			newInsns.append(new DUP2_X1()); // val*, obj, val*
			newInsns.append(new POP()); // val*, obj
			
			/* push tracer */
			newInsns.append(new ALOAD(tracerVar.getIndex())); // val*, obj, tracer
			/* bring tracer to the bottom */
			newInsns.append(new DUP2_X2()); // obj, tracer, val*, obj, tracer
			newInsns.append(new POP()); // obj, tracer, val*, obj
			/* swap obj, var* */
			newInsns.append(new DUP_X2()); // obj, tracer, obj, val*, obj
			newInsns.append(new POP()); // obj, tracer, obj, val*
		}
		Type fieldType = info.getFieldBcType();
		if (fieldType  instanceof BasicType) {
			newInsns.append(new INVOKESTATIC(basicTypeSupporter.getValueOfMethodIdx((BasicType) fieldType, constPool)));
		}
		newInsns.append(new PUSH(constPool, info.getFieldName())); // tracer, obj, val, fieldName
		newInsns.append(new PUSH(constPool, info.getFieldType())); // tracer, obj, val, fieldName, fieldTypeSignature
		newInsns.append(new PUSH(constPool, info.getLine())); // tracer, obj, val, fieldName, fieldTypeSignature, line
		newInsns.append(new INVOKEVIRTUAL(executionTracer_writeField_idx)); // record -> [obj, val] or [obj, val], val
		if (info.isComputationalType2()) {
			newInsns.append(new POP());
		}
		return newInsns;
	}
	
	/**
	 * ex: ldc "strABC" (java.lang.String)
     *      putstatic microbat/instrumentation/trace/testdata/RefVar.staticStr:java.lang.String
	 */
	private InstructionList getInjectCodePutStatic(ConstantPoolGen constPool, LocalVariableGen tracerVar,
			FieldInstructionInfo info) {
		InstructionList newInsns = new InstructionList();
		if (info.getFieldStackSize() == 1) {
			newInsns.append(new DUP()); // val
			newInsns.append(new ALOAD(tracerVar.getIndex())); // val, tracer
			newInsns.append(new SWAP()); // tracer, val
		} else {
			newInsns.append(new DUP2()); // val*
			newInsns.append(new ALOAD(tracerVar.getIndex())); // val*, tracer
			newInsns.append(new DUP_X2()); // tracer, val*, tracer
			newInsns.append(new POP()); // tracer, val*
		}
		Type fieldType = info.getFieldBcType();
		if (fieldType  instanceof BasicType) {
			newInsns.append(new INVOKESTATIC(basicTypeSupporter.getValueOfMethodIdx((BasicType) fieldType, constPool)));
		}
		newInsns.append(new PUSH(constPool, info.getRefType())); // tracer, val*, refType
		newInsns.append(new PUSH(constPool, info.getFieldName())); // tracer, val*, refType, fieldName
		newInsns.append(new PUSH(constPool, info.getFieldType())); // tracer, val*, refType, fieldName, fieldType
		newInsns.append(new PUSH(constPool, info.getLine())); // tracer, val*, refType, fieldName, fieldType, line
		newInsns.append(new INVOKEVIRTUAL(executionTracer_writeStaticField_idx));
		return newInsns;
	}
	
	private InstructionList getInjectCodeGetField(ConstantPoolGen constPool, LocalVariableGen tracerVar,
			FieldInstructionInfo info) {
		InstructionList newInsns = new InstructionList();

		// stack: obj (refValue)
		GETFIELD insn = (GETFIELD) info.getInstruction();
		newInsns.append(new DUP()); // obj, obj
		newInsns.append(new GETFIELD(insn.getIndex())); // obj, val (*)
		if (info.isComputationalType1()) {
			newInsns.append(new DUP_X1()); // [val], obj, val
			newInsns.append(new ALOAD(tracerVar.getIndex())); // [val], obj, val, tracer
			newInsns.append(new DUP_X2()); // [val], tracer, obj, val, tracer
			newInsns.append(new POP()); // [val], tracer, obj, val
		} else {
			newInsns.append(new DUP2_X1()); // [val*], obj, val*
			/* swap */
			newInsns.append(new DUP2_X1()); //  [val*], val*, obj, val*
			newInsns.append(new POP()); // [val*], val*, obj
			
			/* push tracer */
			newInsns.append(new ALOAD(tracerVar.getIndex())); // val*, obj, tracer
			/* bring tracer to the bottom */
			newInsns.append(new DUP2_X2()); // obj, tracer, val*, obj, tracer
			newInsns.append(new POP()); // obj, tracer, val*, obj
			/* swap obj, var* */
			newInsns.append(new DUP_X2()); // obj, tracer, obj, val*, obj
			newInsns.append(new POP()); // obj, tracer, obj, val* ([val, obj], tracer, obj, val*)
		}
		Type fieldType = info.getFieldBcType();
		if (fieldType  instanceof BasicType) {
			newInsns.append(new INVOKESTATIC(basicTypeSupporter.getValueOfMethodIdx((BasicType) fieldType, constPool)));
		}
		newInsns.append(new PUSH(constPool, info.getFieldName())); //tracer, obj, val, fieldName
		newInsns.append(new PUSH(constPool, info.getFieldType())); // tracer, obj, val, fieldName, fieldTypeSignature 
		newInsns.append(new PUSH(constPool, info.getLine())); // tracer, obj, val, fieldName, fieldTypeSignature, line
		newInsns.append(new INVOKEVIRTUAL(executionTracer_readField_idx)); // record -> [obj, val] or [obj, val], val
		if (info.isComputationalType2()) {
			newInsns.append(new POP());
		}
		return newInsns;
	}
	
	private InstructionList getInjectCodeGetStatic(ConstantPoolGen constPool, LocalVariableGen tracerVar,
			FieldInstructionInfo info) {
		InstructionList newInsns = new InstructionList();
		GETSTATIC insn = (GETSTATIC) info.getInstruction();
		newInsns.append(insn); // val
		/* duplicate field value */
		if (info.isComputationalType1()) {
			newInsns.append(new DUP()); // [val], val
			newInsns.append(new ALOAD(tracerVar.getIndex())); // [val], val, tracer
			newInsns.append(new SWAP()); // [val], tracer, val
		} else {
			newInsns.append(new DUP2()); // val*, val*
			newInsns.append(new ALOAD(tracerVar.getIndex())); // [val*], val*, tracer
			/* swap */
			newInsns.append(new DUP_X2()); // [val*], tracer, val*, tracer
			newInsns.append(new POP()); // [val*], tracer, val*
		}
		Type fieldType = info.getFieldBcType();
		if (fieldType  instanceof BasicType) {
			newInsns.append(new INVOKESTATIC(basicTypeSupporter.getValueOfMethodIdx((BasicType) fieldType, constPool)));
		}
		newInsns.append(new PUSH(constPool, info.getRefType())); // tracer, val*, refType
		newInsns.append(new PUSH(constPool, info.getFieldName())); // tracer, val*, refType, fieldName
		newInsns.append(new PUSH(constPool, info.getFieldType())); // tracer, val*, refType, fieldName, fieldType
		newInsns.append(new PUSH(constPool, info.getLine())); // tracer, val*, refType, fieldName, fieldType, line
		newInsns.append(new INVOKEVIRTUAL(executionTracer_readStaticField_idx));
		return newInsns;
	}
	
	private InstructionList getInjectCodeTracerRWLocalVar(ConstantPoolGen constPool, LocalVariableGen tracerVar,
			LocalVarInstructionInfo insnInfo) {
		LocalVariableInstruction insn = insnInfo.getInstruction();
		// ignore reference to self
		if (insn.getIndex() == 0) {
			return null;
		}
		InstructionList newInsns = new InstructionList();
		Type type = insn.getType(constPool);
		/* for load instruction, we need to execute the load instruction first to get the value of local variable,
		 * then onward, the logic would be the same for both case, load & store  */
		int tracerMethodIdx = executionTracer_writeLocalVar_idx;
		if (!insnInfo.isStoreInstruction()) {
			newInsns.append(insn.copy()); // value
			if (insn instanceof IINC) {
				// store first, then load local var to get value
				newInsns.append(new ILOAD(insn.getIndex())); // value
				tracerMethodIdx = executionTracer_writeLocalVar_idx;
			} else {
				tracerMethodIdx = executionTracer_readLocalVar_idx;
			}
		}
		/* invoke tracer */
		if (insnInfo.isComputationalType1()) {
			newInsns.append(new DUP());		// [value], value, $tracer
			newInsns.append(new ALOAD(tracerVar.getIndex())); // value, $tracer
			newInsns.append(new SWAP()); // $tracer, value
		} else { // stack size = 2
			newInsns.append(new DUP2());
			newInsns.append(new ALOAD(tracerVar.getIndex())); // value*, $tracer
			newInsns.append(new DUP_X2()); // $tracer, value*, $tracer
			newInsns.append(new POP()); // $tracer, value*
		}
		if (type instanceof BasicType) {
			newInsns.append(new INVOKESTATIC(basicTypeSupporter.getValueOfMethodIdx((BasicType) type, constPool)));
		}
		newInsns.append(new PUSH(constPool, insnInfo.getVarName())); //  $tracer, value, varName
		newInsns.append(new PUSH(constPool, insnInfo.getVarType())); // $tracer, value, varName, varType
		newInsns.append(new PUSH(constPool, insnInfo.getLine())); // $tracer, value, varName, line
		newInsns.append(new PUSH(constPool, insn.getIndex())); // $tracer, value, varName, bcLocalVarIdx
		newInsns.append(new PUSH(constPool, insnInfo.getVarScopeStartLine())); // $tracer, value, varName, bcLocalVarIdx, varScopeStartLine
		newInsns.append(new PUSH(constPool, insnInfo.getVarScopeEndLine())); // $tracer, value, varName, bcLocalVarIdx, varScopeStartLine, varScopeEndLine
		newInsns.append(new INVOKEVIRTUAL(tracerMethodIdx));
		return newInsns;
	}
	
	private InstructionList getInjectCodeTracerRWriteArray(MethodGen methodGen, ConstantPoolGen constPool,
			LocalVariableGen tracerVar, ArrayInstructionInfo info) {
		InstructionHandle insnHandler = info.getInstructionHandler();
		ArrayInstruction insn = info.getInstruction();
		InstructionList newInsns = new InstructionList();

		LocalVariableGen arrEleTempVar = addTempVar(methodGen, info.getElementType(), insnHandler);
		int tracerMethodIdx = -1;
		if (info.isStoreInstruction()) {
			tracerMethodIdx = executionTracer_writeArrayElementVar_idx;
			// arrRef, idx, val
			newInsns.append(InstructionFactory.createStore(info.getElementType(), arrEleTempVar.getIndex())); // arrRef, idx
			newInsns.append(new DUP2());  // [arrRef, idx], arrRef, idx
			/* in waiting list: [arrRef, idx] */
		} else {
			tracerMethodIdx = executionTracer_readArrayElementVar_idx;
			// arrRef, idx
			newInsns.append(new DUP2()); // [arrRef, idx], arrRef, idx
			newInsns.append(insn.copy()); // arrRef, idx, val
			newInsns.append(InstructionFactory.createStore(info.getElementType(), arrEleTempVar.getIndex())); // arrRef, idx
			/* waiting list (empty): [] */
		}
		
		/* working on active list: arrRef, idx */
		newInsns.append(new ALOAD(tracerVar.getIndex())); // arrRef, idx, tracer
		newInsns.append(new DUP_X2()); // tracer, arrRef, idx, tracer
		newInsns.append(new POP()); // tracer, arrRef, idx
		newInsns.append(InstructionFactory.createLoad(info.getElementType(), arrEleTempVar.getIndex())); // tracer, arrRef, idx, val
		newInsns.append(new PUSH(constPool, info.getVarType())); // tracer, arrRef, idx, val, eleType
		newInsns.append(new PUSH(constPool, info.getLine())); // tracer, arrRef, idx, val, eleType, line
		newInsns.append(new INVOKEVIRTUAL(tracerMethodIdx)); // 
		/* restore element value for use */
		newInsns.append(InstructionFactory.createLoad(info.getElementType(), arrEleTempVar.getIndex())); // val
		/* at this point :
		 * For Store Instruction case: arrRef, idx, val
		 * For Load Instruction case: val */
		return newInsns;
	}
	
	private int tempVarIdx = 0;
	private LocalVariableGen addTempVar(MethodGen methodGen, Type type, InstructionHandle insnHandler) {
		return methodGen.addLocalVariable(TEMP_VAR_NAME + (++tempVarIdx), type, 
				insnHandler, insnHandler.getNext());
	}
	
	private void injectCodeTracerHitLine(InstructionList insnList, ConstantPoolGen constPool,
			LocalVariableGen tracerVar, int line, InstructionHandle lineNumberInsn) {
		InstructionList newInsns = new InstructionList();
		newInsns.append(new ALOAD(tracerVar.getIndex()));
		newInsns.append(new PUSH(constPool, line));
		newInsns.append(new INVOKEVIRTUAL(executionTracer_hitLine_idx));
		InstructionHandle pos = insnList.insert(lineNumberInsn, newInsns);
		updateTargeters(lineNumberInsn, pos);
		newInsns.dispose();
	}

	private LocalVariableGen injectCodeInitTracer(MethodGen methodGen) {
		InstructionList insnList = methodGen.getInstructionList();
		InstructionHandle startInsn = insnList.getStart();
		if (startInsn == null) {
			return null;
		}
		LocalVariableGen tracerVar = methodGen.addLocalVariable(TRACER_VAR_NAME, Type.getType(ExecutionTracer.class),
				insnList.getStart(), insnList.getEnd());
		InstructionList newInsns = new InstructionList();
		newInsns.append(new INVOKESTATIC(executionTracer_getTracer_idx));
		newInsns.append(new ASTORE(tracerVar.getIndex()));
		/* inject code */
		InstructionHandle insertPos = insnList.insert(startInsn, newInsns);
		updateTargeters(startInsn, insertPos);
		newInsns.dispose();
		return tracerVar;
	}
	
	private static void updateTargeters(InstructionHandle oldPos, InstructionHandle newPos) {
		InstructionTargeter[] itList = oldPos.getTargeters();
		if (itList != null) {
			for (InstructionTargeter it : itList) {
				it.updateTarget(oldPos, newPos);
			}
		}
	}
	
	private int executionTracer_getTracer_idx;
	private int executionTracer_hitLine_idx;
	private int executionTracer_writeField_idx;
	private int executionTracer_writeLocalVar_idx;
	private int executionTracer_hitInvoke_idx;
	private int executionTracer_enterMethod_idx;
	private int executionTracer_readField_idx;
	private int executionTracer_readLocalVar_idx;
	private int executionTracer_writeStaticField_idx;
	private int executionTracer_readStaticField_idx;
	private int executionTracer_readArrayElementVar_idx;
	private int executionTracer_writeArrayElementVar_idx;
	private int executionTracer_hitReturn_idx;
	private int executionTracer_hitInvokeStatic_idx;
	
	private void setMethodIndex(ConstantPoolGen cpg) {
		/* this part of code is generated using microbat.tools.CodeGenerator for more convenient */
		executionTracer_enterMethod_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_enterMethod", "(Ljava/lang/String;Ljava/lang/String;)V");
		executionTracer_hitInvoke_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_hitInvoke", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;I)V");
		executionTracer_writeStaticField_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_writeStaticField", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
		executionTracer_readArrayElementVar_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_readArrayElementVar", "(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/String;I)V");
		executionTracer_writeArrayElementVar_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_writeArrayElementVar", "(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/String;I)V");
		executionTracer_hitLine_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_hitLine", "(I)V");
		executionTracer_writeLocalVar_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_writeLocalVar", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;IIII)V");
		executionTracer_readStaticField_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_readStaticField", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
		executionTracer_readLocalVar_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_readLocalVar", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;IIII)V");
		executionTracer_getTracer_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_getTracer", "()Lmicrobat/instrumentation/trace/data/ExecutionTracer;");
		executionTracer_writeField_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_writeField", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;I)V");
		executionTracer_hitInvokeStatic_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_hitInvokeStatic", "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;I)V");
		executionTracer_hitReturn_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_hitReturn", "(Ljava/lang/Object;Ljava/lang/String;I)V");
		executionTracer_readField_idx = cpg.addMethodref("microbat/instrumentation/trace/data/ExecutionTracer", "_readField", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;I)V");

	}
}

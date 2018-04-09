package microbat.mutation.mutation;

import java.util.List;

import org.eclipse.jdt.core.dom.VariableDeclaration;

import japa.parser.ast.Node;
import japa.parser.ast.body.ModifierSet;
import japa.parser.ast.body.VariableDeclarator;
import japa.parser.ast.expr.AssignExpr;
import japa.parser.ast.expr.BooleanLiteralExpr;
import japa.parser.ast.expr.CharLiteralExpr;
import japa.parser.ast.expr.DoubleLiteralExpr;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.FieldAccessExpr;
import japa.parser.ast.expr.IntegerLiteralExpr;
import japa.parser.ast.expr.LongLiteralExpr;
import japa.parser.ast.expr.NullLiteralExpr;
import japa.parser.ast.expr.VariableDeclarationExpr;
import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.stmt.EmptyStmt;
import japa.parser.ast.stmt.ExpressionStmt;
import japa.parser.ast.stmt.IfStmt;
import japa.parser.ast.stmt.ReturnStmt;
import japa.parser.ast.stmt.Statement;
import japa.parser.ast.type.PrimitiveType;
import japa.parser.ast.type.ReferenceType;
import japa.parser.ast.type.Type;
import mutation.mutator.MutationVisitor;
import mutation.mutator.mapping.MutationMap;
import mutation.parser.ClassAnalyzer;
import mutation.parser.VariableDescriptor;
import sav.common.core.utils.CollectionUtils;

/**
 * 
 * @author LLT
 *
 */
public class TraceMutationVisitor extends MutationVisitor {
	private List<MutationType> mutationTypes;
	private VarNameFinder varNameFinder = new VarNameFinder();

	public TraceMutationVisitor(List<MutationType> mutationTypes) {
		super();
		this.mutationTypes = mutationTypes;
	}

 	public TraceMutationVisitor(MutationMap mutationMap, ClassAnalyzer classAnalyzer) {
		super(mutationMap, classAnalyzer);
	}
 	
 	@Override
 	public boolean mutate(VariableDeclarationExpr n) {
 		if (!mutationTypes.contains(MutationType.REMOVE_ASSIGNMENT)) {
 			return super.mutate(n);
 		}
 		MutationNode muNode;
 		VariableDeclarationExpr newVarDeclExpr = (VariableDeclarationExpr) nodeCloner.visit(n, null);
 		Node newNode = null;
		if (n.getParentNode() instanceof ExpressionStmt) {
			muNode = newNode(n.getParentNode());
		} else {
			muNode = newNode(n);
		}
		Type fieldType = n.getType();
		for (VariableDeclarator var : newVarDeclExpr.getVars()) {
			var.setInit(getInitValueExpr(fieldType));
		}
		
		if (n.getParentNode() instanceof ExpressionStmt) {
			newNode = new ExpressionStmt(newVarDeclExpr);
		} else {
			newNode = newVarDeclExpr;
		}
		
		muNode.add(newNode, MutationType.REMOVE_ASSIGNMENT.name());
		return false;
 	}
 	
 	private Expression getInitValueExpr(Type fieldType) {
 		if (fieldType instanceof ReferenceType) {
			return new NullLiteralExpr();
		} else if (fieldType instanceof PrimitiveType){
			switch (((PrimitiveType) fieldType).getType()) {
			case Boolean:
				return new BooleanLiteralExpr();
			case Char:
				return new CharLiteralExpr();
			case Byte:
			case Int:
			case Short:
				return new IntegerLiteralExpr("0");
			case Double:
				return new DoubleLiteralExpr("0.0");
			case Float:
				return new LongLiteralExpr("0f");
			case Long:
				return new LongLiteralExpr("0l");
			}
		}
 		return null;
 	}
 	
	@Override
	public boolean mutate(AssignExpr n) {
		if (mutationTypes.contains(MutationType.REMOVE_ASSIGNMENT)) {
			MutationNode muNode;
			if (n.getParentNode() instanceof ExpressionStmt) {
				muNode = newNode(n.getParentNode());
			} else {
				muNode = newNode(n);
			}
			Node stmt = null;
			/* 
			 * to prevent the compilation error for removing final field assignments in constructor
			 *  */
			VariableDescriptor finalField = getCorrespondingFinalField(n.getTarget());
			if (finalField != null) {
				Type fieldType = finalField.getType();
				AssignExpr newAssignExpr = (AssignExpr)nodeCloner.visit(n, null);
				Expression initValueExpr = getInitValueExpr(fieldType);
				newAssignExpr.setValue(initValueExpr);
				if (n.getParentNode() instanceof ExpressionStmt) {
					stmt = new ExpressionStmt(newAssignExpr);
				} else {
					stmt = newAssignExpr;
				}
			}
			if (stmt == null) {
				stmt = new EmptyStmt();
			}
			muNode.add(stmt, MutationType.REMOVE_ASSIGNMENT.name());
		}
		return super.mutate(n);
	}

	private VariableDescriptor getCorrespondingFinalField(Expression target) {
		if (!(target instanceof FieldAccessExpr)) {
			return null;
		}
		varNameFinder.reset();
		target.accept(varNameFinder, true);
		String varName = varNameFinder.getVarName();
		VariableDescriptor field = classDescriptor.getFieldByName(varName);
		if (field != null && ModifierSet.isFinal(field.getModifier())) {
			return field;
		}
		return null;
	}

	@Override
	public boolean mutate(IfStmt n) {
		boolean ribType = mutationTypes.contains(MutationType.REMOVE_IF_BLOCK);
		boolean ricType = mutationTypes.contains(MutationType.REMOVE_IF_CONDITION);
		if (!ribType && !ricType) {
			return super.mutate(n);
		}
		IfStmt ifStmt = (IfStmt) nodeCloner.visit(n, null);
		// remove Stmt Block Which Has Return Stmt 
		if (isEmptyOrContainReturnStmt(ifStmt.getThenStmt())) {
			ifStmt.setThenStmt(null);
		}
		if (isEmptyOrContainReturnStmt(ifStmt.getElseStmt())) {
			ifStmt.setElseStmt(null);
		}
		MutationNode muNode = newNode(n);
		if (ifStmt.getThenStmt() == null && ifStmt.getElseStmt() == null) {
			if (ribType) {
				/* empty if or if which has return stmt in its stmt block */
				muNode.add(new EmptyStmt(), MutationType.REMOVE_IF_BLOCK.name());
			}
		} else {
			if (ricType) {
				if (ifStmt.getThenStmt() != null) {
					Node newNode = ifStmt.getThenStmt().accept(nodeCloner, null);
					muNode.add(newNode, MutationType.REMOVE_IF_CONDITION.name());
				} 
				if (ifStmt.getElseStmt() != null) {
					Node newNode = ifStmt.getElseStmt().accept(nodeCloner, null);
					muNode.add(newNode, MutationType.REMOVE_IF_CONDITION.name());
				}
			}
		}
		
		return super.mutate(n);
	}
	
	private boolean isEmptyOrContainReturnStmt(Statement stmt) {
		if (stmt == null) {
			return true;
		}
		if (stmt instanceof ReturnStmt) {
			return true;
		}
		if (stmt instanceof BlockStmt) {
			List<Statement> subStmts = ((BlockStmt) stmt).getStmts();
			if (CollectionUtils.isEmpty(subStmts)) {
				return true;
			}
			for (Statement subStmt : subStmts) {
				if (subStmt instanceof ReturnStmt) {
					return true;
				}
			}
		}
		return false; 
	}
}

/*
 * Copyright 2013 Wolfgang Flohr-Hochbichler (developer@jshybugger.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jshybugger.instrumentation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.Assignment;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Block;
import org.mozilla.javascript.ast.CatchClause;
import org.mozilla.javascript.ast.EmptyStatement;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.ForInLoop;
import org.mozilla.javascript.ast.ForLoop;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.IfStatement;
import org.mozilla.javascript.ast.KeywordLiteral;
import org.mozilla.javascript.ast.LabeledStatement;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NodeVisitor;
import org.mozilla.javascript.ast.NumberLiteral;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.ReturnStatement;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.SwitchCase;
import org.mozilla.javascript.ast.ThrowStatement;
import org.mozilla.javascript.ast.TryStatement;
import org.mozilla.javascript.ast.UnaryExpression;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.VariableInitializer;
import org.mozilla.javascript.ast.WhileLoop;


/**
 * The DebugInstrumentator is responsible for instrumenting a javascript resource to 
 * make it debug-able. 
 */
public class DebugInstrumentator implements NodeVisitor {

	/** The processed lines. */
	private HashSet<Integer> processedLines = new HashSet<Integer>();
	
	/** The script uri. */
	private String scriptURI;

	/**
	 * Instantiates a new debug instrumentator.
	 */
	public DebugInstrumentator() {
	}

	/* (non-Javadoc)
	 * @see org.mozilla.javascript.ast.NodeVisitor#visit(org.mozilla.javascript.ast.AstNode)
	 */
	@Override
	public boolean visit(AstNode node) {

		if (node.getLineno() < 0) { // this is a dynamic generated node
			return true;
		}
		
		if (node instanceof AstRoot) {
			scriptURI = ((ScriptNode)node).getSourceName();
			loadFile((AstRoot)node);
			
			return true;
		} else if (node instanceof FunctionNode) {

			prepareStack((ScriptNode) node);
			
			//return true;

		} else if (node instanceof ForLoop) {

			ForLoop forLoop = ((ForLoop)node);
			if (forLoop.getBody() instanceof Scope) {
				instrumentStatement(node, false);
			} else {
				forLoop.setBody(makeScope(forLoop.getBody()));
			}

		} else if (node instanceof WhileLoop) {

			WhileLoop whileLoop = ((WhileLoop)node);
			if (whileLoop.getBody() instanceof Scope) {
				instrumentStatement(node, false);
			} else {
				whileLoop.setBody(makeScope(whileLoop.getBody()));
			}

		} else if (node instanceof IfStatement) {

			IfStatement ifStmt = ((IfStatement)node);
			if ((ifStmt.getThenPart() != null) && !(ifStmt.getThenPart() instanceof Scope)) {
				ifStmt.setThenPart(makeScope(ifStmt.getThenPart()));
			} 
			if ((ifStmt.getElsePart() != null) && !(ifStmt.getElsePart() instanceof Scope)) {
				ifStmt.setElsePart(makeScope(ifStmt.getElsePart()));
			} 
			instrumentStatement(node, false);
			
		} else if (node instanceof ReturnStatement) {

			instrumentStatement(node, false);

		} else if (node instanceof ExpressionStatement) {

			instrumentStatement(node, false);

		} else if (node instanceof VariableDeclaration) {
			
			
			instrumentStatement(node, false);

		} else if (node instanceof NumberLiteral) {
			
			// workaround for hex literal bug - getValue doesn't contain the 0x prefix.
			// so setting setValue(getNumber()) will fix this 
			NumberLiteral numNode = (NumberLiteral)node;
			String value = numNode.getValue();
			double parseFloat = numNode.getNumber() * -1.0;
			try {
				parseFloat = Float.parseFloat(value);
			} catch (NumberFormatException pex) {
			}
			if ((value != null) && (value.indexOf(".")<0) && (parseFloat != numNode.getNumber())) {
				numNode.setValue(String.valueOf((int)numNode.getNumber()));
			}
			
		} else if (node instanceof KeywordLiteral) {

			if (node.getType() == Token.DEBUGGER) {
				instrumentStatement(node, true);
			}
		} else if (node instanceof SwitchCase) {

			return false; // TODO fixit
		} else if (node instanceof EmptyStatement) {
		} else if (node instanceof NumberLiteral) {
		} else {
			//System.out.format(indent, "").println(
			//		"Unknown LINE: " + node.getLineno() + " : "
			//				+ node.getClass());
			return true;
		}

		processedLines.add(node.getLineno());

		return true;
	}
	
	/**
	 * Add JsHybugger.track() statement before javascript statement.
	 *
	 * @param node the statement node
	 * @param debugger true for "debugger" keyword
	 */
	private void instrumentStatement(AstNode node, boolean debugger) {

		if ((node.getPosition() == 0) || processedLines.contains(node.getLineno()) || (node.getParent() instanceof ForInLoop)) {
			return;
		}
		
		ExpressionStatement expr = makeExpression(makeFunctionCall("JsHybugger.track", scriptURI, node.getLineno(), debugger));
		node.getParent().addChildBefore(expr, node);
	}
	
	/**
	 * Add JsHybugger.loadFile() to node.
	 *
	 * @param node the node
	 */
	protected void loadFile(AstRoot node) {
		prepareStack(node);
		((TryStatement)node.getFirstChild()).getTryBlock().addChildToFront(makeExpression(makeFunctionCall("JsHybugger.loadFile", scriptURI, node.getEndLineno())));
	}
	
	/**
	 * Add JsHybugger.pushStack/popStack to function node.
	 *
	 * @param node the node
	 */
	private void prepareStack(ScriptNode node) {
		
		if (/*processedLines.contains(node.getLineno()) ||*/ (node.getLineno() < 0)) {
			return;
		}

		String functionName = "<anonymous>";
		AstNode fctnBody = null;
		String fctnVars = null;
		if (node instanceof AstRoot) {
			fctnBody = new Block();
			for (Node child = node.getFirstChild(); child != null; ) {
				Node nextChild = child.getNext();
				fctnBody.addChild((AstNode) child);
				child = nextChild;
			}
			((AstRoot)node).removeChildren();
			functionName = "<toplevel>";
			//return;
		} else if (node instanceof FunctionNode) {
			FunctionNode functionNode = (FunctionNode)node;
			if (functionNode.getFunctionName() != null) {
				functionName = functionNode.getFunctionName().getIdentifier();
			} else {
				if (functionNode.getParent() instanceof VariableInitializer) {
					if (functionNode.getParent().getParent() instanceof VariableDeclaration) {
						VariableDeclaration decl = (VariableDeclaration)functionNode.getParent().getParent();
						if ((decl.getVariables() != null) && (decl.getVariables().size()>0)) { 
							functionName = decl.getVariables().get(0).getTarget().getString();
						}
					}
				} else if (functionNode.getParent() instanceof LabeledStatement) {
					LabeledStatement decl = (LabeledStatement)functionNode.getParent();
					if ((decl.getLabels() != null) && (decl.getLabels().size() > 0)) {
						functionName = decl.getLabels().get(0).getName();
					}
				} else if (functionNode.getParent() instanceof Assignment) {
					Assignment decl = (Assignment)functionNode.getParent();
					if ((decl.getLeft() != null) && (decl.getLeft() instanceof PropertyGet)) {
						PropertyGet prop = (PropertyGet)decl.getLeft();
						if ((prop.getLeft() instanceof Name) && (prop.getRight() instanceof Name)) {
							functionName =	prop.getLeft().getString() + "." + prop.getRight().getString();
						}
					}
				}
			}
			fctnBody = functionNode.getBody();

			// extract function variables
			functionNode.flattenSymbolTable(false);
			String[] names = functionNode.getParamAndVarNames();
			if (names.length >0) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < names.length; i++) {
					if (i>0) {
						sb.append(",");
					}
					sb.append(names[i]);
				}				
				fctnVars = sb.toString();
			}
//			functionNode.getScope().getSymbolTable();
		}

		// create try block
		TryStatement tryStmt = new TryStatement();
		tryStmt.setTryBlock(fctnBody);
		
		// add jscode: var jsHyBuggerEvalFunc = function(jsHyBuggerEval) { return eval(jsHyBuggerEval); };
		List<VariableInitializer> variables = new ArrayList<VariableInitializer>();
		VariableDeclaration vDecl = new VariableDeclaration();

		FunctionNode fnode = new FunctionNode();
		fnode.addParam(new Name(0, "jsHyBuggerEval"));
		
		ReturnStatement ret = new ReturnStatement();
		ret.setReturnValue(makeFunctionCall("eval", new VariableLiteral("jsHyBuggerEval")));

		fnode.setBody(makeBlock(ret));

		VariableInitializer var = new VariableInitializer();
		var.setInitializer(fnode);
		var.setTarget(new Name(0, "jsHyBuggerEvalFunc"));
		variables.add(var);
		vDecl.setVariables(variables);
		
		ExpressionStatement jsHyBuggerEvalExpr = makeExpression(vDecl);
		fctnBody.addChildBefore(jsHyBuggerEvalExpr, fctnBody.getFirstChild());

		// add jscode: JsHybugger.pushStack("<anonymous>", "www/js/calc.js", '7');
		ExpressionStatement pushStackExpression = makeExpression(makeFunctionCall("JsHybugger.pushStack", new VariableLiteral("this"), new VariableLiteral("jsHyBuggerEvalFunc"), functionName, fctnVars, scriptURI, node.getLineno()));
		fctnBody.addChildAfter(pushStackExpression, jsHyBuggerEvalExpr);

		
		// create catch block
		CatchClause catchClause = makeStackCatchBlock();
		tryStmt.addCatchClause(catchClause);

		// create finally block
		pushStackExpression = makeExpression(makeFunctionCall("JsHybugger.popStack"));
		tryStmt.setFinallyBlock(makeBlock(pushStackExpression));
		
		// replace original body with try/catch wrapped body
		if (node instanceof AstRoot) {
			((AstRoot)node).addChild(tryStmt);
		} else {
			((FunctionNode)node).setBody(makeBlock(tryStmt));
		}
		
		// try {  JsHybugger.pushStack('" + fcntName + "', '" + scriptURI + "', " +node.getLineno() + ");  
		//       var jsHyBuggerEvalFunc = function(jsHyBuggerEval) { return eval(jsHyBuggerEval); };");
		// } catch (jsHyBuggerEx) { if (!jsHyBuggerEx.reThrown) {  JsHybugger.reportException(jsHyBuggerEx); } jsHyBuggerEx.reThrown = true; throw jsHyBuggerEx;} 
		// finally {  JsHybugger.popStack();}");			
	}

	/**
	 * Make try/catch block.
	 *
	 * @return the catch clause
	 */
	private CatchClause makeStackCatchBlock() {
		CatchClause catchClause = new CatchClause();
		catchClause.setVarName(new Name(0, "jsHyBuggerEx"));
		
		Block catchBlock = new Block();
		
		IfStatement ifStmt = new IfStatement();
		UnaryExpression condition = new UnaryExpression();
		condition.setIsPostfix(false);
		condition.setType(Token.NOT);
		
		PropertyGet propGet = new PropertyGet();
		propGet.setLeftAndRight(new Name(0, "jsHyBuggerEx"), new Name(0, "reThrown"));
		condition.setOperand(propGet);
		
		ifStmt.setCondition(condition);
		ifStmt.setThenPart(makeFunctionCall("JsHybugger.reportException", new VariableLiteral("jsHyBuggerEx")));
		catchBlock.addChild(makeExpression(ifStmt));
		
		Assignment assign = new Assignment();
		PropertyGet propLeft = new PropertyGet();
		propLeft.setLeftAndRight(new Name(0, "jsHyBuggerEx"), new Name(0, "reThrown"));
		KeywordLiteral bArg = new KeywordLiteral();
		bArg.setType(Token.TRUE);
		
		assign.setLeftAndRight(propLeft, bArg);
		assign.setType(Token.ASSIGN);
		catchBlock.addChild(makeExpression(assign));
		
		ThrowStatement throwStmt = new ThrowStatement();
		throwStmt.setExpression(new Name(0, "jsHyBuggerEx"));
		catchBlock.addChild(throwStmt);

		catchClause.setBody(catchBlock);
		return catchClause;
	}
	
	/**
	 * Make JS-function call.
	 *
	 * @param functionName the function name
	 * @param args the function args
	 * @return the function call node
	 */
	protected FunctionCall makeFunctionCall(String functionName, Object...args) {
		FunctionCall call = new FunctionCall();
		call.setTarget(new Name(0, functionName));
		
		for (Object arg : args) {
		
			if (arg instanceof String) {
				StringLiteral sArg = new StringLiteral();
				sArg.setQuoteCharacter('\'');
				sArg.setValue((String) arg);
				call.addArgument(sArg);
				
			} else if (arg instanceof Integer) {
				NumberLiteral nArg = new NumberLiteral();
				nArg.setValue(String.valueOf(arg));
				call.addArgument(nArg);

			} else if (arg instanceof Boolean) {
				KeywordLiteral bArg = new KeywordLiteral();
				bArg.setType((Boolean) arg ? Token.TRUE : Token.FALSE);
				call.addArgument(bArg);

			} else if (arg instanceof VariableLiteral) {
				Name vArg = new Name();
				vArg.setIdentifier(((VariableLiteral) arg).getVarName());
				call.addArgument(vArg);
				
			} else {
				KeywordLiteral bArg = new KeywordLiteral();
				bArg.setType(Token.NULL);
				call.addArgument(bArg);
			}
		}
		
		return call;
	}
	
	/**
	 * Make JS expression.
	 *
	 * @param stmt the statement
	 * @return the expression statement node
	 */
	private ExpressionStatement makeExpression(AstNode stmt) {
		ExpressionStatement expr = new ExpressionStatement();
		expr.setExpression(stmt);
		return expr;
	}
	
	/**
	 * Make code block.
	 *
	 * @param stmt the js statement
	 * @return the block node
	 */
	private Block makeBlock(AstNode stmt) {
		Block block = new Block();
		block.addChild(stmt);
		return block;
	}

	/**
	 * Make JS scope.
	 *
	 * @param stmt the statement
	 * @return the scope node
	 */
	private Scope makeScope(AstNode stmt) {
		Scope scope = new Scope();
		scope.addChild(stmt);
		return scope;
	}
	
	/**
	 * The Class VariableLiteral.
	 */
	class VariableLiteral {
		
		/** The var name. */
		private final String varName;

		/**
		 * Instantiates a new variable literal.
		 *
		 * @param varName the var name
		 */
		VariableLiteral(String varName) {
			this.varName = varName;
		}

		/**
		 * Gets the var name.
		 *
		 * @return the var name
		 */
		public String getVarName() {
			return varName;
		}
	}
}
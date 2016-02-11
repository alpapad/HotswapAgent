/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 1999- Shigeru Chiba. All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License.  Alternatively, the contents of this file may be used under
 * the terms of the GNU Lesser General Public License Version 2.1 or later,
 * or the Apache License Version 2.0.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 */

package org.hotswap.agent.javassist.compiler;

public class NoFieldException extends CompileError {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private String fieldName;
	private org.hotswap.agent.javassist.compiler.ast.ASTree expr;

	/*
	 * NAME must be JVM-internal representation.
	 */
	public NoFieldException(String name, org.hotswap.agent.javassist.compiler.ast.ASTree e) {
		super("no such field: " + name);
		fieldName = name;
		expr = e;
	}

	/*
	 * The returned name should be JVM-internal representation.
	 */
	public String getField() {
		return fieldName;
	}

	/*
	 * Returns the expression where this exception is thrown.
	 */
	public org.hotswap.agent.javassist.compiler.ast.ASTree getExpr() {
		return expr;
	}
}

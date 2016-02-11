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

package org.hotswap.agent.javassist.bytecode.stackmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.bytecode.BadBytecode;

public abstract class TypeData {
	/*
	 * Memo: array type is a subtype of Cloneable and Serializable
	 */

	public static TypeData[] make(int size) {
		TypeData[] array = new TypeData[size];
		for (int i = 0; i < size; i++) {
			array[i] = TypeTag.TOP;
		}

		return array;
	}

	protected TypeData() {
	}

	/**
	 * Sets the type name of this object type. If the given type name is a
	 * subclass of the current type name, then the given name becomes the name
	 * of this object type.
	 *
	 * @param className
	 *            dot-separated name unless the type is an array type.
	 */
	@SuppressWarnings("unused")
	private static void setType(TypeData td, String className, ClassPool cp) throws BadBytecode {
		td.setType(className, cp);
	}

	public abstract int getTypeTag();

	public abstract int getTypeData(org.hotswap.agent.javassist.bytecode.ConstPool cp);

	public TypeData join() {
		return new TypeVar(this);
	}

	/**
	 * If the type is a basic type, this method normalizes the type and returns
	 * a BasicType object. Otherwise, it returns null.
	 */
	public abstract BasicType isBasicType();

	public abstract boolean is2WordType();

	/**
	 * Returns false if getName() returns a valid type name.
	 */
	public boolean isNullType() {
		return false;
	}

	public boolean isUninit() {
		return false;
	}

	public abstract boolean eq(TypeData d);

	public abstract String getName();

	public abstract void setType(String s, ClassPool cp) throws org.hotswap.agent.javassist.bytecode.BadBytecode;

	// depth-first search
	public int dfs(ArrayList<TypeVar> order, int index, ClassPool cp) throws NotFoundException {
		return index;
	}

	/**
	 * Returns this if it is a TypeVar or a TypeVar that this type depends on.
	 * Otherwise, this method returns null. It is used by dfs().
	 */
	protected TypeVar toTypeVar() {
		return null;
	}

	// see UninitTypeVar and UninitData
	public void constructorCalled(int offset) {
	}

	/**
	 * Primitive types.
	 */
	protected static class BasicType extends TypeData {
		private String name;
		private int typeTag;

		public BasicType(String type, int tag) {
			name = type;
			typeTag = tag;
		}

		@Override
		public int getTypeTag() {
			return typeTag;
		}

		@Override
		public int getTypeData(org.hotswap.agent.javassist.bytecode.ConstPool cp) {
			return 0;
		}

		@Override
		public TypeData join() {
			if (this == TypeTag.TOP) {
				return this;
			} else {
				return super.join();
			}
		}

		@Override
		public BasicType isBasicType() {
			return this;
		}

		@Override
		public boolean is2WordType() {
			return typeTag == org.hotswap.agent.javassist.bytecode.StackMapTable.LONG
					|| typeTag == org.hotswap.agent.javassist.bytecode.StackMapTable.DOUBLE;
		}

		@Override
		public boolean eq(TypeData d) {
			return this == d;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public void setType(String s, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.bytecode.BadBytecode {
			throw new org.hotswap.agent.javassist.bytecode.BadBytecode("conflict: " + name + " and " + s);
		}

		@Override
		public String toString() {
			return name;
		}
	}

	// a type variable
	public static abstract class AbsTypeVar extends TypeData {
		public AbsTypeVar() {
		}

		public abstract void merge(TypeData t);

		@Override
		public int getTypeTag() {
			return org.hotswap.agent.javassist.bytecode.StackMapTable.OBJECT;
		}

		@Override
		public int getTypeData(org.hotswap.agent.javassist.bytecode.ConstPool cp) {
			return cp.addClassInfo(getName());
		}

		@Override
		public boolean eq(TypeData d) {
			return getName().equals(d.getName());
		}
	}

	/*
	 * a type variable representing a class type or a basic type.
	 */
	public static class TypeVar extends AbsTypeVar {
		protected ArrayList<TypeData> lowers; // lower bounds of this type.
		// ArrayList<TypeData>
		protected ArrayList<TypeVar> usedBy; // reverse relations of lowers
		protected ArrayList<String> uppers; // upper bounds of this type.
		protected String fixedType;

		public TypeVar(TypeData t) {
			uppers = null;
			lowers = new ArrayList<TypeData>(2);
			usedBy = new ArrayList<TypeVar>(2);
			merge(t);
			fixedType = null;
		}

		@Override
		public String getName() {
			if (fixedType == null) {
				return lowers.get(0).getName();
			} else {
				return fixedType;
			}
		}

		@Override
		public BasicType isBasicType() {
			if (fixedType == null) {
				return lowers.get(0).isBasicType();
			} else {
				return null;
			}
		}

		@Override
		public boolean is2WordType() {
			if (fixedType == null) {
				return lowers.get(0).is2WordType();
			} else {
				return false;
			}
		}

		@Override
		public boolean isNullType() {
			if (fixedType == null) {
				return lowers.get(0).isNullType();
			} else {
				return false;
			}
		}

		@Override
		public boolean isUninit() {
			if (fixedType == null) {
				return lowers.get(0).isUninit();
			} else {
				return false;
			}
		}

		@Override
		public void merge(TypeData t) {
			lowers.add(t);
			if (t instanceof TypeVar) {
				((TypeVar) t).usedBy.add(this);
			}
		}

		@Override
		public int getTypeTag() {
			/*
			 * If fixedType is null after calling dfs(), then this type is NULL,
			 * Uninit, or a basic type. So call getTypeTag() on the first
			 * element of lowers.
			 */
			if (fixedType == null) {
				return lowers.get(0).getTypeTag();
			} else {
				return super.getTypeTag();
			}
		}

		@Override
		public int getTypeData(org.hotswap.agent.javassist.bytecode.ConstPool cp) {
			if (fixedType == null) {
				return lowers.get(0).getTypeData(cp);
			} else {
				return super.getTypeData(cp);
			}
		}

		@Override
		public void setType(String typeName, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.bytecode.BadBytecode {
			if (uppers == null) {
				uppers = new ArrayList<String>();
			}

			uppers.add(typeName);
		}

		@Override
		protected TypeVar toTypeVar() {
			return this;
		}

		private int visited = 0;
		private int smallest = 0;
		private boolean inList = false;

		// depth-first serach
		@Override
		public int dfs(ArrayList<TypeVar> preOrder, int index, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.NotFoundException {
			if (visited > 0) {
				return index; // MapMaker.make() may call an already visited
				// node.
			}

			visited = smallest = ++index;
			preOrder.add(this);
			inList = true;
			int n = lowers.size();
			for (int i = 0; i < n; i++) {
				TypeVar child = lowers.get(i).toTypeVar();
				if (child != null) {
					if (child.visited == 0) {
						index = child.dfs(preOrder, index, cp);
						if (child.smallest < smallest) {
							smallest = child.smallest;
						}
					} else if (child.inList) {
						if (child.visited < smallest) {
							smallest = child.visited;
						}
					}
				}
			}

			if (visited == smallest) {
				ArrayList<TypeVar> scc = new ArrayList<TypeVar>(); // strongly
																	// connected
																	// component
				TypeVar cv;
				do {
					cv = preOrder.remove(preOrder.size() - 1);
					cv.inList = false;
					scc.add(cv);
				} while (cv != this);
				fixTypes(scc, cp);
			}

			return index;
		}

		private void fixTypes(ArrayList<TypeVar> scc, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.NotFoundException {
			HashSet<String> lowersSet = new HashSet<String>();
			boolean isBasicType = false;
			TypeData kind = null;
			int size = scc.size();
			for (int i = 0; i < size; i++) {
				ArrayList<TypeData> tds = scc.get(i).lowers;
				int size2 = tds.size();
				for (int j = 0; j < size2; j++) {
					TypeData d = tds.get(j);
					BasicType bt = d.isBasicType();
					if (kind == null) {
						if (bt == null) {
							isBasicType = false;
							kind = d;
							/*
							 * If scc has only an UninitData, fixedType is kept
							 * null. So lowerSet must be empty. If scc has not
							 * only an UninitData but also another TypeData, an
							 * error must be thrown but this error detection has
							 * not been implemented.
							 */
							if (d.isUninit()) {
								break;
							}
						} else {
							isBasicType = true;
							kind = bt;
						}
					} else {
						if (bt == null && isBasicType || bt != null && kind != bt) {
							isBasicType = true;
							kind = TypeTag.TOP;
							break;
						}
					}

					if (bt == null && !d.isNullType()) {
						lowersSet.add(d.getName());
					}
				}
			}

			if (isBasicType) {
				for (int i = 0; i < size; i++) {
					TypeVar cv = scc.get(i);
					cv.lowers.clear();
					cv.lowers.add(kind);
				}
			} else {
				String typeName = fixTypes2(scc, lowersSet, cp);
				for (int i = 0; i < size; i++) {
					TypeVar cv = scc.get(i);
					cv.fixedType = typeName;
				}
			}
		}

		private String fixTypes2(ArrayList<TypeVar> scc, HashSet<String> lowersSet,
				org.hotswap.agent.javassist.ClassPool cp) throws org.hotswap.agent.javassist.NotFoundException {
			Iterator<String> it = lowersSet.iterator();
			if (lowersSet.size() == 0) {
				return null; // only NullType
			} else if (lowersSet.size() == 1) {
				return it.next();
			} else {
				org.hotswap.agent.javassist.CtClass cc = cp.get(it.next());
				while (it.hasNext()) {
					cc = commonSuperClassEx(cc, cp.get(it.next()));
				}

				if (cc.getSuperclass() == null || isObjectArray(cc)) {
					cc = fixByUppers(scc, cp, new HashSet<TypeVar>(), cc);
				}

				if (cc.isArray()) {
					return org.hotswap.agent.javassist.bytecode.Descriptor.toJvmName(cc);
				} else {
					return cc.getName();
				}
			}
		}

		private static boolean isObjectArray(org.hotswap.agent.javassist.CtClass cc)
				throws org.hotswap.agent.javassist.NotFoundException {
			return cc.isArray() && cc.getComponentType().getSuperclass() == null;
		}

		private org.hotswap.agent.javassist.CtClass fixByUppers(ArrayList<TypeVar> users,
				org.hotswap.agent.javassist.ClassPool cp, HashSet<TypeVar> visited,
				org.hotswap.agent.javassist.CtClass type) throws org.hotswap.agent.javassist.NotFoundException {
			if (users == null) {
				return type;
			}

			int size = users.size();
			for (int i = 0; i < size; i++) {
				TypeVar t = users.get(i);
				if (!visited.add(t)) {
					return type;
				}

				if (t.uppers != null) {
					int s = t.uppers.size();
					for (int k = 0; k < s; k++) {
						org.hotswap.agent.javassist.CtClass cc = cp.get(t.uppers.get(k));
						if (cc.subtypeOf(type)) {
							type = cc;
						}
					}
				}

				type = fixByUppers(t.usedBy, cp, visited, type);
			}

			return type;
		}
	}

	/**
	 * Finds the most specific common super class of the given classes by
	 * considering array types.
	 */
	public static org.hotswap.agent.javassist.CtClass commonSuperClassEx(org.hotswap.agent.javassist.CtClass one,
			org.hotswap.agent.javassist.CtClass two) throws org.hotswap.agent.javassist.NotFoundException {
		if (one == two) {
			return one;
		} else if (one.isArray() && two.isArray()) {
			org.hotswap.agent.javassist.CtClass ele1 = one.getComponentType();
			org.hotswap.agent.javassist.CtClass ele2 = two.getComponentType();
			org.hotswap.agent.javassist.CtClass element = commonSuperClassEx(ele1, ele2);
			if (element == ele1) {
				return one;
			} else if (element == ele2) {
				return two;
			} else {
				return one.getClassPool().get(element == null ? "java.lang.Object" : element.getName() + "[]");
			}
		} else if (one.isPrimitive() || two.isPrimitive()) {
			return null; // TOP
		} else if (one.isArray() || two.isArray()) {
			// two.isArray())
			return one.getClassPool().get("java.lang.Object");
		} else {
			return commonSuperClass(one, two);
		}
	}

	/**
	 * Finds the most specific common super class of the given classes. This
	 * method is a copy from Type.
	 */
	public static org.hotswap.agent.javassist.CtClass commonSuperClass(org.hotswap.agent.javassist.CtClass one,
			org.hotswap.agent.javassist.CtClass two) throws org.hotswap.agent.javassist.NotFoundException {
		org.hotswap.agent.javassist.CtClass deep = one;
		org.hotswap.agent.javassist.CtClass shallow = two;
		org.hotswap.agent.javassist.CtClass backupShallow = shallow;
		org.hotswap.agent.javassist.CtClass backupDeep = deep;

		// Phase 1 - Find the deepest hierarchy, set deep and shallow correctly
		for (;;) {
			// In case we get lucky, and find a match early
			if (eq(deep, shallow) && deep.getSuperclass() != null) {
				return deep;
			}

			org.hotswap.agent.javassist.CtClass deepSuper = deep.getSuperclass();
			org.hotswap.agent.javassist.CtClass shallowSuper = shallow.getSuperclass();

			if (shallowSuper == null) {
				// right, now reset shallow
				shallow = backupShallow;
				break;
			}

			if (deepSuper == null) {
				// wrong, swap them, since deep is now useless, its our tmp
				// before we swap it
				deep = backupDeep;
				backupDeep = backupShallow;
				backupShallow = deep;

				deep = shallow;
				shallow = backupShallow;
				break;
			}

			deep = deepSuper;
			shallow = shallowSuper;
		}

		// Phase 2 - Move deepBackup up by (deep end - deep)
		for (;;) {
			deep = deep.getSuperclass();
			if (deep == null) {
				break;
			}

			backupDeep = backupDeep.getSuperclass();
		}

		deep = backupDeep;

		// Phase 3 - The hierarchy positions are now aligned
		// The common super class is easy to find now
		while (!eq(deep, shallow)) {
			deep = deep.getSuperclass();
			shallow = shallow.getSuperclass();
		}

		return deep;
	}

	static boolean eq(org.hotswap.agent.javassist.CtClass one, org.hotswap.agent.javassist.CtClass two) {
		return one == two || one != null && two != null && one.getName().equals(two.getName());
	}

	public static void aastore(TypeData array, TypeData value, org.hotswap.agent.javassist.ClassPool cp)
			throws org.hotswap.agent.javassist.bytecode.BadBytecode {
		if (array instanceof AbsTypeVar) {
			if (!value.isNullType()) {
				((AbsTypeVar) array).merge(ArrayType.make(value));
			}
		}

		if (value instanceof AbsTypeVar) {
			if (array instanceof AbsTypeVar) {
				ArrayElement.make(array); // should call value.setType() later.
			} else if (array instanceof ClassName) {
				if (!array.isNullType()) {
					String type = ArrayElement.typeName(array.getName());
					value.setType(type, cp);
				}
			} else {
				throw new org.hotswap.agent.javassist.bytecode.BadBytecode("bad AASTORE: " + array);
			}
		}
	}

	/*
	 * A type variable representing an array type. It is a decorator of another
	 * type variable.
	 */
	public static class ArrayType extends AbsTypeVar {
		private AbsTypeVar element;

		private ArrayType(AbsTypeVar elementType) {
			element = elementType;
		}

		static TypeData make(TypeData element) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
			if (element instanceof ArrayElement) {
				return ((ArrayElement) element).arrayType();
			} else if (element instanceof AbsTypeVar) {
				return new ArrayType((AbsTypeVar) element);
			} else if (element instanceof ClassName) {
				if (!element.isNullType()) {
					return new ClassName(typeName(element.getName()));
				}
			}

			throw new org.hotswap.agent.javassist.bytecode.BadBytecode("bad AASTORE: " + element);
		}

		@Override
		public void merge(TypeData t) {
			try {
				if (!t.isNullType()) {
					element.merge(ArrayElement.make(t));
				}
			} catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
				// never happens
				throw new RuntimeException("fatal: " + e);
			}
		}

		@Override
		public String getName() {
			return typeName(element.getName());
		}

		public AbsTypeVar elementType() {
			return element;
		}

		@Override
		public BasicType isBasicType() {
			return null;
		}

		@Override
		public boolean is2WordType() {
			return false;
		}

		/*
		 * elementType must be a class name. Basic type names are not allowed.
		 */
		public static String typeName(String elementType) {
			if (elementType.charAt(0) == '[') {
				return "[" + elementType;
			} else {
				return "[L" + elementType.replace('.', '/') + ";";
			}
		}

		@Override
		public void setType(String s, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.bytecode.BadBytecode {
			element.setType(ArrayElement.typeName(s), cp);
		}

		@Override
		protected TypeVar toTypeVar() {
			return element.toTypeVar();
		}

		@Override
		public int dfs(ArrayList<TypeVar> order, int index, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.NotFoundException {
			return element.dfs(order, index, cp);
		}
	}

	/*
	 * A type variable representing an array-element type. It is a decorator of
	 * another type variable.
	 */
	public static class ArrayElement extends AbsTypeVar {
		private AbsTypeVar array;

		private ArrayElement(AbsTypeVar a) { // a is never null
			array = a;
		}

		public static TypeData make(TypeData array) throws org.hotswap.agent.javassist.bytecode.BadBytecode {
			if (array instanceof ArrayType) {
				return ((ArrayType) array).elementType();
			} else if (array instanceof AbsTypeVar) {
				return new ArrayElement((AbsTypeVar) array);
			} else if (array instanceof ClassName) {
				if (!array.isNullType()) {
					return new ClassName(typeName(array.getName()));
				}
			}

			throw new org.hotswap.agent.javassist.bytecode.BadBytecode("bad AASTORE: " + array);
		}

		@Override
		public void merge(TypeData t) {
			try {
				if (!t.isNullType()) {
					array.merge(ArrayType.make(t));
				}
			} catch (org.hotswap.agent.javassist.bytecode.BadBytecode e) {
				// never happens
				throw new RuntimeException("fatal: " + e);
			}
		}

		@Override
		public String getName() {
			return typeName(array.getName());
		}

		public AbsTypeVar arrayType() {
			return array;
		}

		/*
		 * arrayType must be a class name. Basic type names are not allowed.
		 */

		@Override
		public BasicType isBasicType() {
			return null;
		}

		@Override
		public boolean is2WordType() {
			return false;
		}

		private static String typeName(String arrayType) {
			if (arrayType.length() > 1 && arrayType.charAt(0) == '[') {
				char c = arrayType.charAt(1);
				if (c == 'L') {
					return arrayType.substring(2, arrayType.length() - 1).replace('/', '.');
				} else if (c == '[') {
					return arrayType.substring(1);
				}
			}

			return "java.lang.Object"; // the array type may be NullType
		}

		@Override
		public void setType(String s, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.bytecode.BadBytecode {
			array.setType(ArrayType.typeName(s), cp);
		}

		@Override
		protected TypeVar toTypeVar() {
			return array.toTypeVar();
		}

		@Override
		public int dfs(ArrayList<TypeVar> order, int index, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.NotFoundException {
			return array.dfs(order, index, cp);
		}
	}

	public static class UninitTypeVar extends AbsTypeVar {
		protected TypeData type; // UninitData or TOP

		public UninitTypeVar(UninitData t) {
			type = t;
		}

		@Override
		public int getTypeTag() {
			return type.getTypeTag();
		}

		@Override
		public int getTypeData(org.hotswap.agent.javassist.bytecode.ConstPool cp) {
			return type.getTypeData(cp);
		}

		@Override
		public BasicType isBasicType() {
			return type.isBasicType();
		}

		@Override
		public boolean is2WordType() {
			return type.is2WordType();
		}

		@Override
		public boolean isUninit() {
			return type.isUninit();
		}

		@Override
		public boolean eq(TypeData d) {
			return type.eq(d);
		}

		@Override
		public String getName() {
			return type.getName();
		}

		@Override
		protected TypeVar toTypeVar() {
			return null;
		}

		@Override
		public TypeData join() {
			return type.join();
		}

		@Override
		public void setType(String s, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.bytecode.BadBytecode {
			type.setType(s, cp);
		}

		@Override
		public void merge(TypeData t) {
			if (!t.eq(type)) {
				type = TypeTag.TOP;
			}
		}

		@Override
		public void constructorCalled(int offset) {
			type.constructorCalled(offset);
		}

		public int offset() {
			if (type instanceof UninitData) {
				return ((UninitData) type).offset;
			} else {
				throw new RuntimeException("not available");
			}
		}
	}

	/**
	 * Type data for OBJECT.
	 */
	public static class ClassName extends TypeData {
		private String name; // dot separated.

		public ClassName(String n) {
			name = n;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public BasicType isBasicType() {
			return null;
		}

		@Override
		public boolean is2WordType() {
			return false;
		}

		@Override
		public int getTypeTag() {
			return org.hotswap.agent.javassist.bytecode.StackMapTable.OBJECT;
		}

		@Override
		public int getTypeData(org.hotswap.agent.javassist.bytecode.ConstPool cp) {
			return cp.addClassInfo(getName());
		}

		@Override
		public boolean eq(TypeData d) {
			return name.equals(d.getName());
		}

		@Override
		public void setType(String typeName, org.hotswap.agent.javassist.ClassPool cp)
				throws org.hotswap.agent.javassist.bytecode.BadBytecode {
		}
	}

	/**
	 * Type data for NULL or OBJECT. The types represented by the instances of
	 * this class are initially NULL but will be OBJECT.
	 */
	public static class NullType extends ClassName {
		public NullType() {
			super("null-type"); // type name
		}

		@Override
		public int getTypeTag() {
			return org.hotswap.agent.javassist.bytecode.StackMapTable.NULL;
		}

		@Override
		public boolean isNullType() {
			return true;
		}

		@Override
		public int getTypeData(org.hotswap.agent.javassist.bytecode.ConstPool cp) {
			return 0;
		}
	}

	/**
	 * Type data for UNINIT.
	 */
	public static class UninitData extends ClassName {
		int offset;
		boolean initialized;

		UninitData(int offset, String className) {
			super(className);
			this.offset = offset;
			this.initialized = false;
		}

		public UninitData copy() {
			return new UninitData(offset, getName());
		}

		@Override
		public int getTypeTag() {
			return org.hotswap.agent.javassist.bytecode.StackMapTable.UNINIT;
		}

		@Override
		public int getTypeData(org.hotswap.agent.javassist.bytecode.ConstPool cp) {
			return offset;
		}

		@Override
		public TypeData join() {
			if (initialized) {
				return new TypeVar(new ClassName(getName()));
			} else {
				return new UninitTypeVar(copy());
			}
		}

		@Override
		public boolean isUninit() {
			return true;
		}

		@Override
		public boolean eq(TypeData d) {
			if (d instanceof UninitData) {
				UninitData ud = (UninitData) d;
				return offset == ud.offset && getName().equals(ud.getName());
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			return "uninit:" + getName() + "@" + offset;
		}

		public int offset() {
			return offset;
		}

		@Override
		public void constructorCalled(int offset) {
			if (offset == this.offset) {
				initialized = true;
			}
		}
	}

	public static class UninitThis extends UninitData {
		UninitThis(String className) {
			super(-1, className);
		}

		@Override
		public UninitData copy() {
			return new UninitThis(getName());
		}

		@Override
		public int getTypeTag() {
			return org.hotswap.agent.javassist.bytecode.StackMapTable.THIS;
		}

		@Override
		public int getTypeData(org.hotswap.agent.javassist.bytecode.ConstPool cp) {
			return 0;
		}

		@Override
		public String toString() {
			return "uninit:this";
		}
	}
}

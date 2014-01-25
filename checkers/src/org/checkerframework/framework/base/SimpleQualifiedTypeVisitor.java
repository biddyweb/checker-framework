package org.checkerframework.framework.base;

import org.checkerframework.framework.base.QualifiedTypeMirror;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedArrayType;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedDeclaredType;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedExecutableType;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedIntersectionType;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedNoType;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedNullType;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedPrimitiveType;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedTypeVariable;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedUnionType;
import org.checkerframework.framework.base.QualifiedTypeMirror.QualifiedWildcardType;

public class SimpleQualifiedTypeVisitor<Q,R,P> implements QualifiedTypeVisitor<Q,R,P> {
    protected final R DEFAULT_VALUE;

    public SimpleQualifiedTypeVisitor() {
        this(null);
    }

    public SimpleQualifiedTypeVisitor(R defaultValue) {
        this.DEFAULT_VALUE = defaultValue;
    }

    protected R defaultAction(QualifiedTypeMirror<Q> type, P p) {
        return DEFAULT_VALUE;
    }

    @Override
    public R visit(QualifiedTypeMirror<Q> type) {
        return visit(type, null);
    }

    @Override
    public R visit(QualifiedTypeMirror<Q> type, P p) {
        return (type == null) ? null : type.accept(this, p);
    }

    @Override
    public R visitDeclared(QualifiedDeclaredType<Q> type, P p) {
        return defaultAction(type, p);
    }

    @Override
    public R visitIntersection(QualifiedIntersectionType<Q> type, P p) {
        return defaultAction(type, p);
    }

    @Override
    public R visitUnion(QualifiedUnionType<Q> type, P p) {
        return defaultAction(type, p);
    }

    @Override
    public R visitExecutable(QualifiedExecutableType<Q> type, P p) {
        return defaultAction(type, p);
    }

    @Override
    public R visitArray(QualifiedArrayType<Q> type, P p) {
        return defaultAction(type, p);
    }

    @Override
    public R visitTypeVariable(QualifiedTypeVariable<Q> type, P p) {
        return defaultAction(type, p);
    }

    @Override
    public R visitPrimitive(QualifiedPrimitiveType<Q> type, P p) {
        return defaultAction(type, p);
    }

    @Override
    public R visitNoType(QualifiedNoType<Q> type, P p) {
        return defaultAction(type, p);
    }

    @Override
    public R visitNull(QualifiedNullType<Q> type, P p) {
        return defaultAction(type, p);
    }

    @Override
    public R visitWildcard(QualifiedWildcardType<Q> type, P p) {
        return defaultAction(type, p);
    }
}
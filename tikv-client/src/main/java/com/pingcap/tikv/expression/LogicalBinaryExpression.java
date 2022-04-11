/*
 * Copyright 2020 PingCAP, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: expression.proto

package com.pingcap.tikv.expression;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import org.tikv.common.types.IntegerType;
import java.util.List;
import java.util.Objects;

public class LogicalBinaryExpression extends Expression {
  private final Expression left;
  private final Expression right;
  private final Type compType;

  public LogicalBinaryExpression(Type type, Expression left, Expression right) {
    super(IntegerType.BOOLEAN);
    this.left = requireNonNull(left, "left expression is null");
    this.right = requireNonNull(right, "right expression is null");
    this.compType = requireNonNull(type, "type is null");
  }

  public static LogicalBinaryExpression and(Expression left, Expression right) {
    return new LogicalBinaryExpression(Type.AND, left, right);
  }

  public static LogicalBinaryExpression or(Expression left, Expression right) {
    return new LogicalBinaryExpression(Type.OR, left, right);
  }

  public static LogicalBinaryExpression xor(Expression left, Expression right) {
    return new LogicalBinaryExpression(Type.XOR, left, right);
  }

  @Override
  public List<Expression> getChildren() {
    return ImmutableList.of(getLeft(), getRight());
  }

  @Override
  public <R, C> R accept(Visitor<R, C> visitor, C context) {
    return visitor.visit(this, context);
  }

  public Expression getLeft() {
    return left;
  }

  public Expression getRight() {
    return right;
  }

  public Type getCompType() {
    return compType;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof LogicalBinaryExpression)) {
      return false;
    }

    LogicalBinaryExpression that = (LogicalBinaryExpression) other;
    return (compType == that.compType)
        && Objects.equals(left, that.left)
        && Objects.equals(right, that.right);
  }

  @Override
  public int hashCode() {
    return Objects.hash(compType, left, right);
  }

  @Override
  public String toString() {
    return String.format("[%s %s %s]", getLeft(), getCompType(), getRight());
  }

  public enum Type {
    AND,
    OR,
    XOR
  }
}

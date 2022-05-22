/*
 *
 * Copyright 2019 PingCAP, Inc.
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
 *
 */

package com.pingcap.tispark.test.generator

import org.tikv.common.meta.Collation

import java.util
import org.tikv.common.meta.TiColumnInfo.InternalTypeHolder
import org.tikv.common.types
import org.tikv.common.types.{BitType, BytesType, DataType, DateTimeType, DateType, DecimalType, EnumType, IntegerType, JsonType, RealType, SetType, StringType, TimeType, TimestampType}

object DataType extends Enumeration {
  type ReflectedDataType = Value
  type TiDataType = DataType
  val BIT: Val = Val("bit", BitType.BIT)
  val BOOLEAN: Val = Val("boolean", IntegerType.BOOLEAN)
  val TINYINT: Val = Val("tinyint", types.IntegerType.TINYINT)
  val SMALLINT: Val = Val("smallint", types.IntegerType.SMALLINT)
  val MEDIUMINT: Val = Val("mediumint", types.IntegerType.MEDIUMINT)
  val INT: Val = Val("int", types.IntegerType.INT)
  val BIGINT: Val = Val("bigint", types.IntegerType.BIGINT)
  val DECIMAL: Val = Val("decimal", DecimalType.DECIMAL)
  val FLOAT: Val = Val("float", RealType.FLOAT)
  val DOUBLE: Val = Val("double", types.RealType.DOUBLE)
  val TIMESTAMP: Val = Val("timestamp", TimestampType.TIMESTAMP)
  val DATETIME: Val = Val("datetime", DateTimeType.DATETIME)
  val DATE: Val = Val("date", DateType.DATE)
  val TIME: Val = Val("time", TimeType.TIME)
  val YEAR: Val = Val("year", types.IntegerType.YEAR)
  val TEXT: Val = Val("text", BytesType.TEXT)
  val TINYTEXT: Val = Val("tinytext", types.BytesType.TINY_BLOB)
  val MEDIUMTEXT: Val = Val("mediumtext", types.BytesType.MEDIUM_TEXT)
  val LONGTEXT: Val = Val("longtext", types.BytesType.LONG_TEXT)
  val VARCHAR: Val = Val("varchar", StringType.VARCHAR)
  val CHAR: Val = Val("char", types.StringType.CHAR)
  val VARBINARY: Val = Val("varbinary", types.StringType.VARCHAR)
  val BINARY: Val = Val("binary", types.StringType.CHAR)
  val BLOB: Val = Val("blob", types.BytesType.BLOB)
  val TINYBLOB: Val = Val("tinyblob", types.BytesType.TINY_BLOB)
  val MEDIUMBLOB: Val = Val("mediumblob", types.BytesType.MEDIUM_TEXT)
  val LONGBLOB: Val = Val("longblob", types.BytesType.LONG_TEXT)
  val ENUM: Val = Val("enum", EnumType.ENUM)
  val SET: Val = Val("set", SetType.SET)
  val JSON: Val = Val("json", JsonType.JSON)

  def getType(
      dataType: ReflectedDataType,
      flag: Integer,
      len: Long,
      decimal: Integer,
      charset: String,
      collation: Integer): TiDataType =
    dataType.asInstanceOf[Val].getType(flag, len, decimal, charset, collation)

  def getBaseType(dataType: ReflectedDataType): TiDataType =
    dataType.asInstanceOf[Val].getBaseType

  def getTypeName(dataType: ReflectedDataType): String = dataType.asInstanceOf[Val].typeName

  def getBaseFlag(dataType: ReflectedDataType): Int = dataType.asInstanceOf[Val].getBaseFlag

  case class Val(typeName: String, private val baseType: TiDataType) extends super.Val {

    override def toString(): String = typeName

    private[generator] def getType(
        flag: Integer,
        len: Long,
        decimal: Integer,
        charset: String,
        collation: Integer): TiDataType = {
      val constructor = baseType.getClass.getDeclaredConstructor(classOf[InternalTypeHolder])
      constructor.setAccessible(true)
      constructor.newInstance(
        new InternalTypeHolder(
          baseType.getTypeCode,
          flag,
          len,
          decimal,
          charset,
          Collation.translate(collation),
          new util.ArrayList[String]()))
    }

    private[generator] def getBaseFlag: Int = baseType.getFlag

    private[generator] def getBaseType: TiDataType = baseType
  }
}

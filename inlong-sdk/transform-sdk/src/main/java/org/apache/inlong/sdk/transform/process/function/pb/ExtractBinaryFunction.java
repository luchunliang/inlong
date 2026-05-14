/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sdk.transform.process.function.pb;

import org.apache.inlong.sdk.transform.decode.PbNode;
import org.apache.inlong.sdk.transform.decode.PbSourceData;
import org.apache.inlong.sdk.transform.decode.SourceData;
import org.apache.inlong.sdk.transform.process.Context;
import org.apache.inlong.sdk.transform.process.function.FunctionConstant;
import org.apache.inlong.sdk.transform.process.function.TransformFunction;
import org.apache.inlong.sdk.transform.process.operator.OperatorTools;
import org.apache.inlong.sdk.transform.process.parser.ColumnParser;
import org.apache.inlong.sdk.transform.process.parser.ValueParser;

import com.google.protobuf.MessageLite;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.table.data.GenericArrayData;

import java.util.ArrayList;
import java.util.List;

/**
 * ExtractBinaryFunction  ->  extract_binary(path)
 * description:
 * - Return NULL if any parameter is NULL
 * - Return the binary object from protobuf source data based on path
 */
@TransformFunction(type = FunctionConstant.PB_TYPE, names = {
        "extract_binary"}, parameter = "(path)", descriptions = {
                "- Return \"\" if any parameter is NULL;",
                "- Return the binary object from protobuf source data based on 'path'."
        }, examples = {
                "extract_binary($root.feature) = [62,111]"
        })
public class ExtractBinaryFunction implements ValueParser {

    private final ValueParser pathParser;
    private String path;

    public ExtractBinaryFunction(Function expr) {
        List<Expression> expressions = expr.getParameters().getExpressions();
        this.pathParser = OperatorTools.buildParser(expressions.get(0));
        if (pathParser instanceof ColumnParser) {
            this.path = ((ColumnParser) pathParser).getFieldName();
        }
    }

    @Override
    public Object parse(SourceData sourceData, int rowIndex, Context context) {
        // data
        if (!(sourceData instanceof PbSourceData)) {
            return null;
        }
        if (path == null) {
            return null;
        }
        PbSourceData pbData = (PbSourceData) sourceData;
        // node list
        List<PbNode> childNodes = null;
        if (StringUtils.startsWith(path, PbSourceData.ROOT_KEY)) {
            childNodes = pbData.parseStructNodeList(path, pbData.getRootDesc());
        } else if (StringUtils.startsWith(path, PbSourceData.CHILD_KEY)) {
            if (pbData.getChildDesc() == null) {
                return null;
            }
            childNodes = pbData.parseStructNodeList(path, pbData.getChildDesc());
        }
        if (childNodes == null || childNodes.size() <= 0) {
            return null;
        }
        // value
        Object currentNode = pbData.findFieldNode(rowIndex, path);
        if (currentNode == null) {
            return null;
        }
        // array
        PbNode lastNode = childNodes.get(childNodes.size() - 1);
        // primitive
        if (lastNode.isPrimitiveType()) {
            return toByteArray(currentNode);
        }
        // struct
        if (lastNode.isStructType()) {
            return toByteArray(currentNode);
        }
        // array
        if (lastNode.isArrayType()) {
            if (!lastNode.isHasArrayIndex()) {
                if (!(currentNode instanceof List)) {
                    return null;
                }
                List<?> valueList = (List<?>) currentNode;
                List<Object> fieldResult = new ArrayList<>(valueList.size());
                for (Object value : valueList) {
                    fieldResult.add(toByteArray(value));
                }
                return new GenericArrayData(fieldResult.toArray());
            }
            return toByteArray(currentNode);
        }
        // map
        if (lastNode.isMapType()) {
            return toByteArray(currentNode);
        }
        return null;
    }

    private byte[] toByteArray(Object currentNode) {
        if (currentNode == null) {
            return null;
        }
        if (currentNode instanceof MessageLite) {
            return ((MessageLite) currentNode).toByteArray();
        }
        return String.valueOf(currentNode).getBytes();
    }
}

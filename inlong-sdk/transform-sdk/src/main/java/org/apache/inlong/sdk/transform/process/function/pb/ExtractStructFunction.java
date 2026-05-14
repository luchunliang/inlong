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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import org.apache.flink.table.data.GenericRowData;

import java.util.ArrayList;
import java.util.List;

/**
 * ExtractStructFunction  ->  extract_struct(path, field1, field2, field3...)
 * description:
 * - Return NULL if any parameter is NULL
 * - Return the GenericRowData object from protobuf source data based on path
 */
@TransformFunction(type = FunctionConstant.PB_TYPE, names = {
        "extract_struct"}, parameter = "(path, field1,field2,field3...)", descriptions = {
                "- Return \"\" if any parameter is NULL;",
                "- Return the GenericRowData object from protobuf source data based on 'path'."
        }, examples = {
                "extract_struct($root.persion,name,age) = +I(\"Alice\",11)"
        })
public class ExtractStructFunction implements ValueParser {

    private final ValueParser pathParser;
    private final List<ValueParser> fieldParsers;
    private String path;

    public ExtractStructFunction(Function expr) {
        List<Expression> expressions = expr.getParameters().getExpressions();
        this.pathParser = OperatorTools.buildParser(expressions.get(0));
        if (pathParser instanceof ColumnParser) {
            this.path = ((ColumnParser) pathParser).getFieldName();
        }
        this.fieldParsers = new ArrayList<>();
        for (int i = 1; i < expressions.size(); i++) {
            this.fieldParsers.add(OperatorTools.buildParser(expressions.get(i)));
        }
    }

    @Override
    public Object parse(SourceData sourceData, int rowIndex, Context context) {
        if (!(sourceData instanceof PbSourceData)) {
            return null;
        }
        if (path == null) {
            return null;
        }
        PbSourceData pbData = (PbSourceData) sourceData;
        Object currentNode = pbData.findFieldNode(rowIndex, path);
        if (currentNode == null || !(currentNode instanceof DynamicMessage)) {
            return null;
        }
        DynamicMessage currentValue = (DynamicMessage) currentNode;
        Descriptor currentDesc = currentValue.getDescriptorForType();
        GenericRowData result = new GenericRowData(fieldParsers.size());
        int index = 0;
        for (ValueParser parser : fieldParsers) {
            if (parser instanceof ColumnParser) {
                ColumnParser columnParser = (ColumnParser) parser;
                String fieldName = columnParser.getFieldName();
                List<PbNode> childNodes = pbData.parseStructNodeList(fieldName, currentDesc);
                if (childNodes == null || childNodes.size() == 0) {
                    result.setField(index++, null);
                    continue;
                }
                Object fieldValue = pbData.findNodeValue(childNodes, currentValue);
                result.setField(index++, fieldValue);
            } else {
                result.setField(index++, null);
            }
        }
        return result;
    }
}

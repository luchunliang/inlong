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

package org.apache.inlong.sdk.transform.process.function.string;

import org.apache.inlong.sdk.transform.decode.SourceData;
import org.apache.inlong.sdk.transform.process.Context;
import org.apache.inlong.sdk.transform.process.function.FunctionConstant;
import org.apache.inlong.sdk.transform.process.function.TransformFunction;
import org.apache.inlong.sdk.transform.process.operator.OperatorTools;
import org.apache.inlong.sdk.transform.process.parser.ValueParser;

import net.sf.jsqlparser.expression.Function;

/**
 * RtrimFunction  ->  rtrim(str)
 * description:
 * - Return NULL if 'str' is NULL
 * - Return the string 'str' with trailing space characters removed
 */
@TransformFunction(type = FunctionConstant.STRING_TYPE, names = {"rtrim"}, parameter = "(String str)", descriptions = {
        "- Return \"\" if 'str' is NULL;",
        "- Return the string 'str' with trailing space characters removed."
}, examples = {"rtrim(' in long ') = \" in long\""})
public class RtrimFunction implements ValueParser {

    private ValueParser stringParser;

    public RtrimFunction(Function expr) {
        stringParser = OperatorTools.buildParser(expr.getParameters().getExpressions().get(0));
    }

    @Override
    public Object parse(SourceData sourceData, int rowIndex, Context context) {
        Object stringObj = stringParser.parse(sourceData, rowIndex, context);
        if (stringObj == null) {
            return null;
        }
        String str = OperatorTools.parseString(stringObj);
        int len = str.length();
        for (int i = len - 1; i >= 0; i--) {
            if (str.charAt(i) != ' ') {
                return str.substring(0, i + 1);
            }
        }
        return "";
    }
}

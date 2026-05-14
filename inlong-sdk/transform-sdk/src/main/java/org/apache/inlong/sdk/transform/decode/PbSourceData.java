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

package org.apache.inlong.sdk.transform.decode;

import org.apache.inlong.sdk.transform.process.Context;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.table.data.GenericArrayData;
import org.apache.flink.table.data.GenericMapData;
import org.apache.flink.table.data.GenericRowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JsonSourceData
 * 
 */
public class PbSourceData extends AbstractSourceData {

    private static final Logger LOG = LoggerFactory.getLogger(PbSourceData.class);

    public static final String ROOT_KEY = "$root.";

    public static final String CHILD_KEY = "$child.";

    private Descriptors.Descriptor rootDesc;

    private Descriptors.Descriptor childDesc;

    private Map<String, List<PbNode>> columnNodeMap = new ConcurrentHashMap<>();

    private DynamicMessage root;

    private List<DynamicMessage> childRoot;

    protected Charset srcCharset;

    /**
     * Constructor
     */
    public PbSourceData(DynamicMessage root, List<DynamicMessage> childRoot,
            Descriptors.Descriptor rootDesc, Descriptors.Descriptor childDesc,
            Map<String, List<PbNode>> columnNodeMap,
            Charset srcCharset, Context context) {
        this.root = root;
        this.childRoot = childRoot;
        this.rootDesc = rootDesc;
        this.childDesc = childDesc;
        this.columnNodeMap = columnNodeMap;
        this.srcCharset = srcCharset;
        this.context = context;
    }

    /**
     * Constructor
     */
    public PbSourceData(DynamicMessage root,
            Descriptors.Descriptor rootDesc,
            Map<String, List<PbNode>> columnNodeMap,
            Charset srcCharset) {
        this.root = root;
        this.rootDesc = rootDesc;
        this.columnNodeMap = columnNodeMap;
        this.srcCharset = srcCharset;
    }

    /**
     * getRowCount
     * @return
     */
    @Override
    public int getRowCount() {
        if (this.childRoot == null) {
            return 1;
        } else {
            return this.childRoot.size();
        }
    }

    /**
     * getField
     * @param rowNum
     * @param fieldName
     * @return
     */
    @Override
    public Object getField(int rowNum, String fieldName) {
        Object fieldValue = "";
        try {
            if (isContextField(fieldName)) {
                return getContextField(fieldName);
            }
            if (StringUtils.startsWith(fieldName, ROOT_KEY)) {
                fieldValue = this.getRootField(fieldName);
            } else if (StringUtils.startsWith(fieldName, CHILD_KEY)) {
                if (childRoot != null && rowNum < childRoot.size()) {
                    fieldValue = this.getChildField(rowNum, fieldName);
                }
            }
            return fieldValue;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        return fieldValue;
    }

    /**
     * getRootField
     * @param fieldName
     * @return
     */
    private Object getRootField(String srcFieldName) {
        List<PbNode> childNodes = this.columnNodeMap.get(srcFieldName);
        if (childNodes == null) {
            String fieldName = srcFieldName.substring(ROOT_KEY.length());
            childNodes = PbNode.parseNodePath(rootDesc, fieldName);
            if (childNodes == null) {
                childNodes = new ArrayList<>();
            }
            this.columnNodeMap.put(srcFieldName, childNodes);
        }
        // error config
        if (childNodes.size() == 0) {
            return "";
        }
        // parse other node
        Object fieldValue = this.getNodeValue(childNodes, root);
        return fieldValue;
    }

    /**
     * getChildField
     * @param rowNum
     * @param srcFieldName
     * @return
     */
    private Object getChildField(int rowNum, String srcFieldName) {
        if (this.childRoot == null || this.childDesc == null) {
            return "";
        }
        List<PbNode> childNodes = this.columnNodeMap.get(srcFieldName);
        if (childNodes == null) {
            String fieldName = srcFieldName.substring(CHILD_KEY.length());
            childNodes = PbNode.parseNodePath(childDesc, fieldName);
            if (childNodes == null) {
                childNodes = new ArrayList<>();
            }
            this.columnNodeMap.put(srcFieldName, childNodes);
        }
        // error config
        if (childNodes.size() == 0) {
            return "";
        }
        // parse other node
        DynamicMessage child = childRoot.get(rowNum);
        Object fieldValue = this.getNodeValue(childNodes, child);
        return fieldValue;
    }

    /**
     * getNodeValue
     * @param childNodes
     * @param root
     * @return
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object getNodeValue(List<PbNode> childNodes, DynamicMessage root) {
        DynamicMessage current = root;
        for (int i = 0; i < childNodes.size(); i++) {
            PbNode node = childNodes.get(i);
            Object nodeValue = current.getField(node.getFieldDesc());
            if (nodeValue == null) {
                // error data
                break;
            }
            if (!node.isLastNode()) {
                if (node.isArray()) {
                    current = (DynamicMessage) ((List) nodeValue).get(node.getArrayIndex());
                } else if (node.isMap()) {
                    List<DynamicMessage> nodeValueList = (List<DynamicMessage>) nodeValue;
                    DynamicMessage newCurrent = null;
                    for (DynamicMessage subnodeValue : nodeValueList) {
                        String keyValue = String.valueOf(subnodeValue.getField(node.getMapKeyDesc()));
                        if (StringUtils.equals(keyValue, node.getMapKey())) {
                            newCurrent = (DynamicMessage) subnodeValue.getField(node.getMapValueDesc());
                            break;
                        }
                    }
                    if (newCurrent == null) {
                        return null;
                    }
                    current = newCurrent;
                } else {
                    current = (DynamicMessage) nodeValue;
                }
                continue;
            }
            // last node
            if (node.isArray()) {
                return buildStructData(node.getMessageType(), ((List) nodeValue).get(node.getArrayIndex()));
            } else if (node.isMap()) {
                List<DynamicMessage> nodeValueList = (List<DynamicMessage>) nodeValue;
                Object fieldValue = null;
                for (DynamicMessage subnodeValue : nodeValueList) {
                    String keyValue = String.valueOf(subnodeValue.getField(node.getMapKeyDesc()));
                    if (StringUtils.equals(keyValue, node.getMapKey())) {
                        fieldValue = subnodeValue.getField(node.getMapValueDesc());
                        break;
                    }
                }
                return this.buildFieldValue(node.getFieldDesc(), fieldValue, false);
            } else if (node.isMapType()) {
                return this.buildStructData(node.getMessageType(), nodeValue);
            } else if (node.getFieldDesc().isRepeated()) {
                List<Object> valueList = (List) nodeValue;
                List<Object> result = new ArrayList<>(valueList.size());
                for (Object value : valueList) {
                    result.add(this.buildFieldValue(node.getFieldDesc(), value, false));
                }
                return new GenericArrayData(result.toArray());
            } else {
                return this.buildFieldValue(node.getFieldDesc(), nodeValue, false);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object buildFieldValue(FieldDescriptor fieldDesc, Object nodeValue, boolean isRepeated) {
        if (nodeValue == null) {
            return null;
        }
        switch (fieldDesc.getJavaType()) {
            case STRING:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BOOLEAN:
            case ENUM:
                return nodeValue;
            case BYTE_STRING:
                return ((ByteString) nodeValue).toByteArray();
            case MESSAGE: {
                if (!isRepeated) {
                    return this.buildStructData(fieldDesc.getMessageType(), nodeValue);
                } else if (PbNode.isMapDescriptor(fieldDesc.getMessageType())) {
                    return this.buildStructData(fieldDesc.getMessageType(), nodeValue);
                }
                List<DynamicMessage> valueList = (List<DynamicMessage>) nodeValue;
                List<Object> result = new ArrayList<>(valueList.size());
                for (DynamicMessage value : valueList) {
                    result.add(this.buildStructData(fieldDesc.getMessageType(), value));
                }
                return new GenericArrayData(result.toArray());
            }
            default:
                return String.valueOf(nodeValue);
        }
    }

    @SuppressWarnings("unchecked")
    protected Object buildStructData(Descriptors.Descriptor messageType, Object nodeValue) {
        // map
        if (PbNode.isMapDescriptor(messageType)) {
            Descriptors.FieldDescriptor keyField = messageType.findFieldByNumber(1);
            Descriptors.FieldDescriptor valueField = messageType.findFieldByNumber(2);
            List<DynamicMessage> subNodeValueList = (List<DynamicMessage>) nodeValue;
            Map<Object, Object> result = new HashMap<>();
            for (DynamicMessage subnodeValue : subNodeValueList) {
                Object keyValue = buildFieldValue(keyField, subnodeValue.getField(keyField), false);
                Object valueValue = buildFieldValue(valueField, subnodeValue.getField(valueField), false);
                result.put(keyValue, valueValue);
            }
            return new GenericMapData(result);
        }
        // struct
        DynamicMessage msgObj = (DynamicMessage) nodeValue;
        GenericRowData result = new GenericRowData(messageType.getFields().size());
        int index = 0;
        for (FieldDescriptor fieldDesc : messageType.getFields()) {
            Object fieldValue = msgObj.getField(fieldDesc);
            if (fieldValue == null) {
                result.setField(index++, null);
                continue;
            }
            Object fieldResult = this.buildFieldValue(fieldDesc, fieldValue, false);
            result.setField(index++, fieldResult);
        }
        return result;
    }
}

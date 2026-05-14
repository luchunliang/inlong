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
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.DynamicMessage;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.table.data.GenericArrayData;
import org.apache.flink.table.data.GenericMapData;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.binary.BinaryStringData;
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object getField(int rowNum, String fieldName) {
        try {
            if (isContextField(fieldName)) {
                return getContextField(fieldName);
            }
            Object fieldValue = findFieldNode(rowNum, fieldName);
            List<PbNode> childNodes = this.columnNodeMap.get(fieldName);
            if (childNodes == null || childNodes.size() == 0) {
                return null;
            }
            PbNode lastNode = childNodes.get(childNodes.size() - 1);
            // primitive
            if (lastNode.isPrimitiveType()) {
                if (fieldValue instanceof ByteString) {
                    ByteString byteString = (ByteString) fieldValue;
                    return byteString.toByteArray();
                } else {
                    return fieldValue;
                }
            }
            // struct
            if (lastNode.isStructType()) {
                if (!(fieldValue instanceof DynamicMessage)) {
                    return null;
                }
                return buildStructData(lastNode.getFieldDesc().getMessageType(), (DynamicMessage) fieldValue);
            }
            // array
            if (lastNode.isArrayType()) {
                if (!lastNode.isHasArrayIndex()) {
                    if (!(fieldValue instanceof List)) {
                        return null;
                    }
                    List<Object> valueList = (List) fieldValue;
                    List<Object> result = new ArrayList<>(valueList.size());
                    for (Object value : valueList) {
                        result.add(this.buildFieldValue(lastNode.getFieldDesc(), value));
                    }
                    return new GenericArrayData(result.toArray());
                }
                return this.buildFieldValue(lastNode.getFieldDesc(), fieldValue);
            }
            // map
            if (lastNode.isMapType()) {
                if (!lastNode.isHasMapKey()) {
                    return buildMapData(lastNode.getFieldDesc().getMessageType(), fieldValue);
                }
                return this.buildFieldValue(lastNode.getMapValueDesc(), fieldValue);
            }
            return null;
        } catch (Exception e) {
            LOG.error("fail to getField,error:{},rowNum:{},fieldName:{}", e.getMessage(), rowNum, fieldName, e);
            return null;
        }
    }

    private Object buildFieldValue(FieldDescriptor fieldDesc, Object nodeValue) {
        if (fieldDesc == null || nodeValue == null) {
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
            case MESSAGE:
                return this.buildStructData(fieldDesc.getMessageType(), nodeValue);
            default:
                return String.valueOf(nodeValue);
        }
    }

    protected Object buildStructData(Descriptors.Descriptor messageType, Object nodeValue) {
        // map
        if (PbNode.isMapDescriptor(messageType)) {
            return this.buildMapData(messageType, nodeValue);
        }
        // struct
        if (!(nodeValue instanceof DynamicMessage)) {
            return null;
        }
        DynamicMessage msgObj = (DynamicMessage) nodeValue;
        GenericRowData result = new GenericRowData(messageType.getFields().size());
        int index = 0;
        for (FieldDescriptor fieldDesc : messageType.getFields()) {
            Object fieldValue = msgObj.getField(fieldDesc);
            if (fieldValue == null) {
                result.setField(index++, null);
                continue;
            }
            // field
            if (!fieldDesc.isRepeated()) {
                Object fieldResult = this.buildFieldValue(fieldDesc, fieldValue);
                result.setField(index++, fieldResult);
                continue;
            }
            // array
            if (!(fieldValue instanceof List)) {
                result.setField(index++, null);
                continue;
            }
            // map
            if (fieldDesc.getJavaType().equals(JavaType.MESSAGE)
                    && PbNode.isMapDescriptor(fieldDesc.getMessageType())) {
                result.setField(index++, buildMapData(fieldDesc.getMessageType(), fieldValue));
            } else {
                List<?> valueList = (List<?>) fieldValue;
                List<Object> fieldResult = new ArrayList<>(valueList.size());
                for (Object value : valueList) {
                    fieldResult.add(this.buildFieldValue(fieldDesc, value));
                }
                result.setField(index++, new GenericArrayData(fieldResult.toArray()));
            }
        }
        return result;
    }

    protected Object buildMapData(Descriptors.Descriptor messageType, Object nodeValue) {
        if (!(nodeValue instanceof List)) {
            return null;
        }
        Descriptors.FieldDescriptor keyField = messageType.findFieldByNumber(1);
        Descriptors.FieldDescriptor valueField = messageType.findFieldByNumber(2);
        List<?> subNodeValueList = (List<?>) nodeValue;
        Map<Object, Object> result = new HashMap<>();
        for (Object value : subNodeValueList) {
            if (!(value instanceof DynamicMessage)) {
                continue;
            }
            DynamicMessage subnodeValue = (DynamicMessage) value;
            Object keyValue = buildFieldValue(keyField, subnodeValue.getField(keyField));
            Object valueValue = buildFieldValue(valueField, subnodeValue.getField(valueField));
            result.put(keyValue, valueValue);
        }
        return new GenericMapData(result);
    }

    /**
     * get rootDesc
     * @return the rootDesc
     */
    public Descriptors.Descriptor getRootDesc() {
        return rootDesc;
    }

    /**
     * get childDesc
     * @return the childDesc
     */
    public Descriptors.Descriptor getChildDesc() {
        return childDesc;
    }

    /**
     * get root
     * @return the root
     */
    public DynamicMessage getRoot() {
        return root;
    }

    /**
     * get childRoot
     * @return the childRoot
     */
    public List<DynamicMessage> getChildRoot() {
        return childRoot;
    }

    public Object findFieldNode(int rowNum, String fieldName) {
        Object fieldValue = "";
        try {
            if (StringUtils.startsWith(fieldName, ROOT_KEY)) {
                fieldValue = this.findRootField(fieldName);
            } else if (StringUtils.startsWith(fieldName, CHILD_KEY)) {
                if (childRoot != null && rowNum < childRoot.size()) {
                    fieldValue = this.findChildField(rowNum, fieldName);
                }
            } else {
                List<PbNode> childNodes = this.columnNodeMap.get(fieldName);
                if (childNodes == null) {
                    childNodes = PbNode.parseNodePath(rootDesc, fieldName);
                    if (childNodes == null) {
                        childNodes = new ArrayList<>();
                    }
                    this.columnNodeMap.put(fieldName, childNodes);
                }
                // error config
                if (childNodes.size() == 0) {
                    return "";
                }
                // parse other node
                fieldValue = this.findNodeValue(childNodes, root);
            }
            return fieldValue;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        return fieldValue;
    }

    public List<PbNode> parseStructNodeList(String srcFieldName, Descriptor currentDesc) {
        List<PbNode> childNodes = this.columnNodeMap.get(srcFieldName);
        if (childNodes == null) {
            String fieldName = srcFieldName;
            if (StringUtils.startsWith(fieldName, ROOT_KEY)) {
                fieldName = srcFieldName.substring(ROOT_KEY.length());
            } else if (StringUtils.startsWith(fieldName, CHILD_KEY)) {
                fieldName = srcFieldName.substring(CHILD_KEY.length());
            }
            childNodes = PbNode.parseNodePath(currentDesc, fieldName);
            if (childNodes == null) {
                childNodes = new ArrayList<>();
            }
            this.columnNodeMap.put(srcFieldName, childNodes);
        }
        return childNodes;
    }

    private Object findRootField(String srcFieldName) {
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
            return null;
        }
        // parse other node
        Object fieldValue = this.findNodeValue(childNodes, root);
        return fieldValue;
    }

    private Object findChildField(int rowNum, String srcFieldName) {
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
        Object fieldValue = this.findNodeValue(childNodes, child);
        return fieldValue;
    }

    // @SuppressWarnings({"rawtypes", "unchecked"})
    public Object findNodeValue(List<PbNode> childNodes, DynamicMessage root) {
        DynamicMessage current = root;
        for (int i = 0; i < childNodes.size(); i++) {
            PbNode node = childNodes.get(i);
            Object nodeValue = current.getField(node.getFieldDesc());
            if (nodeValue == null) {
                // error data
                break;
            }
            if (node.isLastNode()) {
                // primitive
                if (node.isPrimitiveType()) {
                    if (nodeValue instanceof ByteString) {
                        ByteString byteString = (ByteString) nodeValue;
                        return byteString.toByteArray();
                    } else if (node.getFieldDesc().getJavaType().equals(JavaType.STRING)) {
                        return new BinaryStringData(String.valueOf(nodeValue));
                    } else {
                        return nodeValue;
                    }
                }
                // struct
                if (node.isStructType()) {
                    return nodeValue;
                }
                // array
                if (node.isArrayType()) {
                    if (!node.isHasArrayIndex()) {
                        return nodeValue;
                    }
                    if (!(nodeValue instanceof List)) {
                        return null;
                    }
                    List<?> nodeValueList = (List<?>) nodeValue;
                    return nodeValueList.get(node.getArrayIndex());
                }
                // map
                if (node.isMapType()) {
                    if (!node.isHasMapKey()) {
                        return nodeValue;
                    }
                    if (!(nodeValue instanceof List)) {
                        return null;
                    }
                    List<?> nodeValueList = (List<?>) nodeValue;
                    Object fieldValue = null;
                    for (Object subnodeValue : nodeValueList) {
                        if (!(subnodeValue instanceof DynamicMessage)) {
                            continue;
                        }
                        DynamicMessage msg = (DynamicMessage) subnodeValue;
                        String keyValue = String.valueOf(msg.getField(node.getMapKeyDesc()));
                        if (StringUtils.equals(keyValue, node.getMapKey())) {
                            fieldValue = msg.getField(node.getMapValueDesc());
                            break;
                        }
                    }
                    return fieldValue;
                }
                return null;
            } else {
                // primitive
                if (node.isPrimitiveType()) {
                    return null;
                }
                // struct
                if (node.isStructType()) {
                    if (!(nodeValue instanceof DynamicMessage)) {
                        return null;
                    }
                    current = (DynamicMessage) nodeValue;
                    continue;
                }
                // array
                if (node.isArrayType()) {
                    if (!node.isHasArrayIndex()) {
                        return null;
                    }
                    if (!(nodeValue instanceof List)) {
                        return null;
                    }
                    List<?> nodeValueList = (List<?>) nodeValue;
                    Object newNode = nodeValueList.get(node.getArrayIndex());
                    if (!(newNode instanceof DynamicMessage)) {
                        return null;
                    }
                    current = (DynamicMessage) newNode;
                    continue;
                }
                // map
                if (node.isMapType()) {
                    if (!node.isHasMapKey()) {
                        return null;
                    }
                    if (!(nodeValue instanceof List)) {
                        return null;
                    }
                    List<?> nodeValueList = (List<?>) nodeValue;
                    Object fieldValue = null;
                    for (Object subnodeValue : nodeValueList) {
                        if (!(subnodeValue instanceof DynamicMessage)) {
                            continue;
                        }
                        DynamicMessage msg = (DynamicMessage) subnodeValue;
                        String keyValue = String.valueOf(msg.getField(node.getMapKeyDesc()));
                        if (StringUtils.equals(keyValue, node.getMapKey())) {
                            fieldValue = msg.getField(node.getMapValueDesc());
                            break;
                        }
                    }
                    if (fieldValue == null || !(fieldValue instanceof DynamicMessage)) {
                        return null;
                    }
                    current = (DynamicMessage) fieldValue;
                    continue;
                }
                return null;
            }
        }
        return null;
    }
}

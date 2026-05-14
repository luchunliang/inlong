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

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import lombok.Data;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * PbNode
 * 
 */
@Data
public class PbNode {

    public static final Logger LOG = LoggerFactory.getLogger(PbNode.class);

    private String name;
    private FieldDescriptor fieldDesc;
    private boolean isLastNode = false;
    // primitive
    private boolean isPrimitiveType = false;
    // array
    private boolean isArrayType = false;
    private boolean hasArrayIndex = false;
    private Integer arrayIndex;
    // struct
    private boolean isStructType = false;
    // map
    private boolean isMapType = false;
    private boolean hasMapKey = false;
    private String mapKey;
    private FieldDescriptor mapKeyDesc;
    private FieldDescriptor mapValueDesc;

    public PbNode(Descriptors.Descriptor parentDesc, String nodeString, boolean isLastNode) {
        try {
            if (parentDesc == null) {
                return;
            }
            this.isLastNode = isLastNode;
            // parse name & index
            int beginIndex = nodeString.indexOf('(');
            String indexString = null;
            if (beginIndex < 0) {
                this.name = nodeString;
            } else {
                this.name = StringUtils.trim(nodeString.substring(0, beginIndex));
                int endIndex = nodeString.lastIndexOf(')');
                if (endIndex >= 0) {
                    indexString = nodeString.substring(beginIndex + 1, endIndex);
                }
            }
            // field desc
            this.fieldDesc = parentDesc.findFieldByName(name);
            if (this.fieldDesc == null) {
                return;
            }
            // map
            if (this.fieldDesc.getJavaType() == JavaType.MESSAGE
                    && isMapDescriptor(this.fieldDesc.getMessageType())) {
                this.isMapType = true;
                this.mapKeyDesc = this.fieldDesc.getMessageType().getFields().get(0);
                this.mapValueDesc = this.fieldDesc.getMessageType().getFields().get(1);
                if (indexString != null) {
                    this.hasMapKey = true;
                    this.mapKey = indexString;
                }
                return;
            }
            // array
            if (this.fieldDesc.isRepeated()) {
                this.isArrayType = true;
                this.arrayIndex = NumberUtils.toInt(indexString, -1);
                if (arrayIndex >= 0) {
                    this.hasArrayIndex = true;
                }
                return;
            }
            // struct
            if (this.fieldDesc.getJavaType() == JavaType.MESSAGE) {
                this.isStructType = true;
                return;
            }
            // primitive
            this.isPrimitiveType = true;
        } catch (RuntimeException t) {
            LOG.error("Fail to PbNode,error:{},fullName:{},nodePath:{},isLastNode:{}", parentDesc.getName(),
                    t.getMessage(), nodeString, isLastNode, t);
            throw t;
        }
    }

    /**
     * parseNodePath
     * @param rootDesc
     * @param nodePath
     * @return
     */
    public static List<PbNode> parseNodePath(Descriptors.Descriptor rootDesc, String nodePath) {
        if (StringUtils.isBlank(nodePath)) {
            return null;
        }
        List<PbNode> nodes = new ArrayList<>();
        String[] nodeStrings = nodePath.split("\\.");
        int lastIndex = nodeStrings.length - 1;
        Descriptors.Descriptor current = rootDesc;
        for (int i = 0; i <= lastIndex; i++) {
            if (current == null) {
                return null;
            }
            String nodeString = nodeStrings[i];
            PbNode pbNode = new PbNode(current, nodeString, (i == lastIndex));
            if (pbNode.getFieldDesc() == null) {
                return null;
            }
            if (pbNode.isPrimitiveType()) {
                current = null;
                nodes.add(pbNode);
                continue;
            } else if (pbNode.isArrayType()) {
                if (pbNode.getFieldDesc().getJavaType() == JavaType.MESSAGE) {
                    current = pbNode.getFieldDesc().getMessageType();
                } else {
                    current = null;
                }
                nodes.add(pbNode);
                continue;
            } else if (pbNode.isMapType()) {
                if (pbNode.isHasMapKey()) {
                    if (pbNode.getMapValueDesc().getJavaType() == JavaType.MESSAGE) {
                        current = pbNode.getMapValueDesc().getMessageType();
                    } else {
                        current = null;
                    }
                } else {
                    current = null;
                }
                nodes.add(pbNode);
                continue;
            } else if (pbNode.isStructType()) {
                current = pbNode.getFieldDesc().getMessageType();
                nodes.add(pbNode);
                continue;
            } else {
                return null;
            }
        }
        return nodes;
    }

    public static boolean isMapDescriptor(Descriptors.Descriptor descriptor) {
        if (descriptor.getOptions().getMapEntry()) {
            return true;
        }

        if (descriptor.getFields().size() == 2) {
            Descriptors.FieldDescriptor keyField = descriptor.findFieldByNumber(1);
            Descriptors.FieldDescriptor valueField = descriptor.findFieldByNumber(2);

            if (keyField != null && valueField != null &&
                    "key".equals(keyField.getName()) &&
                    "value".equals(valueField.getName())) {
                return true;
            }
        }
        return false;
    }
}

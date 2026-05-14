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

package org.apache.inlong.sdk.transform.process.processor;

import org.apache.inlong.common.pojo.sort.dataflow.field.format.ArrayFormatInfo;
import org.apache.inlong.common.pojo.sort.dataflow.field.format.BinaryFormatInfo;
import org.apache.inlong.common.pojo.sort.dataflow.field.format.FormatInfo;
import org.apache.inlong.common.pojo.sort.dataflow.field.format.LongFormatInfo;
import org.apache.inlong.common.pojo.sort.dataflow.field.format.MapFormatInfo;
import org.apache.inlong.common.pojo.sort.dataflow.field.format.RowFormatInfo;
import org.apache.inlong.common.pojo.sort.dataflow.field.format.StringFormatInfo;
import org.apache.inlong.sdk.transform.decode.SourceDecoderFactory;
import org.apache.inlong.sdk.transform.encode.SinkEncoderFactory;
import org.apache.inlong.sdk.transform.pojo.FieldInfo;
import org.apache.inlong.sdk.transform.pojo.PbSourceInfo;
import org.apache.inlong.sdk.transform.pojo.RowDataSinkInfo;
import org.apache.inlong.sdk.transform.pojo.TransformConfig;
import org.apache.inlong.sdk.transform.process.TransformProcessor;

import org.apache.flink.table.data.GenericMapData;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

public class TestPb2RowDataProcessor extends AbstractProcessorTestBase {

    @Test
    public void testPb2RowData() throws Exception {
        String transformBase64 = this.getPbTestDescription();
        PbSourceInfo pbSource = new PbSourceInfo("UTF-8", transformBase64, "SdkDataRequest", "msgs");
        String[] fieldNames = new String[]{"sid", "packageID", "msgTime",
                "binaryMsg", "mapExtinfo", "structMsgItem", "listMsgs"};
        List<FieldInfo> sinkFields = this.getTestFieldList("sid", "packageID", "msgTime");
        // binaryMsg
        FieldInfo binaryMsg = new FieldInfo("binaryMsg");
        BinaryFormatInfo binaryMsgFormat = new BinaryFormatInfo(Integer.MAX_VALUE);
        binaryMsg.setFormatInfo(binaryMsgFormat);
        sinkFields.add(binaryMsg);
        // mapExtinfo
        FieldInfo mapExtinfo = new FieldInfo("mapExtinfo");
        MapFormatInfo mapExtinfoFormat = new MapFormatInfo(new StringFormatInfo(), new StringFormatInfo());
        mapExtinfo.setFormatInfo(mapExtinfoFormat);
        sinkFields.add(mapExtinfo);
        // structMsgItem
        FieldInfo structMsgItem = new FieldInfo("structMsgItem");
        String[] structMsgItemFields = new String[]{"msg", "msgTime", "extinfo"};
        FormatInfo[] structMsgItemFormats = new FormatInfo[]{
                new BinaryFormatInfo(Integer.MAX_VALUE),
                new LongFormatInfo(),
                new MapFormatInfo(new StringFormatInfo(), new StringFormatInfo())
        };
        RowFormatInfo structMsgItemFormat = new RowFormatInfo(structMsgItemFields, structMsgItemFormats);
        structMsgItem.setFormatInfo(structMsgItemFormat);
        sinkFields.add(structMsgItem);
        // listMsgs
        FieldInfo listMsgs = new FieldInfo("listMsgs");
        ArrayFormatInfo listMsgsFormat = new ArrayFormatInfo(structMsgItemFormat);
        listMsgs.setFormatInfo(listMsgsFormat);
        sinkFields.add(listMsgs);
        // sink
        RowDataSinkInfo rowSink = new RowDataSinkInfo("UTF-8", sinkFields);
        // sql
        String transformSql = "select $root.sid,$root.packageID,$child.msgTime"
                + ",$child.msg as binaryMsg,"
                + "$child.extinfo as mapExtinfo,"
                + "$root.msgs(1) as structMsgItem,"
                + "$root.msgs as listMsgs from source";
        TransformConfig config = new TransformConfig(transformSql);
        // case1
        TransformProcessor<String, RowData> processor = TransformProcessor
                .create(config, SourceDecoderFactory.createPbDecoder(pbSource),
                        SinkEncoderFactory.createRowEncoder(rowSink));
        byte[] srcBytes = this.getPbTestData();
        List<RowData> output = processor.transformForBytes(srcBytes, new HashMap<>());
        Assert.assertEquals(2, output.size());
        // 0
        Assert.assertEquals(output.get(0).getString(0).toString(), "sid");
        Assert.assertEquals(output.get(0).getString(1).toString(), "1");
        Assert.assertEquals(output.get(0).getString(2).toString(), "1713243918000");
        Assert.assertEquals(new String(output.get(0).getBinary(3)), "msgValue4");
        Assert.assertEquals(((GenericMapData) output.get(0).getMap(4)).get("key"), "value");
        Assert.assertEquals(((GenericMapData) output.get(0).getMap(4)).get("value"), null);
        Assert.assertEquals(new String(((GenericRowData) output.get(0).getRow(5, 3)).getBinary(0)), "msgValue42");
        Assert.assertEquals(((GenericRowData) output.get(0).getRow(5, 3)).getLong(1), 1713243918002L);
        Assert.assertEquals(((GenericRowData) output.get(0).getRow(5, 3)).getMap(2).size(), 1);
        Assert.assertEquals(output.get(0).getArray(6).size(), 2);
        // 1
        Assert.assertEquals(output.get(1).getString(0).toString(), "sid");
        Assert.assertEquals(output.get(1).getString(1).toString(), "1");
        Assert.assertEquals(output.get(1).getString(2).toString(), "1713243918002");
        Assert.assertEquals(new String(output.get(1).getBinary(3)), "msgValue42");
        Assert.assertEquals(((GenericMapData) output.get(1).getMap(4)).get("key2"), "value2");
        Assert.assertEquals(((GenericMapData) output.get(1).getMap(4)).get("value"), null);
        Assert.assertEquals(new String(((GenericRowData) output.get(1).getRow(5, 3)).getBinary(0)), "msgValue42");
        Assert.assertEquals(((GenericRowData) output.get(1).getRow(5, 3)).getLong(1), 1713243918002L);
        Assert.assertEquals(((GenericRowData) output.get(1).getRow(5, 3)).getMap(2).size(), 1);
        Assert.assertEquals(output.get(1).getArray(6).size(), 2);
    }
}

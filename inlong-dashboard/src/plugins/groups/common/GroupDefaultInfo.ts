/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { DataWithBackend } from '@/plugins/DataWithBackend';
import { RenderRow } from '@/plugins/RenderRow';
import { RenderList } from '@/plugins/RenderList';
import i18n from '@/i18n';
import UserSelect from '@/ui/components/UserSelect';
import { statusList, genStatusTag } from './status';
import { groups, defaultValue } from '..';
import { timestampFormat } from '@/core/utils';

const { I18nMap, I18n } = DataWithBackend;
const { FieldList, FieldDecorator } = RenderRow;
const { ColumnList, ColumnDecorator } = RenderList;

export class GroupDefaultInfo implements DataWithBackend, RenderRow, RenderList {
  static I18nMap = I18nMap;
  static FieldList = FieldList;
  static ColumnList = ColumnList;

  readonly id: number;

  @FieldDecorator({
    type: 'input',
    props: {
      maxLength: 200,
    },
    rules: [
      { required: true },
      {
        pattern: /^[a-zA-Z0-9_.-]+$/,
        message: i18n.t('meta.Group.InlongGroupIdRules'),
      },
    ],
  })
  @ColumnDecorator()
  @I18n('meta.Group.InlongGroupId')
  inlongGroupId: string;

  @FieldDecorator({
    type: 'input',
    props: {
      maxLength: 200,
    },
  })
  @I18n('meta.Group.InlongGroupName')
  name: string;

  @FieldDecorator({
    type: UserSelect,
    extra: i18n.t('meta.Group.InlongGroupOwnersExtra'),
    rules: [{ required: true }],
    props: {
      mode: 'multiple',
      currentUserClosable: false,
    },
  })
  @ColumnDecorator()
  @I18n('meta.Group.InlongGroupOwners')
  inCharges: string;

  @FieldDecorator({
    type: 'textarea',
    props: {
      showCount: true,
      maxLength: 100,
    },
  })
  @I18n('meta.Group.InlongGroupIntroduction')
  description: string;

  @FieldDecorator({
    type: 'radio',
    initialValue: defaultValue,
    rules: [{ required: true }],
    props: {
      options: groups.filter(item => Boolean(item.value)),
    },
  })
  @ColumnDecorator({
    width: 300,
    render: type => groups.find(c => c.value === type)?.label || type,
  })
  @I18n('meta.Group.MQType')
  mqType: string;

  @FieldDecorator({
    type: 'text',
  })
  @I18n('MQ Resource')
  readonly mqResource: string;

  @FieldDecorator({
    type: 'select',
    props: {
      allowClear: true,
      options: statusList,
      dropdownMatchSelectWidth: false,
    },
    visible: false,
  })
  @ColumnDecorator({
    render: text => genStatusTag(text),
  })
  @I18n('basic.Status')
  readonly status: string;

  @ColumnDecorator({
    render: text => timestampFormat(text),
  })
  @I18n('basic.CreateTime')
  readonly createTime: string;

  @ColumnDecorator()
  @I18n('basic.Creator')
  readonly creator: string;

  @ColumnDecorator()
  @I18n('basic.Modifier')
  readonly modifier: string;

  parse(data) {
    return data;
  }

  stringify(data) {
    return data;
  }

  renderRow() {
    const constructor = this.constructor as typeof GroupDefaultInfo;
    return constructor.FieldList;
  }

  renderList() {
    const constructor = this.constructor as typeof GroupDefaultInfo;
    return constructor.ColumnList;
  }
}

<template>
  <div class="right-board">
    <el-tabs v-model="currentTab" stretch class="center-tabs">
      <el-tab-pane :label="$tr(&quot;组件属性&quot;)" name="field" />
      <el-tab-pane :label="$tr(&quot;表单属性&quot;)" name="form" />
    </el-tabs>
    <div class="field-box">
      <a class="document-link" target="_blank" :href="documentLink" :title="$tr(&quot;查看组件文档&quot;)">
        <el-icon>
          <Link />
        </el-icon>
      </a>
      <el-scrollbar class="right-scrollbar">
        <!-- 组件属性 -->
        <el-form v-show="currentTab === 'field' && showField" size="default" label-width="90px" label-position="top"
          style="">
          <el-form-item v-if="activeData.changeTag" :label="$tr(&quot;组件类型&quot;)">
            <el-select v-model="activeData.tagIcon" :placeholder="$tr(&quot;请选择组件类型&quot;)" :style="{ width: '100%' }" @change="tagChange">
              <el-option-group v-for="group in tagList" :key="group.label" :label="group.label">
                <el-option v-for="item in group.options" :key="item.label" :label="item.label" :value="item.tagIcon">
                  <svg-icon class="node-icon" :icon-class="item.tagIcon" style="margin-right: 10px;" />
                  <span> {{ item.label }}</span>
                </el-option>
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item v-if="activeData.vModel !== undefined" :label="$tr(&quot;字段名&quot;)">
            <el-input v-model="activeData.vModel" :placeholder="$tr(&quot;请输入字段名（v-model）&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.componentName !== undefined" :label="$tr(&quot;组件名&quot;)">
            {{ activeData.componentName }}
          </el-form-item>
          <el-form-item v-if="activeData.label !== undefined" :label="$tr(&quot;标题&quot;)">
            <el-input v-model="activeData.label" :placeholder="$tr(&quot;请输入标题&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.placeholder !== undefined" :label="$tr(&quot;占位提示&quot;)">
            <el-input v-model="activeData.placeholder" :placeholder="$tr(&quot;请输入占位提示&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData['start-placeholder'] !== undefined" :label="$tr(&quot;开始占位&quot;)">
            <el-input v-model="activeData['start-placeholder']" :placeholder="$tr(&quot;请输入占位提示&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData['end-placeholder'] !== undefined" :label="$tr(&quot;结束占位&quot;)">
            <el-input v-model="activeData['end-placeholder']" :placeholder="$tr(&quot;请输入占位提示&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.span !== undefined" :label="$tr(&quot;表单栅格&quot;)">
            <el-slider v-model="activeData.span" :max="24" :min="1" :marks="{ 12: '' }" @change="spanChange" />
          </el-form-item>
          <el-form-item v-if="activeData.layout === 'rowFormItem'" :label="$tr(&quot;栅格间隔&quot;)">
            <el-input-number v-model="activeData.gutter" :min="0" :placeholder="$tr(&quot;栅格间隔&quot;)" />
          </el-form-item>

          <el-form-item v-if="activeData.justify !== undefined" :label="$tr(&quot;水平排列&quot;)">
            <el-select v-model="activeData.justify" :placeholder="$tr(&quot;请选择水平排列&quot;)" :style="{ width: '100%' }">
              <el-option v-for="(item, index) in justifyOptions" :key="index" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="activeData.align !== undefined" :label="$tr(&quot;垂直排列&quot;)">
            <el-radio-group v-model="activeData.align">
              <el-radio-button label="top" />
              <el-radio-button label="middle" />
              <el-radio-button label="bottom" />
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="activeData.labelWidth !== undefined" :label="$tr(&quot;标签宽度&quot;)">
            <el-input v-model.number="activeData.labelWidth" type="number" :placeholder="$tr(&quot;请输入标签宽度&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.style && activeData.style.width !== undefined" :label="$tr(&quot;组件宽度&quot;)">
            <el-input v-model="activeData.style.width" :placeholder="$tr(&quot;请输入组件宽度&quot;)" clearable />
          </el-form-item>
          <el-form-item v-if="activeData.vModel !== undefined" :label="$tr(&quot;默认值&quot;)">
            <el-input :value="setDefaultValue(activeData.defaultValue)" :placeholder="$tr(&quot;请输入默认值&quot;)"
              @input="onDefaultValueInput" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-checkbox-group'" :label="$tr(&quot;至少应选&quot;)">
            <el-input-number :value="activeData.min" :min="0" :placeholder="$tr(&quot;至少应选&quot;)"
              @input="$set(activeData, 'min', $event ? $event : undefined)" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-checkbox-group'" :label="$tr(&quot;最多可选&quot;)">
            <el-input-number :value="activeData.max" :min="0" :placeholder="$tr(&quot;最多可选&quot;)"
              @input="$set(activeData, 'max', $event ? $event : undefined)" />
          </el-form-item>
          <el-form-item v-if="activeData.prepend !== undefined" :label="$tr(&quot;前缀&quot;)">
            <el-input v-model="activeData.prepend" :placeholder="$tr(&quot;请输入前缀&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.append !== undefined" :label="$tr(&quot;后缀&quot;)">
            <el-input v-model="activeData.append" :placeholder="$tr(&quot;请输入后缀&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData['prefix-icon'] !== undefined" :label="$tr(&quot;前图标&quot;)">
            <el-input v-model="activeData['prefix-icon']" :placeholder="$tr(&quot;请输入前图标名称&quot;)">
              <template #append>
                <el-button icon="Pointer" @click="openIconsDialog('prefix-icon')">{{ $tr(" 选择 ") }}</el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item v-if="activeData['suffix-icon'] !== undefined" :label="$tr(&quot;后图标&quot;)">
            <el-input v-model="activeData['suffix-icon']" :placeholder="$tr(&quot;请输入后图标名称&quot;)">
              <template #append>
                <el-button icon="Pointer" @click="openIconsDialog('suffix-icon')">{{ $tr(" 选择 ") }}</el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-cascader'" :label="$tr(&quot;选项分隔符&quot;)">
            <el-input v-model="activeData.separator" :placeholder="$tr(&quot;请输入选项分隔符&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.autosize !== undefined" :label="$tr(&quot;最小行数&quot;)">
            <el-input-number v-model="activeData.autosize.minRows" :min="1" :placeholder="$tr(&quot;最小行数&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.autosize !== undefined" :label="$tr(&quot;最大行数&quot;)">
            <el-input-number v-model="activeData.autosize.maxRows" :min="1" :placeholder="$tr(&quot;最大行数&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.min !== undefined" :label="$tr(&quot;最小值&quot;)">
            <el-input-number v-model="activeData.min" :placeholder="$tr(&quot;最小值&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.max !== undefined" :label="$tr(&quot;最大值&quot;)">
            <el-input-number v-model="activeData.max" :placeholder="$tr(&quot;最大值&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.step !== undefined" :label="$tr(&quot;步长&quot;)">
            <el-input-number v-model="activeData.step" :placeholder="$tr(&quot;步数&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-input-number'" :label="$tr(&quot;精度&quot;)">
            <el-input-number v-model="activeData.precision" :min="0" :placeholder="$tr(&quot;精度&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-input-number'" :label="$tr(&quot;按钮位置&quot;)">
            <el-radio-group v-model="activeData['controls-position']">
              <el-radio-button label="">{{ $tr(" 默认 ") }}</el-radio-button>
              <el-radio-button label="right">{{ $tr(" 右侧 ") }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="activeData.maxlength !== undefined" :label="$tr(&quot;最多输入&quot;)">
            <el-input v-model="activeData.maxlength" :placeholder="$tr(&quot;请输入字符长度&quot;)">
              <template slot="append">{{ $tr(" 个字符 ") }}</template>
            </el-input>
          </el-form-item>
          <el-form-item v-if="activeData['active-text'] !== undefined" :label="$tr(&quot;开启提示&quot;)">
            <el-input v-model="activeData['active-text']" :placeholder="$tr(&quot;请输入开启提示&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData['inactive-text'] !== undefined" :label="$tr(&quot;关闭提示&quot;)">
            <el-input v-model="activeData['inactive-text']" :placeholder="$tr(&quot;请输入关闭提示&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData['active-value'] !== undefined" :label="$tr(&quot;开启值&quot;)">
            <el-input :value="setDefaultValue(activeData['active-value'])" :placeholder="$tr(&quot;请输入开启值&quot;)"
              @input="onSwitchValueInput($event, 'active-value')" />
          </el-form-item>
          <el-form-item v-if="activeData['inactive-value'] !== undefined" :label="$tr(&quot;关闭值&quot;)">
            <el-input :value="setDefaultValue(activeData['inactive-value'])" :placeholder="$tr(&quot;请输入关闭值&quot;)"
              @input="onSwitchValueInput($event, 'inactive-value')" />
          </el-form-item>
          <el-form-item v-if="activeData.type !== undefined && 'el-date-picker' === activeData.tag" :label="$tr(&quot;时间类型&quot;)">
            <el-select v-model="activeData.type" :placeholder="$tr(&quot;请选择时间类型&quot;)" :style="{ width: '100%' }"
              @change="dateTypeChange">
              <el-option v-for="(item, index) in dateOptions" :key="index" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="activeData.name !== undefined" :label="$tr(&quot;文件字段名&quot;)">
            <el-input v-model="activeData.name" :placeholder="$tr(&quot;请输入上传文件字段名&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.accept !== undefined" :label="$tr(&quot;文件类型&quot;)">
            <el-select v-model="activeData.accept" :placeholder="$tr(&quot;请选择文件类型&quot;)" :style="{ width: '100%' }" clearable>
              <el-option :label="$tr(&quot;图片&quot;)" value="image/*" />
              <el-option :label="$tr(&quot;视频&quot;)" value="video/*" />
              <el-option :label="$tr(&quot;音频&quot;)" value="audio/*" />
              <el-option label="excel" value=".xls,.xlsx" />
              <el-option label="word" value=".doc,.docx" />
              <el-option label="pdf" value=".pdf" />
              <el-option label="txt" value=".txt" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="activeData.fileSize !== undefined" :label="$tr(&quot;文件大小&quot;)">
            <el-input v-model.number="activeData.fileSize" :placeholder="$tr(&quot;请输入文件大小&quot;)">
              <el-select slot="append" v-model="activeData.sizeUnit" :style="{ width: '66px' }">
                <el-option label="KB" value="KB" />
                <el-option label="MB" value="MB" />
                <el-option label="GB" value="GB" />
              </el-select>
            </el-input>
          </el-form-item>
          <el-form-item v-if="activeData.action !== undefined" :label="$tr(&quot;上传地址&quot;)">
            <el-input v-model="activeData.action" :placeholder="$tr(&quot;请输入上传地址&quot;)" clearable />
          </el-form-item>
          <el-form-item v-if="activeData['list-type'] !== undefined" :label="$tr(&quot;列表类型&quot;)">
            <el-radio-group v-model="activeData['list-type']" size="small">
              <el-radio-button label="text">
                text
              </el-radio-button>
              <el-radio-button label="picture">
                picture
              </el-radio-button>
              <el-radio-button label="picture-card">
                picture-card
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="activeData.buttonText !== undefined" v-show="'picture-card' !== activeData['list-type']"
            :label="$tr(&quot;按钮文字&quot;)">
            <el-input v-model="activeData.buttonText" :placeholder="$tr(&quot;请输入按钮文字&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData['range-separator'] !== undefined" :label="$tr(&quot;分隔符&quot;)">
            <el-input v-model="activeData['range-separator']" :placeholder="$tr(&quot;请输入分隔符&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData['picker-options'] !== undefined" :label="$tr(&quot;时间段&quot;)">
            <el-input v-model="activeData['picker-options'].selectableRange" :placeholder="$tr(&quot;请输入时间段&quot;)" />
          </el-form-item>
          <el-form-item v-if="activeData.format !== undefined" :label="$tr(&quot;时间格式&quot;)">
            <el-input :value="activeData.format" :placeholder="$tr(&quot;请输入时间格式&quot;)" @input="setTimeValue($event)" />
          </el-form-item>
          <template v-if="['el-checkbox-group', 'el-radio-group', 'el-select'].indexOf(activeData.tag) > -1">
            <el-divider>{{ $tr("选项") }}</el-divider>
            <draggable :list="activeData.options" :animation="340" group="selectItem" handle=".option-drag"
              item-key="label">
              <template #item="{ element, index }">
                <div :key="index" class="select-item">
                  <div class="select-line-icon option-drag">
                    <i class="el-icon-s-operation" />
                  </div>
                  <el-input v-model="element.label" :placeholder="$tr(&quot;选项名&quot;)" size="small" />
                  <el-input :placeholder="$tr(&quot;选项值&quot;)" size="small" :value="element.value"
                    @input="setOptionValue(element, $event)" />
                  <div class="close-btn select-line-icon" @click="activeData.options.splice(index, 1)">
                    <el-icon>
                      <Remove />
                    </el-icon>
                  </div>
                </div>
              </template>
            </draggable>
            <div>
              <el-button icon="CirclePlus" style="margin-left: 8px; margin-top: 10px;" text bg type="primary"
                @click="addSelectItem">{{ $tr(" 添加选项 ") }}</el-button>
            </div>
            <el-divider />
          </template>

          <template v-if="['el-cascader'].indexOf(activeData.tag) > -1">
            <el-divider>{{ $tr("选项") }}</el-divider>
            <el-form-item :label="$tr(&quot;数据类型&quot;)">
              <el-radio-group v-model="activeData.dataType" size="small">
                <el-radio-button label="dynamic">{{ $tr(" 动态数据 ") }}</el-radio-button>
                <el-radio-button label="static">{{ $tr(" 静态数据 ") }}</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <template v-if="activeData.dataType === 'dynamic'">
              <el-form-item :label="$tr(&quot;标签键名&quot;)">
                <el-input v-model="activeData.labelKey" :placeholder="$tr(&quot;请输入标签键名&quot;)" />
              </el-form-item>
              <el-form-item :label="$tr(&quot;值键名&quot;)">
                <el-input v-model="activeData.valueKey" :placeholder="$tr(&quot;请输入值键名&quot;)" />
              </el-form-item>
              <el-form-item :label="$tr(&quot;子级键名&quot;)">
                <el-input v-model="activeData.childrenKey" :placeholder="$tr(&quot;请输入子级键名&quot;)" />
              </el-form-item>
            </template>

            <el-tree v-if="activeData.dataType === 'static'" draggable :data="activeData.options" node-key="id"
              :expand-on-click-node="false" :render-content="renderContent" />
            <div v-if="activeData.dataType === 'static'">
              <el-button icon="CirclePlus" style="margin-left: 0; margin-top: 10px;" type="primary" text bg
                @click="addTreeItem">{{ $tr(" 添加父级 ") }}</el-button>
            </div>
            <el-divider />
          </template>

          <el-form-item v-if="activeData.optionType !== undefined" :label="$tr(&quot;选项样式&quot;)">
            <el-radio-group v-model="activeData.optionType">
              <el-radio-button label="default">{{ $tr(" 默认 ") }}</el-radio-button>
              <el-radio-button label="button">{{ $tr(" 按钮 ") }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="activeData['active-color'] !== undefined" :label="$tr(&quot;开启颜色&quot;)">
            <el-color-picker v-model="activeData['active-color']" />
          </el-form-item>
          <el-form-item v-if="activeData['inactive-color'] !== undefined" :label="$tr(&quot;关闭颜色&quot;)">
            <el-color-picker v-model="activeData['inactive-color']" />
          </el-form-item>

          <el-form-item v-if="activeData['allow-half'] !== undefined" :label="$tr(&quot;允许半选&quot;)">
            <el-switch v-model="activeData['allow-half']" />
          </el-form-item>
          <el-form-item v-if="activeData['show-text'] !== undefined" :label="$tr(&quot;辅助文字&quot;)">
            <el-switch v-model="activeData['show-text']" @change="rateTextChange" />
          </el-form-item>
          <el-form-item v-if="activeData['show-score'] !== undefined" :label="$tr(&quot;显示分数&quot;)">
            <el-switch v-model="activeData['show-score']" @change="rateScoreChange" />
          </el-form-item>
          <el-form-item v-if="activeData['show-stops'] !== undefined" :label="$tr(&quot;显示间断点&quot;)">
            <el-switch v-model="activeData['show-stops']" />
          </el-form-item>
          <el-form-item v-if="activeData.range !== undefined" :label="$tr(&quot;范围选择&quot;)">
            <el-switch v-model="activeData.range" @change="rangeChange" />
          </el-form-item>
          <el-form-item v-if="activeData.border !== undefined && activeData.optionType === 'default'" :label="$tr(&quot;是否带边框&quot;)">
            <el-switch v-model="activeData.border" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-color-picker'" :label="$tr(&quot;颜色格式&quot;)">
            <el-select v-model="activeData['color-format']" :placeholder="$tr(&quot;请选择颜色格式&quot;)" :style="{ width: '100%' }"
              @change="colorFormatChange">
              <el-option v-for="(item, index) in colorFormatOptions" :key="index" :label="item.label"
                :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="activeData.size !== undefined &&
            (activeData.optionType === 'button' ||
              activeData.border ||
              activeData.tag === 'el-color-picker')" :label="$tr(&quot;选项尺寸&quot;)">
            <el-radio-group v-model="activeData.size">
              <el-radio-button label="large">{{ $tr(" 较大 ") }}</el-radio-button>
              <el-radio-button label="default">{{ $tr(" 默认 ") }}</el-radio-button>
              <el-radio-button label="small">{{ $tr(" 较小 ") }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="activeData['show-word-limit'] !== undefined" :label="$tr(&quot;输入统计&quot;)">
            <el-switch v-model="activeData['show-word-limit']" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-input-number'" :label="$tr(&quot;严格步数&quot;)">
            <el-switch v-model="activeData['step-strictly']" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-cascader'" :label="$tr(&quot;是否多选&quot;)">
            <el-switch v-model="activeData.props.props.multiple" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-cascader'" :label="$tr(&quot;展示全路径&quot;)">
            <el-switch v-model="activeData['show-all-levels']" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-cascader'" :label="$tr(&quot;可否筛选&quot;)">
            <el-switch v-model="activeData.filterable" />
          </el-form-item>
          <el-form-item v-if="activeData.clearable !== undefined" :label="$tr(&quot;能否清空&quot;)">
            <el-switch v-model="activeData.clearable" />
          </el-form-item>
          <el-form-item v-if="activeData.showTip !== undefined" :label="$tr(&quot;显示提示&quot;)">
            <el-switch v-model="activeData.showTip" />
          </el-form-item>
          <el-form-item v-if="activeData.multiple !== undefined" :label="$tr(&quot;多选文件&quot;)">
            <el-switch v-model="activeData.multiple" />
          </el-form-item>
          <el-form-item v-if="activeData['auto-upload'] !== undefined" :label="$tr(&quot;自动上传&quot;)">
            <el-switch v-model="activeData['auto-upload']" />
          </el-form-item>
          <el-form-item v-if="activeData.readonly !== undefined" :label="$tr(&quot;是否只读&quot;)">
            <el-switch v-model="activeData.readonly" />
          </el-form-item>
          <el-form-item v-if="activeData.disabled !== undefined" :label="$tr(&quot;是否禁用&quot;)">
            <el-switch v-model="activeData.disabled" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-select'" :label="$tr(&quot;是否可搜索&quot;)">
            <el-switch v-model="activeData.filterable" />
          </el-form-item>
          <el-form-item v-if="activeData.tag === 'el-select'" :label="$tr(&quot;是否多选&quot;)">
            <el-switch v-model="activeData.multiple" @change="multipleChange" />
          </el-form-item>
          <el-form-item v-if="activeData.required !== undefined" :label="$tr(&quot;是否必填&quot;)">
            <el-switch v-model="activeData.required" />
          </el-form-item>

          <template v-if="activeData.layoutTree">
            <el-divider>{{ $tr("布局结构树") }}</el-divider>
            <el-tree :data="[activeData]" :props="layoutTreeProps" node-key="renderKey" default-expand-all draggable>
              <template #default="{ node, data }">
                <span class="node-label">
                  <svg-icon class="node-icon" :icon-class="data.tagIcon" style="margin-right: 5px;" />
                  {{ node.label }}
                </span>
              </template>
            </el-tree>
          </template>

          <template v-if="activeData.layout === 'colFormItem'">
            <el-divider>{{ $tr("正则校验") }}</el-divider>
            <div v-for="(item, index) in activeData.regList" :key="index" class="reg-item">
              <span class="close-btn" @click="activeData.regList.splice(index, 1)">
                <el-icon>
                  <Close />
                </el-icon>
              </span>
              <el-form-item :label="$tr(&quot;表达式&quot;)">
                <el-input v-model="item.pattern" :placeholder="$tr(&quot;请输入正则&quot;)" />
              </el-form-item>
              <el-form-item :label="$tr(&quot;错误提示&quot;)" style="margin-bottom:0">
                <el-input v-model="item.message" :placeholder="$tr(&quot;请输入错误提示&quot;)" />
              </el-form-item>
            </div>
            <div>
              <el-button icon="CirclePlus" style="margin-left: 0; margin-top: 10px;" type="primary" text bg
                @click="addReg">{{ $tr(" 添加规则 ") }}</el-button>
            </div>
          </template>
        </el-form>
        <!-- 表单属性 -->
        <el-form v-show="currentTab === 'form'" label-width="90px" label-position="top">
          <el-form-item :label="$tr(&quot;表单名&quot;)">
            <el-input v-model="formConf.formRef" :placeholder="$tr(&quot;请输入表单名（ref）&quot;)" />
          </el-form-item>
          <el-form-item :label="$tr(&quot;表单模型&quot;)">
            <el-input v-model="formConf.formModel" :placeholder="$tr(&quot;请输入数据模型&quot;)" />
          </el-form-item>
          <el-form-item :label="$tr(&quot;校验模型&quot;)">
            <el-input v-model="formConf.formRules" :placeholder="$tr(&quot;请输入校验模型&quot;)" />
          </el-form-item>
          <el-form-item :label="$tr(&quot;表单尺寸&quot;)">
            <el-radio-group v-model="formConf.size">
              <el-radio-button value="large">{{ $tr("较大") }}</el-radio-button>
              <el-radio-button value="default">{{ $tr("默认") }}</el-radio-button>
              <el-radio-button value="small">{{ $tr("较小") }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="$tr(&quot;标签对齐&quot;)">
            <el-radio-group v-model="formConf.labelPosition">
              <el-radio-button value="left">{{ $tr("左对齐") }}</el-radio-button>
              <el-radio-button value="right">{{ $tr("右对齐") }}</el-radio-button>
              <el-radio-button value="top">{{ $tr("顶部对齐") }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="$tr(&quot;标签宽度&quot;)">
            <el-input-number v-model="formConf.labelWidth" :placeholder="$tr(&quot;标签宽度&quot;)" />
          </el-form-item>
          <el-form-item :label="$tr(&quot;栅格间隔&quot;)">
            <el-input-number v-model="formConf.gutter" :min="0" :placeholder="$tr(&quot;栅格间隔&quot;)" />
          </el-form-item>
          <el-form-item :label="$tr(&quot;禁用表单&quot;)">
            <el-switch v-model="formConf.disabled" />
          </el-form-item>
          <el-form-item :label="$tr(&quot;表单按钮&quot;)">
            <el-switch v-model="formConf.formBtns" />
          </el-form-item>
          <el-form-item :label="$tr(&quot;显示未选中组件边框&quot;)">
            <el-switch v-model="formConf.unFocusedComponentBorder" />
          </el-form-item>
        </el-form>
      </el-scrollbar>
    </div>
    <icons-dialog v-model="iconsVisible" :current="activeData[currentIconModel]" @select="setIcon" />
    <treeNode-dialog v-model="dialogVisible" @commit="addNode" />

  </div>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import draggable from "vuedraggable/dist/vuedraggable.common"
import { isNumberStr } from '@/utils/index'
import IconsDialog from './IconsDialog'
import TreeNodeDialog from './TreeNodeDialog'
import { inputComponents, selectComponents } from '@/utils/generator/config'

const { proxy } = getCurrentInstance()
const dateTimeFormat = {
  date: 'YYYY-MM-DD',
  week: translateText("YYYY 第 ww 周"),
  month: 'YYYY-MM',
  year: 'YYYY',
  datetime: 'YYYY-MM-DD HH:mm:ss',
  daterange: 'YYYY-MM-DD',
  monthrange: 'YYYY-MM',
  datetimerange: 'YYYY-MM-DD HH:mm:ss'
}
const props = defineProps({
  showField: Boolean,
  activeData: Object,
  formConf: Object
})

const data = reactive({
  currentTab: 'field',
  currentNode: null,
  dialogVisible: false,
  iconsVisible: false,
  currentIconModel: null,
  dateTypeOptions: [
    {
      label: translateText("日(date)"),
      value: 'date'
    },
    {
      label: translateText("周(week)"),
      value: 'week'
    },
    {
      label: translateText("月(month)"),
      value: 'month'
    },
    {
      label: translateText("年(year)"),
      value: 'year'
    },
    {
      label: translateText("日期时间(datetime)"),
      value: 'datetime'
    }
  ],
  dateRangeTypeOptions: [
    {
      label: translateText("日期范围(daterange)"),
      value: 'daterange'
    },
    {
      label: translateText("月范围(monthrange)"),
      value: 'monthrange'
    },
    {
      label: translateText("日期时间范围(datetimerange)"),
      value: 'datetimerange'
    }
  ],
  colorFormatOptions: [
    {
      label: 'hex',
      value: 'hex'
    },
    {
      label: 'rgb',
      value: 'rgb'
    },
    {
      label: 'rgba',
      value: 'rgba'
    },
    {
      label: 'hsv',
      value: 'hsv'
    },
    {
      label: 'hsl',
      value: 'hsl'
    }
  ],
  justifyOptions: [
    {
      label: 'start',
      value: 'start'
    },
    {
      label: 'end',
      value: 'end'
    },
    {
      label: 'center',
      value: 'center'
    },
    {
      label: 'space-around',
      value: 'space-around'
    },
    {
      label: 'space-between',
      value: 'space-between'
    }
  ],
  layoutTreeProps: {
    label(data, node) {
      return data.componentName || `${data.label}: ${data.vModel}`
    }
  }
})

const { currentTab, currentNode, dialogVisible, iconsVisible, currentIconModel, dateTypeOptions, dateRangeTypeOptions, colorFormatOptions, justifyOptions, layoutTreeProps } = toRefs(data)

const documentLink = computed(() => props.activeData.document || 'https://element-plus.org/zh-CN/guide/installation')

const dateOptions = computed(() => {
  if (props.activeData.type !== undefined && props.activeData.tag === 'el-date-picker') {
    if (props.activeData['start-placeholder'] === undefined) {
      return dateTypeOptions.value
    }
    return dateRangeTypeOptions.value
  }
  return []
})

const tagList = ref([
  {
    label: translateText("输入型组件"),
    options: inputComponents
  },
  {
    label: translateText("选择型组件"),
    options: selectComponents
  }
])

const emit = defineEmits(['tag-change'])

function addReg() {
  props.activeData.regList.push({
    pattern: '',
    message: ''
  })
}
function addSelectItem() {
  props.activeData.options.push({
    label: '',
    value: ''
  })
}

function addTreeItem() {
  ++proxy.idGlobal
  dialogVisible.value = true
  currentNode.value = props.activeData.options
}

function renderContent(h, { node, data, store }) {
  return h('div', {
    class: "custom-tree-node"
  }, [
    h('span', node.label),
    h('span', {
      class: "node-operation"
    }, [
      h(resolveComponent('el-link'), {
        type: "primary",
        icon: "Plus",
        underline: false,
        onClick: () => {
          append(data)

        }
      }),
      h(resolveComponent('el-link'), {
        type: "danger",
        icon: "Delete",
        underline: false,
        style: "margin-left: 5px;",
        onClick: () => {
          remove(node, data)
        }
      })
    ])
  ])
}
function append(data) {
  if (!data.children) {
    data.children = []
  }
  dialogVisible.value = true
  currentNode.value = data.children
}
function remove(node, data) {
  const { parent } = node
  const children = parent.data.children || parent.data
  const index = children.findIndex(d => d.id === data.id)
  children.splice(index, 1)
}
function addNode(data) {
  currentNode.value.push(data)
}

function setOptionValue(item, val) {
  item.value = isNumberStr(val) ? +val : val
}
function setDefaultValue(val) {
  if (Array.isArray(val)) {
    return val.join(',')
  }
  if (['string', 'number'].indexOf(val) > -1) {
    return val
  }
  if (typeof val === 'boolean') {
    return `${val}`
  }
  return val
}

function onDefaultValueInput(str) {
  if (Array.isArray(props.activeData.defaultValue)) {
    // 数组
    props.activeData.defaultValue = str.split(',').map(val => (isNumberStr(val) ? +val : val))
  } else if (['true', 'false'].indexOf(str) > -1) {
    // 布尔
    props.activeData.defaultValue = JSON.parse(str)
  } else {
    // 字符串和数字
    props.activeData.defaultValue = isNumberStr(str) ? +str : str
  }
}

function onSwitchValueInput(val, name) {
  if (['true', 'false'].indexOf(val) > -1) {
    props.activeData[name] = JSON.parse(val)
  } else {
    props.activeData[name] = isNumberStr(val) ? +val : val
  }
}

function setTimeValue(val, type) {
  const valueFormat = type === 'week' ? dateTimeFormat.date : val
  props.activeData.defaultValue = null
  props.activeData['value-format'] = valueFormat
  props.activeData.format = val
}

function spanChange(val) {
  props.formConf.span = val
}

function multipleChange(val) {
  props.activeData.defaultValue = val ? [] : ''
}

function dateTypeChange(val) {
  setTimeValue(dateTimeFormat[val], val)
}

function rangeChange(val) {
  props.activeData.defaultValue = val ? [props.activeData.min, props.activeData.max] : props.activeData.min
}

function rateTextChange(val) {
  if (val) props.activeData['show-score'] = false
}

function rateScoreChange(val) {
  if (val) props.activeData['show-text'] = false
}

function colorFormatChange(val) {
  props.activeData.defaultValue = null
  props.activeData['show-alpha'] = val.indexOf('a') > -1
  props.activeData.renderKey = +new Date() // 更新renderKey,重新渲染该组件
}

function openIconsDialog(model) {
  iconsVisible.value = true
  currentIconModel.value = model
}

function setIcon(val) {
  props.activeData[currentIconModel.value] = val
}

function tagChange(tagIcon) {
  let target = inputComponents.find(item => item.tagIcon === tagIcon)
  if (!target) target = selectComponents.find(item => item.tagIcon === tagIcon)
  emit('tag-change', target)
}
</script>

<style lang="scss" scoped>
.right-board {
  width: 350px;
  position: absolute;
  right: 0;
  top: 0;
  padding-top: 3px;

  &:deep() {
    .el-tabs__header {
      margin: 0;
    }

    .el-input-group__append .el-button {
      display: inline-flex;
    }
  }

  .field-box {
    position: relative;
    height: calc(100vh - 50px - 40px - 42px);
    box-sizing: border-box;
    overflow: hidden;
  }

  .el-scrollbar {
    height: 100%;

    &:deep() {
      .el-scrollbar__view {
        padding: 30px 20px;
      }

    }
  }
}

.reg-item {
  padding: 12px 6px;
  background: var(--el-border-color-extra-light);
  position: relative;
  border-radius: 4px;

  .close-btn {
    position: absolute;
    right: -6px;
    top: -6px;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    line-height: 16px;
    background: rgba(0, 0, 0, .2);
    border-radius: 50%;
    color: #fff;
    z-index: 1;
    cursor: pointer;
    font-size: 12px;
  }
}

.select-item {
  display: flex;
  border: 1px dashed #fff;
  box-sizing: border-box;

  & .close-btn {
    cursor: pointer;
    color: #f56c6c;
  }

  & .el-input+.el-input {
    margin-left: 4px;
  }
}

.select-item+.select-item {
  margin-top: 4px;
}

.select-item.sortable-chosen {
  border: 1px dashed #409eff;
}

.select-line-icon {
  line-height: 32px;
  font-size: 22px;
  padding: 0 4px;
  color: #777;
}

.option-drag {
  cursor: move;
}

.time-range {
  .el-date-editor {
    width: 227px;
  }

  :deep() {
    .el-icon-time {
      display: none;
    }
  }
}

.document-link {
  position: absolute;
  display: flex;
  width: 26px;
  height: 26px;
  top: 0;
  left: 0;
  cursor: pointer;
  background: #409eff;
  z-index: 1;
  border-radius: 0 0 6px 0;
  justify-content: center;
  align-items: center;
  color: #fff;
  font-size: 18px;
}

.node-label {
  font-size: 14px;
}

.node-icon {
  color: #bebfc3;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}
</style>
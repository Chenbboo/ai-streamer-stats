<template>
  <el-form ref="genInfoForm" :model="info" :rules="rules" label-width="150px">
    <el-row>
      <el-col :span="12">
        <el-form-item prop="tplCategory">
          <template #label>{{ $tr("生成模板") }}</template>
          <el-select v-model="info.tplCategory" @change="tplSelectChange">
            <el-option :label="$tr(&quot;单表（增删改查）&quot;)" value="crud" />
            <el-option :label="$tr(&quot;树表（增删改查）&quot;)" value="tree" />
            <el-option :label="$tr(&quot;主子表（增删改查）&quot;)" value="sub" />
          </el-select>
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="tplWebType">
          <template #label>{{ $tr("前端类型") }}</template>
          <el-select v-model="info.tplWebType">
            <el-option :label="$tr(&quot;Vue2 Element UI 模版&quot;)" value="element-ui" />
            <el-option :label="$tr(&quot;Vue3 Element Plus 模版&quot;)" value="element-plus" />
            <el-option :label="$tr(&quot;Vue3 Element Plus TypeScript 模版&quot;)" value="element-plus-typescript" />
          </el-select>
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="packageName">
          <template #label>{{ $tr(" 生成包路径 ") }}<el-tooltip :content="$tr(&quot;生成在哪个java包下，例如 com.ruoyi.system&quot;)" placement="top">
              <el-icon><question-filled /></el-icon>
            </el-tooltip>
          </template>
          <el-input v-model="info.packageName" />
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="moduleName">
          <template #label>{{ $tr(" 生成模块名 ") }}<el-tooltip :content="$tr(&quot;可理解为子系统名，例如 system&quot;)" placement="top">
              <el-icon><question-filled /></el-icon>
            </el-tooltip>
          </template>
          <el-input v-model="info.moduleName" />
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="businessName">
          <template #label>{{ $tr(" 生成业务名 ") }}<el-tooltip :content="$tr(&quot;可理解为功能英文名，例如 user&quot;)" placement="top">
              <el-icon><question-filled /></el-icon>
            </el-tooltip>
          </template>
          <el-input v-model="info.businessName" />
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="functionName">
          <template #label>{{ $tr(" 生成功能名 ") }}<el-tooltip :content="$tr(&quot;用作类描述，例如 用户&quot;)" placement="top">
              <el-icon><question-filled /></el-icon>
            </el-tooltip>
          </template>
          <el-input v-model="info.functionName" />
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="formColNum">
          <template #label>{{ $tr(" 表单布局 ") }}<el-tooltip :content="$tr(&quot;选择表单的栅格布局方式&quot;)" placement="top">
              <el-icon><question-filled /></el-icon>
            </el-tooltip>
          </template>
          <el-select v-model="info.formColNum">
            <el-option :label="$tr(&quot;单列&quot;)" :value="1" />
            <el-option :label="$tr(&quot;双列&quot;)" :value="2" />
            <el-option :label="$tr(&quot;三列&quot;)" :value="3" />
          </el-select>
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="genView">
          <template #label>{{ $tr("扩展功能") }}</template>
          <el-checkbox v-model="info.view">{{ $tr("生成详情页") }}</el-checkbox>
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="genType">
          <template #label>{{ $tr(" 生成代码方式 ") }}<el-tooltip :content="$tr(&quot;默认为zip压缩包下载，也可以自定义生成路径&quot;)" placement="top">
              <el-icon><question-filled /></el-icon>
            </el-tooltip>
          </template>
          <el-radio v-model="info.genType" value="0">{{ $tr("zip压缩包") }}</el-radio>
          <el-radio v-model="info.genType" value="1">{{ $tr("自定义路径") }}</el-radio>
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item>
          <template #label>{{ $tr(" 上级菜单 ") }}<el-tooltip :content="$tr(&quot;分配到指定菜单下，例如 系统管理&quot;)" placement="top">
              <el-icon><question-filled /></el-icon>
            </el-tooltip>
          </template>
          <el-tree-select
            v-model="info.parentMenuId"
            :data="menuOptions"
            :props="{ value: 'menuId', label: 'menuName', children: 'children' }"
            :placeholder="$tr(&quot;请选择系统菜单&quot;)"
            check-strictly
          />
        </el-form-item>
      </el-col>

      <el-col :span="24" v-if="info.genType == '1'">
        <el-form-item prop="genPath">
          <template #label>{{ $tr(" 自定义路径 ") }}<el-tooltip :content="$tr(&quot;填写磁盘绝对路径，若不填写，则生成到当前Web项目下&quot;)" placement="top">
              <el-icon><question-filled /></el-icon>
            </el-tooltip>
          </template>
          <el-input v-model="info.genPath">
            <template #append>
              <el-dropdown>
                <el-button type="primary">{{ $tr(" 最近路径快速选择 ") }}<i class="el-icon-arrow-down el-icon--right"></i>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="info.genPath = '/'">{{ $tr("恢复默认的生成基础路径") }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-input>
        </el-form-item>
      </el-col>
    </el-row>
    
    <template v-if="info.tplCategory == 'tree'">
      <h4 class="form-header">{{ $tr("其他信息") }}</h4>
      <el-row v-show="info.tplCategory == 'tree'">
        <el-col :span="12">
          <el-form-item>
            <template #label>{{ $tr(" 树编码字段 ") }}<el-tooltip :content="$tr(&quot;树显示的编码字段名， 如：dept_id&quot;)" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
            </template>
            <el-select v-model="info.treeCode" :placeholder="$tr(&quot;请选择&quot;)">
              <el-option
                v-for="(column, index) in info.columns"
                :key="index"
                :label="column.columnName + '：' + column.columnComment"
                :value="column.columnName"
              ></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item>
            <template #label>{{ $tr(" 树父编码字段 ") }}<el-tooltip :content="$tr(&quot;树显示的父编码字段名， 如：parent_Id&quot;)" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
            </template>
            <el-select v-model="info.treeParentCode" :placeholder="$tr(&quot;请选择&quot;)">
              <el-option
                v-for="(column, index) in info.columns"
                :key="index"
                :label="column.columnName + '：' + column.columnComment"
                :value="column.columnName"
              ></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item>
            <template #label>{{ $tr(" 树名称字段 ") }}<el-tooltip :content="$tr(&quot;树节点的显示名称字段名， 如：dept_name&quot;)" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
            </template>
            <el-select v-model="info.treeName" :placeholder="$tr(&quot;请选择&quot;)">
              <el-option
                v-for="(column, index) in info.columns"
                :key="index"
                :label="column.columnName + '：' + column.columnComment"
                :value="column.columnName"
              ></el-option>
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </template>

    <template v-if="info.tplCategory == 'sub'">
      <h4 class="form-header">{{ $tr("关联信息") }}</h4>
      <el-row>
        <el-col :span="12">
          <el-form-item>
            <template #label>{{ $tr(" 关联子表的表名 ") }}<el-tooltip :content="$tr(&quot;关联子表的表名， 如：sys_user&quot;)" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
            </template>
            <el-select v-model="info.subTableName" :placeholder="$tr(&quot;请选择&quot;)" @change="subSelectChange">
              <el-option
                v-for="(table, index) in tables"
                :key="index"
                :label="table.tableName + '：' + table.tableComment"
                :value="table.tableName"
              ></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item>
            <template #label>{{ $tr(" 子表关联的外键名 ") }}<el-tooltip :content="$tr(&quot;子表关联的外键名， 如：user_id&quot;)" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
            </template>
            <el-select v-model="info.subTableFkName" :placeholder="$tr(&quot;请选择&quot;)">
              <el-option
                v-for="(column, index) in subColumns"
                :key="index"
                :label="column.columnName + '：' + column.columnComment"
                :value="column.columnName"
              ></el-option>
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </template>

  </el-form>
</template>

<script setup>
import { translateText } from '@/locales/translate'

import { listMenu } from "@/api/system/menu"

const subColumns = ref([])
const menuOptions = ref([])
const { proxy } = getCurrentInstance()

const props = defineProps({
  info: {
    type: Object,
    default: null
  },
  tables: {
    type: Array,
    default: null
  }
})

// 表单校验
const rules = ref({
  tplCategory: [{ required: true, message: translateText("请选择生成模板"), trigger: "blur" }],
  packageName: [{ required: true, message: translateText("请输入生成包路径"), trigger: "blur" }],
  moduleName: [{ required: true, message: translateText("请输入生成模块名"), trigger: "blur" }],
  businessName: [{ required: true, message: translateText("请输入生成业务名"), trigger: "blur" }],
  functionName: [{ required: true, message: translateText("请输入生成功能名"), trigger: "blur" }]
})

function subSelectChange(value) {
  props.info.subTableFkName = ""
}

function tplSelectChange(value) {
  if (value !== "sub") {
    props.info.subTableName = ""
    props.info.subTableFkName = ""
  }
}

function setSubTableColumns(value) {
  for (var item in props.tables) {
    const name = props.tables[item].tableName
    if (value === name) {
      subColumns.value = props.tables[item].columns
      break
    }
  }
}

/** 查询菜单下拉树结构 */
function getMenuTreeselect() {
  listMenu().then(response => {
    menuOptions.value = proxy.handleTree(response.data, "menuId")
  })
}

onMounted(() => {
  getMenuTreeselect()
})

watch(() => props.info.subTableName, val => {
  setSubTableColumns(val)
})

watch(() => props.info.tplWebType, val => {
  if (val === '') {
    props.info.tplWebType = "element-plus"
  }
})
</script>

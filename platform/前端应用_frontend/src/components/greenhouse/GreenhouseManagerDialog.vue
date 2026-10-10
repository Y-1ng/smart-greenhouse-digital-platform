<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  AlertTriangle,
  CalendarDays,
  Check,
  Leaf,
  LoaderCircle,
  MapPin,
  Pencil,
  Plus,
  Ruler,
  Save,
  Search,
  Sprout,
  Trash2,
  Warehouse,
  X,
} from 'lucide-vue-next'
import {
  createGreenhouse,
  deleteGreenhouse,
  updateGreenhouseCrop,
} from '@/api/modules/greenhouses'
import {
  northwestCropCategories,
  northwestCrops,
} from '@/data/northwestCrops'
import type { NorthwestCropCategory, NorthwestCropOption } from '@/data/northwestCrops'
import type { GreenhouseSite } from '@/types/greenhouse'

interface GreenhouseForm {
  code: string
  name: string
  area: number
  location: string
  cropName: string
  varietyName: string
  plantingDate: string
  expectedHarvestDate: string
}

const props = defineProps<{
  open: boolean
  sites: GreenhouseSite[]
  selectedId: string
  canManage: boolean
}>()

const emit = defineEmits<{
  close: []
  select: [site: GreenhouseSite]
  changed: [preferredCode: string]
}>()

const mode = ref<'create' | 'crop'>('create')
const cropSearch = ref('')
const activeCategory = ref<'全部' | NorthwestCropCategory>('全部')
const saving = ref(false)
const deleting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const deleteTarget = ref<GreenhouseSite | null>(null)
const form = ref<GreenhouseForm>(createDefaultForm())

const filteredCrops = computed(() => {
  const keyword = cropSearch.value.trim().toLowerCase()
  return northwestCrops.filter((crop) => {
    const categoryMatched =
      activeCategory.value === '全部'
      || crop.category === activeCategory.value
    if (!categoryMatched) return false
    if (!keyword) return true
    return (
      crop.name.toLowerCase().includes(keyword)
      || crop.feature.toLowerCase().includes(keyword)
      || crop.varieties.some((variety) =>
        variety.toLowerCase().includes(keyword),
      )
    )
  })
})

const selectedCrop = computed(
  () => northwestCrops.find((crop) => crop.name === form.value.cropName) ?? null,
)

const categoryOptions = computed<Array<'全部' | NorthwestCropCategory>>(
  () => ['全部', ...northwestCropCategories],
)

const editorTitle = computed(() =>
  mode.value === 'create' ? '新增大棚与作物' : '调整大棚作物',
)

const submitLabel = computed(() => {
  if (saving.value) return '正在保存'
  return mode.value === 'create' ? '创建大棚' : '保存作物'
})

watch(
  () => props.open,
  (open) => {
    if (open) {
      resetMessages()
      resetCreateForm()
    }
  },
)

function dateAfter(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

function nextGreenhouseCode() {
  const numbers = props.sites
    .map((site) => /^GH-(\d+)$/i.exec(site.code)?.[1])
    .filter((value): value is string => Boolean(value))
    .map(Number)
  const next = (numbers.length > 0 ? Math.max(...numbers) : 0) + 1
  return `GH-${String(next).padStart(3, '0')}`
}

function createDefaultForm(): GreenhouseForm {
  return {
    code: '',
    name: '',
    area: 800,
    location: '西北设施农业示范基地',
    cropName: '番茄',
    varietyName: '金棚一号',
    plantingDate: dateAfter(0),
    expectedHarvestDate: dateAfter(120),
  }
}

function resetCreateForm() {
  const code = nextGreenhouseCode()
  form.value = {
    ...createDefaultForm(),
    code,
    name: `${Number(code.slice(3))}号温室`,
  }
  mode.value = 'create'
  cropSearch.value = ''
  activeCategory.value = '全部'
  deleteTarget.value = null
}

function resetMessages() {
  errorMessage.value = ''
  successMessage.value = ''
}

function chooseCrop(crop: NorthwestCropOption) {
  form.value.cropName = crop.name
  form.value.varietyName = crop.varieties[0] ?? '常规品种'
  resetMessages()
}

function beginCropEdit(site: GreenhouseSite) {
  mode.value = 'crop'
  const cropParts = site.crop.split(' · ')
  form.value = {
    code: site.code,
    name: site.name,
    area: Number(site.area.replace(/[^\d.]/g, '')) || 0,
    location: site.location ?? '西北设施农业示范基地',
    cropName: cropParts[0]?.trim() || '番茄',
    varietyName: cropParts[1]?.trim() || '常规品种',
    plantingDate: site.plantingDate ?? dateAfter(0),
    expectedHarvestDate: site.expectedHarvestDate ?? dateAfter(120),
  }
  cropSearch.value = ''
  activeCategory.value = '全部'
  deleteTarget.value = null
  resetMessages()
}

function closeDialog() {
  if (saving.value || deleting.value) return
  emit('close')
}

function selectSite(site: GreenhouseSite) {
  emit('select', site)
  emit('close')
}

function requestDelete(site: GreenhouseSite) {
  deleteTarget.value = site
  resetMessages()
}

function cancelDelete() {
  deleteTarget.value = null
}

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleting.value = true
  resetMessages()
  try {
    await deleteGreenhouse(target.code)
    successMessage.value = `${target.name}已删除`
    deleteTarget.value = null
    emit('changed', '')
  } catch (error) {
    errorMessage.value = errorText(error, '删除大棚失败')
  } finally {
    deleting.value = false
  }
}

async function submitForm() {
  if (!props.canManage || saving.value) return
  resetMessages()
  const area = Number(form.value.area)
  if (!form.value.code.trim() || !form.value.name.trim()) {
    errorMessage.value = '请填写温室编号和名称'
    return
  }
  if (!Number.isFinite(area) || area <= 0) {
    errorMessage.value = '温室面积必须大于 0'
    return
  }
  if (!form.value.cropName.trim()) {
    errorMessage.value = '请选择大棚内种植的作物'
    return
  }
  if (
    form.value.expectedHarvestDate
    && form.value.plantingDate
    && form.value.expectedHarvestDate < form.value.plantingDate
  ) {
    errorMessage.value = '预计采收日期不能早于种植日期'
    return
  }

  saving.value = true
  try {
    if (mode.value === 'create') {
      await createGreenhouse({
        code: form.value.code.trim().toUpperCase(),
        name: form.value.name.trim(),
        area,
        location: form.value.location.trim(),
        cropName: form.value.cropName.trim(),
        varietyName: form.value.varietyName.trim() || '常规品种',
        plantingDate: form.value.plantingDate,
        expectedHarvestDate: form.value.expectedHarvestDate,
      })
      successMessage.value = `${form.value.name.trim()}已创建，并完成作物绑定`
      emit('changed', form.value.code.trim().toUpperCase())
      resetCreateForm()
    } else {
      await updateGreenhouseCrop(form.value.code, {
        cropName: form.value.cropName.trim(),
        varietyName: form.value.varietyName.trim() || '常规品种',
        plantingDate: form.value.plantingDate,
        expectedHarvestDate: form.value.expectedHarvestDate,
      })
      successMessage.value = `${form.value.name}已调整为${form.value.cropName}`
      emit('changed', form.value.code)
      mode.value = 'create'
      resetCreateForm()
    }
  } catch (error) {
    errorMessage.value = errorText(error, '保存大棚信息失败')
  } finally {
    saving.value = false
  }
}

function errorText(error: unknown, fallback: string) {
  const responseMessage = (
    error as { response?: { data?: { message?: string } } }
  ).response?.data?.message
  return responseMessage ?? fallback
}

function siteCropLabel(site: GreenhouseSite) {
  return site.crop || '未选择作物'
}

function siteBatchLabel(site: GreenhouseSite) {
  return site.batchId ? `批次 #${site.batchId}` : site.batch || '暂无批次'
}
</script>

<template>
  <Teleport to="body">
    <Transition name="greenhouse-manager">
      <div
        v-if="open"
        class="greenhouse-manager-overlay"
        @mousedown.self="closeDialog"
      >
        <section
          class="greenhouse-manager-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="greenhouse-manager-title"
        >
          <header class="greenhouse-manager-header">
            <div>
              <span>温室与作物档案</span>
              <h3 id="greenhouse-manager-title">选择温室 / 管理大棚</h3>
              <p>新增、删除、改种和切换当前生产温室都在这里完成</p>
            </div>
            <button
              type="button"
              aria-label="关闭温室管理"
              @click="closeDialog"
            >
              <X :size="18" />
            </button>
          </header>

          <div
            class="greenhouse-manager-body"
            :class="{ 'is-readonly': !canManage }"
          >
            <aside class="greenhouse-manager-list">
              <div class="greenhouse-manager-list__head">
                <div>
                  <strong>园区温室</strong>
                  <span>{{ sites.length }} 座大棚</span>
                </div>
                <button
                  v-if="canManage"
                  type="button"
                  @click="resetCreateForm"
                >
                  <Plus :size="14" />
                  新增
                </button>
              </div>

              <div class="greenhouse-site-list">
                <article
                  v-for="site in sites"
                  :key="site.code"
                  class="greenhouse-site-row"
                  :class="{ 'is-active': site.id === selectedId }"
                >
                  <button
                    class="greenhouse-site-row__select"
                    type="button"
                    @click="selectSite(site)"
                  >
                    <span
                      class="greenhouse-site-row__dot"
                      :class="`is-${site.tone}`"
                    ></span>
                    <span class="greenhouse-site-row__copy">
                      <strong>{{ site.name }}</strong>
                      <small>{{ siteCropLabel(site) }}</small>
                      <em>
                        {{ site.code }} · {{ site.area }} ·
                        {{ siteBatchLabel(site) }}
                      </em>
                    </span>
                    <Check
                      v-if="site.id === selectedId"
                      :size="16"
                      class="greenhouse-site-row__selected"
                    />
                  </button>

                  <div v-if="canManage" class="greenhouse-site-row__actions">
                    <button
                      type="button"
                      aria-label="调整作物"
                      title="调整作物"
                      @click="beginCropEdit(site)"
                    >
                      <Pencil :size="14" />
                    </button>
                    <button
                      type="button"
                      aria-label="删除温室"
                      title="删除温室"
                      @click="requestDelete(site)"
                    >
                      <Trash2 :size="14" />
                    </button>
                  </div>
                </article>

                <p v-if="sites.length === 0" class="greenhouse-site-list__empty">
                  暂无温室，请在右侧创建第一座大棚。
                </p>
              </div>
            </aside>

            <form
              v-if="canManage"
              class="greenhouse-manager-editor"
              @submit.prevent="submitForm"
            >
              <div class="greenhouse-manager-editor__head">
                <div>
                  <span>{{ mode === 'create' ? '园区建设' : '作物调整' }}</span>
                  <h4>{{ editorTitle }}</h4>
                </div>
                <button
                  v-if="mode === 'crop'"
                  type="button"
                  @click="resetCreateForm"
                >
                  返回新增
                </button>
              </div>

              <div v-if="mode === 'create'" class="greenhouse-form-grid">
                <label>
                  <span>温室编号</span>
                  <input v-model.trim="form.code" type="text" maxlength="32" />
                </label>
                <label>
                  <span>温室名称</span>
                  <input v-model.trim="form.name" type="text" maxlength="64" />
                </label>
                <label>
                  <span><Ruler :size="13" /> 面积（㎡）</span>
                  <input v-model.number="form.area" type="number" min="1" step="1" />
                </label>
                <label>
                  <span><MapPin :size="13" /> 所在区域</span>
                  <input
                    v-model.trim="form.location"
                    type="text"
                    maxlength="120"
                    placeholder="例如：宁夏中卫 / 河西走廊"
                  />
                </label>
              </div>

              <section class="greenhouse-crop-picker">
                <div class="greenhouse-crop-picker__head">
                  <div>
                    <span><Sprout :size="13" /> 大棚内农作物</span>
                    <strong>
                      已选 {{ form.cropName || '未选择' }}
                      <em v-if="form.varietyName"> · {{ form.varietyName }}</em>
                    </strong>
                  </div>
                  <small>共 {{ northwestCrops.length }} 种西北适种作物</small>
                </div>

                <div class="greenhouse-crop-toolbar">
                  <div class="greenhouse-crop-search">
                    <Search :size="15" />
                    <input
                      v-model.trim="cropSearch"
                      type="search"
                      placeholder="搜索作物、品种或用途"
                    />
                  </div>

                  <div class="greenhouse-crop-categories">
                    <button
                      v-for="category in categoryOptions"
                      :key="category"
                      type="button"
                      :class="{ 'is-active': activeCategory === category }"
                      @click="activeCategory = category"
                    >
                      {{ category }}
                    </button>
                  </div>
                </div>

                <div class="greenhouse-crop-options">
                  <button
                    v-for="crop in filteredCrops"
                    :key="`${crop.category}-${crop.name}`"
                    type="button"
                    :class="{ 'is-active': crop.name === form.cropName }"
                    @click="chooseCrop(crop)"
                  >
                    <Leaf :size="14" />
                    <span>
                      <strong>{{ crop.name }}</strong>
                      <small>{{ crop.feature }}</small>
                    </span>
                  </button>
                </div>

                <div class="greenhouse-crop-meta">
                  <div class="greenhouse-crop-meta__current">
                    <span>当前作物</span>
                    <strong>{{ form.cropName }}</strong>
                  </div>
                  <label>
                    <span>品种名称</span>
                    <input
                      v-model.trim="form.varietyName"
                      type="text"
                      list="northwest-crop-varieties"
                      maxlength="80"
                    />
                    <datalist id="northwest-crop-varieties">
                      <option
                        v-for="variety in selectedCrop?.varieties ?? []"
                        :key="variety"
                        :value="variety"
                      ></option>
                    </datalist>
                  </label>
                  <label>
                    <span><CalendarDays :size="13" /> 种植日期</span>
                    <input v-model="form.plantingDate" type="date" />
                  </label>
                  <label>
                    <span><CalendarDays :size="13" /> 预计采收</span>
                    <input v-model="form.expectedHarvestDate" type="date" />
                  </label>
                </div>
              </section>

              <div v-if="deleteTarget" class="greenhouse-delete-confirm">
                <AlertTriangle :size="18" />
                <div>
                  <strong>确认删除 {{ deleteTarget.name }}？</strong>
                  <span>
                    如果该温室已有设备、环境周期、水肥或溯源数据，后端会拒绝删除。
                  </span>
                </div>
                <button type="button" @click="cancelDelete">取消</button>
                <button
                  type="button"
                  :disabled="deleting"
                  @click="confirmDelete"
                >
                  {{ deleting ? '删除中' : '确认删除' }}
                </button>
              </div>

              <p v-if="errorMessage" class="greenhouse-manager-message is-error">
                <AlertTriangle :size="14" />
                {{ errorMessage }}
              </p>
              <p v-if="successMessage" class="greenhouse-manager-message is-success">
                <Check :size="14" />
                {{ successMessage }}
              </p>

              <footer class="greenhouse-manager-editor__footer">
                <span>
                  <Warehouse :size="14" />
                  {{ mode === 'create' ? '创建后自动绑定作物批次' : '改种会更新当前作物批次' }}
                </span>
                <button class="primary-button" type="submit" :disabled="saving">
                  <LoaderCircle v-if="saving" :size="15" class="is-spinning" />
                  <Save v-else :size="15" />
                  {{ submitLabel }}
                </button>
              </footer>
            </form>

            <section v-else class="greenhouse-manager-readonly">
              <Warehouse :size="28" />
              <strong>当前账号以只读方式查看温室档案</strong>
              <p>选择和切换温室可以正常使用，新增、删除与改种需要管理员或操作员权限。</p>
            </section>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.greenhouse-manager-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  padding: 20px;
  place-items: center;
  background: rgb(13 25 18 / 48%);
  backdrop-filter: blur(7px);
}

.greenhouse-manager-dialog {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: min(1220px, 94vw);
  height: auto;
  max-height: min(800px, 88vh);
  overflow: hidden;
  background: #f7faf8;
  border: 1px solid #cbd9d1;
  border-radius: 8px;
  box-shadow: 0 28px 80px rgb(18 38 27 / 28%);
}

.greenhouse-manager-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 18px;
  background: #ffffff;
  border-bottom: 1px solid #dfe8e2;
}

.greenhouse-manager-header span,
.greenhouse-manager-editor__head span {
  color: #2d7a58;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.greenhouse-manager-header h3,
.greenhouse-manager-header p,
.greenhouse-manager-editor__head h4 {
  margin: 0;
}

.greenhouse-manager-header h3 {
  margin-top: 4px;
  color: #203328;
  font-size: 18px;
}

.greenhouse-manager-header p {
  margin-top: 4px;
  color: #748179;
  font-size: 10px;
}

.greenhouse-manager-header > button {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  color: #59695f;
  background: #f4f7f5;
  border: 1px solid #dce5df;
  border-radius: 7px;
}

.greenhouse-manager-header > button:hover {
  color: #1e7453;
  background: #eaf4ee;
}

.greenhouse-manager-body {
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr);
  min-height: 450px;
}

.greenhouse-manager-body.is-readonly {
  grid-template-columns: 1fr;
}

.greenhouse-manager-list {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  background: #f2f6f3;
  border-right: 1px solid #dce5df;
}

.greenhouse-manager-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 12px;
  border-bottom: 1px solid #dce5df;
}

.greenhouse-manager-list__head strong,
.greenhouse-manager-list__head span {
  display: block;
}

.greenhouse-manager-list__head strong {
  color: #273a2f;
  font-size: 13px;
}

.greenhouse-manager-list__head span {
  margin-top: 3px;
  color: #77847c;
  font-size: 9px;
}

.greenhouse-manager-list__head button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 9px;
  color: #1e7453;
  background: #ffffff;
  border: 1px solid #bad4c3;
  border-radius: 6px;
  font-size: 10px;
}

.greenhouse-site-list {
  display: grid;
  align-content: start;
  gap: 6px;
  min-height: 0;
  max-height: 405px;
  padding: 10px;
  overflow-y: auto;
}

.greenhouse-site-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px;
  align-items: center;
  background: #ffffff;
  border: 1px solid #dce5df;
  border-radius: 7px;
}

.greenhouse-site-row:hover,
.greenhouse-site-row.is-active {
  background: #f7fcf8;
  border-color: #9fc8b1;
}

.greenhouse-site-row__select {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 8px 9px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
}

.greenhouse-site-row__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.greenhouse-site-row__dot.is-running,
.greenhouse-site-row__dot.is-ok {
  background: #2f9b69;
}

.greenhouse-site-row__dot.is-warning {
  background: #d59a39;
}

.greenhouse-site-row__dot.is-offline {
  background: #b56b65;
}

.greenhouse-site-row__copy {
  min-width: 0;
}

.greenhouse-site-row__copy strong,
.greenhouse-site-row__copy small,
.greenhouse-site-row__copy em {
  display: block;
}

.greenhouse-site-row__copy strong {
  color: #26382e;
  font-size: 11px;
}

.greenhouse-site-row__copy small {
  margin-top: 2px;
  overflow: hidden;
  color: #52705e;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.greenhouse-site-row__copy em {
  margin-top: 4px;
  overflow: hidden;
  color: #859188;
  font-size: 9px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.greenhouse-site-row__selected {
  color: #237b58;
}

.greenhouse-site-row__actions {
  display: inline-flex;
  gap: 2px;
  padding-right: 7px;
}

.greenhouse-site-row__actions button {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  color: #6d7b72;
  background: transparent;
  border: 0;
  border-radius: 5px;
}

.greenhouse-site-row__actions button:hover {
  color: #1f7856;
  background: #eaf4ee;
}

.greenhouse-site-row__actions button:last-child:hover {
  color: #ad534d;
  background: #fbefee;
}

.greenhouse-site-list__empty {
  margin: 18px 10px;
  color: #7a887f;
  font-size: 11px;
  text-align: center;
}

.greenhouse-manager-editor {
  display: grid;
  align-content: start;
  grid-template-rows: auto;
  min-height: 0;
  overflow-y: auto;
  background: #ffffff;
}

.greenhouse-manager-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 16px 10px;
}

.greenhouse-manager-editor__head h4 {
  margin-top: 4px;
  color: #22352a;
  font-size: 15px;
}

.greenhouse-manager-editor__head > button {
  min-height: 28px;
  padding: 0 9px;
  color: #607067;
  background: #f4f7f5;
  border: 1px solid #dce5df;
  border-radius: 6px;
  font-size: 10px;
}

.greenhouse-form-grid,
.greenhouse-crop-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.greenhouse-form-grid {
  padding: 0 16px 12px;
}

.greenhouse-form-grid label,
.greenhouse-crop-meta label {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.greenhouse-form-grid label > span,
.greenhouse-crop-meta label > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #64736a;
  font-size: 10px;
}

.greenhouse-form-grid input,
.greenhouse-crop-meta input {
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  color: #27382f;
  background: #fbfdfc;
  border: 1px solid #d8e3dc;
  border-radius: 6px;
  font-size: 11px;
}

.greenhouse-form-grid input:focus,
.greenhouse-crop-meta input:focus {
  border-color: #7db494;
  outline: 2px solid rgb(63 138 96 / 12%);
}

.greenhouse-crop-picker {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 0;
  padding: 12px 16px;
  background: #f8fbf9;
  border-top: 1px solid #e0e9e3;
  border-bottom: 1px solid #e0e9e3;
}

.greenhouse-crop-picker__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.greenhouse-crop-picker__head span,
.greenhouse-crop-picker__head strong {
  display: block;
}

.greenhouse-crop-picker__head span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #5e6e65;
  font-size: 9px;
}

.greenhouse-crop-picker__head strong {
  margin-top: 3px;
  color: #23392d;
  font-size: 13px;
}

.greenhouse-crop-picker__head strong em {
  color: #267a58;
  font-style: normal;
}

.greenhouse-crop-picker__head small {
  color: #7f8c84;
  font-size: 8px;
}

.greenhouse-crop-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(0, 1.2fr);
  gap: 8px;
}

.greenhouse-crop-search {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  align-items: center;
  min-height: 32px;
  padding: 0 10px;
  color: #77847c;
  background: #ffffff;
  border: 1px solid #d6e2da;
  border-radius: 6px;
}

.greenhouse-crop-search input {
  min-width: 0;
  color: #26382e;
  background: transparent;
  border: 0;
  outline: 0;
  font-size: 10px;
}

.greenhouse-crop-categories {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.greenhouse-crop-categories button {
  min-height: 25px;
  padding: 0 8px;
  color: #64736a;
  background: #ffffff;
  border: 1px solid #d8e3dc;
  border-radius: 999px;
  font-size: 8px;
}

.greenhouse-crop-categories button.is-active {
  color: #ffffff;
  background: #246f52;
  border-color: #246f52;
}

.greenhouse-crop-options {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  max-height: 188px;
  padding-right: 5px;
  overflow-y: auto;
}

.greenhouse-crop-options button {
  display: grid;
  grid-template-columns: 15px minmax(0, 1fr);
  align-items: start;
  gap: 5px;
  min-height: 48px;
  padding: 7px;
  color: #4e6759;
  text-align: left;
  background: #ffffff;
  border: 1px solid #dce5df;
  border-radius: 7px;
}

.greenhouse-crop-options button:hover,
.greenhouse-crop-options button.is-active {
  color: #1f7453;
  background: #edf7f1;
  border-color: #8fc0a4;
}

.greenhouse-crop-options button span {
  min-width: 0;
}

.greenhouse-crop-options button strong,
.greenhouse-crop-options button small {
  display: block;
}

.greenhouse-crop-options button strong {
  color: #2a3b32;
  font-size: 10px;
}

.greenhouse-crop-options button small {
  margin-top: 2px;
  overflow: hidden;
  color: #7d8a82;
  font-size: 8px;
  line-height: 1.28;
  text-overflow: ellipsis;
}

.greenhouse-crop-meta {
  align-items: end;
  padding: 8px 10px;
  background: #ffffff;
  border: 1px solid #dce5df;
  border-radius: 7px;
}

.greenhouse-crop-meta__current {
  align-self: center;
}

.greenhouse-crop-meta__current span,
.greenhouse-crop-meta__current strong {
  display: block;
}

.greenhouse-crop-meta__current span {
  color: #7a8780;
  font-size: 8px;
}

.greenhouse-crop-meta__current strong {
  margin-top: 2px;
  color: #267a58;
  font-size: 13px;
}

.greenhouse-delete-confirm {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  margin: 10px 16px 0;
  padding: 9px 10px;
  color: #8d4c45;
  background: #fff6f5;
  border: 1px solid #efd3d0;
  border-radius: 7px;
}

.greenhouse-delete-confirm strong,
.greenhouse-delete-confirm span {
  display: block;
}

.greenhouse-delete-confirm strong {
  font-size: 11px;
}

.greenhouse-delete-confirm span {
  margin-top: 3px;
  color: #8b6964;
  font-size: 9px;
}

.greenhouse-delete-confirm button {
  min-height: 29px;
  padding: 0 9px;
  color: #81564f;
  background: #ffffff;
  border: 1px solid #e4c9c5;
  border-radius: 6px;
  font-size: 9px;
}

.greenhouse-delete-confirm button:last-child {
  color: #ffffff;
  background: #b65c53;
  border-color: #b65c53;
}

.greenhouse-manager-message {
  display: inline-flex;
  align-items: flex-start;
  gap: 6px;
  margin: 8px 16px 0;
  padding: 8px 9px;
  border-radius: 6px;
  font-size: 10px;
}

.greenhouse-manager-message.is-error {
  color: #9e5049;
  background: #fff4f3;
  border: 1px solid #efd3d0;
}

.greenhouse-manager-message.is-success {
  color: #256a4b;
  background: #eef8f2;
  border: 1px solid #cae3d4;
}

.greenhouse-manager-editor__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 16px 13px;
}

.greenhouse-manager-editor__footer > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #748179;
  font-size: 9px;
}

.greenhouse-manager-editor__footer .primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 116px;
}

.greenhouse-manager-readonly {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 8px;
  padding: 40px;
  color: #668073;
  text-align: center;
}

.greenhouse-manager-readonly strong {
  color: #2d4638;
  font-size: 15px;
}

.greenhouse-manager-readonly p {
  max-width: 420px;
  margin: 0;
  color: #78867e;
  font-size: 11px;
  line-height: 1.7;
}

.greenhouse-manager-enter-active,
.greenhouse-manager-leave-active {
  transition: opacity 180ms ease;
}

.greenhouse-manager-enter-active .greenhouse-manager-dialog,
.greenhouse-manager-leave-active .greenhouse-manager-dialog {
  transition: transform 180ms ease;
}

.greenhouse-manager-enter-from,
.greenhouse-manager-leave-to {
  opacity: 0;
}

.greenhouse-manager-enter-from .greenhouse-manager-dialog,
.greenhouse-manager-leave-to .greenhouse-manager-dialog {
  transform: translateY(10px) scale(0.985);
}

.is-spinning {
  animation: greenhouse-spin 900ms linear infinite;
}

@keyframes greenhouse-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .greenhouse-manager-overlay {
    padding: 10px;
  }

  .greenhouse-manager-dialog {
    max-height: 94vh;
  }

  .greenhouse-manager-body {
    grid-template-columns: 1fr;
    min-height: 0;
    overflow-y: auto;
  }

  .greenhouse-manager-list {
    max-height: 310px;
    border-right: 0;
    border-bottom: 1px solid #dce5df;
  }

  .greenhouse-manager-editor {
    overflow: visible;
  }

  .greenhouse-crop-toolbar {
    grid-template-columns: 1fr;
  }

  .greenhouse-crop-categories {
    justify-content: flex-start;
  }

  .greenhouse-crop-options {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .greenhouse-manager-header {
    padding: 14px;
  }

  .greenhouse-manager-header h3 {
    font-size: 17px;
  }

  .greenhouse-manager-header p {
    display: none;
  }

  .greenhouse-manager-editor__head,
  .greenhouse-crop-picker,
  .greenhouse-form-grid,
  .greenhouse-manager-editor__footer {
    padding-right: 13px;
    padding-left: 13px;
  }

  .greenhouse-form-grid,
  .greenhouse-crop-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .greenhouse-crop-options {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    max-height: 300px;
  }

  .greenhouse-delete-confirm {
    grid-template-columns: auto minmax(0, 1fr);
    margin-right: 13px;
    margin-left: 13px;
  }

  .greenhouse-delete-confirm button {
    grid-column: span 1;
  }

  .greenhouse-manager-editor__footer {
    align-items: stretch;
    flex-direction: column;
  }

  .greenhouse-manager-editor__footer .primary-button {
    width: 100%;
  }
}
</style>

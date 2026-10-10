<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { LocateFixed, MousePointer2 } from 'lucide-vue-next'
import { greenhouseSites } from '@/data/greenhouse'
import type { GreenhouseSite } from '@/types/greenhouse'
import {
  createPlantBatch,
  loadPlantModel,
} from '@/components/greenhouse/plantModel'
import type {
  PlantBatchHandle,
  PlantBatchInstance,
} from '@/components/greenhouse/plantModel'
import { getCropPlantProfile } from '@/components/greenhouse/cropPlantProfile'
import { getSceneQualityProfile } from '@/components/greenhouse/sceneQuality'

interface SiteSceneEntry {
  site: GreenhouseSite
  group: THREE.Group
  ring: THREE.Mesh<THREE.RingGeometry, THREE.MeshBasicMaterial>
  hitMesh: THREE.Mesh
  outline: THREE.LineSegments<THREE.BufferGeometry, THREE.LineBasicMaterial>
}

interface LabelPosition {
  x: number
  y: number
  offsetY: number
  visible: boolean
}

const props = withDefaults(
  defineProps<{
    selectedId?: string
    autoRotate?: boolean
  }>(),
  {
    selectedId: '',
    autoRotate: true,
  },
)

const emit = defineEmits<{
  select: [site: GreenhouseSite]
}>()

const canvasHost = ref<HTMLDivElement | null>(null)
const hoveredId = ref('')
const labelPositions = ref<Record<string, LabelPosition>>(
  Object.fromEntries(
    greenhouseSites.map((site) => [
      site.id,
      { x: 0, y: 0, offsetY: 0, visible: false },
    ]),
  ),
)

let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let renderer: THREE.WebGLRenderer | null = null
let controls: OrbitControls | null = null
let parkGroup: THREE.Group | null = null
let resizeObserver: ResizeObserver | null = null
let animationFrame = 0
let pointerDownPosition: { x: number; y: number } | null = null
let siteEntries: SiteSceneEntry[] = []
let plantTemplate: THREE.Object3D | null = null
let parkPlantBatches: PlantBatchHandle[] = []
let lastOverlayUpdate = 0
const sceneQuality = getSceneQualityProfile()

const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()
const pointerTarget = new THREE.Vector2()
const clock = new THREE.Clock()

function createBeam(
  start: THREE.Vector3,
  end: THREE.Vector3,
  material: THREE.Material,
) {
  const direction = new THREE.Vector3().subVectors(end, start)
  const mesh = new THREE.Mesh(
    new THREE.CylinderGeometry(0.025, 0.025, 1, 8),
    material,
  )
  mesh.position.copy(start).add(end).multiplyScalar(0.5)
  mesh.quaternion.setFromUnitVectors(
    new THREE.Vector3(0, 1, 0),
    direction.clone().normalize(),
  )
  mesh.scale.y = direction.length()
  return mesh
}

function createRoofPanel(
  points: THREE.Vector3[],
  material: THREE.Material,
) {
  const geometry = new THREE.BufferGeometry().setFromPoints(points)
  geometry.setIndex([0, 1, 2, 0, 2, 3])
  geometry.computeVertexNormals()
  return new THREE.Mesh(geometry, material)
}

function createSiteModel(site: GreenhouseSite) {
  const group = new THREE.Group()
  const frameMaterial = new THREE.MeshStandardMaterial({
    color: 0xa8c6b7,
    metalness: 0.5,
    roughness: 0.34,
  })
  const glassMaterial = new THREE.MeshPhysicalMaterial({
    color: 0xbce9d2,
    transparent: true,
    opacity: 0.17,
    roughness: 0.08,
    metalness: 0.02,
    side: THREE.DoubleSide,
    depthWrite: false,
  })
  const bedMaterial = new THREE.MeshStandardMaterial({
    color: 0x2a5a40,
    roughness: 0.9,
  })
  const base = new THREE.Mesh(
    new THREE.BoxGeometry(5.4, 0.1, 3.5),
    new THREE.MeshStandardMaterial({
      color: 0x153024,
      roughness: 0.92,
    }),
  )
  base.position.y = -0.06
  group.add(base)

  const beds = [-1.55, 0, 1.55]
  const plantPlacements: PlantBatchInstance[] = []
  const plantProfile = getCropPlantProfile(site.crop)
  beds.forEach((x, bedIndex) => {
    const bed = new THREE.Mesh(
      new THREE.BoxGeometry(0.88, 0.16, 2.72),
      bedMaterial,
    )
    bed.position.set(x, 0.1, 0)
    group.add(bed)

    for (let row = 0; row < 4; row += 1) {
      const plantIndex = bedIndex * 4 + row
      plantPlacements.push({
        position: [x, 0.19, -0.96 + row * 0.64],
        targetHeight:
          plantProfile.baseHeight * (0.94 + (plantIndex % 3) * 0.045),
        bloomColor:
          plantProfile.bloomColors[plantIndex % plantProfile.bloomColors.length],
        stemColor: plantProfile.stemColorLight,
        rotationY: plantIndex * 0.83,
        lean: plantIndex % 2 === 0 ? -0.04 : 0.04,
      })
    }
  })
  const plantBatch = createPlantBatch(plantTemplate, plantPlacements)
  parkPlantBatches.push(plantBatch)
  group.add(plantBatch.group)

  const zEdges = [-1.72, 1.72]
  const xEdges = [-2.65, -1.32, 0, 1.32, 2.65]
  zEdges.forEach((z) => {
    xEdges.forEach((x) => {
      group.add(
        createBeam(
          new THREE.Vector3(x, 0.02, z),
          new THREE.Vector3(x, x === 0 ? 2.05 : 1.5, z),
          frameMaterial,
        ),
      )
    })
    group.add(
      createBeam(
        new THREE.Vector3(-2.65, 1.5, z),
        new THREE.Vector3(2.65, 1.5, z),
        frameMaterial,
      ),
    )
    group.add(
      createBeam(
        new THREE.Vector3(-2.65, 1.5, z),
        new THREE.Vector3(0, 2.05, z),
        frameMaterial,
      ),
    )
    group.add(
      createBeam(
        new THREE.Vector3(0, 2.05, z),
        new THREE.Vector3(2.65, 1.5, z),
        frameMaterial,
      ),
    )
  })

  group.add(
    createBeam(
      new THREE.Vector3(0, 2.05, -1.72),
      new THREE.Vector3(0, 2.05, 1.72),
      frameMaterial,
    ),
  )

  group.add(
    createRoofPanel(
      [
        new THREE.Vector3(-2.65, 1.5, -1.72),
        new THREE.Vector3(-2.65, 1.5, 1.72),
        new THREE.Vector3(0, 2.05, 1.72),
        new THREE.Vector3(0, 2.05, -1.72),
      ],
      glassMaterial,
    ),
  )
  group.add(
    createRoofPanel(
      [
        new THREE.Vector3(2.65, 1.5, -1.72),
        new THREE.Vector3(2.65, 1.5, 1.72),
        new THREE.Vector3(0, 2.05, 1.72),
        new THREE.Vector3(0, 2.05, -1.72),
      ],
      glassMaterial,
    ),
  )

  const hitMesh = new THREE.Mesh(
    new THREE.BoxGeometry(5.8, 2.4, 3.8),
    new THREE.MeshBasicMaterial({
      transparent: true,
      opacity: 0.001,
      depthWrite: false,
    }),
  )
  hitMesh.position.y = 0.95
  hitMesh.userData.siteId = site.id
  group.add(hitMesh)

  const ring = new THREE.Mesh(
    new THREE.RingGeometry(3.05, 3.24, 48),
    new THREE.MeshBasicMaterial({
      color: 0x86daa4,
      transparent: true,
      opacity: 0,
      side: THREE.DoubleSide,
      depthWrite: false,
    }),
  )
  ring.rotation.x = -Math.PI / 2
  ring.position.y = 0.01
  group.add(ring)

  const outline = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(5.45, 2.08, 3.55)),
    new THREE.LineBasicMaterial({
      color: 0x9bd8b2,
      transparent: true,
      opacity: 0.14,
      depthWrite: false,
    }),
  )
  outline.position.y = 0.96
  group.add(outline)

  group.position.set(...site.position)
  group.scale.setScalar(0.9)
  return { group, ring, hitMesh, outline }
}

function buildScene() {
  if (!canvasHost.value) return

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x06130e)
  scene.fog = new THREE.Fog(0x06130e, 18, 36)

  camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100)
  camera.position.set(17.4, 13.8, 21.6)

  renderer = new THREE.WebGLRenderer({
    antialias: sceneQuality.antialias,
    powerPreference: 'high-performance',
  })
  renderer.setPixelRatio(sceneQuality.maxPixelRatio)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.24
  renderer.shadowMap.enabled = sceneQuality.enableShadows
  renderer.domElement.setAttribute('aria-label', '温室园区三维总览')
  canvasHost.value.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.065
  controls.enablePan = false
  controls.minDistance = 13
  controls.maxDistance = 32
  controls.minPolarAngle = 0.48
  controls.maxPolarAngle = Math.PI / 2.12
  controls.target.set(0, 0.18, 0)
  controls.autoRotate = props.autoRotate && !sceneQuality.reduceMotion
  controls.autoRotateSpeed = 0.32

  scene.add(new THREE.HemisphereLight(0xe9fff3, 0x173c2b, 3.8))

  const keyLight = new THREE.DirectionalLight(0xffffff, 4.2)
  keyLight.position.set(10, 15, 9)
  keyLight.castShadow = sceneQuality.enableShadows
  keyLight.shadow.mapSize.set(
    sceneQuality.shadowMapSize,
    sceneQuality.shadowMapSize,
  )
  keyLight.shadow.camera.left = -16
  keyLight.shadow.camera.right = 16
  keyLight.shadow.camera.top = 16
  keyLight.shadow.camera.bottom = -16
  scene.add(keyLight)

  const fillLight = new THREE.DirectionalLight(0xade7c8, 1.7)
  fillLight.position.set(-9, 7, -11)
  scene.add(fillLight)

  const rimLight = new THREE.DirectionalLight(0xffe2ad, 1.25)
  rimLight.position.set(-7, 4.5, 11)
  scene.add(rimLight)

  const ground = new THREE.Mesh(
    new THREE.CircleGeometry(22, 72),
    new THREE.MeshStandardMaterial({
      color: 0x10281d,
      roughness: 0.98,
    }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -0.16
  ground.receiveShadow = true
  scene.add(ground)

  const focusCanvas = document.createElement('canvas')
  focusCanvas.width = 256
  focusCanvas.height = 256
  const focusContext = focusCanvas.getContext('2d')
  if (focusContext) {
    const gradient = focusContext.createRadialGradient(128, 128, 0, 128, 128, 128)
    gradient.addColorStop(0, 'rgba(73, 160, 113, 0.34)')
    gradient.addColorStop(0.46, 'rgba(44, 112, 78, 0.18)')
    gradient.addColorStop(1, 'rgba(9, 31, 21, 0)')
    focusContext.fillStyle = gradient
    focusContext.fillRect(0, 0, 256, 256)
  }
  const focusTexture = new THREE.CanvasTexture(focusCanvas)
  focusTexture.colorSpace = THREE.SRGBColorSpace
  const focusDisk = new THREE.Mesh(
    new THREE.CircleGeometry(15.2, 72),
    new THREE.MeshBasicMaterial({
      map: focusTexture,
      transparent: true,
      opacity: 0.9,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    }),
  )
  focusDisk.rotation.x = -Math.PI / 2
  focusDisk.position.y = -0.13
  scene.add(focusDisk)

  const grid = new THREE.GridHelper(28, 36, 0x315c46, 0x173225)
  grid.position.y = -0.14
  const gridMaterials = Array.isArray(grid.material)
    ? grid.material
    : [grid.material]
  gridMaterials.forEach((material) => {
    material.transparent = true
    material.opacity = 0.3
  })
  scene.add(grid)

  parkGroup = new THREE.Group()
  parkPlantBatches = []
  greenhouseSites.forEach((site) => {
    const model = createSiteModel(site)
    parkGroup?.add(model.group)
    siteEntries.push({
      site,
      group: model.group,
      ring: model.ring,
      hitMesh: model.hitMesh,
      outline: model.outline,
    })
  })
  scene.add(parkGroup)

  renderer.domElement.addEventListener('pointerdown', handlePointerDown)
  renderer.domElement.addEventListener('pointerup', handlePointerUp)
  renderer.domElement.addEventListener('pointermove', handlePointerMove)
  renderer.domElement.addEventListener('pointerleave', handlePointerLeave)
  document.addEventListener('visibilitychange', handleVisibilityChange)

  resizeObserver = new ResizeObserver(resizeRenderer)
  resizeObserver.observe(canvasHost.value)
  resizeRenderer()
  animate()
}

function handlePointerDown(event: PointerEvent) {
  pointerDownPosition = { x: event.clientX, y: event.clientY }
}

function handlePointerUp(event: PointerEvent) {
  if (
    !pointerDownPosition ||
    Math.hypot(
      event.clientX - pointerDownPosition.x,
      event.clientY - pointerDownPosition.y,
    ) > 5
  ) {
    return
  }

  const siteId = raycastSite(event)
  const entry = siteEntries.find((item) => item.site.id === siteId)
  if (entry) emit('select', entry.site)
}

function handlePointerMove(event: PointerEvent) {
  if (!renderer) return
  const siteId = raycastSite(event)
  const bounds = renderer.domElement.getBoundingClientRect()
  pointerTarget.x = ((event.clientX - bounds.left) / bounds.width) * 2 - 1
  pointerTarget.y = ((event.clientY - bounds.top) / bounds.height) * 2 - 1
  setHoveredSite(siteId ?? '')
  renderer.domElement.style.cursor = siteId ? 'pointer' : 'grab'
}

function handlePointerLeave() {
  setHoveredSite('')
  pointerTarget.set(0, 0)
  if (renderer) renderer.domElement.style.cursor = 'grab'
}

function setHoveredSite(siteId: string) {
  hoveredId.value = siteId
  if (controls) controls.autoRotate = siteId ? false : props.autoRotate
}

function raycastSite(event: PointerEvent) {
  if (!renderer || !camera) return null
  const bounds = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - bounds.left) / bounds.width) * 2 - 1
  pointer.y = -((event.clientY - bounds.top) / bounds.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)

  const intersections = raycaster.intersectObjects(
    siteEntries.map((entry) => entry.hitMesh),
    false,
  )
  return intersections[0]?.object.userData.siteId as string | undefined
}

function updateLabels() {
  if (!renderer || !camera) return
  const activeCamera = camera
  const width = renderer.domElement.clientWidth
  const height = renderer.domElement.clientHeight
  const hostBounds = canvasHost.value?.getBoundingClientRect()
  const visibleTop = hostBounds ? Math.max(0, -hostBounds.top) : 0
  const visibleBottom = hostBounds
    ? Math.min(height, window.innerHeight - hostBounds.top)
    : height
  const topSafeArea = width <= 720 ? 56 : 70
  const bottomSafeArea = width <= 720 ? 78 : 64
  const next: Record<string, LabelPosition> = Object.fromEntries(
    greenhouseSites.map((site) => [
      site.id,
      { x: 0, y: 0, offsetY: 0, visible: false },
    ]),
  )
  const candidates: Array<{
    id: string
    x: number
    y: number
  }> = []

  siteEntries.forEach((entry) => {
    const worldPosition = new THREE.Vector3(
      entry.site.position[0],
      entry.site.position[1] + 2.55,
      entry.site.position[2],
    )
    const projected = worldPosition.project(activeCamera)
    const x = (projected.x * 0.5 + 0.5) * width
    const y = (-projected.y * 0.5 + 0.5) * height
    if (
      projected.z > -1 &&
      projected.z < 1 &&
      x > 50 &&
      x < width - 50 &&
      y > visibleTop + topSafeArea &&
      y < visibleBottom - bottomSafeArea
    ) {
      candidates.push({ id: entry.site.id, x, y })
    }
  })

  const labelWidth = width <= 720 ? 138 : 178
  const labelHeight = width <= 720 ? 56 : 70
  const labelGap = 8
  const placed: Array<{
    left: number
    right: number
    top: number
    bottom: number
  }> = []

  candidates
    .sort((left, right) => left.y - right.y || left.x - right.x)
    .forEach((candidate) => {
      let offsetY = 0
      let rectangle = {
        left: candidate.x - labelWidth / 2,
        right: candidate.x + labelWidth / 2,
        top: candidate.y - labelGap - labelHeight,
        bottom: candidate.y - labelGap,
      }

      for (let attempt = 0; attempt < 7; attempt += 1) {
        const overlaps = placed.some(
          (item) =>
            rectangle.left < item.right + 8 &&
            rectangle.right > item.left - 8 &&
            rectangle.top < item.bottom + 6 &&
            rectangle.bottom > item.top - 6,
        )
        if (!overlaps) break
        offsetY += labelHeight + 8
        rectangle = {
          ...rectangle,
          top: rectangle.top - labelHeight - 8,
          bottom: rectangle.bottom - labelHeight - 8,
        }
      }

      const visible =
        rectangle.top >= visibleTop + 8 &&
        rectangle.bottom <= visibleBottom - 8
      next[candidate.id] = {
        x: candidate.x,
        y: candidate.y,
        offsetY,
        visible,
      }
      if (visible) {
        placed.push(rectangle)
      }
    })

  labelPositions.value = next
}

function animate() {
  if (!scene || !camera || !renderer || !controls || !parkGroup) return
  animationFrame = window.requestAnimationFrame(animate)
  const elapsed = clock.getElapsedTime()

  if (!hoveredId.value) {
    parkGroup.rotation.y +=
      (pointerTarget.x * 0.035 - parkGroup.rotation.y) * 0.035
    parkGroup.rotation.x +=
      (-pointerTarget.y * 0.018 - parkGroup.rotation.x) * 0.035
  }

  siteEntries.forEach((entry, index) => {
    const active =
      entry.site.id === hoveredId.value || entry.site.id === props.selectedId
    entry.ring.material.opacity = active
      ? 0.58 + Math.sin(elapsed * 2.8 + index) * 0.12
      : 0
    const targetOutlineOpacity = active ? 0.72 : 0.14
    entry.outline.material.opacity +=
      (targetOutlineOpacity - entry.outline.material.opacity) * 0.12
    const targetY = active ? 0.12 : 0
    entry.group.position.y += (targetY - entry.group.position.y) * 0.08
  })

  parkPlantBatches.forEach((batch) => batch.update(elapsed))

  controls.update()
  renderer.render(scene, camera)
  const nowMs = performance.now()
  if (nowMs - lastOverlayUpdate >= 1000 / sceneQuality.overlayFps) {
    lastOverlayUpdate = nowMs
    updateLabels()
  }
}

function handleVisibilityChange() {
  if (document.hidden) {
    window.cancelAnimationFrame(animationFrame)
    animationFrame = 0
    return
  }
  if (!animationFrame) {
    animate()
  }
}

function resizeRenderer() {
  if (!renderer || !camera || !canvasHost.value) return
  const { clientWidth, clientHeight } = canvasHost.value
  if (!clientWidth || !clientHeight) return
  camera.aspect = clientWidth / clientHeight
  camera.updateProjectionMatrix()
  renderer.setSize(clientWidth, clientHeight, false)
  lastOverlayUpdate = 0
  updateLabels()
}

function resetView() {
  if (!camera || !controls) return
  camera.position.set(17.4, 13.8, 21.6)
  controls.target.set(0, 0.18, 0)
  controls.update()
}

function disposeObject(root: THREE.Object3D) {
  root.traverse((object) => {
    if (
      !(object instanceof THREE.Mesh) &&
      !(object instanceof THREE.LineSegments)
    ) {
      return
    }
    object.geometry.dispose()
    const materials = Array.isArray(object.material)
      ? object.material
      : [object.material]
    materials.forEach((material) => {
      const materialWithMap = material as THREE.Material & {
        map?: THREE.Texture
      }
      materialWithMap.map?.dispose()
      material.dispose()
    })
  })
}

watch(
  () => props.autoRotate,
  (value) => {
    if (controls && !hoveredId.value) {
      controls.autoRotate = value && !sceneQuality.reduceMotion
    }
  },
)

onMounted(async () => {
  plantTemplate = await loadPlantModel()
  buildScene()
})

onBeforeUnmount(() => {
  window.cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (renderer) {
    renderer.domElement.removeEventListener('pointerdown', handlePointerDown)
    renderer.domElement.removeEventListener('pointerup', handlePointerUp)
    renderer.domElement.removeEventListener('pointermove', handlePointerMove)
    renderer.domElement.removeEventListener('pointerleave', handlePointerLeave)
  }
  if (scene) disposeObject(scene)
  controls?.dispose()
  renderer?.dispose()
  siteEntries = []
  parkPlantBatches = []
  canvasHost.value?.replaceChildren()
})

defineExpose({ resetView })
</script>

<template>
  <div class="park-scene">
    <div ref="canvasHost" class="park-scene__canvas"></div>

    <div class="park-scene__labels">
      <button
        v-for="site in greenhouseSites"
        v-show="labelPositions[site.id]?.visible"
        :key="site.id"
        class="park-site-label"
        :class="{
          'is-active':
            hoveredId === site.id || selectedId === site.id,
        }"
        :style="{
          '--label-x': `${labelPositions[site.id]?.x ?? 0}px`,
          '--label-y': `${labelPositions[site.id]?.y ?? 0}px`,
          '--label-offset': `${labelPositions[site.id]?.offsetY ?? 0}px`,
        }"
        type="button"
        @mouseenter="setHoveredSite(site.id)"
        @mouseleave="setHoveredSite('')"
        @click="emit('select', site)"
      >
        <span>{{ site.name }}</span>
        <strong>{{ site.crop }}</strong>
        <div class="park-site-label__meta">
          <small>{{ site.metric }}</small>
          <em :class="`is-${site.tone}`">{{ site.status }}</em>
        </div>
      </button>
    </div>

    <div class="park-scene__hint">
      <MousePointer2 :size="14" />
      <span>悬停高亮 · 点击温室进入数字孪生</span>
    </div>

    <button class="park-scene__reset" type="button" aria-label="重置园区视角" @click="resetView">
      <LocateFixed :size="16" />
    </button>
  </div>
</template>

<style scoped>
.park-scene {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 100%;
  overflow: hidden;
  background: #06130e;
}

.park-scene__canvas,
.park-scene__canvas :deep(canvas) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.park-scene__labels {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
}

.park-site-label {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  gap: 3px;
  width: 178px;
  padding: 10px 12px;
  color: #dcebe2;
  text-align: left;
  pointer-events: auto;
  background: rgb(8 25 17 / 82%);
  border: 1px solid rgb(185 226 201 / 18%);
  border-radius: 9px;
  transform: translate3d(var(--label-x, 0), var(--label-y, 0), 0)
    translate(-50%, calc(-100% - 8px - var(--label-offset, 0px)));
  backdrop-filter: blur(10px);
  transition: border-color 160ms ease, background-color 160ms ease, transform 160ms ease;
}

.park-site-label::after {
  position: absolute;
  top: 100%;
  left: 50%;
  width: 1px;
  height: calc(10px + var(--label-offset, 0px));
  content: "";
  background: rgb(185 226 201 / 42%);
}

.park-site-label:hover,
.park-site-label.is-active {
  background: rgb(12 38 26 / 94%);
  border-color: #8fd4a8;
  transform: translate3d(var(--label-x, 0), var(--label-y, 0), 0)
    translate(-50%, calc(-100% - 8px - var(--label-offset, 0px))) scale(1.035);
}

.park-site-label > span {
  color: #8fd4a8;
  font-size: 12px;
  font-weight: 600;
}

.park-site-label strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.park-site-label__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.park-site-label small,
.park-site-label em {
  color: #91a99c;
  font-size: 10px;
  font-style: normal;
  white-space: nowrap;
}

.park-site-label em.is-warning {
  color: #f0c46d;
}

.park-site-label em.is-running {
  color: #8fd4a8;
}

.park-scene__hint,
.park-scene__reset {
  position: absolute;
  z-index: 4;
  color: #d9e8df;
  background: rgb(13 27 20 / 82%);
  border: 1px solid rgb(185 226 201 / 20%);
  backdrop-filter: blur(12px);
}

.park-scene__hint {
  right: 14px;
  bottom: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 10px;
}

.park-scene__reset {
  top: 14px;
  right: 14px;
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 8px;
}

@media (max-width: 720px) {
  .park-site-label {
    width: 138px;
    padding: 8px 9px;
    transform: translate3d(var(--label-x, 0), var(--label-y, 0), 0)
      translate(-50%, calc(-100% - 6px - var(--label-offset, 0px)));
  }

  .park-site-label:hover,
  .park-site-label.is-active {
    transform: translate3d(var(--label-x, 0), var(--label-y, 0), 0)
      translate(-50%, calc(-100% - 6px - var(--label-offset, 0px))) scale(1.03);
  }

  .park-site-label__meta em {
    display: none;
  }
}
</style>

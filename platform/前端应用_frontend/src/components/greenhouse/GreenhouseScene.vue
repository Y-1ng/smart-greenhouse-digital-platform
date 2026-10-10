<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import {
  Compass,
  LocateFixed,
  Moon,
  MousePointer2,
  Rotate3d,
  Sun,
} from 'lucide-vue-next'
import { greenhouseHotspots } from '@/data/greenhouse'
import type { GreenhouseHotspot, SceneMode } from '@/types/greenhouse'
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

const CELESTIAL_LOCATION = {
  label: '中国北方',
  latitude: 40.84,
  longitude: 111.75,
  standardMeridian: 120,
}

const props = withDefaults(
  defineProps<{
    mode: SceneMode
    selectedId: string
    autoRotate: boolean
    crop?: string
    activeZone?: string
    highlightMetric?: string
    theme?: 'light' | 'dark'
    immersive?: boolean
  }>(),
  {
    theme: 'light',
    immersive: false,
    crop: '',
    activeZone: '',
    highlightMetric: '',
  },
)

const emit = defineEmits<{
  select: [hotspot: GreenhouseHotspot]
}>()

interface MarkerPosition {
  x: number
  y: number
  visible: boolean
}

interface MarkerMeshEntry {
  id: string
  mesh: THREE.Mesh
}

interface ZoneDefinition {
  id: string
  label: string
  aliases: string[]
  x: number
  z: number
  width: number
  depth: number
  color: number
}

interface ZoneMeshEntry {
  definition: ZoneDefinition
  mesh: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>
  border: THREE.LineLoop<THREE.BufferGeometry, THREE.LineBasicMaterial>
}

interface CompassPoint {
  id: 'north' | 'east' | 'south' | 'west'
  label: 'N' | 'E' | 'S' | 'W'
  position: [number, number, number]
}

interface CameraTween {
  startPosition: THREE.Vector3
  endPosition: THREE.Vector3
  startTarget: THREE.Vector3
  endTarget: THREE.Vector3
  startedAt: number
  duration: number
}

const canvasHost = ref<HTMLDivElement | null>(null)
const sceneReady = ref(false)
const sceneError = ref('')

const markerPositions = ref<Record<string, MarkerPosition>>(
  Object.fromEntries(
    greenhouseHotspots.map((hotspot) => [
      hotspot.id,
      { x: 0, y: 0, visible: false },
    ]),
  ),
)

const zoneDefinitions: ZoneDefinition[] = [
  {
    id: 'zone-a',
    label: 'A区',
    aliases: ['A区'],
    x: -1.82,
    z: 0,
    width: 3.18,
    depth: 3.72,
    color: 0x56b788,
  },
  {
    id: 'zone-b',
    label: 'B区',
    aliases: ['B区'],
    x: 0.91,
    z: 0,
    width: 1.16,
    depth: 3.72,
    color: 0xd9a23f,
  },
  {
    id: 'zone-c',
    label: 'C区',
    aliases: ['C区'],
    x: 2.72,
    z: 0,
    width: 1.16,
    depth: 3.72,
    color: 0x5b8fd9,
  },
]

const zonePositions = ref<Record<string, MarkerPosition>>(
  Object.fromEntries(
    zoneDefinitions.map((zone) => [
      zone.id,
      { x: 0, y: 0, visible: false },
    ]),
  ),
)

const compassPoints: CompassPoint[] = [
  { id: 'north', label: 'N', position: [0, 0.08, -3.72] },
  { id: 'east', label: 'E', position: [5.05, 0.08, 0] },
  { id: 'south', label: 'S', position: [0, 0.08, 3.72] },
  { id: 'west', label: 'W', position: [-5.05, 0.08, 0] },
]

const compassPositions = ref<Record<string, MarkerPosition>>(
  Object.fromEntries(
    compassPoints.map((point) => [
      point.id,
      { x: 0, y: 0, visible: false },
    ]),
  ),
)

const metricHotspotMap: Record<string, string> = {
  temperature: 'air-sensor',
  humidity: 'air-sensor',
  light: 'light-sensor',
  soil_moisture: 'soil-sensor',
}

const celestialState = ref({
  sunVisible: false,
  sunAzimuth: 180,
  sunElevation: 0,
  moonVisible: false,
  moonAzimuth: 0,
  moonElevation: 0,
})

const sunDirectionText = computed(() =>
  directionLabel(celestialState.value.sunAzimuth * Math.PI / 180),
)
const moonDirectionText = computed(() =>
  directionLabel(celestialState.value.moonAzimuth * Math.PI / 180),
)

const visibleHotspots = computed(() =>
  greenhouseHotspots.filter((hotspot) => {
    if (props.mode === 'overview') return true
    if (props.mode === 'environment') return hotspot.category === 'sensor'
    return hotspot.category === 'actuator'
  }),
)

let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let renderer: THREE.WebGLRenderer | null = null
let controls: OrbitControls | null = null
let resizeObserver: ResizeObserver | null = null
let markerMeshes: MarkerMeshEntry[] = []
let zoneMeshes: ZoneMeshEntry[] = []
let pointerDownPosition: { x: number; y: number } | null = null
let greenhouseModel: THREE.Group | null = null
let cameraTween: CameraTween | null = null
let plantTemplate: THREE.Object3D | null = null
let plantBatchHandle: PlantBatchHandle | null = null
let lastOverlayUpdate = 0
let lastCelestialUpdate = 0
let hemisphereLight: THREE.HemisphereLight | null = null
let sunLight: THREE.DirectionalLight | null = null
let moonLight: THREE.DirectionalLight | null = null
let fillLight: THREE.DirectionalLight | null = null
let hologramGroup: THREE.Group | null = null
let sunModel: THREE.Group | null = null
let moonModel: THREE.Group | null = null
let starField: THREE.Points | null = null
let isDarkTheme = false
const sceneQuality = getSceneQualityProfile()

const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()
const pointerTarget = new THREE.Vector2()
const clock = new THREE.Clock()

function createBeam(
  start: THREE.Vector3,
  end: THREE.Vector3,
  radius: number,
  material: THREE.Material,
) {
  const direction = new THREE.Vector3().subVectors(end, start)
  const mesh = new THREE.Mesh(
    new THREE.CylinderGeometry(radius, radius, 1, 8),
    material,
  )

  mesh.position.copy(start).add(end).multiplyScalar(0.5)
  mesh.quaternion.setFromUnitVectors(
    new THREE.Vector3(0, 1, 0),
    direction.clone().normalize(),
  )
  mesh.scale.y = direction.length()
  mesh.castShadow = true
  return mesh
}

function createPlaneFromPoints(
  points: THREE.Vector3[],
  material: THREE.Material,
) {
  const geometry = new THREE.BufferGeometry().setFromPoints(points)
  geometry.setIndex(points.length === 4 ? [0, 1, 2, 0, 2, 3] : [])
  geometry.computeVertexNormals()
  return new THREE.Mesh(geometry, material)
}

function createGableGeometry() {
  const shape = new THREE.Shape()
  shape.moveTo(-4.2, 0)
  shape.lineTo(4.2, 0)
  shape.lineTo(4.2, 2.2)
  shape.lineTo(0, 3)
  shape.lineTo(-4.2, 2.2)
  shape.closePath()
  return new THREE.ShapeGeometry(shape)
}

function createZoneOverlay(
  zone: ZoneDefinition,
): ZoneMeshEntry {
  const geometry = new THREE.PlaneGeometry(zone.width, zone.depth)
  const mesh = new THREE.Mesh(
    geometry,
    new THREE.MeshBasicMaterial({
      color: zone.color,
      transparent: true,
      opacity: 0.05,
      side: THREE.DoubleSide,
      depthWrite: false,
    }),
  )
  mesh.rotation.x = -Math.PI / 2
  mesh.position.set(zone.x, 0.265, zone.z)
  mesh.renderOrder = 1

  const halfWidth = zone.width / 2
  const halfDepth = zone.depth / 2
  const border = new THREE.LineLoop(
    new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-halfWidth, 0, -halfDepth),
      new THREE.Vector3(halfWidth, 0, -halfDepth),
      new THREE.Vector3(halfWidth, 0, halfDepth),
      new THREE.Vector3(-halfWidth, 0, halfDepth),
    ]),
    new THREE.LineBasicMaterial({
      color: zone.color,
      transparent: true,
      opacity: 0.24,
      depthWrite: false,
    }),
  )
  border.position.set(zone.x, 0.275, zone.z)
  border.renderOrder = 2

  return { definition: zone, mesh, border }
}

function createSunModel() {
  const group = new THREE.Group()
  const sun = new THREE.Mesh(
    new THREE.SphereGeometry(0.66, 28, 18),
    new THREE.MeshStandardMaterial({
      color: 0xffd36a,
      emissive: 0xffb43c,
      emissiveIntensity: 2.8,
      roughness: 0.34,
      metalness: 0,
      fog: false,
    }),
  )
  const glow = new THREE.Mesh(
    new THREE.SphereGeometry(1.12, 24, 16),
    new THREE.MeshBasicMaterial({
      color: 0xffc75a,
      transparent: true,
      opacity: 0.2,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      fog: false,
    }),
  )
  group.add(sun, glow)
  group.renderOrder = 8
  return group
}

function createMoonModel() {
  const group = new THREE.Group()
  const moon = new THREE.Mesh(
    new THREE.SphereGeometry(0.52, 28, 18),
    new THREE.MeshStandardMaterial({
      color: 0xeef4ff,
      emissive: 0xbcd2f0,
      emissiveIntensity: 1.16,
      roughness: 0.9,
      metalness: 0,
      fog: false,
    }),
  )
  const craterMaterial = new THREE.MeshBasicMaterial({
    color: 0x8fa2bc,
    transparent: true,
    opacity: 0.5,
    fog: false,
  })
  const craterPositions = [
    [-0.18, 0.18, 0.42],
    [0.2, 0.05, 0.45],
    [0.08, -0.2, 0.48],
    [-0.28, -0.12, 0.38],
    [0.31, 0.25, 0.26],
  ]
  craterPositions.forEach(([x, y, z], index) => {
    const crater = new THREE.Mesh(
      new THREE.CircleGeometry(0.07 + index * 0.008, 12),
      craterMaterial.clone(),
    )
    crater.position.set(x, y, z)
    crater.lookAt(0, 0, 0)
    crater.rotateY(Math.PI)
    group.add(crater)
  })
  const glow = new THREE.Mesh(
    new THREE.SphereGeometry(1.02, 24, 16),
    new THREE.MeshBasicMaterial({
      color: 0xa9c8ff,
      transparent: true,
      opacity: 0.2,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      fog: false,
    }),
  )
  group.add(moon, glow)
  group.renderOrder = 8
  return group
}

function createStarField() {
  const positions: number[] = []
  for (let index = 0; index < 420; index += 1) {
    const radius = 24 + Math.random() * 18
    const theta = Math.random() * Math.PI * 2
    const phi = Math.acos(2 * Math.random() - 1)
    positions.push(
      radius * Math.sin(phi) * Math.cos(theta),
      radius * Math.cos(phi) * 0.55 + 5,
      radius * Math.sin(phi) * Math.sin(theta),
    )
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute(
    'position',
    new THREE.Float32BufferAttribute(positions, 3),
  )
  const material = new THREE.PointsMaterial({
    color: 0xa9c8ff,
    size: 0.075,
    transparent: true,
    opacity: 0.46,
    depthWrite: false,
    fog: false,
    sizeAttenuation: true,
  })
  const points = new THREE.Points(geometry, material)
  points.renderOrder = -1
  return points
}

function dayOfYear(date: Date) {
  const start = Date.UTC(date.getFullYear(), 0, 0)
  const current = Date.UTC(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
  )
  return Math.floor((current - start) / 86_400_000)
}

function degreesToRadians(value: number) {
  return value * Math.PI / 180
}

function normalizeAzimuth(value: number) {
  const full = Math.PI * 2
  return ((value % full) + full) % full
}

function directionLabel(azimuthRadians: number) {
  const directions = ['北', '东北', '东', '东南', '南', '西南', '西', '西北']
  const azimuthDegrees = azimuthRadians * 180 / Math.PI
  const index = Math.round(azimuthDegrees / 45) % directions.length
  return directions[index]
}

function updateCelestialState(timestamp: number) {
  if (
    !scene ||
    !renderer ||
    !sunLight ||
    !moonLight ||
    !hemisphereLight ||
    !fillLight ||
    !sunModel ||
    !moonModel
  ) {
    return
  }

  const date = new Date(timestamp)
  const localHours =
    date.getHours()
    + date.getMinutes() / 60
    + date.getSeconds() / 3600
  const latitude = degreesToRadians(CELESTIAL_LOCATION.latitude)
  const declination = degreesToRadians(
    23.44 * Math.sin(
      Math.PI * 2 * (284 + dayOfYear(date)) / 365,
    ),
  )
  const longitudeCorrectionMinutes =
    (CELESTIAL_LOCATION.longitude - CELESTIAL_LOCATION.standardMeridian) * 4
  const solarHours = localHours + longitudeCorrectionMinutes / 60
  const hourAngle = degreesToRadians((solarHours - 12) * 15)
  const elevation = Math.asin(
    Math.sin(latitude) * Math.sin(declination)
    + Math.cos(latitude) * Math.cos(declination) * Math.cos(hourAngle),
  )
  const azimuth = normalizeAzimuth(
    Math.atan2(
      Math.sin(hourAngle),
      Math.cos(hourAngle) * Math.sin(latitude)
        - Math.tan(declination) * Math.cos(latitude),
    ) + Math.PI,
  )
  const radius = isDarkTheme ? 9.8 : 11.8
  const sunPosition = new THREE.Vector3(
    radius * Math.cos(elevation) * Math.sin(azimuth),
    radius * Math.sin(elevation),
    -radius * Math.cos(elevation) * Math.cos(azimuth),
  )
  const moonElevation = -elevation
  const moonAzimuth = normalizeAzimuth(azimuth + Math.PI)
  const moonPosition = new THREE.Vector3(
    radius * Math.cos(moonElevation) * Math.sin(moonAzimuth),
    radius * Math.sin(moonElevation),
    -radius * Math.cos(moonElevation) * Math.cos(moonAzimuth),
  )

  sunModel.position.copy(sunPosition)
  moonModel.position.copy(moonPosition)
  sunModel.visible = elevation > -0.05
  moonModel.visible = moonElevation > -0.05
  sunLight.position.copy(sunPosition)
  moonLight.position.copy(moonPosition)
  celestialState.value = {
    sunVisible: sunModel.visible,
    sunAzimuth: azimuth * 180 / Math.PI,
    sunElevation: elevation * 180 / Math.PI,
    moonVisible: moonModel.visible,
    moonAzimuth: moonAzimuth * 180 / Math.PI,
    moonElevation: moonElevation * 180 / Math.PI,
  }

  const daylight = Math.max(0, Math.min(1, Math.sin(elevation)))
  sunLight.intensity = daylight > 0 ? 0.4 + daylight * 3.8 : 0
  moonLight.intensity = moonElevation > 0 ? 0.78 : 0
  hemisphereLight.intensity = (isDarkTheme ? 1.7 : 0.9) + daylight * 2.1
  fillLight.intensity = 0.42 + daylight * (isDarkTheme ? 1.5 : 1)
  renderer.toneMappingExposure =
    (isDarkTheme ? 1.02 : 0.78) + daylight * 0.38

  const dayColor = new THREE.Color(isDarkTheme ? 0x0b2a1d : 0xdceef2)
  const nightColor = new THREE.Color(isDarkTheme ? 0x020806 : 0x091521)
  const skyColor = nightColor.clone().lerp(dayColor, daylight)
  scene.background = skyColor
  scene.fog?.color.copy(skyColor)
}

function focusCelestial(kind: 'sun' | 'moon') {
  if (!camera || !controls) {
    return
  }
  const model = kind === 'sun' ? sunModel : moonModel
  const celestial = kind === 'sun'
    ? celestialState.value.sunVisible
    : celestialState.value.moonVisible
  if (!model || !celestial) {
    return
  }
  const target = model.position.clone()
  const direction = target.clone().normalize()
  const cameraPosition = direction.multiplyScalar(8.6)
  cameraPosition.y = Math.max(cameraPosition.y, 1.8)
  startCameraTween(cameraPosition, target, 720)
}

function createFan(position: THREE.Vector3, rotationY: number) {
  const fan = new THREE.Group()
  const ringMaterial = new THREE.MeshStandardMaterial({
    color: 0x4d6258,
    metalness: 0.55,
    roughness: 0.35,
  })
  const bladeMaterial = new THREE.MeshStandardMaterial({
    color: 0x8fb7a0,
    metalness: 0.35,
    roughness: 0.42,
  })

  const ring = new THREE.Mesh(
    new THREE.TorusGeometry(0.28, 0.035, 8, 24),
    ringMaterial,
  )
  ring.rotation.x = Math.PI / 2
  fan.add(ring)

  const hub = new THREE.Mesh(
    new THREE.CylinderGeometry(0.055, 0.055, 0.12, 12),
    ringMaterial,
  )
  hub.rotation.x = Math.PI / 2
  fan.add(hub)

  for (let index = 0; index < 4; index += 1) {
    const blade = new THREE.Mesh(
      new THREE.BoxGeometry(0.2, 0.025, 0.08),
      bladeMaterial,
    )
    blade.position.set(
      Math.cos((index / 4) * Math.PI * 2) * 0.13,
      Math.sin((index / 4) * Math.PI * 2) * 0.13,
      0,
    )
    blade.rotation.z = (index / 4) * Math.PI * 2
    fan.add(blade)
  }

  fan.position.copy(position)
  fan.rotation.y = rotationY
  fan.rotation.x = Math.PI / 2
  return fan
}

function buildGreenhouse(sceneRoot: THREE.Scene) {
  const isDark = props.theme === 'dark'
  isDarkTheme = isDark
  const greenhouse = new THREE.Group()
  greenhouse.name = 'greenhouse-model'

  const frameMaterial = new THREE.MeshStandardMaterial({
    color: isDark ? 0x8fd8b5 : 0x75877d,
    emissive: isDark ? 0x123c2b : 0x000000,
    emissiveIntensity: isDark ? 1.05 : 0,
    metalness: 0.55,
    roughness: 0.32,
  })
  const darkFrameMaterial = new THREE.MeshStandardMaterial({
    color: isDark ? 0x559d7a : 0x425a4f,
    emissive: isDark ? 0x0b2a1d : 0x000000,
    emissiveIntensity: isDark ? 0.5 : 0,
    metalness: 0.45,
    roughness: 0.38,
  })
  const glassMaterial = new THREE.MeshPhysicalMaterial({
    color: isDark ? 0x79e1b2 : 0xd8eee2,
    transparent: true,
    opacity: isDark ? 0.18 : 0.28,
    roughness: 0.08,
    metalness: 0.03,
    transmission: 0.18,
    thickness: 0.08,
    side: THREE.DoubleSide,
    depthWrite: false,
  })
  const soilMaterial = new THREE.MeshStandardMaterial({
    color: isDark ? 0x315b44 : 0x6d8b65,
    roughness: 0.95,
  })
  const bedMaterial = new THREE.MeshStandardMaterial({
    color: isDark ? 0x28543c : 0x49624a,
    roughness: 0.9,
  })

  const base = new THREE.Mesh(
    new THREE.BoxGeometry(8.8, 0.12, 5.2),
    new THREE.MeshStandardMaterial({
      color: isDark ? 0x152b20 : 0xcbd8cf,
      emissive: isDark ? 0x0a2116 : 0x000000,
      emissiveIntensity: isDark ? 0.55 : 0,
      roughness: 0.92,
    }),
  )
  base.position.y = -0.07
  base.receiveShadow = true
  greenhouse.add(base)

  const soil = new THREE.Mesh(
    new THREE.BoxGeometry(8.25, 0.08, 4.55),
    soilMaterial,
  )
  soil.position.y = 0.02
  soil.receiveShadow = true
  greenhouse.add(soil)

  zoneDefinitions.forEach((zone) => {
    const overlay = createZoneOverlay(zone)
    greenhouse.add(overlay.mesh, overlay.border)
    zoneMeshes.push(overlay)
  })

  const bedPositions = [-2.72, -0.91, 0.91, 2.72]
  const plantPlacements: PlantBatchInstance[] = []
  const plantProfile = getCropPlantProfile(props.crop)
  bedPositions.forEach((x, bedIndex) => {
    const bed = new THREE.Mesh(
      new THREE.BoxGeometry(1.28, 0.22, 3.85),
      bedMaterial,
    )
    bed.position.set(x, 0.16, 0)
    bed.castShadow = true
    bed.receiveShadow = true
    greenhouse.add(bed)

    const pipe = new THREE.Mesh(
      new THREE.CylinderGeometry(0.026, 0.026, 3.6, 8),
      darkFrameMaterial,
    )
    pipe.rotation.x = Math.PI / 2
    pipe.position.set(x, 0.31, 0)
    greenhouse.add(pipe)

    for (let row = 0; row < 5; row += 1) {
      const plantIndex = bedIndex * 5 + row
      plantPlacements.push({
        position: [x, 0.27, -1.42 + row * 0.71],
        targetHeight:
          plantProfile.baseHeight * (0.94 + (plantIndex % 3) * 0.045),
        bloomColor:
          plantProfile.bloomColors[plantIndex % plantProfile.bloomColors.length],
        stemColor: isDark
          ? plantProfile.stemColorDark
          : plantProfile.stemColorLight,
        rotationY: plantIndex * 0.79,
        lean: plantIndex % 2 === 0 ? -0.045 : 0.045,
      })
    }
  })
  plantBatchHandle = createPlantBatch(plantTemplate, plantPlacements)
  greenhouse.add(plantBatchHandle.group)

  const sideZ = [-2.3, 2.3]
  const postX = [-4.2, -2.1, 0, 2.1, 4.2]

  sideZ.forEach((z) => {
    postX.forEach((x) => {
      greenhouse.add(
        createBeam(
          new THREE.Vector3(x, 0.06, z),
          new THREE.Vector3(x, x === 0 ? 3 : 2.2, z),
          0.035,
          frameMaterial,
        ),
      )
    })

    greenhouse.add(
      createBeam(
        new THREE.Vector3(-4.2, 2.2, z),
        new THREE.Vector3(4.2, 2.2, z),
        0.035,
        frameMaterial,
      ),
    )
    greenhouse.add(
      createBeam(
        new THREE.Vector3(-4.2, 2.2, z),
        new THREE.Vector3(0, 3, z),
        0.04,
        frameMaterial,
      ),
    )
    greenhouse.add(
      createBeam(
        new THREE.Vector3(0, 3, z),
        new THREE.Vector3(4.2, 2.2, z),
        0.04,
        frameMaterial,
      ),
    )
  })

  greenhouse.add(
    createBeam(
      new THREE.Vector3(0, 3, -2.3),
      new THREE.Vector3(0, 3, 2.3),
      0.04,
      frameMaterial,
    ),
  )
  greenhouse.add(
    createBeam(
      new THREE.Vector3(-4.2, 2.2, -2.3),
      new THREE.Vector3(-4.2, 2.2, 2.3),
      0.035,
      frameMaterial,
    ),
  )
  greenhouse.add(
    createBeam(
      new THREE.Vector3(4.2, 2.2, -2.3),
      new THREE.Vector3(4.2, 2.2, 2.3),
      0.035,
      frameMaterial,
    ),
  )

  const leftRoof = createPlaneFromPoints(
    [
      new THREE.Vector3(-4.2, 2.2, -2.3),
      new THREE.Vector3(-4.2, 2.2, 2.3),
      new THREE.Vector3(0, 3, 2.3),
      new THREE.Vector3(0, 3, -2.3),
    ],
    glassMaterial,
  )
  const rightRoof = createPlaneFromPoints(
    [
      new THREE.Vector3(4.2, 2.2, -2.3),
      new THREE.Vector3(4.2, 2.2, 2.3),
      new THREE.Vector3(0, 3, 2.3),
      new THREE.Vector3(0, 3, -2.3),
    ],
    glassMaterial,
  )
  greenhouse.add(leftRoof, rightRoof)

  sideZ.forEach((z) => {
    const gable = new THREE.Mesh(createGableGeometry(), glassMaterial)
    gable.position.z = z
    greenhouse.add(gable)
  })

  const door = new THREE.Mesh(
    new THREE.BoxGeometry(1.18, 1.82, 0.05),
    new THREE.MeshStandardMaterial({
      color: 0x638272,
      transparent: true,
      opacity: 0.46,
      metalness: 0.16,
      roughness: 0.35,
    }),
  )
  door.position.set(-3.08, 0.98, 2.34)
  greenhouse.add(door)

  greenhouse.add(
    createBeam(
      new THREE.Vector3(-3.72, 0.1, 2.36),
      new THREE.Vector3(-3.72, 1.9, 2.36),
      0.025,
      darkFrameMaterial,
    ),
  )
  greenhouse.add(
    createBeam(
      new THREE.Vector3(-2.44, 0.1, 2.36),
      new THREE.Vector3(-2.44, 1.9, 2.36),
      0.025,
      darkFrameMaterial,
    ),
  )
  greenhouse.add(
    createBeam(
      new THREE.Vector3(-3.72, 1.9, 2.36),
      new THREE.Vector3(-2.44, 1.9, 2.36),
      0.025,
      darkFrameMaterial,
    ),
  )

  greenhouse.add(
    createFan(new THREE.Vector3(0.9, 2.72, -1.3), Math.PI / 2),
  )
  greenhouse.add(
    createFan(new THREE.Vector3(-1.3, 2.72, -1.3), Math.PI / 2),
  )

  const lightMaterial = new THREE.MeshStandardMaterial({
    color: 0xf0c96d,
    emissive: 0xf0c96d,
    emissiveIntensity: 1.2,
  })
  ;[-2.7, 0, 1.6, 3].forEach((x) => {
    const light = new THREE.Mesh(
      new THREE.BoxGeometry(0.72, 0.055, 0.13),
      lightMaterial,
    )
    light.position.set(x, 2.14, 0.2)
    greenhouse.add(light)
  })

  const mistMaterial = new THREE.MeshStandardMaterial({
    color: 0x75bde0,
    emissive: 0x75bde0,
    emissiveIntensity: 0.65,
  })
  ;[-2.8, -1.4, 0, 1.4, 2.8].forEach((x) => {
    const nozzle = new THREE.Mesh(
      new THREE.SphereGeometry(0.055, 10, 8),
      mistMaterial,
    )
    nozzle.position.set(x, 2.53, 0.72)
    greenhouse.add(nozzle)
  })

  const markerColors = {
    sensor: 0x2f8d68,
    actuator: 0xc48c2d,
  }

  greenhouseHotspots.forEach((hotspot) => {
    const marker = new THREE.Mesh(
      new THREE.SphereGeometry(0.095, 14, 10),
      new THREE.MeshBasicMaterial({
        color: markerColors[hotspot.category],
        transparent: true,
        opacity: 0.92,
        depthTest: false,
      }),
    )
    marker.position.set(...hotspot.position)
    marker.renderOrder = 5
    marker.userData.hotspotId = hotspot.id
    greenhouse.add(marker)
    markerMeshes.push({ id: hotspot.id, mesh: marker })
  })

  if (isDark) {
    const hologram = new THREE.Group()
    hologram.name = 'digital-twin-base'
    const ringMaterial = new THREE.MeshBasicMaterial({
      color: 0x4fe0a0,
      transparent: true,
      opacity: 0.26,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })
    const amberMaterial = new THREE.MeshBasicMaterial({
      color: 0xf1b650,
      transparent: true,
      opacity: 0.2,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })
    ;[
      [6.35, -0.115, ringMaterial, 0.028],
      [6.78, -0.118, amberMaterial, 0.014],
      [7.18, -0.121, ringMaterial, 0.009],
    ].forEach(([radius, y, material, tube]) => {
      const ring = new THREE.Mesh(
        new THREE.TorusGeometry(
          Number(radius),
          Number(tube),
          8,
          96,
        ),
        material as THREE.Material,
      )
      ring.rotation.x = Math.PI / 2
      ring.position.y = Number(y)
      hologram.add(ring)
    })

    const scan = new THREE.Mesh(
      new THREE.RingGeometry(4.65, 4.72, 96, 1, 0, Math.PI * 1.35),
      ringMaterial,
    )
    scan.rotation.x = -Math.PI / 2
    scan.position.y = -0.108
    hologram.add(scan)
    hologramGroup = hologram
    greenhouse.add(hologram)
  }

  greenhouseModel = greenhouse
  sceneRoot.add(greenhouse)
}

function buildScene() {
  if (!canvasHost.value) return

  const isDark = props.theme === 'dark'
  isDarkTheme = isDark
  scene = new THREE.Scene()
  scene.background = new THREE.Color(isDark ? 0x06130e : 0xf7fbf8)
  scene.fog = new THREE.Fog(isDark ? 0x06130e : 0xf7fbf8, 14, 28)

  camera = new THREE.PerspectiveCamera(props.immersive ? 45 : 38, 1, 0.1, 100)
  camera.position.set(
    props.immersive ? 7.7 : 9.6,
    props.immersive ? 4.5 : 6.8,
    props.immersive ? 8.4 : 10.4,
  )

  renderer = new THREE.WebGLRenderer({
    antialias: sceneQuality.antialias,
    alpha: false,
    powerPreference: 'high-performance',
  })
  renderer.setPixelRatio(sceneQuality.maxPixelRatio)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = isDark ? 1.15 : 1.05
  renderer.shadowMap.enabled = sceneQuality.enableShadows
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.domElement.setAttribute('role', 'img')
  renderer.domElement.setAttribute(
    'aria-label',
    '可交互温室三维场景。拖动旋转，滚轮或双指缩放，点击设备热点查看详情。',
  )
  renderer.domElement.setAttribute(
    'aria-describedby',
    'greenhouse-scene-hint',
  )
  canvasHost.value.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.enablePan = true
  controls.minDistance = props.immersive ? 5.8 : 6.2
  controls.maxDistance = props.immersive ? 19 : 17
  controls.minPolarAngle = 0.45
  controls.maxPolarAngle = Math.PI / 2.08
  controls.target.set(0, props.immersive ? 1.05 : 1.25, 0)
  controls.autoRotate = props.autoRotate && !sceneQuality.reduceMotion
  controls.autoRotateSpeed = 0.42

  hemisphereLight = new THREE.HemisphereLight(
    0xffffff,
    isDark ? 0x18372a : 0xc8d8ce,
    isDark ? 3.2 : 2.7,
  )
  scene.add(hemisphereLight)

  sunLight = new THREE.DirectionalLight(0xfff4d6, isDark ? 4.3 : 3.2)
  sunLight.castShadow = sceneQuality.enableShadows
  sunLight.shadow.mapSize.set(
    sceneQuality.shadowMapSize,
    sceneQuality.shadowMapSize,
  )
  sunLight.shadow.camera.left = -8
  sunLight.shadow.camera.right = 8
  sunLight.shadow.camera.top = 8
  sunLight.shadow.camera.bottom = -8
  scene.add(sunLight)

  moonLight = new THREE.DirectionalLight(0xa9c8ff, 0)
  moonLight.castShadow = false
  scene.add(moonLight)

  fillLight = new THREE.DirectionalLight(0xbfe0cf, isDark ? 1.8 : 1.2)
  fillLight.position.set(-7, 5, -6)
  scene.add(fillLight)

  sunModel = createSunModel()
  moonModel = createMoonModel()
  scene.add(sunModel, moonModel)
  if (isDark) {
    starField = createStarField()
    scene.add(starField)
  }
  updateCelestialState(Date.now())
  lastCelestialUpdate = performance.now()

  const ground = new THREE.Mesh(
    new THREE.CircleGeometry(10, 64),
    new THREE.MeshStandardMaterial({
      color: isDark ? 0x0d2118 : 0xdce9e1,
      roughness: 0.98,
    }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -0.145
  ground.receiveShadow = true
  scene.add(ground)

  const grid = new THREE.GridHelper(
    15,
    24,
    isDark ? 0x315c46 : 0xb6c8bd,
    isDark ? 0x173225 : 0xd7e2db,
  )
  grid.position.y = -0.13
  const gridMaterials = Array.isArray(grid.material)
    ? grid.material
    : [grid.material]
  gridMaterials.forEach((material) => {
    material.transparent = true
    material.opacity = 0.38
  })
  scene.add(grid)

  buildGreenhouse(scene)

  renderer.domElement.addEventListener('pointerdown', handlePointerDown)
  renderer.domElement.addEventListener('pointerup', handlePointerUp)
  renderer.domElement.addEventListener('pointermove', handlePointerMove)
  renderer.domElement.addEventListener('pointerleave', handlePointerLeave)
  document.addEventListener('visibilitychange', handleVisibilityChange)

  resizeObserver = new ResizeObserver(resizeRenderer)
  resizeObserver.observe(canvasHost.value)
  resizeRenderer()
  renderer.setAnimationLoop(animate)
  sceneError.value = ''
  window.requestAnimationFrame(() => {
    sceneReady.value = true
  })
}

function selectHotspot(hotspotId: string) {
  const hotspot = greenhouseHotspots.find((item) => item.id === hotspotId)
  if (hotspot) emit('select', hotspot)
}

function handlePointerDown(event: PointerEvent) {
  pointerDownPosition = { x: event.clientX, y: event.clientY }
}

function handlePointerUp(event: PointerEvent) {
  if (
    !renderer ||
    !camera ||
    !pointerDownPosition ||
    Math.hypot(
      event.clientX - pointerDownPosition.x,
      event.clientY - pointerDownPosition.y,
    ) > 5
  ) {
    return
  }

  const targetId = raycastMarker(event)
  if (targetId) selectHotspot(targetId)
}

function handlePointerMove(event: PointerEvent) {
  if (!renderer) return
  const bounds = renderer.domElement.getBoundingClientRect()
  pointerTarget.x = ((event.clientX - bounds.left) / bounds.width) * 2 - 1
  pointerTarget.y = ((event.clientY - bounds.top) / bounds.height) * 2 - 1
  renderer.domElement.style.cursor = raycastMarker(event) ? 'pointer' : 'grab'
}

function handlePointerLeave() {
  pointerTarget.set(0, 0)
  if (renderer) renderer.domElement.style.cursor = 'grab'
}

function raycastMarker(event: PointerEvent) {
  if (!renderer || !camera || markerMeshes.length === 0) return null

  const bounds = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - bounds.left) / bounds.width) * 2 - 1
  pointer.y = -((event.clientY - bounds.top) / bounds.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)

  const intersections = raycaster.intersectObjects(
    markerMeshes.map((entry) => entry.mesh),
    false,
  )
  return intersections[0]?.object.userData.hotspotId as string | undefined
}

function updateMarkerPositions() {
  if (!renderer || !camera) return

  const activeRenderer = renderer
  const activeCamera = camera
  const width = activeRenderer.domElement.clientWidth
  const height = activeRenderer.domElement.clientHeight
  const nextPositions: Record<string, MarkerPosition> = {}

  greenhouseHotspots.forEach((hotspot) => {
    const isVisible =
      props.mode === 'overview' ||
      (props.mode === 'environment' && hotspot.category === 'sensor') ||
      (props.mode === 'control' && hotspot.category === 'actuator')

    if (!isVisible) {
      nextPositions[hotspot.id] = { x: 0, y: 0, visible: false }
      return
    }

    const projected = new THREE.Vector3(...hotspot.position).project(activeCamera)
    const x = (projected.x * 0.5 + 0.5) * width
    const y = (-projected.y * 0.5 + 0.5) * height
    const visible =
      projected.z > -1 &&
      projected.z < 1 &&
      x > 18 &&
      x < width - 18 &&
      y > 42 &&
      y < height - 34

    nextPositions[hotspot.id] = { x, y, visible }
  })

  markerPositions.value = nextPositions

  const nextZones: Record<string, MarkerPosition> = {}
  zoneDefinitions.forEach((zone) => {
    const projected = new THREE.Vector3(
      zone.x,
      0.38,
      zone.z,
    ).project(activeCamera)
    const x = (projected.x * 0.5 + 0.5) * width
    const y = (-projected.y * 0.5 + 0.5) * height
    nextZones[zone.id] = {
      x,
      y,
      visible:
        projected.z > -1 &&
        projected.z < 1 &&
        x > 24 &&
        x < width - 24 &&
        y > 36 &&
        y < height - 24,
    }
  })
  zonePositions.value = nextZones

  const nextCompass: Record<string, MarkerPosition> = {}
  compassPoints.forEach((point) => {
    const projected = new THREE.Vector3(...point.position).project(
      activeCamera,
    )
    const x = (projected.x * 0.5 + 0.5) * width
    const y = (-projected.y * 0.5 + 0.5) * height
    nextCompass[point.id] = {
      x,
      y,
      visible:
        projected.z > -1 &&
        projected.z < 1 &&
        x > 18 &&
        x < width - 18 &&
        y > 24 &&
        y < height - 18,
    }
  })
  compassPositions.value = nextCompass
}

function animate() {
  if (!renderer || !scene || !camera || !controls) return

  const elapsed = clock.getElapsedTime()
  const now = performance.now()

  if (starField) {
    starField.rotation.y = elapsed * 0.006
  }

  if (now - lastCelestialUpdate >= 30_000) {
    lastCelestialUpdate = now
    updateCelestialState(Date.now())
  }

  if (cameraTween) {
    const progress = Math.min(
      (now - cameraTween.startedAt) / cameraTween.duration,
      1,
    )
    const eased = 1 - Math.pow(1 - progress, 3)
    camera.position.lerpVectors(
      cameraTween.startPosition,
      cameraTween.endPosition,
      eased,
    )
    controls.target.lerpVectors(
      cameraTween.startTarget,
      cameraTween.endTarget,
      eased,
    )
    if (progress >= 1) cameraTween = null
  }

  const linkedHotspot = metricHotspotMap[props.highlightMetric] ?? ''
  markerMeshes.forEach((entry, index) => {
    const selected = entry.id === props.selectedId
    const linked = entry.id === linkedHotspot
    const pulse = selected
      ? 1.6
      : linked
        ? 1.45 + Math.sin(elapsed * 3.4) * 0.08
        : 1 + Math.sin(elapsed * 2.2 + index) * 0.08
    entry.mesh.scale.setScalar(pulse)
    const material = entry.mesh.material as THREE.MeshBasicMaterial
    const targetOpacity = linkedHotspot
      ? linked
        ? 1
        : 0.22
      : 0.92
    material.opacity += (targetOpacity - material.opacity) * 0.14
  })

  const activeZone = (props.activeZone ?? '').replace(/\s/g, '')
  zoneMeshes.forEach((entry, index) => {
    const active = entry.definition.aliases.some((alias) =>
      activeZone.includes(alias),
    )
    const targetOpacity = active
      ? 0.18 + Math.sin(elapsed * 2.4 + index) * 0.035
      : 0.045
    entry.mesh.material.opacity +=
      (targetOpacity - entry.mesh.material.opacity) * 0.12
    const targetBorderOpacity = active ? 0.9 : 0.22
    entry.border.material.opacity +=
      (targetBorderOpacity - entry.border.material.opacity) * 0.14
  })

  if (hologramGroup) {
    hologramGroup.rotation.y = elapsed * 0.08
    hologramGroup.children.forEach((child, index) => {
      const material = (child as THREE.Mesh).material
      if (material instanceof THREE.Material) {
        const baseOpacity = index === 1 ? 0.2 : 0.26
        material.opacity =
          baseOpacity
          + Math.sin(elapsed * 1.8 + index * 0.8) * 0.055
      }
    })
  }

  plantBatchHandle?.update(elapsed)

  if (greenhouseModel) {
    const rotationStrength = props.immersive ? 0.065 : 0.035
    greenhouseModel.rotation.y +=
      (pointerTarget.x * rotationStrength - greenhouseModel.rotation.y) * 0.045
    greenhouseModel.rotation.x +=
      (-pointerTarget.y * rotationStrength * 0.45 - greenhouseModel.rotation.x) *
      0.045
  }

  controls.update()
  renderer.render(scene, camera)
  const nowMs = performance.now()
  if (nowMs - lastOverlayUpdate >= 1000 / sceneQuality.overlayFps) {
    lastOverlayUpdate = nowMs
    updateMarkerPositions()
  }
}

function handleVisibilityChange() {
  if (!renderer) return
  if (document.hidden) {
    renderer.setAnimationLoop(null)
    return
  }
  renderer.setAnimationLoop(animate)
}

function resizeRenderer() {
  if (!renderer || !camera || !canvasHost.value) return
  const { clientWidth, clientHeight } = canvasHost.value
  if (clientWidth === 0 || clientHeight === 0) return

  const aspect = clientWidth / clientHeight
  camera.aspect = aspect
  camera.fov = aspect < 0.75
    ? 62
    : aspect < 1
      ? 55
      : props.immersive
        ? 45
        : 38
  camera.updateProjectionMatrix()
  renderer.setSize(clientWidth, clientHeight, false)
  lastOverlayUpdate = 0
  updateMarkerPositions()
}

function resetView() {
  if (!camera || !controls) return
  startCameraTween(
    new THREE.Vector3(
      props.immersive ? 7.7 : 9.6,
      props.immersive ? 4.5 : 6.8,
      props.immersive ? 8.4 : 10.4,
    ),
    new THREE.Vector3(0, props.immersive ? 1.05 : 1.25, 0),
    620,
  )
}

function focusHotspot(hotspotId: string) {
  if (!camera || !controls) return
  const hotspot = greenhouseHotspots.find((item) => item.id === hotspotId)
  if (!hotspot) return

  const nextTarget = new THREE.Vector3(...hotspot.position)
  const direction = camera.position
    .clone()
    .sub(controls.target)
    .normalize()
  const distance = props.immersive ? 7.2 : 6.6
  const nextPosition = nextTarget
    .clone()
    .add(direction.multiplyScalar(distance))
    .add(new THREE.Vector3(0, 0.35, 0))
  startCameraTween(nextPosition, nextTarget, 700)
}

function startCameraTween(
  endPosition: THREE.Vector3,
  endTarget: THREE.Vector3,
  duration: number,
) {
  if (!camera || !controls) return
  cameraTween = {
    startPosition: camera.position.clone(),
    endPosition,
    startTarget: controls.target.clone(),
    endTarget,
    startedAt: performance.now(),
    duration,
  }
}

function disposeSceneObjects(root: THREE.Object3D) {
  root.traverse((object) => {
    if (
      !(object instanceof THREE.Mesh) &&
      !(object instanceof THREE.Line) &&
      !(object instanceof THREE.Points)
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
    if (controls) {
      controls.autoRotate = value && !sceneQuality.reduceMotion
    }
  },
)

onMounted(async () => {
  try {
    plantTemplate = await loadPlantModel()
    buildScene()
  } catch {
    sceneError.value = '三维场景加载失败，请检查网络或稍后重试。'
    sceneReady.value = true
  }
})

onBeforeUnmount(() => {
  renderer?.setAnimationLoop(null)
  resizeObserver?.disconnect()
  document.removeEventListener('visibilitychange', handleVisibilityChange)

  if (renderer) {
    renderer.domElement.removeEventListener('pointerdown', handlePointerDown)
    renderer.domElement.removeEventListener('pointerup', handlePointerUp)
    renderer.domElement.removeEventListener('pointermove', handlePointerMove)
    renderer.domElement.removeEventListener('pointerleave', handlePointerLeave)
  }

  if (scene) disposeSceneObjects(scene)
  controls?.dispose()
  renderer?.dispose()
  markerMeshes = []
  zoneMeshes = []
  plantBatchHandle = null
  sunModel = null
  moonModel = null
  starField = null
  sunLight = null
  moonLight = null
  hemisphereLight = null
  fillLight = null
  hologramGroup = null
  canvasHost.value?.replaceChildren()
})

defineExpose({ resetView, focusHotspot })
</script>

<template>
  <div
    class="greenhouse-scene"
    :class="[
      `greenhouse-scene--${mode}`,
      `greenhouse-scene--${theme}`,
      { 'greenhouse-scene--immersive': immersive },
    ]"
  >
    <div ref="canvasHost" class="greenhouse-scene__canvas"></div>

    <div
      v-if="!sceneReady"
      class="greenhouse-scene__loading"
      role="status"
      aria-live="polite"
    >
      <span></span>
      <strong>正在构建数字孪生场景</strong>
      <small>加载温室模型、设备热点与实时环境数据</small>
    </div>

    <div
      v-else-if="sceneError"
      class="greenhouse-scene__error"
      role="alert"
    >
      {{ sceneError }}
    </div>

    <div class="greenhouse-scene__hotspots">
      <button
        v-for="hotspot in visibleHotspots"
        v-show="markerPositions[hotspot.id]?.visible"
        :key="hotspot.id"
        class="scene-hotspot"
        :class="[
          `scene-hotspot--${hotspot.category}`,
          { 'scene-hotspot--selected': selectedId === hotspot.id },
        ]"
        :style="{
          '--hotspot-x': `${markerPositions[hotspot.id]?.x ?? 0}px`,
          '--hotspot-y': `${markerPositions[hotspot.id]?.y ?? 0}px`,
        }"
        type="button"
        @click="emit('select', hotspot)"
      >
        <span class="scene-hotspot__icon">
          <component :is="hotspot.icon" :size="15" />
        </span>
        <span class="scene-hotspot__text">
          <strong>{{ hotspot.label }}</strong>
          <small>{{ hotspot.value }} {{ hotspot.unit }}</small>
        </span>
      </button>
    </div>

    <div class="greenhouse-scene__zone-labels">
      <span
        v-for="zone in zoneDefinitions"
        v-show="zonePositions[zone.id]?.visible"
        :key="zone.id"
        class="scene-zone-label"
        :class="{
          'is-active': (activeZone ?? '').includes(zone.label),
        }"
        :style="{
          '--zone-x': `${zonePositions[zone.id]?.x ?? 0}px`,
          '--zone-y': `${zonePositions[zone.id]?.y ?? 0}px`,
          '--zone-color': `#${zone.color.toString(16).padStart(6, '0')}`,
        }"
      >
        {{ zone.label }}
      </span>
    </div>

    <div class="greenhouse-scene__compass">
      <span
        v-for="point in compassPoints"
        v-show="compassPositions[point.id]?.visible"
        :key="point.id"
        class="scene-compass-point"
        :style="{
          '--compass-x': `${compassPositions[point.id]?.x ?? 0}px`,
          '--compass-y': `${compassPositions[point.id]?.y ?? 0}px`,
        }"
      >
        {{ point.label }}
      </span>
    </div>

    <div class="greenhouse-scene__celestial">
      <span class="greenhouse-scene__celestial-title">
        <Compass :size="13" />
        实时天象 · {{ CELESTIAL_LOCATION.label }}
      </span>
      <button
        v-if="celestialState.sunVisible"
        type="button"
        :aria-label="`定位到太阳，方位${sunDirectionText}，高度${celestialState.sunElevation.toFixed(0)}度`"
        @click="focusCelestial('sun')"
      >
        <Sun :size="14" />
        太阳 · {{ sunDirectionText }} · {{ celestialState.sunElevation.toFixed(0) }}°
      </button>
      <button
        v-if="celestialState.moonVisible"
        type="button"
        :aria-label="`定位到月亮，方位${moonDirectionText}，高度${celestialState.moonElevation.toFixed(0)}度`"
        @click="focusCelestial('moon')"
      >
        <Moon :size="14" />
        月亮 · {{ moonDirectionText }} · {{ celestialState.moonElevation.toFixed(0) }}°
      </button>
    </div>

    <div class="greenhouse-scene__legend">
      <span><i class="is-sensor"></i>传感器</span>
      <span><i class="is-actuator"></i>执行设备</span>
    </div>

    <div id="greenhouse-scene-hint" class="greenhouse-scene__hint">
      <MousePointer2 :size="14" />
      <span class="greenhouse-scene__hint-desktop">
        拖动旋转 · 滚轮缩放 · 点击热点
      </span>
      <span class="greenhouse-scene__hint-mobile">
        拖动 · 双指缩放 · 点击热点
      </span>
    </div>

    <button
      class="greenhouse-scene__reset"
      type="button"
      aria-label="重置三维视角"
      @click="resetView"
    >
      <LocateFixed :size="16" />
    </button>

    <div v-if="autoRotate" class="greenhouse-scene__rotating">
      <Rotate3d :size="14" />
      <span>自动环视</span>
    </div>
  </div>
</template>

<style scoped>
.greenhouse-scene {
  position: relative;
  min-height: 570px;
  overflow: hidden;
  background: #f7fbf8;
  border-radius: 8px;
}

.greenhouse-scene--dark {
  background: #06130e;
}

.greenhouse-scene--immersive {
  min-height: 100%;
  border-radius: 0;
}

.greenhouse-scene--immersive .greenhouse-scene__legend,
.greenhouse-scene--immersive .greenhouse-scene__reset,
.greenhouse-scene--immersive .greenhouse-scene__rotating {
  display: none;
}

.greenhouse-scene--immersive .greenhouse-scene__celestial {
  top: 72px;
  left: 18px;
  gap: 6px;
}

.greenhouse-scene__canvas,
.greenhouse-scene__canvas :deep(canvas) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.greenhouse-scene__canvas :deep(canvas) {
  touch-action: none;
  cursor: grab;
}

.greenhouse-scene__loading,
.greenhouse-scene__error {
  position: absolute;
  inset: 0;
  z-index: 8;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 8px;
  padding: 28px;
  text-align: center;
  background:
    radial-gradient(
      circle at 50% 46%,
      rgb(19 52 37 / 72%),
      rgb(5 18 12 / 96%) 68%
    );
}

.greenhouse-scene__loading > span {
  position: relative;
  width: 42px;
  height: 42px;
  overflow: hidden;
  border: 1px solid rgb(143 216 181 / 26%);
  border-radius: 50%;
}

.greenhouse-scene__loading > span::after {
  position: absolute;
  inset: -45%;
  content: "";
  background: conic-gradient(
    transparent 0 58%,
    rgb(143 216 181 / 82%) 78%,
    transparent 82%
  );
  animation: greenhouse-scene-loading 1.25s linear infinite;
}

.greenhouse-scene__loading strong {
  color: #e9f7ef;
  font-size: 13px;
}

.greenhouse-scene__loading small {
  color: #8fb2a1;
  font-size: 10px;
}

.greenhouse-scene__error {
  color: #ffc7c3;
  font-size: 12px;
}

@keyframes greenhouse-scene-loading {
  to {
    transform: rotate(1turn);
  }
}

.greenhouse-scene__hotspots {
  position: absolute;
  inset: 0;
  z-index: 4;
  pointer-events: none;
}

.greenhouse-scene__zone-labels {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
}

.greenhouse-scene__compass {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
}

.scene-compass-point {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: #26382e;
  background: rgb(255 255 255 / 88%);
  border: 1px solid #cddbd2;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  box-shadow: 0 4px 12px rgb(29 55 41 / 12%);
  transform: translate3d(
    var(--compass-x, 0),
    var(--compass-y, 0),
    0
  ) translate(-50%, -50%);
  backdrop-filter: blur(7px);
}

.greenhouse-scene__celestial {
  position: absolute;
  top: 50px;
  left: 12px;
  z-index: 5;
  display: grid;
  gap: 5px;
  justify-items: start;
  pointer-events: none;
}

.greenhouse-scene__celestial-title,
.greenhouse-scene__celestial button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 27px;
  padding: 0 8px;
  color: #53665b;
  background: rgb(255 255 255 / 88%);
  border: 1px solid #d7e2da;
  border-radius: 6px;
  font-size: 9px;
  white-space: nowrap;
  backdrop-filter: blur(8px);
}

.greenhouse-scene__celestial-title {
  color: #267a58;
  font-weight: 600;
}

.greenhouse-scene__celestial button {
  pointer-events: auto;
  cursor: pointer;
}

.greenhouse-scene__celestial button:hover {
  color: #1d6f50;
  background: #eef7f1;
  border-color: #9fc8b1;
}

.greenhouse-scene--dark .greenhouse-scene__celestial-title,
.greenhouse-scene--dark .greenhouse-scene__celestial button {
  color: #dceee5;
  background: rgb(8 22 16 / 88%);
  border-color: rgb(143 216 181 / 24%);
  box-shadow: 0 8px 22px rgb(0 0 0 / 18%);
  backdrop-filter: blur(12px);
}

.greenhouse-scene--dark .greenhouse-scene__celestial-title {
  color: #9ee6bd;
}

.greenhouse-scene--dark .scene-compass-point {
  color: #eef8f2;
  background: rgb(8 22 16 / 84%);
  border-color: rgb(143 216 181 / 24%);
  box-shadow: 0 6px 18px rgb(0 0 0 / 22%);
}

.scene-zone-label {
  position: absolute;
  top: 0;
  left: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  min-height: 23px;
  padding: 0 8px;
  color: #ffffff;
  background: color-mix(in srgb, var(--zone-color) 76%, #1d3026);
  border: 1px solid color-mix(in srgb, var(--zone-color) 58%, #ffffff);
  border-radius: 6px;
  font-size: 10px;
  font-weight: 600;
  transform: translate3d(var(--zone-x, 0), var(--zone-y, 0), 0)
    translate(-50%, -50%);
  transition: opacity 160ms ease, transform 160ms ease;
}

.scene-zone-label.is-active {
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--zone-color) 22%, transparent);
  transform: translate3d(var(--zone-x, 0), var(--zone-y, 0), 0)
    translate(-50%, -50%) scale(1.08);
}

.scene-hotspot {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  max-width: 172px;
  padding: 5px 8px 5px 5px;
  color: #1e3027;
  text-align: left;
  pointer-events: auto;
  background: color-mix(in srgb, #ffffff 94%, transparent);
  border: 1px solid #bfd8cb;
  border-radius: 9px;
  box-shadow: 0 8px 24px rgb(6 24 15 / 18%);
  transform: translate3d(var(--hotspot-x, 0), var(--hotspot-y, 0), 0)
    translate(-50%, calc(-100% - 8px));
  backdrop-filter: blur(8px);
}

.scene-hotspot::after {
  position: absolute;
  top: 100%;
  left: 50%;
  width: 1px;
  height: 9px;
  content: "";
  background: #9fb8aa;
}

.scene-hotspot:hover,
.scene-hotspot--selected {
  border-color: #2f9b72;
  box-shadow:
    0 0 0 3px rgb(47 155 114 / 12%),
    0 10px 28px rgb(6 24 15 / 24%);
}

.scene-hotspot:focus-visible,
.greenhouse-scene__celestial button:focus-visible,
.greenhouse-scene__reset:focus-visible {
  outline: 2px solid #f47b20;
  outline-offset: 2px;
}

.scene-hotspot--selected {
  transform: translate3d(var(--hotspot-x, 0), var(--hotspot-y, 0), 0)
    translate(-50%, calc(-100% - 8px)) scale(1.03);
}

.scene-hotspot__icon {
  display: grid;
  flex: 0 0 auto;
  width: 27px;
  height: 27px;
  place-items: center;
  color: #ffffff;
  border-radius: 6px;
}

.scene-hotspot--sensor .scene-hotspot__icon {
  background: linear-gradient(135deg, #3cb083, #1f7d5d);
}

.scene-hotspot--actuator .scene-hotspot__icon {
  background: linear-gradient(135deg, #e4a83d, #b66f16);
}

.scene-hotspot__text {
  display: none;
  min-width: 0;
}

.scene-hotspot:hover .scene-hotspot__text,
.scene-hotspot--selected .scene-hotspot__text {
  display: block;
}

.scene-hotspot__text strong,
.scene-hotspot__text small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scene-hotspot__text strong {
  font-size: 10.5px;
  font-weight: 600;
}

.scene-hotspot__text small {
  margin-top: 2px;
  color: #64786d;
  font-size: 9.5px;
}

.greenhouse-scene__legend,
.greenhouse-scene__hint,
.greenhouse-scene__rotating {
  position: absolute;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 9px;
  color: #53665b;
  background: rgb(255 255 255 / 88%);
  border: 1px solid #d7e2da;
  border-radius: 6px;
  font-size: 10px;
  backdrop-filter: blur(8px);
}

.greenhouse-scene--dark .greenhouse-scene__legend,
.greenhouse-scene--dark .greenhouse-scene__hint,
.greenhouse-scene--dark .greenhouse-scene__rotating,
.greenhouse-scene--dark .greenhouse-scene__reset {
  color: #d9e8df;
  background: rgb(8 22 16 / 86%);
  border-color: rgb(143 216 181 / 24%);
  box-shadow: 0 8px 22px rgb(0 0 0 / 18%);
}

.greenhouse-scene__legend {
  top: 12px;
  left: 12px;
}

.greenhouse-scene__legend span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.greenhouse-scene__legend i {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.greenhouse-scene__legend i.is-sensor {
  background: #2f8d68;
}

.greenhouse-scene__legend i.is-actuator {
  background: #bd8429;
}

.greenhouse-scene__hint {
  right: 12px;
  bottom: 12px;
}

.greenhouse-scene__hint-mobile {
  display: none;
}

.greenhouse-scene__reset {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 5;
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  color: #385246;
  background: rgb(255 255 255 / 90%);
  border: 1px solid #d7e2da;
  border-radius: 7px;
  backdrop-filter: blur(8px);
}

.greenhouse-scene__rotating {
  top: 52px;
  right: 12px;
}

@media (max-width: 820px) {
  .greenhouse-scene {
    min-height: 470px;
  }

  .greenhouse-scene__celestial button {
    min-width: 44px;
    min-height: 44px;
  }

  .scene-hotspot {
    max-width: 145px;
  }
}

@media (max-width: 560px) {
  .greenhouse-scene {
    min-height: 420px;
  }

  .greenhouse-scene__celestial button {
    min-width: 44px;
    min-height: 44px;
    justify-content: center;
    padding: 0;
    overflow: hidden;
    border-radius: 50%;
    font-size: 0;
  }

  .greenhouse-scene--immersive .greenhouse-scene__celestial {
    top: 88px;
    left: 12px;
    display: flex;
    gap: 6px;
  }

  .greenhouse-scene--immersive .greenhouse-scene__celestial-title {
    display: none;
  }

  .greenhouse-scene--immersive .greenhouse-scene__celestial button {
    width: 44px;
    min-height: 44px;
    justify-content: center;
    padding: 0;
    overflow: hidden;
    border-radius: 50%;
    font-size: 0 !important;
  }

  .greenhouse-scene--immersive .greenhouse-scene__hint {
    display: none;
  }

  .greenhouse-scene__hint-desktop {
    display: none;
  }

  .greenhouse-scene__hint-mobile {
    display: inline;
  }

  .scene-hotspot {
    min-width: 44px;
    min-height: 44px;
    justify-content: center;
    padding: 5px;
  }

  .scene-hotspot__icon {
    width: 34px;
    height: 34px;
  }

  .scene-hotspot__text {
    display: none;
  }

  .scene-hotspot {
    transform: translate3d(var(--hotspot-x, 0), var(--hotspot-y, 0), 0)
      translate(-50%, calc(-100% - 7px));
  }

}

@media (prefers-reduced-motion: reduce) {
  .greenhouse-scene__loading > span::after {
    animation-duration: 3s;
  }

  .scene-hotspot,
  .scene-zone-label,
  .greenhouse-scene__celestial button,
  .greenhouse-scene__reset {
    transition-duration: 0.01ms !important;
  }
}
</style>

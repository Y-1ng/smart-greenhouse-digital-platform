<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'

const props = withDefaults(
  defineProps<{
    accent?: string
    autoRotate?: boolean
    modelRotation?: number
    highlight?: string
  }>(),
  {
    accent: '#e3a347',
    autoRotate: true,
    modelRotation: 0,
    highlight: '',
  },
)

const canvasHost = ref<HTMLDivElement | null>(null)
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let renderer: THREE.WebGLRenderer | null = null
let controls: OrbitControls | null = null
let greenhouse: THREE.Group | null = null
let energyRing: THREE.Group | null = null
let animationFrame = 0
let resizeObserver: ResizeObserver | null = null
let pointerTarget = new THREE.Vector2()
const clock = new THREE.Clock()
const plantMaterials: THREE.MeshStandardMaterial[] = []

const accentColor = () => new THREE.Color(props.accent)

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
  return mesh
}

function createGlassPanel(points: THREE.Vector3[], material: THREE.Material) {
  const geometry = new THREE.BufferGeometry().setFromPoints(points)
  geometry.setIndex([0, 1, 2, 0, 2, 3])
  geometry.computeVertexNormals()
  return new THREE.Mesh(geometry, material)
}

function createPlant(x: number, z: number, scale: number) {
  const plant = new THREE.Group()
  const trunkMaterial = new THREE.MeshStandardMaterial({
    color: 0x7a7353,
    roughness: 0.9,
  })
  const leafMaterial = new THREE.MeshStandardMaterial({
    color: 0x4f8a3f,
    emissive: 0x173719,
    emissiveIntensity: 0.22,
    roughness: 0.78,
  })
  plantMaterials.push(leafMaterial)

  const trunk = new THREE.Mesh(
    new THREE.CylinderGeometry(0.025 * scale, 0.04 * scale, 0.45 * scale, 6),
    trunkMaterial,
  )
  trunk.position.y = 0.22 * scale
  plant.add(trunk)

  ;[0.45, 0.68, 0.9].forEach((height, index) => {
    const crown = new THREE.Mesh(
      new THREE.IcosahedronGeometry((0.2 - index * 0.028) * scale, 0),
      leafMaterial,
    )
    crown.position.set(
      Math.sin(index * 1.7) * 0.04 * scale,
      height * scale,
      Math.cos(index * 1.3) * 0.04 * scale,
    )
    crown.scale.set(1, 0.82, 1)
    plant.add(crown)
  })

  plant.position.set(x, 0.03, z)
  plant.rotation.y = (x + z) * 0.73
  return plant
}

function createFan(z: number, x: number) {
  const fan = new THREE.Group()
  const material = new THREE.MeshStandardMaterial({
    color: 0x9e8f72,
    metalness: 0.5,
    roughness: 0.4,
  })
  const accentMaterial = new THREE.MeshBasicMaterial({
    color: accentColor(),
    transparent: true,
    opacity: 0.68,
  })
  const ring = new THREE.Mesh(
    new THREE.TorusGeometry(0.25, 0.022, 8, 28),
    accentMaterial,
  )
  const hub = new THREE.Mesh(
    new THREE.CylinderGeometry(0.045, 0.045, 0.08, 10),
    material,
  )
  hub.rotation.x = Math.PI / 2
  fan.add(ring, hub)
  for (let index = 0; index < 4; index += 1) {
    const blade = new THREE.Mesh(
      new THREE.BoxGeometry(0.2, 0.018, 0.06),
      material,
    )
    blade.position.set(
      Math.cos(index * Math.PI / 2) * 0.11,
      Math.sin(index * Math.PI / 2) * 0.11,
      0,
    )
    blade.rotation.z = index * Math.PI / 2
    fan.add(blade)
  }
  fan.position.set(x, 1.62, z)
  return fan
}

function buildScene() {
  if (!canvasHost.value) return
  const host = canvasHost.value
  const accent = accentColor()

  scene = new THREE.Scene()
  scene.fog = new THREE.Fog(0x080504, 17, 34)

  camera = new THREE.PerspectiveCamera(40, 1, 0.1, 80)
  camera.position.set(11.8, 6.1, 12.6)

  renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: true,
    powerPreference: 'high-performance',
  })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.8))
  renderer.setClearColor(0x000000, 0)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.16
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.domElement.setAttribute('aria-label', '多跨智慧温室三维模型')
  host.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.055
  controls.enablePan = false
  controls.minDistance = 10
  controls.maxDistance = 23
  controls.minPolarAngle = 0.75
  controls.maxPolarAngle = Math.PI / 2.12
  controls.target.set(0, 1.15, 0)
  controls.autoRotate = props.autoRotate
  controls.autoRotateSpeed = 0.32

  const ambient = new THREE.HemisphereLight(0xffe1b0, 0x110b07, 1.7)
  scene.add(ambient)

  const keyLight = new THREE.DirectionalLight(0xffc978, 4.2)
  keyLight.position.set(8, 10, 7)
  keyLight.castShadow = true
  keyLight.shadow.mapSize.set(2048, 2048)
  scene.add(keyLight)

  const fillLight = new THREE.DirectionalLight(0xff8f35, 1.5)
  fillLight.position.set(-8, 5, -5)
  scene.add(fillLight)

  greenhouse = new THREE.Group()
  greenhouse.name = 'hero-greenhouse'

  const frameMaterial = new THREE.MeshStandardMaterial({
    color: 0xcabba2,
    metalness: 0.72,
    roughness: 0.28,
  })
  const accentFrameMaterial = new THREE.MeshStandardMaterial({
    color: accent,
    emissive: accent,
    emissiveIntensity: 0.18,
    metalness: 0.58,
    roughness: 0.34,
  })
  const glassMaterial = new THREE.MeshPhysicalMaterial({
    color: 0xe4dcc7,
    transparent: true,
    opacity: 0.17,
    roughness: 0.12,
    metalness: 0.04,
    side: THREE.DoubleSide,
    depthWrite: false,
  })

  const bayCount = 5
  const bayWidth = 2.35
  const width = bayCount * bayWidth
  const depth = 6.6
  const eave = 2.05
  const ridge = 3.15
  const xEdges = Array.from(
    { length: bayCount + 1 },
    (_, index) => -width / 2 + index * bayWidth,
  )
  const zEdges = [-depth / 2, 0, depth / 2]

  const base = new THREE.Mesh(
    new THREE.BoxGeometry(width + 0.65, 0.16, depth + 0.55),
    new THREE.MeshStandardMaterial({
      color: 0x17130f,
      roughness: 0.88,
      metalness: 0.12,
    }),
  )
  base.position.y = -0.09
  base.receiveShadow = true
  greenhouse!.add(base)

  const soil = new THREE.Mesh(
    new THREE.BoxGeometry(width - 0.15, 0.06, depth - 0.15),
    new THREE.MeshStandardMaterial({
      color: 0x241a11,
      roughness: 0.96,
    }),
  )
  soil.position.y = 0.02
  greenhouse!.add(soil)

  zEdges.forEach((z) => {
    xEdges.forEach((x, index) => {
      greenhouse!.add(
        createBeam(
          new THREE.Vector3(x, 0.05, z),
          new THREE.Vector3(x, eave, z),
          index === 0 || index === xEdges.length - 1 ? 0.035 : 0.026,
          index === 0 || index === xEdges.length - 1
            ? frameMaterial
            : accentFrameMaterial,
        ),
      )
    })

    for (let index = 0; index < bayCount; index += 1) {
      const left = xEdges[index]
      const right = xEdges[index + 1]
      const center = (left + right) / 2
      greenhouse!.add(
        createBeam(
          new THREE.Vector3(left, eave, z),
          new THREE.Vector3(center, ridge, z),
          0.02,
          frameMaterial,
        ),
      )
      greenhouse!.add(
        createBeam(
          new THREE.Vector3(center, ridge, z),
          new THREE.Vector3(right, eave, z),
          0.02,
          frameMaterial,
        ),
      )
      greenhouse!.add(
        createGlassPanel(
          [
            new THREE.Vector3(left, eave, z),
            new THREE.Vector3(center, ridge, z),
            new THREE.Vector3(center, ridge, z + 0.02),
            new THREE.Vector3(left, eave, z + 0.02),
          ],
          glassMaterial,
        ),
      )

      greenhouse!.add(
        createBeam(
          new THREE.Vector3(center, ridge, -depth / 2),
          new THREE.Vector3(center, ridge, depth / 2),
          0.018,
          accentFrameMaterial,
        ),
      )
    }
  })

  xEdges.forEach((x) => {
    greenhouse!.add(
      createBeam(
        new THREE.Vector3(x, eave, -depth / 2),
        new THREE.Vector3(x, eave, depth / 2),
        0.018,
        frameMaterial,
      ),
    )
  })

  for (let index = 0; index < bayCount; index += 1) {
    const left = xEdges[index]
    const right = xEdges[index + 1]
    const center = (left + right) / 2
    greenhouse!.add(
      createGlassPanel(
        [
          new THREE.Vector3(left, eave, -depth / 2),
          new THREE.Vector3(center, ridge, -depth / 2),
          new THREE.Vector3(center, ridge, depth / 2),
          new THREE.Vector3(left, eave, depth / 2),
        ],
        glassMaterial,
      ),
      createGlassPanel(
        [
          new THREE.Vector3(center, ridge, -depth / 2),
          new THREE.Vector3(right, eave, -depth / 2),
          new THREE.Vector3(right, eave, depth / 2),
          new THREE.Vector3(center, ridge, depth / 2),
        ],
        glassMaterial,
      ),
    )
  }

  ;[-width / 2, width / 2].forEach((x) => {
    zEdges.forEach((z, index) => {
      if (index === zEdges.length - 1) return
      greenhouse!.add(
        createGlassPanel(
          [
            new THREE.Vector3(x, 0.05, z),
            new THREE.Vector3(x, eave, z),
            new THREE.Vector3(x, eave, zEdges[index + 1]),
            new THREE.Vector3(x, 0.05, zEdges[index + 1]),
          ],
          glassMaterial,
        ),
      )
    })
  })

  for (let row = 0; row < bayCount; row += 1) {
    const x = (xEdges[row] + xEdges[row + 1]) / 2
    for (let plantIndex = 0; plantIndex < 6; plantIndex += 1) {
      const z = -2.4 + plantIndex * 0.96
      const plant = createPlant(x, z, 0.94 + (row + plantIndex) % 3 * 0.05)
      plant.castShadow = true
      greenhouse!.add(plant)
    }
  }

  greenhouse!.add(createFan(-depth / 2 - 0.02, -1.15))
  greenhouse!.add(createFan(-depth / 2 - 0.02, 1.15))

  const animatedRing = new THREE.Group()
  const ringMaterial = new THREE.MeshBasicMaterial({
    color: accent,
    transparent: true,
    opacity: 0.34,
    side: THREE.DoubleSide,
    depthWrite: false,
  })
  ;[
    [5.9, 0.012],
    [7.2, 0.008],
    [8.8, 0.006],
  ].forEach(([radius, tube]) => {
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(radius, tube, 6, 120),
      ringMaterial,
    )
    ring.rotation.x = Math.PI / 2
    animatedRing.add(ring)
  })
  animatedRing.position.y = -0.15
  energyRing = animatedRing
  greenhouse!.add(energyRing)

  scene.add(greenhouse)

  const grid = new THREE.GridHelper(20, 44, accent, 0x3b2516)
  grid.position.y = -0.19
  const gridMaterials = Array.isArray(grid.material)
    ? grid.material
    : [grid.material]
  gridMaterials.forEach((material) => {
    material.transparent = true
    material.opacity = 0.24
  })
  scene.add(grid)

  const outer = new THREE.LineLoop(
    new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-9.2, -0.18, -6.7),
      new THREE.Vector3(9.2, -0.18, -6.7),
      new THREE.Vector3(9.2, -0.18, 6.7),
      new THREE.Vector3(-9.2, -0.18, 6.7),
    ]),
    new THREE.LineBasicMaterial({
      color: accent,
      transparent: true,
      opacity: 0.38,
    }),
  )
  scene.add(outer)

  renderer.domElement.addEventListener('pointermove', (event) => {
    const rect = renderer!.domElement.getBoundingClientRect()
    pointerTarget.set(
      ((event.clientX - rect.left) / rect.width) * 2 - 1,
      -((event.clientY - rect.top) / rect.height) * 2 + 1,
    )
  })
  renderer.domElement.addEventListener('pointerleave', () => {
    pointerTarget.set(0, 0)
  })

  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(host)
  resize()
  animate()
}

function resize() {
  if (!canvasHost.value || !renderer || !camera) return
  const width = Math.max(1, canvasHost.value.clientWidth)
  const height = Math.max(1, canvasHost.value.clientHeight)
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

function animate() {
  if (!renderer || !scene || !camera || !controls) return
  animationFrame = requestAnimationFrame(animate)
  const elapsed = clock.getElapsedTime()
  if (greenhouse) {
    greenhouse.rotation.y +=
      (pointerTarget.x * 0.05 + (props.modelRotation / 180) * 0.12
        - greenhouse.rotation.y) * 0.025
    greenhouse.rotation.x +=
      (-pointerTarget.y * 0.025 - greenhouse.rotation.x) * 0.025
  }
  if (energyRing) {
    energyRing.rotation.y = elapsed * 0.09
    energyRing.children.forEach((child, index) => {
      const material = (child as THREE.Mesh).material
      if (material instanceof THREE.MeshBasicMaterial) {
        material.opacity =
          0.2 + Math.sin(elapsed * 1.25 + index * 0.9) * 0.09
      }
    })
  }
  plantMaterials.forEach((material, index) => {
    material.emissiveIntensity =
      0.16 + Math.sin(elapsed * 1.4 + index * 0.2) * 0.06
  })
  controls.update()
  renderer.render(scene, camera)
}

function resetView() {
  if (!camera || !controls) return
  camera.position.set(11.8, 6.1, 12.6)
  controls.target.set(0, 1.15, 0)
  controls.update()
}

watch(
  () => props.highlight,
  (value) => {
    plantMaterials.forEach((material, index) => {
      material.emissive.set(value ? props.accent : 0x173719)
      material.emissiveIntensity = value ? 0.3 + (index % 3) * 0.04 : 0.2
    })
  },
)

onMounted(buildScene)

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  controls?.dispose()
  renderer?.dispose()
  scene?.traverse((object) => {
    if (object instanceof THREE.Mesh || object instanceof THREE.Line) {
      object.geometry.dispose()
      const materials = Array.isArray(object.material)
        ? object.material
        : [object.material]
      materials.forEach((material) => material.dispose())
    }
  })
  canvasHost.value?.replaceChildren()
  scene = null
  camera = null
  renderer = null
  controls = null
  greenhouse = null
  energyRing = null
  plantMaterials.length = 0
})

defineExpose({ resetView })
</script>

<template>
  <div ref="canvasHost" class="hero-greenhouse-scene"></div>
</template>

<style scoped>
.hero-greenhouse-scene {
  width: 100%;
  height: 100%;
  min-height: 420px;
}

.hero-greenhouse-scene :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
}
</style>

import * as THREE from 'three'
import { STAGE, chainPoint, stagePoint } from '../panels.js'

// 温室结构尺寸（单位：米），与现有三维场景保持同一套农业语义：
// 三跨文洛式玻璃温室 + 四畦种植带 + 环境感知/执行设备/可信存证/全链追溯四层可视化。
const GUTTER_X = [-7.2, -2.4, 2.4, 7.2]
const RIDGE_X = [-4.8, 0, 4.8]
const HALF_D = 4.8
const EAVE_Y = 2.9
const RIDGE_Y = 4.3
const ROW_Z = [-4.8, -1.6, 1.6, 4.8]
const BED_X = [-5.4, -1.8, 1.8, 5.4]

const COLOR = {
  deck: 0x0d251c,
  deckLine: 0x4fd1a5,
  frame: 0xb9c9c2,
  frameDark: 0x6f837a,
  glass: 0x8fd9bd,
  bed: 0x2a2118,
  soil: 0x1d1710,
  plant: 0x5fae74,
  accent: 0x4fd1a5,
  amber: 0xe8a33d,
  lime: 0xa8c957,
  coral: 0xe2685a,
}

const UP = new THREE.Vector3(0, 1, 0)
const UNIT_CYLINDER = new THREE.CylinderGeometry(1, 1, 1, 6, 1, false)
const UNIT_BOX = new THREE.BoxGeometry(1, 1, 1)

const dummy = new THREE.Object3D()
const tmpA = new THREE.Vector3()
const tmpB = new THREE.Vector3()

function strut(start, end, radius, material) {
  const a = new THREE.Vector3().fromArray(start)
  const b = new THREE.Vector3().fromArray(end)
  const direction = tmpA.subVectors(b, a)
  const length = direction.length()
  const mesh = new THREE.Mesh(UNIT_CYLINDER, material)
  mesh.position.copy(a).addScaledVector(direction, 0.5)
  mesh.quaternion.setFromUnitVectors(UP, tmpB.copy(direction).normalize())
  mesh.scale.set(radius, length, radius)
  return mesh
}

function slab(position, size, material) {
  const mesh = new THREE.Mesh(UNIT_BOX, material)
  mesh.position.fromArray(position)
  mesh.scale.fromArray(size)
  return mesh
}

// 任意凸多边形三角化；统一计算法线，避免出现无光照的黑面。
function surface(points, material) {
  const positions = []
  for (let index = 1; index < points.length - 1; index += 1) {
    positions.push(...points[0], ...points[index], ...points[index + 1])
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geometry.computeVertexNormals()
  return new THREE.Mesh(geometry, material)
}

function glowMaterial(color, opacity = 1) {
  return new THREE.MeshBasicMaterial({
    color,
    transparent: true,
    opacity,
    // 白天场景下叠加混合会被浅色背景吃掉，改用常规混合保留识别色。
    blending: THREE.NormalBlending,
    depthWrite: false,
  })
}

function makeTextTexture(text, options = {}) {
  const {
    sub = '',
    color = '#8ff5d3',
    width = 768,
    height = 192,
    font = '600 62px "Bahnschrift", "Microsoft YaHei", sans-serif',
  } = options
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = color
  ctx.font = font
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 8, sub ? height * 0.36 : height * 0.5)
  if (sub) {
    ctx.font = '400 30px "Bahnschrift", "Microsoft YaHei", sans-serif'
    ctx.globalAlpha = 0.62
    ctx.fillText(sub, 10, height * 0.74)
    ctx.globalAlpha = 1
  }
  ctx.fillStyle = color
  ctx.globalAlpha = 0.75
  ctx.fillRect(8, height - 26, Math.min(width - 16, 420), 3)
  ctx.globalAlpha = 1
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = 4
  return texture
}

function makeCodeTexture() {
  const size = 320
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#071d17'
  ctx.fillRect(0, 0, size, size)

  const cells = 21
  const cell = size / cells
  let seed = 20261003
  const random = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648
    return seed / 2147483648
  }

  ctx.fillStyle = '#7cf0c4'
  for (let y = 0; y < cells; y += 1) {
    for (let x = 0; x < cells; x += 1) {
      if (random() > 0.52) ctx.fillRect(x * cell, y * cell, cell, cell)
    }
  }

  const finder = (ox, oy) => {
    ctx.fillStyle = '#071d17'
    ctx.fillRect(ox * cell - 2, oy * cell - 2, cell * 7 + 4, cell * 7 + 4)
    ctx.fillStyle = '#7cf0c4'
    ctx.fillRect(ox * cell, oy * cell, cell * 7, cell * 7)
    ctx.fillStyle = '#071d17'
    ctx.fillRect((ox + 1) * cell, (oy + 1) * cell, cell * 5, cell * 5)
    ctx.fillStyle = '#7cf0c4'
    ctx.fillRect((ox + 2) * cell, (oy + 2) * cell, cell * 3, cell * 3)
  }
  finder(0, 0)
  finder(cells - 7, 0)
  finder(0, cells - 7)

  ctx.fillStyle = '#7cf0c4'
  ctx.fillRect(size * 0.3, size * 0.44, size * 0.4, size * 0.15)
  ctx.fillStyle = '#071d17'
  ctx.font = '600 30px "Bahnschrift", sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('CB-2026-001', size / 2, size * 0.545)

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

function sprite(texture, width, height) {
  const material = new THREE.SpriteMaterial({
    map: texture,
    transparent: true,
    depthWrite: false,
    // 白天背景是浅色，叠加混合会把标签洗成白色，改为常规混合保证可读。
    blending: THREE.NormalBlending,
    opacity: 0,
  })
  const item = new THREE.Sprite(material)
  item.scale.set(width, height, 1)
  return item
}

function buildDeck() {
  const group = new THREE.Group()
  const slabMaterial = new THREE.MeshStandardMaterial({
    color: COLOR.deck,
    metalness: 0.62,
    roughness: 0.58,
  })
  const base = slab([0, -0.24, 0], [19.4, 0.44, 14.6], slabMaterial)
  base.receiveShadow = true
  group.add(base)

  const edges = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(19.4, 0.46, 14.6)),
    new THREE.LineBasicMaterial({
      color: COLOR.deckLine,
      transparent: true,
      opacity: 0.6,
    }),
  )
  edges.position.y = -0.24
  group.add(edges)

  const grid = new THREE.GridHelper(19, 19, 0x2f6b58, 0x163a2e)
  grid.position.y = 0.012
  grid.material.transparent = true
  grid.material.opacity = 0.42
  grid.material.depthWrite = false
  group.add(grid)

  const ticks = new THREE.InstancedMesh(
    UNIT_BOX,
    glowMaterial(COLOR.accent, 0.5),
    48,
  )
  for (let index = 0; index < 48; index += 1) {
    const angle = (index / 48) * Math.PI * 2
    const long = index % 4 === 0
    dummy.position.set(Math.cos(angle) * 9.6, 0.03, Math.sin(angle) * 9.6)
    dummy.rotation.set(0, -angle, 0)
    dummy.scale.set(long ? 0.7 : 0.26, 0.02, 0.045)
    dummy.updateMatrix()
    ticks.setMatrixAt(index, dummy.matrix)
  }
  group.add(ticks)

  return { group, ticks }
}

function buildShell() {
  const group = new THREE.Group()
  const frameMaterial = new THREE.MeshStandardMaterial({
    color: COLOR.frame,
    metalness: 0.92,
    roughness: 0.26,
  })
  const frameDarkMaterial = new THREE.MeshStandardMaterial({
    color: COLOR.frameDark,
    metalness: 0.7,
    roughness: 0.42,
  })
  const glassMaterial = new THREE.MeshPhysicalMaterial({
    color: COLOR.glass,
    transparent: true,
    opacity: 0.18,
    roughness: 0.06,
    metalness: 0.0,
    clearcoat: 1,
    clearcoatRoughness: 0.08,
    side: THREE.DoubleSide,
    depthWrite: false,
  })
  const glassGroup = new THREE.Group()
  const frameGroup = new THREE.Group()

  GUTTER_X.forEach((x) => {
    ROW_Z.forEach((z) => {
      frameGroup.add(strut([x, 0, z], [x, EAVE_Y, z], 0.055, frameMaterial))
    })
    frameGroup.add(
      strut([x, EAVE_Y, -HALF_D], [x, EAVE_Y, HALF_D], 0.07, frameDarkMaterial),
    )
  })

  RIDGE_X.forEach((x) => {
    frameGroup.add(
      strut([x, RIDGE_Y, -HALF_D], [x, RIDGE_Y, HALF_D], 0.06, frameMaterial),
    )
  })

  ROW_Z.forEach((z) => {
    GUTTER_X.forEach((x, index) => {
      if (index === GUTTER_X.length - 1) return
      const ridge = RIDGE_X[index]
      frameGroup.add(
        strut([x, EAVE_Y, z], [ridge, RIDGE_Y, z], 0.05, frameMaterial),
      )
      frameGroup.add(
        strut([ridge, RIDGE_Y, z], [x + 4.8, EAVE_Y, z], 0.05, frameMaterial),
      )
      frameGroup.add(
        strut([x, EAVE_Y, z], [x + 4.8, EAVE_Y, z], 0.045, frameDarkMaterial),
      )
    })
  })

  ROW_Z.forEach((z) => {
    const ridge = RIDGE_X[1]
    frameGroup.add(
      strut(
        [GUTTER_X[0], EAVE_Y + 0.02, z],
        [ridge, RIDGE_Y - 0.02, z],
        0.028,
        frameDarkMaterial,
      ),
    )
    frameGroup.add(
      strut(
        [ridge, RIDGE_Y - 0.02, z],
        [GUTTER_X[3], EAVE_Y + 0.02, z],
        0.028,
        frameDarkMaterial,
      ),
    )
  })

  RIDGE_X.forEach((x) => {
    ;[-1, 1].forEach((side) => {
      frameGroup.add(
        strut(
          [x + side * 1.2, EAVE_Y + 0.7, -HALF_D],
          [x + side * 1.2, EAVE_Y + 0.7, HALF_D],
          0.03,
          frameDarkMaterial,
        ),
      )
    })
  })

  RIDGE_X.forEach((x) => {
    glassGroup.add(
      surface(
        [
          [x - 2.4, EAVE_Y, -HALF_D],
          [x - 2.4, EAVE_Y, HALF_D],
          [x, RIDGE_Y, HALF_D],
          [x, RIDGE_Y, -HALF_D],
        ],
        glassMaterial,
      ),
    )
    glassGroup.add(
      surface(
        [
          [x, RIDGE_Y, -HALF_D],
          [x, RIDGE_Y, HALF_D],
          [x + 2.4, EAVE_Y, HALF_D],
          [x + 2.4, EAVE_Y, -HALF_D],
        ],
        glassMaterial,
      ),
    )
  })

  ;[-HALF_D, HALF_D].forEach((z) => {
    GUTTER_X.forEach((x, index) => {
      if (index === GUTTER_X.length - 1) return
      glassGroup.add(
        surface(
          [
            [x, 0.05, z],
            [x, EAVE_Y, z],
            [x + 4.8, EAVE_Y, z],
            [x + 4.8, 0.05, z],
          ],
          glassMaterial,
        ),
      )
      const ridge = RIDGE_X[index]
      glassGroup.add(
        surface(
          [
            [x, EAVE_Y, z],
            [x + 4.8, EAVE_Y, z],
            [ridge, RIDGE_Y, z],
          ],
          glassMaterial,
        ),
      )
    })
  })

  ;[-7.2, 7.2].forEach((x) => {
    glassGroup.add(
      surface(
        [
          [x, 0.05, -HALF_D],
          [x, EAVE_Y, -HALF_D],
          [x, EAVE_Y, HALF_D],
          [x, 0.05, HALF_D],
        ],
        glassMaterial,
      ),
    )
  })

  const vents = new THREE.Group()
  const ventMaterial = glassMaterial.clone()
  ventMaterial.opacity = 0.2
  ;[-4.8, 4.8].forEach((x) => {
    const vent = new THREE.Group()
    vent.position.set(x, RIDGE_Y, 0)
    const panel = surface(
      [
        [0, 0, -3.1],
        [0, 0, 3.1],
        [2.4, -1.4, 3.1],
        [2.4, -1.4, -3.1],
      ],
      ventMaterial,
    )
    const rail = strut([2.4, -1.4, -3.1], [2.4, -1.4, 3.1], 0.035, frameMaterial)
    vent.add(panel, rail)
    vents.add(vent)
  })

  const door = new THREE.Group()
  const doorPanel = slab(
    [0, 1.05, -HALF_D - 0.02],
    [1.5, 2.1, 0.06],
    new THREE.MeshPhysicalMaterial({
      color: COLOR.glass,
      transparent: true,
      opacity: 0.22,
      roughness: 0.1,
      clearcoat: 1,
    }),
  )
  door.add(
    doorPanel,
    strut([-0.75, 0, -HALF_D - 0.02], [-0.75, 2.1, -HALF_D - 0.02], 0.04, frameMaterial),
    strut([0.75, 0, -HALF_D - 0.02], [0.75, 2.1, -HALF_D - 0.02], 0.04, frameMaterial),
  )

  group.add(frameGroup, glassGroup, vents, door)
  group.traverse((item) => {
    if (item.isMesh && item.material.metalness > 0.5) item.castShadow = true
  })

  return { group, glass: glassGroup, vents, frame: frameGroup }
}

function buildInterior() {
  const group = new THREE.Group()
  const bedMaterial = new THREE.MeshStandardMaterial({
    color: COLOR.bed,
    roughness: 0.88,
    metalness: 0.1,
  })
  const soilMaterial = new THREE.MeshStandardMaterial({
    color: COLOR.soil,
    roughness: 0.96,
  })
  const plantMaterial = new THREE.MeshStandardMaterial({
    color: COLOR.plant,
    emissive: new THREE.Color(0x1f3d26),
    emissiveIntensity: 0.42,
    roughness: 0.62,
  })
  const dripMaterial = new THREE.MeshBasicMaterial({
    color: COLOR.accent,
    transparent: true,
    opacity: 0.5,
    depthWrite: false,
  })

  const beds = new THREE.Group()
  const drip = new THREE.Group()
  BED_X.forEach((x, bedIndex) => {
    const bed = slab([x, 0.17, 0], [1.7, 0.34, 8.6], bedMaterial)
    bed.castShadow = true
    bed.receiveShadow = true
    beds.add(bed)
    beds.add(slab([x, 0.35, 0], [1.44, 0.06, 8.2], soilMaterial))

    const line = slab([x, 0.42, 0], [0.05, 0.05, 8.2], dripMaterial.clone())
    line.userData.baseOpacity = 0.32
    drip.add(line)
    ;[-1.9, 1.9].forEach((offset) => {
      const row = slab([x + offset, 0.44, 0], [0.035, 0.035, 8.0], dripMaterial.clone())
      row.userData.baseOpacity = 0.2
      drip.add(row)
    })
    void bedIndex
  })

  const plantCount = BED_X.length * 7
  const plants = new THREE.InstancedMesh(
    new THREE.IcosahedronGeometry(0.36, 0),
    plantMaterial,
    plantCount,
  )
  const color = new THREE.Color()
  let index = 0
  BED_X.forEach((x) => {
    for (let row = 0; row < 7; row += 1) {
      const z = -3.6 + row * 1.2
      const jitter = ((row % 3) - 1) * 0.12
      dummy.position.set(x + jitter, 0.66 + (row % 2) * 0.05, z)
      dummy.rotation.set(row * 0.4, row * 0.9, 0)
      const scale = 0.86 + ((row + index) % 3) * 0.12
      dummy.scale.set(scale, scale * 1.35, scale)
      dummy.updateMatrix()
      plants.setMatrixAt(index, dummy.matrix)
      color.setHSL(0.38 + ((index % 5) - 2) * 0.012, 0.52, 0.42 + (index % 3) * 0.04)
      plants.setColorAt(index, color)
      index += 1
    }
  })
  plants.castShadow = true
  plants.instanceMatrix.needsUpdate = true
  if (plants.instanceColor) plants.instanceColor.needsUpdate = true

  group.add(beds, drip, plants)
  return { group, beds, drip, plants }
}

function buildClimate() {
  const group = new THREE.Group()
  const sensors = new THREE.Group()
  const fans = new THREE.Group()
  const curtains = new THREE.Group()
  const lights = new THREE.Group()

  const sensorPositions = [
    [-5.4, 2.25, -2.4],
    [-1.8, 1.7, 2.6],
    [1.8, 2.55, 0.4],
    [5.4, 1.95, -3.0],
    [0, 3.5, 1.2],
  ]
  sensorPositions.forEach((position) => {
    const sensor = new THREE.Group()
    sensor.position.fromArray(position)
    const coreMaterial = glowMaterial(COLOR.accent, 0.95)
    const ringMaterial = glowMaterial(COLOR.accent, 0.7)
    const core = new THREE.Mesh(new THREE.OctahedronGeometry(0.13, 0), coreMaterial)
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.29, 0.016, 6, 28), ringMaterial)
    ring.rotation.x = Math.PI / 2
    const halo = new THREE.Mesh(new THREE.TorusGeometry(0.42, 0.008, 6, 28), glowMaterial(COLOR.accent, 0.28))
    halo.rotation.x = Math.PI / 2
    const beam = new THREE.Mesh(
      new THREE.CylinderGeometry(0.014, 0.014, 1.5, 6),
      glowMaterial(COLOR.accent, 0.22),
    )
    beam.position.y = -0.75
    sensor.add(core, ring, halo, beam)
    sensor.userData = {
      baseY: position[1],
      ring,
      halo,
      core,
      ringSpeed: 0.5 + Math.random() * 0.5,
      phase: Math.random() * Math.PI * 2,
    }
    sensors.add(sensor)
  })

  const fanMaterial = new THREE.MeshStandardMaterial({
    color: 0x9a9aa0,
    metalness: 0.85,
    roughness: 0.3,
  })
  const bladeMaterial = new THREE.MeshStandardMaterial({
    color: 0xd4d4d8,
    metalness: 0.6,
    roughness: 0.28,
  })
  const fanPlacements = [
    [-3.6, 2.05, -HALF_D - 0.08, Math.PI],
    [3.6, 2.05, -HALF_D - 0.08, Math.PI],
    [-3.6, 2.05, HALF_D + 0.08, 0],
    [3.6, 2.05, HALF_D + 0.08, 0],
  ]
  fanPlacements.forEach(([x, y, z, rotY]) => {
    const fan = new THREE.Group()
    fan.position.set(x, y, z)
    fan.rotation.y = rotY
    fan.add(
      new THREE.Mesh(new THREE.TorusGeometry(0.45, 0.05, 8, 24), fanMaterial),
      new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.07, 0.16, 10), fanMaterial),
    )
    const blades = new THREE.Group()
    for (let i = 0; i < 4; i += 1) {
      const blade = slab([0, 0, 0], [0.62, 0.05, 0.16], bladeMaterial)
      blade.rotation.z = (i / 4) * Math.PI * 2
      blade.position.set(Math.cos((i / 4) * Math.PI * 2) * 0.3, Math.sin((i / 4) * Math.PI * 2) * 0.3, 0.02)
      blades.add(blade)
    }
    const hub = new THREE.Mesh(
      new THREE.SphereGeometry(0.08, 12, 10),
      new THREE.MeshStandardMaterial({ color: 0xe4e4e7, metalness: 0.7, roughness: 0.24 }),
    )
    blades.add(hub)
    fan.add(blades)
    fan.userData = { blades }
    fans.add(fan)
  })

  const curtainMaterial = new THREE.MeshStandardMaterial({
    color: 0x2b3d36,
    transparent: true,
    opacity: 0.46,
    roughness: 0.72,
    metalness: 0.15,
    side: THREE.DoubleSide,
  })
  const curtainGlow = glowMaterial(COLOR.amber, 0.32)
  ;[-7.26, 7.26].forEach((x) => {
    const curtain = new THREE.Group()
    const fabric = slab([x, 0, 0], [0.05, EAVE_Y, 9.2], curtainMaterial)
    fabric.position.y = -EAVE_Y / 2
    const rail = slab([x, 0.02, 0], [0.16, 0.08, 9.6], curtainGlow)
    curtain.add(fabric, rail)
    curtain.position.y = EAVE_Y
    curtain.userData = { fabric, rail }
    curtains.add(curtain)
  })

  RIDGE_X.forEach((x) => {
    const bar = slab([x, 3.32, 0], [0.18, 0.09, 8.7], glowMaterial(COLOR.amber, 0.22))
    bar.userData.baseOpacity = 0.22
    lights.add(bar)
  })

  group.add(sensors, fans, curtains, lights)
  return { group, sensors, fans, curtains, lights }
}

function buildGhost() {
  const group = new THREE.Group()
  const material = new THREE.LineBasicMaterial({
    color: COLOR.accent,
    transparent: true,
    opacity: 0,
    blending: THREE.NormalBlending,
    depthWrite: false,
  })
  const edges = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(14.4, RIDGE_Y, 9.6)),
    material,
  )
  edges.position.y = RIDGE_Y / 2
  const ridgeLines = new THREE.Group()
  RIDGE_X.forEach((x) => {
    ridgeLines.add(strut([x, RIDGE_Y, -HALF_D], [x, RIDGE_Y, HALF_D], 0.012, material))
  })
  const scanMaterial = new THREE.MeshBasicMaterial({
    color: COLOR.accent,
    transparent: true,
    opacity: 0,
    blending: THREE.NormalBlending,
    depthWrite: false,
    side: THREE.DoubleSide,
  })
  const scan = new THREE.Mesh(new THREE.PlaneGeometry(16, 11), scanMaterial)
  scan.rotation.x = -Math.PI / 2
  group.add(edges, ridgeLines, scan)
  group.userData = { material, scanMaterial, scan, edges }
  return group
}

function buildMotes() {
  const count = 96
  const material = glowMaterial(COLOR.accent, 0.55)
  const motes = new THREE.InstancedMesh(
    new THREE.TetrahedronGeometry(0.075, 0),
    material,
    count,
  )
  const seeds = []
  for (let index = 0; index < count; index += 1) {
    seeds.push({
      radius: 8.6 + Math.random() * 5.4,
      angle: Math.random() * Math.PI * 2,
      speed: 0.06 + Math.random() * 0.12,
      y: 0.4 + Math.random() * 7.4,
      drift: 0.16 + Math.random() * 0.36,
      scale: 0.6 + Math.random() * 1.2,
    })
    dummy.position.set(0, -50, 0)
    dummy.updateMatrix()
    motes.setMatrixAt(index, dummy.matrix)
  }
  motes.userData = { seeds }
  return motes
}

function buildHalo() {
  const group = new THREE.Group()
  const ring = new THREE.Mesh(
    new THREE.RingGeometry(9.35, 9.5, 96),
    glowMaterial(COLOR.accent, 0.16),
  )
  ring.rotation.x = -Math.PI / 2
  ring.position.y = 0.02

  const outer = new THREE.Mesh(
    new THREE.RingGeometry(10.6, 10.66, 96),
    glowMaterial(COLOR.accent, 0.1),
  )
  outer.rotation.x = -Math.PI / 2
  outer.position.y = 0.02

  const sweep = new THREE.Mesh(
    new THREE.RingGeometry(4.2, 11.4, 96, 1, 0, Math.PI / 5),
    new THREE.MeshBasicMaterial({
      color: COLOR.accent,
      transparent: true,
      opacity: 0.07,
      blending: THREE.NormalBlending,
      depthWrite: false,
      side: THREE.DoubleSide,
    }),
  )
  sweep.rotation.x = -Math.PI / 2
  sweep.position.y = 0.03

  group.add(ring, outer, sweep)
  group.userData = { sweep, outer }
  return group
}

function buildChain() {
  const group = new THREE.Group()

  const nodeMaterial = new THREE.MeshStandardMaterial({
    color: 0x18332a,
    emissive: new THREE.Color(COLOR.lime),
    emissiveIntensity: 0.1,
    metalness: 0.6,
    roughness: 0.28,
  })
  const outlineMaterial = glowMaterial(COLOR.lime, 0.34)
  const linkMaterial = glowMaterial(COLOR.lime, 0.26)
  const nodes = []
  const positions = []
  for (let index = 0; index < STAGE.chain.levels; index += 1) {
    positions.push(chainPoint(index))
  }
  positions.forEach((position, index) => {
    const node = new THREE.Group()
    node.position.fromArray(position)
    const material = nodeMaterial.clone()
    const outline = outlineMaterial.clone()
    const shell = new THREE.Mesh(
      new THREE.CylinderGeometry(0.36, 0.36, 0.12, 6),
      material,
    )
    shell.rotation.x = Math.PI / 2
    const edge = new THREE.LineSegments(
      new THREE.EdgesGeometry(new THREE.CylinderGeometry(0.38, 0.38, 0.14, 6)),
      outline,
    )
    edge.rotation.x = Math.PI / 2
    const dot = new THREE.Mesh(
      new THREE.OctahedronGeometry(0.09, 0),
      glowMaterial(COLOR.lime, 0.85),
    )
    dot.position.z = 0.12
    node.add(shell, edge, dot)
    node.userData = { material, outline, dot, index, baseY: position[1] }
    nodes.push(node)
    group.add(node)
    if (index > 0) {
      group.add(
        strut(positions[index - 1], position, 0.022, linkMaterial.clone()),
      )
    }
  })

  const rootTag = sprite(
    makeTextTexture('ROOT HASH', { sub: 'SHA-256 · 周期根摘要', color: '#c9f5d8' }),
    2.9,
    0.72,
  )
  const top = positions[positions.length - 1]
  rootTag.position.set(top[0], top[1] + 1.35, top[2])
  group.add(rootTag)

  group.visible = false
  group.userData = { nodes, rootTag }
  return group
}

function buildTrace() {
  const group = new THREE.Group()
  const curve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(...stagePoint(4, -2.6, 5.6, 1.0)),
    new THREE.Vector3(...stagePoint(4, -0.6, 7.6, 1.6)),
    new THREE.Vector3(...stagePoint(4, 3.4, 8.6, 2.2)),
    new THREE.Vector3(...stagePoint(4, STAGE.trace.right, STAGE.trace.toward, STAGE.trace.height)),
  ])
  const tube = new THREE.Mesh(
    new THREE.TubeGeometry(curve, 72, 0.035, 8, false),
    glowMaterial(COLOR.coral, 0.6),
  )

  const sparks = []
  for (let index = 0; index < 4; index += 1) {
    const spark = new THREE.Mesh(
      new THREE.SphereGeometry(0.11, 12, 10),
      glowMaterial(COLOR.coral, 0.95),
    )
    spark.userData.offset = index / 4
    sparks.push(spark)
    group.add(spark)
  }

  const plateTexture = makeCodeTexture()
  const plate = sprite(plateTexture, 2.1, 2.1)
  plate.material.blending = THREE.NormalBlending
  plate.material.opacity = 0
  plate.position.copy(curve.getPointAt(1))
  plate.position.y += 0.55

  const tag = sprite(
    makeTextTexture('一物一码', { sub: '扫码验签 · 链上核对', color: '#ffd0c4' }),
    3.2,
    0.8,
  )
  tag.position.copy(plate.position)
  tag.position.y -= 1.5

  group.add(tube, plate, tag)
  group.visible = false
  group.userData = { curve, tube, sparks, plate, tag }
  return group
}

function buildTags() {
  const group = new THREE.Group()
  const name = sprite(
    makeTextTexture('GH-001', { sub: '番茄 · 金棚一号 · 1,280 ㎡', color: '#b8f2d8' }),
    4.4,
    1.1,
  )
  name.position.set(...stagePoint(0, 6.4, 3.0, 1.25))

  const sync = sprite(
    makeTextTexture('数字孪生体', { sub: 'SYNC 98.6% · 延迟 1.8s', color: '#e6fff2' }),
    4.6,
    1.15,
  )
  sync.position.set(0, 6.2, 0)

  group.add(name, sync)
  group.userData = { name, sync }
  return group
}

export function buildGreenhouseModel() {
  const root = new THREE.Group()
  const parts = {}

  const deck = buildDeck()
  const shell = buildShell()
  const interior = buildInterior()
  const climate = buildClimate()
  const ghost = buildGhost()
  const motes = buildMotes()
  const halo = buildHalo()
  const chain = buildChain()
  const trace = buildTrace()
  const tags = buildTags()

  root.add(
    deck.group,
    shell.group,
    interior.group,
    climate.group,
    ghost,
    motes,
    halo,
    chain,
    trace,
    tags,
  )

  parts.deck = deck.group
  parts.shell = shell.group
  parts.vents = shell.vents
  parts.glass = shell.glass
  parts.beds = interior.beds
  parts.plants = interior.plants
  parts.drip = interior.drip
  parts.sensors = climate.sensors
  parts.fans = climate.fans
  parts.curtains = climate.curtains
  parts.lights = climate.lights
  parts.ghost = ghost
  parts.motes = motes
  parts.halo = halo
  parts.chain = chain
  parts.trace = trace
  parts.tags = tags

  const state = {
    time: 0,
    sensing: 0,
    control: 0,
    chain: 0,
    trace: 0,
    ghost: 0,
    focus: 0,
  }

  function update(delta) {
    state.time += delta
    const time = state.time

    const ticks = deck.ticks
    ticks.rotation.y = time * 0.05

    shell.vents.children.forEach((vent, index) => {
      const target = state.control * (index === 0 ? 0.34 : 0.26)
      vent.rotation.z = THREE.MathUtils.damp(vent.rotation.z, -target, 3, delta)
    })

    climate.sensors.children.forEach((sensor) => {
      const data = sensor.userData
      sensor.position.y = data.baseY + Math.sin(time * 1.2 + data.phase) * 0.06
      data.ring.rotation.z += delta * data.ringSpeed * (1 + state.sensing * 3.4)
      data.halo.rotation.z -= delta * data.ringSpeed * 0.4
      const boost = 1 + state.sensing * 1.7
      data.core.scale.setScalar(0.9 + state.sensing * 0.5)
      data.ring.material.opacity = Math.min(1, 0.42 + state.sensing * 0.58) * boost * 0.72
      data.halo.material.opacity = 0.12 + state.sensing * 0.4
    })

    climate.fans.children.forEach((fan) => {
      fan.userData.blades.rotation.z += delta * (0.6 + state.control * 11)
    })

    climate.curtains.children.forEach((curtain) => {
      const target = 0.05 + state.control * 0.62
      curtain.userData.fabric.scale.y = THREE.MathUtils.damp(
        curtain.userData.fabric.scale.y,
        target,
        2.6,
        delta,
      )
      const fabric = curtain.userData.fabric
      fabric.position.y = (-EAVE_Y * fabric.scale.y) / 2
      curtain.userData.rail.material.opacity = 0.14 + state.control * 0.5
    })

    climate.lights.children.forEach((bar) => {
      bar.material.opacity = 0.16 + state.control * 0.7 + Math.sin(time * 6) * 0.03 * state.control
    })

    const plantMaterial = interior.plants.material
    plantMaterial.emissiveIntensity = 0.4 + state.sensing * 0.9
    interior.drip.children.forEach((line) => {
      const base = line.userData.baseOpacity ?? 0.2
      line.material.opacity = base + state.control * 0.5
    })

    ghost.userData.material.opacity = state.ghost * 0.34
    ghost.userData.scanMaterial.opacity = state.ghost * 0.055
    const scanY = 0.4 + ((time * 0.55) % 1) * (RIDGE_Y + 1.2)
    ghost.userData.scan.position.y = scanY
    ghost.userData.scan.visible = state.ghost > 0.01

    halo.userData.sweep.rotation.z = time * 0.35
    halo.userData.outer.rotation.z = -time * 0.12

    const seeds = motes.userData.seeds
    for (let index = 0; index < seeds.length; index += 1) {
      const seed = seeds[index]
      const angle = seed.angle + time * seed.speed
      const y = ((seed.y + time * seed.drift) % 8.4) + 0.3
      dummy.position.set(Math.cos(angle) * seed.radius, y, Math.sin(angle) * seed.radius)
      dummy.rotation.set(time * seed.speed * 3, angle, 0)
      dummy.scale.setScalar(seed.scale * (0.7 + state.sensing * 0.7))
      dummy.updateMatrix()
      motes.setMatrixAt(index, dummy.matrix)
    }
    motes.instanceMatrix.needsUpdate = true
    motes.material.opacity = 0.22 + state.ghost * 0.3 + state.chain * 0.18

    chain.visible = state.chain > 0.01
    if (chain.visible) {
      const nodes = chain.userData.nodes
      const progress = THREE.MathUtils.clamp(state.chain * 1.15, 0, 1) * nodes.length
      nodes.forEach((node) => {
        const data = node.userData
        const local = THREE.MathUtils.clamp(progress - data.index, 0, 1)
        node.scale.setScalar(0.72 + local * 0.32)
        node.position.y = data.baseY + (1 - local) * -0.5
        node.rotation.z = (1 - local) * 0.5
        data.material.emissiveIntensity = 0.08 + local * (1.35 + Math.sin(time * 3 + data.index) * 0.2)
        data.outline.opacity = 0.14 + local * 0.6
        data.dot.material.opacity = 0.2 + local * 0.75
      })
      chain.userData.rootTag.material.opacity = THREE.MathUtils.clamp(progress - nodes.length + 1, 0, 1) * 0.85
    }

    trace.visible = state.trace > 0.01
    if (trace.visible) {
      const { curve, tube, sparks, plate, tag } = trace.userData
      tube.material.opacity = 0.16 + state.trace * 0.5
      sparks.forEach((spark, index) => {
        const t = (time * 0.16 + spark.userData.offset) % 1
        spark.position.copy(curve.getPointAt(t))
        spark.scale.setScalar(0.6 + Math.sin((t + index * 0.2) * Math.PI) * 0.55)
        spark.material.opacity = state.trace * 0.95
      })
      plate.material.opacity = state.trace * 0.95
      tag.material.opacity = state.trace * 0.9
      plate.scale.setScalar(2.1 * (0.9 + state.trace * 0.1))
    }

    tags.userData.name.material.opacity = 0.86 + state.ghost * 0.14
    tags.userData.sync.material.opacity = 0.72 + state.ghost * 0.22 + state.chain * 0.06
    tags.userData.sync.position.y = 6.2 + Math.sin(time * 0.9) * 0.08
  }

  return { root, parts, state, update }
}

import * as THREE from 'three'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { buildGreenhouseModel } from './greenhouse.js'

const FOCUS_DISTANCE = 15.5
const PANEL_TARGET = new THREE.Vector3(0, 1.7, 0)
const WORLD_UP = new THREE.Vector3(0, 1, 0)
const FOOTPRINT = { x: 8.6, z: 6.4 }

function makeBackdropTexture() {
  const canvas = document.createElement('canvas')
  canvas.width = 64
  canvas.height = 512
  const ctx = canvas.getContext('2d')
  const gradient = ctx.createLinearGradient(0, 0, 0, 512)
  gradient.addColorStop(0, '#123a2c')
  gradient.addColorStop(0.34, '#0d2b22')
  gradient.addColorStop(0.72, '#08201a')
  gradient.addColorStop(1, '#051511')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, 64, 512)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

function makeGlowTexture() {
  const size = 256
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  const gradient = ctx.createRadialGradient(size / 2, size / 2, 4, size / 2, size / 2, size / 2)
  gradient.addColorStop(0, 'rgba(70, 200, 155, 0.42)')
  gradient.addColorStop(0.45, 'rgba(30, 92, 72, 0.24)')
  gradient.addColorStop(1, 'rgba(7, 29, 23, 0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, size, size)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

function damp(current, target, lambda, delta) {
  return THREE.MathUtils.damp(current, target, lambda, delta)
}

export function createCoverScene(canvas, options = {}) {
  const onProject = options.onProject ?? (() => {})
  const forceStatic =
    new URLSearchParams(window.location.search).get('force') === 'static'
    || new URLSearchParams(window.location.search).get('forceStatic') === '1'
  const appCoverElement = document.querySelector('[data-app-cover]')
  const isAppCoverVisible = () => {
    if (!appCoverElement) return true
    const style = getComputedStyle(appCoverElement)
    return style.display !== 'none' && style.visibility !== 'hidden'
  }
  const softwareRenderer = (() => {
    try {
      const probe = document.createElement('canvas')
      const gl =
        probe.getContext('webgl2', { powerPreference: 'high-performance' })
        ?? probe.getContext('webgl', { powerPreference: 'high-performance' })
      if (!gl) return true
      const info = gl.getExtension('WEBGL_debug_renderer_info')
      const name = info
        ? String(gl.getParameter(info.UNMASKED_RENDERER_WEBGL) ?? '')
        : ''
      gl.getExtension('WEBGL_lose_context')?.loseContext()
      return /swiftshader|llvmpipe|software|basic render/i.test(name)
    } catch {
      return true
    }
  })()

  if (softwareRenderer || forceStatic) {
    document.body.classList.add('is-software-renderer')
    document.body.dataset.renderEngine = forceStatic ? 'forced-static' : 'software-static'
    return {
      setHotspots() {},
      setPanel() {},
      snapToPanel() {},
      setLoginComposition() {},
      setPerformanceMode() {},
      focus() {},
      clearFocus() {},
      resize() {},
      dispose() {},
    }
  }

  const lowPowerHint =
    (navigator.hardwareConcurrency ?? 8) <= 4
    || (navigator.deviceMemory ?? 8) <= 4
  let performanceMode = options.performanceMode ?? 'home'
  if (lowPowerHint) {
    performanceMode = 'low'
  }
  // 默认直接渲染：稳定、性能好、任何显卡都能跑。
  // 独显 / 高配设备可用 ?post=bloom 打开辉光合成，观感更“发光”。
  const postMode = new URLSearchParams(window.location.search).get('post') ?? 'direct'

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: performanceMode !== 'low',
    alpha: false,
    powerPreference: 'high-performance',
  })
  renderer.setPixelRatio(
    Math.min(
      window.devicePixelRatio || 1,
      performanceMode === 'low'
        ? 1
        : performanceMode === 'login'
          ? 1.15
          : performanceMode === 'shared'
            ? 1.25
            : 1.75,
    ),
  )
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  renderer.shadowMap.enabled =
    performanceMode === 'home' || performanceMode === 'shared'
  renderer.shadowMap.type = THREE.PCFSoftShadowMap

  const scene = new THREE.Scene()
  scene.background = makeBackdropTexture()

  const camera = new THREE.PerspectiveCamera(42, 16 / 9, 0.2, 160)

  const pmrem = new THREE.PMREMGenerator(renderer)
  const environment = pmrem.fromScene(new RoomEnvironment(), 0.04)
  scene.environment = environment.texture
  scene.environmentIntensity = 0.38

  const glowSprite = new THREE.Sprite(
    new THREE.SpriteMaterial({
      map: makeGlowTexture(),
      transparent: true,
      depthWrite: false,
      depthTest: false,
      opacity: 0.4,
    }),
  )
  glowSprite.scale.set(46, 30, 1)
  glowSprite.position.set(0, 3, -8)
  glowSprite.renderOrder = -2
  scene.add(glowSprite)

  const hemisphere = new THREE.HemisphereLight(0xdff7ec, 0x0b2418, 1.15)
  scene.add(hemisphere)

  const keyLight = new THREE.DirectionalLight(0xffffff, 2.4)
  keyLight.position.set(14, 20, 12)
  keyLight.intensity = 2.3
  keyLight.castShadow = performanceMode === 'home' || performanceMode === 'shared'
  const shadowMapSize = performanceMode === 'home' ? 2048 : 1024
  keyLight.shadow.mapSize.set(shadowMapSize, shadowMapSize)
  keyLight.shadow.camera.near = 4
  keyLight.shadow.camera.far = 60
  keyLight.shadow.camera.left = -18
  keyLight.shadow.camera.right = 18
  keyLight.shadow.camera.top = 18
  keyLight.shadow.camera.bottom = -18
  keyLight.shadow.bias = -0.0009
  scene.add(keyLight)

  document.body.dataset.renderEngine = 'hardware'

  function setPerformanceMode(mode) {
    performanceMode = mode
    renderer.setPixelRatio(
      Math.min(
        window.devicePixelRatio || 1,
        mode === 'low'
          ? 1
          : mode === 'login'
            ? 1.15
            : mode === 'shared'
              ? 1.25
              : compact
                ? 1.45
                : 1.65,
      ),
    )
    renderer.shadowMap.enabled = mode === 'home' || mode === 'shared'
    keyLight.castShadow = mode === 'home' || mode === 'shared'
    if (mode === 'home' || mode === 'shared') {
      renderer.shadowMap.needsUpdate = true
    }
    resize()
  }

  const rimLight = new THREE.DirectionalLight(0x7ce0b8, 1.3)
  rimLight.position.set(-16, 9, -14)
  scene.add(rimLight)

  const warmLight = new THREE.DirectionalLight(0xe8b878, 0.55)
  warmLight.position.set(-8, 5, 16)
  scene.add(warmLight)

  const model = buildGreenhouseModel()
  scene.add(model.root)

  const emphasis = { sensing: 0, control: 0, chain: 0, trace: 0, ghost: 1 }
  const emphasisTarget = { ...emphasis }

  const rig = {
    azimuth: -0.55,
    elevation: 0.33,
    distance: 26,
    target: PANEL_TARGET.clone(),
    lookOffset: 5.2,
  }
  const rigTarget = { ...rig, target: rig.target.clone() }
  const focusView = {
    active: false,
    position: new THREE.Vector3(),
    azimuth: 0,
    elevation: 0.18,
  }
  let swing = 0
  let swingVelocity = 0
  let punch = 0
  let compact = false

  // 极轻微鼠标视差（动效档位 4–5）：指针位置只推动相机几十分之一弧度，
  // 用来让玻璃 HUD 与模型之间产生"前后层"关系；减弱动效时完全静止。
  const parallax = { x: 0, y: 0, targetX: 0, targetY: 0 }

  function prefersReducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches
  }

  function handlePointerMove(event) {
    if (prefersReducedMotion()) return
    parallax.targetX = (event.clientX / Math.max(window.innerWidth, 1)) * 2 - 1
    parallax.targetY = (event.clientY / Math.max(window.innerHeight, 1)) * 2 - 1
  }

  function handlePointerLeave() {
    parallax.targetX = 0
    parallax.targetY = 0
  }

  const hotspots = []
  const projected = []
  const pivot = new THREE.Vector3()
  const cameraPosition = new THREE.Vector3()
  const forward = new THREE.Vector3()
  const right = new THREE.Vector3()
  const lookPoint = new THREE.Vector3()
  const anchor = new THREE.Vector3()
  const depthRef = new THREE.Vector3()

  let composer = null
  let bloomPass = null
  let frameId = 0
  let running = true
  let slowFrameCount = 0
  let qualityLocked = performanceMode === 'low'
  let lastProjectionAt = 0
  let width = canvas.clientWidth || window.innerWidth
  let height = canvas.clientHeight || window.innerHeight
  const clock = new THREE.Clock()

  function usePostProcessing() {
    return (
      postMode !== 'direct'
      && !isSoftwareRenderer()
      && window.innerWidth >= 760
      && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    )
  }

  // 软件渲染（SwiftShader / llvmpipe）下浮点渲染目标不稳定，辉光会整屏发黑，直接跳过。
  function isSoftwareRenderer() {
    try {
      const gl = renderer.getContext()
      const info = gl.getExtension('WEBGL_debug_renderer_info')
      const name = info ? String(gl.getParameter(info.UNMASKED_RENDERER_WEBGL)) : ''
      return /swiftshader|llvmpipe|software|basic render/i.test(name)
    } catch {
      return false
    }
  }

  function buildComposer() {
    composer = new EffectComposer(renderer)
    composer.addPass(new RenderPass(scene, camera))
    if (postMode === 'bloom') {
      bloomPass = new UnrealBloomPass(
        new THREE.Vector2(Math.round(width / 2), Math.round(height / 2)),
        0.58,
        0.52,
        0.78,
      )
      composer.addPass(bloomPass)
    }
    if (postMode !== 'render') composer.addPass(new OutputPass())
  }

  function resize() {
    width = canvas.clientWidth || window.innerWidth
    height = canvas.clientHeight || window.innerHeight
    compact = width < 900
    renderer.setPixelRatio(
      Math.min(
        window.devicePixelRatio || 1,
        performanceMode === 'low'
          ? 1
          : performanceMode === 'login'
            ? 1.15
            : performanceMode === 'shared'
              ? 1.25
              : compact
                ? 1.45
                : 1.65,
      ),
    )
    renderer.setSize(width, height, false)
    camera.aspect = width / Math.max(height, 1)
    camera.fov = compact ? 52 : 42
    camera.updateProjectionMatrix()
    if (composer) composer.setSize(width, height)
    if (bloomPass) bloomPass.resolution.set(Math.round(width / 2), Math.round(height / 2))
    rig.lookOffset = compact ? 0 : 5.2
    rigTarget.lookOffset = rig.lookOffset
  }

  function setHotspots(list) {
    hotspots.length = 0
    projected.length = 0
    list.forEach((hotspot) => {
      hotspots.push({ ...hotspot })
    })
  }

  function setPanel(panel, direction = 1) {
    focusView.active = false
    const view = panel.view
    const distanceScale = compact ? 1.36 : 1
    rigTarget.azimuth = view.azimuth
    rigTarget.elevation = view.elevation + (compact ? 0.1 : 0)
    rigTarget.distance = view.distance * distanceScale
    rigTarget.target.set(0, view.targetY - (compact ? 2.35 : 0), 0)
    rigTarget.lookOffset = compact ? 0 : (view.lookOffset ?? 5.2)
    emphasisTarget.sensing = panel.scene?.sensing ?? 0
    emphasisTarget.control = panel.scene?.control ?? 0
    emphasisTarget.chain = panel.scene?.chain ?? 0
    emphasisTarget.trace = panel.scene?.trace ?? 0
    emphasisTarget.ghost = panel.scene?.ghost ?? 0
    swing += direction * 0.34
    swingVelocity += direction * 1.5
    punch = -1.7
  }

  function setLoginComposition() {
    focusView.active = false
    rig.azimuth = -0.72
    rig.elevation = 0.3
    rig.distance = compact ? 31 : 24.5
    rig.lookOffset = compact ? -2.6 : -8.4
    rig.target.set(0, compact ? -2.2 : 0, 0)
    rigTarget.azimuth = rig.azimuth
    rigTarget.elevation = rig.elevation
    rigTarget.distance = rig.distance
    rigTarget.lookOffset = rig.lookOffset
    rigTarget.target.copy(rig.target)
    swing = 0
    swingVelocity = 0
    punch = 0
  }

  function snapToPanel(panel) {
    focusView.active = false
    setPanel(panel, 0)
    rig.azimuth = rigTarget.azimuth
    rig.elevation = rigTarget.elevation
    rig.distance = rigTarget.distance
    rig.lookOffset = rigTarget.lookOffset
    rig.target.copy(rigTarget.target)
    swing = 0
    swingVelocity = 0
    punch = 0
  }

  function focus(hotspot) {
    if (!hotspot?.position) return
    focusView.active = true
    focusView.position.fromArray(hotspot.position)
    focusView.azimuth = Math.atan2(focusView.position.x, focusView.position.z)
    const heightFactor = THREE.MathUtils.clamp(focusView.position.y / 6, 0, 1)
    focusView.elevation = 0.16 + heightFactor * 0.2
    rigTarget.azimuth = focusView.azimuth
    rigTarget.elevation = focusView.elevation
    rigTarget.distance = focusView.position.length() > 12 ? FOCUS_DISTANCE + 3.2 : FOCUS_DISTANCE
    rigTarget.target.copy(focusView.position)
    rigTarget.lookOffset = compact ? 0 : -3.4
  }

  function clearFocus(panel) {
    focusView.active = false
    if (panel) setPanel(panel, 0)
  }

  function updateRig(delta) {
    swingVelocity += -swing * 26 * delta
    swingVelocity *= Math.exp(-3.4 * delta)
    swing += swingVelocity * delta
    punch = damp(punch, 0, 2.6, delta)

    rig.azimuth = damp(rig.azimuth, rigTarget.azimuth, focusView.active ? 2.4 : 3.1, delta)
    rig.elevation = damp(rig.elevation, rigTarget.elevation, 3, delta)
    rig.distance = damp(rig.distance, rigTarget.distance, 2.6, delta)
    rig.target.lerp(rigTarget.target, 1 - Math.exp(-3.2 * delta))
    rig.lookOffset = damp(rig.lookOffset, rigTarget.lookOffset, 3.4, delta)

    if (prefersReducedMotion()) {
      parallax.targetX = 0
      parallax.targetY = 0
    }
    parallax.x = damp(parallax.x, parallax.targetX, 1.6, delta)
    parallax.y = damp(parallax.y, parallax.targetY, 1.6, delta)
    const parallaxAmount = compact ? 0.03 : 0.05

    const azimuth = rig.azimuth + swing + parallax.x * parallaxAmount
    const elevation = rig.elevation - parallax.y * parallaxAmount * 0.6
    const distance = Math.max(6, rig.distance + punch)
    const horizontal = Math.cos(elevation) * distance
    cameraPosition.set(
      rig.target.x + Math.sin(azimuth) * horizontal,
      rig.target.y + Math.sin(elevation) * distance,
      rig.target.z + Math.cos(azimuth) * horizontal,
    )
    forward.subVectors(rig.target, cameraPosition).normalize()
    right.crossVectors(forward, WORLD_UP).normalize()
    lookPoint.copy(rig.target).addScaledVector(right, -rig.lookOffset)
    camera.position.copy(cameraPosition)
    camera.lookAt(lookPoint)
    camera.updateMatrixWorld()
  }

  function projectHotspots() {
    if (projected.length !== hotspots.length) {
      projected.length = 0
      hotspots.forEach((item) => projected.push({ id: item.id, x: 0, y: 0, scale: 1, visible: false }))
    }
    depthRef.set(0, rig.target.y, 0).project(camera)
    hotspots.forEach((item, index) => {
      const entry = projected[index]
      anchor.fromArray(item.position)
      pivot.copy(anchor).project(camera)
      const visible = pivot.z < 1 && pivot.z > -1
      const insideFootprint = Math.abs(anchor.x) < FOOTPRINT.x && Math.abs(anchor.z) < FOOTPRINT.z
      const depthDelta = pivot.z - depthRef.z
      const occluded = insideFootprint && depthDelta > 0.035
      entry.id = item.id
      entry.x = (pivot.x * 0.5 + 0.5) * width
      entry.y = (-pivot.y * 0.5 + 0.5) * height
      entry.scale = THREE.MathUtils.clamp(1.16 - pivot.z * 0.34, 0.72, 1.2)
      entry.visible = visible && !occluded
    })
    onProject(projected, { width, height, focused: focusView.active })
  }

  function updateEmphasis(delta) {
    emphasis.sensing = damp(emphasis.sensing, emphasisTarget.sensing, 2.4, delta)
    emphasis.control = damp(emphasis.control, emphasisTarget.control, 2.4, delta)
    emphasis.chain = damp(emphasis.chain, emphasisTarget.chain, 2, delta)
    emphasis.trace = damp(emphasis.trace, emphasisTarget.trace, 2, delta)
    emphasis.ghost = damp(emphasis.ghost, emphasisTarget.ghost, 2.4, delta)
    Object.assign(model.state, emphasis)
  }

  function frame() {
    frameId = window.requestAnimationFrame(frame)
    if (!running) return
    if (!isAppCoverVisible()) return
    const delta = Math.min(clock.getDelta(), 0.05)
    monitorPerformance(delta)
    updateEmphasis(delta)
    model.update(delta)
    updateRig(delta)
    glowSprite.material.opacity = 0.5 + emphasis.chain * 0.12 + emphasis.sensing * 0.08
    if (
      !document.body.classList.contains('is-login-embed')
      && !document.body.classList.contains('is-home-revealing')
    ) {
      const now = performance.now()
      const projectionInterval = performanceMode === 'low' ? 140 : 80
      if (now - lastProjectionAt >= projectionInterval) {
        lastProjectionAt = now
        projectHotspots()
      }
    }
    if (composer) composer.render()
    else renderer.render(scene, camera)
  }

  function monitorPerformance(delta) {
    if (qualityLocked || performanceMode === 'home') return
    if (
      !document.body.classList.contains('is-login-embed')
      && !document.body.classList.contains('is-cover-pending')
    ) {
      return
    }
    slowFrameCount = delta > 0.024
      ? slowFrameCount + 1
      : Math.max(0, slowFrameCount - 1)
    if (slowFrameCount >= 36) {
      qualityLocked = true
      setPerformanceMode('low')
    }
  }

  function handleVisibility() {
    running = document.visibilityState === 'visible'
    if (running) clock.getDelta()
  }

  resize()
  if (usePostProcessing()) buildComposer()
  window.addEventListener('resize', resize)
  window.addEventListener('pointermove', handlePointerMove, { passive: true })
  window.addEventListener('pointerleave', handlePointerLeave)
  document.addEventListener('visibilitychange', handleVisibility)
  const coverVisibilityObserver = appCoverElement
    ? new MutationObserver(() => {
        if (isAppCoverVisible()) {
          window.requestAnimationFrame(resize)
        }
      })
    : null
  if (coverVisibilityObserver) {
    coverVisibilityObserver.observe(appCoverElement, {
      attributes: true,
      attributeFilter: ['class', 'style'],
    })
  }
  frame()

  return {
    setHotspots,
    setPanel,
    snapToPanel,
    setPerformanceMode,
    setLoginComposition,
    focus,
    clearFocus,
    resize,
    dispose() {
      window.cancelAnimationFrame(frameId)
      coverVisibilityObserver?.disconnect()
      window.removeEventListener('resize', resize)
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerleave', handlePointerLeave)
      document.removeEventListener('visibilitychange', handleVisibility)
      scene.traverse((item) => {
        if (item.geometry) item.geometry.dispose()
        const materials = Array.isArray(item.material) ? item.material : [item.material]
        materials.filter(Boolean).forEach((material) => {
          if (material.map) material.map.dispose()
          material.dispose()
        })
      })
      environment.texture.dispose()
      pmrem.dispose()
      composer?.dispose()
      renderer.dispose()
    },
  }
}

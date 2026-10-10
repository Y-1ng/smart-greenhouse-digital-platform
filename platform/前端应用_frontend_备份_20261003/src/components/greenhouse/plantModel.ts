import * as THREE from 'three'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'

// 替换 public/models 下的同名 GLB 即可整体更新温室植株外观。
const DEFAULT_PLANT_MODEL_URL = `${import.meta.env.BASE_URL}models/植物_花株.glb`

export interface PlantInstanceOptions {
  targetHeight?: number
  bloomColor?: THREE.ColorRepresentation
  stemColor?: THREE.ColorRepresentation
  rotationY?: number
  lean?: number
}

export interface PlantBatchInstance extends PlantInstanceOptions {
  position: [number, number, number]
}

export interface PlantBatchHandle {
  group: THREE.Group
  update: (elapsed: number) => void
}

const plantTemplateCache = new Map<
  string,
  Promise<THREE.Object3D | null>
>()

function createLeafGeometry() {
  const shape = new THREE.Shape()
  shape.moveTo(0, 0)
  shape.bezierCurveTo(0.055, 0.035, 0.09, 0.11, 0, 0.19)
  shape.bezierCurveTo(-0.09, 0.11, -0.055, 0.035, 0, 0)
  return new THREE.ShapeGeometry(shape, 8)
}

function createFallbackPlant(options: PlantInstanceOptions) {
  const plant = new THREE.Group()
  const targetHeight = options.targetHeight ?? 0.52
  const stemColor = options.stemColor ?? 0x4f8258
  const bloomColor = options.bloomColor ?? 0xe8ca72
  const stemMaterial = new THREE.MeshStandardMaterial({
    color: stemColor,
    roughness: 0.9,
  })
  const leafMaterial = new THREE.MeshStandardMaterial({
    color: new THREE.Color(stemColor).offsetHSL(0.015, 0.04, 0.08),
    roughness: 0.86,
    side: THREE.DoubleSide,
  })
  const bloomMaterial = new THREE.MeshStandardMaterial({
    color: bloomColor,
    roughness: 0.76,
    side: THREE.DoubleSide,
  })

  const stemHeight = targetHeight * 0.72
  const stem = new THREE.Mesh(
    new THREE.CylinderGeometry(
      targetHeight * 0.018,
      targetHeight * 0.026,
      stemHeight,
      7,
    ),
    stemMaterial,
  )
  stem.position.y = stemHeight / 2
  stem.castShadow = true
  plant.add(stem)

  const leafGeometry = createLeafGeometry()
  const leafScale = targetHeight * 1.08
  const leafAngles = [0.25, 1.42, 2.58, 3.74, 4.9]
  leafAngles.forEach((angle, index) => {
    const leaf = new THREE.Mesh(leafGeometry, leafMaterial)
    leaf.position.y = targetHeight * (0.2 + index * 0.095)
    leaf.rotation.y = angle
    leaf.rotation.z = index % 2 === 0 ? 0.72 : -0.72
    leaf.scale.setScalar(leafScale * (0.8 + (index % 3) * 0.08))
    leaf.castShadow = true
    plant.add(leaf)
  })

  const bloomGeometry = new THREE.CircleGeometry(targetHeight * 0.12, 9)
  const bloom = new THREE.Mesh(bloomGeometry, bloomMaterial)
  bloom.position.y = targetHeight * 0.88
  bloom.rotation.x = -Math.PI / 2
  bloom.castShadow = true
  plant.add(bloom)

  return plant
}

export function loadPlantModel(url = DEFAULT_PLANT_MODEL_URL) {
  const cached = plantTemplateCache.get(url)
  if (cached) {
    return cached
  }

  const pending = new Promise<THREE.Object3D | null>((resolve) => {
    new GLTFLoader().load(
      url,
      (gltf) => {
        gltf.scene.traverse((object) => {
          if (!(object instanceof THREE.Mesh)) {
            return
          }
          object.castShadow = true
          object.receiveShadow = true
        })
        resolve(gltf.scene)
      },
      undefined,
      (error) => {
        console.warn('GLTF 植株模型加载失败，已降级为程序化模型', error)
        resolve(null)
      },
    )
  })
  plantTemplateCache.set(url, pending)
  return pending
}

export function createPlantInstance(
  template: THREE.Object3D | null,
  options: PlantInstanceOptions = {},
) {
  if (!template) {
    return createFallbackPlant(options)
  }

  const targetHeight = options.targetHeight ?? 0.52
  const model = template.clone(true)
  model.rotation.y = options.rotationY ?? 0
  model.rotation.z = options.lean ?? 0
  model.updateMatrixWorld(true)

  let bounds = new THREE.Box3().setFromObject(model)
  const modelHeight = Math.max(bounds.max.y - bounds.min.y, 0.001)
  model.scale.multiplyScalar(targetHeight / modelHeight)
  model.updateMatrixWorld(true)
  bounds = new THREE.Box3().setFromObject(model)
  model.position.y -= bounds.min.y

  model.traverse((object) => {
    if (!(object instanceof THREE.Mesh)) {
      return
    }

    object.castShadow = true
    object.receiveShadow = true
    const sourceMaterials = Array.isArray(object.material)
      ? object.material
      : [object.material]
    const materials = sourceMaterials.map((sourceMaterial) => {
      const material = sourceMaterial.clone()
      if (!(material instanceof THREE.MeshStandardMaterial)) {
        return material
      }

      material.metalness = 0
      material.roughness = 0.86
      const isBlossom =
        object.name.toLowerCase().includes('blossom')
        || material.name.toLowerCase().includes('blossom')
      if (isBlossom) {
        material.color.set(options.bloomColor ?? 0xe8ca72)
        material.roughness = 0.76
      } else {
        material.color.set(options.stemColor ?? 0x4f8258)
      }
      return material
    })

    object.material = Array.isArray(object.material) ? materials : materials[0]
  })

  const instance = new THREE.Group()
  instance.add(model)
  return instance
}

function addWindShader(material: THREE.Material, uniforms: Array<{ value: number }>) {
  const windTime = { value: 0 }
  uniforms.push(windTime)
  material.onBeforeCompile = (shader) => {
    shader.uniforms.uWindTime = windTime
    shader.vertexShader = shader.vertexShader
      .replace(
        '#include <common>',
        `#include <common>
uniform float uWindTime;`,
      )
      .replace(
        '#include <begin_vertex>',
        `#include <begin_vertex>
#ifdef USE_INSTANCING
  float plantPhase = instanceMatrix[3].x * 1.35 + instanceMatrix[3].z * 0.95;
#else
  float plantPhase = 0.0;
#endif
float windWeight = smoothstep(0.02, 0.34, transformed.y);
transformed.x += sin(uWindTime * 1.35 + plantPhase) * 0.014 * windWeight;
transformed.z += cos(uWindTime * 1.05 + plantPhase) * 0.009 * windWeight;`,
      )
  }
  material.customProgramCacheKey = () => 'greenhouse-plant-wind-v1'
}

/**
 * 将同一作物、同一材质植株合并为 InstancedMesh，避免每株单独产生 Draw Call。
 */
export function createPlantBatch(
  template: THREE.Object3D | null,
  instances: PlantBatchInstance[],
): PlantBatchHandle {
  const group = new THREE.Group()
  if (!template || instances.length === 0) {
    instances.forEach((instance) => {
      const plant = createFallbackPlant(instance)
      plant.position.set(...instance.position)
      group.add(plant)
    })
    return { group, update: () => undefined }
  }

  template.updateMatrixWorld(true)
  const templateBounds = new THREE.Box3().setFromObject(template)
  const baseMinY = templateBounds.min.y
  const baseHeight = Math.max(templateBounds.max.y - baseMinY, 0.001)
  const parts: Array<{
    geometry: THREE.BufferGeometry
    material: THREE.Material
    isBlossom: boolean
  }> = []

  template.traverse((object) => {
    if (!(object instanceof THREE.Mesh)) {
      return
    }
    const geometry = object.geometry.clone()
    geometry.applyMatrix4(object.matrixWorld)
    geometry.translate(0, -baseMinY, 0)
    geometry.computeBoundingSphere()
    const materialName = Array.isArray(object.material)
      ? object.material[0]?.name ?? ''
      : object.material.name
    parts.push({
      geometry,
      material: object.material,
      isBlossom:
        object.name.toLowerCase().includes('blossom')
        || materialName.toLowerCase().includes('blossom'),
    })
  })

  const windUniforms: Array<{ value: number }> = []
  const matrix = new THREE.Matrix4()
  const quaternion = new THREE.Quaternion()
  const scale = new THREE.Vector3()
  const position = new THREE.Vector3()
  const euler = new THREE.Euler()

  parts.forEach((part) => {
    const material = part.material.clone()
    if (material instanceof THREE.MeshStandardMaterial) {
      material.metalness = 0
      material.roughness = part.isBlossom ? 0.76 : 0.88
      material.color.set(part.isBlossom ? 0xffffff : 0x4f8258)
    }
    addWindShader(material, windUniforms)

    const mesh = new THREE.InstancedMesh(
      part.geometry,
      material,
      instances.length,
    )
    mesh.castShadow = true
    mesh.receiveShadow = true
    instances.forEach((instance, index) => {
      const heightScale = (instance.targetHeight ?? 0.52) / baseHeight
      euler.set(0, instance.rotationY ?? 0, instance.lean ?? 0)
      quaternion.setFromEuler(euler)
      position.set(...instance.position)
      scale.setScalar(heightScale)
      matrix.compose(position, quaternion, scale)
      mesh.setMatrixAt(index, matrix)
      if (part.isBlossom) {
        mesh.setColorAt(
          index,
          new THREE.Color(instance.bloomColor ?? 0xe8ca72),
        )
      }
    })
    mesh.instanceMatrix.needsUpdate = true
    if (mesh.instanceColor) {
      mesh.instanceColor.needsUpdate = true
    }
    mesh.computeBoundingSphere()
    group.add(mesh)
  })

  return {
    group,
    update: (elapsed) => {
      windUniforms.forEach((uniform) => {
        uniform.value = elapsed
      })
    },
  }
}

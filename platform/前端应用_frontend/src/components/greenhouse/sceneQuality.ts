export interface SceneQualityProfile {
  lowPower: boolean
  maxPixelRatio: number
  antialias: boolean
  enableShadows: boolean
  shadowMapSize: number
  overlayFps: number
  reduceMotion: boolean
}

export function getSceneQualityProfile(): SceneQualityProfile {
  const reducedMotion = window.matchMedia(
    '(prefers-reduced-motion: reduce)',
  ).matches
  const cores = navigator.hardwareConcurrency ?? 8
  const memory = (
    navigator as Navigator & { deviceMemory?: number }
  ).deviceMemory ?? 8
  const lowPower =
    window.innerWidth <= 820 || cores <= 4 || memory <= 4

  return {
    lowPower,
    maxPixelRatio: lowPower
      ? Math.min(window.devicePixelRatio, 1.35)
      : Math.min(window.devicePixelRatio, 2),
    antialias: !lowPower,
    enableShadows: !lowPower,
    shadowMapSize: lowPower ? 1024 : 2048,
    overlayFps: lowPower ? 16 : 30,
    reduceMotion: reducedMotion,
  }
}

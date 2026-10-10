export interface CropPlantProfile {
  baseHeight: number
  bloomColors: number[]
  stemColorLight: number
  stemColorDark: number
}

const DEFAULT_PROFILE: CropPlantProfile = {
  baseHeight: 0.46,
  bloomColors: [0xe4c66f, 0xf0dfaa, 0xdda05d],
  stemColorLight: 0x4f7d52,
  stemColorDark: 0x67a66c,
}

export function getCropPlantProfile(crop = ''): CropPlantProfile {
  if (crop.includes('番茄')) {
    return {
      baseHeight: 0.52,
      bloomColors: [0xe0b45d, 0xefd78b, 0xd89b49],
      stemColorLight: 0x4f7d52,
      stemColorDark: 0x67a66c,
    }
  }
  if (crop.includes('草莓')) {
    return {
      baseHeight: 0.34,
      bloomColors: [0xf1e8df, 0xffffff, 0xf5dfd4],
      stemColorLight: 0x5b8756,
      stemColorDark: 0x76a96c,
    }
  }
  if (crop.includes('黄瓜')) {
    return {
      baseHeight: 0.56,
      bloomColors: [0xe8ce68, 0xf3df8a, 0xd8b94e],
      stemColorLight: 0x4b8355,
      stemColorDark: 0x67a66c,
    }
  }
  if (crop.includes('辣椒')) {
    return {
      baseHeight: 0.45,
      bloomColors: [0xf0ead1, 0xffffff, 0xe9dfb9],
      stemColorLight: 0x56865a,
      stemColorDark: 0x72a96e,
    }
  }
  if (crop.includes('生菜')) {
    return {
      baseHeight: 0.28,
      bloomColors: [0xcfe29b, 0xdce9ad, 0xbdd58b],
      stemColorLight: 0x69965f,
      stemColorDark: 0x82ad72,
    }
  }
  return DEFAULT_PROFILE
}

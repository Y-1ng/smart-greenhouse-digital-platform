import type { Component } from 'vue'

export type SceneMode = 'overview' | 'environment' | 'control'

export type HotspotCategory = 'sensor' | 'actuator'

export type StatusTone = 'ok' | 'warning' | 'offline' | 'running'

export interface GreenhouseHotspot {
  id: string
  label: string
  category: HotspotCategory
  position: [number, number, number]
  value: string
  unit: string
  status: string
  tone: StatusTone
  description: string
  icon: Component
}

export interface GreenhouseDevice {
  id: string
  name: string
  location: string
  value: string
  status: string
  tone: StatusTone
  active: boolean
}

export interface GreenhouseSite {
  id: string
  code: string
  name: string
  crop: string
  batch: string
  area: string
  location?: string
  greenhouseId?: number
  batchId?: number
  plantingDate?: string
  expectedHarvestDate?: string
  metric: string
  status: string
  tone: StatusTone
  position: [number, number, number]
}

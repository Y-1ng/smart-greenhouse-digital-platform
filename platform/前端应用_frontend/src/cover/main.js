import { PANELS } from './panels.js'
import { createCoverScene } from './scene/coverScene.js'

const params = new URLSearchParams(window.location.search)
const appCover = document.querySelector('[data-app-cover]')
const isAppCover = Boolean(appCover)
const embedMode = isAppCover ? 'shared' : params.get('embed')
const sharedMode = embedMode === 'shared'

function setCoverClass(name, enabled = true) {
  document.body.classList.toggle(name, enabled)
  appCover?.classList.toggle(name, enabled)
}

if (embedMode === 'login' || sharedMode) {
  setCoverClass('is-login-embed')
}
if (sharedMode) {
  setCoverClass('is-cover-pending')
}

const cover = document.querySelector('#cover')
const canvas = document.querySelector('#scene')
const deck = document.querySelector('#deck')
const topnav = document.querySelector('#topnav')
const rail = document.querySelector('#rail')
const dots = document.querySelector('#dots')
const hotspotLayer = document.querySelector('#hotspots')
const conceptCard = document.querySelector('#concept')
const sweep = document.querySelector('#sweep')
const dockIndex = document.querySelector('#dockIndex')
const dockTag = document.querySelector('#dockTag')

const state = {
  index: 0,
  locked: false,
  focusId: null,
  hotspots: new Map(),
  wheelDelta: 0,
}

const iconArrowLeft = `
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="m12 19-7-7 7-7" /><path d="M19 12H5" />
  </svg>`

function buildPanels() {
  deck.innerHTML = PANELS.map((panel, index) => {
    const points = panel.points
      .map((point) => `<li class="reveal" style="--d:${4 + panel.points.indexOf(point)}">${point}</li>`)
      .join('')
    const stats = panel.stats
      .map(
        (stat, statIndex) => `
          <div class="panel__stat reveal" style="--d:${7 + statIndex}">
            <strong>${stat.value}</strong>
            <span>${stat.label}</span>
          </div>`,
      )
      .join('')
    return `
      <article class="panel" data-index="${index}" ${index === 0 ? '' : 'aria-hidden="true"'}>
        <div class="panel__copy">
          <p class="panel__eyebrow reveal" style="--d:0">
            <span>${panel.index}</span>
            <b>${panel.category}</b>
            <i>${panel.categoryEn}</i>
          </p>
          <h1 class="panel__title">
            <span class="reveal" style="--d:1">${panel.title[0]}</span>
            <span class="reveal" style="--d:2">${panel.title[1]}</span>
          </h1>
          <p class="panel__desc reveal" style="--d:3">${panel.desc}</p>
          <ul class="panel__points">${points}</ul>
          <div class="panel__stats">${stats}</div>
          <p class="panel__tag reveal" style="--d:10"><i></i>${panel.tag}</p>
        </div>
      </article>`
  }).join('')
}

function buildNav() {
  topnav.innerHTML = PANELS.map(
    (panel, index) => `
      <button type="button" data-index="${index}" ${index === 0 ? 'class="is-active"' : ''}>
        <span>${panel.category}</span>
      </button>`,
  ).join('')

  rail.innerHTML = PANELS.map(
    (panel, index) => `
      <li data-index="${index}" ${index === 0 ? 'class="is-active"' : ''}>
        <span>${panel.index}</span><i></i>
      </li>`,
  ).join('')

  dots.innerHTML = PANELS.map(
    (panel, index) => `
      <button
        class="dot${index === 0 ? ' is-active' : ''}"
        type="button"
        role="tab"
        data-index="${index}"
        aria-selected="${index === 0}"
        aria-label="${panel.index} ${panel.category}"
      ></button>`,
  ).join('')
}

function buildHotspots() {
  PANELS.forEach((panel, panelIndex) => {
    panel.hotspots.forEach((hotspot) => {
      const element = document.createElement('button')
      element.type = 'button'
      element.className = 'hotspot'
      element.dataset.panel = String(panelIndex)
      element.dataset.id = hotspot.id
      element.innerHTML = `
        <span class="hotspot__node"><i></i><b></b></span>
        <span class="hotspot__label">
          <strong>${hotspot.label}</strong>
          <em>${hotspot.value}</em>
        </span>`
      element.addEventListener('click', (event) => {
        event.stopPropagation()
        focusHotspot(hotspot)
      })
      hotspotLayer.append(element)
      state.hotspots.set(`${panelIndex}:${hotspot.id}`, element)
    })
  })
}

function setActiveStates() {
  const panel = PANELS[state.index]
  cover.style.setProperty('--accent', panel.accent)
  cover.style.setProperty('--accent-rgb', hexToRgb(panel.accent))
  cover.style.setProperty('--sweep-rgb', hexToRgb(panel.accent))
  dockIndex.textContent = panel.index
  dockTag.textContent = panel.tag
  ;[...deck.children].forEach((element, index) => {
    element.classList.toggle('is-active', index === state.index)
    element.toggleAttribute('aria-hidden', index !== state.index)
  })
  topnav.querySelectorAll('button').forEach((button, index) => {
    button.classList.toggle('is-active', index === state.index)
  })
  rail.querySelectorAll('li').forEach((item, index) => {
    item.classList.toggle('is-active', index === state.index)
  })
  dots.querySelectorAll('.dot').forEach((dot, index) => {
    dot.classList.toggle('is-active', index === state.index)
    dot.setAttribute('aria-selected', String(index === state.index))
  })
}

function hexToRgb(hex) {
  const value = Number.parseInt(hex.replace('#', ''), 16)
  return `${(value >> 16) & 255} ${(value >> 8) & 255} ${value & 255}`
}

function runSweep(direction) {
  sweep.classList.toggle('is-reverse', direction < 0)
  sweep.classList.remove('is-running')
  void sweep.offsetWidth
  sweep.classList.add('is-running')
}

function showHotspots(panelIndex) {
  state.hotspots.forEach((element) => {
    const active = Number(element.dataset.panel) === panelIndex
    element.classList.toggle('is-visible', active && !state.focusId)
    element.classList.remove('is-active')
  })
}

function revealHomeAfterLogin() {
  setCoverClass('is-home-revealing')
  window.requestAnimationFrame(() => {
    setCoverClass('is-home-chrome-revealed')
    window.setTimeout(() => {
      setCoverClass('is-home-content-revealed')
      window.setTimeout(() => {
        setCoverClass('is-login-embed', false)
        setCoverClass('is-home-revealing', false)
        setCoverClass('is-home-chrome-revealed', false)
        setCoverClass('is-home-content-revealed', false)
      }, 120)
    }, 90)
  })
}

function goTo(index, direction = 1) {
  const total = PANELS.length
  const next = (index + total) % total
  if (next === state.index || state.locked) return
  state.locked = true
  window.setTimeout(() => {
    state.locked = false
  }, 780)

  closeFocus()
  state.index = next
  const panel = PANELS[next]
  scene.setHotspots([])
  showHotspots(-1)
  setActiveStates()
  runSweep(direction)
  scene.setPanel(panel, direction)
  window.setTimeout(() => {
    if (state.index !== next) return
    scene.setHotspots(panel.hotspots)
    showHotspots(next)
  }, 380)
}

function focusHotspot(hotspot) {
  state.focusId = hotspot.id
  hotspotLayer.classList.add('is-focused')
  state.hotspots.forEach((element) => {
    element.classList.toggle('is-active', element.dataset.id === hotspot.id)
  })
  scene.focus(hotspot)
  renderConcept(hotspot)
}

function closeFocus() {
  if (!state.focusId) return
  state.focusId = null
  hotspotLayer.classList.remove('is-focused')
  state.hotspots.forEach((element) => element.classList.remove('is-active'))
  conceptCard.hidden = true
  conceptCard.innerHTML = ''
  scene.clearFocus(PANELS[state.index])
  window.setTimeout(() => {
    if (!state.focusId) showHotspots(state.index)
  }, 260)
}

function clearFocusOnly() {
  if (!state.focusId) return
  state.focusId = null
  hotspotLayer.classList.remove('is-focused')
  state.hotspots.forEach((element) => element.classList.remove('is-active'))
  conceptCard.hidden = true
  conceptCard.innerHTML = ''
  scene.clearFocus(PANELS[state.index])
  showHotspots(state.index)
}

function renderConcept(hotspot) {
  const focus = hotspot.focus
  conceptCard.hidden = false
  conceptCard.innerHTML = `
    <button class="concept__close" type="button" aria-label="返回全景">
      ${iconArrowLeft}
      <span>返回全景</span>
    </button>
    <p class="concept__eyebrow"><i></i>${focus.concept}</p>
    <h2>${focus.title}</h2>
    <p class="concept__desc">${focus.desc}</p>
    <ul class="concept__points">
      ${focus.points.map((point) => `<li>${point}</li>`).join('')}
    </ul>
    <div class="concept__metric">
      <span>${focus.metric.label}</span>
      <strong>${focus.metric.value}</strong>
    </div>`
  conceptCard.querySelector('.concept__close').addEventListener('click', (event) => {
    event.stopPropagation()
    clearFocusOnly()
  })
}

function handleWheel(event) {
  event.preventDefault()
  if (state.focusId) {
    if (Math.abs(event.deltaY) > 18) clearFocusOnly()
    return
  }
  state.wheelDelta += event.deltaY + event.deltaX * 0.6
  if (Math.abs(state.wheelDelta) < 26 || state.locked) return
  const direction = state.wheelDelta > 0 ? 1 : -1
  state.wheelDelta = 0
  goTo(state.index + direction, direction)
}

function handleKey(event) {
  const key = event.key
  if (key === 'Escape') {
    clearFocusOnly()
    return
  }
  if (['ArrowRight', 'ArrowDown', 'PageDown', ' '].includes(key)) {
    event.preventDefault()
    if (state.focusId) {
      clearFocusOnly()
      return
    }
    goTo(state.index + 1, 1)
  } else if (['ArrowLeft', 'ArrowUp', 'PageUp'].includes(key)) {
    event.preventDefault()
    if (state.focusId) {
      clearFocusOnly()
      return
    }
    goTo(state.index - 1, -1)
  } else if (key === 'Home') {
    event.preventDefault()
    goTo(0, -1)
  } else if (key === 'End') {
    event.preventDefault()
    goTo(PANELS.length - 1, 1)
  }
}

let touchStart = null

function handleTouchStart(event) {
  const touch = event.changedTouches[0]
  touchStart = { x: touch.clientX, y: touch.clientY }
}

function handleTouchEnd(event) {
  if (!touchStart) return
  const touch = event.changedTouches[0]
  const dx = touch.clientX - touchStart.x
  const dy = touch.clientY - touchStart.y
  touchStart = null
  if (Math.abs(dx) < 44 && Math.abs(dy) < 44) return
  if (state.focusId) {
    clearFocusOnly()
    return
  }
  const horizontal = Math.abs(dx) > Math.abs(dy)
  const direction = horizontal ? (dx < 0 ? 1 : -1) : dy < 0 ? 1 : -1
  goTo(state.index + direction, direction)
}

function handleCoverClick(event) {
  if (event.target.closest('button, a, .concept')) return
  const bounds = cover.getBoundingClientRect()
  const ratio = (event.clientX - bounds.left) / bounds.width
  if (state.focusId) {
    clearFocusOnly()
    return
  }
  if (ratio > 0.45) goTo(state.index + 1, 1)
  else goTo(state.index - 1, -1)
}

function onProject(list) {
  const offset = 13
  list.forEach((entry) => {
    const element = state.hotspots.get(`${state.index}:${entry.id}`)
    if (!element) return
    const visible = entry.visible && !state.focusId
    element.classList.toggle('is-visible', visible)
    if (!visible) return
    element.style.transform = `translate3d(${entry.x - offset}px, ${entry.y - offset}px, 0)`
    element.style.setProperty('--depth-scale', entry.scale.toFixed(3))
  })
}

function bindEvents() {
  cover.addEventListener('wheel', handleWheel, { passive: false })
  cover.addEventListener('click', handleCoverClick)
  cover.addEventListener('mouseenter', () => {
    if (!document.body.classList.contains('is-login-embed')) {
      cover.focus({ preventScroll: true })
    }
  }, { once: true })
  cover.addEventListener('pointerdown', () => {
    if (!document.body.classList.contains('is-login-embed')) {
      cover.focus({ preventScroll: true })
    }
  }, { once: true })
  window.addEventListener('keydown', handleKey)
  cover.addEventListener('touchstart', handleTouchStart, { passive: true })
  cover.addEventListener('touchend', handleTouchEnd, { passive: true })
  topnav.querySelectorAll('button').forEach((button) => {
    button.addEventListener('click', () => {
      const index = Number(button.dataset.index)
      goTo(index, index > state.index ? 1 : -1)
    })
  })
  rail.querySelectorAll('li').forEach((item) => {
    item.addEventListener('click', () => {
      const index = Number(item.dataset.index)
      goTo(index, index > state.index ? 1 : -1)
    })
  })
  dots.querySelectorAll('.dot').forEach((dot) => {
    dot.addEventListener('click', () => {
      const index = Number(dot.dataset.index)
      goTo(index, index > state.index ? 1 : -1)
    })
  })
  sweep.addEventListener('animationend', () => sweep.classList.remove('is-running'))
}

buildPanels()
buildNav()
buildHotspots()
setActiveStates()
bindEvents()

const scene = createCoverScene(canvas, {
  onProject,
  performanceMode: sharedMode ? 'shared' : embedMode === 'login' ? 'login' : 'home',
})
scene.setPanel(PANELS[0], 1)
if (embedMode === 'login' || sharedMode) {
  scene.setLoginComposition()
}
scene.setHotspots(PANELS[0].hotspots)
showHotspots(0)

function handleCoverCommand(data) {
  if (data?.type === 'greenhouse-cover-mode') {
    const mode = data.mode
    if (mode === 'login') {
      setCoverClass('is-login-embed')
      scene.setLoginComposition()
    } else if (data.arrival) {
      setCoverClass('is-arriving-from-login')
      setCoverClass('is-login-embed')
      revealHomeAfterLogin()
      scene.snapToPanel(PANELS[0])
      window.setTimeout(() => cover.focus({ preventScroll: true }), 260)
    } else {
      setCoverClass('is-login-embed', false)
      setCoverClass('is-arriving-from-login', false)
      scene.setPanel(PANELS[0], 1)
      window.setTimeout(() => cover.focus({ preventScroll: true }), 80)
    }
    setCoverClass('is-cover-pending', false)
  }
}

if (embedMode === 'login' || sharedMode) {
  window.addEventListener('message', (event) => {
    if (event.origin !== window.location.origin) return
    handleCoverCommand(event.data)
  })
  window.addEventListener('greenhouse-cover-mode', (event) => {
    handleCoverCommand(event.detail)
  })
}

window.requestAnimationFrame(() => {
  window.requestAnimationFrame(() => {
    if (embedMode === 'login' || sharedMode) {
      if (isAppCover) {
        window.dispatchEvent(new CustomEvent('greenhouse-cover-ready'))
      } else {
        window.parent.postMessage(
          { type: 'greenhouse-cover-ready' },
          window.location.origin,
        )
      }
    }
    window.setTimeout(() => cover.classList.add('is-booted'), 80)
  })
})

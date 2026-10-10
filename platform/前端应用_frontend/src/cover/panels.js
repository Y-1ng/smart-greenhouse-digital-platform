// 五个章节共用一条 72° 递进的环绕机位，滚轮切换时温室模型会连续转一整圈。
export const VIEWS = [
  { azimuth: 2.55, elevation: 0.3, distance: 29, targetY: 1.9, lookOffset: 4.6 },
  { azimuth: 3.8, elevation: 0.4, distance: 26.5, targetY: 2.1, lookOffset: 4.6 },
  { azimuth: 5.05, elevation: 0.26, distance: 26, targetY: 1.7, lookOffset: 5 },
  { azimuth: 6.3, elevation: 0.34, distance: 28.5, targetY: 2.3, lookOffset: 4.8 },
  { azimuth: 7.55, elevation: 0.3, distance: 27.5, targetY: 2.0, lookOffset: 4.6 },
]

// 场景道具按「第几个章节的镜头」摆位：right 为画面右方，toward 为靠近相机方向。
export const STAGE = {
  chain: { right: 7.4, toward: 4.4, baseY: 1.6, step: 0.86, levels: 6 },
  trace: { right: 6.6, toward: 7.6, height: 2.9 },
}

export function stagePoint(panelIndex, rightOffset, towardOffset, height) {
  const { azimuth } = VIEWS[panelIndex] ?? VIEWS[0]
  const rightX = Math.cos(azimuth)
  const rightZ = -Math.sin(azimuth)
  const towardX = Math.sin(azimuth)
  const towardZ = Math.cos(azimuth)
  return [
    rightX * rightOffset + towardX * towardOffset,
    height,
    rightZ * rightOffset + towardZ * towardOffset,
  ]
}

export function chainPoint(index) {
  const { chain } = STAGE
  const node = stagePoint(3, chain.right, chain.toward, chain.baseY + index * chain.step)
  node[0] += Math.sin(index * 1.1) * 0.36
  node[2] += Math.cos(index * 0.8) * 0.3
  return node
}

export const PANELS = [
  {
    id: 'twin',
    index: '01',
    category: '数字孪生',
    categoryEn: 'DIGITAL TWIN',
    accent: '#4fd1a5',
    title: ['让一座温室', '先在数据里生长'],
    desc: '结构、作物、设备与实时数据在同一个三维空间里对齐。风、光、水、肥的每一次变化，都会在孪生体上同步显形。',
    points: ['结构 / 设备 / 作物统一坐标系', '实时数据驱动状态映射', '机理模型与数据模型双引擎'],
    stats: [
      { value: '1:1', label: '结构还原' },
      { value: '1.8s', label: '数据延迟' },
      { value: '98.6%', label: '同步率' },
    ],
    tag: '概念点题 · 数字孪生体',
    view: VIEWS[0],
    scene: { ghost: 1, sensing: 0.25 },
    hotspots: [
      {
        id: 'twin-shell',
        label: '温室孪生体',
        value: 'GH-001 · 1,280 ㎡',
        position: [0, 5.4, 0],
        focus: {
          concept: '数字孪生',
          title: '结构建模 + 实时映射',
          desc: '三维结构还原温室的跨度、屋面与畦面，测点、设备、作物共用同一套坐标，实时数据直接改变孪生体状态。',
          points: ['1:1 结构与设备建模', '8 类测点持续回传', '孪生状态与现场延迟 1.8 秒'],
          metric: { label: '孪生同步率', value: '98.6%' },
        },
      },
      {
        id: 'twin-sync',
        label: '数据映射层',
        value: '实时 · 周期双通道',
        position: stagePoint(0, 5.6, 4.2, 3.1),
        focus: {
          concept: '实时映射',
          title: '一条数据，两种时间尺度',
          desc: '实时通道驱动孪生体动态，周期通道按固定窗口聚合并生成哈希链，用于后续存证与追溯。',
          points: ['实时通道：状态刷新 1.8 秒', '周期通道：5 分钟聚合一次', '周期数据生成根哈希留痕'],
          metric: { label: '采样点', value: '8 个' },
        },
      },
    ],
  },
  {
    id: 'sensing',
    index: '02',
    category: '环境感知',
    categoryEn: 'SENSING',
    accent: '#7ee0bd',
    title: ['每一度变化', '都在被看见'],
    desc: '空气温湿度、CO₂、光照、土壤墒情统一接入物联网网关。异常在形成风险之前，就已经被周期模型识别。',
    points: ['多源传感器统一接入', '周期采样与哈希链留痕', '阈值判定与趋势预警'],
    stats: [
      { value: '22.6℃', label: '空气温度' },
      { value: '68.4%', label: '空气湿度' },
      { value: '18,500lx', label: '光照强度' },
    ],
    tag: '概念点题 · 物联感知层',
    view: VIEWS[1],
    scene: { sensing: 1, ghost: 0.25 },
    hotspots: [
      {
        id: 'sensor-air',
        label: '空气温湿度',
        value: '22.6 ℃ / 68.4 %',
        position: [-5.4, 2.25, -2.4],
        focus: {
          concept: '环境感知',
          title: '温湿度是通风策略的第一输入',
          desc: '空气温湿度按分钟级采样，同时参与通风、湿帘与加温策略计算，超出适宜区间立即进入阈值判定。',
          points: ['适宜区间 18 - 28 ℃', '湿度阈值 60% - 80%', '越限后 1 个周期内触发判定'],
          metric: { label: '当前状态', value: '适宜' },
        },
      },
      {
        id: 'sensor-light',
        label: '光照传感器',
        value: '18,500 lx',
        position: [1.8, 2.55, 0.4],
        focus: {
          concept: '光照感知',
          title: '低于阈值就自动补光',
          desc: '棚内光照强度低于 12,000 lx 时进入补光判定，结合光照时数决定补光灯组的开启时长。',
          points: ['补光阈值 12,000 lx', '按光照时数累计判定', '联动 A 区补光灯组'],
          metric: { label: '距阈值', value: '+6,500 lx' },
        },
      },
      {
        id: 'sensor-soil',
        label: '土壤墒情',
        value: '19.8 ℃ / 43.2 %',
        position: [-1.8, 1.05, 2.6],
        focus: {
          concept: '根区感知',
          title: '灌溉计划来自根区数据',
          desc: '土壤温度与含水率共同决定灌溉窗口，结合水肥配方生成分区滴灌计划，避免过量灌溉。',
          points: ['含水率下限 38%', '按根区温度调整窗口', '下一次灌溉 15:30'],
          metric: { label: '计划灌量', value: '2.4 m³' },
        },
      },
    ],
  },
  {
    id: 'control',
    index: '03',
    category: '智能控制',
    categoryEn: 'CONTROL',
    accent: '#f0a04b',
    title: ['环境一变', '设备立即响应'],
    desc: '策略命中后自动下发指令，风机、卷帘、补光与滴灌按方案联动。每条指令都有编号、超时与回执状态。',
    points: ['策略命中自动下发', '设备联动闭环回执', '指令超时与人工补偿'],
    stats: [
      { value: '68%', label: '循环风机' },
      { value: '42%', label: '外遮阳开度' },
      { value: '15:30', label: '下次灌溉' },
    ],
    tag: '概念点题 · 执行控制层',
    view: VIEWS[2],
    scene: { control: 1, ghost: 0.15 },
    hotspots: [
      {
        id: 'actuator-fan',
        label: '循环风机',
        value: '运行中 · 68%',
        position: [-3.6, 2.05, -5.05],
        focus: {
          concept: '设备联动',
          title: '温度越限，风机先动',
          desc: '温度高于 28 ℃ 触发通风策略，风机按转速档位执行，并等待设备回执确认后再写入执行记录。',
          points: ['触发条件：温度 > 28 ℃', '执行档位：68% 转速', '回执状态：执行正常'],
          metric: { label: '响应耗时', value: '2.4 s' },
        },
      },
      {
        id: 'actuator-curtain',
        label: '卷帘机',
        value: '已开启 · 42%',
        position: [7.3, 1.7, 1.8],
        focus: {
          concept: '外遮阳',
          title: '光太强，就收一层阳',
          desc: '光照超过上限时开启外遮阳，降低棚内温度与蒸腾强度，开度按光照强度分档控制。',
          points: ['开度 42%', '与风机策略互斥校验', '支持手动远程干预'],
          metric: { label: '当前开度', value: '42%' },
        },
      },
      {
        id: 'actuator-light',
        label: '补光灯组',
        value: '待机 · 0%',
        position: [-4.8, 3.35, -3.4],
        focus: {
          concept: '补光策略',
          title: '按光照时数决定补光',
          desc: 'A 区补光灯组在线待机，当自然光照时数不足时自动补足，避免无效补光带来的能耗。',
          points: ['目标光照时数 12 h', '分区独立控制', '能耗计入周期统计'],
          metric: { label: '今日补光', value: '1.2 h' },
        },
      },
      {
        id: 'actuator-drip',
        label: '滴灌系统',
        value: '下次 15:30 · 2.4 m³',
        position: [1.8, 0.9, 2.6],
        focus: {
          concept: '水肥一体',
          title: '灌溉按计划，也按墒情',
          desc: '滴灌计划结合土壤含水率与作物生育期生成，执行时按分区轮灌并记录实际用水量。',
          points: ['分区轮灌 4 组', '计划灌量 2.4 m³', '执行记录写入周期数据'],
          metric: { label: '今日用水', value: '6.8 m³' },
        },
      },
    ],
  },
  {
    id: 'proof',
    index: '04',
    category: '可信存证',
    categoryEn: 'BLOCKCHAIN',
    accent: '#7fb3e0',
    title: ['上链的不是文件', '是关键摘要'],
    desc: '环境周期按 SHA-256 形成哈希链，只把根哈希写入联盟链。电子合格证与一物一码在链下保留原文、链上完成核验。',
    points: ['周期数据哈希链', '仅根哈希写链', '链上回执可核验'],
    stats: [
      { value: '4 / 4', label: '联盟链节点' },
      { value: '1,284,602', label: '区块高度' },
      { value: 'SHA-256', label: '周期根哈希' },
    ],
    tag: '概念点题 · 联盟链存证',
    view: VIEWS[3],
    scene: { chain: 1, ghost: 0.2 },
    hotspots: [
      {
        id: 'chain-root',
        label: '周期根哈希',
        value: 'SHA-256 · 每 5 分钟',
        position: chainPoint(5),
        focus: {
          concept: '联盟链存证',
          title: '链下存原文，链上存摘要',
          desc: '一个周期内的环境采样先形成哈希链，再对链头取根哈希写入联盟链，链下保留完整原始数据。',
          points: ['周期窗口 5 分钟', '根哈希一次上链', '交易回执保存至业务库'],
          metric: { label: '写入耗时', value: '0.8 s' },
        },
      },
      {
        id: 'chain-node',
        label: '联盟链节点',
        value: '4 / 4 正常',
        position: chainPoint(2),
        focus: {
          concept: '多节点共识',
          title: '四个节点共同确认',
          desc: '存证请求由联盟链多节点共同确认，任一节点异常不影响整体出块，链上回执可用于事后核验。',
          points: ['共识节点 4 个', '当前区块高度 1,284,602', '支持手工补偿重试'],
          metric: { label: '节点状态', value: '4 / 4' },
        },
      },
    ],
  },
  {
    id: 'trace',
    index: '05',
    category: '全链追溯',
    categoryEn: 'TRACEABILITY',
    accent: '#8fd39a',
    title: ['从一株作物', '到一张合格证'],
    desc: '种植、检测、加工、运输与销售节点以前一摘要串联。扫码即可校验签名，并核对链上存证记录。',
    points: ['一物一码绑定批次', '电子合格证验签', '田块指针链防断链'],
    stats: [
      { value: 'CB-2026-001', label: '产品编码' },
      { value: '6 节点', label: '追溯链路' },
      { value: '首次扫码', label: '验签状态' },
    ],
    tag: '概念点题 · 全链条追溯',
    view: VIEWS[4],
    scene: { trace: 1, ghost: 0.3 },
    hotspots: [
      {
        id: 'trace-code',
        label: '一物一码',
        value: 'CB-2026-001',
        position: stagePoint(
          4,
          STAGE.trace.right - 0.6,
          STAGE.trace.toward + 0.9,
          STAGE.trace.height + 1.7,
        ),
        focus: {
          concept: '一物一码',
          title: '一个编码，绑定一条链',
          desc: '产品编码与签名绑定，扫码时同时完成签名校验与链上记录核对，重复扫码会被标记。',
          points: ['编码唯一绑定批次', '扫码即验签', '重复扫码单独留痕'],
          metric: { label: '验签结果', value: '通过' },
        },
      },
      {
        id: 'trace-pointer',
        label: '田块指针链',
        value: '前一摘要串联',
        position: stagePoint(4, 4.2, 2.4, 1.4),
        focus: {
          concept: '指针链',
          title: '断链可以被立即识别',
          desc: '每个追溯节点记录前一节点摘要，任一节点被篡改都会导致指针链校验失败，并定位到具体环节。',
          points: ['种植 → 检测 → 加工 → 运输 → 销售', '节点摘要逐级串联', '断链定位到具体节点'],
          metric: { label: '链路完整性', value: '完整' },
        },
      },
    ],
  },
]

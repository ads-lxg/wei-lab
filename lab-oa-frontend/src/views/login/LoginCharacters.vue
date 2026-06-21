<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps<{
  isTyping?: boolean
  hasPassword?: boolean
  showPassword?: boolean
  isDarkMode?: boolean
  isExcited?: boolean
}>()

const mouseX = ref(0)
const mouseY = ref(0)
const purpleRef = ref<HTMLElement | null>(null)
const blackRef = ref<HTMLElement | null>(null)
const yellowRef = ref<HTMLElement | null>(null)
const orangeRef = ref<HTMLElement | null>(null)
const isPurpleBlinking = ref(false)
const isBlackBlinking = ref(false)
const isLookingAtEachOther = ref(false)
const isPurplePeeking = ref(false)
// 每个角色独立的偷瞄状态
type CharName = 'purple' | 'black' | 'orange' | 'yellow'
const peekingChars = ref<CharName[]>([])
// 每个角色独立的偷瞄风格和眼睛方向
const charPeekStyle = ref<Record<CharName, number>>({ purple: 0, black: 0, orange: 0, yellow: 0 })
const charPeekEyeSide = ref<Record<CharName, 'left' | 'right'>>({ purple: 'left', black: 'left', orange: 'left', yellow: 'left' })
const charPeekPupilX = ref<Record<CharName, number>>({ purple: 0, black: 0, orange: 0, yellow: 0 })
const startledChar = ref<CharName | null>(null)
const passwordPeekChar = ref<CharName | null>(null)
// 兴奋时随机偷看用户的方向（瞳孔偏移）
const excitedLookAtUser = ref(false)

// 律动效果类型：每次进入兴奋状态，每个角色随机选一种
// - bounce：上下起伏
// - stretch：伸缩拉伸
// - inflate：膨胀收缩
// - sway：左右摇摆
// - spin：旋转扭动
// - wobble：不倒翁（左右大幅倾斜回正）
// - wiggle：扭屁股（skewX 左右扭腰）
// - drunk：醉酒（东倒西歪不规则）
// - nod：点头哈腰（前后鞠躬）
// - hula：扭胯（左右平移 + 扭腰，草裙舞）
type DanceStyle = 'bounce' | 'stretch' | 'inflate' | 'sway' | 'spin' | 'wobble' | 'wiggle' | 'drunk' | 'nod' | 'hula'
const DANCE_STYLES: DanceStyle[] = ['bounce', 'stretch', 'inflate', 'sway', 'spin', 'wobble', 'wiggle', 'drunk', 'nod', 'hula']
const charDanceStyle = ref<Record<CharName, DanceStyle>>({ purple: 'bounce', black: 'bounce', orange: 'bounce', yellow: 'bounce' })

// 黄色角色搞怪嘴巴类型：每次进入兴奋状态，随机选一种（本次固定）
// - grin：大笑（半圆弧）
// - big-o：惊讶大O
// - smirk：歪嘴笑
// - pucker：嘟嘴
// - open-wide：张大嘴
// - flat-wide：一字嘴
// - puffy：鼓气嘴（圆润椭圆）
// - bean：腰果嘴（圆润逗号形）
// - cat-smile：猫嘴（圆润W形）
// - heart：爱心嘴
// - drool：流口水嘴（圆润U形+水滴）
// - tongue：吐舌嘴（圆润椭圆+舌头）
// - whistle：吹口哨嘴（小巧O形）
// - war-god：歪嘴战神（不屑/欠揍）
// - pea-shooter：豌豆射手嘴（用力喷东西）
// - onigiri：饭团/包子嘴（嘴里塞满食物）
// - cloud：云朵嘴（打呼/冒泡）
// - vortex：漩涡嘴（彻底无语）
// - moustache：胡子嘴（搞笑胡子+小嘴在下面）
// - funny-three：搞笑3嘴（圆润的3，鸭子嘴）
type YellowMouthStyle = 'sleep-o' | 'grin' | 'big-o' | 'smirk' | 'pucker' | 'open-wide' | 'flat-wide' | 'puffy' | 'bean' | 'cat-smile' | 'heart' | 'drool' | 'tongue' | 'whistle' | 'war-god' | 'pea-shooter' | 'onigiri' | 'cloud' | 'vortex' | 'moustache' | 'funny-three'
const EXCITED_MOUTH_STYLES: YellowMouthStyle[] = ['grin', 'big-o', 'smirk', 'pucker', 'open-wide', 'flat-wide', 'puffy', 'bean', 'cat-smile', 'heart', 'drool', 'tongue', 'whistle', 'war-god', 'pea-shooter', 'onigiri', 'cloud', 'vortex', 'moustache', 'funny-three']
const yellowMouthStyle = ref<YellowMouthStyle>('sleep-o')

// 黄色角色在正常模式下点击输入框时的惊讶状态（椭圆O嘴巴，2-3秒后复原）
const yellowSurprised = ref(false)

// 兴奋时橙色/黄色角色偷瞄用户状态（让呆板的角色更有活人感）
// 时不时快速瞟一眼用户（看鼠标），然后移开，间隔随机 1-3 秒
const orangeYellowPeekingUser = ref(false)

// 橙色/黄色角色兴奋时的眼睛戏类型（每次进入兴奋状态随机选一种）
// - grow：眼睛变大变小
// - spin：瞳孔滴溜溜转
// - blink：疯狂眨眼
// - wiggle：眼珠左右乱窜
// - star：星星眼（★替换瞳孔）
// - wink：单眼眨眼挑逗（左眼闭合右眼睁开，循环切换）
// - heart：爱心眼（眼睛变成爱心形状，倾心眼神）
// - seductive：媚眼（半闭眼+瞳孔上瞟，轻佻挑逗）
// - flutter：飞眼（快速眨眼+瞳孔放大缩小，抛媚眼）
type EyeDramaStyle = 'grow' | 'spin' | 'blink' | 'wiggle' | 'star' | 'wink' | 'heart' | 'seductive' | 'flutter'
const EYE_DRAMA_STYLES: EyeDramaStyle[] = ['grow', 'spin', 'blink', 'wiggle', 'star', 'wink', 'heart', 'seductive', 'flutter']
const orangeYellowEyeDrama = ref<EyeDramaStyle>('grow')

// wink 模式下当前闭合的眼睛（左/右交替），通过定时器切换
const winkEyeSide = ref<'left' | 'right'>('left')

function pickMouthStyle(): YellowMouthStyle {
  return EXCITED_MOUTH_STYLES[Math.floor(Math.random() * EXCITED_MOUTH_STYLES.length)]
}

function pickDanceStyle(): DanceStyle {
  return DANCE_STYLES[Math.floor(Math.random() * DANCE_STYLES.length)]
}

// 暗黑模式 = 关灯睡觉
const isSleeping = computed(() => !!props.isDarkMode)
// 兴奋状态（鼠标悬停登录按钮）：角色睁眼、跳舞、望鼠标
const isExcited = computed(() => !!props.isExcited && isSleeping.value)

const timers: ReturnType<typeof setTimeout>[] = []
function addTimer(fn: () => void, delay: number) {
  const t = setTimeout(fn, delay)
  timers.push(t)
}

function handleMouseMove(e: MouseEvent) {
  mouseX.value = e.clientX
  mouseY.value = e.clientY
}

onMounted(() => {
  window.addEventListener('mousemove', handleMouseMove)
  const scheduleBlink = (blinkRef: typeof isPurpleBlinking) => {
    const delay = Math.random() * 4000 + 3000
    addTimer(() => {
      // 睡觉时不眨眼（眼睛本来就闭着）
      if (!isSleeping.value) blinkRef.value = true
      addTimer(() => {
        blinkRef.value = false
        scheduleBlink(blinkRef)
      }, 150)
    }, delay)
  }
  scheduleBlink(isPurpleBlinking)
  scheduleBlink(isBlackBlinking)
})

onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove)
  timers.forEach(clearTimeout)
  Object.values(charPeekTimers).forEach(t => { if (t !== null) clearTimeout(t) })
})

watch(() => props.isTyping, (val) => {
  if (val && !isSleeping.value) {
    isLookingAtEachOther.value = true
    addTimer(() => { isLookingAtEachOther.value = false }, 800)
    // 黄色角色惊讶：点击输入框时嘴巴变成椭圆O，2-3秒后复原
    yellowSurprised.value = true
    addTimer(() => { yellowSurprised.value = false }, Math.random() * 1000 + 1000)
  } else {
    isLookingAtEachOther.value = false
    yellowSurprised.value = false
  }
})

// 密码可见时紫色偷瞄（醒着时）
watch(() => [props.hasPassword, props.showPassword], () => {
  if (props.hasPassword && props.showPassword && !isSleeping.value) {
    const schedulePeek = () => {
      const delay = Math.random() * 3000 + 2000
      addTimer(() => {
        isPurplePeeking.value = true
        addTimer(() => {
          isPurplePeeking.value = false
          if (props.hasPassword && props.showPassword && !isSleeping.value) schedulePeek()
        }, 800)
      }, delay)
    }
    schedulePeek()
  } else {
    isPurplePeeking.value = false
  }
})

// ===== 密码输入时夸张偷瞄（暗黑模式下也生效）=====
// 输入密码时，随机一个角色夸张地睁大双眼盯着密码框方向
// 离开密码框立即闭眼
watch(() => props.isTyping, (typing) => {
  if (typing && isSleeping.value) {
    const chars = ['purple', 'black', 'orange', 'yellow'] as const
    passwordPeekChar.value = chars[Math.floor(Math.random() * 4)]
  } else {
    passwordPeekChar.value = null
  }
})

// ===== 兴奋状态（鼠标悬停登录按钮）=====
// 进入兴奋：暂停偷瞄，角色睁眼跳舞，紫黑色眼神切换，黄色搞怪嘴巴
// 离开兴奋：恢复沉睡和偷瞄循环
watch(isExcited, (excited) => {
  if (excited) {
    // 暂停所有偷瞄
    peekingChars.value = []
    passwordPeekChar.value = null
    // 每个角色随机选一种律动效果（本次固定）
    const chars: CharName[] = ['purple', 'black', 'orange', 'yellow']
    chars.forEach(c => {
      charDanceStyle.value[c] = pickDanceStyle()
    })
    // 黄色角色随机选一种搞怪嘴巴（本次固定）
    yellowMouthStyle.value = pickMouthStyle()
    // 橙色/黄色角色随机选一种眼睛戏（本次固定）
    orangeYellowEyeDrama.value = EYE_DRAMA_STYLES[Math.floor(Math.random() * EYE_DRAMA_STYLES.length)]
    // 启动紫黑色眼神暗示循环：盯屏幕 1.5s → 看登录按钮 0.5s → 循环
    excitedLookAtUser.value = true  // 先盯屏幕
    scheduleExcitedLook()
    // 启动橙色/黄色偷瞄用户循环：时不时快速瞟一眼用户，增加活人感
    orangeYellowPeekingUser.value = false
    scheduleOrangeYellowPeek()
    // 若选中 wink 模式，启动左右眼交替闭合循环
    winkEyeSide.value = 'left'
    scheduleWinkToggle()
  } else {
    excitedLookAtUser.value = false
    orangeYellowPeekingUser.value = false
    yellowMouthStyle.value = 'sleep-o'
    // 恢复偷瞄循环
    if (isSleeping.value) {
      const chars: CharName[] = ['purple', 'black', 'orange', 'yellow']
      chars.forEach(c => scheduleCharPeek(c))
    }
  }
})

// 兴奋时紫黑色角色眼神暗示循环（暗示用户点击登录）
// 阶段1：盯屏幕 1.5s（excitedLookAtUser = true）
// 阶段2：看登录按钮 0.5s（excitedLookAtUser = false）
// 循环往复，直到离开兴奋状态
function scheduleExcitedLook() {
  if (!isExcited.value) return
  // 当前处于"盯屏幕"阶段，1.5s 后切换到"看登录按钮"
  if (excitedLookAtUser.value) {
    addTimer(() => {
      if (!isExcited.value) return
      excitedLookAtUser.value = false  // 切换到看登录按钮
      scheduleExcitedLook()
    }, 1500)
  } else {
    // 当前处于"看登录按钮"阶段，0.5s 后切换回"盯屏幕"
    addTimer(() => {
      if (!isExcited.value) return
      excitedLookAtUser.value = true  // 切换回盯屏幕
      scheduleExcitedLook()
    }, 500)
  }
}

// 兴奋时橙色/黄色角色偷瞄用户循环（增加活人感）
// 每个周期：偷看用户 0.4s → 移开看别处 1-3s → 循环
function scheduleOrangeYellowPeek() {
  if (!isExcited.value) return
  const awayDelay = Math.random() * 2000 + 1000  // 移开持续时间 1-3s
  addTimer(() => {
    if (!isExcited.value) return
    orangeYellowPeekingUser.value = true  // 快速偷瞄用户
    addTimer(() => {
      if (!isExcited.value) return
      orangeYellowPeekingUser.value = false  // 移开
      scheduleOrangeYellowPeek()
    }, 400)
  }, awayDelay)
}

// wink 模式下左右眼交替闭合的循环（每 0.7s 切换一次）
function scheduleWinkToggle() {
  if (!isExcited.value || orangeYellowEyeDrama.value !== 'wink') return
  addTimer(() => {
    if (!isExcited.value || orangeYellowEyeDrama.value !== 'wink') return
    winkEyeSide.value = winkEyeSide.value === 'left' ? 'right' : 'left'
    scheduleWinkToggle()
  }, 700)
}

// 暗黑模式切换时清理/启动
watch(isSleeping, (sleeping) => {
  if (sleeping) {
    // 每个角色独立启动偷瞄循环
    const chars: CharName[] = ['purple', 'black', 'orange', 'yellow']
    chars.forEach(c => scheduleCharPeek(c))
  } else {
    peekingChars.value = []
    startledChar.value = null
    passwordPeekChar.value = null
  }
}, { immediate: true })

// ===== 每个角色独立的偷瞄调度 =====
// 间隔 10-20 秒，偷瞄持续 2-3 秒
// 每个角色独立的偷瞄定时器ID，用于取消旧定时器防止累积泄漏
const charPeekTimers: Record<CharName, ReturnType<typeof setTimeout> | null> = {
  purple: null, black: null, orange: null, yellow: null
}

function scheduleCharPeek(char: CharName) {
  if (!isSleeping.value) return
  // 取消该角色之前排期的偷瞄，防止定时器累积（兴奋状态进出会导致重复调度）
  if (charPeekTimers[char] !== null) {
    clearTimeout(charPeekTimers[char]!)
    charPeekTimers[char] = null
  }
  // 每个角色独立随机间隔 10-20 秒
  const delay = Math.random() * 10000 + 10000
  const timerId = setTimeout(() => {
    charPeekTimers[char] = null
    if (!isSleeping.value) return
    // 惊吓中或正在密码偷瞄的角色跳过本次，重新调度
    if (startledChar.value === char || passwordPeekChar.value === char) {
      scheduleCharPeek(char)
      return
    }
    // 开始偷瞄
    charPeekStyle.value[char] = Math.floor(Math.random() * 5) + 1
    charPeekEyeSide.value[char] = Math.random() > 0.5 ? 'left' : 'right'
    if (!peekingChars.value.includes(char)) {
      peekingChars.value = [...peekingChars.value, char]
    }
    performCharPeek(char, charPeekStyle.value[char])
  }, delay)
  charPeekTimers[char] = timerId
}

function performCharPeek(char: CharName, style: number) {
  if (style === 1) {
    // 风格1：单眼眯缝慢慢偷瞄→闭眼装睡（2.5秒）
    addTimer(() => endCharPeek(char), 2500)
  } else if (style === 2) {
    // 风格2：单眼眯缝→眼神飘忽左右偷看→闭眼（3秒）
    charPeekPupilX.value[char] = -4
    addTimer(() => {
      if (!peekingChars.value.includes(char)) return
      charPeekPupilX.value = { ...charPeekPupilX.value, [char]: 4 }
      addTimer(() => {
        if (!peekingChars.value.includes(char)) return
        charPeekPupilX.value = { ...charPeekPupilX.value, [char]: -3 }
        addTimer(() => {
          charPeekPupilX.value = { ...charPeekPupilX.value, [char]: 0 }
          endCharPeek(char)
        }, 800)
      }, 800)
    }, 800)
  } else if (style === 3) {
    // 风格3：单眼眯缝盯鼠标（猥琐跟踪）→闭眼（3秒）
    addTimer(() => endCharPeek(char), 3000)
  } else if (style === 4) {
    // 风格4：双眼眯缝装睡偷看→被发现快速闭眼（2秒）
    addTimer(() => endCharPeek(char), 2000)
  } else {
    // 风格5：双眼全睁惊讶偷看→迅速闭眼装睡（2秒）
    addTimer(() => endCharPeek(char), 2000)
  }
}

function endCharPeek(char: CharName) {
  peekingChars.value = peekingChars.value.filter(c => c !== char)
  // 偷瞄结束，重新调度下一次
  if (isSleeping.value) scheduleCharPeek(char)
}

// 点击角色：惊吓晃动（1s逐渐停止）
function onCharClick(char: CharName) {
  if (!isSleeping.value) return
  // 移除该角色的偷瞄状态
  peekingChars.value = peekingChars.value.filter(c => c !== char)
  passwordPeekChar.value = null
  startledChar.value = char
  addTimer(() => {
    startledChar.value = null
    // 惊吓结束后恢复该角色的偷瞄循环
    if (isSleeping.value) scheduleCharPeek(char)
  }, 1000)
}

function getPos(el: HTMLElement | null) {
  if (!el || isSleeping.value) return { faceX: 0, faceY: 0, bodySkew: 0, pupilX: 0, pupilY: 0 }
  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 3
  const dx = mouseX.value - cx
  const dy = mouseY.value - cy
  return {
    faceX: Math.max(-15, Math.min(15, dx / 20)),
    faceY: Math.max(-10, Math.min(10, dy / 30)),
    bodySkew: Math.max(-6, Math.min(6, -dx / 120)),
    pupilX: Math.max(-5, Math.min(5, dx / 50)),
    pupilY: Math.max(-4, Math.min(4, dy / 60))
  }
}

const purplePos = computed(() => getPos(purpleRef.value))
const blackPos = computed(() => getPos(blackRef.value))
const yellowPos = computed(() => getPos(yellowRef.value))
const orangePos = computed(() => getPos(orangeRef.value))

const pwdVisible = computed(() => props.hasPassword && props.showPassword)
const purpleTall = computed(() => props.isTyping || (props.hasPassword && !props.showPassword))

// ===== 眼睛闭合状态 =====
// 睡觉时所有眼睛闭合，正在偷瞄或被惊吓的角色眼睛睁开
// 偷瞄支持：单眼睁开（左/右）、双眼眯缝、双眼全睁、密码框夸张偷瞄、兴奋全睁
const isPeeking = (char: string) =>
  peekingChars.value.includes(char as 'purple' | 'black' | 'orange' | 'yellow') ||
  passwordPeekChar.value === char ||
  startledChar.value === char ||
  isExcited.value

// 判断某只眼是否闭合：'left' | 'right'
function isEyeClosed(char: 'purple' | 'black' | 'orange' | 'yellow', side: 'left' | 'right') {
  if (!isSleeping.value) return false
  if (isExcited.value) return false                  // 兴奋：双眼全睁
  if (startledChar.value === char) return false       // 惊吓：双眼全睁
  if (passwordPeekChar.value === char) return false   // 密码偷瞄：双眼全睁（夸张）
  if (!peekingChars.value.includes(char)) return true // 睡觉：双眼闭合
  // 偷瞄中
  const style = charPeekStyle.value[char]
  if (style === 4) return false  // 双眼眯缝：两只眼都半开
  if (style === 5) return false  // 双眼全睁
  // 风格1/2/3：只睁开 peekEyeSide 那只眼
  return charPeekEyeSide.value[char] !== side
}

// 判断某只眼是否眯缝（半睁半闭，能看到一点瞳孔）
function isEyeSquinting(char: CharName, side: 'left' | 'right') {
  if (!isSleeping.value) return false
  if (isExcited.value) return false                   // 兴奋：全睁
  if (startledChar.value === char) return false       // 惊吓：全睁
  if (passwordPeekChar.value === char) return false   // 密码偷瞄：全睁（夸张）
  if (!peekingChars.value.includes(char)) return false // 睡觉：全闭
  const style = charPeekStyle.value[char]
  if (style === 5) return false             // 双眼全睁：不眯缝
  // 风格1/2/3/4：眯缝眼
  return true
}

// ===== 兴奋时橙色/黄色角色眼睛戏的辅助判断 =====
// wink 模式：当前闭合的那只眼（左右交替）
function isWinkEye(side: 'left' | 'right') {
  return isExcited.value && orangeYellowEyeDrama.value === 'wink' && winkEyeSide.value === side
}
// seductive 模式：双眼半闭（媚眼）
function isSeductiveEye() {
  return isExcited.value && orangeYellowEyeDrama.value === 'seductive'
}
// heart 模式：爱心眼（用 SVG 替换瞳孔）
function isHeartEye() {
  return isExcited.value && orangeYellowEyeDrama.value === 'heart'
}
// star 模式：星星眼（用 SVG 替换瞳孔）
function isStarEye() {
  return isExcited.value && orangeYellowEyeDrama.value === 'star'
}

// 获取兴奋时瞳孔的 Y 偏移（seductive 模式瞳孔上瞟）
function getDramaPupilY(char: CharName, baseY: number) {
  if (isSeductiveEye()) return -3                  // 媚眼：瞳孔上瞟
  return baseY
}

function getSleepPupil(char: CharName) {
  if (startledChar.value === char) return { x: 0, y: 0 } // 惊吓：直视前方
  // 兴奋状态
  if (isExcited.value) {
    // 紫色和黑色：眼神暗示循环（盯屏幕 1.5s ↔ 看登录按钮 0.5s）
    if (char === 'purple' || char === 'black') {
      if (excitedLookAtUser.value) {
        // 盯屏幕（正向前方看）
        return { x: 0, y: 0 }
      }
      // 看登录按钮（右侧）
      return { x: 5, y: 2 }
    }
    // 黄色和橙色：活泼地偷瞄用户（增加活人感）
    // 每隔 1-3s 快速瞟一眼用户（看鼠标），其余时间看别处（左/右飘忽）
    if (orangeYellowPeekingUser.value) {
      return getMouseLookPupil(char)
    }
    // 不看用户时：随机看向左右下方，像在四处张望
    return char === 'orange' ? { x: -3, y: 2 } : { x: 3, y: 2 }
  }
  // 密码偷瞄：夸张地盯着密码框方向（右侧）
  if (passwordPeekChar.value === char) return { x: 5, y: -3 }
  if (!peekingChars.value.includes(char)) return { x: 0, y: 0 }
  const style = charPeekStyle.value[char]
  if (style === 2) return { x: charPeekPupilX.value[char], y: 0 } // 左右飘忽
  if (style === 3) {
    // 盯鼠标（猥琐跟踪）
    return getMouseLookPupil(char)
  }
  return { x: 0, y: 0 } // 风格1/4/5：直视
}

// 辅助：获取望鼠标方向的瞳孔偏移
function getMouseLookPupil(char: CharName) {
  const refMap = { purple: purpleRef, black: blackRef, orange: orangeRef, yellow: yellowRef } as const
  const el = refMap[char].value
  if (!el) return { x: 0, y: 0 }
  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 3
  const dx = mouseX.value - cx
  const dy = mouseY.value - cy
  return { x: Math.max(-5, Math.min(5, dx / 40)), y: Math.max(-4, Math.min(4, dy / 50)) }
}

// 兼容旧逻辑（用于橙色/黄色的整体闭合判断，实际渲染用 isEyeClosed）
const purpleEyeClosed = computed(() => isSleeping.value && !isPeeking('purple'))
const blackEyeClosed = computed(() => isSleeping.value && !isPeeking('black'))
const orangeEyeClosed = computed(() => isSleeping.value && !isPeeking('orange'))
const yellowEyeClosed = computed(() => isSleeping.value && !isPeeking('yellow'))

// Purple
const purpleTransform = computed(() => {
  if (isSleeping.value) return 'skewX(0deg)'
  if (pwdVisible.value) return 'skewX(0deg)'
  if (purpleTall.value) return `skewX(${purplePos.value.bodySkew - 12}deg) translateX(40px)`
  return `skewX(${purplePos.value.bodySkew}deg)`
})
const purpleEyeLeft = computed(() => {
  if (isSleeping.value) return '45px'
  if (pwdVisible.value) return '20px'
  if (isLookingAtEachOther.value) return '55px'
  return `${45 + purplePos.value.faceX}px`
})
const purpleEyeTop = computed(() => {
  if (isSleeping.value) return '40px'
  if (pwdVisible.value) return '35px'
  if (isLookingAtEachOther.value) return '65px'
  return `${40 + purplePos.value.faceY}px`
})
const purplePupil = computed(() => {
  if (isSleeping.value) return getSleepPupil('purple')
  if (pwdVisible.value) return { x: isPurplePeeking.value ? 4 : -4, y: isPurplePeeking.value ? 5 : -4 }
  if (isLookingAtEachOther.value) return { x: 3, y: 4 }
  return { x: purplePos.value.pupilX, y: purplePos.value.pupilY }
})

// Black
const blackTransform = computed(() => {
  if (isSleeping.value) return 'skewX(0deg)'
  if (pwdVisible.value) return 'skewX(0deg)'
  if (isLookingAtEachOther.value) return `skewX(${blackPos.value.bodySkew * 1.5 + 10}deg) translateX(20px)`
  if (purpleTall.value) return `skewX(${blackPos.value.bodySkew * 1.5}deg)`
  return `skewX(${blackPos.value.bodySkew}deg)`
})
const blackEyeLeft = computed(() => {
  if (isSleeping.value) return '26px'
  if (pwdVisible.value) return '10px'
  if (isLookingAtEachOther.value) return '32px'
  return `${26 + blackPos.value.faceX}px`
})
const blackEyeTop = computed(() => {
  if (isSleeping.value) return '32px'
  if (pwdVisible.value) return '28px'
  if (isLookingAtEachOther.value) return '12px'
  return `${32 + blackPos.value.faceY}px`
})
const blackPupil = computed(() => {
  if (isSleeping.value) return getSleepPupil('black')
  if (pwdVisible.value) return { x: -4, y: -4 }
  if (isLookingAtEachOther.value) return { x: 0, y: -4 }
  return { x: blackPos.value.pupilX, y: blackPos.value.pupilY }
})

// Orange
const orangeTransform = computed(() => {
  if (isSleeping.value) return 'skewX(0deg)'
  return pwdVisible.value ? 'skewX(0deg)' : `skewX(${orangePos.value.bodySkew}deg)`
})
const orangeEyeLeft = computed(() => {
  if (isSleeping.value) return '82px'
  return pwdVisible.value ? '50px' : `${82 + orangePos.value.faceX}px`
})
const orangeEyeTop = computed(() => {
  if (isSleeping.value) return '90px'
  return pwdVisible.value ? '85px' : `${90 + orangePos.value.faceY}px`
})
const orangePupil = computed(() => {
  if (isSleeping.value) return getSleepPupil('orange')
  return pwdVisible.value ? { x: -5, y: -4 } : { x: orangePos.value.pupilX, y: orangePos.value.pupilY }
})

// Yellow
const yellowTransform = computed(() => {
  if (isSleeping.value) return 'skewX(0deg)'
  return pwdVisible.value ? 'skewX(0deg)' : `skewX(${yellowPos.value.bodySkew}deg)`
})
const yellowEyeLeft = computed(() => {
  if (isSleeping.value) return '52px'
  return pwdVisible.value ? '20px' : `${52 + yellowPos.value.faceX}px`
})
const yellowEyeTop = computed(() => {
  if (isSleeping.value) return '40px'
  return pwdVisible.value ? '35px' : `${40 + yellowPos.value.faceY}px`
})
const yellowPupil = computed(() => {
  if (isSleeping.value) return getSleepPupil('yellow')
  return pwdVisible.value ? { x: -5, y: -4 } : { x: yellowPos.value.pupilX, y: yellowPos.value.pupilY }
})

// 黄色角色嘴巴额外 class（用于爱心/云朵/漩涡等 clip-path 嘴型）
const yellowMouthStyleClass = computed(() => {
  if (!isExcited.value) return ''
  if (yellowMouthStyle.value === 'heart') return 'yellow-mouth-heart'
  if (yellowMouthStyle.value === 'cloud') return 'yellow-mouth-cloud'
  if (yellowMouthStyle.value === 'vortex') return 'yellow-mouth-vortex'
  if (yellowMouthStyle.value === 'tongue') return 'yellow-mouth-tongue'
  return ''
})

// 黄色角色嘴巴样式
// - 睡觉：圆形O + 脉动动画
// - 兴奋：搞怪嘴巴（每次随机一种）
// - 正常模式惊讶（点击输入框）：椭圆O
// - 默认：横线
const yellowMouthStyleObj = computed(() => {
  const mouthCenter = parseFloat(yellowEyeLeft.value) + 26
  const mouthTop = parseFloat(yellowEyeTop.value) + 48

  if (isExcited.value) {
    switch (yellowMouthStyle.value) {
      case 'grin': // 大笑（半圆弧）
        return { width: '50px', height: '25px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 25}px`, top: `${mouthTop - 5}px`, borderRadius: '0 0 9999px 9999px' }
      case 'big-o': // 惊讶大O
        return { width: '22px', height: '22px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 11}px`, top: `${mouthTop - 4}px`, borderRadius: '50%' }
      case 'smirk': // 歪嘴笑（一边翘起）
        return { width: '40px', height: '8px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 20}px`, top: `${mouthTop}px`, borderRadius: '0 9999px 0 9999px' }
      case 'pucker': // 嘟嘴
        return { width: '14px', height: '14px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 7}px`, top: `${mouthTop - 1}px`, borderRadius: '50%' }
      case 'open-wide': // 张大嘴（椭圆）
        return { width: '26px', height: '38px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 13}px`, top: `${mouthTop - 8}px`, borderRadius: '40%' }
      case 'flat-wide': // 一字嘴（宽横线）
        return { width: '60px', height: '5px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 30}px`, top: `${mouthTop + 2}px`, borderRadius: '9999px' }
      case 'puffy': // 鼓气嘴（圆润椭圆，像含着糖果）
        return { width: '34px', height: '26px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 17}px`, top: `${mouthTop - 7}px`, borderRadius: '55% 55% 45% 45%' }
      case 'bean': // 腰果嘴（圆润逗号形）
        return { width: '26px', height: '16px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 13}px`, top: `${mouthTop - 2}px`, borderRadius: '40% 60% 60% 40% / 40% 40% 60% 60%' }
      case 'cat-smile': // 猫嘴（圆润W形，两头翘起+中间两个弧线凹陷，用内嵌SVG）
        return { width: '38px', height: '18px', backgroundColor: 'transparent', left: `${mouthCenter - 19}px`, top: `${mouthTop - 2}px`, borderRadius: '0' }
      case 'heart': // 爱心嘴（纯黑，用 clip-path 在 class 中定义）
        return { width: '30px', height: '26px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 15}px`, top: `${mouthTop - 6}px`, borderRadius: '0' }
      case 'drool': // 流口水嘴（圆润U形+蓝色口水）
        return { width: '28px', height: '18px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 14}px`, top: `${mouthTop - 2}px`, borderRadius: '10px 10px 30px 30px' }
      case 'tongue': // 吐舌嘴（圆润椭圆+粉色舌头）
        return { width: '28px', height: '18px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 14}px`, top: `${mouthTop - 2}px`, borderRadius: '40%' }
      case 'whistle': // 吹口哨嘴（小巧O形）
        return { width: '10px', height: '10px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 5}px`, top: `${mouthTop + 2}px`, borderRadius: '50%' }
      case 'war-god': // 歪嘴战神（不屑/欠揍，一边高一边低）
        return { width: '44px', height: '10px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 22}px`, top: `${mouthTop + 2}px`, borderRadius: '9999px', transform: 'rotate(-8deg)' }
      case 'pea-shooter': // 豌豆射手嘴（用力喷东西，长椭圆向前突出）
        return { width: '16px', height: '42px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 8}px`, top: `${mouthTop - 24}px`, borderRadius: '50% 50% 40% 40%' }
      case 'onigiri': // 饭团/包子嘴（嘴里塞满食物，圆鼓鼓三角）
        return { width: '34px', height: '28px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 17}px`, top: `${mouthTop - 8}px`, borderRadius: '50% 50% 50% 50% / 60% 60% 40% 40%' }
      case 'cloud': // 云朵嘴（打呼/冒泡，用 clip-path 在 class 中定义）
        return { width: '44px', height: '22px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 22}px`, top: `${mouthTop - 4}px`, borderRadius: '0' }
      case 'vortex': // 漩涡嘴（彻底无语，用 clip-path 在 class 中定义）
        return { width: '30px', height: '30px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 15}px`, top: `${mouthTop - 8}px`, borderRadius: '0' }
      case 'moustache': // 胡子嘴（搞笑胡子+小嘴在下面，胡子用子元素）
        return { width: '10px', height: '10px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 5}px`, top: `${mouthTop + 10}px`, borderRadius: '50%' }
      case 'funny-three': // 搞笑3嘴（粉红数字3，嘟嘴亲亲，用内嵌SVG）
        return { width: '28px', height: '32px', backgroundColor: 'transparent', left: `${mouthCenter - 14}px`, top: `${mouthTop - 12}px`, borderRadius: '0' }
      default:
        return { width: '12px', height: '12px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 6}px`, top: `${mouthTop}px`, borderRadius: '50%' }
    }
  }
  // 睡觉时：大O（打呼），配合 CSS 脉动动画
  if (isSleeping.value) {
    return { width: '18px', height: '18px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 9}px`, top: `${mouthTop - 2}px`, borderRadius: '50%' }
  }
  // 正常模式惊讶（点击输入框）：椭圆O
  if (yellowSurprised.value) {
    return { width: '20px', height: '28px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 10}px`, top: `${mouthTop - 6}px`, borderRadius: '50%' }
  }
  // 默认：横线嘴巴
  return { width: '50px', height: '4px', backgroundColor: '#2D2D2D', left: `${mouthCenter - 25}px`, top: `${mouthTop}px`, borderRadius: '9999px' }
})
</script>

<template>
  <div class="characters-container relative" style="width: 550px; height: 400px; transform: scale(1.3); transform-origin: bottom center;">
    <!-- ===== 睡觉时的 Zzz 飘字 / 兴奋时的音乐符号 ===== -->
    <template v-if="isSleeping && !isExcited">
      <div class="zzz zzz-purple" style="left: 130px; top: -20px;">z</div>
      <div class="zzz zzz-purple" style="left: 150px; top: -50px; animation-delay: 0.8s;">z</div>
      <div class="zzz zzz-purple" style="left: 170px; top: -80px; animation-delay: 1.6s;">z</div>

      <div class="zzz zzz-black" style="left: 280px; top: -30px; animation-delay: 0.3s;">z</div>
      <div class="zzz zzz-black" style="left: 300px; top: -60px; animation-delay: 1.1s;">z</div>

      <div class="zzz zzz-orange" style="left: 90px; top: 10px; animation-delay: 0.5s;">z</div>
      <div class="zzz zzz-orange" style="left: 110px; top: -20px; animation-delay: 1.3s;">z</div>

      <div class="zzz zzz-yellow" style="left: 360px; top: -10px; animation-delay: 0.6s;">z</div>
      <div class="zzz zzz-yellow" style="left: 380px; top: -40px; animation-delay: 1.4s;">z</div>
    </template>

    <!-- 兴奋时的音乐符号 -->
    <template v-if="isExcited">
      <div class="music-note music-purple" style="left: 130px; top: -20px;">♪</div>
      <div class="music-note music-purple" style="left: 160px; top: -60px; animation-delay: 0.5s;">♫</div>

      <div class="music-note music-black" style="left: 280px; top: -30px; animation-delay: 0.3s;">♪</div>
      <div class="music-note music-black" style="left: 310px; top: -70px; animation-delay: 1.0s;">♫</div>

      <div class="music-note music-orange" style="left: 90px; top: 10px; animation-delay: 0.7s;">♪</div>
      <div class="music-note music-orange" style="left: 120px; top: -30px; animation-delay: 1.3s;">♫</div>

      <div class="music-note music-yellow" style="left: 360px; top: -10px; animation-delay: 0.6s;">♪</div>
      <div class="music-note music-yellow" style="left: 390px; top: -50px; animation-delay: 1.2s;">♫</div>
    </template>

    <!-- Purple tall rectangle (back layer) -->
    <div
      ref="purpleRef"
      class="absolute bottom-0 transition-all duration-700 ease-in-out"
      :class="{ 'char-sleeping': isSleeping, 'char-startled': startledChar === 'purple', ['char-excited char-dance-' + charDanceStyle.purple]: isExcited, 'cursor-pointer': isSleeping }"
      :style="{
        left: '70px',
        width: '180px',
        height: purpleTall && !isSleeping ? '440px' : '400px',
        backgroundColor: '#6C3FF5',
        borderRadius: '10px 10px 0 0',
        zIndex: 1,
        transform: purpleTransform,
        transformOrigin: 'bottom center'
      }"
      @click="onCharClick('purple')"
    >
      <div v-if="startledChar === 'purple'" class="exclamation-bubble">!</div>
      <div
        class="absolute flex gap-8 transition-all duration-700 ease-in-out"
        :style="{ left: purpleEyeLeft, top: purpleEyeTop }"
      >
        <!-- 左眼：支持全闭/眯缝/全睁 -->
        <div
          class="rounded-full flex items-center justify-center transition-all duration-200"
          :style="{ width: '18px', height: isEyeClosed('purple','left') ? '2px' : (isEyeSquinting('purple','left') ? '6px' : '18px'), backgroundColor: 'white', overflow: 'hidden' }"
        >
          <div
            v-if="!isEyeClosed('purple','left')"
            class="rounded-full"
            :style="{ width: '7px', height: isEyeSquinting('purple','left') ? '4px' : '7px', backgroundColor: '#2D2D2D', transform: `translate(${purplePupil.x}px, ${purplePupil.y}px)`, transition: 'transform 0.1s ease-out' }"
          />
        </div>
        <!-- 右眼：支持全闭/眯缝/全睁 -->
        <div
          class="rounded-full flex items-center justify-center transition-all duration-200"
          :style="{ width: '18px', height: isEyeClosed('purple','right') ? '2px' : (isEyeSquinting('purple','right') ? '6px' : '18px'), backgroundColor: 'white', overflow: 'hidden' }"
        >
          <div
            v-if="!isEyeClosed('purple','right')"
            class="rounded-full"
            :style="{ width: '7px', height: isEyeSquinting('purple','right') ? '4px' : '7px', backgroundColor: '#2D2D2D', transform: `translate(${purplePupil.x}px, ${purplePupil.y}px)`, transition: 'transform 0.1s ease-out' }"
          />
        </div>
      </div>
    </div>

    <!-- Black tall rectangle (middle layer) -->
    <div
      ref="blackRef"
      class="absolute bottom-0 transition-all duration-700 ease-in-out"
      :class="{ 'char-sleeping': isSleeping, 'char-startled': startledChar === 'black', ['char-excited char-dance-' + charDanceStyle.black]: isExcited, 'cursor-pointer': isSleeping }"
      :style="{
        left: '240px',
        width: '120px',
        height: '310px',
        backgroundColor: '#2D2D2D',
        borderRadius: '8px 8px 0 0',
        zIndex: 2,
        transform: blackTransform,
        transformOrigin: 'bottom center'
      }"
      @click="onCharClick('black')"
    >
      <div v-if="startledChar === 'black'" class="exclamation-bubble">!</div>
      <div
        class="absolute flex gap-6 transition-all duration-700 ease-in-out"
        :style="{ left: blackEyeLeft, top: blackEyeTop }"
      >
        <!-- 左眼：支持全闭/眯缝/全睁 -->
        <div
          class="rounded-full flex items-center justify-center transition-all duration-200"
          :style="{ width: '16px', height: isEyeClosed('black','left') ? '2px' : (isEyeSquinting('black','left') ? '5px' : '16px'), backgroundColor: 'white', overflow: 'hidden' }"
        >
          <div
            v-if="!isEyeClosed('black','left')"
            class="rounded-full"
            :style="{ width: '6px', height: isEyeSquinting('black','left') ? '3px' : '6px', backgroundColor: '#2D2D2D', transform: `translate(${blackPupil.x}px, ${blackPupil.y}px)`, transition: 'transform 0.1s ease-out' }"
          />
        </div>
        <!-- 右眼：支持全闭/眯缝/全睁 -->
        <div
          class="rounded-full flex items-center justify-center transition-all duration-200"
          :style="{ width: '16px', height: isEyeClosed('black','right') ? '2px' : (isEyeSquinting('black','right') ? '5px' : '16px'), backgroundColor: 'white', overflow: 'hidden' }"
        >
          <div
            v-if="!isEyeClosed('black','right')"
            class="rounded-full"
            :style="{ width: '6px', height: isEyeSquinting('black','right') ? '3px' : '6px', backgroundColor: '#2D2D2D', transform: `translate(${blackPupil.x}px, ${blackPupil.y}px)`, transition: 'transform 0.1s ease-out' }"
          />
        </div>
      </div>
    </div>

    <!-- Orange semi-circle (front left) -->
    <div
      ref="orangeRef"
      class="absolute bottom-0 transition-all duration-700 ease-in-out"
      :class="{ 'char-sleeping': isSleeping, 'char-startled': startledChar === 'orange', ['char-excited char-dance-' + charDanceStyle.orange]: isExcited, 'cursor-pointer': isSleeping }"
      :style="{
        left: '0px',
        width: '240px',
        height: '200px',
        zIndex: 3,
        backgroundColor: '#FF9B6B',
        borderRadius: '120px 120px 0 0',
        transform: orangeTransform,
        transformOrigin: 'bottom center'
      }"
      @click="onCharClick('orange')"
    >
      <div v-if="startledChar === 'orange'" class="exclamation-bubble">!</div>
      <div
        class="absolute flex gap-8 transition-all duration-200 ease-out"
        :style="{ left: orangeEyeLeft, top: orangeEyeTop }"
      >
        <!-- 左眼+微笑弧线（眉毛） -->
        <div class="relative">
          <!-- 眉毛/微笑弧线（与黄色角色同步：cat-smile 或 grin 嘴型时显示） -->
          <svg
            v-if="isExcited && !isEyeClosed('orange','left') && !isWinkEye('left') && (yellowMouthStyle === 'cat-smile' || yellowMouthStyle === 'grin')"
            class="absolute"
            width="16" height="8" viewBox="0 0 16 8"
            style="left: -2px; top: -10px;"
          >
            <path d="M2 6 Q8 0 14 6" fill="none" stroke="#2D2D2D" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <!-- 星星眼 -->
          <svg
            v-if="isExcited && !isEyeClosed('orange','left') && !isWinkEye('left') && isStarEye()"
            class="eye-drama-star"
            width="18" height="18" viewBox="0 0 18 18"
          >
            <polygon points="9,1 11,6 16,7 12.5,11 13.5,16 9,13.5 5.5,16 5.5,11 2,7 7,6" fill="#2D2D2D"/>
          </svg>
          <!-- 爱心眼 -->
          <svg
            v-else-if="isExcited && !isEyeClosed('orange','left') && !isWinkEye('left') && isHeartEye()"
            class="eye-drama-heart-svg"
            width="16" height="16" viewBox="0 0 16 16"
          >
            <path d="M8 14 C8 14, 2 9, 2 5.5 C2 3, 4 2, 5.5 2 C6.8 2, 8 3, 8 4.5 C8 3, 9.2 2, 10.5 2 C12 2, 14 3, 14 5.5 C14 9, 8 14, 8 14 Z" fill="#2D2D2D"/>
          </svg>
          <!-- 普通眼睛（支持 grow/spin/blink/wiggle/flutter/seductive/wink） -->
          <div
            v-else
            class="rounded-full transition-all duration-200"
            :class="isExcited && !isEyeClosed('orange','left') && !isWinkEye('left') ? `eye-drama-${orangeYellowEyeDrama}` : ''"
            :style="{
              width: isEyeClosed('orange','left') ? '14px' : (isWinkEye('left') ? '14px' : '12px'),
              height: isEyeClosed('orange','left') ? '2px' : (isWinkEye('left') ? '2px' : (isSeductiveEye() ? '6px' : (isEyeSquinting('orange','left') ? '4px' : '12px'))),
              backgroundColor: '#2D2D2D',
              transform: `translate(${orangePupil.x}px, ${getDramaPupilY('orange', orangePupil.y)}px)`,
              transition: 'transform 0.1s ease-out, all 0.2s ease-out'
            }"
          />
        </div>
        <!-- 右眼+微笑弧线（眉毛） -->
        <div class="relative">
          <!-- 眉毛/微笑弧线 -->
          <svg
            v-if="isExcited && !isEyeClosed('orange','right') && !isWinkEye('right') && (yellowMouthStyle === 'cat-smile' || yellowMouthStyle === 'grin')"
            class="absolute"
            width="16" height="8" viewBox="0 0 16 8"
            style="left: -2px; top: -10px;"
          >
            <path d="M2 6 Q8 0 14 6" fill="none" stroke="#2D2D2D" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <!-- 星星眼 -->
          <svg
            v-if="isExcited && !isEyeClosed('orange','right') && !isWinkEye('right') && isStarEye()"
            class="eye-drama-star"
            width="18" height="18" viewBox="0 0 18 18"
          >
            <polygon points="9,1 11,6 16,7 12.5,11 13.5,16 9,13.5 5.5,16 5.5,11 2,7 7,6" fill="#2D2D2D"/>
          </svg>
          <!-- 爱心眼 -->
          <svg
            v-else-if="isExcited && !isEyeClosed('orange','right') && !isWinkEye('right') && isHeartEye()"
            class="eye-drama-heart-svg"
            width="16" height="16" viewBox="0 0 16 16"
          >
            <path d="M8 14 C8 14, 2 9, 2 5.5 C2 3, 4 2, 5.5 2 C6.8 2, 8 3, 8 4.5 C8 3, 9.2 2, 10.5 2 C12 2, 14 3, 14 5.5 C14 9, 8 14, 8 14 Z" fill="#2D2D2D"/>
          </svg>
          <!-- 普通眼睛 -->
          <div
            v-else
            class="rounded-full transition-all duration-200"
            :class="isExcited && !isEyeClosed('orange','right') && !isWinkEye('right') ? `eye-drama-${orangeYellowEyeDrama}` : ''"
            :style="{
              width: isEyeClosed('orange','right') ? '14px' : (isWinkEye('right') ? '14px' : '12px'),
              height: isEyeClosed('orange','right') ? '2px' : (isWinkEye('right') ? '2px' : (isSeductiveEye() ? '6px' : (isEyeSquinting('orange','right') ? '4px' : '12px'))),
              backgroundColor: '#2D2D2D',
              transform: `translate(${orangePupil.x}px, ${getDramaPupilY('orange', orangePupil.y)}px)`,
              transition: 'transform 0.1s ease-out, all 0.2s ease-out'
            }"
          />
        </div>
      </div>
    </div>

    <!-- Yellow tall rectangle (front right) -->
    <div
      ref="yellowRef"
      class="absolute bottom-0 transition-all duration-700 ease-in-out"
      :class="{ 'char-sleeping': isSleeping, 'char-startled': startledChar === 'yellow', ['char-excited char-dance-' + charDanceStyle.yellow]: isExcited, 'cursor-pointer': isSleeping }"
      :style="{
        left: '310px',
        width: '140px',
        height: '230px',
        backgroundColor: '#E8D754',
        borderRadius: '70px 70px 0 0',
        zIndex: 4,
        transform: yellowTransform,
        transformOrigin: 'bottom center'
      }"
      @click="onCharClick('yellow')"
    >
      <div v-if="startledChar === 'yellow'" class="exclamation-bubble">!</div>
      <div
        class="absolute flex gap-6 transition-all duration-200 ease-out"
        :style="{ left: yellowEyeLeft, top: yellowEyeTop }"
      >
        <!-- 左眼+微笑弧线 -->
        <div class="relative">
          <svg
            v-if="isExcited && !isEyeClosed('yellow','left') && !isWinkEye('left') && (yellowMouthStyle === 'cat-smile' || yellowMouthStyle === 'grin')"
            class="absolute"
            width="16" height="8" viewBox="0 0 16 8"
            style="left: -2px; top: -10px;"
          >
            <path d="M2 6 Q8 0 14 6" fill="none" stroke="#2D2D2D" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <!-- 星星眼 -->
          <svg
            v-if="isExcited && !isEyeClosed('yellow','left') && !isWinkEye('left') && isStarEye()"
            class="eye-drama-star"
            width="18" height="18" viewBox="0 0 18 18"
          >
            <polygon points="9,1 11,6 16,7 12.5,11 13.5,16 9,13.5 5.5,16 5.5,11 2,7 7,6" fill="#2D2D2D"/>
          </svg>
          <!-- 爱心眼 -->
          <svg
            v-else-if="isExcited && !isEyeClosed('yellow','left') && !isWinkEye('left') && isHeartEye()"
            class="eye-drama-heart-svg"
            width="16" height="16" viewBox="0 0 16 16"
          >
            <path d="M8 14 C8 14, 2 9, 2 5.5 C2 3, 4 2, 5.5 2 C6.8 2, 8 3, 8 4.5 C8 3, 9.2 2, 10.5 2 C12 2, 14 3, 14 5.5 C14 9, 8 14, 8 14 Z" fill="#2D2D2D"/>
          </svg>
          <!-- 普通眼睛（支持 grow/spin/blink/wiggle/flutter/seductive/wink） -->
          <div
            v-else
            class="rounded-full transition-all duration-200"
            :class="isExcited && !isEyeClosed('yellow','left') && !isWinkEye('left') ? `eye-drama-${orangeYellowEyeDrama}` : ''"
            :style="{
              width: isEyeClosed('yellow','left') ? '14px' : (isWinkEye('left') ? '14px' : '12px'),
              height: isEyeClosed('yellow','left') ? '2px' : (isWinkEye('left') ? '2px' : (isSeductiveEye() ? '6px' : (isEyeSquinting('yellow','left') ? '4px' : '12px'))),
              backgroundColor: '#2D2D2D',
              transform: `translate(${yellowPupil.x}px, ${getDramaPupilY('yellow', yellowPupil.y)}px)`,
              transition: 'transform 0.1s ease-out, all 0.2s ease-out'
            }"
          />
        </div>
        <!-- 右眼+微笑弧线 -->
        <div class="relative">
          <svg
            v-if="isExcited && !isEyeClosed('yellow','right') && !isWinkEye('right') && (yellowMouthStyle === 'cat-smile' || yellowMouthStyle === 'grin')"
            class="absolute"
            width="16" height="8" viewBox="0 0 16 8"
            style="left: -2px; top: -10px;"
          >
            <path d="M2 6 Q8 0 14 6" fill="none" stroke="#2D2D2D" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <!-- 星星眼 -->
          <svg
            v-if="isExcited && !isEyeClosed('yellow','right') && !isWinkEye('right') && isStarEye()"
            class="eye-drama-star"
            width="18" height="18" viewBox="0 0 18 18"
          >
            <polygon points="9,1 11,6 16,7 12.5,11 13.5,16 9,13.5 5.5,16 5.5,11 2,7 7,6" fill="#2D2D2D"/>
          </svg>
          <!-- 爱心眼 -->
          <svg
            v-else-if="isExcited && !isEyeClosed('yellow','right') && !isWinkEye('right') && isHeartEye()"
            class="eye-drama-heart-svg"
            width="16" height="16" viewBox="0 0 16 16"
          >
            <path d="M8 14 C8 14, 2 9, 2 5.5 C2 3, 4 2, 5.5 2 C6.8 2, 8 3, 8 4.5 C8 3, 9.2 2, 10.5 2 C12 2, 14 3, 14 5.5 C14 9, 8 14, 8 14 Z" fill="#2D2D2D"/>
          </svg>
          <!-- 普通眼睛 -->
          <div
            v-else
            class="rounded-full transition-all duration-200"
            :class="isExcited && !isEyeClosed('yellow','right') && !isWinkEye('right') ? `eye-drama-${orangeYellowEyeDrama}` : ''"
            :style="{
              width: isEyeClosed('yellow','right') ? '14px' : (isWinkEye('right') ? '14px' : '12px'),
              height: isEyeClosed('yellow','right') ? '2px' : (isWinkEye('right') ? '2px' : (isSeductiveEye() ? '6px' : (isEyeSquinting('yellow','right') ? '4px' : '12px'))),
              backgroundColor: '#2D2D2D',
              transform: `translate(${yellowPupil.x}px, ${getDramaPupilY('yellow', yellowPupil.y)}px)`,
              transition: 'transform 0.1s ease-out, all 0.2s ease-out'
            }"
          />
        </div>
      </div>
      <!-- 嘴巴：睡觉时小O（打呼，脉动）/ 兴奋时搞怪嘴巴（每次随机一种） -->
      <div
        class="absolute"
        :class="[
          isSleeping && !isExcited ? 'yellow-mouth-pulse' : 'transition-all duration-300 ease-out',
          yellowMouthStyleClass
        ]"
        :style="yellowMouthStyleObj"
      >
        <!-- 复合嘴型：舌头 -->
        <div
          v-if="yellowMouthStyle === 'tongue'"
          class="absolute"
          style="width: 14px; height: 18px; background-color: #FF6B8A; border-radius: 0 0 9999px 9999px; left: 50%; top: 70%; transform: translateX(-50%);"
        />
        <!-- 复合嘴型：口水 -->
        <div
          v-if="yellowMouthStyle === 'drool'"
          class="absolute"
          style="width: 8px; height: 14px; background-color: #87CEEB; border-radius: 0 0 9999px 9999px; left: 60%; top: 75%;"
        />
        <!-- 复合嘴型：胡子嘴（两撇大胡子+小嘴在下面） -->
        <div
          v-if="yellowMouthStyle === 'moustache'"
          class="absolute"
          style="width: 24px; height: 10px; background-color: #2D2D2D; border-radius: 0 0 9999px 9999px; left: 50%; top: -8px; transform: translateX(-85%) rotate(-15deg);"
        />
        <div
          v-if="yellowMouthStyle === 'moustache'"
          class="absolute"
          style="width: 24px; height: 10px; background-color: #2D2D2D; border-radius: 0 0 9999px 9999px; left: 50%; top: -8px; transform: translateX(-15%) rotate(15deg);"
        />
        <!-- 猫嘴：圆润W形SVG（两头翘起+两个柔和弧线凹陷） -->
        <svg
          v-if="yellowMouthStyle === 'cat-smile'"
          class="absolute"
          width="38" height="18" viewBox="0 0 38 18"
          style="left: 0; top: 0;"
        >
          <path d="M4 5 Q11 14 19 7 Q27 14 34 5" fill="none" stroke="#2D2D2D" stroke-width="4" stroke-linecap="round"/>
        </svg>
        <!-- 搞笑3嘴：粉红数字3 SVG（嘟嘴亲亲，对称饱满，圆嘟嘟） -->
         <svg
           v-if="yellowMouthStyle === 'funny-three'"
           class="absolute"
           width="28" height="32" viewBox="0 0 28 32"
           style="left: 0; top: 0;"
         >
           <path d="M8 6 C18 3, 24 3, 22 10 C20 16, 12 14, 12 16 C12 18, 20 20, 22 24 C24 29, 18 30, 8 28" fill="none" stroke="#2D2D2D" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
         </svg>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 睡觉时身体轻微摇晃（呼吸感） */
.char-sleeping {
  animation: breathe 3s ease-in-out infinite;
}

@keyframes breathe {
  0%, 100% { filter: brightness(0.82); }
  50% { filter: brightness(1); }
}

/* 黄色角色睡觉时嘴巴脉动（忽大忽小，像打呼噜） */
.yellow-mouth-pulse {
  animation: yellow-mouth-pulse 1.5s ease-in-out infinite;
}

@keyframes yellow-mouth-pulse {
  0%, 100% { transform: scale(0.6); }
  50% { transform: scale(1.4); }
}

/* 黄色角色爱心嘴 */
.yellow-mouth-heart {
  clip-path: path('M15 25 C15 18, 0 10, 0 5 C0 0, 6 -3, 10 2 C12 4, 13 5, 15 5 C17 5, 18 4, 20 2 C24 -3, 30 0, 30 5 C30 10, 15 18, 15 25 Z');
}

/* 黄色角色云朵嘴（三个圆叠加的云朵） */
.yellow-mouth-cloud {
  clip-path: path('M10 18 A8 8 0 0 1 10 2 A8 8 0 0 1 22 4 A8 8 0 0 1 34 2 A8 8 0 0 1 34 18 A8 8 0 0 1 22 20 A8 8 0 0 1 10 18 Z');
}

/* 黄色角色漩涡嘴 */
.yellow-mouth-vortex {
  clip-path: polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%);
  animation: yellow-mouth-vortex-spin 1s linear infinite;
}
@keyframes yellow-mouth-vortex-spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 黄色角色吐舌嘴 */
.yellow-mouth-tongue {
  overflow: visible !important;
}

/* 橙色/黄色角色兴奋时眼睛戏 */
/* 1. grow：眼睛变大变小 */
.eye-drama-grow {
  animation: eye-drama-grow 0.8s ease-in-out infinite;
}
@keyframes eye-drama-grow {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.5); }
}

/* 2. spin：瞳孔滴溜溜转（整个眼珠绕圈转动） */
.eye-drama-spin {
  animation: eye-drama-spin 1s linear infinite;
}
@keyframes eye-drama-spin {
  0% { transform: rotate(0deg) translate(3px) rotate(0deg); }
  100% { transform: rotate(360deg) translate(3px) rotate(-360deg); }
}

/* 3. blink：疯狂眨眼 */
.eye-drama-blink {
  animation: eye-drama-blink 0.4s ease-in-out infinite;
}
@keyframes eye-drama-blink {
  0%, 100% { transform: scaleY(1); }
  50% { transform: scaleY(0.1); }
}

/* 4. wiggle：眼珠左右乱窜 */
.eye-drama-wiggle {
  animation: eye-drama-wiggle 0.5s ease-in-out infinite;
}
@keyframes eye-drama-wiggle {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-4px); }
  40% { transform: translateX(4px); }
  60% { transform: translateX(-3px); }
  80% { transform: translateX(3px); }
}

/* 5. star：星星眼闪烁 */
.eye-drama-star {
  animation: eye-drama-star-glow 1.2s ease-in-out infinite;
}
@keyframes eye-drama-star-glow {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.2); opacity: 1; }
}

/* 6. wink：单眼眨眼挑逗（通过 class 控制单眼闭合，无需整体动画） */
.eye-drama-wink {
  /* 单眼闭合由模板中的 isWinkingEye 判断控制，这里仅保留占位 */
}

/* 7. heart：爱心眼（爱心 SVG 替换瞳孔，无需整体动画，SVG 自身有脉动） */
.eye-drama-heart {
  /* 爱心眼由模板中的 SVG 渲染，这里仅保留占位 */
}

/* 8. seductive：媚眼（半闭眼+瞳孔上瞟，整体轻微上下浮动） */
.eye-drama-seductive {
  animation: eye-drama-seductive 2s ease-in-out infinite;
}
@keyframes eye-drama-seductive {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-1px); }
}

/* 9. flutter：飞眼（快速眨眼+瞳孔放大缩小） */
.eye-drama-flutter {
  animation: eye-drama-flutter 0.6s ease-in-out infinite;
}
@keyframes eye-drama-flutter {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(0.3); }
}

/* 爱心眼 SVG 脉动 */
.eye-drama-heart-svg {
  animation: eye-drama-heart-pulse 1s ease-in-out infinite;
}
@keyframes eye-drama-heart-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.25); }
}

/* 惊吓晃动（1s逐渐停止） */
.char-startled {
  animation: startled-shake 1s ease-out, breathe 3s ease-in-out infinite;
}

@keyframes startled-shake {
  0%   { transform: translateX(0) skewX(0deg); }
  8%   { transform: translateX(-7px) skewX(5deg); }
  16%  { transform: translateX(7px) skewX(-5deg); }
  24%  { transform: translateX(-6px) skewX(4deg); }
  32%  { transform: translateX(6px) skewX(-4deg); }
  40%  { transform: translateX(-5px) skewX(3deg); }
  50%  { transform: translateX(4px) skewX(-3deg); }
  60%  { transform: translateX(-3px) skewX(2deg); }
  70%  { transform: translateX(3px) skewX(-2deg); }
  80%  { transform: translateX(-2px) skewX(1deg); }
  90%  { transform: translateX(1px) skewX(0deg); }
  100% { transform: translateX(0) skewX(0deg); }
}

/* 惊吓感叹号气泡 */
.exclamation-bubble {
  position: absolute;
  top: -35px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 36px;
  font-weight: 900;
  color: #FFD700;
  z-index: 20;
  animation: pop 0.3s ease-out;
  text-shadow: 0 0 12px rgba(255, 215, 0, 0.7), 0 2px 4px rgba(0, 0, 0, 0.3);
  pointer-events: none;
}

@keyframes pop {
  0%   { transform: translateX(-50%) scale(0); opacity: 0; }
  50%  { transform: translateX(-50%) scale(1.4); opacity: 1; }
  100% { transform: translateX(-50%) scale(1); opacity: 1; }
}

/* Zzz 飘字动画 */
.zzz {
  position: absolute;
  font-size: 24px;
  font-weight: bold;
  color: rgba(255, 255, 255, 0.6);
  font-style: italic;
  animation: floatZ 3s ease-in-out infinite;
  pointer-events: none;
  z-index: 10;
  text-shadow: 0 0 8px rgba(255, 255, 255, 0.3);
}

.zzz-purple { color: rgba(168, 140, 255, 0.8); }
.zzz-black { color: rgba(180, 180, 180, 0.7); }
.zzz-orange { color: rgba(255, 180, 140, 0.8); }
.zzz-yellow { color: rgba(255, 230, 120, 0.8); }

/* ===== 兴奋跳舞动画（10种律动效果，节奏放慢）===== */
/* 每次进入兴奋状态，每个角色随机选一种，本次固定 */

/* 基础：禁用 transition，防止 transition-all 与动画 transform 冲突导致卡顿 */
.char-excited {
  transition: none !important;
  will-change: transform;
}

/* 1. bounce：上下起伏（慢节奏跳跃） */
.char-dance-bounce {
  animation: dance-bounce 1.4s ease-in-out infinite;
}
@keyframes dance-bounce {
  0%, 100% { transform: translateY(0) scale(1); }
  25% { transform: translateY(-22px) scale(1.03); }
  50% { transform: translateY(0) scale(1); }
  75% { transform: translateY(-18px) scale(1.02); }
}

/* 2. stretch：伸缩拉伸（纵向拉长压扁） */
.char-dance-stretch {
  animation: dance-stretch 1.6s ease-in-out infinite;
}
@keyframes dance-stretch {
  0%, 100% { transform: scaleY(1) scaleX(1); }
  25% { transform: scaleY(1.15) scaleX(0.9) translateY(-8px); }
  50% { transform: scaleY(1) scaleX(1); }
  75% { transform: scaleY(0.9) scaleX(1.1) translateY(-5px); }
}

/* 3. inflate：膨胀收缩（整体放大缩小，像呼吸鼓气） */
.char-dance-inflate {
  animation: dance-inflate 1.8s ease-in-out infinite;
}
@keyframes dance-inflate {
  0%, 100% { transform: scale(1) rotate(0deg); }
  30% { transform: scale(1.18) rotate(-3deg); }
  60% { transform: scale(0.92) rotate(3deg); }
}

/* 4. sway：左右摇摆（钟摆运动，极简关键帧 + ease-in-out = 丝滑） */
.char-dance-sway {
  animation: dance-sway 1.8s ease-in-out infinite;
  transform-origin: bottom center;
}
@keyframes dance-sway {
  0%, 100% { transform: rotate(-7deg) translateX(-7px); }
  50%      { transform: rotate(7deg) translateX(7px); }
}

/* 5. spin：旋转扭动（小幅旋转 + 上下） */
.char-dance-spin {
  animation: dance-spin 1.7s ease-in-out infinite;
}
@keyframes dance-spin {
  0%, 100% { transform: rotate(0deg) translateY(0); }
  20% { transform: rotate(-6deg) translateY(-12px); }
  40% { transform: rotate(0deg) translateY(0); }
  60% { transform: rotate(6deg) translateY(-15px); }
  80% { transform: rotate(0deg) translateY(0); }
}

/* 6. wobble：不倒翁（左右大幅倾斜，底部固定，像不倒翁晃悠） */
.char-dance-wobble {
  animation: dance-wobble 2s cubic-bezier(0.45, 0, 0.55, 1) infinite;
  transform-origin: bottom center;
}
@keyframes dance-wobble {
  0%   { transform: rotate(0deg); }
  15%  { transform: rotate(-12deg); }
  30%  { transform: rotate(9deg); }
  45%  { transform: rotate(-7deg); }
  60%  { transform: rotate(5deg); }
  75%  { transform: rotate(-3deg); }
  90%  { transform: rotate(1deg); }
  100% { transform: rotate(0deg); }
}

/* 7. wiggle：扭屁股（skewX 左右扭腰，像扭秧歌） */
.char-dance-wiggle {
  animation: dance-wiggle 1.2s ease-in-out infinite;
  transform-origin: bottom center;
}
@keyframes dance-wiggle {
  0%, 100% { transform: skewX(0deg) translateX(0); }
  20% { transform: skewX(8deg) translateX(-4px); }
  40% { transform: skewX(0deg) translateX(0); }
  60% { transform: skewX(-8deg) translateX(4px); }
  80% { transform: skewX(0deg) translateX(0); }
}

/* 8. drunk：醉酒（东倒西歪，幅度大且不规则，像喝醉了） */
.char-dance-drunk {
  animation: dance-drunk 2.2s ease-in-out infinite;
  transform-origin: bottom center;
}
@keyframes dance-drunk {
  0%   { transform: rotate(0deg) translateX(0) translateY(0); }
  12%  { transform: rotate(-10deg) translateX(-8px) translateY(-3px); }
  25%  { transform: rotate(6deg) translateX(5px) translateY(-6px); }
  38%  { transform: rotate(-8deg) translateX(-6px) translateY(-2px); }
  50%  { transform: rotate(12deg) translateX(8px) translateY(-8px); }
  62%  { transform: rotate(-5deg) translateX(-4px) translateY(-4px); }
  75%  { transform: rotate(9deg) translateX(6px) translateY(-5px); }
  88%  { transform: rotate(-6deg) translateX(-5px) translateY(-2px); }
  100% { transform: rotate(0deg) translateX(0) translateY(0); }
}

/* 9. nod：点头哈腰（前后倾斜，像在鞠躬致意） */
.char-dance-nod {
  animation: dance-nod 1.6s ease-in-out infinite;
  transform-origin: bottom center;
}
@keyframes dance-nod {
  0%, 100% { transform: rotate(0deg) translateY(0); }
  25% { transform: rotate(-6deg) translateY(8px); }
  50% { transform: rotate(0deg) translateY(0); }
  75% { transform: rotate(-6deg) translateY(8px); }
}

/* 10. hula：扭胯（左右平移 + 扭腰，像跳草裙舞） */
.char-dance-hula {
  animation: dance-hula 1.5s ease-in-out infinite;
  transform-origin: bottom center;
}
@keyframes dance-hula {
  0%, 100% { transform: translateX(0) skewX(0deg) rotate(0deg); }
  25% { transform: translateX(-10px) skewX(5deg) rotate(-2deg); }
  50% { transform: translateX(0) skewX(0deg) rotate(0deg); }
  75% { transform: translateX(10px) skewX(-5deg) rotate(2deg); }
}

/* 音乐符号飘动动画 */
.music-note {
  position: absolute;
  font-size: 28px;
  font-weight: bold;
  animation: floatMusic 2s ease-in-out infinite;
  pointer-events: none;
  z-index: 10;
  text-shadow: 0 0 10px currentColor;
}

.music-purple { color: rgba(168, 140, 255, 0.9); }
.music-black { color: rgba(200, 200, 200, 0.9); }
.music-orange { color: rgba(255, 180, 140, 0.9); }
.music-yellow { color: rgba(255, 230, 120, 0.9); }

@keyframes floatMusic {
  0% {
    opacity: 0;
    transform: translateY(0) translateX(0) scale(0.5) rotate(-15deg);
  }
  20% {
    opacity: 1;
    transform: translateY(-15px) translateX(5px) scale(1.1) rotate(0deg);
  }
  50% {
    opacity: 1;
    transform: translateY(-35px) translateX(-5px) scale(1.2) rotate(15deg);
  }
  80% {
    opacity: 0.8;
    transform: translateY(-55px) translateX(3px) scale(1.1) rotate(-10deg);
  }
  100% {
    opacity: 0;
    transform: translateY(-70px) translateX(0) scale(1.3) rotate(10deg);
  }
}

@keyframes floatZ {
  0% {
    opacity: 0;
    transform: translateY(0) scale(0.5);
  }
  20% {
    opacity: 1;
    transform: translateY(-10px) scale(1);
  }
  80% {
    opacity: 1;
    transform: translateY(-40px) scale(1.2);
  }
  100% {
    opacity: 0;
    transform: translateY(-60px) scale(1.5);
  }
}

@media (max-width: 1280px) {
  .characters-container {
    transform: scale(0.85) !important;
    transform-origin: bottom center;
  }
}
</style>

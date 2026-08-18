const SLOT_GAP_RPX = 12
const SLOT_MIN_HEIGHT_RPX = 64
const SLOT_MAX_HEIGHT_RPX = 120
const BOARD_TOP_RPX = 24
const PALETTE_GAP_RPX = 24
const PALETTE_SUBTITLE_OFFSET_RPX = 52
const PIECE_GAP_RPX = 12
const PIECE_TOP_PADDING_RPX = 40
const STAGE_BOTTOM_PADDING_RPX = 24
const MAX_PUZZLE_COUNT = 36

function normalizeCount(count, fallback) {
  const value = Number(count)
  if (!Number.isFinite(value) || value < 1) return fallback
  return Math.min(Math.floor(value), MAX_PUZZLE_COUNT)
}

function getColumnCount(count) {
  if (count <= 1) return 1
  // 5 块沿用首版视觉：两列四槽，最后一槽横跨整行。
  if (count === 5) return 2
  return Math.ceil(Math.sqrt(count))
}

function getPatternNames(patternNames, count) {
  const source = Array.isArray(patternNames) ? patternNames : []
  return Array.from({ length: count }, (_, index) => source[index] || `纹样${index + 1}`)
}

/**
 * 根据拼图块数量生成槽位和碎片坐标。所有坐标使用 px，直接供 movable-view 使用。
 */
function createPuzzleLayout({ count, stageWidth, scale, patternNames }) {
  const puzzleCount = normalizeCount(count, 1)
  const gap = SLOT_GAP_RPX * scale
  const columns = getColumnCount(puzzleCount)
  const rows = Math.ceil(puzzleCount / columns)
  const slotWidth = (stageWidth - (columns - 1) * gap) / columns
  const slotHeight = Math.max(
    SLOT_MIN_HEIGHT_RPX * scale,
    Math.min(SLOT_MAX_HEIGHT_RPX * scale, slotWidth * 0.62),
  )
  const names = getPatternNames(patternNames, puzzleCount)

  const slots = Array.from({ length: puzzleCount }, (_, index) => {
    const row = Math.floor(index / columns)
    const column = index % columns
    const remainingInLastRow = puzzleCount - row * columns
    const isSingleLastSlot = row === rows - 1 && remainingInLastRow === 1 && columns > 1
    return {
      id: `slot-${index + 1}`,
      number: index + 1,
      left: isSingleLastSlot ? 0 : column * (slotWidth + gap),
      top: BOARD_TOP_RPX * scale + row * (slotHeight + gap),
      width: isSingleLastSlot ? stageWidth : slotWidth,
      height: slotHeight,
      filled: false,
      filledName: '',
    }
  })

  const boardHeight = BOARD_TOP_RPX * scale + rows * slotHeight + (rows - 1) * gap
  const paletteTitleTop = boardHeight + PALETTE_GAP_RPX * scale
  const paletteSubtitleTop = paletteTitleTop + PALETTE_SUBTITLE_OFFSET_RPX * scale
  const pieceColumns = puzzleCount <= 5 ? 1 : columns
  const pieceGap = PIECE_GAP_RPX * scale
  const pieceCellWidth = (stageWidth - (pieceColumns - 1) * pieceGap) / pieceColumns
  const pieceWidth = pieceCellWidth * 0.9
  const pieceHeight = Math.max(SLOT_MIN_HEIGHT_RPX * scale * 0.72, Math.min(64 * scale, slotHeight * 0.72))
  const pieceTop = paletteSubtitleTop + PIECE_TOP_PADDING_RPX * scale
  const pieceRows = Math.ceil(puzzleCount / pieceColumns)

  const pieces = names.map((name, index) => {
    const row = Math.floor(index / pieceColumns)
    const column = index % pieceColumns
    const initialX = column * (pieceCellWidth + pieceGap) + (pieceCellWidth - pieceWidth) / 2
    const initialY = pieceTop + row * (pieceHeight + pieceGap)
    return {
      id: `pattern-${index + 1}`,
      name,
      symbol: name.slice(0, 1),
      width: pieceWidth,
      height: pieceHeight,
      initialX,
      initialY,
      x: initialX,
      y: initialY,
      placed: false,
    }
  })

  return {
    count: puzzleCount,
    slots,
    pieces,
    boardHeight,
    paletteTitleTop,
    paletteSubtitleTop,
    stageHeight: pieceTop + pieceRows * pieceHeight + Math.max(0, pieceRows - 1) * pieceGap + STAGE_BOTTOM_PADDING_RPX * scale,
  }
}

module.exports = { createPuzzleLayout, normalizeCount }

/**
 * ladder-renderer.js
 * Standalone SVG ladder diagram renderer for MBLogic-CLJ
 *
 * Converts JSON rung data from the API into visual SVG ladder diagrams.
 * Uses pure SVG and JavaScript with no external dependencies.
 */

// ==============================================================================
// CONSTANTS AND CONFIGURATION
// ==============================================================================

const LADDER = {
  // Symbol dimensions
  SYMBOL_WIDTH: 75,
  SYMBOL_HEIGHT: 70,

  // Spacing and margins
  CELL_PADDING: 2,
  TOP_MARGIN: 20,
  BOTTOM_MARGIN: 20,
  LEFT_MARGIN: 40,
  RIGHT_MARGIN: 40,

  // Power rails
  POWER_RAIL_X_LEFT: 10,
  POWER_RAIL_WIDTH: 2,
  POWER_RAIL_COLOR: '#000',

  // Drawing styles
  STROKE_WIDTH: 1,
  STROKE_COLOR: '#000',
  FILL_COLOR: 'white',
  TEXT_COLOR: '#333',
  TEXT_SIZE: '10px',
  TEXT_FAMILY: 'monospace'
};

// ==============================================================================
// SVG UTILITY FUNCTIONS
// ==============================================================================

function createSVGElement(tag, attrs = {}) {
  const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
  Object.entries(attrs).forEach(([k, v]) => {
    if (v !== null && v !== undefined) {
      el.setAttribute(k, v);
    }
  });
  return el;
}

function createGroup(x, y) {
  return createSVGElement('g', {
    transform: `translate(${x},${y})`
  });
}

function createLine(x1, y1, x2, y2, stroke = LADDER.STROKE_COLOR, width = LADDER.STROKE_WIDTH) {
  return createSVGElement('line', {
    'x1': x1,
    'y1': y1,
    'x2': x2,
    'y2': y2,
    'stroke': stroke,
    'stroke-width': width,
    'fill': 'none'
  });
}

function createRect(x, y, width, height, stroke = LADDER.STROKE_COLOR, fill = 'none', width_attr = LADDER.STROKE_WIDTH) {
  return createSVGElement('rect', {
    'x': x,
    'y': y,
    'width': width,
    'height': height,
    'stroke': stroke,
    'stroke-width': width_attr,
    'fill': fill
  });
}

function createCircle(cx, cy, r, stroke = LADDER.STROKE_COLOR, fill = 'none', width = LADDER.STROKE_WIDTH) {
  return createSVGElement('circle', {
    'cx': cx,
    'cy': cy,
    'r': r,
    'stroke': stroke,
    'stroke-width': width,
    'fill': fill
  });
}

function createEllipse(cx, cy, rx, ry, stroke = LADDER.STROKE_COLOR, fill = 'none', width = LADDER.STROKE_WIDTH) {
  return createSVGElement('ellipse', {
    'cx': cx,
    'cy': cy,
    'rx': rx,
    'ry': ry,
    'stroke': stroke,
    'stroke-width': width,
    'fill': fill
  });
}

function createText(text, x, y, fontSize = LADDER.TEXT_SIZE, anchor = 'middle') {
  const el = createSVGElement('text', {
    'x': x,
    'y': y,
    'font-size': fontSize,
    'font-family': LADDER.TEXT_FAMILY,
    'text-anchor': anchor,
    'fill': LADDER.TEXT_COLOR,
    'user-select': 'none',
    'class': 'address-label'
  });
  el.textContent = text;
  return el;
}

// ==============================================================================
// SYMBOL GENERATORS
// ==============================================================================

/**
 * Normally Open Contact (NO/noc)
 * Standard contact symbol - two parallel vertical lines
 */
function createContactNO() {
  const g = createGroup(0, 0);

  // Left and right connection lines
  g.appendChild(createLine(0, 0, 30, 0));
  g.appendChild(createLine(45, 0, LADDER.SYMBOL_WIDTH, 0));

  // Contact symbol (parallel lines)
  g.appendChild(createLine(30, -15, 30, 15));
  g.appendChild(createLine(45, -15, 45, 15));

  g.dataset.symbol = 'noc';
  return g;
}

/**
 * Normally Closed Contact (NC/ncc)
 * Contact symbol with diagonal line crossing
 */
function createContactNC() {
  const g = createGroup(0, 0);

  // Connection lines
  g.appendChild(createLine(0, 0, 30, 0));
  g.appendChild(createLine(45, 0, LADDER.SYMBOL_WIDTH, 0));

  // Contact symbol
  g.appendChild(createLine(30, -15, 30, 15));
  g.appendChild(createLine(45, -15, 45, 15));

  // Diagonal line indicating NC
  g.appendChild(createLine(30, -15, 45, 15));

  g.dataset.symbol = 'ncc';
  return g;
}

/**
 * Rising Edge Contact (Positive Edge Detect)
 */
function createContactRisingEdge() {
  const g = createGroup(0, 0);

  g.appendChild(createLine(0, 0, 30, 0));
  g.appendChild(createLine(45, 0, LADDER.SYMBOL_WIDTH, 0));

  // Contact symbol
  g.appendChild(createLine(30, -15, 30, 15));
  g.appendChild(createLine(45, -15, 45, 15));

  // Rising edge indicator (diagonal from bottom-left to top-right)
  g.appendChild(createLine(30, 10, 45, -10));

  g.dataset.symbol = 'nocpd';
  return g;
}

/**
 * Falling Edge Contact (Negative Edge Detect)
 */
function createContactFallingEdge() {
  const g = createGroup(0, 0);

  g.appendChild(createLine(0, 0, 30, 0));
  g.appendChild(createLine(45, 0, LADDER.SYMBOL_WIDTH, 0));

  // Contact symbol
  g.appendChild(createLine(30, -15, 30, 15));
  g.appendChild(createLine(45, -15, 45, 15));

  // Falling edge indicator (diagonal from top-left to bottom-right)
  g.appendChild(createLine(30, -10, 45, 10));

  g.dataset.symbol = 'nocnd';
  return g;
}

/**
 * Coil Output (OUT, SET, RST, PD)
 */
function createCoil(type = 'out') {
  const g = createGroup(0, 0);

  // Connection lines
  g.appendChild(createLine(0, 0, 20, 0));
  g.appendChild(createLine(55, 0, LADDER.SYMBOL_WIDTH, 0));

  // Coil symbol (circle/ellipse)
  g.appendChild(createEllipse(37.5, 0, 17, 12));

  // Label inside coil
  const labels = {
    'out': 'OUT',
    'set': 'SET',
    'rst': 'RST',
    'pd': 'PD'
  };

  if (labels[type]) {
    g.appendChild(createText(labels[type], 37.5, 4, '9px'));
  }

  g.dataset.symbol = type;
  return g;
}

/**
 * Comparison Contacts (EQ, NEQ, GT, LT, GE, LE)
 */
function createComparison(compType = 'eq') {
  const g = createGroup(0, 0);

  // Connection lines
  g.appendChild(createLine(0, 0, 15, 0));
  g.appendChild(createLine(60, 0, LADDER.SYMBOL_WIDTH, 0));

  // Symbol box
  g.appendChild(createRect(15, -15, 45, 30));

  // Comparison operator
  const ops = {
    'compeq': '=',
    'compne': '≠',
    'compgt': '>',
    'complt': '<',
    'compge': '≥',
    'comple': '≤'
  };

  const op = ops[compType] || '?';
  g.appendChild(createText(op, 37.5, 4, '11px'));

  g.dataset.symbol = compType;
  return g;
}

/**
 * Wire Elements (hbar, vbar, branch connectors)
 */
function createWireElement(wireType = 'hbar') {
  const g = createGroup(0, 0);

  switch(wireType) {
    case 'hbar':
      // Horizontal bar (wire on top of rung)
      g.appendChild(createLine(0, 0, LADDER.SYMBOL_WIDTH, 0));
      break;
    case 'vbar':
    case 'vbarr':
    case 'vbarl':
      // Vertical bar (wire going down)
      g.appendChild(createLine(LADDER.SYMBOL_WIDTH / 2, -LADDER.SYMBOL_HEIGHT / 2,
                              LADDER.SYMBOL_WIDTH / 2, LADDER.SYMBOL_HEIGHT / 2));
      break;
    case 'branchtl':
      // T-junction to left
      g.appendChild(createLine(0, 0, LADDER.SYMBOL_WIDTH, 0));
      g.appendChild(createLine(LADDER.SYMBOL_WIDTH, -20, LADDER.SYMBOL_WIDTH, 20));
      break;
    case 'branchtr':
      // T-junction to right
      g.appendChild(createLine(0, 0, LADDER.SYMBOL_WIDTH, 0));
      g.appendChild(createLine(0, -20, 0, 20));
      break;
    case 'branchttr':
      // Top-right corner
      g.appendChild(createLine(0, 0, LADDER.SYMBOL_WIDTH / 2, 0));
      g.appendChild(createLine(LADDER.SYMBOL_WIDTH / 2, 0, LADDER.SYMBOL_WIDTH / 2, LADDER.SYMBOL_HEIGHT / 2));
      break;
    case 'branchttl':
      // Top-left corner
      g.appendChild(createLine(LADDER.SYMBOL_WIDTH / 2, 0, LADDER.SYMBOL_WIDTH, 0));
      g.appendChild(createLine(LADDER.SYMBOL_WIDTH / 2, 0, LADDER.SYMBOL_WIDTH / 2, LADDER.SYMBOL_HEIGHT / 2));
      break;
  }

  g.dataset.symbol = wireType;
  return g;
}

/**
 * Block Instructions (TMR, CNTU, COPY, FILL, etc.)
 */
function createBlockInstruction(blockType, addresses = []) {
  const g = createGroup(0, 0);

  // Larger block rectangle
  const blockWidth = 75;
  const blockHeight = 70;

  g.appendChild(createRect(0, -blockHeight / 2, blockWidth, blockHeight));

  // Label
  const labels = {
    'tmr': 'TMR',
    'tmra': 'TMRA',
    'tmroff': 'TMROFF',
    'cntu': 'CNTU',
    'cntd': 'CNTD',
    'udc': 'UDC',
    'copy': 'COPY',
    'cpyblk': 'CPYBLK',
    'fill': 'FILL',
    'pack': 'PACK',
    'unpack': 'UNPACK',
    'findeq': 'FIND=',
    'findne': 'FIND≠',
    'findgt': 'FIND>',
    'findlt': 'FIND<',
    'findge': 'FIND≥',
    'findle': 'FIND≤',
    'mathdec': 'MATH',
    'mathhex': 'MATHX',
    'sum': 'SUM',
    'shfrg': 'SHFRG',
    'end': 'END',
    'endc': 'ENDC',
    'rt': 'RT',
    'rtc': 'RTC',
    'call': 'CALL',
    'for': 'FOR',
    'next': 'NEXT'
  };

  const label = labels[blockType] || blockType.toUpperCase();
  g.appendChild(createText(label, blockWidth / 2, 0, '11px'));

  // Addresses (stacked below label)
  if (addresses && addresses.length > 0) {
    const addrStartY = 10;
    addresses.slice(0, 3).forEach((addr, idx) => {
      g.appendChild(createText(addr, blockWidth / 2, addrStartY + (idx * 10), '8px'));
    });
  }

  g.dataset.symbol = blockType;
  return g;
}

/**
 * Empty Cell Placeholder
 */
function createEmptyCell() {
  const g = createGroup(0, 0);
  g.appendChild(createRect(2, -35, 70, 70, LADDER.STROKE_COLOR, 'none', '0.5'));
  g.dataset.symbol = 'empty';
  return g;
}

// ==============================================================================
// SYMBOL LOOKUP TABLE
// ==============================================================================

const SYMBOL_GENERATORS = {
  // Contacts
  'noc': createContactNO,
  'ncc': createContactNC,
  'nocpd': createContactRisingEdge,
  'nocnd': createContactFallingEdge,

  // Comparisons
  'compeq': () => createComparison('compeq'),
  'compne': () => createComparison('compne'),
  'compneq': () => createComparison('compne'),
  'compgt': () => createComparison('compgt'),
  'complt': () => createComparison('complt'),
  'compge': () => createComparison('compge'),
  'comple': () => createComparison('comple'),

  // Coils
  'out': () => createCoil('out'),
  'set': () => createCoil('set'),
  'rst': () => createCoil('rst'),
  'pd': () => createCoil('pd'),

  // Wires
  'hbar': () => createWireElement('hbar'),
  'vbar': () => createWireElement('vbar'),
  'vbarl': () => createWireElement('vbarl'),
  'vbarr': () => createWireElement('vbarr'),
  'branchtl': () => createWireElement('branchtl'),
  'branchtr': () => createWireElement('branchtr'),
  'branchttr': () => createWireElement('branchttr'),
  'branchttl': () => createWireElement('branchttl'),
  'branchl': () => createWireElement('branchtl'),
  'branchr': () => createWireElement('branchtr'),
  'brancht': () => createWireElement('hbar'),
  'branchx': () => createWireElement('hbar'),

  // Blocks (timers, counters, etc.)
  'tmr': () => createBlockInstruction('tmr'),
  'tmra': () => createBlockInstruction('tmra'),
  'tmroff': () => createBlockInstruction('tmroff'),
  'cntu': () => createBlockInstruction('cntu'),
  'cntd': () => createBlockInstruction('cntd'),
  'udc': () => createBlockInstruction('udc'),
  'copy': () => createBlockInstruction('copy'),
  'cpyblk': () => createBlockInstruction('cpyblk'),
  'fill': () => createBlockInstruction('fill'),
  'pack': () => createBlockInstruction('pack'),
  'unpack': () => createBlockInstruction('unpack'),
  'findeq': () => createBlockInstruction('findeq'),
  'findne': () => createBlockInstruction('findne'),
  'findneq': () => createBlockInstruction('findne'),
  'findgt': () => createBlockInstruction('findgt'),
  'findlt': () => createBlockInstruction('findlt'),
  'findge': () => createBlockInstruction('findge'),
  'findle': () => createBlockInstruction('findle'),
  'mathdec': () => createBlockInstruction('mathdec'),
  'mathhex': () => createBlockInstruction('mathhex'),
  'sum': () => createBlockInstruction('sum'),
  'shfrg': () => createBlockInstruction('shfrg'),
  'end': () => createBlockInstruction('end'),
  'endc': () => createBlockInstruction('endc'),
  'rt': () => createBlockInstruction('rt'),
  'rtc': () => createBlockInstruction('rtc'),
  'call': () => createBlockInstruction('call'),
  'for': () => createBlockInstruction('for'),
  'next': () => createBlockInstruction('next')
};

// ==============================================================================
// GRID LAYOUT ENGINE
// ==============================================================================

function calculateGridLayout(rung) {
  if (!rung) return null;

  const colWidth = LADDER.SYMBOL_WIDTH + LADDER.CELL_PADDING;
  const rowHeight = LADDER.SYMBOL_HEIGHT + LADDER.CELL_PADDING;

  const rows = rung.rows || 1;
  const cols = rung.cols || 1;

  return {
    rungWidth: cols * colWidth + LADDER.LEFT_MARGIN + LADDER.RIGHT_MARGIN,
    rungHeight: rows * rowHeight + LADDER.TOP_MARGIN + LADDER.BOTTOM_MARGIN,
    colWidth: colWidth,
    rowHeight: rowHeight,
    rows: rows,
    cols: cols,
    leftMargin: LADDER.LEFT_MARGIN,
    topMargin: LADDER.TOP_MARGIN
  };
}

function getCellPosition(cell, layout) {
  const x = layout.leftMargin + (cell.col * layout.colWidth) + (LADDER.SYMBOL_WIDTH / 2);
  const y = layout.topMargin + (cell.row * layout.rowHeight) + (LADDER.SYMBOL_HEIGHT / 2);
  return { x, y };
}

// ==============================================================================
// ADDRESS LABEL DISPLAY
// ==============================================================================

function createAddressLabel(address, x, y, labelType = 'default') {
  if (!address) return null;

  // Determine vertical offset based on label type
  let offsetY = y;

  switch(labelType) {
    case 'contact':
      offsetY = y - 20;  // Above contact
      break;
    case 'coil':
      offsetY = y + 18;  // Below coil
      break;
    case 'block':
      // Blocks handle their own labels
      return null;
    default:
      offsetY = y + 5;
  }

  return createText(address, x, offsetY, '9px');
}

// ==============================================================================
// MAIN RENDERER FUNCTION
// ==============================================================================

/**
 * Render a ladder rung to SVG
 * @param {Object} rung - Rung data from API
 * @param {HTMLElement} container - Container element to append SVG to
 */
function renderLadderSVG(rung, container) {
  if (!rung || !container) {
    console.error('Invalid rung or container');
    return;
  }

  // Calculate layout
  const layout = calculateGridLayout(rung);
  if (!layout) {
    console.error('Failed to calculate layout');
    return;
  }

  // Create SVG element
  const svg = createSVGElement('svg', {
    'xmlns': 'http://www.w3.org/2000/svg',
    'width': layout.rungWidth,
    'height': layout.rungHeight,
    'viewBox': `0 0 ${layout.rungWidth} ${layout.rungHeight}`,
    'class': 'ladder-svg'
  });

  // Add background
  svg.appendChild(createSVGElement('rect', {
    'x': 0,
    'y': 0,
    'width': layout.rungWidth,
    'height': layout.rungHeight,
    'fill': 'white',
    'stroke': '#ddd',
    'stroke-width': 1
  }));

  // Calculate power rail positions
  const powerRailLeftX = LADDER.LEFT_MARGIN / 2;
  const powerRailRightX = layout.rungWidth - (LADDER.RIGHT_MARGIN / 2);

  // Draw power rails
  const centerY = layout.topMargin + (layout.rowHeight * layout.rows / 2);
  svg.appendChild(createLine(powerRailLeftX, layout.topMargin, powerRailLeftX,
                            layout.topMargin + layout.rowHeight * layout.rows,
                            LADDER.POWER_RAIL_COLOR, 2));
  svg.appendChild(createLine(powerRailRightX, layout.topMargin, powerRailRightX,
                            layout.topMargin + layout.rowHeight * layout.rows,
                            LADDER.POWER_RAIL_COLOR, 2));

  // Draw cells
  if (rung.cells && rung.cells.length > 0) {
    rung.cells.forEach((cell, idx) => {
      try {
        const pos = getCellPosition(cell, layout);

        // Get symbol generator
        const symbolGenerator = SYMBOL_GENERATORS[cell.symbol];
        if (!symbolGenerator) {
          console.warn(`Unknown symbol: ${cell.symbol}`);
          svg.appendChild(createEmptyCell());
          return;
        }

        // Create and position symbol
        const symbolGroup = createGroup(pos.x - LADDER.SYMBOL_WIDTH / 2, pos.y - LADDER.SYMBOL_HEIGHT / 2);
        symbolGroup.appendChild(symbolGenerator());
        svg.appendChild(symbolGroup);

        // Add address label if present
        if (cell.address && cell.address !== '-') {
          const label = createAddressLabel(cell.address, pos.x, pos.y,
                                          cell.type === ':contact' ? 'contact' : 'coil');
          if (label) svg.appendChild(label);
        }
      } catch (error) {
        console.error(`Error rendering cell ${idx}:`, error);
      }
    });
  }

  // Clear and append to container
  if (container instanceof HTMLElement) {
    container.innerHTML = '';
    container.appendChild(svg);
  }
}

// ==============================================================================
// EXPORT
// ==============================================================================

// Export for use in browser
if (typeof window !== 'undefined') {
  window.renderLadderSVG = renderLadderSVG;
  window.LADDER = LADDER;
}

/* =================================================================
 *  screen-paint.js — 拼接大屏画布渲染引擎
 *  负责：网格渲染、窗口 DOM 渲染、自适应缩放、坐标换算
 * ================================================================= */

var ScreenPaint = {
  // 状态
  currentScreen: null,       // 当前大屏详情 ScreenDetailVO
  cells: [],                 // 单元列表 CellVO[]
  windows: [],               // 窗口列表 ScreenWindowVO[]
  cellSize: 200,             // 自适应计算后的单元像素尺寸
  canvasPixelW: 0,           // 画布总像素宽度
  canvasPixelH: 0,           // 画布总像素高度
  actualW: 0,                // 大屏实际总宽度（cellWidth × cols）
  actualH: 0,                // 大屏实际总高度（cellHeight × rows）
  containerEl: null,         // 画布容器 DOM
  canvasEl: null,            // 画布 DOM

  // ---------- 初始化 ----------
  init: function () {
    this.containerEl = document.getElementById('canvasContainer');
    this.canvasEl = document.getElementById('screenCanvas');
  },

  // ---------- 渲染完整画布 ----------
  render: function (screenDetail, windows) {
    this.currentScreen = screenDetail;
    this.cells = screenDetail.cells || [];
    this.windows = windows || [];

    var rows = screenDetail.rowsCount;
    var cols = screenDetail.colsCount;
    var cellW = screenDetail.cellWidth || 1920;
    var cellH = screenDetail.cellHeight || 1080;

    // 计算实际总分辨率
    this.actualW = cellW * cols;
    this.actualH = cellH * rows;

    // 自适应单元尺寸
    this.cellSize = this.calcCellSize(rows, cols);

    // 画布像素尺寸
    this.canvasPixelW = this.cellSize * cols;
    this.canvasPixelH = this.cellSize * rows;

    // 设置画布大小
    this.canvasEl.style.width = this.canvasPixelW + 'px';
    this.canvasEl.style.height = this.canvasPixelH + 'px';
    this.canvasEl.style.display = '';
    this.canvasEl.innerHTML = '';

    // 渲染网格
    this.renderGrid(rows, cols);

    // 渲染窗口
    this.renderWindows();
  },

  // ---------- 自适应单元尺寸计算 ----------
  calcCellSize: function (rows, cols) {
    if (!this.containerEl) return 200;

    var containerW = this.containerEl.clientWidth - 20;   // 留边距
    var containerH = this.containerEl.clientHeight - 20;
    var gap = 2;

    var w = Math.floor((containerW - gap * (cols - 1)) / cols);
    var h = Math.floor((containerH - gap * (rows - 1)) / rows);
    return Math.max(Math.min(w, h), 120);  // 最小 120px
  },

  // ---------- 渲染网格 ----------
  renderGrid: function (rows, cols) {
    var fragment = document.createDocumentFragment();

    // 创建网格容器
    var gridEl = document.createElement('div');
    gridEl.className = 'canvas-grid';
    gridEl.style.width = this.canvasPixelW + 'px';
    gridEl.style.height = this.canvasPixelH + 'px';
    gridEl.style.gridTemplateColumns = 'repeat(' + cols + ', 1fr)';
    gridEl.style.gridTemplateRows = 'repeat(' + rows + ', 1fr)';

    for (var r = 0; r < rows; r++) {
      for (var c = 0; c < cols; c++) {
        var cell = this.getCellAt(r, c);
        var cellEl = document.createElement('div');
        cellEl.className = 'canvas-cell';

        // 单元标签
        var label = document.createElement('div');
        label.className = 'cell-label';

        if (cell && cell.deviceId) {
          cellEl.classList.add(cell.online === 1 ? 'cell-bound' : 'cell-offline');
          label.innerHTML = '<span class="cell-device">' + escapeHtml(cell.deviceName || '设备#' + cell.deviceId) + '</span><br><span class="cell-channel">' + escapeHtml(cell.channelName || '') + '</span>';
        } else {
          cellEl.classList.add('cell-unbound');
          label.textContent = '未绑定';
        }

        // 单元索引
        var indexEl = document.createElement('span');
        indexEl.className = 'cell-index';
        indexEl.textContent = r + ',' + c;

        cellEl.appendChild(label);
        cellEl.appendChild(indexEl);
        gridEl.appendChild(cellEl);
      }
    }

    this.canvasEl.appendChild(gridEl);
  },

  // ---------- 渲染所有窗口 ----------
  renderWindows: function () {
    for (var i = 0; i < this.windows.length; i++) {
      this.renderWindowDom(this.windows[i]);
    }
  },

  // ---------- 渲染单个窗口 DOM ----------
  renderWindowDom: function (winData) {
    var el = document.createElement('div');
    el.className = 'canvas-window';
    el.setAttribute('data-window-id', winData.windowId);

    // 窗口像素位置和尺寸
    var pixelX = this.toPixelX(winData.x || 0);
    var pixelY = this.toPixelY(winData.y || 0);
    var pixelW = this.toPixelW(winData.width || 960);
    var pixelH = this.toPixelH(winData.height || 540);

    el.style.left = pixelX + 'px';
    el.style.top = pixelY + 'px';
    el.style.width = pixelW + 'px';
    el.style.height = pixelH + 'px';

    // 降级标记
    if (winData.degraded === 1) {
      el.classList.add('window-degraded');
    }

    // 标题栏
    var titlebar = document.createElement('div');
    titlebar.className = 'window-titlebar';

    // 同步状态标记
    var syncBadge = document.createElement('span');
    syncBadge.className = 'window-sync-badge ' + (winData.syncStatus || 'synced');
    syncBadge.title = winData.syncStatus === 'pending' ? '待同步' : winData.syncStatus === 'failed' ? '同步失败' : '已同步';
    titlebar.appendChild(syncBadge);

    // 标题
    var title = document.createElement('span');
    title.className = 'window-title';
    title.textContent = (winData.deviceName || '设备#' + winData.deviceId) + ' / ' + (winData.channelName || '');
    titlebar.appendChild(title);

    // 关闭按钮
    var closeBtn = document.createElement('button');
    closeBtn.className = 'window-close-btn';
    closeBtn.textContent = '×';
    closeBtn.title = '关闭窗口';
    closeBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      if (typeof ScreenInteract !== 'undefined') {
        ScreenInteract.closeWindow(el, winData);
      }
    });
    titlebar.appendChild(closeBtn);

    el.appendChild(titlebar);

    // 内容区
    var content = document.createElement('div');
    content.className = 'window-content';
    content.style.width = (pixelW - 2) + 'px';
    content.style.height = (pixelH - 24) + 'px';

    var contentInner = document.createElement('div');
    contentInner.className = 'window-content-inner';
    contentInner.style.width = '100%';
    contentInner.style.height = '100%';

    if (winData.sourceUrl) {
      // 有播放地址时用 iframe 嵌入
      contentInner.style.pointerEvents = 'auto';
      var iframe = document.createElement('iframe');
      iframe.src = winData.sourceUrl;
      iframe.className = 'window-iframe';
      iframe.setAttribute('allow', 'autoplay; encrypted-media; picture-in-picture; fullscreen');
      iframe.setAttribute('allowfullscreen', '');
      iframe.setAttribute('referrerpolicy', 'no-referrer');
      contentInner.appendChild(iframe);
    } else {
      // 无播放地址时显示文字
      contentInner.style.display = 'flex';
      contentInner.style.alignItems = 'center';
      contentInner.style.justifyContent = 'center';
      contentInner.style.fontSize = '10px';
      contentInner.style.color = 'rgba(255, 255, 255, 0.3)';
      contentInner.textContent = (winData.sourceType || '') + (winData.sourceType && winData.sourceUrl ? ' — ' : '') + (winData.sourceUrl || '');
    }

    content.appendChild(contentInner);
    el.appendChild(content);

    // 降级提示
    if (winData.degraded === 1) {
      var tip = document.createElement('div');
      tip.className = 'window-degraded-tip';
      tip.textContent = '部分设备离线，画面不完整';
      el.appendChild(tip);
    }

    // 根据窗口覆盖单元的输出设备能力，决定是否显示缩放把手和可移动样式
    var caps = this.getWindowCapabilities(winData);
    if (!caps.canMove) {
      el.classList.add('not-movable');
    }
    if (!caps.canResize) {
      el.classList.add('not-resizable');
    } else {
      // 8个缩放把手
      var handles = ['n', 's', 'e', 'w', 'nw', 'ne', 'sw', 'se'];
      for (var i = 0; i < handles.length; i++) {
        var handle = document.createElement('div');
        handle.className = 'window-resize-handle ' + handles[i];
        el.appendChild(handle);
      }
    }

    // 绑定交互事件
    if (typeof ScreenInteract !== 'undefined') {
      ScreenInteract.attachEvents(el, winData);
    }

    this.canvasEl.appendChild(el);
  },

  // ---------- 刷新单个窗口 DOM ----------
  refreshWindowDom: function (windowId, winData) {
    // 移除旧 DOM
    var oldEl = this.canvasEl.querySelector('.canvas-window[data-window-id="' + windowId + '"]');
    if (oldEl) {
      oldEl.remove();
    }
    // 重新渲染
    if (winData) {
      this.renderWindowDom(winData);
    }
  },

  // ---------- 坐标换算 ----------

  /** 实际 X → 画布像素 X */
  toPixelX: function (actualX) {
    if (this.actualW === 0) return actualX;
    return Math.round(actualX * this.canvasPixelW / this.actualW);
  },

  /** 实际 Y → 画布像素 Y */
  toPixelY: function (actualY) {
    if (this.actualH === 0) return actualY;
    return Math.round(actualY * this.canvasPixelH / this.actualH);
  },

  /** 实际宽度 → 画布像素宽度 */
  toPixelW: function (actualW) {
    if (this.actualW === 0) return actualW;
    return Math.round(actualW * this.canvasPixelW / this.actualW);
  },

  /** 实际高度 → 画布像素高度 */
  toPixelH: function (actualH) {
    if (this.actualH === 0) return actualH;
    return Math.round(actualH * this.canvasPixelH / this.actualH);
  },

  /** 画布像素 X → 实际 X */
  toActualX: function (pixelX) {
    if (this.canvasPixelW === 0) return pixelX;
    return Math.round(pixelX * this.actualW / this.canvasPixelW);
  },

  /** 画布像素 Y → 实际 Y */
  toActualY: function (pixelY) {
    if (this.canvasPixelH === 0) return pixelY;
    return Math.round(pixelY * this.actualH / this.canvasPixelH);
  },

  /** 画布像素宽度 → 实际宽度 */
  toActualW: function (pixelW) {
    if (this.canvasPixelW === 0) return pixelW;
    return Math.round(pixelW * this.actualW / this.canvasPixelW);
  },

  /** 画布像素高度 → 实际高度 */
  toActualH: function (pixelH) {
    if (this.canvasPixelH === 0) return pixelH;
    return Math.round(pixelH * this.actualH / this.canvasPixelH);
  },

  // ---------- 单元定位 ----------

  /** 根据行列获取单元数据 */
  getCellAt: function (row, col) {
    for (var i = 0; i < this.cells.length; i++) {
      if (this.cells[i].rowIndex === row && this.cells[i].colIndex === col) {
        return this.cells[i];
      }
    }
    return null;
  },

  /** 像素位置 → 单元索引 */
  getCellAtPos: function (pixelX, pixelY) {
    if (!this.currentScreen) return null;
    var cols = this.currentScreen.colsCount;
    var rows = this.currentScreen.rowsCount;
    var cellPW = this.canvasPixelW / cols;
    var cellPH = this.canvasPixelH / rows;
    return {
      row: Math.floor(pixelY / cellPH),
      col: Math.floor(pixelX / cellPW),
    };
  },

  /** 单元位置 → 绑定的设备 */
  getDeviceForCell: function (row, col) {
    var cell = this.getCellAt(row, col);
    if (cell && cell.deviceId) {
      return cell;
    }
    return null;
  },

  /** 返回窗口覆盖的单元列表 */
  getCellsForWindow: function (winData) {
    var cols = this.currentScreen.colsCount;
    var rows = this.currentScreen.rowsCount;
    var cellW = this.currentScreen.cellWidth || 1920;
    var cellH = this.currentScreen.cellHeight || 1080;

    var wLeft = winData.x || 0;
    var wTop = winData.y || 0;
    var wRight = wLeft + (winData.width || 960);
    var wBottom = wTop + (winData.height || 540);

    var startCol = Math.max(0, Math.floor(wLeft / cellW));
    var endCol = Math.min(cols - 1, Math.floor((wRight - 1) / cellW));
    var startRow = Math.max(0, Math.floor(wTop / cellH));
    var endRow = Math.min(rows - 1, Math.floor((wBottom - 1) / cellH));

    var cells = [];
    for (var r = startRow; r <= endRow; r++) {
      for (var c = startCol; c <= endCol; c++) {
        var cell = this.getCellAt(r, c);
        if (cell) {
          cells.push(cell);
        }
      }
    }
    return cells;
  },

  /** 根据窗口覆盖的单元，聚合各输出设备的能力限制 */
  getWindowCapabilities: function (winData) {
    var cells = this.getCellsForWindow(winData);
    var canMove = true;
    var canResize = true;
    var canOverlay = true;
    for (var i = 0; i < cells.length; i++) {
      var c = cells[i];
      if (c.supportMove != null && c.supportMove === 0) canMove = false;
      if (c.supportResize != null && c.supportResize === 0) canResize = false;
      if (c.supportOverlay != null && c.supportOverlay === 0) canOverlay = false;
    }
    return { canMove: canMove, canResize: canResize, canOverlay: canOverlay };
  },

  // ---------- 清空画布 ----------
  clear: function () {
    if (this.canvasEl) {
      this.canvasEl.innerHTML = '';
      this.canvasEl.style.display = 'none';
    }
    this.currentScreen = null;
    this.cells = [];
    this.windows = [];
    this.cellSize = 200;
    this.canvasPixelW = 0;
    this.canvasPixelH = 0;
    this.actualW = 0;
    this.actualH = 0;
  },

  // ---------- 窗口列表管理 ----------
  getWindowById: function (windowId) {
    for (var i = 0; i < this.windows.length; i++) {
      if (this.windows[i].windowId === windowId) {
        return this.windows[i];
      }
    }
    return null;
  },

  addWindow: function (winData) {
    this.windows.push(winData);
    this.renderWindowDom(winData);
  },

  removeWindow: function (windowId) {
    for (var i = 0; i < this.windows.length; i++) {
      if (this.windows[i].windowId === windowId) {
        this.windows.splice(i, 1);
        break;
      }
    }
    var el = this.canvasEl.querySelector('.canvas-window[data-window-id="' + windowId + '"]');
    if (el) el.remove();
  },

  updateWindow: function (windowId, newData) {
    for (var i = 0; i < this.windows.length; i++) {
      if (this.windows[i].windowId === windowId) {
        // 合并数据
        for (var key in newData) {
          if (newData.hasOwnProperty(key)) {
            this.windows[i][key] = newData[key];
          }
        }
        break;
      }
    }
    this.refreshWindowDom(windowId, this.getWindowById(windowId));
  },
};

// 初始化（页面加载后）
document.addEventListener('DOMContentLoaded', function () {
  ScreenPaint.init();
});
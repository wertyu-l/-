/* =================================================================
 *  screen-interact.js — 窗口交互
 *  负责：拖拽移动、边缘缩放、关闭确认、边界约束
 * ================================================================= */

var ScreenInteract = {
  // 拖拽状态
  dragState: null,      // { windowEl, windowData, startX, startY, origLeft, origTop }
  // 缩放状态
  resizeState: null,    // { windowEl, windowData, direction, startX, startY, origLeft, origTop, origWidth, origHeight }
  // 是否正在交互中
  interacting: false,

  // ---------- 绑定事件 ----------
  attachEvents: function (windowEl, windowData) {
    // 标题栏拖拽
    var titlebar = windowEl.querySelector('.window-titlebar');
    if (titlebar) {
      titlebar.addEventListener('mousedown', (function (el, data) {
        return function (e) {
          if (e.target.classList.contains('window-close-btn')) return; // 关闭按钮不拖拽
          ScreenInteract.startDrag(e, el, data);
        };
      })(windowEl, windowData));
    }

    // 缩放把手
    var handles = windowEl.querySelectorAll('.window-resize-handle');
    for (var i = 0; i < handles.length; i++) {
      handles[i].addEventListener('mousedown', (function (el, data, dir) {
        return function (e) {
          e.stopPropagation();
          e.preventDefault();
          ScreenInteract.startResize(e, el, data, dir);
        };
      })(windowEl, windowData, handles[i].classList[1])); // classList[1] = direction (n/s/e/w/nw/ne/sw/se)
    }
  },

  // ---------- 开始拖拽 ----------
  startDrag: function (e, windowEl, windowData) {
    // 检查设备是否支持移动
    if (!this.canMove(windowData)) return;

    e.preventDefault();
    this.dragState = {
      windowEl: windowEl,
      windowData: windowData,
      startX: e.clientX,
      startY: e.clientY,
      origLeft: parseInt(windowEl.style.left, 10) || 0,
      origTop: parseInt(windowEl.style.top, 10) || 0,
    };
    this.interacting = true;
    windowEl.classList.add('window-dragging');
  },

  // ---------- 开始缩放 ----------
  startResize: function (e, windowEl, windowData, direction) {
    // 检查设备是否支持缩放
    if (!this.canResize(windowData)) return;

    e.preventDefault();
    this.resizeState = {
      windowEl: windowEl,
      windowData: windowData,
      direction: direction,
      startX: e.clientX,
      startY: e.clientY,
      origLeft: parseInt(windowEl.style.left, 10) || 0,
      origTop: parseInt(windowEl.style.top, 10) || 0,
      origWidth: parseInt(windowEl.style.width, 10) || 0,
      origHeight: parseInt(windowEl.style.height, 10) || 0,
    };
    this.interacting = true;
    windowEl.classList.add('window-resizing');
  },

  // ---------- 拖拽结束 ----------
  endDrag: function () {
    if (!this.dragState) return;
    var state = this.dragState;
    state.windowEl.classList.remove('window-dragging');

    // 计算实际坐标
    var pixelLeft = parseInt(state.windowEl.style.left, 10);
    var pixelTop = parseInt(state.windowEl.style.top, 10);
    var pixelW = parseInt(state.windowEl.style.width, 10) || 0;
    var pixelH = parseInt(state.windowEl.style.height, 10) || 0;

    // 检查叠加限制：若新位置在某个不支持叠加的单元上产生重叠，则回退
    if (this.checkOverlap({left: pixelLeft, top: pixelTop, width: pixelW, height: pixelH}, state.windowData.windowId)) {
      state.windowEl.style.left = state.origLeft + 'px';
      state.windowEl.style.top = state.origTop + 'px';
      showToast('目标位置与已有窗口重叠，设备不支持窗口叠加，已回退', 'warning');
      this.dragState = null;
      this.interacting = false;
      return;
    }

    var actualX = ScreenPaint.toActualX(pixelLeft);
    var actualY = ScreenPaint.toActualY(pixelTop);
    var actualW = ScreenPaint.toActualW(pixelW);
    var actualH = ScreenPaint.toActualH(pixelH);

    // 检查窗口数上限：若新位置所在单元已达窗口上限，则回退
    if (this.checkMaxWindows({x: actualX, y: actualY, width: actualW, height: actualH}, state.windowData.windowId)) {
      state.windowEl.style.left = state.origLeft + 'px';
      state.windowEl.style.top = state.origTop + 'px';
      showToast('目标设备窗口数已达上限，已回退', 'warning');
      this.dragState = null;
      this.interacting = false;
      return;
    }

    // 检查移动能力：若新位置覆盖的单元不支持移动，则回退
    if (!this.checkCapability({x: actualX, y: actualY, width: actualW, height: actualH}, 'supportMove')) {
      state.windowEl.style.left = state.origLeft + 'px';
      state.windowEl.style.top = state.origTop + 'px';
      showToast('目标设备不支持窗口移动，已回退', 'warning');
      this.dragState = null;
      this.interacting = false;
      return;
    }

    // 更新窗口数据
    var windowData = state.windowData;
    windowData.x = actualX;
    windowData.y = actualY;

    // 调用 API 保存（仅位置）
    this.syncWindowMove(windowData);

    this.dragState = null;
    this.interacting = false;
  },

  // ---------- 缩放结束 ----------
  endResize: function () {
    if (!this.resizeState) return;
    var state = this.resizeState;
    state.windowEl.classList.remove('window-resizing');

    // 计算实际坐标和尺寸
    var pixelLeft = parseInt(state.windowEl.style.left, 10);
    var pixelTop = parseInt(state.windowEl.style.top, 10);
    var pixelW = parseInt(state.windowEl.style.width, 10);
    var pixelH = parseInt(state.windowEl.style.height, 10);

    // 检查叠加限制：若新尺寸在某个不支持叠加的单元上产生重叠，则回退
    if (this.checkOverlap({left: pixelLeft, top: pixelTop, width: pixelW, height: pixelH}, state.windowData.windowId)) {
      state.windowEl.style.left = state.origLeft + 'px';
      state.windowEl.style.top = state.origTop + 'px';
      state.windowEl.style.width = state.origWidth + 'px';
      state.windowEl.style.height = state.origHeight + 'px';
      // 恢复内容区尺寸
      var content = state.windowEl.querySelector('.window-content');
      if (content) {
        content.style.width = (state.origWidth - 2) + 'px';
        content.style.height = (state.origHeight - 24) + 'px';
      }
      showToast('目标尺寸与已有窗口重叠，设备不支持窗口叠加，已回退', 'warning');
      this.resizeState = null;
      this.interacting = false;
      return;
    }

    var actualX = ScreenPaint.toActualX(pixelLeft);
    var actualY = ScreenPaint.toActualY(pixelTop);
    var actualW = ScreenPaint.toActualW(pixelW);
    var actualH = ScreenPaint.toActualH(pixelH);

    // 检查窗口数上限：若新尺寸所在单元已达窗口上限，则回退
    if (this.checkMaxWindows({x: actualX, y: actualY, width: actualW, height: actualH}, state.windowData.windowId)) {
      state.windowEl.style.left = state.origLeft + 'px';
      state.windowEl.style.top = state.origTop + 'px';
      state.windowEl.style.width = state.origWidth + 'px';
      state.windowEl.style.height = state.origHeight + 'px';
      var content = state.windowEl.querySelector('.window-content');
      if (content) {
        content.style.width = (state.origWidth - 2) + 'px';
        content.style.height = (state.origHeight - 24) + 'px';
      }
      showToast('目标设备窗口数已达上限，已回退', 'warning');
      this.resizeState = null;
      this.interacting = false;
      return;
    }

    // 检查缩放能力：若新尺寸覆盖的单元不支持缩放，则回退
    if (!this.checkCapability({x: actualX, y: actualY, width: actualW, height: actualH}, 'supportResize')) {
      state.windowEl.style.left = state.origLeft + 'px';
      state.windowEl.style.top = state.origTop + 'px';
      state.windowEl.style.width = state.origWidth + 'px';
      state.windowEl.style.height = state.origHeight + 'px';
      var content = state.windowEl.querySelector('.window-content');
      if (content) {
        content.style.width = (state.origWidth - 2) + 'px';
        content.style.height = (state.origHeight - 24) + 'px';
      }
      showToast('目标设备不支持窗口缩放，已回退', 'warning');
      this.resizeState = null;
      this.interacting = false;
      return;
    }

    // 更新窗口数据
    var windowData = state.windowData;
    windowData.x = actualX;
    windowData.y = actualY;
    windowData.width = actualW;
    windowData.height = actualH;

    // 调用 API 保存
    this.syncWindowPosition(windowData);

    this.resizeState = null;
    this.interacting = false;
  },

  // ---------- 关闭窗口 ----------
  closeWindow: function (windowEl, windowData) {
    if (!confirm('确定要关闭窗口 "' + (windowData.deviceName || '') + ' / ' + (windowData.channelName || '') + '" 吗？')) {
      return;
    }

    var windowId = windowData.windowId;
    var screenId = AppMode.currentScreenId;

    WindowApi.close(screenId, windowId).then(function () {
      showToast('窗口已关闭', 'success');
      ScreenPaint.removeWindow(windowId);
      // 刷新信号源面板的窗口计数
      if (typeof SignalSource !== 'undefined' && SignalSource.refreshWindowCounts) {
        SignalSource.refreshWindowCounts();
      }
    }).catch(function (err) {
      showToast('关闭窗口失败: ' + err.message, 'error');
    });
  },

  // ---------- 同步仅位置到后台（不触发缩放校验） ----------
  syncWindowMove: function (windowData) {
    var screenId = AppMode.currentScreenId;
    if (!screenId) return;

    var reqX = windowData.x;
    var reqY = windowData.y;

    WindowApi.update(screenId, windowData.windowId, {
      x: reqX,
      y: reqY,
    }).then(function (result) {
      if (result && result.data) {
        var updated = result.data;
        // 服务端回退（移动跨到不支持移动的通道）：同步 DOM 与数据回原位置
        if ((updated.x != null && updated.x !== reqX) || (updated.y != null && updated.y !== reqY)) {
          ScreenInteract.applyServerRevert(windowData, updated, '目标设备不支持窗口移动，已回退');
          return;
        }
        ScreenPaint.updateWindow(windowData.windowId, {
          syncStatus: updated.syncStatus || 'synced',
          degraded: updated.degraded || 0,
        });
      }
    }).catch(function (err) {
      showToast('保存窗口位置失败: 无法连接服务器，请检查网络', 'error');
    });
  },

  // ---------- 同步位置/尺寸到后台 ----------
  syncWindowPosition: function (windowData) {
    var screenId = AppMode.currentScreenId;
    if (!screenId) return;

    var reqX = windowData.x;
    var reqY = windowData.y;
    var reqW = windowData.width;
    var reqH = windowData.height;

    WindowApi.update(screenId, windowData.windowId, {
      x: reqX,
      y: reqY,
      width: reqW,
      height: reqH,
    }).then(function (result) {
      // 更新同步状态
      if (result && result.data) {
        var updated = result.data;
        // 服务端回退（缩放/移动跨到不支持对应能力的通道）：同步 DOM 与数据回原位置/尺寸
        if ((updated.x != null && updated.x !== reqX) ||
            (updated.y != null && updated.y !== reqY) ||
            (updated.width != null && updated.width !== reqW) ||
            (updated.height != null && updated.height !== reqH)) {
          ScreenInteract.applyServerRevert(windowData, updated, '目标设备不支持该窗口操作，已回退');
          return;
        }
        ScreenPaint.updateWindow(windowData.windowId, {
          syncStatus: updated.syncStatus || 'synced',
          degraded: updated.degraded || 0,
        });
      }
    }).catch(function (err) {
      showToast('保存窗口位置失败: 无法连接服务器，请检查网络', 'error');
    });
  },

  // ---------- 服务端回退：将窗口 DOM 与数据回退到服务端返回的位置/尺寸 ----------
  applyServerRevert: function (windowData, updated, toastMsg) {
    var el = ScreenPaint.canvasEl.querySelector('.canvas-window[data-window-id="' + windowData.windowId + '"]');
    if (el) {
      var px = ScreenPaint.toPixelX(updated.x != null ? updated.x : (windowData.x || 0));
      var py = ScreenPaint.toPixelY(updated.y != null ? updated.y : (windowData.y || 0));
      var pw = ScreenPaint.toPixelW(updated.width != null ? updated.width : (windowData.width || 960));
      var ph = ScreenPaint.toPixelH(updated.height != null ? updated.height : (windowData.height || 540));
      el.style.left = px + 'px';
      el.style.top = py + 'px';
      el.style.width = pw + 'px';
      el.style.height = ph + 'px';
      var content = el.querySelector('.window-content');
      if (content) {
        content.style.width = (pw - 2) + 'px';
        content.style.height = (ph - 24) + 'px';
      }
      var contentInner = el.querySelector('.window-content-inner');
      if (contentInner) {
        contentInner.style.lineHeight = (ph - 24) + 'px';
      }
    }
    // 更新窗口数据（同一引用，同步更新 ScreenPaint.windows）
    windowData.x = updated.x != null ? updated.x : windowData.x;
    windowData.y = updated.y != null ? updated.y : windowData.y;
    windowData.width = updated.width != null ? updated.width : windowData.width;
    windowData.height = updated.height != null ? updated.height : windowData.height;
    showToast(toastMsg, 'warning');
  },

  // ---------- 边界约束 ----------
  constrainBounds: function (left, top, width, height) {
    var maxW = ScreenPaint.canvasPixelW;
    var maxH = ScreenPaint.canvasPixelH;

    // 最小尺寸
    var MIN_W = 60;
    var MIN_H = 40;

    if (width < MIN_W) width = MIN_W;
    if (height < MIN_H) height = MIN_H;

    // 边界约束
    if (left < 0) left = 0;
    if (top < 0) top = 0;
    if (left + width > maxW) {
      if (width > maxW) {
        width = maxW;
        left = 0;
      } else {
        left = maxW - width;
      }
    }
    if (top + height > maxH) {
      if (height > maxH) {
        height = maxH;
        top = 0;
      } else {
        top = maxH - height;
      }
    }

    return { left: left, top: top, width: width, height: height };
  },

  // ---------- 检查窗口数上限（单元级别） ----------
  checkMaxWindows: function (actualRect, excludeWindowId) {
    if (!ScreenPaint.currentScreen) return false;

    var cellW = ScreenPaint.currentScreen.cellWidth || 1920;
    var cellH = ScreenPaint.currentScreen.cellHeight || 1080;
    var cols = ScreenPaint.currentScreen.colsCount;
    var rows = ScreenPaint.currentScreen.rowsCount;

    var wLeft = actualRect.x || 0;
    var wTop = actualRect.y || 0;
    var wRight = wLeft + (actualRect.width || 960);
    var wBottom = wTop + (actualRect.height || 540);

    var startCol = Math.max(0, Math.floor(wLeft / cellW));
    var endCol = Math.min(cols - 1, Math.floor((wRight - 1) / cellW));
    var startRow = Math.max(0, Math.floor(wTop / cellH));
    var endRow = Math.min(rows - 1, Math.floor((wBottom - 1) / cellH));

    for (var r = startRow; r <= endRow; r++) {
      for (var c = startCol; c <= endCol; c++) {
        var cell = ScreenPaint.getCellAt(r, c);
        if (!cell || !cell.deviceId || cell.maxWindows == null) continue;

        var countOnCell = 0;
        for (var i = 0; i < ScreenPaint.windows.length; i++) {
          var w = ScreenPaint.windows[i];
          if (w.windowId === excludeWindowId) continue;

          var wL = w.x || 0;
          var wT = w.y || 0;
          var wR = wL + (w.width || 960);
          var wB = wT + (w.height || 540);

          var cellLeft = c * cellW;
          var cellTop = r * cellH;
          var cellRight = cellLeft + cellW;
          var cellBottom = cellTop + cellH;

          if (wL < cellRight && wR > cellLeft && wT < cellBottom && wB > cellTop) {
            countOnCell++;
          }
        }

        if (countOnCell >= cell.maxWindows) {
          return true; // 超过上限
        }
      }
    }

    return false;
  },

  // ---------- 检查窗口重叠（单元级别 + 输入设备级别） ----------
  checkOverlap: function (pixelRect, excludeWindowId) {
    if (!ScreenPaint.currentScreen) return false;

    var cellW = ScreenPaint.currentScreen.cellWidth || 1920;
    var cellH = ScreenPaint.currentScreen.cellHeight || 1080;
    var cols = ScreenPaint.currentScreen.colsCount;
    var rows = ScreenPaint.currentScreen.rowsCount;

    // 将像素坐标转为实际坐标
    var actualLeft = ScreenPaint.toActualX(pixelRect.left);
    var actualTop = ScreenPaint.toActualY(pixelRect.top);
    var actualRight = actualLeft + ScreenPaint.toActualW(pixelRect.width);
    var actualBottom = actualTop + ScreenPaint.toActualH(pixelRect.height);

    // 找出被检查窗口的 deviceId（用于输入设备叠加检查）
    var targetWindow = null;
    for (var i = 0; i < ScreenPaint.windows.length; i++) {
      if (ScreenPaint.windows[i].windowId === excludeWindowId) {
        targetWindow = ScreenPaint.windows[i];
        break;
      }
    }

    // 检查输出设备（单元）级别叠加
    var startCol = Math.max(0, Math.floor(actualLeft / cellW));
    var endCol = Math.min(cols - 1, Math.floor((actualRight - 1) / cellW));
    var startRow = Math.max(0, Math.floor(actualTop / cellH));
    var endRow = Math.min(rows - 1, Math.floor((actualBottom - 1) / cellH));

    for (var r = startRow; r <= endRow; r++) {
      for (var c = startCol; c <= endCol; c++) {
        var cell = ScreenPaint.getCellAt(r, c);
        if (!cell || !cell.deviceId) continue;
        // 该单元支持叠加则跳过
        if (cell.supportOverlay === 1) continue;

        // 该单元不支持叠加 → 检查是否有其他窗口也覆盖此单元
        var cellLeft = c * cellW;
        var cellTop = r * cellH;
        var cellRight = cellLeft + cellW;
        var cellBottom = cellTop + cellH;

        for (var i = 0; i < ScreenPaint.windows.length; i++) {
          var w = ScreenPaint.windows[i];
          if (w.windowId === excludeWindowId) continue;

          var wLeft = w.x || 0;
          var wTop = w.y || 0;
          var wRight = wLeft + (w.width || 960);
          var wBottom = wTop + (w.height || 540);

          // 窗口 w 是否也覆盖此单元
          if (wLeft < cellRight && wRight > cellLeft &&
              wTop < cellBottom && wBottom > cellTop) {
            return true; // 存在重叠，且该单元不支持叠加
          }
        }
      }
    }

    return false;
  },

  // ---------- 检查目标单元是否支持指定能力（supportMove / supportResize） ----------
  checkCapability: function (actualRect, capability) {
    if (!ScreenPaint.currentScreen) return true;

    var cellW = ScreenPaint.currentScreen.cellWidth || 1920;
    var cellH = ScreenPaint.currentScreen.cellHeight || 1080;
    var cols = ScreenPaint.currentScreen.colsCount;
    var rows = ScreenPaint.currentScreen.rowsCount;

    var wLeft = actualRect.x || 0;
    var wTop = actualRect.y || 0;
    var wRight = wLeft + (actualRect.width || 960);
    var wBottom = wTop + (actualRect.height || 540);

    var startCol = Math.max(0, Math.floor(wLeft / cellW));
    var endCol = Math.min(cols - 1, Math.floor((wRight - 1) / cellW));
    var startRow = Math.max(0, Math.floor(wTop / cellH));
    var endRow = Math.min(rows - 1, Math.floor((wBottom - 1) / cellH));

    for (var r = startRow; r <= endRow; r++) {
      for (var c = startCol; c <= endCol; c++) {
        var cell = ScreenPaint.getCellAt(r, c);
        if (!cell || !cell.deviceId) continue;
        if (cell[capability] === 0) return false;
      }
    }
    return true;
  },

  // ---------- 设备能力检查 ----------
  canMove: function (windowData) {
    // 再检查输出设备（单元）能力
    if (!ScreenPaint.currentScreen) return true;
    var cells = ScreenPaint.getCellsForWindow(windowData);
    for (var i = 0; i < cells.length; i++) {
      if (cells[i].supportMove === 0) return false;
    }
    return true;
  },

  canResize: function (windowData) {
    // 再检查输出设备（单元）能力
    if (!ScreenPaint.currentScreen) return true;
    var cells = ScreenPaint.getCellsForWindow(windowData);
    for (var i = 0; i < cells.length; i++) {
      if (cells[i].supportResize === 0) return false;
    }
    return true;
  },

  getDeviceCapability: function (deviceId) {
    // 从缓存获取
    if (typeof DeviceCache !== 'undefined' && DeviceCache[deviceId]) {
      return DeviceCache[deviceId];
    }
    return null;
  },
};

// ---------- 全局 mousemove / mouseup ----------
document.addEventListener('mousemove', function (e) {
  // 拖拽
  if (ScreenInteract.dragState) {
    var state = ScreenInteract.dragState;
    var dx = e.clientX - state.startX;
    var dy = e.clientY - state.startY;

    var newLeft = state.origLeft + dx;
    var newTop = state.origTop + dy;
    var width = parseInt(state.windowEl.style.width, 10) || 0;
    var height = parseInt(state.windowEl.style.height, 10) || 0;

    var constrained = ScreenInteract.constrainBounds(newLeft, newTop, width, height);
    state.windowEl.style.left = constrained.left + 'px';
    state.windowEl.style.top = constrained.top + 'px';
    return;
  }

  // 缩放
  if (ScreenInteract.resizeState) {
    var rs = ScreenInteract.resizeState;
    var rdx = e.clientX - rs.startX;
    var rdy = e.clientY - rs.startY;
    var dir = rs.direction;

    var nLeft = rs.origLeft;
    var nTop = rs.origTop;
    var nWidth = rs.origWidth;
    var nHeight = rs.origHeight;

    // 根据方向调整
    if (dir.indexOf('e') !== -1) {
      nWidth = rs.origWidth + rdx;
    }
    if (dir.indexOf('w') !== -1) {
      nWidth = rs.origWidth - rdx;
      nLeft = rs.origLeft + rdx;
    }
    if (dir.indexOf('s') !== -1) {
      nHeight = rs.origHeight + rdy;
    }
    if (dir.indexOf('n') !== -1) {
      nHeight = rs.origHeight - rdy;
      nTop = rs.origTop + rdy;
    }

    var constrained = ScreenInteract.constrainBounds(nLeft, nTop, nWidth, nHeight);
    rs.windowEl.style.left = constrained.left + 'px';
    rs.windowEl.style.top = constrained.top + 'px';
    rs.windowEl.style.width = constrained.width + 'px';
    rs.windowEl.style.height = constrained.height + 'px';

    // 更新内容区尺寸
    var content = rs.windowEl.querySelector('.window-content');
    if (content) {
      content.style.width = (constrained.width - 2) + 'px';
      content.style.height = (constrained.height - 24) + 'px';
    }
    var contentInner = rs.windowEl.querySelector('.window-content-inner');
    if (contentInner) {
      contentInner.style.lineHeight = (constrained.height - 24) + 'px';
    }
    return;
  }
});

document.addEventListener('mouseup', function () {
  if (ScreenInteract.dragState) {
    ScreenInteract.endDrag();
  }
  if (ScreenInteract.resizeState) {
    ScreenInteract.endResize();
  }
});
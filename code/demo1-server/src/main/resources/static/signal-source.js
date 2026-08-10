/* =================================================================
 *  signal-source.js — 信号源面板
 *  负责：设备通道列表渲染、通道卡片拖拽开窗、设备能力校验
 * ================================================================= */

var SignalSource = {
  // 状态
  devices: [],           // 输入设备列表（包含能力信息）
  channelCards: [],      // { deviceId, deviceName, channelName, online }
  panelEl: null,
  bodyEl: null,
  dragPreview: null,

  // ---------- 初始化 ----------
  init: function () {
    var self = this;
    this.panelEl = document.getElementById('sourcePanel');
    this.bodyEl = document.getElementById('sourcePanelBody');
    this.outputBodyEl = document.getElementById('outputPanelBody');

    // Tab 切换
    var tabInputBtn = document.getElementById('tabInputBtn');
    var tabOutputBtn = document.getElementById('tabOutputBtn');

    if (tabInputBtn) {
      tabInputBtn.addEventListener('click', function () {
        self._switchTab('input');
      });
    }
    if (tabOutputBtn) {
      tabOutputBtn.addEventListener('click', function () {
        self._switchTab('output');
      });
    }
  },

  _switchTab: function (tab) {
    var tabInputBtn = document.getElementById('tabInputBtn');
    var tabOutputBtn = document.getElementById('tabOutputBtn');

    if (tab === 'input') {
      tabInputBtn.classList.add('active');
      tabOutputBtn.classList.remove('active');
      this.bodyEl.style.display = '';
      this.outputBodyEl.style.display = 'none';
    } else {
      tabInputBtn.classList.remove('active');
      tabOutputBtn.classList.add('active');
      this.bodyEl.style.display = 'none';
      this.outputBodyEl.style.display = '';
    }
  },

  // ==================== 输出设备面板 ====================

  /** 加载输出设备窗口信息 */
  loadOutputDevices: function (screenId) {
    var self = this;
    if (!this.outputBodyEl) this.outputBodyEl = document.getElementById('outputPanelBody');
    this.outputBodyEl.innerHTML = '<div class="source-loading">加载输出设备...</div>';

    WindowApi.getOutputDeviceWindows(screenId).then(function (result) {
      var data = result.data || [];
      self.renderOutputDevices(data);
    }).catch(function (err) {
      self.outputBodyEl.innerHTML = '<div class="source-empty" style="color:#e74c3c;">加载失败: ' + escapeHtml(err.message) + '</div>';
    });
  },

  /** 渲染输出设备列表 */
  renderOutputDevices: function (data) {
    if (!data || data.length === 0) {
      this.outputBodyEl.innerHTML = '<div class="source-empty">暂无已绑定的输出设备</div>';
      return;
    }

    var html = '';
    for (var i = 0; i < data.length; i++) {
      var od = data[i];
      var isOnline = od.online === 1;

      html += '<div class="output-device-group">';
      html += '<div class="output-device-header">';
      html += '<span class="od-name">' + escapeHtml(od.deviceName || '设备#' + od.deviceId) + '</span>';
      html += '<span class="od-position">[' + od.rowIndex + ',' + od.colIndex + '] ' + escapeHtml(od.channelName || '') + '</span>';
      html += '<span class="od-capability">';
      var capParts = [];
      if (od.maxWindows != null) capParts.push('最大窗口:' + od.maxWindows);
      if (od.supportMove != null) capParts.push('移动:' + (od.supportMove === 1 ? '是' : '否'));
      if (od.supportResize != null) capParts.push('缩放:' + (od.supportResize === 1 ? '是' : '否'));
      if (od.supportOverlay != null) capParts.push('叠加:' + (od.supportOverlay === 1 ? '是' : '否'));
      if (od.maxResolution) capParts.push(od.maxResolution);
      html += capParts.join(' | ') + '</span>';
      if (!isOnline) {
        html += '<span class="od-offline-badge">● 离线</span>';
      }
      html += '</div>';

      html += '<div class="output-device-body">';
      var windows = od.windows || [];
      if (windows.length === 0) {
        html += '<div class="od-no-windows">无窗口</div>';
      } else {
        for (var j = 0; j < windows.length; j++) {
          var sub = windows[j];
          html += '<div class="od-window-item">';
          html += '<span class="od-window-sync ' + (sub.syncStatus || 'synced') + '" title="' + (sub.syncStatus || 'synced') + '"></span>';
          html += '<span class="od-window-source">' + escapeHtml(sub.sourceDeviceName || '') + ' / ' + escapeHtml(sub.sourceChannelName || '') + '</span>';
          html += '<span class="od-window-coords">(' + sub.x + ',' + sub.y + ' ' + sub.width + '×' + sub.height + ')</span>';
          if (sub.degraded === 1) {
            html += '<span title="部分设备离线" style="color:#e74c3c;font-size:10px;">⚠</span>';
          }
          html += '</div>';
        }
      }
      html += '</div>';
      html += '</div>';
    }

    this.outputBodyEl.innerHTML = html;
  },

  // ---------- 加载信号源 ----------
  loadSignalSources: function (screenDetail) {
    var self = this;
    this.bodyEl.innerHTML = '<div class="source-loading">加载信号源...</div>';

    // 收集大屏已绑定的输出设备 ID
    var outputDeviceIds = [];
    var cells = screenDetail.cells || [];
    for (var i = 0; i < cells.length; i++) {
      if (cells[i].deviceId && outputDeviceIds.indexOf(cells[i].deviceId) === -1) {
        outputDeviceIds.push(cells[i].deviceId);
      }
    }

    // 加载所有输入设备
    this.loadInputDevices().then(function (inputDevices) {
      self.devices = inputDevices;

      if (inputDevices.length === 0) {
        self.bodyEl.innerHTML = '<div class="source-empty">暂无可用信号源<br><small>请先添加输入设备</small></div>';
        return;
      }

      self.renderSourceGroups(inputDevices, screenDetail);
    }).catch(function (err) {
      self.bodyEl.innerHTML = '<div class="source-empty" style="color:#e74c3c;">加载信号源失败: ' + escapeHtml(err.message) + '</div>';
    });
  },

  // ---------- 加载输入设备列表 ----------
  loadInputDevices: async function () {
    var allDevices = [];
    var page = 1;
    var hasMore = true;

    while (hasMore) {
      try {
        var result = await DeviceApi.page({ page: page, pageSize: 100 });
        var records = result.data.records || [];
        // 仅过滤输入设备
        for (var i = 0; i < records.length; i++) {
          if (records[i].deviceCategory === 'INPUT') {
            allDevices.push(records[i]);
          }
        }
        if (records.length < 100) {
          hasMore = false;
        } else {
          page++;
        }
      } catch (err) {
        hasMore = false;
      }
    }

    // 为每个设备加载能力和通道 URL
    for (var j = 0; j < allDevices.length; j++) {
      try {
        var capResult = await DeviceApi.getCapability(allDevices[j].id);
        allDevices[j]._capability = capResult.data;
      } catch (_) {
        allDevices[j]._capability = {};
      }
      // 加载通道 URL 配置
      try {
        var baseUrl = allDevices[j].baseUrl || '';
        if (baseUrl) {
          var urlResult = await fetch(baseUrl + '/simulator/channel/urls');
          var urlJson = await urlResult.json();
          if (urlJson.code === 1) {
            allDevices[j]._channelUrls = urlJson.data || {};
          } else {
            allDevices[j]._channelUrls = {};
          }
        } else {
          allDevices[j]._channelUrls = {};
        }
      } catch (_) {
        allDevices[j]._channelUrls = {};
      }
    }

    return allDevices;
  },

  // ---------- 渲染信号源分组 ----------
  renderSourceGroups: function (devices, screenDetail) {
    var self = this;
    this.channelCards = [];
    var html = '';

    // 获取当前窗口总数
    var currentWindowCount = ScreenPaint.windows ? ScreenPaint.windows.length : 0;

    for (var i = 0; i < devices.length; i++) {
      var device = devices[i];
      var channels = [];
      if (device.inputChannel1) channels.push(device.inputChannel1);
      if (device.inputChannel2) channels.push(device.inputChannel2);

      if (channels.length === 0) continue;

      var isOnline = device.online === 1;
      var isEnabled = device.enabled === 1;
      var canDrag = isOnline && isEnabled;

      html += '<div class="source-group">';
      html += '<div class="source-group-title">' + escapeHtml(device.deviceName) + (isOnline ? '' : ' <span style="color:#e74c3c;">● 离线</span>') + '</div>';
      html += '<div class="source-group-capability">';
      var cap = device._capability || {};
      var capItems = [];
      if (cap.maxWindows != null) capItems.push('最大窗口:' + cap.maxWindows);
      if (cap.maxResolution) capItems.push(cap.maxResolution);
      if (cap.supportMove != null) capItems.push('移动:' + (cap.supportMove ? '是' : '否'));
      if (cap.supportResize != null) capItems.push('缩放:' + (cap.supportResize ? '是' : '否'));
      if (cap.supportOverlay != null) capItems.push('叠加:' + (cap.supportOverlay ? '是' : '否'));
      if (capItems.length > 0) html += capItems.join(' | ') + '</div>';

      for (var j = 0; j < channels.length; j++) {
        var channelName = channels[j];
        var channelUrls = device._channelUrls || {};
        var sourceUrl = channelUrls[channelName] || '';

        var cardData = {
          deviceId: device.id,
          deviceName: device.deviceName,
          channelName: channelName,
          sourceUrl: sourceUrl,
          online: isOnline,
        };

        this.channelCards.push(cardData);

        // 统计该通道当前已创建的窗口数
        var chanWinCount = 0;
        if (ScreenPaint.windows) {
          for (var k = 0; k < ScreenPaint.windows.length; k++) {
            var w = ScreenPaint.windows[k];
            if (w.deviceId === device.id && w.channelName === channelName) {
              chanWinCount++;
            }
          }
        }

        html += '<div class="channel-card' + (canDrag ? '' : ' disabled') + '"';
        html += ' data-device-id="' + device.id + '"';
        html += ' data-channel-name="' + escapeHtml(channelName) + '"';
        html += ' data-device-name="' + escapeHtml(device.deviceName) + '"';
        html += ' data-source-url="' + escapeHtml(sourceUrl) + '"';
        html += ' draggable="' + (canDrag ? 'true' : 'false') + '">';
        html += '<span class="channel-name">' + escapeHtml(channelName) + '</span>';
        html += '<span class="channel-window-count">已创建 ' + chanWinCount + ' 窗口</span>';
        html += '</div>';
      }

      html += '</div>';
    }

    this.bodyEl.innerHTML = html;

    // 绑定拖拽事件
    this.setupDragDrop();
  },

  // ---------- 设置拖拽 ----------
  setupDragDrop: function () {
    var self = this;
    var cards = this.bodyEl.querySelectorAll('.channel-card:not(.disabled)');

    for (var i = 0; i < cards.length; i++) {
      var card = cards[i];

      card.addEventListener('dragstart', function (e) {
        var deviceId = parseInt(this.getAttribute('data-device-id'), 10);
        var channelName = this.getAttribute('data-channel-name');
        var deviceName = this.getAttribute('data-device-name');
        var sourceUrl = this.getAttribute('data-source-url') || '';

        e.dataTransfer.setData('text/plain', JSON.stringify({
          deviceId: deviceId,
          channelName: channelName,
          deviceName: deviceName,
          sourceUrl: sourceUrl,
        }));
        e.dataTransfer.effectAllowed = 'copy';

        // 创建拖拽预览
        self.showDragPreview(e, deviceName, channelName);
      });

      card.addEventListener('dragend', function () {
        self.hideDragPreview();
      });
    }

    // 画布作为放置目标（先移除旧监听器，避免重复绑定导致多次创建窗口）
    var canvasContainer = document.getElementById('canvasContainer');
    if (canvasContainer) {
      if (this._dropHandler) {
        canvasContainer.removeEventListener('dragover', this._dropHandler);
        canvasContainer.removeEventListener('drop', this._dropHandler);
      }

      this._dropHandler = function (e) {
        e.preventDefault();
        if (e.type === 'dragover') {
          e.dataTransfer.dropEffect = 'copy';
        } else {
          self.hideDragPreview();
          self.onDrop(e);
        }
      };

      canvasContainer.addEventListener('dragover', this._dropHandler);
      canvasContainer.addEventListener('drop', this._dropHandler);
    }
  },

  // ---------- 拖拽预览 ----------
  showDragPreview: function (e, deviceName, channelName) {
    this.hideDragPreview();
    var preview = document.createElement('div');
    preview.className = 'channel-card-drag-preview';
    preview.textContent = deviceName + ' / ' + channelName;
    document.body.appendChild(preview);
    this.dragPreview = preview;

    // 跟随鼠标
    var self = this;
    this._dragMoveHandler = function (ev) {
      if (self.dragPreview) {
        self.dragPreview.style.left = ev.clientX + 'px';
        self.dragPreview.style.top = ev.clientY + 'px';
      }
    };
    document.addEventListener('dragover', this._dragMoveHandler);
  },

  hideDragPreview: function () {
    if (this.dragPreview) {
      this.dragPreview.remove();
      this.dragPreview = null;
    }
    if (this._dragMoveHandler) {
      document.removeEventListener('dragover', this._dragMoveHandler);
      this._dragMoveHandler = null;
    }
  },

  // ---------- 释放创建窗口 ----------
  onDrop: function (e) {
    var self = this;

    // 解析拖拽数据
    var rawData = e.dataTransfer.getData('text/plain');
    if (!rawData) return;

    var dragData;
    try {
      dragData = JSON.parse(rawData);
    } catch (_) {
      return;
    }

    if (!dragData.deviceId || !dragData.channelName) return;

    // 检查是否有已加载的大屏
    if (!AppMode.currentScreenId || !ScreenPaint.currentScreen) {
      showToast('请先选择大屏', 'warning');
      return;
    }

    // 计算释放位置在画布上的像素坐标
    var canvasEl = ScreenPaint.canvasEl;
    if (!canvasEl) return;

    var canvasRect = canvasEl.getBoundingClientRect();
    var pixelX = e.clientX - canvasRect.left;
    var pixelY = e.clientY - canvasRect.top;

    // 检查是否在画布范围内
    if (pixelX < 0 || pixelY < 0 || pixelX > canvasRect.width || pixelY > canvasRect.height) {
      return;
    }

    // 定位落入的单元
    var cellPos = ScreenPaint.getCellAtPos(pixelX, pixelY);
    if (!cellPos) {
      showToast('无法定位目标单元', 'error');
      return;
    }

    // 获取该单元绑定的设备
    var cell = ScreenPaint.getCellAt(cellPos.row, cellPos.col);
    if (!cell || !cell.deviceId) {
      showToast('目标单元未绑定设备，无法创建窗口', 'error');
      return;
    }

    // 默认窗口尺寸（画布像素 200×120 → 实际坐标）
    var defWidth = Math.round(200 * ScreenPaint.actualW / ScreenPaint.canvasPixelW);
    var defHeight = Math.round(120 * ScreenPaint.actualH / ScreenPaint.canvasPixelH);

    // 窗口居中：鼠标释放位置 = 窗口中心点
    var centerActualX = ScreenPaint.toActualX(pixelX);
    var centerActualY = ScreenPaint.toActualY(pixelY);
    var actualX = centerActualX - Math.round(defWidth / 2);
    var actualY = centerActualY - Math.round(defHeight / 2);

    // 边界约束
    actualX = Math.max(0, Math.min(actualX, ScreenPaint.actualW - defWidth));
    actualY = Math.max(0, Math.min(actualY, ScreenPaint.actualH - defHeight));

    // 生成 windowId
    var windowId = 'win-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8);

    // 检查叠加限制：新窗口是否会与已有窗口在某个不支持叠加的单元上重叠
    var newPixelLeft = ScreenPaint.toPixelX(actualX);
    var newPixelTop = ScreenPaint.toPixelY(actualY);
    var newPixelW = ScreenPaint.toPixelW(defWidth);
    var newPixelH = ScreenPaint.toPixelH(defHeight);
    if (typeof ScreenInteract !== 'undefined' && ScreenInteract.checkOverlap) {
      if (ScreenInteract.checkOverlap({left: newPixelLeft, top: newPixelTop, width: newPixelW, height: newPixelH}, null)) {
        showToast('目标位置与已有窗口重叠，设备不支持窗口叠加，无法创建', 'warning');
        return;
      }
    }

    // 调用 API 创建窗口
    var createReq = {
      windowId: windowId,
      deviceId: dragData.deviceId,
      channelName: dragData.channelName,
      sourceUrl: dragData.sourceUrl || '',
      x: actualX,
      y: actualY,
      width: defWidth,
      height: defHeight,
    };

    WindowApi.create(AppMode.currentScreenId, createReq).then(function (result) {
      var winData = result.data;
      showToast('窗口创建成功', 'success');

      // 添加到画布
      ScreenPaint.addWindow(winData);

      // 刷新信号源窗口计数
      self.refreshWindowCounts();
    }).catch(function (err) {
      showToast('创建窗口失败: ' + err.message, 'error');
    });
  },

  // ---------- 刷新所有通道的窗口计数 ----------
  refreshWindowCounts: function () {
    var cards = this.bodyEl.querySelectorAll('.channel-card');
    for (var i = 0; i < cards.length; i++) {
      var card = cards[i];
      var deviceId = parseInt(card.getAttribute('data-device-id'), 10);
      var channelName = card.getAttribute('data-channel-name');

      var count = 0;
      if (ScreenPaint.windows) {
        for (var j = 0; j < ScreenPaint.windows.length; j++) {
          var w = ScreenPaint.windows[j];
          if (w.deviceId === deviceId && w.channelName === channelName) {
            count++;
          }
        }
      }

      var countEl = card.querySelector('.channel-window-count');
      if (countEl) {
        countEl.textContent = '已创建 ' + count + ' 窗口';
      }
    }

    // 更新窗口数限制
    this.updateChannelAvailability();
  },

  // ---------- 更新通道可用性 ----------
  updateChannelAvailability: function () {
    var cards = this.bodyEl.querySelectorAll('.channel-card');
    for (var i = 0; i < cards.length; i++) {
      var card = cards[i];

      // 检查设备是否仍在线
      var deviceId = parseInt(card.getAttribute('data-device-id'), 10);
      var device = this.getDeviceById(deviceId);
      var isOnline = device ? device.online === 1 : true;

      if (isOnline) {
        card.classList.remove('disabled');
        card.setAttribute('draggable', 'true');
      } else {
        card.classList.add('disabled');
        card.setAttribute('draggable', 'false');
      }
    }
  },

  getDeviceById: function (deviceId) {
    for (var i = 0; i < this.devices.length; i++) {
      if (this.devices[i].id === deviceId) return this.devices[i];
    }
    return null;
  },

  // ---------- 清空面板 ----------
  clear: function () {
    if (this.bodyEl) {
      this.bodyEl.innerHTML = '<div class="source-empty">请先选择大屏</div>';
    }
    if (this.outputBodyEl) {
      this.outputBodyEl.innerHTML = '<div class="source-empty">请先选择大屏</div>';
    }
    this.devices = [];
    this.channelCards = [];
  },
};

// 初始化
document.addEventListener('DOMContentLoaded', function () {
  SignalSource.init();
});
/* =================================================================
 *  异构硬件设备管控系统 — 前端逻辑
 *  适配后端:
 *    用户模块: POST /user/login, /user/logout, /user, /user/page, /user/{id}
 *    设备模块: POST /device, /device/discover, DELETE /device/{id},
 *             GET /device/page,
 *             PUT /device/{id}/enabled, /device/{id}/refresh
 *    详情弹窗直接复用列表缓存数据，不再请求 /info /status
 *  统一返回格式: { code: 1|0, msg: string, data: T }
 *  认证方式: Authorization: Bearer <token>
 * ================================================================= */

// ---------- 配置 ----------
const CONFIG = {
  TOKEN_KEY: 'auth_token',
  PAGE_SIZE: 10,
};

// ---------- 工具函数 ----------
function $(sel) { return document.querySelector(sel); }
function $$(sel) { return document.querySelectorAll(sel); }

// ---------- Toast 消息 ----------
function showToast(message, type) {
  type = type || 'info';
  var container = $('#toastContainer');
  var toast = document.createElement('div');
  toast.className = 'toast toast-' + type;
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(function () { toast.remove(); }, 3000);
}

// ---------- Auth 管理 ----------
var Auth = {
  getToken: function () {
    return localStorage.getItem(CONFIG.TOKEN_KEY);
  },
  setToken: function (token) {
    localStorage.setItem(CONFIG.TOKEN_KEY, token);
  },
  clearToken: function () {
    localStorage.removeItem(CONFIG.TOKEN_KEY);
  },
  getUsername: function () {
    return localStorage.getItem('auth_username') || '';
  },
  setUsername: function (name) {
    localStorage.setItem('auth_username', name);
  },
  clearAll: function () {
    localStorage.removeItem(CONFIG.TOKEN_KEY);
    localStorage.removeItem('auth_username');
  },
};

// ---------- 通用 API 请求 ----------
/**
 * 发送 API 请求，自动处理:
 *  - 添加 Authorization header
 *  - 解析 Result<T> 统一响应
 *  - 401 / token 过期 → 跳转登录页
 *  - 网络异常捕获
 */
async function api(url, options) {
  options = options || {};
  var headers = options.headers || {};
  headers['Content-Type'] = headers['Content-Type'] || 'application/json';

  var token = Auth.getToken();
  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }

  try {
    var res = await fetch(url, {
      method: options.method || 'GET',
      headers: headers,
      body: options.body || undefined,
    });

    // 401 — token 无效或过期
    if (res.status === 401) {
      Auth.clearAll();
      showLoginView();
      showToast('登录已过期，请重新登录', 'error');
      throw new Error('未授权');
    }

    var result = await res.json();

    // 后端统一返回 { code, msg, data }
    if (result.code === 0) {
      throw new Error(result.msg || '操作失败');
    }

    return result;
  } catch (err) {
    // 网络异常等
    if (err.message === '未授权') throw err;
    if (err.message && err.message.indexOf('Failed to fetch') === -1) {
      throw err; // 已是业务错误，直接抛出
    }
    throw new Error('无法连接服务器，请检查网络');
  }
}

// ---------- 用户 API ----------
var UserApi = {
  /** 登录 */
  login: function (username, password) {
    return api('/user/login', {
      method: 'POST',
      body: JSON.stringify({ username: username, password: password }),
    });
  },

  /** 分页查询 */
  getPage: function (pageDTO) {
    var params = new URLSearchParams();
    params.append('page', pageDTO.page);
    params.append('pageSize', pageDTO.pageSize);
    if (pageDTO.username) {
      params.append('username', pageDTO.username);
    }
    return api('/user/page?' + params.toString());
  },

  /** 根据 ID 获取用户 */
  getById: function (id) {
    return api('/user/' + id);
  },

  /** 新增用户 */
  add: function (user) {
    return api('/user', {
      method: 'POST',
      body: JSON.stringify(user),
    });
  },

  /** 修改用户 */
  update: function (user) {
    return api('/user', {
      method: 'PUT',
      body: JSON.stringify(user),
    });
  },

  /** 删除用户（按用户名） */
  delete: function (username) {
    return api('/user/' + encodeURIComponent(username), {
      method: 'DELETE',
    });
  },

  /** 退出 */
  logout: function () {
    return api('/user/logout', { method: 'POST' });
  },
};

// ---------- 设备 API ----------
var DeviceApi = {
  /** 添加设备（仅 baseUrl） */
  add: function (baseUrl) {
    return api('/device', {
      method: 'POST',
      body: JSON.stringify({ baseUrl: baseUrl }),
    });
  },

  /** 搜索发现设备 */
  discover: function () {
    return api('/device/discover', { method: 'POST' });
  },

  /** 删除设备 */
  delete: function (id) {
    return api('/device/' + id, { method: 'DELETE' });
  },

  /** 启用/禁用设备 */
  setEnabled: function (id, enabled) {
    return api('/device/' + id + '/enabled', {
      method: 'PUT',
      body: JSON.stringify({ enabled: enabled }),
    });
  },

  /** 刷新设备信息 */
  refresh: function (id) {
    return api('/device/' + id + '/refresh', { method: 'PUT' });
  },

  /** 分页查询 */
  page: function (params) {
    var qs = new URLSearchParams();
    qs.append('page', params.page);
    qs.append('pageSize', params.pageSize);
    if (params.deviceName) {
      qs.append('deviceName', params.deviceName);
    }
    if (params.deviceType) {
      qs.append('deviceType', params.deviceType);
    }
    return api('/device/page?' + qs.toString());
  },

  /** 获取设备基本信息（实时查询模拟设备） */
  getInfo: function (id) {
    return api('/device/' + id + '/info');
  },

  /** 获取设备运行状态（实时查询模拟设备） */
  getStatus: function (id) {
    return api('/device/' + id + '/status');
  },

  /** 获取设备能力 */
  getCapability: function (id) {
    return api('/device/' + id + '/capability');
  },

};

// ---------- 大屏 API ----------
var ScreenApi = {
  /** 创建大屏 */
  create: function (req) {
    return api('/screen', {
      method: 'POST',
      body: JSON.stringify(req),
    });
  },

  /** 分页查询大屏列表 */
  getPage: function (dto) {
    var params = new URLSearchParams();
    params.append('page', dto.page);
    params.append('pageSize', dto.pageSize);
    if (dto.keyword) {
      params.append('keyword', dto.keyword);
    }
    return api('/screen/page?' + params.toString());
  },

  /** 获取大屏详情 */
  getDetail: function (id) {
    return api('/screen/' + id);
  },

  /** 删除大屏 */
  delete: function (id) {
    return api('/screen/' + id, { method: 'DELETE' });
  },

  /** 绑定/更换设备通道 */
  bindCell: function (screenId, cellId, req) {
    return api('/screen/' + screenId + '/cell/' + cellId, {
      method: 'PUT',
      body: JSON.stringify(req),
    });
  },
};

// ---------- 窗口 API ----------
var WindowApi = {
  /** 创建窗口 */
  create: function (screenId, req) {
    return api('/screen/' + screenId + '/window', {
      method: 'POST',
      body: JSON.stringify(req),
    });
  },

  /** 更新窗口位置/大小 */
  update: function (screenId, windowId, req) {
    return api('/screen/' + screenId + '/window/' + encodeURIComponent(windowId), {
      method: 'PUT',
      body: JSON.stringify(req),
    });
  },

  /** 关闭窗口 */
  close: function (screenId, windowId) {
    return api('/screen/' + screenId + '/window/' + encodeURIComponent(windowId), {
      method: 'DELETE',
    });
  },

  /** 查询窗口列表 */
  list: function (screenId) {
    return api('/screen/' + screenId + '/windows');
  },

  /** 一键清空窗口 */
  clearAll: function (screenId) {
    return api('/screen/' + screenId + '/windows', { method: 'DELETE' });
  },

  /** 查询各输出设备的窗口信息 */
  getOutputDeviceWindows: function (screenId) {
    return api('/screen/' + screenId + '/output-devices');
  },
};

// ---------- 视图切换 ----------
function showLoginView() {
  $('#loginView').style.display = 'flex';
  $('#mainView').style.display = 'none';
  $('#loginError').textContent = '';
  $('#loginUsername').value = '';
  $('#loginPassword').value = '';
}

function showMainView() {
  $('#loginView').style.display = 'none';
  $('#mainView').style.display = 'flex';
  $('#currentUser').textContent = Auth.getUsername();
}

// ---------- 模块切换 ----------
var ModuleConfig = {
  user: { title: '用户管理', panelId: 'panel-user' },
  device: { title: '设备管理', panelId: 'panel-device' },
  screen: { title: '大屏配置', panelId: 'panel-screen' },
};

function switchModule(moduleName) {
  var config = ModuleConfig[moduleName];
  if (!config) return;

  // 更新侧边栏激活状态
  var navItems = $$('#sidebarNav .nav-item');
  for (var i = 0; i < navItems.length; i++) {
    var item = navItems[i];
    if (item.getAttribute('data-module') === moduleName) {
      item.classList.add('active');
    } else {
      item.classList.remove('active');
    }
  }

  // 更新顶栏标题
  $('#moduleTitle').textContent = config.title;

  // 切换内容面板
  var panels = $$('.content-panel');
  for (var j = 0; j < panels.length; j++) {
    panels[j].classList.remove('active');
  }
  var targetPanel = document.getElementById(config.panelId);
  if (targetPanel) {
    targetPanel.classList.add('active');
  }

  // 加载对应模块数据
  if (moduleName === 'user') {
    resetPage();
    loadUserList();
  } else if (moduleName === 'device') {
    resetDevicePage();
    loadDeviceList();
  } else if (moduleName === 'screen') {
    resetScreenPage();
    loadScreenList();
  }
}

// ---------- 分页状态 ----------
var PageState = {
  page: 1,
  pageSize: CONFIG.PAGE_SIZE,
  total: 0,
  searchUsername: '',
};

function resetPage() {
  PageState.page = 1;
  PageState.searchUsername = '';
  $('#searchInput').value = '';
}

// ---------- 表格渲染 ----------
function renderTable(records) {
  var tbody = $('#tableBody');

  if (!records || records.length === 0) {
    tbody.innerHTML = '<tr><td colspan="12" class="table-empty">暂无数据</td></tr>';
    return;
  }

  var html = '';
  for (var i = 0; i < records.length; i++) {
    var u = records[i];
    var enabledHtml = u.isEnabled === 1
      ? '<span class="status-tag status-enabled">启用</span>'
      : '<span class="status-tag status-disabled">禁用</span>';

    var validUntil = u.validUntil
      ? (typeof u.validUntil === 'string' ? u.validUntil.substring(0, 10) : u.validUntil)
      : '-';

    html += '<tr>';
    html += '<td>' + escapeHtml(u.username) + '</td>';
    html += '<td>' + escapeHtml(u.role) + '</td>';
    html += '<td>' + escapeHtml(u.userType) + '</td>';
    html += '<td>' + (u.userLevel != null ? u.userLevel : '') + '</td>';
    html += '<td>' + escapeHtml(u.department) + '</td>';
    html += '<td>' + escapeHtml(u.position) + '</td>';
    html += '<td>' + escapeHtml(u.phone) + '</td>';
    html += '<td>' + escapeHtml(u.email) + '</td>';
    html += '<td>' + enabledHtml + '</td>';
    html += '<td>' + validUntil + '</td>';
    html += '<td><div class="action-btns">';
    html += '<button class="btn btn-outline btn-xs edit-btn" data-id="' + u.id + '">编辑</button>';
    html += '<button class="btn btn-danger btn-xs delete-btn" data-username="' + escapeHtml(u.username) + '" data-id="' + u.id + '">删除</button>';
    html += '</div></td>';
    html += '</tr>';
  }

  tbody.innerHTML = html;
}

function renderPagination() {
  var totalPages = Math.ceil(PageState.total / PageState.pageSize) || 1;
  $('#pageInfo').textContent =
    '第 ' + PageState.page + ' 页 / 共 ' + totalPages + ' 页（共 ' + PageState.total + ' 条）';
  $('#prevPageBtn').disabled = PageState.page <= 1;
  $('#nextPageBtn').disabled = PageState.page >= totalPages;
}

// ---------- 设备数据缓存（供详情弹窗直接使用，避免重复请求） ----------
var DeviceCache = {};

// ---------- 前后台切换状态 ----------
var AppMode = {
  current: 'backend',  // 'backend' | 'frontend'
  currentScreenId: null,
};

// ---------- 大屏分页状态 ----------
var ScreenPageState = {
  page: 1,
  pageSize: CONFIG.PAGE_SIZE,
  total: 0,
  searchKeyword: '',
};

// ---------- 设备分页状态 ----------
var DevicePageState = {
  page: 1,
  pageSize: CONFIG.PAGE_SIZE,
  total: 0,
  searchDeviceName: '',
  searchDeviceType: '',
};

function resetDevicePage() {
  DevicePageState.page = 1;
  DevicePageState.searchDeviceName = '';
  DevicePageState.searchDeviceType = '';
  $('#deviceSearchName').value = '';
  $('#deviceSearchType').value = '';
}

// ---------- 设备表格渲染 ----------
function renderDeviceTable(records) {
  var tbody = $('#deviceTableBody');

  if (!records || records.length === 0) {
    tbody.innerHTML = '<tr><td colspan="10" class="table-empty">暂无数据</td></tr>';
    return;
  }

  var html = '';
  for (var i = 0; i < records.length; i++) {
    var d = records[i];
    DeviceCache[d.id] = d;

    // 在线状态
    var onlineHtml = d.online === 1
      ? '<span class="status-text"><span class="status-dot online"></span>在线</span>'
      : '<span class="status-text"><span class="status-dot offline"></span>离线<span class="heartbeat-time">' + (d.lastHeartbeat ? formatDateTime(d.lastHeartbeat) : '未知') + '</span></span>';

    // 启用状态
    var enabledHtml = d.enabled === 1
      ? '<span class="status-tag status-enabled">启用</span>'
      : '<span class="status-tag status-disabled">禁用</span>';

    // 操作按钮
    var actionHtml = '<div class="action-btns">';
    actionHtml += '<button class="btn btn-outline btn-xs detail-btn" data-id="' + d.id + '">详情</button>';
    actionHtml += '<button class="btn btn-outline btn-xs refresh-btn" data-id="' + d.id + '">刷新</button>';

    if (d.enabled === 1) {
      actionHtml += '<button class="btn btn-outline btn-xs toggle-btn" data-id="' + d.id + '" data-enabled="1">禁用</button>';
    } else {
      actionHtml += '<button class="btn btn-outline btn-xs toggle-btn" data-id="' + d.id + '" data-enabled="0">启用</button>';
      actionHtml += '<button class="btn btn-danger btn-xs delete-device-btn" data-id="' + d.id + '" data-name="' + escapeHtml(d.deviceName) + '">删除</button>';
    }
    actionHtml += '</div>';

    html += '<tr>';
    html += '<td>' + escapeHtml(d.deviceName) + '</td>';
    html += '<td>' + escapeHtml(d.deviceType) + '</td>';
    html += '<td>' + (d.deviceCategory === 'INPUT' ? '<span class="status-tag status-enabled">输入设备</span>' : d.deviceCategory === 'OUTPUT' ? '<span class="status-tag status-disabled">输出设备</span>' : escapeHtml(d.deviceCategory || '-')) + '</td>';
    html += '<td>' + escapeHtml(d.model) + '</td>';
    html += '<td>' + escapeHtml(d.serialNumber) + '</td>';
    html += '<td>' + escapeHtml(d.maxResolution) + '</td>';
    html += '<td style="font-family:monospace;font-size:12px;">' + escapeHtml(d.baseUrl) + '</td>';
    html += '<td>' + onlineHtml + '</td>';
    html += '<td>' + enabledHtml + '</td>';
    html += '<td>' + actionHtml + '</td>';
    html += '</tr>';
  }

  tbody.innerHTML = html;
}

function renderDevicePagination() {
  var totalPages = Math.ceil(DevicePageState.total / DevicePageState.pageSize) || 1;
  $('#devicePageInfo').textContent =
    '第 ' + DevicePageState.page + ' 页 / 共 ' + totalPages + ' 页（共 ' + DevicePageState.total + ' 条）';
  $('#devicePrevPageBtn').disabled = DevicePageState.page <= 1;
  $('#deviceNextPageBtn').disabled = DevicePageState.page >= totalPages;
}

function formatDateTime(dateStr) {
  if (!dateStr) return '';
  // 处理 ISO 时间戳或数组格式
  try {
    var d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    var yyyy = d.getFullYear();
    var MM = String(d.getMonth() + 1).padStart(2, '0');
    var dd = String(d.getDate()).padStart(2, '0');
    var HH = String(d.getHours()).padStart(2, '0');
    var mm = String(d.getMinutes()).padStart(2, '0');
    var ss = String(d.getSeconds()).padStart(2, '0');
    return yyyy + '-' + MM + '-' + dd + ' ' + HH + ':' + mm + ':' + ss;
  } catch (e) {
    return dateStr;
  }
}

// ---------- 加载设备列表 ----------
async function loadDeviceList() {
  try {
    $('#deviceTableBody').innerHTML = '<tr><td colspan="10" class="table-empty">加载中...</td></tr>';

    var result = await DeviceApi.page({
      deviceName: DevicePageState.searchDeviceName || null,
      deviceType: DevicePageState.searchDeviceType || null,
      page: DevicePageState.page,
      pageSize: DevicePageState.pageSize,
    });

    var pageResult = result.data;
    DevicePageState.total = pageResult.total;
    renderDeviceTable(pageResult.records);
    renderDevicePagination();
  } catch (err) {
    $('#deviceTableBody').innerHTML =
      '<tr><td colspan="10" class="table-empty" style="color:#e74c3c;">加载失败: ' + escapeHtml(err.message) + '</td></tr>';
    renderDevicePagination();
  }
}

// ---------- 设备弹窗控制 ----------

// 新增设备弹窗
function openDeviceAddModal() {
  $('#deviceAddModal').style.display = 'flex';
  $('#deviceBaseUrl').value = '';
  $('#deviceAddError').textContent = '';
}

function closeDeviceAddModal() {
  $('#deviceAddModal').style.display = 'none';
  $('#deviceAddError').textContent = '';
}

// 设备删除弹窗
function openDeviceDeleteModal(id, name) {
  $('#deleteDeviceName').textContent = name;
  $('#deviceDeleteModal').style.display = 'flex';
  $('#confirmDeviceDeleteBtn').setAttribute('data-id', id);
}

function closeDeviceDeleteModal() {
  $('#deviceDeleteModal').style.display = 'none';
}

// 设备详情弹窗（直接使用列表缓存数据，无需额外请求）
function openDeviceDetailModal(device) {
  $('#deviceDetailModal').style.display = 'flex';
  $('#deviceDetailTitle').textContent = '设备详情 — ' + escapeHtml(device.deviceName);
  // 确保运行状态区域可见（屏幕详情可能会隐藏）
  $('#deviceStatusGrid').parentElement.style.display = '';

  // 基本信息
  var infoHtml = '';
  infoHtml += '<div class="detail-item"><span class="detail-label">设备名称</span><span class="detail-value">' + escapeHtml(device.deviceName) + '</span></div>';
  infoHtml += '<div class="detail-item"><span class="detail-label">设备类型</span><span class="detail-value">' + escapeHtml(device.deviceType) + '</span></div>';
  infoHtml += '<div class="detail-item"><span class="detail-label">型号</span><span class="detail-value">' + escapeHtml(device.model) + '</span></div>';
  infoHtml += '<div class="detail-item"><span class="detail-label">序列号</span><span class="detail-value">' + escapeHtml(device.serialNumber) + '</span></div>';
  var inputChannels = [];
  if (device.inputChannel1) inputChannels.push(device.inputChannel1);
  if (device.inputChannel2) inputChannels.push(device.inputChannel2);
  var outputChannels = [];
  if (device.outputChannel1) outputChannels.push(device.outputChannel1);
  if (device.outputChannel2) outputChannels.push(device.outputChannel2);
  if (device.outputChannel3) outputChannels.push(device.outputChannel3);
  infoHtml += '<div class="detail-item"><span class="detail-label">设备类别</span><span class="detail-value">' + (device.deviceCategory === 'INPUT' ? '输入设备' : device.deviceCategory === 'OUTPUT' ? '输出设备' : (device.deviceCategory || '')) + '</span></div>';
  infoHtml += '<div class="detail-item"><span class="detail-label">输入通道</span><span class="detail-value">' + (inputChannels.length > 0 ? inputChannels.join(', ') : '无') + '</span></div>';
  infoHtml += '<div class="detail-item"><span class="detail-label">输出通道</span><span class="detail-value">' + (outputChannels.length > 0 ? outputChannels.join(', ') : '无') + '</span></div>';
  infoHtml += '<div class="detail-item"><span class="detail-label">最大分辨率</span><span class="detail-value">' + escapeHtml(device.maxResolution) + '</span></div>';
  infoHtml += '<div class="detail-item"><span class="detail-label">Base URL</span><span class="detail-value" style="font-family:monospace;font-size:12px;">' + escapeHtml(device.baseUrl) + '</span></div>';
  $('#deviceInfoGrid').innerHTML = infoHtml;

  // 运行状态
  var statusHtml = '';
  statusHtml += '<div class="detail-item"><span class="detail-label">在线状态</span><span class="detail-value">' + (device.online === 1 ? '<span class="status-dot online"></span> 在线' : '<span class="status-dot offline"></span> 离线') + '</span></div>';
  statusHtml += '<div class="detail-item"><span class="detail-label">启用状态</span><span class="detail-value">' + (device.enabled === 1 ? '<span class="status-tag status-enabled">已启用</span>' : '<span class="status-tag status-disabled">已禁用</span>') + '</span></div>';
  statusHtml += '<div class="detail-item"><span class="detail-label">最后心跳</span><span class="detail-value">' + (device.lastHeartbeat ? formatDateTime(device.lastHeartbeat) : '暂无') + '</span></div>';
  $('#deviceStatusGrid').innerHTML = statusHtml;

  // 设备能力（异步加载，显示占位文字）
  $('#deviceCapabilityGrid').innerHTML = '<div class="detail-loading">加载中...</div>';
  DeviceApi.getCapability(device.id).then(function (result) {
    var cap = result.data;
    var capHtml = '';
    if (device.deviceCategory === 'OUTPUT') {
      capHtml += '<div class="detail-item"><span class="detail-label">最大窗口数</span><span class="detail-value">' + cap.maxWindows + '</span></div>';
      capHtml += '<div class="detail-item"><span class="detail-label">窗口移动</span><span class="detail-value">' + (cap.supportMove ? '支持' : '不支持') + '</span></div>';
      capHtml += '<div class="detail-item"><span class="detail-label">窗口缩放</span><span class="detail-value">' + (cap.supportResize ? '支持' : '不支持') + '</span></div>';
      capHtml += '<div class="detail-item"><span class="detail-label">窗口叠加</span><span class="detail-value">' + (cap.supportOverlay ? '支持' : '不支持') + '</span></div>';
    }
    capHtml += '<div class="detail-item"><span class="detail-label">最大分辨率</span><span class="detail-value">' + escapeHtml(cap.maxResolution) + '</span></div>';
    var capInputChannels = [];
    if (cap.inputChannel1) capInputChannels.push(cap.inputChannel1);
    if (cap.inputChannel2) capInputChannels.push(cap.inputChannel2);
    var capOutputChannels = [];
    if (cap.outputChannel1) capOutputChannels.push(cap.outputChannel1);
    if (cap.outputChannel2) capOutputChannels.push(cap.outputChannel2);
    if (cap.outputChannel3) capOutputChannels.push(cap.outputChannel3);
    capHtml += '<div class="detail-item"><span class="detail-label">输入通道</span><span class="detail-value">' + (capInputChannels.length > 0 ? capInputChannels.join(', ') : '无') + '</span></div>';
    capHtml += '<div class="detail-item"><span class="detail-label">输出通道</span><span class="detail-value">' + (capOutputChannels.length > 0 ? capOutputChannels.join(', ') : '无') + '</span></div>';
    $('#deviceCapabilityGrid').innerHTML = capHtml;
  }).catch(function () {
    $('#deviceCapabilityGrid').innerHTML = '<div class="detail-loading">获取能力信息失败</div>';
  });
}

function closeDeviceDetailModal() {
  $('#deviceDetailModal').style.display = 'none';
}

// 搜索发现设备弹窗
async function openDiscoverModal() {
  $('#discoverModal').style.display = 'flex';
  $('#discoverContent').innerHTML = '<div class="discover-hint">正在搜索局域网内的模拟设备，请稍候...</div>';
  await doDiscover();
}

function closeDiscoverModal() {
  $('#discoverModal').style.display = 'none';
}

async function doDiscover() {
  try {
    var result = await DeviceApi.discover();
    var nodes = result.data;

    if (!nodes || nodes.length === 0) {
      $('#discoverContent').innerHTML = '<div class="discover-empty">未发现设备</div>';
      return;
    }

    var html = '<div class="discover-list">';
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      html += '<div class="discover-item">';
      html += '<div><span class="discover-item-baseurl">' + escapeHtml(node.baseUrl) + '</span>';
      if (node.added) {
        html += '<span class="discover-item-badge added">已添加</span>';
      }
      html += '</div>';
      if (node.added) {
        html += '<button class="btn btn-outline btn-sm" disabled>已添加</button>';
      } else {
        html += '<button class="btn btn-primary btn-sm discover-add-btn" data-baseurl="' + escapeHtml(node.baseUrl) + '">添加</button>';
      }
      html += '</div>';
    }
    html += '</div>';
    $('#discoverContent').innerHTML = html;

    // 绑定添加按钮事件
    var addBtns = $('#discoverContent').querySelectorAll('.discover-add-btn');
    for (var j = 0; j < addBtns.length; j++) {
      addBtns[j].addEventListener('click', async function () {
        var baseUrl = this.getAttribute('data-baseurl');
        this.disabled = true;
        this.textContent = '添加中...';
        try {
          await DeviceApi.add(baseUrl);
          closeDiscoverModal();
          showToast('设备添加成功', 'success');
          loadDeviceList();
        } catch (err) {
          showToast('添加失败: ' + err.message, 'error');
          this.disabled = false;
          this.textContent = '添加';
        }
      });
    }
  } catch (err) {
    $('#discoverContent').innerHTML = '<div class="discover-empty" style="color:#e74c3c;">搜索失败: ' + escapeHtml(err.message) + '</div>';
  }
}

// ---------- 设备操作 ----------

// 添加设备
async function addDevice() {
  var baseUrl = $('#deviceBaseUrl').value.trim();

  if (!baseUrl) {
    $('#deviceAddError').textContent = '请输入设备地址 (Base URL)';
    return;
  }

  // 只允许 IP+端口 格式，不允许 localhost 或域名
  if (!/^https?:\/\/\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+$/.test(baseUrl)) {
    $('#deviceAddError').textContent = '请输入有效的 IP+端口 地址，例如 http://192.168.1.100:8086';
    return;
  }

  $('#deviceAddError').textContent = '';
  var saveBtn = $('#saveDeviceBtn');
  saveBtn.disabled = true;
  saveBtn.textContent = '添加中...';

  try {
    await DeviceApi.add(baseUrl);
    showToast('设备添加成功', 'success');
    closeDeviceAddModal();
    loadDeviceList();
  } catch (err) {
    $('#deviceAddError').textContent = err.message;
  } finally {
    saveBtn.disabled = false;
    saveBtn.textContent = '添加';
  }
}

// 删除设备
async function confirmDeviceDelete() {
  var id = $('#confirmDeviceDeleteBtn').getAttribute('data-id');
  var btn = $('#confirmDeviceDeleteBtn');
  btn.disabled = true;
  btn.textContent = '删除中...';

  try {
    await DeviceApi.delete(id);
    showToast('设备删除成功', 'success');
    closeDeviceDeleteModal();
    // 如果当前页删空了，回到上一页
    if (DevicePageState.page > 1 && (DevicePageState.total - 1) <= (DevicePageState.page - 1) * DevicePageState.pageSize) {
      DevicePageState.page--;
    }
    loadDeviceList();
  } catch (err) {
    showToast('删除失败: ' + err.message, 'error');
    closeDeviceDeleteModal();
  } finally {
    btn.disabled = false;
    btn.textContent = '确认删除';
  }
}

// 启用/禁用设备
async function toggleDeviceEnabled(id, currentEnabled) {
  var newEnabled = currentEnabled === 1 ? 0 : 1;
  var actionText = newEnabled === 1 ? '启用' : '禁用';

  try {
    await DeviceApi.setEnabled(id, newEnabled);
    showToast('设备已' + actionText, 'success');
    loadDeviceList();
  } catch (err) {
    showToast(actionText + '失败: ' + err.message, 'error');
  }
}

// 刷新设备信息
async function refreshDevice(id) {
  try {
    await DeviceApi.refresh(id);
    showToast('设备信息已刷新', 'success');
    loadDeviceList();
  } catch (err) {
    showToast('刷新失败: ' + err.message, 'error');
  }
}

// ---------- 大屏管理功能 ----------

function resetScreenPage() {
  ScreenPageState.page = 1;
  ScreenPageState.searchKeyword = '';
  $('#screenSearchKeyword').value = '';
}

// 当前登录用户缓存（加载设备列表时更新）
var _allDevicesCache = [];

async function loadScreenList() {
  try {
    $('#screenTableBody').innerHTML = '<tr><td colspan="8" class="table-empty">加载中...</td></tr>';

    var result = await ScreenApi.getPage({
      keyword: ScreenPageState.searchKeyword || null,
      page: ScreenPageState.page,
      pageSize: ScreenPageState.pageSize,
    });

    var pageResult = result.data;
    ScreenPageState.total = pageResult.total;
    renderScreenTable(pageResult.records);
    renderScreenPagination();
  } catch (err) {
    $('#screenTableBody').innerHTML =
      '<tr><td colspan="8" class="table-empty" style="color:#e74c3c;">加载失败: ' + escapeHtml(err.message) + '</td></tr>';
    renderScreenPagination();
  }
}

function renderScreenTable(records) {
  var tbody = $('#screenTableBody');

  if (!records || records.length === 0) {
    tbody.innerHTML = '<tr><td colspan="8" class="table-empty">暂无数据</td></tr>';
    return;
  }

  var html = '';
  for (var i = 0; i < records.length; i++) {
    var s = records[i];
    html += '<tr>';
    html += '<td>' + escapeHtml(s.screenName) + '</td>';
    html += '<td>' + s.rowsCount + '</td>';
    html += '<td>' + s.colsCount + '</td>';
    html += '<td>' + (s.cellWidth || 1920) + '×' + (s.cellHeight || 1080) + '</td>';
    html += '<td>' + s.cellCount + '</td>';
    html += '<td>' + (s.windowCount || 0) + '</td>';
    html += '<td>' + (s.createTime ? formatDateTime(s.createTime) : '-') + '</td>';
    html += '<td><div class="action-btns">';
    html += '<button class="btn btn-outline btn-xs screen-detail-btn" data-id="' + s.id + '">详情</button>';
    html += '<button class="btn btn-danger btn-xs screen-delete-btn" data-id="' + s.id + '" data-name="' + escapeHtml(s.screenName) + '">删除</button>';
    html += '</div></td>';
    html += '</tr>';
  }

  tbody.innerHTML = html;
}

function renderScreenPagination() {
  var totalPages = Math.ceil(ScreenPageState.total / ScreenPageState.pageSize) || 1;
  $('#screenPageInfo').textContent =
    '第 ' + ScreenPageState.page + ' 页 / 共 ' + totalPages + ' 页（共 ' + ScreenPageState.total + ' 条）';
  $('#screenPrevPageBtn').disabled = ScreenPageState.page <= 1;
  $('#screenNextPageBtn').disabled = ScreenPageState.page >= totalPages;
}

// 创建设备下拉选单选项
function buildDeviceOptions(selectedId) {
  var opts = '<option value="">-- 请选择输出设备 --</option>';
  for (var i = 0; i < _allDevicesCache.length; i++) {
    var d = _allDevicesCache[i];
    if (d.deviceCategory !== 'OUTPUT') continue;
    if (d.online !== 1 || d.enabled !== 1) continue;
    var sel = (selectedId && d.id === selectedId) ? ' selected' : '';
    opts += '<option value="' + d.id + '"' + sel + '>' + escapeHtml(d.deviceName) + ' (' + escapeHtml(d.maxResolution) + ')</option>';
  }
  return opts;
}

// 创建设备通道下拉选单
function buildChannelOptions(deviceId, selectedChannel) {
  var opts = '<option value="">-- 请选择通道 --</option>';
  if (!deviceId) return opts;
  for (var i = 0; i < _allDevicesCache.length; i++) {
    var d = _allDevicesCache[i];
    if (d.id !== parseInt(deviceId, 10)) continue;
    var channels = [];
    if (d.outputChannel1) channels.push(d.outputChannel1);
    if (d.outputChannel2) channels.push(d.outputChannel2);
    if (d.outputChannel3) channels.push(d.outputChannel3);
    for (var j = 0; j < channels.length; j++) {
      var sel = (selectedChannel && selectedChannel === channels[j]) ? ' selected' : '';
      opts += '<option value="' + escapeHtml(channels[j]) + '"' + sel + '>' + escapeHtml(channels[j]) + '</option>';
    }
    break;
  }
  return opts;
}

// 生成单元绑定表单
function generateCellBindForm(rows, cols) {
  var listEl = $('#cellBindList');
  var total = rows * cols;
  var html = '';
  for (var r = 0; r < rows; r++) {
    for (var c = 0; c < cols; c++) {
      html += '<div class="cell-bind-item">';
      html += '<span class="cell-bind-label">单元 [' + r + ',' + c + ']</span>';
      html += '<select class="bind-device-select" data-row="' + r + '" data-col="' + c + '">' + buildDeviceOptions() + '</select>';
      html += '<select class="bind-channel-select" data-row="' + r + '" data-col="' + c + '"><option value="">-- 请选择通道 --</option></select>';
      html += '</div>';
    }
  }
  listEl.innerHTML = html;

  // 设备下拉改变时联动通道下拉
  var deviceSelects = listEl.querySelectorAll('.bind-device-select');
  for (var i = 0; i < deviceSelects.length; i++) {
    deviceSelects[i].addEventListener('change', function () {
      var row = this.getAttribute('data-row');
      var col = this.getAttribute('data-col');
      var channelSelect = listEl.querySelector('.bind-channel-select[data-row="' + row + '"][data-col="' + col + '"]');
      channelSelect.innerHTML = buildChannelOptions(this.value);
    });
  }
}

// 行/列数变化时重新生成绑定表单
function onScreenGridChange() {
  var rows = parseInt($('#screenRows').value, 10) || 1;
  var cols = parseInt($('#screenCols').value, 10) || 1;
  generateCellBindForm(rows, cols);
}

// 创建大屏弹窗
async function openScreenCreateModal() {
  $('#screenCreateModal').style.display = 'flex';
  $('#screenCreateForm').reset();
  $('#screenCreateError').textContent = '';
  $('#screenRows').value = 2;
  $('#screenCols').value = 2;
  $('#screenCellWidth').value = 1920;
  $('#screenCellHeight').value = 1080;
  // 先加载设备列表，再生成绑定表单
  await loadAllDevicesForBind();
  generateCellBindForm(2, 2);
}

function closeScreenCreateModal() {
  $('#screenCreateModal').style.display = 'none';
  $('#screenCreateError').textContent = '';
}

// 加载所有设备供绑定选择
async function loadAllDevicesForBind() {
  try {
    var result = await DeviceApi.page({ page: 1, pageSize: 200 });
    _allDevicesCache = result.data.records || [];
  } catch (_) {
    _allDevicesCache = [];
  }
}

// 保存大屏
async function saveScreen() {
  var screenName = $('#screenName').value.trim();
  var rowsCount = parseInt($('#screenRows').value, 10) || 1;
  var colsCount = parseInt($('#screenCols').value, 10) || 1;
  var cellWidth = parseInt($('#screenCellWidth').value, 10) || 1920;
  var cellHeight = parseInt($('#screenCellHeight').value, 10) || 1080;

  if (!screenName) {
    $('#screenCreateError').textContent = '请输入大屏名称';
    return;
  }

  // 收集每个单元的绑定信息
  var cells = [];
  var deviceSelects = $('#cellBindList').querySelectorAll('.bind-device-select');
  var channelSelects = $('#cellBindList').querySelectorAll('.bind-channel-select');

  for (var i = 0; i < deviceSelects.length; i++) {
    var ds = deviceSelects[i];
    var cs = channelSelects[i];
    var row = parseInt(ds.getAttribute('data-row'), 10);
    var col = parseInt(ds.getAttribute('data-col'), 10);
    var deviceId = ds.value ? parseInt(ds.value, 10) : null;
    var channelName = cs.value || null;

    if (!deviceId || !channelName) {
      $('#screenCreateError').textContent = '单元 [' + row + ',' + col + '] 必须绑定设备';
      return;
    }

    cells.push({ rowIndex: row, colIndex: col, deviceId: deviceId, channelName: channelName });
  }

  var saveBtn = $('#saveScreenBtn');
  saveBtn.disabled = true;
  saveBtn.textContent = '创建中...';
  $('#screenCreateError').textContent = '';

  try {
    await ScreenApi.create({
      screenName: screenName,
      rowsCount: rowsCount,
      colsCount: colsCount,
      cellWidth: cellWidth,
      cellHeight: cellHeight,
      cells: cells,
    });
    showToast('大屏创建成功', 'success');
    closeScreenCreateModal();
    loadScreenList();
  } catch (err) {
    $('#screenCreateError').textContent = err.message;
  } finally {
    saveBtn.disabled = false;
    saveBtn.textContent = '创建';
  }
}

// 大屏删除弹窗
function openScreenDeleteModal(id, name) {
  $('#deleteScreenName').textContent = name;
  $('#screenDeleteModal').style.display = 'flex';
  $('#confirmScreenDeleteBtn').setAttribute('data-id', id);
}

function closeScreenDeleteModal() {
  $('#screenDeleteModal').style.display = 'none';
}

// 确认删除大屏
async function confirmScreenDelete() {
  var id = $('#confirmScreenDeleteBtn').getAttribute('data-id');
  var btn = $('#confirmScreenDeleteBtn');
  btn.disabled = true;
  btn.textContent = '删除中...';

  try {
    await ScreenApi.delete(id);
    showToast('大屏删除成功', 'success');
    closeScreenDeleteModal();
    if (ScreenPageState.page > 1 && (ScreenPageState.total - 1) <= (ScreenPageState.page - 1) * ScreenPageState.pageSize) {
      ScreenPageState.page--;
    }
    loadScreenList();
  } catch (err) {
    showToast('删除失败: ' + err.message, 'error');
    closeScreenDeleteModal();
  } finally {
    btn.disabled = false;
    btn.textContent = '确认删除';
  }
}

// 单元绑定设备弹窗
var _bindScreenId = null;
var _bindCellId = null;

async function openCellBindModal(screenId, cell) {
  _bindScreenId = screenId;
  _bindCellId = cell.id;
  $('#bindScreenId').value = screenId;
  $('#bindCellId').value = cell.id;
  $('#bindCellPosition').textContent = '行 ' + cell.rowIndex + ' / 列 ' + cell.colIndex;
  $('#cellBindError').textContent = '';
  $('#cellBindModal').style.display = 'flex';

  // 确保设备列表已加载
  if (_allDevicesCache.length === 0) {
    await loadAllDevicesForBind();
  }

  // 加载设备下拉选单
  $('#bindDeviceSelect').innerHTML = buildDeviceOptions(cell.deviceId || null);
  $('#bindChannelSelect').innerHTML = buildChannelOptions(cell.deviceId || null, cell.channelName || null);

  // 设备下拉联动通道
  $('#bindDeviceSelect').onchange = function () {
    $('#bindChannelSelect').innerHTML = buildChannelOptions(this.value);
  };
}

function closeCellBindModal() {
  $('#cellBindModal').style.display = 'none';
  $('#cellBindError').textContent = '';
}

// 保存单元绑定
async function saveCellBind() {
  var deviceId = parseInt($('#bindDeviceSelect').value, 10);
  var channelName = $('#bindChannelSelect').value;

  if (!deviceId) {
    $('#cellBindError').textContent = '请选择输出设备';
    return;
  }
  if (!channelName) {
    $('#cellBindError').textContent = '请选择输出通道';
    return;
  }

  var saveBtn = $('#saveCellBindBtn');
  saveBtn.disabled = true;
  saveBtn.textContent = '绑定中...';
  $('#cellBindError').textContent = '';

  try {
    await ScreenApi.bindCell(_bindScreenId, _bindCellId, {
      deviceId: deviceId,
      channelName: channelName,
    });
    showToast('设备通道绑定成功', 'success');
    closeCellBindModal();
    // 刷新大屏详情（如果在前台模式）
    if (AppMode.current === 'frontend' && AppMode.currentScreenId === _bindScreenId) {
      loadFrontendScreen(AppMode.currentScreenId);
    }
  } catch (err) {
    $('#cellBindError').textContent = err.message;
  } finally {
    saveBtn.disabled = false;
    saveBtn.textContent = '绑定';
  }
}

// 查看大屏详情（跳转到详情弹窗显示单元列表）
async function openScreenDetailModal(screenId) {
  try {
    var result = await ScreenApi.getDetail(screenId);
    var screenDetail = result.data;

    // 使用设备详情弹窗来展示
    $('#deviceDetailModal').style.display = 'flex';
    $('#deviceDetailTitle').textContent = '大屏详情 — ' + escapeHtml(screenDetail.screenName);

    // 基本信息
    var infoHtml = '';
    infoHtml += '<div class="detail-item"><span class="detail-label">大屏名称</span><span class="detail-value">' + escapeHtml(screenDetail.screenName) + '</span></div>';
    infoHtml += '<div class="detail-item"><span class="detail-label">布局</span><span class="detail-value">' + screenDetail.rowsCount + ' × ' + screenDetail.colsCount + '</span></div>';
    infoHtml += '<div class="detail-item"><span class="detail-label">单元分辨率</span><span class="detail-value">' + (screenDetail.cellWidth || 1920) + ' × ' + (screenDetail.cellHeight || 1080) + '</span></div>';
    infoHtml += '<div class="detail-item"><span class="detail-label">创建时间</span><span class="detail-value">' + (screenDetail.createTime ? formatDateTime(screenDetail.createTime) : '-') + '</span></div>';
    $('#deviceInfoGrid').innerHTML = infoHtml;

    // 运行状态（大屏无运行状态，隐藏该区域）
    $('#deviceStatusGrid').parentElement.style.display = 'none';

    // 单元列表
    var cells = screenDetail.cells || [];
    var cellHtml = '<div>';
    cellHtml += '<table class="data-table"><thead><tr><th>位置</th><th>设备</th><th>通道</th><th>类别</th><th>在线</th><th>最大窗口</th><th>移动</th><th>缩放</th><th>叠加</th><th>分辨率</th><th>操作</th></tr></thead><tbody>';
    for (var i = 0; i < cells.length; i++) {
      var c = cells[i];
      var pos = '[' + c.rowIndex + ',' + c.colIndex + ']';
      var deviceInfo = c.deviceName ? escapeHtml(c.deviceName) : '<span style="color:#999;">未绑定</span>';
      var channelInfo = c.channelName ? escapeHtml(c.channelName) : '-';
      var catInfo = c.deviceCategory ? (c.deviceCategory === 'OUTPUT' ? '<span class="status-tag status-disabled">输出</span>' : escapeHtml(c.deviceCategory)) : '-';
      var onlineInfo = c.online === 1 ? '<span class="status-dot online"></span>在线' : '<span class="status-dot offline"></span>离线';
      var maxWinInfo = c.maxWindows != null ? c.maxWindows : '-';
      var moveInfo = c.supportMove != null ? (c.supportMove === 1 ? '✓' : '✗') : '-';
      var resizeInfo = c.supportResize != null ? (c.supportResize === 1 ? '✓' : '✗') : '-';
      var overlayInfo = c.supportOverlay != null ? (c.supportOverlay === 1 ? '✓' : '✗') : '-';
      var resInfo = c.maxResolution || '-';
      var actionHtml = '<button class="btn btn-outline btn-xs cell-bind-btn" data-screen-id="' + screenDetail.id + '" data-cell-id="' + c.id + '" data-row="' + c.rowIndex + '" data-col="' + c.colIndex + '" data-device-id="' + (c.deviceId || '') + '" data-channel="' + escapeHtml(c.channelName || '') + '">更换绑定</button>';
      cellHtml += '<tr><td>' + pos + '</td><td>' + deviceInfo + '</td><td>' + channelInfo + '</td><td>' + catInfo + '</td><td>' + onlineInfo + '</td><td>' + maxWinInfo + '</td><td>' + moveInfo + '</td><td>' + resizeInfo + '</td><td>' + overlayInfo + '</td><td>' + resInfo + '</td><td>' + actionHtml + '</td></tr>';
    }
    cellHtml += '</tbody></table></div>';
    $('#deviceCapabilityGrid').innerHTML = cellHtml;

    // 绑定更换按钮事件
    var bindBtns = $('#deviceCapabilityGrid').querySelectorAll('.cell-bind-btn');
    for (var j = 0; j < bindBtns.length; j++) {
      bindBtns[j].addEventListener('click', function () {
        var cellData = {
          id: parseInt(this.getAttribute('data-cell-id'), 10),
          rowIndex: parseInt(this.getAttribute('data-row'), 10),
          colIndex: parseInt(this.getAttribute('data-col'), 10),
          deviceId: this.getAttribute('data-device-id') ? parseInt(this.getAttribute('data-device-id'), 10) : null,
          channelName: this.getAttribute('data-channel') || null,
        };
        openCellBindModal(parseInt(this.getAttribute('data-screen-id'), 10), cellData);
      });
    }
  } catch (err) {
    showToast('获取大屏详情失败: ' + err.message, 'error');
  }
}

// ---------- 前后台切换 ----------

function switchToFrontend() {
  AppMode.current = 'frontend';

  // 隐藏侧边栏和后台面板
  $('#sidebar').style.display = 'none';
  var panels = $$('.content-panel');
  for (var i = 0; i < panels.length; i++) {
    panels[i].classList.remove('active');
  }
  $('.main-area').style.marginLeft = '0';

  // 显示前台视图
  $('#frontendView').classList.add('active');

  // 更新顶栏
  $('#moduleTitle').textContent = '前台展示';
  $('#switchToFrontendBtn').textContent = '后台管理';
  $('#switchToFrontendBtn').classList.add('active');

  // 加载大屏列表到下拉选择
  loadScreenSelect();
}

function switchToBackend() {
  AppMode.current = 'backend';
  AppMode.currentScreenId = null;
  sessionStorage.removeItem('frontend_screen_id');

  // 显示侧边栏和后台面板
  $('#sidebar').style.display = '';
  $('.main-area').style.marginLeft = '';
  $('#frontendView').classList.remove('active');

  // 清空前台画布和信号源
  if (typeof ScreenPaint !== 'undefined' && ScreenPaint.clear) {
    ScreenPaint.clear();
  }
  if (typeof SignalSource !== 'undefined' && SignalSource.clear) {
    SignalSource.clear();
  }

  // 隐藏画布
  $('#screenCanvas').style.display = 'none';
  $('#canvasPlaceholder').style.display = '';
  $('#canvasToolbar').style.display = 'none';

  // 更新顶栏
  $('#moduleTitle').textContent = '大屏配置';
  $('#switchToFrontendBtn').textContent = '前台展示';
  $('#switchToFrontendBtn').classList.remove('active');

  // 切回后台管理面板
  switchModule('screen');
}

// 加载大屏选择下拉框
async function loadScreenSelect() {
  try {
    var result = await ScreenApi.getPage({ page: 1, pageSize: 100 });
    var screens = result.data.records || [];

    if (screens.length === 0) {
      $('#screenSelectDialog').style.display = 'none';
      $('#canvasPlaceholder').querySelector('.placeholder-text').textContent = '暂无可用大屏';
      $('#canvasPlaceholder').querySelector('.placeholder-hint').textContent = '请先在后台管理中创建大屏并绑定输出设备';
      return;
    }

    // 有多个大屏时显示选择下拉
    if (screens.length === 1) {
      loadFrontendScreen(screens[0].id);
    } else {
      var dropdown = $('#screenSelectDropdown');
      var html = '<option value="">-- 请选择大屏 --</option>';
      for (var i = 0; i < screens.length; i++) {
        html += '<option value="' + screens[i].id + '">' + escapeHtml(screens[i].screenName) + ' (' + screens[i].rowsCount + '×' + screens[i].colsCount + ')</option>';
      }
      dropdown.innerHTML = html;
      $('#screenSelectDialog').style.display = '';
      $('#screenCanvas').style.display = 'none';
      $('#canvasPlaceholder').querySelector('.placeholder-text').textContent = '请选择大屏';
      $('#canvasPlaceholder').querySelector('.placeholder-hint').textContent = '';
    }
  } catch (err) {
    showToast('加载大屏列表失败: ' + err.message, 'error');
  }
}

// 加载前台大屏
async function loadFrontendScreen(screenId) {
  try {
    AppMode.currentScreenId = screenId;
    sessionStorage.setItem('frontend_screen_id', screenId);

    // 显示画布
    $('#screenCanvas').style.display = '';
    $('#canvasPlaceholder').style.display = 'none';
    $('#screenSelectDialog').style.display = 'none';
    $('#canvasToolbar').style.display = '';

    // 加载大屏详情
    var detailResult = await ScreenApi.getDetail(screenId);
    var screenDetail = detailResult.data;

    // 加载窗口列表
    var windowsResult = await WindowApi.list(screenId);
    var windows = windowsResult.data || [];

    // 渲染画布
    if (typeof ScreenPaint !== 'undefined') {
      ScreenPaint.render(screenDetail, windows);
    }

    // 加载信号源
    if (typeof SignalSource !== 'undefined') {
      SignalSource.loadSignalSources(screenDetail);
      // 同时加载输出设备窗口信息
      SignalSource.loadOutputDevices(screenId);
    }

    // 更新顶栏大屏选择
    updateTopbarScreenSelector(screenDetail);

  } catch (err) {
    showToast('加载大屏失败: ' + err.message, 'error');
  }
}

// 更新顶栏的大屏选择器
function updateTopbarScreenSelector(screenDetail) {
  var actionsEl = $('#topbarActions');
  // 移除旧的选择器
  var oldSelector = actionsEl.querySelector('.screen-selector');
  if (oldSelector) oldSelector.remove();

  var selectorHtml = '<div class="screen-selector">';
  selectorHtml += '<span>当前大屏：</span>';
  selectorHtml += '<strong>' + escapeHtml(screenDetail.screenName) + '</strong>';
  selectorHtml += '<span style="font-size:12px;color:var(--text-muted)">（' + screenDetail.rowsCount + '×' + screenDetail.colsCount + '）</span>';
  selectorHtml += '<button class="btn btn-outline btn-xs" id="changeScreenBtn">切换大屏</button>';
  selectorHtml += '</div>';

  // 临时插入
  var switchBtn = $('#switchToFrontendBtn');
  var div = document.createElement('div');
  div.innerHTML = selectorHtml;
  var selectorEl = div.firstChild;
  actionsEl.insertBefore(selectorEl, switchBtn);

  // 绑定切换大屏按钮
  selectorEl.querySelector('#changeScreenBtn').addEventListener('click', function () {
    // 显示选择对话框
    $('#screenCanvas').style.display = 'none';
    $('#canvasPlaceholder').style.display = '';
    $('#canvasToolbar').style.display = 'none';
    if (typeof ScreenPaint !== 'undefined' && ScreenPaint.clear) {
      ScreenPaint.clear();
    }
    if (typeof SignalSource !== 'undefined' && SignalSource.clear) {
      SignalSource.clear();
    }
    // 移除选择器
    var sel = actionsEl.querySelector('.screen-selector');
    if (sel) sel.remove();
    loadScreenSelect();
  });
}

function escapeHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// ---------- 加载用户列表 ----------
async function loadUserList() {
  try {
    $('#tableBody').innerHTML = '<tr><td colspan="12" class="table-empty">加载中...</td></tr>';

    var result = await UserApi.getPage({
      username: PageState.searchUsername || null,
      page: PageState.page,
      pageSize: PageState.pageSize,
    });

    var pageResult = result.data;
    PageState.total = pageResult.total;
    renderTable(pageResult.records);
    renderPagination();
  } catch (err) {
    $('#tableBody').innerHTML =
      '<tr><td colspan="12" class="table-empty" style="color:#e74c3c;">加载失败: ' + escapeHtml(err.message) + '</td></tr>';
    renderPagination();
  }
}

// ---------- 弹窗控制 ----------
function openModal(title) {
  $('#modalTitle').textContent = title;
  $('#userModal').style.display = 'flex';
  $('#userForm').reset();
  $('#formError').textContent = '';
  $('#formUserId').value = '';
}

function closeModal() {
  $('#userModal').style.display = 'none';
  $('#formError').textContent = '';
}

function openDeleteModal(username) {
  $('#deleteUsername').textContent = username;
  $('#deleteModal').style.display = 'flex';
  // 把待删除的用户名暂存到按钮上
  $('#confirmDeleteBtn').setAttribute('data-username', username);
}

function closeDeleteModal() {
  $('#deleteModal').style.display = 'none';
}

// ---------- 表单模式切换 ----------
function setFormMode(mode) {
  // mode: 'add' | 'edit'
  if (mode === 'add') {
    $('#passwordRequired').style.display = 'inline';
    $('#passwordHint').style.display = 'none';
    $('#formPassword').required = true;
    $('#formPassword').placeholder = '请输入密码';
  } else {
    $('#passwordRequired').style.display = 'none';
    $('#passwordHint').style.display = 'block';
    $('#formPassword').required = false;
    $('#formPassword').placeholder = '留空则不修改密码';
  }
}

// ---------- 填充编辑表单 ----------
async function openEditModal(userId) {
  try {
    var result = await UserApi.getById(userId);
    var user = result.data;
    openModal('编辑用户');
    setFormMode('edit');

    $('#formUserId').value = user.id;
    $('#formUsername').value = user.username || '';
    $('#formPassword').value = '';
    $('#formRole').value = user.role || 'user';
    $('#formUserType').value = user.userType || '普通用户';
    $('#formUserLevel').value = user.userLevel != null ? user.userLevel : 1;
    $('#formDepartment').value = user.department || '';
    $('#formPosition').value = user.position || '';
    $('#formPhone').value = user.phone || '';
    $('#formEmail').value = user.email || '';
    $('#formIsEnabled').value = user.isEnabled === 1 ? '1' : '0';

    // 处理日期格式
    if (user.validUntil) {
      var d = typeof user.validUntil === 'string'
        ? user.validUntil.substring(0, 10)
        : user.validUntil;
      $('#formValidUntil').value = d;
    } else {
      $('#formValidUntil').value = '';
    }

    // 暂存原始密码，编辑提交时若密码为空则使用原始密码
    $('#formPassword').setAttribute('data-original-password', user.password || '');
  } catch (err) {
    showToast('获取用户信息失败: ' + err.message, 'error');
  }
}

// ---------- 收集表单数据 ----------
function collectFormData() {
  var userId = $('#formUserId').value;
  var isEdit = !!userId;

  var user = {
    username: $('#formUsername').value.trim(),
    password: $('#formPassword').value,
    role: $('#formRole').value,
    userType: $('#formUserType').value,
    userLevel: parseInt($('#formUserLevel').value, 10) || 1,
    department: $('#formDepartment').value.trim(),
    position: $('#formPosition').value.trim(),
    phone: $('#formPhone').value.trim(),
    email: $('#formEmail').value.trim(),
    isEnabled: parseInt($('#formIsEnabled').value, 10),
    validUntil: $('#formValidUntil').value
      ? $('#formValidUntil').value + 'T00:00:00'
      : null,
  };

  if (isEdit) {
    user.id = parseInt(userId, 10);
    // 密码为空则使用原始密码
    if (!user.password) {
      user.password = $('#formPassword').getAttribute('data-original-password') || '';
    }
  }

  return user;
}

// ---------- 表单验证 ----------
function validateForm(user, isEdit) {
  var errors = [];

  if (!user.username) {
    errors.push('请输入用户名');
  } else if (user.username.length < 2) {
    errors.push('用户名至少需要2个字符');
  }

  if (!isEdit && !user.password) {
    errors.push('请输入密码');
  }
  if (user.password && user.password.length < 4) {
    errors.push('密码至少需要4个字符');
  }

  if (user.phone && !/^[\d\-+\s()]{7,20}$/.test(user.phone)) {
    errors.push('手机号格式不正确');
  }

  if (user.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(user.email)) {
    errors.push('邮箱格式不正确');
  }

  return errors;
}

// ---------- 保存用户 ----------
async function saveUser() {
  var isEdit = !!$('#formUserId').value;
  var user = collectFormData();
  var errors = validateForm(user, isEdit);

  if (errors.length > 0) {
    $('#formError').textContent = errors.join('；');
    return;
  }

  $('#formError').textContent = '';
  var saveBtn = $('#saveUserBtn');
  saveBtn.disabled = true;
  saveBtn.textContent = '保存中...';

  try {
    if (isEdit) {
      await UserApi.update(user);
      showToast('用户修改成功', 'success');
    } else {
      await UserApi.add(user);
      showToast('用户新增成功', 'success');
    }
    closeModal();
    loadUserList();
  } catch (err) {
    $('#formError').textContent = err.message;
  } finally {
    saveBtn.disabled = false;
    saveBtn.textContent = '保存';
  }
}

// ---------- 删除用户 ----------
async function confirmDelete() {
  var username = $('#confirmDeleteBtn').getAttribute('data-username');
  var btn = $('#confirmDeleteBtn');
  btn.disabled = true;
  btn.textContent = '删除中...';

  try {
    await UserApi.delete(username);
    showToast('用户删除成功', 'success');
    closeDeleteModal();
    // 如果当前页删空了，回到上一页
    if (PageState.page > 1 && (PageState.total - 1) <= (PageState.page - 1) * PageState.pageSize) {
      PageState.page--;
    }
    loadUserList();
  } catch (err) {
    showToast('删除失败: ' + err.message, 'error');
    closeDeleteModal();
  } finally {
    btn.disabled = false;
    btn.textContent = '确认删除';
  }
}

// ---------- 事件绑定 ----------
function bindEvents() {
  // 登录表单
  $('#loginForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    var username = $('#loginUsername').value.trim();
    var password = $('#loginPassword').value.trim();

    if (!username || !password) {
      $('#loginError').textContent = '请输入用户名和密码';
      return;
    }

    $('#loginError').textContent = '';
    var loginBtn = $('#loginBtn');
    loginBtn.disabled = true;
    loginBtn.textContent = '登录中...';

    try {
      var result = await UserApi.login(username, password);
      var token = result.data;
      Auth.setToken(token);
      Auth.setUsername(username);
      showMainView();
      resetPage();
      loadUserList();
    } catch (err) {
      $('#loginError').textContent = err.message;
    } finally {
      loginBtn.disabled = false;
      loginBtn.textContent = '登 录';
    }
  });

  // 退出
  $('#logoutBtn').addEventListener('click', async function () {
    try { await UserApi.logout(); } catch (_) { /* 忽略 */ }
    Auth.clearAll();
    showLoginView();
    showToast('已退出登录', 'info');
  });

  // 侧边栏导航切换 — 直接绑定到每个 nav-item
  var navItems = $$('#sidebarNav .nav-item:not(.disabled)');
  for (var ni = 0; ni < navItems.length; ni++) {
    (function (navItem) {
      navItem.addEventListener('click', function (e) {
        e.preventDefault();
        var moduleName = navItem.getAttribute('data-module');
        if (moduleName) {
          switchModule(moduleName);
        }
      });
    })(navItems[ni]);
  }

  // 搜索
  $('#searchBtn').addEventListener('click', function () {
    PageState.searchUsername = $('#searchInput').value.trim();
    PageState.page = 1;
    loadUserList();
  });

  $('#searchInput').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      PageState.searchUsername = $('#searchInput').value.trim();
      PageState.page = 1;
      loadUserList();
    }
  });

  $('#resetSearchBtn').addEventListener('click', function () {
    $('#searchInput').value = '';
    PageState.searchUsername = '';
    PageState.page = 1;
    loadUserList();
  });

  // 分页
  $('#prevPageBtn').addEventListener('click', function () {
    if (PageState.page > 1) {
      PageState.page--;
      loadUserList();
    }
  });

  $('#nextPageBtn').addEventListener('click', function () {
    var totalPages = Math.ceil(PageState.total / PageState.pageSize) || 1;
    if (PageState.page < totalPages) {
      PageState.page++;
      loadUserList();
    }
  });

  $('#pageSizeSelect').addEventListener('change', function () {
    PageState.pageSize = parseInt(this.value, 10);
    PageState.page = 1;
    loadUserList();
  });

  // 新增用户
  $('#addUserBtn').addEventListener('click', function () {
    openModal('新增用户');
    setFormMode('add');
  });

  // 表格中的编辑/删除按钮（事件委托）
  $('#tableBody').addEventListener('click', function (e) {
    var target = e.target;
    if (target.classList.contains('edit-btn')) {
      openEditModal(target.getAttribute('data-id'));
    }
    if (target.classList.contains('delete-btn')) {
      var username = target.getAttribute('data-username');
      // 检查是否是 admin
      // 找到同一行的角色列来判断
      var row = target.closest('tr');
      var roleCell = row.querySelectorAll('td')[2];
      if (roleCell && roleCell.textContent.trim() === 'admin') {
        showToast('管理员用户不允许删除', 'error');
        return;
      }
      openDeleteModal(username);
    }
  });

  // 弹窗关闭
  $('#closeModalBtn').addEventListener('click', closeModal);
  $('#cancelModalBtn').addEventListener('click', closeModal);
  $('#userModal').addEventListener('click', function (e) {
    if (e.target === this) closeModal();
  });

  // 保存用户
  $('#saveUserBtn').addEventListener('click', saveUser);

  // 删除弹窗
  $('#closeDeleteModalBtn').addEventListener('click', closeDeleteModal);
  $('#cancelDeleteBtn').addEventListener('click', closeDeleteModal);
  $('#deleteModal').addEventListener('click', function (e) {
    if (e.target === this) closeDeleteModal();
  });
  $('#confirmDeleteBtn').addEventListener('click', confirmDelete);

  // ==================== 设备管理事件绑定 ====================

  // 设备搜索
  $('#deviceSearchBtn').addEventListener('click', function () {
    DevicePageState.searchDeviceName = $('#deviceSearchName').value.trim();
    DevicePageState.searchDeviceType = $('#deviceSearchType').value;
    DevicePageState.page = 1;
    loadDeviceList();
  });

  $('#deviceSearchName').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      DevicePageState.searchDeviceName = $('#deviceSearchName').value.trim();
      DevicePageState.searchDeviceType = $('#deviceSearchType').value;
      DevicePageState.page = 1;
      loadDeviceList();
    }
  });

  $('#deviceResetSearchBtn').addEventListener('click', function () {
    $('#deviceSearchName').value = '';
    $('#deviceSearchType').value = '';
    DevicePageState.searchDeviceName = '';
    DevicePageState.searchDeviceType = '';
    DevicePageState.page = 1;
    loadDeviceList();
  });

  // 设备分页
  $('#devicePrevPageBtn').addEventListener('click', function () {
    if (DevicePageState.page > 1) {
      DevicePageState.page--;
      loadDeviceList();
    }
  });

  $('#deviceNextPageBtn').addEventListener('click', function () {
    var totalPages = Math.ceil(DevicePageState.total / DevicePageState.pageSize) || 1;
    if (DevicePageState.page < totalPages) {
      DevicePageState.page++;
      loadDeviceList();
    }
  });

  $('#devicePageSizeSelect').addEventListener('change', function () {
    DevicePageState.pageSize = parseInt(this.value, 10);
    DevicePageState.page = 1;
    loadDeviceList();
  });

  // 新增设备按钮
  $('#addDeviceBtn').addEventListener('click', function () {
    openDeviceAddModal();
  });

  // 新增设备弹窗事件
  $('#closeDeviceAddModalBtn').addEventListener('click', closeDeviceAddModal);
  $('#cancelDeviceAddBtn').addEventListener('click', closeDeviceAddModal);
  $('#deviceAddModal').addEventListener('click', function (e) {
    if (e.target === this) closeDeviceAddModal();
  });
  $('#saveDeviceBtn').addEventListener('click', addDevice);

  $('#deviceBaseUrl').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') addDevice();
  });

  // 设备删除弹窗事件
  $('#closeDeviceDeleteModalBtn').addEventListener('click', closeDeviceDeleteModal);
  $('#cancelDeviceDeleteBtn').addEventListener('click', closeDeviceDeleteModal);
  $('#deviceDeleteModal').addEventListener('click', function (e) {
    if (e.target === this) closeDeviceDeleteModal();
  });
  $('#confirmDeviceDeleteBtn').addEventListener('click', confirmDeviceDelete);

  // 设备详情弹窗事件
  $('#closeDeviceDetailModalBtn').addEventListener('click', closeDeviceDetailModal);
  $('#closeDeviceDetailBtn').addEventListener('click', closeDeviceDetailModal);
  $('#deviceDetailModal').addEventListener('click', function (e) {
    if (e.target === this) closeDeviceDetailModal();
  });

  // 搜索发现设备弹窗事件
  $('#discoverDeviceBtn').addEventListener('click', function () {
    openDiscoverModal();
  });
  $('#closeDiscoverModalBtn').addEventListener('click', closeDiscoverModal);
  $('#cancelDiscoverBtn').addEventListener('click', closeDiscoverModal);
  $('#discoverModal').addEventListener('click', function (e) {
    if (e.target === this) closeDiscoverModal();
  });
  $('#refreshDiscoverBtn').addEventListener('click', function () {
    $('#discoverContent').innerHTML = '<div class="discover-hint">正在搜索局域网内的模拟设备，请稍候...</div>';
    doDiscover();
  });

  // 设备表格操作按钮（事件委托）
  $('#deviceTableBody').addEventListener('click', function (e) {
    var target = e.target;

    // 详情按钮（从缓存取数据，不发请求）
    if (target.classList.contains('detail-btn')) {
      var device = DeviceCache[target.getAttribute('data-id')];
      if (device) {
        openDeviceDetailModal(device);
      }
    }

    // 刷新按钮
    if (target.classList.contains('refresh-btn')) {
      var refreshBtn = target;
      refreshBtn.disabled = true;
      refreshBtn.textContent = '刷新中...';
      refreshDevice(target.getAttribute('data-id')).finally(function () {
        refreshBtn.disabled = false;
        refreshBtn.textContent = '刷新';
      });
    }

    // 启用/禁用按钮
    if (target.classList.contains('toggle-btn')) {
      var toggleBtn = target;
      toggleBtn.disabled = true;
      var id = toggleBtn.getAttribute('data-id');
      var currentEnabled = parseInt(toggleBtn.getAttribute('data-enabled'), 10);
      toggleDeviceEnabled(id, currentEnabled).finally(function () {
        toggleBtn.disabled = false;
      });
    }

    // 删除按钮
    if (target.classList.contains('delete-device-btn')) {
      var deviceId = target.getAttribute('data-id');
      var deviceName = target.getAttribute('data-name');
      openDeviceDeleteModal(deviceId, deviceName);
    }
  });

  // ==================== 前后台切换 ====================
  $('#switchToFrontendBtn').addEventListener('click', function () {
    if (AppMode.current === 'frontend') {
      switchToBackend();
    } else {
      switchToFrontend();
    }
  });

  // 加载大屏按钮
  $('#loadScreenBtn').addEventListener('click', function () {
    var screenId = parseInt($('#screenSelectDropdown').value, 10);
    if (!screenId) {
      showToast('请先选择大屏', 'warning');
      return;
    }
    loadFrontendScreen(screenId);
  });

  // 一键清空窗口按钮
  $('#clearWindowsBtn').addEventListener('click', async function () {
    if (!AppMode.currentScreenId) return;
    if (!confirm('确定要清空当前大屏的所有窗口吗？此操作不可撤销。')) return;

    var btn = this;
    btn.disabled = true;
    btn.textContent = '清空中...';
    try {
      await WindowApi.clearAll(AppMode.currentScreenId);
      showToast('窗口已清空', 'success');
      // 重新加载大屏
      loadFrontendScreen(AppMode.currentScreenId);
    } catch (err) {
      showToast('清空失败: ' + err.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = '一键清空';
    }
  });

  // ==================== 大屏管理事件绑定 ====================

  // 大屏搜索
  $('#screenSearchBtn').addEventListener('click', function () {
    ScreenPageState.searchKeyword = $('#screenSearchKeyword').value.trim();
    ScreenPageState.page = 1;
    loadScreenList();
  });

  $('#screenSearchKeyword').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      ScreenPageState.searchKeyword = $('#screenSearchKeyword').value.trim();
      ScreenPageState.page = 1;
      loadScreenList();
    }
  });

  $('#screenResetSearchBtn').addEventListener('click', function () {
    $('#screenSearchKeyword').value = '';
    ScreenPageState.searchKeyword = '';
    ScreenPageState.page = 1;
    loadScreenList();
  });

  // 大屏分页
  $('#screenPrevPageBtn').addEventListener('click', function () {
    if (ScreenPageState.page > 1) {
      ScreenPageState.page--;
      loadScreenList();
    }
  });

  $('#screenNextPageBtn').addEventListener('click', function () {
    var totalPages = Math.ceil(ScreenPageState.total / ScreenPageState.pageSize) || 1;
    if (ScreenPageState.page < totalPages) {
      ScreenPageState.page++;
      loadScreenList();
    }
  });

  $('#screenPageSizeSelect').addEventListener('change', function () {
    ScreenPageState.pageSize = parseInt(this.value, 10);
    ScreenPageState.page = 1;
    loadScreenList();
  });

  // 创建大屏按钮
  $('#addScreenBtn').addEventListener('click', function () {
    openScreenCreateModal();
  });

  // 行/列数变化
  $('#screenRows').addEventListener('change', onScreenGridChange);
  $('#screenCols').addEventListener('change', onScreenGridChange);

  $('#screenRows').addEventListener('input', onScreenGridChange);
  $('#screenCols').addEventListener('input', onScreenGridChange);

  // 创建大屏弹窗事件
  $('#closeScreenCreateModalBtn').addEventListener('click', closeScreenCreateModal);
  $('#cancelScreenCreateBtn').addEventListener('click', closeScreenCreateModal);
  $('#screenCreateModal').addEventListener('click', function (e) {
    if (e.target === this) closeScreenCreateModal();
  });
  $('#saveScreenBtn').addEventListener('click', saveScreen);

  // 大屏删除弹窗事件
  $('#closeScreenDeleteModalBtn').addEventListener('click', closeScreenDeleteModal);
  $('#cancelScreenDeleteBtn').addEventListener('click', closeScreenDeleteModal);
  $('#screenDeleteModal').addEventListener('click', function (e) {
    if (e.target === this) closeScreenDeleteModal();
  });
  $('#confirmScreenDeleteBtn').addEventListener('click', confirmScreenDelete);

  // 单元绑定弹窗事件
  $('#closeCellBindModalBtn').addEventListener('click', closeCellBindModal);
  $('#cancelCellBindBtn').addEventListener('click', closeCellBindModal);
  $('#cellBindModal').addEventListener('click', function (e) {
    if (e.target === this) closeCellBindModal();
  });
  $('#saveCellBindBtn').addEventListener('click', saveCellBind);

  // 大屏表格操作（事件委托）
  $('#screenTableBody').addEventListener('click', function (e) {
    var target = e.target;
    if (target.classList.contains('screen-detail-btn')) {
      openScreenDetailModal(target.getAttribute('data-id'));
    }
    if (target.classList.contains('screen-delete-btn')) {
      var screenId = target.getAttribute('data-id');
      var screenName = target.getAttribute('data-name');
      openScreenDeleteModal(screenId, screenName);
    }
  });

  // 键盘关闭弹窗
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      if ($('#userModal').style.display === 'flex') closeModal();
      if ($('#deleteModal').style.display === 'flex') closeDeleteModal();
      if ($('#deviceAddModal').style.display === 'flex') closeDeviceAddModal();
      if ($('#deviceDeleteModal').style.display === 'flex') closeDeviceDeleteModal();
      if ($('#deviceDetailModal').style.display === 'flex') closeDeviceDetailModal();
      if ($('#discoverModal').style.display === 'flex') closeDiscoverModal();
      if ($('#screenCreateModal').style.display === 'flex') closeScreenCreateModal();
      if ($('#screenDeleteModal').style.display === 'flex') closeScreenDeleteModal();
      if ($('#cellBindModal').style.display === 'flex') closeCellBindModal();
    }
  });
}

// ---------- 初始化 ----------
function init() {
  bindEvents();

  var token = Auth.getToken();
  if (token) {
    showMainView();
    loadUserList();

    // 刷新后恢复前台状态
    var savedScreenId = sessionStorage.getItem('frontend_screen_id');
    if (savedScreenId) {
      switchToFrontend();
      loadFrontendScreen(parseInt(savedScreenId, 10));
    }
  } else {
    showLoginView();
  }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', init);
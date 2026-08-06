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
    }
    capHtml += '<div class="detail-item"><span class="detail-label">窗口移动</span><span class="detail-value">' + (cap.supportMove ? '支持' : '不支持') + '</span></div>';
    capHtml += '<div class="detail-item"><span class="detail-label">窗口缩放</span><span class="detail-value">' + (cap.supportResize ? '支持' : '不支持') + '</span></div>';
    capHtml += '<div class="detail-item"><span class="detail-label">窗口叠加</span><span class="detail-value">' + (cap.supportOverlay ? '支持' : '不支持') + '</span></div>';
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

  // 键盘关闭弹窗
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      if ($('#userModal').style.display === 'flex') closeModal();
      if ($('#deleteModal').style.display === 'flex') closeDeleteModal();
      if ($('#deviceAddModal').style.display === 'flex') closeDeviceAddModal();
      if ($('#deviceDeleteModal').style.display === 'flex') closeDeviceDeleteModal();
      if ($('#deviceDetailModal').style.display === 'flex') closeDeviceDetailModal();
      if ($('#discoverModal').style.display === 'flex') closeDiscoverModal();
    }
  });
}

// ---------- 初始化 ----------
function init() {
  bindEvents();

  var token = Auth.getToken();
  if (token) {
    // 有 token，直接进入主页面（token 有效性由 api() 中的 401 处理兜底）
    showMainView();
    loadUserList();
  } else {
    showLoginView();
  }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', init);
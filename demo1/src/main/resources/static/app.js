/* =================================================================
 *  用户管理系统 — 前端逻辑
 *  适配后端 POST /user, GET /user/{id}, PUT /user,
 *            DELETE /user/{username}, POST /user/page,
 *            POST /user/login, POST /user/logout
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
    html += '<td>' + (u.id != null ? u.id : '') + '</td>';
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

  // 侧边栏导航切换
  $('#sidebarNav').addEventListener('click', function (e) {
    var navItem = e.target.closest('.nav-item');
    if (!navItem || navItem.classList.contains('disabled')) return;
    var moduleName = navItem.getAttribute('data-module');
    if (moduleName) {
      switchModule(moduleName);
    }
  });

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

  // 键盘关闭弹窗
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      if ($('#userModal').style.display === 'flex') closeModal();
      if ($('#deleteModal').style.display === 'flex') closeDeleteModal();
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
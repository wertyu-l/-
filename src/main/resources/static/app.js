const API_BASE = '';

const loginForm = document.getElementById('loginForm');
const errorMsg = document.getElementById('errorMsg');
const loginBtn = document.getElementById('loginBtn');

loginForm.addEventListener('submit', async (e) => {
  e.preventDefault();

  const username = document.getElementById('username').value.trim();
  const password = document.getElementById('password').value.trim();

  if (!username || !password) {
    errorMsg.textContent = '请输入用户名和密码';
    return;
  }

  errorMsg.textContent = '';
  errorMsg.style.color = '#e74c3c';
  loginBtn.disabled = true;
  loginBtn.textContent = '登录中...';

  try {
    const res = await fetch(`${API_BASE}/api/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    const data = await res.json();

    if (res.ok) {
      errorMsg.style.color = '#27ae60';
      errorMsg.textContent = '登录成功，正在跳转...';
      if (data.token) {
        localStorage.setItem('token', data.token);
      }
      setTimeout(() => {
        window.location.href = '/dashboard.html';
      }, 1000);
    } else {
      errorMsg.textContent = data.message || '用户名或密码错误';
    }
  } catch (err) {
    errorMsg.textContent = '无法连接服务器，请检查网络';
  } finally {
    loginBtn.disabled = false;
    loginBtn.textContent = '登 录';
  }
});
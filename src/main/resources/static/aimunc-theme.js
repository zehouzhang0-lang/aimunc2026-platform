/* ================================================================
   AIMUNC 2026 — 主题系统
   aimunc-theme.js  v1.0

   职责：
   1. 页面加载时读取 localStorage('aimunc_theme')，应用主题
   2. 注入主题切换按钮（右上角 ☀/🌙）
   3. 提供全局 window.aimuncToggleTheme() 函数
   4. 兼容 index.html 的旧 key (mun_theme) — 迁移到统一 key
   ================================================================ */

(function () {
    'use strict';

    // ── 常量 ────────────────────────────────────────────────────────
    var STORAGE_KEY = 'aimunc_theme';
    var LEGACY_KEY  = 'mun_theme';   // index.html 旧 key，迁移用
    var DEFAULT_THEME = 'dark';

    // ── 读取当前主题 ────────────────────────────────────────────────
    function getTheme() {
        var t = localStorage.getItem(STORAGE_KEY);
        if (t === 'dark' || t === 'light') return t;
        // 兼容旧 key（index.html 历史数据）
        var legacy = localStorage.getItem(LEGACY_KEY);
        if (legacy === 'light') return 'light';
        return DEFAULT_THEME;
    }

    // ── 应用主题到 <html> ────────────────────────────────────────────
    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem(STORAGE_KEY, theme);
        // 同步旧 key，保证 index.html 也能感知
        localStorage.setItem(LEGACY_KEY, theme);
        // 更新按钮图标
        var btn = document.getElementById('aimunc-theme-toggle');
        if (btn) btn.textContent = theme === 'dark' ? '🌙' : '☀️';
        // Chen-Lee 背景板联动
        if (window._aimuncBg && window._aimuncBg.setTheme) {
            window._aimuncBg.setTheme(theme);
        }
    }

    // ── 切换主题（全局暴露） ────────────────────────────────────────
    window.aimuncToggleTheme = function () {
        var current = document.documentElement.getAttribute('data-theme') || DEFAULT_THEME;
        applyTheme(current === 'dark' ? 'light' : 'dark');
    };

    // ── 注入主题切换按钮 ────────────────────────────────────────────
    function injectToggleButton() {
        // 防止重复注入
        if (document.getElementById('aimunc-theme-toggle')) return;
        var btn = document.createElement('button');
        btn.id = 'aimunc-theme-toggle';
        btn.title = '切换深色/浅色主题';
        btn.setAttribute('aria-label', '切换主题');
        btn.addEventListener('click', window.aimuncToggleTheme);
        document.body.appendChild(btn);
    }

    // ── 初始化（DOMContentLoaded 或立即执行） ─────────────────────
    function init() {
        var theme = getTheme();
        applyTheme(theme);
        injectToggleButton();
    }

    // 尽早应用主题（防止闪白/闪黑）：先在 <html> 上打属性
    document.documentElement.setAttribute('data-theme', getTheme());

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
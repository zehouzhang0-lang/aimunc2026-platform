/* ================================================================
   AIMUNC 2026 — 会话安全管理库
   aimunc-session.js  v1.0

   职责：
   1. 将身份凭证从 localStorage 迁移至 sessionStorage
      → sessionStorage 标签页隔离：每个新标签页独立会话，
        可同时以不同身份登录（代表/领队/管理员互不干扰）
      → 关闭标签页/浏览器自动清除，无需担心隔夜身份残留

   2. 提供 MunSession 工具对象，统一读写凭证 key
      → 防止散落在各页面的 sessionStorage 字符串拼写出错

   3. initIdleTimeout(minutes, redirectUrl)
      → 用户无操作超过指定分钟数后，弹窗提示并自动跳转登录页

   ⚠️ 不影响 localStorage 中的持久偏好：
      aimunc_theme / mun_theme（主题）/ mun_music（音乐状态）
      这些用户偏好应跨标签页、跨会话保持，继续存于 localStorage。
   ================================================================ */

(function () {
    'use strict';

    // ── 凭证 Key 白名单（全部存于 sessionStorage）─────────────────────
    var AUTH_KEYS = [
        'mun_jwt',              // 代表/领队 JWT token
        'mun_user_id',          // 代表/领队数据库 ID
        'mun_username',         // 代表/领队用户名（展示用）
        'mun_role',             // 代表/领队角色（DELEGATE / LEADER）
        'mun_my_delegation_id', // 领队代表团 ID 缓存
        'mun_my_invite_code',   // 领队邀请码缓存
        'mun_admin_jwt',        // 管理员 JWT token
        'mun_admin_id',         // 管理员数据库 ID
        'mun_admin_username',   // 管理员用户名（展示用）
        'mun_admin_role'        // 管理员角色标签（ADMIN）
    ];

    // ── MunSession：统一读写 sessionStorage 凭证 ──────────────────────
    window.MunSession = {
        get: function (key) {
            return sessionStorage.getItem(key);
        },
        set: function (key, value) {
            sessionStorage.setItem(key, value);
        },
        remove: function (key) {
            sessionStorage.removeItem(key);
        },
        /**
         * 清除所有身份凭证（退出登录 / 超时退出时调用）
         * 不影响 localStorage 中的主题、音乐等持久偏好
         */
        clearAuth: function () {
            AUTH_KEYS.forEach(function (k) {
                sessionStorage.removeItem(k);
            });
        }
    };

    // ── initIdleTimeout：无操作自动退出 ───────────────────────────────
    /**
     * @param {number} minutes     无操作多少分钟后触发退出（建议 60）
     * @param {string} redirectUrl 超时后跳转的登录页 URL
     *
     * 监听 click / mousemove / keydown / touchstart / scroll 事件，
     * 任一事件发生即重置计时器。
     * 超时后：① 弹 alert 告知用户 ② 清除凭证 ③ 跳转登录页
     */
    window.initIdleTimeout = function (minutes, redirectUrl) {
        var ms = minutes * 60 * 1000;
        var timer = null;

        function reset() {
            clearTimeout(timer);
            timer = setTimeout(function () {
                alert('⏰ 您已 ' + minutes + ' 分钟无操作，登录已超时，请重新登录。');
                MunSession.clearAuth();
                location.href = redirectUrl;
            }, ms);
        }

        // 用户任意交互均重置计时器
        ['click', 'mousemove', 'keydown', 'touchstart', 'scroll'].forEach(function (evt) {
            document.addEventListener(evt, reset, { passive: true });
        });

        // 页面加载即启动计时
        reset();
    };

})();
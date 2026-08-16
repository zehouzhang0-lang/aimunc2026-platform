// chen-lee-bg.js — AIMUNC 2026 背景板
// 基于 Chen-Lee 奇异吸引子 v19，架构师指令 #040 适配
// 产出：Task 1 · 独立 JS 文件
// RK4 积分器核心数学与 v19 基线逐字一致，仅改外围参数

(function () {
  'use strict';

  // ── 双主题色板（指令 §1.4.3）─────────────────────────────
  var THEMES = {
    dark: {
      clearColor: 0x08091a,
      fogColor: new THREE.Color(0x08091a),
      fogDensity: 0.003,
      trailColorDeep:   [0.02, 0.08, 0.28],
      trailColorMid:    [0.10, 0.45, 0.90],
      trailColorBright: [0.50, 0.80, 1.00],
      trailBoost:       [0.12, 0.18, 0.28],
      trailAlphaMul: 0.85,
      headHueBase: 200, headHueRange: 40,
      headSatBase: 0.6,  headSatRange: 0.25,
      headLitBase: 0.50, headLitRange: 0.25,
      dustBrightBase: 0.3, dustBrightRange: 0.25, dustOpacity: 0.15,
      overlayBg: 'radial-gradient(ellipse at 50% 50%, transparent 40%, rgba(8,9,26,0.7) 100%)'
    },
    light: {
      clearColor: 0xf4f4f6,
      fogColor: new THREE.Color(0xf4f4f6),
      fogDensity: 0.002,
      trailColorDeep:   [0.06, 0.12, 0.35],
      trailColorMid:    [0.15, 0.42, 0.82],
      trailColorBright: [0.45, 0.72, 0.96],
      trailBoost:       [0.08, 0.12, 0.18],
      trailAlphaMul: 0.72,
      headHueBase: 210, headHueRange: 30,
      headSatBase: 0.55, headSatRange: 0.25,
      headLitBase: 0.35, headLitRange: 0.25,
      dustBrightBase: 0.55, dustBrightRange: 0.25, dustOpacity: 0.2,
      overlayBg: 'radial-gradient(ellipse at 50% 50%, transparent 50%, rgba(220,222,228,0.6) 100%)'
    }
  };

  // ── 核心参数（指令 §1.5 降档值）────────────────────────────
  var NUM   = 80;      // 粒子数（v19: 120）
  var MAX_T = 300;     // 拖尾缓冲（v19: 600）
  var DT    = 0.0006;  // 积分步长（不变）
  var SC    = 0.7;     // 空间缩放（不变）
  var AL = 5, BE = -10, DE = -0.38;  // Chen-Lee 参数（不变）
  var SPD   = 3;       // 速度（固定，不可调）
  var T_LEN = 180;     // 拖尾显示长度（v19: 400）

  // ── HSL → RGB 工具函数 ─────────────────────────────────────
  function hsl2rgb(h, s, l) {
    var a = s * Math.min(l, 1 - l);
    var f = function (n) {
      var k = (n + h / 30) % 12;
      return l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1));
    };
    return [f(0), f(8), f(4)];
  }

  // ══════════════════════════════════════════════════════════════
  //  window.aimuncBgInit(containerId) — 唯一对外接口
  // ══════════════════════════════════════════════════════════════
  window.aimuncBgInit = function (containerId) {
    var container = document.getElementById(containerId);
    if (!container) return null;

    // ── Canvas + Renderer ─────────────────────────────────────
    var cv = document.createElement('canvas');
    cv.style.cssText = 'display:block;position:absolute;inset:0;width:100%;height:100%;z-index:0;pointer-events:none;';
    container.appendChild(cv);

    var R = new THREE.WebGLRenderer({
      canvas: cv,
      antialias: false,              // 指令 §1.5：背景关 AA
      powerPreference: 'high-performance'
    });
    R.setSize(container.clientWidth || innerWidth, container.clientHeight || innerHeight);
    R.setPixelRatio(Math.min(devicePixelRatio, 1.5));  // 指令 §1.5：DPR ≤ 1.5
    R.setClearColor(0x08091a);

    var Sc = new THREE.Scene();
    Sc.fog = new THREE.FogExp2(0x08091a, 0.003);
    var cam = new THREE.PerspectiveCamera(
      52,
      (container.clientWidth || innerWidth) / (container.clientHeight || innerHeight),
      0.1, 1500
    );

    // ── 遮罩层（指令 §1.4.1：只保留 .ov-v 径向渐变）─────────
    var ov = document.createElement('div');
    ov.style.cssText = 'position:absolute;inset:0;z-index:1;pointer-events:none;';
    ov.style.background = THEMES.dark.overlayBg;
    container.appendChild(ov);

    // ── 粒子状态 ──────────────────────────────────────────────
    var ps = new Float32Array(NUM * 3);
    var tb = new Float32Array(NUM * MAX_T * 3);
    var wh = new Int32Array(NUM);
    var fc = new Int32Array(NUM);
    var totalS = 0;

    function initP() {
      for (var i = 0; i < NUM; i++) {
        var o = i * 3;
        ps[o]     = (Math.random() - 0.5) * 4;
        ps[o + 1] = (Math.random() - 0.5) * 4;
        ps[o + 2] = (Math.random() - 0.5) * 4;
        wh[i] = 0; fc[i] = 0;
      }
      tb.fill(0); totalS = 0;
    }
    initP();

    // ── RK4 积分器（与 v19 基线逐字一致，禁止修改）───────────
    var _k1x, _k1y, _k1z, _k2x, _k2y, _k2z, _k3x, _k3y, _k3z, _k4x, _k4y, _k4z, _ux, _uy, _uz;
    var Dx = function (x, y, z) { return AL * x - y * z; };
    var Dy = function (x, y, z) { return BE * y + x * z; };
    var Dz = function (x, y, z) { return DE * z + x * y / 3; };

    function rk4(i, dt) {
      var o = i * 3; var x = ps[o], y = ps[o + 1], z = ps[o + 2];
      _k1x = Dx(x, y, z); _k1y = Dy(x, y, z); _k1z = Dz(x, y, z);
      _ux = x + _k1x * dt * .5; _uy = y + _k1y * dt * .5; _uz = z + _k1z * dt * .5;
      _k2x = Dx(_ux, _uy, _uz); _k2y = Dy(_ux, _uy, _uz); _k2z = Dz(_ux, _uy, _uz);
      _ux = x + _k2x * dt * .5; _uy = y + _k2y * dt * .5; _uz = z + _k2z * dt * .5;
      _k3x = Dx(_ux, _uy, _uz); _k3y = Dy(_ux, _uy, _uz); _k3z = Dz(_ux, _uy, _uz);
      _ux = x + _k3x * dt; _uy = y + _k3y * dt; _uz = z + _k3z * dt;
      _k4x = Dx(_ux, _uy, _uz); _k4y = Dy(_ux, _uy, _uz); _k4z = Dz(_ux, _uy, _uz);
      var nx = x + (_k1x + 2 * _k2x + 2 * _k3x + _k4x) * dt / 6,
          ny = y + (_k1y + 2 * _k2y + 2 * _k3y + _k4y) * dt / 6,
          nz = z + (_k1z + 2 * _k2z + 2 * _k3z + _k4z) * dt / 6;
      if (Math.abs(nx) > 500 || Math.abs(ny) > 500 || Math.abs(nz) > 500 || nx !== nx) {
        ps[o] = (Math.random() - .5) * 4; ps[o + 1] = (Math.random() - .5) * 4; ps[o + 2] = (Math.random() - .5) * 4;
        wh[i] = 0; fc[i] = 0; return;
      }
      ps[o] = nx; ps[o + 1] = ny; ps[o + 2] = nz;
      var base = (i * MAX_T + wh[i]) * 3;
      tb[base] = nx * SC; tb[base + 1] = ny * SC; tb[base + 2] = nz * SC;
      wh[i] = (wh[i] + 1) % MAX_T;
      if (fc[i] < MAX_T) fc[i]++;
    }

    // ── Trail 几何体 + Shader（指令 §1.4.4：uniforms 化）─────
    var TN = NUM * MAX_T;
    var tP  = new Float32Array(TN * 3);
    var tAl = new Float32Array(TN);
    var tCI = new Float32Array(TN);
    var tG  = new THREE.BufferGeometry();
    tG.setAttribute('position', new THREE.BufferAttribute(tP, 3));
    tG.setAttribute('alpha',    new THREE.BufferAttribute(tAl, 1));
    tG.setAttribute('colorId',  new THREE.BufferAttribute(tCI, 1));

    var tMat = new THREE.ShaderMaterial({
      vertexShader:
        'attribute float alpha; attribute float colorId;' +
        'varying float vA, vC;' +
        'void main(){' +
        '  vA=alpha; vC=colorId;' +
        '  vec4 mv=modelViewMatrix*vec4(position,1.);' +
        '  gl_Position=projectionMatrix*mv;' +
        '  vA*=smoothstep(300.,10.,length(mv.xyz));' +
        '}',
      fragmentShader:
        'varying float vA, vC;' +
        'uniform float uT;' +
        'uniform vec3 uColDeep, uColMid, uColBright, uColBoost;' +
        'uniform float uAlphaMul;' +
        'vec3 cb(float t){' +
        '  float s=sin(t*3.14159)*.5+.5;' +
        '  vec3 c=mix(uColDeep,uColMid,s);' +
        '  c=mix(c,uColBright,sin(t*6.28+.8)*.5+.5);' +
        '  return mix(c,uColBright,smoothstep(.7,1.,t)*.3);' +
        '}' +
        'void main(){' +
        '  if(vA<.003)discard;' +
        '  gl_FragColor=vec4(cb(fract(vC+uT*.012))+uColBoost*vA, vA*uAlphaMul);' +
        '}',
      uniforms: {
        uT:         { value: 0 },
        uColDeep:   { value: new THREE.Vector3() },
        uColMid:    { value: new THREE.Vector3() },
        uColBright: { value: new THREE.Vector3() },
        uColBoost:  { value: new THREE.Vector3() },
        uAlphaMul:  { value: 0.85 }
      },
      transparent: true,
      blending: THREE.NormalBlending,
      depthWrite: false
    });

    var tMesh = new THREE.LineSegments(tG, tMat);
    Sc.add(tMesh);

    var tIdx = new Uint32Array(NUM * (MAX_T - 1) * 2);
    var ip = 0;
    for (var i = 0; i < NUM; i++) {
      var b = i * MAX_T;
      for (var j = 0; j < MAX_T - 1; j++) {
        tIdx[ip++] = b + j;
        tIdx[ip++] = b + j + 1;
      }
    }
    tG.setIndex(new THREE.BufferAttribute(tIdx, 1));

    // ── Head 粒子（指令 §1.4.5 + §1.5 head gl_PointSize）────
    var hP = new Float32Array(NUM * 3);
    var hC = new Float32Array(NUM * 3);
    var hG = new THREE.BufferGeometry();
    hG.setAttribute('position', new THREE.BufferAttribute(hP, 3));
    hG.setAttribute('color',    new THREE.BufferAttribute(hC, 3));

    var hMat = new THREE.ShaderMaterial({
      vertexShader:
        'attribute vec3 color; varying vec3 vCol; uniform float uT;' +
        'void main(){' +
        '  vCol=color;' +
        '  vec4 mv=modelViewMatrix*vec4(position,1.);' +
        '  gl_Position=projectionMatrix*mv;' +
        '  gl_PointSize=max(1.5, 80./length(mv.xyz));' +  // 指令 §1.5：缩小，删除呼吸脉冲
        '}',
      fragmentShader:
        'varying vec3 vCol;' +
        'void main(){' +
        '  vec2 uv=gl_PointCoord*2.-1.; float d=length(uv);' +
        '  if(d>1.)discard;' +
        '  gl_FragColor=vec4(vCol, exp(-d*d*2.)*.9);' +
        '}',
      uniforms: { uT: { value: 0 } },
      transparent: true,
      blending: THREE.NormalBlending,
      depthWrite: false
    });
    Sc.add(new THREE.Points(hG, hMat));

    // Head 颜色应用函数（指令 §1.4.5：setTheme 时重算）
    function applyHeadColors(t) {
      for (var i = 0; i < NUM; i++) {
        var ratio = i / NUM;
        var h = t.headHueBase + ratio * t.headHueRange;
        var s = t.headSatBase + ratio * t.headSatRange;
        var l = t.headLitBase + (1 - ratio) * t.headLitRange;
        var rgb = hsl2rgb(h, s, l);
        hC[i * 3]     = rgb[0];
        hC[i * 3 + 1] = rgb[1];
        hC[i * 3 + 2] = rgb[2];
        // colorId for trail（与 v19 同逻辑）
        var cb2 = i * MAX_T;
        for (var j2 = 0; j2 < MAX_T; j2++) tCI[cb2 + j2] = ratio;
      }
      hG.attributes.color.needsUpdate = true;
      tG.attributes.colorId.needsUpdate = true;
    }

    // ── Dust 尘埃（指令 §1.5：600 颗，半径 60+300）───────────
    var SN  = 600;
    var sp2 = new Float32Array(SN * 3);
    var sc2 = new Float32Array(SN * 3);
    for (var i = 0; i < SN; i++) {
      var r  = 60 + Math.random() * 300;   // 指令 §1.5：60+random*300
      var th = Math.random() * 6.283;
      var ph = Math.acos(2 * Math.random() - 1);
      sp2[i * 3]     = r * Math.sin(ph) * Math.cos(th);
      sp2[i * 3 + 1] = r * Math.sin(ph) * Math.sin(th);
      sp2[i * 3 + 2] = r * Math.cos(ph);
    }
    var sG = new THREE.BufferGeometry();
    sG.setAttribute('position', new THREE.BufferAttribute(sp2, 3));
    sG.setAttribute('color',    new THREE.BufferAttribute(sc2, 3));
    var dustMat = new THREE.PointsMaterial({
      size: 0.4, vertexColors: true, transparent: true,
      opacity: 0.15, depthWrite: false
    });
    Sc.add(new THREE.Points(sG, dustMat));

    // Dust 颜色应用函数（指令 §1.4.6：setTheme 时重算）
    function applyDustColors(t) {
      for (var i = 0; i < SN; i++) {
        var v = t.dustBrightBase + Math.random() * t.dustBrightRange;
        sc2[i * 3]     = v * 0.75;
        sc2[i * 3 + 1] = v * 0.8;
        sc2[i * 3 + 2] = v * 0.92;
      }
      sG.attributes.color.needsUpdate = true;
      dustMat.opacity = t.dustOpacity;
    }

    // ── Trail / Head 更新函数（与 v19 逻辑一致）──────────────
    function updT() {
      var pa = tP, aa = tAl;
      for (var i = 0; i < NUM; i++) {
        var pb = i * MAX_T, fill = fc[i], head = wh[i], el = Math.min(fill, T_LEN);
        if (el < 2) { for (var j = 0; j < MAX_T; j++) aa[pb + j] = 0; continue; }
        var sr = (head - el + MAX_T) % MAX_T;
        for (var j = 0; j < el; j++) {
          var ri = (sr + j) % MAX_T, so = (i * MAX_T + ri) * 3, d = (pb + j) * 3;
          pa[d] = tb[so]; pa[d + 1] = tb[so + 1]; pa[d + 2] = tb[so + 2];
          aa[pb + j] = (j / (el - 1)) * (j / (el - 1));  // **2
        }
        for (var j = el; j < MAX_T; j++) aa[pb + j] = 0;
      }
      tG.attributes.position.needsUpdate = true;
      tG.attributes.alpha.needsUpdate = true;
    }

    function updH() {
      for (var i = 0; i < NUM; i++) {
        var o = i * 3;
        hP[o] = ps[o] * SC; hP[o + 1] = ps[o + 1] * SC; hP[o + 2] = ps[o + 2] * SC;
      }
      hG.attributes.position.needsUpdate = true;
    }

    // ══════════════════════════════════════════════════════════
    //  摄像机（指令 §1.6）
    // ══════════════════════════════════════════════════════════

    // §1.6.1 状态变量
    var camA   = Math.random() * Math.PI * 2;          // 随机方位角
    var camE   = 0.15 + Math.random() * 0.20;          // 随机仰角 0.15~0.35
    var camD   = 35;                                    // 距离（OPENING 起始）
    var camFov = 52;                                    // 视场角
    var camMode   = 0;                                  // 0=OPENING, 1=ORBIT
    var openingT  = 0;
    var OPENING_DUR = 8;                                // 8秒（v19: 12秒）
    var camEStart = camE;                               // 记录初始仰角

    // §1.6.2 + §1.6.3 + §1.6.4 摄像机更新
    function dirUpd(dt, now) {
      if (camMode === 0) {
        // MODE 0: OPENING — 35 → 5，8 秒
        openingT += dt;
        var t = Math.min(openingT / OPENING_DUR, 1);
        var e = 1 - Math.pow(1 - t, 3);  // ease-out cubic

        camD = 35 - e * 30;                             // 35 → 5
        camA += dt * 0.03 * (1 + e * 0.5);             // 慢旋
        camE = camEStart - (camEStart - 0.15) * e;      // 仰角收敛到 ~0.15
        camFov = 52 + e * 3;                            // FOV 52 → 55

        if (t >= 1) camMode = 1;
      } else {
        // MODE 1: ORBIT — 安静慢转
        var angSpeed = 0.025 + Math.sin(now * 0.013) * 0.008;
        camA += angSpeed * dt;

        var tgtE = Math.sin(now * 0.017) * 0.15 + Math.sin(now * 0.041 + 2) * 0.06;
        camE += (tgtE - camE) * dt * 0.4;

        var breathRaw = Math.sin(now * 0.025) * 0.5 + Math.sin(now * 0.011 + 1.5) * 0.3;
        var n01 = (breathRaw + 1) * 0.5;
        var tgtD = 8 + n01 * 17;                        // 8 → 25
        camD += (tgtD - camD) * dt * 0.8;               // 阻尼 0.8

        camFov = 52;                                    // 固定
      }

      // APPLY — 绕原点 (0,0,0) 旋转
      var cy = camD * Math.sin(camE);
      var cr = camD * Math.cos(camE);
      cam.position.set(Math.sin(camA) * cr, cy, Math.cos(camA) * cr);
      cam.lookAt(0, 0, 0);

      // FOV 更新
      var nf = Math.round(camFov * 10) / 10;
      if (Math.abs(cam.fov - nf) > 0.05) {
        cam.fov = nf;
        cam.updateProjectionMatrix();
      }
    }

    // ── setTheme（指令 §1.8）──────────────────────────────────
    function setTheme(name) {
      var t = THEMES[name] || THEMES.dark;
      // 1. WebGL clearColor + fog
      R.setClearColor(t.clearColor);
      Sc.fog.color.copy(t.fogColor);
      Sc.fog.density = t.fogDensity;
      // 2. Trail shader uniforms
      tMat.uniforms.uColDeep.value.fromArray(t.trailColorDeep);
      tMat.uniforms.uColMid.value.fromArray(t.trailColorMid);
      tMat.uniforms.uColBright.value.fromArray(t.trailColorBright);
      tMat.uniforms.uColBoost.value.fromArray(t.trailBoost);
      tMat.uniforms.uAlphaMul.value = t.trailAlphaMul;
      // 3. Head 粒子颜色重算
      applyHeadColors(t);
      // 4. Dust 颜色+透明度重算
      applyDustColors(t);
      // 5. 遮罩 div
      ov.style.background = t.overlayBg;
    }

    // ── destroy（指令 §1.9）───────────────────────────────────
    var rafId;

    function destroy() {
      cancelAnimationFrame(rafId);
      R.dispose();
      tG.dispose(); hG.dispose(); sG.dispose();
      tMat.dispose(); hMat.dispose();
      cv.remove();
      ov.remove();
      window.removeEventListener('resize', onResize);
    }

    // ── resize（指令 §1.10）───────────────────────────────────
    function onResize() {
      var w = container.clientWidth  || innerWidth;
      var h = container.clientHeight || innerHeight;
      cam.aspect = w / h;
      cam.updateProjectionMatrix();
      R.setSize(w, h);
    }
    window.addEventListener('resize', onResize);

    // ── 主循环（指令 §1.7：简化版，无 pause/manual/FPS）──────
    var _lt = performance.now() * 0.001;

    function loop(now) {
      rafId = requestAnimationFrame(loop);
      var ns = now * 0.001;
      var dt = Math.min(ns - _lt, 0.06);
      _lt = ns;

      // 始终 cinema 模式
      dirUpd(dt, ns);

      // 始终运行，无 pause 判断
      for (var s = 0; s < SPD; s++) {
        for (var i = 0; i < NUM; i++) rk4(i, DT);
        totalS++;
      }
      updH();
      updT();

      tMat.uniforms.uT.value = ns;
      hMat.uniforms.uT.value = ns;
      R.render(Sc, cam);
    }

    // ── 启动 ──────────────────────────────────────────────────
    var initTheme = document.documentElement.getAttribute('data-theme') || 'dark';
    setTheme(initTheme);
    rafId = requestAnimationFrame(loop);

    return { setTheme: setTheme, destroy: destroy };
  };

})();

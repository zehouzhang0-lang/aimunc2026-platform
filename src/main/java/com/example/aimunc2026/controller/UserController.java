package com.example.aimunc2026.controller;

import com.example.aimunc2026.Entity.Delegation;
import com.example.aimunc2026.Entity.User;
import com.example.aimunc2026.dto.LoginResponse;
import com.example.aimunc2026.repository.UserRepository;
import com.example.aimunc2026.service.DelegationService;
import com.example.aimunc2026.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private DelegationService delegationService;

    // 【#050 新增】注入JWT工具类，用于登录成功后颁发token
    @Autowired
    private JwtUtil jwtUtil;

    // ── GET /api/users/exists ── 【#024 新增】账号存在性探测（安全加固版）
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkUserExists(
            @RequestParam String username,
            @RequestParam String role) {

        if (!"DELEGATE".equals(role) && !"LEADER".equals(role)) {
            return ResponseEntity.badRequest().build();
        }

        // 随机延迟 20-50ms，模糊响应时间，干扰时序枚举攻击
        try { Thread.sleep(new Random().nextInt(30) + 20); } catch (InterruptedException ignored) {}

        // 格式防护：防止绕过前端直接暴力枚举
        if (username == null || !username.matches("^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]{6,13}$")) {
            return ResponseEntity.badRequest().build();
        }

        boolean exists = userRepository.existsByUsernameAndRole(username, role);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    // ── POST /api/users/login ── 【#CRIT-01 修复】纯登录接口，只查不建
    // ────────────────────────────────────────────────────────────────────
    // 【为什么要新建这个接口？】
    //   原来 admin-login.html 错误地调用了 /register（登录+注册一体化接口）。
    //   /register 的设计是：账号不存在就自动创建。管理员账号本不该被自动创建，
    //   前端虽然在发现 isNewUser=true 后立刻 DELETE 销毁，但存在两个漏洞：
    //     ① 竞态窗口：token 在 DELETE 之前已经颁发，攻击者可抢先使用
    //     ② DELETE 失败：若网络抖动，幽灵 ADMIN 账号会永久残留在数据库
    //
    // 【本接口的设计原则】
    //   ✅ 只做"查找 + 密码验证"，绝对不创建任何账号
    //   ✅ 账号不存在 → 401，刻意不告知"是账号不存在还是密码错误"（防枚举）
    //   ✅ 密码错误   → 同样 401
    //   ✅ 全程复用 buildLoginResponse()，响应格式与 /register 完全一致
    //
    // 【现有 /register 接口是否受影响？】
    //   完全不受影响。代表和领队仍然使用 /register（注册/登录一体化），
    //   本接口只是专门为"纯登录场景"（管理员）提供的安全替代。
    // ────────────────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> loginOnly(@RequestBody User incomingUser) {

        // 防御性校验：role 不能为空，否则 (username, null) 查询结果不可预期
        if (incomingUser.getRole() == null || incomingUser.getRole().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 按 (username, role) 联合查找。
        // 查不到 → 直接 401，此处永远不会走到创建账号的逻辑
        Optional<User> existing = userRepository.findByUsernameAndRole(
                incomingUser.getUsername(), incomingUser.getRole());

        if (existing.isEmpty()) {
            // 故意不区分"账号不存在"和"密码错误"，
            // 统一返回 401，防止攻击者通过错误信息枚举有效账号
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User dbUser = existing.get();

        // 密码比对：BCrypt 安全哈希，错误同样返回 401
        if (!passwordEncoder.matches(incomingUser.getPassword_hash(), dbUser.getPassword_hash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 验证通过，复用现有辅助方法构建响应。
        // isNewUser 固定传 false：此接口永远是登录，绝不是注册。
        return ResponseEntity.ok(buildLoginResponse(dbUser, false));
    }

    // ── POST /api/users/register ── 登录/注册一体化（原有接口，未改动）
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> loginOrRegister(@RequestBody User incomingUser) {

        // 发布加固：公开注册只允许普通业务角色，管理员只能通过纯登录接口认证。
        String role = incomingUser.getRole();
        if (!"DELEGATE".equals(role) && !"LEADER".equals(role)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 【#022】后端用户名格式守门
        String uname = incomingUser.getUsername();
        if (uname == null || !uname.matches("^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]{6,13}$")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 【#016】按 (username, role) 联合查询，双身份隔离
        Optional<User> existingUser = userRepository.findByUsernameAndRole(
                incomingUser.getUsername(), incomingUser.getRole());

        if (existingUser.isPresent()) {
            User dbUser = existingUser.get();
            if (!passwordEncoder.matches(incomingUser.getPassword_hash(), dbUser.getPassword_hash())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            return ResponseEntity.ok(buildLoginResponse(dbUser, false));
        } else {
            incomingUser.setCreated_at(LocalDateTime.now());
            incomingUser.setUpdated_at(LocalDateTime.now());
            incomingUser.setPassword_hash(passwordEncoder.encode(incomingUser.getPassword_hash()));
            User savedUser = userRepository.save(incomingUser);
            return ResponseEntity.ok(buildLoginResponse(savedUser, true));
        }
    }

    // ── DELETE /api/users/{id} ── 撤销自动创建的空账号（原有接口，未改动）
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteNewUser(@PathVariable Long id,
                                                HttpServletRequest request) {
        Object jwtUserId = request.getAttribute("jwtUserId");
        Object jwtRole = request.getAttribute("jwtRole");
        if (jwtUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }
        if (!"ADMIN".equals(jwtRole) && !id.equals(jwtUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("只能撤销自己的空账号");
        }

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("账号不存在");

        User user = userOpt.get();
        if ("LEADER".equals(user.getRole())) {
            Delegation delegation = delegationService.getDelegationByLeaderId(id);
            if (delegation != null)
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("该账号已有代表团数据，禁止删除");
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok("已撤销账号创建");
    }

    // ── 私有辅助：构建 LoginResponse ──
    // 【#050 改动】生成 JWT token 并传入新构造函数
    private LoginResponse buildLoginResponse(User user, boolean isNewUser) {
        Long delegationId = null;
        String inviteCode = null;
        if ("LEADER".equals(user.getRole())) {
            Delegation delegation = delegationService.getDelegationByLeaderId(user.getId());
            if (delegation != null) { delegationId = delegation.getId(); inviteCode = delegation.getInviteCode(); }
        }
        // 【#050】生成 JWT token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(user.getId(), user.getUsername(), user.getRole(),
                delegationId, inviteCode, isNewUser, token);
    }
}

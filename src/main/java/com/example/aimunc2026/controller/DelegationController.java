package com.example.aimunc2026.controller;

import com.example.aimunc2026.Entity.Delegation;
import com.example.aimunc2026.service.DelegationService;
import com.example.aimunc2026.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.example.aimunc2026.repository.DelegateRepository;
import com.example.aimunc2026.repository.UserRepository;
import com.example.aimunc2026.Entity.Delegate;
import com.example.aimunc2026.Entity.User;

/**
 * ════════════════════════════════════════════════════════════
 *  【控制器】代表团相关接口 (DelegationController)
 *  处理路径前缀：/api/delegation
 * ════════════════════════════════════════════════════════════
 *
 *  【指令 #020 新增】
 *
 *  GET /api/delegation/check-name?name=...
 *    前端实时探测接口：输入协会名后 onblur 时调用，
 *    返回 {"exists": true/false}，前端据此显示黄色软提醒。
 *    该接口不影响提交流程，仅用于用户体验提示。
 *
 *  【#050 改动】adminList 接口鉴权从 X-Admin-Id 裸ID 改为 JWT
 *
 *  ★ 原有三个接口（/verify/{code}、/create、/{id}）完全保留，未改动任何一行。
 */
@RestController
@RequestMapping("/api/delegation")
public class DelegationController {

    @Autowired
    private DelegationService delegationService;

    @Autowired
    private DelegateRepository delegateRepository;

    @Autowired
    private UserRepository userRepository;

    // 【#050 新增】注入 JWT 工具类
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 实时校验邀请码（原有接口，未改动）
     * 路径：GET /api/delegation/verify/{code}
     */
    @GetMapping("/verify/{code}")
    public ResponseEntity<Delegation> verifyInviteCode(@PathVariable String code) {
        Delegation delegation = delegationService.getDelegationByInviteCode(code);
        if (delegation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(delegation);
    }

    /**
     * 创建代表团
     * 路径：POST /api/delegation/create
     *
     * 【#D-05 修复】补充 JWT 鉴权，并用 JWT 覆盖 leaderUserId，关闭 IDOR 漏洞。
     *
     * 原来的问题：
     *   请求体中的 leaderUserId 完全由前端自填，后端直接信任并存入数据库。
     *   攻击者可以登录自己的账号（ID=42），但在请求体中填写别人的 ID（如 ID=8），
     *   系统就会以"ID=8 的用户"名义建立代表团。
     *
     * 修复方案：
     *   ① 必须携带有效 JWT，未登录 → 401。
     *   ② 从 JWT 提取 userId，强制覆盖请求体里的 leaderUserId，
     *      前端传什么都无所谓，后端只认 token 里的身份。
     *   ③ role 校验（可选加固）：只有 LEADER 角色可以建团，
     *      DELEGATE 账号误操作时返回 403，给出明确提示。
     *
     * 对前端的影响：
     *   create_delegation.html 的请求体中不再需要传 leaderUserId，
     *   但传了也无害（会被覆盖）。已在前端同步补上 Authorization 头。
     *
     * 错误场景（均返回 HTTP 400）：
     *   - 「该学校已建好代表团，不能重复建团」← #020 校区冲突
     *   - 「该领队账号已创建过代表团...」← 原有领队防重
     */
    @PostMapping("/create")
    public ResponseEntity<?> createDelegation(@RequestBody Delegation newDelegation,
                                              HttpServletRequest request) {

        // ── 鉴权：必须携带有效 JWT ──────────────────────────────────────
        Object jwtUserId = request.getAttribute("jwtUserId");
        Object jwtRole   = request.getAttribute("jwtRole");
        if (jwtUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录领队账号");
        }

        // ── 角色校验：只有 LEADER 可以建团 ─────────────────────────────
        // ADMIN 日常不需要建团；DELEGATE 账号误入此页时给出友好提示。
        if (!"LEADER".equals(jwtRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("权限不足：只有领队账号可以创建代表团（当前角色：" + jwtRole + "）");
        }

        // ── IDOR 修复：强制用 JWT 里的身份覆盖前端传来的 leaderUserId ──
        // 无论前端传了什么，以 token 里的真实身份为准，杜绝伪造。
        newDelegation.setLeaderUserId((Long) jwtUserId);

        try {
            Delegation created = delegationService.createDelegation(newDelegation);
            return ResponseEntity.ok(created);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 根据 ID 获取代表团详情（原有接口，未改动）
     * 路径：GET /api/delegation/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Delegation> getDelegationById(@PathVariable Long id) {
        Delegation delegation = delegationService.getDelegationById(id);
        if (delegation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(delegation);
    }

    /**
     * 【#020 新增】协会名重名软探测接口
     * 路径：GET /api/delegation/check-name?name=...
     *
     * 返回：{"exists": true}  → 前端显示黄色提醒「该协会已有已建好的代表团，请注意审查」
     *       {"exists": false} → 名称干净，无需提示
     *
     * 注意：此接口返回 200 无论 exists 是 true 还是 false，
     * HTTP 错误码仅用于网络故障或服务器异常。
     * 前端通过 JSON 字段 exists 判断，不依赖 HTTP 状态码。
     */
    @GetMapping("/check-name")
    public ResponseEntity<Map<String, Boolean>> checkName(@RequestParam String name) {
        boolean exists = delegationService.isNameExists(name);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * 【新增】代表团全量列表（ADMIN 专用）
     * 路径：GET /api/delegation/admin/list
     *
     * 【#050 改动】鉴权从 X-Admin-Id 裸ID查库 改为 JWT role 校验
     *
     * 每条记录包含：
     *   - 代表团基本信息（id, name, schoolName, inviteCode, leaderName, leaderPhone）
     *   - 领队用户名（通过 leaderUserId 查 User 表）
     *   - 成员列表（id, realName, status, paymentStatus）
     *   - 完成状态标志：allApplied / allPaid / allApproved
     */
    @GetMapping("/admin/list")
    public ResponseEntity<?> adminList(HttpServletRequest request) {

        // 【#050】JWT鉴权：从 JwtFilter 注入的 request attribute 读取 role
        Object role = request.getAttribute("jwtRole");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("权限不足");
        }

        List<Delegation> all = delegationService.getAllDelegations();

        List<Map<String, Object>> result = all.stream().map(delegation -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           delegation.getId());
            m.put("name",         delegation.getName());
            m.put("schoolName",   delegation.getSchoolName());
            m.put("inviteCode",   delegation.getInviteCode());
            m.put("leaderName",   delegation.getLeaderName());
            m.put("leaderPhone",  delegation.getLeaderPhone());
            m.put("leaderUserId", delegation.getLeaderUserId());

            // 领队用户名
            String leaderUsername = "—";
            if (delegation.getLeaderUserId() != null) {
                var userOpt = userRepository.findById(delegation.getLeaderUserId());
                if (userOpt.isPresent()) leaderUsername = userOpt.get().getUsername();
            }
            m.put("leaderUsername", leaderUsername);

            // 成员列表
            List<Delegate> members = delegateRepository.findByDelegationId(delegation.getId());
            List<Map<String, Object>> memberList = members.stream().map(d -> {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("id",            d.getId());
                dm.put("realName",      d.getRealName());
                dm.put("grade",         d.getGrade());
                dm.put("school",        d.getSchool());
                dm.put("status",        d.getStatus());
                dm.put("paymentStatus", d.getPaymentStatus() != null ? d.getPaymentStatus() : "NOT_SUBMITTED");
                dm.put("committeePref1", d.getCommitteePref1());
                dm.put("committeePref2", d.getCommitteePref2());
                dm.put("committeePref3", d.getCommitteePref3());
                dm.put("assignedCommittee", d.getAssignedCommittee());
                return dm;
            }).collect(Collectors.toList());
            m.put("members", memberList);
            m.put("memberCount", memberList.size());

            // 三项完成状态
            boolean allApplied  = !memberList.isEmpty();
            boolean allPaid     = !memberList.isEmpty() && memberList.stream()
                    .allMatch(d -> !"NOT_SUBMITTED".equals(d.get("paymentStatus")));
            boolean allApproved = !memberList.isEmpty() && memberList.stream()
                    .allMatch(d -> "ACADEMIC_BOARD_APPROVED".equals(d.get("status")));
            m.put("allApplied",  allApplied);
            m.put("allPaid",     allPaid);
            m.put("allApproved", allApproved);

            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

}
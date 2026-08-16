package com.example.aimunc2026.controller;

import com.example.aimunc2026.Entity.Delegate;
import com.example.aimunc2026.Entity.Delegation;
import com.example.aimunc2026.Entity.User;
import com.example.aimunc2026.repository.DelegateRepository;
import com.example.aimunc2026.repository.UserRepository;
import com.example.aimunc2026.service.DelegationService;
import com.example.aimunc2026.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.example.aimunc2026.repository.DelegationRepository;

/**
 * ════════════════════════════════════════════════════════════
 *  【控制器】代表报名相关接口 (DelegateController)
 *  处理路径前缀：/api/delegate
 * ════════════════════════════════════════════════════════════
 *
 *  【指令 #017-C 新增接口】
 *
 *  GET  /api/delegate/admin/list     → 全量代表列表（ADMIN 专用）
 *  GET  /api/delegate/admin/stats    → 统计看板数据（ADMIN 专用）
 *  PATCH /api/delegate/{id}/status   → 学委终审操作（ADMIN 专用）
 *
 *  【#050 改动说明】
 *
 *  1. verifyAdmin() 从查库校验 X-Admin-Id 改为从 JWT request attribute 读取 role
 *  2. 所有 Admin 接口签名从 @RequestHeader("X-Admin-Id") 改为 HttpServletRequest
 *  3. /apply 接口从 JWT 覆盖 userId，关闭 IDOR 核心漏洞
 *  4. /update-status 接口从 JWT 读取 operatorUserId，不再从请求体读
 *  5. /payment-proof 上传接口移除 @RequestParam operatorId，从 JWT 读取
 *  6. /payment-proof 获取接口从 JWT 读取身份，不再从 X-Admin-Id / X-Operator-Id 读
 *
 *  【原有业务逻辑完全保留，未做任何修改】
 */
@RestController
@RequestMapping("/api/delegate")
public class DelegateController {

    // ════════════════════════════════════════════════════════════════
    // 【付款凭证 Step 2】上传目录配置
    // application.properties 中配置：aimunc.upload.path=/opt/aimunc/uploads/payments/
    // 本地开发时在 application-dev.properties 中覆盖为本地路径
    // ════════════════════════════════════════════════════════════════
    @Value("${aimunc.upload.path}")
    private String uploadPathStr;

    private Path uploadDir;

    /** 应用启动时确保上传目录存在，不存在则自动创建 */
    @PostConstruct
    public void initUploadDir() throws IOException {
        this.uploadDir = Paths.get(uploadPathStr).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    @Autowired
    private DelegateRepository delegateRepository;

    @Autowired
    private DelegationService delegationService;

    // ════════════════════════════════════════════════════════════════
    // 【指令 #017-C 新增】注入 UserRepository，用于 Admin 身份校验
    // ════════════════════════════════════════════════════════════════
    @Autowired
    private UserRepository userRepository;

    // 【B1】注入 DelegationRepository，用于 admin/list 联查代表团信息
    @Autowired
    private DelegationRepository delegationRepository;

    // 【#050 新增】注入 JWT 工具类
    @Autowired
    private JwtUtil jwtUtil;

    // ════════════════════════════════════════════════════════════════
    // 原有接口 ①：代表报名/更新接口（幂等）
    // 路径：POST /api/delegate/apply
    // 【#050 改动】从JWT覆盖userId，关闭IDOR核心漏洞
    // ════════════════════════════════════════════════════════════════

    /**
     * 代表报名/更新接口（幂等）
     * 路径：POST /api/delegate/apply
     *
     * 代表被驳回（REJECTED）后重新提交时，前端把 status 重置为 PENDING 再发送，
     * 本接口直接保存，无需特殊处理，状态机流转由前端控制。
     *
     * 【#050 改动】从JWT提取userId覆盖前端传来的值，防止伪造
     */
    @PostMapping("/apply")
    public ResponseEntity<String> applyOrUpdate(@RequestBody Delegate incomingData,
                                                HttpServletRequest request) {

        // 【#050 IDOR修复】从JWT提取userId，覆盖前端传来的值，防止伪造
        Object jwtUserId = request.getAttribute("jwtUserId");
        if (jwtUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }
        incomingData.setUserId((Long) jwtUserId);

        if (incomingData.getInviteCode() != null && !incomingData.getInviteCode().trim().isEmpty()) {
            Delegation del = delegationService.getDelegationByInviteCode(incomingData.getInviteCode().trim());
            if (del != null) incomingData.setDelegationId(del.getId());
        } else {
            incomingData.setDelegationId(null);
        }

        Optional<Delegate> existing = delegateRepository.findAll().stream()
                .filter(d -> d.getUserId().equals(incomingData.getUserId()))
                .findFirst();
        if (existing.isPresent()) {
            Delegate existingDelegate = existing.get();
            incomingData.setId(existingDelegate.getId());
            // 【付款凭证 Step 2 - 防覆盖保护】
            // 代表重新提交报名表时，前端 FormData 不含付款字段，
            // 直接 save(incomingData) 会以 null 覆盖已有付款记录。
            // 将旧记录的付款字段移植到新对象，使付款状态不受报名表更新影响。
            incomingData.setPaymentProofFilename(existingDelegate.getPaymentProofFilename());
            incomingData.setPaymentStatus(existingDelegate.getPaymentStatus());
        }

        delegateRepository.save(incomingData);
        return ResponseEntity.ok("数据同步成功！状态：" + incomingData.getStatus());
    }

    // ════════════════════════════════════════════════════════════════
    // 原有接口 ②：领队审核接口
    // 路径：POST /api/delegate/update-status
    // 【#050 改动】从JWT读取operatorUserId，不再从请求体读
    // ════════════════════════════════════════════════════════════════

    /**
     * 【指令 #013 Task1】领队审核接口
     * 路径：POST /api/delegate/update-status
     *
     * 入参（JSON）：
     * {
     *   "targetUserId":   123,                ← 被审核代表的 userId
     *   "newStatus":      "LEADER_APPROVED",  ← 目标状态
     *   "rejectReason":   "学术经历过于简略"    ← 驳回理由（通过时可不传）
     * }
     *
     * 【#050 改动】operatorUserId 不再从请求体读取，改为从JWT提取
     *
     * 驳回理由存储方案（零数据库字段改动）：
     *   将理由编码进 status 字段："REJECTED:学术经历过于简略"
     *   前端用 split(':') 读取，简单高效，不改 schema。
     *   未来如需独立字段，只需迁移这一列数据。
     *
     * 安全校验（双重验证，防止代表伪造领队操作）：
     *   1. 找到代表记录，取 delegationId
     *   2. 查代表团，取 leaderUserId
     *   3. 校验 leaderUserId == operatorUserId（从JWT提取），不符则 403
     *
     * 状态流转：
     *   PENDING → LEADER_APPROVED  （领队通过）
     *   PENDING → REJECTED:理由     （领队驳回）
     *   LEADER_APPROVED → REJECTED:理由 （领队撤回通过，重新打回）
     *   REJECTED → PENDING           （代表修改后重新提交，走 /apply 接口）
     */
    @PostMapping("/update-status")
    public ResponseEntity<String> updateStatus(@RequestBody Map<String, Object> body,
                                               HttpServletRequest request) {

        // 【#050】从JWT提取操作者userId，不信任请求体中的值
        Object jwtUserId = request.getAttribute("jwtUserId");
        if (jwtUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }
        Long operatorUserId = (Long) jwtUserId;

        Long targetUserId   = Long.valueOf(body.get("targetUserId").toString());
        String newStatus    = body.get("newStatus").toString();
        String rejectReason = (body.containsKey("rejectReason") && body.get("rejectReason") != null)
                ? body.get("rejectReason").toString().trim() : "";

        // 查找被审核代表
        Optional<Delegate> delegateOpt = delegateRepository.findAll().stream()
                .filter(d -> d.getUserId().equals(targetUserId))
                .findFirst();

        if (delegateOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("未找到该代表的报名记录");
        }

        Delegate delegate = delegateOpt.get();

        // 个人代表（无 delegationId）不受领队管辖
        if (delegate.getDelegationId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("该代表为个人报名，不属于任何代表团，领队无权审核");
        }

        // 安全校验：操作者必须是该团领队
        Delegation delegation = delegationService.getDelegationById(delegate.getDelegationId());
        if (delegation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("代表团信息异常");
        }
        if (!delegation.getLeaderUserId().equals(operatorUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("权限不足：您不是该代表团的领队，无法审核此代表");
        }

        // 更新状态（驳回时将理由编码进状态字段）
        if ("REJECTED".equals(newStatus) && !rejectReason.isEmpty()) {
            delegate.setStatus("REJECTED:" + rejectReason);
        } else {
            delegate.setStatus(newStatus);
        }

        delegateRepository.save(delegate);
        return ResponseEntity.ok("操作成功，状态已更新为：" + delegate.getStatus());
    }

    // ════════════════════════════════════════════════════════════════
    // 原有接口 ③：根据代表团ID获取成员名单
    // 路径：GET /api/delegate/by-delegation/{delId}
    // 【发布加固】仅该代表团领队或管理员可访问
    // ════════════════════════════════════════════════════════════════

    /**
     * 根据代表团ID获取成员名单
     * 路径：GET /api/delegate/by-delegation/{delId}
     */
    @GetMapping("/by-delegation/{delId}")
    public ResponseEntity<?> getByDelegation(@PathVariable Long delId,
                                             HttpServletRequest request) {
        Object jwtUserId = request.getAttribute("jwtUserId");
        Object jwtRole = request.getAttribute("jwtRole");
        if (jwtUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        if (!"ADMIN".equals(jwtRole)) {
            if (!"LEADER".equals(jwtRole)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("权限不足");
            }
            Delegation ownDelegation = delegationService.getDelegationByLeaderId((Long) jwtUserId);
            if (ownDelegation == null || !delId.equals(ownDelegation.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("只能查看自己代表团的成员");
            }
        }

        return ResponseEntity.ok(delegateRepository.findByDelegationId(delId));
    }

    // ════════════════════════════════════════════════════════════════
    // 原有接口 ④：根据用户ID获取代表报名信息
    // 路径：GET /api/delegate/user/{userId}
    // 【#CRIT-02 修复】补充 JWT 鉴权，关闭任意用户可查他人信息的漏洞
    // ════════════════════════════════════════════════════════════════

    /**
     * 根据用户ID获取代表报名信息（用于页面回填）
     * 路径：GET /api/delegate/user/{userId}
     *
     * 【#CRIT-02 修复说明】
     *
     * 原来的问题：
     *   此接口无任何身份验证。任何人只要知道别人的用户 ID（一个从 1 开始递增的数字），
     *   就能直接访问他人的姓名、手机号、紧急联系人等全部个人信息。
     *
     * 修复方案 — 两级访问控制：
     *   级别1（必须登录）：
     *     请求必须携带有效 JWT token。未登录 → 401。
     *   级别2（只能查自己，ADMIN 例外）：
     *     JWT 里的 userId 必须与路径里的 {userId} 一致。
     *     不一致 → 403。管理员（role=ADMIN）可查任意用户。
     *
     * 对前端的影响：
     *   调用方（status.html）必须在请求头中携带：
     *     Authorization: Bearer <mun_jwt>
     *   已在 status.html 中同步修复。
     *
     * 返回值变化（对前端完全兼容）：
     *   原返回 Delegate 对象（未找到时返回 null，Spring 自动转 200+空body）
     *   → 现返回 ResponseEntity<?>
     *     · 无 token         → 401（前端 !res.ok → renderEmpty，体验不变）
     *     · userId 不匹配    → 403（前端 !res.ok → renderEmpty，体验不变）
     *     · 无报名记录        → 204 No Content（前端 res.status===204 → renderEmpty，体验不变）
     *     · 正常返回          → 200 + JSON body（完全不变）
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getByUserId(@PathVariable Long userId,
                                         HttpServletRequest request) {

        // ── 级别1：必须携带有效 JWT ────────────────────────────────────
        Object jwtUserId = request.getAttribute("jwtUserId");
        Object jwtRole   = request.getAttribute("jwtRole");
        if (jwtUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // ── 级别2：只能查自己的数据（ADMIN 可查所有人）────────────────
        boolean isAdmin = "ADMIN".equals(jwtRole);
        if (!isAdmin && !((Long) jwtUserId).equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // ── 查询：用 findByUserId 代替 findAll() + 内存过滤 ───────────
        // 原实现用 findAll() 拉全表再在内存里 filter，随报名人数增长性能线性下降。
        // 改为 Spring Data JPA 直接生成 WHERE user_id = ? 的 SQL，只取一条记录。
        Optional<Delegate> delegateOpt = delegateRepository.findByUserId(userId);

        if (delegateOpt.isEmpty()) {
            // 未找到报名记录 → 204 No Content
            // status.html 对 204 有专门处理（renderEmpty），体验不变
            return ResponseEntity.noContent().build();
        }

        Delegate delegate = delegateOpt.get();

        // 原有逻辑保留：回填邀请码（@Transient 字段，数据库无此列）
        if (delegate.getDelegationId() != null) {
            Delegation team = delegationService.getDelegationById(delegate.getDelegationId());
            if (team != null) delegate.setInviteCode(team.getInviteCode());
        }

        return ResponseEntity.ok(delegate);
    }

    // ════════════════════════════════════════════════════════════════
    // 【#050 改动】私有辅助：Admin 身份验证
    // 原版基于 X-Admin-Id 裸ID查库，新版从 JWT request attribute 读取 role
    // ════════════════════════════════════════════════════════════════

    /**
     * 【#050】JWT鉴权：验证请求是否携带有效的ADMIN token
     *
     * 原版校验链路（已废弃）：
     *   前端 localStorage.getItem('mun_admin_id')
     *   → 请求头 X-Admin-Id
     *   → 查 users 表确认 role = 'ADMIN'
     *
     * 新版校验链路：
     *   前端 localStorage.getItem('mun_admin_jwt')
     *   → 请求头 Authorization: Bearer xxx
     *   → JwtFilter 解析后存入 request.getAttribute("jwtRole")
     *   → 本方法直接读取，无需查库
     *
     * @param request HttpServletRequest（JwtFilter 已注入 jwtRole attribute）
     * @return true = ADMIN 角色验证通过；false = 非ADMIN或未携带token
     */
    private boolean verifyAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("jwtRole");
        return "ADMIN".equals(role);
    }

    // ════════════════════════════════════════════════════════════════
    // 【指令 #017-C 新增】接口 A：全量代表列表
    // 路径：GET /api/delegate/admin/list
    // 【#050 改动】鉴权从 X-Admin-Id 改为 JWT
    // ════════════════════════════════════════════════════════════════

    /**
     * 全量代表列表（ADMIN 专用）
     * 路径：GET /api/delegate/admin/list
     *
     * 一次性返回所有代表的完整字段（约 300 人）。
     * 排序逻辑由前端 JS 完成，后端只做原始数据输出，
     * 避免维护复杂的多级嵌套 SQL ORDER BY。
     *
     * 安全：校验JWT中role是否为ADMIN，非 ADMIN 返回 403。
     */
    @GetMapping("/admin/list")
    public ResponseEntity<?> adminList(HttpServletRequest request) {

        if (!verifyAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("权限不足：此接口仅限学术委员会管理员访问");
        }

        // 【B1】构建 delegationId → Delegation 查找 Map，避免 N+1 查询
        Map<Long, com.example.aimunc2026.Entity.Delegation> delegationMap =
                delegationRepository.findAll()
                        .stream()
                        .collect(Collectors.toMap(
                                com.example.aimunc2026.Entity.Delegation::getId,
                                d -> d
                        ));

        List<Map<String, Object>> result = delegateRepository.findAll()
                .stream()
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",                            d.getId());
                    m.put("userId",                        d.getUserId());
                    m.put("realName",                      d.getRealName());
                    m.put("gender",                        d.getGender());
                    m.put("grade",                         d.getGrade());
                    m.put("school",                        d.getSchool());
                    m.put("phone",                         d.getPhone());
                    m.put("email",                         d.getEmail());
                    m.put("wechat",                        d.getWechat());
                    m.put("committeePref1",                d.getCommitteePref1());
                    m.put("committeePref2",                d.getCommitteePref2());
                    m.put("committeePref3",                d.getCommitteePref3());
                    m.put("isAdjustmentAccepted",          d.getIsAdjustmentAccepted());
                    m.put("academicExperience",            d.getAcademicExperience());
                    m.put("emergencyContact1Name",         d.getEmergencyContact1Name());
                    m.put("emergencyContact1Phone",        d.getEmergencyContact1Phone());
                    m.put("emergencyContact1Relationship", d.getEmergencyContact1Relationship());
                    m.put("emergencyContact2Name",         d.getEmergencyContact2Name());
                    m.put("emergencyContact2Phone",        d.getEmergencyContact2Phone());
                    m.put("emergencyContact2Relationship", d.getEmergencyContact2Relationship());
                    m.put("status",                        d.getStatus());
                    m.put("paymentStatus",                 d.getPaymentStatus());
                    m.put("assignedCommittee",             d.getAssignedCommittee());
                    m.put("delegationId",                  d.getDelegationId());
                    // 【B1】代表团名称 + 邀请码（个人代表两者均为 null）
                    if (d.getDelegationId() != null && delegationMap.containsKey(d.getDelegationId())) {
                        var delegation = delegationMap.get(d.getDelegationId());
                        m.put("delegationName",       delegation.getName());
                        m.put("delegationInviteCode", delegation.getInviteCode());
                    } else {
                        m.put("delegationName",       null);
                        m.put("delegationInviteCode", null);
                    }
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════════════════════════
    // 【指令 #017-C 新增】接口 B：统计看板数据
    // 路径：GET /api/delegate/admin/stats
    // 【#050 改动】鉴权从 X-Admin-Id 改为 JWT
    // ════════════════════════════════════════════════════════════════

    /**
     * 统计看板数据（ADMIN 专用）
     * 路径：GET /api/delegate/admin/stats
     *
     * 返回结构：
     * {
     *   "total": 248,
     *   "statusCounts": {
     *     "PENDING": 120,
     *     "LEADER_APPROVED": 64,
     *     "ACADEMIC_BOARD_APPROVED": 31,
     *     "REJECTED": 33
     *   },
     *   "mpcZhCount": 12,
     *   "mpcEnCount": 8
     * }
     *
     * MPC 判断逻辑：代表的任一志愿（pref1/2/3）填写了 mpc-zh 或 mpc-en 即计入。
     * 这与 Delegate 实体的现有字段完全兼容，无需新增数据库列。
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<?> adminStats(HttpServletRequest request) {

        if (!verifyAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("权限不足：此接口仅限学术委员会管理员访问");
        }

        List<Delegate> all = delegateRepository.findAll();

        // ── 各状态计数 ─────────────────────────────────────────────────────
        Map<String, Long> statusCounts = new HashMap<>();
        statusCounts.put("PENDING",
                all.stream().filter(d -> "PENDING".equals(d.getStatus())).count());
        statusCounts.put("LEADER_APPROVED",
                all.stream().filter(d -> "LEADER_APPROVED".equals(d.getStatus())).count());
        statusCounts.put("ACADEMIC_BOARD_APPROVED",
                all.stream().filter(d -> "ACADEMIC_BOARD_APPROVED".equals(d.getStatus())).count());
        // REJECTED 状态可能带理由（格式："REJECTED:理由"），用 startsWith 判断
        statusCounts.put("REJECTED",
                all.stream().filter(d -> d.getStatus() != null
                        && d.getStatus().startsWith("REJECTED")).count());

        // ── MPC 人数（#028 修复：按实际志愿值全志愿扫描）────────────────────
        //
        // 【原逻辑错误】之前使用 "mpc-zh" / "mpc-en" 作为比对值，
        // 但 apply.html 实际存入的值为：
        //   中文MPC: linked-system-mpc / chinese-committee-mpc / icj-mpc
        //   英文MPC: english-committee-mpc
        // 旧值在数据库中从不存在，导致统计永远为 0。
        //
        // 【新逻辑】对三个志愿字段逐一判断，只要任一志愿命中即计入。
        final java.util.Set<String> ZH_MPC_VALS = java.util.Set.of(
                "linked-system-mpc", "chinese-committee-mpc", "icj-mpc");

        long mpcZhCount = all.stream().filter(d ->
                ZH_MPC_VALS.contains(d.getCommitteePref1()) ||
                        ZH_MPC_VALS.contains(d.getCommitteePref2()) ||
                        ZH_MPC_VALS.contains(d.getCommitteePref3())).count();

        long mpcEnCount = all.stream().filter(d ->
                "english-committee-mpc".equals(d.getCommitteePref1()) ||
                        "english-committee-mpc".equals(d.getCommitteePref2()) ||
                        "english-committee-mpc".equals(d.getCommitteePref3())).count();

        // ── 汇总 ────────────────────────────────────────────────────────────
        Map<String, Object> result = new HashMap<>();
        result.put("total",        all.size());
        result.put("statusCounts", statusCounts);
        result.put("mpcZhCount",   mpcZhCount);
        result.put("mpcEnCount",   mpcEnCount);

        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════════════════════════
    // 【指令 #017-C 新增】接口 C：学委终审操作
    // 路径：PATCH /api/delegate/{id}/status
    // 【#050 改动】鉴权从 X-Admin-Id 改为 JWT
    // ════════════════════════════════════════════════════════════════

    /**
     * 学委终审操作（ADMIN 专用）
     * 路径：PATCH /api/delegate/{id}/status
     *
     * 与领队的 POST /update-status 接口的区别：
     *   领队接口：通过 userId 查代表 → 需走代表团归属校验（防止越权）
     *   学委接口：通过 delegate.id（主键）直接操作 → Admin 有最高权限，无需团校验
     *
     * 入参（JSON）：
     * {
     *   "newStatus":    "ACADEMIC_BOARD_APPROVED",  ← 或 "REJECTED"
     *   "rejectReason": "学术经历不符合要求"          ← 驳回时填写，通过时不传
     * }
     *
     * 驳回理由编码方案与领队接口保持一致：status = "REJECTED:理由"
     * 代表端 apply.html 用 split(':')[0] 判断状态，split(':')[1] 读取理由，完全兼容。
     *
     * 状态流转（学委层面）：
     *   LEADER_APPROVED         → ACADEMIC_BOARD_APPROVED  （终审通过）
     *   LEADER_APPROVED         → REJECTED:理由              （终审驳回）
     *   ACADEMIC_BOARD_APPROVED → REJECTED:理由              （撤回终审）
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<String> adminUpdateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        if (!verifyAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("权限不足：终审操作仅限学术委员会管理员");
        }

        Optional<Delegate> delegateOpt = delegateRepository.findById(id);
        if (delegateOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("未找到 ID 为 " + id + " 的代表记录");
        }

        Delegate delegate = delegateOpt.get();

        String newStatus    = body.getOrDefault("newStatus", "").toString().trim();
        String rejectReason = body.containsKey("rejectReason") && body.get("rejectReason") != null
                ? body.get("rejectReason").toString().trim() : "";

        if (newStatus.isEmpty()) {
            return ResponseEntity.badRequest().body("newStatus 不能为空");
        }

        // 【#033】驳回时编码进状态字段
        // 学委驳回加 [学委] 前缀，前端据此精准区分来源
        // 格式：REJECTED:[学委]理由  (学委)  /  REJECTED:理由  (领队，不变)
        if ("REJECTED".equals(newStatus)) {
            String encoded = rejectReason.isEmpty() ? "" : "[学委]" + rejectReason;
            delegate.setStatus(encoded.isEmpty() ? "REJECTED" : "REJECTED:" + encoded);

            // 【#034】驳回即释放：强制清空会场分配
            // 业务规则：代表被驳回意味着失去当前席位，分配结果必须同步释放，
            // 防止"幽灵占位"——名额被驳回代表锁死，其他代表无法被分配进该席位。
            // 代表修改报名表重新提交并再次通过终审后，学委可重新执行会场分配。
            delegate.setAssignedCommittee(null);
        } else {
            delegate.setStatus(newStatus);
        }

        delegateRepository.save(delegate);
        return ResponseEntity.ok("操作成功，代表 #" + id + " 状态已更新为：" + delegate.getStatus());
    }

    // ════════════════════════════════════════════════════════════════
    // 路径：PATCH /api/delegate/{id}/assign-committee  【#030 新增】
    // 【#050 改动】鉴权从 X-Admin-Id 改为 JWT
    // ════════════════════════════════════════════════════════════════

    /**
     * 学委会场分配接口（ADMIN 专用）
     * 路径：PATCH /api/delegate/{id}/assign-committee
     *
     * 入参（JSON）：
     * {
     *   "committeeKey": "linked-system"   ← 前端 COMMITTEES 对象的 Key
     * }
     *
     * 业务规则：
     *   1. 只允许对已通过终审（ACADEMIC_BOARD_APPROVED）的代表操作
     *   2. committeeKey 为空时表示撤回分配（重置为 null）
     *   3. 分配后不改变 status 字段，仅更新 assignedCommittee 字段
     *
     * 返回：更新后的代表对象（前端据此做本地内存更新，无需重新拉取列表）
     */
    @PatchMapping("/{id}/assign-committee")
    public ResponseEntity<?> assignCommittee(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        if (!verifyAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("权限不足：会场分配操作仅限学术委员会管理员");
        }

        Optional<Delegate> delegateOpt = delegateRepository.findById(id);
        if (delegateOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("未找到 ID 为 " + id + " 的代表记录");
        }

        Delegate delegate = delegateOpt.get();

        // 安全防护：只允许对终审通过的代表进行会场分配
        if (!"ACADEMIC_BOARD_APPROVED".equals(delegate.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("仅允许对状态为 ACADEMIC_BOARD_APPROVED 的代表进行会场分配（当前状态：" + delegate.getStatus() + "）");
        }

        // committeeKey 为空字符串或 null 时表示撤回分配
        String committeeKey = body.containsKey("committeeKey") && body.get("committeeKey") != null
                ? body.get("committeeKey").toString().trim() : "";
        delegate.setAssignedCommittee(committeeKey.isEmpty() ? null : committeeKey);

        delegateRepository.save(delegate);
        return ResponseEntity.ok(delegate);
    }

    // ════════════════════════════════════════════════════════════════
    // 【付款凭证 Step 2】接口 D：代表上传付款凭证
    // 路径：POST /api/delegate/{id}/payment-proof
    // 【#050 改动】移除 @RequestParam operatorId，从JWT读取身份
    // ════════════════════════════════════════════════════════════════

    /**
     * 代表上传付款凭证
     * 路径：POST /api/delegate/{id}/payment-proof
     *
     * 入参（multipart/form-data）：
     *   file : MultipartFile（图片，≤2MB，仅 jpg/png）
     *
     * 【#050 改动】移除 operatorId 参数，从JWT提取身份
     * 身份校验：本人 或 管理员均可上传
     *
     * 覆盖重传：无论 paymentStatus 为何值均允许，
     * 覆盖后 paymentStatus 重置为 PENDING_REVIEW，旧文件物理删除（C-05 约束）。
     */
    @PostMapping(value = "/{id}/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadPaymentProof(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Optional<Delegate> delegateOpt = delegateRepository.findById(id);
        if (delegateOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("未找到 ID 为 " + id + " 的代表记录");
        }
        Delegate delegate = delegateOpt.get();

        // 【#050】从JWT提取操作者身份
        Object jwtUserId = request.getAttribute("jwtUserId");
        Object jwtRole   = request.getAttribute("jwtRole");
        if (jwtUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }
        Long operatorId = (Long) jwtUserId;

        // 身份校验：本人 或 管理员均可上传
        boolean isAdmin = "ADMIN".equals(jwtRole);
        if (!isAdmin && !delegate.getUserId().equals(operatorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("权限不足：只有本人或管理员可以上传付款凭证");
        }

        // 文件大小校验（≤2MB，双重防护 C-06 约束）
        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("图片大小不能超过 2MB");
        }

        // MIME 类型校验（仅 jpg / png）
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals(MediaType.IMAGE_JPEG_VALUE) &&
                        !contentType.equals(MediaType.IMAGE_PNG_VALUE))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("仅支持 JPG / PNG 格式的图片");
        }

        try {
            // 生成唯一文件名（id + 时间戳，防碰撞）
            String ext = contentType.equals(MediaType.IMAGE_PNG_VALUE) ? "png" : "jpg";
            String newFilename = "payment_" + id + "_" + System.currentTimeMillis() + "." + ext;

            // 删除旧文件，防止垃圾文件积累（C-05 约束）
            String oldFilename = delegate.getPaymentProofFilename();
            if (oldFilename != null && !oldFilename.isBlank()) {
                Files.deleteIfExists(uploadDir.resolve(oldFilename));
            }

            // 写入新文件
            Files.copy(file.getInputStream(),
                    uploadDir.resolve(newFilename),
                    StandardCopyOption.REPLACE_EXISTING);

            // 更新数据库
            delegate.setPaymentProofFilename(newFilename);
            delegate.setPaymentStatus("PENDING_REVIEW");
            delegateRepository.save(delegate);

            return ResponseEntity.ok("付款凭证上传成功");

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("文件写入失败：" + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 【付款凭证 Step 2】接口 E：Admin 审核付款凭证状态
    // 路径：PATCH /api/delegate/{id}/payment-status
    // 【#050 改动】鉴权从 X-Admin-Id 改为 JWT
    // ════════════════════════════════════════════════════════════════

    /**
     * Admin 审核付款凭证（ADMIN 专用）
     * 路径：PATCH /api/delegate/{id}/payment-status
     *
     * 入参（JSON）：
     *   { "newPaymentStatus": "VERIFIED" }       ← 通过
     *   { "newPaymentStatus": "NOT_SUBMITTED" }  ← 驳回（重置，代表可重新上传）
     *
     * 驳回行为：仅重置 paymentStatus，文件保留在磁盘。
     * 复用 verifyAdmin() 进行 Admin 鉴权（C-10 约束）。
     */
    @PatchMapping("/{id}/payment-status")
    public ResponseEntity<String> adminUpdatePaymentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        if (!verifyAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("权限不足：付款凭证审核仅限学术委员会管理员");
        }

        Optional<Delegate> delegateOpt = delegateRepository.findById(id);
        if (delegateOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("未找到 ID 为 " + id + " 的代表记录");
        }
        Delegate delegate = delegateOpt.get();

        String newPaymentStatus = body.getOrDefault("newPaymentStatus", "").toString().trim();
        if (!newPaymentStatus.equals("VERIFIED") && !newPaymentStatus.equals("NOT_SUBMITTED")) {
            return ResponseEntity.badRequest()
                    .body("newPaymentStatus 只允许传 VERIFIED 或 NOT_SUBMITTED");
        }

        delegate.setPaymentStatus(newPaymentStatus);
        delegateRepository.save(delegate);
        return ResponseEntity.ok("操作成功，代表 #" + id + " 付款状态已更新为：" + newPaymentStatus);
    }

    // ════════════════════════════════════════════════════════════════
    // 【付款凭证 Step 2】接口 F：带权限校验的图片流获取
    // 路径：GET /api/delegate/{id}/payment-proof
    // 【#050 改动】从JWT读取身份，不再从 X-Admin-Id / X-Operator-Id 读
    // ════════════════════════════════════════════════════════════════

    /**
     * 获取付款凭证图片（带身份校验，文件流返回）
     * 路径：GET /api/delegate/{id}/payment-proof
     *
     * 不走静态资源映射，所有请求经过身份校验（C-03 约束）。
     *
     * 【#050 改动】权限从 header 裸ID 改为 JWT：
     *   1. JWT role = ADMIN → 放行（学委管理员）
     *   2. JWT userId 等于 delegate.userId → 放行（本人）
     *   3. JWT userId 是该代表所在代表团的 leaderUserId → 放行（领队）
     */
    @GetMapping("/{id}/payment-proof")
    public ResponseEntity<?> getPaymentProof(
            @PathVariable Long id,
            HttpServletRequest request) {

        Optional<Delegate> delegateOpt = delegateRepository.findById(id);
        if (delegateOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("代表记录不存在");
        }
        Delegate delegate = delegateOpt.get();

        // ── 【#050】JWT 权限校验（三层，满足一层即放行）──────────────────
        boolean allowed = false;
        Object jwtRole   = request.getAttribute("jwtRole");
        Object jwtUserId = request.getAttribute("jwtUserId");

        // 层1：Admin 身份
        if ("ADMIN".equals(jwtRole)) allowed = true;

        // 层2：本人 / 领队身份
        if (!allowed && jwtUserId != null) {
            Long operatorId = (Long) jwtUserId;
            // 本人
            if (delegate.getUserId().equals(operatorId)) allowed = true;
            // 领队
            if (!allowed && delegate.getDelegationId() != null) {
                Delegation team = delegationService.getDelegationById(delegate.getDelegationId());
                if (team != null && team.getLeaderUserId().equals(operatorId)) allowed = true;
            }
        }

        if (!allowed) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("权限不足：无权查看该代表的付款凭证");
        }

        // ── 文件存在性校验 ──────────────────────────────────────────
        String filename = delegate.getPaymentProofFilename();
        if (filename == null || filename.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("该代表尚未上传付款凭证");
        }

        try {
            Path filePath = uploadDir.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("凭证文件不存在或不可读");
            }
            MediaType mediaType = filename.toLowerCase().endsWith(".png")
                    ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("文件读取失败：" + e.getMessage());
        }
    }
}

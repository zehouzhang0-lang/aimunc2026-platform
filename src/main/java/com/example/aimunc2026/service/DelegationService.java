package com.example.aimunc2026.service;

import com.example.aimunc2026.Entity.Delegation;
import com.example.aimunc2026.repository.DelegationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * ════════════════════════════════════════════════════════════
 *  【服务层】代表团业务逻辑 (DelegationService)
 * ════════════════════════════════════════════════════════════
 *
 *  【指令 #020 改动】createDelegation() 增加双层唯一性校验：
 *
 *  层1（硬拦截）：校区唯一性
 *    schoolName 不为空时，调用 existsBySchoolName() 检查。
 *    若已存在 → 抛出 IllegalStateException（前端显示红色错误，阻止提交）。
 *
 *  层2（兜底防重）：领队防重建团（原有逻辑，完全保留）
 *    同一 leaderUserId 不能建第二个团。
 *
 *  注意：协会名(name)的重名检测仅供前端实时探测用，
 *  Service 层不做硬拦截（允许多校区同名协会共存）。
 *
 *  ★ 原有所有方法签名、返回类型、异常类型均未改变，100% 向后兼容。
 */
@Service
public class DelegationService {

    @Autowired
    private DelegationRepository delegationRepository;

    /**
     * 根据邀请码查找代表团（原有方法，未改动）
     */
    public Delegation getDelegationByInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) return null;
        String normalizedCode = inviteCode.trim().toUpperCase();
        return delegationRepository.findByInviteCode(normalizedCode).orElse(null);
    }

    /**
     * 根据领队用户ID查找代表团（原有方法，未改动）
     */
    public Delegation getDelegationByLeaderId(Long leaderId) {
        return delegationRepository.findByLeaderUserId(leaderId).orElse(null);
    }

    /**
     * 创建代表团
     *
     * 【指令 #020】新增双层校验，在原有领队防重之前：
     *
     * 层1 - 校区硬拦截：
     *   若 schoolName 非空且已存在于库中，立即抛出 IllegalStateException，
     *   消息：「该学校已建好代表团，不能重复建团」
     *   DelegationController 捕获后返回 HTTP 400，前端显示红色预警。
     *
     * 层2 - 领队防重（原有逻辑，完全保留）：
     *   若同一 leaderUserId 已有团，抛出 IllegalStateException。
     *
     * @throws IllegalStateException 校区冲突 或 领队已建团
     */
    public Delegation createDelegation(Delegation delegation) {

        // ── 层1：校区唯一性硬拦截（#020 新增）────────────────────────────────
        String schoolName = delegation.getSchoolName();
        if (schoolName != null && !schoolName.trim().isEmpty()) {
            if (delegationRepository.existsBySchoolName(schoolName.trim())) {
                throw new IllegalStateException(
                        "该学校已建好代表团，不能重复建团"
                );
            }
            // 写回 trim 后的值，防止前后空格引发的绕过
            delegation.setSchoolName(schoolName.trim());
        }

        // ── 层2：领队防重校验（原有逻辑，完全保留）──────────────────────────
        Delegation existing = getDelegationByLeaderId(delegation.getLeaderUserId());
        if (existing != null) {
            throw new IllegalStateException(
                    "该领队账号已创建过代表团（邀请码：" + existing.getInviteCode() + "），每个账号只能创建一个代表团。"
            );
        }

        delegation.setInviteCode(generateInviteCode());
        return delegationRepository.save(delegation);
    }

    /**
     * 根据数据库主键 ID 查找代表团（原有方法，未改动）
     */
    public Delegation getDelegationById(Long id) {
        return delegationRepository.findById(id).orElse(null);
    }

    /**
     * 【#020 新增】检查协会名是否已存在（供前端实时探测接口调用）
     * 仅用于软提醒，不用于阻止提交。
     */
    public boolean isNameExists(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        return delegationRepository.existsByName(name.trim());
    }

    /**
     * 生成 6 位随机邀请码（大写字母 + 数字，原有方法，未改动）
     */
    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random rnd = new Random();
        while (code.length() < 6) {
            code.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return code.toString();
    }

    /**
     * 【新增】返回全部代表团列表，供 admin/list 端点使用
     */
    public List<Delegation> getAllDelegations() {
        return delegationRepository.findAll();
    }
}
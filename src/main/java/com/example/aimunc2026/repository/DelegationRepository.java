package com.example.aimunc2026.repository;

import com.example.aimunc2026.Entity.Delegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ════════════════════════════════════════════════════════════
 *  【数据仓库】代表团 Repository
 * ════════════════════════════════════════════════════════════
 *
 *  【指令 #020 新增】唯一性校验方法
 *
 *  existsBySchoolName(schoolName)：
 *    硬拦截用 — 校区唯一性检查，同一物理校区只能建一个代表团。
 *    对应 SQL: SELECT COUNT(*) > 0 FROM delegations WHERE school_name = ?
 *
 *  existsByName(name)：
 *    软提醒用 — 协会名重名检测，不阻止提交，仅提示用户注意校区填写。
 *    对应 SQL: SELECT COUNT(*) > 0 FROM delegations WHERE name = ?
 */
@Repository
public interface DelegationRepository extends JpaRepository<Delegation, Long> {

    /**
     * 根据邀请码查找代表团
     * 对应 SQL: SELECT * FROM delegations WHERE invite_code = ?
     */
    Optional<Delegation> findByInviteCode(String inviteCode);

    /**
     * 根据领队用户ID查找代表团
     * 对应 SQL: SELECT * FROM delegations WHERE leader_user_id = ?
     */
    Optional<Delegation> findByLeaderUserId(Long leaderUserId);

    /**
     * 【#020 新增】校区唯一性硬校验
     * 物理校区是唯一的，不允许两个领队用同一学校名建团。
     * 对应 SQL: SELECT COUNT(*) > 0 FROM delegations WHERE school_name = ?
     */
    boolean existsBySchoolName(String schoolName);

    /**
     * 【#020 新增】协会名重名软检测
     * 协会名允许重名（多校区场景），但重名时前端需给出黄色提醒。
     * 对应 SQL: SELECT COUNT(*) > 0 FROM delegations WHERE name = ?
     */
    boolean existsByName(String name);
}
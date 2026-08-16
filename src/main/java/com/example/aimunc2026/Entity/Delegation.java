package com.example.aimunc2026.Entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 代表团实体类
 * 对应数据库中的 delegations 表
 *
 * 【指令 #023-B 新增字段】
 *
 *  leaderName（领队真实姓名）
 *    → 数据库列：leader_name VARCHAR(50)
 *    → ddl-auto=update 重启后自动添加，无需手动执行 SQL
 *
 *  leaderPhone（领队手机号，11位）
 *    → 数据库列：leader_phone VARCHAR(20)
 *    → ddl-auto=update 重启后自动添加，无需手动执行 SQL
 *
 *  不新增 leaderSchool：领队所属学校直接复用 schoolName 字段，
 *  避免数据冗余与潜在不一致（领队必须属于该代表团校区）。
 *
 *  ★ 原有 5 个字段（id、leaderUserId、name、schoolName、inviteCode）
 *    注解、列名、约束均一字未动，100% 向后兼容。
 */
@Entity
@Table(name = "delegations")
@Data
public class Delegation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 领队的用户ID，映射数据库字段 leader_user_id
    @Column(name = "leader_user_id", nullable = false)
    private Long leaderUserId;

    // 代表团名称
    @Column(nullable = false)
    private String name;

    // 学校/校区名称，映射数据库字段 school_name
    @Column(name = "school_name")
    private String schoolName;

    // 6位唯一邀请码，映射数据库字段 invite_code
    @Column(name = "invite_code", unique = true, length = 6)
    private String inviteCode;

    // 【#023-B 新增】领队真实姓名，用于实名合规与会务紧急联络
    @Column(name = "leader_name", length = 50)
    private String leaderName;

    // 【#023-B 新增】领队手机号（11位），用于会务紧急联络
    @Column(name = "leader_phone", length = 20)
    private String leaderPhone;
}
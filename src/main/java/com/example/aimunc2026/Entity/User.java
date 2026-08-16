package com.example.aimunc2026.Entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * ════════════════════════════════════════════════════════════
 *  【实体类】用户账号 (User)
 *  对应数据库表：users
 * ════════════════════════════════════════════════════════════
 *
 *  【指令 #016 改动说明】
 *
 *  问题：username 字段上的 unique = true 是"单列唯一约束"，
 *  意味着"张三"只能注册一次，无论他是代表还是领队。
 *  即使你在 MySQL 里手动删掉了这个索引，Hibernate 在下次启动时
 *  （ddl-auto = update）会看到 @Column(unique = true) 然后自动重建它！
 *  所以 Java 代码里的 unique = true 必须同步去掉。
 *
 *  方案：改为 @Table 级别的 uniqueConstraints，对 (username, role) 建复合唯一约束。
 *  效果："张三+DELEGATE" 和 "张三+LEADER" 是两条完全独立的记录，互不干扰。
 *  数据安全：表名、列名、已有数据完全不受影响，只是索引逻辑变了。
 */
@Entity
@Table(
        name = "users",
        // 【#016 核心】复合唯一约束：同角色下用户名唯一，不同角色可以同名
        // 对应 SQL: ALTER TABLE users ADD CONSTRAINT uq_username_role UNIQUE (username, role);
        // （该 SQL 已在 migration_016_composite_unique.sql 中手动执行过）
        uniqueConstraints = @UniqueConstraint(
                name = "uq_username_role",
                columnNames = {"username", "role"}
        )
)
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    /**
     * 【#016 改动】去掉了 unique = true
     * 原来：@Column(nullable = false, unique = true)  ← 单列唯一，导致身份锁死
     * 现在：@Column(nullable = false)                 ← 唯一性改由 @Table 的复合约束保证
     */
    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String password_hash;

    /**
     * 用户角色：DELEGATE / LEADER / ADMIN
     * 与 username 共同构成复合唯一键
     */
    @Column(nullable = false)
    private String role;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime created_at;

    @Column(name = "updated_at")
    private LocalDateTime updated_at;
}
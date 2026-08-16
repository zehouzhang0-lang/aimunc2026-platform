package com.example.aimunc2026.repository;

import com.example.aimunc2026.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * ════════════════════════════════════════════════════════════
 *  【数据仓库】用户账号 Repository
 * ════════════════════════════════════════════════════════════
 *
 *  【指令 #016 改动说明】
 *
 *  旧方法：findByUsername(String username)
 *    → 生成 SQL: WHERE username = ?
 *    → 问题：查到旧账号就直接返回，完全忽略用户选择的新角色
 *
 *  新方法：findByUsernameAndRole(String username, String role)
 *    → 生成 SQL: WHERE username = ? AND role = ?
 *    → 效果：「张三+DELEGATE」查不到「张三+LEADER」，两条记录完全隔离
 *
 *  JPA 方法命名魔法：框架自动解析方法名，无需手写 SQL，
 *  And 连接两个字段就能实现复合条件查询。
 *
 *  【指令 #024 新增】
 *
 *  existsByUsernameAndRole(String username, String role)
 *    → 生成 SQL: SELECT COUNT(*) > 0 FROM users WHERE username = ? AND role = ?
 *    → 用于 GET /api/users/exists 轻量探测接口，返回 boolean，无需拉取整条记录
 *    → 比 findByUsernameAndRole 更高效（不返回实体，只判断存在性）
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 【#016 核心】按 (username + role) 联合查询
     *
     * 对应 SQL: SELECT * FROM users WHERE username = ? AND role = ?
     *
     * 查不到 → 当前角色下该用户名不存在 → 走注册流程，创建新账号
     * 查得到 → 该 (username, role) 组合已存在 → 走登录流程，校验密码
     */
    Optional<User> findByUsernameAndRole(String username, String role);

    /**
     * 【#024 新增】存在性轻量探测
     *
     * 对应 SQL: SELECT COUNT(*) > 0 FROM users WHERE username = ? AND role = ?
     *
     * 专用于注册页动态探测 —— 判断该 (username, role) 组合是否已注册，
     * 据此决定是否展开「确认密码」输入框。
     * 只返回 boolean，不拉取用户实体，性能最优。
     */
    boolean existsByUsernameAndRole(String username, String role);
}
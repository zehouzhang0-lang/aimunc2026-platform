package com.example.aimunc2026.repository;

import com.example.aimunc2026.Entity.Delegate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 【审计重置版】代表资料仓库
 */
@Repository
public interface DelegateRepository extends JpaRepository<Delegate, Long> {

    /**
     * 核心功能：根据代表团 ID 查询所有成员
     * Spring Data JPA 会自动解析方法名生成 SQL:
     * SELECT * FROM delegates WHERE delegation_id = ?
     */
    List<Delegate> findByDelegationId(Long delegationId);

    /**
     * 【#CRIT-02 新增】根据用户 ID 查询代表报名记录
     *
     * 为什么要加这个方法？
     *   原来 getByUserId 接口使用 findAll() 把全部代表拉进内存再过滤，
     *   等报名人数增长到 200+ 时每次查询都要扫描全表，性能极差。
     *   这里让 Spring Data JPA 直接生成带 WHERE 条件的 SQL，
     *   只取需要的那一条记录。
     *
     * 对应 SQL: SELECT * FROM delegates WHERE user_id = ? LIMIT 1
     *
     * 返回 Optional 而非直接返回 Delegate，
     * 调用方可以用 .orElse(null) 或 .isPresent() 安全处理空结果，
     * 不会因为记录不存在而抛出异常。
     */
    Optional<Delegate> findByUserId(Long userId);

}
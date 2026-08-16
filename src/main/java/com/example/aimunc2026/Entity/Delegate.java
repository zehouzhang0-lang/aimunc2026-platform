package com.example.aimunc2026.Entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 【审计重置版】代表详细资料实体类
 * * 降妖除魔核心逻辑：
 * 1. 类名 Delegate 必须首字母大写，与文件名 Delegate.java 保持绝对一致。
 * 2. 使用 @Data 自动生成 Getter 和 Setter，请务必确认 IDEA 已安装 Lombok 插件。
 *
 * ════════════════════════════════════════════════════════════
 *  【指令 #017-C 新增字段说明】
 *
 *  以下 4 个字段为本次新增，其余字段与原版本完全一致，未做任何修改。
 *
 *  1. school（学校）
 *     原实体缺失，管理员详情面板需要展示。
 *     → 需执行：ALTER TABLE delegates ADD COLUMN school VARCHAR(100);
 *
 *  2. wechat（微信号）
 *     管理员详情面板联络区域展示用。
 *     → 需执行：ALTER TABLE delegates ADD COLUMN wechat VARCHAR(100);
 *
 *  3. emergencyContact1Relationship（紧急联系人一关系）
 *     如"父亲"、"母亲"、"监护人"。
 *     → 需执行：ALTER TABLE delegates ADD COLUMN emergency_contact_1_relationship VARCHAR(50);
 *
 *  4. emergencyContact2Relationship（紧急联系人二关系）
 *     → 需执行：ALTER TABLE delegates ADD COLUMN emergency_contact_2_relationship VARCHAR(50);
 *
 *  【数据库迁移脚本】存为 migration_018_delegate_fields.sql 后执行：
 *
 *    ALTER TABLE delegates ADD COLUMN school VARCHAR(100);
 *    ALTER TABLE delegates ADD COLUMN wechat VARCHAR(100);
 *    ALTER TABLE delegates ADD COLUMN emergency_contact_1_relationship VARCHAR(50);
 *    ALTER TABLE delegates ADD COLUMN emergency_contact_2_relationship VARCHAR(50);
 *
 *  注意：ddl-auto=update 会自动添加新列，但不会删除或修改已有列，
 *  所以重启 Spring Boot 后新字段会自动建好，手动执行 SQL 二选一即可。
 * ════════════════════════════════════════════════════════════
 */
@Entity
@Table(name = "delegates") // 明确指定数据库中的表名为 delegates
@Data
public class Delegate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @Column(name = "user_id") 就像一个翻译官：
     * 它告诉 JPA：你在 Java 里看到的 userId，其实就是数据库里的 user_id 列。
     * 这样即使前后端命名习惯不同，也能精准找到坑位。
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "delegation_id")
    private Long delegationId;

    /**
     * @Transient 注解非常关键！
     * 它的意思是"瞬时的/不持久化的"。
     * 这个字段只在内存里用来接收前端传来的 27JTN6 邀请码，
     * 数据库的 delegates 表里根本没有这一列，加了这个注解 JPA 就会跳过它，不会报错。
     */
    @Transient
    private String inviteCode;

    @Column(name = "real_name", nullable = false)
    private String realName;

    // 以下字段如果数据库列名和变量名一致，可以简写，但为了"防弹"，我们统一指定
    @Column(name = "gender")
    private String gender;

    /** 【指令 #017-C 新增】学校名称，原实体缺失，补充持久化 */
    @Column(name = "school")
    private String school;

    @Column(name = "grade")
    private String grade;

    /** 【指令 #017-C 新增】微信号，管理员详情面板联络区域展示 */
    @Column(name = "wechat")
    private String wechat;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "academic_experience", columnDefinition = "TEXT")
    private String academicExperience;

    @Column(name = "committee_pref_1")
    private String committeePref1;

    @Column(name = "committee_pref_2")
    private String committeePref2;

    @Column(name = "committee_pref_3")
    private String committeePref3;

    @Column(name = "is_adjustment_accepted")
    private Boolean isAdjustmentAccepted;

    @Column(name = "status")
    private String status;

    /**
     * 【#030 新增】学委最终录取会场
     * 存储会场 Key（与前端 COMMITTEES 对象保持一致），如 "linked-system"、"english-committee-mpc"
     * ddl-auto=update 重启后自动建列，无需手动执行 SQL
     * 未分配时为 null，仅在 status=ACADEMIC_BOARD_APPROVED 后由学委操作写入
     */
    @Column(name = "assigned_committee")
    private String assignedCommittee;

    // 紧急联系人一
    @Column(name = "emergency_contact_1_name")
    private String emergencyContact1Name;

    /** 【指令 #017-C 新增】紧急联系人一与代表的关系，如"父亲"、"母亲"、"监护人" */
    @Column(name = "emergency_contact_1_relationship")
    private String emergencyContact1Relationship;

    @Column(name = "emergency_contact_1_phone")
    private String emergencyContact1Phone;

    // 紧急联系人二
    @Column(name = "emergency_contact_2_name")
    private String emergencyContact2Name;

    /** 【指令 #017-C 新增】紧急联系人二与代表的关系 */
    @Column(name = "emergency_contact_2_relationship")
    private String emergencyContact2Relationship;

    @Column(name = "emergency_contact_2_phone")
    private String emergencyContact2Phone;

    // ════════════════════════════════════════════════════════════════
    // 【付款凭证 Step 1】新增字段
    // ════════════════════════════════════════════════════════════════

    /**
     * 【付款凭证】磁盘文件名（非路径、非 Base64）
     * 示例："payment_42_1740000000000.jpg"
     * 未上传时为 null。
     *
     * @JsonIgnore：此字段绝不出现在列表接口响应中，
     * 防止无意义字符串污染列表请求。
     * 图片通过专用接口 GET /{id}/payment-proof 获取。
     */
    @JsonIgnore
    @Column(name = "payment_proof_filename")
    private String paymentProofFilename;

    /**
     * 【付款凭证】审核状态
     * 合法值：NOT_SUBMITTED（默认）/ PENDING_REVIEW / VERIFIED
     * 历史记录中为 null 时，前端统一等价于 NOT_SUBMITTED（C-08 约束）。
     * 不加 @JsonIgnore，正常出现在列表接口响应中供前端显示和筛选。
     */
    @Column(name = "payment_status",
            columnDefinition = "VARCHAR(20) DEFAULT 'NOT_SUBMITTED'")
    private String paymentStatus;
}
package com.linkhub.common;

/**
 * 项目统一文案管理类
 * 集中管理所有提示信息、错误描述、业务常量文案
 * 方便统一修改，避免硬编码散落在各处
 */
public final class MessageConstants {

    private MessageConstants() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ================================================================
    // 1. 通用提示
    // ================================================================

    public static final String OPERATION_SUCCESS = "操作成功";
    public static final String OPERATION_FAILED = "操作失败";
    public static final String SYSTEM_BUSY = "系统繁忙，请稍后再试";

    // ================================================================
    // 2. 用户认证模块 (10000)
    // ================================================================

    public static final String USER_NOT_EXIST = "该用户不存在";
    public static final String USER_PASSWORD_ERROR = "账号或密码错误";
    public static final String USER_ACCOUNT_LOCKED = "账号已被锁定，请联系管理员";
    public static final String USER_EMAIL_REGISTERED = "该邮箱已被注册";
    public static final String USER_UNREGISTERED = "该邮箱尚未注册";
    public static final String USER_LOGIN_SUCCESS = "登录成功";
    public static final String USER_REGISTER_SUCCESS = "注册成功，您的用户名是：";
    public static final String USER_LOGOUT_SUCCESS = "退出成功";
    public static final String USER_UPDATE_SUCCESS = "个人信息更新成功";
    public static final String USER_PASSWORD_UPDATE_SUCCESS = "密码修改成功";
    public static final String USER_DELETE_SUCCESS = "账号已注销";
    public static final String USER_NOT_LOGGED_IN = "用户未登录";
    public static final String CANNOT_GET_USER_ID = "无法获取用户ID";

    // 密码校验
    public static final String PASSWORD_NOT_EMPTY = "密码不能为空";
    public static final String PASSWORD_LENGTH_INVALID = "密码长度必须在 6-20 字符之间";
    public static final String PASSWORD_SAME_AS_OLD = "新密码不能与旧密码相同";
    public static final String OLD_PASSWORD_NOT_EMPTY = "旧密码不能为空";
    public static final String OLD_PASSWORD_WRONG = "旧密码错误";

    // 用户名/邮箱校验
    public static final String USERNAME_NOT_EMPTY = "用户名不能为空";
    public static final String USERNAME_INVALID = "用户名只能包含字母、数字、下划线，长度4-20位";
    public static final String USERNAME_ALREADY_EXISTS = "用户名已被占用";
    public static final String EMAIL_INVALID = "邮箱格式不正确";

    // 验证码
    public static final String VERIFY_CODE_ERROR = "验证码错误或已过期";
    public static final String VERIFY_CODE_SEND_TOO_FREQUENT = "验证码发送过于频繁，请稍后再试";
    public static final String VERIFY_CODE_SENT_SUCCESS = "验证码已发送";
    public static final String VERIFY_CODE_NOT_EMPTY = "验证码不能为空";
    public static final String VERIFY_CODE_LENGTH_INVALID = "验证码必须是 6 位数";

    // ================================================================
    // 3. 短链接业务模块 (30000)
    // ================================================================

    // 生成短链
    public static final String SHORT_LINK_GENERATE_SUCCESS = "生成成功";
    public static final String SHORT_LINK_ALREADY_EXISTS = "短链已存在";
    public static final String SHORT_LINK_NOT_EXIST = "该短链接不存在或已失效";
    public static final String SHORT_LINK_IS_BANNED = "该短链接因违规已被封禁";
    public static final String SHORT_LINK_EXPIRED = "该短链接已过期";
    public static final String SHORT_LINK_DELETE_SUCCESS = "删除成功";
    public static final String SHORT_LINK_UPDATE_SUCCESS = "更新成功";
    public static final String SHORT_LINK_BATCH_DELETE_SUCCESS = "批量删除成功";
    public static final String SHORT_LINK_MOVE_SUCCESS = "移动成功";

    // 短码校验
    public static final String ORIGINAL_URL_NOT_EMPTY = "原始长链接不能为空";
    public static final String ORIGINAL_URL_INVALID = "请输入有效的长链接 URL 地址";
    public static final String SHORT_CODE_CUSTOM_INVALID = "自定义短码仅支持字母、数字、下划线、短横线，长度 4-16 位";
    public static final String SHORT_CODE_CUSTOM_OCCUPIED = "该短码已被占用，请换一个";
    public static final String SHORT_CODE_CUSTOM_RESERVED = "该短码为系统保留词，不可使用";
    public static final String SHORT_CODE_GENERATE_FAILED = "短码生成失败，请稍后重试";
    public static final String SHORT_CODE_NOT_EMPTY = "短码不能为空";

    // 有效期
    public static final String VALID_DAYS_MIN = "有效期天数最小为 1 天";
    public static final String VALID_DAYS_MAX = "有效期天数最大为 365 天";
    public static final String VALID_DATE_PERMANENT = "永久";

    // 分组
    public static final String GROUP_SELECT_AT_LEAST_ONE = "请选择要删除的短链";

    // ================================================================
    // 4. 管理员模块 (40000)
    // ================================================================

    public static final String ADMIN_REQUIRED = "需要管理员权限";
    public static final String ADMIN_USER_BANNED = "该用户已被封禁";
    public static final String ADMIN_USER_ACTIVE = "该用户已是正常状态";
    public static final String ADMIN_LINK_BANNED = "该短链接已被封禁";
    public static final String ADMIN_LINK_ACTIVE = "该短链接已是正常状态";
    public static final String ADMIN_BAN_USER_SUCCESS = "封禁成功";
    public static final String ADMIN_UNBAN_USER_SUCCESS = "解封成功";
    public static final String ADMIN_BAN_LINK_SUCCESS = "封禁成功";
    public static final String ADMIN_UNBAN_LINK_SUCCESS = "解封成功";
    public static final String ADMIN_STATUS_INVALID = "status 只能为 0 或 1";

    // ================================================================
    // 5. 限流模块 (429)
    // ================================================================

    public static final String TOO_MANY_REQUESTS = "请求过于频繁，请稍后再试";

    // ================================================================
    // 6. 参数校验通用 (400)
    // ================================================================

    public static final String PARAM_ERROR = "参数错误";
    public static final String PARAM_PAGE_MIN = "页码最小为 1";
    public static final String PARAM_SIZE_MIN = "每页大小最小为 1";
    public static final String SHORT_LINK_ID_NOT_EMPTY = "短链ID不能为空";

    // ================================================================
    // 7. HTTP 状态码描述
    // ================================================================

    public static final String HTTP_401 = "暂未登录或 Token 已经过期";
    public static final String HTTP_403 = "没有相关权限，拒绝访问";
    public static final String HTTP_404 = "请求的接口或资源不存在";

    // ================================================================
    // 8. 日志相关
    // ================================================================

    public static final String LOG_RECEIVE_REDIRECT_REQUEST = "收到短链接跳转请求: {}";
    public static final String LOG_CACHE_HIT = "缓存命中: {} -> {}";
    public static final String LOG_CACHE_MISS = "缓存未命中，查询数据库: {}";
    public static final String LOG_SHORT_LINK_NOT_FOUND = "短链接不存在: {}";
    public static final String LOG_SHORT_LINK_BANNED = "短链接已被封禁: {}";
    public static final String LOG_SHORT_LINK_EXPIRED = "短链接已过期，清理缓存: {}";
    public static final String LOG_CACHE_PARSE_ERROR = "缓存数据解析异常，降级查库: {}";
    public static final String LOG_CACHE_BACKFILL_FAILED = "缓存回填失败";
    public static final String LOG_CLICK_RECORD_SUCCESS = "点击日志记录成功，短码: {}, IP: {}, 设备: {}";
    public static final String LOG_CLICK_RECORD_FAILED = "记录点击日志失败，短码: {}";

    public static final String LOG_USER_GENERATING_SHORT_LINK = "用户 {} 正在生成短链，原始URL: {}";
    public static final String LOG_SHORT_LINK_ALREADY_EXISTS = "短链已存在，直接返回已有短链: {}";
    public static final String LOG_USER_CUSTOM_CODE = "用户使用自定义短码: {}";
    public static final String LOG_SYSTEM_GENERATE_CODE = "系统自动生成短码: {}";
    public static final String LOG_SHORT_LINK_SAVE_FAILED = "短链保存失败，userId: {}, originalUrl: {}";
    public static final String LOG_SHORT_LINK_GENERATE_SUCCESS = "短链接生成成功: {} -> {}, userId: {}";

    public static final String LOG_SHORT_LINK_UPDATE_SUCCESS = "短链接更新成功，id: {}, userId: {}";
    public static final String LOG_SHORT_LINK_DELETE_SUCCESS = "短链接删除成功，id: {}, shortCode: {}, userId: {}";
    public static final String LOG_SHORT_LINK_BATCH_DELETE_SUCCESS = "批量删除短链接成功，数量: {}, userId: {}";
    public static final String LOG_SHORT_LINK_MOVE_SUCCESS = "短链移动到分组成功，linkId: {}, gid: {}, userId: {}";

    public static final String LOG_RATE_LIMIT_IP_CODE = "限流：同一IP({}) + 同一短码({}) 超过限制，当前次数: {}";
    public static final String LOG_RATE_LIMIT_IP_GLOBAL = "限流：同一IP({}) 全局超过限制，当前次数: {}";

    public static final String LOG_ADMIN_BAN_LINK = "管理员操作短链，linkId: {}, status: {}";
    public static final String LOG_ADMIN_BAN_USER = "管理员操作用户，userId: {}, status: {}";

    public static final String LOG_MAIL_SEND_SUCCESS = "邮件发送成功！已发送至: {}";
    public static final String LOG_MAIL_SEND_FAILED = "邮件发送失败，目标邮箱: {}";
    public static final String LOG_MAIL_CONSUME_SUCCESS = "MQ 消费者接收到发邮件任务，目标邮箱: {}, deliveryTag: {}";

    // ================================================================
    // 9. Redis Key 前缀（集中管理）
    // ================================================================

    public static final String REDIS_KEY_VERIFY_CODE = "verify_code:";
    public static final String REDIS_KEY_VERIFY_CODE_FREQ = "verify_code_freq:";
    public static final String REDIS_KEY_RATE_LIMIT_IP_CODE = "rate_limit:ip_code:";
    public static final String REDIS_KEY_RATE_LIMIT_IP_GLOBAL = "rate_limit:ip_global:";

    // ================================================================
    // 10. 系统保留关键词（管理员/系统路径）
    // ================================================================

    public static final String[] RESERVED_KEYWORDS = {
            "admin", "api", "login", "register", "auth",
            "stats", "link", "user", "manage", "system",
            "www", "mail", "ftp", "localhost"
    };

    // ================================================================
    // 11. 缓存配置常量
    // ================================================================

    public static final int CACHE_EXPIRE_DAYS = 7;
    public static final int SHORT_CODE_LENGTH = 8;
    public static final int SHORT_CODE_MAX_RETRY = 5;

    // ================================================================
    // 12. 限流配置常量
    // ================================================================

    public static final int RATE_LIMIT_IP_CODE_MAX = 30;
    public static final int RATE_LIMIT_IP_GLOBAL_MAX = 120;
    public static final int RATE_LIMIT_TIME_WINDOW_SECONDS = 60;

    // ================================================================
    // 13. 线程池配置常量
    // ================================================================

    public static final int ASYNC_CORE_POOL_SIZE = 5;
    public static final int ASYNC_MAX_POOL_SIZE = 20;
    public static final int ASYNC_QUEUE_CAPACITY = 100;
    public static final int ASYNC_KEEP_ALIVE_SECONDS = 60;
    public static final String ASYNC_THREAD_NAME_PREFIX = "async-task-";
}
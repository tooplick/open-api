// 无依赖的共享常量,放这里避免 store 与 http 之间的循环引用初始化问题。
export const TOKEN_KEY = 'aiopen_token'
export const USER_KEY = 'aiopen_user'

/** 新手教程:首次登录改密后置 1,触发自动引导 */
export const TOUR_PENDING_KEY = 'aiopen_tour_pending'
/** 新手教程:用户完成或关闭教程后置 1,后续登录不再自动触发 */
export const TOUR_COMPLETED_KEY = 'aiopen_tour_completed'

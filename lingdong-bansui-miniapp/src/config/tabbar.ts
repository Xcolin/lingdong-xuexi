/** 各身份端底部导航配置,页签切换用 redirectTo 保持栈浅。 */
export interface TabItem { icon: string; label: string; url: string }

export const STUDENT_TABS: TabItem[] = [
  { icon: '任', label: '任务', url: '/pages/task-list/task-list' },
  { icon: '奖', label: '奖励', url: '/pages/rewards/rewards' },
  { icon: '成', label: '复盘', url: '/pages/growth-reviews/growth-reviews' },
  { icon: '我', label: '我的', url: '/pages/student-home/student-home' }
];

export const PARENT_TABS: TabItem[] = [
  { icon: '首', label: '首页', url: '/pages/parent-home/parent-home' },
  { icon: '任', label: '任务', url: '/pages/family-tasks/family-tasks' },
  { icon: '周', label: '成长', url: '/pages/parent-weekly-reviews/parent-weekly-reviews' },
  { icon: '我', label: '我的', url: '/pages/parent-me/parent-me' }
];

export const TEACHER_TABS: TabItem[] = [
  { icon: '任', label: '任务', url: '/pages/managed-tasks/managed-tasks?identity=teacher' },
  { icon: '报', label: '报备', url: '/pages/exception-reports/exception-reports?identity=teacher' },
  { icon: '勤', label: '考勤', url: '/pages/attendance/attendance?identity=teacher' },
  { icon: '我', label: '我的', url: '/pages/teacher-home/teacher-home' }
];

export const ORG_TABS: TabItem[] = [
  { icon: '首', label: '首页', url: '/pages/organization-home/organization-home' },
  { icon: '任', label: '任务', url: '/pages/managed-tasks/managed-tasks?identity=organization' },
  { icon: '勤', label: '考勤', url: '/pages/attendance/attendance?identity=organization' },
  { icon: '我', label: '我的', url: '/pages/account-security/account-security?identity=organization' }
];

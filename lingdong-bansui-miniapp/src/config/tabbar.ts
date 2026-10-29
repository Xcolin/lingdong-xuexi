/** 各身份端底部导航配置,页签切换用 redirectTo 保持栈浅。 */
export interface TabItem { icon: string; label: string; url: string }

export const STUDENT_TABS: TabItem[] = [
  { icon: '首', label: '首页', url: '/pages/student-dashboard/student-dashboard' },
  { icon: '任', label: '任务', url: '/pages/task-list/task-list' },
  { icon: '成', label: '成长', url: '/pages/growth-reviews/growth-reviews' },
  { icon: '我', label: '我的', url: '/pages/student-home/student-home' }
];

export const PARENT_TABS: TabItem[] = [
  { icon: '首', label: '首页', url: '/pages/parent-home/parent-home' },
  { icon: '任', label: '任务', url: '/pages/family-tasks/family-tasks' },
  { icon: '周', label: '成长', url: '/pages/parent-weekly-reviews/parent-weekly-reviews' },
  { icon: '我', label: '我的', url: '/pages/parent-me/parent-me' }
];

export const TEACHER_TABS: TabItem[] = [
 {icon:'首',label:'首页',url:'/pages/teacher-home/teacher-home'},
 {icon:'任',label:'任务',url:'/pages/managed-tasks/managed-tasks?identity=teacher'},
 {icon:'成',label:'成长',url:'/pages/organization-statistics/organization-statistics?identity=teacher'},
 {icon:'我',label:'我的',url:'/pages/organization-me/organization-me?identity=teacher'}
];

export const ORG_TABS: TabItem[] = [
  { icon: '首', label: '首页', url: '/pages/organization-home/organization-home' },
  { icon: '任', label: '任务', url: '/pages/managed-tasks/managed-tasks?identity=organization' },
  { icon: '成', label: '成长', url: '/pages/organization-statistics/organization-statistics?identity=organization' },
  { icon: '我', label: '我的', url: '/pages/organization-me/organization-me?identity=organization' }
];

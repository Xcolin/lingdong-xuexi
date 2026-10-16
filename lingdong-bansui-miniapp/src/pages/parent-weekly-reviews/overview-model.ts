import type { WeeklySummary, WeeklyDetail } from '@/api/parent-weekly-review';
export function latestWeekly(rows:WeeklySummary[]):WeeklySummary|undefined { return rows.filter(row=>row.periodType==='WEEK').sort((a,b)=>b.periodEnd.localeCompare(a.periodEnd))[0]; }
export function weeklyOverview(report:WeeklyDetail|null):WeeklyDetail|null {return report?.periodType==='WEEK'?report:null;}
export function trendHeight(report:WeeklyDetail|null,count:number):number {return count/Math.max(1,...(report?.dailyTrends.map(day=>day.completedCount)||[]))*100;}

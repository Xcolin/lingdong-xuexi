package com.lingdong.learning.learningtask.web;

/** 复制昨日任务请求；同名确认必须由用户显式选择。 */
public record CopyPreviousDayTasksRequest(boolean confirmDuplicateTitles) {
}

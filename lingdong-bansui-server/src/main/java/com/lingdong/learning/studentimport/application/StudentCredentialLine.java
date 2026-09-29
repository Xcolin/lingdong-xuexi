package com.lingdong.learning.studentimport.application;

/** 初始凭证文件的一行，使用源行号定位原始导入记录。 */
public record StudentCredentialLine(int sourceRowNumber, String studentAccount, String initialLoginCode) { }

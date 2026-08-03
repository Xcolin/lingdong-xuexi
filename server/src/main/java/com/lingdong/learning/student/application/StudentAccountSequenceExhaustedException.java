package com.lingdong.learning.student.application;

/** 某年度学生账号六位流水已全部使用。 */
public class StudentAccountSequenceExhaustedException extends IllegalStateException {
    public StudentAccountSequenceExhaustedException(int sequenceYear) {
        super(sequenceYear + "年度学生账号流水已用尽");
    }
}

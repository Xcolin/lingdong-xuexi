-- 接口服务审批与实际生效分阶段执行，单独记录执行结果，避免执行失败被误认为已生效。
ALTER TABLE sys_interface_service_change
    ADD COLUMN execution_status VARCHAR(16) NOT NULL DEFAULT 'PENDING';

ALTER TABLE sys_interface_service_change
    ADD COLUMN failure_reason VARCHAR(1000) NULL;

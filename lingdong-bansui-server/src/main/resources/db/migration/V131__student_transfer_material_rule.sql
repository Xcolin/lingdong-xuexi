-- Independent business-authorized proof files; no change to task attachment rules.
INSERT INTO sys_attachment_rule(id,module_code,file_category,rule_name,max_file_size_bytes,max_batch_count,preview_enabled,download_scope,status)
VALUES(2201000000000131000,'STUDENT_TRANSFER','MATERIAL','转学申请证明材料',10485760,3,0,'BUSINESS_AUTHORIZED','ENABLED');
INSERT INTO sys_attachment_rule_extension(id,rule_id,extension) VALUES
(2201000000000131001,2201000000000131000,'jpg'),
(2201000000000131002,2201000000000131000,'jpeg'),
(2201000000000131003,2201000000000131000,'png'),
(2201000000000131004,2201000000000131000,'pdf');

-- 管理端权限与组织体系升级（组织侧）：行政区划字段、内置组织类型、行政区划字典与重庆市区县组织初始化。
-- 全部语句幂等：重复执行不产生重复数据，不破坏既有组织节点。

-- 1. 组织节点行政区划字段（逻辑引用字典条目，不加外键）
ALTER TABLE sys_organization ADD COLUMN admin_division_code VARCHAR(32) NULL;

-- 2. 内置组织类型：城市、国家、家庭（编码或名称任一已存在即跳过，避免与既有 HOME=家庭 等同名类型冲突）
INSERT INTO sys_organization_type (id, type_code, type_name, built_in, status, sort_order)
SELECT 1874244142494649001, 'CITY', '城市', 1, 'ENABLED', 60
WHERE NOT EXISTS (SELECT 1 FROM sys_organization_type WHERE type_code = 'CITY')
  AND NOT EXISTS (SELECT 1 FROM sys_organization_type WHERE type_name = '城市');

INSERT INTO sys_organization_type (id, type_code, type_name, built_in, status, sort_order)
SELECT 1874244142494649002, 'COUNTRY', '国家', 1, 'ENABLED', 70
WHERE NOT EXISTS (SELECT 1 FROM sys_organization_type WHERE type_code = 'COUNTRY')
  AND NOT EXISTS (SELECT 1 FROM sys_organization_type WHERE type_name = '国家');

INSERT INTO sys_organization_type (id, type_code, type_name, built_in, status, sort_order)
SELECT 1874244142494649003, 'FAMILY', '家庭', 1, 'ENABLED', 80
WHERE NOT EXISTS (SELECT 1 FROM sys_organization_type WHERE type_code = 'FAMILY')
  AND NOT EXISTS (SELECT 1 FROM sys_organization_type WHERE type_name = '家庭');

-- 3. 行政区划字典类型与重庆市 38 区县条目（编码采用 GB/T 2260 行政区划码）
INSERT INTO sys_dictionary_type (id, type_code, type_name, status, sort_order)
SELECT 1874400000000000100, 'ADMIN_DIVISION', '行政区划', 'ENABLED', 900
WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE type_code = 'ADMIN_DIVISION');

INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000101, t.id, '500101', '万州区', 10, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500101');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000102, t.id, '500102', '涪陵区', 20, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500102');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000103, t.id, '500103', '渝中区', 30, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500103');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000104, t.id, '500104', '大渡口区', 40, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500104');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000105, t.id, '500105', '江北区', 50, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500105');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000106, t.id, '500106', '沙坪坝区', 60, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500106');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000107, t.id, '500107', '九龙坡区', 70, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500107');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000108, t.id, '500108', '南岸区', 80, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500108');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000109, t.id, '500109', '北碚区', 90, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500109');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000110, t.id, '500110', '綦江区', 100, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500110');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000111, t.id, '500111', '大足区', 110, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500111');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000112, t.id, '500112', '渝北区', 120, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500112');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000113, t.id, '500113', '巴南区', 130, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500113');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000114, t.id, '500114', '黔江区', 140, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500114');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000115, t.id, '500115', '长寿区', 150, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500115');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000116, t.id, '500116', '江津区', 160, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500116');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000117, t.id, '500117', '合川区', 170, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500117');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000118, t.id, '500118', '永川区', 180, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500118');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000119, t.id, '500119', '南川区', 190, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500119');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000120, t.id, '500120', '璧山区', 200, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500120');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000121, t.id, '500121', '铜梁区', 210, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500121');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000122, t.id, '500122', '潼南区', 220, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500122');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000123, t.id, '500123', '荣昌区', 230, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500123');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000124, t.id, '500124', '开州区', 240, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500124');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000125, t.id, '500125', '梁平区', 250, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500125');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000126, t.id, '500126', '武隆区', 260, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500126');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000127, t.id, '500229', '城口县', 270, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500229');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000128, t.id, '500230', '丰都县', 280, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500230');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000129, t.id, '500231', '垫江县', 290, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500231');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000130, t.id, '500233', '忠县', 300, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500233');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000131, t.id, '500236', '云阳县', 310, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500236');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000132, t.id, '500237', '奉节县', 320, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500237');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000133, t.id, '500238', '巫山县', 330, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500238');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000134, t.id, '500239', '巫溪县', 340, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500239');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000135, t.id, '500240', '石柱土家族自治县', 350, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500240');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000136, t.id, '500241', '秀山土家族苗族自治县', 360, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500241');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000137, t.id, '500242', '酉阳土家族苗族自治县', 370, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500242');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
SELECT 1874400000000000138, t.id, '500243', '彭水苗族土家族自治县', 380, 0, 'ENABLED'
FROM sys_dictionary_type t
WHERE t.type_code = 'ADMIN_DIVISION'
  AND NOT EXISTS (SELECT 1 FROM sys_dictionary_item i WHERE i.type_id = t.id AND i.item_code = '500243');

-- 4. 组织树初始化：重庆市（CITY）+ 38 区县（REGION），节点编码使用行政区划码
-- 已存在根级“重庆市”节点时复用该节点（不重建、不改编码与层级，仅把类型升级为 CITY），
-- 避免 uk_sys_organization_sibling_name(ROOT, 重庆市) 唯一键冲突。
UPDATE sys_organization
SET organization_type = 'CITY'
WHERE parent_id IS NULL
  AND organization_name = '重庆市'
  AND organization_type <> 'CITY';

INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000001, NULL, 'ROOT', '500000', '重庆市', 'CITY', '500000/', 10, 'ENABLED', 'ENABLED', 1, '500000'
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500000')
  AND NOT EXISTS (SELECT 1 FROM sys_organization WHERE parent_id IS NULL AND organization_name = '重庆市');

INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000002, p.id, CONCAT('PARENT:', p.id), '500101', '万州区', 'REGION', CONCAT(p.organization_path, '500101/'), 10, 'ENABLED', 'ENABLED', 1, '500101'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500101');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000003, p.id, CONCAT('PARENT:', p.id), '500102', '涪陵区', 'REGION', CONCAT(p.organization_path, '500102/'), 20, 'ENABLED', 'ENABLED', 1, '500102'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500102');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000004, p.id, CONCAT('PARENT:', p.id), '500103', '渝中区', 'REGION', CONCAT(p.organization_path, '500103/'), 30, 'ENABLED', 'ENABLED', 1, '500103'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500103');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000005, p.id, CONCAT('PARENT:', p.id), '500104', '大渡口区', 'REGION', CONCAT(p.organization_path, '500104/'), 40, 'ENABLED', 'ENABLED', 1, '500104'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500104');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000006, p.id, CONCAT('PARENT:', p.id), '500105', '江北区', 'REGION', CONCAT(p.organization_path, '500105/'), 50, 'ENABLED', 'ENABLED', 1, '500105'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500105');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000007, p.id, CONCAT('PARENT:', p.id), '500106', '沙坪坝区', 'REGION', CONCAT(p.organization_path, '500106/'), 60, 'ENABLED', 'ENABLED', 1, '500106'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500106');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000008, p.id, CONCAT('PARENT:', p.id), '500107', '九龙坡区', 'REGION', CONCAT(p.organization_path, '500107/'), 70, 'ENABLED', 'ENABLED', 1, '500107'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500107');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000009, p.id, CONCAT('PARENT:', p.id), '500108', '南岸区', 'REGION', CONCAT(p.organization_path, '500108/'), 80, 'ENABLED', 'ENABLED', 1, '500108'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500108');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000010, p.id, CONCAT('PARENT:', p.id), '500109', '北碚区', 'REGION', CONCAT(p.organization_path, '500109/'), 90, 'ENABLED', 'ENABLED', 1, '500109'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500109');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000011, p.id, CONCAT('PARENT:', p.id), '500110', '綦江区', 'REGION', CONCAT(p.organization_path, '500110/'), 100, 'ENABLED', 'ENABLED', 1, '500110'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500110');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000012, p.id, CONCAT('PARENT:', p.id), '500111', '大足区', 'REGION', CONCAT(p.organization_path, '500111/'), 110, 'ENABLED', 'ENABLED', 1, '500111'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500111');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000013, p.id, CONCAT('PARENT:', p.id), '500112', '渝北区', 'REGION', CONCAT(p.organization_path, '500112/'), 120, 'ENABLED', 'ENABLED', 1, '500112'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500112');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000014, p.id, CONCAT('PARENT:', p.id), '500113', '巴南区', 'REGION', CONCAT(p.organization_path, '500113/'), 130, 'ENABLED', 'ENABLED', 1, '500113'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500113');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000015, p.id, CONCAT('PARENT:', p.id), '500114', '黔江区', 'REGION', CONCAT(p.organization_path, '500114/'), 140, 'ENABLED', 'ENABLED', 1, '500114'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500114');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000016, p.id, CONCAT('PARENT:', p.id), '500115', '长寿区', 'REGION', CONCAT(p.organization_path, '500115/'), 150, 'ENABLED', 'ENABLED', 1, '500115'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500115');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000017, p.id, CONCAT('PARENT:', p.id), '500116', '江津区', 'REGION', CONCAT(p.organization_path, '500116/'), 160, 'ENABLED', 'ENABLED', 1, '500116'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500116');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000018, p.id, CONCAT('PARENT:', p.id), '500117', '合川区', 'REGION', CONCAT(p.organization_path, '500117/'), 170, 'ENABLED', 'ENABLED', 1, '500117'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500117');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000019, p.id, CONCAT('PARENT:', p.id), '500118', '永川区', 'REGION', CONCAT(p.organization_path, '500118/'), 180, 'ENABLED', 'ENABLED', 1, '500118'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500118');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000020, p.id, CONCAT('PARENT:', p.id), '500119', '南川区', 'REGION', CONCAT(p.organization_path, '500119/'), 190, 'ENABLED', 'ENABLED', 1, '500119'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500119');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000021, p.id, CONCAT('PARENT:', p.id), '500120', '璧山区', 'REGION', CONCAT(p.organization_path, '500120/'), 200, 'ENABLED', 'ENABLED', 1, '500120'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500120');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000022, p.id, CONCAT('PARENT:', p.id), '500121', '铜梁区', 'REGION', CONCAT(p.organization_path, '500121/'), 210, 'ENABLED', 'ENABLED', 1, '500121'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500121');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000023, p.id, CONCAT('PARENT:', p.id), '500122', '潼南区', 'REGION', CONCAT(p.organization_path, '500122/'), 220, 'ENABLED', 'ENABLED', 1, '500122'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500122');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000024, p.id, CONCAT('PARENT:', p.id), '500123', '荣昌区', 'REGION', CONCAT(p.organization_path, '500123/'), 230, 'ENABLED', 'ENABLED', 1, '500123'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500123');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000025, p.id, CONCAT('PARENT:', p.id), '500124', '开州区', 'REGION', CONCAT(p.organization_path, '500124/'), 240, 'ENABLED', 'ENABLED', 1, '500124'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500124');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000026, p.id, CONCAT('PARENT:', p.id), '500125', '梁平区', 'REGION', CONCAT(p.organization_path, '500125/'), 250, 'ENABLED', 'ENABLED', 1, '500125'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500125');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000027, p.id, CONCAT('PARENT:', p.id), '500126', '武隆区', 'REGION', CONCAT(p.organization_path, '500126/'), 260, 'ENABLED', 'ENABLED', 1, '500126'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500126');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000028, p.id, CONCAT('PARENT:', p.id), '500229', '城口县', 'REGION', CONCAT(p.organization_path, '500229/'), 270, 'ENABLED', 'ENABLED', 1, '500229'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500229');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000029, p.id, CONCAT('PARENT:', p.id), '500230', '丰都县', 'REGION', CONCAT(p.organization_path, '500230/'), 280, 'ENABLED', 'ENABLED', 1, '500230'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500230');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000030, p.id, CONCAT('PARENT:', p.id), '500231', '垫江县', 'REGION', CONCAT(p.organization_path, '500231/'), 290, 'ENABLED', 'ENABLED', 1, '500231'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500231');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000031, p.id, CONCAT('PARENT:', p.id), '500233', '忠县', 'REGION', CONCAT(p.organization_path, '500233/'), 300, 'ENABLED', 'ENABLED', 1, '500233'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500233');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000032, p.id, CONCAT('PARENT:', p.id), '500236', '云阳县', 'REGION', CONCAT(p.organization_path, '500236/'), 310, 'ENABLED', 'ENABLED', 1, '500236'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500236');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000033, p.id, CONCAT('PARENT:', p.id), '500237', '奉节县', 'REGION', CONCAT(p.organization_path, '500237/'), 320, 'ENABLED', 'ENABLED', 1, '500237'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500237');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000034, p.id, CONCAT('PARENT:', p.id), '500238', '巫山县', 'REGION', CONCAT(p.organization_path, '500238/'), 330, 'ENABLED', 'ENABLED', 1, '500238'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500238');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000035, p.id, CONCAT('PARENT:', p.id), '500239', '巫溪县', 'REGION', CONCAT(p.organization_path, '500239/'), 340, 'ENABLED', 'ENABLED', 1, '500239'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500239');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000036, p.id, CONCAT('PARENT:', p.id), '500240', '石柱土家族自治县', 'REGION', CONCAT(p.organization_path, '500240/'), 350, 'ENABLED', 'ENABLED', 1, '500240'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500240');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000037, p.id, CONCAT('PARENT:', p.id), '500241', '秀山土家族苗族自治县', 'REGION', CONCAT(p.organization_path, '500241/'), 360, 'ENABLED', 'ENABLED', 1, '500241'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500241');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000038, p.id, CONCAT('PARENT:', p.id), '500242', '酉阳土家族苗族自治县', 'REGION', CONCAT(p.organization_path, '500242/'), 370, 'ENABLED', 'ENABLED', 1, '500242'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500242');
INSERT INTO sys_organization (id, parent_id, parent_scope_key, organization_code, organization_name, organization_type, organization_path, sort_order, status, effective_status, version_no, admin_division_code)
SELECT 1874400000000000039, p.id, CONCAT('PARENT:', p.id), '500243', '彭水苗族土家族自治县', 'REGION', CONCAT(p.organization_path, '500243/'), 380, 'ENABLED', 'ENABLED', 1, '500243'
FROM (SELECT id, organization_path FROM sys_organization
      WHERE parent_id IS NULL
        AND (organization_code = '500000' OR organization_name = '重庆市')
      ORDER BY CASE WHEN organization_code = '500000' THEN 0 ELSE 1 END, id
      LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_organization WHERE organization_code = '500243');

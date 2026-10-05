import { Alert, Drawer, Form, Input, InputNumber, Select, Space } from 'antd';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import type { MenuButtonInput } from '../../api/menus';

interface Props { pages: {id:string;name:string}[]; existingCodes:string[]; initialPageId?:string; busy:boolean; error?:string; onSubmit:(pageId:string,buttons:MenuButtonInput[])=>Promise<boolean|undefined>; onClose:()=>void }
export function BatchMenuButtons({pages,existingCodes,initialPageId,busy,error,onSubmit,onClose}:Props) {
  const [form]=Form.useForm<{pageId:string;buttons:MenuButtonInput[]}>();
  return <Drawer open title="批量新增按钮" width="min(720px, 96vw)" closable={!busy} maskClosable={!busy} keyboard={!busy} onClose={onClose}
    extra={<Button actionKey="menu-management.batch-buttons.save" aria-label="批量保存" type="primary" loading={busy} disabled={busy} onClick={()=>form.submit()}>批量保存</Button>}>
    {error && <Alert type="error" message={error} style={{marginBottom:16}}/>}
    <Form form={form} layout="vertical" disabled={busy} requiredMark={false} initialValues={{pageId:initialPageId,buttons:[{sortOrder:10}]}}
      onFinish={async values=>{if(busy)return; const saved=await onSubmit(values.pageId,values.buttons.map(b=>({code:b.code.trim(),name:b.name.trim(),sortOrder:b.sortOrder,icon:null,grantable:true,status:'ENABLED'}))); if(saved)onClose();}}>
      <Form.Item label="所属页面" name="pageId" rules={[{required:true,message:'请选择所属页面'}]}><Select showSearch optionFilterProp="label" options={pages.map(p=>({value:p.id,label:p.name}))}/></Form.Item>
      <Form.List name="buttons">{(fields,{add,remove})=><>
        {fields.map((field,index)=><div className="menu-button-batch-row" key={field.key}>
          <Form.Item name={[field.name,'name']} label="按钮名称" rules={[{required:true,whitespace:true,message:'请输入按钮名称'}]}><Input aria-label={`按钮名称${index+1}`} maxLength={128}/></Form.Item>
          <Form.Item name={[field.name,'code']} label="权限编码" rules={[{required:true,message:'请输入权限编码'},{pattern:/^[A-Z][A-Z0-9_]{2,127}$/,message:'使用大写字母、数字、下划线，至少3位'},
            {validator:(_,value)=>{const codes=(form.getFieldValue('buttons')??[]).map((b:MenuButtonInput)=>b.code?.trim()); return value && (existingCodes.includes(value.trim()) || codes.filter((c:string)=>c===value.trim()).length>1)?Promise.reject(new Error('编码已存在或在本批次重复')):Promise.resolve();}}]}><Input aria-label={`权限编码${index+1}`} maxLength={128}/></Form.Item>
          <Form.Item name={[field.name,'sortOrder']} label="排序号" rules={[{required:true,message:'请输入排序号'}]}><InputNumber aria-label={`排序号${index+1}`} min={0} max={1000000} precision={0} style={{width:'100%'}}/></Form.Item>
          <Button actionKey="menu-management.batch-buttons.remove" aria-label={`删除第${index+1}行`} disabled={busy||fields.length===1} danger onClick={()=>remove(field.name)}>删除</Button>
        </div>)}
        <Space><Button actionKey="menu-management.batch-buttons.add" aria-label="添加一行" disabled={busy||fields.length>=100} onClick={()=>add({sortOrder:(fields.length+1)*10})}>添加一行</Button></Space>
      </>}</Form.List>
    </Form>
  </Drawer>;
}

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { BatchMenuButtons } from './BatchMenuButtons';
it('一次提交多行按钮并派生权限标记，失败保留输入', async () => {
  const submit=vi.fn().mockResolvedValue(false), close=vi.fn();
  render(<BatchMenuButtons pages={[{id:'p',name:'用户'}]} existingCodes={[]} initialPageId="p" busy={false} error="编码冲突" onSubmit={submit} onClose={close}/>);
  fireEvent.change(screen.getByLabelText('按钮名称1'),{target:{value:'新增'}});
  fireEvent.change(screen.getByLabelText('权限编码1'),{target:{value:'USER_ADD'}});
  fireEvent.click(screen.getByRole('button',{name:'添加一行'}));
  fireEvent.change(screen.getByLabelText('按钮名称2'),{target:{value:'删除'}});
  fireEvent.change(screen.getByLabelText('权限编码2'),{target:{value:'USER_DELETE'}});
  fireEvent.click(screen.getByRole('button',{name:'批量保存'}));
  await waitFor(()=>expect(submit).toHaveBeenCalledWith('p',[
    {name:'新增',code:'USER_ADD',grantable:true,icon:null,sortOrder:10,status:'ENABLED'},
    {name:'删除',code:'USER_DELETE',grantable:true,icon:null,sortOrder:20,status:'ENABLED'}
  ]));
  expect(close).not.toHaveBeenCalled(); expect(screen.getByLabelText('权限编码1')).toHaveValue('USER_ADD');
});
it('拒绝重复编码且不提交', async () => {
  const submit=vi.fn(); render(<BatchMenuButtons pages={[{id:'p',name:'用户'}]} existingCodes={['USER_ADD']} initialPageId="p" busy={false} onSubmit={submit} onClose={vi.fn()}/>);
  fireEvent.change(screen.getByLabelText('按钮名称1'),{target:{value:'新增'}}); fireEvent.change(screen.getByLabelText('权限编码1'),{target:{value:'USER_ADD'}}); fireEvent.click(screen.getByRole('button',{name:'批量保存'}));
  expect(await screen.findByText('编码已存在或在本批次重复')).toBeInTheDocument(); expect(submit).not.toHaveBeenCalled();
});

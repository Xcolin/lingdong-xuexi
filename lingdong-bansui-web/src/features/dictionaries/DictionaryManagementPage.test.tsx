import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { dictionaryApi } from '../../api/dictionaries';
import { DictionaryManagementPage } from './DictionaryManagementPage';

vi.mock('../../api/dictionaries', () => ({
  dictionaryApi: {
    listTypes: vi.fn(),
    listItems: vi.fn(),
    createType: vi.fn(),
    updateType: vi.fn(),
    createItem: vi.fn(),
    updateItem: vi.fn()
  }
}));

const types = [
  { id: '1874244142494647001', code: 'LEARNING_SCENE', name: '学习场景', status: 'ENABLED', sortOrder: 10 },
  { id: '1874244142494647002', code: 'TASK_STATUS', name: '任务状态', status: 'DISABLED', sortOrder: 20 }
] as const;

function renderPage(canManage = true) {
  return render(
    <ConfigProvider locale={zhCN}>
      <DictionaryManagementPage canManage={canManage} />
    </ConfigProvider>
  );
}

describe('数据字典管理页面', () => {
  beforeEach(() => {
    vi.mocked(dictionaryApi.listTypes).mockResolvedValue([...types]);
    vi.mocked(dictionaryApi.listItems).mockResolvedValue([
      {
        id: '1874244142494647101',
        typeId: types[0].id,
        code: 'HOME',
        name: '家庭',
        sortOrder: 10,
        defaultItem: true,
        status: 'ENABLED'
      }
    ]);
    vi.mocked(dictionaryApi.createType).mockResolvedValue(types[0]);
    vi.mocked(dictionaryApi.createItem).mockResolvedValue({
      id: '1874244142494647102',
      typeId: types[0].id,
      code: 'SCHOOL',
      name: '学校',
      sortOrder: 20,
      defaultItem: false,
      status: 'ENABLED'
    });
    vi.mocked(dictionaryApi.updateType).mockResolvedValue({
      ...types[0],
      name: '学习场景分类',
      status: 'DISABLED'
    });
    vi.mocked(dictionaryApi.updateItem).mockResolvedValue({
      id: '1874244142494647101',
      typeId: types[0].id,
      code: 'HOME',
      name: '居家',
      sortOrder: 10,
      defaultItem: false,
      status: 'ENABLED'
    });
  }, 180_000);

  it('加载类型并展示所选类型的全部字典项', async () => {
    renderPage();

    expect(await screen.findByText('学习场景')).toBeInTheDocument();
    expect(await screen.findByText('家庭')).toBeInTheDocument();
    expect(screen.getByText('任务状态')).toBeInTheDocument();
    expect(dictionaryApi.listItems).toHaveBeenCalledWith(types[0].id);
  });

  it('只有查询权限时隐藏全部新增和编辑操作', async () => {
    renderPage(false);

    expect(await screen.findByText('学习场景')).toBeInTheDocument();
    expect(await screen.findByText('家庭')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增类型' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增字典项' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑字典类型-学习场景' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑字典项-家庭' })).not.toBeInTheDocument();
  });

  it('创建字典类型和当前类型下的字典项', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('学习场景');

    await user.click(screen.getByRole('button', { name: '新增类型' }));
    const typeDialog = screen.getByRole('dialog', { name: '新增字典类型' });
    await user.type(within(typeDialog).getByLabelText('类型编码'), 'LEARNING_SCENE');
    await user.type(within(typeDialog).getByLabelText('类型名称'), '学习场景');
    await user.click(within(typeDialog).getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(dictionaryApi.createType).toHaveBeenCalledWith({
      code: 'LEARNING_SCENE',
      name: '学习场景',
      sortOrder: 0
    }));

    await user.click(screen.getByRole('button', { name: '新增字典项' }));
    const itemDialog = screen.getByRole('dialog', { name: '新增字典项' });
    await user.type(within(itemDialog).getByLabelText('字典项编码'), 'SCHOOL');
    await user.type(within(itemDialog).getByLabelText('字典项名称'), '学校');
    await user.click(within(itemDialog).getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(dictionaryApi.createItem).toHaveBeenCalledWith(types[0].id, {
      code: 'SCHOOL',
      name: '学校',
      sortOrder: 0,
      defaultItem: false
    }));
  }, 180_000);

  it('非法编码在提交前被拦截并提示格式规则', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('学习场景');

    await user.click(screen.getByRole('button', { name: '新增字典项' }));
    const itemDialog = screen.getByRole('dialog', { name: '新增字典项' });
    await user.type(within(itemDialog).getByLabelText('字典项编码'), '类型-1');
    await user.type(within(itemDialog).getByLabelText('字典项名称'), '学校');
    await user.click(within(itemDialog).getByRole('button', { name: /保\s*存/ }));

    expect(await screen.findByText('编码仅允许3至64位字母、数字和下划线')).toBeInTheDocument();
    expect(dictionaryApi.createItem).not.toHaveBeenCalled();
  }, 180_000);

  it('数字开头的字典项编码可正常提交', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('学习场景');

    await user.click(screen.getByRole('button', { name: '新增字典项' }));
    const itemDialog = screen.getByRole('dialog', { name: '新增字典项' });
    await user.type(within(itemDialog).getByLabelText('字典项编码'), '1ST_PRIZE');
    await user.type(within(itemDialog).getByLabelText('字典项名称'), '一等奖');
    await user.click(within(itemDialog).getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(dictionaryApi.createItem).toHaveBeenCalledWith(types[0].id, {
      code: '1ST_PRIZE',
      name: '一等奖',
      sortOrder: 0,
      defaultItem: false
    }));
  }, 180_000);

  it('修改字典类型的名称、排序和状态', async () => {
    renderPage();
    await screen.findByText('学习场景');

    fireEvent.click(screen.getByRole('button', { name: '编辑字典类型-学习场景' }));
    const typeDialog = screen.getByRole('dialog', { name: '修改字典类型' });
    const typeName = within(typeDialog).getByLabelText('类型名称');
    fireEvent.change(typeName, { target: { value: '学习场景分类' } });
    fireEvent.mouseDown(within(typeDialog).getByLabelText('状态'));
    fireEvent.click(await screen.findByText('停用', { selector: '.ant-select-item-option-content' }));
    expect(within(typeDialog).getByText('停用', { selector: '.ant-select-selection-item' })).toBeInTheDocument();
    fireEvent.click(within(typeDialog).getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(dictionaryApi.updateType).toHaveBeenCalledWith(types[0].id, {
      name: '学习场景分类',
      sortOrder: 10,
      status: 'DISABLED'
    }));
  }, 180_000);

  it('修改字典项的名称、排序、状态和默认标记', async () => {
    renderPage();
    await screen.findByText('家庭');
    fireEvent.click(screen.getByRole('button', { name: '编辑字典项-家庭' }));
    const itemDialog = screen.getByRole('dialog', { name: '修改字典项' });
    const itemName = within(itemDialog).getByLabelText('字典项名称');
    fireEvent.change(itemName, { target: { value: '居家' } });
    fireEvent.click(within(itemDialog).getByLabelText('设为默认项'));
    fireEvent.click(within(itemDialog).getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(dictionaryApi.updateItem).toHaveBeenCalledWith('1874244142494647101', {
      name: '居家',
      sortOrder: 10,
      status: 'ENABLED',
      defaultItem: false
    }));
  }, 180_000);
});

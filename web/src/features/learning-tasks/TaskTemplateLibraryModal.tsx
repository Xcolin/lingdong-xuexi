import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Button, Empty, List, Modal, Space, Tabs, Tag, Tooltip, message } from 'antd';
import { ArrowDown, ArrowUp, Check, Edit3, Plus, Trash2 } from 'lucide-react';
import { taskTemplateApi } from './taskTemplateApi';
import { TaskTemplateEditorModal } from './TaskTemplateEditorModal';
import type { LearningTaskTemplate } from './types';

interface TaskTemplateLibraryModalProps {
  open: boolean;
  onClose: () => void;
  onSelect: (template: LearningTaskTemplate) => void;
}

export function TaskTemplateLibraryModal({ open, onClose, onSelect }: TaskTemplateLibraryModalProps) {
  const [templates, setTemplates] = useState<LearningTaskTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<LearningTaskTemplate | null>(null);
  const systemTemplates = useMemo(
    () => templates.filter((item) => item.templateScope === 'SYSTEM'),
    [templates]
  );
  const personalTemplates = useMemo(
    () => templates.filter((item) => item.templateScope === 'PERSONAL'),
    [templates]
  );

  useEffect(() => {
    if (open) void loadTemplates();
  }, [open]);

  async function loadTemplates(): Promise<void> {
    setLoading(true);
    try {
      setTemplates(await taskTemplateApi.list());
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setLoading(false);
    }
  }

  function confirmDelete(template: LearningTaskTemplate): void {
    Modal.confirm({
      title: '确认删除个人模板',
      content: `删除“${template.templateName}”后，不影响已经创建的学习任务。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await taskTemplateApi.remove(template.id, template.versionNo);
          message.success('个人模板已删除');
          await loadTemplates();
        } catch (error) {
          message.error(toMessage(error));
          throw error;
        }
      }
    });
  }

  async function move(templateIndex: number, direction: -1 | 1): Promise<void> {
    const targetIndex = templateIndex + direction;
    if (targetIndex < 0 || targetIndex >= personalTemplates.length) return;
    const ordered = [...personalTemplates];
    [ordered[templateIndex], ordered[targetIndex]] = [ordered[targetIndex], ordered[templateIndex]];
    try {
      setTemplates(await taskTemplateApi.reorder(ordered.map((item) => ({
        templateId: item.id,
        versionNo: item.versionNo
      }))));
      message.success('个人模板顺序已更新');
    } catch (error) {
      message.error(toMessage(error));
    }
  }

  function renderList(items: LearningTaskTemplate[], personal: boolean) {
    if (!loading && items.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={personal ? '暂无个人模板' : '暂无系统模板'} />;
    }
    return (
      <List
        loading={loading}
        dataSource={items}
        renderItem={(template, index) => (
          <List.Item actions={[
            <TemplateAction
              key="select"
              label={`选用 ${template.templateName}`}
              title="选用模板"
              icon={<Check size={16} />}
              onClick={() => onSelect(template)}
            />,
            ...(personal ? [
              <TemplateAction
                key="edit"
                label={`编辑 ${template.templateName}`}
                title="编辑个人模板"
                icon={<Edit3 size={16} />}
                onClick={() => {
                  setEditingTemplate(template);
                  setEditorOpen(true);
                }}
              />,
              <TemplateAction
                key="up"
                label={`上移 ${template.templateName}`}
                title="上移"
                icon={<ArrowUp size={16} />}
                disabled={index === 0}
                onClick={() => void move(index, -1)}
              />,
              <TemplateAction
                key="down"
                label={`下移 ${template.templateName}`}
                title="下移"
                icon={<ArrowDown size={16} />}
                disabled={index === items.length - 1}
                onClick={() => void move(index, 1)}
              />,
              <TemplateAction
                key="delete"
                label={`删除 ${template.templateName}`}
                title="删除个人模板"
                icon={<Trash2 size={16} />}
                danger
                onClick={() => confirmDelete(template)}
              />
            ] : [])
          ]}>
            <List.Item.Meta
              title={<Space wrap><span>{template.templateName}</span><Tag>{template.difficultyLevel} 级</Tag><Tag>{template.durationMinutes} 分钟</Tag></Space>}
              description={
                <div className="task-template-description">
                  <strong>{template.taskTitle}</strong>
                  <span>{[template.categoryCode, ...template.tagCodes].filter(Boolean).join(' · ') || '未设置分类和标签'}</span>
                  {template.remark && <span>{template.remark}</span>}
                </div>
              }
            />
          </List.Item>
        )}
      />
    );
  }

  return (
    <>
      <Modal
        title="任务模板"
        open={open}
        onCancel={onClose}
        footer={<Button onClick={onClose}>关闭</Button>}
        width={760}
        destroyOnHidden
      >
        <Tabs items={[
          { key: 'SYSTEM', label: `系统模板（${systemTemplates.length}）`, children: renderList(systemTemplates, false) },
          {
            key: 'PERSONAL',
            label: `个人模板（${personalTemplates.length}）`,
            children: (
              <div className="task-template-personal-pane">
                <div className="task-template-toolbar">
                  <Button
                    type="primary"
                    icon={<Plus size={16} />}
                    onClick={() => {
                      setEditingTemplate(null);
                      setEditorOpen(true);
                    }}
                  >新建个人模板</Button>
                </div>
                {renderList(personalTemplates, true)}
              </div>
            )
          }
        ]} />
      </Modal>
      <TaskTemplateEditorModal
        open={editorOpen}
        template={editingTemplate}
        onClose={() => setEditorOpen(false)}
        onSaved={loadTemplates}
      />
    </>
  );
}

function TemplateAction({
  label,
  title,
  icon,
  danger = false,
  disabled = false,
  onClick
}: {
  label: string;
  title: string;
  icon: ReactNode;
  danger?: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <Tooltip title={title}>
      <Button
        type="text"
        aria-label={label}
        icon={icon}
        danger={danger}
        disabled={disabled}
        onClick={onClick}
      />
    </Tooltip>
  );
}

function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成';
}

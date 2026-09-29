import { useEffect, useState } from 'react';
import { Alert, Button, Spin, Steps, Typography } from 'antd';
import { ArrowRight, Check } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../api/auth';

const steps = [
  { title: '欢迎使用', description: '建立清晰、稳定的家庭学习节奏。' },
  { title: '任务模式', description: '家长可以独立创建任务，也可以管理机构关联学生的家庭任务。' },
  { title: '产品理念', description: '用明确目标、及时反馈和正向激励支持学生持续成长。' },
  { title: '专注模式', description: '按学生实际情况安排专注时段，减少无关干扰。' }
];

/** 家长首次登录的独立四步引导，完成前不进入普通管理页面。 */
export function ParentOnboardingPage() {
  const navigate = useNavigate();
  const [current, setCurrent] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [parentState, setParentState] = useState<Awaited<ReturnType<typeof authApi.parentState>> | null>(null);

  useEffect(() => {
    if (!authApi.hasLocalSession()) {
      navigate('/login', { replace: true });
      return;
    }
    void authApi.parentState()
      .then((state) => {
        if (!state.onboardingRequired && !state.agreementAcceptanceRequired) {
          navigate('/dashboard', { replace: true });
          return;
        }
        setParentState(state);
      })
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : '家长状态加载失败'));
  }, [navigate]);

  async function acceptAgreement(): Promise<void> {
    if (!parentState) return;
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await authApi.acceptParentAgreement(parentState.currentAgreementVersion);
      if (!parentState.onboardingRequired) {
        navigate('/dashboard', { replace: true });
        return;
      }
      setParentState({ ...parentState, agreementAcceptanceRequired: false });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '协议接受状态保存失败');
    } finally {
      setSubmitting(false);
    }
  }

  async function complete(): Promise<void> {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await authApi.completeParentOnboarding();
      navigate('/dashboard', { replace: true });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '引导状态保存失败');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="onboarding-page">
      <section className="onboarding-surface" aria-label="家长首次使用引导">
        {!parentState ? <div className="route-loading"><Spin /></div> : parentState.agreementAcceptanceRequired ? <>
          <Typography.Title level={2}>用户协议更新</Typography.Title>
          {errorMessage && <Alert type="error" showIcon message={errorMessage} />}
          <div className="onboarding-content">
            <Typography.Title level={4}>当前协议版本</Typography.Title>
            <Typography.Paragraph>{parentState.currentAgreementVersion}</Typography.Paragraph>
            <Typography.Paragraph>请确认接受当前用户协议后继续使用家长端。</Typography.Paragraph>
          </div>
          <div className="onboarding-actions">
            <Button type="primary" icon={<Check size={17} />} loading={submitting} onClick={() => void acceptAgreement()}>
              同意并继续
            </Button>
          </div>
        </> : <>
        <Typography.Title level={2}>家长首次引导</Typography.Title>
        <Steps current={current} responsive items={steps.map(({ title }) => ({ title }))} />
        {errorMessage && <Alert type="error" showIcon message={errorMessage} />}
        <div className="onboarding-content">
          <Typography.Title level={4}>{steps[current].title}</Typography.Title>
          <Typography.Paragraph>{steps[current].description}</Typography.Paragraph>
        </div>
        <div className="onboarding-actions">
          {current > 0 && <Button onClick={() => setCurrent((value) => value - 1)}>上一步</Button>}
          {current < steps.length - 1
            ? <Button type="primary" icon={<ArrowRight size={17} />} onClick={() => setCurrent((value) => value + 1)}>下一步</Button>
            : <Button type="primary" icon={<Check size={17} />} loading={submitting} onClick={() => void complete()}>完成引导</Button>}
        </div>
        </>}
      </section>
    </main>
  );
}

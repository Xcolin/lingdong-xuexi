import { lazy, Suspense, useEffect, useState } from 'react';
import { Avatar, Button, ConfigProvider, Dropdown, Layout, Menu, Spin } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AppWindow, BookOpenCheck, Building2, ChevronDown, ClipboardList, Coins, Gift, KeyRound, LayoutDashboard, LogOut, QrCode, ShieldCheck, UsersRound } from 'lucide-react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { authApi, type CurrentUser } from '../api/auth';
import { capabilityApi, type ClientCapabilities } from '../api/capability';
import { LoginPage } from '../features/auth/LoginPage';

const DashboardPage = lazy(async () => ({ default: (await import('../features/dashboard/DashboardPage')).DashboardPage }));
const IamManagementPage = lazy(async () => ({ default: (await import('../features/iam/IamManagementPage')).IamManagementPage }));
const OrganizationManagementPage = lazy(async () => ({ default: (await import('../features/organizations/OrganizationManagementPage')).OrganizationManagementPage }));
const UserManagementPage = lazy(async () => ({ default: (await import('../features/users/UserManagementPage')).UserManagementPage }));
const LearningTaskManagementPage = lazy(async () => ({ default: (await import('../features/learning-tasks/LearningTaskManagementPage')).LearningTaskManagementPage }));
const GrowthPointPage = lazy(async () => ({ default: (await import('../features/growth-points/GrowthPointPage')).GrowthPointPage }));
const RewardManagementPage = lazy(async () => ({ default: (await import('../features/rewards/RewardManagementPage')).RewardManagementPage }));
const GrowthReviewPage = lazy(async () => ({ default: (await import('../features/growth-reviews/GrowthReviewPage')).GrowthReviewPage }));
const StudentLoginManagementPage = lazy(async () => ({ default: (await import('../features/student-login/StudentLoginManagementPage')).StudentLoginManagementPage }));

const { Header, Sider, Content } = Layout;

export function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/*" element={<ProtectedManagementApp />} />
      </Routes>
    </ConfigProvider>
  );
}

function ProtectedManagementApp() {
  const navigate = useNavigate();
  const location = useLocation();
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [capabilities, setCapabilities] = useState<ClientCapabilities | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!authApi.hasLocalSession()) {
      navigate('/login', { replace: true });
      return;
    }
    void Promise.all([authApi.currentUser(), capabilityApi.web()])
      .then(([user, loadedCapabilities]) => {
        setCurrentUser(user);
        setCapabilities(loadedCapabilities);
      })
      .catch(() => {
        authApi.clearLocalSession();
        navigate('/login', { replace: true });
      })
      .finally(() => setLoading(false));
  }, [navigate]);

  function endSession(): void {
    authApi.clearLocalSession();
    navigate('/login', { replace: true });
  }

  if (loading || !currentUser || !capabilities) {
    return <div className="app-loading"><Spin size="large" /></div>;
  }

  const learningTasksAvailable = canAccessLearningTasks(currentUser, capabilities);
  const growthPointsAvailable = canAccessGrowthPoints(currentUser, capabilities);
  const rewardsAvailable = canAccessRewards(currentUser, capabilities);
  const growthReviewsAvailable = canAccessGrowthReviews(currentUser, capabilities);
  const studentQrLoginAvailable = canAccessStudentQrLogin(currentUser, capabilities);
  const selectedKey = location.pathname.startsWith('/student-login') ? 'student-login'
    : location.pathname.startsWith('/growth-reviews') ? 'growth-reviews'
    : location.pathname.startsWith('/rewards') ? 'rewards'
    : location.pathname.startsWith('/growth-points') ? 'growth-points'
    : location.pathname.startsWith('/learning-tasks') ? 'learning-tasks'
    : location.pathname.startsWith('/users') ? 'users'
    : location.pathname.startsWith('/organizations') ? 'organizations'
    : location.pathname.startsWith('/iam') ? 'iam' : 'dashboard';

  return (
    <Layout className="app-shell">
      <Sider width={232} breakpoint="lg" collapsedWidth={64} className="app-sider">
        <div className="brand-lockup"><AppWindow size={22} aria-hidden="true" /><span>灵动学习</span></div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={[selectedKey]}
          onClick={({ key }) => navigate(key === 'dashboard' ? '/dashboard' : `/${key}`)}
          items={[
            { key: 'dashboard', icon: <LayoutDashboard size={18} />, label: '工作台' },
            learningTasksAvailable
              ? { key: 'learning-tasks', icon: <ClipboardList size={18} />, label: '学习任务' }
              : null,
            growthPointsAvailable
              ? { key: 'growth-points', icon: <Coins size={18} />, label: '积分台账' }
              : null,
            rewardsAvailable
              ? { key: 'rewards', icon: <Gift size={18} />, label: '奖励管理' }
              : null,
            growthReviewsAvailable
              ? { key: 'growth-reviews', icon: <BookOpenCheck size={18} />, label: '成长复盘' }
              : null,
            studentQrLoginAvailable
              ? { key: 'student-login', icon: <QrCode size={18} />, label: '学生登录' }
              : null,
            { key: 'users', icon: <UsersRound size={18} />, label: '用户管理' },
            { key: 'iam', icon: <ShieldCheck size={18} />, label: '角色与权限' },
            { key: 'organizations', icon: <Building2 size={18} />, label: '组织管理' }
          ]}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <span className="header-context">管理端</span>
          <Dropdown menu={{ items: [{ key: 'logout', icon: <LogOut size={16} />, label: '退出登录', onClick: endSession }] }} trigger={['click']}>
            <Button type="text" className="user-menu"><Avatar size="small" icon={<KeyRound size={14} />} />{currentUser.displayName}<ChevronDown size={16} /></Button>
          </Dropdown>
        </Header>
        <Content className="app-content">
          <Suspense fallback={<div className="route-loading"><Spin /></div>}>
            <Routes>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<DashboardPage currentUser={currentUser} onSessionEnded={endSession} />} />
              <Route path="/users" element={<UserManagementPage />} />
              <Route path="/iam" element={<IamManagementPage />} />
              <Route path="/organizations" element={<OrganizationManagementPage currentUser={currentUser} />} />
              <Route
                path="/learning-tasks"
                element={learningTasksAvailable
                  ? <LearningTaskManagementPage
                      currentUser={currentUser}
                      previousDayTaskCopyEnabled={capabilities.previousDayTaskCopyEnabled}
                      learningTaskTemplateEnabled={capabilities.learningTaskTemplateEnabled}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/growth-points"
                element={growthPointsAvailable
                  ? <GrowthPointPage correctionEnabled={capabilities.growthPointCorrectionEnabled} />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/rewards"
                element={rewardsAvailable
                  ? <RewardManagementPage />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/growth-reviews"
                element={growthReviewsAvailable
                  ? <GrowthReviewPage />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/student-login"
                element={studentQrLoginAvailable
                  ? <StudentLoginManagementPage />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  );
}

export function canAccessLearningTasks(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.learningTaskManagementEnabled
    && currentUser.roleCodes.some((role) => ['PARENT', 'ORG_ADMIN', 'TEACHER'].includes(role));
}

export function canAccessGrowthPoints(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.growthPointQueryEnabled && currentUser.roleCodes.includes('PARENT');
}

export function canAccessRewards(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.rewardExchangeEnabled && currentUser.roleCodes.includes('PARENT');
}

export function canAccessGrowthReviews(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return (capabilities.dailyGrowthReviewEnabled || capabilities.periodicGrowthReportEnabled)
    && currentUser.roleCodes.includes('PARENT');
}

export function canAccessStudentQrLogin(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.studentQrLoginEnabled
    && currentUser.roleCodes.some((role) => ['PARENT', 'ORG_ADMIN'].includes(role));
}

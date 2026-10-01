import { lazy, useCallback, useEffect, useState } from 'react';
import { Alert, Avatar, Button, ConfigProvider, Dropdown, Layout, Menu, Spin, message } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AppWindow, BookOpenCheck, Building2, Cable, ChevronDown, CircleAlert, ClipboardList, Coins, Database, FileDown, FileSpreadsheet, Gauge, Gift, GraduationCap, KeyRound, LayoutDashboard, LogOut, Paperclip, QrCode, ShieldCheck, UserRoundCog, UsersRound } from 'lucide-react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { authApi, type CurrentUser } from '../api/auth';
import { capabilityApi, type ClientCapabilities } from '../api/capability';
import { LoginPage } from '../features/auth/LoginPage';
import { ChangePasswordModal } from '../features/auth/ChangePasswordModal';
import { ParentOnboardingPage } from '../features/auth/ParentOnboardingPage';
import { ParentRelationshipInvitationPage } from '../features/parent-relationships/ParentRelationshipInvitationPage';
import { WorkspaceTabs } from './WorkspaceTabs';
import { menuApi, type MenuNode } from '../api/menus';
import { MenuConfigurationProvider, menuOrderStyles } from './MenuConfiguration';
import { buildMenuNavigation } from './menuNavigation';
import * as NavigationIcons from 'lucide-react';
import { menuPageCatalog } from './menuCatalog';
import { canAccessAttendance } from '../features/attendance/rules';
import { canAccessSystemTasks } from '../features/system-tasks/SystemTaskWorkbench';

const DashboardPage = lazy(async () => ({ default: (await import('../features/dashboard/DashboardPage')).DashboardPage }));
const MenuManagementPage = lazy(async () => ({ default: (await import('../features/menus/MenuManagementPage')).MenuManagementPage }));
const SystemTaskWorkbench = lazy(async () => ({ default: (await import('../features/system-tasks/SystemTaskWorkbench')).SystemTaskWorkbench }));
const FeatureManagementPage = lazy(async () => ({ default: (await import('../features/feature-management/FeatureManagementPage')).FeatureManagementPage }));
const IamManagementPage = lazy(async () => ({ default: (await import('../features/iam/IamManagementPage')).IamManagementPage }));
const OrganizationManagementPage = lazy(async () => ({ default: (await import('../features/organizations/OrganizationManagementPage')).OrganizationManagementPage }));
const UserManagementPage = lazy(async () => ({ default: (await import('../features/users/UserManagementPage')).UserManagementPage }));
const LearningTaskManagementPage = lazy(async () => ({ default: (await import('../features/learning-tasks/LearningTaskManagementPage')).LearningTaskManagementPage }));
const GrowthPointPage = lazy(async () => ({ default: (await import('../features/growth-points/GrowthPointPage')).GrowthPointPage }));
const RewardManagementPage = lazy(async () => ({ default: (await import('../features/rewards/RewardManagementPage')).RewardManagementPage }));
const GrowthReviewPage = lazy(async () => ({ default: (await import('../features/growth-reviews/GrowthReviewPage')).GrowthReviewPage }));
const AnonymousRankPage = lazy(async () => ({ default: (await import('../features/anonymous-ranks/AnonymousRankPage')).AnonymousRankPage }));
const AnonymousRankWithdrawalPage = lazy(async () => ({ default: (await import('../features/anonymous-ranks/AnonymousRankWithdrawalPage')).AnonymousRankWithdrawalPage }));
const StudentLoginManagementPage = lazy(async () => ({ default: (await import('../features/student-login/StudentLoginManagementPage')).StudentLoginManagementPage }));
const ParentRelationshipPage = lazy(async () => ({ default: (await import('../features/parent-relationships/ParentRelationshipPage')).ParentRelationshipPage }));
const DictionaryManagementPage = lazy(async () => ({ default: (await import('../features/dictionaries/DictionaryManagementPage')).DictionaryManagementPage }));
const CacheManagementPage = lazy(async () => ({ default: (await import('../features/cache/CacheManagementPage')).CacheManagementPage }));
const InterfaceServiceManagementPage = lazy(async () => ({ default: (await import('../features/interface-services/InterfaceServiceManagementPage')).InterfaceServiceManagementPage }));
const AttachmentManagementPage = lazy(async () => ({ default: (await import('../features/attachments/AttachmentManagementPage')).AttachmentManagementPage }));
const ImportExportTemplateManagementPage = lazy(async () => ({ default: (await import('../features/import-export-templates/ImportExportTemplateManagementPage')).ImportExportTemplateManagementPage }));
const ImportJobManagementPage = lazy(async () => ({ default: (await import('../features/import-jobs/ImportJobManagementPage')).ImportJobManagementPage }));
const ExportJobManagementPage = lazy(async () => ({ default: (await import('../features/export-jobs/ExportJobManagementPage')).ExportJobManagementPage }));
const TeacherManagementPage = lazy(async () => ({ default: (await import('../features/teachers/TeacherManagementPage')).TeacherManagementPage }));
const ExceptionReportPage = lazy(async () => ({ default: (await import('../features/exception-reports/ExceptionReportPage')).ExceptionReportPage }));
const AttendancePage = lazy(async () => ({ default: (await import('../features/attendance/AttendancePage')).AttendancePage }));

const { Header, Sider, Content } = Layout;

export function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/parent-onboarding" element={<ParentOnboardingPage />} />
        <Route path="/parent-relationship-invitations/:invitationId" element={<ParentRelationshipInvitationPage />} />
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
  const [configuredMenus, setConfiguredMenus] = useState<MenuNode[]>([]);
  const [menuError, setMenuError] = useState('');
  const [passwordOpen, setPasswordOpen] = useState(false);
  const refreshMenus = useCallback(async () => {
    try { setConfiguredMenus(await menuApi.current()); setMenuError(''); }
    catch (error) { setMenuError(error instanceof Error ? error.message : '菜单配置加载失败'); throw error; }
  }, []);
  const updateAttendanceAccess = useCallback((user: CurrentUser, loadedCapabilities: ClientCapabilities) => {
    setCurrentUser(user);
    setCapabilities(loadedCapabilities);
  }, []);

  useEffect(() => {
    if (!authApi.hasLocalSession()) {
      navigate('/login', { replace: true });
      return;
    }
    void Promise.all([authApi.currentUser(), capabilityApi.web(), menuApi.current().catch(error => {
      setMenuError(error instanceof Error ? error.message : '菜单配置加载失败');
      return [] as MenuNode[];
    })])
      .then(([user, loadedCapabilities, menus]) => {
        setCurrentUser(user);
        setCapabilities(loadedCapabilities);
        setConfiguredMenus(menus);
      })
      .catch(() => {
        authApi.clearLocalSession();
        navigate('/login', { replace: true });
      })
      .finally(() => setLoading(false));
  }, [navigate]);

  useEffect(() => {
    const reload = () => { if (authApi.hasLocalSession()) void refreshMenus().catch(() => {}); };
    window.addEventListener('focus', reload);
    return () => window.removeEventListener('focus', reload);
  }, [refreshMenus]);

  function endSession(): void {
    authApi.clearLocalSession();
    navigate('/login', { replace: true });
  }

  if (loading || !currentUser || !capabilities) {
    return <div className="app-loading"><Spin size="large" /></div>;
  }
  if (menuError && configuredMenus.length === 0) {
    return <div className="app-loading"><Alert type="error" message={menuError} action={<Button onClick={() => void refreshMenus().catch(() => {})}>重试</Button>} /></div>;
  }

  const learningTasksAvailable = canAccessLearningTasks(currentUser, capabilities);
  const systemTasksAvailable = canAccessSystemTasks(currentUser, capabilities);
  const featureManagementAvailable = currentUser.clientType === 'WEB' && capabilities.client === 'WEB'
    && currentUser.roleCodes.some(role => ['SYS_ADMIN', 'SYS_AUDITOR'].includes(role))
    && currentUser.permissionCodes.includes('FEATURE_TOGGLE_READ');
  const growthPointsAvailable = canAccessGrowthPoints(currentUser, capabilities);
  const rewardsAvailable = canAccessRewards(currentUser, capabilities);
  const growthReviewsAvailable = canAccessGrowthReviews(currentUser, capabilities);
  const anonymousRankAvailable = capabilities.anonymousClassRankEnabled === true
    && currentUser.roleCodes.includes('PARENT') && !currentUser.roleCodes.includes('SYS_AUDITOR')
    && currentUser.permissionCodes.includes('ANONYMOUS_CLASS_RANK_READ');
  const rankWithdrawalAvailable = currentUser.roleCodes.includes('PARENT') && !currentUser.roleCodes.includes('SYS_AUDITOR');
  const studentQrLoginAvailable = canAccessStudentQrLogin(currentUser, capabilities);
  const parentRelationshipsAvailable = canAccessParentRelationships(currentUser, capabilities);
  const organizationPageAvailable = canAccessOrganizationPage(currentUser, capabilities);
  const dictionaryManagementAvailable = canAccessDictionaryManagement(currentUser, capabilities);
  const cacheManagementAvailable = canAccessCacheManagement(currentUser, capabilities);
  const interfaceServiceManagementAvailable = canAccessInterfaceServiceManagement(currentUser, capabilities);
  const attachmentManagementAvailable = canAccessAttachmentManagement(currentUser, capabilities);
  const importExportTemplatesAvailable = canAccessImportExportTemplates(currentUser, capabilities);
  const importJobsAvailable = canAccessImportJobs(currentUser, capabilities);
  const exportJobsAvailable = canAccessExportJobs(currentUser, capabilities);
  const teacherManagementAvailable = canAccessTeacherManagement(currentUser, capabilities);
  const exceptionReportsAvailable = canAccessExceptionReports(currentUser, capabilities);
  const attendanceAvailable = canAccessAttendance(currentUser, capabilities.attendanceManagementEnabled);
  const selectedKey = location.pathname.startsWith('/menu-management') ? 'menu-management'
    : location.pathname.startsWith('/rank-preferences') ? 'rank-preferences'
    : location.pathname.startsWith('/system-tasks') ? 'system-tasks'
    : location.pathname.startsWith('/feature-management') ? 'feature-management'
    : location.pathname.startsWith('/anonymous-ranks') ? 'anonymous-ranks'
    : location.pathname.startsWith('/attendance-records') ? 'attendance-records'
    : location.pathname.startsWith('/exception-reports') ? 'exception-reports'
    : location.pathname.startsWith('/export-jobs') ? 'export-jobs'
    : location.pathname.startsWith('/import-jobs') ? 'import-jobs'
    : location.pathname.startsWith('/parent-relationships') ? 'parent-relationships'
    : location.pathname.startsWith('/import-export-templates') ? 'import-export-templates'
    : location.pathname.startsWith('/attachment-management') ? 'attachment-management'
    : location.pathname.startsWith('/interface-services') ? 'interface-services'
    : location.pathname.startsWith('/cache-management') ? 'cache-management'
    : location.pathname.startsWith('/dictionaries') ? 'dictionaries'
    : location.pathname.startsWith('/student-login') ? 'student-login'
    : location.pathname.startsWith('/growth-reviews') ? 'growth-reviews'
    : location.pathname.startsWith('/rewards') ? 'rewards'
    : location.pathname.startsWith('/growth-points') ? 'growth-points'
    : location.pathname.startsWith('/teachers') ? 'teachers'
    : location.pathname.startsWith('/learning-tasks') ? 'learning-tasks'
    : location.pathname.startsWith('/users') ? 'users'
    : location.pathname.startsWith('/organizations') ? 'organizations'
    : location.pathname.startsWith('/iam') ? 'iam' : 'dashboard';

  const registeredMenus = [
            { key: 'dashboard', icon: <LayoutDashboard size={18} />, label: '工作台' },
            systemTasksAvailable ? { key: 'system-tasks', icon: <ClipboardList size={18} />, label: '系统任务' } : null,
            featureManagementAvailable ? { key: 'feature-management', icon: <Gauge size={18} />, label: '功能开关' } : null,
            attendanceAvailable
              ? { key: 'attendance-records', icon: <BookOpenCheck size={18} />, label: '考勤台账' }
              : null,
            learningTasksAvailable
              ? { key: 'learning-tasks', icon: <ClipboardList size={18} />, label: '学习任务' }
              : null,
            exceptionReportsAvailable
              ? { key: 'exception-reports', icon: <CircleAlert size={18} />, label: '异常报备' }
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
            anonymousRankAvailable
              ? { key: 'anonymous-ranks', icon: <Coins size={18} />, label: '班级匿名排行' }
              : null,
            rankWithdrawalAvailable
              ? { key: 'rank-preferences', icon: <ShieldCheck size={18} />, label: '排行查看授权' }
              : null,
            studentQrLoginAvailable
              ? { key: 'student-login', icon: <QrCode size={18} />, label: '学生登录' }
              : null,
            parentRelationshipsAvailable
              ? { key: 'parent-relationships', icon: <UserRoundCog size={18} />, label: '家长关系' }
              : null,
            teacherManagementAvailable
              ? { key: 'teachers', icon: <GraduationCap size={18} />, label: '教师管理' }
              : null,
            { key: 'users', icon: <UsersRound size={18} />, label: '用户管理' },
            { key: 'iam', icon: <ShieldCheck size={18} />, label: '角色与权限' },
            currentUser.roleCodes.some(role => ['SYS_ADMIN', 'SYS_AUDITOR'].includes(role)) && currentUser.permissionCodes.includes('MENU_READ')
              ? { key: 'menu-management', icon: <NavigationIcons.ListTree size={18} />, label: '菜单管理' } : null,
            dictionaryManagementAvailable
              ? { key: 'dictionaries', icon: <Database size={18} />, label: '数据字典' }
              : null,
            cacheManagementAvailable
              ? { key: 'cache-management', icon: <Gauge size={18} />, label: '缓存管理' }
              : null,
            interfaceServiceManagementAvailable
              ? { key: 'interface-services', icon: <Cable size={18} />, label: '接口服务' }
              : null,
            attachmentManagementAvailable
              ? { key: 'attachment-management', icon: <Paperclip size={18} />, label: '附件管理' }
              : null,
            importExportTemplatesAvailable
              ? { key: 'import-export-templates', icon: <FileSpreadsheet size={18} />, label: '导入导出模板' }
              : null,
            importJobsAvailable
              ? { key: 'import-jobs', icon: <FileSpreadsheet size={18} />, label: '导入校验作业' }
              : null,
            exportJobsAvailable
              ? { key: 'export-jobs', icon: <FileDown size={18} />, label: '数据导出中心' }
              : null,
            organizationPageAvailable
              ? { key: 'organizations', icon: <Building2 size={18} />, label: currentUser.roleCodes.includes('ORG_ADMIN') ? '机构业务' : '组织管理' }
              : null
          ].filter((item): item is NonNullable<typeof item> => item !== null);

  const configuredNavigation = buildMenuNavigation(configuredMenus, registeredMenus.map(item => ({ path: `/${item.key}`, label: item.label, icon: item.icon })), name => {
    const known = menuPageCatalog.some(page => page.icon === name) || name === 'Folder';
    const Icon = known && name ? NavigationIcons[name as keyof typeof NavigationIcons] as typeof NavigationIcons.Folder : null;
    return Icon ? <Icon size={18} /> : null;
  });

  return (
    <MenuConfigurationProvider nodes={menuError ? [] : configuredMenus} refresh={refreshMenus}>
    <style>{menuOrderStyles(configuredMenus)}</style>
    <Layout className="app-shell">
      <Sider width={232} breakpoint="lg" collapsedWidth={64} className="app-sider">
        <div className="brand-lockup"><AppWindow size={22} aria-hidden="true" /><span>灵动伴随</span></div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={[`/${selectedKey}`]}
          onClick={({ key }) => navigate(key)}
          items={configuredNavigation.items}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <span className="header-context">管理端</span>
          <Dropdown menu={{ items: [
            { key: 'password', icon: <KeyRound size={16} />, label: '修改密码', onClick: () => setPasswordOpen(true) },
            { key: 'logout', icon: <LogOut size={16} />, label: '退出登录', onClick: endSession }
          ] }} trigger={['click']}>
            <Button type="text" className="user-menu"><Avatar size="small" icon={<KeyRound size={14} />} />{currentUser.displayName}<ChevronDown size={16} /></Button>
          </Dropdown>
        </Header>
        <Content className="app-content">
          {menuError && <Alert type="error" message={menuError} action={<Button onClick={() => void refreshMenus().catch(() => {})}>重试</Button>} />}
          <WorkspaceTabs key={currentUser.userId} pages={configuredNavigation.pages}>
            <Routes>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/system-tasks" element={systemTasksAvailable
                ? <SystemTaskWorkbench key={currentUser.userId} currentUser={currentUser} onNavigate={navigate} onAccessChange={updateAttendanceAccess} />
                : <Navigate to="/dashboard" replace />} />
              <Route path="/feature-management" element={featureManagementAvailable
                ? <FeatureManagementPage key={currentUser.userId} currentUser={currentUser} onAccessChange={updateAttendanceAccess} />
                : <Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={(
                <DashboardPage
                  currentUser={currentUser}
                  accountSecurityManagementEnabled={capabilities.accountSecurityManagementEnabled}
                  learningTaskManagementEnabled={capabilities.learningTaskManagementEnabled}
                  parentAccountLifecycleEnabled={capabilities.parentAccountLifecycleEnabled}
                  attendanceAvailable={attendanceAvailable}
                  onOpenAttendance={() => navigate('/attendance-records')}
                  onSessionEnded={endSession}
                />
              )} />
              <Route path="/users" element={<UserManagementPage />} />
              <Route path="/iam" element={<IamManagementPage />} />
              <Route path="/menu-management" element={currentUser.permissionCodes.includes('MENU_READ') && currentUser.roleCodes.some(role => ['SYS_ADMIN', 'SYS_AUDITOR'].includes(role))
                ? <MenuManagementPage currentUser={currentUser} /> : <Navigate to="/dashboard" replace />} />
              <Route path="/attendance-records" element={attendanceAvailable
                ? <AttendancePage currentUser={currentUser} onAccessUpdated={updateAttendanceAccess} />
                : <Navigate to="/dashboard" replace />} />
              <Route path="/exception-reports" element={exceptionReportsAvailable
                ? <ExceptionReportPage currentUser={currentUser} />
                : <Navigate to="/dashboard" replace />} />
              <Route
                path="/dictionaries"
                element={dictionaryManagementAvailable
                  ? <DictionaryManagementPage
                      canManage={currentUser.permissionCodes.includes('DICTIONARY_MANAGE')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/cache-management"
                element={cacheManagementAvailable
                  ? <CacheManagementPage
                      canManage={currentUser.permissionCodes.includes('CACHE_MANAGE')}
                      canReview={currentUser.permissionCodes.includes('CACHE_REVIEW')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/interface-services"
                element={interfaceServiceManagementAvailable
                  ? <InterfaceServiceManagementPage
                      canManage={currentUser.permissionCodes.includes('INTERFACE_SERVICE_MANAGE')}
                      canReview={currentUser.permissionCodes.includes('INTERFACE_SERVICE_REVIEW')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/attachment-management"
                element={attachmentManagementAvailable
                  ? <AttachmentManagementPage
                      canReadRules={currentUser.permissionCodes.includes('ATTACHMENT_RULE_READ')}
                      canManage={currentUser.permissionCodes.includes('ATTACHMENT_RULE_MANAGE')}
                      canReadFiles={currentUser.permissionCodes.includes('ATTACHMENT_FILE_LEDGER_READ')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/import-export-templates"
                element={importExportTemplatesAvailable
                  ? <ImportExportTemplateManagementPage
                      canManage={currentUser.permissionCodes.includes('IMPORT_EXPORT_TEMPLATE_MANAGE')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/import-jobs"
                element={importJobsAvailable
                  ? <ImportJobManagementPage
                      canCreate={currentUser.permissionCodes.includes('IMPORT_JOB_CREATE')}
                      canExecuteStudentImport={capabilities.studentBatchImportEnabled === true
                        && currentUser.permissionCodes.includes('STUDENT_IMPORT_EXECUTE')}
                      canReadStudentImport={capabilities.studentBatchImportEnabled === true
                        && currentUser.permissionCodes.includes('STUDENT_IMPORT_RESULT_READ')}
                      canDownloadStudentCredentials={capabilities.studentBatchImportEnabled === true
                        && currentUser.permissionCodes.includes('STUDENT_IMPORT_CREDENTIAL_DOWNLOAD')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/export-jobs"
                element={exportJobsAvailable
                  ? <ExportJobManagementPage
                      accessRevision={JSON.stringify([currentUser.userId, currentUser.roleCodes, currentUser.permissionCodes, capabilities])}
                      canRead={currentUser.permissionCodes.includes('EXPORT_JOB_READ')}
                      canCreateOrdinary={currentUser.permissionCodes.includes('EXPORT_JOB_CREATE')}
                      canExportDictionary={capabilities.dictionaryManagementEnabled === true
                        && currentUser.roleCodes.includes('SYS_ADMIN')
                        && !currentUser.roleCodes.includes('SYS_AUDITOR')
                        && currentUser.permissionCodes.includes('DICTIONARY_READ')
                        && currentUser.permissionCodes.includes('DICTIONARY_EXPORT')}
                      canExportTemplate={capabilities.importExportTemplateManagementEnabled === true
                        && currentUser.roleCodes.includes('SYS_ADMIN')
                        && !currentUser.roleCodes.includes('SYS_AUDITOR')
                        && currentUser.permissionCodes.includes('IMPORT_EXPORT_TEMPLATE_READ')
                        && currentUser.permissionCodes.includes('IMPORT_EXPORT_TEMPLATE_EXPORT')}
                      canExportInterface={canExportInterfaceLedger(currentUser, capabilities)}
                      canExportCache={canExportCache(currentUser, capabilities)}
                      canExportSystemTasks={canExportSystemTasks(currentUser, capabilities)}
                      canExportRewards={canExportRewards(currentUser, capabilities)}
                      canExportStudentTasks={canExportStudentTasks(currentUser, capabilities)}
                      canExportOrgTaskStatistics={canExportOrgTaskStatistics(currentUser, capabilities)}
                      canExportAttendanceLedger={canExportAttendanceLedger(currentUser, capabilities)}
                      canExportAttachments={canExportAttachments(currentUser, capabilities)}
                      canExportExceptions={canExportExceptions(currentUser, capabilities)}
                      canSubmitSensitive={currentUser.permissionCodes.includes('EXPORT_SENSITIVE_SUBMIT')}
                      canReview={currentUser.permissionCodes.includes('EXPORT_SENSITIVE_REVIEW')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route path="/organizations" element={(
                organizationPageAvailable ? <OrganizationManagementPage
                  currentUser={currentUser}
                  organizationManagementEnabled={capabilities.organizationManagementEnabled === true}
                  studentOrganizationRelationshipEnabled={capabilities.studentOrganizationRelationshipEnabled}
                  parentMobileManualRecoveryEnabled={capabilities.parentMobileManualRecoveryEnabled === true}
                  studentAccountCancellationEnabled={capabilities.studentAccountCancellationEnabled === true}
                  classManagementEnabled={capabilities.classManagementEnabled === true}
                /> : <Navigate to="/dashboard" replace />
              )} />
              <Route
                path="/teachers"
                element={teacherManagementAvailable
                  ? <TeacherManagementPage permissionCodes={currentUser.permissionCodes} />
                  : <Navigate to="/dashboard" replace />}
              />
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
                  ? <GrowthPointPage
                      correctionEnabled={capabilities.growthPointCorrectionEnabled}
                      dataExportEnabled={capabilities.dataExportEnabled === true}
                      canCreateExport={currentUser.permissionCodes.includes('EXPORT_JOB_CREATE')}
                    />
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
                  ? <GrowthReviewPage subscriptionEnabled={capabilities.growthReviewSubscriptionEnabled === true
                      && currentUser.roleCodes.includes('PARENT') && !currentUser.roleCodes.includes('SYS_AUDITOR')}
                    canEnableSubscription={currentUser.permissionCodes.includes('GROWTH_REVIEW_SUBSCRIBE_CHILD')
                      && currentUser.permissionCodes.includes('GROWTH_REVIEW_READ_CHILD')}
                    canCreateExport={capabilities.growthReviewPdfExportEnabled === true
                      && !currentUser.roleCodes.includes('SYS_AUDITOR')
                      && currentUser.permissionCodes.includes('EXPORT_JOB_CREATE')
                      && currentUser.permissionCodes.includes('GROWTH_REVIEW_READ_CHILD')}
                    canReadExportHistory={capabilities.attachmentServiceEnabled === true
                      && !currentUser.roleCodes.includes('SYS_AUDITOR')
                      && currentUser.permissionCodes.includes('EXPORT_JOB_READ')
                      && currentUser.permissionCodes.includes('GROWTH_REVIEW_READ_CHILD')} />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route path="/anonymous-ranks" element={anonymousRankAvailable
                ? <AnonymousRankPage /> : <Navigate to="/dashboard" replace />} />
              <Route path="/rank-preferences" element={rankWithdrawalAvailable
                ? <AnonymousRankWithdrawalPage /> : <Navigate to="/dashboard" replace />} />
              <Route
                path="/student-login"
                element={studentQrLoginAvailable
                  ? <StudentLoginManagementPage
                      studentWechatAuthEnabled={capabilities.studentWechatAuthEnabled === true}
                      canManageStudentWechat={currentUser.roleCodes.includes('PARENT')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/parent-relationships"
                element={parentRelationshipsAvailable
                  ? <ParentRelationshipPage currentUser={currentUser} />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </WorkspaceTabs>
        </Content>
      </Layout>
    </Layout>
      {passwordOpen && <ChangePasswordModal onCancel={() => setPasswordOpen(false)} onSuccess={() => {
        setPasswordOpen(false);
        message.success('密码修改成功，请使用新密码重新登录');
        endSession();
      }} />}
    </MenuConfigurationProvider>
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
  return (capabilities.studentQrLoginEnabled
      && currentUser.roleCodes.some((role) => ['PARENT', 'ORG_ADMIN'].includes(role)))
    || (capabilities.studentWechatAuthEnabled === true && currentUser.roleCodes.includes('PARENT'));
}

export function canAccessParentRelationships(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.parentRelationshipManagementEnabled
    && currentUser.roleCodes.includes('PARENT');
}

export function canAccessOrganizationPage(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  if (currentUser.roleCodes.includes('ORG_ADMIN')) {
    return true;
  }
  return capabilities.organizationManagementEnabled === true
    && currentUser.roleCodes.some((role) => ['SYS_ADMIN', 'SYS_AUDITOR'].includes(role));
}

export function canAccessDictionaryManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.dictionaryManagementEnabled === true
    && currentUser.permissionCodes.includes('DICTIONARY_READ');
}

export function canAccessCacheManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.cacheManagementEnabled === true
    && currentUser.permissionCodes.includes('CACHE_READ');
}

export function canAccessInterfaceServiceManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.interfaceServiceManagementEnabled === true
    && currentUser.permissionCodes.includes('INTERFACE_SERVICE_READ');
}

export function canAccessAttachmentManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.attachmentServiceEnabled === true
    && currentUser.permissionCodes.some((permission) =>
      ['ATTACHMENT_RULE_READ', 'ATTACHMENT_FILE_LEDGER_READ'].includes(permission));
}

export function canAccessImportExportTemplates(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.importExportTemplateManagementEnabled === true
    && currentUser.permissionCodes.includes('IMPORT_EXPORT_TEMPLATE_READ');
}

export function canAccessImportJobs(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.dataImportValidationEnabled === true
    && currentUser.permissionCodes.includes('IMPORT_JOB_READ');
}

export function canAccessExportJobs(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.dataExportEnabled === true
    && currentUser.permissionCodes.some((permission) =>
      ['EXPORT_JOB_READ', 'EXPORT_SENSITIVE_REVIEW'].includes(permission));
}

export function canExportInterfaceLedger(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true
    && capabilities.attachmentServiceEnabled === true
    && capabilities.interfaceServiceManagementEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true
    && currentUser.roleCodes.includes('SYS_ADMIN')
    && !currentUser.roleCodes.includes('SYS_AUDITOR')
    && currentUser.permissionCodes.includes('INTERFACE_SERVICE_READ')
    && currentUser.permissionCodes.includes('INTERFACE_SERVICE_EXPORT');
}

export function canAccessTeacherManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.teacherManagementEnabled === true
    && currentUser.roleCodes.includes('ORG_ADMIN')
    && currentUser.permissionCodes.includes('TEACHER_READ');
}

export function canAccessExceptionReports(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.studentExceptionReportEnabled === true
    && currentUser.permissionCodes.includes('EXCEPTION_REPORT_READ')
    && currentUser.roleCodes.some((role) => ['TEACHER', 'ORG_ADMIN'].includes(role));
}

export function canExportCache(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.cacheManagementEnabled === true
    && currentUser.roleCodes.includes('SYS_ADMIN') && !currentUser.roleCodes.includes('SYS_AUDITOR')
    && currentUser.permissionCodes.includes('CACHE_READ') && currentUser.permissionCodes.includes('CACHE_EXPORT');
}

export function canExportSystemTasks(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true
    && currentUser.roleCodes.some(role => role === 'SYS_ADMIN' || role === 'SYS_AUDITOR')
    && currentUser.permissionCodes.includes('SYSTEM_TASK_READ') && currentUser.permissionCodes.includes('SYSTEM_TASK_EXPORT');
}

export function canExportRewards(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.rewardExchangeEnabled === true
    && currentUser.roleCodes.includes('PARENT') && !currentUser.roleCodes.includes('SYS_AUDITOR')
    && currentUser.permissionCodes.includes('REWARD_EXCHANGE_REVIEW_CHILD')
    && currentUser.permissionCodes.includes('REWARD_EXCHANGE_EXPORT');
}

export function canExportExceptions(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.studentExceptionReportEnabled === true
    && currentUser.roleCodes.some(role => role === 'TEACHER' || role === 'ORG_ADMIN')
    && !currentUser.roleCodes.includes('SYS_AUDITOR')
    && currentUser.permissionCodes.includes('EXCEPTION_REPORT_READ')
    && currentUser.permissionCodes.includes('EXCEPTION_REPORT_EXPORT');
}

export function canExportAttachments(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true
    && currentUser.permissionCodes.includes('ATTACHMENT_FILE_LEDGER_READ')
    && currentUser.permissionCodes.includes('ATTACHMENT_FILE_LEDGER_EXPORT');
}

/** 按服务端角色优先级选择任务读取权限，不允许高优先级身份回退到家长范围。 */
export function canExportStudentTasks(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  const roles = currentUser.roleCodes;
  const readPermission = roles.includes('ORG_ADMIN') || roles.includes('TEACHER')
    ? 'LEARNING_TASK_PROGRESS_READ' : roles.includes('PARENT') ? 'LEARNING_TASK_READ_MANAGED' : undefined;
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.learningTaskManagementEnabled === true
    && !roles.includes('SYS_AUDITOR') && readPermission !== undefined
    && currentUser.permissionCodes.includes(readPermission)
    && currentUser.permissionCodes.includes('STUDENT_TASK_REPORT_EXPORT');
}

/** 机构任务统计仅机构管理员可导出，兼任审核员排除。 */
export function canExportOrgTaskStatistics(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.learningTaskManagementEnabled === true
    && currentUser.roleCodes.includes('ORG_ADMIN') && !currentUser.roleCodes.includes('SYS_AUDITOR')
    && currentUser.permissionCodes.includes('ORGANIZATION_TASK_STATISTICS_EXPORT')
    && currentUser.permissionCodes.includes('LEARNING_TASK_PROGRESS_READ');
}

/** 考勤台账四身份可导出（机构/教师/家长/学生），兼任审核员排除，沿用考勤管理开关。 */
export function canExportAttendanceLedger(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  const roles = currentUser.roleCodes;
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.attendanceManagementEnabled === true
    && !roles.includes('SYS_AUDITOR')
    && roles.some(role => ['ORG_ADMIN', 'TEACHER', 'PARENT', 'STUDENT'].includes(role))
    && currentUser.permissionCodes.includes('ATTENDANCE_LEDGER_EXPORT')
    && currentUser.permissionCodes.includes('ATTENDANCE_READ');
}
